package dev.smarthorde.entity.ai.movement;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import java.util.EnumSet;
import java.util.List;

/**
 * 攀爬/叠罗汉翻墙：检测前方 2 格高墙 -> 贴墙跳；
 * 墙体更高不可攀爬时 -> 检测 1.5 格内同类实体 -> 骑上去叠罗汉，冷却 30 tick。
 * 遇到关闭的木门会直接拉开（铁门保持关闭），零方块破坏。
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
        if (this.mob.tickCount < this.nextUseTick || !this.mob.onGround()) {
            return false;
        }
        LivingEntity target = this.mob.getTarget();
        // 仅在有追击目标且被墙体阻挡时生效
        return target != null && target.isAlive() && wallHeightAhead() > 0;
    }

    @Override
    public void start() {
        this.nextUseTick = this.mob.tickCount + COOLDOWN_TICKS;
        // 木门优先：能开门就不撞墙（铁门音效为金属，自动排除）
        if (tryOpenDoorAhead()) {
            return;
        }
        int wallHeight = wallHeightAhead();
        if (wallHeight <= 2 && !this.mob.isPassenger()) {
            wallJump();
        } else if (this.mob.isPassenger()) {
            // 已在叠罗汉上层：脱离并向目标方向跃出
            this.mob.stopRiding();
            leapTowardsTarget(1.1);
        } else {
            tryStack();
        }
    }

    /** 2 格高墙：贴墙跳。 */
    private void wallJump() {
        Direction facing = this.mob.getMotionDirection();
        this.mob.setDeltaMovement(new Vec3(facing.getStepX() * 0.35, 0.85, facing.getStepZ() * 0.35));
        this.mob.hasImpulse = true;
    }

    /** 更高的墙：骑上 1.5 格内同类实体叠罗汉抬升高度。 */
    private void tryStack() {
        // 过滤候选者：排除自身/死亡实体、已在他人身上者（不能当坐骑）、
        // 以及已有 ≥2 个乘客者（与下方“限制两层”一致）；
        // 注意检查的是候选实体的乘客数，而非 mob 自己的（原实现此处语义错位）
        List<? extends PathfinderMob> candidates = this.mob.level().getEntitiesOfClass(this.mob.getClass(),
                this.mob.getBoundingBox().inflate(STACK_SEARCH_RANGE),
                e -> e != this.mob && e.isAlive() && !e.isPassenger() && e.getPassengers().size() < 2);
        if (candidates.isEmpty()) {
            return;
        }
        PathfinderMob mount = candidates.get(0);
        // 限制两层，防止无限叠高
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

    /** 拉开正前方的关闭木门；返回是否处理了门。 */
    private boolean tryOpenDoorAhead() {
        Direction facing = this.mob.getMotionDirection();
        BlockPos base = this.mob.blockPosition().relative(facing);
        for (BlockPos pos : List.of(base, base.above())) {
            BlockState state = this.mob.level().getBlockState(pos);
            if (state.getBlock() instanceof DoorBlock
                    && !state.getValue(DoorBlock.OPEN)
                    && state.getSoundType() == SoundType.WOOD) {
                this.mob.level().setBlock(pos, state.setValue(DoorBlock.OPEN, Boolean.TRUE), 3);
                return true;
            }
        }
        return false;
    }

    /** 返回正前方实心墙高度（0 = 无墙，>2 = 不可直接攀爬）。 */
    private int wallHeightAhead() {
        Direction facing = this.mob.getMotionDirection();
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
