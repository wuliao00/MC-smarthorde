package dev.smarthorde.init;

import dev.smarthorde.SmartHordeMod;
import dev.smarthorde.item.HordeHornItem;
import dev.smarthorde.item.SmartBladeItem;
import dev.smarthorde.item.SmartHelmetItem;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.DeferredSpawnEggItem;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * 模组物品与创造模式标签页注册：
 * <ul>
 *   <li>{@code smart_zombie_spawn_egg} —— 智能僵尸刷怪蛋（刷怪蛋栏）；</li>
 *   <li>{@code horde_boss_spawn_egg} —— 尸潮领主刷怪蛋（刷怪蛋栏）；</li>
 *   <li>{@code horde_horn} —— 智潮战号（战斗用品栏，生存可合成，用于无作弊启动尸潮挑战）；</li>
 *   <li>{@code smart_helmet} —— 智潮战术头盔（战斗用品栏，减免尸潮伤害并扫描绕后突袭者）；</li>
 *   <li>{@code smart_sword} —— 智潮破阵之刃（战斗用品栏，克制精英智尸/领主并破除先锋盾防）。</li>
 * </ul>
 */
@EventBusSubscriber(modid = SmartHordeMod.MODID, bus = EventBusSubscriber.Bus.MOD)
public final class ModItems {

    public static final DeferredRegister<Item> ITEMS =
            DeferredRegister.create(Registries.ITEM, SmartHordeMod.MODID);

    public static final DeferredHolder<Item, DeferredSpawnEggItem> SMART_ZOMBIE_SPAWN_EGG =
            ITEMS.register("smart_zombie_spawn_egg", () -> new DeferredSpawnEggItem(
                    ModEntities.SMART_ZOMBIE, 0x4A6B3E, 0xD9483B, new Item.Properties()));

    public static final DeferredHolder<Item, DeferredSpawnEggItem> HORDE_BOSS_SPAWN_EGG =
            ITEMS.register("horde_boss_spawn_egg", () -> new DeferredSpawnEggItem(
                    ModEntities.HORDE_BOSS, 0x2B2832, 0xFF7A28, new Item.Properties()));

    public static final DeferredHolder<Item, HordeHornItem> HORDE_HORN =
            ITEMS.register("horde_horn", () -> new HordeHornItem(
                    new Item.Properties().stacksTo(1).rarity(Rarity.RARE)));

    public static final DeferredHolder<Item, SmartHelmetItem> SMART_HELMET =
            ITEMS.register("smart_helmet", () -> new SmartHelmetItem(
                    new Item.Properties()
                            .durability(ArmorItem.Type.HELMET.getDurability(37))
                            .rarity(Rarity.RARE)));

    public static final DeferredHolder<Item, SmartBladeItem> SMART_SWORD =
            ITEMS.register("smart_sword", () -> new SmartBladeItem(
                    new Item.Properties().rarity(Rarity.RARE)));

    private ModItems() {
    }

    public static void register(IEventBus modBus) {
        ITEMS.register(modBus);
    }

    @SubscribeEvent
    public static void onBuildCreativeTabContents(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey() == CreativeModeTabs.SPAWN_EGGS) {
            event.accept(SMART_ZOMBIE_SPAWN_EGG.get());
            event.accept(HORDE_BOSS_SPAWN_EGG.get());
        } else if (event.getTabKey() == CreativeModeTabs.COMBAT) {
            event.accept(SMART_SWORD.get());
            event.accept(SMART_HELMET.get());
            event.accept(HORDE_HORN.get());
        }
    }
}
