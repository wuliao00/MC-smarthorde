package dev.smarthorde.network;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import dev.smarthorde.SmartHordeMod;

/**
 * 波次同步包（S -> C）：currentWave / totalWaves / remainingMobs / phase / countdown。
 *
 * phase 编码契约：显式使用 {@link dev.smarthorde.horde.HordeWaveManager.WaveState}
 * 的 ordinal 序数值（VAR_INT 编码）：0=IDLE（服务端不会发送，保留值）、
 * 1=COUNTDOWN、2=ACTIVE、3=BETWEEN_WAVES、4=COMPLETE。
 * 严禁重排 WaveState 枚举常量；客户端消费方必须对未知序数走 default 兜底
 * （不可用 values()[phase] 索引），防止恶意/跨版本包内非法序数导致崩溃。
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
