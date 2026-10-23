package dev.smarthorde.config;

import net.neoforged.neoforge.common.ModConfigSpec;

import java.util.List;

/**
 * 配置中心。保留全部既有配置键兼容旧版 smarthorde-server.toml，
 * 并扩展精英兵种、Boss 主动技能、里程碑决战波配置及未加载安全读取封装。
 */
public final class SmartHordeConfig {

    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();

    // ---------------- difficulty ----------------
    public static final ModConfigSpec.ConfigValue<String> DIFFICULTY_PRESET = BUILDER
            .comment("难度预设: easy / normal / hard / nightmare")
            .define("difficulty.preset", "normal");

    // ---------------- inject ----------------
    public static final ModConfigSpec.BooleanValue INJECT_VANILLA = BUILDER
            .comment("增强原版僵尸（速度 x1.15、血量 x1.5、替换目标选择器与战术 AI）")
            .define("inject.vanillaMobs", true);

    // ---------------- boss ----------------
    public static final ModConfigSpec.BooleanValue BOSS_ENABLED = BUILDER
            .comment("允许 Boss 召唤")
            .define("boss.enabled", true);

    public static final ModConfigSpec.BooleanValue BOSS_BAR_ENABLED = BUILDER
            .comment("显示 BossBar")
            .define("boss.bossBarEnabled", true);

    public static final ModConfigSpec.DoubleValue BOSS_HEALTH_MULTIPLIER = BUILDER
            .comment("Boss 血量倍率")
            .defineInRange("boss.healthMultiplier", 1.0, 0.1, 10.0);

    public static final ModConfigSpec.BooleanValue BOSS_SUMMONS_HORDE_ON_PHASE = BUILDER
            .comment("Boss 切阶段时召唤仆从（冷却 5 秒）")
            .define("boss.summonsHordeOnPhase", true);

    public static final ModConfigSpec.BooleanValue BOSS_ABILITIES_ENABLED = BUILDER
            .comment("启用 Boss 四变体专属主动战技（震地咆哮/疫毒迷雾/凛冬霜环/烈焰新星）")
            .define("boss.abilitiesEnabled", true);

    public static final ModConfigSpec.ConfigValue<List<? extends Double>> BOSS_PHASE_THRESHOLDS = BUILDER
            .comment("Boss 阶段切换血量阈值（降序）")
            .defineList("boss.phaseThresholds", List.of(0.75, 0.5, 0.25),
                    o -> o instanceof Double d && d >= 0.0 && d <= 1.0);

    // ---------------- horde ----------------
    public static final ModConfigSpec.BooleanValue HORDE_ENABLED = BUILDER
            .comment("尸潮系统开关")
            .define("horde.enabled", true);

    public static final ModConfigSpec.IntValue HORDE_BASE_COUNT = BUILDER
            .comment("首波怪物数量")
            .defineInRange("horde.baseCount", 7, 1, 200);

    public static final ModConfigSpec.IntValue HORDE_COUNT_PER_WAVE = BUILDER
            .comment("每波递增数量（每波数量 = baseCount + wave * countPerWave）")
            .defineInRange("horde.countPerWave", 3, 0, 100);

    public static final ModConfigSpec.IntValue HORDE_COUNTDOWN_SECONDS = BUILDER
            .comment("波间准备时间（秒）")
            .defineInRange("horde.countdownSeconds", 5, 1, 120);

    public static final ModConfigSpec.DoubleValue ELITE_SPAWN_CHANCE = BUILDER
            .comment("智能僵尸进化为精英战术兵种（突袭者/重装先锋/狂血战鬼/百夫长）的基础概率")
            .defineInRange("horde.eliteChance", 0.35, 0.0, 1.0);

    public static final ModConfigSpec.BooleanValue ELITE_EQUIPMENT_ENABLED = BUILDER
            .comment("精英兵种生成时是否配备专属战术武器与盾牌（零掉落防刷物）")
            .define("horde.eliteEquipment", true);

    public static final ModConfigSpec.BooleanValue HORDE_BOSS_MILESTONE_WAVE = BUILDER
            .comment("尸潮每满 5 波（或总波数>=5 的最终决战波）自动伴随一位尸潮领主参战")
            .define("horde.bossOnMilestoneWave", true);

    public static final ModConfigSpec.BooleanValue HORDE_FAIL_ON_DEATH = BUILDER
            .comment("玩家在尸潮进行中阵亡时立即判定本次尸潮挑战失败并清理波次怪")
            .define("horde.failOnDeath", true);

    public static final ModConfigSpec.IntValue HORDE_HORN_WAVES = BUILDER
            .comment("生存模式吹响智潮战号（horde_horn）触发的尸潮波数")
            .defineInRange("horde.hornWaves", 5, 1, 20);

    // ---------------- effects ----------------
    public static final ModConfigSpec.BooleanValue PARTICLES_ENABLED = BUILDER
            .comment("粒子特效开关")
            .define("effects.particlesEnabled", true);

    public static final ModConfigSpec.BooleanValue SOUNDS_ENABLED = BUILDER
            .comment("音效开关")
            .define("effects.soundsEnabled", true);

    public static final ModConfigSpec.BooleanValue HEAD_HEALTH_BAR = BUILDER
            .comment("头顶血量条开关")
            .define("effects.headHealthBar", true);

    // ---------------- performance ----------------
    public static final ModConfigSpec.BooleanValue AUDIT_ENABLED = BUILDER
            .comment("性能审计（默认关闭，仅输出到日志）")
            .define("performance.auditEnabled", false);

    public static final ModConfigSpec.IntValue MAX_PARTICLES = BUILDER
            .comment("单次粒子上限")
            .defineInRange("performance.maxParticles", 48, 1, 2048);

    public static final ModConfigSpec SPEC = BUILDER.build();

    private SmartHordeConfig() {
    }

    /** 客户端渲染阶段安全读取（避免 SERVER 配置尚未同步时抛出 IllegalStateException）。 */
    public static boolean isHeadHealthBarEnabledSafe() {
        return !SPEC.isLoaded() || HEAD_HEALTH_BAR.get();
    }

    public static boolean isParticlesEnabledSafe() {
        return !SPEC.isLoaded() || PARTICLES_ENABLED.get();
    }

    public static boolean isSoundsEnabledSafe() {
        return !SPEC.isLoaded() || SOUNDS_ENABLED.get();
    }

    public static int maxParticlesSafe() {
        return SPEC.isLoaded() ? MAX_PARTICLES.get() : 48;
    }

    public static boolean isBossEnabledSafe() {
        return !SPEC.isLoaded() || BOSS_ENABLED.get();
    }

    public static boolean isBossBarEnabledSafe() {
        return !SPEC.isLoaded() || BOSS_BAR_ENABLED.get();
    }

    public static boolean isHordeEnabledSafe() {
        return !SPEC.isLoaded() || HORDE_ENABLED.get();
    }

    public static int hordeBaseCountSafe() {
        return SPEC.isLoaded() ? HORDE_BASE_COUNT.get() : 7;
    }

    public static int hordeCountPerWaveSafe() {
        return SPEC.isLoaded() ? HORDE_COUNT_PER_WAVE.get() : 3;
    }

    public static int hordeCountdownSecondsSafe() {
        return SPEC.isLoaded() ? HORDE_COUNTDOWN_SECONDS.get() : 5;
    }

    public static boolean isHordeBossMilestoneWaveSafe() {
        return !SPEC.isLoaded() || HORDE_BOSS_MILESTONE_WAVE.get();
    }

    public static boolean isHordeFailOnDeathSafe() {
        return !SPEC.isLoaded() || HORDE_FAIL_ON_DEATH.get();
    }

    public static int hordeHornWavesSafe() {
        return SPEC.isLoaded() ? HORDE_HORN_WAVES.get() : 5;
    }
}
