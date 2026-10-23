package dev.smarthorde.client.model;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 纯数学骨骼运动学与防穿模/防浮空约束单元测试（无需 Minecraft 引导）。
 */
class PostureClampTest {

    @Test
    void headRotationIsClampedToPreventNeckTwistClipping() {
        assertEquals(0.0F, PostureClamp.clampHeadYawDeg(0.0F), 1.0E-6F);
        assertEquals(55.0F, PostureClamp.clampHeadYawDeg(160.0F), 1.0E-6F);
        assertEquals(-55.0F, PostureClamp.clampHeadYawDeg(-175.0F), 1.0E-6F);

        assertEquals(-50.0F, PostureClamp.clampHeadPitchDeg(-89.0F), 1.0E-6F);
        assertEquals(55.0F, PostureClamp.clampHeadPitchDeg(88.0F), 1.0E-6F);
    }

    @Test
    void limbSwingAmountSoftSaturatesAtHighSpeeds() {
        assertEquals(0.0F, PostureClamp.clampLimbSwingAmount(-0.2F), 1.0E-6F);
        assertEquals(0.5F, PostureClamp.clampLimbSwingAmount(0.5F), 1.0E-6F);

        float sprint = PostureClamp.clampLimbSwingAmount(1.4F);
        float extreme = PostureClamp.clampLimbSwingAmount(5.0F);
        assertTrue(sprint > 0.65F && sprint <= PostureClamp.MAX_LIMB_SWING_AMOUNT);
        assertTrue(extreme >= sprint && extreme <= PostureClamp.MAX_LIMB_SWING_AMOUNT);
    }

    @Test
    void overheadRaiseForcesOutwardSplayToClearHelmet() {
        assertEquals(0.04F, PostureClamp.clampRightArmZRot(-0.3F, -1.0F), 1.0E-6F);
        assertEquals(-0.04F, PostureClamp.clampLeftArmZRot(0.3F, -1.0F), 1.0E-6F);

        assertEquals(0.20F, PostureClamp.clampRightArmZRot(0.05F, -2.30F), 1.0E-6F);
        assertEquals(-0.20F, PostureClamp.clampLeftArmZRot(-0.05F, -2.30F), 1.0E-6F);
    }

    @Test
    void groundedTorsoCouplingKeepsWaistConnectedAndFeetPlanted() {
        // 中立姿态下与标准枢轴严格一致
        assertEquals(-5.0F, PostureClamp.computeShoulderX(true, 0.0F), 1.0E-5F);
        assertEquals(5.0F, PostureClamp.computeShoulderX(false, 0.0F), 1.0E-5F);
        assertEquals(2.0F, PostureClamp.computeShoulderY(PostureClamp.DEFAULT_SHOULDER_Y, 0.0F), 1.0E-5F);
        assertEquals(0.0F, PostureClamp.computeShoulderZ(true, 0.0F, 0.0F), 1.0E-5F);
        assertEquals(0.0F, PostureClamp.computeUpperBodyDropY(0.0F), 1.0E-5F);
        assertEquals(12.0F, PostureClamp.computeGroundedHipY(), 1.0E-5F);
        assertEquals(0.0F, PostureClamp.computeHipZ(0.0F), 1.0E-5F);

        // 任意俯仰角下：上身下沉量 + 旋转躯干投影 == 恒定接地髋部 Y(12.0)，双脚永不悬空且腰腹不断裂
        for (float pitch : new float[]{-0.24F, -0.10F, 0.15F, 0.34F}) {
            float dropY = PostureClamp.computeUpperBodyDropY(pitch);
            float waistY = dropY + (float) (12.0 * Math.cos(pitch));
            assertEquals(PostureClamp.computeGroundedHipY(), waistY, 1.0E-5F);
        }

        // Boss 宽肩距（±7.0）耦合验证
        assertEquals(-7.0F, PostureClamp.computeShoulderX(true, PostureClamp.BOSS_SHOULDER_X, 0.0F), 1.0E-5F);
        assertEquals(7.0F, PostureClamp.computeShoulderX(false, PostureClamp.BOSS_SHOULDER_X, 0.0F), 1.0E-5F);
        assertTrue(PostureClamp.computeHipZ(0.25F) > 2.5F);
        assertTrue(PostureClamp.computeShoulderZ(true, 0.0F, 0.35F) > 1.5F);
        assertTrue(PostureClamp.computeShoulderZ(false, 0.0F, 0.35F) < -1.5F);
    }

    @Test
    void easingFunctionsAreBoundedAndMonotonic() {
        assertEquals(0.0F, PostureClamp.easeInOutSine(0.0F), 1.0E-6F);
        assertEquals(1.0F, PostureClamp.easeInOutSine(1.0F), 1.0E-6F);
        assertEquals(0.0F, PostureClamp.easeOutCubic(0.0F), 1.0E-6F);
        assertEquals(1.0F, PostureClamp.easeOutCubic(1.0F), 1.0E-6F);

        float prev = 0.0F;
        for (int i = 1; i <= 10; i++) {
            float cur = PostureClamp.easeInOutSine(i / 10.0F);
            assertTrue(cur >= prev);
            prev = cur;
        }
    }
}
