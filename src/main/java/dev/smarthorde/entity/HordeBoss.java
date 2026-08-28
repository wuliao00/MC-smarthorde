package dev.smarthorde.entity;

import dev.smarthorde.config.DifficultyManager;
import dev.smarthorde.config.SmartHordeConfig;
import dev.smarthorde.entity.ai.combat.BossPhaseManager;
import dev.smarthorde.horde.HordeWaveSpawner;
import dev.smarthorde.init.ModLootTables;
import dev.smarthorde.init.ModSounds;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.BossEvent;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.storage.loot.LootTable;

import javax.annotation.Nullable;

/**
 * Boss 实体：阶段化（BossPhaseManager 按血量阈值切阶段）+ BossBar + 四变体。
 * 变体决定：属性倍率、命中附加效果、BossBar 配色、仆从数量、光环粒子与名称，
 * 变体索引经 SynchedEntityData 同步并写入 NBT 持久化。
 */
public class HordeBoss extends Zombie {

    /** Boss 变体定义 */
    public enum Variant {
        //        id        血量  伤害 攻速  护甲加成 仆从基数 命中效果
        BRUTE("brute", 1.00, 1.00, 1.00, 0, 4, HitEffect.KNOCKBACK,
                new BossEvent.BossBarColor[]{BossEvent.BossBarColor.WHITE, BossEvent.BossBarColor.BLUE, BossEvent.BossBarColor.PURPLE, BossEvent.BossBarColor.RED},
                null),
        PLAGUE("plague", 0.85, 0.85, 1.00, 2, 5, HitEffect.POISON,
                new BossEvent.BossBarColor[]{BossEvent.BossBarColor.GREEN, BossEvent.BossBarColor.YELLOW, BossEvent.BossBarColor.GREEN, BossEvent.BossBarColor.PURPLE},
                ParticleTypes.SNEEZE),
        FROST("frost", 1.15, 0.90, 0.90, 6, 4, HitEffect.SLOWNESS,
                new BossEvent.BossBarColor[]{BossEvent.BossBarColor.BLUE, BossEvent.BossBarColor.WHITE, BossEvent.BossBarColor.BLUE, BossEvent.BossBarColor.PURPLE},
                ParticleTypes.SNOWFLAKE),
        INFERNO("inferno", 1.00, 1.10, 1.25, 0, 4, HitEffect.FIRE,
                new BossEvent.BossBarColor[]{BossEvent.BossBarColor.RED, BossEvent.BossBarColor.YELLOW, BossEvent.BossBarColor.PURPLE, BossEvent.BossBarColor.PINK},
                ParticleTypes.SMALL_FLAME);

        private static final Variant[] ALL = values();

        public final String id;
        public final double healthMul;
        public final double damageMul;
        public final double attackSpeedMul;
        public final int armorBonus;
        public final int minionsBase;
        public final HitEffect hitEffect;
        public final BossEvent.BossBarColor[] colors;
        @Nullable
        public final ParticleOptions auraParticle;

        Variant(String id, double healthMul, double damageMul, double attackSpeedMul, int armorBonus,
                int minionsBase, HitEffect hitEffect, BossEvent.BossBarColor[] colors, @Nullable ParticleOptions auraParticle) {
            this.id = id;
            this.healthMul = healthMul;
            this.damageMul = damageMul;
            this.attackSpeedMul = attackSpeedMul;
            this.armorBonus = armorBonus;
            this.minionsBase = minionsBase;
            this.hitEffect = hitEffect;
            this.colors = colors;
            this.auraParticle = auraParticle;
        }

        public String nameKey() {
            return "entity.smarthorde.horde_boss." + this.id;
        }

        public static Variant byId(String id) {
            for (Variant variant : ALL) {
                if (variant.id.equalsIgnoreCase(id)) {
                    return variant;
                }
            }
            throw new IllegalArgumentException("Unknown variant: " + id);
        }

        public static Variant byOrdinal(int ordinal) {
            return ALL[Math.floorMod(ordinal, ALL.length)];
        }

        public static Variant random(RandomSource random) {
            return ALL[random.nextInt(ALL.length)];
        }
    }

    /** 命中附加效果类型 */
    public enum HitEffect {NONE, KNOCKBACK, POISON, SLOWNESS, FIRE}

    private static final EntityDataAccessor<Integer> PHASE =
            SynchedEntityData.defineId(HordeBoss.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> VARIANT_INDEX =
            SynchedEntityData.defineId(HordeBoss.class, EntityDataSerializers.INT);

    private static final int SUMMON_COOLDOWN_TICKS = 100; // 5 秒
    private static final int AURA_INTERVAL_TICKS = 30;

    private final ServerBossEvent bossBar;
    private final BossPhaseManager phaseManager = new BossPhaseManager(this);

    private int lastSummonTick = -SUMMON_COOLDOWN_TICKS;
    private boolean variantLocked;

    public HordeBoss(EntityType<? extends Zombie> type, Level level) {
        super(type, level);
        this.xpReward = 120;
        this.setPersistenceRequired();
        this.bossBar = new ServerBossEvent(this.getDisplayName(), BossEvent.BossBarColor.WHITE, BossEvent.BossBarOverlay.PROGRESS);
        this.bossBar.setVisible(true);
        this.phaseManager.addPhaseListener(this::onPhaseChanged);
    }

    public static AttributeSupplier.Builder createAttributes() {
        // 与文档对齐：200 血量 / 10 伤害 / 0.8 击退抗性 / 8 护甲
        return Zombie.createAttributes()
                .add(Attributes.MAX_HEALTH, 200.0)
                .add(Attributes.MOVEMENT_SPEED, 0.30)
                .add(Attributes.ATTACK_DAMAGE, 10.0)
                .add(Attributes.ATTACK_SPEED, 1.0)
                .add(Attributes.ARMOR, 8.0)
                .add(Attributes.KNOCKBACK_RESISTANCE, 0.8)
                .add(Attributes.FOLLOW_RANGE, 64.0)
                .add(Attributes.STEP_HEIGHT, 1.05);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(PHASE, 0);
        builder.define(VARIANT_INDEX, Variant.BRUTE.ordinal());
    }

    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(0, new FloatGoal(this));
        this.goalSelector.addGoal(2, new MeleeAttackGoal(this, 1.1, true));
        this.goalSelector.addGoal(7, new WaterAvoidingRandomStrollGoal(this, 1.0));

        this.targetSelector.addGoal(1, new HurtByTargetGoal(this));
        this.targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, false));
    }

    // ---------------- 变体 ----------------

    public Variant getVariant() {
        return Variant.byOrdinal(this.entityData.get(VARIANT_INDEX));
    }

    /** 设置变体并锁定（命令/NBT 调用；finalizeSpawn 前未锁定则随机）。 */
    public void setVariant(Variant variant) {
        this.entityData.set(VARIANT_INDEX, variant.ordinal());
        this.variantLocked = true;
        this.bossBar.setName(Component.translatable(variant.nameKey()));
    }

    @Override
    public Component getName() {
        return Component.translatable(this.getVariant().nameKey());
    }

    public BossEvent.BossBarColor variantPhaseColor(int phase) {
        Variant variant = this.getVariant();
        return variant.colors[Math.min(Math.max(phase, 0), variant.colors.length - 1)];
    }

    // ---------------- 主循环 ----------------

    @Override
    public void tick() {
        super.tick();
        if (this.level() instanceof ServerLevel serverLevel && this.isAlive()) {
            this.bossBar.setProgress(this.getHealth() / Math.max(1.0F, this.getMaxHealth()));
            this.bossBar.setVisible(SmartHordeConfig.BOSS_BAR_ENABLED.get());
            this.phaseManager.tick();
            spawnAura(serverLevel);
        }
    }

    /** 变体光环粒子。 */
    private void spawnAura(ServerLevel level) {
        ParticleOptions particle = this.getVariant().auraParticle;
        if (particle == null || !SmartHordeConfig.PARTICLES_ENABLED.get()
                || this.tickCount % AURA_INTERVAL_TICKS != 0) {
            return;
        }
        level.sendParticles(particle,
                this.getX(), this.getY() + this.getBbHeight() * 0.7, this.getZ(),
                4, this.getBbWidth() * 0.5, 0.4, this.getBbWidth() * 0.5, 0.01);
    }

    public int getPhase() {
        return this.entityData.get(PHASE);
    }

    private void setPhase(int phase) {
        this.entityData.set(PHASE, phase);
    }

    public ServerBossEvent getBossBar() {
        return this.bossBar;
    }

    /** BossPhaseManager 切阶段回调：BossBar 配色、攻速与仆从召唤（冷却 5 秒）。 */
    public void onPhaseChanged(int newPhase) {
        setPhase(newPhase);
        this.bossBar.setColor(variantPhaseColor(newPhase));

        if (!(this.level() instanceof ServerLevel serverLevel)
                || !SmartHordeConfig.BOSS_SUMMONS_HORDE_ON_PHASE.get()) {
            return;
        }
        if (this.tickCount - this.lastSummonTick < SUMMON_COOLDOWN_TICKS) {
            return;
        }
        this.lastSummonTick = this.tickCount;

        int minions = this.getVariant().minionsBase + newPhase * 2;
        BlockPos center = this.blockPosition();
        HordeWaveSpawner.spawnWave(serverLevel, center, 0, minions, 6.0, 12.0);

        serverLevel.sendParticles(ParticleTypes.FLAME,
                this.getX(), this.getY() + this.getBbHeight() * 0.6, this.getZ(),
                Math.min(40, SmartHordeConfig.MAX_PARTICLES.get()), 1.2, 1.0, 1.2, 0.05);
        serverLevel.playSound(null, this.getX(), this.getY(), this.getZ(),
                ModSounds.BOSS_PHASE_CHANGE.get(), SoundSource.HOSTILE, 2.0F, 1.0F);
    }

    /** 变体命中附加效果（MeleeAttackGoal 经 doHurtTarget 调用）。 */
    @Override
    public boolean doHurtTarget(Entity target) {
        boolean hurt = super.doHurtTarget(target);
        if (!hurt || !(target instanceof LivingEntity living)) {
            return hurt;
        }
        switch (this.getVariant().hitEffect) {
            case KNOCKBACK ->
                    living.knockback(0.8, this.getX() - living.getX(), this.getZ() - living.getZ());
            case POISON -> living.addEffect(new MobEffectInstance(MobEffects.POISON, 120, 0));
            case SLOWNESS -> living.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 100, 1));
            case FIRE -> living.setRemainingFireTicks(100);
            case NONE -> {
            }
        }
        return true;
    }

    // ---------------- 玩家可见性 / BossBar ----------------

    @Override
    public void startSeenByPlayer(ServerPlayer player) {
        super.startSeenByPlayer(player);
        this.bossBar.addPlayer(player);
    }

    @Override
    public void stopSeenByPlayer(ServerPlayer player) {
        super.stopSeenByPlayer(player);
        this.bossBar.removePlayer(player);
    }

    @Override
    public void die(DamageSource source) {
        this.bossBar.removeAllPlayers();
        super.die(source);
    }

    // ---------------- 生成 / 持久化 ----------------

    @Override
    protected ResourceKey<LootTable> getDefaultLootTable() {
        return ModLootTables.HORDE_BOSS;
    }

    @Override
    public boolean removeWhenFarAway(double distance) {
        return false;
    }

    @Override
    public boolean isSunSensitive() {
        return false;
    }

    @Override
    protected boolean convertsInWater() {
        return false;
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putInt("Variant", this.getVariant().ordinal());
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        setVariant(Variant.byOrdinal(tag.getInt("Variant")));
    }

    public static boolean checkHordeBossSpawnRules(EntityType<HordeBoss> type, LevelAccessor level,
                                                   MobSpawnType reason, BlockPos pos, RandomSource random) {
        return Monster.checkMonsterSpawnRules(type, (ServerLevelAccessor) level, reason, pos, random);
    }

    @Nullable
    @Override
    public SpawnGroupData finalizeSpawn(ServerLevelAccessor level, DifficultyInstance difficulty,
                                        MobSpawnType reason, @Nullable SpawnGroupData spawnData) {
        if (!this.variantLocked) {
            setVariant(Variant.random(this.random));
        }
        SpawnGroupData data = super.finalizeSpawn(level, difficulty, reason, spawnData);
        DifficultyManager.applyTo(this);

        Variant variant = this.getVariant();
        AttributeInstance health = this.getAttribute(Attributes.MAX_HEALTH);
        if (health != null) {
            health.setBaseValue(health.getBaseValue()
                    * SmartHordeConfig.BOSS_HEALTH_MULTIPLIER.get() * variant.healthMul);
            this.setHealth(this.getMaxHealth());
        }
        scale(Attributes.ATTACK_DAMAGE, variant.damageMul);
        scale(Attributes.ATTACK_SPEED, variant.attackSpeedMul);
        AttributeInstance armor = this.getAttribute(Attributes.ARMOR);
        if (armor != null) {
            armor.setBaseValue(armor.getBaseValue() + variant.armorBonus);
        }
        return data;
    }

    private void scale(net.minecraft.core.Holder<net.minecraft.world.entity.ai.attributes.Attribute> attribute,
                       double multiplier) {
        AttributeInstance instance = this.getAttribute(attribute);
        if (instance != null) {
            instance.setBaseValue(instance.getBaseValue() * multiplier);
        }
    }
}
