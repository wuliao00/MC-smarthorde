package dev.smarthorde.client.model;

import dev.smarthorde.entity.EliteRole;
import dev.smarthorde.entity.SmartZombie;
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
import javax.annotation.Nullable;

/**
 * 精英战术僵尸专属多部件 3D 模型（128x128 独立 UV 布局）：
 * <ul>
 *   <li>立体骨骼雕塑：深陷眉弓、战术腰封、双前臂护腕，告别原版扁平 6 方块火柴盒；</li>
 *   <li>四兵种专属 3D 几何体按需显隐：
 *     突袭者（兜帽+飘带+双臂袖刃）、先锋（重装面罩+顶冠+胸甲+层叠肩甲）、
 *     狂战士（弯折骨角+尖刺护肩+背脊骨刺）、统领（鎏金战冠+百夫长横羽+重肩甲+动态战袍披风）；</li>
 *   <li>严密防穿模：上身弯腰下沉而双脚锁死接地不浮空、副手大盾过顶防穿颅、拾取外部盔甲自动隐藏冲突部件。</li>
 * </ul>
 */
public class SmartZombieModel<T extends SmartZombie> extends ZombieModel<T> {

    @Nullable private final ModelPart browRidge;
    @Nullable private final ModelPart beltHarness;
    @Nullable private final ModelPart vambraceR;
    @Nullable private final ModelPart vambraceL;
    @Nullable private final ModelPart greavePlateR;
    @Nullable private final ModelPart greavePlateL;

    @Nullable private final ModelPart standardVisorBand;
    @Nullable private final ModelPart standardPauldronL;
    @Nullable private final ModelPart standardBackPack;

    @Nullable private final ModelPart raiderHood;
    @Nullable private final ModelPart raiderScarf;
    @Nullable private final ModelPart raiderBladeR;
    @Nullable private final ModelPart raiderBladeL;

    @Nullable private final ModelPart vanguardHelm;
    @Nullable private final ModelPart vanguardCrest;
    @Nullable private final ModelPart vanguardCuirass;
    @Nullable private final ModelPart vanguardTassets;
    @Nullable private final ModelPart vanguardPauldronR;
    @Nullable private final ModelPart vanguardPauldronL;

    @Nullable private final ModelPart berserkerHornR;
    @Nullable private final ModelPart berserkerHornL;
    @Nullable private final ModelPart berserkerPauldronR;
    @Nullable private final ModelPart berserkerClawR;
    @Nullable private final ModelPart berserkerClawL;
    @Nullable private final ModelPart berserkerSpines;

    @Nullable private final ModelPart commanderCrown;
    @Nullable private final ModelPart commanderPlume;
    @Nullable private final ModelPart commanderPauldronR;
    @Nullable private final ModelPart commanderPauldronL;
    @Nullable private final ModelPart commanderCuirass;
    @Nullable private final ModelPart commanderPteruges;
    @Nullable private final ModelPart commanderCape;

    public SmartZombieModel(ModelPart root) {
        super(root);
        this.browRidge = safeChild(this.head, "brow_ridge");
        this.beltHarness = safeChild(this.body, "belt_harness");
        this.vambraceR = safeChild(this.rightArm, "vambrace_r");
        this.vambraceL = safeChild(this.leftArm, "vambrace_l");
        this.greavePlateR = safeChild(this.rightLeg, "greave_plate_r");
        this.greavePlateL = safeChild(this.leftLeg, "greave_plate_l");

        this.standardVisorBand = safeChild(this.head, "standard_visor_band");
        this.standardPauldronL = safeChild(this.leftArm, "standard_pauldron_l");
        this.standardBackPack = safeChild(this.body, "standard_back_pack");

        this.raiderHood = safeChild(this.head, "raider_hood");
        this.raiderScarf = safeChild(this.body, "raider_scarf");
        this.raiderBladeR = safeChild(this.rightArm, "raider_blade_r");
        this.raiderBladeL = safeChild(this.leftArm, "raider_blade_l");

        this.vanguardHelm = safeChild(this.head, "vanguard_helm");
        this.vanguardCrest = safeChild(this.head, "vanguard_crest");
        this.vanguardCuirass = safeChild(this.body, "vanguard_cuirass");
        this.vanguardTassets = safeChild(this.body, "vanguard_tassets");
        this.vanguardPauldronR = safeChild(this.rightArm, "vanguard_pauldron_r");
        this.vanguardPauldronL = safeChild(this.leftArm, "vanguard_pauldron_l");

        this.berserkerHornR = safeChild(this.head, "berserker_horn_r");
        this.berserkerHornL = safeChild(this.head, "berserker_horn_l");
        this.berserkerPauldronR = safeChild(this.rightArm, "berserker_pauldron_r");
        this.berserkerClawR = safeChild(this.rightArm, "berserker_claw_r");
        this.berserkerClawL = safeChild(this.leftArm, "berserker_claw_l");
        this.berserkerSpines = safeChild(this.body, "berserker_spines");

        this.commanderCrown = safeChild(this.head, "commander_crown");
        this.commanderPlume = safeChild(this.head, "commander_plume");
        this.commanderPauldronR = safeChild(this.rightArm, "commander_pauldron_r");
        this.commanderPauldronL = safeChild(this.leftArm, "commander_pauldron_l");
        this.commanderCuirass = safeChild(this.body, "commander_cuirass");
        this.commanderPteruges = safeChild(this.body, "commander_pteruges");
        this.commanderCape = safeChild(this.body, "commander_cape");
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

        // 1. 头部与兵种头饰
        PartDefinition head = root.addOrReplaceChild("head",
                CubeListBuilder.create()
                        .texOffs(0, 0).addBox(-4.0F, -8.0F, -4.0F, 8.0F, 8.0F, 8.0F),
                PartPose.ZERO);
        root.addOrReplaceChild("hat",
                CubeListBuilder.create()
                        .texOffs(32, 0).addBox(-4.0F, -8.0F, -4.0F, 8.0F, 8.0F, 8.0F, new CubeDeformation(0.35F)),
                PartPose.ZERO);

        head.addOrReplaceChild("brow_ridge",
                CubeListBuilder.create()
                        .texOffs(64, 0).addBox(-4.0F, -5.6F, -4.6F, 8.0F, 2.0F, 1.0F, new CubeDeformation(0.12F)),
                PartPose.ZERO);
        head.addOrReplaceChild("standard_visor_band",
                CubeListBuilder.create()
                        .texOffs(0, 112).addBox(-4.0F, -8.1F, -4.0F, 8.0F, 3.0F, 8.0F, new CubeDeformation(0.42F)),
                PartPose.ZERO);
        head.addOrReplaceChild("raider_hood",
                CubeListBuilder.create()
                        .texOffs(64, 20).addBox(-4.0F, -8.0F, -4.0F, 8.0F, 8.0F, 8.0F, new CubeDeformation(0.55F)),
                PartPose.ZERO);
        head.addOrReplaceChild("vanguard_helm",
                CubeListBuilder.create()
                        .texOffs(64, 38).addBox(-4.0F, -8.0F, -4.0F, 8.0F, 8.0F, 8.0F, new CubeDeformation(0.58F)),
                PartPose.ZERO);
        head.addOrReplaceChild("vanguard_crest",
                CubeListBuilder.create()
                        .texOffs(98, 44).addBox(-1.0F, -10.0F, -4.5F, 2.0F, 2.0F, 9.0F),
                PartPose.ZERO);
        head.addOrReplaceChild("berserker_horn_r",
                CubeListBuilder.create()
                        .texOffs(70, 64).addBox(-1.0F, -6.0F, -1.0F, 2.0F, 6.0F, 2.0F),
                PartPose.offsetAndRotation(-4.0F, -6.5F, -1.0F, 0.35F, 0.0F, -0.45F));
        head.addOrReplaceChild("berserker_horn_l",
                CubeListBuilder.create()
                        .texOffs(80, 64).addBox(-1.0F, -6.0F, -1.0F, 2.0F, 6.0F, 2.0F),
                PartPose.offsetAndRotation(4.0F, -6.5F, -1.0F, 0.35F, 0.0F, 0.45F));
        head.addOrReplaceChild("commander_crown",
                CubeListBuilder.create()
                        .texOffs(0, 80).addBox(-4.0F, -9.2F, -4.0F, 8.0F, 4.0F, 8.0F, new CubeDeformation(0.50F)),
                PartPose.ZERO);
        head.addOrReplaceChild("commander_plume",
                CubeListBuilder.create()
                        .texOffs(34, 80).addBox(-1.0F, -13.0F, -4.5F, 2.0F, 4.0F, 10.0F),
                PartPose.ZERO);

        // 2. 躯干与胸甲/背脊/披风
        PartDefinition body = root.addOrReplaceChild("body",
                CubeListBuilder.create()
                        .texOffs(16, 16).addBox(-4.0F, 0.0F, -2.0F, 8.0F, 12.0F, 4.0F),
                PartPose.ZERO);
        body.addOrReplaceChild("belt_harness",
                CubeListBuilder.create()
                        .texOffs(64, 4).addBox(-4.0F, 9.0F, -2.0F, 8.0F, 2.0F, 4.0F, new CubeDeformation(0.30F)),
                PartPose.ZERO);
        body.addOrReplaceChild("standard_back_pack",
                CubeListBuilder.create()
                        .texOffs(56, 112).addBox(-3.0F, 1.5F, 2.0F, 6.0F, 7.0F, 2.0F),
                PartPose.ZERO);
        body.addOrReplaceChild("raider_scarf",
                CubeListBuilder.create()
                        .texOffs(98, 20).addBox(-3.0F, 0.0F, 0.0F, 6.0F, 10.0F, 1.0F),
                PartPose.offsetAndRotation(0.0F, 0.8F, 2.2F, 0.26F, 0.0F, 0.0F));
        body.addOrReplaceChild("vanguard_cuirass",
                CubeListBuilder.create()
                        .texOffs(0, 64).addBox(-4.0F, 0.0F, -2.0F, 8.0F, 10.0F, 4.0F, new CubeDeformation(0.46F)),
                PartPose.ZERO);
        body.addOrReplaceChild("vanguard_tassets",
                CubeListBuilder.create()
                        .texOffs(86, 96).addBox(-4.0F, 9.6F, -2.0F, 8.0F, 3.0F, 4.0F, new CubeDeformation(0.36F)),
                PartPose.ZERO);
        body.addOrReplaceChild("berserker_spines",
                CubeListBuilder.create()
                        .texOffs(112, 64).addBox(-1.0F, 1.0F, 2.0F, 2.0F, 9.0F, 2.0F),
                PartPose.ZERO);
        body.addOrReplaceChild("commander_cuirass",
                CubeListBuilder.create()
                        .texOffs(0, 96).addBox(-4.0F, 0.0F, -2.0F, 8.0F, 10.0F, 4.0F, new CubeDeformation(0.42F)),
                PartPose.ZERO);
        body.addOrReplaceChild("commander_pteruges",
                CubeListBuilder.create()
                        .texOffs(74, 112).addBox(-4.0F, 9.5F, -2.0F, 8.0F, 3.0F, 4.0F, new CubeDeformation(0.36F)),
                PartPose.ZERO);
        body.addOrReplaceChild("commander_cape",
                CubeListBuilder.create()
                        .texOffs(26, 96).addBox(-4.5F, 0.0F, 0.0F, 9.0F, 15.0F, 1.0F),
                PartPose.offsetAndRotation(0.0F, 0.5F, 2.4F, 0.22F, 0.0F, 0.0F));

        // 3. 左右双臂、护腕、袖刃与立体肩甲
        PartDefinition rightArm = root.addOrReplaceChild("right_arm",
                CubeListBuilder.create()
                        .texOffs(40, 16).addBox(-3.0F, -2.0F, -2.0F, 4.0F, 12.0F, 4.0F),
                PartPose.offset(-5.0F, 2.0F, 0.0F));
        rightArm.addOrReplaceChild("vambrace_r",
                CubeListBuilder.create()
                        .texOffs(64, 11).addBox(-3.0F, 4.0F, -2.0F, 4.0F, 4.0F, 4.0F, new CubeDeformation(0.26F)),
                PartPose.ZERO);
        rightArm.addOrReplaceChild("raider_blade_r",
                CubeListBuilder.create()
                        .texOffs(98, 32).addBox(-3.8F, 3.0F, -1.0F, 1.0F, 8.0F, 2.0F),
                PartPose.ZERO);
        rightArm.addOrReplaceChild("vanguard_pauldron_r",
                CubeListBuilder.create()
                        .texOffs(26, 64).addBox(-4.0F, -3.0F, -2.5F, 5.0F, 5.0F, 5.0F, new CubeDeformation(0.22F)),
                PartPose.offsetAndRotation(0.0F, 0.0F, 0.0F, 0.0F, 0.0F, -0.10F));
        rightArm.addOrReplaceChild("berserker_pauldron_r",
                CubeListBuilder.create()
                        .texOffs(90, 64).addBox(-4.0F, -3.0F, -2.5F, 5.0F, 4.0F, 5.0F),
                PartPose.offsetAndRotation(0.0F, 0.0F, 0.0F, 0.0F, 0.0F, -0.12F));
        rightArm.addOrReplaceChild("berserker_claw_r",
                CubeListBuilder.create()
                        .texOffs(100, 112).addBox(-3.8F, 4.0F, -1.0F, 1.0F, 7.0F, 2.0F),
                PartPose.ZERO);
        rightArm.addOrReplaceChild("commander_pauldron_r",
                CubeListBuilder.create()
                        .texOffs(60, 80).addBox(-4.0F, -3.0F, -2.5F, 5.0F, 5.0F, 5.0F, new CubeDeformation(0.26F)),
                PartPose.offsetAndRotation(0.0F, 0.0F, 0.0F, 0.0F, 0.0F, -0.10F));

        PartDefinition leftArm = root.addOrReplaceChild("left_arm",
                CubeListBuilder.create()
                        .texOffs(32, 48).addBox(-1.0F, -2.0F, -2.0F, 4.0F, 12.0F, 4.0F),
                PartPose.offset(5.0F, 2.0F, 0.0F));
        leftArm.addOrReplaceChild("vambrace_l",
                CubeListBuilder.create()
                        .texOffs(82, 11).addBox(-1.0F, 4.0F, -2.0F, 4.0F, 4.0F, 4.0F, new CubeDeformation(0.26F)),
                PartPose.ZERO);
        leftArm.addOrReplaceChild("raider_blade_l",
                CubeListBuilder.create()
                        .texOffs(106, 32).addBox(2.8F, 3.0F, -1.0F, 1.0F, 8.0F, 2.0F),
                PartPose.ZERO);
        leftArm.addOrReplaceChild("berserker_claw_l",
                CubeListBuilder.create()
                        .texOffs(112, 96).addBox(2.8F, 4.0F, -1.0F, 1.0F, 7.0F, 2.0F),
                PartPose.ZERO);
        leftArm.addOrReplaceChild("standard_pauldron_l",
                CubeListBuilder.create()
                        .texOffs(34, 112).addBox(-1.0F, -2.8F, -2.5F, 5.0F, 4.0F, 5.0F, new CubeDeformation(0.18F)),
                PartPose.offsetAndRotation(0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.08F));
        leftArm.addOrReplaceChild("vanguard_pauldron_l",
                CubeListBuilder.create()
                        .texOffs(48, 64).addBox(-1.0F, -3.0F, -2.5F, 5.0F, 5.0F, 5.0F, new CubeDeformation(0.22F)),
                PartPose.offsetAndRotation(0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.10F));
        leftArm.addOrReplaceChild("commander_pauldron_l",
                CubeListBuilder.create()
                        .texOffs(82, 80).addBox(-1.0F, -3.0F, -2.5F, 5.0F, 5.0F, 5.0F, new CubeDeformation(0.26F)),
                PartPose.offsetAndRotation(0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.10F));

        // 4. 双腿与立体护膝胫甲
        PartDefinition rightLeg = root.addOrReplaceChild("right_leg",
                CubeListBuilder.create()
                        .texOffs(0, 16).addBox(-2.0F, 0.0F, -2.0F, 4.0F, 12.0F, 4.0F),
                PartPose.offset(-1.9F, 12.0F, 0.0F));
        rightLeg.addOrReplaceChild("greave_plate_r",
                CubeListBuilder.create()
                        .texOffs(50, 96).addBox(-2.0F, 4.5F, -2.0F, 4.0F, 5.0F, 4.0F, new CubeDeformation(0.24F)),
                PartPose.ZERO);

        PartDefinition leftLeg = root.addOrReplaceChild("left_leg",
                CubeListBuilder.create()
                        .texOffs(16, 48).addBox(-2.0F, 0.0F, -2.0F, 4.0F, 12.0F, 4.0F),
                PartPose.offset(1.9F, 12.0F, 0.0F));
        leftLeg.addOrReplaceChild("greave_plate_l",
                CubeListBuilder.create()
                        .texOffs(68, 96).addBox(-2.0F, 4.5F, -2.0F, 4.0F, 5.0F, 4.0F, new CubeDeformation(0.24F)),
                PartPose.ZERO);

        return LayerDefinition.create(mesh, 128, 128);
    }

    @Override
    public void prepareMobModel(T entity, float limbSwing, float limbSwingAmount, float partialTick) {
        super.prepareMobModel(entity, limbSwing, limbSwingAmount, partialTick);
        this.leftArmPose = entity.isShieldBlocking() ? HumanoidModel.ArmPose.BLOCK : HumanoidModel.ArmPose.EMPTY;
        this.rightArmPose = !entity.getMainHandItem().isEmpty()
                ? HumanoidModel.ArmPose.ITEM
                : HumanoidModel.ArmPose.EMPTY;
    }

    @Override
    public void setupAnim(T entity, float limbSwing, float limbSwingAmount,
                          float ageInTicks, float netHeadYaw, float headPitch) {
        float clampedYaw = PostureClamp.clampHeadYawDeg(netHeadYaw);
        float clampedPitch = PostureClamp.clampHeadPitchDeg(headPitch);
        float safeSwingAmount = PostureClamp.clampLimbSwingAmount(limbSwingAmount);

        resetPartPivots();
        super.setupAnim(entity, limbSwing, safeSwingAmount, ageInTicks, clampedYaw, clampedPitch);

        EliteRole role = entity.getEliteRole();
        updateRolePartVisibility(entity, role);

        int animType = entity.getAttackAnimType();
        int animTick = entity.getAttackAnimTick();
        int dodgeTicks = entity.getDodgeAnimTicks();
        boolean climbing = entity.isWallClimbing();
        boolean blocking = entity.isShieldBlocking();
        boolean hasWeapon = !entity.getMainHandItem().isEmpty();
        boolean offhandShield = entity.hasOffhandShield();

        this.body.xRot = 0.06F + 0.05F * safeSwingAmount;
        this.body.yRot = 0.0F;
        this.body.zRot = 0.0F;

        if (role == EliteRole.RAIDER) {
            this.body.xRot += 0.08F;
        } else if (role == EliteRole.BERSERKER && entity.isFrenzyActive()) {
            this.body.xRot += 0.11F;
        } else if (role == EliteRole.COMMANDER) {
            this.body.xRot = 0.03F + 0.03F * safeSwingAmount;
        }

        // 1. 基准上肢姿态：脱战自然垂臂摆手；战斗态（服务端持续同步 isAggressive）保持稳定战术架势
        if (!entity.isAggressive() && animType == TacticalAttacker.ATTACK_NONE && !climbing && !blocking) {
            float swing = Mth.cos(limbSwing * 0.6662F + (float) Math.PI) * 1.15F * safeSwingAmount;
            float oppSwing = Mth.cos(limbSwing * 0.6662F) * 1.15F * safeSwingAmount;
            this.rightArm.xRot = (hasWeapon ? -0.30F : -0.10F) + swing * 0.55F;
            this.leftArm.xRot = (offhandShield ? -0.45F : -0.10F) + oppSwing * 0.55F;
            this.rightArm.yRot = 0.0F;
            this.leftArm.yRot = offhandShield ? 0.18F : 0.0F;
            this.rightArm.zRot = 0.07F;
            this.leftArm.zRot = offhandShield ? -0.15F : -0.07F;
        } else {
            applyCombatGuardPose(role, hasWeapon, offhandShield, limbSwing, safeSwingAmount, ageInTicks);
        }

        // 2. 爬墙交替攀援动作
        if (climbing) {
            float climbCycle = ageInTicks * 0.42F;
            this.body.xRot = 0.14F;
            this.head.xRot = PostureClamp.clamp(this.head.xRot - 0.32F, -1.05F, 0.5F);
            this.rightArm.xRot = -1.92F + 0.48F * Mth.sin(climbCycle);
            this.leftArm.xRot = offhandShield
                    ? -1.15F + 0.22F * Mth.sin(climbCycle + (float) Math.PI)
                    : -1.92F + 0.48F * Mth.sin(climbCycle + (float) Math.PI);
            this.rightArm.yRot = 0.10F;
            this.leftArm.yRot = -0.10F;
            this.rightArm.zRot = 0.22F;
            this.leftArm.zRot = -0.22F;
            this.rightLeg.xRot = -0.52F + 0.34F * Mth.cos(climbCycle);
            this.leftLeg.xRot = -0.52F - 0.34F * Mth.cos(climbCycle);
        }

        // 3. 战术侧扑闪避收身动作（8 tick 正弦拱形过渡，压低重心并护住肋部）
        if (dodgeTicks > 0 && !climbing && animType == TacticalAttacker.ATTACK_NONE) {
            float dodgePhase = Mth.sin(PostureClamp.clamp(dodgeTicks / 8.0F, 0.0F, 1.0F) * (float) Math.PI);
            float sideSign = (entity.getId() & 1) == 0 ? 1.0F : -1.0F;
            this.body.xRot += 0.16F * dodgePhase;
            this.body.yRot = 0.28F * sideSign * dodgePhase;
            this.rightArm.xRot = PostureClamp.lerp(dodgePhase, this.rightArm.xRot, -1.18F);
            this.leftArm.xRot = PostureClamp.lerp(dodgePhase, this.leftArm.xRot, -1.12F);
            this.rightArm.zRot = PostureClamp.lerp(dodgePhase, this.rightArm.zRot, 0.30F);
            this.leftArm.zRot = PostureClamp.lerp(dodgePhase, this.leftArm.zRot, -0.30F);
            this.rightLeg.xRot -= 0.28F * dodgePhase;
            this.leftLeg.xRot += 0.24F * dodgePhase;
        }

        // 4. 四段式战术出招关键帧与统领战吼姿态（animTick 为服务端正序已进行帧 0..total-1）
        if (animType != TacticalAttacker.ATTACK_NONE) {
            applyTacticalAttackPose(animType, animTick, offhandShield);
        }

        // 4. 先锋正面架盾防御姿态（最高优先级覆盖左臂）
        if (blocking) {
            this.leftArm.xRot = -1.02F;
            this.leftArm.yRot = 0.42F;
            this.leftArm.zRot = -0.14F;
            this.body.yRot = PostureClamp.clampBodyYaw(this.body.yRot - 0.16F);
            if (animType == TacticalAttacker.ATTACK_NONE) {
                this.rightArm.xRot = -0.68F;
                this.rightArm.yRot = -0.15F;
                this.rightArm.zRot = 0.16F;
            }
        }

        // 5. 飘带与百夫长披风动态随风摆动（仅向外后方飘起，绝不内穿后背与双腿）
        if (this.raiderScarf != null && this.raiderScarf.visible) {
            this.raiderScarf.xRot = 0.26F + 0.42F * safeSwingAmount + 0.06F * Mth.sin(ageInTicks * 0.18F);
            this.raiderScarf.yRot = 0.08F * Mth.cos(ageInTicks * 0.12F);
        }
        if (this.commanderCape != null && this.commanderCape.visible) {
            this.commanderCape.xRot = 0.22F + 0.46F * safeSwingAmount + 0.05F * Mth.sin(ageInTicks * 0.14F);
            this.commanderCape.zRot = 0.04F * Mth.cos(ageInTicks * 0.10F);
        }

        // 7. 防穿模姿态钳制 + 接地刚体耦合解算（乘船/矿车保留原版 -81° 坐姿防穿船底，步行双脚踩实 y=24）
        this.body.xRot = this.riding ? 0.0F : PostureClamp.clampBodyPitch(this.body.xRot);
        this.body.yRot = PostureClamp.clampBodyYaw(this.body.yRot);

        this.rightArm.yRot = PostureClamp.clampRightArmYRot(this.rightArm.yRot);
        this.leftArm.yRot = PostureClamp.clampLeftArmYRot(this.leftArm.yRot);
        this.rightArm.zRot = PostureClamp.clampRightArmZRot(this.rightArm.zRot, this.rightArm.xRot);
        this.leftArm.zRot = PostureClamp.clampLeftArmZRot(this.leftArm.zRot, this.leftArm.xRot);

        if (!this.riding) {
            this.rightLeg.xRot = PostureClamp.clamp(this.rightLeg.xRot, -1.05F, 1.05F);
            this.leftLeg.xRot = PostureClamp.clamp(this.leftLeg.xRot, -1.05F, 1.05F);
        }

        applyGroundedBodyKinematics();
    }

    private void updateRolePartVisibility(T entity, EliteRole role) {
        boolean noExtHelmet = entity.getItemBySlot(EquipmentSlot.HEAD).isEmpty();
        boolean noExtChest = entity.getItemBySlot(EquipmentSlot.CHEST).isEmpty();
        boolean noExtLegArmor = entity.getItemBySlot(EquipmentSlot.LEGS).isEmpty()
                && entity.getItemBySlot(EquipmentSlot.FEET).isEmpty();

        setVisible(this.browRidge, noExtHelmet && role != EliteRole.VANGUARD);
        setVisible(this.beltHarness, noExtChest);
        setVisible(this.vambraceR, true);
        setVisible(this.vambraceL, true);
        setVisible(this.greavePlateR, noExtLegArmor);
        setVisible(this.greavePlateL, noExtLegArmor);

        boolean isStandard = role == EliteRole.STANDARD;
        setVisible(this.standardVisorBand, isStandard && noExtHelmet);
        setVisible(this.standardPauldronL, isStandard && noExtChest);
        setVisible(this.standardBackPack, isStandard && noExtChest);

        boolean isRaider = role == EliteRole.RAIDER;
        setVisible(this.raiderHood, isRaider && noExtHelmet);
        setVisible(this.raiderScarf, isRaider && noExtChest);
        setVisible(this.raiderBladeR, isRaider);
        setVisible(this.raiderBladeL, isRaider);

        boolean isVanguard = role == EliteRole.VANGUARD;
        setVisible(this.vanguardHelm, isVanguard && noExtHelmet);
        setVisible(this.vanguardCrest, isVanguard && noExtHelmet);
        setVisible(this.vanguardCuirass, isVanguard && noExtChest);
        setVisible(this.vanguardTassets, isVanguard && noExtChest && noExtLegArmor);
        setVisible(this.vanguardPauldronR, isVanguard && noExtChest);
        setVisible(this.vanguardPauldronL, isVanguard && noExtChest);

        boolean isBerserker = role == EliteRole.BERSERKER;
        setVisible(this.berserkerHornR, isBerserker && noExtHelmet);
        setVisible(this.berserkerHornL, isBerserker && noExtHelmet);
        setVisible(this.berserkerPauldronR, isBerserker && noExtChest);
        setVisible(this.berserkerClawR, isBerserker);
        setVisible(this.berserkerClawL, isBerserker);
        setVisible(this.berserkerSpines, isBerserker && noExtChest);

        boolean isCommander = role == EliteRole.COMMANDER;
        setVisible(this.commanderCrown, isCommander && noExtHelmet);
        setVisible(this.commanderPlume, isCommander && noExtHelmet);
        setVisible(this.commanderPauldronR, isCommander && noExtChest);
        setVisible(this.commanderPauldronL, isCommander && noExtChest);
        setVisible(this.commanderCuirass, isCommander && noExtChest);
        setVisible(this.commanderPteruges, isCommander && noExtChest && noExtLegArmor);
        setVisible(this.commanderCape, isCommander && noExtChest);
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
        this.rightArm.x = -PostureClamp.DEFAULT_SHOULDER_X;
        this.rightArm.y = PostureClamp.DEFAULT_SHOULDER_Y;
        this.rightArm.z = 0.0F;
        this.leftArm.x = PostureClamp.DEFAULT_SHOULDER_X;
        this.leftArm.y = PostureClamp.DEFAULT_SHOULDER_Y;
        this.leftArm.z = 0.0F;
        this.rightLeg.x = -1.9F;
        this.rightLeg.y = PostureClamp.DEFAULT_HIP_Y;
        this.rightLeg.z = 0.0F;
        this.leftLeg.x = 1.9F;
        this.leftLeg.y = PostureClamp.DEFAULT_HIP_Y;
        this.leftLeg.z = 0.0F;
    }

    private void applyCombatGuardPose(EliteRole role, boolean hasWeapon, boolean offhandShield,
                                      float limbSwing, float safeSwingAmount, float ageInTicks) {
        float strideBob = Mth.cos(limbSwing * 0.6662F) * 0.20F * safeSwingAmount;
        float breath = Mth.sin(ageInTicks * 0.09F) * 0.04F;

        switch (role) {
            case RAIDER -> {
                this.rightArm.xRot = -0.82F + strideBob + breath;
                this.leftArm.xRot = -0.95F - strideBob - breath;
                this.rightArm.yRot = -0.10F;
                this.leftArm.yRot = 0.14F;
                this.rightArm.zRot = 0.18F;
                this.leftArm.zRot = -0.18F;
            }
            case VANGUARD -> {
                this.rightArm.xRot = -0.85F + strideBob * 0.6F + breath;
                this.leftArm.xRot = -0.82F - breath;
                this.rightArm.yRot = -0.08F;
                this.leftArm.yRot = 0.26F;
                this.rightArm.zRot = 0.14F;
                this.leftArm.zRot = -0.16F;
            }
            case BERSERKER -> {
                this.rightArm.xRot = -1.24F + strideBob + breath;
                this.leftArm.xRot = -1.02F - strideBob - breath;
                this.rightArm.yRot = 0.10F;
                this.leftArm.yRot = -0.08F;
                this.rightArm.zRot = 0.24F;
                this.leftArm.zRot = -0.22F;
            }
            case COMMANDER -> {
                this.rightArm.xRot = -0.92F + strideBob * 0.6F + breath;
                this.leftArm.xRot = -0.58F - strideBob * 0.5F;
                this.rightArm.yRot = -0.08F;
                this.leftArm.yRot = 0.12F;
                this.rightArm.zRot = 0.16F;
                this.leftArm.zRot = -0.16F;
            }
            default -> {
                this.rightArm.xRot = (hasWeapon ? -1.02F : -1.16F) + strideBob + breath;
                this.leftArm.xRot = (offhandShield ? -0.78F : -1.06F) - strideBob - breath;
                this.rightArm.yRot = -0.08F;
                this.leftArm.yRot = offhandShield ? 0.22F : 0.08F;
                this.rightArm.zRot = 0.12F;
                this.leftArm.zRot = -0.12F;
            }
        }
    }

    private void applyTacticalAttackPose(int animType, int animTick, boolean offhandShield) {
        switch (animType) {
            case TacticalAttacker.ATTACK_LIGHT -> {
                int total = 17;
                int elapsed = (int) PostureClamp.clamp(animTick, 0, total);
                if (elapsed <= 6) {
                    float w = PostureClamp.easeInOutSine(elapsed / 6.0F);
                    this.body.yRot = 0.28F * w;
                    this.rightArm.xRot = PostureClamp.lerp(w, -1.0F, -1.78F);
                    this.rightArm.yRot = PostureClamp.lerp(w, -0.08F, 0.28F);
                    this.rightArm.zRot = PostureClamp.lerp(w, 0.12F, 0.28F);
                } else if (elapsed <= 9) {
                    float s = PostureClamp.easeOutCubic((elapsed - 6) / 3.0F);
                    this.body.yRot = PostureClamp.lerp(s, 0.28F, -0.26F);
                    this.body.xRot += 0.10F * s;
                    this.rightArm.xRot = PostureClamp.lerp(s, -1.78F, -0.45F);
                    this.rightArm.yRot = PostureClamp.lerp(s, 0.28F, -0.30F);
                    this.rightArm.zRot = 0.16F;
                } else {
                    float r = PostureClamp.easeInOutSine((elapsed - 9) / 8.0F);
                    this.body.yRot = PostureClamp.lerp(r, -0.26F, 0.0F);
                    this.rightArm.xRot = PostureClamp.lerp(r, -0.45F, -1.02F);
                    this.rightArm.yRot = PostureClamp.lerp(r, -0.30F, -0.08F);
                }
                applyOffhandBalanceOrShieldBrace(offhandShield, -0.72F, 0.14F, -0.18F);
            }
            case TacticalAttacker.ATTACK_HEAVY -> {
                int total = 34;
                int elapsed = (int) PostureClamp.clamp(animTick, 0, total);
                if (elapsed <= 14) {
                    float w = PostureClamp.easeInOutSine(elapsed / 14.0F);
                    this.body.xRot = PostureClamp.lerp(w, 0.08F, -0.16F);
                    this.rightArm.xRot = PostureClamp.lerp(w, -1.1F, -2.35F);
                    this.rightArm.zRot = PostureClamp.lerp(w, 0.15F, 0.30F);
                    if (!offhandShield) {
                        this.leftArm.xRot = PostureClamp.lerp(w, -1.0F, -2.25F);
                        this.leftArm.zRot = PostureClamp.lerp(w, -0.15F, -0.30F);
                    }
                } else if (elapsed <= 18) {
                    float s = PostureClamp.easeOutCubic((elapsed - 14) / 4.0F);
                    this.body.xRot = PostureClamp.lerp(s, -0.16F, 0.28F);
                    this.rightArm.xRot = PostureClamp.lerp(s, -2.35F, -0.35F);
                    this.rightArm.zRot = 0.20F;
                    if (!offhandShield) {
                        this.leftArm.xRot = PostureClamp.lerp(s, -2.25F, -0.42F);
                        this.leftArm.zRot = -0.20F;
                    }
                } else {
                    float r = PostureClamp.easeInOutSine((elapsed - 18) / 16.0F);
                    this.body.xRot = PostureClamp.lerp(r, 0.28F, 0.08F);
                    this.rightArm.xRot = PostureClamp.lerp(r, -0.35F, -1.05F);
                    if (!offhandShield) {
                        this.leftArm.xRot = PostureClamp.lerp(r, -0.42F, -1.00F);
                    }
                }
                if (offhandShield) {
                    applyOffhandBalanceOrShieldBrace(true, -0.84F, 0.26F, -0.18F);
                }
            }
            case TacticalAttacker.ATTACK_SWEEP -> {
                int total = 25;
                int elapsed = (int) PostureClamp.clamp(animTick, 0, total);
                if (elapsed <= 10) {
                    float w = PostureClamp.easeInOutSine(elapsed / 10.0F);
                    this.body.yRot = 0.38F * w;
                    this.rightArm.xRot = -1.28F;
                    this.rightArm.yRot = PostureClamp.lerp(w, 0.0F, 0.56F);
                    this.rightArm.zRot = PostureClamp.lerp(w, 0.14F, 0.44F);
                } else if (elapsed <= 13) {
                    float s = PostureClamp.easeOutCubic((elapsed - 10) / 3.0F);
                    this.body.yRot = PostureClamp.lerp(s, 0.38F, -0.38F);
                    this.rightArm.xRot = -1.16F;
                    this.rightArm.yRot = PostureClamp.lerp(s, 0.56F, -0.42F);
                    this.rightArm.zRot = PostureClamp.lerp(s, 0.44F, 0.14F);
                } else {
                    float r = PostureClamp.easeInOutSine((elapsed - 13) / 12.0F);
                    this.body.yRot = PostureClamp.lerp(r, -0.38F, 0.0F);
                    this.rightArm.xRot = PostureClamp.lerp(r, -1.16F, -1.05F);
                    this.rightArm.yRot = PostureClamp.lerp(r, -0.42F, -0.08F);
                }
                applyOffhandBalanceOrShieldBrace(offhandShield, -0.70F, -0.18F, -0.22F);
            }
            case TacticalAttacker.ATTACK_THRUST -> {
                int total = 24;
                int elapsed = (int) PostureClamp.clamp(animTick, 0, total);
                if (elapsed <= 8) {
                    float w = PostureClamp.easeInOutSine(elapsed / 8.0F);
                    this.body.yRot = 0.30F * w;
                    this.body.xRot = PostureClamp.lerp(w, 0.08F, -0.08F);
                    this.rightArm.xRot = PostureClamp.lerp(w, -1.0F, -0.58F);
                    this.rightArm.yRot = 0.24F * w;
                    this.rightArm.zRot = 0.20F;
                } else if (elapsed <= 11) {
                    float s = PostureClamp.easeOutCubic((elapsed - 8) / 3.0F);
                    this.body.yRot = PostureClamp.lerp(s, 0.30F, -0.22F);
                    this.body.xRot = PostureClamp.lerp(s, -0.08F, 0.22F);
                    this.rightArm.xRot = PostureClamp.lerp(s, -0.58F, -1.52F);
                    this.rightArm.yRot = PostureClamp.lerp(s, 0.24F, -0.08F);
                } else {
                    float r = PostureClamp.easeInOutSine((elapsed - 11) / 13.0F);
                    this.body.yRot = PostureClamp.lerp(r, -0.22F, 0.0F);
                    this.body.xRot = PostureClamp.lerp(r, 0.22F, 0.08F);
                    this.rightArm.xRot = PostureClamp.lerp(r, -1.52F, -1.02F);
                }
                applyOffhandBalanceOrShieldBrace(offhandShield, -0.68F, -0.16F, -0.22F);
            }
            case TacticalAttacker.ATTACK_SPECIAL -> {
                int elapsed = (int) PostureClamp.clamp(animTick, 0, 16);
                float p = elapsed <= 8
                        ? PostureClamp.easeInOutSine(elapsed / 8.0F)
                        : 1.0F - PostureClamp.easeInOutSine((elapsed - 8) / 8.0F);
                this.body.xRot = -0.14F * p;
                this.rightArm.xRot = PostureClamp.lerp(p, -0.92F, -2.35F);
                this.rightArm.zRot = PostureClamp.lerp(p, 0.16F, 0.36F);
                if (!offhandShield) {
                    this.leftArm.xRot = PostureClamp.lerp(p, -0.58F, -1.65F);
                    this.leftArm.zRot = PostureClamp.lerp(p, -0.16F, -0.34F);
                } else {
                    applyOffhandBalanceOrShieldBrace(true, -0.84F, 0.26F, -0.18F);
                }
            }
            default -> {
            }
        }
    }

    private void applyOffhandBalanceOrShieldBrace(boolean offhandShield,
                                                  float fallbackX, float fallbackY, float fallbackZ) {
        if (offhandShield) {
            this.leftArm.xRot = -0.86F;
            this.leftArm.yRot = 0.26F;
            this.leftArm.zRot = -0.18F;
        } else {
            this.leftArm.xRot = fallbackX;
            this.leftArm.yRot = fallbackY;
            this.leftArm.zRot = fallbackZ;
        }
    }

    private void applyGroundedBodyKinematics() {
        float pitch = this.body.xRot;
        float yaw = this.body.yRot;
        float dropY = PostureClamp.computeUpperBodyDropY(pitch);

        this.head.y = dropY;
        this.hat.copyFrom(this.head);
        this.body.y = dropY;

        float shoulderY = PostureClamp.computeShoulderY(PostureClamp.DEFAULT_SHOULDER_Y, pitch);
        this.rightArm.x = PostureClamp.computeShoulderX(true, yaw);
        this.rightArm.y = shoulderY;
        this.rightArm.z = PostureClamp.computeShoulderZ(true, pitch, yaw);

        this.leftArm.x = PostureClamp.computeShoulderX(false, yaw);
        this.leftArm.y = shoulderY;
        this.leftArm.z = PostureClamp.computeShoulderZ(false, pitch, yaw);

        float hipY = PostureClamp.computeGroundedHipY();
        float hipZ = PostureClamp.computeHipZ(pitch);
        this.rightLeg.y = hipY;
        this.rightLeg.z = hipZ;
        this.leftLeg.y = hipY;
        this.leftLeg.z = hipZ;
    }
}
