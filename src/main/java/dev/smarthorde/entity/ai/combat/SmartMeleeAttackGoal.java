package dev.smarthorde.entity.ai.combat;

import dev.smarthorde.config.SmartHordeConfig;
import dev.smarthorde.effects.EffectManager;
import dev.smarthorde.entity.EliteRole;
import dev.smarthorde.entity.HordeBoss;
import dev.smarthorde.entity.SmartZombie;
import dev.smarthorde.entity.TacticalAttacker;
import dev.smarthorde.init.ModSounds;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;
import java.util.EnumSet;
import java.util.List;

/**
 * 招式化近战系统（状态机 IDLE -> TELEGRAPH -> STRIKE -> RECOVER）：
 * <ul>
 *   <li>同时服务于 {@link SmartZombie}、{@link HordeBoss} 与被注入的原版僵尸；</li>
 *   <li>寻路节流与死区修复：消除 3.3~3.5 格起手死区发呆 Bug 与每 tick 重复 A* 重算，受阻满 12 tick 后才让出 MOVE 给侧翼绕障；</li>
 *   <li>防穿模核心修复：TELEGRAPH/STRIKE/RECOVER 全程保持 MOVE 锁；突刺冲量动态限幅在玩家身前 0.65 格停住；</li>
 *   <li>保持交战态 {@code setAggressive(true)} 不断帧，避免被闪避/战吼抢占时手臂在战斗架势与垂臂之间闪跳。</li>
 * </ul>
 */
public class SmartMeleeAttackGoal extends Goal {

    /** 攻击招式定义 */
    public record Move(int id, String name, int telegraphTicks, int strikeTicks, int recoverTicks,
                       double reach, float halfArcDeg, double damageMult, int cooldownTicks, int weight) {

        public boolean isThrust() {
            return this.id == TacticalAttacker.ATTACK_THRUST;
        }
    }

    public static final List<Move> MOVES = List.of(
            new Move(TacticalAttacker.ATTACK_LIGHT, "light", 6, 3, 8, 3.3, 45.0F, 1.0, 36, 5),
            new Move(TacticalAttacker.ATTACK_HEAVY, "heavy", 14, 4, 16, 3.9, 60.0F, 1.7, 76, 2),
            new Move(TacticalAttacker.ATTACK_SWEEP, "sweep", 10, 3, 12, 3.7, 110.0F, 1.2, 56, 3),
            new Move(TacticalAttacker.ATTACK_THRUST, "thrust", 8, 3, 13, 5.0, 18.0F, 1.5, 84, 2));

    private static final double MAX_MOVE_REACH = 5.0;
    private static final double COOLDOWN_STANDOFF_DISTANCE = 3.0;

    private enum Phase {IDLE, TELEGRAPH, STRIKE, RECOVER}

    private final PathfinderMob mob;
    private final double speedModifier;

    private Phase phase = Phase.IDLE;
    @Nullable
    private Move currentMove;
    private int phaseTicks;
    private boolean strikeApplied;
    private final int[] moveReadyAtTick = new int[MOVES.size()];

    private int pathRecalcCooldown;
    @Nullable
    private Vec3 lastPathTargetPos;
    private int stuckTicks;

    public SmartMeleeAttackGoal(PathfinderMob mob, double speedModifier, boolean longMemory) {
        this.mob = mob;
        this.speedModifier = speedModifier;
        this.setFlags(EnumSet.of(Goal.Flag.MOVE, Goal.Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        if (this.mob instanceof SmartZombie zombie && zombie.isShieldBlocking()) {
            return false;
        }
        LivingEntity target = this.mob.getTarget();
        return target != null && target.isAlive()
                && this.mob.distanceToSqr(target) < followRange() * followRange();
    }

    @Override
    public boolean canContinueToUse() {
        if (this.mob instanceof SmartZombie zombie && zombie.isShieldBlocking() && this.phase == Phase.IDLE) {
            return false;
        }
        LivingEntity target = this.mob.getTarget();
        return target != null && target.isAlive()
                && this.mob.distanceToSqr(target) < followRange() * followRange() * 1.2;
    }

    @Override
    public void start() {
        super.start();
        this.mob.setAggressive(true);
        this.pathRecalcCooldown = 0;
        this.stuckTicks = 0;
        this.lastPathTargetPos = null;
    }

    @Override
    public void stop() {
        this.phase = Phase.IDLE;
        this.currentMove = null;
        this.pathRecalcCooldown = 0;
        this.stuckTicks = 0;
        this.lastPathTargetPos = null;
        LivingEntity target = this.mob.getTarget();
        if (target == null || !target.isAlive()) {
            this.mob.setAggressive(false);
        }
        syncAttackState(TacticalAttacker.ATTACK_NONE, 0);
        this.setFlags(EnumSet.of(Goal.Flag.MOVE, Goal.Flag.LOOK));
        super.stop();
    }

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
        // 出招与骑乘状态下同步校准躯干朝向目标，避免上半身与下半身错位扭曲
        if ((this.mob.isPassenger() || this.phase != Phase.IDLE) && this.mob.level() instanceof ServerLevel) {
            Vec3 toTarget = flatten(target.position().subtract(this.mob.position()));
            float wantedYaw = (float) (Math.atan2(toTarget.z, toTarget.x) * (180.0 / Math.PI)) - 90.0F;
            if (this.mob.isPassenger()) {
                this.mob.setYRot(wantedYaw);
                this.mob.setYHeadRot(wantedYaw);
            } else {
                float delta = Mth.wrapDegrees(wantedYaw - this.mob.yBodyRot);
                this.mob.yBodyRot += Mth.clamp(delta, -22.0F, 22.0F);
                this.mob.setYRot(this.mob.yBodyRot);
            }
        }

        switch (this.phase) {
            case IDLE -> approach(target);
            case TELEGRAPH -> {
                updateFlags(true);
                this.mob.getNavigation().stop();
                syncAttackState(this.currentMove.id(), this.phaseTicks);
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
                updateFlags(true);
                syncAttackState(this.currentMove.id(), this.currentMove.telegraphTicks() + this.phaseTicks);
                if (!this.strikeApplied) {
                    this.strikeApplied = true;
                    this.mob.swing(InteractionHand.MAIN_HAND);
                    if (this.currentMove.isThrust()) {
                        lungeForwardSafe(target);
                    }
                    applyStrike(target);
                }
                advanceIfDone(this.currentMove.strikeTicks(), Phase.RECOVER);
            }
            case RECOVER -> {
                updateFlags(true);
                this.mob.getNavigation().stop();
                int elapsed = this.currentMove.telegraphTicks() + this.currentMove.strikeTicks() + this.phaseTicks;
                syncAttackState(this.currentMove.id(), elapsed);
                Move finishedMove = this.currentMove;
                advanceIfDone(finishedMove.recoverTicks(), Phase.IDLE);
                if (this.phase == Phase.IDLE) {
                    int moveIdx = MOVES.indexOf(finishedMove);
                    if (moveIdx >= 0) {
                        this.moveReadyAtTick[moveIdx] = this.mob.tickCount + scaledCooldown(finishedMove);
                    }
                    this.currentMove = null;
                    syncAttackState(TacticalAttacker.ATTACK_NONE, 0);
                }
            }
        }
    }

    private void approach(LivingEntity target) {
        double reachBonus = sizeReachBonus();
        double maxReach = MAX_MOVE_REACH + reachBonus;
        double distSqr = this.mob.distanceToSqr(target);

        if (distSqr <= maxReach * maxReach) {
            Move move = pickMove(target);
            if (move != null) {
                updateFlags(true);
                this.stuckTicks = 0;
                this.mob.getNavigation().stop();
                this.currentMove = move;
                this.phaseTicks = 0;
                this.strikeApplied = false;
                this.phase = Phase.TELEGRAPH;
                syncAttackState(move.id(), 0);
                return;
            }
            // 若全部招式均处于冷却且已贴近目标，释放 MOVE 让距离管理/分离 Goal 横移走位
            double standoff = COOLDOWN_STANDOFF_DISTANCE + reachBonus;
            if (!hasAnyMoveOffCooldown() && distSqr <= standoff * standoff) {
                this.mob.getNavigation().stop();
                updateFlags(false);
                return;
            }
        }

        // 仍有就绪短手招式（如轻击 3.3 格）或尚在远处：继续逼近目标
        pathTowardTargetThrottled(target);
    }

    private void pathTowardTargetThrottled(LivingEntity target) {
        if (this.pathRecalcCooldown > 0) {
            this.pathRecalcCooldown--;
        }
        boolean targetMoved = this.lastPathTargetPos == null
                || target.distanceToSqr(this.lastPathTargetPos) >= 2.25;
        boolean navIdle = !this.mob.getNavigation().isInProgress();

        boolean pathSuccess = true;
        if (this.pathRecalcCooldown <= 0 || targetMoved || navIdle) {
            this.lastPathTargetPos = target.position();
            this.pathRecalcCooldown = 5 + this.mob.getRandom().nextInt(5);
            pathSuccess = this.mob.getNavigation().moveTo(target, this.speedModifier);
        }

        boolean blocked = !pathSuccess || this.mob.horizontalCollision
                || (this.mob.onGround() && this.mob.getDeltaMovement().horizontalDistanceSqr() < 4.0E-4);
        if (blocked) {
            this.stuckTicks = Math.min(40, this.stuckTicks + 1);
        } else {
            this.stuckTicks = Math.max(0, this.stuckTicks - 2);
        }
        // 持续受阻满 12 tick 后临时让出 MOVE，交由 FlankGoal 侧向绕开障碍
        updateFlags(this.stuckTicks < 12);
    }

    private boolean hasAnyMoveOffCooldown() {
        for (int readyTick : this.moveReadyAtTick) {
            if (this.mob.tickCount >= readyTick) {
                return true;
            }
        }
        return false;
    }

    private void advanceIfDone(int duration, Phase next) {
        this.phaseTicks++;
        if (this.phaseTicks >= duration) {
            this.phase = next;
            this.phaseTicks = 0;
        }
    }

    @Nullable
    private Move pickMove(LivingEntity target) {
        double dist = this.mob.distanceTo(target);
        double reachBonus = sizeReachBonus();
        int totalWeight = 0;
        for (int i = 0; i < MOVES.size(); i++) {
            Move move = MOVES.get(i);
            if (this.mob.tickCount >= this.moveReadyAtTick[i] && dist <= move.reach() + reachBonus) {
                totalWeight += roleAdjustedWeight(move);
            }
        }
        if (totalWeight <= 0) {
            return null;
        }
        int roll = this.mob.getRandom().nextInt(totalWeight);
        for (int i = 0; i < MOVES.size(); i++) {
            Move move = MOVES.get(i);
            if (this.mob.tickCount < this.moveReadyAtTick[i] || dist > move.reach() + reachBonus) {
                continue;
            }
            roll -= roleAdjustedWeight(move);
            if (roll < 0) {
                return move;
            }
        }
        return null;
    }

    private int roleAdjustedWeight(Move move) {
        int base = move.weight();
        if (this.mob instanceof SmartZombie zombie) {
            EliteRole role = zombie.getEliteRole();
            return switch (role) {
                case RAIDER -> (move.id() == TacticalAttacker.ATTACK_LIGHT
                        || move.id() == TacticalAttacker.ATTACK_THRUST) ? base * 2 : base;
                case VANGUARD -> (move.id() == TacticalAttacker.ATTACK_HEAVY
                        || move.id() == TacticalAttacker.ATTACK_SWEEP) ? base * 2 : base;
                case BERSERKER -> (move.id() == TacticalAttacker.ATTACK_HEAVY
                        || move.id() == TacticalAttacker.ATTACK_LIGHT) ? base * 2 : base;
                default -> base;
            };
        }
        if (this.mob instanceof HordeBoss) {
            return (move.id() == TacticalAttacker.ATTACK_HEAVY
                    || move.id() == TacticalAttacker.ATTACK_SWEEP) ? base * 2 : base;
        }
        return base;
    }

    private int scaledCooldown(Move move) {
        double factor = 1.0;
        double attackSpeed = this.mob.getAttributeValue(Attributes.ATTACK_SPEED);
        if (attackSpeed > 0.2) {
            factor /= Math.min(2.0, Math.max(0.6, attackSpeed));
        }
        if (this.mob instanceof SmartZombie zombie && zombie.isFrenzyActive()) {
            factor *= 0.72;
        }
        return Math.max(12, (int) Math.round(move.cooldownTicks() * factor));
    }

    private double sizeReachBonus() {
        return Math.max(0.0, this.mob.getBbWidth() - 0.6) * 0.9;
    }

    private void applyStrike(LivingEntity target) {
        if (!(this.mob.level() instanceof ServerLevel serverLevel) || this.currentMove == null) {
            return;
        }
        Vec3 aim = this.mob.isPassenger()
                ? flatten(target.position().subtract(this.mob.position()))
                : flatten(this.mob.getLookAngle());
        double reach = this.currentMove.reach() + sizeReachBonus();
        double cosLimit = Math.cos(Math.toRadians(this.currentMove.halfArcDeg()));
        float damage = (float) (this.mob.getAttributeValue(Attributes.ATTACK_DAMAGE) * this.currentMove.damageMult());
        double verticalLimit = Math.max(3.0, this.mob.getBbHeight() + 1.2);

        List<LivingEntity> victims = serverLevel.getEntitiesOfClass(LivingEntity.class,
                this.mob.getBoundingBox().inflate(reach),
                e -> e != this.mob && e.isAlive() && !e.isSpectator()
                        && !(e instanceof Player p && p.isCreative())
                        && (e == target || e instanceof Player)
                        && !(e instanceof net.minecraft.world.entity.monster.Zombie)
                        && !this.mob.isAlliedTo(e));

        for (LivingEntity victim : victims) {
            Vec3 toVictim = flatten(victim.position().subtract(this.mob.position()));
            double distSqr = toVictim.lengthSqr();
            double dy = Math.abs(victim.getY() - this.mob.getY());
            if (dy > verticalLimit || distSqr > reach * reach) {
                continue;
            }
            Vec3 normalized = toVictim.normalize();
            boolean pointBlank = distSqr <= 3.2;
            if (!pointBlank && !this.mob.isPassenger() && aim.dot(normalized) < cosLimit) {
                continue;
            }
            boolean hurt = victim.hurt(this.mob.damageSources().mobAttack(this.mob), damage);
            if (hurt) {
                victim.knockback(0.4, -normalized.x, -normalized.z);
                if (this.mob instanceof HordeBoss boss) {
                    boss.applyVariantHitEffect(victim);
                }
                serverLevel.sendParticles(ParticleTypes.SWEEP_ATTACK,
                        victim.getX(), victim.getY() + victim.getBbHeight() * 0.5, victim.getZ(),
                        1, 0.0, 0.0, 0.0, 0.0);
                EffectManager.spawnAttackParticles(serverLevel,
                        victim.position().add(0, victim.getBbHeight() * 0.5, 0), false);
            }
        }

        if (SmartHordeConfig.isParticlesEnabledSafe()) {
            int arcSteps = Math.max(3, Math.min(12, SmartHordeConfig.maxParticlesSafe() / 4));
            for (int i = 0; i <= arcSteps; i++) {
                double angle = Math.toRadians(this.currentMove.halfArcDeg() * 2 * i / arcSteps
                        - this.currentMove.halfArcDeg());
                double yaw = Math.toRadians(-Mth.wrapDegrees(this.mob.getYRot()) + 90);
                double dirX = -Math.sin(yaw + angle);
                double dirZ = Math.cos(yaw + angle);
                serverLevel.sendParticles(ParticleTypes.CRIT,
                        this.mob.getX() + dirX * reach * 0.78,
                        this.mob.getY() + this.mob.getBbHeight() * 0.52,
                        this.mob.getZ() + dirZ * reach * 0.78,
                        1, 0.0, 0.0, 0.0, 0.02);
            }
        }
    }

    /**
     * 防穿模突刺位移：根据与目标的水平净距动态计算冲量上限，
     * 确保停在玩家身前 0.65 格处，绝不穿过玩家身体导致第一人称视角穿模。
     */
    private void lungeForwardSafe(LivingEntity target) {
        Vec3 diff = new Vec3(target.getX() - this.mob.getX(), 0, target.getZ() - this.mob.getZ());
        double horizDist = diff.length();
        double minSafeGap = (this.mob.getBbWidth() + target.getBbWidth()) * 0.5 + 0.65;
        if (horizDist <= minSafeGap || horizDist < 1.0E-4) {
            return;
        }
        Vec3 towards = diff.scale(1.0 / horizDist);
        double dashSpeed = Mth.clamp((horizDist - minSafeGap) * 0.34, 0.0, 0.78);
        if (!this.mob.level().noCollision(this.mob,
                this.mob.getBoundingBox().move(towards.x * 0.55, 0.1, towards.z * 0.55))) {
            return;
        }
        this.mob.setDeltaMovement(new Vec3(
                towards.x * dashSpeed,
                Math.min(0.14, this.mob.getDeltaMovement().y + 0.08),
                towards.z * dashSpeed));
        this.mob.hasImpulse = true;
    }

    private void playTelegraphCue() {
        if (this.mob.level() instanceof ServerLevel serverLevel && SmartHordeConfig.isSoundsEnabledSafe()) {
            float pitch = this.mob instanceof HordeBoss ? 0.75F : 1.0F;
            serverLevel.playSound(null, this.mob,
                    ModSounds.SMART_ATTACK.get(), SoundSource.HOSTILE, 0.8F, pitch);
        }
    }

    private static Vec3 flatten(Vec3 vec) {
        Vec3 flat = new Vec3(vec.x, 0, vec.z);
        return flat.lengthSqr() < 1.0E-4 ? new Vec3(0, 0, 1) : flat.normalize();
    }

    private double followRange() {
        return this.mob.getAttributeValue(Attributes.FOLLOW_RANGE);
    }

    private void syncAttackState(int id, int ticks) {
        if (this.mob instanceof TacticalAttacker attacker) {
            attacker.setAttackId(id);
            attacker.setAttackTicks(ticks);
        }
    }
}
