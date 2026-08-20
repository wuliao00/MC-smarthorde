package dev.smarthorde.entity.ai;

import dev.smarthorde.config.SmartHordeConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.level.block.state.BlockState;

import java.util.EnumSet;
import java.util.List;

/**
 * 攀爬 + 叠罗汉翻墙（轮4）。合一 Goal，内部互斥切换。
 * 绝不挖墙、绝不垫方块。
 */
public class ClimbOrStackGoal extends Goal {
    private final PathfinderMob mob;
    private Mode activeMode = Mode.NONE;
    private int recheckCooldown = 0;
    private static final int RECHECK_INTERVAL = 10;
    private static final double STACK_SEARCH_RADIUS = 3.0D;
    private static final double CLIMB_WALL_DIST = 0.6D;

    private enum Mode { NONE, CLIMB, STACK }

    public ClimbOrStackGoal(PathfinderMob mob) {
        this.mob = mob;
        this.setFlags(EnumSet.of(Flag.MOVE, Flag.JUMP));
    }

    @Override
    public boolean canUse() {
        if (recheckCooldown > 0) { recheckCooldown--; return false; }
        recheckCooldown = RECHECK_INTERVAL;

        LivingEntity target = mob.getTarget();
        if (target == null || !target.isAlive()) return false;

        double heightDiff = target.getY() - mob.getY();
        if (heightDiff < 1.5D) return false;

        double hDist = Math.sqrt(mob.distanceToSqr(target));
        if (hDist > 6.0D) return false;

        if (SmartHordeConfig.CLIMBING.get() && hasClimbableWall()) {
            activeMode = Mode.CLIMB;
            return true;
        }
        if (SmartHordeConfig.STACK_UP.get() && hasStackPartner()) {
            activeMode = Mode.STACK;
            return true;
        }
        activeMode = Mode.NONE;
        return false;
    }

    @Override
    public boolean canContinueToUse() {
        if (activeMode == Mode.NONE) return false;
        LivingEntity target = mob.getTarget();
        if (target == null || !target.isAlive()) return false;
        double heightDiff = target.getY() - mob.getY();
        if (heightDiff < 0.8D) return false;
        if (activeMode == Mode.CLIMB) return SmartHordeConfig.CLIMBING.get() && hasClimbableWall();
        if (activeMode == Mode.STACK) return SmartHordeConfig.STACK_UP.get() && hasStackPartner();
        return false;
    }

    @Override
    public void tick() {
        LivingEntity target = mob.getTarget();
        if (target == null) return;

        if (activeMode == Mode.CLIMB) {
            mob.getJumpControl().jump();
            mob.getNavigation().moveTo(target, 1.0D);
        } else if (activeMode == Mode.STACK) {
            Mob partner = findNearestStackPartner();
            if (partner != null && !mob.isPassenger()) {
                mob.startRiding(partner, true);
            }
            if (mob.isPassenger()) {
                mob.getNavigation().moveTo(target, 1.0D);
            }
        }
    }

    @Override
    public void stop() {
        activeMode = Mode.NONE;
        if (mob.isPassenger()) {
            mob.stopRiding();
        }
        mob.getNavigation().stop();
    }

    // ===== 攀爬检测 =====
    private boolean hasClimbableWall() {
        BlockPos mobPos = mob.blockPosition();
        for (Direction dir : Direction.Plane.HORIZONTAL) {
            BlockPos adjacent = mobPos.relative(dir);
            BlockState state = mob.level().getBlockState(adjacent);
            if (state.isSolidRender(mob.level(), adjacent)) {
                double wallDist = switch (dir) {
                    case NORTH -> Math.abs(mob.getZ() - (adjacent.getZ() + 1.0D));
                    case SOUTH -> Math.abs(mob.getZ() - adjacent.getZ());
                    case WEST  -> Math.abs(mob.getX() - (adjacent.getX() + 1.0D));
                    case EAST  -> Math.abs(mob.getX() - adjacent.getX());
                    default -> Double.MAX_VALUE;
                };
                if (wallDist <= CLIMB_WALL_DIST) return true;
            }
        }
        return false;
    }

    // ===== 叠罗汉检测 =====
    private boolean hasStackPartner() {
        return findNearestStackPartner() != null;
    }

    private Mob findNearestStackPartner() {
        List<? extends Mob> nearby = mob.level().getEntitiesOfClass(
                Mob.class,
                mob.getBoundingBox().inflate(STACK_SEARCH_RADIUS),
                e -> e != mob && e.isAlive() && e.getType() == mob.getType()
                        && !e.isPassenger() && !e.isVehicle()
        );
        Mob best = null;
        double bestDist = Double.MAX_VALUE;
        for (Mob e : nearby) {
            double d = mob.distanceToSqr(e);
            if (d < bestDist) {
                bestDist = d;
                best = e;
            }
        }
        return best;
    }
}
