package dev.smarthorde.entity;

import dev.smarthorde.config.DifficultyManager;
import dev.smarthorde.entity.ai.*;
import dev.smarthorde.entity.ai.combat.SmartTargetGoal;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;

import javax.annotation.Nullable;

/**
 * 示范智能怪物（轮2-6）。
 * 轮6新增：finalizeSpawn 中按当前难度缩放属性。
 */
public class SmartZombie extends Monster implements dev.smarthorde.entity.ai.IAttackMob {

    public static final EntityDataAccessor<Integer> DATA_ATTACK_ID =
            SynchedEntityData.defineId(SmartZombie.class, EntityDataSerializers.INT);
    public static final EntityDataAccessor<Integer> DATA_ATTACK_TICKS =
            SynchedEntityData.defineId(SmartZombie.class, EntityDataSerializers.INT);

    private SmartMeleeAttackGoal meleeGoal;

    public SmartZombie(EntityType<? extends SmartZombie> type, Level level) {
        super(type, level);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(DATA_ATTACK_ID, 0);
        builder.define(DATA_ATTACK_TICKS, 0);
    }

    public int getAttackId()              { return this.entityData.get(DATA_ATTACK_ID); }
    public void setAttackId(int id)       { this.entityData.set(DATA_ATTACK_ID, id); }
    public int getAttackTicks()           { return this.entityData.get(DATA_ATTACK_TICKS); }
    public void setAttackTicks(int ticks) { this.entityData.set(DATA_ATTACK_TICKS, ticks); }
    public SmartMeleeAttackGoal getMeleeGoal() { return meleeGoal; }

    /** [轮6] 出生时应用难度缩放。 */
    @Override
    public SpawnGroupData finalizeSpawn(ServerLevelAccessor level, DifficultyInstance difficulty,
                                        MobSpawnType spawnType, @Nullable SpawnGroupData spawnGroupData) {
        SpawnGroupData data = super.finalizeSpawn(level, difficulty, spawnType, spawnGroupData);
        DifficultyManager.applyTo(this);
        return data;
    }

    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(0, new FloatGoal(this));
        this.meleeGoal = new SmartMeleeAttackGoal(this);
        this.goalSelector.addGoal(1, this.meleeGoal);
        this.goalSelector.addGoal(2, new CoordinationGoal(this));
        this.goalSelector.addGoal(3, new EvadeGoal(this));
        this.goalSelector.addGoal(4, new SeparationGoal(this));
        this.goalSelector.addGoal(5, new FlankGoal(this));
        this.goalSelector.addGoal(6, new MaintainDistanceGoal(this));
        this.goalSelector.addGoal(7, new ClimbOrStackGoal(this));
        this.goalSelector.addGoal(8, new SmartOpenDoorGoal(this));
        this.goalSelector.addGoal(9, new WaterAvoidingRandomStrollGoal(this, 0.8D));
        // [轮11修复] 确保主动索敌玩家 + 被攻击反击
        this.targetSelector.addGoal(1, new net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal<>(this, net.minecraft.world.entity.player.Player.class, true));
        this.targetSelector.addGoal(2, new net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal(this));
        this.targetSelector.addGoal(3, new SmartTargetGoal(this));
    }
}
