package dev.smarthorde.entity;

import dev.smarthorde.config.DifficultyManager;
import dev.smarthorde.entity.ai.combat.SmartMeleeAttackGoal;
import dev.smarthorde.entity.ai.defense.DodgeGoal;
import dev.smarthorde.entity.ai.movement.ClimbOrStackGoal;
import dev.smarthorde.entity.ai.movement.FlankGoal;
import dev.smarthorde.entity.ai.movement.MaintainDistanceGoal;
import dev.smarthorde.entity.ai.movement.SeparationGoal;
import dev.smarthorde.init.ModLootTables;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.RandomSource;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
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
 * 核心怪物实体（完整 AI）。
 * 继承 Zombie 以复用 ZombieRenderer 渲染（渲染器要求实体为 Zombie 实例），
 * 同时通过覆写关闭原版的阳光灼烧与溺毙转化，交由 Smart AI 控制。
 */
public class SmartZombie extends Zombie {

    /** 当前攻击招式 id（0=无攻击，1=light，2=heavy，3=sweep），供客户端表现使用 */
    private static final EntityDataAccessor<Integer> ATTACK_ID =
            SynchedEntityData.defineId(SmartZombie.class, EntityDataSerializers.INT);
    /** 当前攻击阶段已进行 tick 数 */
    private static final EntityDataAccessor<Integer> ATTACK_TICKS =
            SynchedEntityData.defineId(SmartZombie.class, EntityDataSerializers.INT);

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
        builder.define(ATTACK_ID, 0);
        builder.define(ATTACK_TICKS, 0);
    }

    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(0, new FloatGoal(this));
        // 闪避优先级高于攻击：受击/被瞄准/弹射物来袭时打断当前动作
        this.goalSelector.addGoal(1, new DodgeGoal(this));
        this.goalSelector.addGoal(2, new SmartMeleeAttackGoal(this, 1.35, true));
        this.goalSelector.addGoal(3, new SeparationGoal(this));
        this.goalSelector.addGoal(4, new FlankGoal(this, 1.25));
        this.goalSelector.addGoal(5, new MaintainDistanceGoal(this, 1.1));
        this.goalSelector.addGoal(6, new ClimbOrStackGoal(this));
        this.goalSelector.addGoal(7, new WaterAvoidingRandomStrollGoal(this, 1.0));

        this.targetSelector.addGoal(1, new HurtByTargetGoal(this));
        this.targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, false));
    }

    public int getAttackId() {
        return this.entityData.get(ATTACK_ID);
    }

    public void setAttackId(int attackId) {
        this.entityData.set(ATTACK_ID, attackId);
    }

    public int getAttackTicks() {
        return this.entityData.get(ATTACK_TICKS);
    }

    public void setAttackTicks(int attackTicks) {
        this.entityData.set(ATTACK_TICKS, attackTicks);
    }

    @Override
    protected ResourceKey<LootTable> getDefaultLootTable() {
        return ModLootTables.SMART_ZOMBIE;
    }

    /** 亮度<=7 的自然生成规则（委托 Monster 标准规则）。 */
    public static boolean checkSmartZombieSpawnRules(EntityType<SmartZombie> type, LevelAccessor level,
                                                     MobSpawnType reason, BlockPos pos, RandomSource random) {
        return Monster.checkMonsterSpawnRules(type, (ServerLevelAccessor) level, reason, pos, random);
    }

    @Override
    public boolean isSunSensitive() {
        // 智能僵尸不会在白天被阳光烧死，保持尸潮挑战的连续性
        return false;
    }

    @Override
    protected boolean convertsInWater() {
        // 不转化为溺尸
        return false;
    }

    @Nullable
    @Override
    public SpawnGroupData finalizeSpawn(ServerLevelAccessor level, DifficultyInstance difficulty,
                                        MobSpawnType reason, @Nullable SpawnGroupData spawnData) {
        SpawnGroupData data = super.finalizeSpawn(level, difficulty, reason, spawnData);
        DifficultyManager.applyTo(this);
        return data;
    }
}
