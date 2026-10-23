package dev.smarthorde.entity;

/**
 * 战术动作状态同步接口：由 {@link SmartZombie} 与 {@link HordeBoss} 共同实现，
 * 将服务端 AI 招式编号与帧进度同步至客户端专属模型，驱动零穿模关键帧姿态。
 *
 * <p>招式编码约定：
 * <ul>
 *   <li>0 = 无招式（战术戒备/移动）</li>
 *   <li>1 = 轻击（light）</li>
 *   <li>2 = 重击（heavy）</li>
 *   <li>3 = 横扫（sweep）</li>
 *   <li>4 = 突刺（thrust）</li>
 *   <li>5 = 特殊战技（统领战吼 / Boss 变体主动技能）</li>
 * </ul>
 */
public interface TacticalAttacker {

    int ATTACK_NONE = 0;
    int ATTACK_LIGHT = 1;
    int ATTACK_HEAVY = 2;
    int ATTACK_SWEEP = 3;
    int ATTACK_THRUST = 4;
    int ATTACK_SPECIAL = 5;

    int getAttackId();

    void setAttackId(int attackId);

    int getAttackTicks();

    void setAttackTicks(int attackTicks);

    default boolean isExecutingAttack() {
        return getAttackId() != ATTACK_NONE;
    }
}
