package dev.smarthorde.network;

import javax.annotation.Nullable;

/**
 * HUD 数据包载体（客户端状态）：保存最近一次 HordeSyncPacket，
 * 超过 5 秒未刷新视为会话结束，HUD 自动隐藏。
 */
public final class WaveHudPayload {

    private static final long STALE_MILLIS = 5000;

    private static volatile HordeSyncPacket lastPacket;
    private static volatile long lastUpdateTime;

    private WaveHudPayload() {
    }

    public static void update(HordeSyncPacket packet) {
        lastPacket = packet;
        lastUpdateTime = System.currentTimeMillis();
    }

    public static void clear() {
        lastPacket = null;
        lastUpdateTime = 0;
    }

    /** 数据过期（会话结束/断线）时返回 null。 */
    @Nullable
    public static HordeSyncPacket current() {
        HordeSyncPacket packet = lastPacket;
        if (packet == null) {
            return null;
        }
        if (System.currentTimeMillis() - lastUpdateTime > STALE_MILLIS) {
            return null;
        }
        return packet;
    }
}
