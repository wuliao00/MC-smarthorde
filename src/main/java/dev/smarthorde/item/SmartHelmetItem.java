package dev.smarthorde.item;

import dev.smarthorde.SmartHordeMod;
import dev.smarthorde.entity.EliteRole;
import dev.smarthorde.entity.SmartZombie;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ArmorMaterial;
import net.minecraft.world.item.ArmorMaterials;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

import java.util.List;

/**
 * 智潮战术头盔（SmartHorde Tactical Helm）：
 * <ul>
 *   <li>独立 3D 穿戴装甲贴图 {@code textures/models/armor/smart_tactical_layer_1.png}；</li>
 *   <li>被动「守御镀层」：受到智能僵尸与尸潮领主的伤害降低 15%；</li>
 *   <li>被动「战术目镜扫描」：穿戴时每 2 秒自动高亮标记 16 格内潜行绕后的疾风突袭者与视线遮挡智尸。</li>
 * </ul>
 */
public class SmartHelmetItem extends ArmorItem {

    private static final ResourceLocation ARMOR_TEXTURE = ResourceLocation.fromNamespaceAndPath(
            SmartHordeMod.MODID, "textures/models/armor/smart_tactical_layer_1.png");
    private static final double SCAN_RADIUS = 16.0;

    public SmartHelmetItem(Properties properties) {
        super(ArmorMaterials.DIAMOND, Type.HELMET, properties);
    }

    @Override
    public ResourceLocation getArmorTexture(ItemStack stack, Entity entity, EquipmentSlot slot,
                                            ArmorMaterial.Layer layer, boolean innerModel) {
        return ARMOR_TEXTURE;
    }

    @Override
    public void inventoryTick(ItemStack stack, Level level, Entity entity, int slotId, boolean isSelected) {
        super.inventoryTick(stack, level, entity, slotId, isSelected);
        if (level.isClientSide() || !(entity instanceof Player player)) {
            return;
        }
        if (player.getItemBySlot(EquipmentSlot.HEAD) != stack || player.tickCount % 40 != 0) {
            return;
        }
        List<SmartZombie> threats = level.getEntitiesOfClass(SmartZombie.class,
                player.getBoundingBox().inflate(SCAN_RADIUS, 8.0, SCAN_RADIUS),
                z -> z.isAlive() && (z.getEliteRole() == EliteRole.RAIDER || !player.hasLineOfSight(z)));
        int marked = 0;
        for (SmartZombie threat : threats) {
            if (marked++ >= 6) {
                break;
            }
            threat.addEffect(new MobEffectInstance(MobEffects.GLOWING, 55, 0, true, false));
        }
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("item.smarthorde.smart_helmet.lore").withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("item.smarthorde.smart_helmet.passive").withStyle(ChatFormatting.AQUA));
    }
}
