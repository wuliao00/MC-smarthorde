package dev.smarthorde.entity.ai.movement;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.phys.Vec3;

import java.util.EnumSet;

/**
 * 绕侧包抄：计算目标左侧或右侧 90 度方向点并导航过去，冷却 60 tick。
 */
public class FlankGoal extends Goal {

    private static final int COOLDOWN_TICKS = 60;
    private static final double MIN_DISTANCE = 4.0;
    private static final double MAX_DISTANCE = 12.0;
    private static final double FLANK_OFFSET = 2.5;
    private static final int TIMEOUT_TICKS = 60;

    private final PathfinderMob mob;
    private final double speedModifier;

    private int nextUseTick;
    private int timeoutTick;

    public FlankGoal(PathfinderMob mob, double speedModifier) {
        this.mob = mob;
        this.speedModifier = speedModifier;
        this.setFlags(EnumSet.of(Goal.Flag.MOVE));
    }

    @Override
    public boolean canUse() {
        if (this.mob.tickCount < this.nextUseTick) {
            return false;
        }
        LivingEntity target = this.mob.getTarget();
        if (target == null || !target.isAlive()) {
            return false;
        }
        double distance = this.mob.distanceTo(target);
        return distance >= MIN_DISTANCE && distance <= MAX_DISTANCE
                && this.mob.getRandom().nextInt(10) == 0; // 避免所有怪物同时包抄
    }

    @Override
    public boolean canContinueToUse() {
        return this.mob.tickCount < this.timeoutTick
                && this.mob.getNavigation().isInProgress()
                && this.mob.getTarget() != null
                && this.mob.getTarget().isAlive();
    }

    @Override
    public void start() {
        LivingEntity target = this.mob.getTarget();
        if (target == null) {
            return;
        }
        Vec3 toTarget = flatten(target.position().subtract(this.mob.position()));
        // 随机选择目标左侧或右侧 90 度方向点
        boolean left = this.mob.getRandom().nextBoolean();
        Vec3 flankDirection = left
                ? new Vec3(toTarget.z, 0, -toTarget.x).normalize()
                : new Vec3(-toTarget.z, 0, toTarget.x).normalize();
        Vec3 destination = target.position().add(flankDirection.scale(FLANK_OFFSET));
        this.mob.getNavigation().moveTo(destination.x, target.getY(), destination.z, this.speedModifier);
        this.timeoutTick = this.mob.tickCount + TIMEOUT_TICKS;
    }

    @Override
    public void stop() {
        this.nextUseTick = this.mob.tickCount + COOLDOWN_TICKS;
        this.mob.getNavigation().stop();
    }

    private static Vec3 flatten(Vec3 vec) {
        Vec3 flat = new Vec3(vec.x, 0, vec.z);
        return flat.lengthSqr() < 1.0E-4 ? new Vec3(1, 0, 0) : flat.normalize();
    }
}
