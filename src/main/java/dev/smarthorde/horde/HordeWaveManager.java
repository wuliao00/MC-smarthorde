package dev.smarthorde.horde;

import dev.smarthorde.SmartHordeMod;
import dev.smarthorde.config.SmartHordeConfig;
import dev.smarthorde.effects.EffectManager;
import dev.smarthorde.entity.SmartZombie;
import dev.smarthorde.network.HordeSyncPacket;
import dev.smarthorde.stats.HordeStats;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 波次状态机（全局单例管理，每玩家一个会话）：
 * IDLE -> COUNTDOWN -> ACTIVE -> BETWEEN_WAVES -> (下一波) -> COMPLETE。
 * 每波数量 = baseCount + wave x countPerWave；清波奖励绿宝石+经验；通关大奖钻石。
 * getActiveSessionCount() 供 PerformanceAudit 调用。
 */
@EventBusSubscriber(modid = SmartHordeMod.MODID)
public final class HordeWaveManager {

    public enum WaveState {IDLE, COUNTDOWN, ACTIVE, BETWEEN_WAVES, COMPLETE}

    /** 单个玩家的尸潮会话。 */
    public static final class WaveSession {
        public final UUID playerId;
        public final ServerLevel level;
        public final int totalWaves;
        public int currentWave;          // 0-based
        public WaveState state = WaveState.COUNTDOWN;
        public int stateTicks;
        public int retryAttempts;
        public final List<UUID> mobIds = new ArrayList<>();

        WaveSession(ServerPlayer player, int totalWaves) {
            this.playerId = player.getUUID();
            this.level = (ServerLevel) player.level();
            this.totalWaves = totalWaves;
        }

        public int remainingMobs() {
            int alive = 0;
            for (UUID id : this.mobIds) {
                if (this.level.getEntity(id) instanceof SmartZombie zombie && zombie.isAlive()) {
                    alive++;
                }
            }
            return alive;
        }
    }

    private static final Map<UUID, WaveSession> SESSIONS = new ConcurrentHashMap<>();
    private static final int GRAND_REWARD_DIAMONDS = 3;

    private HordeWaveManager() {
    }

    public static boolean start(ServerPlayer player, int totalWaves) {
        if (!SmartHordeConfig.HORDE_ENABLED.get()) {
            return false;
        }
        if (SESSIONS.containsKey(player.getUUID())) {
            return false;
        }
        WaveSession session = new WaveSession(player, Math.max(1, totalWaves));
        SESSIONS.put(player.getUUID(), session);
        sync(session);
        return true;
    }

    public static boolean stop(ServerPlayer player) {
        WaveSession removed = SESSIONS.remove(player.getUUID());
        return removed != null;
    }

    public static WaveSession sessionOf(UUID playerId) {
        return SESSIONS.get(playerId);
    }

    /** 供审计调用。 */
    public static int getActiveSessionCount() {
        return SESSIONS.size();
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        MinecraftServer server = event.getServer();
        if (SESSIONS.isEmpty()) {
            return;
        }
        Iterator<WaveSession> iterator = SESSIONS.values().iterator();
        while (iterator.hasNext()) {
            WaveSession session = iterator.next();
            ServerPlayer player = server.getPlayerList().getPlayer(session.playerId);
            if (player == null || player.level() != session.level) {
                iterator.remove();
                continue;
            }
            if (!player.isAlive()) {
                continue; // 玩家死亡时暂停，等待重生
            }
            tickSession(session, player);
        }
    }

    private static void tickSession(WaveSession session, ServerPlayer player) {
        session.stateTicks++;

        switch (session.state) {
            case COUNTDOWN -> {
                if (session.stateTicks % 20 == 0) {
                    sync(session);
                }
                int countdownTicks = SmartHordeConfig.HORDE_COUNTDOWN_SECONDS.get() * 20;
                if (session.stateTicks >= countdownTicks) {
                    spawnSessionWave(session, player);
                }
            }
            case ACTIVE -> {
                if (session.stateTicks % 20 == 0) {
                    sync(session);
                }
                if (session.remainingMobs() == 0) {
                    if (session.mobIds.isEmpty() && session.retryAttempts < 3) {
                        // 生成全部失败：稍后重试本波
                        session.retryAttempts++;
                        session.stateTicks = 0;
                        return;
                    }
                    onWaveCleared(session, player);
                }
            }
            case BETWEEN_WAVES -> {
                if (session.stateTicks % 20 == 0) {
                    sync(session);
                }
                int betweenTicks = SmartHordeConfig.HORDE_COUNTDOWN_SECONDS.get() * 20;
                if (session.stateTicks >= betweenTicks) {
                    session.currentWave++;
                    spawnSessionWave(session, player);
                }
            }
            case COMPLETE -> {
                if (session.stateTicks >= 100) {
                    SESSIONS.remove(session.playerId);
                }
            }
            default -> {
            }
        }
    }

    private static void spawnSessionWave(WaveSession session, ServerPlayer player) {
        int count = waveCount(session.currentWave);
        BlockPos center = player.blockPosition();
        List<SmartZombie> spawned = HordeWaveSpawner.spawnWave(session.level, center, session.currentWave, count);
        session.mobIds.clear();
        spawned.forEach(z -> session.mobIds.add(z.getUUID()));
        session.retryAttempts = 0;
        session.state = WaveState.ACTIVE;
        session.stateTicks = 0;
        EffectManager.spawnHordeStartEffect(session.level, player.position());
        sync(session);
    }

    /** 每波数量 = baseCount + wave x countPerWave（清单：7 + wave x 3）。 */
    private static int waveCount(int waveIndex) {
        return SmartHordeConfig.HORDE_BASE_COUNT.get() + waveIndex * SmartHordeConfig.HORDE_COUNT_PER_WAVE.get();
    }

    private static void onWaveCleared(WaveSession session, ServerPlayer player) {
        EffectManager.spawnWaveClearEffect(session.level, player.position());
        giveItem(player, new ItemStack(Items.EMERALD, 1 + session.currentWave));
        player.giveExperiencePoints(10 * (session.currentWave + 1));
        HordeStats.onWaveCleared(player);

        if (session.currentWave + 1 >= session.totalWaves) {
            // 通关大奖：钻石 + 经验
            giveItem(player, new ItemStack(Items.DIAMOND, GRAND_REWARD_DIAMONDS));
            player.giveExperiencePoints(100);
            player.sendSystemMessage(Component.translatable("hud.smarthorde.complete"));
            session.state = WaveState.COMPLETE;
            HordeStats.onHordeCompleted(player);
        } else {
            session.state = WaveState.BETWEEN_WAVES;
        }
        session.stateTicks = 0;
        sync(session);
    }

    private static void giveItem(ServerPlayer player, ItemStack stack) {
        if (!player.getInventory().add(stack)) {
            player.drop(stack, false);
        }
    }

    /** 波次数据同步到客户端 HUD。 */
    private static void sync(WaveSession session) {
        ServerPlayer player = session.level.getServer().getPlayerList().getPlayer(session.playerId);
        if (player == null) {
            return;
        }
        int countdownSeconds = 0;
        int totalTicks = SmartHordeConfig.HORDE_COUNTDOWN_SECONDS.get() * 20;
        if (session.state == WaveState.COUNTDOWN || session.state == WaveState.BETWEEN_WAVES) {
            countdownSeconds = Math.max(0, (totalTicks - session.stateTicks) / 20);
        }
        HordeSyncPacket packet = new HordeSyncPacket(
                session.currentWave + 1,
                session.totalWaves,
                session.remainingMobs(),
                // phase：WaveState 的 ordinal 序数值，编码契约见 HordeSyncPacket 类注释
                session.state.ordinal(),
                countdownSeconds);
        PacketDistributor.sendToPlayer(player, packet);
    }
}
