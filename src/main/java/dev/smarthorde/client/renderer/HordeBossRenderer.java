package dev.smarthorde.client.renderer;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.smarthorde.SmartHordeMod;
import dev.smarthorde.entity.HordeBoss;
import net.minecraft.client.model.ZombieModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.entity.AbstractZombieRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;

import java.util.EnumMap;
import java.util.Map;

/**
 * HordeBoss 专属渲染器：僵尸姿态模型（含内外盔甲层）、1.6 倍缩放贴合 1.2x3.2 碰撞箱，
 * 每个变体一张自绘贴图。
 */
public class HordeBossRenderer extends AbstractZombieRenderer<HordeBoss, ZombieModel<HordeBoss>> {

    private static final Map<HordeBoss.Variant, ResourceLocation> TEXTURES = new EnumMap<>(HordeBoss.Variant.class);

    static {
        for (HordeBoss.Variant variant : HordeBoss.Variant.values()) {
            TEXTURES.put(variant, ResourceLocation.fromNamespaceAndPath(SmartHordeMod.MODID,
                    "textures/entity/horde_boss_" + variant.id + ".png"));
        }
    }

    public HordeBossRenderer(EntityRendererProvider.Context context) {
        super(context, new ZombieModel<>(context.bakeLayer(ModelLayers.ZOMBIE)),
                new ZombieModel<>(context.bakeLayer(ModelLayers.ZOMBIE_INNER_ARMOR)),
                new ZombieModel<>(context.bakeLayer(ModelLayers.ZOMBIE_OUTER_ARMOR)));
        this.shadowRadius = 0.9F;
    }

    @Override
    public ResourceLocation getTextureLocation(HordeBoss entity) {
        return TEXTURES.get(entity.getVariant());
    }

    @Override
    protected void scale(HordeBoss entity, PoseStack poseStack, float partialTick) {
        poseStack.scale(1.6F, 1.6F, 1.6F);
    }
}
