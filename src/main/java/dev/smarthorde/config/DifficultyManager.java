package dev.smarthorde.config;

import dev.smarthorde.SmartHordeMod;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Monster;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.server.ServerStartingEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 难度管理器：四档预设（生命/伤害/速度/攻速/闪避冷却五维）。
 *
 * 与文档表一致：
 * Easy       x0.75 x0.70 x0.90 x0.75  闪避 2.25s
 * Normal     x1.00 x1.00 x1.00 x1.00  闪避 1.5s
 * Hard       x1.35 x1.25 x1.10 x1.25  闪避 1.1s
 * Nightmare  x1.75 x1.50 x1.25 x1.50  闪避 0.7s
 *
 * 注：NeoForge 21.x 已移除 ModConfigEvent，SERVER 类型配置文件由内建的
 * 文件监视器热重载 —— 修改 smarthorde-server.toml 后无需重启即可生效；
 * /smarthorde difficulty 命令的运行时覆盖保持到下一次服务器启动（开服时清除）。
 */
@EventBusSubscriber(modid = SmartHordeMod.MODID)
public final class DifficultyManager {

    private static final Logger LOGGER = LoggerFactory.getLogger("DifficultyManager");

    public enum Preset {
        //        血量   伤害   速度   攻速   闪避冷却(tick)
        EASY(0.75, 0.70, 0.90, 0.75, 45),
        NORMAL(1.00, 1.00, 1.00, 1.00, 30),
        HARD(1.35, 1.25, 1.10, 1.25, 22),
        NIGHTMARE(1.75, 1.50, 1.25, 1.50, 14);

        /** 生命倍率 */
        public final double healthMul;
        /** 伤害倍率 */
        public final double damageMul;
        /** 移动速度倍率 */
        public final double speedMul;
        /** 攻击速度倍率 */
        public final double attackSpeedMul;
        /** 三源闪避冷却（tick；30 tick = 1.5 秒） */
        public final int dodgeTicks;

        Preset(double healthMul, double damageMul, double speedMul, double attackSpeedMul, int dodgeTicks) {
            this.healthMul = healthMul;
            this.damageMul = damageMul;
            this.speedMul = speedMul;
            this.attackSpeedMul = attackSpeedMul;
            this.dodgeTicks = dodgeTicks;
        }

        /** 当前难度下的闪避冷却秒数（用于展示）。 */
        public double dodgeSeconds() {
            return this.dodgeTicks / 20.0;
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

    /** 当前难度下的三源闪避冷却（tick）。 */
    public static int dodgeCooldownTicks() {
        return Math.max(1, current().dodgeTicks);
    }

    private static void scale(Mob mob,
                              net.minecraft.core.Holder<net.minecraft.world.entity.ai.attributes.Attribute> attribute,
                              double mul) {
        AttributeInstance instance = mob.getAttribute(attribute);
        if (instance != null) {
            instance.setBaseValue(instance.getBaseValue() * mul);
        }
    }

    /** 每次开服清除上一局的命令覆盖，让配置文件重新成为唯一事实来源。 */
    @SubscribeEvent
    public static void onServerStarting(ServerStartingEvent event) {
        MinecraftServer server = event.getServer();
        if (override != null) {
            LOGGER.info("Server starting, clearing difficulty override ({})", override);
            override = null;
        }
    }
}
