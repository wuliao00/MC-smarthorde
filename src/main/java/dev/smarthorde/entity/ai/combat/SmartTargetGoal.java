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
 * 综合评分 = 距离 + 血量 x2 + 护甲值 x8，取分值最低者。
 * 同时用于 SmartZombie 与被注入的原版僵尸（VanillaMobEnhancer）。
 */
public class SmartTargetGoal extends TargetGoal {

    private final PathfinderMob mob;

    @Nullable
    private Player target;

    public SmartTargetGoal(PathfinderMob mob) {
        super(mob, true, false);
        this.mob = mob;
    }

    @Override
    public boolean canUse() {
        // 随机节流：约每 10 tick 执行一次目标扫描（与原版 NearestAttackableTargetGoal
        // 的 randomInterval 相同模式）；已锁定目标的维持由 TargetGoal#canContinueToUse
        // 负责，不受本节流影响，目标记忆逻辑保持正确
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
            if (!this.mob.getSensing().hasLineOfSight(player)) {
                continue;
            }
            double distance = Math.sqrt(this.mob.distanceToSqr(player));
            double score = distance + player.getHealth() * 2.0 + player.getArmorValue() * 8.0;
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
