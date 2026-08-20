package dev.smarthorde.horde;

import dev.smarthorde.init.ModEntities;
import dev.smarthorde.entity.SmartZombie;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.level.block.state.BlockState;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * 波次生成器（轮7）。环形生成 SmartZombie，零方块操作。
 */
public final class HordeWaveSpawner {

    private static final Logger LOGGER = LoggerFactory.getLogger("SmartHorde");
    private static final int MAX_RETRIES = 5;
    private static final double BASE_RADIUS = 8.0D;
    private static final double RADIUS_PER_WAVE = 2.0D;

    private HordeWaveSpawner() {}

    public static double getSpawnRadius(int wave) {
        return BASE_RADIUS + wave * RADIUS_PER_WAVE;
    }

    public static List<UUID> spawnWave(ServerLevel level, BlockPos center, int wave, int count) {
        List<UUID> spawned = new ArrayList<>();
        double radius = getSpawnRadius(wave);

        for (int i = 0; i < count; i++) {
            BlockPos spawnPos = findSpawnPosition(level, center, radius);
            if (spawnPos == null) {
                LOGGER.warn("[SmartHorde] 波次{}第{}只怪找不到合法生成位置", wave, i);
                continue;
            }

            SmartZombie zombie = ModEntities.SMART_ZOMBIE.get().create(level);
            if (zombie == null) continue;

            zombie.setPos(spawnPos.getX() + 0.5D, spawnPos.getY(), spawnPos.getZ() + 0.5D);
            zombie.finalizeSpawn(level, level.getCurrentDifficultyAt(spawnPos),
                    MobSpawnType.EVENT, null);
            level.addFreshEntity(zombie);
            spawned.add(zombie.getUUID());
        }

        LOGGER.info("[SmartHorde] 波次{}生成 {}/{} 只怪", wave, spawned.size(), count);
        return spawned;
    }

    private static BlockPos findSpawnPosition(ServerLevel level, BlockPos center, double radius) {
        for (int attempt = 0; attempt < MAX_RETRIES; attempt++) {
            double angle = level.getRandom().nextDouble() * Math.PI * 2.0D;
            double dist = radius * (0.6D + level.getRandom().nextDouble() * 0.4D);
            int x = center.getX() + (int) (Math.cos(angle) * dist);
            int z = center.getZ() + (int) (Math.sin(angle) * dist);

            int y = center.getY();
            for (int dy = 0; dy >= -10; dy--) {
                BlockPos ground = new BlockPos(x, y + dy, z);
                BlockPos above1 = ground.above();
                BlockPos above2 = ground.above(2);

                if (isSolidGround(level, ground)
                        && isAir(level, above1)
                        && isAir(level, above2)) {
                    return above1;
                }
            }
        }
        return null;
    }

    private static boolean isSolidGround(ServerLevel level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        return state.isSolidRender(level, pos);
    }

    private static boolean isAir(ServerLevel level, BlockPos pos) {
        return level.getBlockState(pos).isAir();
    }
}
