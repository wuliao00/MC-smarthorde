package dev.smarthorde.stats;

import dev.smarthorde.SmartHordeMod;
import dev.smarthorde.config.DifficultyManager;
import dev.smarthorde.entity.HordeBoss;
import dev.smarthorde.entity.SmartZombie;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 尸潮统计与成就：
 * - 每玩家统计（清波数/通关数/击杀智能僵尸/Boss 数）存于玩家 PersistentData，随存档持久化；
 * - 击杀类成就由数据包 advancement + 原版 player_killed_entity 触发器完成；
 * - 波次/通关/噩梦难度成就由本类在对应时机代码授予。
 * 排行榜数据源：/smarthorde top 按通关数+清波数排序在线玩家。
 */
@EventBusSubscriber(modid = SmartHordeMod.MODID)
public final class HordeStats {

    private static final Logger LOGGER = LoggerFactory.getLogger("HordeStats");

    public static final String WAVES_CLEARED = "wavesCleared";
    public static final String HORDES_COMPLETED = "hordesCompleted";
    public static final String ZOMBIES_KILLED = "zombiesKilled";
    public static final String BOSSES_KILLED = "bossesKilled";

    private static final String TAG = "smarthorde_stats";

    private HordeStats() {
    }

    // ---------------- 击杀监听 ----------------

    @SubscribeEvent
    public static void onLivingDeath(LivingDeathEvent event) {
        if (event.getSource().getEntity() instanceof ServerPlayer player) {
            if (event.getEntity() instanceof SmartZombie) {
                add(player, ZOMBIES_KILLED, 1);
            } else if (event.getEntity() instanceof HordeBoss) {
                add(player, BOSSES_KILLED, 1);
            }
        }
    }

    // ---------------- 波次/通关钩子（HordeWaveManager 调用） ----------------

    public static void onWaveCleared(ServerPlayer player) {
        add(player, WAVES_CLEARED, 1);
        grant(player, "wave_survivor");
    }

    public static void onHordeCompleted(ServerPlayer player) {
        add(player, HORDES_COMPLETED, 1);
        grant(player, "horde_champion");
        if (DifficultyManager.current() == DifficultyManager.Preset.NIGHTMARE) {
            grant(player, "nightmare_finish");
        }
    }

    // ---------------- 数据读写 ----------------

    public static int get(ServerPlayer player, String key) {
        return data(player).getInt(key);
    }

    public static void add(ServerPlayer player, String key, int amount) {
        CompoundTag tag = player.getPersistentData();
        CompoundTag stats = tag.getCompound(TAG);
        stats.putInt(key, stats.getInt(key) + amount);
        tag.put(TAG, stats);
    }

    private static CompoundTag data(ServerPlayer player) {
        return player.getPersistentData().getCompound(TAG);
    }

    /** 排行榜排序分：通关数优先，其次清波数。 */
    public static long leaderboardScore(ServerPlayer player) {
        return get(player, HORDES_COMPLETED) * 1_000_000L + get(player, WAVES_CLEARED) * 1_000L
                + get(player, ZOMBIES_KILLED);
    }

    // ---------------- 成就授予 ----------------

    /** 代码授予成就：smarthorde:<path>，授予其全部 criteria（JSON 中均为单一 criterion）。 */
    public static void grant(ServerPlayer player, String path) {
        MinecraftServer server = player.getServer();
        if (server == null) {
            return;
        }
        AdvancementHolder holder = server.getAdvancements()
                .get(ResourceLocation.fromNamespaceAndPath(SmartHordeMod.MODID, path));
        if (holder == null) {
            LOGGER.debug("Advancement smarthorde:{} not found", path);
            return;
        }
        for (String criterion : holder.value().criteria().keySet()) {
            player.getAdvancements().award(holder, criterion);
        }
    }
}
