package dev.smarthorde.client.renderer.layer;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.smarthorde.client.model.PostureClamp;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FastColor;
import net.minecraft.world.entity.LivingEntity;

import java.util.function.Function;

/**
 * 全亮度自发光图层（Full-Bright Emissive Layer）：
 * 为精英战术僵尸与尸潮领主渲染夜间/洞穴可见的发光瞳孔、战术面罩、符文裂隙与元素胸核，
 * 并支持随狂暴、战吼或领主阶段实时脉动亮度。
 */
public class EmissiveGlowLayer<T extends LivingEntity, M extends EntityModel<T>> extends RenderLayer<T, M> {

    private static final int FULL_BRIGHT_LIGHT = 15728640;

    @FunctionalInterface
    public interface PulseResolver<E> {
        float resolveIntensity(E entity, float ageInTicks);
    }

    private final Function<T, ResourceLocation> textureGetter;
    private final PulseResolver<T> pulseResolver;

    public EmissiveGlowLayer(RenderLayerParent<T, M> parent,
                             Function<T, ResourceLocation> textureGetter,
                             PulseResolver<T> pulseResolver) {
        super(parent);
        this.textureGetter = textureGetter;
        this.pulseResolver = pulseResolver;
    }

    @Override
    public void render(PoseStack poseStack, MultiBufferSource bufferSource, int packedLight,
                       T entity, float limbSwing, float limbSwingAmount,
                       float partialTicks, float ageInTicks, float netHeadYaw, float headPitch) {
        if (entity.isInvisible()) {
            return;
        }
        ResourceLocation glowTex = this.textureGetter.apply(entity);
        if (glowTex == null) {
            return;
        }
        float intensity = PostureClamp.clamp(this.pulseResolver.resolveIntensity(entity, ageInTicks), 0.15F, 1.0F);
        int channel = Math.round(intensity * 255.0F);
        int color = FastColor.ARGB32.color(255, channel, channel, channel);
        VertexConsumer vertexConsumer = bufferSource.getBuffer(RenderType.eyes(glowTex));
        this.getParentModel().renderToBuffer(poseStack, vertexConsumer, FULL_BRIGHT_LIGHT, OverlayTexture.NO_OVERLAY, color);
    }
}
