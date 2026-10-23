package dev.smarthorde.entity.ai.movement;

import dev.smarthorde.entity.EliteRole;
import dev.smarthorde.entity.SmartZombie;
import dev.smarthorde.entity.TacticalAttacker;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.phys.Vec3;

import java.util.EnumSet;
import java.util.List;

/**
 * 绕侧包抄：统计目标左右两侧友军密度，优先向人数更少的一侧 90° 侧翼包抄，
 * 自动形成合围阵型，且校验落点无方块阻挡防止撞墙卡模。
 */
public class FlankGoal extends Goal {

    private static final int COOLDOWN_TICKS = 55;
    private static final double MIN_DISTANCE = 3.8;
    private static final double MAX_DISTANCE = 13.0;
    private static final double FLANK_OFFSET = 2.8;
    private static final int TIMEOUT_TICKS = 55;

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
        if (this.mob instanceof TacticalAttacker attacker && attacker.isExecutingAttack()) {
            return false;
        }
        LivingEntity target = this.mob.getTarget();
        if (target == null || !target.isAlive()) {
            return false;
        }
        double distance = this.mob.distanceTo(target);
        if (distance < MIN_DISTANCE || distance > MAX_DISTANCE) {
            return false;
        }
        // 突袭者（RAIDER）更积极绕侧包抄
        int chanceBound = (this.mob instanceof SmartZombie sz && sz.getEliteRole() == EliteRole.RAIDER) ? 5 : 9;
        return this.mob.getRandom().nextInt(chanceBound) == 0;
    }

    @Override
    public boolean canContinueToUse() {
        if (this.mob instanceof TacticalAttacker attacker && attacker.isExecutingAttack()) {
            return false;
        }
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
        Vec3 leftDir = new Vec3(toTarget.z, 0, -toTarget.x).normalize();
        Vec3 rightDir = new Vec3(-toTarget.z, 0, toTarget.x).normalize();

        boolean preferLeft = chooseLessCrowdedSide(target, leftDir);
        Vec3 primary = preferLeft ? leftDir : rightDir;
        Vec3 secondary = preferLeft ? rightDir : leftDir;

        Vec3 dest = target.position().add(primary.scale(FLANK_OFFSET));
        if (!isWalkableSpot(dest)) {
            dest = target.position().add(secondary.scale(FLANK_OFFSET));
            if (!isWalkableSpot(dest)) {
                this.timeoutTick = this.mob.tickCount;
                return;
            }
        }
        this.mob.getNavigation().moveTo(dest.x, target.getY(), dest.z, this.speedModifier);
        this.timeoutTick = this.mob.tickCount + TIMEOUT_TICKS;
    }

    @Override
    public void stop() {
        int cd = (this.mob instanceof SmartZombie sz && sz.getEliteRole() == EliteRole.RAIDER)
                ? COOLDOWN_TICKS / 2 : COOLDOWN_TICKS;
        this.nextUseTick = this.mob.tickCount + cd;
        this.mob.getNavigation().stop();
    }

    /** 评估目标左右两侧的僵尸分布，选择较空旷一侧实现双翼合围。 */
    private boolean chooseLessCrowdedSide(LivingEntity target, Vec3 leftDir) {
        List<Zombie> allies = this.mob.level().getEntitiesOfClass(Zombie.class,
                target.getBoundingBox().inflate(6.0, 2.5, 6.0),
                z -> z != this.mob && z.isAlive());
        int leftCount = 0;
        int rightCount = 0;
        for (Zombie ally : allies) {
            Vec3 rel = ally.position().subtract(target.position());
            if (rel.dot(leftDir) >= 0.0) {
                leftCount++;
            } else {
                rightCount++;
            }
        }
        if (leftCount == rightCount) {
            return this.mob.getRandom().nextBoolean();
        }
        return leftCount < rightCount;
    }

    private boolean isWalkableSpot(Vec3 pos) {
        BlockPos blockPos = BlockPos.containing(pos.x, pos.y, pos.z);
        BlockPos headPos = blockPos.above();
        if (!this.mob.level().getBlockState(blockPos).getCollisionShape(this.mob.level(), blockPos).isEmpty()
                || !this.mob.level().getBlockState(headPos).getCollisionShape(this.mob.level(), headPos).isEmpty()
                || !this.mob.level().getFluidState(blockPos).isEmpty()
                || !this.mob.level().getFluidState(headPos).isEmpty()) {
            return false;
        }
        for (int down = 1; down <= 2; down++) {
            BlockPos below = blockPos.below(down);
            if (!this.mob.level().getFluidState(below).isEmpty()) {
                return false;
            }
            if (!this.mob.level().getBlockState(below).getCollisionShape(this.mob.level(), below).isEmpty()) {
                return true;
            }
        }
        return false;
    }

    private static Vec3 flatten(Vec3 vec) {
        Vec3 flat = new Vec3(vec.x, 0, vec.z);
        return flat.lengthSqr() < 1.0E-4 ? new Vec3(1, 0, 0) : flat.normalize();
    }
}
