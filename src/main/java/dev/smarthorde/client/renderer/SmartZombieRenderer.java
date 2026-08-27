package dev.smarthorde.client.renderer;

import dev.smarthorde.SmartHordeMod;
import dev.smarthorde.entity.SmartZombie;
import net.minecraft.client.model.ZombieModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.entity.AbstractZombieRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;

/**
 * SmartZombie 专属渲染器：僵尸姿态模型（含内外盔甲层）+ 自绘贴图。
 */
public class SmartZombieRenderer extends AbstractZombieRenderer<SmartZombie, ZombieModel<SmartZombie>> {

    private static final ResourceLocation TEXTURE =
            ResourceLocation.fromNamespaceAndPath(SmartHordeMod.MODID, "textures/entity/smart_zombie.png");

    public SmartZombieRenderer(EntityRendererProvider.Context context) {
        super(context, new ZombieModel<>(context.bakeLayer(ModelLayers.ZOMBIE)),
                new ZombieModel<>(context.bakeLayer(ModelLayers.ZOMBIE_INNER_ARMOR)),
                new ZombieModel<>(context.bakeLayer(ModelLayers.ZOMBIE_OUTER_ARMOR)));
    }

    @Override
    public ResourceLocation getTextureLocation(SmartZombie entity) {
        return TEXTURE;
    }
}
