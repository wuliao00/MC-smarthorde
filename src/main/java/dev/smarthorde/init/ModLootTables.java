package dev.smarthorde.init;

import dev.smarthorde.SmartHordeMod;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.storage.loot.LootTable;

/**
 * 战利品表 ResourceKey 常量。
 * 1.21.1 数据包目录为 data/smarthorde/loot_table/entities/（单数形式）。
 */
public final class ModLootTables {

    public static final ResourceKey<LootTable> SMART_ZOMBIE = key("entities/smart_zombie");
    public static final ResourceKey<LootTable> HORDE_BOSS = key("entities/horde_boss");

    private ModLootTables() {
    }

    private static ResourceKey<LootTable> key(String path) {
        return ResourceKey.create(Registries.LOOT_TABLE,
                ResourceLocation.fromNamespaceAndPath(SmartHordeMod.MODID, path));
    }
}
