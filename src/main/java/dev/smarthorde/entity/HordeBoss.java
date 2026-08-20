package dev.smarthorde.entity;

import dev.smarthorde.config.DifficultyManager;
import dev.smarthorde.config.SmartHordeConfig;
import dev.smarthorde.entity.ai.BossMeleeAttackGoal;
import dev.smarthorde.entity.ai.CoordinationGoal;
import dev.smarthorde.entity.ai.SeparationGoal;
import dev.smarthorde.entity.ai.combat.BossPhaseManager;
import dev.smarthorde.entity.ai.combat.SmartTargetGoal;
import dev.smarthorde.horde.HordeLeaderboard;
import dev.smarthorde.horde.HordeWaveSpawner;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.BossEvent;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;

import javax.annotation.Nullable;
import java.util.List;

/**
 * Boss 级单位「尸潮领主」（轮10）。
 */
public class HordeBoss extends Monster implements dev.smarthorde.entity.ai.IAttackMob {

    public static final EntityDataAccessor<Integer> DATA_ATTACK_ID =
            SynchedEntityData.defineId(HordeBoss.class, EntityDataSerializers.INT);
    public static final EntityDataAccessor<Integer> DATA_ATTACK_TICKS =
            SynchedEntityData.defineId(HordeBoss.class, EntityDataSerializers.INT);
    public static final EntityDataAccessor<Integer> DATA_PHASE =
            SynchedEntityData.defineId(HordeBoss.class, EntityDataSerializers.INT);

    private final ServerBossEvent bossEvent;
    private BossPhaseManager phaseManager;
    private BossMeleeAttackGoal meleeGoal;
    private int phaseSummonCooldown = 0;

    public HordeBoss(EntityType<? extends HordeBoss> type, Level level) {
        super(type, level);
        this.bossEvent = new ServerBossEvent(
                Component.literal("尸潮领主"),
                BossEvent.BossBarColor.WHITE,
                BossEvent.BossBarOverlay.PROGRESS
        );
        this.bossEvent.setDarkenScreen(true);
        this.xpReward = 50;
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(DATA_ATTACK_ID, 0);
        builder.define(DATA_ATTACK_TICKS, 0);
        builder.define(DATA_PHASE, 0);
    }

    public int getAttackId()              { return this.entityData.get(DATA_ATTACK_ID); }
    public void setAttackId(int id)       { this.entityData.set(DATA_ATTACK_ID, id); }
    public int getAttackTicks()           { return this.entityData.get(DATA_ATTACK_TICKS); }
    public void setAttackTicks(int ticks) { this.entityData.set(DATA_ATTACK_TICKS, ticks); }
    public int getBossPhase()             { return this.entityData.get(DATA_PHASE); }
    public void setBossPhase(int phase)   { this.entityData.set(DATA_PHASE, phase); }
    public BossMeleeAttackGoal getMeleeGoal() { return meleeGoal; }

    @Override
    public SpawnGroupData finalizeSpawn(ServerLevelAccessor level, DifficultyInstance difficulty,
                                        MobSpawnType spawnType, @Nullable SpawnGroupData spawnGroupData) {
        SpawnGroupData data = super.finalizeSpawn(level, difficulty, spawnType, spawnGroupData);
        DifficultyManager.applyTo(this);

        double hpMul = SmartHordeConfig.BOSS_HEALTH_MUL.get();
        if (hpMul != 1.0D) {
            var attr = this.getAttribute(Attributes.MAX_HEALTH);
            if (attr != null) {
                attr.setBaseValue(attr.getBaseValue() * hpMul);
                this.setHealth(this.getMaxHealth());
            }
        }

        this.phaseManager = new BossPhaseManager(this, this::onPhaseChange);
        return data;
    }

    @Override
    public void aiStep() {
        super.aiStep();

        this.bossEvent.setProgress(this.getHealth() / this.getMaxHealth());

        if (phaseManager != null) {
            phaseManager.tick();
            if (phaseManager.getCurrentPhase() != getBossPhase()) {
                setBossPhase(phaseManager.getCurrentPhase());
            }
            this.bossEvent.setColor(phaseManager.getBarColor());
            // 同步阶段到 Boss 攻击控制器
            if (meleeGoal != null) {
                meleeGoal.setBossPhase(phaseManager.getCurrentPhase());
            }
        }

        if (phaseSummonCooldown > 0) phaseSummonCooldown--;
    }

    @Override
    public void startSeenByPlayer(ServerPlayer player) {
        super.startSeenByPlayer(player);
        if (SmartHordeConfig.BOSS_BAR.get()) {
            this.bossEvent.addPlayer(player);
        }
    }

    @Override
    public void stopSeenByPlayer(ServerPlayer player) {
        super.stopSeenByPlayer(player);
        this.bossEvent.removePlayer(player);
    }

    private void onPhaseChange(int oldPhase, int newPhase, Monster boss) {
        setBossPhase(newPhase);

        if (!SmartHordeConfig.BOSS_SUMMONS_HORDE.get()) return;
        if (phaseSummonCooldown > 0) return;
        phaseSummonCooldown = 100;

        if (!(level() instanceof ServerLevel sl)) return;

        int count = 4 + newPhase * 2;
        HordeWaveSpawner.spawnWave(sl, this.blockPosition(), newPhase, count);

        List<ServerPlayer> nearby = sl.getPlayers(p -> p.distanceTo(this) < 48.0D);
        for (ServerPlayer p : nearby) {
            p.sendSystemMessage(Component.literal(
                    "⚠ 尸潮领主进入第 " + (newPhase + 1) + " 阶段！召唤了 " + count + " 只仆从！")
                    .withStyle(ChatFormatting.DARK_RED));
        }
    }

    @Override
    public void die(net.minecraft.world.damagesource.DamageSource source) {
        super.die(source);
        this.bossEvent.removeAllPlayers();

        // [轮12] 排行榜：Boss 击杀统计
        if (level() instanceof ServerLevel sl) {
            HordeLeaderboard board = HordeLeaderboard.get(sl);
            board.addBossKill();
        }
    }

    @Override
    public void remove(RemovalReason reason) {
        super.remove(reason);
        this.bossEvent.removeAllPlayers();
    }

    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(0, new FloatGoal(this));
        this.meleeGoal = new BossMeleeAttackGoal(this);
        this.goalSelector.addGoal(1, this.meleeGoal);
        this.goalSelector.addGoal(2, new CoordinationGoal(this));
        this.goalSelector.addGoal(3, new SeparationGoal(this));
        this.goalSelector.addGoal(8, new WaterAvoidingRandomStrollGoal(this, 0.9D));
        this.targetSelector.addGoal(1, new net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal<>(this, net.minecraft.world.entity.player.Player.class, true));
        this.targetSelector.addGoal(2, new net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal(this));
        this.targetSelector.addGoal(3, new SmartTargetGoal(this));
    }
}
