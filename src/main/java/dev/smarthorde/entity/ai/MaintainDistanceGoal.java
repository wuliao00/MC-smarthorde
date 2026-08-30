package dev.smarthorde.entity.ai;

import dev.smarthorde.config.SmartHordeConfig;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.phys.Vec3;

import java.util.EnumSet;

/**
 * 距离管理（轮4）。
 * 太近后撤、太远逼近、被风筝时斜向切入。
 */
public class MaintainDistanceGoal extends Goal {
    private final PathfinderMob mob;
    private static final double MIN_DIST = 1.8D;
    private static final double IDEAL_DIST = 2.8D;
    private static final double MAX_DIST = 5.0D;
    private static final double KITE_DETECT_DIST = 6.0D;
    private int tickCounter = 0;
    private Vec3 lastTargetPos = Vec3.ZERO;
    private boolean wasKiting = false;

    public MaintainDistanceGoal(PathfinderMob mob) {
        this.mob = mob;
        this.setFlags(EnumSet.of(Flag.MOVE));
    }

    @Override
    public boolean canUse() {
        if (!SmartHordeConfig.MAINTAIN_DISTANCE.get()) return false;
        // [F3] STACK 骑乘中让位：乘客导航无效，抢占会 stopRiding 致塔瓦解
        if (ClimbOrStackGoal.isStackRider(mob)) return false;
        LivingEntity target = mob.getTarget();
        if (target == null || !target.isAlive()) return false;
        double dist = mob.distanceTo(target);
        return dist < MIN_DIST || dist > MAX_DIST;
    }

    @Override
    public boolean canContinueToUse() {
        if (!SmartHordeConfig.MAINTAIN_DISTANCE.get()) return false;
        // [F3] STACK 骑乘中让位
        if (ClimbOrStackGoal.isStackRider(mob)) return false;
        LivingEntity target = mob.getTarget();
        if (target == null || !target.isAlive()) return false;
        double dist = mob.distanceTo(target);
        return dist < MIN_DIST - 0.3D || dist > MAX_DIST + 0.5D;
    }

    @Override
    public void tick() {
        LivingEntity target = mob.getTarget();
        if (target == null) return;
        double dist = mob.distanceTo(target);

        tickCounter++;
        if (tickCounter % 8 != 0) return;

        Vec3 dest;
        if (dist < MIN_DIST) {
            Vec3 away = mob.position().subtract(target.position()).normalize();
            dest = mob.position().add(away.scale(IDEAL_DIST - dist + 1.0D));
        } else {
            boolean kiting = isBeingKited(target);
            if (kiting && !wasKiting) {
                Vec3 toTarget = target.position().subtract(mob.position()).normalize();
                Vec3 perp = new Vec3(-toTarget.z, 0.0D, toTarget.x).normalize();
                if (mob.getRandom().nextBoolean()) perp = perp.scale(-1.0D);
                dest = target.position().add(perp.scale(2.0D)).add(toTarget.scale(-1.0D));
            } else {
                Vec3 toward = target.position().subtract(mob.position()).normalize();
                dest = mob.position().add(toward.scale(Math.min(dist - IDEAL_DIST, 4.0D)));
            }
            wasKiting = kiting;
        }

        mob.getNavigation().moveTo(dest.x, dest.y, dest.z, 1.0D);
    }

    private boolean isBeingKited(LivingEntity target) {
        double dist = mob.distanceTo(target);
        if (dist > KITE_DETECT_DIST) return false;
        Vec3 targetMove = target.position().subtract(lastTargetPos);
        lastTargetPos = target.position();
        if (targetMove.lengthSqr() < 0.01D) return false;
        Vec3 toMob = mob.position().subtract(target.position());
        return targetMove.dot(toMob) < 0;
    }

    @Override
    public void stop() {
        mob.getNavigation().stop();
        wasKiting = false;
        tickCounter = 0;
    }
}
