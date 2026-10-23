package dev.smarthorde.client.model;

/**
 * 骨骼姿态运动学耦合与防穿模约束工具（纯数学实现，零 Minecraft 依赖，支持独立单元测试）。
 *
 * <p>核心解决的五类穿模与浮空根源：
 * <ol>
 *   <li>颈部反拧穿模：限制头部相对躯干偏航角（±55°）与俯仰角（[-50°, +55°]），消除后退/侧移时的 180° 扭头；</li>
 *   <li>高速迈腿穿腹/穿甲：对高移速下的 limbSwingAmount 做软饱和封顶（≤0.85），防止腿部摆幅超过 60° 刺穿胸甲下摆；</li>
 *   <li>手臂穿胸/穿头盔/左右互穿：约束手臂内外展角 zRot 与内收偏航角 yRot；高举过顶蓄力时自动外展避开头盔碰撞体；</li>
 *   <li>躯干俯仰/扭转关节脱节：解算颈部、双肩与髋部三维枢轴耦合位移，确保头、胸、双臂、双腿严丝合缝；</li>
 *   <li>俯身双脚悬空消除：髋部 Y 锁死在 12.0 使双脚始终踩实地面（y=24），改为将上身枢轴下沉 12*(1-cosθ) 实现真实弯腰重心下沉。</li>
 * </ol>
 */
public final class PostureClamp {

    public static final float MAX_HEAD_YAW_DEG = 55.0F;
    public static final float MIN_HEAD_PITCH_DEG = -50.0F;
    public static final float MAX_HEAD_PITCH_DEG = 55.0F;
    public static final float MAX_LIMB_SWING_AMOUNT = 0.85F;

    public static final float MIN_BODY_PITCH_RAD = -0.24F;
    public static final float MAX_BODY_PITCH_RAD = 0.34F;
    public static final float MAX_BODY_YAW_RAD = 0.46F;

    /** 标准人形肩部横向枢轴距离（±5.0 像素单位） */
    public static final float DEFAULT_SHOULDER_X = 5.0F;
    /** 标准人形肩部竖向枢轴高度（2.0 像素单位） */
    public static final float DEFAULT_SHOULDER_Y = 2.0F;
    /** 巨型领主肩部横向枢轴距离（±7.0 像素单位，适配 11 宽倒三角胸腔） */
    public static final float BOSS_SHOULDER_X = 7.0F;
    /** 巨型领主肩部竖向枢轴高度（1.5 像素单位） */
    public static final float BOSS_SHOULDER_Y = 1.5F;
    /** 髋部标准竖向枢轴高度（HumanoidModel 默认 12.0 像素单位，双脚踩地 y=24.0） */
    public static final float DEFAULT_HIP_Y = 12.0F;

    private static final float OVERHEAD_PITCH_THRESHOLD = -1.95F;
    private static final float OVERHEAD_MIN_SPLAY_Z = 0.20F;
    private static final float SIDE_MIN_SPLAY_Z = 0.04F;
    private static final float MAX_INWARD_YAW = 0.50F;
    private static final float MAX_OUTWARD_YAW = 0.68F;

    private PostureClamp() {
    }

    public static float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }

    public static float lerp(float delta, float start, float end) {
        float t = clamp(delta, 0.0F, 1.0F);
        return start + t * (end - start);
    }

    /** 余弦缓入缓出：用于蓄力与收招平滑过渡。 */
    public static float easeInOutSine(float t) {
        float clamped = clamp(t, 0.0F, 1.0F);
        return (float) (0.5 - 0.5 * Math.cos(clamped * Math.PI));
    }

    /** 三次缓出：用于出刀瞬间的爆发与顺势缓冲。 */
    public static float easeOutCubic(float t) {
        float inv = 1.0F - clamp(t, 0.0F, 1.0F);
        return 1.0F - inv * inv * inv;
    }

    public static float clampHeadYawDeg(float netHeadYaw) {
        return clamp(netHeadYaw, -MAX_HEAD_YAW_DEG, MAX_HEAD_YAW_DEG);
    }

    public static float clampHeadPitchDeg(float headPitch) {
        return clamp(headPitch, MIN_HEAD_PITCH_DEG, MAX_HEAD_PITCH_DEG);
    }

    /** 软压缩步幅强度，防止冲刺/高难度加速时大腿刺穿躯干与护腿甲片。 */
    public static float clampLimbSwingAmount(float limbSwingAmount) {
        if (limbSwingAmount <= 0.0F) {
            return 0.0F;
        }
        if (limbSwingAmount <= 0.65F) {
            return limbSwingAmount;
        }
        float excess = limbSwingAmount - 0.65F;
        float compressed = 0.65F + excess / (1.0F + excess * 3.5F);
        return Math.min(MAX_LIMB_SWING_AMOUNT, compressed);
    }

    public static float clampBodyPitch(float xRot) {
        return clamp(xRot, MIN_BODY_PITCH_RAD, MAX_BODY_PITCH_RAD);
    }

    public static float clampBodyYaw(float yRot) {
        return clamp(yRot, -MAX_BODY_YAW_RAD, MAX_BODY_YAW_RAD);
    }

    /**
     * 右臂外展角约束（Model 空间中右臂位于负 X 侧，zRot>0 为向外展开远离肋骨，zRot<0 会穿入胸腔）。
     * 当手臂高举过顶（xRot < -1.95 rad）时强制至少 0.20 rad 外展，避开头颅与头盔模型块。
     */
    public static float clampRightArmZRot(float zRot, float xRot) {
        float minOutward = xRot < OVERHEAD_PITCH_THRESHOLD ? OVERHEAD_MIN_SPLAY_Z : SIDE_MIN_SPLAY_Z;
        return clamp(zRot, minOutward, 0.85F);
    }

    /**
     * 左臂外展角约束（Model 空间中左臂位于正 X 侧，zRot<0 为向外展开远离肋骨，zRot>0 会穿入胸腔）。
     */
    public static float clampLeftArmZRot(float zRot, float xRot) {
        float maxInward = xRot < OVERHEAD_PITCH_THRESHOLD ? -OVERHEAD_MIN_SPLAY_Z : -SIDE_MIN_SPLAY_Z;
        return clamp(zRot, -0.85F, maxInward);
    }

    /** 右臂偏航角约束：限制过度向内横穿左胸（负值向内，正值向外）。 */
    public static float clampRightArmYRot(float yRot) {
        return clamp(yRot, -MAX_INWARD_YAW, MAX_OUTWARD_YAW);
    }

    /** 左臂偏航角约束：限制过度向内横穿右胸（正值向内，负值向外）。 */
    public static float clampLeftArmYRot(float yRot) {
        return clamp(yRot, -MAX_OUTWARD_YAW, MAX_INWARD_YAW);
    }

    /**
     * 弯腰/后仰时上身（颈部与胸腔顶端）下沉量：12 * (1 - cos(bodyPitch))。
     * 配合锁死在 y=12.0 的髋部枢轴，既保证腰腹严丝合缝，又保证双脚严格踩实地面 y=24.0 不浮空。
     */
    public static float computeUpperBodyDropY(float bodyPitch) {
        return (float) (DEFAULT_HIP_Y * (1.0 - Math.cos(bodyPitch)));
    }

    /** 髋部接地 Y 坐标（恒定 12.0F，确保双脚永远贴合地面不悬空）。 */
    public static float computeGroundedHipY() {
        return DEFAULT_HIP_Y;
    }

    /** 随躯干俯仰角（body.xRot）耦合计算髋部 Z 坐标（+xRot 前倾时下腰向 +Z 后移）。 */
    public static float computeHipZ(float bodyPitch) {
        return (float) (DEFAULT_HIP_Y * Math.sin(bodyPitch));
    }

    /** 随上身下沉与躯干俯仰角耦合计算肩部 Y 坐标。 */
    public static float computeShoulderY(float baseShoulderY, float bodyPitch) {
        return computeUpperBodyDropY(bodyPitch) + (float) (baseShoulderY * Math.cos(bodyPitch));
    }

    /** 随躯干偏航角耦合计算标准人形肩部 X 坐标。 */
    public static float computeShoulderX(boolean rightArm, float bodyYaw) {
        return computeShoulderX(rightArm, DEFAULT_SHOULDER_X, bodyYaw);
    }

    /** 随躯干偏航角耦合计算指定肩宽模型的肩部 X 坐标。 */
    public static float computeShoulderX(boolean rightArm, float baseShoulderX, float bodyYaw) {
        float baseX = rightArm ? -baseShoulderX : baseShoulderX;
        return (float) (baseX * Math.cos(bodyYaw));
    }

    /** 随躯干俯仰角与偏航角耦合计算标准人形肩部 Z 坐标。 */
    public static float computeShoulderZ(boolean rightArm, float bodyPitch, float bodyYaw) {
        return computeShoulderZ(rightArm, DEFAULT_SHOULDER_X, DEFAULT_SHOULDER_Y, bodyPitch, bodyYaw);
    }

    /** 随躯干俯仰角与偏航角耦合计算指定肩宽/肩高模型的肩部 Z 坐标。 */
    public static float computeShoulderZ(boolean rightArm, float baseShoulderX, float baseShoulderY,
                                         float bodyPitch, float bodyYaw) {
        float baseX = rightArm ? -baseShoulderX : baseShoulderX;
        return (float) (baseShoulderY * Math.sin(bodyPitch) - baseX * Math.sin(bodyYaw));
    }
}
