package dev.smarthorde.entity.ai.movement;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.phys.Vec3;

import java.util.EnumSet;

/**
 * 距离管理：目标距离小于 3 格时后退拉开身位；
 * 被风筝（目标正在后退）时斜向切入，冷却 20 tick。
 */
public class MaintainDistanceGoal extends Goal {

    private static final double BACKOFF_DISTANCE = 3.0;
    private static final int COOLDOWN_TICKS = 20;
    private static final int MOVE_TIMEOUT_TICKS = 20;

    private final PathfinderMob mob;
    private final double speedModifier;

    private int timeoutTick;

    public MaintainDistanceGoal(PathfinderMob mob, double speedModifier) {
        this.mob = mob;
        this.speedModifier = speedModifier;
        this.setFlags(EnumSet.of(Goal.Flag.MOVE));
    }

    @Override
    public boolean canUse() {
        LivingEntity target = this.mob.getTarget();
        return target != null && target.isAlive()
                && this.mob.distanceTo(target) < BACKOFF_DISTANCE;
    }

    @Override
    public boolean canContinueToUse() {
        return this.mob.tickCount < this.timeoutTick && this.mob.getNavigation().isInProgress();
    }

    @Override
    public void start() {
        LivingEntity target = this.mob.getTarget();
        if (target == null) {
            return;
        }
        Vec3 away = flatten(this.mob.position().subtract(target.position()));

        Vec3 direction;
        if (isBeingKited(target, away)) {
            // 斜向切入：朝目标方向的 45 度侧前方移动，截断风筝走位
            Vec3 towards = flatten(target.position().subtract(this.mob.position()));
            Vec3 side = new Vec3(-towards.z, 0, towards.x).normalize().scale(0.8);
            direction = towards.add(side).normalize();
        } else {
            direction = away;
        }
        Vec3 destination = this.mob.position().add(direction.scale(3.5));
        this.mob.getNavigation().moveTo(destination.x, this.mob.getY(), destination.z, this.speedModifier);
        this.timeoutTick = this.mob.tickCount + MOVE_TIMEOUT_TICKS;
    }

    @Override
    public void stop() {
        this.mob.getNavigation().stop();
    }

    /** 目标移动方向与其远离本实体的方向一致 => 正在被风筝。 */
    private boolean isBeingKited(LivingEntity target, Vec3 awayFromTarget) {
        Vec3 targetMotion = target.getDeltaMovement();
        Vec3 flatMotion = new Vec3(targetMotion.x, 0, targetMotion.z);
        return flatMotion.lengthSqr() > 0.001
                && flatten(flatMotion).dot(awayFromTarget) > 0.5;
    }

    private static Vec3 flatten(Vec3 vec) {
        Vec3 flat = new Vec3(vec.x, 0, vec.z);
        return flat.lengthSqr() < 1.0E-4 ? new Vec3(1, 0, 0) : flat.normalize();
    }
}
