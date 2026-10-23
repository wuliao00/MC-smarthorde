package dev.smarthorde.entity.ai.movement;

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
 * 分离防挤团：检测周围全部僵尸类友军，过近时横向散开形成包围圈。
 *
 * <p>防叠模/防扭头/防撞墙优化：
 * <ul>
 *   <li>扫描范围覆盖 {@link Zombie} 全子类（智能僵尸与增强原版僵尸互相避让，不再叠成同一坐标）；</li>
 *   <li>近战交战距离内采用面向目标的横向 {@code strafe} 散开，并校验墙体碰撞与悬崖净空，防止肩膀挤压进墙面或跌落深渊；</li>
 *   <li>出招期间不抢占移动控制。</li>
 * </ul>
 */
public class SeparationGoal extends Goal {

    private static final double SEPARATION_DISTANCE = 1.75;
    private static final double COMBAT_STRAFE_RANGE_SQR = 8.0 * 8.0;
    private static final int COOLDOWN_TICKS = 14;
    private static final int MOVE_TIMEOUT_TICKS = 14;

    private final PathfinderMob mob;
    private final double speedModifier;

    private int nextCheckTick;
    private int timeoutTick;
    private boolean useCombatStrafe;
    private float strafeRight;

    public SeparationGoal(PathfinderMob mob) {
        this(mob, 1.05);
    }

    public SeparationGoal(PathfinderMob mob, double speedModifier) {
        this.mob = mob;
        this.speedModifier = speedModifier;
        this.setFlags(EnumSet.of(Goal.Flag.MOVE));
    }

    @Override
    public boolean canUse() {
        if (this.mob.tickCount < this.nextCheckTick || !this.mob.onGround()) {
            return false;
        }
        if (this.mob instanceof TacticalAttacker attacker && attacker.isExecutingAttack()) {
            return false;
        }
        this.nextCheckTick = this.mob.tickCount + COOLDOWN_TICKS;
        return findClosestNeighborSqr() < SEPARATION_DISTANCE * SEPARATION_DISTANCE;
    }

    @Override
    public boolean canContinueToUse() {
        if (this.mob.tickCount >= this.timeoutTick) {
            return false;
        }
        if (this.mob instanceof TacticalAttacker attacker && attacker.isExecutingAttack()) {
            return false;
        }
        return this.useCombatStrafe || this.mob.getNavigation().isInProgress();
    }

    @Override
    public void start() {
        Vec3 push = separationVector();
        if (push.lengthSqr() < 1.0E-4) {
            this.timeoutTick = this.mob.tickCount;
            return;
        }
        LivingEntity target = this.mob.getTarget();
        if (target != null && target.isAlive()
                && this.mob.distanceToSqr(target) <= COMBAT_STRAFE_RANGE_SQR) {
            Vec3 toTarget = flatten(target.position().subtract(this.mob.position()));
            Vec3 rightAxis = new Vec3(-toTarget.z, 0, toTarget.x);
            double lateral = push.normalize().dot(rightAxis);
            float dir = lateral >= 0.0 ? 1.0F : -1.0F;
            if (!canStepLateral(rightAxis.scale(dir))) {
                if (canStepLateral(rightAxis.scale(-dir))) {
                    dir = -dir;
                } else {
                    this.timeoutTick = this.mob.tickCount;
                    return;
                }
            }
            this.useCombatStrafe = true;
            this.mob.getNavigation().stop();
            this.strafeRight = 0.55F * dir;
        } else {
            this.useCombatStrafe = false;
            Vec3 destination = this.mob.position().add(push.normalize().scale(2.4));
            this.mob.getNavigation().moveTo(destination.x, this.mob.getY(), destination.z, this.speedModifier);
        }
        this.timeoutTick = this.mob.tickCount + MOVE_TIMEOUT_TICKS;
    }

    @Override
    public void tick() {
        if (!this.useCombatStrafe) {
            return;
        }
        LivingEntity target = this.mob.getTarget();
        if (target != null && target.isAlive()) {
            this.mob.getLookControl().setLookAt(target, 30.0F, 30.0F);
            Vec3 toTarget = flatten(target.position().subtract(this.mob.position()));
            Vec3 rightAxis = new Vec3(-toTarget.z, 0, toTarget.x);
            if (!canStepLateral(rightAxis.scale(Math.signum(this.strafeRight)))) {
                this.timeoutTick = this.mob.tickCount;
                return;
            }
        }
        this.mob.getMoveControl().strafe(0.05F, this.strafeRight);
    }

    @Override
    public void stop() {
        if (this.useCombatStrafe) {
            this.mob.setXxa(0.0F);
            this.mob.setZza(0.0F);
        } else {
            this.mob.getNavigation().stop();
        }
        this.useCombatStrafe = false;
    }

    private boolean canStepLateral(Vec3 dir) {
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

    private double findClosestNeighborSqr() {
        List<Zombie> neighbors = this.mob.level().getEntitiesOfClass(Zombie.class,
                this.mob.getBoundingBox().inflate(SEPARATION_DISTANCE, 1.0, SEPARATION_DISTANCE),
                e -> e != this.mob && e.isAlive() && !this.mob.hasPassenger(e) && !e.hasPassenger(this.mob));
        double closest = Double.MAX_VALUE;
        for (Zombie neighbor : neighbors) {
            closest = Math.min(closest, this.mob.distanceToSqr(neighbor));
        }
        return closest;
    }

    private Vec3 separationVector() {
        Vec3 push = Vec3.ZERO;
        List<Zombie> neighbors = this.mob.level().getEntitiesOfClass(Zombie.class,
                this.mob.getBoundingBox().inflate(SEPARATION_DISTANCE, 1.0, SEPARATION_DISTANCE),
                e -> e != this.mob && e.isAlive());
        for (Zombie neighbor : neighbors) {
            Vec3 away = new Vec3(this.mob.getX() - neighbor.getX(), 0, this.mob.getZ() - neighbor.getZ());
            double dist = away.length();
            if (dist < SEPARATION_DISTANCE && dist > 1.0E-4) {
                push = push.add(away.scale(1.0 / (dist * dist)));
            } else if (dist <= 1.0E-4) {
                double angle = (this.mob.getId() * 1.61803398875) * Math.PI * 2.0;
                push = push.add(Math.cos(angle), 0, Math.sin(angle));
            }
        }
        return push;
    }

    private static Vec3 flatten(Vec3 vec) {
        Vec3 flat = new Vec3(vec.x, 0, vec.z);
        return flat.lengthSqr() < 1.0E-4 ? new Vec3(1, 0, 0) : flat.normalize();
    }
}
