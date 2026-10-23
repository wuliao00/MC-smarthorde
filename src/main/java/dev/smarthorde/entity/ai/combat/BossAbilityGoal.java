package dev.smarthorde.entity.ai.combat;

import dev.smarthorde.config.SmartHordeConfig;
import dev.smarthorde.effects.EffectManager;
import dev.smarthorde.entity.HordeBoss;
import dev.smarthorde.entity.TacticalAttacker;
import dev.smarthorde.init.ModSounds;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

import java.util.EnumSet;
import java.util.List;

/**
 * 尸潮领主四变体专属主动战技（全程零方块破坏，带 16 tick 蓄力前摇与反垫高跳跃突进）：
 * <ul>
 *   <li>{@link HordeBoss.Variant#BRUTE}（蛮横）—— 震地咆哮：双臂高举猛砸地面释放冲击环，玩家起跳可减免半数伤害；</li>
 *   <li>{@link HordeBoss.Variant#PLAGUE}（瘟疫）—— 疫毒迷雾：扩散剧毒孢子环使周围玩家中毒虚弱，并加速周围友军；</li>
 *   <li>{@link HordeBoss.Variant#FROST}（寒霜）—— 凛冬霜环：释放寒冰冲击波叠加细雪冻伤与迟缓；</li>
 *   <li>{@link HordeBoss.Variant#INFERNO}（炼狱）—— 烈焰新星：引爆周身火焰新星点燃并震退近身玩家；</li>
 *   <li>反垫方块逃课突进：若玩家站在 2~5 格高柱上或中距风筝射击领主，蓄力阶段自动触发碰撞校验的泰坦跃击拉近并释放纵向冲击气浪。</li>
 * </ul>
 */
public class BossAbilityGoal extends Goal {

    public static final int TELEGRAPH_TICKS = 16;
    public static final int TOTAL_TICKS = 26;
    private static final double MELEE_TRIGGER_RANGE = 6.2;
    private static final double LEAP_TRIGGER_RANGE = 13.5;
    private static final double EFFECT_RADIUS = 6.4;
    private static final double VERTICAL_REACH_UP = 5.2;
    private static final double VERTICAL_REACH_DOWN = 3.2;

    private final HordeBoss boss;
    private int ticks;
    private int nextUseTick = 80;
    private boolean leapScheduled;

    public BossAbilityGoal(HordeBoss boss) {
        this.boss = boss;
        this.setFlags(EnumSet.of(Goal.Flag.MOVE, Goal.Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        if (SmartHordeConfig.SPEC.isLoaded() && !SmartHordeConfig.BOSS_ABILITIES_ENABLED.get()) {
            return false;
        }
        if (this.boss.tickCount < this.nextUseTick || !this.boss.onGround()) {
            return false;
        }
        if (this.boss.isExecutingAttack()) {
            return false;
        }
        LivingEntity target = this.boss.getTarget();
        if (target == null || !target.isAlive()) {
            return false;
        }
        double distSqr = this.boss.distanceToSqr(target);
        if (distSqr <= MELEE_TRIGGER_RANGE * MELEE_TRIGGER_RANGE) {
            return true;
        }
        boolean targetElevated = target.getY() - this.boss.getY() >= 2.0
                && target.getY() - this.boss.getY() <= VERTICAL_REACH_UP;
        boolean rangedHarass = this.boss.getLastHurtByMob() == target && this.boss.hurtTime > 0;
        return distSqr <= LEAP_TRIGGER_RANGE * LEAP_TRIGGER_RANGE && (targetElevated || rangedHarass);
    }

    @Override
    public boolean canContinueToUse() {
        return this.ticks < TOTAL_TICKS && this.boss.isAlive();
    }

    @Override
    public void start() {
        this.ticks = 0;
        LivingEntity target = this.boss.getTarget();
        this.leapScheduled = target != null
                && (this.boss.distanceToSqr(target) > MELEE_TRIGGER_RANGE * MELEE_TRIGGER_RANGE
                || target.getY() - this.boss.getY() >= 2.0);
        this.boss.getNavigation().stop();
        this.boss.setAttackId(TacticalAttacker.ATTACK_SPECIAL);
        this.boss.setAttackTicks(0);
        if (this.boss.level() instanceof ServerLevel serverLevel) {
            EffectManager.playSfx(serverLevel, this.boss, ModSounds.WAR_CRY.get(), 1.5F, 0.85F);
        }
    }

    @Override
    public void tick() {
        LivingEntity target = this.boss.getTarget();
        if (target != null) {
            this.boss.getLookControl().setLookAt(target, 30.0F, 30.0F);
        }
        this.boss.getNavigation().stop();
        this.boss.setAttackTicks(this.ticks);

        if (this.leapScheduled && this.ticks == 8 && target != null) {
            tryTitanLeapTowardTarget(target);
        }

        if (this.boss.level() instanceof ServerLevel serverLevel) {
            if (this.ticks < TELEGRAPH_TICKS && this.ticks % 3 == 0) {
                ParticleOptions cue = telegraphParticle(this.boss.getVariant());
                double radius = 1.2 + (this.ticks / (double) TELEGRAPH_TICKS) * 1.8;
                EffectManager.spawnRingParticles(serverLevel,
                        this.boss.position().add(0, 0.25, 0), cue, radius, 14, 0.02);
            } else if (this.ticks == TELEGRAPH_TICKS) {
                unleashVariantSkill(serverLevel);
            }
        }
        this.ticks++;
    }

    @Override
    public void stop() {
        this.leapScheduled = false;
        this.boss.setAttackId(TacticalAttacker.ATTACK_NONE);
        this.boss.setAttackTicks(0);
        int phaseBonus = Math.min(60, this.boss.getPhase() * 20);
        this.nextUseTick = this.boss.tickCount + (170 - phaseBonus);
    }

    /** 碰撞安全校验的泰坦跃击：停在目标身前安全净距处，绝不卡入头顶天花板或墙体。 */
    private void tryTitanLeapTowardTarget(LivingEntity target) {
        if (!this.boss.onGround()) {
            return;
        }
        BlockPos aboveHead = BlockPos.containing(
                this.boss.getX(), this.boss.getY() + this.boss.getBbHeight() + 0.8, this.boss.getZ());
        if (!this.boss.level().getBlockState(aboveHead).getCollisionShape(this.boss.level(), aboveHead).isEmpty()) {
            return;
        }
        Vec3 diff = new Vec3(target.getX() - this.boss.getX(), 0, target.getZ() - this.boss.getZ());
        double horizDist = diff.length();
        if (horizDist < 1.2) {
            return;
        }
        Vec3 dir = diff.scale(1.0 / horizDist);
        if (!this.boss.level().noCollision(this.boss,
                this.boss.getBoundingBox().move(dir.x * 0.9, 0.45, dir.z * 0.9))) {
            return;
        }
        double leapHoriz = Mth.clamp((horizDist - 2.2) * 0.14, 0.28, 0.92);
        double leapVert = target.getY() - this.boss.getY() >= 2.0 ? 0.56 : 0.42;
        this.boss.setDeltaMovement(dir.x * leapHoriz, leapVert, dir.z * leapHoriz);
        this.boss.hasImpulse = true;
    }

    private void unleashVariantSkill(ServerLevel level) {
        HordeBoss.Variant variant = this.boss.getVariant();
        Vec3 origin = this.boss.position().add(0, 0.35, 0);
        ParticleOptions ringParticle = telegraphParticle(variant);

        EffectManager.spawnRingParticles(level, origin, ringParticle, EFFECT_RADIUS * 0.55, 18, 0.04);
        EffectManager.spawnRingParticles(level, origin, ringParticle, EFFECT_RADIUS, 26, 0.06);
        EffectManager.playSfx(level, this.boss, ModSounds.SMART_ATTACK.get(), 1.4F, 0.65F);

        float baseDamage = (float) this.boss.getAttributeValue(Attributes.ATTACK_DAMAGE);
        LivingEntity primaryTarget = this.boss.getTarget();
        List<LivingEntity> victims = level.getEntitiesOfClass(LivingEntity.class,
                this.boss.getBoundingBox().inflate(EFFECT_RADIUS, VERTICAL_REACH_UP, EFFECT_RADIUS),
                e -> e != this.boss && e.isAlive() && !e.isSpectator()
                        && !(e instanceof Player p && p.isCreative())
                        && (e == primaryTarget || e instanceof Player)
                        && !(e instanceof Zombie)
                        && !this.boss.isAlliedTo(e));

        for (LivingEntity victim : victims) {
            Vec3 delta = new Vec3(victim.getX() - this.boss.getX(), 0, victim.getZ() - this.boss.getZ());
            double dist = delta.length();
            double dy = victim.getY() - this.boss.getY();
            if (dist > EFFECT_RADIUS || dy > VERTICAL_REACH_UP || dy < -VERTICAL_REACH_DOWN) {
                continue;
            }
            Vec3 pushDir = dist < 1.0E-4 ? new Vec3(1, 0, 0) : delta.scale(1.0 / dist);

            switch (variant) {
                case BRUTE -> {
                    // 正常平地起跳可规避半伤；但站在高柱顶端（onGround == true）仍承受完整震波冲击并被震落！
                    float mult = victim.onGround() ? 0.90F : 0.45F;
                    if (victim.hurt(this.boss.damageSources().mobAttack(this.boss), baseDamage * mult)) {
                        victim.knockback(1.15, -pushDir.x, -pushDir.z);
                        victim.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 50, 0));
                    }
                }
                case PLAGUE -> {
                    if (victim.hurt(this.boss.damageSources().mobAttack(this.boss), baseDamage * 0.55F)) {
                        victim.addEffect(new MobEffectInstance(MobEffects.POISON, 140, 0));
                        victim.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 100, 0));
                    }
                }
                case FROST -> {
                    if (victim.hurt(this.boss.damageSources().mobAttack(this.boss), baseDamage * 0.65F)) {
                        victim.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 120, 1));
                        int maxFreeze = victim.getTicksRequiredToFreeze() + 60;
                        victim.setTicksFrozen(Math.min(maxFreeze, victim.getTicksFrozen() + 140));
                    }
                }
                case INFERNO -> {
                    if (victim.hurt(this.boss.damageSources().mobAttack(this.boss), baseDamage * 0.80F)) {
                        victim.setRemainingFireTicks(Math.max(victim.getRemainingFireTicks(), 120));
                        victim.knockback(0.85, -pushDir.x, -pushDir.z);
                    }
                }
            }
        }

        if (variant == HordeBoss.Variant.PLAGUE) {
            List<Zombie> minions = level.getEntitiesOfClass(Zombie.class,
                    this.boss.getBoundingBox().inflate(10.0, 4.0, 10.0),
                    z -> z != this.boss && z.isAlive());
            for (Zombie minion : minions) {
                minion.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 120, 0, false, true));
            }
        }
    }

    private static ParticleOptions telegraphParticle(HordeBoss.Variant variant) {
        return switch (variant) {
            case BRUTE -> ParticleTypes.CAMPFIRE_COSY_SMOKE;
            case PLAGUE -> ParticleTypes.SNEEZE;
            case FROST -> ParticleTypes.SNOWFLAKE;
            case INFERNO -> ParticleTypes.FLAME;
        };
    }
}
