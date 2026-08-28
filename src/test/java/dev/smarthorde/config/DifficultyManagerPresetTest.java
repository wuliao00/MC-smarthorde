package dev.smarthorde.config;

import dev.smarthorde.config.DifficultyManager.Preset;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 纯逻辑单元测试（无需 Minecraft 引导）：仅访问嵌套枚举 Preset，
 * 不会触发外围类 DifficultyManager 的静态初始化。
 * 守护文档难度表的五维数值（生命/伤害/速度/攻速/闪避冷却秒）。
 */
class DifficultyManagerPresetTest {

    @Test
    void allPresetsHavePositiveMultipliers() {
        for (Preset preset : Preset.values()) {
            assertTrue(preset.healthMul > 0, preset + " healthMul 必须为正");
            assertTrue(preset.damageMul > 0, preset + " damageMul 必须为正");
            assertTrue(preset.speedMul > 0, preset + " speedMul 必须为正");
            assertTrue(preset.attackSpeedMul > 0, preset + " attackSpeedMul 必须为正");
            assertTrue(preset.dodgeTicks > 0, preset + " dodgeTicks 必须为正");
        }
    }

    @Test
    void normalIsNeutralBaseline() {
        assertEquals(1.0, Preset.NORMAL.healthMul);
        assertEquals(1.0, Preset.NORMAL.damageMul);
        assertEquals(1.0, Preset.NORMAL.speedMul);
        assertEquals(1.0, Preset.NORMAL.attackSpeedMul);
        assertEquals(1.5, Preset.NORMAL.dodgeSeconds());
    }

    @Test
    void harderPresetsScaleUpCombatStats() {
        assertTrue(Preset.NORMAL.healthMul < Preset.HARD.healthMul);
        assertTrue(Preset.HARD.healthMul < Preset.NIGHTMARE.healthMul);
        assertTrue(Preset.NORMAL.damageMul < Preset.NIGHTMARE.damageMul);
        assertTrue(Preset.NORMAL.attackSpeedMul < Preset.NIGHTMARE.attackSpeedMul);
        assertTrue(Preset.NORMAL.speedMul < Preset.NIGHTMARE.speedMul);
    }

    @Test
    void harderPresetsDodgeMoreOften() {
        // dodgeTicks 越低闪避越频繁；文档表：2.25s / 1.5s / 1.1s / 0.7s
        assertTrue(Preset.NORMAL.dodgeSeconds() > Preset.HARD.dodgeSeconds());
        assertTrue(Preset.HARD.dodgeSeconds() > Preset.NIGHTMARE.dodgeSeconds());
        assertEquals(2.25, Preset.EASY.dodgeSeconds(), 1.0E-9);
        assertEquals(1.5, Preset.NORMAL.dodgeSeconds(), 1.0E-9);
        assertEquals(1.1, Preset.HARD.dodgeSeconds(), 1.0E-9);
        assertEquals(0.7, Preset.NIGHTMARE.dodgeSeconds(), 1.0E-9);
    }
}
