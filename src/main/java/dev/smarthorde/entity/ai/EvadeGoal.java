package dev.smarthorde.entity.ai;

import dev.smarthorde.config.SmartHordeConfig;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.EnumSet;
import java.util.List;

/**
 * 闪避 Goal（轮5，[C4]/[C5] 调整）。
 * 触发条件：受击 / 被瞄准 / 弹射物来袭。
 * <ul>
 *   <li>[C4] 概率触发：canUse 内按 EVADE_CHANCE 掷骰；冷却接入 EVADE_COOLDOWN 配置</li>
 *   <li>[C4] 事件驱动：{@link EvadeEventTrigger} 在伤害结算前置位 pendingEvade，
 *       canUse 优先消费该标志直接触发受击闪避；hurtTime 轮询保留为兜底</li>
 *   <li>[C5] 玩家瞄准/弹射物扫描各带独立扫描冷却（AI_TICK_INTERVAL 派生，
 *       与闪避冷却解耦），空闲期不再每 tick 双扫描</li>
 * </ul>
 * 闪避动作：受击→后撤+侧向、瞄准→横向侧跳、弹射物→垂直规避。
 * priority=3（SmartZombie 注册点），仅作用于移动。
 */
public class EvadeGoal extends Goal {
    private final PathfinderMob mob;
    private int cooldown = 0;
    private Vec3 evadeDestination = Vec3.ZERO;
    private TriggerReason lastTrigger = TriggerReason.NONE;

    /** [C4] 事件驱动闪避标志（EvadeEventTrigger 置位，canUse 消费）。 */
    private boolean pendingEvade = false;
    /** [m5] 闪避请求置位时的 tickCount：消费时校验时效，冷却/概率未过导致的滞留不再凭空消费。 */
    private int pendingEvadeRequestTick = Integer.MIN_VALUE;
    /** [C5] 玩家瞄准扫描冷却（独立于闪避冷却）。 */
    private int aimScanCooldown = 0;
    /** [C5] 弹射物扫描冷却（独立于闪避冷却）。 */
    private int projScanCooldown = 0;

    private static final double AIM_DETECT_RANGE = 12.0D;
    private static final double PROJECTILE_SCAN_RADIUS = 4.0D;
    private static final double EVADE_DISTANCE = 3.0D;
    /** [C5] 扫描冷却 = AI_TICK_INTERVAL × 2（默认档 8 tick，落在 4-10 区间）。 */
    private static final int SCAN_COOLDOWN_MULT = 2;
    /** [m5] 闪避请求有效时长（tick）：超时未消费即作废。 */
    private static final int PENDING_EVADE_TTL_TICKS = 10;

    private enum TriggerReason { NONE, HURT, AIMED, PROJECTILE }

    public EvadeGoal(PathfinderMob mob) {
        this.mob = mob;
        this.setFlags(EnumSet.of(Flag.MOVE));
    }

    /** [C4] 事件驱动入口：伤害结算前由 EvadeEventTrigger 调用。 */
    public void requestEvade() {
        this.pendingEvade = true;
        // [m5] 记录请求时刻，消费时校验时效
        this.pendingEvadeRequestTick = mob.tickCount;
    }

    private static int scanIntervalTicks() {
        return Math.max(1, SmartHordeConfig.AI_TICK_INTERVAL.get()) * SCAN_COOLDOWN_MULT;
    }

    @Override
    public boolean canUse() {
        if (!SmartHordeConfig.EVADE.get()) return false;
        // [F3] STACK 骑乘中让位：乘客无法自主位移，闪避无意义
        if (ClimbOrStackGoal.isStackRider(mob)) return false;
        if (cooldown > 0) { cooldown--; return false; }

        // [C4] 概率判定：本次轮询是否允许触发闪避
        if (mob.getRandom().nextFloat() >= SmartHordeConfig.EVADE_CHANCE.get().floatValue()) {
            return false;
        }

        // [C4] 事件驱动：受击事件已置位，直接按受击方向闪避
        if (pendingEvade) {
            pendingEvade = false;
            // [m5] 过期丢弃：请求超过 10 tick 未消费（被冷却/概率阻塞滞留）即作废，
            //      继续走后续常规检测，避免滞后很久后凭空触发受击闪避
            if (mob.tickCount - pendingEvadeRequestTick <= PENDING_EVADE_TTL_TICKS) {
                lastTrigger = TriggerReason.HURT;
                computeHurtEvade();
                return true;
            }
        }

        // 1. 受击检测：hurtTime > 0 表示刚受击（事件轮询兜底路径）
        if (mob.hurtTime > 0) {
            lastTrigger = TriggerReason.HURT;
            computeHurtEvade();
            return true;
        }

        // 2. 瞄准检测（[C5] 独立扫描冷却）
        Player aimer = null;
        if (aimScanCooldown > 0) {
            aimScanCooldown--;
        } else {
            aimer = findAimingPlayer();
            aimScanCooldown = scanIntervalTicks();
        }
        if (aimer != null) {
            lastTrigger = TriggerReason.AIMED;
            computeAimEvade(aimer);
            return true;
        }

        // 3. 弹射物检测（[C5] 独立扫描冷却）
        Projectile incoming = null;
        if (projScanCooldown > 0) {
            projScanCooldown--;
        } else {
            incoming = findIncomingProjectile();
            projScanCooldown = scanIntervalTicks();
        }
        if (incoming != null) {
            lastTrigger = TriggerReason.PROJECTILE;
            computeProjectileEvade(incoming);
            return true;
        }

        lastTrigger = TriggerReason.NONE;
        return false;
    }

    @Override
    public void start() {
        if (mob.level() instanceof ServerLevel sl) {
            dev.smarthorde.effects.HordeEffects.playDodge(sl, mob.position());
        }
        mob.getNavigation().moveTo(evadeDestination.x, evadeDestination.y, evadeDestination.z, 1.4D);
    }

    @Override
    public boolean canContinueToUse() {
        if (!SmartHordeConfig.EVADE.get()) return false;
        // [F3] STACK 骑乘中让位
        if (ClimbOrStackGoal.isStackRider(mob)) return false;
        if (!mob.getNavigation().isInProgress()) return false;
        return mob.distanceToSqr(evadeDestination) > 1.0D;
    }

    @Override
    public void stop() {
        mob.getNavigation().stop();
        // [C4] 冷却接入 EVADE_COOLDOWN 配置
        cooldown = SmartHordeConfig.EVADE_COOLDOWN.get();
        pendingEvade = false;
        pendingEvadeRequestTick = Integer.MIN_VALUE; // [m5] 清标志时一并重置时效基准
        lastTrigger = TriggerReason.NONE;
    }

    // ===== 受击闪避 =====
    private void computeHurtEvade() {
        LivingEntity attacker = mob.getLastHurtByMob();
        Vec3 awayDir;
        if (attacker != null && attacker.isAlive()) {
            awayDir = mob.position().subtract(attacker.position()).normalize();
        } else {
            awayDir = mob.getLookAngle().scale(-1.0D).normalize();
        }
        double sideX = (mob.getRandom().nextDouble() - 0.5D) * 2.0D;
        double sideZ = (mob.getRandom().nextDouble() - 0.5D) * 2.0D;
        Vec3 sideOffset = new Vec3(sideX, 0.0D, sideZ).normalize().scale(EVADE_DISTANCE * 0.6D);
        evadeDestination = mob.position().add(awayDir.scale(EVADE_DISTANCE)).add(sideOffset);
    }

    // ===== 瞄准闪避 =====
    private void computeAimEvade(Player aimer) {
        Vec3 aimDir = aimer.getLookAngle().normalize();
        Vec3 perp = new Vec3(-aimDir.z, 0.0D, aimDir.x).normalize();
        if (mob.getRandom().nextBoolean()) perp = perp.scale(-1.0D);
        evadeDestination = mob.position().add(perp.scale(EVADE_DISTANCE));
    }

    // ===== 弹射物闪避 =====
    private void computeProjectileEvade(Projectile proj) {
        Vec3 projVel = proj.getDeltaMovement().normalize();
        Vec3 perp = new Vec3(-projVel.z, 0.0D, projVel.x).normalize();
        Vec3 toMob = mob.position().subtract(proj.position());
        if (toMob.dot(perp) < 0) perp = perp.scale(-1.0D);
        evadeDestination = mob.position().add(perp.scale(EVADE_DISTANCE));
    }

    // ===== 瞄准检测 =====
    private Player findAimingPlayer() {
        List<Player> players = mob.level().getEntitiesOfClass(
                Player.class,
                mob.getBoundingBox().inflate(AIM_DETECT_RANGE),
                p -> p.isAlive() && !p.isCreative() && !p.isSpectator()
        );
        for (Player p : players) {
            if (isAimingAt(p)) return p;
        }
        return null;
    }

    private boolean isAimingAt(Player player) {
        double dist = player.distanceTo(mob);
        if (dist > AIM_DETECT_RANGE) return false;

        Vec3 lookDir = player.getLookAngle().normalize();
        Vec3 toMob = mob.position().add(0.0D, mob.getBbHeight() / 2.0D, 0.0D)
                .subtract(player.getEyePosition()).normalize();
        double dot = lookDir.dot(toMob);
        if (dot < Math.cos(Math.toRadians(15.0D))) return false;

        var mainHand = player.getMainHandItem();
        return mainHand.getItem() instanceof net.minecraft.world.item.BowItem
                || mainHand.getItem() instanceof net.minecraft.world.item.CrossbowItem
                || mainHand.getItem() instanceof net.minecraft.world.item.TridentItem;
    }

    // ===== 弹射物检测 =====
    private Projectile findIncomingProjectile() {
        AABB scanArea = mob.getBoundingBox().inflate(PROJECTILE_SCAN_RADIUS);
        List<Projectile> projectiles = mob.level().getEntitiesOfClass(
                Projectile.class, scanArea,
                p -> p.isAlive() && p.getOwner() != mob
        );
        for (Projectile p : projectiles) {
            Vec3 vel = p.getDeltaMovement();
            if (vel.lengthSqr() < 0.01D) continue;
            Vec3 toMob = mob.position().subtract(p.position());
            if (vel.normalize().dot(toMob.normalize()) > 0.7D) {
                return p;
            }
        }
        return null;
    }
}
