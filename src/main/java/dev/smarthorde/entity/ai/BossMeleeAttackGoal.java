package dev.smarthorde.entity.ai;

import dev.smarthorde.entity.ai.combat.AttackMove;
import dev.smarthorde.entity.ai.combat.BossAttackController;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.Goal;

import java.util.EnumSet;

/**
 * Boss 专属近战攻击 Goal（轮12增强）。
 * 使用 BossAttackController 的 5 招独立招式表。
 */
public class BossMeleeAttackGoal extends Goal {

    private final Mob mob;
    private final BossAttackController controller;
    private static final double APPROACH_SPEED = 1.15D;

    public BossMeleeAttackGoal(Mob mob) {
        this.mob = mob;
        this.controller = new BossAttackController(mob);
        this.setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    public BossAttackController getController() { return controller; }

    public void setBossPhase(int phase) { controller.setBossPhase(phase); }

    @Override
    public boolean canUse() {
        LivingEntity target = mob.getTarget();
        if (target == null || !target.isAlive()) return false;
        controller.tickCooldowns();
        return mob.distanceTo(target) <= 8.0D;
    }

    @Override
    public boolean canContinueToUse() {
        LivingEntity target = mob.getTarget();
        if (target == null || !target.isAlive()) return false;
        return controller.isBusy() || mob.distanceTo(target) <= 10.0D;
    }

    @Override
    public void start() {}

    @Override
    public void tick() {
        controller.tickCooldowns();
        LivingEntity target = mob.getTarget();
        if (target == null || !target.isAlive()) { controller.abort(); return; }

        double dist = mob.distanceTo(target);

        if (controller.isBusy()) {
            mob.getLookControl().setLookAt(target);
            controller.tick();
            return;
        }

        AttackMove move = controller.selectMove(dist);
        if (move != null) {
            mob.getLookControl().setLookAt(target);
            controller.startMove(move);
        } else {
            mob.getNavigation().moveTo(target, APPROACH_SPEED);
            mob.getLookControl().setLookAt(target);
        }
    }

    @Override
    public void stop() {
        controller.abort();
        mob.getNavigation().stop();
    }
}
