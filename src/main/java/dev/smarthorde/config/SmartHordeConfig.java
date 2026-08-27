package dev.smarthorde.config;

import net.neoforged.neoforge.common.ModConfigSpec;

import java.util.List;

/**
 * 配置中心。所有键与《SmartHorde 完整文件清单》中的"配置默认值速查"一一对应。
 * ai.dodgeCooldownTicks 为清单补充键：DodgeGoal 冷却"从配置读取（默认 90 tick）"。
 */
public final class SmartHordeConfig {

    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();

    // ---------------- difficulty ----------------
    public static final ModConfigSpec.ConfigValue<String> DIFFICULTY_PRESET = BUILDER
            .comment("难度预设: easy / normal / hard / nightmare")
            .define("difficulty.preset", "normal");

    // ---------------- inject ----------------
    public static final ModConfigSpec.BooleanValue INJECT_VANILLA = BUILDER
            .comment("增强原版僵尸（速度 x1.15、血量 x1.5、替换目标选择器）")
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

    // ---------------- ai（清单补充） ----------------
    public static final ModConfigSpec.IntValue DODGE_COOLDOWN_TICKS = BUILDER
            .comment("闪避基础冷却（tick），实际值受难度倍率影响")
            .defineInRange("ai.dodgeCooldownTicks", 90, 0, 1200);

    public static final ModConfigSpec SPEC = BUILDER.build();

    private SmartHordeConfig() {
    }
}
