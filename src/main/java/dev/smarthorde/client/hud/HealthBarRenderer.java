package dev.smarthorde.client.hud;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.smarthorde.config.SmartHordeConfig;
import dev.smarthorde.entity.EliteRole;
import dev.smarthorde.entity.HordeBoss;
import dev.smarthorde.entity.SmartZombie;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.neoforge.client.event.RenderLivingEvent;
import org.joml.Matrix4f;
import org.joml.Quaternionf;

import java.util.HashMap;
import java.util.Map;

/**
 * 头顶血量条渲染（防闪烁穿模增强版）：
 * <ul>
 *   <li>无重叠平面分块绘制（边框四边 + 精英徽记 + 当前血量段 + 受击缓冲白条段 + 空槽段互不覆盖），
 *       从几何根源彻底消除共面 Quad 的深度 Z-Fighting 闪烁穿模；</li>
 *   <li>感知模型缩放（{@link EliteRole#modelScale}）与头顶名称牌高度，自动上抬避免与头盔或名字重叠穿插；</li>
 *   <li>满血隐藏，距离 &gt; 32 格不渲染。</li>
 * </ul>
 */
public final class HealthBarRenderer {

    private static final double MAX_RENDER_DISTANCE_SQR = 32.0 * 32.0;
    private static final Map<Integer, Float> DAMAGE_TRAIL = new HashMap<>();

    private HealthBarRenderer() {
    }

    public static void clearCache() {
        DAMAGE_TRAIL.clear();
    }

    public static void renderHealthBar(RenderLivingEvent.Post<?, ?> event) {
        if (!SmartHordeConfig.isHeadHealthBarEnabledSafe()) {
            return;
        }
        LivingEntity entity = event.getEntity();
        if (!(entity instanceof SmartZombie) && !(entity instanceof HordeBoss)) {
            return;
        }
        if (entity.isInvisible() || entity.isDeadOrDying()) {
            DAMAGE_TRAIL.remove(entity.getId());
            return;
        }
        float healthFraction = Math.max(0.0F, Math.min(1.0F,
                entity.getHealth() / Math.max(1.0F, entity.getMaxHealth())));
        if (healthFraction >= 0.999F) {
            DAMAGE_TRAIL.remove(entity.getId());
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null
                || minecraft.player.distanceToSqr(entity) > MAX_RENDER_DISTANCE_SQR) {
            return;
        }

        float trailFraction = updateDamageTrail(entity.getId(), healthFraction);

        float visualHeight = entity.getBbHeight();
        EliteRole eliteRole = EliteRole.STANDARD;
        if (entity instanceof SmartZombie smartZombie) {
            eliteRole = smartZombie.getEliteRole();
            visualHeight *= Math.max(1.0F, eliteRole.modelScale);
        }
        // 若实体显示名称牌（自定义名、Boss 或准星正指），额外上抬避开原版 NameTag
        boolean hasVisibleNameTag = entity.hasCustomName()
                || entity instanceof HordeBoss
                || minecraft.crosshairPickEntity == entity;
        float yOffset = visualHeight + (hasVisibleNameTag ? 0.80F : 0.45F);

        PoseStack poseStack = event.getPoseStack();
        poseStack.pushPose();
        poseStack.translate(0.0F, yOffset, 0.0F);
        Quaternionf cameraRotation = minecraft.gameRenderer.getMainCamera().rotation();
        poseStack.mulPose(cameraRotation);
        Matrix4f matrix = poseStack.last().pose();

        VertexConsumer buffer = event.getMultiBufferSource().getBuffer(RenderType.gui());
        float totalWidth = Math.max(0.92F, Math.min(1.85F, entity.getBbWidth() + 0.52F));
        float totalHeight = 0.13F;
        float border = 0.018F;

        float left = -totalWidth * 0.5F;
        float right = totalWidth * 0.5F;
        float top = -totalHeight * 0.5F;
        float bottom = totalHeight * 0.5F;

        // 1. 外框四条互不重叠的边带
        fillQuad(buffer, matrix, left, top, right, top + border, 16, 16, 20, 210);
        fillQuad(buffer, matrix, left, bottom - border, right, bottom, 16, 16, 20, 210);
        fillQuad(buffer, matrix, left, top + border, left + border, bottom - border, 16, 16, 20, 210);
        fillQuad(buffer, matrix, right - border, top + border, right, bottom - border, 16, 16, 20, 210);

        float innerLeft = left + border;
        float innerRight = right - border;
        float innerTop = top + border;
        float innerBottom = bottom - border;

        // 2. 精英兵种左侧色彩徽记（与血条槽严密拼接不重叠）
        if (eliteRole.isElite()) {
            float badgeWidth = 0.09F;
            int rgb = eliteRole.badgeColorRgb;
            fillQuad(buffer, matrix, innerLeft, innerTop, innerLeft + badgeWidth, innerBottom,
                    (rgb >> 16) & 0xFF, (rgb >> 8) & 0xFF, rgb & 0xFF, 235);
            fillQuad(buffer, matrix, innerLeft + badgeWidth, innerTop, innerLeft + badgeWidth + border, innerBottom,
                    16, 16, 20, 210);
            innerLeft += badgeWidth + border;
        }

        // 3. 槽内三段严密分区：[innerLeft, hpEnd] 当前血量 | [hpEnd, trailEnd] 掉血残影 | [trailEnd, innerRight] 空血底槽
        float trackWidth = Math.max(0.05F, innerRight - innerLeft);
        float hpEnd = innerLeft + trackWidth * healthFraction;
        float trailEnd = innerLeft + trackWidth * Math.max(healthFraction, trailFraction);

        int red = (int) (220 * (1.0F - healthFraction));
        int green = (int) (40 + 180 * healthFraction);
        if (hpEnd > innerLeft) {
            fillQuad(buffer, matrix, innerLeft, innerTop, hpEnd, innerBottom, red, green, 32, 232);
        }
        if (trailEnd > hpEnd + 0.002F) {
            fillQuad(buffer, matrix, hpEnd, innerTop, trailEnd, innerBottom, 245, 210, 120, 215);
        }
        if (innerRight > trailEnd + 0.002F) {
            fillQuad(buffer, matrix, trailEnd, innerTop, innerRight, innerBottom, 34, 34, 38, 175);
        }

        poseStack.popPose();
    }

    private static float updateDamageTrail(int entityId, float currentFraction) {
        if (DAMAGE_TRAIL.size() > 256) {
            DAMAGE_TRAIL.clear();
        }
        float previous = DAMAGE_TRAIL.getOrDefault(entityId, currentFraction);
        float next = previous > currentFraction
                ? Math.max(currentFraction, previous - 0.012F)
                : currentFraction;
        DAMAGE_TRAIL.put(entityId, next);
        return next;
    }

    private static void fillQuad(VertexConsumer buffer, Matrix4f matrix,
                                 float x1, float y1, float x2, float y2,
                                 int r, int g, int b, int a) {
        buffer.addVertex(matrix, x1, y1, 0.0F).setColor(r, g, b, a);
        buffer.addVertex(matrix, x1, y2, 0.0F).setColor(r, g, b, a);
        buffer.addVertex(matrix, x2, y2, 0.0F).setColor(r, g, b, a);
        buffer.addVertex(matrix, x2, y1, 0.0F).setColor(r, g, b, a);
    }
}
