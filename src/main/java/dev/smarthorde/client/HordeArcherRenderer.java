package dev.smarthorde.client;

import dev.smarthorde.entity.HordeArcher;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.HumanoidMobRenderer;
import net.minecraft.resources.ResourceLocation;

/** HordeArcher 渲染器，用骷髅贴图。 */
public final class HordeArcherRenderer extends HumanoidMobRenderer<HordeArcher, HumanoidModel<HordeArcher>> {
    private static final ResourceLocation TEXTURE =
            ResourceLocation.withDefaultNamespace("textures/entity/skeleton/skeleton.png");
    public HordeArcherRenderer(EntityRendererProvider.Context ctx) {
        super(ctx, new HumanoidModel<>(ctx.bakeLayer(ModelLayers.SKELETON)), 0.5F);
    }
    @Override public ResourceLocation getTextureLocation(HordeArcher entity) { return TEXTURE; }
}
