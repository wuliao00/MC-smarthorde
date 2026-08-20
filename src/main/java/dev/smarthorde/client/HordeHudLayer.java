package dev.smarthorde.client;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.LayeredDraw;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

/**
 * 尸潮 HUD 叠加层（轮8）。
 * 屏幕顶部居中：波次进度条 + 存活数 + 阶段 + 倒计时。
 */
public class HordeHudLayer implements LayeredDraw.Layer {

    private static final int PANEL_W = 140;
    private static final int PANEL_H = 52;
    private static final int BAR_H   = 6;
    private static final int PAD     = 4;

    private static final int BG          = 0x90_00_00_00;
    private static final int TITLE_COLOR = 0xFF_FF_55_55;
    private static final int BAR_BG      = 0xFF_33_33_33;
    private static final int BAR_FG      = 0xFF_55_FF_55;
    private static final int BAR_LOW     = 0xFF_FF_55_55;
    private static final int COUNT_COLOR = 0xFF_FF_FF_55;

    @Override
    public void render(GuiGraphics gui, DeltaTracker delta) {
        if (!ClientHordeData.active) return;

        Minecraft mc = Minecraft.getInstance();
        Font font = mc.font;
        int sw = gui.guiWidth();

        int x0 = (sw - PANEL_W) / 2;
        int y0 = 8;

        gui.fill(x0, y0, x0 + PANEL_W, y0 + PANEL_H, BG);

        MutableComponent title = Component.literal("WAVE ")
                .append(Component.literal(String.valueOf(ClientHordeData.currentWave)))
                .append(" / ")
                .append(Component.literal(String.valueOf(ClientHordeData.totalWaves)));
        int titleW = font.width(title);
        gui.drawString(font, title, x0 + (PANEL_W - titleW) / 2, y0 + PAD, TITLE_COLOR, false);

        int barX = x0 + PAD;
        int barY = y0 + PAD + 12;
        int barW = PANEL_W - PAD * 2;
        gui.fill(barX, barY, barX + barW, barY + BAR_H, BAR_BG);

        int size = Math.max(ClientHordeData.waveSize, 1);
        float ratio = (float) ClientHordeData.aliveCount / size;
        int fillW = (int) (barW * Math.max(ratio, 0));
        int barColor = ratio > 0.35f ? BAR_FG : BAR_LOW;
        if (fillW > 0) {
            gui.fill(barX, barY, barX + fillW, barY + BAR_H, barColor);
        }

        String aliveText = ClientHordeData.aliveCount + " / " + size;
        int aliveW = font.width(aliveText);
        gui.drawString(font, aliveText, x0 + (PANEL_W - aliveW) / 2, barY + BAR_H + 2, 0xFF_FF_FF_FF, false);

        String info;
        int infoColor;
        switch (ClientHordeData.phase) {
            case 0 -> { info = "下一波: " + ClientHordeData.countdownSec + "s"; infoColor = COUNT_COLOR; }
            case 1 -> { info = "⚔ 战斗中"; infoColor = TITLE_COLOR; }
            case 2 -> { info = "✔ 波次清除！"; infoColor = BAR_FG; }
            default -> { info = "🏆 全部完成！"; infoColor = 0xFF_FF_D7_00; }
        }
        int infoW = font.width(info);
        gui.drawString(font, info, x0 + (PANEL_W - infoW) / 2, y0 + PANEL_H - 12, infoColor, false);
    }
}
