package dev.smarthorde.audit;

import dev.smarthorde.config.SmartHordeConfig;
import dev.smarthorde.entity.HordeBoss;
import dev.smarthorde.entity.SmartZombie;
import dev.smarthorde.horde.HordeWaveManager;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.AABB;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 全局性能审计（轮11）。
 * 每 60 秒统计 SmartZombie/Boss 数量、活跃尸潮会话数。
 * 受 performance.auditEnabled 控制，默认关。
 */
@EventBusSubscriber
public final class PerformanceAudit {

    private static final Logger LOGGER = LoggerFactory.getLogger("SmartHorde");
    private static int tickCounter = 0;
    private static final int AUDIT_INTERVAL = 1200;

    private PerformanceAudit() {}

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        if (!SmartHordeConfig.AUDIT_ENABLED.get()) return;

        tickCounter++;
        if (tickCounter < AUDIT_INTERVAL) return;
        tickCounter = 0;

        int zombieCount = 0;
        int bossCount = 0;
        for (ServerLevel level : event.getServer().getAllLevels()) {
            zombieCount += level.getEntitiesOfClass(SmartZombie.class, AABB.INFINITE).size();
            bossCount += level.getEntitiesOfClass(HordeBoss.class, AABB.INFINITE).size();
        }

        int hordeSessions = HordeWaveManager.getInstance().getActiveSessionCount();

        LOGGER.info("[SmartHorde-Audit] SmartZombie={} HordeBoss={} HordeSessions={}",
                zombieCount, bossCount, hordeSessions);
    }
}
