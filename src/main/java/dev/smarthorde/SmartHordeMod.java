package dev.smarthorde;

import com.mojang.logging.LogUtils;
import dev.smarthorde.init.ModEntities;
import dev.smarthorde.init.ModNetworking;
import dev.smarthorde.init.ModSounds;
import dev.smarthorde.config.SmartHordeConfig;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import org.slf4j.Logger;

/**
 * SmartHorde 主入口。
 * 命令注册见 {@link dev.smarthorde.command.SmartHordeCommands}（GAME 总线监听 RegisterCommandsEvent）。
 */
@Mod(SmartHordeMod.MODID)
public class SmartHordeMod {

    public static final String MODID = "smarthorde";
    public static final Logger LOGGER = LogUtils.getLogger();

    public SmartHordeMod(IEventBus modBus, ModContainer container) {
        ModEntities.register(modBus);
        ModSounds.register(modBus);
        ModNetworking.register(modBus);

        container.registerConfig(ModConfig.Type.COMMON, SmartHordeConfig.SPEC);

        LOGGER.info("SmartHorde initialized (difficulty={}, injectVanilla={})",
                SmartHordeConfig.DIFFICULTY_PRESET.getDefault(),
                SmartHordeConfig.INJECT_VANILLA.getDefault());
    }
}
