package dev.smarthorde.entity.ai.combat;

import dev.smarthorde.effects.EffectManager;
import dev.smarthorde.entity.EliteRole;
import dev.smarthorde.entity.SmartZombie;
import dev.smarthorde.entity.TacticalAttacker;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.monster.Zombie;

import java.util.EnumSet;
import java.util.List;

/**
 * 尸潮百夫长（COMMANDER）专属战吼光环 AI：
 * 交战中每隔约 9 秒振臂发出战吼（同步 {@link TacticalAttacker#ATTACK_SPECIAL} 战吼动画），
 * 为周围 12 格内至多 8 名僵尸友军赋予短暂迅捷与力量增益。
 */
public class CommanderAuraGoal extends Goal {

    private static final int COOLDOWN_TICKS = 180;
    private static final int TOTAL_CAST_TICKS = 16;
    private static final int BUFF_TRIGGER_TICK = 7;
    private static final double RALLY_RADIUS = 12.0;
    private static final int MAX_BUFFED_ALLIES = 8;

    private final SmartZombie commander;
    private int castTicks;
    private int nextUseTick = 40;

    public CommanderAuraGoal(SmartZombie commander) {
        this.commander = commander;
        this.setFlags(EnumSet.of(Goal.Flag.MOVE, Goal.Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        if (this.commander.getEliteRole() != EliteRole.COMMANDER) {
            return false;
        }
        if (this.commander.tickCount < this.nextUseTick || !this.commander.onGround()) {
            return false;
        }
        if (this.commander.isExecutingAttack() || this.commander.isWallClimbing()) {
            return false;
        }
        LivingEntity target = this.commander.getTarget();
        return target != null && target.isAlive() && this.commander.distanceToSqr(target) <= 18.0 * 18.0;
    }

    @Override
    public boolean canContinueToUse() {
        return this.castTicks < TOTAL_CAST_TICKS && this.commander.isAlive();
    }

    @Override
    public void start() {
        this.castTicks = 0;
        this.commander.getNavigation().stop();
        this.commander.setAttackId(TacticalAttacker.ATTACK_SPECIAL);
        this.commander.setAttackTicks(0);
    }

    @Override
    public void tick() {
        LivingEntity target = this.commander.getTarget();
        if (target != null) {
            this.commander.getLookControl().setLookAt(target, 30.0F, 30.0F);
        }
        this.commander.getNavigation().stop();
        this.commander.setAttackTicks(this.castTicks);

        if (this.castTicks == BUFF_TRIGGER_TICK && this.commander.level() instanceof ServerLevel serverLevel) {
            EffectManager.spawnWarCryEffect(serverLevel, this.commander);
            this.commander.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, 100, 0, false, true));

            List<Zombie> allies = serverLevel.getEntitiesOfClass(Zombie.class,
                    this.commander.getBoundingBox().inflate(RALLY_RADIUS, 4.0, RALLY_RADIUS),
                    ally -> ally != this.commander && ally.isAlive());
            int count = 0;
            for (Zombie ally : allies) {
                if (count++ >= MAX_BUFFED_ALLIES) {
                    break;
                }
                ally.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 120, 0, false, true));
                ally.addEffect(new MobEffectInstance(MobEffects.DAMAGE_BOOST, 100, 0, false, true));
            }
        }
        this.castTicks++;
    }

    @Override
    public void stop() {
        this.commander.setAttackId(TacticalAttacker.ATTACK_NONE);
        this.commander.setAttackTicks(0);
        this.nextUseTick = this.commander.tickCount + COOLDOWN_TICKS;
    }
}
