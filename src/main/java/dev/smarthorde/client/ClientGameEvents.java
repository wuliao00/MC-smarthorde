package dev.smarthorde.client;

import dev.smarthorde.SmartHordeMod;
import dev.smarthorde.client.hud.HealthBarRenderer;
import dev.smarthorde.network.WaveHudPayload;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.RenderLivingEvent;

/**
 * 客户端游戏事件（GAME 总线，仅客户端）：注册头顶血量条渲染与退服 HUD 缓存清理。
 */
@EventBusSubscriber(modid = SmartHordeMod.MODID, value = Dist.CLIENT)
public final class ClientGameEvents {

    private ClientGameEvents() {
    }

    @SubscribeEvent
    public static void onRenderLivingPost(RenderLivingEvent.Post<?, ?> event) {
        HealthBarRenderer.renderHealthBar(event);
    }

    @SubscribeEvent
    public static void onClientLogout(ClientPlayerNetworkEvent.LoggingOut event) {
        WaveHudPayload.clear();
        HealthBarRenderer.clearCache();
    }
}
