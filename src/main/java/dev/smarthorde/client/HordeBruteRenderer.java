package dev.smarthorde.client;

import dev.smarthorde.entity.HordeBrute;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.HumanoidMobRenderer;
import net.minecraft.resources.ResourceLocation;

/** HordeBrute 渲染器，用劫毁兽贴图。 */
public final class HordeBruteRenderer extends HumanoidMobRenderer<HordeBrute, HumanoidModel<HordeBrute>> {
    private static final ResourceLocation TEXTURE =
            ResourceLocation.withDefaultNamespace("textures/entity/illager/ravager.png");
    public HordeBruteRenderer(EntityRendererProvider.Context ctx) {
        super(ctx, new HumanoidModel<>(ctx.bakeLayer(ModelLayers.RAVAGER)), 1.0F);
    }
    @Override public ResourceLocation getTextureLocation(HordeBrute entity) { return TEXTURE; }
}
