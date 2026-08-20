package dev.smarthorde.horde;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 波次奖励发放（轮7）。
 */
public final class HordeWaveReward {

    private static final Logger LOGGER = LoggerFactory.getLogger("SmartHorde");

    private HordeWaveReward() {}

    public static void giveWaveReward(ServerPlayer player, int wave, int totalWaves) {
        if (player == null) return;
        int emeralds = 3 + wave * 2;
        int xp = 10 + wave * 5;
        player.addItem(new ItemStack(Items.EMERALD, emeralds));
        player.giveExperiencePoints(xp);
        player.sendSystemMessage(Component.literal(
                String.format("☠ 波次 %d/%d 清除！奖励: %d 绿宝石, %d 经验",
                        wave, totalWaves, emeralds, xp))
                .withStyle(ChatFormatting.GREEN));
    }

    public static void giveFinalReward(ServerPlayer player, int totalWaves) {
        if (player == null) return;
        int emeralds = 20 + totalWaves * 5;
        int diamonds = 1 + totalWaves / 3;
        int xp = 50 + totalWaves * 10;
        player.addItem(new ItemStack(Items.EMERALD, emeralds));
        player.addItem(new ItemStack(Items.DIAMOND, diamonds));
        player.giveExperiencePoints(xp);
        player.sendSystemMessage(Component.literal(
                String.format("🏆 尸潮 %d 波全部清除！大奖: %d 绿宝石, %d 钻石, %d 经验",
                        totalWaves, emeralds, diamonds, xp))
                .withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD));
    }
}
