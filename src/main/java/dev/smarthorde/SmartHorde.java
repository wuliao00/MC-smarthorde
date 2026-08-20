package dev.smarthorde;

import dev.smarthorde.command.SmartHordeCommands;
import dev.smarthorde.config.DifficultyManager;
import dev.smarthorde.config.SmartHordeConfig;
import dev.smarthorde.horde.HordeWaveManager;
import dev.smarthorde.init.ModEntities;
import dev.smarthorde.init.ModAttributes;
import dev.smarthorde.init.ModSounds;
import dev.smarthorde.init.ModParticles;
import dev.smarthorde.init.ModSpawns;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.event.config.ModConfigEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * SmartHorde 模组主入口。
 * 轮6新增：配置加载/热重载监听 + 命令注册。
 */
@Mod(SmartHorde.MOD_ID)
public class SmartHorde {
    public static final String MOD_ID = "smarthorde";
    public static final Logger LOGGER = LoggerFactory.getLogger("SmartHorde");

    public SmartHorde(IEventBus modBus, ModContainer container) {
        ModEntities.register(modBus);
        ModSpawns.register(modBus);
        ModAttributes.register(modBus);
        ModSounds.register(modBus);
        ModParticles.register(modBus);

        container.registerConfig(ModConfig.Type.COMMON, SmartHordeConfig.SPEC);
        container.registerConfig(ModConfig.Type.SERVER, SmartHordeConfig.SPEC);

        // [轮6] 配置加载/热重载监听（mod 总线）
        modBus.addListener(SmartHorde::onConfigLoad);
        modBus.addListener(SmartHorde::onConfigReload);

        LOGGER.info("[SmartHorde] 骨架已加载，等待后续模块填充。");
    }

    @SubscribeEvent
    public static void onConfigLoad(ModConfigEvent.Loading event) {
        if (event.getConfig().getSpec() == SmartHordeConfig.SPEC) {
            DifficultyManager.reloadFromConfig();
        }
    }

    @SubscribeEvent
    public static void onConfigReload(ModConfigEvent.Reloading event) {
        if (event.getConfig().getSpec() == SmartHordeConfig.SPEC) {
            DifficultyManager.reloadFromConfig();
        }
    }

    /** [轮6+7] 命令注册 + 波次 tick 驱动（NeoForge 游戏总线，自动注册）。 */
    @net.neoforged.fml.common.EventBusSubscriber(modid = MOD_ID)
    public static class GameBusEvents {
        @SubscribeEvent
        public static void onRegisterCommands(net.neoforged.neoforge.event.RegisterCommandsEvent event) {
            SmartHordeCommands.register(event);
        }

        // [轮7] 波次管理器的 tick 驱动
        @SubscribeEvent
        public static void onServerTick(net.neoforged.neoforge.event.tick.ServerTickEvent.Post event) {
            HordeWaveManager.getInstance().onServerTick(event);
        }
    }
}
