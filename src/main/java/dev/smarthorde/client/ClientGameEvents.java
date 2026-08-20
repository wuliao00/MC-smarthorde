package dev.smarthorde.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.*;
import dev.smarthorde.SmartHorde;
import dev.smarthorde.entity.SmartZombie;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.joml.Matrix4f;

/**
 * 客户端世界渲染事件（轮8）：在 SmartZombie 头顶渲染血量条。
 * 用 RenderLevelStageEvent.Stage.AFTER_PARTICLES 在世界后处理阶段绘制。
 */
@EventBusSubscriber(modid = SmartHorde.MOD_ID, value = Dist.CLIENT)
public final class ClientGameEvents {

    private ClientGameEvents() {}

    private static final double MAX_BAR_DIST_SQR = 16.0D * 16.0D;
    private static final float BAR_HALF_W = 0.5f;
    private static final float BAR_H      = 0.08f;
    private static final float Y_OFF      = 0.35f;

    @SubscribeEvent
    public static void onRenderLevel(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES) return;

        Minecraft mc = Minecraft.getInstance();
        var level = mc.level;
        if (level == null) return;

        var camera = mc.gameRenderer.getMainCamera();
        var camPos = camera.getPosition();

        // 遍历 nearby SmartZombie
        var poseStack = event.getPoseStack();
        for (net.minecraft.world.entity.Entity entity : level.entitiesForRendering()) {
            if (!(entity instanceof SmartZombie zombie)) continue;
            if (!zombie.isAlive()) continue;
            if (zombie.getHealth() >= zombie.getMaxHealth()) continue;
            if (zombie.position().distanceToSqr(camPos) > MAX_BAR_DIST_SQR) continue;

            renderHealthBar(poseStack, zombie, mc);
        }
    }

    private static void renderHealthBar(com.mojang.blaze3d.vertex.PoseStack poseStack,
                                         SmartZombie zombie, Minecraft mc) {
        poseStack.pushPose();

        // 移到实体头顶
        poseStack.translate(
                zombie.getX() - mc.gameRenderer.getMainCamera().getPosition().x,
                zombie.getY() - mc.gameRenderer.getMainCamera().getPosition().y + zombie.getBbHeight() + Y_OFF,
                zombie.getZ() - mc.gameRenderer.getMainCamera().getPosition().z
        );

        // billboard：用相机旋转使血条始终面向玩家
        poseStack.mulPose(mc.getEntityRenderDispatcher().cameraOrientation());

        // 缩放到世界单位
        poseStack.scale(1.0f, 1.0f, 1.0f);

        float pct = zombie.getHealth() / zombie.getMaxHealth();
        float w = BAR_HALF_W;
        float h = BAR_H;

        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        RenderSystem.disableDepthTest();
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();

        Matrix4f mat = poseStack.last().pose();

        // 背景
        drawQuad(mat, -w, 0, w, h, 0.15f, 0.15f, 0.15f, 0.85f);

        // 血量前景
        float fillW = w * 2.0f * pct;
        float r = 1.0f - pct;
        float g = pct;
        drawQuad(mat, -w, 0, -w + fillW, h, r, g, 0.05f, 0.92f);

        RenderSystem.enableDepthTest();
        RenderSystem.disableBlend();
        poseStack.popPose();
    }

    private static void drawQuad(Matrix4f mat, float x1, float y1,
                                  float x2, float y2,
                                  float r, float g, float b, float a) {
        Tesselator tesselator = Tesselator.getInstance();
        BufferBuilder buf = tesselator.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
        buf.addVertex(mat, x1, y1, 0).setColor(r, g, b, a);
        buf.addVertex(mat, x1, y2, 0).setColor(r, g, b, a);
        buf.addVertex(mat, x2, y2, 0).setColor(r, g, b, a);
        buf.addVertex(mat, x2, y1, 0).setColor(r, g, b, a);
        BufferUploader.drawWithShader(buf.buildOrThrow());
    }
}
