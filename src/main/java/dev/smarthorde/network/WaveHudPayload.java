package dev.smarthorde.network;

import javax.annotation.Nullable;

/**
 * HUD 数据包载体（客户端状态）：
 * 保存最近一次 {@link HordeSyncPacket} 并追踪当波怪物峰值用于平滑波内清剿进度；
 * 收到 {@code phase == 0}（IDLE，如执行 wave stop）立即清空隐藏，超过 5 秒未刷新亦自动隐藏。
 */
public final class WaveHudPayload {

    private static final long STALE_MILLIS = 5000;

    private static volatile HordeSyncPacket lastPacket;
    private static volatile long lastUpdateTime;
    private static volatile int trackedWave = -1;
    private static volatile int peakMobsInWave = 1;

    private WaveHudPayload() {
    }

    public static void update(HordeSyncPacket packet) {
        if (packet.phase() == 0) {
            clear();
            return;
        }
        if (packet.currentWave() != trackedWave || packet.phase() != 2) {
            trackedWave = packet.currentWave();
            peakMobsInWave = Math.max(1, packet.remainingMobs());
        } else if (packet.remainingMobs() > peakMobsInWave) {
            peakMobsInWave = packet.remainingMobs();
        }
        lastPacket = packet;
        lastUpdateTime = System.currentTimeMillis();
    }

    public static void clear() {
        lastPacket = null;
        lastUpdateTime = 0;
        trackedWave = -1;
        peakMobsInWave = 1;
    }

    public static int peakMobsInWave() {
        return Math.max(1, peakMobsInWave);
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
