package dev.smarthorde.item;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.SwordItem;
import net.minecraft.world.item.Tiers;
import net.minecraft.world.item.TooltipFlag;

import java.util.List;

/**
 * 智潮破阵之刃（SmartHorde Vanguard Blade）：
 * <ul>
 *   <li>被动「斩精锋刃」：对四大精英战术智尸与尸潮领主造成 +25% 额外战术伤害；</li>
 *   <li>被动「破盾冲击」：命中举盾防御的重装先锋时可直接击溃其盾防 3.5 秒（70 tick）。</li>
 * </ul>
 */
public class SmartBladeItem extends SwordItem {

    public SmartBladeItem(Properties properties) {
        super(Tiers.DIAMOND, properties.attributes(SwordItem.createAttributes(Tiers.DIAMOND, 3, -2.4F)));
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("item.smarthorde.smart_sword.lore").withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("item.smarthorde.smart_sword.passive").withStyle(ChatFormatting.AQUA));
    }
}
