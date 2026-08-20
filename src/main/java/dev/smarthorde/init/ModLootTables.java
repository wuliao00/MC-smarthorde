package dev.smarthorde.init;

import dev.smarthorde.SmartHorde;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.storage.loot.LootTable;

/**
 * 战利品表注册（轮11）。
 * 实体战利品表通过 data/smarthorde/loot_tables/entities/ JSON 定义。
 */
public final class ModLootTables {

    public static final ResourceKey<LootTable> SMART_ZOMBIE =
            ResourceKey.create(Registries.LOOT_TABLE,
                    ResourceLocation.fromNamespaceAndPath(SmartHorde.MOD_ID, "entities/smart_zombie"));

    public static final ResourceKey<LootTable> HORDE_BOSS =
            ResourceKey.create(Registries.LOOT_TABLE,
                    ResourceLocation.fromNamespaceAndPath(SmartHorde.MOD_ID, "entities/horde_boss"));

    private ModLootTables() {}
}
