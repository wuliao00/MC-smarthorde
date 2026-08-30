package dev.smarthorde.horde;

import net.minecraft.ChatFormatting;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import dev.smarthorde.config.DifficultyManager;
import dev.smarthorde.config.DifficultyPreset;
import dev.smarthorde.config.SmartHordeConfig;
import dev.smarthorde.effects.HordeEffects;
import dev.smarthorde.network.HordeWaveSyncPacket;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 尸潮波次管理器（轮7）。单例，由 ServerTickEvent 驱动。
 * COUNTDOWN → ACTIVE → REWARD → 循环/COMPLETE
 * [C2] 分帧生成（每 tick ≤ 8 只）+ MAX_CONCURRENT 同屏上限；
 * [C3] 夜间自动触发（每 100 tick 巡检）+ ANNOUNCE_WAVES 播报开关。
 */
public final class HordeWaveManager {

    private static final Logger LOGGER = LoggerFactory.getLogger("SmartHorde");
    private static final HordeWaveManager INSTANCE = new HordeWaveManager();

    /** [C2] 分帧生成：每 tick 最多生成的实体数。 */
    private static final int SPAWN_BATCH_SIZE = 8;
    /** [C3] 夜间自动尸潮巡检间隔（tick）。 */
    private static final int NIGHT_CHECK_INTERVAL = 100;
    /** [C3] 自动尸潮波数：适配默认 WAVE_INTERVAL 下一个夜晚的时长。 */
    private static final int AUTO_WAVE_COUNT = 3;
    /** [m4] 分帧生成连续失败上限：连续这么多 tick 一只都没生成就放弃剩余份额并结算。 */
    private static final int SPAWN_GIVE_UP_TICKS = 20;

    private final Map<UUID, HordeWaveSession> sessions = new ConcurrentHashMap<>();
    private int nightCheckTimer = 0;
    /** [m3] 上次尸潮会话结束的世界 tick（含正常完成/手动终止/掉线清理），夜间自动尸潮冷却基准。 */
    private long lastNightWaveEndTick = 0L;

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
                String.format("☠ 尸潮即将开始！共 %d 波，%d 秒后第一波来袭...",
                        totalWaves, HordeWaveSession.countdownTicks(1) / 20))
                .withStyle(ChatFormatting.DARK_RED, ChatFormatting.BOLD));
        LOGGER.info("[SmartHorde] 玩家 {} 开始尸潮，共 {} 波", player.getName().getString(), totalWaves);
        return true;
    }

    public boolean stopWave(ServerPlayer player) {
        UUID id = player.getUUID();
        HordeWaveSession session = sessions.remove(id);
        if (session == null) return false;
        lastNightWaveEndTick = session.getLevel().getGameTime(); // [m3] 会话结束入冷却
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
        // [C3] 夜间自动尸潮：每 100 tick 低频巡检（全局无活跃会话时才触发）
        if (++nightCheckTimer >= NIGHT_CHECK_INTERVAL) {
            nightCheckTimer = 0;
            tryTriggerNightWave(event.getServer());
        }

        if (sessions.isEmpty()) return;

        Iterator<Map.Entry<UUID, HordeWaveSession>> it = sessions.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<UUID, HordeWaveSession> entry = it.next();
            HordeWaveSession session = entry.getValue();

            if (!session.isOwnerOnline()) {
                ServerPlayer owner = session.getOwner();
                lastNightWaveEndTick = session.getLevel().getGameTime(); // [m3] 会话结束入冷却
                cleanupEntities(session);
                syncClear(owner);
                it.remove();
                LOGGER.info("[SmartHorde] 玩家掉线，清理尸潮");
                continue;
            }

            tickSession(session);

            if (session.getPhase() == HordeWaveSession.Phase.COMPLETE) {
                ServerPlayer owner = session.getOwner();
                lastNightWaveEndTick = session.getLevel().getGameTime(); // [m3] 会话结束入冷却
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
            if (owner != null && SmartHordeConfig.ANNOUNCE_WAVES.get()) {
                owner.sendSystemMessage(Component.literal(
                        "下一波: " + session.getCountdownSeconds() + " 秒...")
                        .withStyle(ChatFormatting.GRAY));
            }
            syncToClient(session);
        }

        if (session.getPhaseTicks() <= 0) {
            session.advanceWave();
            // [C2] 波次实体进入待生成队列，由 tickActive 分帧生成（每 tick ≤ 8 只）
            session.getSpawnedEntities().clear();
            session.setPendingSpawns(session.getWaveSpawnCount());
            session.setAliveCount(0);
            session.setPhase(HordeWaveSession.Phase.ACTIVE);

            ServerPlayer owner = session.getOwner();
            if (owner != null && SmartHordeConfig.ANNOUNCE_WAVES.get()) {
                owner.sendSystemMessage(Component.literal(
                        String.format("⚔ 波次 %d/%d 开始！%d 只怪来袭！",
                                session.getCurrentWave(), session.getTotalWaves(),
                                session.getWaveSpawnCount()))
                        .withStyle(ChatFormatting.RED, ChatFormatting.BOLD));
            }
            HordeEffects.playWaveStart(session.getLevel(), session.getCenter(),
                    HordeWaveSpawner.getSpawnRadius(session.getCurrentWave()));
        }
    }

    private void tickActive(HordeWaveSession session) {
        // [C2] 分帧生成：每 tick 最多 SPAWN_BATCH_SIZE 只，受 MAX_CONCURRENT 同屏上限约束
        // [m4] 按实际生成数扣减：找不到位置 / MAX_CONCURRENT 超限的份额不扣，顺延到后续 tick 重试
        //      （波次路径为全权重混编，eliteAllowed=true，受 BOSS_ENABLED 约束）
        if (session.getPendingSpawns() > 0) {
            int request = Math.min(SPAWN_BATCH_SIZE, session.getPendingSpawns());
            List<UUID> batch = HordeWaveSpawner.spawnBatch(session.getLevel(), session.getCenter(),
                    session.getCurrentWave(), request, session.getSpawnedEntities(), true);
            session.getSpawnedEntities().addAll(batch);
            session.setPendingSpawns(session.getPendingSpawns() - batch.size());

            // [m4] 防永久卡死：连续 SPAWN_GIVE_UP_TICKS tick 一只都生成不了（位置全部不可用等）
            //      → 放弃剩余份额并结算；任一 tick 有产出即重置计数
            if (batch.isEmpty()) {
                if (session.getFailedSpawnTicks() >= SPAWN_GIVE_UP_TICKS) {
                    LOGGER.warn("[SmartHorde] 波次{}连续 {} tick 无法生成任何实体，放弃剩余 {} 份额",
                            session.getCurrentWave(), SPAWN_GIVE_UP_TICKS, session.getPendingSpawns());
                    session.setPendingSpawns(0);
                    session.setFailedSpawnTicks(0);
                } else {
                    session.setFailedSpawnTicks(session.getFailedSpawnTicks() + 1);
                }
            } else {
                session.setFailedSpawnTicks(0);
            }
        }

        if (session.getLevel().getGameTime() % 10 != 0) return;

        int alive = countAliveEntities(session);
        session.setAliveCount(alive);
        syncToClient(session);

        // [C2] 待生成余量未清零时不结算波次
        if (alive <= 0 && session.getPendingSpawns() <= 0) {
            session.setPhase(HordeWaveSession.Phase.REWARD);
            session.setPhaseTicks(HordeWaveSession.REWARD_TICKS);
            ServerPlayer owner = session.getOwner();
            HordeWaveReward.giveWaveReward(owner, session.getCurrentWave(), session.getTotalWaves());
            // [m7] 进度授予：清除第一个尸潮波次 → wave_survivor（criteria: done）
            if (session.getCurrentWave() == 1) {
                awardAdvancement(owner, "wave_survivor", "done");
            }
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
                // [m7] 进度授予：完整撑过一整场尸潮会话 → horde_champion
                awardAdvancement(owner, "horde_champion", "done");
                // [m7] 噩梦难度下完成整场尸潮 → nightmare_finish
                if (DifficultyManager.get() == DifficultyPreset.NIGHTMARE) {
                    awardAdvancement(owner, "nightmare_finish", "done");
                }
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
                // [m2] 后续波倒计时接入 WAVE_INTERVAL（首波已在会话创建时固定 100 tick）
                session.setPhaseTicks(HordeWaveSession.countdownTicks(session.getCurrentWave() + 1));
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
            // [C7] 单次查询实体，避免每个 UUID 两次 getEntity
            Entity entity = level.getEntity(uuid);
            if (entity != null) {
                entity.discard();
            }
        }
        session.getSpawnedEntities().clear();
    }

    // ===== [C3] 夜间自动尸潮 =====

    /** 每 100 tick 巡检：NIGHT_AUTO + 夜间时间窗 + 允许维度 + 全局无活跃会话 → 自动开波。 */
    private void tryTriggerNightWave(MinecraftServer server) {
        if (!SmartHordeConfig.NIGHT_AUTO.get()) return;
        if (!sessions.isEmpty()) return;
        // [m3] 冷却：上次会话结束后需间隔 WAVE_INTERVAL 才允许再次自动触发，
        //      避免会话一结束下个巡检立即连环开波
        long now = server.overworld().getGameTime();
        if (now - lastNightWaveEndTick < SmartHordeConfig.WAVE_INTERVAL.get()) return;

        List<ServerPlayer> candidates = new ArrayList<>();
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            if (isNightInAllowedDimension(p.serverLevel())) {
                candidates.add(p);
            }
        }
        if (candidates.isEmpty()) return;

        ServerPlayer chosen = candidates.get(
                candidates.get(0).getRandom().nextInt(candidates.size()));
        // 沿用手动触发同一路径；波数固定 AUTO_WAVE_COUNT 适配默认波次间隔下的夜长
        startWave(chosen, AUTO_WAVE_COUNT);
        LOGGER.info("[SmartHorde] 夜间自动尸潮触发：玩家 {}", chosen.getName().getString());
    }

    private static boolean isNightInAllowedDimension(ServerLevel level) {
        long timeOfDay = level.getDayTime() % 24000L;
        if (timeOfDay < SmartHordeConfig.NIGHT_START_TICK.get()
                || timeOfDay >= SmartHordeConfig.NIGHT_END_TICK.get()) {
            return false;
        }
        return SmartHordeConfig.HORDE_DIMENSIONS.get()
                .contains(level.dimension().location().toString());
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

    // ===== [m7] 进度授予（impossible 触发器进度由代码侧手动 award） =====

    /**
     * [m7] 授予进度：criterion 名与 JSON criteria 键一致（三个进度均为 done）。
     * NeoForge 21.1.244：ServerAdvancementManager.get() 返回 AdvancementHolder（未加载时为 null）。
     * 玩家离线（null）或进度未加载（null）时静默跳过；
     * 重复授予由 PlayerAdvancements.award 自身幂等，重复调用安全。
     */
    private static void awardAdvancement(ServerPlayer player, String advancementPath, String criterion) {
        if (player == null) return;
        AdvancementHolder holder = player.server.getAdvancements()
                .get(ResourceLocation.fromNamespaceAndPath("smarthorde", advancementPath));
        if (holder != null) {
            player.getAdvancements().award(holder, criterion);
        }
    }
}
