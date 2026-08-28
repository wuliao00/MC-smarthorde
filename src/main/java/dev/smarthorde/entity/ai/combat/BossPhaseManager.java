package dev.smarthorde.entity.ai.combat;

import dev.smarthorde.config.SmartHordeConfig;
import dev.smarthorde.entity.HordeBoss;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.IntConsumer;

/**
 * Boss 阶段状态机：每 tick 检测血量阈值，跨过阈值即切阶段。
 * 阈值从配置 boss.phaseThresholds 读取并降序排列；
 * 攻速倍率 = 1.0 + phase x 0.25；
 * BossBar 配色由实体变体（HordeBoss.Variant）提供。
 */
public final class BossPhaseManager {

    private final HordeBoss boss;
    private final List<IntConsumer> phaseListeners = new ArrayList<>();

    private double[] thresholds;
    private double baseAttackSpeed = -1.0;
    private double baseMoveSpeed = -1.0;
    private int phase;

    public BossPhaseManager(HordeBoss boss) {
        this.boss = boss;
    }

    public void addPhaseListener(IntConsumer listener) {
        this.phaseListeners.add(listener);
    }

    public int getPhase() {
        return this.phase;
    }

    /** 服务端每 tick 调用。 */
    public void tick() {
        ensureInitialized();
        if (this.boss.isDeadOrDying()) {
            return;
        }
        double fraction = this.boss.getHealth() / Math.max(1.0, this.boss.getMaxHealth());
        while (this.phase < this.thresholds.length && fraction <= this.thresholds[this.phase]) {
            advancePhase();
        }
    }

    private void advancePhase() {
        this.phase++;
        AttributeInstance attackSpeed = this.boss.getAttribute(Attributes.ATTACK_SPEED);
        if (attackSpeed != null && this.baseAttackSpeed > 0) {
            // 每阶段攻击速度 +25%
            attackSpeed.setBaseValue(this.baseAttackSpeed * (1.0 + this.phase * 0.25));
        }
        AttributeInstance moveSpeed = this.boss.getAttribute(Attributes.MOVEMENT_SPEED);
        if (moveSpeed != null && this.baseMoveSpeed > 0) {
            // 每阶段移动速度 +15%
            moveSpeed.setBaseValue(this.baseMoveSpeed * (1.0 + this.phase * 0.15));
        }
        this.phaseListeners.forEach(listener -> listener.accept(this.phase));
    }

    /** 首次服务端 tick 时才读配置，避免实体构造早于配置加载。 */
    private void ensureInitialized() {
        if (this.thresholds != null) {
            return;
        }
        this.thresholds = SmartHordeConfig.BOSS_PHASE_THRESHOLDS.get().stream()
                .map(Double::doubleValue)
                .sorted(Comparator.reverseOrder())
                .mapToDouble(Double::doubleValue)
                .toArray();
        AttributeInstance attackSpeed = this.boss.getAttribute(Attributes.ATTACK_SPEED);
        if (attackSpeed != null) {
            this.baseAttackSpeed = attackSpeed.getBaseValue();
        }
        AttributeInstance moveSpeed = this.boss.getAttribute(Attributes.MOVEMENT_SPEED);
        if (moveSpeed != null) {
            this.baseMoveSpeed = moveSpeed.getBaseValue();
        }
    }
}
