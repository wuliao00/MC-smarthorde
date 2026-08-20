package dev.smarthorde.entity.ai;

/**
 * 支持招式同步的实体接口（轮10）。
 * SmartZombie 和 HordeBoss 都实现此接口，使 SmartMeleeAttackGoal 可通用。
 */
public interface IAttackMob {
    void setAttackId(int id);
    void setAttackTicks(int ticks);
    int getAttackId();
    int getAttackTicks();
}
