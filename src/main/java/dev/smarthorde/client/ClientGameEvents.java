package dev.smarthorde.client;

import dev.smarthorde.SmartHordeMod;
import dev.smarthorde.client.hud.HealthBarRenderer;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderLivingEvent;

/**
 * 客户端游戏事件（GAME 总线，仅客户端）：注册头顶血量条渲染。
 * HUD 层注册见 SmartHordeClient（MOD 总线）。
 */
@EventBusSubscriber(modid = SmartHordeMod.MODID, value = Dist.CLIENT)
public final class ClientGameEvents {

    private ClientGameEvents() {
    }

    @SubscribeEvent
    public static void onRenderLivingPost(RenderLivingEvent.Post event) {
        HealthBarRenderer.renderHealthBar(event);
    }
}
