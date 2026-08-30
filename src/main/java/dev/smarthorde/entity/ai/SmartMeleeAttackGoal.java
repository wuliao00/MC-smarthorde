package dev.smarthorde.entity.ai;

import dev.smarthorde.entity.ai.combat.AttackController;
import dev.smarthorde.entity.ai.combat.AttackMove;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;

import java.util.EnumSet;

/**
 * 招式化近战攻击 Goal（轮3+10）。
 * 接受任何实现 IAttackMob 接口的 Mob（SmartZombie / HordeBoss）。
 */
public class SmartMeleeAttackGoal extends Goal {

    private final Mob mob;
    private final IAttackMob attackMob;
    private final AttackController controller;
    private static final double APPROACH_SPEED = 1.15D;

    public SmartMeleeAttackGoal(Mob mob) {
        this.mob = mob;
        this.attackMob = (mob instanceof IAttackMob am) ? am : null;
        this.controller = new AttackController(mob);
        this.setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    public AttackController getController() {
        return controller;
    }

    @Override
    public boolean canUse() {
        // [F3] STACK 骑乘中让位：乘客导航无效，近战抢占会 stopRiding 致塔瓦解
        if (ClimbOrStackGoal.isStackRider(this.mob)) return false;
        LivingEntity target = this.mob.getTarget();
        if (target == null || !target.isAlive()) return false;
        controller.tickCooldowns();
        double dist = this.mob.distanceTo(target);
        return dist <= 8.0D;
    }

    @Override
    public boolean canContinueToUse() {
        // [F3] STACK 骑乘中让位（防抢占致塔瓦解）
        if (ClimbOrStackGoal.isStackRider(this.mob)) return false;
        LivingEntity target = this.mob.getTarget();
        if (target == null || !target.isAlive()) return false;
        return controller.isBusy() || this.mob.distanceTo(target) <= 10.0D;
    }

    @Override
    public void start() {
    }

    @Override
    public void tick() {
        controller.tickCooldowns();
        LivingEntity target = this.mob.getTarget();
        if (target == null || !target.isAlive()) {
            controller.abort();
            return;
        }

        double dist = this.mob.distanceTo(target);

        if (controller.isBusy()) {
            this.mob.getLookControl().setLookAt(target);
            controller.tick();
            return;
        }

        AttackMove move = controller.selectMove(dist);
        if (move != null) {
            this.mob.getLookControl().setLookAt(target);
            controller.startMove(move);
        } else {
            this.mob.getNavigation().moveTo(target, APPROACH_SPEED);
            this.mob.getLookControl().setLookAt(target);
        }
    }

    @Override
    public void stop() {
        controller.abort();
        this.mob.getNavigation().stop();
    }
}
