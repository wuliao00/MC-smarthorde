package dev.smarthorde.client.hud;

import dev.smarthorde.network.HordeSyncPacket;
import dev.smarthorde.network.WaveHudPayload;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.LayeredDraw;
import net.minecraft.network.chat.Component;

/**
 * 波次进度条渲染：绘制波次进度条 + 存活数 + 阶段文字，数据来自 HordeSyncPacket。
 */
public final class WaveHudOverlay implements LayeredDraw.Layer {

    public static final WaveHudOverlay INSTANCE = new WaveHudOverlay();

    private static final int BAR_WIDTH = 182;
    private static final int BAR_HEIGHT = 8;
    private static final int BAR_Y = 40;

    private WaveHudOverlay() {
    }

    @Override
    public void render(GuiGraphics gui, DeltaTracker deltaTracker) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.options.hideGui) {
            return;
        }
        HordeSyncPacket data = WaveHudPayload.current();
        if (data == null) {
            return;
        }
        Font font = minecraft.font;
        int x = (gui.guiWidth() - BAR_WIDTH) / 2;

        int progressColor = switch (data.phase()) {
            case 1 -> 0xFFE8A33D; // COUNTDOWN
            case 2 -> 0xFFCC3333; // ACTIVE
            case 3 -> 0xFF66CC66; // BETWEEN_WAVES
            case 4 -> 0xFFD4AF37; // COMPLETE
            default -> 0xFFAAAAAA;
        };

        // 阶段文字
        Component line = switch (data.phase()) {
            case 1 -> Component.translatable("hud.smarthorde.countdown", data.countdown());
            case 2 -> Component.translatable("hud.smarthorde.wave_line",
                    data.currentWave(), data.totalWaves(), data.remainingMobs());
            case 3 -> Component.translatable("hud.smarthorde.between", data.countdown());
            case 4 -> Component.translatable("hud.smarthorde.complete");
            default -> null;
        };
        if (line != null) {
            int textWidth = font.width(line);
            gui.drawString(font, line, (gui.guiWidth() - textWidth) / 2, BAR_Y - 12, 0xFFFFFF, true);
        }

        // 进度条外框 + 背景 + 进度
        float waveProgress = data.totalWaves() <= 0 ? 0
                : Math.min(1.0F, (float) data.currentWave() / data.totalWaves());
        gui.fill(x - 1, BAR_Y - 1, x + BAR_WIDTH + 1, BAR_Y + BAR_HEIGHT + 1, 0xFF000000);
        gui.fill(x, BAR_Y, x + BAR_WIDTH, BAR_Y + BAR_HEIGHT, 0x90000000);
        gui.fill(x, BAR_Y, x + (int) (BAR_WIDTH * waveProgress), BAR_Y + BAR_HEIGHT, progressColor);
    }
}
