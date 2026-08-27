package dev.smarthorde.entity.ai.movement;

import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.phys.Vec3;

import java.util.EnumSet;
import java.util.List;

/**
 * 分离防挤团：检测周围同类实体，距离小于 2 格时向反方向散开，冷却 10 tick。
 */
public class SeparationGoal extends Goal {

    private static final double SEPARATION_DISTANCE = 2.0;
    private static final int COOLDOWN_TICKS = 10;
    private static final int MOVE_TIMEOUT_TICKS = 20;

    private final PathfinderMob mob;
    private final double speedModifier;

    private int nextCheckTick;
    private int timeoutTick;
    private boolean separated;

    public SeparationGoal(PathfinderMob mob) {
        this(mob, 1.1);
    }

    public SeparationGoal(PathfinderMob mob, double speedModifier) {
        this.mob = mob;
        this.speedModifier = speedModifier;
        this.setFlags(EnumSet.of(Goal.Flag.MOVE));
    }

    @Override
    public boolean canUse() {
        if (this.mob.tickCount < this.nextCheckTick) {
            return false;
        }
        this.nextCheckTick = this.mob.tickCount + COOLDOWN_TICKS;
        this.separated = findNeighbors() < SEPARATION_DISTANCE * SEPARATION_DISTANCE;
        return this.separated;
    }

    @Override
    public boolean canContinueToUse() {
        return this.mob.tickCount < this.timeoutTick && this.mob.getNavigation().isInProgress();
    }

    @Override
    public void start() {
        Vec3 away = separationVector();
        if (away.lengthSqr() < 1.0E-4) {
            return;
        }
        Vec3 destination = this.mob.position().add(away.normalize().scale(3.0));
        this.mob.getNavigation().moveTo(destination.x, this.mob.getY(), destination.z, this.speedModifier);
        this.timeoutTick = this.mob.tickCount + MOVE_TIMEOUT_TICKS;
    }

    @Override
    public void stop() {
        this.mob.getNavigation().stop();
    }

    /** 返回同类实体平均位置的距离平方。 */
    private double findNeighbors() {
        List<? extends PathfinderMob> neighbors = this.mob.level().getEntitiesOfClass(this.mob.getClass(),
                this.mob.getBoundingBox().inflate(SEPARATION_DISTANCE, 1.0, SEPARATION_DISTANCE),
                e -> e != this.mob && e.isAlive() && !this.mob.hasPassenger(e));
        double closest = Double.MAX_VALUE;
        for (PathfinderMob neighbor : neighbors) {
            closest = Math.min(closest, this.mob.distanceToSqr(neighbor));
        }
        return closest;
    }

    /** 所有 2 格内同类实体的斥力合力。 */
    private Vec3 separationVector() {
        Vec3 push = Vec3.ZERO;
        List<? extends PathfinderMob> neighbors = this.mob.level().getEntitiesOfClass(this.mob.getClass(),
                this.mob.getBoundingBox().inflate(SEPARATION_DISTANCE, 1.0, SEPARATION_DISTANCE),
                e -> e != this.mob && e.isAlive());
        for (PathfinderMob neighbor : neighbors) {
            Vec3 away = this.mob.position().subtract(neighbor.position());
            double dist = away.length();
            if (dist < SEPARATION_DISTANCE && dist > 1.0E-4) {
                push = push.add(away.scale(1.0 / dist));
            }
        }
        return push;
    }
}
