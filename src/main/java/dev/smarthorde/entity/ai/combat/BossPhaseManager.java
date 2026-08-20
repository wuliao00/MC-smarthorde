package dev.smarthorde.entity.ai.combat;

import dev.smarthorde.config.SmartHordeConfig;
import net.minecraft.world.BossEvent;
import net.minecraft.world.entity.monster.Monster;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/**
 * Boss 阶段管理器（轮10）。
 * 根据血量百分比切换阶段；每切一次触发回调。
 * BossBar 颜色递进：白→蓝→紫→红。
 */
public class BossPhaseManager {

    private static final Logger LOGGER = LoggerFactory.getLogger("SmartHorde");

    @FunctionalInterface
    public interface PhaseCallback {
        void onPhaseChange(int oldPhase, int newPhase, Monster boss);
    }

    private final Monster boss;
    private final double[] thresholds;
    private int currentPhase = 0;
    private final int totalPhases;
    private final PhaseCallback callback;

    private static final BossEvent.BossBarColor[] BAR_COLORS = {
            BossEvent.BossBarColor.WHITE,
            BossEvent.BossBarColor.BLUE,
            BossEvent.BossBarColor.PURPLE,
            BossEvent.BossBarColor.RED
    };

    public BossPhaseManager(Monster boss, PhaseCallback callback) {
        this.boss = boss;
        this.callback = callback;

        List<? extends Double> raw = SmartHordeConfig.BOSS_PHASE_THRESHOLDS.get();
        double[] sorted = raw.stream()
                .filter(d -> d != null && d > 0.0D && d < 1.0D)
                .mapToDouble(Double::doubleValue)
                .sorted()
                .toArray();
        for (int i = 0; i < sorted.length / 2; i++) {
            double tmp = sorted[i];
            sorted[i] = sorted[sorted.length - 1 - i];
            sorted[sorted.length - 1 - i] = tmp;
        }
        this.thresholds = sorted;
        this.totalPhases = sorted.length + 1;
    }

    public int getCurrentPhase() { return currentPhase; }
    public int getTotalPhases() { return totalPhases; }

    public boolean tick() {
        float hpRatio = boss.getHealth() / boss.getMaxHealth();
        int targetPhase = 0;
        for (int i = 0; i < thresholds.length; i++) {
            if (hpRatio <= thresholds[i]) {
                targetPhase = i + 1;
            }
        }
        if (targetPhase != currentPhase) {
            int old = currentPhase;
            currentPhase = targetPhase;
            LOGGER.info("[SmartHorde] Boss 阶段切换: {} → {}", old, currentPhase);
            if (callback != null) {
                callback.onPhaseChange(old, currentPhase, boss);
            }
            return true;
        }
        return false;
    }

    public BossEvent.BossBarColor getBarColor() {
        int idx = Math.min(currentPhase, BAR_COLORS.length - 1);
        return BAR_COLORS[idx];
    }

    public double getAttackSpeedMultiplier() {
        return 1.0D + currentPhase * 0.25D;
    }

    public double getSpeedBonus() {
        return currentPhase * 0.03D;
    }
}
