package dev.smarthorde.client.renderer;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.smarthorde.SmartHordeMod;
import dev.smarthorde.client.model.HordeBossModel;
import dev.smarthorde.client.model.ModModelLayers;
import dev.smarthorde.client.renderer.layer.EmissiveGlowLayer;
import dev.smarthorde.entity.HordeBoss;
import dev.smarthorde.entity.TacticalAttacker;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.entity.AbstractZombieRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

import java.util.EnumMap;
import java.util.Map;

/**
 * 尸潮领主渲染器：绑定专属 128x128 泰坦巨兽 3D 模型、四变体皮肤与阶段脉动元素发光图层。
 */
public class HordeBossRenderer extends AbstractZombieRenderer<HordeBoss, HordeBossModel> {

    private static final float BOSS_SCALE = 1.45F;
    private static final Map<HordeBoss.Variant, ResourceLocation> VARIANT_TEXTURES =
            new EnumMap<>(HordeBoss.Variant.class);
    private static final Map<HordeBoss.Variant, ResourceLocation> VARIANT_GLOW_TEXTURES =
            new EnumMap<>(HordeBoss.Variant.class);

    static {
        for (HordeBoss.Variant v : HordeBoss.Variant.values()) {
            String baseName = "horde_boss_" + v.getSerializedName();
            VARIANT_TEXTURES.put(v, ResourceLocation.fromNamespaceAndPath(
                    SmartHordeMod.MODID, "textures/entity/" + baseName + ".png"));
            VARIANT_GLOW_TEXTURES.put(v, ResourceLocation.fromNamespaceAndPath(
                    SmartHordeMod.MODID, "textures/entity/" + baseName + "_glow.png"));
        }
    }

    public HordeBossRenderer(EntityRendererProvider.Context context) {
        super(
                context,
                new HordeBossModel(context.bakeLayer(ModModelLayers.HORDE_BOSS)),
                new HordeBossModel(context.bakeLayer(ModelLayers.ZOMBIE_INNER_ARMOR)),
                new HordeBossModel(context.bakeLayer(ModelLayers.ZOMBIE_OUTER_ARMOR))
        );
        this.shadowRadius = 0.85F;
        this.addLayer(new EmissiveGlowLayer<>(
                this,
                entity -> VARIANT_GLOW_TEXTURES.getOrDefault(
                        entity.getVariant(), VARIANT_GLOW_TEXTURES.get(HordeBoss.Variant.BRUTE)),
                HordeBossRenderer::computeBossGlowPulse
        ));
    }

    private static float computeBossGlowPulse(HordeBoss entity, float ageInTicks) {
        if (entity.getAttackAnimType() == TacticalAttacker.ATTACK_SPECIAL) {
            return 1.0F;
        }
        int phase = entity.getCurrentPhase();
        float speed = phase >= 3 ? 0.36F : (phase == 2 ? 0.22F : 0.12F);
        float base = phase >= 3 ? 0.85F : (phase == 2 ? 0.78F : 0.70F);
        float amp = phase >= 3 ? 0.15F : 0.16F;
        return base + amp * Mth.sin(ageInTicks * speed);
    }

    @Override
    protected void scale(HordeBoss entity, PoseStack poseStack, float partialTick) {
        poseStack.scale(BOSS_SCALE, BOSS_SCALE, BOSS_SCALE);
    }

    @Override
    public ResourceLocation getTextureLocation(HordeBoss entity) {
        return VARIANT_TEXTURES.getOrDefault(
                entity.getVariant(),
                VARIANT_TEXTURES.get(HordeBoss.Variant.BRUTE));
    }
}
