package dev.smarthorde.entity.ai.movement;

import dev.smarthorde.entity.SmartZombie;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;
import java.util.EnumSet;
import java.util.List;

/**
 * 攀墙与开门系统：
 * <ul>
 *   <li>遇到关闭的木门时调用 {@link DoorBlock#setOpen} 原子同步上下两格门板并播放开门音效（铁门保持关闭，零方块破坏）；</li>
 *   <li>支持圆石墙、栅栏、楼梯及实体墙攀爬（上限 6 格），校验头顶净空防止头颅顶穿屋檐，
 *       并在被击退脱离墙面超过 5 tick 时立即终止攀爬，彻底消除半空悬停爬空气 Bug；</li>
 *   <li>爬至墙顶边缘时施加登顶前跨冲量并重置摔落高度，避免卡在墙沿抽动或无故摔伤。</li>
 * </ul>
 */
public class ClimbOrStackGoal extends Goal {

    private static final org.slf4j.Logger LOGGER = com.mojang.logging.LogUtils.getLogger();
    private static final int COOLDOWN_TICKS = 35;
    private static final int DOOR_COOLDOWN_TICKS = 20;
    private static final int MAX_CLIMB_TICKS = 150;
    private static final int MAX_DETACHED_TICKS = 5;
    private static final double MAX_CLIMB_HEIGHT = 6.0;
    private static final double CLIMB_SPEED = 0.25;
    private static final double WALL_PUSH = 0.12;
    private static final double LEDGE_VAULT_SPEED = 0.28;

    private final PathfinderMob mob;
    private int nextUseTick;
    private int nextDoorTick;
    private int climbTicks;
    private int detachedTicks;
    private double climbStartY;
    private boolean climbing;
    @Nullable
    private Direction climbFacing;

    public ClimbOrStackGoal(PathfinderMob mob) {
        this.mob = mob;
        this.setFlags(EnumSet.of(Goal.Flag.MOVE, Goal.Flag.JUMP));
    }

    @Override
    public boolean canUse() {
        if (this.climbing) {
            return false;
        }
        LivingEntity target = this.mob.getTarget();
        if (target == null || !target.isAlive()) {
            return false;
        }
        Direction facing = facing(target);
        // 优先处理正前方关闭的木门（平地追击与仰攻均生效）
        if (this.mob.tickCount >= this.nextDoorTick && tryOpenDoorAhead(facing)) {
            this.nextDoorTick = this.mob.tickCount + DOOR_COOLDOWN_TICKS;
            return false;
        }
        if (this.mob.tickCount < this.nextUseTick) {
            return false;
        }
        if (target.getY() <= this.mob.getY() + 1.25) {
            return false;
        }
        if (!this.mob.onGround() || !this.mob.horizontalCollision) {
            return false;
        }
        if (hasOverheadCeiling()) {
            return false;
        }
        return wallHeightAhead(facing) >= 1;
    }

    @Override
    public void start() {
        this.climbing = true;
        this.climbTicks = 0;
        this.detachedTicks = 0;
        this.climbStartY = this.mob.getY();
        this.climbFacing = facing(this.mob.getTarget());
        syncClimbing(true);
        LOGGER.debug("[ClimbStack] {} starts wall-climbing", this.mob.getName().getString());
    }

    @Override
    public boolean canContinueToUse() {
        if (!this.climbing) {
            return false;
        }
        LivingEntity target = this.mob.getTarget();
        if (target == null || !target.isAlive()) {
            return false;
        }
        if (this.detachedTicks > MAX_DETACHED_TICKS) {
            return false;
        }
        if (hasOverheadCeiling()) {
            return false;
        }
        if (this.mob.onGround() && this.climbTicks > 8 && target.getY() <= this.mob.getY() + 1.2) {
            return false;
        }
        return this.climbTicks < MAX_CLIMB_TICKS
                && this.mob.getY() - this.climbStartY < MAX_CLIMB_HEIGHT;
    }

    @Override
    public void tick() {
        LivingEntity target = this.mob.getTarget();
        if (target == null) {
            this.climbing = false;
            return;
        }
        this.climbTicks++;
        this.mob.resetFallDistance();
        this.mob.getNavigation().stop();

        Direction facing = this.climbFacing != null ? this.climbFacing : facing(target);
        if (this.mob.horizontalCollision) {
            this.detachedTicks = 0;
        } else {
            this.detachedTicks++;
        }

        if (this.mob.tickCount >= this.nextDoorTick && tryOpenDoorAhead(facing)) {
            this.nextDoorTick = this.mob.tickCount + DOOR_COOLDOWN_TICKS;
        }

        // 攀爬中保持身体正对墙面，确保手臂自然贴墙而非斜插进墙角
        float wallYaw = facing.toYRot();
        this.mob.setYRot(wallYaw);
        this.mob.setYBodyRot(wallYaw);
        this.mob.setYHeadRot(wallYaw);

        int remainingWall = wallHeightAhead(facing);
        if (remainingWall == 0) {
            // 已抵达墙顶边缘：向前跨上平台，结束攀爬
            Vec3 vault = new Vec3(facing.getStepX() * LEDGE_VAULT_SPEED, 0.18, facing.getStepZ() * LEDGE_VAULT_SPEED);
            this.mob.setDeltaMovement(vault);
            this.mob.hasImpulse = true;
            this.climbing = false;
            return;
        }

        Vec3 push = new Vec3(facing.getStepX() * WALL_PUSH, CLIMB_SPEED, facing.getStepZ() * WALL_PUSH);
        this.mob.setDeltaMovement(push);
        this.mob.hasImpulse = true;
    }

    @Override
    public void stop() {
        this.climbing = false;
        this.climbFacing = null;
        this.detachedTicks = 0;
        this.mob.resetFallDistance();
        syncClimbing(false);
        this.nextUseTick = this.mob.tickCount + COOLDOWN_TICKS;
    }

    private void syncClimbing(boolean active) {
        if (this.mob instanceof SmartZombie smartZombie) {
            smartZombie.setWallClimbing(active);
        }
    }

    private boolean hasOverheadCeiling() {
        BlockPos headAbove = BlockPos.containing(
                this.mob.getX(), this.mob.getY() + this.mob.getBbHeight() + 0.15, this.mob.getZ());
        BlockState state = this.mob.level().getBlockState(headAbove);
        return !state.getCollisionShape(this.mob.level(), headAbove).isEmpty();
    }

    private Direction facing(@Nullable LivingEntity target) {
        Vec3 reference = target != null
                ? target.position().subtract(this.mob.position())
                : this.mob.getDeltaMovement();
        Vec3 flat = new Vec3(reference.x, 0, reference.z);
        if (flat.lengthSqr() < 1.0E-4) {
            return this.mob.getDirection();
        }
        return Direction.getNearest(flat.x, 0, flat.z);
    }

    /** 拉开正前方关闭的木门（使用 DoorBlock#setOpen 同步双格门板与音效，零方块破坏）。 */
    private boolean tryOpenDoorAhead(Direction facing) {
        BlockPos pos = findClosedWoodDoor(facing);
        if (pos == null) {
            return false;
        }
        BlockState state = this.mob.level().getBlockState(pos);
        if (state.getBlock() instanceof DoorBlock doorBlock) {
            doorBlock.setOpen(this.mob, this.mob.level(), state, pos, true);
            this.mob.swing(InteractionHand.MAIN_HAND);
            return true;
        }
        return false;
    }

    @Nullable
    private BlockPos findClosedWoodDoor(Direction facing) {
        BlockPos current = this.mob.blockPosition();
        BlockPos ahead = current.relative(facing);
        for (BlockPos pos : List.of(current, current.above(), ahead, ahead.above())) {
            BlockState state = this.mob.level().getBlockState(pos);
            if (state.getBlock() instanceof DoorBlock doorBlock
                    && !state.getValue(DoorBlock.OPEN)
                    && doorBlock.type().canOpenByHand()) {
                return pos;
            }
        }
        return null;
    }

    private int wallHeightAhead(Direction facing) {
        BlockPos wallBase = this.mob.blockPosition().relative(facing);
        int height = 0;
        while (height < 4) {
            BlockPos pos = wallBase.above(height);
            BlockState state = this.mob.level().getBlockState(pos);
            if (state.getBlock() instanceof DoorBlock && state.getValue(DoorBlock.OPEN)) {
                break;
            }
            if (state.getCollisionShape(this.mob.level(), pos).isEmpty()) {
                break;
            }
            height++;
        }
        return height;
    }
}
