package dev.smarthorde.client;

import dev.smarthorde.SmartHordeMod;
import dev.smarthorde.client.hud.WaveHudOverlay;
import dev.smarthorde.client.renderer.HordeBossRenderer;
import dev.smarthorde.client.renderer.SmartZombieRenderer;
import dev.smarthorde.init.ModEntities;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;

/**
 * 渲染器注册（MOD 总线，仅客户端）。
 * SmartZombie / HordeBoss 使用专属渲染器：僵尸姿态模型 + 自绘贴图，Boss 按变体换肤并放大。
 */
@EventBusSubscriber(modid = SmartHordeMod.MODID, bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class SmartHordeClient {

    private SmartHordeClient() {
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
