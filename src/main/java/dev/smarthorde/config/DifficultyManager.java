package dev.smarthorde.config;

import dev.smarthorde.SmartHordeMod;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Monster;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 难度管理器：四档预设 + 属性缩放。
 * /smarthorde difficulty 命令的运行时覆盖仅存于内存，配置文件仍为主。
 *
 * 注: NeoForge 21.x 移除了 ModConfigEvent。配置热重载不再清空运行时覆盖；
 * /smarthorde difficulty 命令的覆盖保持到进程结束（与原版用户体验一致）。
 */
public final class DifficultyManager {

    private static final Logger LOGGER = LoggerFactory.getLogger("DifficultyManager");

    public enum Preset {
        EASY(0.70, 0.80, 1.00, 1.00, 1.40),
        NORMAL(1.00, 1.00, 1.00, 1.00, 1.00),
        HARD(1.40, 1.25, 1.10, 1.10, 0.75),
        NIGHTMARE(1.80, 1.50, 1.20, 1.20, 0.50);

        /** 血量倍率 */
        public final double healthMul;
        /** 攻击伤害倍率 */
        public final double damageMul;
        /** 移动速度倍率 */
        public final double speedMul;
        /** 攻击速度倍率（独立于移动速度缩放，便于单独调整；默认与 speedMul 同值保持旧行为） */
        public final double attackSpeedMul;
        /** 闪避冷却倍率（越低闪避越频繁） */
        public final double dodgeCooldownMul;

        Preset(double healthMul, double damageMul, double speedMul, double attackSpeedMul, double dodgeCooldownMul) {
            this.healthMul = healthMul;
            this.damageMul = damageMul;
            this.speedMul = speedMul;
            this.attackSpeedMul = attackSpeedMul;
            this.dodgeCooldownMul = dodgeCooldownMul;
        }
    }

    private static volatile Preset override;

    private DifficultyManager() {
    }

    /** 当前生效预设：命令覆盖优先，否则读配置。 */
    public static Preset current() {
        if (override != null) {
            return override;
        }
        String raw = SmartHordeConfig.DIFFICULTY_PRESET.get();
        try {
            return Preset.valueOf(raw.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            LOGGER.warn("Unknown difficulty preset '{}', falling back to NORMAL", raw);
            return Preset.NORMAL;
        }
    }

    /** 命令设置运行时覆盖（不回写配置文件）。 */
    public static void setOverride(Preset preset) {
        override = preset;
        LOGGER.info("Difficulty override set to {}", preset);
    }

    /** 根据当前难度缩放 MAX_HEALTH / ATTACK_DAMAGE / MOVEMENT_SPEED / ATTACK_SPEED。 */
    public static void applyTo(Monster monster) {
        Preset preset = current();

        AttributeInstance health = monster.getAttribute(Attributes.MAX_HEALTH);
        if (health != null) {
            health.setBaseValue(health.getBaseValue() * preset.healthMul);
            monster.setHealth(monster.getMaxHealth());
        }
        scale(monster, Attributes.ATTACK_DAMAGE, preset.damageMul);
        scale(monster, Attributes.MOVEMENT_SPEED, preset.speedMul);
        scale(monster, Attributes.ATTACK_SPEED, preset.attackSpeedMul);
    }

    public static int dodgeCooldownTicks() {
        return Math.max(1, (int) (SmartHordeConfig.DODGE_COOLDOWN_TICKS.get() * current().dodgeCooldownMul));
    }

    private static void scale(Mob mob, net.minecraft.core.Holder<net.minecraft.world.entity.ai.attributes.Attribute> attribute, double mul) {
        AttributeInstance instance = mob.getAttribute(attribute);
        if (instance != null) {
            instance.setBaseValue(instance.getBaseValue() * mul);
        }
    }
}
