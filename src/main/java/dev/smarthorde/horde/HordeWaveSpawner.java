package dev.smarthorde.horde;

import dev.smarthorde.entity.SmartZombie;
import dev.smarthorde.init.ModEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;

import java.util.ArrayList;
import java.util.List;

/**
 * 环形生成器：以中心点为圆心、指定半径环上随机角度取点生成 SmartZombie。
 * 通过 MOTION_BLOCKING_NO_LEAVES 高度图取地表，确保不卡墙、不悬空。
 */
public final class HordeWaveSpawner {

    /** 清单默认半径：以玩家为中心 24~32 格。 */
    private static final double DEFAULT_MIN_RADIUS = 24.0;
    private static final double DEFAULT_MAX_RADIUS = 32.0;
    private static final int MAX_ATTEMPTS_PER_MOB = 8;

    private static final RandomSource RANDOM = RandomSource.create();

    private HordeWaveSpawner() {
    }

    /** 清单标准签名：wave start / 尸潮会话使用（半径 24~32）。 */
    public static List<SmartZombie> spawnWave(ServerLevel level, BlockPos center, int waveIndex, int count) {
        return spawnWave(level, center, waveIndex, count, DEFAULT_MIN_RADIUS, DEFAULT_MAX_RADIUS);
    }

    /** Boss 切阶段召唤仆从 / 命令召唤使用可配置半径的重载。 */
    public static List<SmartZombie> spawnWave(ServerLevel level, BlockPos center, int waveIndex, int count,
                                              double minRadius, double maxRadius) {
        List<SmartZombie> spawned = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            for (int attempt = 0; attempt < MAX_ATTEMPTS_PER_MOB; attempt++) {
                double angle = RANDOM.nextDouble() * Math.PI * 2;
                double radius = minRadius + RANDOM.nextDouble() * (maxRadius - minRadius);
                int x = center.getX() + (int) Math.round(Math.cos(angle) * radius);
                int z = center.getZ() + (int) Math.round(Math.sin(angle) * radius);
                int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);

                BlockPos pos = new BlockPos(x, y, z);
                if (!isSafeSpawnPos(level, pos)) {
                    continue;
                }

                SmartZombie zombie = createZombie(level, pos);
                if (zombie != null) {
                    spawned.add(zombie);
                    break;
                }
            }
        }
        return spawned;
    }

    private static SmartZombie createZombie(ServerLevel level, BlockPos pos) {
        EntityType<SmartZombie> type = ModEntities.SMART_ZOMBIE.get();
        SmartZombie zombie = type.create(level);
        if (zombie == null) {
            return null;
        }
        zombie.moveTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5,
                RANDOM.nextFloat() * 360.0F, 0.0F);
        DifficultyInstance difficulty = level.getCurrentDifficultyAt(pos);
        zombie.finalizeSpawn(level, difficulty, MobSpawnType.EVENT, null);
        level.addFreshEntity(zombie);
        return zombie;
    }

    /** 地表下方为实心碰撞块、身体两格无碰撞 => 不卡墙不悬空。 */
    private static boolean isSafeSpawnPos(ServerLevel level, BlockPos pos) {
        BlockState below = level.getBlockState(pos.below());
        if (!below.isCollisionShapeFullBlock(level, pos.below())) {
            return false;
        }
        return level.getBlockState(pos).getCollisionShape(level, pos).isEmpty()
                && level.getBlockState(pos.above()).getCollisionShape(level, pos.above()).isEmpty();
    }
}
