package dev.smarthorde.entity;

import dev.smarthorde.entity.ai.BossMeleeAttackGoal;
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
import net.minecraft.world.entity.animal.IronGolem;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.npc.AbstractVillager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;

/**
 * 肉盾型 Boss「尸潮蛮兽」。300血/15伤，BossBar 红色。
 */
public class HordeBrute extends Monster implements IAttackMob {
    private static final EntityDataAccessor<Integer> DATA_ATTACK_ID =
            SynchedEntityData.defineId(HordeBrute.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> DATA_ATTACK_TICKS =
            SynchedEntityData.defineId(HordeBrute.class, EntityDataSerializers.INT);

    private final ServerBossEvent bossEvent = new ServerBossEvent(
            net.minecraft.network.chat.Component.translatable("entity.smarthorde.horde_brute"),
            BossBarColor.RED, BossBarOverlay.NOTCHED_20);

    public HordeBrute(EntityType<? extends HordeBrute> type, Level level) { super(type, level); }

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
        goalSelector.addGoal(1, new BossMeleeAttackGoal(this));
        goalSelector.addGoal(2, new CoordinationGoal(this));
        goalSelector.addGoal(8, new WaterAvoidingRandomStrollGoal(this, 0.5D));
        targetSelector.addGoal(1, new NearestAttackableTargetGoal<>(this, Player.class, true));
        // [F4] 反击排除全体尸潮单位，防互殴反击闭环
        targetSelector.addGoal(2, new HurtByTargetGoal(this, SmartZombie.class, HordeBoss.class, HordeBrute.class, HordeArcher.class));
        targetSelector.addGoal(3, new SmartTargetGoal(this));
        // [F5] 对齐尸潮语义：补村民/铁傀儡索敌（排在玩家之后）
        targetSelector.addGoal(4, new NearestAttackableTargetGoal<>(this, AbstractVillager.class, false));
        targetSelector.addGoal(5, new NearestAttackableTargetGoal<>(this, IronGolem.class, true));
    }

    @Override
    public void customServerAiStep() {
        super.customServerAiStep();
        bossEvent.setProgress(getHealth() / getMaxHealth());
    }
    @Override public void startSeenByPlayer(ServerPlayer p) { super.startSeenByPlayer(p); bossEvent.addPlayer(p); }
    @Override public void stopSeenByPlayer(ServerPlayer p) { super.stopSeenByPlayer(p); bossEvent.removePlayer(p); }
    @Override public void remove(RemovalReason r) { super.remove(r); bossEvent.removeAllPlayers(); }
    @Override protected SoundEvent getAmbientSound() { return SoundEvents.RAVAGER_AMBIENT; }
    @Override protected SoundEvent getHurtSound(net.minecraft.world.damagesource.DamageSource s) { return SoundEvents.RAVAGER_HURT; }
    @Override protected SoundEvent getDeathSound() { return SoundEvents.RAVAGER_DEATH; }
}
