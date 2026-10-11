package de.lardtom.propwash.video.client;

import com.mojang.blaze3d.platform.NativeImage;
import de.lardtom.propwash.Constants;
import de.lardtom.propwash.build.VideoSystem;
import de.lardtom.propwash.osd.client.OsdText;
import de.lardtom.propwash.video.EffectParams;
import de.lardtom.propwash.video.FeedLook;
import de.lardtom.propwash.video.ImageArea;
import de.lardtom.propwash.osd.client.OsdHud;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ARGB;
import net.minecraft.util.Util;
import org.joml.Matrix3x2fStack;
import org.jspecify.annotations.Nullable;

import java.util.Locale;

public final class VideoOverlay {
    private static final int MAX_CLUSTERS = 14;
    static final Identifier NOISE_TEXTURE = Constants.id("video/snow");
    static final int NOISE_SIZE = 512;
    private static final float NOISE_TINT = 0.12F;
    private static final float SNOW_CELL_PX_AT_1080 = 1.5F;
    private static final Xorshift FRAME_RANDOM = new Xorshift();
    private static final Xorshift BLOCK_RANDOM = new Xorshift();
    private static final int BAR_COLOR = 0xFF000000;
    private static long barsDrawn;
    private static boolean noiseReady;
    private static boolean noiseFailed;

    private VideoOverlay() {
    }

    static void extract(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker) {
        GogglesBlackout.Mismatch mismatch = GogglesBlackout.current();
        if (mismatch != null) {
            noVideo(graphics, mismatch);
            return;
        }
        VideoState state = VideoState.INSTANCE;
        ImageArea area = FpvImage.area();
        if (state.active()) {
            EffectParams params = state.params();
            int width = graphics.guiWidth();
            int height = graphics.guiHeight();
            HeadlightOverlay.extract(graphics, width, height);
            if (VideoPass.INSTANCE.overlayFallback()) {
                FRAME_RANDOM.seed(params.seed, 0x5DEECE66DL);
                BLOCK_RANDOM.seed(params.blockSeed, 0x2545F4914F6CDD1DL);
                if (area.full()) {
                    approximate(graphics, params, width, height);
                } else {
                    approximateIn(graphics, params, area);
                }
            }
        }
        if (!area.full()) {
            bars(graphics, area);
        }
    }

    private static void approximateIn(GuiGraphicsExtractor graphics, EffectParams params, ImageArea area) {
        float scale = FpvImage.guiScale();
        Matrix3x2fStack pose = graphics.pose();
        pose.pushMatrix();
        try {
            pose.translate(area.x() / scale, area.y() / scale);
            approximate(graphics, params, (int) Math.ceil(area.width() / scale), (int) Math.ceil(area.height() / scale));
        } finally {
            pose.popMatrix();
        }
    }

    private static void noVideo(GuiGraphicsExtractor graphics, GogglesBlackout.Mismatch mismatch) {
        int width = graphics.guiWidth();
        int height = graphics.guiHeight();
        Font font = Minecraft.getInstance().font;
        float scale = OsdText.scale(graphics, 1.0F);
        float line = OsdText.height(scale) + 3.0F * scale;
        float middle = height / 2.0F;
        if (mismatch.goggles() == VideoSystem.ANALOG) {
            VideoConfig config = VideoSettings.get();
            graphics.fill(0, 0, width, height, ARGB.color(255, 60, 60, 60));
            FRAME_RANDOM.seed((float) Math.floor(Util.getMillis() / (config.reduceFlicker ? 33.0 : 16.0)), 0x5DEECE66DL);
            snow(graphics, 1.0F, config.reduceFlicker ? 0.65F : 1.0F, 0.9F, width, height);
            graphics.fill(0, Math.round(middle - line * 3.0F), width, Math.round(middle + line * 3.25F), ARGB.color(190, 0, 0, 0));
        } else {
            graphics.fill(0, 0, width, height, ARGB.color(255, 0, 0, 0));
        }
        String headline = Component.translatable("propwash.video.no_signal").getString();
        String systems = Component.translatable("propwash.video.mismatch", systemName(mismatch.goggles()), systemName(mismatch.video())).getString();
        String hint = Component.translatable("propwash.video.mismatch.hint").getString();
        OsdText.drawCentered(graphics, font, headline, width / 2.0F, middle - line * 2.0F, fitted(font, headline, OsdText.scale(graphics, 2.0F), width),
                OsdText.color());
        OsdText.drawCentered(graphics, font, systems, width / 2.0F, middle + line * 0.5F, fitted(font, systems, scale, width), OsdText.color());
        OsdText.drawCentered(graphics, font, hint, width / 2.0F, middle + line * 1.75F, fitted(font, hint, scale, width), OsdText.color());
        GogglesBlackout.drawn();
    }

    private static float fitted(Font font, String text, float scale, int width) {
        float needed = OsdText.width(font, text, scale);
        float room = width * 0.94F;
        return needed <= room || needed <= 0.0F ? scale : scale * room / needed;
    }

    private static Component systemName(VideoSystem system) {
        return Component.translatable("propwash.video.system." + system.name().toLowerCase(Locale.ROOT));
    }

    static void bars(GuiGraphicsExtractor graphics, ImageArea area) {
        float scale = FpvImage.guiScale();
        Matrix3x2fStack pose = graphics.pose();
        pose.pushMatrix();
        try {
            pose.scale(1.0F / scale, 1.0F / scale);
            int w = area.screenWidth();
            int h = area.screenHeight();
            if (area.pillarboxed()) {
                graphics.fill(0, 0, area.x(), h, BAR_COLOR);
                graphics.fill(area.right(), 0, w, h, BAR_COLOR);
            }
            if (area.letterboxed()) {
                graphics.fill(0, 0, w, area.y(), BAR_COLOR);
                graphics.fill(0, area.bottom(), w, h, BAR_COLOR);
            }
            barsDrawn++;
        } finally {
            pose.popMatrix();
        }
    }

    public static long barsDrawn() {
        return barsDrawn;
    }

    static void extractCard(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker) {
        VideoState state = VideoState.INSTANCE;
        if (!state.active()) {
            return;
        }
        EffectParams params = state.params();
        int width = graphics.guiWidth();
        int height = graphics.guiHeight();
        Font font = Minecraft.getInstance().font;
        float scale = OsdText.scale(graphics, 1.0F);
        float line = OsdText.height(scale) + 3.0F * scale;
        Component headline = headline(state, params);
        if (headline != null) {
            float big = OsdText.scale(graphics, 2.0F);
            OsdText.drawCentered(graphics, font, headline.getString(), width / 2.0F, height / 2.0F - line * 2.0F, big, OsdText.color());
            Component subline = subline(state);
            if (subline != null) {
                OsdText.drawCentered(graphics, font, subline.getString(), width / 2.0F, height / 2.0F + line * 0.5F, scale, OsdText.color());
            }
        }
        if (showsLinkIndicator(state, params)) {
            linkIndicator(graphics, font, state.signalQuality(), width, height / 2.0F + line * 2.0F, scale);
        }
    }

    static boolean signalLossShown(VideoState state, EffectParams params) {
        return state.status() == VideoState.LinkStatus.CONNECTED && params.noSignal >= VideoState.LOST_LEVEL;
    }

    static boolean showsLinkIndicator(VideoState state, EffectParams params) {
        return signalLossShown(state, params) && !OsdHud.showsVideoSignal();
    }

    private static void linkIndicator(GuiGraphicsExtractor graphics, Font font, float quality, int width, float top, float scale) {
        int percent = Math.round(Math.min(Math.max(quality, 0.0F), 1.0F) * 100.0F);
        String label = Component.translatable("propwash.video.link", percent).getString();
        int bars = 4;
        float barWidth = 2.0F * scale;
        float barGap = 1.0F * scale;
        float iconWidth = bars * barWidth + (bars - 1) * barGap;
        float gap = 4.0F * scale;
        float textWidth = OsdText.width(font, label, scale);
        float total = iconWidth + gap + textWidth;
        float x0 = width / 2.0F - total / 2.0F;
        float glyph = OsdText.height(scale);
        int lit = Math.round(quality * bars);
        int shade = 0xC0000000;
        for (int i = 0; i < bars; i++) {
            float barHeight = glyph * (i + 1) / bars;
            int bx0 = Math.round(x0 + i * (barWidth + barGap));
            int bx1 = Math.round(x0 + i * (barWidth + barGap) + barWidth);
            int by1 = Math.round(top + glyph);
            int by0 = Math.round(top + glyph - barHeight);
            int color = i < lit ? OsdText.color() : (i == 0 ? OsdText.warningColor() : 0xFF7A7A7A);
            int outline = Math.max(1, Math.round(scale * 0.5F));
            graphics.fill(bx0 - outline, by0 - outline, bx1 + outline, by1 + outline, shade);
            graphics.fill(bx0, by0, bx1, by1, color);
        }
        OsdText.draw(graphics, font, label, x0 + iconWidth + gap, top, scale, lit > 0 ? OsdText.color() : OsdText.warningColor());
    }

    private static void approximate(GuiGraphicsExtractor graphics, EffectParams p, int width, int height) {
        if (p.blackout()) {
            graphics.fill(0, 0, width, height, 0xFF000000);
            return;
        }
        float scale = height / 1080.0F;
        int mode = Math.round(p.mode);
        if (mode == FeedLook.DIGITAL.shaderMode()) {
            digital(graphics, p, width, height, scale);
        }
        if (mode == FeedLook.ANALOG.shaderMode()) {
            analogColourLoss(graphics, p, width, height);
            analogGlitches(graphics, p, width, height);
        }
        snow(graphics, p.noise, p.noiseContrast, 1.0F, width, height);
        if (mode == FeedLook.ANALOG.shaderMode()) {
            analogSync(graphics, p, width, height);
            analogScreen(graphics, p, width, height);
        }
        if (p.brightness < 0.999F) {
            graphics.fill(0, 0, width, height, ARGB.color(Math.round((1.0F - p.brightness) * 255.0F), 0, 0, 0));
        }
        feedStatic(graphics, p, width, height);
    }

    private static void analogGlitches(GuiGraphicsExtractor graphics, EffectParams p, int width, int height) {
        if (p.glitch > 0.0F) {
            int lines = 1 + FRAME_RANDOM.nextInt(3);
            for (int i = 0; i < lines; i++) {
                int y = FRAME_RANDOM.nextInt(Math.max(1, height));
                int shift = Math.round((FRAME_RANDOM.nextFloat() - 0.5F) * 0.16F * p.glitch * width);
                int alpha = Math.round(p.glitch * 150.0F);
                graphics.fill(Math.max(0, shift), y, Math.min(width, width + shift), y + 1, ARGB.color(alpha, 235, 235, 235));
                graphics.fill(0, y + 1, width, y + 2, ARGB.color(alpha / 2, 0, 0, 0));
            }
        }
        if (p.sparkle > 0.0F) {
            int streaks = Math.round(p.sparkle * 24.0F);
            for (int i = 0; i < streaks; i++) {
                int x = FRAME_RANDOM.nextInt(width);
                int y = FRAME_RANDOM.nextInt(height);
                int shade = FRAME_RANDOM.nextInt(4) == 0 ? 0x101010 : 0xFFFFFF;
                graphics.fill(x, y, x + 2 + FRAME_RANDOM.nextInt(5), y + 1, (0xCC << 24) | shade);
            }
        }
    }

    private static void analogColourLoss(GuiGraphicsExtractor graphics, EffectParams p, int width, int height) {
        float wash = Math.min(Math.max(p.desaturation, 0.0F), 1.0F) * 0.14F;
        if (wash > 0.004F) {
            graphics.fill(0, 0, width, height, ARGB.color(Math.round(wash * 255.0F), 128, 128, 128));
        }
        if (p.chromaBands > 0.0F && p.desaturation < 0.98F) {
            int bands = 3;
            for (int i = 0; i < bands; i++) {
                float centre = (float) (0.5 + 0.5 * Math.sin(p.bandPhase * (1.3 + i * 0.7) + i * 2.1));
                int y = Math.round(centre * height);
                int half = Math.max(2, Math.round(height * (0.04F + 0.1F * p.chromaBands)));
                int alpha = Math.round(p.chromaBands * 0.3F * 255.0F);
                graphics.fillGradient(0, y - half, width, y, 0x00808080, ARGB.color(alpha, 128, 128, 128));
                graphics.fillGradient(0, y, width, y + half, ARGB.color(alpha, 128, 128, 128), 0x00808080);
            }
        }
    }

    private static void analogSync(GuiGraphicsExtractor graphics, EffectParams p, int width, int height) {
        if (p.syncShift != 0.0F) {
            int y0 = Math.round(p.shiftTop * height);
            int y1 = Math.round(p.shiftBottom * height);
            int span = Math.max(1, y1 - y0);
            int row = Math.max(1, height / 240);
            float drift = FRAME_RANDOM.nextFloat() * 10.0F;
            float groupSeed = FRAME_RANDOM.nextFloat() * 100.0F;
            for (int y = y0; y < y1; y += row) {
                float rel = (y - y0) / (float) span;
                float taper = smooth(0.0F, 0.14F, rel) * smooth(1.0F, 0.86F, rel);
                int group = (y - y0) / Math.max(1, row * 14);
                boolean gap = span > height / 8 && hash(group, groupSeed) < 0.24F;
                if (gap) {
                    continue;
                }
                float ragged = (float) Math.sin(y * 0.09F + drift) * 0.25F + (FRAME_RANDOM.nextFloat() - 0.5F) * 0.3F;
                int blank = Math.round(Math.abs(p.syncShift) * width * (0.3F + 0.7F * taper) * (1.0F + ragged));
                if (blank <= 0) {
                    continue;
                }
                int grey = 44 + FRAME_RANDOM.nextInt(24);
                int fill = ARGB.color(0xE0, grey, grey, grey);
                int smear = ARGB.color(0x70, 150, 150, 150);
                int smearWidth = Math.max(1, blank / 3);
                if (p.syncShift > 0.0F) {
                    graphics.fill(0, y, blank, y + row, fill);
                    graphics.fill(blank, y, blank + smearWidth, y + row, smear);
                } else {
                    graphics.fill(width - blank, y, width, y + row, fill);
                    graphics.fill(width - blank - smearWidth, y, width - blank, y + row, smear);
                }
            }
        }
        if (p.roll != 0.0F) {
            float r = p.roll - (float) Math.floor(p.roll);
            int bar = Math.max(2, height / 14);
            int y = Math.round((1.0F - r) * height);
            graphics.fill(0, y - bar / 2, width, y + bar / 2, 0xE0000000);
        }
    }

    private static void analogScreen(GuiGraphicsExtractor graphics, EffectParams p, int width, int height) {
        if (p.vignette <= 0.0F) {
            return;
        }
        int alpha = Math.round(Math.min(p.vignette * 1.6F, 0.8F) * 255.0F);
        int edge = Math.max(4, height / 5);
        graphics.fillGradient(0, 0, width, edge, ARGB.color(alpha, 0, 0, 0), 0x00000000);
        graphics.fillGradient(0, height - edge, width, height, 0x00000000, ARGB.color(alpha, 0, 0, 0));
        int strips = 8;
        int stripWidth = Math.max(1, width / 12 / strips);
        for (int i = 0; i < strips; i++) {
            int stripAlpha = alpha * (strips - i) / (strips + 1);
            int color = ARGB.color(stripAlpha, 0, 0, 0);
            graphics.fill(i * stripWidth, 0, (i + 1) * stripWidth, height, color);
            graphics.fill(width - (i + 1) * stripWidth, 0, width - i * stripWidth, height, color);
        }
    }

    private static void feedStatic(GuiGraphicsExtractor graphics, EffectParams p, int width, int height) {
        if (p.noSignal <= 0.0F) {
            return;
        }
        if (p.noSignalBlack > 0.5F) {
            graphics.fill(0, 0, width, height, ARGB.color(Math.round(p.noSignal * 255.0F), 0, 0, 0));
        } else {
            graphics.fill(0, 0, width, height, ARGB.color(Math.round(p.noSignal * 200.0F), 60, 60, 60));
            snow(graphics, p.noSignal, p.noiseContrast, 0.9F, width, height);
        }
    }

    private static void digital(GuiGraphicsExtractor graphics, EffectParams p, int width, int height, float scale) {
        if (p.resolution < 0.999F) {
            int alpha = Math.round((1.0F - p.resolution) * 0.25F * 255.0F);
            graphics.fill(0, 0, width, height, ARGB.color(alpha, 90, 90, 90));
        }
        if (p.blockiness <= 0.0F) {
            return;
        }
        int size = Math.max(2, Math.round(p.blockPx * scale));
        int columns = Math.max(1, width / size);
        int rows = Math.max(1, height / size);
        int clusters = Math.min(MAX_CLUSTERS, Math.max(1, Math.round(p.blockiness * MAX_CLUSTERS)));
        for (int i = 0; i < clusters; i++) {
            boolean band = BLOCK_RANDOM.nextInt(3) == 0;
            int cw = band ? 4 + BLOCK_RANDOM.nextInt(Math.max(1, columns / 2)) : 2 + BLOCK_RANDOM.nextInt(5);
            int ch = band ? 1 + BLOCK_RANDOM.nextInt(2) : 1 + BLOCK_RANDOM.nextInt(3);
            int cx = BLOCK_RANDOM.nextInt(Math.max(1, columns - cw + 1));
            int cy = BLOCK_RANDOM.nextInt(Math.max(1, rows - ch + 1));
            int base = 70 + BLOCK_RANDOM.nextInt(60);
            for (int by = 0; by < ch; by++) {
                for (int bx = 0; bx < cw; bx++) {
                    if (BLOCK_RANDOM.nextInt(7) == 0) {
                        continue;
                    }
                    int x0 = (cx + bx) * size;
                    int y0 = (cy + by) * size;
                    int shade = Math.clamp(base + BLOCK_RANDOM.nextInt(17) - 8, 0, 255);
                    graphics.fill(x0, y0, x0 + size, y0 + size, ARGB.color(0x90, shade, shade, shade));
                    int light = Math.min(255, shade + 30);
                    graphics.fill(x0, y0 + size / 2, x0 + size, y0 + size / 2 + 1, ARGB.color(0x50, light, light, light));
                }
            }
        }
    }

    private static float smooth(float edge0, float edge1, float x) {
        float t = Math.clamp((x - edge0) / (edge1 - edge0), 0.0F, 1.0F);
        return t * t * (3.0F - 2.0F * t);
    }

    private static float hash(int a, float seed) {
        long h = (long) a * 0x9E3779B97F4A7C15L ^ Float.floatToIntBits(seed) * 0xC2B2AE3D27D4EB4FL;
        h ^= h >>> 29;
        h *= 0xBF58476D1CE4E5B9L;
        h ^= h >>> 32;
        return (h >>> 40) * 0x1.0p-24F;
    }

    private static void snow(GuiGraphicsExtractor graphics, float amount, float contrast, float brightness, int width, int height) {
        float a = Math.min(amount, 1.0F);
        if (a <= 0.0F || width <= 0 || height <= 0 || !ensureNoiseTexture()) {
            return;
        }
        float c = Math.min(Math.max(contrast, 0.0F), 1.0F);
        int level = Math.round(brightness * 255.0F);
        float flat = c >= 0.999F ? 0.0F : 1.0F - (1.0F - a) / (1.0F - a * c);
        if (flat > 0.0F) {
            int grey = Math.round(level * 0.5F);
            graphics.fill(0, 0, width, height, ARGB.color(Math.round(flat * 255.0F), grey, grey, grey));
        }
        int windowHeight = Minecraft.getInstance().getWindow().getHeight();
        float guiScale = windowHeight > 0 ? (float) windowHeight / height : 1.0F;
        float cellPx = Math.max(1.0F, SNOW_CELL_PX_AT_1080 * windowHeight / 1080.0F);
        float texelsPerGuiPixel = Math.max(0.25F, guiScale / cellPx);
        int sourceWidth = Math.max(1, Math.round(width * texelsPerGuiPixel));
        int sourceHeight = Math.max(1, Math.round(height * texelsPerGuiPixel));
        float u = FRAME_RANDOM.nextInt(NOISE_SIZE);
        float v = FRAME_RANDOM.nextInt(NOISE_SIZE);
        int color = ARGB.color(Math.round(a * c * 255.0F), level, level, level);
        graphics.blit(RenderPipelines.GUI_TEXTURED, NOISE_TEXTURE, 0, 0, u, v, width, height, sourceWidth, sourceHeight,
                NOISE_SIZE, NOISE_SIZE, color);
    }

    static boolean ensureNoiseTexture() {
        if (noiseReady) {
            return true;
        }
        if (noiseFailed) {
            return false;
        }
        try {
            DynamicTexture texture = new DynamicTexture(() -> "propwash video snow", NOISE_SIZE, NOISE_SIZE, false);
            NativeImage pixels = texture.getPixels();
            Xorshift random = new Xorshift();
            random.seed(1.0F, 0x534E4F57L);
            for (int y = 0; y < NOISE_SIZE; y++) {
                for (int x = 0; x < NOISE_SIZE; x++) {
                    float value = random.nextFloat();
                    int red = channel(value, random.nextFloat());
                    int green = channel(value, random.nextFloat());
                    int blue = channel(value, random.nextFloat());
                    pixels.setPixel(x, y, ARGB.color(255, red, green, blue));
                }
            }
            texture.upload();
            Minecraft.getInstance().getTextureManager().register(NOISE_TEXTURE, texture);
            noiseReady = true;
        } catch (RuntimeException exception) {
            noiseFailed = true;
            Constants.LOG.warn("Video-Effekt: Rauschtextur für das Ersatz-Overlay nicht anlegbar ({})", exception.toString());
        }
        return noiseReady;
    }

    private static int channel(float value, float tint) {
        float channel = value + (tint - 0.5F) * NOISE_TINT;
        return Math.round(Math.min(Math.max(channel, 0.0F), 1.0F) * 255.0F);
    }

    private static @Nullable Component headline(VideoState state, EffectParams params) {
        return switch (state.status()) {
            case INACTIVE -> null;
            case NO_GOGGLES -> Component.translatable("propwash.video.no_goggles");
            case WRONG_SYSTEM -> Component.translatable("propwash.video.no_signal");
            case CONNECTED -> null;
        };
    }

    private static @Nullable Component subline(VideoState state) {
        return switch (state.status()) {
            case NO_GOGGLES -> Component.translatable("propwash.video.no_goggles.hint");
            case WRONG_SYSTEM -> Component.translatable("propwash.video.wrong_system.hint");
            default -> null;
        };
    }

    private static final class Xorshift {
        private long state = 1L;

        void seed(float seed, long salt) {
            long h = (long) seed * 0x632BE59BD9B4E019L ^ salt ^ 0x9E3779B97F4A7C15L;
            h ^= h >>> 33;
            h *= 0xFF51AFD7ED558CCDL;
            h ^= h >>> 33;
            this.state = h == 0L ? 1L : h;
        }

        int nextInt(int bound) {
            if (bound <= 1) {
                return 0;
            }
            return (int) ((this.nextBits() >>> 33) % bound);
        }

        float nextFloat() {
            return (this.nextBits() >>> 40) * 0x1.0p-24F;
        }

        private long nextBits() {
            long x = this.state;
            x ^= x << 13;
            x ^= x >>> 7;
            x ^= x << 17;
            this.state = x;
            return x;
        }
    }
}
