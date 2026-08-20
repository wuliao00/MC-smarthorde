package dev.smarthorde.network;

import dev.smarthorde.SmartHorde;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

/**
 * 网络通道注册（轮8）。MOD 事件总线。
 */
@EventBusSubscriber(modid = SmartHorde.MOD_ID)
public final class ModNetworking {

    private ModNetworking() {}

    @SubscribeEvent
    public static void register(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar("1");
        registrar.playToClient(
                HordeWaveSyncPacket.TYPE,
                HordeWaveSyncPacket.STREAM_CODEC,
                HordeWaveSyncPacket::handle
        );
    }
}
