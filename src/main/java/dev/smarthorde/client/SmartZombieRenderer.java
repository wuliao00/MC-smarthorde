package dev.smarthorde.client;

import dev.smarthorde.entity.SmartZombie;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.HumanoidMobRenderer;
import net.minecraft.resources.ResourceLocation;

/**
 * SmartZombie 渲染器（轮12修复）。
 *
 * 1.21.1 NeoForge 21.1.244 使用的是 1.21.1 的渲染器 API（基于实体的旧式签名），
 * 而不是 1.21.2+ 的基于 RenderState 的 API。
 *
 * 关键点：
 *   - HumanoidMobRenderer 的泛型签名是 {@code <T extends Mob, M extends HumanoidModel<T>>}，
 *     类型参数只有两个，且 T 绑定到实体类（Mob），不是 RenderState。
 *   - HumanoidModel 的泛型签名是 {@code <T extends LivingEntity>}，
 *     所以这里用 HumanoidModel<SmartZombie>。（不能使用 ZombieModel，因为它要求 T extends Zombie，
 *     而 SmartZombie extends Monster 而非 Zombie。）
 *   - getTextureLocation 直接接收实体实例 T，不需要重写 createRenderState。
 */
public final class SmartZombieRenderer extends HumanoidMobRenderer<SmartZombie, HumanoidModel<SmartZombie>> {

    private static final ResourceLocation TEXTURE =
            ResourceLocation.fromNamespaceAndPath("smarthorde", "textures/entity/smart_zombie.png");

    public SmartZombieRenderer(EntityRendererProvider.Context ctx) {
        super(ctx, new HumanoidModel<>(ctx.bakeLayer(ModelLayers.ZOMBIE)), 0.5F);
    }

    @Override
    public ResourceLocation getTextureLocation(SmartZombie entity) {
        return TEXTURE;
    }
}
