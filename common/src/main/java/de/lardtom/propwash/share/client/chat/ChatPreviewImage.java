package de.lardtom.propwash.share.client.chat;

import de.lardtom.propwash.api.build.DroneData;
import de.lardtom.propwash.api.client.PropwashClientApi;
import de.lardtom.propwash.ui.client.theme.ThemeDraw;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent;

public record ChatPreviewImage(DroneData drone) implements ClientTooltipComponent {
    public static final int WIDTH = 112;
    public static final int HEIGHT = 64;
    private static final int GAP = 3;
    private static final long TURN_MILLIS = 14000L;

    @Override
    public int getHeight(Font font) {
        return HEIGHT + GAP;
    }

    @Override
    public int getWidth(Font font) {
        return WIDTH;
    }

    @Override
    public void extractImage(Font font, int x, int y, int w, int h, GuiGraphicsExtractor graphics) {
        int x0 = x + Math.max(0, (w - WIDTH) / 2);
        ThemeDraw.previewStage(graphics, x0, y + 1, x0 + WIDTH, y + 1 + HEIGHT);
        float yaw = (float) ((System.currentTimeMillis() % TURN_MILLIS) / (double) TURN_MILLIS * 360.0);
        PropwashClientApi.renderPreview(graphics, this.drone, x0, y + 1, x0 + WIDTH, y + 1 + HEIGHT, yaw);
    }
}
