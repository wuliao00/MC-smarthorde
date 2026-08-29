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
import java.util.List;

import javax.annotation.Nullable;

/**
 * 攀爬/叠罗汉翻墙：检测前方 2 格高墙 -> 贴墙跳；
 * 墙体更高不可攀爬时 -> 检测 1.5 格内同类实体 -> 骑上去叠罗汉，冷却 30 tick。
 * 遇到关闭的木门会直接拉开（铁门保持关闭），零方块破坏。
 * 骑乘状态且目标在自己上方时 -> 主动脱离坐骑向目标跃出（翻上高台）。
 */
public class ClimbOrStackGoal extends Goal {

    private static final int COOLDOWN_TICKS = 30;
    private static final double STACK_SEARCH_RANGE = 1.5;

    private final PathfinderMob mob;
    private int nextUseTick;

    public ClimbOrStackGoal(PathfinderMob mob) {
        this.mob = mob;
        this.setFlags(EnumSet.of(Goal.Flag.MOVE, Goal.Flag.JUMP));
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
        // 骑乘状态：目标在自己上方 => 脱离并跃向高台
        if (this.mob.isPassenger()) {
            return target.getY() > this.mob.getY() + 0.5;
        }
        if (!this.mob.onGround()) {
            return false;
        }
        // 被墙挡住，或正前方有拉不开的通道（关闭的木门）
        return wallHeightAhead(facing(target)) > 0 || closedDoorAhead(facing(target));
    }

    @Override
    public void start() {
        this.nextUseTick = this.mob.tickCount + COOLDOWN_TICKS;
        LivingEntity target = this.mob.getTarget();
        Direction facing = facing(target);

        if (this.mob.isPassenger()) {
            // 已在叠罗汉上层：脱离坐骑向目标跃出
            this.mob.stopRiding();
            leapTowardsTarget(1.2);
            return;
        }
        // 木门优先：能开门就不撞墙（铁门音效为金属，自动排除）
        if (tryOpenDoorAhead(facing)) {
            return;
        }
        int wallHeight = wallHeightAhead(facing);
        if (wallHeight > 0 && wallHeight <= 2) {
            wallJump(facing);
        } else {
            tryStack();
        }
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

    /** 2 格高墙：贴墙跳。 */
    private void wallJump(Direction facing) {
        this.mob.setDeltaMovement(new Vec3(facing.getStepX() * 0.35, 0.85, facing.getStepZ() * 0.35));
        this.mob.hasImpulse = true;
    }

    /** 更高的墙：骑上 1.5 格内同类实体叠罗汉抬升高度。 */
    private void tryStack() {
        // 过滤候选者：排除自身/死亡实体、已在他人身上者（不能当坐骑）、
        // 以及已有 ≥2 个乘客者（限制两层，防止无限叠高）
        List<? extends PathfinderMob> candidates = this.mob.level().getEntitiesOfClass(this.mob.getClass(),
                this.mob.getBoundingBox().inflate(STACK_SEARCH_RANGE),
                e -> e != this.mob && e.isAlive() && !e.isPassenger() && e.getPassengers().size() < 2);
        if (candidates.isEmpty()) {
            return;
        }
        PathfinderMob mount = candidates.get(0);
        if (mount.getPassengers().size() < 2) {
            this.mob.startRiding(mount, true);
        }
    }

    private void leapTowardsTarget(double forward) {
        LivingEntity target = this.mob.getTarget();
        if (target == null) {
            return;
        }
        Vec3 towards = target.position().subtract(this.mob.position());
        Vec3 flat = new Vec3(towards.x, 0, towards.z);
        if (flat.lengthSqr() < 1.0E-4) {
            return;
        }
        flat = flat.normalize().scale(forward);
        this.mob.setDeltaMovement(new Vec3(flat.x, 0.75, flat.z));
        this.mob.hasImpulse = true;
    }

    /** 正前方是否存在关闭的木门。 */
    private boolean closedDoorAhead(Direction facing) {
        return findClosedDoor(facing) != null;
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
        for (BlockPos pos : List.of(base, base.above())) {
            BlockState state = this.mob.level().getBlockState(pos);
            if (state.getBlock() instanceof DoorBlock
                    && !state.getValue(DoorBlock.OPEN)
                    && state.getSoundType() == SoundType.WOOD) {
                return pos;
            }
        }
        return null;
    }

    /** 返回指定方向上的实心墙高度（0 = 无墙，>2 = 不可直接攀爬）。 */
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
