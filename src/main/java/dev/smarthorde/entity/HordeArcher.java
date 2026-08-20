package dev.smarthorde.entity;

import dev.smarthorde.entity.ai.CoordinationGoal;
import dev.smarthorde.entity.ai.IAttackMob;
import dev.smarthorde.entity.ai.combat.SmartTargetGoal;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.BossEvent.BossBarColor;
import net.minecraft.world.BossEvent.BossBarOverlay;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;

/**
 * 远程型 Boss「尸潮弓手」。120血，BossBar 绿色。
 */
public class HordeArcher extends Monster implements IAttackMob {
    private static final EntityDataAccessor<Integer> DATA_ATTACK_ID =
            SynchedEntityData.defineId(HordeArcher.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> DATA_ATTACK_TICKS =
            SynchedEntityData.defineId(HordeArcher.class, EntityDataSerializers.INT);

    private final ServerBossEvent bossEvent = new ServerBossEvent(
            net.minecraft.network.chat.Component.literal("尸潮弓手"),
            BossBarColor.GREEN, BossBarOverlay.NOTCHED_10);

    public HordeArcher(EntityType<? extends HordeArcher> type, Level level) { super(type, level); }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(DATA_ATTACK_ID, 0);
        builder.define(DATA_ATTACK_TICKS, 0);
    }
    @Override public int getAttackId() { return entityData.get(DATA_ATTACK_ID); }
    @Override public void setAttackId(int id) { entityData.set(DATA_ATTACK_ID, id); }
    @Override public int getAttackTicks() { return entityData.get(DATA_ATTACK_TICKS); }
    @Override public void setAttackTicks(int t) { entityData.set(DATA_ATTACK_TICKS, t); }

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(0, new FloatGoal(this));
        // 简单远程：靠近到 15 格内射箭
        goalSelector.addGoal(1, new dev.smarthorde.entity.ai.ArcherAttackGoal(this, 0.8D, 15.0F, 20));
        goalSelector.addGoal(2, new CoordinationGoal(this));
        goalSelector.addGoal(8, new WaterAvoidingRandomStrollGoal(this, 0.6D));
        targetSelector.addGoal(1, new NearestAttackableTargetGoal<>(this, Player.class, true));
        targetSelector.addGoal(2, new HurtByTargetGoal(this));
        targetSelector.addGoal(3, new SmartTargetGoal(this));
    }

    @Override
    public void customServerAiStep() {
        super.customServerAiStep();
        bossEvent.setProgress(getHealth() / getMaxHealth());
    }
    @Override public void startSeenByPlayer(ServerPlayer p) { super.startSeenByPlayer(p); bossEvent.addPlayer(p); }
    @Override public void stopSeenByPlayer(ServerPlayer p) { super.stopSeenByPlayer(p); bossEvent.removePlayer(p); }
    @Override public void remove(RemovalReason r) { super.remove(r); bossEvent.removeAllPlayers(); }
    @Override protected SoundEvent getAmbientSound() { return SoundEvents.SKELETON_AMBIENT; }
    @Override protected SoundEvent getHurtSound(net.minecraft.world.damagesource.DamageSource s) { return SoundEvents.SKELETON_HURT; }
    @Override protected SoundEvent getDeathSound() { return SoundEvents.SKELETON_DEATH; }
}
