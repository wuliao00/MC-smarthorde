package dev.smarthorde.horde;

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
    private final List<UUID> spawnedEntities = new ArrayList<>();

    public static final int COUNTDOWN_TICKS = 20 * 5;
    public static final int REWARD_TICKS    = 20 * 3;

    public HordeWaveSession(ServerPlayer owner, BlockPos center, int totalWaves) {
        this.ownerId = owner.getUUID();
        this.level = owner.serverLevel();
        this.center = center;
        this.totalWaves = Math.max(1, totalWaves);
        this.currentWave = 0;
        this.phase = Phase.COUNTDOWN;
        this.phaseTicks = COUNTDOWN_TICKS;
        this.aliveCount = 0;
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

    public ServerPlayer getOwner() {
        return level.getServer().getPlayerList().getPlayer(ownerId);
    }

    public boolean isOwnerOnline() {
        return getOwner() != null;
    }

    public int getWaveSpawnCount() {
        return 4 + currentWave * 3;
    }

    public int getCountdownSeconds() {
        return (phaseTicks + 19) / 20;
    }
}
