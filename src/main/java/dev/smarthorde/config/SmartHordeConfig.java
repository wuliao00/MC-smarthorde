package dev.smarthorde.config;

import net.neoforged.neoforge.common.ModConfigSpec;
// 若编译报找不到 ModConfigSpec，改用：import net.neoforged.fml.config.ModConfigSpec;

import java.util.List;

/**
 * SmartHorde 配置中心。逻辑层一律读取本类，禁止硬编码魔法数字。
 * 难度倍率在 {@link DifficultyPreset}；预设→数值的换算在轮6落地。
 */
public final class SmartHordeConfig {
    private static final ModConfigSpec.Builder B = new ModConfigSpec.Builder();

    // ===== general =====
    public static final ModConfigSpec.BooleanValue ENABLED =
            B.comment("模组总开关").define("general.enabled", true);
    public static final ModConfigSpec.ConfigValue<String> DIFFICULTY_PRESET =
            B.comment("难度预设: easy / normal / hard / nightmare（支持热重载）").define("difficulty.preset", "normal");

    // ===== combat =====
    public static final ModConfigSpec.BooleanValue COMBO_ATTACKS =
            B.comment("招式化连招攻击").define("combat.comboAttacksEnabled", true);
    public static final ModConfigSpec.BooleanValue TELEGRAPH =
            B.comment("攻击前摇提示（动画/粒子/音效）").define("combat.telegraphEnabled", true);
    public static final ModConfigSpec.BooleanValue PHASE_BEHAVIOR =
            B.comment("按血量/时长切换 激进/防守/狂暴 状态").define("combat.phaseBehaviorEnabled", true);
    public static final ModConfigSpec.BooleanValue EVADE =
            B.comment("受击/被瞄准时闪避").define("combat.evadeEnabled", true);
    public static final ModConfigSpec.DoubleValue EVADE_CHANCE =
            B.comment("闪避触发概率 0..1").defineInRange("combat.evadeChance", 0.35D, 0D, 1D);
    public static final ModConfigSpec.IntValue EVADE_COOLDOWN =
            B.comment("闪避冷却(tick)").defineInRange("combat.evadeCooldownTicks", 40, 0, 6000);
    public static final ModConfigSpec.BooleanValue SMART_TARGET =
            B.comment("智能目标选择（威胁/仇恨/低血量优先）").define("combat.targetSelectionSmart", true);

    // ===== movement =====
    public static final ModConfigSpec.BooleanValue FLANK =
            B.comment("绕侧/绕后包抄").define("movement.flankingEnabled", true);
    public static final ModConfigSpec.BooleanValue MAINTAIN_DISTANCE =
            B.comment("维持最佳攻击距离（太近后撤/太远逼近/被风筝绕路）").define("movement.maintainDistanceEnabled", true);
    public static final ModConfigSpec.BooleanValue OPEN_DOORS =
            B.comment("允许开门（不算破坏方块）").define("movement.openDoorsEnabled", true);
    public static final ModConfigSpec.BooleanValue CLIMBING =
            B.comment("攀爬：够不到目标时爬墙/翻檐（轮4实现，绝不挖墙）").define("movement.climbingEnabled", true);
    public static final ModConfigSpec.BooleanValue STACK_UP =
            B.comment("叠罗汉：尸潮以骑乘抬升翻墙够人（轮4实现，不破坏方块）").define("movement.stackUpEnabled", true);

    // ===== horde（夜间自动尸潮）=====
    public static final ModConfigSpec.BooleanValue NIGHT_AUTO =
            B.comment("夜间自动触发尸潮").define("horde.nightAutoEnabled", true);
    public static final ModConfigSpec.ConfigValue<List<? extends String>> HORDE_DIMENSIONS =
            B.comment("允许尸潮的维度 id 列表").defineListAllowEmpty("horde.dimensions",
                    () -> List.of("minecraft:overworld"),
                    o -> o instanceof String s && s.contains(":"));
    public static final ModConfigSpec.IntValue NIGHT_START_TICK =
            B.comment("夜晚判定起点（世界tick，13000≈日落）").defineInRange("horde.nightStartTick", 13000, 0, 24000);
    public static final ModConfigSpec.IntValue NIGHT_END_TICK =
            B.comment("夜晚判定终点（世界tick，23000≈日出）").defineInRange("horde.nightEndTick", 23000, 0, 24000);
    public static final ModConfigSpec.IntValue WAVE_INTERVAL =
            B.comment("同夜内波次间隔(tick)").defineInRange("horde.waveIntervalTicks", 2400, 600, 24000);
    public static final ModConfigSpec.IntValue BASE_WAVE_SIZE =
            B.comment("基础波次规模（随波次与难度放大）").defineInRange("horde.baseWaveSize", 8, 1, 64);
    public static final ModConfigSpec.IntValue MAX_CONCURRENT =
            B.comment("尸潮最大同屏数（性能红线）").defineInRange("horde.maxConcurrentMobs", 40, 4, 200);
    public static final ModConfigSpec.IntValue MAX_SPAWN_LIGHT =
            B.comment("生成点最大光照（0-15，越暗越易刷）").defineInRange("horde.maxSpawnLight", 7, 0, 15);
    public static final ModConfigSpec.BooleanValue ANNOUNCE_WAVES =
            B.comment("波次来袭时 title/actionbar 提示").define("horde.announceWaves", true);
    public static final ModConfigSpec.IntValue COMP_MELEE =
            B.comment("波次构成权重-近战（运行时归一化）").defineInRange("horde.compMelee", 6, 0, 100);
    public static final ModConfigSpec.IntValue COMP_RANGED =
            B.comment("波次构成权重-远程").defineInRange("horde.compRanged", 2, 0, 100);
    public static final ModConfigSpec.IntValue COMP_FLANKER =
            B.comment("波次构成权重-绕后").defineInRange("horde.compFlanker", 3, 0, 100);
    public static final ModConfigSpec.IntValue COMP_RALLY =
            B.comment("波次构成权重-指挥（光环加移速/攻速）").defineInRange("horde.compRally", 1, 0, 100);

    // ===== effects =====
    public static final ModConfigSpec.BooleanValue EFFECT_PARTICLES =
            B.comment("粒子特效总开关").define("effects.particlesEnabled", true);
    public static final ModConfigSpec.BooleanValue EFFECT_SOUNDS =
            B.comment("模组音效总开关").define("effects.soundsEnabled", true);

    // ===== enhance（增强原版怪物，白名单制）=====
    public static final ModConfigSpec.BooleanValue ENHANCE_VANILLA =
            B.comment("是否增强原版怪物（替换其站桩AI）").define("enhance.enabled", true);
    public static final ModConfigSpec.ConfigValue<List<? extends String>> ENHANCE_WHITELIST =
            B.comment("增强的原版实体 id 白名单；末影人/监守者默认不列入，避免毁体验")
             .defineListAllowEmpty("enhance.whitelist",
                    () -> List.of(
                            "minecraft:zombie", "minecraft:zombie_villager", "minecraft:husk", "minecraft:drowned",
                            "minecraft:skeleton", "minecraft:stray",
                            "minecraft:spider", "minecraft:cave_spider",
                            "minecraft:pillager", "minecraft:vindicator"),
                    o -> o instanceof String s && s.contains(":"));

    // ===== boss =====
    public static final ModConfigSpec.BooleanValue BOSS_ENABLED =
            B.comment("Boss 级单位总开关").define("boss.enabled", true);
    public static final ModConfigSpec.DoubleValue BOSS_HEALTH_MUL =
            B.comment("Boss 生命倍率").defineInRange("boss.healthMultiplier", 1.0D, 0.1D, 100D);
    public static final ModConfigSpec.ConfigValue<List<? extends Double>> BOSS_PHASE_THRESHOLDS =
            B.comment("阶段切换血量阈值（0..1 降序）").defineListAllowEmpty("boss.phaseThresholds",
                    () -> List.of(0.75D, 0.5D, 0.25D),
                    o -> o instanceof Number n && n.doubleValue() > 0D && n.doubleValue() < 1D);
    public static final ModConfigSpec.BooleanValue BOSS_SUMMONS_HORDE =
            B.comment("Boss 切阶段时召唤一小波尸潮").define("boss.summonsHordeOnPhase", true);
    public static final ModConfigSpec.BooleanValue BOSS_BAR =
            B.comment("Boss 血条").define("boss.bossBarEnabled", true);

    // ===== performance（节流红线）=====
    public static final ModConfigSpec.IntValue AI_TICK_INTERVAL =
            B.comment("重型AI决策间隔(tick)，1=不节流").defineInRange("performance.aiTickInterval", 4, 1, 40);
    public static final ModConfigSpec.BooleanValue AUDIT_ENABLED =
            B.comment("是否启用性能审计日志（每60秒输出一次）").define("performance.auditEnabled", false);

    public static final ModConfigSpec SPEC = B.build();

    private SmartHordeConfig() {}
}
