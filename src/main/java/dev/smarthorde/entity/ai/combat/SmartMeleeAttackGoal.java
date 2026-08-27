package dev.smarthorde.entity.ai.combat;

import dev.smarthorde.config.SmartHordeConfig;
import dev.smarthorde.effects.EffectManager;
import dev.smarthorde.entity.SmartZombie;
import dev.smarthorde.init.ModSounds;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.phys.Vec3;

import java.util.EnumSet;
import java.util.List;

import javax.annotation.Nullable;

/**
 * 招式化攻击（状态机 IDLE -> TELEGRAPH -> STRIKE -> RECOVER）。
 * 每招拥有独立的前摇帧数 / 冷却帧数 / 攻击范围 / 扇形角度；
 * TELEGRAPH 期间头顶冒 CRIT 粒子作为预警，STRIKE 命中时播放横扫粒子弧。
 */
public class SmartMeleeAttackGoal extends Goal {

    /** 攻击招式定义 */
    public record Move(String name, int telegraphTicks, int strikeTicks, int recoverTicks,
                       double reach, float halfArcDeg, double damageMult, int cooldownTicks, int weight) {
    }

    private static final List<Move> MOVES = List.of(
            new Move("light", 6, 3, 8, 2.8, 45.0F, 1.0, 40, 5),
            new Move("heavy", 14, 4, 18, 3.4, 60.0F, 1.7, 80, 2),
            new Move("sweep", 10, 3, 12, 3.0, 110.0F, 1.2, 60, 3));

    private enum Phase {IDLE, TELEGRAPH, STRIKE, RECOVER}

    private final SmartZombie mob;
    private final double speedModifier;

    private Phase phase = Phase.IDLE;
    private Move currentMove;
    private int phaseTicks;
    private boolean strikeApplied;
    private final int[] moveReadyAtTick = new int[MOVES.size()];

    public SmartMeleeAttackGoal(SmartZombie mob, double speedModifier, boolean longMemory) {
        this.mob = mob;
        this.speedModifier = speedModifier;
        this.setFlags(EnumSet.of(Goal.Flag.MOVE, Goal.Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        LivingEntity target = this.mob.getTarget();
        return target != null && target.isAlive()
                && this.mob.distanceToSqr(target) < followRange() * followRange();
    }

    @Override
    public boolean canContinueToUse() {
        LivingEntity target = this.mob.getTarget();
        return target != null && target.isAlive()
                && this.mob.distanceToSqr(target) < followRange() * followRange() * 1.2;
    }

    @Override
    public void stop() {
        this.phase = Phase.IDLE;
        this.currentMove = null;
        this.mob.setAttackId(0);
        this.mob.setAttackTicks(0);
        super.stop();
    }

    @Override
    public void tick() {
        LivingEntity target = this.mob.getTarget();
        if (target == null) {
            return;
        }
        this.mob.getLookControl().setLookAt(target, 30.0F, 30.0F);

        switch (this.phase) {
            case IDLE -> approach(target);
            case TELEGRAPH -> {
                this.mob.getNavigation().stop();
                this.mob.setAttackTicks(this.phaseTicks);
                if (this.phaseTicks == 0) {
                    playTelegraphCue();
                }
                if (this.phaseTicks % 2 == 0 && this.mob.level() instanceof ServerLevel serverLevel) {
                    Vec3 head = this.mob.position().add(0, this.mob.getBbHeight() * 0.9, 0);
                    EffectManager.spawnAttackParticles(serverLevel, head, true);
                }
                advanceIfDone(this.currentMove.telegraphTicks(), Phase.STRIKE);
            }
            case STRIKE -> {
                this.mob.setAttackTicks(this.currentMove.telegraphTicks() + this.phaseTicks);
                if (!this.strikeApplied) {
                    this.strikeApplied = true;
                    applyStrike(target);
                }
                advanceIfDone(this.currentMove.strikeTicks(), Phase.RECOVER);
            }
            case RECOVER -> {
                this.mob.getNavigation().stop();
                advanceIfDone(this.currentMove.recoverTicks(), Phase.IDLE);
                if (this.phase == Phase.IDLE) {
                    this.moveReadyAtTick[MOVES.indexOf(this.currentMove)] =
                            this.mob.tickCount + this.currentMove.cooldownTicks();
                    this.mob.setAttackId(0);
                    this.mob.setAttackTicks(0);
                }
            }
        }
    }

    private void approach(LivingEntity target) {
        double reachSqr = 9.0; // 进入 3 格内开始出招
        if (this.mob.distanceToSqr(target) > reachSqr) {
            this.mob.getNavigation().moveTo(target, this.speedModifier);
            return;
        }
        Move move = pickMove(target);
        if (move == null) {
            this.mob.getNavigation().moveTo(target, this.speedModifier);
            return;
        }
        this.mob.getNavigation().stop();
        this.currentMove = move;
        this.phaseTicks = -1;
        this.strikeApplied = false;
        this.phase = Phase.TELEGRAPH;
        this.mob.setAttackId(MOVES.indexOf(move) + 1);
    }

    private void advanceIfDone(int duration, Phase next) {
        this.phaseTicks++;
        if (this.phaseTicks >= duration) {
            this.phase = next;
            this.phaseTicks = -1;
        }
    }

    @Nullable
    private Move pickMove(LivingEntity target) {
        double dist = this.mob.distanceTo(target);
        int totalWeight = 0;
        for (int i = 0; i < MOVES.size(); i++) {
            Move move = MOVES.get(i);
            if (this.mob.tickCount >= this.moveReadyAtTick[i] && dist <= move.reach()) {
                totalWeight += move.weight();
            }
        }
        if (totalWeight == 0) {
            return null;
        }
        int roll = this.mob.getRandom().nextInt(totalWeight);
        for (int i = 0; i < MOVES.size(); i++) {
            Move move = MOVES.get(i);
            if (this.mob.tickCount < this.moveReadyAtTick[i] || dist > move.reach()) {
                continue;
            }
            roll -= move.weight();
            if (roll < 0) {
                return move;
            }
        }
        return null;
    }

    /** 扇形范围判定并命中所有目标。 */
    private void applyStrike(LivingEntity target) {
        if (!(this.mob.level() instanceof ServerLevel serverLevel)) {
            return;
        }
        Vec3 look = flatten(this.mob.getLookAngle());
        double reach = this.currentMove.reach();
        double cosLimit = Math.cos(Math.toRadians(this.currentMove.halfArcDeg()));
        float damage = (float) (this.mob.getAttributeValue(Attributes.ATTACK_DAMAGE) * this.currentMove.damageMult());

        List<LivingEntity> victims = serverLevel.getEntitiesOfClass(LivingEntity.class,
                this.mob.getBoundingBox().inflate(reach),
                v -> v != this.mob && v.isAlive() && !(v instanceof SmartZombie) && !v.isAlliedTo(this.mob));

        for (LivingEntity victim : victims) {
            Vec3 toVictim = flatten(victim.position().subtract(this.mob.position()));
            if (toVictim.lengthSqr() > reach * reach) {
                continue;
            }
            Vec3 normalized = toVictim.normalize();
            if (look.dot(normalized) < cosLimit) {
                continue;
            }
            victim.hurt(this.mob.damageSources().mobAttack(this.mob), damage);
            victim.knockback(0.4, -normalized.x, -normalized.z);
            serverLevel.sendParticles(ParticleTypes.SWEEP_ATTACK,
                    victim.getX(), victim.getY() + victim.getBbHeight() * 0.5, victim.getZ(),
                    1, 0.0, 0.0, 0.0, 0.0);
            EffectManager.spawnAttackParticles(serverLevel,
                    victim.position().add(0, victim.getBbHeight() * 0.5, 0), false);
        }
        // 扇形弧线上的横扫粒子
        if (SmartHordeConfig.PARTICLES_ENABLED.get()) {
            int arcSteps = Math.min(12, SmartHordeConfig.MAX_PARTICLES.get() / 4);
            for (int i = 0; i <= arcSteps; i++) {
                double angle = Math.toRadians(this.currentMove.halfArcDeg() * 2 * i / arcSteps
                        - this.currentMove.halfArcDeg());
                double yaw = Math.toRadians(-Mth.wrapDegrees(this.mob.getYRot()) + 90);
                double dirX = -Math.sin(yaw + angle);
                double dirZ = Math.cos(yaw + angle);
                serverLevel.sendParticles(ParticleTypes.CRIT,
                        this.mob.getX() + dirX * reach * 0.8,
                        this.mob.getY() + this.mob.getBbHeight() * 0.5,
                        this.mob.getZ() + dirZ * reach * 0.8,
                        1, 0.0, 0.0, 0.0, 0.02);
            }
        }
    }

    private void playTelegraphCue() {
        if (this.mob.level() instanceof ServerLevel serverLevel && SmartHordeConfig.SOUNDS_ENABLED.get()) {
            serverLevel.playSound(null, this.mob,
                    ModSounds.SMART_ATTACK.get(), SoundSource.HOSTILE, 0.8F, 1.0F);
        }
    }

    private static Vec3 flatten(Vec3 vec) {
        Vec3 flat = new Vec3(vec.x, 0, vec.z);
        return flat.lengthSqr() < 1.0E-4 ? new Vec3(0, 0, 1) : flat.normalize();
    }

    /** 1.21.x Mob 没有 getFollowRange()，从 FOLLOW_RANGE 属性读取。 */
    private double followRange() {
        return this.mob.getAttributeValue(Attributes.FOLLOW_RANGE);
    }
}
