package dev.smarthorde.client;

/**
 * 客户端波次数据持有者（轮8）。
 * 纯静态 volatile 字段，由网络包写入，由 HUD 层读取。
 */
public final class ClientHordeData {

    private ClientHordeData() {}

    public static volatile boolean active       = false;
    public static volatile int     currentWave  = 0;
    public static volatile int     totalWaves   = 0;
    public static volatile int     aliveCount   = 0;
    public static volatile int     waveSize     = 0;
    public static volatile int     phase        = 0;
    public static volatile int     countdownSec = 0;

    public static void update(boolean active, int currentWave, int totalWaves,
                              int aliveCount, int waveSize, int phase, int countdownSec) {
        ClientHordeData.active       = active;
        ClientHordeData.currentWave  = currentWave;
        ClientHordeData.totalWaves   = totalWaves;
        ClientHordeData.aliveCount   = aliveCount;
        ClientHordeData.waveSize     = waveSize;
        ClientHordeData.phase        = phase;
        ClientHordeData.countdownSec = countdownSec;
    }

    public static void reset() {
        active       = false;
        currentWave  = 0;
        totalWaves   = 0;
        aliveCount   = 0;
        waveSize     = 0;
        phase        = 0;
        countdownSec = 0;
    }
}
