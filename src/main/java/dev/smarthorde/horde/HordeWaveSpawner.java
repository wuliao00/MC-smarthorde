package dev.smarthorde.horde;

import dev.smarthorde.config.SmartHordeConfig;
import dev.smarthorde.entity.EliteRole;
import dev.smarthorde.entity.HordeBoss;
import dev.smarthorde.entity.SmartZombie;
import dev.smarthorde.init.ModEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.level.block.BaseFireBlock;
import net.minecraft.world.level.block.CactusBlock;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.level.block.MagmaBlock;
import net.minecraft.world.level.block.PowderSnowBlock;
import net.minecraft.world.level.block.SweetBerryBushBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;

/**
 * 环形安全生成器（支持地表、室内建筑、地下洞穴与下界堡垒）：
 * <ul>
 *   <li>优先在中心点同层高度（+6 至 -8 格）搜索安全地板，洞穴/室内开启波次时不再把怪物错误刷到几十格外的头顶地表；</li>
 *   <li>外圈被岩壁阻挡时自动向内收缩生成半径，排除液体与危险方块并严格校验实体碰撞箱净空；</li>
 *   <li>提供 1.2x3.2 巨型 Boss 同层优先安全落点搜寻，彻底杜绝 Boss 刷入地下或屋顶的穿模与脱战 Bug。</li>
 * </ul>
 */
public final class HordeWaveSpawner {

    private static final double DEFAULT_MIN_RADIUS = 24.0;
    private static final double DEFAULT_MAX_RADIUS = 32.0;
    private static final int MAX_ATTEMPTS_PER_MOB = 14;

    private static final RandomSource RANDOM = RandomSource.create();

    private HordeWaveSpawner() {
    }

    public static List<SmartZombie> spawnWave(ServerLevel level, BlockPos center, int waveIndex, int count) {
        return spawnWave(level, center, waveIndex, count, DEFAULT_MIN_RADIUS, DEFAULT_MAX_RADIUS, null, null);
    }

    public static List<SmartZombie> spawnWave(ServerLevel level, BlockPos center, int waveIndex, int count,
                                              double minRadius, double maxRadius) {
        return spawnWave(level, center, waveIndex, count, minRadius, maxRadius, null, null);
    }

    public static List<SmartZombie> spawnWave(ServerLevel level, BlockPos center, int waveIndex, int count,
                                              double minRadius, double maxRadius,
                                              @Nullable EliteRole forcedRole,
                                              @Nullable LivingEntity initialTarget) {
        List<SmartZombie> spawned = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            for (int attempt = 0; attempt < MAX_ATTEMPTS_PER_MOB; attempt++) {
                // 前 8 次按标准外圈搜索；若身处狭窄洞穴/室内则自动收缩半径搜寻可用地面
                double effMin = attempt < 8 ? minRadius : Math.max(5.0, minRadius * 0.35);
                double effMax = attempt < 8 ? maxRadius : Math.max(effMin + 4.0, maxRadius * 0.55);
                double angle = RANDOM.nextDouble() * Math.PI * 2;
                double radius = effMin + RANDOM.nextDouble() * (effMax - effMin);
                int x = center.getX() + (int) Math.round(Math.cos(angle) * radius);
                int z = center.getZ() + (int) Math.round(Math.sin(angle) * radius);

                BlockPos pos = findSafeFloorNearY(level, x, center.getY(), z);
                if (pos == null) {
                    continue;
                }

                SmartZombie zombie = createZombie(level, pos, waveIndex, forcedRole, initialTarget);
                if (zombie != null) {
                    spawned.add(zombie);
                    break;
                }
            }
        }
        return spawned;
    }

    /**
     * 优先在 [preferredY + 6, preferredY - 8] 区间寻找与目标同层的安全地板（完美适配洞穴/地堡/室内），
     * 仅在周边无同层空间且露天可达时回退至地表 Heightmap（且高度差不超过 18 格）。
     */
    @Nullable
    private static BlockPos findSafeFloorNearY(ServerLevel level, int x, int preferredY, int z) {
        int maxDy = 6;
        int minDy = -8;
        for (int step = 0; step <= Math.max(maxDy, -minDy); step++) {
            if (step <= maxDy) {
                BlockPos up = new BlockPos(x, preferredY + step, z);
                if (isSafeSpawnPos(level, up)) {
                    return up;
                }
            }
            if (step > 0 && -step >= minDy) {
                BlockPos down = new BlockPos(x, preferredY - step, z);
                if (isSafeSpawnPos(level, down)) {
                    return down;
                }
            }
        }
        int surfaceY = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
        if (Math.abs(surfaceY - preferredY) <= 18) {
            BlockPos surface = new BlockPos(x, surfaceY, z);
            if (isSafeSpawnPos(level, surface)) {
                return surface;
            }
        }
        return null;
    }

    /** 为里程碑决战波生成一位尸潮领主统帅。 */
    @Nullable
    public static HordeBoss spawnWaveBoss(ServerLevel level, BlockPos center, @Nullable LivingEntity target) {
        if (SmartHordeConfig.SPEC.isLoaded() && !SmartHordeConfig.BOSS_ENABLED.get()) {
            return null;
        }
        EntityType<HordeBoss> type = ModEntities.HORDE_BOSS.get();
        HordeBoss boss = type.create(level);
        if (boss == null) {
            return null;
        }
        double angle = RANDOM.nextDouble() * Math.PI * 2;
        Vec3 preferred = new Vec3(
                center.getX() + Math.cos(angle) * 18.0,
                center.getY(),
                center.getZ() + Math.sin(angle) * 18.0);
        Vec3 safePos = findSafeBossSpawnPos(level, boss, preferred, center);
        boss.moveTo(safePos.x, safePos.y, safePos.z, RANDOM.nextFloat() * 360.0F, 0.0F);
        boss.finalizeSpawn(level, level.getCurrentDifficultyAt(BlockPos.containing(safePos)), MobSpawnType.EVENT, null);
        if (target != null && target.isAlive()) {
            boss.setTarget(target);
        }
        level.addFreshEntity(boss);
        return boss;
    }

    /**
     * 为 1.2x3.2 碰撞箱的 HordeBoss 搜寻无方块碰撞、无液体浸泡的同层优先安全落点。
     */
    public static Vec3 findSafeBossSpawnPos(ServerLevel level, HordeBoss boss, Vec3 preferred, BlockPos fallbackCenter) {
        BlockPos prefBlock = BlockPos.containing(preferred.x, preferred.y, preferred.z);
        for (int dy = 4; dy >= -5; dy--) {
            Vec3 candidate = new Vec3(prefBlock.getX() + 0.5, prefBlock.getY() + dy, prefBlock.getZ() + 0.5);
            if (isSafeBossSpot(level, boss, candidate)) {
                return candidate;
            }
        }

        for (int radius = 3; radius <= 15; radius += 3) {
            for (int step = 0; step < 8; step++) {
                double theta = (Math.PI * 2.0 * step) / 8.0;
                int x = fallbackCenter.getX() + (int) Math.round(Math.cos(theta) * radius);
                int z = fallbackCenter.getZ() + (int) Math.round(Math.sin(theta) * radius);
                for (int dy = 4; dy >= -5; dy--) {
                    Vec3 candidate = new Vec3(x + 0.5, fallbackCenter.getY() + dy, z + 0.5);
                    if (isSafeBossSpot(level, boss, candidate)) {
                        return candidate;
                    }
                }
                int surfaceY = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
                if (Math.abs(surfaceY - fallbackCenter.getY()) <= 16) {
                    Vec3 surfaceCandidate = new Vec3(x + 0.5, surfaceY, z + 0.5);
                    if (isSafeBossSpot(level, boss, surfaceCandidate)) {
                        return surfaceCandidate;
                    }
                }
            }
        }
        return new Vec3(fallbackCenter.getX() + 0.5, fallbackCenter.getY(), fallbackCenter.getZ() + 0.5);
    }

    private static boolean isSafeBossSpot(ServerLevel level, HordeBoss boss, Vec3 pos) {
        BlockPos blockPos = BlockPos.containing(pos.x, pos.y, pos.z);
        BlockState below = level.getBlockState(blockPos.below());
        if (below.getCollisionShape(level, blockPos.below()).isEmpty() || isHazardBlock(below)) {
            return false;
        }
        if (!level.getFluidState(blockPos).isEmpty() || !level.getFluidState(blockPos.above()).isEmpty()) {
            return false;
        }
        boss.setPos(pos.x, pos.y, pos.z);
        return level.noCollision(boss);
    }

    @Nullable
    private static SmartZombie createZombie(ServerLevel level, BlockPos pos, int waveIndex,
                                            @Nullable EliteRole forcedRole, @Nullable LivingEntity initialTarget) {
        EntityType<SmartZombie> type = ModEntities.SMART_ZOMBIE.get();
        SmartZombie zombie = type.create(level);
        if (zombie == null) {
            return null;
        }
        zombie.moveTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5,
                RANDOM.nextFloat() * 360.0F, 0.0F);
        if (!level.noCollision(zombie)) {
            return null;
        }
        if (forcedRole != null) {
            zombie.setEliteRole(forcedRole);
        } else if (waveIndex > 0) {
            double baseChance = SmartHordeConfig.SPEC.isLoaded()
                    ? SmartHordeConfig.ELITE_SPAWN_CHANCE.get()
                    : 0.35;
            double boostedChance = Math.min(0.75, baseChance + waveIndex * 0.05);
            if (RANDOM.nextDouble() < boostedChance) {
                zombie.setEliteRole(EliteRole.pickEliteByWeightRoll(
                        RANDOM.nextInt(EliteRole.totalEliteWeight())));
            } else {
                zombie.setEliteRole(EliteRole.STANDARD);
            }
        }
        DifficultyInstance difficulty = level.getCurrentDifficultyAt(pos);
        zombie.finalizeSpawn(level, difficulty, MobSpawnType.EVENT, null);
        if (initialTarget != null && initialTarget.isAlive()) {
            zombie.setPersistenceRequired();
            zombie.setTarget(initialTarget);
        }
        level.addFreshEntity(zombie);
        return zombie;
    }

    private static boolean isSafeSpawnPos(ServerLevel level, BlockPos pos) {
        BlockState below = level.getBlockState(pos.below());
        if (below.getCollisionShape(level, pos.below()).isEmpty() || isHazardBlock(below)) {
            return false;
        }
        if (!level.getFluidState(pos).isEmpty() || !level.getFluidState(pos.above()).isEmpty()) {
            return false;
        }
        BlockState feet = level.getBlockState(pos);
        BlockState head = level.getBlockState(pos.above());
        if (isHazardBlock(feet) || isHazardBlock(head)) {
            return false;
        }
        return feet.getCollisionShape(level, pos).isEmpty()
                && head.getCollisionShape(level, pos.above()).isEmpty();
    }

    private static boolean isHazardBlock(BlockState state) {
        var block = state.getBlock();
        return block instanceof CactusBlock
                || block instanceof MagmaBlock
                || block instanceof CampfireBlock
                || block instanceof BaseFireBlock
                || block instanceof SweetBerryBushBlock
                || block instanceof PowderSnowBlock;
    }
}
