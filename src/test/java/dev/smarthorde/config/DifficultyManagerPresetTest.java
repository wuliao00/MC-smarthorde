package dev.smarthorde.config;

import dev.smarthorde.config.DifficultyManager.Preset;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 纯逻辑单元测试（无需 Minecraft 引导）：仅访问嵌套枚举 Preset，
 * 不会触发外围类 DifficultyManager 的静态初始化。
 * 守护拆分后的独立 attackSpeedMul 字段语义与各预设倍率不变量。
 */
class DifficultyManagerPresetTest {

    @Test
    void allPresetsHavePositiveMultipliers() {
        for (Preset preset : Preset.values()) {
            assertTrue(preset.healthMul > 0, preset + " healthMul 必须为正");
            assertTrue(preset.damageMul > 0, preset + " damageMul 必须为正");
            assertTrue(preset.speedMul > 0, preset + " speedMul 必须为正");
            assertTrue(preset.attackSpeedMul > 0, preset + " attackSpeedMul 必须为正");
            assertTrue(preset.dodgeCooldownMul > 0, preset + " dodgeCooldownMul 必须为正");
        }
    }

    @Test
    void normalIsNeutralBaseline() {
        assertEquals(1.0, Preset.NORMAL.healthMul);
        assertEquals(1.0, Preset.NORMAL.damageMul);
        assertEquals(1.0, Preset.NORMAL.speedMul);
        assertEquals(1.0, Preset.NORMAL.attackSpeedMul);
        assertEquals(1.0, Preset.NORMAL.dodgeCooldownMul);
    }

    @Test
    void harderPresetsScaleUpCombatStats() {
        assertTrue(Preset.NORMAL.healthMul < Preset.HARD.healthMul);
        assertTrue(Preset.HARD.healthMul < Preset.NIGHTMARE.healthMul);
        assertTrue(Preset.NORMAL.damageMul < Preset.NIGHTMARE.damageMul);
        assertTrue(Preset.NORMAL.attackSpeedMul < Preset.NIGHTMARE.attackSpeedMul);
    }

    @Test
    void harderPresetsDodgeMoreOften() {
        // dodgeCooldownMul 越低闪避越频繁
        assertTrue(Preset.NORMAL.dodgeCooldownMul > Preset.HARD.dodgeCooldownMul);
        assertTrue(Preset.HARD.dodgeCooldownMul > Preset.NIGHTMARE.dodgeCooldownMul);
    }
}
