package dev.smarthorde.client.hud;

import dev.smarthorde.config.SmartHordeConfig;
import dev.smarthorde.entity.HordeBoss;
import dev.smarthorde.network.HordeSyncPacket;
import dev.smarthorde.network.WaveHudPayload;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.LayeredDraw;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.entity.boss.wither.WitherBoss;
import net.minecraft.world.phys.AABB;

/**
 * 波次进度条渲染：
 * <ul>
 *   <li>支持波内击杀实时推进进度条与波次分段刻度线；</li>
 *   <li>动态感知附近活跃 BossBar 数量并自动下移 Y 坐标，避免与单个或多个领主血条文字重叠；</li>
 *   <li>半透明战术横幅衬底提升亮色天空/雪地背景下的可读性。</li>
 * </ul>
 */
public final class WaveHudOverlay implements LayeredDraw.Layer {

    public static final WaveHudOverlay INSTANCE = new WaveHudOverlay();

    private static final int BAR_WIDTH = 182;
    private static final int BAR_HEIGHT = 8;
    private static final int BASE_BAR_Y = 20;
    private static final int BOSS_BAR_STEP_Y = 19;

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
        int barY = computeDynamicBarY(minecraft, gui.guiHeight());

        int progressColor = switch (data.phase()) {
            case 1 -> 0xFFE8A33D; // COUNTDOWN
            case 2 -> 0xFFCC3333; // ACTIVE
            case 3 -> 0xFF66CC66; // BETWEEN_WAVES
            case 4 -> 0xFFD4AF37; // COMPLETE
            default -> 0xFFAAAAAA;
        };

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
            int textX = (gui.guiWidth() - textWidth) / 2;
            int textY = barY - 12;
            gui.fill(textX - 4, textY - 2, textX + textWidth + 4, textY + 9, 0x66080A0E);
            gui.drawString(font, line, textX, textY, 0xFFFFFF, true);
        }

        float waveProgress = computeOverallProgress(data);
        gui.fill(x - 1, barY - 1, x + BAR_WIDTH + 1, barY + BAR_HEIGHT + 1, 0xFF101014);
        gui.fill(x, barY, x + BAR_WIDTH, barY + BAR_HEIGHT, 0x95000000);

        int filledWidth = (int) (BAR_WIDTH * waveProgress);
        if (filledWidth > 0) {
            gui.fill(x, barY, x + filledWidth, barY + BAR_HEIGHT, progressColor);
            gui.fill(x, barY, x + filledWidth, barY + 1, 0x45FFFFFF);
        }

        int totalWaves = data.totalWaves();
        if (totalWaves > 1 && totalWaves <= 20) {
            for (int i = 1; i < totalWaves; i++) {
                int tickX = x + (BAR_WIDTH * i) / totalWaves;
                gui.fill(tickX, barY, tickX + 1, barY + BAR_HEIGHT, 0x80000000);
            }
        }
    }

    private static int computeDynamicBarY(Minecraft minecraft, int guiHeight) {
        if (minecraft.level == null || minecraft.player == null) {
            return BASE_BAR_Y;
        }
        AABB scan = minecraft.player.getBoundingBox().inflate(72.0);
        int activeBossBars = 0;
        if (SmartHordeConfig.isBossBarEnabledSafe()) {
            activeBossBars += minecraft.level.getEntitiesOfClass(HordeBoss.class, scan, HordeBoss::isAlive).size();
        }
        activeBossBars += minecraft.level.getEntitiesOfClass(WitherBoss.class, scan, WitherBoss::isAlive).size();
        activeBossBars += minecraft.level.getEntitiesOfClass(EnderDragon.class, scan, EnderDragon::isAlive).size();
        int maxBarsOnScreen = Math.max(0, (guiHeight / 3 - 12) / BOSS_BAR_STEP_Y + 1);
        int visibleBars = Math.min(activeBossBars, maxBarsOnScreen);
        return BASE_BAR_Y + visibleBars * BOSS_BAR_STEP_Y;
    }

    private static float computeOverallProgress(HordeSyncPacket data) {
        int total = data.totalWaves();
        if (total <= 0) {
            return 0.0F;
        }
        if (data.phase() == 4) {
            return 1.0F;
        }
        int completedWaves = Math.max(0, data.currentWave() - 1);
        float intraWave = switch (data.phase()) {
            case 1 -> 0.0F;
            case 2 -> 1.0F - Math.min(1.0F, (float) data.remainingMobs() / WaveHudPayload.peakMobsInWave());
            case 3 -> 1.0F;
            default -> 0.0F;
        };
        return Math.max(0.0F, Math.min(1.0F, (completedWaves + intraWave) / total));
    }
}
