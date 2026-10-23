package dev.smarthorde.entity;

import dev.smarthorde.config.DifficultyManager;
import dev.smarthorde.config.SmartHordeConfig;
import dev.smarthorde.entity.ai.combat.BossAbilityGoal;
import dev.smarthorde.entity.ai.combat.BossPhaseManager;
import dev.smarthorde.entity.ai.combat.SmartMeleeAttackGoal;
import dev.smarthorde.entity.ai.combat.SmartTargetGoal;
import dev.smarthorde.entity.ai.movement.PeacefulStrollGoal;
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
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.BossEvent;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.storage.loot.LootTable;

import javax.annotation.Nullable;

/**
 * 尸潮领主 Boss 实体：
 * 阶段化（{@link BossPhaseManager}）+ BossBar + 四大元素变体 + 招式化近战 + 变体主动战技（{@link BossAbilityGoal}），
 * 同步招式进度与战斗警戒态供 {@code HordeBossModel} 渲染泰坦级立体骨骼与怒吼动画。
 */
public class HordeBoss extends Zombie implements TacticalAttacker {

    /** Boss 变体定义 */
    public enum Variant {
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

        public String getSerializedName() {
            return this.id;
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
    private static final EntityDataAccessor<Integer> ATTACK_ID =
            SynchedEntityData.defineId(HordeBoss.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> ATTACK_TICKS =
            SynchedEntityData.defineId(HordeBoss.class, EntityDataSerializers.INT);

    private static final int SUMMON_COOLDOWN_TICKS = 100;
    private static final int AURA_INTERVAL_TICKS = 30;
    private static final String TAG_STATS_INIT = "BossStatsInitialized";

    private final ServerBossEvent bossBar;
    private final BossPhaseManager phaseManager = new BossPhaseManager(this);

    private int lastSummonTick = -SUMMON_COOLDOWN_TICKS;
    private boolean variantLocked;
    private boolean statsInitialized;

    public HordeBoss(EntityType<? extends Zombie> type, Level level) {
        super(type, level);
        this.xpReward = 120;
        this.setPersistenceRequired();
        this.bossBar = new ServerBossEvent(this.getDisplayName(), BossEvent.BossBarColor.WHITE, BossEvent.BossBarOverlay.PROGRESS);
        this.bossBar.setVisible(true);
        this.phaseManager.addPhaseListener(this::onPhaseChanged);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Zombie.createAttributes()
                .add(Attributes.MAX_HEALTH, 200.0)
                .add(Attributes.MOVEMENT_SPEED, 0.30)
                .add(Attributes.ATTACK_DAMAGE, 10.0)
                .add(Attributes.ATTACK_SPEED, 1.0)
                .add(Attributes.ARMOR, 10.0)
                .add(Attributes.KNOCKBACK_RESISTANCE, 0.8)
                .add(Attributes.FOLLOW_RANGE, 64.0)
                .add(Attributes.STEP_HEIGHT, 1.05);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(PHASE, 0);
        builder.define(VARIANT_INDEX, Variant.BRUTE.ordinal());
        builder.define(ATTACK_ID, TacticalAttacker.ATTACK_NONE);
        builder.define(ATTACK_TICKS, 0);
    }

    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(0, new FloatGoal(this));
        this.goalSelector.addGoal(1, new BossAbilityGoal(this));
        this.goalSelector.addGoal(2, new SmartMeleeAttackGoal(this, 1.15, true));
        this.goalSelector.addGoal(7, new PeacefulStrollGoal(this, 1.0));

        // 忽略友方仆从误碰，被玩家攻击时召唤周围智能僵尸协同围攻
        this.targetSelector.addGoal(1, new HurtByTargetGoal(this, SmartZombie.class).setAlertOthers(SmartZombie.class));
        this.targetSelector.addGoal(2, new SmartTargetGoal(this));
    }

    // ---------------- TacticalAttacker ----------------

    @Override
    public int getAttackId() {
        return this.entityData.get(ATTACK_ID);
    }

    @Override
    public void setAttackId(int attackId) {
        this.entityData.set(ATTACK_ID, attackId);
    }

    @Override
    public int getAttackTicks() {
        return this.entityData.get(ATTACK_TICKS);
    }

    @Override
    public void setAttackTicks(int attackTicks) {
        this.entityData.set(ATTACK_TICKS, attackTicks);
    }

    public int getAttackAnimType() {
        return getAttackId();
    }

    public int getAttackAnimTick() {
        return getAttackTicks();
    }

    // ---------------- 变体 ----------------

    public Variant getVariant() {
        return Variant.byOrdinal(this.entityData.get(VARIANT_INDEX));
    }

    public void setVariant(Variant variant) {
        this.entityData.set(VARIANT_INDEX, variant.ordinal());
        this.variantLocked = true;
        this.bossBar.setName(Component.translatable(variant.nameKey()));
        this.bossBar.setColor(variantPhaseColor(getPhase()));
    }

    @Override
    public Component getName() {
        return this.hasCustomName() ? super.getName() : Component.translatable(this.getVariant().nameKey());
    }

    public BossEvent.BossBarColor variantPhaseColor(int phase) {
        Variant variant = this.getVariant();
        return variant.colors[Math.min(Math.max(phase, 0), variant.colors.length - 1)];
    }

    // ---------------- 主循环 ----------------

    @Override
    public void tick() {
        super.tick();
        if (this.getVariant() == Variant.INFERNO && this.isOnFire()) {
            this.clearFire();
        }
        if (!this.level().isClientSide && this.isAlive()) {
            if (!this.statsInitialized) {
                initializeBossStatsOnce();
            }
            boolean activeTarget = this.getTarget() != null && this.getTarget().isAlive();
            if (this.isAggressive() != activeTarget) {
                this.setAggressive(activeTarget);
            }
        }
        if (this.isAlive() && (this.isAggressive() || this.getTarget() != null || isExecutingAttack())) {
            float yawDelta = Mth.wrapDegrees(this.yHeadRot - this.yBodyRot);
            if (Math.abs(yawDelta) > 38.0F) {
                this.yBodyRot += Mth.clamp(yawDelta, -18.0F, 18.0F);
            }
        }
        if (this.level() instanceof ServerLevel serverLevel && this.isAlive()) {
            this.bossBar.setProgress(this.getHealth() / Math.max(1.0F, this.getMaxHealth()));
            this.bossBar.setVisible(SmartHordeConfig.isBossBarEnabledSafe());
            this.phaseManager.tick();
            spawnAura(serverLevel);
        }
    }

    private void spawnAura(ServerLevel level) {
        ParticleOptions particle = this.getVariant().auraParticle;
        if (particle == null || !SmartHordeConfig.isParticlesEnabledSafe()
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

    public int getCurrentPhase() {
        return getPhase();
    }

    private void setPhase(int phase) {
        this.entityData.set(PHASE, phase);
    }

    public ServerBossEvent getBossBar() {
        return this.bossBar;
    }

    public void onPhaseChanged(int newPhase) {
        setPhase(newPhase);
        this.bossBar.setColor(variantPhaseColor(newPhase));

        if (!(this.level() instanceof ServerLevel serverLevel)
                || (SmartHordeConfig.SPEC.isLoaded() && !SmartHordeConfig.BOSS_SUMMONS_HORDE_ON_PHASE.get())) {
            return;
        }
        if (this.tickCount - this.lastSummonTick < SUMMON_COOLDOWN_TICKS) {
            return;
        }
        this.lastSummonTick = this.tickCount;

        int minions = this.getVariant().minionsBase + newPhase * 2;
        BlockPos center = this.blockPosition();
        var spawned = HordeWaveSpawner.spawnWave(serverLevel, center, newPhase, minions, 5.0, 11.0);
        LivingEntity currentTarget = this.getTarget();
        if (currentTarget != null && currentTarget.isAlive()) {
            for (SmartZombie minion : spawned) {
                minion.setTarget(currentTarget);
            }
        }

        if (SmartHordeConfig.isParticlesEnabledSafe()) {
            serverLevel.sendParticles(ParticleTypes.FLAME,
                    this.getX(), this.getY() + this.getBbHeight() * 0.6, this.getZ(),
                    Math.min(40, SmartHordeConfig.maxParticlesSafe()), 1.2, 1.0, 1.2, 0.05);
        }
        if (SmartHordeConfig.isSoundsEnabledSafe()) {
            serverLevel.playSound(null, this.getX(), this.getY(), this.getZ(),
                    ModSounds.BOSS_PHASE_CHANGE.get(), SoundSource.HOSTILE, 2.0F, 1.0F);
        }
    }

    @Override
    public boolean doHurtTarget(Entity target) {
        boolean hurt = super.doHurtTarget(target);
        if (hurt && target instanceof LivingEntity living) {
            applyVariantHitEffect(living);
        }
        return hurt;
    }

    /** 施加变体命中附带效果（供普通攻击与招式化攻击共用）。 */
    public void applyVariantHitEffect(LivingEntity living) {
        switch (this.getVariant().hitEffect) {
            case KNOCKBACK ->
                    living.knockback(0.8, this.getX() - living.getX(), this.getZ() - living.getZ());
            case POISON -> living.addEffect(new MobEffectInstance(MobEffects.POISON, 120, 0));
            case SLOWNESS -> living.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 100, 1));
            case FIRE -> living.setRemainingFireTicks(Math.max(living.getRemainingFireTicks(), 100));
            case NONE -> {
            }
        }
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
    public boolean canPickUpLoot() {
        return false;
    }

    @Override
    public boolean isSunSensitive() {
        return false;
    }

    @Override
    public boolean fireImmune() {
        return this.getVariant() == Variant.INFERNO || super.fireImmune();
    }

    @Override
    public boolean canFreeze() {
        return this.getVariant() != Variant.FROST && super.canFreeze();
    }

    @Override
    protected boolean convertsInWater() {
        return false;
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putInt("Variant", this.getVariant().ordinal());
        tag.putBoolean(TAG_STATS_INIT, this.statsInitialized);
        this.phaseManager.saveToTag(tag);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        this.phaseManager.loadFromTag(tag);
        setPhase(this.phaseManager.getPhase());
        setVariant(Variant.byOrdinal(tag.getInt("Variant")));
        this.statsInitialized = tag.getBoolean(TAG_STATS_INIT);
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
        // 严禁原版 Zombie.finalizeSpawn 5% 概率将领主变成幼年小僵尸或穿戴随机皮甲
        this.setBaby(false);
        for (EquipmentSlot slot : EquipmentSlot.values()) {
            this.setItemSlot(slot, ItemStack.EMPTY);
            this.setDropChance(slot, 0.0F);
        }

        initializeBossStatsOnce();
        return data;
    }

    private void initializeBossStatsOnce() {
        if (this.statsInitialized) {
            return;
        }
        this.statsInitialized = true;
        this.setBaby(false);
        DifficultyManager.applyTo(this);

        Variant variant = this.getVariant();
        double bossHpMul = SmartHordeConfig.SPEC.isLoaded()
                ? SmartHordeConfig.BOSS_HEALTH_MULTIPLIER.get()
                : 1.0;
        AttributeInstance health = this.getAttribute(Attributes.MAX_HEALTH);
        if (health != null) {
            health.setBaseValue(health.getBaseValue() * bossHpMul * variant.healthMul);
            this.setHealth(this.getMaxHealth());
        }
        scale(Attributes.ATTACK_DAMAGE, variant.damageMul);
        scale(Attributes.ATTACK_SPEED, variant.attackSpeedMul);
        AttributeInstance armor = this.getAttribute(Attributes.ARMOR);
        if (armor != null) {
            armor.setBaseValue(armor.getBaseValue() + variant.armorBonus);
        }
    }

    private void scale(net.minecraft.core.Holder<net.minecraft.world.entity.ai.attributes.Attribute> attribute,
                       double multiplier) {
        AttributeInstance instance = this.getAttribute(attribute);
        if (instance != null) {
            instance.setBaseValue(instance.getBaseValue() * multiplier);
        }
    }
}
