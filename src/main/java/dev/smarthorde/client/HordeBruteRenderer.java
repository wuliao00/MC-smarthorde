package dev.smarthorde.client;

import dev.smarthorde.entity.HordeBrute;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.HumanoidMobRenderer;
import net.minecraft.resources.ResourceLocation;

/**
 * HordeBrute 渲染器，自绘纹理（取自 1.0.2 jar 的 horde_boss_brute 贴图，复制为 horde_brute.png）。
 *
 * 模型层修复（缺陷：加载时 Failed to create model for smarthorde:horde_brute）：
 * 不能 bake ModelLayers.RAVAGER —— Ravager 层是 128x128 四足网格（顶层部件为
 * neck/body/right_hind_leg/...，head 嵌套在 neck 下，无 humanoid 顶层 head/hat/body/
 * right_arm/left_arm/right_leg/left_leg），而 HumanoidModel 构造器要求顶层存在全部
 * 7 个 humanoid 部件，导致渲染器构造抛 NoSuchElementException: Can't find part head，
 * 整次资源包加载失败。改用 ModelLayers.ZOMBIE（64x64 humanoid 全部件层，与
 * horde_brute.png 的 64x64 布局匹配），与 HordeBossRenderer/SmartZombieRenderer 一致。
 */
public final class HordeBruteRenderer extends HumanoidMobRenderer<HordeBrute, HumanoidModel<HordeBrute>> {
    private static final ResourceLocation TEXTURE =
            ResourceLocation.fromNamespaceAndPath("smarthorde", "textures/entity/horde_brute.png");
    public HordeBruteRenderer(EntityRendererProvider.Context ctx) {
        super(ctx, new HumanoidModel<>(ctx.bakeLayer(ModelLayers.ZOMBIE)), 1.0F);
    }
    @Override public ResourceLocation getTextureLocation(HordeBrute entity) { return TEXTURE; }
}
