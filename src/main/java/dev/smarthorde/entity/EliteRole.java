package dev.smarthorde.entity;

/**
 * 智能僵尸战术兵种枚举（纯 Java 数据定义，零 Minecraft 静态依赖，便于单元测试）。
 *
 * <ul>
 *   <li>{@link #STANDARD}：均衡型标准智尸</li>
 *   <li>{@link #RAIDER}：疾风突袭者 —— 高移速、短闪避冷却，双臂袖刃偏好轻击与突刺</li>
 *   <li>{@link #VANGUARD}：重装先锋 —— 高血高甲高抗击退，自带重装胸甲肩甲并持盾推进防御</li>
 *   <li>{@link #BERSERKER}：狂血战鬼 —— 高伤骨角狂魔，半血以下进入嗜血狂暴（移速/攻速激增）</li>
 *   <li>{@link #COMMANDER}：尸潮百夫长 —— 披风战冠统领，周期性发出战吼激励周围友军</li>
 * </ul>
 */
public enum EliteRole {
    //         id           生命   伤害   移速   护甲加成 抗击退加成 闪避冷却倍率 模型缩放 徽记RGB   精英权重
    STANDARD("standard",   1.00, 1.00, 1.00, 0.0,  0.00, 1.00, 1.00F, 0x5C8A4A, 0),
    RAIDER("raider",       0.85, 1.05, 1.18, 2.0,  0.00, 0.70, 0.95F, 0x3CC8E0, 35),
    VANGUARD("vanguard",   1.45, 1.05, 0.90, 11.0, 0.35, 1.35, 1.08F, 0xE09A3C, 25),
    BERSERKER("berserker", 1.10, 1.25, 1.08, 4.0,  0.15, 0.90, 1.04F, 0xE03C3C, 25),
    COMMANDER("commander", 1.35, 1.15, 1.02, 8.0,  0.25, 1.00, 1.12F, 0xF0C83C, 15);

    private static final EliteRole[] ALL = values();
    private static final int TOTAL_ELITE_WEIGHT;

    static {
        int sum = 0;
        for (EliteRole role : ALL) {
            sum += Math.max(0, role.eliteWeight);
        }
        TOTAL_ELITE_WEIGHT = sum;
    }

    public final String id;
    public final double healthMul;
    public final double damageMul;
    public final double speedMul;
    public final double armorBonus;
    public final double knockbackResBonus;
    public final double dodgeCooldownMul;
    public final float modelScale;
    public final int badgeColorRgb;
    public final int eliteWeight;

    EliteRole(String id, double healthMul, double damageMul, double speedMul,
              double armorBonus, double knockbackResBonus, double dodgeCooldownMul,
              float modelScale, int badgeColorRgb, int eliteWeight) {
        this.id = id;
        this.healthMul = healthMul;
        this.damageMul = damageMul;
        this.speedMul = speedMul;
        this.armorBonus = armorBonus;
        this.knockbackResBonus = knockbackResBonus;
        this.dodgeCooldownMul = dodgeCooldownMul;
        this.modelScale = modelScale;
        this.badgeColorRgb = badgeColorRgb;
        this.eliteWeight = eliteWeight;
    }

    public String getSerializedName() {
        return this.id;
    }

    public boolean isElite() {
        return this != STANDARD;
    }

    public String nameKey() {
        return this == STANDARD
                ? "entity.smarthorde.smart_zombie"
                : "entity.smarthorde.smart_zombie." + this.id;
    }

    public String texturePath() {
        return this == STANDARD
                ? "textures/entity/smart_zombie.png"
                : "textures/entity/smart_zombie_" + this.id + ".png";
    }

    public String glowTexturePath() {
        return this == STANDARD
                ? "textures/entity/smart_zombie_glow.png"
                : "textures/entity/smart_zombie_" + this.id + "_glow.png";
    }

    public static EliteRole byOrdinal(int ordinal) {
        return ALL[Math.floorMod(ordinal, ALL.length)];
    }

    public static EliteRole byId(String id) {
        for (EliteRole role : ALL) {
            if (role.id.equalsIgnoreCase(id)) {
                return role;
            }
        }
        throw new IllegalArgumentException("Unknown elite role: " + id);
    }

    public static int totalEliteWeight() {
        return TOTAL_ELITE_WEIGHT;
    }

    /** 按 [0, totalEliteWeight) 的随机数抽取精英兵种。 */
    public static EliteRole pickEliteByWeightRoll(int roll) {
        int remaining = Math.floorMod(roll, TOTAL_ELITE_WEIGHT);
        for (EliteRole role : ALL) {
            if (role.eliteWeight <= 0) {
                continue;
            }
            remaining -= role.eliteWeight;
            if (remaining < 0) {
                return role;
            }
        }
        return RAIDER;
    }
}
