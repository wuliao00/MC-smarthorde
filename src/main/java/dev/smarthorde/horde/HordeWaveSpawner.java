package dev.smarthorde.horde;

import dev.smarthorde.config.SmartHordeConfig;
import dev.smarthorde.init.ModEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.level.block.state.BlockState;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * 波次生成器（轮7，[C2] 混编扩展）。
 * <ul>
 *   <li>按 COMP_MELEE / COMP_RANGED / COMP_FLANKER 权重轮盘混编
 *       SmartZombie / HordeArcher / HordeBrute（权重 0 不出现，全 0 兜底僵尸）；
 *       仅波次路径允许精英（eliteAllowed=true），且受 BOSS_ENABLED 总开关约束；
 *       Boss 召唤仆从与 /smarthorde summon 固定仅出 SmartZombie，避免叠多条 Boss 血条</li>
 *   <li>生成点带光照检查（MAX_SPAWN_LIGHT），找不到合格暗点时放宽回退原几何条件避免卡死</li>
 *   <li>会话路径受 MAX_CONCURRENT 同屏上限约束（超限跳过该只）</li>
 *   <li>支持分帧批量生成（spawnBatch，由 HordeWaveManager 每 tick 调一批）</li>
 * </ul>
 * 零方块操作。
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

    /** 同步整波生成（仅 SmartZombie；保持旧 4 参调用点编译兼容）。 */
    public static List<UUID> spawnWave(ServerLevel level, BlockPos center, int wave, int count) {
        return spawnWave(level, center, wave, count, false);
    }

    /**
     * 同步整波生成。
     *
     * @param eliteAllowed false 时仅生成 SmartZombie（Boss 召唤仆从与 summon 命令路径，
     *                     避免叠多条 ServerBossEvent 血条）；true 时按权重轮盘混编（受 BOSS_ENABLED 约束）
     */
    public static List<UUID> spawnWave(ServerLevel level, BlockPos center, int wave,
                                       int count, boolean eliteAllowed) {
        List<UUID> spawned = spawnBatch(level, center, wave, count, null, eliteAllowed);
        LOGGER.info("[SmartHorde] 波次{}生成 {}/{} 只怪", wave, spawned.size(), count);
        return spawned;
    }

    /**
     * [C2] 批量生成（波次会话分帧调用点：每 tick 最多一批，全权重混编）。
     *
     * @param existingSessionEntities 当前会话已生成实体的 UUID 列表；
     *                                非 null 时统计其中存活数并按 MAX_CONCURRENT 跳过超限生成
     */
    public static List<UUID> spawnBatch(ServerLevel level, BlockPos center, int wave,
                                        int maxToSpawn, List<UUID> existingSessionEntities) {
        return spawnBatch(level, center, wave, maxToSpawn, existingSessionEntities, true);
    }

    /**
     * [C2] 批量生成（完整版本）。
     *
     * @param eliteAllowed false 时仅生成 SmartZombie；true 时按权重轮盘混编（受 BOSS_ENABLED 约束）
     */
    public static List<UUID> spawnBatch(ServerLevel level, BlockPos center, int wave,
                                        int maxToSpawn, List<UUID> existingSessionEntities,
                                        boolean eliteAllowed) {
        List<UUID> spawned = new ArrayList<>();
        if (maxToSpawn <= 0) return spawned;

        boolean capEnabled = existingSessionEntities != null;
        int cap = capEnabled ? SmartHordeConfig.MAX_CONCURRENT.get() : Integer.MAX_VALUE;
        int aliveExisting = capEnabled ? countAlive(level, existingSessionEntities) : 0;
        double radius = getSpawnRadius(wave);

        for (int i = 0; i < maxToSpawn && aliveExisting + spawned.size() < cap; i++) {
            BlockPos spawnPos = findSpawnPosition(level, center, radius);
            if (spawnPos == null) {
                LOGGER.warn("[SmartHorde] 波次{}第{}只怪找不到合法生成位置", wave, i);
                continue;
            }

            Monster mob = createWaveMob(level, eliteAllowed);
            if (mob == null) continue;

            mob.setPos(spawnPos.getX() + 0.5D, spawnPos.getY(), spawnPos.getZ() + 0.5D);
            mob.finalizeSpawn(level, level.getCurrentDifficultyAt(spawnPos),
                    MobSpawnType.EVENT, null);
            level.addFreshEntity(mob);
            spawned.add(mob.getUUID());
        }
        return spawned;
    }

    private static int countAlive(ServerLevel level, List<UUID> entities) {
        int count = 0;
        for (UUID uuid : entities) {
            if (level.getEntity(uuid) instanceof Mob mob && mob.isAlive()) {
                count++;
            }
        }
        return count;
    }

    /**
     * [C2] 按权重轮盘混编：COMP_MELEE→SmartZombie、COMP_RANGED→HordeArcher、
     * COMP_FLANKER→HordeBrute。权重 0 的类型不出现；权重全 0 时兜底 SmartZombie。
     * [M2] eliteAllowed=false（Boss 召唤仆从 / summon 命令）时仅出 SmartZombie；
     * eliteAllowed=true 时 HordeArcher/HordeBrute 权重受 BOSS_ENABLED 总开关约束：
     * 总开关关闭则二者权重视为 0（兜底仍为 SmartZombie）。
     * （COMP_RALLY 暂无对应"指挥"实体，配置保留待后续接入）
     */
    private static Monster createWaveMob(ServerLevel level, boolean eliteAllowed) {
        EntityType<? extends Monster> type;
        if (!eliteAllowed) {
            type = ModEntities.SMART_ZOMBIE.get();
        } else {
            int melee   = Math.max(0, SmartHordeConfig.COMP_MELEE.get());
            int ranged  = Math.max(0, SmartHordeConfig.COMP_RANGED.get());
            int flanker = Math.max(0, SmartHordeConfig.COMP_FLANKER.get());
            if (!SmartHordeConfig.BOSS_ENABLED.get()) {
                ranged = 0;
                flanker = 0;
            }
            int total = melee + ranged + flanker;

            if (total <= 0) {
                type = ModEntities.SMART_ZOMBIE.get();
            } else {
                int roll = level.getRandom().nextInt(total);
                if (roll < melee) {
                    type = ModEntities.SMART_ZOMBIE.get();
                } else if (roll < melee + ranged) {
                    type = ModEntities.HORDE_ARCHER.get();
                } else {
                    type = ModEntities.HORDE_BRUTE.get();
                }
            }
        }
        return type.create(level);
    }

    private static BlockPos findSpawnPosition(ServerLevel level, BlockPos center, double radius) {
        BlockPos fallback = null;
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
                    // [C2] 光照检查：过亮的点先记为回退候选，继续找更暗的点
                    if (level.getMaxLocalRawBrightness(above1)
                            <= SmartHordeConfig.MAX_SPAWN_LIGHT.get()) {
                        return above1;
                    }
                    if (fallback == null) {
                        fallback = above1;
                    }
                }
            }
        }
        // [C2] 找不到合格暗点 → 放宽回退原几何条件，避免卡死
        return fallback;
    }

    private static boolean isSolidGround(ServerLevel level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        return state.isSolidRender(level, pos);
    }

    private static boolean isAir(ServerLevel level, BlockPos pos) {
        return level.getBlockState(pos).isAir();
    }
}
