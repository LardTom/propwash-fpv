package de.lardtom.propwash.content.client.paint;

import de.lardtom.propwash.build.PaintSwatch;
import de.lardtom.propwash.ui.client.theme.Palette;
import net.minecraft.client.gui.GuiGraphicsExtractor;

public final class PaintSwatches {
    private PaintSwatches() {
    }

    public static int opaque(int rgb) {
        return 0xFF000000 | (rgb & 0xFFFFFF);
    }

    public static int shade(int rgb, float factor) {
        int r = Math.min(255, Math.round(((rgb >> 16) & 0xFF) * factor));
        int g = Math.min(255, Math.round(((rgb >> 8) & 0xFF) * factor));
        int b = Math.min(255, Math.round((rgb & 0xFF) * factor));
        return 0xFF000000 | (r << 16) | (g << 8) | b;
    }

    public static void chip(GuiGraphicsExtractor g, int x0, int y0, int x1, int y1, int rgb, boolean finish, boolean selected, boolean hovered) {
        g.fill(x0, y0, x1, y1, Palette.SHADOW);
        int ix0 = x0 + 1;
        int iy0 = y0 + 1;
        int ix1 = x1 - 1;
        int iy1 = y1 - 1;
        g.fill(ix0, iy0, ix1, iy1, opaque(rgb));
        PaintSwatch swatch = PaintSwatch.byColor(rgb).orElse(null);
        if (finish && swatch == PaintSwatch.CARBON) {
            int weave = shade(rgb, 1.45F);
            for (int y = iy0; y < iy1; y++) {
                for (int x = ix0 + ((y - iy0) / 2 % 2) * 2; x < ix1; x += 4) {
                    g.fill(x, y, Math.min(ix1, x + 2), y + 1, weave);
                }
            }
        } else if (finish) {
            g.fill(ix0, iy0, ix1, iy0 + Math.max(1, (iy1 - iy0) / 3), shade(rgb, 1.25F));
            g.fill(ix0, iy1 - 1, ix1, iy1, shade(rgb, 0.7F));
        } else {
            g.fill(ix0, iy0, ix1, iy0 + 1, shade(rgb, 1.2F));
        }
        if (selected) {
            g.outline(x0 - 1, y0 - 1, x1 - x0 + 2, y1 - y0 + 2, Palette.ACCENT);
            g.outline(x0, y0, x1 - x0, y1 - y0, Palette.SHADOW);
        } else if (hovered) {
            g.outline(x0 - 1, y0 - 1, x1 - x0 + 2, y1 - y0 + 2, Palette.HOVER_OUTLINE);
        }
    }

    public static void standard(GuiGraphicsExtractor g, int x0, int y0, int x1, int y1, int rgb) {
        g.fill(x0, y0, x1, y1, Palette.SHADOW);
        g.fill(x0 + 1, y0 + 1, x1 - 1, y1 - 1, opaque(rgb));
        for (int i = 0; i < Math.min(x1 - x0, y1 - y0) - 2; i++) {
            g.fill(x1 - 2 - i, y0 + 1 + i, x1 - 1 - i, y0 + 2 + i, Palette.withAlpha(Palette.TEXT, 0x90));
        }
    }
}
