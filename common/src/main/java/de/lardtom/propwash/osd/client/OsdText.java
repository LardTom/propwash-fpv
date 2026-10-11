package de.lardtom.propwash.osd.client;

import de.lardtom.propwash.osd.OsdColors;
import de.lardtom.propwash.osd.OsdConfig;
import de.lardtom.propwash.osd.OsdGrid;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;

public final class OsdText {
    private static final OsdDraw DRAW = new OsdDraw();

    private OsdText() {
    }

    public static float scale(GuiGraphicsExtractor graphics, float factor) {
        int guiScale = Minecraft.getInstance().getWindow().getGuiScale();
        OsdGrid grid = OsdHud.grid(graphics.guiWidth(), graphics.guiHeight());
        return OsdGrid.snapToPixels(grid.textScale(OsdHud.config().scale) * factor, guiScale);
    }

    public static float width(Font font, String text, float scale) {
        return OsdDraw.width(font, text, scale);
    }

    public static float height(float scale) {
        return OsdDraw.GLYPH_HEIGHT * scale;
    }

    public static int color() {
        return OsdColors.withAlpha(OsdHud.config().primaryRgb(), 1.0F);
    }

    public static int warningColor() {
        return OsdColors.withAlpha(OsdHud.config().warningRgb(), 1.0F);
    }

    public static void draw(GuiGraphicsExtractor graphics, Font font, String text, float x, float y, float scale, int argb) {
        OsdConfig config = OsdHud.config();
        DRAW.begin(graphics, font, config.outline, OsdConfig.Background.OFF, Minecraft.getInstance().getWindow().getGuiScale());
        try {
            DRAW.text(text, x, y, scale, argb);
        } finally {
            DRAW.end();
        }
    }

    public static void drawCentered(GuiGraphicsExtractor graphics, Font font, String text, float centerX, float y, float scale, int argb) {
        draw(graphics, font, text, centerX - width(font, text, scale) / 2.0F, y, scale, argb);
    }
}
