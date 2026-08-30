package dev.smarthorde.entity.ai;

import dev.smarthorde.config.SmartHordeConfig;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.EnumSet;
import java.util.List;

/**
 * 群体协作 AI（轮12增强）。
 * 3+只时分工：ATTACKER 正面进攻 / FLANKER 绕后 / ARCHER 保持距离施压。
 * 5+只时 ATTACKER 轮流进攻，避免同时硬直。
 * 角色基于实体 UUID 分配，每 20 tick 重新评估。
 * [C7] FlankGoal 已删除：绕侧/绕后统一由 FLANKER 角色承担，
 * 开关收归 movement.flankingEnabled（关闭时 FLANKER 降级为 ATTACKER）。
 */
public class CoordinationGoal extends Goal {

    private final PathfinderMob mob;
    private GroupRole role = GroupRole.ATTACKER;
    private int recheckCooldown = 0;
    private static final int RECHECK_INTERVAL = 20;
    private static final double ALLY_SCAN_RADIUS = 16.0D;
    // [C5] 扫描结果缓存（消除每 tick 双 getEntitiesOfClass）
    private int cachedAllyCount = -1;
    private int allyCacheTick = -1;
    private Boolean cachedAllyAttacking = null;
    private int attackCacheTick = -1;
    /** [C5] 缓存时长 = AI_TICK_INTERVAL × 5（默认档 20 tick）。 */
    private static final int CACHE_MULT = 5;

    public enum GroupRole { ATTACKER, FLANKER, ARCHER }

    public CoordinationGoal(PathfinderMob mob) {
        this.mob = mob;
        this.setFlags(EnumSet.of(Flag.MOVE));
    }

    @Override
    public void start() {
        // [C5] goal 启动时失效缓存
        cachedAllyCount = -1;
        cachedAllyAttacking = null;
    }

    @Override
    public boolean canUse() {
        if (recheckCooldown > 0) { recheckCooldown--; return false; }
        recheckCooldown = RECHECK_INTERVAL;

        // [F3] STACK 骑乘中让位：乘客导航无效，抢占会 stopRiding 致塔瓦解
        if (ClimbOrStackGoal.isStackRider(mob)) return false;
        LivingEntity target = mob.getTarget();
        if (target == null || !target.isAlive()) return false;

        int allyCount = countNearbyAllies();
        if (allyCount < 3) return false;

        assignRole(allyCount);
        return true;
    }

    @Override
    public boolean canContinueToUse() {
        // [F3] STACK 骑乘中让位
        if (ClimbOrStackGoal.isStackRider(mob)) return false;
        LivingEntity target = mob.getTarget();
        return target != null && target.isAlive();
    }

    @Override
    public void tick() {
        LivingEntity target = mob.getTarget();
        if (target == null || !target.isAlive()) return;

        switch (role) {
            case ATTACKER -> {
                // 5+只时轮流进攻：检测同类是否正在出招
                int allyCount = countNearbyAllies();
                if (allyCount >= 5) {
                    boolean someoneAttacking = isAllyAttacking();
                    if (someoneAttacking) {
                        // 保持移动但不靠近，等别人打完
                        maintainDistance(target, 4.0D, 6.0D);
                        return;
                    }
                }
                // 正常靠近进攻
                mob.getNavigation().moveTo(target, 1.0D);
            }
            case FLANKER -> {
                // 移动到目标侧后方
                Vec3 targetLook = target.getLookAngle().normalize();
                // 取目标背后偏侧的位置
                double angle = (mob.getId() % 2 == 0) ? -120.0D : 120.0D;
                double rad = Math.toRadians(angle);
                Vec3 flankDir = new Vec3(
                        -targetLook.x * Math.cos(rad) - targetLook.z * Math.sin(rad),
                        0,
                        targetLook.x * Math.sin(rad) - targetLook.z * Math.cos(rad)
                ).normalize();
                Vec3 flankPos = target.position().add(flankDir.scale(4.0D));
                mob.getNavigation().moveTo(flankPos.x, flankPos.y, flankPos.z, 1.3D);
            }
            case ARCHER -> {
                // 保持 5-7 格距离施压
                maintainDistance(target, 5.0D, 7.0D);
            }
        }
    }

    /** 维持与目标的距离在 [min, max] 范围内 */
    private void maintainDistance(LivingEntity target, double min, double max) {
        double dist = mob.distanceTo(target);
        if (dist < min) {
            // 太近，后退
            Vec3 away = mob.position().subtract(target.position()).normalize();
            Vec3 retreat = mob.position().add(away.scale(2.0D));
            mob.getNavigation().moveTo(retreat.x, retreat.y, retreat.z, 1.0D);
        } else if (dist > max) {
            // 太远，靠近
            mob.getNavigation().moveTo(target, 0.8D);
        }
        // 在范围内则不动
    }

    /** 基于 UUID 哈希分配角色 */
    private void assignRole(int allyCount) {
        long hash = Math.abs(mob.getUUID().getLeastSignificantBits() % 100);
        if (allyCount >= 5) {
            if (hash < 50) role = GroupRole.ATTACKER;
            else if (hash < 80) role = GroupRole.FLANKER;
            else role = GroupRole.ARCHER;
        } else {
            if (hash < 55) role = GroupRole.ATTACKER;
            else if (hash < 85) role = GroupRole.FLANKER;
            else role = GroupRole.ARCHER;
        }
        // [C7] FlankGoal 移除后，绕侧开关收归 FLANKER 角色门控
        if (role == GroupRole.FLANKER && !SmartHordeConfig.FLANK.get()) {
            role = GroupRole.ATTACKER;
        }
    }

    /** 检测附近的同类是否正在出招。[C5] 结果缓存 AI_TICK_INTERVAL×5 tick */
    private boolean isAllyAttacking() {
        if (!(mob instanceof IAttackMob)) return false;
        int interval = Math.max(1, SmartHordeConfig.AI_TICK_INTERVAL.get()) * CACHE_MULT;
        if (cachedAllyAttacking != null && mob.tickCount - attackCacheTick < interval) {
            return cachedAllyAttacking;
        }
        AABB box = mob.getBoundingBox().inflate(ALLY_SCAN_RADIUS);
        List<Mob> allies = mob.level().getEntitiesOfClass(Mob.class, box,
                e -> e != mob && e.getClass() == mob.getClass() && e.isAlive());
        boolean result = false;
        for (Mob ally : allies) {
            if (ally instanceof IAttackMob am && am.getAttackId() != 0) {
                result = true;
                break;
            }
        }
        cachedAllyAttacking = result;
        attackCacheTick = mob.tickCount;
        return result;
    }

    /** 检测附近同类数量（含自己）。[C5] 结果缓存 AI_TICK_INTERVAL×5 tick */
    private int countNearbyAllies() {
        int interval = Math.max(1, SmartHordeConfig.AI_TICK_INTERVAL.get()) * CACHE_MULT;
        if (cachedAllyCount >= 0 && mob.tickCount - allyCacheTick < interval) {
            return cachedAllyCount;
        }
        AABB box = mob.getBoundingBox().inflate(ALLY_SCAN_RADIUS);
        List<Mob> allies = mob.level().getEntitiesOfClass(Mob.class, box,
                e -> e.getClass() == mob.getClass() && e.isAlive());
        cachedAllyCount = allies.size();
        allyCacheTick = mob.tickCount;
        return cachedAllyCount;
    }

    public GroupRole getRole() { return role; }
}
