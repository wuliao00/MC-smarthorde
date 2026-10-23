package dev.smarthorde.entity.ai.movement;

import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;

/**
 * 仅在无活跃战斗目标时允许随机漫步，彻底杜绝僵尸/领主在追击或绕障空隙突然转身散步的 AI Bug。
 */
public class PeacefulStrollGoal extends WaterAvoidingRandomStrollGoal {

    public PeacefulStrollGoal(PathfinderMob mob, double speedModifier) {
        super(mob, speedModifier);
    }

    @Override
    public boolean canUse() {
        if (this.mob.getTarget() != null && this.mob.getTarget().isAlive()) {
            return false;
        }
        return super.canUse();
    }

    @Override
    public boolean canContinueToUse() {
        if (this.mob.getTarget() != null && this.mob.getTarget().isAlive()) {
            return false;
        }
        return super.canContinueToUse();
    }

    @Override
    public void start() {
        if (this.mob.isAggressive()) {
            this.mob.setAggressive(false);
        }
        super.start();
    }
}
