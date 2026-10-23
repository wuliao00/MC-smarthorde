package dev.smarthorde.entity.ai.defense;

import dev.smarthorde.config.DifficultyManager;
import dev.smarthorde.config.SmartHordeConfig;
import dev.smarthorde.effects.EffectManager;
import dev.smarthorde.entity.SmartZombie;
import dev.smarthorde.entity.TacticalAttacker;
import dev.smarthorde.init.ModSounds;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.item.ProjectileWeaponItem;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;
import java.util.EnumSet;
import java.util.List;

/**
 * 四源战术闪避（含地形净空与防打断优化）：
 * <ol>
 *   <li>受击后 5 tick 内 -> 向斜后安全侧冲刺；</li>
 *   <li>48 格内持远程武器拉弦瞄准的玩家 -> 横向侧跳；</li>
 *   <li>2.4~10 格内准星直指本实体的玩家（出招期间不触发，避免自行打断前摇鬼畜抽搐）-> 横向侧跳；</li>
 *   <li>8 格内飞来的箭矢 -> 垂直于弹道方向侧扑闪避。</li>
 * </ol>
 * 选定左右方向前探测 1.15 格碰撞箱净空与脚下悬崖/流体，杜绝闪避撞墙卡模或跳崖。
 */
public class DodgeGoal extends Goal {

    private static final double BOW_THREAT_RANGE = 48.0;
    private static final double STARE_MIN_RANGE_SQR = 2.4 * 2.4;
    private static final double STARE_THREAT_RANGE = 10.0;
    private static final double ARROW_THREAT_RANGE = 8.0;
    private static final double DASH_STRENGTH = 0.52;
    private static final double STARE_ANGLE_DEG = 6.0;
    private static final int DODGE_ANIM_DURATION_TICKS = 8;

    private final PathfinderMob mob;
    private int nextDodgeTick;
    @Nullable
    private Vec3 pendingDodge;

    public DodgeGoal(PathfinderMob mob) {
        this.mob = mob;
        this.setFlags(EnumSet.of(Goal.Flag.MOVE, Goal.Flag.JUMP));
    }

    @Override
    public boolean canUse() {
        if (this.mob.tickCount < this.nextDodgeTick || !this.mob.onGround()) {
            return false;
        }
        // 重装先锋举盾推进时保持盾墙稳固，不乱跳破防
        if (this.mob instanceof SmartZombie zombie && zombie.isShieldBlocking()) {
            return false;
        }
        if (this.mob.hurtTime <= 5 && this.mob.getRandom().nextInt(5) != 0) {
            return false;
        }
        this.pendingDodge = findDodgeVector();
        return this.pendingDodge != null;
    }

    @Override
    public boolean canContinueToUse() {
        return false;
    }

    @Override
    public void start() {
        Vec3 dodge = this.pendingDodge != null ? this.pendingDodge : findDodgeVector();
        this.pendingDodge = null;
        if (dodge == null) {
            return;
        }
        this.mob.setDeltaMovement(new Vec3(dodge.x * DASH_STRENGTH, 0.25, dodge.z * DASH_STRENGTH));
        this.mob.hasImpulse = true;

        int cooldown = DifficultyManager.dodgeCooldownTicks();
        if (this.mob instanceof SmartZombie smartZombie) {
            cooldown = Math.max(8, (int) Math.round(cooldown * smartZombie.getEliteRole().dodgeCooldownMul));
            smartZombie.triggerDodgeAnim(DODGE_ANIM_DURATION_TICKS);
        }
        this.nextDodgeTick = this.mob.tickCount + cooldown;

        if (this.mob.level() instanceof ServerLevel serverLevel) {
            EffectManager.spawnDodgeParticles(serverLevel, this.mob.position());
            if (SmartHordeConfig.isSoundsEnabledSafe()) {
                serverLevel.playSound(null, this.mob,
                        ModSounds.SMART_DODGE.get(), SoundSource.HOSTILE, 0.7F, 1.2F);
            }
        }
    }

    @Nullable
    private Vec3 findDodgeVector() {
        boolean executingAttack = this.mob instanceof TacticalAttacker attacker && attacker.isExecutingAttack();

        // 源 1：刚受击（非主动出招帧时向斜后撤步避让连击）
        if (this.mob.hurtTime > 5 && !executingAttack) {
            LivingEntity attacker = this.mob.getLastHurtByMob();
            Vec3 away = attacker != null
                    ? flatten(this.mob.position().subtract(attacker.position()))
                    : flatten(this.mob.getLookAngle().scale(-1));
            Vec3 candidateA = away.add(new Vec3(-away.z, 0, away.x).scale(0.65)).normalize();
            Vec3 candidateB = away.add(new Vec3(away.z, 0, -away.x).scale(0.65)).normalize();
            return pickSafeDirection(candidateA, candidateB);
        }

        // 源 4（高紧急度）：8 格内飞来的箭矢
        Vec3 arrowMotion = findIncomingArrowMotion();
        if (arrowMotion != null) {
            Vec3 perp = new Vec3(-arrowMotion.z, 0, arrowMotion.x).normalize();
            return chooseLateralPair(perp);
        }

        // 出招前摇/挥砍过程中不再因玩家瞄准而自我打断，保证招式动画连贯完整
        if (executingAttack) {
            return null;
        }

        // 源 2：48 格内正在拉弓/装填弩瞄准的玩家
        Vec3 bowThreat = findAimingArcher();
        if (bowThreat != null) {
            Vec3 away = flatten(this.mob.position().subtract(new Vec3(bowThreat.x, this.mob.getY(), bowThreat.z)));
            return chooseLateralPair(new Vec3(-away.z, 0, away.x));
        }

        // 源 3：中近距准星直指本实体（贴脸 <2.4 格时交由近战博弈，不再鬼畜侧跳）
        Vec3 stareThreat = findAimingPlayer();
        if (stareThreat != null) {
            Vec3 away = flatten(this.mob.position().subtract(new Vec3(stareThreat.x, this.mob.getY(), stareThreat.z)));
            return chooseLateralPair(new Vec3(-away.z, 0, away.x));
        }

        return null;
    }

    @Nullable
    private Vec3 chooseLateralPair(Vec3 leftPerp) {
        Vec3 norm = flatten(leftPerp);
        boolean firstLeft = this.mob.getRandom().nextBoolean();
        Vec3 first = firstLeft ? norm : norm.scale(-1);
        Vec3 second = firstLeft ? norm.scale(-1) : norm;
        return pickSafeDirection(first, second);
    }

    @Nullable
    private Vec3 pickSafeDirection(Vec3 primary, Vec3 fallback) {
        if (isSafeDashDirection(primary)) {
            return primary;
        }
        if (isSafeDashDirection(fallback)) {
            return fallback;
        }
        return null;
    }

    /** 校验冲刺方向无墙体碰撞且落点下方 2 格内有安全地面（非岩浆/深渊）。 */
    private boolean isSafeDashDirection(Vec3 dir) {
        Vec3 offset = new Vec3(dir.x * 1.15, 0.25, dir.z * 1.15);
        if (!this.mob.level().noCollision(this.mob, this.mob.getBoundingBox().move(offset))) {
            return false;
        }
        BlockPos dest = BlockPos.containing(
                this.mob.getX() + dir.x * 1.15,
                this.mob.getY(),
                this.mob.getZ() + dir.z * 1.15);
        if (!this.mob.level().getFluidState(dest).isEmpty()) {
            return false;
        }
        for (int down = 1; down <= 2; down++) {
            BlockPos below = dest.below(down);
            if (!this.mob.level().getFluidState(below).isEmpty()) {
                return false;
            }
            if (!this.mob.level().getBlockState(below).getCollisionShape(this.mob.level(), below).isEmpty()) {
                return true;
            }
        }
        return false;
    }

    @Nullable
    private Vec3 findAimingArcher() {
        List<Player> players = this.mob.level().getEntitiesOfClass(Player.class,
                this.mob.getBoundingBox().inflate(BOW_THREAT_RANGE),
                p -> p.isAlive() && !p.isSpectator() && !p.isCreative() && p.isUsingItem()
                        && (p.getMainHandItem().getItem() instanceof ProjectileWeaponItem
                        || p.getOffhandItem().getItem() instanceof ProjectileWeaponItem));
        if (players.isEmpty()) {
            return null;
        }
        return players.get(0).position();
    }

    @Nullable
    private Vec3 findAimingPlayer() {
        List<Player> players = this.mob.level().getEntitiesOfClass(Player.class,
                this.mob.getBoundingBox().inflate(STARE_THREAT_RANGE),
                p -> p.isAlive() && !p.isSpectator() && !p.isCreative()
                        && this.mob.distanceToSqr(p) >= STARE_MIN_RANGE_SQR);
        double cosLimit = Math.cos(Math.toRadians(STARE_ANGLE_DEG));
        for (Player player : players) {
            Vec3 eye = player.getEyePosition();
            Vec3 myCenter = this.mob.position().add(0, this.mob.getBbHeight() * 0.5, 0);
            Vec3 toMe = myCenter.subtract(eye);
            if (toMe.lengthSqr() < 1.0E-4) {
                continue;
            }
            if (player.getLookAngle().normalize().dot(toMe.normalize()) >= cosLimit) {
                return player.position();
            }
        }
        return null;
    }

    @Nullable
    private Vec3 findIncomingArrowMotion() {
        List<AbstractArrow> arrows = this.mob.level().getEntitiesOfClass(AbstractArrow.class,
                this.mob.getBoundingBox().inflate(ARROW_THREAT_RANGE),
                arrow -> arrow.isAlive() && !arrow.onGround() && arrow.getOwner() != this.mob);
        for (AbstractArrow arrow : arrows) {
            Vec3 motion = arrow.getDeltaMovement();
            Vec3 toMe = this.mob.position().subtract(arrow.position());
            if (motion.horizontalDistanceSqr() > 0.02 && flatten(motion).dot(flatten(toMe)) > 0.7) {
                return motion;
            }
        }
        return null;
    }

    private static Vec3 flatten(Vec3 vec) {
        Vec3 flat = new Vec3(vec.x, 0, vec.z);
        return flat.lengthSqr() < 1.0E-4 ? new Vec3(1, 0, 0) : flat.normalize();
    }
}
