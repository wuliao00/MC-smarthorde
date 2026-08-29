package dev.smarthorde.entity.ai.movement;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import java.util.EnumSet;

import javax.annotation.Nullable;

/**
 * 攀墙系统（蜘蛛式攀爬）：目标在上方且面前有实体墙时，僵尸贴墙垂直攀爬，
 * 任意单只僵尸可独立翻越高墙（上限 6 格，防止基地防守被无限破）。爬墙期间
 * 原版僵尸自带攀爬动画（horizontalCollision 触发 climbing 姿态）。
 * 遇到关闭的木门会直接拉开（铁门保持关闭），零方块破坏。
 */
public class ClimbOrStackGoal extends Goal {

    private static final org.slf4j.Logger LOGGER = com.mojang.logging.LogUtils.getLogger();
    private static final int COOLDOWN_TICKS = 40;
    private static final int MAX_CLIMB_TICKS = 160;
    private static final double MAX_CLIMB_HEIGHT = 6.0;
    private static final double CLIMB_SPEED = 0.28;
    private static final double WALL_PUSH = 0.18;

    private final PathfinderMob mob;
    private int nextUseTick;
    private int climbTicks;
    private double climbStartY;
    private boolean climbing;

    public ClimbOrStackGoal(PathfinderMob mob) {
        this.mob = mob;
        this.setFlags(EnumSet.of(Goal.Flag.MOVE, Goal.Flag.JUMP));
    }

    /** 目标在上方、自身在地面、面前有实体墙 => 开始攀爬。 */
    @Override
    public boolean canUse() {
        if (this.mob.tickCount < this.nextUseTick || this.climbing) {
            return false;
        }
        LivingEntity target = this.mob.getTarget();
        if (target == null || !target.isAlive()) {
            return false;
        }
        if (target.getY() <= this.mob.getY() + 1.2) {
            return false; // 目标不在上方，交给其他战术目标
        }
        if (!this.mob.onGround()) {
            return false;
        }
        return wallHeightAhead(facing(target)) >= 1;
    }

    @Override
    public void start() {
        this.climbing = true;
        this.climbTicks = 0;
        this.climbStartY = this.mob.getY();
        LOGGER.info("[ClimbStack] {} starts wall-climbing", this.mob.getName().getString());
    }

    /** 攀爬进行中：每 tick 贴墙向上。 */
    @Override
    public boolean canContinueToUse() {
        if (!this.climbing) {
            return false;
        }
        LivingEntity target = this.mob.getTarget();
        if (target == null || !target.isAlive()) {
            return false;
        }
        // 已翻上顶部：目标不再显著高于自身 => 完成
        if (this.mob.onGround() && target.getY() <= this.mob.getY() + 1.2) {
            return false;
        }
        return this.climbTicks < MAX_CLIMB_TICKS
                && this.mob.getY() - this.climbStartY < MAX_CLIMB_HEIGHT;
    }

    @Override
    public void tick() {
        LivingEntity target = this.mob.getTarget();
        if (target == null) {
            return;
        }
        this.climbTicks++;
        Direction facing = facing(target);

        // 关键：停掉寻路导航，否则 MoveControl 每 tick 覆盖 deltaMovement，攀爬推力无效
        this.mob.getNavigation().stop();

        // 朝向目标并压向墙面
        float wanted = (float) (Math.atan2(
                target.getZ() - this.mob.getZ(), target.getX() - this.mob.getX()) * (180.0 / Math.PI)) - 90.0F;
        this.mob.setYRot(wanted);
        this.mob.setYHeadRot(wanted);

        Vec3 push = new Vec3(facing.getStepX() * WALL_PUSH, CLIMB_SPEED, facing.getStepZ() * WALL_PUSH);
        this.mob.setDeltaMovement(push);
        this.mob.hasImpulse = true;
    }

    @Override
    public void stop() {
        if (this.climbing) {
            LOGGER.info("[ClimbStack] {} climb ended at dy={} (ticks={})",
                    this.mob.getName().getString(),
                    String.format("%.1f", this.mob.getY() - this.climbStartY), this.climbTicks);
        }
        this.climbing = false;
        this.nextUseTick = this.mob.tickCount + COOLDOWN_TICKS;
    }

    /** 朝向取目标方向（战斗中几乎总是面朝目标）；无目标时退回运动方向。 */
    private Direction facing(@Nullable LivingEntity target) {
        Vec3 reference;
        if (target != null) {
            reference = target.position().subtract(this.mob.position());
        } else {
            reference = this.mob.getDeltaMovement();
        }
        Vec3 flat = new Vec3(reference.x, 0, reference.z);
        if (flat.lengthSqr() < 1.0E-4) {
            return this.mob.getMotionDirection();
        }
        return Direction.getNearest(flat.x, 0, flat.z);
    }

    /** 拉开正前方的关闭木门；返回是否处理了门。 */
    private boolean tryOpenDoorAhead(Direction facing) {
        BlockPos pos = findClosedDoor(facing);
        if (pos == null) {
            return false;
        }
        BlockState state = this.mob.level().getBlockState(pos);
        this.mob.level().setBlock(pos, state.setValue(DoorBlock.OPEN, Boolean.TRUE), 3);
        return true;
    }

    @Nullable
    private BlockPos findClosedDoor(Direction facing) {
        BlockPos base = this.mob.blockPosition().relative(facing);
        for (BlockPos pos : java.util.List.of(base, base.above())) {
            BlockState state = this.mob.level().getBlockState(pos);
            if (state.getBlock() instanceof DoorBlock
                    && !state.getValue(DoorBlock.OPEN)
                    && state.getSoundType() == SoundType.WOOD) {
                return pos;
            }
        }
        return null;
    }

    /** 返回指定方向上的实心墙高度（0 = 无墙）。 */
    private int wallHeightAhead(Direction facing) {
        BlockPos wallBase = this.mob.blockPosition().relative(facing);
        int height = 0;
        while (height < 4) {
            BlockPos pos = wallBase.above(height);
            BlockState state = this.mob.level().getBlockState(pos);
            if (!state.isCollisionShapeFullBlock(this.mob.level(), pos)) {
                break;
            }
            height++;
        }
        return height;
    }
}
