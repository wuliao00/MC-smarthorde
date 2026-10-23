package dev.smarthorde.entity.ai.combat;

import dev.smarthorde.config.SmartHordeConfig;
import dev.smarthorde.entity.HordeBoss;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.IntConsumer;

/**
 * Boss 阶段状态机：每 tick 检测血量阈值，跨过阈值即切阶段。
 * 支持 NBT 持久化阶段进度与初始属性基线，防止区块卸载重载后重复叠乘攻速/移速或重复招怪。
 */
public final class BossPhaseManager {

    private static final String TAG_PHASE = "BossPhase";
    private static final String TAG_BASE_ATK_SPD = "BossBaseAttackSpeed";
    private static final String TAG_BASE_MOVE_SPD = "BossBaseMoveSpeed";

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

    public void saveToTag(CompoundTag tag) {
        tag.putInt(TAG_PHASE, this.phase);
        if (this.baseAttackSpeed > 0.0) {
            tag.putDouble(TAG_BASE_ATK_SPD, this.baseAttackSpeed);
        }
        if (this.baseMoveSpeed > 0.0) {
            tag.putDouble(TAG_BASE_MOVE_SPD, this.baseMoveSpeed);
        }
    }

    public void loadFromTag(CompoundTag tag) {
        if (tag.contains(TAG_PHASE)) {
            this.phase = Math.max(0, tag.getInt(TAG_PHASE));
        }
        if (tag.contains(TAG_BASE_ATK_SPD)) {
            this.baseAttackSpeed = tag.getDouble(TAG_BASE_ATK_SPD);
        }
        if (tag.contains(TAG_BASE_MOVE_SPD)) {
            this.baseMoveSpeed = tag.getDouble(TAG_BASE_MOVE_SPD);
        }
    }

    private void advancePhase() {
        this.phase++;
        AttributeInstance attackSpeed = this.boss.getAttribute(Attributes.ATTACK_SPEED);
        if (attackSpeed != null && this.baseAttackSpeed > 0) {
            attackSpeed.setBaseValue(this.baseAttackSpeed * (1.0 + this.phase * 0.25));
        }
        AttributeInstance moveSpeed = this.boss.getAttribute(Attributes.MOVEMENT_SPEED);
        if (moveSpeed != null && this.baseMoveSpeed > 0) {
            moveSpeed.setBaseValue(this.baseMoveSpeed * (1.0 + this.phase * 0.15));
        }
        this.phaseListeners.forEach(listener -> listener.accept(this.phase));
    }

    /** 首次服务端 tick 时读取配置与属性基线（若已从 NBT 恢复基线则不覆盖）。 */
    private void ensureInitialized() {
        if (this.thresholds == null) {
            if (SmartHordeConfig.SPEC.isLoaded()) {
                this.thresholds = SmartHordeConfig.BOSS_PHASE_THRESHOLDS.get().stream()
                        .map(Double::doubleValue)
                        .sorted(Comparator.reverseOrder())
                        .mapToDouble(Double::doubleValue)
                        .toArray();
            } else {
                this.thresholds = new double[]{0.75, 0.5, 0.25};
            }
        }
        if (this.baseAttackSpeed <= 0.0) {
            AttributeInstance attackSpeed = this.boss.getAttribute(Attributes.ATTACK_SPEED);
            if (attackSpeed != null) {
                this.baseAttackSpeed = attackSpeed.getBaseValue() / (1.0 + this.phase * 0.25);
            }
        }
        if (this.baseMoveSpeed <= 0.0) {
            AttributeInstance moveSpeed = this.boss.getAttribute(Attributes.MOVEMENT_SPEED);
            if (moveSpeed != null) {
                this.baseMoveSpeed = moveSpeed.getBaseValue() / (1.0 + this.phase * 0.15);
            }
        }
    }
}
