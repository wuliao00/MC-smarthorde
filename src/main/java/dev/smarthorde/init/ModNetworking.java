package dev.smarthorde.init;

import dev.smarthorde.SmartHordeMod;
import dev.smarthorde.network.HordeSyncPacket;
import dev.smarthorde.network.WaveHudPayload;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

/**
 * 网络通道注册：HordeSyncPacket（S->C）波次同步。
 */
public final class ModNetworking {

    private ModNetworking() {
    }

    public static void register(IEventBus modBus) {
        modBus.addListener(ModNetworking::onRegisterPayloadHandlers);
    }

    private static void onRegisterPayloadHandlers(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar("1");
        registrar.playToClient(
                HordeSyncPacket.TYPE,
                HordeSyncPacket.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(() -> WaveHudPayload.update(payload)));
    }
}
