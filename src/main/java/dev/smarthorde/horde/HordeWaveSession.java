package dev.smarthorde.horde;

import dev.smarthorde.config.SmartHordeConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * 单次尸潮波次会话（轮7）。
 */
public class HordeWaveSession {

    public enum Phase {
        COUNTDOWN, ACTIVE, REWARD, COMPLETE
    }

    private final UUID ownerId;
    private final ServerLevel level;
    private final BlockPos center;
    private final int totalWaves;
    private int currentWave;
    private Phase phase;
    private int phaseTicks;
    private int aliveCount;
    private int pendingSpawns;
    /** [m4] 连续生成失败 tick 计数（防波次卡死，见 HordeWaveManager.SPAWN_GIVE_UP_TICKS）。 */
    private int failedSpawnTicks;
    private final List<UUID> spawnedEntities = new ArrayList<>();

    public static final int REWARD_TICKS    = 20 * 3;
    /** [m2] 首波倒计时固定 100 tick（5 秒），避免 WAVE_INTERVAL 过长拖沓开局。 */
    private static final int FIRST_WAVE_COUNTDOWN_TICKS = 100;

    /**
     * [m2] 倒计时时长：首波（nextWave<=1）固定 100 tick；
     * 后续波接入 WAVE_INTERVAL（同夜内波次间隔配置，至少 20 tick）。
     */
    public static int countdownTicks(int nextWave) {
        if (nextWave <= 1) return FIRST_WAVE_COUNTDOWN_TICKS;
        return Math.max(20, SmartHordeConfig.WAVE_INTERVAL.get());
    }

    public HordeWaveSession(ServerPlayer owner, BlockPos center, int totalWaves) {
        this.ownerId = owner.getUUID();
        this.level = owner.serverLevel();
        this.center = center;
        this.totalWaves = Math.max(1, totalWaves);
        this.currentWave = 0;
        this.phase = Phase.COUNTDOWN;
        // [m2] 首波倒计时固定 100 tick
        this.phaseTicks = countdownTicks(1);
        this.aliveCount = 0;
        this.pendingSpawns = 0;
    }

    public UUID getOwnerId()          { return ownerId; }
    public ServerLevel getLevel()     { return level; }
    public BlockPos getCenter()       { return center; }
    public int getTotalWaves()        { return totalWaves; }
    public int getCurrentWave()       { return currentWave; }
    public Phase getPhase()           { return phase; }
    public int getPhaseTicks()        { return phaseTicks; }
    public int getAliveCount()        { return aliveCount; }
    public List<UUID> getSpawnedEntities() { return spawnedEntities; }

    public void setPhase(Phase phase)       { this.phase = phase; }
    public void setPhaseTicks(int ticks)    { this.phaseTicks = ticks; }
    public void setAliveCount(int count)    { this.aliveCount = count; }
    public void advanceWave()               { this.currentWave++; }

    /** [C2] 待生成余量（分帧生成：每 tick 消化一批）。 */
    public int getPendingSpawns()           { return pendingSpawns; }
    public void setPendingSpawns(int n)     { this.pendingSpawns = Math.max(0, n); }

    /** [m4] 连续生成失败 tick 计数（任一 tick 有产出即归零，达上限放弃剩余份额）。 */
    public int getFailedSpawnTicks()        { return failedSpawnTicks; }
    public void setFailedSpawnTicks(int n)  { this.failedSpawnTicks = Math.max(0, n); }

    public ServerPlayer getOwner() {
        return level.getServer().getPlayerList().getPlayer(ownerId);
    }

    public boolean isOwnerOnline() {
        return getOwner() != null;
    }

    public int getWaveSpawnCount() {
        // [C2] 基础规模接入配置，随波次线性放大
        return SmartHordeConfig.BASE_WAVE_SIZE.get() + currentWave * 3;
    }

    public int getCountdownSeconds() {
        return (phaseTicks + 19) / 20;
    }
}
