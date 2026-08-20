package dev.smarthorde.network;

import dev.smarthorde.SmartHorde;
import dev.smarthorde.client.ClientHordeData;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * 波次数据同步包（轮8）。服务端 → 客户端。
 * 手动编解码（7 字段超过 StreamCodec.composite 内置上限）。
 */
public record HordeWaveSyncPacket(
        boolean active,
        int     currentWave,
        int     totalWaves,
        int     aliveCount,
        int     waveSize,
        int     phase,
        int     countdownSec
) implements CustomPacketPayload {

    public static final Type<HordeWaveSyncPacket> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(SmartHorde.MOD_ID, "horde_wave_sync"));

    public static final StreamCodec<FriendlyByteBuf, HordeWaveSyncPacket> STREAM_CODEC =
            StreamCodec.ofMember(HordeWaveSyncPacket::write, HordeWaveSyncPacket::decode);

    public void write(FriendlyByteBuf buf) {
        buf.writeBoolean(active);
        buf.writeInt(currentWave);
        buf.writeInt(totalWaves);
        buf.writeInt(aliveCount);
        buf.writeInt(waveSize);
        buf.writeInt(phase);
        buf.writeInt(countdownSec);
    }

    public static HordeWaveSyncPacket decode(FriendlyByteBuf buf) {
        return new HordeWaveSyncPacket(
                buf.readBoolean(),
                buf.readInt(),
                buf.readInt(),
                buf.readInt(),
                buf.readInt(),
                buf.readInt(),
                buf.readInt()
        );
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(HordeWaveSyncPacket pkt, IPayloadContext ctx) {
        ctx.enqueueWork(() -> ClientHordeData.update(
                pkt.active(), pkt.currentWave(), pkt.totalWaves(),
                pkt.aliveCount(), pkt.waveSize(), pkt.phase(), pkt.countdownSec()));
    }
}
