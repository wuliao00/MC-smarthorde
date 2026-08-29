package dev.smarthorde.entity.ai.combat;

import dev.smarthorde.config.SmartHordeConfig;
import dev.smarthorde.effects.EffectManager;
import dev.smarthorde.entity.HordeBoss;
import dev.smarthorde.entity.SmartZombie;
import dev.smarthorde.init.ModSounds;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.npc.Villager;
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

        public boolean isThrust() {
            return "thrust".equals(this.name);
        }
    }

    private static final List<Move> MOVES = List.of(
            new Move("light", 6, 3, 8, 3.3, 45.0F, 1.0, 40, 5),
            new Move("heavy", 14, 4, 18, 3.9, 60.0F, 1.7, 80, 2),
            new Move("sweep", 10, 3, 12, 3.7, 110.0F, 1.2, 60, 3),
            // 突刺：窄角高伤远程招，出招瞬间向前冲刺位移
            new Move("thrust", 8, 2, 14, 5.2, 16.0F, 1.5, 90, 2));

    /** 起手距离（格）——进入该范围后开始选招出招 */
    private static final double ENGAGE_DISTANCE = 3.5;

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
        this.setFlags(EnumSet.of(Goal.Flag.MOVE, Goal.Flag.LOOK));
        super.stop();
    }

    /**
     * 动态标志：出招各阶段与"被卡住"时只保留 LOOK，
     * 把 MOVE 让给闪避/攀墙/包抄/距离管理等战术 Goal（否则低优先级 Goal 被饿死）。
     */
    private void updateFlags(boolean wantsMove) {
        boolean hasMove = this.getFlags().contains(Goal.Flag.MOVE);
        if (hasMove == wantsMove) {
            return;
        }
        this.setFlags(wantsMove
                ? EnumSet.of(Goal.Flag.MOVE, Goal.Flag.LOOK)
                : EnumSet.of(Goal.Flag.LOOK));
    }

    @Override
    public void tick() {
        LivingEntity target = this.mob.getTarget();
        if (target == null) {
            return;
        }
        this.mob.getLookControl().setLookAt(target, 30.0F, 30.0F);
        // 骑乘状态下 LookControl 不生效，手动转向目标，保证渲染与命中朝向一致
        if (this.mob.isPassenger() && this.mob.level() instanceof ServerLevel) {
            Vec3 toTarget = flatten(target.position().subtract(this.mob.position()));
            float wanted = (float) (Math.atan2(toTarget.z, toTarget.x) * (180.0 / Math.PI)) - 90.0F;
            this.mob.setYRot(wanted);
            this.mob.setYHeadRot(wanted);
        }

        switch (this.phase) {
            case IDLE -> approach(target);
            case TELEGRAPH -> {
                updateFlags(false);
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
                updateFlags(false);
                this.mob.setAttackTicks(this.currentMove.telegraphTicks() + this.phaseTicks);
                if (!this.strikeApplied) {
                    this.strikeApplied = true;
                    if (this.currentMove.isThrust()) {
                        lungeForward(target);
                    }
                    applyStrike(target);
                }
                advanceIfDone(this.currentMove.strikeTicks(), Phase.RECOVER);
            }
            case RECOVER -> {
                updateFlags(false);
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
        double reachSqr = ENGAGE_DISTANCE * ENGAGE_DISTANCE;
        if (this.mob.distanceToSqr(target) > reachSqr) {
            // 被墙/障碍卡住时释放 MOVE，让攀墙、包抄等战术 Goal 接管走位
            boolean stuck = this.mob.horizontalCollision
                    || !this.mob.getNavigation().isInProgress();
            updateFlags(!stuck);
            this.mob.getNavigation().moveTo(target, this.speedModifier);
            return;
        }
        updateFlags(false);
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
        // 骑乘时朝向跟随坐骑不可靠，直接以目标方向为瞄准轴；贴脸(<=2格)时跳过扇形判定
        Vec3 aim = this.mob.isPassenger()
                ? flatten(target.position().subtract(this.mob.position()))
                : flatten(this.mob.getLookAngle());
        double reach = this.currentMove.reach();
        double cosLimit = Math.cos(Math.toRadians(this.currentMove.halfArcDeg()));
        float damage = (float) (this.mob.getAttributeValue(Attributes.ATTACK_DAMAGE) * this.currentMove.damageMult());

        List<LivingEntity> victims = serverLevel.getEntitiesOfClass(LivingEntity.class,
                this.mob.getBoundingBox().inflate(reach),
                v -> v != this.mob && v.isAlive()
                        // 排除己方（SmartZombie/HordeBoss 均为 mod 实体），避免尸潮中 Boss 被误伤
                        && !(v instanceof SmartZombie)
                        && !(v instanceof HordeBoss)
                        // 排除村民与动物类，避免无差别命中被动实体
                        && !(v instanceof Villager)
                        && !(v instanceof Animal)
                        && !v.isAlliedTo(this.mob));

        for (LivingEntity victim : victims) {
            Vec3 toVictim = flatten(victim.position().subtract(this.mob.position()));
            double distSqr = toVictim.lengthSqr();
            // 竖直方向差值单独放宽：命中叠罗汉/高台上的玩家
            double dy = Math.abs(victim.getY() - this.mob.getY());
            if (dy > 3.0 || distSqr > reach * reach) {
                continue;
            }
            Vec3 normalized = toVictim.normalize();
            boolean pointBlank = distSqr <= 4.0;
            if (!pointBlank && !this.mob.isPassenger() && aim.dot(normalized) < cosLimit) {
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

    /** 突刺冲刺位移：朝目标方向猛扑一小段。 */
    private void lungeForward(LivingEntity target) {
        Vec3 towards = flatten(target.position().subtract(this.mob.position()));
        this.mob.setDeltaMovement(new Vec3(
                towards.x * 0.85, Math.min(0.15, this.mob.getDeltaMovement().y + 0.1), towards.z * 0.85));
        this.mob.hasImpulse = true;
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
