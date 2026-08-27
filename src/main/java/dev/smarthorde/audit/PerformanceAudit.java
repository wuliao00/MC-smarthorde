package dev.smarthorde.audit;

import com.mojang.logging.LogUtils;
import dev.smarthorde.SmartHordeMod;
import dev.smarthorde.config.SmartHordeConfig;
import dev.smarthorde.entity.HordeBoss;
import dev.smarthorde.entity.SmartZombie;
import dev.smarthorde.horde.HordeWaveManager;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.entity.EntityTypeTest;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.slf4j.Logger;

/**
 * 性能审计日志：每 1200 tick（60 秒）统计一次 SmartZombie/HordeBoss 数量与活跃波次会话数。
 * 受 performance.auditEnabled 控制（默认 false），仅输出到 SLF4J 日志。
 */
@EventBusSubscriber(modid = SmartHordeMod.MODID)
public final class PerformanceAudit {

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final int AUDIT_INTERVAL_TICKS = 1200;

    private static int tickCounter;

    private PerformanceAudit() {
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        if (!SmartHordeConfig.AUDIT_ENABLED.get()) {
            return;
        }
        if (++tickCounter < AUDIT_INTERVAL_TICKS) {
            return;
        }
        tickCounter = 0;

        int activeSessions = HordeWaveManager.getActiveSessionCount();
        for (ServerLevel level : event.getServer().getAllLevels()) {
            int zombies = level.getEntities(EntityTypeTest.forClass(SmartZombie.class), e -> true).size();
            int bosses = level.getEntities(EntityTypeTest.forClass(HordeBoss.class), e -> true).size();
            if (zombies > 0 || bosses > 0 || activeSessions > 0) {
                LOGGER.info("[SmartHorde audit] dimension={} smartZombies={} hordeBosses={} activeWaveSessions={}",
                        level.dimension().location(), zombies, bosses, activeSessions);
            }
        }
    }
}
