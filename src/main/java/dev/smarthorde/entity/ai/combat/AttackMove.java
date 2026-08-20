package dev.smarthorde.entity.ai.combat;

/**
 * 单个招式的不可变数据定义。
 * 所有数值由配置层在运行时注入（轮6预设换算），此处仅承载结构。
 * @param id            招式唯一标识（0=轻击, 1=重击, 2=横扫, 3=冲刺）
 * @param windupTicks   前摇 tick 数（telegraph 窗口）
 * @param strikeTicks   命中判定窗口 tick 数
 * @param recoveryTicks 后摇 tick 数（硬直）
 * @param damage        基础伤害（实际 = damage × 难度倍率）
 * @param range         攻击范围（格）
 * @param arcDegrees    扇形角度（360=圆形全周）
 * @param knockback     击退力度
 * @param cooldownTicks 使用后的冷却 tick
 * @param minDist       最小触发距离（目标太近不用这招）
 * @param maxDist       最大触发距离（目标太远不用这招）
 * @param weight        加权随机权重
 */
public record AttackMove(
        int id,
        int windupTicks,
        int strikeTicks,
        int recoveryTicks,
        float damage,
        double range,
        float arcDegrees,
        double knockback,
        int cooldownTicks,
        double minDist,
        double maxDist,
        int weight
) {
    /** 总持续 tick = 前摇 + 命中 + 后摇 */
    public int totalTicks() {
        return windupTicks + strikeTicks + recoveryTicks;
    }
}
