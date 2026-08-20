package dev.smarthorde.horde;

import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.saveddata.SavedData;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 尸潮排行榜 / 统计系统（轮12）。
 * 基于 {@link SavedData}，将 Boss 击杀数与波次完成数持久化到 ServerLevel，
 * 跨服务器重启保留。
 *
 * 用法：
 *   HordeLeaderboard board = HordeLeaderboard.get(serverLevel);
 *   board.addBossKill();           // Boss 被击杀时
 *   board.addWaveCompleted(total); // 单波全部完成时
 *   board.sendReport(player);      // /smarthorde leaderboard 查询
 */
public final class HordeLeaderboard extends SavedData {

    private static final Logger LOGGER = LoggerFactory.getLogger("SmartHorde");
    private static final String DATA_NAME = "smarthorde_leaderboard";

    private int bossKills;
    private int wavesCompleted;

    public HordeLeaderboard() {
        this.bossKills = 0;
        this.wavesCompleted = 0;
    }

    // ===== 加载 / 创建 =====

    public static HordeLeaderboard get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(
                new SavedData.Factory<>(
                        HordeLeaderboard::new,
                        HordeLeaderboard::load,
                        null),
                DATA_NAME);
    }

    private static HordeLeaderboard load(CompoundTag tag,
                                         net.minecraft.core.HolderLookup.Provider registries) {
        HordeLeaderboard board = new HordeLeaderboard();
        board.bossKills = tag.getInt("BossKills");
        board.wavesCompleted = tag.getInt("WavesCompleted");
        LOGGER.info("[SmartHorde] 排行榜数据已加载: bossKills={}, wavesCompleted={}",
                board.bossKills, board.wavesCompleted);
        return board;
    }

    // ===== 数据修改 =====

    public void addBossKill() {
        this.bossKills++;
        setDirty();
        LOGGER.info("[SmartHorde] Boss 击杀数 +1 → {}", bossKills);
    }

    public void addWaveCompleted() {
        this.wavesCompleted++;
        setDirty();
        LOGGER.info("[SmartHorde] 波次完成数 +1 → {}", wavesCompleted);
    }

    public int getBossKills()       { return bossKills; }
    public int getWavesCompleted()  { return wavesCompleted; }

    // ===== 查询 =====

    /** 向命令发送者展示当前统计。 */
    public void sendReport(ServerPlayer player) {
        player.sendSystemMessage(Component.literal("=== 尸潮排行榜 ===")
                .withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD));
        player.sendSystemMessage(Component.literal("Boss 击杀数: " + bossKills)
                .withStyle(ChatFormatting.RED));
        player.sendSystemMessage(Component.literal("波次完成数: " + wavesCompleted)
                .withStyle(ChatFormatting.AQUA));
    }

    // ===== 持久化 =====

    @Override
    public CompoundTag save(CompoundTag tag, net.minecraft.core.HolderLookup.Provider registries) {
        tag.putInt("BossKills", bossKills);
        tag.putInt("WavesCompleted", wavesCompleted);
        return tag;
    }
}
