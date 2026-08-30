package dev.smarthorde.entity.ai.combat;

import dev.smarthorde.config.DifficultyManager;
import dev.smarthorde.config.SmartHordeConfig;
import dev.smarthorde.effects.HordeEffects;
import dev.smarthorde.entity.ai.IAttackMob;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * 招式状态机控制器。
 * 由 SmartMeleeAttackGoal 每 tick 驱动；
 * 在 STRIKE 阶段执行范围检测与伤害结算。
 */
public class AttackController {

    private final Mob mob;
    private final Random random = new Random();

    // 默认招式表（轮6会按难度预设换算伤害/冷却）
    private static final AttackMove[] MOVES = {
            // id, windup, strike, recovery, dmg, range, arc, kb, cd, minD, maxD, weight
            new AttackMove(0, 4,  3, 3,  3.0F, 3.0D, 90F,  0.3D, 8,  0.0D, 4.0D, 50),  // 轻击
            new AttackMove(1, 10, 4, 6,  7.0F, 3.5D, 120F, 0.8D, 25, 0.0D, 5.0D, 20),  // 重击
            new AttackMove(2, 7,  5, 5,  5.0F, 4.0D, 270F, 0.6D, 20, 0.0D, 6.0D, 20),  // 横扫
            new AttackMove(3, 5,  3, 4,  4.0F, 4.0D, 60F,  1.2D, 15, 2.0D, 8.0D, 10),  // 冲刺
    };

    private CombatPhase phase = CombatPhase.IDLE;
    private AttackMove currentMove = null;
    private int phaseTicksLeft = 0;
    private int[] cooldowns = new int[MOVES.length];
    private boolean hasStruck = false;

    public AttackController(Mob mob) {
        this.mob = mob;
    }

    // ===== 状态查询 =====
    public CombatPhase getPhase() { return phase; }
    public AttackMove getCurrentMove() { return currentMove; }
    public boolean isBusy() { return phase != CombatPhase.IDLE; }

    // ===== 招式选择 =====

    public AttackMove selectMove(double distToTarget) {
        if (!SmartHordeConfig.COMBO_ATTACKS.get()) {
            return MOVES[0];
        }
        int totalWeight = 0;
        for (AttackMove m : MOVES) {
            if (canUseMove(m, distToTarget)) {
                totalWeight += m.weight();
            }
        }
        if (totalWeight <= 0) return null;

        int roll = random.nextInt(totalWeight);
        for (AttackMove m : MOVES) {
            if (!canUseMove(m, distToTarget)) continue;
            roll -= m.weight();
            if (roll < 0) return m;
        }
        return MOVES[0];
    }

    private boolean canUseMove(AttackMove m, double dist) {
        if (cooldowns[m.id()] > 0) return false;
        return dist >= m.minDist() && dist <= m.maxDist();
    }

    // ===== 状态机推进 =====

    public void startMove(AttackMove move) {
        this.currentMove = move;
        this.phase = CombatPhase.WINDUP;
        this.phaseTicksLeft = move.windupTicks();
        this.hasStruck = false;
        if (this.mob instanceof IAttackMob sz) {
            sz.setAttackId(move.id());
            sz.setAttackTicks(move.totalTicks());
        }
        if (SmartHordeConfig.TELEGRAPH.get()) {
            playTelegraph();
        }
    }

    public boolean tick() {
        if (phase == CombatPhase.IDLE) return false;

        phaseTicksLeft--;

        if (phaseTicksLeft <= 0) {
            advancePhase();
        }

        if (phase == CombatPhase.STRIKE && !hasStruck) {
            executeStrike();
            hasStruck = true;
        }

        return phase != CombatPhase.IDLE;
    }

    private void advancePhase() {
        switch (phase) {
            case WINDUP -> {
                phase = CombatPhase.STRIKE;
                phaseTicksLeft = currentMove.strikeTicks();
            }
            case STRIKE -> {
                phase = CombatPhase.RECOVERY;
                phaseTicksLeft = currentMove.recoveryTicks();
            }
            case RECOVERY -> {
                cooldowns[currentMove.id()] = currentMove.cooldownTicks();
                phase = CombatPhase.IDLE;
                currentMove = null;
                if (mob instanceof IAttackMob sz) {
                    sz.setAttackId(0);
                    sz.setAttackTicks(0);
                }
            }
            default -> { phase = CombatPhase.IDLE; }
        }
    }

    public void tickCooldowns() {
        for (int i = 0; i < cooldowns.length; i++) {
            if (cooldowns[i] > 0) cooldowns[i]--;
        }
    }

    // ===== 命中判定 =====

    private void executeStrike() {
        if (!(mob.level() instanceof ServerLevel serverLevel)) return;
        if (currentMove == null) return;

        HordeEffects.playStrike(serverLevel, mob, currentMove);

        Vec3 mobPos = mob.position();
        Vec3 lookDir = mob.getLookAngle();
        double range = currentMove.range();
        float arc = currentMove.arcDegrees();

        AABB area = mob.getBoundingBox().inflate(range);

        // [F4] 友伤根治（结构性方案，参照 1.0.x 41931c5）：受害者仅限
        // 当前攻击目标 + 范围内玩家（需 !isAlliedTo 且非观察者/创造），
        // 完全不再波及任何 Mob——伤害源头排除后永不触发 HurtByTargetGoal 反击闭环
        List<LivingEntity> targets = new ArrayList<>();
        LivingEntity currentTarget = mob.getTarget();
        if (currentTarget != null && currentTarget.isAlive()) {
            targets.add(currentTarget);
        }
        for (Player player : serverLevel.getEntitiesOfClass(Player.class, area,
                p -> p.isAlive() && !p.isSpectator() && !p.isCreative() && !p.isAlliedTo(mob))) {
            if (player != currentTarget) {
                targets.add(player);
            }
        }

        DamageSource source = mob.damageSources().mobAttack(mob);

        for (LivingEntity target : targets) {
            Vec3 toTarget = target.position().subtract(mobPos).normalize();
            double dist = mobPos.distanceTo(target.position());
            if (dist > range + 0.5D) continue;

            if (arc < 360F) {
                double dot = lookDir.dot(toTarget);
                double angleDeg = Math.toDegrees(Math.acos(Math.clamp(dot, -1.0D, 1.0D)));
                if (angleDeg > arc / 2.0F) continue;
            }

            float finalDamage = currentMove.damage() * DifficultyManager.get().damageMultiplier();
            target.hurt(source, finalDamage);

            if (currentMove.knockback() > 0) {
                Vec3 kb = toTarget.scale(currentMove.knockback());
                target.push(kb.x, 0.1D, kb.z);
            }
        }
    }

    // ===== Telegraph 前摇提示 =====

    private void playTelegraph() {
        if (!(mob.level() instanceof ServerLevel sl)) return;
        HordeEffects.playWindup(sl, mob);
    }

    public void abort() {
        phase = CombatPhase.IDLE;
        currentMove = null;
        hasStruck = false;
        if (mob instanceof IAttackMob sz) {
            sz.setAttackId(0);
            sz.setAttackTicks(0);
        }
    }
}
