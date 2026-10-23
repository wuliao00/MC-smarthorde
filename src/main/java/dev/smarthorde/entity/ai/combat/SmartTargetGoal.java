package dev.smarthorde.entity.ai.combat;

import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.target.TargetGoal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;

import javax.annotation.Nullable;
import java.util.List;

/**
 * 智能目标选择：优先攻击 距离最近 + 血量最低 + 无护甲 的玩家。
 * 综合评分 = 距离 + 血量 x2 + 护甲值 x8（无直视视线但在近距感知范围内附加遮挡惩罚），取分值最低者。
 * 同时用于 SmartZombie、HordeBoss 与被注入的原版僵尸（VanillaMobEnhancer）。
 */
public class SmartTargetGoal extends TargetGoal {

    private static final double BLIND_SENSE_RANGE_SQR = 20.0 * 20.0;
    private static final double OCCLUSION_PENALTY = 16.0;

    private final PathfinderMob mob;

    @Nullable
    private Player target;

    public SmartTargetGoal(PathfinderMob mob) {
        super(mob, false, false);
        this.mob = mob;
        this.setUnseenMemoryTicks(160);
    }

    @Override
    public boolean canUse() {
        // 随机节流：约每 10 tick 执行一次目标扫描；已锁定目标由 TargetGoal#canContinueToUse 维持
        if (this.mob.getRandom().nextInt(10) != 0) {
            return false;
        }
        double range = this.mob.getAttributeValue(Attributes.FOLLOW_RANGE);
        AABB searchBox = this.mob.getBoundingBox().inflate(range, range / 2, range);
        List<Player> candidates = this.mob.level().getEntitiesOfClass(Player.class, searchBox,
                p -> p.isAlive() && !p.isSpectator() && !p.isCreative() && !p.isDeadOrDying());

        Player best = null;
        double bestScore = Double.MAX_VALUE;
        for (Player player : candidates) {
            double distSqr = this.mob.distanceToSqr(player);
            boolean hasLos = this.mob.getSensing().hasLineOfSight(player);
            if (!hasLos && distSqr > BLIND_SENSE_RANGE_SQR) {
                continue;
            }
            double distance = Math.sqrt(distSqr);
            double score = distance
                    + player.getHealth() * 2.0
                    + player.getArmorValue() * 8.0
                    + (hasLos ? 0.0 : OCCLUSION_PENALTY);
            if (score < bestScore) {
                bestScore = score;
                best = player;
            }
        }
        if (best == null) {
            return false;
        }
        this.target = best;
        return true;
    }

    @Override
    public void start() {
        this.mob.setTarget(this.target);
        super.start();
    }

    @Override
    public void stop() {
        this.target = null;
        super.stop();
    }
}
