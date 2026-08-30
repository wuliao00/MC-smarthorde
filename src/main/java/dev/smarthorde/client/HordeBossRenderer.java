package dev.smarthorde.client;

import dev.smarthorde.entity.HordeBoss;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.HumanoidMobRenderer;
import net.minecraft.resources.ResourceLocation;

/**
 * HordeBoss 渲染器（轮12修复）。
 *
 * 1.21.1 NeoForge 21.1.244 使用的是旧式基于实体的渲染器 API，不是 1.21.2+ 的 RenderState API。
 *
 * 关键点：
 *   - HumanoidMobRenderer 泛型签名 {@code <T extends Mob, M extends HumanoidModel<T>>}，T 是实体类，
 *     不需要 RenderState。
 *   - 用 HumanoidModel<HordeBoss> 作为模型（不能用 ZombieModel，因为 HordeBoss extends Monster 而非 Zombie）。
 *   - getTextureLocation(HordeBoss entity) 直接接收实体，不需要 createRenderState。
 *   - 自绘纹理：1.0.2 jar 提供的 boss 变体图中选用 inferno 作为默认 Boss 皮肤
 *     （复制为 horde_boss.png；frost/plague 变体系统暂不实现）。
 */
public final class HordeBossRenderer extends HumanoidMobRenderer<HordeBoss, HumanoidModel<HordeBoss>> {

    private static final ResourceLocation TEXTURE =
            ResourceLocation.fromNamespaceAndPath("smarthorde", "textures/entity/horde_boss.png");

    public HordeBossRenderer(EntityRendererProvider.Context ctx) {
        // 阴影半径 1.0F 以体现 Boss 较大体型（实际大小仍由 EntityType.dimensions 决定）。
        super(ctx, new HumanoidModel<>(ctx.bakeLayer(ModelLayers.ZOMBIE)), 1.0F);
    }

    @Override
    public ResourceLocation getTextureLocation(HordeBoss entity) {
        return TEXTURE;
    }
}
