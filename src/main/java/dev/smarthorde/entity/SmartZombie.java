package dev.smarthorde.entity;

import dev.smarthorde.config.DifficultyManager;
import dev.smarthorde.config.SmartHordeConfig;
import dev.smarthorde.entity.ai.combat.CommanderAuraGoal;
import dev.smarthorde.entity.ai.combat.SmartMeleeAttackGoal;
import dev.smarthorde.entity.ai.combat.SmartTargetGoal;
import dev.smarthorde.entity.ai.defense.DodgeGoal;
import dev.smarthorde.entity.ai.defense.ShieldGuardGoal;
import dev.smarthorde.entity.ai.movement.ClimbOrStackGoal;
import dev.smarthorde.entity.ai.movement.FlankGoal;
import dev.smarthorde.entity.ai.movement.MaintainDistanceGoal;
import dev.smarthorde.entity.ai.movement.PeacefulStrollGoal;
import dev.smarthorde.entity.ai.movement.SeparationGoal;
import dev.smarthorde.init.ModLootTables;
import dev.smarthorde.item.SmartBladeItem;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
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
import net.minecraft.world.item.AxeItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;
import java.util.List;

/**
 * 核心战术僵尸实体：
 * <ul>
 *   <li>支持五大兵种分化（{@link EliteRole}：普通/疾风突袭者/重装先锋/狂血战鬼/百夫长）；</li>
 *   <li>同步招式帧、攀墙、闪避、举盾、狂暴及持续战斗警戒态供 {@code SmartZombieModel} 驱动平滑骨骼动画；</li>
 *   <li>内建双端躯干-头部防反拧对齐与轻量碰撞检测软排斥，彻底消除多怪叠模、扭头穿模与脱战闪跳。</li>
 * </ul>
 */
public class SmartZombie extends Zombie implements TacticalAttacker {

    private static final EntityDataAccessor<Integer> ATTACK_ID =
            SynchedEntityData.defineId(SmartZombie.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> ATTACK_TICKS =
            SynchedEntityData.defineId(SmartZombie.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> ROLE_INDEX =
            SynchedEntityData.defineId(SmartZombie.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Boolean> WALL_CLIMBING =
            SynchedEntityData.defineId(SmartZombie.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Integer> DODGE_ANIM_TICKS =
            SynchedEntityData.defineId(SmartZombie.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Boolean> SHIELD_BLOCKING =
            SynchedEntityData.defineId(SmartZombie.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Boolean> FRENZY_ACTIVE =
            SynchedEntityData.defineId(SmartZombie.class, EntityDataSerializers.BOOLEAN);

    private static final String TAG_ELITE_ROLE = "EliteRole";
    private static final String TAG_STATS_INIT = "SmartStatsInitialized";

    private boolean roleLocked;
    private boolean statsInitialized;
    private int shieldDisabledUntilTick;

    public SmartZombie(EntityType<? extends Zombie> type, Level level) {
        super(type, level);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Zombie.createAttributes()
                .add(Attributes.MAX_HEALTH, 24.0)
                .add(Attributes.MOVEMENT_SPEED, 0.26)
                .add(Attributes.ATTACK_DAMAGE, 4.0)
                .add(Attributes.ATTACK_SPEED, 1.2)
                .add(Attributes.FOLLOW_RANGE, 40.0)
                .add(Attributes.ARMOR, 4.0)
                .add(Attributes.KNOCKBACK_RESISTANCE, 0.2);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(ATTACK_ID, TacticalAttacker.ATTACK_NONE);
        builder.define(ATTACK_TICKS, 0);
        builder.define(ROLE_INDEX, EliteRole.STANDARD.ordinal());
        builder.define(WALL_CLIMBING, false);
        builder.define(DODGE_ANIM_TICKS, 0);
        builder.define(SHIELD_BLOCKING, false);
        builder.define(FRENZY_ACTIVE, false);
    }

    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(0, new FloatGoal(this));
        this.goalSelector.addGoal(1, new DodgeGoal(this));
        this.goalSelector.addGoal(2, new CommanderAuraGoal(this));
        this.goalSelector.addGoal(3, new ClimbOrStackGoal(this));
        this.goalSelector.addGoal(4, new ShieldGuardGoal(this));
        this.goalSelector.addGoal(5, new SmartMeleeAttackGoal(this, 1.35, true));
        this.goalSelector.addGoal(6, new SeparationGoal(this));
        this.goalSelector.addGoal(7, new FlankGoal(this, 1.25));
        this.goalSelector.addGoal(8, new MaintainDistanceGoal(this, 1.1));
        this.goalSelector.addGoal(9, new PeacefulStrollGoal(this, 1.0));

        // 友军免误伤联动：被攻击时呼叫周围同类增援，且不攻击 HordeBoss
        this.targetSelector.addGoal(1, new HurtByTargetGoal(this, HordeBoss.class).setAlertOthers(SmartZombie.class));
        this.targetSelector.addGoal(2, new SmartTargetGoal(this));
    }

    // ---------------- 同步状态读写 ----------------

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

    /** 兼容渲染层统一调用接口 */
    public int getAttackAnimType() {
        return getAttackId();
    }

    public int getAttackAnimTick() {
        return getAttackTicks();
    }

    public EliteRole getEliteRole() {
        return EliteRole.byOrdinal(this.entityData.get(ROLE_INDEX));
    }

    public void setEliteRole(EliteRole role) {
        this.entityData.set(ROLE_INDEX, role.ordinal());
        this.roleLocked = true;
    }

    public boolean isWallClimbing() {
        return this.entityData.get(WALL_CLIMBING);
    }

    public void setWallClimbing(boolean climbing) {
        this.entityData.set(WALL_CLIMBING, climbing);
    }

    public int getDodgeAnimTicks() {
        return this.entityData.get(DODGE_ANIM_TICKS);
    }

    public void triggerDodgeAnim(int ticks) {
        this.entityData.set(DODGE_ANIM_TICKS, Math.max(0, ticks));
    }

    public boolean isShieldBlocking() {
        return this.entityData.get(SHIELD_BLOCKING);
    }

    public void setShieldBlocking(boolean blocking) {
        this.entityData.set(SHIELD_BLOCKING, blocking);
    }

    public boolean isShieldDisabled() {
        return this.tickCount < this.shieldDisabledUntilTick;
    }

    public void disableShieldGuard(int durationTicks) {
        this.shieldDisabledUntilTick = this.tickCount + durationTicks;
    }

    public boolean hasOffhandShield() {
        return this.getOffhandItem().is(Items.SHIELD);
    }

    public boolean isFrenzyActive() {
        return this.entityData.get(FRENZY_ACTIVE);
    }

    @Override
    public Component getName() {
        if (this.hasCustomName()) {
            return super.getName();
        }
        return Component.translatable(this.getEliteRole().nameKey());
    }

    // ---------------- 每帧更新：同步警戒态 + 双端防反拧对齐 + 防叠模软排斥 + 狂战状态 ----------------

    @Override
    public void aiStep() {
        super.aiStep();

        if (!this.level().isClientSide) {
            if (!this.statsInitialized && this.isAlive()) {
                initializeStatsAndGearOnce();
            }
            boolean activeTarget = this.getTarget() != null && this.getTarget().isAlive();
            if (this.isAggressive() != activeTarget) {
                this.setAggressive(activeTarget);
            }
            int dodgeLeft = getDodgeAnimTicks();
            if (dodgeLeft > 0) {
                this.entityData.set(DODGE_ANIM_TICKS, dodgeLeft - 1);
            }
            tickBerserkerFrenzy();
            applySoftCrowdRepulsion();
        }

        // 双端同步防反拧：客户端通过 isAggressive() 实时感知交战状态，限制躯干与头部最大夹角
        if (this.isAlive() && (this.isAggressive() || this.getTarget() != null
                || isExecutingAttack() || isShieldBlocking())) {
            float yawDelta = Mth.wrapDegrees(this.yHeadRot - this.yBodyRot);
            if (Math.abs(yawDelta) > 38.0F) {
                this.yBodyRot += Mth.clamp(yawDelta, -20.0F, 20.0F);
            }
        }
    }

    private void tickBerserkerFrenzy() {
        if (this.getEliteRole() != EliteRole.BERSERKER || !this.isAlive()) {
            if (isFrenzyActive()) {
                this.entityData.set(FRENZY_ACTIVE, false);
            }
            return;
        }
        boolean lowHp = this.getHealth() <= this.getMaxHealth() * 0.5F;
        if (lowHp != isFrenzyActive()) {
            this.entityData.set(FRENZY_ACTIVE, lowHp);
        }
        if (lowHp && this.tickCount % 30 == 0) {
            this.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 45, 0, false, false));
            if (this.level() instanceof ServerLevel serverLevel
                    && SmartHordeConfig.isParticlesEnabledSafe()) {
                serverLevel.sendParticles(ParticleTypes.CRIMSON_SPORE,
                        this.getX(), this.getY() + this.getBbHeight() * 0.65, this.getZ(),
                        4, 0.25, 0.3, 0.25, 0.02);
            }
        }
    }

    /**
     * 轻量防叠模软排斥：每 2 tick 检测半径 0.72 格内重叠的僵尸并施加带碰撞检测的微弱横向滑移，
     * 消除尸潮贴身混战时多只僵尸完全重合在同一坐标穿模的问题，且绝不推入墙体。
     */
    private void applySoftCrowdRepulsion() {
        if (!this.onGround() || this.isPassenger() || this.isWallClimbing()
                || ((this.tickCount + this.getId()) & 1) != 0) {
            return;
        }
        List<Zombie> overlapping = this.level().getEntitiesOfClass(Zombie.class,
                this.getBoundingBox().inflate(0.45, 0.2, 0.45),
                other -> other != this && other.isAlive() && !other.isPassenger());
        if (overlapping.isEmpty()) {
            return;
        }
        double pushX = 0.0;
        double pushZ = 0.0;
        int processed = 0;
        for (Zombie other : overlapping) {
            if (processed++ >= 4) {
                break;
            }
            double dx = this.getX() - other.getX();
            double dz = this.getZ() - other.getZ();
            double distSqr = dx * dx + dz * dz;
            if (distSqr < 1.0E-5) {
                double angle = (this.getId() * 2.399963229728653) % (Math.PI * 2.0);
                dx = Math.cos(angle) * 0.1;
                dz = Math.sin(angle) * 0.1;
                distSqr = dx * dx + dz * dz;
            }
            if (distSqr < 0.72 * 0.72) {
                double dist = Math.sqrt(distSqr);
                double factor = (0.72 - dist) * 0.08 / dist;
                pushX += dx * factor;
                pushZ += dz * factor;
            }
        }
        double mag = Math.hypot(pushX, pushZ);
        if (mag < 1.0E-4) {
            return;
        }
        if (mag > 0.06) {
            pushX = pushX / mag * 0.06;
            pushZ = pushZ / mag * 0.06;
        }
        if (this.level().noCollision(this, this.getBoundingBox().move(pushX, 0.02, pushZ))) {
            Vec3 cur = this.getDeltaMovement();
            this.setDeltaMovement(cur.x + pushX, cur.y, cur.z + pushZ);
        }
    }

    // ---------------- 重装先锋举盾格挡与斧头破盾机制 ----------------

    @Override
    public boolean hurt(DamageSource source, float amount) {
        if (!this.level().isClientSide && isShieldBlocking() && hasOffhandShield()
                && !isShieldDisabled() && !source.is(DamageTypeTags.BYPASSES_SHIELD)
                && isFrontalDamage(source)) {
            if (source.getDirectEntity() instanceof LivingEntity attacker) {
                boolean isAxe = attacker.getMainHandItem().getItem() instanceof AxeItem;
                boolean isVanguardBlade = attacker.getMainHandItem().getItem() instanceof SmartBladeItem;
                if (isAxe || isVanguardBlade) {
                    // 玩家持斧劈砍（5秒破盾）或持智潮破阵之刃（3.5秒破盾）
                    disableShieldGuard(isAxe ? 100 : 70);
                    setShieldBlocking(false);
                    this.playSound(SoundEvents.SHIELD_BREAK, 1.0F, 0.9F + this.random.nextFloat() * 0.2F);
                    if (this.level() instanceof ServerLevel serverLevel
                            && SmartHordeConfig.isParticlesEnabledSafe()) {
                        serverLevel.sendParticles(ParticleTypes.CRIT,
                                this.getX(), this.getY() + 1.1, this.getZ(), 10, 0.3, 0.3, 0.3, 0.1);
                    }
                    return super.hurt(source, amount * 0.65F);
                }
            }

            this.playSound(SoundEvents.SHIELD_BLOCK, 1.0F, 0.9F + this.random.nextFloat() * 0.2F);
            if (source.is(DamageTypeTags.IS_PROJECTILE)) {
                return false;
            }
            if (source.getDirectEntity() instanceof LivingEntity attacker) {
                Vec3 dir = this.position().subtract(attacker.position());
                attacker.knockback(0.28, dir.x, dir.z);
            }
            return super.hurt(source, amount * 0.30F);
        }
        return super.hurt(source, amount);
    }

    private boolean isFrontalDamage(DamageSource source) {
        Vec3 srcPos = source.getSourcePosition();
        if (srcPos == null) {
            return false;
        }
        Vec3 toSource = new Vec3(srcPos.x - this.getX(), 0.0, srcPos.z - this.getZ());
        if (toSource.lengthSqr() < 1.0E-4) {
            return true;
        }
        Vec3 forward = Vec3.directionFromRotation(0.0F, this.yBodyRot);
        Vec3 flatForward = new Vec3(forward.x, 0.0, forward.z).normalize();
        return flatForward.dot(toSource.normalize()) > 0.15;
    }

    // ---------------- 生成、兵种装备与 NBT 持久化 ----------------

    @Override
    protected ResourceKey<LootTable> getDefaultLootTable() {
        return ModLootTables.SMART_ZOMBIE;
    }

    public static boolean checkSmartZombieSpawnRules(EntityType<SmartZombie> type, LevelAccessor level,
                                                     MobSpawnType reason, BlockPos pos, RandomSource random) {
        return Monster.checkMonsterSpawnRules(type, (ServerLevelAccessor) level, reason, pos, random);
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
        tag.putInt(TAG_ELITE_ROLE, this.getEliteRole().ordinal());
        tag.putBoolean(TAG_STATS_INIT, this.statsInitialized);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        if (tag.contains(TAG_ELITE_ROLE)) {
            setEliteRole(EliteRole.byOrdinal(tag.getInt(TAG_ELITE_ROLE)));
        }
        this.statsInitialized = tag.getBoolean(TAG_STATS_INIT);
    }

    @Nullable
    @Override
    public SpawnGroupData finalizeSpawn(ServerLevelAccessor level, DifficultyInstance difficulty,
                                        MobSpawnType reason, @Nullable SpawnGroupData spawnData) {
        SpawnGroupData data = super.finalizeSpawn(level, difficulty, reason, spawnData);
        // 清空原版随机穿戴的皮甲/锁甲，确保专属 3D 兵种模型部件完整展现且不发生双层盔甲穿模
        this.setItemSlot(EquipmentSlot.HEAD, ItemStack.EMPTY);
        this.setItemSlot(EquipmentSlot.CHEST, ItemStack.EMPTY);
        this.setItemSlot(EquipmentSlot.LEGS, ItemStack.EMPTY);
        this.setItemSlot(EquipmentSlot.FEET, ItemStack.EMPTY);

        if (!this.roleLocked) {
            double chance = SmartHordeConfig.SPEC.isLoaded()
                    ? SmartHordeConfig.ELITE_SPAWN_CHANCE.get()
                    : 0.32;
            if (this.random.nextDouble() < chance) {
                setEliteRole(EliteRole.pickEliteByWeightRoll(
                        this.random.nextInt(EliteRole.totalEliteWeight())));
            } else {
                setEliteRole(EliteRole.STANDARD);
            }
        }
        if (this.getEliteRole().isElite()) {
            this.setBaby(false);
        }
        initializeStatsAndGearOnce();
        return data;
    }

    private void initializeStatsAndGearOnce() {
        if (this.statsInitialized) {
            return;
        }
        this.statsInitialized = true;
        if (this.getEliteRole().isElite()) {
            this.setBaby(false);
        }
        DifficultyManager.applyTo(this);
        applyEliteRoleStatsAndGear(this.getEliteRole());
    }

    private void applyEliteRoleStatsAndGear(EliteRole role) {
        if (role == EliteRole.STANDARD) {
            return;
        }
        this.xpReward = Math.max(this.xpReward, 12);

        AttributeInstance maxHealth = this.getAttribute(Attributes.MAX_HEALTH);
        if (maxHealth != null) {
            maxHealth.setBaseValue(maxHealth.getBaseValue() * role.healthMul);
            this.setHealth(this.getMaxHealth());
        }
        scaleAttribute(Attributes.ATTACK_DAMAGE, role.damageMul);
        scaleAttribute(Attributes.MOVEMENT_SPEED, role.speedMul);
        addAttribute(Attributes.ARMOR, role.armorBonus);
        addAttribute(Attributes.KNOCKBACK_RESISTANCE, role.knockbackResBonus);

        if (SmartHordeConfig.SPEC.isLoaded() && !SmartHordeConfig.ELITE_EQUIPMENT_ENABLED.get()) {
            return;
        }
        for (EquipmentSlot slot : EquipmentSlot.values()) {
            this.setDropChance(slot, 0.0F);
        }
        switch (role) {
            case RAIDER -> this.setItemSlot(EquipmentSlot.MAINHAND, ItemStack.EMPTY);
            case VANGUARD -> {
                this.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.STONE_SWORD));
                this.setItemSlot(EquipmentSlot.OFFHAND, new ItemStack(Items.SHIELD));
            }
            case BERSERKER -> this.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.IRON_AXE));
            case COMMANDER -> this.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.IRON_SWORD));
            default -> {
            }
        }
    }

    private void scaleAttribute(net.minecraft.core.Holder<net.minecraft.world.entity.ai.attributes.Attribute> attr,
                                double mul) {
        AttributeInstance inst = this.getAttribute(attr);
        if (inst != null) {
            inst.setBaseValue(inst.getBaseValue() * mul);
        }
    }

    private void addAttribute(net.minecraft.core.Holder<net.minecraft.world.entity.ai.attributes.Attribute> attr,
                              double bonus) {
        if (bonus == 0.0) {
            return;
        }
        AttributeInstance inst = this.getAttribute(attr);
        if (inst != null) {
            inst.setBaseValue(inst.getBaseValue() + bonus);
        }
    }
}
