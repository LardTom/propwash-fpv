package de.lardtom.propwash.guide.client.screen;

import de.lardtom.propwash.ui.client.theme.Palette;
import de.lardtom.propwash.ui.client.theme.ThemeDraw;
import de.lardtom.propwash.ui.client.theme.ThemeText;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;

public final class GuideDraw {
    public static final int KEYCAP_PADDING = 3;
    public static final int MOUSE_WIDTH = 7;
    public static final int MOUSE_HEIGHT = 10;
    private static final int MAX_KEY_CHARS = 14;

    public enum Icon {
        PLAY,
        PAUSE,
        PREVIOUS,
        NEXT,
        REPLAY,
        CLOSE,
        RESET_VIEW
    }

    private GuideDraw() {
    }

    public static int fade(int color, float alpha) {
        int a = Math.round(Palette.alpha(color) * Math.clamp(alpha, 0.0F, 1.0F));
        return Palette.withAlpha(color, a);
    }

    public static String shortKeyName(String name) {
        String trimmed = name == null ? "" : name.trim();
        if (trimmed.length() <= MAX_KEY_CHARS) {
            return trimmed;
        }
        return trimmed.substring(0, MAX_KEY_CHARS - 1) + "…";
    }

    public static int keycapWidth(Font font, String key) {
        return Math.max(9, font.width(key) + 2 * KEYCAP_PADDING);
    }

    public static int keycap(GuiGraphicsExtractor g, Font font, String key, int x, int y, int height, int alpha) {
        float a = alpha / 255.0F;
        int width = keycapWidth(font, key);
        ThemeDraw.framedBox(g, x, y, x + width, y + height, fade(Palette.BUTTON, a), fade(Palette.BORDER_STRONG, a));
        g.fill(x + 1, y + height - 2, x + width - 1, y + height - 1, fade(Palette.SHADOW, a));
        g.fill(x + 1, y + 1, x + width - 1, y + 2, fade(Palette.withAlpha(0xFFFFFF, 0x22), a));
        ThemeText.text(g, font, key, x + (width - font.width(key)) / 2, y + (height - 8) / 2, fade(Palette.TEXT, a));
        return width;
    }

    public static void mouse(GuiGraphicsExtractor g, int x, int y, int button, int alpha) {
        float a = alpha / 255.0F;
        int outline = fade(Palette.TEXT_MUTED, a);
        int body = fade(Palette.BUTTON, a);
        int active = fade(Palette.ACCENT, a);
        int w = MOUSE_WIDTH;
        int h = MOUSE_HEIGHT;
        ThemeDraw.framedBox(g, x, y, x + w, y + h, body, outline);
        int split = y + 4;
        g.fill(x + 1, split, x + w - 1, split + 1, outline);
        g.fill(x + w / 2, y + 1, x + w / 2 + 1, split, outline);
        if (button == 1) {
            g.fill(x + 1, y + 1, x + w / 2, split, active);
        } else if (button == 3) {
            g.fill(x + w / 2 + 1, y + 1, x + w - 1, split, active);
        } else if (button == 2) {
            g.fill(x + w / 2, y + 1, x + w / 2 + 1, split, active);
        }
    }

    public static void line(GuiGraphicsExtractor g, float x0, float y0, float x1, float y1, int color) {
        float dx = x1 - x0;
        float dy = y1 - y0;
        float length = (float) Math.sqrt(dx * dx + dy * dy);
        if (length < 0.5F) {
            return;
        }
        g.pose().pushMatrix();
        g.pose().translate(x0, y0);
        g.pose().rotate((float) Math.atan2(dy, dx));
        g.pose().translate(0.0F, -0.5F);
        g.fill(0, 0, Math.round(length), 1, color);
        g.pose().popMatrix();
    }

    public static void dot(GuiGraphicsExtractor g, float x, float y, int color, int border) {
        int cx = Math.round(x);
        int cy = Math.round(y);
        g.fill(cx - 2, cy - 2, cx + 3, cy + 3, border);
        g.fill(cx - 1, cy - 1, cx + 2, cy + 2, color);
    }

    public static void icon(GuiGraphicsExtractor g, Icon icon, int cx, int cy, int color) {
        switch (icon) {
            case PLAY -> triangleRight(g, cx - 2, cy - 4, color);
            case PAUSE -> {
                g.fill(cx - 3, cy - 4, cx - 1, cy + 4, color);
                g.fill(cx + 1, cy - 4, cx + 3, cy + 4, color);
            }
            case PREVIOUS -> {
                g.fill(cx - 4, cy - 4, cx - 3, cy + 4, color);
                triangleLeft(g, cx - 2, cy - 4, color);
            }
            case NEXT -> {
                triangleRight(g, cx - 2, cy - 4, color);
                g.fill(cx + 3, cy - 4, cx + 4, cy + 4, color);
            }
            case REPLAY -> {
                g.fill(cx - 3, cy - 4, cx + 1, cy - 3, color);
                g.fill(cx - 4, cy - 3, cx - 3, cy + 3, color);
                g.fill(cx - 3, cy + 3, cx + 3, cy + 4, color);
                g.fill(cx + 3, cy - 1, cx + 4, cy + 3, color);
                g.fill(cx + 1, cy - 6, cx + 2, cy - 1, color);
                g.fill(cx + 2, cy - 5, cx + 3, cy - 2, color);
                g.fill(cx + 3, cy - 4, cx + 4, cy - 3, color);
            }
            case CLOSE -> {
                for (int i = 0; i < 7; i++) {
                    g.fill(cx - 3 + i, cy - 3 + i, cx - 2 + i, cy - 2 + i, color);
                    g.fill(cx + 3 - i, cy - 3 + i, cx + 4 - i, cy - 2 + i, color);
                }
            }
            case RESET_VIEW -> {
                g.fill(cx - 2, cy - 4, cx + 3, cy - 3, color);
                g.fill(cx - 2, cy + 4, cx + 3, cy + 5, color);
                g.fill(cx - 4, cy - 2, cx - 3, cy + 3, color);
                g.fill(cx + 4, cy - 2, cx + 5, cy + 3, color);
                g.fill(cx - 3, cy - 3, cx - 2, cy - 2, color);
                g.fill(cx + 3, cy - 3, cx + 4, cy - 2, color);
                g.fill(cx - 3, cy + 3, cx - 2, cy + 4, color);
                g.fill(cx + 3, cy + 3, cx + 4, cy + 4, color);
                g.fill(cx, cy, cx + 1, cy + 1, color);
            }
        }
    }

    private static void triangleRight(GuiGraphicsExtractor g, int x, int y, int color) {
        for (int column = 0; column < 4; column++) {
            g.fill(x + column, y + column, x + column + 1, y + 8 - column, color);
        }
    }

    private static void triangleLeft(GuiGraphicsExtractor g, int x, int y, int color) {
        for (int column = 0; column < 4; column++) {
            g.fill(x + 3 - column, y + column, x + 4 - column, y + 8 - column, color);
        }
    }
}
