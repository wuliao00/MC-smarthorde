package dev.smarthorde.horde;

import dev.smarthorde.horde.HordeWaveManager.WaveState;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * 纯逻辑单元测试（无需 Minecraft 引导）：仅访问嵌套枚举 WaveState，
 * 不会触发外围类 HordeWaveManager 的静态初始化。
 * 守护 HordeSyncPacket.phase 的网络编码契约（phase = WaveState.ordinal()，
 * 详见 HordeSyncPacket 类注释）——枚举常量一旦被重排，此测试立即失败，
 * 防止客户端 HUD 解码错乱。
 */
class WaveStateOrdinalContractTest {

    @Test
    void waveStateOrdinalsMustMatchNetworkContract() {
        assertEquals(0, WaveState.IDLE.ordinal(), "IDLE 必须保持序数 0（保留值，服务端不会发送）");
        assertEquals(1, WaveState.COUNTDOWN.ordinal(), "COUNTDOWN 必须保持序数 1");
        assertEquals(2, WaveState.ACTIVE.ordinal(), "ACTIVE 必须保持序数 2");
        assertEquals(3, WaveState.BETWEEN_WAVES.ordinal(), "BETWEEN_WAVES 必须保持序数 3");
        assertEquals(4, WaveState.COMPLETE.ordinal(), "COMPLETE 必须保持序数 4");
    }

    @Test
    void waveStateHasExactlyFiveStates() {
        // 新增/删除枚举常量都会改变编码契约，必须同步更新 HordeSyncPacket 注释与客户端 switch
        assertEquals(5, WaveState.values().length);
    }
}
