package dev.smarthorde.client.model;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.smarthorde.entity.HordeBoss;
import dev.smarthorde.entity.TacticalAttacker;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.ZombieModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.HumanoidArm;
import org.jetbrains.annotations.Nullable;

/**
 * 尸潮领主专属多部件泰坦级 3D 模型（128x128 独立 UV 布局）：
 * <ul>
 *   <li>倒三角巨兽骨架：11 宽魁梧胸腔 + 8 宽腰腹核心 + 7.0 宽肩距 + 巨型重装粉碎护手 + 护膝胫甲；</li>
 *   <li>可动咆哮下颚（jaw）：在蓄力重击、主动大招与狂暴三阶段时动态张颌怒吼；</li>
 *   <li>四变体独立 3D 剪影：
 *     蛮横（前弯巨牛角+肩刺）、瘟疫（腐化鹿角+呼吸脉动孢囊）、寒霜（冰晶皇冠+双肩冰棱）、炼狱（后掠恶魔熔角+烈焰顶冠+背脊熔炉排口）；</li>
 *   <li>接地刚体耦合：宽肩距（±7.0）彻底杜绝粗壮手臂穿胸，双脚恒定踩实地面不浮空。</li>
 * </ul>
 */
public class HordeBossModel extends ZombieModel<HordeBoss> {

    @Nullable private final ModelPart jaw;
    @Nullable private final ModelPart chestCore;
    @Nullable private final ModelPart beltSkullBuckle;
    @Nullable private final ModelPart dorsalPlate;
    @Nullable private final ModelPart tassetApron;
    @Nullable private final ModelPart bossPauldronR;
    @Nullable private final ModelPart bossPauldronL;
    @Nullable private final ModelPart gauntletRidgeR;
    @Nullable private final ModelPart gauntletRidgeL;

    @Nullable private final ModelPart bruteHornR;
    @Nullable private final ModelPart bruteHornL;
    @Nullable private final ModelPart bruteSpikeR;
    @Nullable private final ModelPart bruteSpikeL;

    @Nullable private final ModelPart plagueAntler;
    @Nullable private final ModelPart plagueSacBack;
    @Nullable private final ModelPart plagueSacShoulder;

    @Nullable private final ModelPart frostCrown;
    @Nullable private final ModelPart frostShardR;
    @Nullable private final ModelPart frostShardL;

    @Nullable private final ModelPart infernoHornR;
    @Nullable private final ModelPart infernoHornL;
    @Nullable private final ModelPart infernoCrest;
    @Nullable private final ModelPart infernoVents;

    public HordeBossModel(ModelPart root) {
        super(root);
        this.jaw = safeChild(this.head, "jaw");
        this.chestCore = safeChild(this.body, "chest_core");
        this.beltSkullBuckle = safeChild(this.body, "belt_skull_buckle");
        this.dorsalPlate = safeChild(this.body, "dorsal_plate");
        this.tassetApron = safeChild(this.body, "tasset_apron");
        this.bossPauldronR = safeChild(this.rightArm, "boss_pauldron_r");
        this.bossPauldronL = safeChild(this.leftArm, "boss_pauldron_l");
        this.gauntletRidgeR = safeChild(this.rightArm, "gauntlet_ridge_r");
        this.gauntletRidgeL = safeChild(this.leftArm, "gauntlet_ridge_l");

        this.bruteHornR = safeChild(this.head, "brute_horn_r");
        this.bruteHornL = safeChild(this.head, "brute_horn_l");
        this.bruteSpikeR = safeChild(this.rightArm, "brute_spike_r");
        this.bruteSpikeL = safeChild(this.leftArm, "brute_spike_l");

        this.plagueAntler = safeChild(this.head, "plague_antler");
        this.plagueSacBack = safeChild(this.body, "plague_sac_back");
        this.plagueSacShoulder = safeChild(this.leftArm, "plague_sac_shoulder");

        this.frostCrown = safeChild(this.head, "frost_crown");
        this.frostShardR = safeChild(this.rightArm, "frost_shard_r");
        this.frostShardL = safeChild(this.leftArm, "frost_shard_l");

        this.infernoHornR = safeChild(this.head, "inferno_horn_r");
        this.infernoHornL = safeChild(this.head, "inferno_horn_l");
        this.infernoCrest = safeChild(this.head, "inferno_crest");
        this.infernoVents = safeChild(this.body, "inferno_vents");
    }

    @Nullable
    private static ModelPart safeChild(@Nullable ModelPart parent, String name) {
        return parent != null && parent.hasChild(name) ? parent.getChild(name) : null;
    }

    private static void setVisible(@Nullable ModelPart part, boolean visible) {
        if (part != null) {
            part.visible = visible;
        }
    }

    public static LayerDefinition createBodyLayer() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();

        // 1. 领主凶悍头颅 + 突出眉骨 + 铰接咆哮下颚 + 四变体头饰
        PartDefinition head = root.addOrReplaceChild("head",
                CubeListBuilder.create()
                        .texOffs(0, 0).addBox(-4.0F, -8.0F, -4.0F, 8.0F, 7.0F, 8.0F)
                        .texOffs(34, 0).addBox(-4.5F, -6.8F, -4.8F, 9.0F, 2.0F, 1.0F),
                PartPose.ZERO);
        root.addOrReplaceChild("hat", CubeListBuilder.create(), PartPose.ZERO);

        head.addOrReplaceChild("jaw",
                CubeListBuilder.create()
                        .texOffs(34, 5).addBox(-3.5F, 0.0F, -3.8F, 7.0F, 2.0F, 4.0F),
                PartPose.offset(0.0F, -1.5F, -0.5F));

        head.addOrReplaceChild("brute_horn_r",
                CubeListBuilder.create()
                        .texOffs(60, 52).addBox(-1.0F, -7.0F, -1.0F, 2.0F, 7.0F, 2.0F),
                PartPose.offsetAndRotation(-4.0F, -6.0F, -1.0F, 0.55F, 0.0F, -0.60F));
        head.addOrReplaceChild("brute_horn_l",
                CubeListBuilder.create()
                        .texOffs(70, 52).addBox(-1.0F, -7.0F, -1.0F, 2.0F, 7.0F, 2.0F),
                PartPose.offsetAndRotation(4.0F, -6.0F, -1.0F, 0.55F, 0.0F, 0.60F));

        head.addOrReplaceChild("plague_antler",
                CubeListBuilder.create()
                        .texOffs(0, 68).addBox(-1.0F, -6.0F, -1.0F, 2.0F, 6.0F, 2.0F)
                        .texOffs(114, 84).addBox(3.6F, -3.5F, -0.5F, 2.0F, 3.0F, 2.0F),
                PartPose.offsetAndRotation(-2.6F, -7.6F, -1.0F, -0.15F, 0.0F, -0.24F));

        head.addOrReplaceChild("frost_crown",
                CubeListBuilder.create()
                        .texOffs(50, 68).addBox(-4.0F, -9.2F, -4.0F, 8.0F, 2.0F, 8.0F, new CubeDeformation(0.22F))
                        .texOffs(64, 84).addBox(-1.0F, -15.0F, -4.3F, 2.0F, 6.0F, 2.0F)
                        .texOffs(74, 84).addBox(-3.8F, -13.2F, -4.1F, 2.0F, 4.0F, 2.0F)
                        .texOffs(84, 84).addBox(1.8F, -13.2F, -4.1F, 2.0F, 4.0F, 2.0F)
                        .texOffs(94, 84).addBox(-3.6F, -12.2F, 2.1F, 2.0F, 3.0F, 2.0F)
                        .texOffs(104, 84).addBox(1.6F, -12.2F, 2.1F, 2.0F, 3.0F, 2.0F),
                PartPose.ZERO);

        head.addOrReplaceChild("inferno_horn_r",
                CubeListBuilder.create()
                        .texOffs(0, 84).addBox(-1.0F, -8.0F, -1.0F, 2.0F, 8.0F, 2.0F),
                PartPose.offsetAndRotation(-3.8F, -6.5F, -1.5F, -0.46F, 0.0F, -0.36F));
        head.addOrReplaceChild("inferno_horn_l",
                CubeListBuilder.create()
                        .texOffs(10, 84).addBox(-1.0F, -8.0F, -1.0F, 2.0F, 8.0F, 2.0F),
                PartPose.offsetAndRotation(3.8F, -6.5F, -1.5F, -0.46F, 0.0F, 0.36F));
        head.addOrReplaceChild("inferno_crest",
                CubeListBuilder.create()
                        .texOffs(20, 84).addBox(-1.0F, -11.0F, -4.0F, 2.0F, 3.0F, 8.0F),
                PartPose.ZERO);

        // 2. 倒三角巨兽胸腔（上胸宽 11 深 6 + 腹核宽 8 + 重型战带 + 背脊龙骨）
        PartDefinition body = root.addOrReplaceChild("body",
                CubeListBuilder.create()
                        .texOffs(0, 16).addBox(-5.5F, 0.0F, -3.0F, 11.0F, 7.0F, 6.0F)
                        .texOffs(0, 30).addBox(-4.0F, 7.0F, -2.5F, 8.0F, 5.0F, 5.0F)
                        .texOffs(28, 30).addBox(-4.5F, 10.0F, -3.0F, 9.0F, 2.0F, 6.0F)
                        .texOffs(36, 16).addBox(-1.0F, 0.5F, 3.0F, 2.0F, 10.0F, 2.0F),
                PartPose.ZERO);

        body.addOrReplaceChild("chest_core",
                CubeListBuilder.create()
                        .texOffs(0, 96).addBox(-2.0F, -2.0F, -0.5F, 4.0F, 4.0F, 1.0F, new CubeDeformation(0.10F)),
                PartPose.offset(0.0F, 3.4F, -3.1F));

        body.addOrReplaceChild("belt_skull_buckle",
                CubeListBuilder.create()
                        .texOffs(56, 96).addBox(-2.0F, -2.0F, -1.0F, 4.0F, 4.0F, 2.0F),
                PartPose.offset(0.0F, 10.6F, -2.8F));

        body.addOrReplaceChild("dorsal_plate",
                CubeListBuilder.create()
                        .texOffs(72, 96).addBox(-3.5F, 1.0F, 2.5F, 7.0F, 6.0F, 2.0F),
                PartPose.ZERO);

        body.addOrReplaceChild("tasset_apron",
                CubeListBuilder.create()
                        .texOffs(38, 96).addBox(-3.0F, 0.0F, 0.0F, 6.0F, 5.0F, 1.0F),
                PartPose.offsetAndRotation(0.0F, 11.2F, -2.9F, -0.10F, 0.0F, 0.0F));

        body.addOrReplaceChild("plague_sac_back",
                CubeListBuilder.create()
                        .texOffs(10, 68).addBox(-3.0F, -3.0F, 0.0F, 6.0F, 6.0F, 4.0F),
                PartPose.offset(1.2F, 3.5F, 2.8F));

        body.addOrReplaceChild("inferno_vents",
                CubeListBuilder.create()
                        .texOffs(42, 84).addBox(-3.5F, 1.0F, 3.0F, 7.0F, 6.0F, 2.0F),
                PartPose.ZERO);

        // 3. 巨型粉碎铁拳双臂（宽肩距 ±7.0，上臂 5x6x5，前臂重拳套 6x8x6）
        PartDefinition rightArm = root.addOrReplaceChild("right_arm",
                CubeListBuilder.create()
                        .texOffs(60, 0).addBox(-2.5F, -2.0F, -2.5F, 5.0F, 6.0F, 5.0F)
                        .texOffs(60, 12).addBox(-3.0F, 4.0F, -3.0F, 6.0F, 8.0F, 6.0F),
                PartPose.offset(-PostureClamp.BOSS_SHOULDER_X, PostureClamp.BOSS_SHOULDER_Y, 0.0F));
        rightArm.addOrReplaceChild("boss_pauldron_r",
                CubeListBuilder.create()
                        .texOffs(0, 52).addBox(-3.8F, -3.5F, -3.5F, 7.0F, 5.0F, 7.0F),
                PartPose.offsetAndRotation(0.0F, 0.0F, 0.0F, 0.0F, 0.0F, -0.14F));
        rightArm.addOrReplaceChild("gauntlet_ridge_r",
                CubeListBuilder.create()
                        .texOffs(14, 96).addBox(-3.7F, 4.5F, -1.5F, 1.0F, 7.0F, 3.0F),
                PartPose.ZERO);
        rightArm.addOrReplaceChild("brute_spike_r",
                CubeListBuilder.create()
                        .texOffs(80, 52).addBox(-1.0F, -7.5F, -1.0F, 2.0F, 4.0F, 2.0F),
                PartPose.offsetAndRotation(-1.5F, 0.0F, 0.0F, 0.0F, 0.0F, -0.22F));
        rightArm.addOrReplaceChild("frost_shard_r",
                CubeListBuilder.create()
                        .texOffs(84, 68).addBox(-1.0F, -7.0F, -1.5F, 2.0F, 7.0F, 3.0F),
                PartPose.offsetAndRotation(-1.8F, -2.5F, 0.0F, 0.0F, 0.0F, -0.36F));

        PartDefinition leftArm = root.addOrReplaceChild("left_arm",
                CubeListBuilder.create()
                        .texOffs(86, 0).addBox(-2.5F, -2.0F, -2.5F, 5.0F, 6.0F, 5.0F)
                        .texOffs(86, 12).addBox(-3.0F, 4.0F, -3.0F, 6.0F, 8.0F, 6.0F),
                PartPose.offset(PostureClamp.BOSS_SHOULDER_X, PostureClamp.BOSS_SHOULDER_Y, 0.0F));
        leftArm.addOrReplaceChild("boss_pauldron_l",
                CubeListBuilder.create()
                        .texOffs(30, 52).addBox(-3.2F, -3.5F, -3.5F, 7.0F, 5.0F, 7.0F),
                PartPose.offsetAndRotation(0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.14F));
        leftArm.addOrReplaceChild("gauntlet_ridge_l",
                CubeListBuilder.create()
                        .texOffs(26, 96).addBox(2.7F, 4.5F, -1.5F, 1.0F, 7.0F, 3.0F),
                PartPose.ZERO);
        leftArm.addOrReplaceChild("brute_spike_l",
                CubeListBuilder.create()
                        .texOffs(90, 52).addBox(-1.0F, -7.5F, -1.0F, 2.0F, 4.0F, 2.0F),
                PartPose.offsetAndRotation(1.5F, 0.0F, 0.0F, 0.0F, 0.0F, 0.22F));
        leftArm.addOrReplaceChild("plague_sac_shoulder",
                CubeListBuilder.create()
                        .texOffs(32, 68).addBox(-2.0F, -3.0F, -2.0F, 4.0F, 4.0F, 4.0F),
                PartPose.offset(1.2F, -3.2F, 0.0F));
        leftArm.addOrReplaceChild("frost_shard_l",
                CubeListBuilder.create()
                        .texOffs(96, 68).addBox(-1.0F, -7.0F, -1.5F, 2.0F, 7.0F, 3.0F),
                PartPose.offsetAndRotation(1.8F, -2.5F, 0.0F, 0.0F, 0.0F, 0.36F));

        // 4. 重装胫甲双腿 + 立体护膝
        root.addOrReplaceChild("right_leg",
                CubeListBuilder.create()
                        .texOffs(60, 28).addBox(-2.5F, 0.0F, -2.5F, 5.0F, 12.0F, 5.0F)
                        .texOffs(60, 46).addBox(-2.5F, 3.5F, -3.2F, 5.0F, 3.0F, 1.0F),
                PartPose.offset(-2.5F, 12.0F, 0.0F));
        root.addOrReplaceChild("left_leg",
                CubeListBuilder.create()
                        .texOffs(86, 28).addBox(-2.5F, 0.0F, -2.5F, 5.0F, 12.0F, 5.0F)
                        .texOffs(86, 46).addBox(-2.5F, 3.5F, -3.2F, 5.0F, 3.0F, 1.0F),
                PartPose.offset(2.5F, 12.0F, 0.0F));

        return LayerDefinition.create(mesh, 128, 128);
    }

    @Override
    public void prepareMobModel(HordeBoss entity, float limbSwing, float limbSwingAmount, float partialTick) {
        super.prepareMobModel(entity, limbSwing, limbSwingAmount, partialTick);
        this.rightArmPose = !entity.getMainHandItem().isEmpty()
                ? HumanoidModel.ArmPose.ITEM
                : HumanoidModel.ArmPose.EMPTY;
    }

    @Override
    public void setupAnim(HordeBoss entity, float limbSwing, float limbSwingAmount,
                          float ageInTicks, float netHeadYaw, float headPitch) {
        float clampedYaw = PostureClamp.clampHeadYawDeg(netHeadYaw);
        float clampedPitch = PostureClamp.clampHeadPitchDeg(headPitch);
        float safeSwingAmount = PostureClamp.clampLimbSwingAmount(limbSwingAmount * 0.82F);

        resetPartPivots();
        super.setupAnim(entity, limbSwing, safeSwingAmount, ageInTicks, clampedYaw, clampedPitch);

        HordeBoss.Variant variant = entity.getVariant();
        int phase = entity.getCurrentPhase();
        updateVariantPartVisibility(entity, variant, phase, ageInTicks);

        int animType = entity.getAttackAnimType();
        int animTick = entity.getAttackAnimTick();

        float breathFreq = phase >= 3 ? 0.16F : (phase == 2 ? 0.11F : 0.07F);
        float breathAmp = phase >= 3 ? 0.08F : 0.04F;
        float breath = Mth.sin(ageInTicks * breathFreq) * breathAmp;
        float chestExpansion = 1.0F + 0.016F * Mth.sin(ageInTicks * breathFreq);
        this.body.xScale = chestExpansion;
        this.body.zScale = chestExpansion;

        this.body.xRot = (phase >= 3 ? 0.12F : 0.06F) + breath * 0.5F;
        this.body.yRot = 0.0F;
        this.body.zRot = 0.0F;

        float strideSwing = Mth.cos(limbSwing * 0.6662F + (float) Math.PI) * 0.72F * safeSwingAmount;
        float oppStrideSwing = Mth.cos(limbSwing * 0.6662F) * 0.72F * safeSwingAmount;

        if (!entity.isAggressive() && animType == TacticalAttacker.ATTACK_NONE) {
            this.rightArm.xRot = -0.24F + strideSwing;
            this.leftArm.xRot = -0.24F + oppStrideSwing;
            this.rightArm.yRot = 0.0F;
            this.leftArm.yRot = 0.0F;
            this.rightArm.zRot = 0.14F;
            this.leftArm.zRot = -0.14F;
        } else {
            this.rightArm.xRot = -0.88F + strideSwing * 0.45F + breath;
            this.leftArm.xRot = -0.82F + oppStrideSwing * 0.45F - breath;
            this.rightArm.yRot = -0.06F;
            this.leftArm.yRot = 0.06F;
            this.rightArm.zRot = 0.18F + (phase >= 3 ? 0.08F : 0.0F);
            this.leftArm.zRot = -0.18F - (phase >= 3 ? 0.08F : 0.0F);
        }

        float roarOpen = phase >= 3 ? 0.18F + 0.08F * Mth.sin(ageInTicks * 0.22F) : 0.05F;

        if (animType != TacticalAttacker.ATTACK_NONE) {
            roarOpen = Math.max(roarOpen, applyBossAttackPose(animType, animTick));
        }

        if (this.jaw != null) {
            this.jaw.xRot = PostureClamp.clamp(roarOpen, 0.02F, 0.62F);
        }

        // 防穿模与接地刚体耦合解算（乘船/矿车坐姿保留原版 -81° 抬腿防止双腿穿透船底）
        this.body.xRot = this.riding ? 0.0F : PostureClamp.clampBodyPitch(this.body.xRot);
        this.body.yRot = PostureClamp.clampBodyYaw(this.body.yRot);

        this.rightArm.yRot = PostureClamp.clampRightArmYRot(this.rightArm.yRot);
        this.leftArm.yRot = PostureClamp.clampLeftArmYRot(this.leftArm.yRot);
        this.rightArm.zRot = PostureClamp.clampRightArmZRot(this.rightArm.zRot, this.rightArm.xRot);
        this.leftArm.zRot = PostureClamp.clampLeftArmZRot(this.leftArm.zRot, this.leftArm.xRot);

        if (!this.riding) {
            this.rightLeg.xRot = PostureClamp.clamp(this.rightLeg.xRot * 0.85F, -0.85F, 0.85F);
            this.leftLeg.xRot = PostureClamp.clamp(this.leftLeg.xRot * 0.85F, -0.85F, 0.85F);
        }

        if (this.tassetApron != null && this.tassetApron.visible) {
            float forwardLegPitch = Math.min(0.0F, Math.min(this.rightLeg.xRot, this.leftLeg.xRot));
            this.tassetApron.xRot = -0.10F + forwardLegPitch * 0.72F;
        }

        applyGroundedBossKinematics();
    }

    @Override
    public void copyPropertiesTo(HumanoidModel<HordeBoss> target) {
        super.copyPropertiesTo(target);
        if (target != this) {
            // 将外部盔甲层等比外扩包裹 11 宽泰坦胸腔、6x8x6 巨拳与 5x12x5 胫甲，杜绝指令穿甲时裸露穿模
            target.head.xScale = 1.06F;
            target.head.zScale = 1.06F;
            target.hat.xScale = 1.06F;
            target.hat.zScale = 1.06F;
            target.body.xScale = 1.40F;
            target.body.zScale = 1.52F;
            target.rightArm.xScale = 1.46F;
            target.rightArm.zScale = 1.46F;
            target.leftArm.xScale = 1.46F;
            target.leftArm.zScale = 1.46F;
            target.rightLeg.xScale = 1.28F;
            target.rightLeg.zScale = 1.28F;
            target.leftLeg.xScale = 1.28F;
            target.leftLeg.zScale = 1.28F;
        }
    }

    @Override
    public void translateToHand(HumanoidArm arm, PoseStack poseStack) {
        this.getArm(arm).translateAndRotate(poseStack);
        float sign = arm == HumanoidArm.RIGHT ? 1.0F : -1.0F;
        // 对齐 6x8x6 重型粉碎护手掌心中心（(0.0, 10.5, -2.6) px），消除原版 ±1px 偏置穿入手套
        poseStack.translate(sign / 16.0F, 0.5F / 16.0F, -0.6F / 16.0F);
    }

    private void updateVariantPartVisibility(HordeBoss entity, HordeBoss.Variant variant,
                                             int phase, float ageInTicks) {
        boolean noExtHelmet = entity.getItemBySlot(EquipmentSlot.HEAD).isEmpty();
        boolean noExtChest = entity.getItemBySlot(EquipmentSlot.CHEST).isEmpty();
        boolean noExtLegs = entity.getItemBySlot(EquipmentSlot.LEGS).isEmpty();

        setVisible(this.bossPauldronR, noExtChest);
        setVisible(this.bossPauldronL, noExtChest);
        setVisible(this.gauntletRidgeR, true);
        setVisible(this.gauntletRidgeL, true);
        setVisible(this.chestCore, noExtChest);
        setVisible(this.beltSkullBuckle, noExtChest && noExtLegs);
        setVisible(this.dorsalPlate, noExtChest
                && (variant == HordeBoss.Variant.BRUTE || variant == HordeBoss.Variant.FROST));
        setVisible(this.tassetApron, noExtChest && noExtLegs);

        if (this.chestCore != null && this.chestCore.visible) {
            float coreSpeed = phase >= 3 ? 0.30F : (phase == 2 ? 0.20F : 0.12F);
            float corePulse = 1.0F + 0.06F * Mth.sin(ageInTicks * coreSpeed);
            this.chestCore.xScale = corePulse;
            this.chestCore.yScale = corePulse;
        }

        boolean isBrute = variant == HordeBoss.Variant.BRUTE;
        setVisible(this.bruteHornR, isBrute && noExtHelmet);
        setVisible(this.bruteHornL, isBrute && noExtHelmet);
        setVisible(this.bruteSpikeR, isBrute && noExtChest);
        setVisible(this.bruteSpikeL, isBrute && noExtChest);

        boolean isPlague = variant == HordeBoss.Variant.PLAGUE;
        setVisible(this.plagueAntler, isPlague && noExtHelmet);
        setVisible(this.plagueSacBack, isPlague && noExtChest);
        setVisible(this.plagueSacShoulder, isPlague && noExtChest);
        if (isPlague) {
            float pulse = 1.0F + 0.08F * Mth.sin(ageInTicks * 0.16F);
            if (this.plagueSacBack != null) {
                this.plagueSacBack.xScale = pulse;
                this.plagueSacBack.yScale = pulse;
                this.plagueSacBack.zScale = pulse;
            }
            if (this.plagueSacShoulder != null) {
                float p2 = 1.0F + 0.07F * Mth.cos(ageInTicks * 0.16F);
                this.plagueSacShoulder.xScale = p2;
                this.plagueSacShoulder.yScale = p2;
                this.plagueSacShoulder.zScale = p2;
            }
        }

        boolean isFrost = variant == HordeBoss.Variant.FROST;
        setVisible(this.frostCrown, isFrost && noExtHelmet);
        setVisible(this.frostShardR, isFrost && noExtChest);
        setVisible(this.frostShardL, isFrost && noExtChest);

        boolean isInferno = variant == HordeBoss.Variant.INFERNO;
        setVisible(this.infernoHornR, isInferno && noExtHelmet);
        setVisible(this.infernoHornL, isInferno && noExtHelmet);
        setVisible(this.infernoCrest, isInferno && noExtHelmet);
        setVisible(this.infernoVents, isInferno && noExtChest);
    }

    private void resetPartPivots() {
        this.head.x = 0.0F;
        this.head.y = 0.0F;
        this.head.z = 0.0F;
        this.hat.x = 0.0F;
        this.hat.y = 0.0F;
        this.hat.z = 0.0F;
        this.body.x = 0.0F;
        this.body.y = 0.0F;
        this.body.z = 0.0F;
        this.body.xScale = 1.0F;
        this.body.yScale = 1.0F;
        this.body.zScale = 1.0F;
        if (this.chestCore != null) {
            this.chestCore.xScale = 1.0F;
            this.chestCore.yScale = 1.0F;
            this.chestCore.zScale = 1.0F;
        }
        if (this.plagueSacBack != null) {
            this.plagueSacBack.xScale = 1.0F;
            this.plagueSacBack.yScale = 1.0F;
            this.plagueSacBack.zScale = 1.0F;
        }
        if (this.plagueSacShoulder != null) {
            this.plagueSacShoulder.xScale = 1.0F;
            this.plagueSacShoulder.yScale = 1.0F;
            this.plagueSacShoulder.zScale = 1.0F;
        }
        if (this.tassetApron != null) {
            this.tassetApron.xRot = -0.10F;
        }
        this.rightArm.x = -PostureClamp.BOSS_SHOULDER_X;
        this.rightArm.y = PostureClamp.BOSS_SHOULDER_Y;
        this.rightArm.z = 0.0F;
        this.leftArm.x = PostureClamp.BOSS_SHOULDER_X;
        this.leftArm.y = PostureClamp.BOSS_SHOULDER_Y;
        this.leftArm.z = 0.0F;
        this.rightLeg.x = -2.5F;
        this.rightLeg.y = PostureClamp.DEFAULT_HIP_Y;
        this.rightLeg.z = 0.0F;
        this.leftLeg.x = 2.5F;
        this.leftLeg.y = PostureClamp.DEFAULT_HIP_Y;
        this.leftLeg.z = 0.0F;
    }

    private float applyBossAttackPose(int animType, int animTick) {
        switch (animType) {
            case TacticalAttacker.ATTACK_SPECIAL -> {
                int total = 26;
                int elapsed = (int) PostureClamp.clamp(animTick, 0, total);
                if (elapsed <= 16) {
                    float w = PostureClamp.easeInOutSine(elapsed / 16.0F);
                    this.body.xRot = PostureClamp.lerp(w, 0.06F, -0.22F);
                    this.head.xRot = PostureClamp.clamp(this.head.xRot - 0.35F * w, -1.0F, 0.5F);
                    this.rightArm.xRot = PostureClamp.lerp(w, -0.90F, -2.50F);
                    this.leftArm.xRot = PostureClamp.lerp(w, -0.90F, -2.50F);
                    this.rightArm.zRot = PostureClamp.lerp(w, 0.18F, 0.38F);
                    this.leftArm.zRot = PostureClamp.lerp(w, -0.18F, -0.38F);
                    return 0.55F * w;
                } else {
                    float s = PostureClamp.easeOutCubic((elapsed - 16) / 10.0F);
                    this.body.xRot = PostureClamp.lerp(s, -0.22F, 0.32F);
                    this.rightArm.xRot = PostureClamp.lerp(s, -2.50F, -0.30F);
                    this.leftArm.xRot = PostureClamp.lerp(s, -2.50F, -0.30F);
                    this.rightArm.zRot = PostureClamp.lerp(s, 0.38F, 0.24F);
                    this.leftArm.zRot = PostureClamp.lerp(s, -0.38F, -0.24F);
                    return PostureClamp.lerp(s, 0.58F, 0.15F);
                }
            }
            case TacticalAttacker.ATTACK_HEAVY -> {
                int total = 34;
                int elapsed = (int) PostureClamp.clamp(animTick, 0, total);
                if (elapsed <= 14) {
                    float w = PostureClamp.easeInOutSine(elapsed / 14.0F);
                    this.body.xRot = PostureClamp.lerp(w, 0.08F, -0.18F);
                    this.rightArm.xRot = PostureClamp.lerp(w, -0.95F, -2.42F);
                    this.leftArm.xRot = PostureClamp.lerp(w, -0.90F, -2.32F);
                    this.rightArm.zRot = 0.32F;
                    this.leftArm.zRot = -0.32F;
                    return 0.48F * w;
                } else if (elapsed <= 18) {
                    float s = PostureClamp.easeOutCubic((elapsed - 14) / 4.0F);
                    this.body.xRot = PostureClamp.lerp(s, -0.18F, 0.30F);
                    this.rightArm.xRot = PostureClamp.lerp(s, -2.42F, -0.35F);
                    this.leftArm.xRot = PostureClamp.lerp(s, -2.32F, -0.40F);
                    return 0.45F;
                } else {
                    float r = PostureClamp.easeInOutSine((elapsed - 18) / 16.0F);
                    this.body.xRot = PostureClamp.lerp(r, 0.30F, 0.08F);
                    this.rightArm.xRot = PostureClamp.lerp(r, -0.35F, -0.90F);
                    this.leftArm.xRot = PostureClamp.lerp(r, -0.40F, -0.85F);
                    return PostureClamp.lerp(r, 0.40F, 0.08F);
                }
            }
            case TacticalAttacker.ATTACK_SWEEP -> {
                int total = 25;
                int elapsed = (int) PostureClamp.clamp(animTick, 0, total);
                if (elapsed <= 10) {
                    float w = PostureClamp.easeInOutSine(elapsed / 10.0F);
                    this.body.yRot = 0.40F * w;
                    this.rightArm.xRot = -1.22F;
                    this.rightArm.yRot = PostureClamp.lerp(w, 0.0F, 0.58F);
                    this.rightArm.zRot = 0.44F;
                    return 0.25F * w;
                } else if (elapsed <= 13) {
                    float s = PostureClamp.easeOutCubic((elapsed - 10) / 3.0F);
                    this.body.yRot = PostureClamp.lerp(s, 0.40F, -0.40F);
                    this.rightArm.xRot = -1.12F;
                    this.rightArm.yRot = PostureClamp.lerp(s, 0.58F, -0.44F);
                    return 0.35F;
                } else {
                    float r = PostureClamp.easeInOutSine((elapsed - 13) / 12.0F);
                    this.body.yRot = PostureClamp.lerp(r, -0.40F, 0.0F);
                    this.rightArm.xRot = PostureClamp.lerp(r, -1.12F, -0.90F);
                    this.rightArm.yRot = PostureClamp.lerp(r, -0.44F, -0.06F);
                    return 0.12F;
                }
            }
            default -> {
                int total = animType == TacticalAttacker.ATTACK_THRUST ? 24 : 17;
                int windup = animType == TacticalAttacker.ATTACK_THRUST ? 8 : 6;
                int elapsed = (int) PostureClamp.clamp(animTick, 0, total);
                if (elapsed <= windup) {
                    float w = PostureClamp.easeInOutSine(elapsed / (float) windup);
                    this.body.yRot = 0.28F * w;
                    this.rightArm.xRot = PostureClamp.lerp(w, -0.90F, -1.85F);
                    this.rightArm.zRot = 0.26F;
                    return 0.22F * w;
                } else if (elapsed <= windup + 3) {
                    float s = PostureClamp.easeOutCubic((elapsed - windup) / 3.0F);
                    this.body.yRot = PostureClamp.lerp(s, 0.28F, -0.26F);
                    this.rightArm.xRot = PostureClamp.lerp(s, -1.85F, -0.45F);
                    return 0.32F;
                } else {
                    float r = PostureClamp.easeInOutSine((elapsed - windup - 3) / (float) (total - windup - 3));
                    this.body.yRot = PostureClamp.lerp(r, -0.26F, 0.0F);
                    this.rightArm.xRot = PostureClamp.lerp(r, -0.45F, -0.88F);
                    return 0.08F;
                }
            }
        }
    }

    private void applyGroundedBossKinematics() {
        float pitch = this.body.xRot;
        float yaw = this.body.yRot;
        float dropY = PostureClamp.computeUpperBodyDropY(pitch);

        this.head.y = dropY;
        this.hat.copyFrom(this.head);
        this.body.y = dropY;

        float shoulderY = PostureClamp.computeShoulderY(PostureClamp.BOSS_SHOULDER_Y, pitch);
        this.rightArm.x = PostureClamp.computeShoulderX(true, PostureClamp.BOSS_SHOULDER_X, yaw);
        this.rightArm.y = shoulderY;
        this.rightArm.z = PostureClamp.computeShoulderZ(
                true, PostureClamp.BOSS_SHOULDER_X, PostureClamp.BOSS_SHOULDER_Y, pitch, yaw);

        this.leftArm.x = PostureClamp.computeShoulderX(false, PostureClamp.BOSS_SHOULDER_X, yaw);
        this.leftArm.y = shoulderY;
        this.leftArm.z = PostureClamp.computeShoulderZ(
                false, PostureClamp.BOSS_SHOULDER_X, PostureClamp.BOSS_SHOULDER_Y, pitch, yaw);

        float hipY = PostureClamp.computeGroundedHipY();
        float hipZ = PostureClamp.computeHipZ(pitch);
        this.rightLeg.y = hipY;
        this.rightLeg.z = hipZ;
        this.leftLeg.y = hipY;
        this.leftLeg.z = hipZ;
    }
}
