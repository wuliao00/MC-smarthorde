package dev.smarthorde.client;

import dev.smarthorde.SmartHordeMod;
import dev.smarthorde.client.hud.WaveHudOverlay;
import dev.smarthorde.client.model.HordeBossModel;
import dev.smarthorde.client.model.ModModelLayers;
import dev.smarthorde.client.model.SmartZombieModel;
import dev.smarthorde.client.renderer.HordeBossRenderer;
import dev.smarthorde.client.renderer.SmartZombieRenderer;
import dev.smarthorde.init.ModEntities;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.gui.ConfigurationScreen;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;

/**
 * 模组客户端入口与 MOD 总线注册：
 * 注册 NeoForge 原生图形化配置界面（ConfigurationScreen）、自定义 3D 模型骨骼层、实体渲染器与波次 HUD 覆层。
 */
@Mod(value = SmartHordeMod.MODID, dist = Dist.CLIENT)
@EventBusSubscriber(modid = SmartHordeMod.MODID, bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public class SmartHordeClient {

    public SmartHordeClient(ModContainer container) {
        container.registerExtensionPoint(IConfigScreenFactory.class,
                (IConfigScreenFactory) (mc, parent) -> new ConfigurationScreen(mc, parent));
    }

    @SubscribeEvent
    public static void onRegisterLayerDefinitions(EntityRenderersEvent.RegisterLayerDefinitions event) {
        event.registerLayerDefinition(ModModelLayers.SMART_ZOMBIE, SmartZombieModel::createBodyLayer);
        event.registerLayerDefinition(ModModelLayers.HORDE_BOSS, HordeBossModel::createBodyLayer);
    }

    @SubscribeEvent
    public static void onRegisterRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(ModEntities.SMART_ZOMBIE.get(), SmartZombieRenderer::new);
        event.registerEntityRenderer(ModEntities.HORDE_BOSS.get(), HordeBossRenderer::new);
    }

    @SubscribeEvent
    public static void onRegisterGuiLayers(RegisterGuiLayersEvent event) {
        event.registerAboveAll(
                ResourceLocation.fromNamespaceAndPath(SmartHordeMod.MODID, "wave_hud"),
                WaveHudOverlay.INSTANCE);
    }
}
