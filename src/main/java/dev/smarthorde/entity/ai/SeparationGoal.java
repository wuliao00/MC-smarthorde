package dev.smarthorde.entity.ai;

import dev.smarthorde.config.SmartHordeConfig;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.phys.Vec3;

import java.util.EnumSet;
import java.util.List;

/**
 分离走位：同类挤太近时，朝"群体质心"的反方向转向，
 避免尸潮一窝蜂堆同一点（防卡 + 为包抄让路）。
 一次性转向 + 冷却节流，冷却对齐 performance.aiTickInterval；
 轮6接入尸潮"同队标签"后升级为按队伍识别。
 */
public class SeparationGoal extends Goal {
    private final PathfinderMob mob;
    private final double radius = 1.75D;
    private Vec3 targetPos = Vec3.ZERO;
    private int cooldown = 0;

    public SeparationGoal(PathfinderMob mob) {
        this.mob = mob;
        this.setFlags(EnumSet.of(Flag.MOVE));
    }

    @Override
    public boolean canUse() {
        // [F3] STACK 骑乘中让位：乘客导航无效
        if (ClimbOrStackGoal.isStackRider(this.mob)) return false;
        if (this.cooldown > 0) {
            this.cooldown--;
            return false;
        }
        List<? extends Mob> nearby = this.mob.level().getEntitiesOfClass(
                Mob.class,
                this.mob.getBoundingBox().inflate(this.radius),
                e -> e != this.mob && e.isAlive() && e.getType() == this.mob.getType());
        if (nearby.size() < 2) {
            return false;
        }
        // 求同类群体质心，取反方向作为转向目标
        double cx = 0.0D, cz = 0.0D;
        for (Mob e : nearby) {
            cx += e.getX();
            cz += e.getZ();
        }
        cx /= nearby.size();
        cz /= nearby.size();
        Vec3 away = new Vec3(this.mob.getX() - cx, 0.0D, this.mob.getZ() - cz);
        if (away.lengthSqr() < 1.0E-4D) { // 恰好站在质心上：随机方向兜底
            away = Vec3.directionFromRotation(0.0F, this.mob.getRandom().nextFloat() * 360.0F);
        }
        this.targetPos = this.mob.position().add(away.normalize().scale(4.0D));
        return true;
    }

    @Override
    public void start() {
        this.cooldown = SmartHordeConfig.AI_TICK_INTERVAL.get() * 5;
        this.mob.getNavigation().moveTo(this.targetPos.x, this.targetPos.y, this.targetPos.z, 1.0D);
    }

    @Override
    public boolean canContinueToUse() {
        return false; // 一次性转向；navigation 自行执行剩余寻路
    }
}
