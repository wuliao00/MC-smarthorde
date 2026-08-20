package dev.smarthorde.client;

import dev.smarthorde.SmartHorde;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;

/**
 * MOD 总线客户端事件（轮8）：注册 HUD 叠加层。
 */
@EventBusSubscriber(modid = SmartHorde.MOD_ID, value = Dist.CLIENT)
public final class ClientModEvents {

    private ClientModEvents() {}

    @SubscribeEvent
    public static void registerGuiLayers(RegisterGuiLayersEvent event) {
        event.registerAboveAll(
                ResourceLocation.fromNamespaceAndPath(SmartHorde.MOD_ID, "horde_hud"),
                new HordeHudLayer()
        );
    }
}
