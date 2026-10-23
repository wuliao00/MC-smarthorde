package dev.smarthorde.item;

import dev.smarthorde.config.SmartHordeConfig;
import dev.smarthorde.horde.HordeWaveManager;
import dev.smarthorde.init.ModSounds;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.level.Level;

import java.util.List;

/**
 * 智潮战号（Horde War Horn）：
 * 允许无管理员权限的生存模式玩家主动吹响号角发起尸潮生存试炼（零方块破坏，纯可重用号角信物）：
 * <ul>
 *   <li>长按右键 1.2 秒（24 tick）：吹响战号启动 {@code horde.hornWaves} 波尸潮挑战；</li>
 *   <li>潜行 + 右键：查询当前个人尸潮波次进度。</li>
 * </ul>
 */
public class HordeHornItem extends Item {

    private static final int USE_DURATION_TICKS = 24;
    private static final int COOLDOWN_TICKS = 200;

    public HordeHornItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (player.isShiftKeyDown()) {
            if (!level.isClientSide() && player instanceof ServerPlayer serverPlayer) {
                HordeWaveManager.WaveSession session = HordeWaveManager.sessionOf(serverPlayer.getUUID());
                if (session == null) {
                    serverPlayer.sendSystemMessage(Component.translatable("commands.smarthorde.wave.no_session"));
                } else {
                    serverPlayer.sendSystemMessage(Component.translatable("commands.smarthorde.wave.info",
                            session.currentWave + 1, session.totalWaves,
                            session.state.name(), session.remainingMobs()));
                }
            }
            return InteractionResultHolder.sidedSuccess(stack, level.isClientSide());
        }
        player.startUsingItem(hand);
        return InteractionResultHolder.consume(stack);
    }

    @Override
    public UseAnim getUseAnimation(ItemStack stack) {
        return UseAnim.TOOT_HORN;
    }

    @Override
    public int getUseDuration(ItemStack stack, LivingEntity entity) {
        return USE_DURATION_TICKS;
    }

    @Override
    public ItemStack finishUsingItem(ItemStack stack, Level level, LivingEntity entity) {
        if (!level.isClientSide() && entity instanceof ServerPlayer serverPlayer) {
            if (!SmartHordeConfig.isHordeEnabledSafe()) {
                serverPlayer.sendSystemMessage(Component.translatable("commands.smarthorde.wave.disabled"));
                return stack;
            }
            int waves = SmartHordeConfig.hordeHornWavesSafe();
            if (HordeWaveManager.start(serverPlayer, waves)) {
                serverPlayer.getCooldowns().addCooldown(this, COOLDOWN_TICKS);
                serverPlayer.sendSystemMessage(Component.translatable("commands.smarthorde.wave.started", waves));
                if (level instanceof ServerLevel serverLevel && SmartHordeConfig.isSoundsEnabledSafe()) {
                    serverLevel.playSound(null, serverPlayer.getX(), serverPlayer.getY(), serverPlayer.getZ(),
                            ModSounds.HORDE_WAVE_START.get(), SoundSource.PLAYERS, 1.4F, 1.0F);
                }
            } else {
                serverPlayer.sendSystemMessage(Component.translatable("commands.smarthorde.wave.already_active"));
            }
        }
        return stack;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("item.smarthorde.horde_horn.desc1").withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("item.smarthorde.horde_horn.desc2").withStyle(ChatFormatting.DARK_GRAY));
    }
}
