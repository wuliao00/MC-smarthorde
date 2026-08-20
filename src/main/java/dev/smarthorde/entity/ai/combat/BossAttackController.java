package dev.smarthorde.entity.ai.combat;

import dev.smarthorde.effects.HordeEffects;
import dev.smarthorde.entity.ai.IAttackMob;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.List;
import java.util.Random;

/**
 * Boss 专属攻击控制器（轮12增强）。
 * 5 招独立招式表，伤害随阶段递增。
 */
public class BossAttackController {

    private final Mob mob;
    private final Random random = new Random();

    // Boss 专属招式表（id 0-4）
    private static final AttackMove[] BOSS_MOVES = {
            // a) 普通挥击
            new AttackMove(0, 4, 3, 3, 8.0F, 3.5D, 90F, 0.5D, 10, 0.0D, 4.5D, 40),
            // b) 横扫风暴（360°全周）
            new AttackMove(1, 6, 4, 5, 12.0F, 4.5D, 360F, 1.0D, 20, 0.0D, 5.5D, 25),
            // c) 地震波（大范围高伤）
            new AttackMove(2, 8, 3, 6, 15.0F, 6.0D, 180F, 1.5D, 30, 0.0D, 7.0D, 15),
            // d) 冲锋突进（远距离突进）
            new AttackMove(3, 5, 2, 4, 10.0F, 5.0D, 60F, 2.0D, 18, 3.0D, 8.0D, 20),
            // e) 狂暴连击（阶段3+解锁，极快）
            new AttackMove(4, 3, 2, 2, 6.0F, 3.0D, 90F, 0.3D, 6, 0.0D, 3.5D, 30),
    };

    private CombatPhase phase = CombatPhase.IDLE;
    private AttackMove currentMove = null;
    private int phaseTicksLeft = 0;
    private int[] cooldowns = new int[BOSS_MOVES.length];
    private boolean hasStruck = false;
    private int bossPhase = 0; // 由 HordeBoss.aiStep 更新

    public BossAttackController(Mob mob) {
        this.mob = mob;
    }

    public void setBossPhase(int phase) { this.bossPhase = phase; }

    public CombatPhase getPhase() { return phase; }
    public AttackMove getCurrentMove() { return currentMove; }
    public boolean isBusy() { return phase != CombatPhase.IDLE; }

    public AttackMove selectMove(double distToTarget) {
        int totalWeight = 0;
        for (AttackMove m : BOSS_MOVES) {
            if (canUseMove(m, distToTarget)) {
                totalWeight += m.weight();
            }
        }
        if (totalWeight <= 0) return null;

        int roll = random.nextInt(totalWeight);
        for (AttackMove m : BOSS_MOVES) {
            if (!canUseMove(m, distToTarget)) continue;
            roll -= m.weight();
            if (roll < 0) return m;
        }
        return BOSS_MOVES[0];
    }

    private boolean canUseMove(AttackMove m, double dist) {
        if (cooldowns[m.id()] > 0) return false;
        // 狂暴连击只在阶段3+解锁
        if (m.id() == 4 && bossPhase < 2) return false;
        return dist >= m.minDist() && dist <= m.maxDist();
    }

    public void startMove(AttackMove move) {
        this.currentMove = move;
        this.phase = CombatPhase.WINDUP;
        this.phaseTicksLeft = move.windupTicks();
        this.hasStruck = false;
        if (mob instanceof IAttackMob am) {
            am.setAttackId(move.id() + 10); // Boss 招式 id 偏移 10+，区别于普通怪
            am.setAttackTicks(move.totalTicks());
        }
        playTelegraph();
    }

    public boolean tick() {
        if (phase == CombatPhase.IDLE) return false;
        phaseTicksLeft--;
        if (phaseTicksLeft <= 0) advancePhase();
        if (phase == CombatPhase.STRIKE && !hasStruck) {
            executeStrike();
            hasStruck = true;
        }
        return phase != CombatPhase.IDLE;
    }

    private void advancePhase() {
        switch (phase) {
            case WINDUP -> { phase = CombatPhase.STRIKE; phaseTicksLeft = currentMove.strikeTicks(); }
            case STRIKE -> { phase = CombatPhase.RECOVERY; phaseTicksLeft = currentMove.recoveryTicks(); }
            case RECOVERY -> {
                cooldowns[currentMove.id()] = currentMove.cooldownTicks();
                phase = CombatPhase.IDLE; currentMove = null;
                if (mob instanceof IAttackMob am) { am.setAttackId(0); am.setAttackTicks(0); }
            }
            default -> phase = CombatPhase.IDLE;
        }
    }

    public void tickCooldowns() {
        for (int i = 0; i < cooldowns.length; i++) if (cooldowns[i] > 0) cooldowns[i]--;
    }

    private void executeStrike() {
        if (!(mob.level() instanceof ServerLevel sl)) return;
        if (currentMove == null) return;

        HordeEffects.playStrike(sl, mob, currentMove);

        Vec3 mobPos = mob.position();
        Vec3 lookDir = mob.getLookAngle();
        double range = currentMove.range();
        float arc = currentMove.arcDegrees();

        AABB area = mob.getBoundingBox().inflate(range);
        List<LivingEntity> targets = sl.getEntitiesOfClass(LivingEntity.class, area,
                e -> e != mob && e.isAlive() && !(e instanceof Mob m && m.getType() == mob.getType()));

        // Boss 伤害随阶段递增: ×(1.0 + phase * 0.3)
        float phaseMul = 1.0F + bossPhase * 0.3F;
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
            float finalDamage = currentMove.damage() * phaseMul;
            target.hurt(source, finalDamage);
            if (currentMove.knockback() > 0) {
                Vec3 kb = toTarget.scale(currentMove.knockback());
                target.push(kb.x, 0.1D, kb.z);
            }
        }
    }

    private void playTelegraph() {
        if (!(mob.level() instanceof ServerLevel sl)) return;
        HordeEffects.playWindup(sl, mob);
    }

    public void abort() {
        phase = CombatPhase.IDLE; currentMove = null; hasStruck = false;
        if (mob instanceof IAttackMob am) { am.setAttackId(0); am.setAttackTicks(0); }
    }
}
