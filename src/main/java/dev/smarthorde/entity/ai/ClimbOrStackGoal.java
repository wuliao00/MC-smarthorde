package dev.smarthorde.entity.ai;

import dev.smarthorde.config.SmartHordeConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import java.util.EnumSet;
import java.util.List;

/**
 * 攀爬 + 叠罗汉翻墙（合一 Goal，内部互斥切换）。
 * 绝不挖墙、绝不垫方块。
 *
 * <p>[F1] 优先级 0（高于 SmartMeleeAttackGoal 的 1），但 canUse 严格门控：
 * 仅当攻击目标显著高于自身（≥1.5 格）、水平距离 ≤6 格、自身在地面，
 * 且面前有可攀墙（CLIMB）或可叠罗汉伙伴（STACK）时才启动，避免过度抢占近战。
 * 优先级 1 会复刻旧死代码：SmartMeleeAttackGoal 同为 1 且先注册先启动，
 * WrappedGoal 同优先级先到先得，爬墙连 canUse 都不会被评估。
 *
 * <p>[F2] CLIMB 物理移植自 1.0.x ccad2ba：navigation.stop() +
 * 朝墙 setDeltaMovement（水平 0.18 / 垂直 0.28）+ hasImpulse=true。
 * 关键在于停掉寻路导航——否则原版 MoveControl 每 tick 覆盖 deltaMovement，
 * 攀爬推力无效。带总爬升高度 6 格、单次时长 160 tick 双保护。
 *
 * <p>[F3] STACK 移植 1.0.x fd99a06 三层塔：允许骑乘者再被骑（每层限 2 乘客），
 * 塔由底层（非乘客）成员执行 moveTo，乘客不 moveTo；乘客接近目标
 * （水平 ≤2 格且不低于目标高度 -1）后主动 stopRiding 并跃出（dismount leap）。
 * 骑乘期间置 persistent-data 标记，近战/距离管理等 Goal 让位，防乘客被抢占致塔瓦解。
 */
public class ClimbOrStackGoal extends Goal {

    // ===== 爬墙参数（参考 1.0.x ccad2ba）=====
    /** 垂直攀爬推力。 */
    private static final double CLIMB_SPEED = 0.28D;
    /** 水平贴墙推力。 */
    private static final double WALL_PUSH = 0.18D;
    /** [F2] 单次爬墙时长上限（tick）。 */
    private static final int MAX_CLIMB_TICKS = 160;
    /** [F2] 总爬升高度上限（格）。 */
    private static final double MAX_CLIMB_HEIGHT = 6.0D;
    /** 爬墙结束后的再触发冷却（防贴墙抖动）。 */
    private static final int CLIMB_COOLDOWN_TICKS = 40;

    // ===== 叠罗汉参数（参考 1.0.x fd99a06）=====
    private static final double STACK_SEARCH_RADIUS = 3.0D;
    /** 每层最多乘客数（< 该值的候选可骑，3 层塔每层最多 2 乘客）。 */
    private static final int MAX_STACK_PASSENGERS = 2;
    /** 距伙伴小于该距离才执行骑乘（格）。 */
    private static final double STACK_RIDE_DIST = 2.0D;
    /** [F3] dismount 判定：距目标水平距离（格）。 */
    private static final double DISMOUNT_HORIZ_DIST = 2.0D;
    /** [F3] dismount 高度余量：不低于目标高度 -1 格。 */
    private static final double DISMOUNT_HEIGHT_MARGIN = 1.0D;
    /** [F3] 塔已到位但高度不足时的保底脱离宽限（tick）。 */
    private static final int DISMOUNT_GRACE_TICKS = 40;
    /** dismount 跃出上抛（fd99a06: 0.75 -> 0.85）。 */
    private static final double DISMOUNT_LEAP_UP = 0.85D;
    /** dismount 跃出水平推力。 */
    private static final double DISMOUNT_LEAP_FORWARD = 1.2D;

    /** [F3] STACK 骑乘标记（persistent data，近战/距离管理 Goal 读取后让位）。 */
    private static final String TAG_STACK_RIDER = "smarthorde_stack_rider";

    private final PathfinderMob mob;
    private Mode activeMode = Mode.NONE;
    private int recheckCooldown = 0;
    /** CLIMB/STACK 共用时长计数（tick）。 */
    private int climbTicks = 0;
    private double climbStartY = 0.0D;
    /** [F3] 塔到位（水平 ≤2 格）但高度不足的连续 tick 计数。 */
    private int dismountNearTicks = 0;

    // [C5] 检测结果缓存（节流，避免每 tick 重扫）
    private boolean cachedClimbable = false;
    private int climbCacheTick = -1;
    private Mob cachedPartner = null;
    private int partnerCacheTick = -1;
    private static final int RECHECK_INTERVAL = 10;
    /** [C5] 缓存时长 = AI_TICK_INTERVAL × 2（默认档 8 tick，落在 5-10 区间）。 */
    private static final int CACHE_MULT = 2;
    private static final double CLIMB_WALL_DIST = 0.6D;

    private enum Mode { NONE, CLIMB, STACK }

    public ClimbOrStackGoal(PathfinderMob mob) {
        this.mob = mob;
        this.setFlags(EnumSet.of(Flag.MOVE, Flag.JUMP));
    }

    // ===== [F3] STACK 乘客标记（供其它 Goal 查询让位）=====

    /** 该实体是否正处于 STACK 骑乘阶段（近战/距离管理等 Goal 检查后让位）。 */
    public static boolean isStackRider(Mob mob) {
        return mob.getPersistentData().getBoolean(TAG_STACK_RIDER);
    }

    private static void setStackRider(Mob mob, boolean value) {
        mob.getPersistentData().putBoolean(TAG_STACK_RIDER, value);
    }

    @Override
    public void start() {
        // [C5] goal 启动时失效缓存
        climbCacheTick = -1;
        cachedPartner = null;
        partnerCacheTick = -1; // [m6] null 结果也节流后，重启需一并失效时间戳
        climbTicks = 0;
        climbStartY = mob.getY();
        dismountNearTicks = 0;
    }

    @Override
    public boolean canUse() {
        if (recheckCooldown > 0) { recheckCooldown--; return false; }
        recheckCooldown = RECHECK_INTERVAL;

        LivingEntity target = mob.getTarget();
        if (target == null || !target.isAlive()) return false;

        // [F1] 严格门控 (a)：目标须显著高于自身（防过度抢占近战）
        if (target.getY() - mob.getY() < 1.5D) return false;
        // 空中/攀爬途中不重新触发（参考 1.0.x 稳定性检查）
        if (!mob.onGround()) return false;

        double dist = Math.sqrt(mob.distanceToSqr(target));
        if (dist > 6.0D) return false;

        // [F1] 严格门控 (b)：面前有可攀墙（CLIMB）或可叠伙伴（STACK）才允许启动
        if (SmartHordeConfig.CLIMBING.get() && hasClimbableWall()) {
            activeMode = Mode.CLIMB;
            return true;
        }
        if (SmartHordeConfig.STACK_UP.get() && hasStackPartner()) {
            activeMode = Mode.STACK;
            return true;
        }
        activeMode = Mode.NONE;
        return false;
    }

    @Override
    public boolean canContinueToUse() {
        if (activeMode == Mode.NONE) return false;
        LivingEntity target = mob.getTarget();
        if (target == null || !target.isAlive()) return false;
        // CLIMB/STACK 共用时长保护
        if (climbTicks >= MAX_CLIMB_TICKS) return false;

        if (activeMode == Mode.CLIMB) {
            if (!SmartHordeConfig.CLIMBING.get()) return false;
            // [F2] 保护：总爬升高度上限
            if (mob.getY() - climbStartY >= MAX_CLIMB_HEIGHT) return false;
            // [F2] 到达目标高度（落回地面且高度差不再显著）=> 正常退出交还控制
            if (mob.onGround() && target.getY() - mob.getY() <= 1.2D) return false;
            return hasClimbableWall();
        }
        if (activeMode == Mode.STACK) {
            if (!SmartHordeConfig.STACK_UP.get()) return false;
            if (getStackPartner() == null) return false;
            // 乘客：由 tick 内 dismount 逻辑决定退出
            if (mob.isPassenger()) return true;
            return target.getY() - mob.getY() > 0.5D;
        }
        return false;
    }

    @Override
    public void tick() {
        LivingEntity target = mob.getTarget();
        if (target == null) return;

        climbTicks++;

        if (activeMode == Mode.CLIMB) {
            tickClimb(target);
        } else if (activeMode == Mode.STACK) {
            tickStack(target);
        }
    }

    /** [F2] 爬墙 tick：停导航 + 朝墙推力（ccad2ba 方案）。 */
    private void tickClimb(LivingEntity target) {
        // 关键：停掉寻路导航，否则 MoveControl 每 tick 覆盖 deltaMovement，攀爬推力无效
        mob.getNavigation().stop();

        // 朝向目标并压向墙面（原版 horizontalCollision 会触发攀爬姿态动画）
        float wanted = (float) (Math.atan2(
                target.getZ() - mob.getZ(), target.getX() - mob.getX()) * (180.0 / Math.PI)) - 90.0F;
        mob.setYRot(wanted);
        mob.setYHeadRot(wanted);

        Direction facing = facingTowards(target);
        Vec3 push = new Vec3(facing.getStepX() * WALL_PUSH, CLIMB_SPEED, facing.getStepZ() * WALL_PUSH);
        mob.setDeltaMovement(push);
        mob.hasImpulse = true;
    }

    /** [F3] 叠罗汉 tick：底层 moveTo / 乘客 dismount。 */
    private void tickStack(LivingEntity target) {
        Mob partner = getStackPartner();
        if (partner == null) return; // 下轮 canContinueToUse=false -> stop()

        if (!mob.isPassenger()) {
            // [F3] 塔由底层（非乘客）成员驱动：仅底层执行 moveTo，乘客不 moveTo
            //（乘客 moveTo 无效，塔的移动由 vehicle 带动）
            mob.getNavigation().moveTo(target, 1.0D);
            // 靠近伙伴后骑上（fd99a06：骑乘者可再被骑，形成 3 层链）
            if (mob.distanceToSqr(partner) < STACK_RIDE_DIST * STACK_RIDE_DIST
                    && !wouldFormRideCycle(partner)
                    && mob.startRiding(partner, true)) {
                mob.getNavigation().stop();
                setStackRider(mob, true);
            }
        } else {
            setStackRider(mob, true);
            double dx = target.getX() - mob.getX();
            double dz = target.getZ() - mob.getZ();
            double horizDist = Math.sqrt(dx * dx + dz * dz);

            if (horizDist <= DISMOUNT_HORIZ_DIST) {
                dismountNearTicks++;
                if (mob.getY() >= target.getY() - DISMOUNT_HEIGHT_MARGIN) {
                    // [F3] 主动 dismount：接近目标水平 2 格且不低于目标高度 -1 => 跳下并跃向目标
                    mob.stopRiding();
                    setStackRider(mob, false);
                    leapTowardsTarget(target);
                    activeMode = Mode.NONE;
                    dismountNearTicks = 0;
                } else if (dismountNearTicks >= DISMOUNT_GRACE_TICKS) {
                    // 塔已到位但高度不足（如中层跳不上 4 格台）：保底脱离，交还 AI 控制权
                    mob.stopRiding();
                    setStackRider(mob, false);
                    activeMode = Mode.NONE;
                    dismountNearTicks = 0;
                }
            } else {
                dismountNearTicks = 0;
            }
        }
    }

    @Override
    public void stop() {
        if (activeMode == Mode.CLIMB) {
            // [F2] 爬墙结束后进入冷却，防贴墙抖动反复触发
            recheckCooldown = Math.max(recheckCooldown, CLIMB_COOLDOWN_TICKS);
        }
        activeMode = Mode.NONE;
        climbTicks = 0;
        dismountNearTicks = 0;
        setStackRider(mob, false);
        if (mob.isPassenger()) {
            mob.stopRiding();
        }
        mob.getNavigation().stop();
    }

    // ===== 攀爬检测 =====

    /** [C5] 结果缓存 AI_TICK_INTERVAL×2 tick，避免每 tick 4 次 getBlockState。 */
    private boolean hasClimbableWall() {
        int interval = Math.max(1, SmartHordeConfig.AI_TICK_INTERVAL.get()) * CACHE_MULT;
        if (climbCacheTick >= 0 && mob.tickCount - climbCacheTick < interval) {
            return cachedClimbable;
        }
        cachedClimbable = scanClimbableWall();
        climbCacheTick = mob.tickCount;
        return cachedClimbable;
    }

    private boolean scanClimbableWall() {
        BlockPos mobPos = mob.blockPosition();
        for (Direction dir : Direction.Plane.HORIZONTAL) {
            BlockPos adjacent = mobPos.relative(dir);
            BlockState state = mob.level().getBlockState(adjacent);
            if (state.isSolidRender(mob.level(), adjacent)) {
                double wallDist = switch (dir) {
                    case NORTH -> Math.abs(mob.getZ() - (adjacent.getZ() + 1.0D));
                    case SOUTH -> Math.abs(mob.getZ() - adjacent.getZ());
                    case WEST  -> Math.abs(mob.getX() - (adjacent.getX() + 1.0D));
                    case EAST  -> Math.abs(mob.getX() - adjacent.getX());
                    default -> Double.MAX_VALUE;
                };
                if (wallDist <= CLIMB_WALL_DIST) return true;
            }
        }
        return false;
    }

    // ===== 叠罗汉检测 =====

    private boolean hasStackPartner() {
        return getStackPartner() != null;
    }

    /**
     * [C5] 缓存版伙伴获取：非骑乘状态按 AI_TICK_INTERVAL×2 tick 节流重扫；
     * 骑乘中只要伙伴存活即复用，避免骑乘后伙伴变为 vehicle 被原过滤条件
     * 排除而导致刚上骑就下骑的抖动。
     * [m6] 找不到伙伴（null）时同样记录时间戳：间隔内直接返回 null 不重扫，
     * 否则每次调用都会立即全量搜索，节流失效。
     */
    private Mob getStackPartner() {
        int interval = Math.max(1, SmartHordeConfig.AI_TICK_INTERVAL.get()) * CACHE_MULT;
        if (cachedPartner != null) {
            boolean ridingIt = mob.isPassenger() && mob.getVehicle() == cachedPartner;
            if (cachedPartner.isAlive() && !cachedPartner.isRemoved()
                    && (ridingIt || mob.tickCount - partnerCacheTick < interval)) {
                return cachedPartner;
            }
            cachedPartner = null;
        }
        // [m6] null 结果节流：距上次扫描（含空手而归）不足间隔时直接返回 null
        if (partnerCacheTick >= 0 && mob.tickCount - partnerCacheTick < interval) {
            return null;
        }
        cachedPartner = findNearestStackPartner();
        partnerCacheTick = mob.tickCount;
        return cachedPartner;
    }

    /**
     * [F3] 放开 isPassenger 过滤：允许骑乘者再被骑（fd99a06 三层塔，
     * feet ≈3.9 格可翻 4 格塔）。每层限 {@link #MAX_STACK_PASSENGERS} 乘客防分叉；
     * 骑乘链防环见 {@link #wouldFormRideCycle}。
     */
    private Mob findNearestStackPartner() {
        List<? extends Mob> nearby = mob.level().getEntitiesOfClass(
                Mob.class,
                mob.getBoundingBox().inflate(STACK_SEARCH_RADIUS),
                e -> e != mob && e.isAlive() && e.getType() == mob.getType()
                        && e.getPassengers().size() < MAX_STACK_PASSENGERS
                        && !wouldFormRideCycle(e)
        );
        Mob best = null;
        double bestDist = Double.MAX_VALUE;
        for (Mob e : nearby) {
            double d = mob.distanceToSqr(e);
            if (d < bestDist) {
                bestDist = d;
                best = e;
            }
        }
        return best;
    }

    /**
     * [F3] 防环：候选者若已位于自身骑乘链（vehicle 链）上则不可再骑。
     * vanilla 非强制路径的链检测在 force=true 时的覆盖语义不作依赖，
     * 此处显式检测：从候选者沿 vehicle 链向上，遇到自身即拒绝
     *（同时覆盖"候选者是我的乘客"与"候选者是更深层祖先"两种成环形态）。
     */
    private boolean wouldFormRideCycle(Mob candidate) {
        if (candidate == mob.getVehicle()) return true;
        for (Entity e = candidate.getVehicle(); e != null; e = e.getVehicle()) {
            if (e == mob) return true;
        }
        return false;
    }

    // ===== 位移辅助 =====

    /** 朝向目标方向取主轴向（几乎正对目标时用运动方向兜底）。 */
    private Direction facingTowards(LivingEntity target) {
        Vec3 flat = new Vec3(target.getX() - mob.getX(), 0, target.getZ() - mob.getZ());
        if (flat.lengthSqr() < 1.0E-4) {
            return mob.getMotionDirection();
        }
        return Direction.getNearest(flat.x, 0, flat.z);
    }

    /** [F3] dismount 跃出：向目标水平 1.2 / 上抛 0.85（fd99a06）。 */
    private void leapTowardsTarget(LivingEntity target) {
        Vec3 towards = target.position().subtract(mob.position());
        Vec3 flat = new Vec3(towards.x, 0, towards.z);
        if (flat.lengthSqr() < 1.0E-4) {
            return;
        }
        flat = flat.normalize().scale(DISMOUNT_LEAP_FORWARD);
        mob.setDeltaMovement(new Vec3(flat.x, DISMOUNT_LEAP_UP, flat.z));
        mob.hasImpulse = true;
    }
}
