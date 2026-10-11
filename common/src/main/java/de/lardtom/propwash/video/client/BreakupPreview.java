package de.lardtom.propwash.video.client;

import de.lardtom.propwash.build.VideoSystem;
import de.lardtom.propwash.ui.TextFit;
import de.lardtom.propwash.ui.client.theme.Metrics;
import de.lardtom.propwash.ui.client.theme.Palette;
import de.lardtom.propwash.ui.client.theme.ThemeText;
import de.lardtom.propwash.video.ImageArea;
import de.lardtom.propwash.video.ImageFormat;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.util.ARGB;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Util;

import java.util.ArrayList;
import java.util.List;
import java.util.function.IntPredicate;

final class BreakupPreview {
    private static final int TILES = 5;
    private static final int GAP = 6;
    private static final int ROW_GAP = 8;
    private static final int[] COLUMN_CHOICES = {TILES, 3, 2};
    private static final int MAX_TILE_WIDTH = 150;
    private static final float HORIZON = 0.58F;
    private static final int SKY_TOP = 0xFF5E9BD6;
    private static final int SKY_BOTTOM = 0xFFB7D6EF;
    private static final int GRASS_TOP = 0xFF6A9A3E;
    private static final int GRASS_BOTTOM = 0xFF3F6424;
    private static final int GATE = 0xFF3FD35C;
    private static final int LEAVES = 0xFF2F5A22;
    private static final int TRUNK = 0xFF5B3D22;
    private static final String[] ANALOG = {"clean", "glitches", "colour_loss", "snow", "static"};
    private static final String[] DIGITAL = {"clean", "soft", "blocky", "freezes", "lost"};

    private BreakupPreview() {
    }

    static int extract(GuiGraphicsExtractor g, Font font, VideoSystem system, float strength, ImageFormat format, int x, int y, int width) {
        boolean digital = system == VideoSystem.DIGITAL;
        String[] names = digital ? DIGITAL : ANALOG;
        List<Component> labels = new ArrayList<>();
        for (int i = 0; i < TILES; i++) {
            labels.add(Component.translatable("propwash.video.config.preview.stage", i + 1, Component.translatable("propwash.video.config.preview." + names[i])));
        }
        int noVideo = digital ? ThemeText.smallWidth(font, Component.translatable("propwash.video.no_video").getString()) : 0;
        int columns = columns(width, tile -> fits(font, labels, noVideo, format, tile));
        int tileWidth = tileWidth(width, columns);
        int tileHeight = tileHeight(tileWidth);
        float s = Math.clamp(strength, 0.0F, 1.0F);
        long frame = Util.getMillis() / 80L;
        int top = y;
        for (int first = 0; first < TILES; first += columns) {
            int last = Math.min(TILES, first + columns);
            int labelLines = 1;
            for (int i = first; i < last; i++) {
                int x0 = x + (i - first) * (tileWidth + GAP);
                int x1 = x0 + tileWidth;
                int y1 = top + tileHeight;
                g.fill(x0 - 1, top - 1, x1 + 1, y1 + 1, Palette.BORDER_STRONG);
                ImageArea area = ImageArea.of(format, tileWidth, tileHeight);
                if (!area.full()) {
                    g.fill(x0, top, x1, y1, 0xFF000000);
                }
                int ix0 = x0 + area.x();
                int iy0 = top + area.y();
                int ix1 = ix0 + area.width();
                int iy1 = iy0 + area.height();
                g.enableScissor(ix0, iy0, ix1, iy1);
                try {
                    if (digital) {
                        digital(g, font, i, s, ix0, iy0, ix1, iy1, frame);
                    } else {
                        analog(g, i, s, ix0, iy0, ix1, iy1, frame);
                    }
                } finally {
                    g.disableScissor();
                }
                List<FormattedCharSequence> lines = font.split(labels.get(i), Math.max(1, tileWidth));
                labelLines = Math.max(labelLines, lines.size());
                for (int line = 0; line < lines.size(); line++) {
                    ThemeText.text(g, font, lines.get(line), x0 + (tileWidth - font.width(lines.get(line))) / 2, y1 + 4 + line * Metrics.LINE_HEIGHT,
                            Palette.TEXT_MUTED);
                }
            }
            top += tileHeight + 4 + labelLines * Metrics.LINE_HEIGHT + (last < TILES ? ROW_GAP : 0);
        }
        return top;
    }

    private static boolean fits(Font font, List<Component> labels, int noVideo, ImageFormat format, int tileWidth) {
        for (Component label : labels) {
            if (ThemeText.breaksWord(font, label, tileWidth)) {
                return false;
            }
        }
        int image = ImageArea.of(format, tileWidth, tileHeight(tileWidth)).width();
        return noVideo == 0 || TextFit.mode(noVideo, noVideoRoom(image)) != TextFit.Mode.CUT;
    }

    static int columns(int width, IntPredicate fits) {
        for (int columns : COLUMN_CHOICES) {
            if (fits.test(tileWidth(width, columns))) {
                return columns;
            }
        }
        return 1;
    }

    static int tileWidth(int width, int columns) {
        return Math.max(1, Math.min(MAX_TILE_WIDTH, (width - (columns - 1) * GAP) / columns));
    }

    static int tileHeight(int tileWidth) {
        return Math.max(20, tileWidth * 9 / 16);
    }

    static int noVideoRoom(int imageWidth) {
        return Math.max(4, imageWidth - 4);
    }

    private static void analog(GuiGraphicsExtractor g, int stage, float s, int x0, int y0, int x1, int y1, long frame) {
        int w = x1 - x0;
        int h = y1 - y0;
        float look = 0.35F + 0.65F * s;
        switch (stage) {
            case 0 -> scene(g, x0, y0, x1, y1, false);
            case 1 -> {
                scene(g, x0, y0, x1, y1, false);
                int torn = y0 + (int) (hash(frame, 1) * h);
                int shift = Math.round((hash(frame, 2) - 0.5F) * w * 0.2F * look);
                g.fill(x0 + Math.max(0, shift), torn, x1 + Math.min(0, shift), torn + 1, ARGB.color(Math.round(200 * look), 235, 235, 235));
                for (int i = 0; i < 6; i++) {
                    int sx = x0 + (int) (hash(frame, 10 + i) * w);
                    int sy = y0 + (int) (hash(frame, 20 + i) * h);
                    g.fill(sx, sy, sx + 2 + (int) (hash(frame, 30 + i) * 4), sy + 1, ARGB.color(Math.round(220 * look), 250, 250, 250));
                }
            }
            case 2 -> {
                scene(g, x0, y0, x1, y1, true);
                snow(g, x0, y0, x1, y1, 0.16F * look, frame);
            }
            case 3 -> {
                scene(g, x0, y0, x1, y1, true);
                snow(g, x0, y0, x1, y1, 0.55F * look, frame);
                int band0 = y0 + h / 3;
                int band1 = y0 + h * 2 / 3;
                for (int y = band0; y < band1; y++) {
                    int blank = Math.round(w * 0.08F * look * (0.6F + 0.8F * hash(frame + y, 3)));
                    g.fill(x0, y, x0 + blank, y + 1, 0xE0383838);
                    g.fill(x0 + blank, y, x0 + blank + Math.max(1, blank / 3), y + 1, 0x60999999);
                }
            }
            default -> {
                g.fill(x0, y0, x1, y1, 0xFF7A7A7A);
                snow(g, x0, y0, x1, y1, 1.0F, frame);
            }
        }
        for (int y = y0; y < y1; y += 2) {
            g.fill(x0, y, x1, y + 1, 0x14000000);
        }
    }

    private static void digital(GuiGraphicsExtractor g, Font font, int stage, float s, int x0, int y0, int x1, int y1, long frame) {
        int w = x1 - x0;
        int h = y1 - y0;
        switch (stage) {
            case 0 -> scene(g, x0, y0, x1, y1, false);
            case 1 -> {
                pixelated(g, x0, y0, x1, y1, 2, 0.0F, frame);
                g.fill(x0, y0, x1, y1, 0x14FFFFFF);
            }
            case 2 -> pixelated(g, x0, y0, x1, y1, Math.max(3, w / 22), 0.35F * (0.4F + 0.6F * s), frame);
            case 3 -> {
                pixelated(g, x0, y0, x1, y1, Math.max(4, w / 16), 0.6F * (0.4F + 0.6F * s), frame / 6L);
                int stale = y0 + h / 5;
                g.fill(x0, stale, x1, stale + h / 4, 0x30000000);
            }
            default -> {
                g.fill(x0, y0, x1, y1, 0xFF050505);
                String text = Component.translatable("propwash.video.no_video").getString();
                int room = noVideoRoom(w);
                int shown = Math.min(ThemeText.smallWidth(font, text), room);
                ThemeText.smallFitted(g, font, text, x0 + (w - shown) / 2, y0 + (h - ThemeText.smallLineHeight()) / 2, room, 0xFFE6E6E6);
            }
        }
    }

    private static void scene(GuiGraphicsExtractor g, int x0, int y0, int x1, int y1, boolean grey) {
        int w = x1 - x0;
        int h = y1 - y0;
        int horizon = y0 + Math.round(h * HORIZON);
        g.fillGradient(x0, y0, x1, horizon, tone(SKY_TOP, grey), tone(SKY_BOTTOM, grey));
        g.fillGradient(x0, horizon, x1, y1, tone(GRASS_TOP, grey), tone(GRASS_BOTTOM, grey));
        g.fill(x0 + Math.round(w * 0.17F), y0 + Math.round(h * 0.47F), x0 + Math.round(w * 0.2F), horizon, tone(TRUNK, grey));
        g.fill(x0 + Math.round(w * 0.12F), y0 + Math.round(h * 0.3F), x0 + Math.round(w * 0.25F), y0 + Math.round(h * 0.5F), tone(LEAVES, grey));
        int gx0 = x0 + Math.round(w * 0.56F);
        int gx1 = x0 + Math.round(w * 0.84F);
        int gy0 = y0 + Math.round(h * 0.28F);
        int gy1 = y0 + Math.round(h * 0.74F);
        int post = Math.max(2, w / 40);
        int gate = tone(GATE, grey);
        g.fill(gx0, gy0, gx0 + post, gy1, gate);
        g.fill(gx1 - post, gy0, gx1, gy1, gate);
        g.fill(gx0, gy0, gx1, gy0 + post, gate);
    }

    private static void pixelated(GuiGraphicsExtractor g, int x0, int y0, int x1, int y1, int size, float breakup, long frame) {
        int w = x1 - x0;
        int h = y1 - y0;
        int columns = (w + size - 1) / size;
        int rows = (h + size - 1) / size;
        for (int row = 0; row < rows; row++) {
            boolean smearRow = breakup > 0.0F && hash(frame * 31L + row / 2, 5) < breakup * 0.45F;
            int drag = smearRow ? 1 + (int) (hash(frame, 7 + row) * 3) : 0;
            for (int col = 0; col < columns; col++) {
                int region = col / 5;
                boolean frozen = breakup > 0.0F && hash(frame * 17L + region * 7L + row / 2, 9) < breakup * 0.3F;
                float fx = (col + 0.5F) / columns;
                float fy = (row + 0.5F - (smearRow ? drag : 0) - (frozen ? 1.5F : 0.0F)) / rows;
                int color = sceneColor(Math.clamp(fx, 0.0F, 1.0F), Math.clamp(fy, 0.0F, 1.0F));
                int bx0 = x0 + col * size;
                int by0 = y0 + row * size;
                g.fill(bx0, by0, Math.min(x1, bx0 + size), Math.min(y1, by0 + size), color);
            }
        }
    }

    private static int sceneColor(float fx, float fy) {
        if (fx >= 0.56F && fx <= 0.84F && fy >= 0.28F && fy <= 0.74F) {
            boolean post = fx <= 0.585F || fx >= 0.815F;
            boolean beam = fy <= 0.32F;
            if (post || beam) {
                return GATE;
            }
        }
        if (fx >= 0.12F && fx <= 0.25F && fy >= 0.3F && fy <= 0.5F) {
            return LEAVES;
        }
        if (fx >= 0.17F && fx <= 0.2F && fy > 0.5F && fy < HORIZON) {
            return TRUNK;
        }
        if (fy < HORIZON) {
            return ARGB.srgbLerp(fy / HORIZON, SKY_TOP, SKY_BOTTOM);
        }
        return ARGB.srgbLerp((fy - HORIZON) / (1.0F - HORIZON), GRASS_TOP, GRASS_BOTTOM);
    }

    private static void snow(GuiGraphicsExtractor g, int x0, int y0, int x1, int y1, float amount, long frame) {
        if (amount <= 0.0F || !VideoOverlay.ensureNoiseTexture()) {
            return;
        }
        int w = x1 - x0;
        int h = y1 - y0;
        float u = (frame * 97L) % VideoOverlay.NOISE_SIZE;
        float v = (frame * 61L) % VideoOverlay.NOISE_SIZE;
        int alpha = Math.round(Math.clamp(amount, 0.0F, 1.0F) * 255.0F);
        g.blit(RenderPipelines.GUI_TEXTURED, VideoOverlay.NOISE_TEXTURE, x0, y0, u, v, w, h, w, h, VideoOverlay.NOISE_SIZE,
                VideoOverlay.NOISE_SIZE, ARGB.color(alpha, 255, 255, 255));
    }

    private static int tone(int argb, boolean grey) {
        if (!grey) {
            return argb;
        }
        int luma = Math.round(ARGB.red(argb) * 0.299F + ARGB.green(argb) * 0.587F + ARGB.blue(argb) * 0.114F);
        return ARGB.color(ARGB.alpha(argb), luma, luma, luma);
    }

    private static float hash(long a, int salt) {
        long h = a * 0x9E3779B97F4A7C15L + salt * 0xC2B2AE3D27D4EB4FL;
        h ^= h >>> 31;
        h *= 0xBF58476D1CE4E5B9L;
        h ^= h >>> 29;
        return (h >>> 40) * 0x1.0p-24F;
    }
}
