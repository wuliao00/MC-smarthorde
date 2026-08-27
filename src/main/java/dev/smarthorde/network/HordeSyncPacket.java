package dev.smarthorde.network;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import dev.smarthorde.SmartHordeMod;

/**
 * 波次同步包（S -> C）：currentWave / totalWaves / remainingMobs / phase / countdown。
 */
public record HordeSyncPacket(int currentWave, int totalWaves, int remainingMobs, int phase, int countdown)
        implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<HordeSyncPacket> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(SmartHordeMod.MODID, "horde_sync"));

    public static final StreamCodec<ByteBuf, HordeSyncPacket> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, HordeSyncPacket::currentWave,
            ByteBufCodecs.VAR_INT, HordeSyncPacket::totalWaves,
            ByteBufCodecs.VAR_INT, HordeSyncPacket::remainingMobs,
            ByteBufCodecs.VAR_INT, HordeSyncPacket::phase,
            ByteBufCodecs.VAR_INT, HordeSyncPacket::countdown,
            HordeSyncPacket::new);

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
