package dev.smarthorde.entity.ai.combat;

import dev.smarthorde.config.SmartHordeConfig;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.target.TargetGoal;
import net.minecraft.world.entity.player.Player;

import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;

/**
 * 智能目标选择（轮3）。
 * 评分逻辑：正在输出本怪的玩家→高威胁，低血量玩家→优先收割，
 * 距离近→加分，最近被本怪攻击过的→仇恨加分。
 * 当 SmartTarget 配置关闭时退化为"最近玩家"。
 */
public class SmartTargetGoal extends TargetGoal {

    private final Mob mob;
    private int scanCooldown = 0;
    private static final int SCAN_INTERVAL = 10;

    public SmartTargetGoal(Mob mob) {
        super(mob, false);
        this.mob = mob;
        this.setFlags(EnumSet.of(Flag.TARGET));
    }

    @Override
    public boolean canUse() {
        if (!SmartHordeConfig.ENABLED.get()) return false;
        if (scanCooldown > 0) {
            scanCooldown--;
            return false;
        }
        scanCooldown = SCAN_INTERVAL;

        if (!SmartHordeConfig.SMART_TARGET.get()) {
            LivingEntity closest = findClosestPlayer();
            if (closest != null) {
                this.targetMob = closest;
                return true;
            }
            return false;
        }

        LivingEntity best = findBestTarget();
        if (best != null) {
            this.targetMob = best;
            return true;
        }
        return false;
    }

    @Override
    public boolean canContinueToUse() {
        if (this.targetMob == null || !this.targetMob.isAlive()) return false;
        double followRange = this.mob.getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.FOLLOW_RANGE);
        return this.mob.distanceToSqr(this.targetMob) < followRange * followRange;
    }

    @Override
    public void stop() {
        this.targetMob = null;
        super.stop();
    }

    private LivingEntity findBestTarget() {
        double followRange = this.mob.getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.FOLLOW_RANGE);
        List<Player> players = this.mob.level().getEntitiesOfClass(
                Player.class,
                this.mob.getBoundingBox().inflate(followRange),
                p -> p.isAlive() && !p.isCreative() && !p.isSpectator()
        );
        if (players.isEmpty()) return null;

        return players.stream()
                .max(Comparator.comparingDouble(this::threatScore))
                .orElse(null);
    }

    private double threatScore(Player p) {
        double score = 10.0D;
        double dist = this.mob.distanceTo(p);
        score += Math.max(0, 20.0D - dist);
        float hpRatio = p.getHealth() / p.getMaxHealth();
        score += (1.0D - hpRatio) * 15.0D;
        if (this.mob.getLastHurtByMob() == p) {
            score += 25.0D;
        }
        return score;
    }

    private LivingEntity findClosestPlayer() {
        double followRange = this.mob.getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.FOLLOW_RANGE);
        List<Player> players = this.mob.level().getEntitiesOfClass(
                Player.class,
                this.mob.getBoundingBox().inflate(followRange),
                p -> p.isAlive() && !p.isCreative() && !p.isSpectator()
        );
        Player closest = null;
        double minDist = Double.MAX_VALUE;
        for (Player p : players) {
            double d = this.mob.distanceToSqr(p);
            if (d < minDist) {
                minDist = d;
                closest = p;
            }
        }
        return closest;
    }
}
