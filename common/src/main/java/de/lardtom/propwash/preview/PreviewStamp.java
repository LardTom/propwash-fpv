package de.lardtom.propwash.preview;

import java.nio.ByteBuffer;

public final class PreviewStamp {
    public static final int TEXT_ARGB = 0xB4FFFFFF;
    public static final int SHADOW_ARGB = 0xB4000000;
    public static final float TEXT_ALPHA = (TEXT_ARGB >>> 24) / 255.0F;
    public static final float SHADOW_ALPHA = (SHADOW_ARGB >>> 24) / 255.0F;
    public static final int MIN_SCALE = 1;
    public static final int MARGIN = 4;
    private static final float SCREEN_PIXELS_PER_STEP = 540.0F;
    private static final float FRAME_PIXELS_PER_STEP = 360.0F;
    private static final int LUMA_BLACK = 16;
    private static final int LUMA_WHITE = 235;
    private static final int CHROMA_NEUTRAL = 128;

    private PreviewStamp() {
    }

    public static int screenScale(int framebufferHeight) {
        return Math.max(MIN_SCALE, Math.round(framebufferHeight / SCREEN_PIXELS_PER_STEP));
    }

    public static int stampScale(int frameHeight) {
        return Math.max(MIN_SCALE, Math.round(frameHeight / FRAME_PIXELS_PER_STEP));
    }

    public static int margin(int frameHeight) {
        return MARGIN * stampScale(frameHeight);
    }

    public static Placement place(PreviewFont.Raster raster, int width, int height, int lift) {
        int scale = screenScale(height);
        int right = width - MARGIN * scale;
        int bottom = height - MARGIN * scale - lift;
        return new Placement(right - (raster.width() + 1) * scale, bottom - (raster.height() + 1) * scale, scale);
    }

    public static Mask mask(PreviewFont.Raster raster, int frameHeight) {
        int scale = stampScale(frameHeight);
        int width = (raster.width() + 1) * scale;
        int height = (raster.height() + 1) * scale;
        byte[] alpha = new byte[width * height];
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                if (raster.ink(x / scale, y / scale)) {
                    alpha[y * width + x] = (byte) 0xFF;
                }
            }
        }
        return new Mask(width, height, scale, alpha);
    }

    public static int stampI420(ByteBuffer frame, int width, int height, Mask mask) {
        if (width < 2 || height < 2 || frame.limit() < width * height * 3 / 2) {
            return 0;
        }
        int right = width - margin(height);
        int bottom = height - margin(height);
        int left = right - mask.width();
        int top = bottom - mask.height();
        int changed = 0;
        for (int y = Math.max(0, top); y < Math.min(height, bottom); y++) {
            for (int x = Math.max(0, left); x < Math.min(width, right); x++) {
                int text = mask.text(x - left, y - top);
                int shade = mask.shade(x - left, y - top);
                if (text == 0 && shade == 0) {
                    continue;
                }
                int index = y * width + x;
                int value = frame.get(index) & 0xFF;
                value = blend(value, LUMA_BLACK, shade * SHADOW_ALPHA / 255.0F);
                value = blend(value, LUMA_WHITE, text * TEXT_ALPHA / 255.0F);
                frame.put(index, (byte) value);
                changed++;
            }
        }
        int luma = width * height;
        int chromaWidth = width / 2;
        int chromaHeight = height / 2;
        int uOffset = luma;
        int vOffset = luma + luma / 4;
        for (int cy = Math.max(0, top / 2); cy < Math.min(chromaHeight, (bottom + 1) / 2); cy++) {
            for (int cx = Math.max(0, left / 2); cx < Math.min(chromaWidth, (right + 1) / 2); cx++) {
                int cover = 0;
                for (int dy = 0; dy < 2; dy++) {
                    for (int dx = 0; dx < 2; dx++) {
                        int x = cx * 2 + dx;
                        int y = cy * 2 + dy;
                        if (x >= left && x < right && y >= top && y < bottom) {
                            cover = Math.max(cover, Math.max(mask.text(x - left, y - top), mask.shade(x - left, y - top)));
                        }
                    }
                }
                if (cover == 0) {
                    continue;
                }
                float amount = cover * TEXT_ALPHA / 255.0F;
                int chroma = cy * chromaWidth + cx;
                frame.put(uOffset + chroma, (byte) blend(frame.get(uOffset + chroma) & 0xFF, CHROMA_NEUTRAL, amount));
                frame.put(vOffset + chroma, (byte) blend(frame.get(vOffset + chroma) & 0xFF, CHROMA_NEUTRAL, amount));
            }
        }
        return changed;
    }

    public static int stampArgb(Pixels pixels, int width, int height, Mask mask) {
        int right = width - margin(height);
        int bottom = height - margin(height);
        int left = right - mask.width();
        int top = bottom - mask.height();
        int changed = 0;
        for (int y = Math.max(0, top); y < Math.min(height, bottom); y++) {
            for (int x = Math.max(0, left); x < Math.min(width, right); x++) {
                int text = mask.text(x - left, y - top);
                int shade = mask.shade(x - left, y - top);
                if (text == 0 && shade == 0) {
                    continue;
                }
                float dark = shade * SHADOW_ALPHA / 255.0F;
                float light = text * TEXT_ALPHA / 255.0F;
                int argb = pixels.get(x, y);
                int red = blend(blend(argb >> 16 & 0xFF, 0, dark), 255, light);
                int green = blend(blend(argb >> 8 & 0xFF, 0, dark), 255, light);
                int blue = blend(blend(argb & 0xFF, 0, dark), 255, light);
                pixels.set(x, y, argb & 0xFF000000 | red << 16 | green << 8 | blue);
                changed++;
            }
        }
        return changed;
    }

    private static int blend(int from, int to, float amount) {
        if (amount <= 0.0F) {
            return from;
        }
        return Math.clamp(Math.round(from + (to - from) * Math.min(1.0F, amount)), 0, 255);
    }

    public interface Pixels {
        int get(int x, int y);

        void set(int x, int y, int argb);
    }

    public record Placement(int left, int top, int scale) {
    }

    public record Mask(int width, int height, int shadow, byte[] alpha) {
        public int text(int x, int y) {
            if (x < 0 || y < 0 || x >= this.width || y >= this.height) {
                return 0;
            }
            return this.alpha[y * this.width + x] & 0xFF;
        }

        public int shade(int x, int y) {
            return this.text(x - this.shadow, y - this.shadow);
        }
    }
}
