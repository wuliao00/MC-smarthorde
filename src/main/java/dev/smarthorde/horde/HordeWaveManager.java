package dev.smarthorde.horde;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Mob;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import dev.smarthorde.effects.HordeEffects;
import dev.smarthorde.network.HordeWaveSyncPacket;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 尸潮波次管理器（轮7）。单例，由 ServerTickEvent 驱动。
 * COUNTDOWN → ACTIVE → REWARD → 循环/COMPLETE
 */
public final class HordeWaveManager {

    private static final Logger LOGGER = LoggerFactory.getLogger("SmartHorde");
    private static final HordeWaveManager INSTANCE = new HordeWaveManager();

    private final Map<UUID, HordeWaveSession> sessions = new ConcurrentHashMap<>();

    private HordeWaveManager() {}

    public static HordeWaveManager getInstance() { return INSTANCE; }

    public boolean startWave(ServerPlayer player, int totalWaves) {
        UUID id = player.getUUID();
        if (sessions.containsKey(id)) {
            player.sendSystemMessage(Component.literal("⚠ 你已有一场尸潮进行中！")
                    .withStyle(ChatFormatting.RED));
            return false;
        }
        BlockPos center = player.blockPosition();
        HordeWaveSession session = new HordeWaveSession(player, center, totalWaves);
        sessions.put(id, session);
        player.sendSystemMessage(Component.literal(
                String.format("☠ 尸潮即将开始！共 %d 波，5 秒后第一波来袭...", totalWaves))
                .withStyle(ChatFormatting.DARK_RED, ChatFormatting.BOLD));
        LOGGER.info("[SmartHorde] 玩家 {} 开始尸潮，共 {} 波", player.getName().getString(), totalWaves);
        return true;
    }

    public boolean stopWave(ServerPlayer player) {
        UUID id = player.getUUID();
        HordeWaveSession session = sessions.remove(id);
        if (session == null) return false;
        cleanupEntities(session);
        syncClear(player);
        LOGGER.info("[SmartHorde] 玩家 {} 终止尸潮", player.getName().getString());
        return true;
    }

    /** 供性能审计调用（轮11）。 */
    public int getActiveSessionCount() {
        return sessions.size();
    }

    public String getStatus(ServerPlayer player) {
        HordeWaveSession session = sessions.get(player.getUUID());
        if (session == null) return "当前无尸潮。";
        return String.format("波次 %d/%d | 阶段: %s | 存活: %d",
                session.getCurrentWave(), session.getTotalWaves(),
                session.getPhase().name(), session.getAliveCount());
    }

    public boolean hasActiveSession(UUID playerId) {
        return sessions.containsKey(playerId);
    }

    @SubscribeEvent
    public void onServerTick(ServerTickEvent.Post event) {
        if (sessions.isEmpty()) return;

        Iterator<Map.Entry<UUID, HordeWaveSession>> it = sessions.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<UUID, HordeWaveSession> entry = it.next();
            HordeWaveSession session = entry.getValue();

            if (!session.isOwnerOnline()) {
                ServerPlayer owner = session.getOwner();
                cleanupEntities(session);
                syncClear(owner);
                it.remove();
                LOGGER.info("[SmartHorde] 玩家掉线，清理尸潮");
                continue;
            }

            tickSession(session);

            if (session.getPhase() == HordeWaveSession.Phase.COMPLETE) {
                ServerPlayer owner = session.getOwner();
                syncClear(owner);
                it.remove();
            }
        }
    }

    private void tickSession(HordeWaveSession session) {
        switch (session.getPhase()) {
            case COUNTDOWN -> tickCountdown(session);
            case ACTIVE    -> tickActive(session);
            case REWARD    -> tickReward(session);
            case COMPLETE  -> { }
        }
    }

    private void tickCountdown(HordeWaveSession session) {
        session.setPhaseTicks(session.getPhaseTicks() - 1);

        if (session.getPhaseTicks() % 20 == 0 && session.getPhaseTicks() > 0) {
            ServerPlayer owner = session.getOwner();
            if (owner != null) {
                owner.sendSystemMessage(Component.literal(
                        "下一波: " + session.getCountdownSeconds() + " 秒...")
                        .withStyle(ChatFormatting.GRAY));
            }
            syncToClient(session);
        }

        if (session.getPhaseTicks() <= 0) {
            session.advanceWave();
            int count = session.getWaveSpawnCount();
            List<UUID> spawned = HordeWaveSpawner.spawnWave(
                    session.getLevel(), session.getCenter(),
                    session.getCurrentWave(), count);
            session.getSpawnedEntities().clear();
            session.getSpawnedEntities().addAll(spawned);
            session.setAliveCount(spawned.size());
            session.setPhase(HordeWaveSession.Phase.ACTIVE);

            ServerPlayer owner = session.getOwner();
            if (owner != null) {
                owner.sendSystemMessage(Component.literal(
                        String.format("⚔ 波次 %d/%d 开始！%d 只怪来袭！",
                                session.getCurrentWave(), session.getTotalWaves(), spawned.size()))
                        .withStyle(ChatFormatting.RED, ChatFormatting.BOLD));
            }
            HordeEffects.playWaveStart(session.getLevel(), session.getCenter(),
                    HordeWaveSpawner.getSpawnRadius(session.getCurrentWave()));
        }
    }

    private void tickActive(HordeWaveSession session) {
        if (session.getLevel().getGameTime() % 10 != 0) return;

        int alive = countAliveEntities(session);
        session.setAliveCount(alive);
        syncToClient(session);

        if (alive <= 0) {
            session.setPhase(HordeWaveSession.Phase.REWARD);
            session.setPhaseTicks(HordeWaveSession.REWARD_TICKS);
            ServerPlayer owner = session.getOwner();
            HordeWaveReward.giveWaveReward(owner, session.getCurrentWave(), session.getTotalWaves());
            if (owner != null) {
                HordeEffects.playWaveClear(session.getLevel(), owner.position());
            }
        }
    }

    private void tickReward(HordeWaveSession session) {
        session.setPhaseTicks(session.getPhaseTicks() - 1);
        if (session.getPhaseTicks() <= 0) {
            if (session.getCurrentWave() >= session.getTotalWaves()) {
                session.setPhase(HordeWaveSession.Phase.COMPLETE);
                ServerPlayer owner = session.getOwner();
                HordeWaveReward.giveFinalReward(owner, session.getTotalWaves());
                if (owner != null) {
                    HordeEffects.playVictory(session.getLevel(), owner.position());
                }
                // [轮12] 排行榜：波次完成统计
                HordeLeaderboard leaderboard = HordeLeaderboard.get(session.getLevel());
                leaderboard.addWaveCompleted();
                LOGGER.info("[SmartHorde] 玩家 {} 完成全部 {} 波尸潮！",
                        owner != null ? owner.getName().getString() : "unknown",
                        session.getTotalWaves());
            } else {
                session.setPhase(HordeWaveSession.Phase.COUNTDOWN);
                session.setPhaseTicks(HordeWaveSession.COUNTDOWN_TICKS);
                syncToClient(session);
            }
        }
    }

    private int countAliveEntities(HordeWaveSession session) {
        ServerLevel level = session.getLevel();
        int count = 0;
        for (UUID uuid : session.getSpawnedEntities()) {
            if (level.getEntity(uuid) instanceof Mob mob && mob.isAlive()) {
                count++;
            }
        }
        return count;
    }

    private void cleanupEntities(HordeWaveSession session) {
        ServerLevel level = session.getLevel();
        for (UUID uuid : session.getSpawnedEntities()) {
            if (level.getEntity(uuid) != null) {
                level.getEntity(uuid).discard();
            }
        }
        session.getSpawnedEntities().clear();
    }

    // ===== [轮8] 客户端同步 =====

    private void syncToClient(HordeWaveSession session) {
        ServerPlayer owner = session.getOwner();
        if (owner == null) return;
        PacketDistributor.sendToPlayer(owner, new HordeWaveSyncPacket(
                true,
                session.getCurrentWave(),
                session.getTotalWaves(),
                session.getAliveCount(),
                session.getWaveSpawnCount(),
                session.getPhase().ordinal(),
                session.getCountdownSeconds()
        ));
    }

    private void syncClear(ServerPlayer player) {
        if (player == null) return;
        PacketDistributor.sendToPlayer(player, new HordeWaveSyncPacket(
                false, 0, 0, 0, 0, 0, 0));
    }
}
