package dev.smarthorde.client.renderer;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.smarthorde.SmartHordeMod;
import dev.smarthorde.client.model.ModModelLayers;
import dev.smarthorde.client.model.SmartZombieModel;
import dev.smarthorde.client.renderer.layer.EmissiveGlowLayer;
import dev.smarthorde.entity.EliteRole;
import dev.smarthorde.entity.SmartZombie;
import dev.smarthorde.entity.TacticalAttacker;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.entity.AbstractZombieRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

import java.util.EnumMap;
import java.util.Map;

/**
 * 精英战术僵尸渲染器：绑定专属 128x128 多部件 3D 模型、体型缩放、四兵种皮肤与全亮度自发光图层。
 */
public class SmartZombieRenderer extends AbstractZombieRenderer<SmartZombie, SmartZombieModel<SmartZombie>> {

    private static final Map<EliteRole, ResourceLocation> ROLE_TEXTURES = new EnumMap<>(EliteRole.class);
    private static final Map<EliteRole, ResourceLocation> ROLE_GLOW_TEXTURES = new EnumMap<>(EliteRole.class);

    static {
        for (EliteRole role : EliteRole.values()) {
            ROLE_TEXTURES.put(role, ResourceLocation.fromNamespaceAndPath(
                    SmartHordeMod.MODID, role.texturePath()));
            ROLE_GLOW_TEXTURES.put(role, ResourceLocation.fromNamespaceAndPath(
                    SmartHordeMod.MODID, role.glowTexturePath()));
        }
    }

    public SmartZombieRenderer(EntityRendererProvider.Context context) {
        super(
                context,
                new SmartZombieModel<>(context.bakeLayer(ModModelLayers.SMART_ZOMBIE)),
                new SmartZombieModel<>(context.bakeLayer(ModelLayers.ZOMBIE_INNER_ARMOR)),
                new SmartZombieModel<>(context.bakeLayer(ModelLayers.ZOMBIE_OUTER_ARMOR))
        );
        this.addLayer(new EmissiveGlowLayer<>(
                this,
                entity -> ROLE_GLOW_TEXTURES.getOrDefault(
                        entity.getEliteRole(), ROLE_GLOW_TEXTURES.get(EliteRole.STANDARD)),
                SmartZombieRenderer::computeGlowPulse
        ));
    }

    private static float computeGlowPulse(SmartZombie entity, float ageInTicks) {
        if (entity.getAttackAnimType() == TacticalAttacker.ATTACK_SPECIAL) {
            return 1.0F;
        }
        if (entity.isFrenzyActive()) {
            return 0.82F + 0.18F * Mth.sin(ageInTicks * 0.35F);
        }
        return 0.72F + 0.16F * Mth.sin(ageInTicks * 0.12F);
    }

    @Override
    protected void scale(SmartZombie entity, PoseStack poseStack, float partialTick) {
        super.scale(entity, poseStack, partialTick);
        float s = entity.getEliteRole().modelScale;
        if (s != 1.0F) {
            poseStack.scale(s, s, s);
        }
    }

    @Override
    public ResourceLocation getTextureLocation(SmartZombie entity) {
        return ROLE_TEXTURES.getOrDefault(entity.getEliteRole(), ROLE_TEXTURES.get(EliteRole.STANDARD));
    }
}
