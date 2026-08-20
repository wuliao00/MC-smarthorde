package dev.smarthorde.entity.ai;

import dev.smarthorde.config.SmartHordeConfig;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.phys.Vec3;

import java.util.EnumSet;
import java.util.List;

/**
 * 增强版绕侧/绕后包抄（轮4升级）。
 * <p>改进点：
 * <ul>
 *   <li>移动到玩家侧方而非正面（90°~150° 偏离正面视线）</li>
 *   <li>多只怪自动分散到不同角度（左/右/后），避免重叠同一方向</li>
 *   <li>包抄速度提高到 1.3 倍移速</li>
 *   <li>检测附近同类数量，3 只以上才触发包抄（零散单怪直接正面推进）</li>
 * </ul>
 */
public class FlankGoal extends Goal {
    private final PathfinderMob mob;
    private Vec3 flankPos = Vec3.ZERO;
    private int recheckCooldown = 0;
    private static final int RECHECK_INTERVAL = 12;

    /** 目标正面方向的夹角阈值：cos(60°)=0.5，即视线锥 60° 内才算"正面朝向我" */
    private static final double FLANK_ANGLE_THRESHOLD = Math.cos(Math.toRadians(60.0));
    /** 包抄站位与目标的距离 */
    private static final double FLANK_DISTANCE = 4.0D;
    /** 触发包抄所需的最少同类数量（含自己） */
    private static final int MIN_ALLIES_FOR_FLANK = 3;
    /** 同类检测半径 */
    private static final double ALLY_SCAN_RADIUS = 12.0D;
    /** 包抄移速倍率 */
    private static final double FLANK_SPEED_MUL = 1.3D;

    public FlankGoal(PathfinderMob mob) {
        this.mob = mob;
        this.setFlags(EnumSet.of(Flag.MOVE));
    }

    @Override
    public boolean canUse() {
        if (!SmartHordeConfig.FLANK.get()) return false;
        if (recheckCooldown > 0) { recheckCooldown--; return false; }
        recheckCooldown = RECHECK_INTERVAL;

        LivingEntity target = mob.getTarget();
        if (target == null || !target.isAlive()) return false;

        double dist = mob.distanceTo(target);
        if (dist < 1.5D || dist > 10.0D) return false;

        // 检测附近同类数量，3 只以上才触发包抄
        int allyCount = countNearbyAllies();
        if (allyCount < MIN_ALLIES_FOR_FLANK) return false;

        Vec3 targetLook = target.getLookAngle().normalize();
        Vec3 toMob = mob.position().subtract(target.position()).normalize();
        double dot = targetLook.dot(toMob);

        // 只在目标正面朝向我时才包抄（迫使玩家回头）
        if (dot < FLANK_ANGLE_THRESHOLD) return false;

        // 计算包抄方向：在左/右/后 3 个扇区中选一个，基于实体 ID 分散
        int sectorIndex = chooseSector(allyCount);
        flankPos = computeFlankPosition(target, targetLook, sectorIndex);
        return true;
    }

    @Override
    public void start() {
        // 包抄速度 1.3 倍移速
        mob.getNavigation().moveTo(flankPos.x, flankPos.y, flankPos.z, FLANK_SPEED_MUL);
    }

    @Override
    public boolean canContinueToUse() {
        if (!SmartHordeConfig.FLANK.get()) return false;
        if (!mob.getNavigation().isInProgress()) return false;
        LivingEntity target = mob.getTarget();
        return target != null && target.isAlive() && mob.distanceToSqr(flankPos) > 2.0D;
    }

    @Override
    public void stop() {
        mob.getNavigation().stop();
    }

    // ===== 核心逻辑 =====

    /**
     * 用实体 ID 取模来为每只怪分配一个扇区索引，保证群体内各怪分散到不同方向。
     * <p>扇区含义（相对目标视线）：
     * <ul>
     *   <li>0 = 左侧 90°</li>
     *   <li>1 = 右侧 90°</li>
     *   <li>2 = 正后方 180°</li>
     *   <li>3 = 左后 135°（更多怪时进一步分散）</li>
     *   <li>4 = 右后 135°</li>
     * </ul>
     */
    private int chooseSector(int allyCount) {
        long id = mob.getId();
        int sectorCount = Math.min(5, Math.max(3, allyCount));
        return (int) (Math.abs(id) % sectorCount);
    }

    /**
     * 按扇区索引计算包抄站位坐标。
     *
     * @param target     目标
     * @param targetLook 目标视线方向（已归一化）
     * @param sectorIndex 扇区号（0=左,1=右,2=后,3=左后,4=右后）
     */
    private Vec3 computeFlankPosition(LivingEntity target, Vec3 targetLook, int sectorIndex) {
        // 目标视线的垂直向量（左手侧）
        Vec3 perp = new Vec3(-targetLook.z, 0.0D, targetLook.x).normalize();
        Vec3 backward = targetLook.scale(-1.0D);

        double angleDeg = switch (sectorIndex) {
            case 0  ->  90.0D; // 左侧 90°
            case 1  -> -90.0D; // 右侧 90°
            case 2  -> 180.0D; // 正后方
            case 3  -> 135.0D; // 左后 135°
            case 4  -> -135.0D; // 右后 135°
            default ->  90.0D;
        };

        // 在目标视线坐标系中旋转，得到偏移方向
        double rad = Math.toRadians(angleDeg);
        double cos = Math.cos(rad);
        double sin = Math.sin(rad);
        // offset = backward * cos + perp * sin （绕 Y 轴旋转）
        Vec3 offset = backward.scale(cos).add(perp.scale(sin));
        return target.position().add(offset.normalize().scale(FLANK_DISTANCE));
    }

    /**
     * 计算附近同类（含自己）的数量。
     */
    private int countNearbyAllies() {
        List<? extends Mob> nearby = mob.level().getEntitiesOfClass(
                Mob.class,
                mob.getBoundingBox().inflate(ALLY_SCAN_RADIUS),
                e -> e.isAlive() && e.getType() == mob.getType()
        );
        return nearby.size(); // nearby 包含自己
    }
}
