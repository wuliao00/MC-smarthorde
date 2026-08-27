package dev.smarthorde.client.hud;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.smarthorde.config.SmartHordeConfig;
import dev.smarthorde.entity.HordeBoss;
import dev.smarthorde.entity.SmartZombie;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.neoforge.client.event.RenderLivingEvent;
import org.joml.Matrix4f;
import org.joml.Quaternionf;

/**
 * 头顶血量条渲染：绿 -> 红渐变，满血隐藏，距离 > 32 格不渲染。
 */
public final class HealthBarRenderer {

    private static final double MAX_RENDER_DISTANCE_SQR = 32.0 * 32.0;

    private HealthBarRenderer() {
    }

    public static void renderHealthBar(RenderLivingEvent.Post event) {
        if (!SmartHordeConfig.HEAD_HEALTH_BAR.get()) {
            return;
        }
        LivingEntity entity = event.getEntity();
        if (!(entity instanceof SmartZombie) && !(entity instanceof HordeBoss)) {
            return;
        }
        if (entity.isInvisible() || entity.isDeadOrDying()) {
            return;
        }
        float healthFraction = entity.getHealth() / Math.max(1.0F, entity.getMaxHealth());
        if (healthFraction >= 0.999F) {
            return; // 满血隐藏
        }
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null
                || minecraft.player.distanceToSqr(entity) > MAX_RENDER_DISTANCE_SQR) {
            return;
        }

        PoseStack poseStack = event.getPoseStack();
        poseStack.pushPose();
        poseStack.translate(0, entity.getBbHeight() + 0.45F, 0);
        Quaternionf cameraRotation = minecraft.gameRenderer.getMainCamera().rotation();
        poseStack.mulPose(cameraRotation);
        Matrix4f matrix = poseStack.last().pose();

        VertexConsumer buffer = event.getMultiBufferSource().getBuffer(RenderType.gui());
        float width = Math.max(0.9F, Math.min(1.8F, entity.getBbWidth() + 0.5F));
        float height = 0.12F;

        // 背景条
        fillQuad(buffer, matrix, -width / 2, -height / 2, width / 2, height / 2, 32, 32, 32, 190);
        // 血量条：绿 (0,220,60) -> 红 (220,40,30)
        int red = (int) (220 * (1.0F - healthFraction));
        int green = (int) (40 + 180 * healthFraction);
        float healthWidth = width * healthFraction;
        fillQuad(buffer, matrix, -width / 2, -height / 2, -width / 2 + healthWidth, height / 2,
                red, green, 30, 230);

        poseStack.popPose();
    }

    private static void fillQuad(VertexConsumer buffer, Matrix4f matrix,
                                 float x1, float y1, float x2, float y2,
                                 int r, int g, int b, int a) {
        buffer.addVertex(matrix, x1, y1, 0).setColor(r, g, b, a);
        buffer.addVertex(matrix, x1, y2, 0).setColor(r, g, b, a);
        buffer.addVertex(matrix, x2, y2, 0).setColor(r, g, b, a);
        buffer.addVertex(matrix, x2, y1, 0).setColor(r, g, b, a);
    }
}
