package dev.smarthorde.client.model;

import dev.smarthorde.SmartHordeMod;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.resources.ResourceLocation;

/**
 * SmartHorde 专属 3D 实体骨骼网格层定义索引。
 */
public final class ModModelLayers {

    public static final ModelLayerLocation SMART_ZOMBIE = new ModelLayerLocation(
            ResourceLocation.fromNamespaceAndPath(SmartHordeMod.MODID, "smart_zombie"), "main");

    public static final ModelLayerLocation HORDE_BOSS = new ModelLayerLocation(
            ResourceLocation.fromNamespaceAndPath(SmartHordeMod.MODID, "horde_boss"), "main");

    private ModModelLayers() {
    }
}
