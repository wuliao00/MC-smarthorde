package dev.smarthorde.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 难度预设（轮6）。四档数值倍率表。
 * 新刷怪出生时由 DifficultyManager.applyTo() 缩放属性。
 */
public enum DifficultyPreset {
    //            id            生命    伤害    速度    攻频    闪避冷却
    EASY       ("easy",        0.75D, 0.70D, 0.90D, 0.75D, 45),
    NORMAL     ("normal",      1.00D, 1.00D, 1.00D, 1.00D, 30),
    HARD       ("hard",        1.35D, 1.25D, 1.10D, 1.25D, 22),
    NIGHTMARE  ("nightmare",   1.75D, 1.50D, 1.25D, 1.50D, 14);

    private static final Logger LOGGER = LoggerFactory.getLogger("SmartHorde");

    private final String id;
    private final double healthMultiplier;
    private final double damageMultiplier;
    private final double speedMultiplier;
    private final double attackRateMultiplier;
    private final int evadeCooldownTicks;

    DifficultyPreset(String id, double health, double damage, double speed,
                     double attackRate, int evadeCooldown) {
        this.id = id;
        this.healthMultiplier = health;
        this.damageMultiplier = damage;
        this.speedMultiplier = speed;
        this.attackRateMultiplier = attackRate;
        this.evadeCooldownTicks = evadeCooldown;
    }

    public String getId()                     { return id; }
    public double getHealthMultiplier()      { return healthMultiplier; }
    public double getDamageMultiplier()      { return damageMultiplier; }
    public double getSpeedMultiplier()       { return speedMultiplier; }
    public double getAttackRateMultiplier()  { return attackRateMultiplier; }
    public int getEvadeCooldownTicks()        { return evadeCooldownTicks; }

    // 兼容旧代码（AttackController 调用 damageMultiplier()）
    public float damageMultiplier()          { return (float) damageMultiplier; }

    /** 按 id 查找，未知值回退 NORMAL 并警告。 */
    public static DifficultyPreset byId(String id) {
        if (id != null) {
            for (DifficultyPreset p : values()) {
                if (p.id.equalsIgnoreCase(id.trim())) return p;
            }
        }
        LOGGER.warn("[SmartHorde] 未知难度: '{}'，回退到 normal", id);
        return NORMAL;
    }
}
