package dev.smarthorde.entity.ai.movement;

import dev.smarthorde.entity.TacticalAttacker;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.phys.Vec3;

import java.util.EnumSet;

/**
 * 距离管理：目标过近且招式冷却中时保持面朝目标后撤微调身位；
 * 被风筝（目标正在后退）时斜向切入截击。
 *
 * <p>防穿模关键优化：改用 {@code MoveControl#strafe} 倒退/斜切并保持面向目标，
 * 彻底消除旧版 {@code navigation.moveTo(away)} 导致的躯干 180° 背对玩家、头部反拧穿模问题，
 * 并实时检测侧步/退步方向的墙体净空、流体与悬崖落差，杜绝侧向挤入墙体或退落悬崖。
 */
public class MaintainDistanceGoal extends Goal {

    private static final double BACKOFF_DISTANCE = 2.4;
    private static final double KITE_INTERCEPT_MAX_DISTANCE = 7.5;
    private static final int COOLDOWN_TICKS = 28;
    private static final int STRAFE_DURATION_TICKS = 14;

    private final PathfinderMob mob;
    private final double speedModifier;

    private int nextUseTick;
    private int endTick;
    private float strafeForward;
    private float strafeRight;

    public MaintainDistanceGoal(PathfinderMob mob, double speedModifier) {
        this.mob = mob;
        this.speedModifier = speedModifier;
        this.setFlags(EnumSet.of(Goal.Flag.MOVE, Goal.Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        if (this.mob.tickCount < this.nextUseTick || !this.mob.onGround()) {
            return false;
        }
        if (this.mob instanceof TacticalAttacker attacker && attacker.isExecutingAttack()) {
            return false;
        }
        LivingEntity target = this.mob.getTarget();
        if (target == null || !target.isAlive()) {
            return false;
        }
        double dist = this.mob.distanceTo(target);
        if (dist < BACKOFF_DISTANCE) {
            Vec3 away = flatten(this.mob.position().subtract(target.position()));
            return canStepDirection(away);
        }
        if (dist <= KITE_INTERCEPT_MAX_DISTANCE) {
            Vec3 away = flatten(this.mob.position().subtract(target.position()));
            return isBeingKited(target, away);
        }
        return false;
    }

    @Override
    public boolean canContinueToUse() {
        if (this.mob.tickCount >= this.endTick || !this.mob.onGround()) {
            return false;
        }
        if (this.mob instanceof TacticalAttacker attacker && attacker.isExecutingAttack()) {
            return false;
        }
        LivingEntity target = this.mob.getTarget();
        return target != null && target.isAlive();
    }

    @Override
    public void start() {
        LivingEntity target = this.mob.getTarget();
        if (target == null) {
            return;
        }
        this.mob.getNavigation().stop();
        Vec3 away = flatten(this.mob.position().subtract(target.position()));
        Vec3 toTarget = away.scale(-1.0);
        Vec3 rightAxis = new Vec3(-toTarget.z, 0, toTarget.x);
        float sideSign = chooseSafeSideSign(rightAxis);
        float speedScale = (float) Math.min(1.0, this.speedModifier);

        if (isBeingKited(target, away)) {
            this.strafeForward = 0.72F * speedScale;
            this.strafeRight = 0.58F * sideSign * speedScale;
        } else {
            this.strafeForward = -0.50F * speedScale;
            this.strafeRight = 0.32F * sideSign * speedScale;
        }
        this.endTick = this.mob.tickCount + STRAFE_DURATION_TICKS;
    }

    @Override
    public void tick() {
        LivingEntity target = this.mob.getTarget();
        if (target == null) {
            return;
        }
        this.mob.getLookControl().setLookAt(target, 30.0F, 30.0F);
        Vec3 toTarget = flatten(target.position().subtract(this.mob.position()));
        Vec3 rightAxis = new Vec3(-toTarget.z, 0, toTarget.x);
        Vec3 worldMove = toTarget.scale(this.strafeForward).add(rightAxis.scale(this.strafeRight));
        if (worldMove.lengthSqr() > 1.0E-4 && !canStepDirection(worldMove.normalize())) {
            this.endTick = this.mob.tickCount;
            return;
        }
        this.mob.getMoveControl().strafe(this.strafeForward, this.strafeRight);
    }

    @Override
    public void stop() {
        this.nextUseTick = this.mob.tickCount + COOLDOWN_TICKS;
        this.mob.setZza(0.0F);
        this.mob.setXxa(0.0F);
    }

    private float chooseSafeSideSign(Vec3 rightAxis) {
        float primary = this.mob.getRandom().nextBoolean() ? 1.0F : -1.0F;
        if (canStepDirection(rightAxis.scale(primary))) {
            return primary;
        }
        if (canStepDirection(rightAxis.scale(-primary))) {
            return -primary;
        }
        return 0.0F;
    }

    private boolean canStepDirection(Vec3 dir) {
        if (!this.mob.level().noCollision(this.mob,
                this.mob.getBoundingBox().move(dir.x * 0.65, 0.06, dir.z * 0.65))) {
            return false;
        }
        BlockPos dest = BlockPos.containing(
                this.mob.getX() + dir.x * 0.65, this.mob.getY(), this.mob.getZ() + dir.z * 0.65);
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

    /** 目标移动方向与其远离本实体的方向一致 => 正在被风筝。 */
    private boolean isBeingKited(LivingEntity target, Vec3 awayFromTarget) {
        Vec3 targetMotion = target.getDeltaMovement();
        Vec3 flatMotion = new Vec3(targetMotion.x, 0, targetMotion.z);
        return flatMotion.lengthSqr() > 0.002
                && flatten(flatMotion).dot(awayFromTarget) > 0.5;
    }

    private static Vec3 flatten(Vec3 vec) {
        Vec3 flat = new Vec3(vec.x, 0, vec.z);
        return flat.lengthSqr() < 1.0E-4 ? new Vec3(1, 0, 0) : flat.normalize();
    }
}
