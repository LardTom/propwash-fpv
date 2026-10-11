package de.lardtom.propwash.drone.trail;

import de.lardtom.propwash.build.DronePaint;
import de.lardtom.propwash.build.PaintSlot;
import de.lardtom.propwash.drone.led.LedColor;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.OptionalInt;

public final class TrailColor {
    public static final List<PaintSlot> PAINT_ORDER = List.of(PaintSlot.FRAME, PaintSlot.TPU, PaintSlot.PROP_FRONT_LEFT,
            PaintSlot.PROP_FRONT_RIGHT, PaintSlot.PROP_REAR_LEFT, PaintSlot.PROP_REAR_RIGHT, PaintSlot.MOTORS, PaintSlot.CAMERA,
            PaintSlot.ANTENNA, PaintSlot.ACCESSORIES, PaintSlot.BATTERY, PaintSlot.STACK);
    public static final int[] PALETTE = {0x22E5FF, 0xFF3FD2, 0x9BFF2E, 0xFF8A1C, 0xFFE32A, 0xA05CFF, 0xFF3B3B, 0x3DFFB0};
    public static final float MIN_SATURATION = 0.3F;
    public static final float MIN_VALUE = 0.25F;
    public static final float VIVID_SATURATION = 0.7F;
    public static final float HALO_VALUE = 0.28F;

    private TrailColor() {
    }

    public static int pick(@Nullable LedColor led, @Nullable DronePaint paint, long seed) {
        if (led != null && led != LedColor.WHITE && led != LedColor.WARM_WHITE && colourful(led.rgb())) {
            return vivid(led.rgb());
        }
        if (paint != null) {
            for (PaintSlot slot : PAINT_ORDER) {
                OptionalInt color = paint.color(slot);
                if (color.isPresent() && colourful(color.getAsInt())) {
                    return vivid(color.getAsInt());
                }
            }
        }
        return palette(seed);
    }

    public static int palette(long seed) {
        long mixed = seed * 0x9E3779B97F4A7C15L;
        mixed ^= mixed >>> 29;
        int index = (int) Math.floorMod(mixed, (long) PALETTE.length);
        return PALETTE[index];
    }

    public static boolean colourful(int rgb) {
        float[] hsv = hsv(rgb);
        return hsv[1] >= MIN_SATURATION && hsv[2] >= MIN_VALUE;
    }

    public static int vivid(int rgb) {
        float[] hsv = hsv(rgb);
        return fromHsv(hsv[0], Math.max(VIVID_SATURATION, hsv[1]), 1.0F);
    }

    public static int halo(int rgb) {
        float[] hsv = hsv(rgb);
        return fromHsv(hsv[0], Math.min(1.0F, hsv[1] + 0.15F), HALO_VALUE);
    }

    static float[] hsv(int rgb) {
        float r = ((rgb >> 16) & 0xFF) / 255.0F;
        float g = ((rgb >> 8) & 0xFF) / 255.0F;
        float b = (rgb & 0xFF) / 255.0F;
        float max = Math.max(r, Math.max(g, b));
        float min = Math.min(r, Math.min(g, b));
        float delta = max - min;
        float hue;
        if (delta <= 0.0F) {
            hue = 0.0F;
        } else if (max == r) {
            hue = ((g - b) / delta) % 6.0F;
        } else if (max == g) {
            hue = (b - r) / delta + 2.0F;
        } else {
            hue = (r - g) / delta + 4.0F;
        }
        hue /= 6.0F;
        if (hue < 0.0F) {
            hue += 1.0F;
        }
        float saturation = max <= 0.0F ? 0.0F : delta / max;
        return new float[]{hue, saturation, max};
    }

    static int fromHsv(float hue, float saturation, float value) {
        float h = (hue - (float) Math.floor(hue)) * 6.0F;
        int sector = (int) Math.floor(h) % 6;
        float f = h - (float) Math.floor(h);
        float p = value * (1.0F - saturation);
        float q = value * (1.0F - f * saturation);
        float t = value * (1.0F - (1.0F - f) * saturation);
        float r;
        float g;
        float b;
        switch (sector) {
            case 0 -> {
                r = value;
                g = t;
                b = p;
            }
            case 1 -> {
                r = q;
                g = value;
                b = p;
            }
            case 2 -> {
                r = p;
                g = value;
                b = t;
            }
            case 3 -> {
                r = p;
                g = q;
                b = value;
            }
            case 4 -> {
                r = t;
                g = p;
                b = value;
            }
            default -> {
                r = value;
                g = p;
                b = q;
            }
        }
        return (channel(r) << 16) | (channel(g) << 8) | channel(b);
    }

    private static int channel(float value) {
        return Math.max(0, Math.min(255, Math.round(value * 255.0F)));
    }
}
