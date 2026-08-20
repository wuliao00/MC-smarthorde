package dev.smarthorde.entity.ai;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.projectile.Arrow;
import net.minecraft.world.entity.monster.Monster;

import java.util.EnumSet;

/**
 * 简单远程射箭 Goal（轮12新增）。靠近到射程内射箭，太远则靠近。
 */
public class ArcherAttackGoal extends Goal {
    private final PathfinderMob mob;
    private final double speedMod;
    private final float attackRadius;
    private final int cooldown;
    private int tickCounter = 0;

    public ArcherAttackGoal(PathfinderMob mob, double speedMod, float attackRadius, int cooldown) {
        this.mob = mob;
        this.speedMod = speedMod;
        this.attackRadius = attackRadius;
        this.cooldown = cooldown;
        this.setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        LivingEntity target = mob.getTarget();
        return target != null && target.isAlive();
    }

    @Override
    public boolean canContinueToUse() {
        return canUse();
    }

    @Override
    public void tick() {
        LivingEntity target = mob.getTarget();
        if (target == null || !target.isAlive()) return;
        mob.getLookControl().setLookAt(target, 30F, 30F);

        double dist = mob.distanceTo(target);
        if (dist > attackRadius) {
            mob.getNavigation().moveTo(target, speedMod);
        } else if (dist < 3.0D) {
            // 太近，后退
            double dx = mob.getX() - target.getX();
            double dz = mob.getZ() - target.getZ();
            mob.getNavigation().moveTo(mob.getX() + dx * 0.5, mob.getY(), mob.getZ() + dz * 0.5, speedMod);
        } else {
            mob.getNavigation().stop();
        }

        tickCounter++;
        if (tickCounter >= cooldown && dist <= attackRadius + 2.0) {
            shootArrow(target);
            tickCounter = 0;
        }
    }

    private void shootArrow(LivingEntity target) {
        Arrow arrow = net.minecraft.world.entity.EntityType.ARROW.create(mob.level());
        if (arrow == null) return;
        arrow.setPos(mob.getX(), mob.getY() + mob.getEyeHeight(), mob.getZ());
        arrow.setOwner(mob);
        double dx = target.getX() - mob.getX();
        double dy = target.getEyeY() - arrow.getY();
        double dz = target.getZ() - mob.getZ();
        double dist = Math.sqrt(dx * dx + dz * dz);
        arrow.shoot(dx, dy + dist * 0.2, dz, 1.6F, 2.0F);
        mob.level().addFreshEntity(arrow);
        mob.playSound(net.minecraft.sounds.SoundEvents.ARROW_SHOOT, 1.0F, 1.0F / (mob.getRandom().nextFloat() * 0.4F + 0.8F));
    }
}
