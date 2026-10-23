package dev.smarthorde.horde;

import dev.smarthorde.SmartHordeMod;
import dev.smarthorde.config.SmartHordeConfig;
import dev.smarthorde.effects.EffectManager;
import dev.smarthorde.entity.HordeBoss;
import dev.smarthorde.entity.SmartZombie;
import dev.smarthorde.network.HordeSyncPacket;
import dev.smarthorde.stats.HordeStats;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 波次状态机（全局单例管理，每玩家一个会话）：
 * IDLE -> COUNTDOWN -> ACTIVE -> BETWEEN_WAVES -> (下一波) -> COMPLETE。
 * <ul>
 *   <li>每第 5 波（或 5+ 波最终波）刷出尸潮领主决战；</li>
 *   <li>阵亡挑战失败、远距离脱离战场（&gt;144 格）自动终止与区块卸载防误判胜利保护，彻底封堵传送/重生白嫖钻石漏洞；</li>
 *   <li>残敌发光防卡关信标、安全重试退避及关服清理。</li>
 * </ul>
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
        public boolean stragglerHintSent;
        @Nullable
        public BlockPos waveAnchor;
        public final List<UUID> mobIds = new ArrayList<>();
        private final Set<UUID> pendingAliveIds = new LinkedHashSet<>();

        WaveSession(ServerPlayer player, int totalWaves) {
            this.playerId = player.getUUID();
            this.level = (ServerLevel) player.level();
            this.totalWaves = totalWaves;
            this.waveAnchor = player.blockPosition();
        }

        void trackMob(UUID uuid) {
            this.mobIds.add(uuid);
            this.pendingAliveIds.add(uuid);
        }

        void markMobDefeated(UUID uuid) {
            this.pendingAliveIds.remove(uuid);
        }

        /**
         * 统计尚未被击败的波次怪物数量：
         * 仅当实体在已加载区块中明确死亡或被移除时才剔除；区块临时卸载时保留待击败记录，防止卸载区块误判清波。
         */
        public int remainingMobs() {
            Iterator<UUID> it = this.pendingAliveIds.iterator();
            while (it.hasNext()) {
                UUID id = it.next();
                Entity entity = this.level.getEntity(id);
                if (entity != null && (!(entity instanceof Mob mob) || !mob.isAlive() || mob.isRemoved())) {
                    it.remove();
                }
            }
            return this.pendingAliveIds.size();
        }

        void discardRemainingMobs() {
            for (UUID id : this.mobIds) {
                Entity entity = this.level.getEntity(id);
                if (entity instanceof Mob mob && mob.isAlive()) {
                    if (mob instanceof HordeBoss boss) {
                        boss.getBossBar().removeAllPlayers();
                    }
                    mob.discard();
                }
            }
            this.mobIds.clear();
            this.pendingAliveIds.clear();
        }
    }

    private static final Map<UUID, WaveSession> SESSIONS = new ConcurrentHashMap<>();
    private static final int GRAND_REWARD_DIAMONDS = 3;
    private static final int STRAGGLER_BEACON_START_TICKS = 600;
    private static final double MAX_BATTLEFIELD_LEASH_SQR = 144.0 * 144.0;

    private HordeWaveManager() {
    }

    public static boolean start(ServerPlayer player, int totalWaves) {
        if (!SmartHordeConfig.isHordeEnabledSafe()) {
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
        if (removed == null) {
            return false;
        }
        removed.discardRemainingMobs();
        sendClearHud(player);
        return true;
    }

    /** 跳过当前倒计时或直接清除当前波次进入下一阶段（测试/管理指令）。 */
    public static boolean skipCurrentWave(ServerPlayer player) {
        WaveSession session = SESSIONS.get(player.getUUID());
        if (session == null) {
            return false;
        }
        switch (session.state) {
            case COUNTDOWN -> {
                session.retryAttempts = 0;
                spawnSessionWave(session, player);
            }
            case BETWEEN_WAVES -> {
                session.currentWave++;
                session.retryAttempts = 0;
                spawnSessionWave(session, player);
            }
            case ACTIVE -> {
                session.discardRemainingMobs();
                onWaveCleared(session, player);
            }
            default -> {
                return false;
            }
        }
        return true;
    }

    public static WaveSession sessionOf(UUID playerId) {
        return SESSIONS.get(playerId);
    }

    public static int getActiveSessionCount() {
        return SESSIONS.size();
    }

    @SubscribeEvent
    public static void onLivingDeath(LivingDeathEvent event) {
        if (event.getEntity().level().isClientSide()) {
            return;
        }
        if (event.getEntity() instanceof ServerPlayer player) {
            if (SmartHordeConfig.isHordeFailOnDeathSafe()) {
                WaveSession failed = SESSIONS.remove(player.getUUID());
                if (failed != null && failed.state != WaveState.COMPLETE) {
                    failed.discardRemainingMobs();
                    sendClearHud(player);
                    player.sendSystemMessage(Component.translatable("hud.smarthorde.failed_death"));
                }
            }
            return;
        }
        if (event.getEntity() instanceof Mob deadMob && !SESSIONS.isEmpty()) {
            UUID mobUuid = deadMob.getUUID();
            for (WaveSession session : SESSIONS.values()) {
                session.markMobDefeated(mobUuid);
            }
        }
    }

    @SubscribeEvent
    public static void onServerStopping(ServerStoppingEvent event) {
        SESSIONS.clear();
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
                session.discardRemainingMobs();
                iterator.remove();
                continue;
            }
            if (!player.isAlive()) {
                continue;
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
                int countdownTicks = SmartHordeConfig.hordeCountdownSecondsSafe() * 20;
                if (session.stateTicks >= countdownTicks) {
                    session.retryAttempts = 0;
                    spawnSessionWave(session, player);
                }
            }
            case ACTIVE -> {
                if (session.waveAnchor != null) {
                    double dx = player.getX() - (session.waveAnchor.getX() + 0.5);
                    double dz = player.getZ() - (session.waveAnchor.getZ() + 0.5);
                    if (dx * dx + dz * dz > MAX_BATTLEFIELD_LEASH_SQR) {
                        session.discardRemainingMobs();
                        SESSIONS.remove(session.playerId);
                        sendClearHud(player);
                        player.sendSystemMessage(Component.translatable("commands.smarthorde.wave.abandoned"));
                        return;
                    }
                }
                if (session.stateTicks % 20 == 0) {
                    sync(session);
                }
                if (session.mobIds.isEmpty()) {
                    if (session.stateTicks < 20) {
                        return;
                    }
                    if (session.retryAttempts < 3) {
                        session.retryAttempts++;
                        spawnSessionWave(session, player);
                    } else {
                        SESSIONS.remove(session.playerId);
                        sendClearHud(player);
                        player.sendSystemMessage(Component.translatable("commands.smarthorde.wave.spawn_failed"));
                    }
                    return;
                }
                int remaining = session.remainingMobs();
                if (remaining == 0) {
                    onWaveCleared(session, player);
                    return;
                }
                if (session.stateTicks >= STRAGGLER_BEACON_START_TICKS && session.stateTicks % 100 == 0) {
                    guideAndHighlightStragglers(session, player, remaining);
                }
            }
            case BETWEEN_WAVES -> {
                if (session.stateTicks % 20 == 0) {
                    sync(session);
                }
                int betweenTicks = SmartHordeConfig.hordeCountdownSecondsSafe() * 20;
                if (session.stateTicks >= betweenTicks) {
                    session.currentWave++;
                    session.retryAttempts = 0;
                    spawnSessionWave(session, player);
                }
            }
            case COMPLETE -> {
                if (session.stateTicks >= 100) {
                    SESSIONS.remove(session.playerId);
                    sendClearHud(player);
                }
            }
            default -> {
            }
        }
    }

    private static void guideAndHighlightStragglers(WaveSession session, ServerPlayer player, int remaining) {
        boolean shouldGlow = remaining <= 3 || session.stateTicks >= 900;
        int highlighted = 0;
        for (UUID id : session.pendingAliveIds) {
            if (!(session.level.getEntity(id) instanceof Mob mob) || !mob.isAlive()) {
                continue;
            }
            if (mob.getTarget() == null || !mob.getTarget().isAlive()) {
                mob.setTarget(player);
            }
            if (shouldGlow) {
                mob.addEffect(new MobEffectInstance(MobEffects.GLOWING, 130, 0, true, false));
                highlighted++;
            }
        }
        if (highlighted > 0 && !session.stragglerHintSent) {
            session.stragglerHintSent = true;
            player.sendSystemMessage(Component.translatable("hud.smarthorde.straggler_glow", highlighted));
        }
    }

    private static void spawnSessionWave(WaveSession session, ServerPlayer player) {
        int count = waveCount(session.currentWave);
        BlockPos center = player.blockPosition();
        session.waveAnchor = center;
        session.mobIds.clear();
        session.pendingAliveIds.clear();
        session.stragglerHintSent = false;

        List<SmartZombie> spawned = HordeWaveSpawner.spawnWave(
                session.level, center, session.currentWave, count, 24.0, 32.0, null, player);
        spawned.forEach(z -> session.trackMob(z.getUUID()));

        int waveNumber = session.currentWave + 1;
        boolean isMilestoneBossWave = SmartHordeConfig.isHordeBossMilestoneWaveSafe()
                && (waveNumber % 5 == 0 || (session.totalWaves >= 5 && waveNumber == session.totalWaves));
        if (isMilestoneBossWave) {
            HordeBoss boss = HordeWaveSpawner.spawnWaveBoss(session.level, center, player);
            if (boss != null) {
                session.trackMob(boss.getUUID());
                player.sendSystemMessage(Component.translatable(
                        "hud.smarthorde.boss_wave_alert", waveNumber, boss.getName()));
            }
        }

        session.state = WaveState.ACTIVE;
        session.stateTicks = 0;
        if (!session.mobIds.isEmpty()) {
            session.retryAttempts = 0;
            EffectManager.spawnHordeStartEffect(session.level, player.position());
        }
        sync(session);
    }

    private static int waveCount(int waveIndex) {
        return SmartHordeConfig.hordeBaseCountSafe() + waveIndex * SmartHordeConfig.hordeCountPerWaveSafe();
    }

    private static void onWaveCleared(WaveSession session, ServerPlayer player) {
        EffectManager.spawnWaveClearEffect(session.level, player.position());
        giveItem(player, new ItemStack(Items.EMERALD, 1 + session.currentWave));
        player.giveExperiencePoints(10 * (session.currentWave + 1));
        HordeStats.onWaveCleared(player);

        if (session.currentWave + 1 >= session.totalWaves) {
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

    private static void sendClearHud(ServerPlayer player) {
        PacketDistributor.sendToPlayer(player,
                new HordeSyncPacket(0, 0, 0, WaveState.IDLE.ordinal(), 0));
    }

    private static void sync(WaveSession session) {
        ServerPlayer player = session.level.getServer().getPlayerList().getPlayer(session.playerId);
        if (player == null) {
            return;
        }
        int countdownSeconds = 0;
        int totalTicks = SmartHordeConfig.hordeCountdownSecondsSafe() * 20;
        if (session.state == WaveState.COUNTDOWN || session.state == WaveState.BETWEEN_WAVES) {
            countdownSeconds = Math.max(0, (totalTicks - session.stateTicks) / 20);
        }
        HordeSyncPacket packet = new HordeSyncPacket(
                session.currentWave + 1,
                session.totalWaves,
                session.remainingMobs(),
                session.state.ordinal(),
                countdownSeconds);
        PacketDistributor.sendToPlayer(player, packet);
    }
}
