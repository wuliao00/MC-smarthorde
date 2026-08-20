package dev.smarthorde.entity.ai;

import dev.smarthorde.config.SmartHordeConfig;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.DoorInteractGoal;

/**
 * 开门 Goal（轮4）。继承原版 DoorInteractGoal，
 * 额外受 openDoorsEnabled 配置开关控制。
 * 原版已内置开关门逻辑与 DoorBlock 安全检查，此处仅加配置守卫。
 */
public class SmartOpenDoorGoal extends DoorInteractGoal {

    public SmartOpenDoorGoal(Mob mob) {
        super(mob);
    }

    @Override
    public boolean canUse() {
        if (!SmartHordeConfig.OPEN_DOORS.get()) return false;
        return super.canUse();
    }

    @Override
    public boolean canContinueToUse() {
        if (!SmartHordeConfig.OPEN_DOORS.get()) return false;
        return super.canContinueToUse();
    }
}
