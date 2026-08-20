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
 * 闪避 Goal（轮5）。
 * 触发条件：受击 / 被瞄准 / 弹射物来袭。
 * 闪避动作：受击→后撤+侧向、瞄准→横向侧跳、弹射物→垂直规避。
 * 冷却 30 tick 防止无限闪避。priority=2，高于所有走位/攻击。
 */
public class EvadeGoal extends Goal {
    private final PathfinderMob mob;
    private int cooldown = 0;
    private Vec3 evadeDestination = Vec3.ZERO;
    private TriggerReason lastTrigger = TriggerReason.NONE;

    private static final int COOLDOWN_TICKS = 30;
    private static final double AIM_DETECT_RANGE = 12.0D;
    private static final double PROJECTILE_SCAN_RADIUS = 4.0D;
    private static final double EVADE_DISTANCE = 3.0D;

    private enum TriggerReason { NONE, HURT, AIMED, PROJECTILE }

    public EvadeGoal(PathfinderMob mob) {
        this.mob = mob;
        this.setFlags(EnumSet.of(Flag.MOVE));
    }

    @Override
    public boolean canUse() {
        if (!SmartHordeConfig.EVADE.get()) return false;
        if (cooldown > 0) { cooldown--; return false; }

        // 1. 受击检测：hurtTime > 0 表示刚受击（受击后递减）
        if (mob.hurtTime > 0) {
            lastTrigger = TriggerReason.HURT;
            computeHurtEvade();
            return true;
        }

        // 2. 瞄准检测
        Player aimer = findAimingPlayer();
        if (aimer != null) {
            lastTrigger = TriggerReason.AIMED;
            computeAimEvade(aimer);
            return true;
        }

        // 3. 弹射物检测
        Projectile incoming = findIncomingProjectile();
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
        if (!mob.getNavigation().isInProgress()) return false;
        return mob.distanceToSqr(evadeDestination) > 1.0D;
    }

    @Override
    public void stop() {
        mob.getNavigation().stop();
        cooldown = dev.smarthorde.config.DifficultyManager.get().getEvadeCooldownTicks();
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
