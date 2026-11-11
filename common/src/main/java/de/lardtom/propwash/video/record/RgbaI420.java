package de.lardtom.propwash.video.record;

import java.nio.ByteBuffer;

public final class RgbaI420 {
    private static final float KR = 0.2126F;
    private static final float KG = 0.7152F;
    private static final float KB = 0.0722F;
    private static final float[] UNIT = new float[256];
    private static final float[] RED = new float[256];
    private static final float[] GREEN = new float[256];
    private static final float[] BLUE = new float[256];

    static {
        for (int i = 0; i < 256; i++) {
            UNIT[i] = i / 255.0F;
            RED[i] = UNIT[i] * KR;
            GREEN[i] = UNIT[i] * KG;
            BLUE[i] = UNIT[i] * KB;
        }
    }

    private RgbaI420() {
    }

    public static void convert(ByteBuffer rgba, int width, int height, ByteBuffer out) {
        if (width <= 0 || height <= 0 || width % 2 != 0 || height % 2 != 0) {
            throw new IllegalArgumentException("Bildgröße " + width + "x" + height);
        }
        int rowBytes = width * 4;
        int luma = width * height;
        int chromaWidth = width / 2;
        if (rgba.limit() < rowBytes * height || out.limit() < luma * 3 / 2) {
            throw new IllegalArgumentException("Puffer zu klein für " + width + "x" + height);
        }
        int uOffset = luma;
        int vOffset = luma + luma / 4;
        byte[] upper = new byte[rowBytes];
        byte[] lower = new byte[rowBytes];
        byte[] upperY = new byte[width];
        byte[] lowerY = new byte[width];
        byte[] u = new byte[chromaWidth];
        byte[] v = new byte[chromaWidth];
        for (int y = 0; y < height; y += 2) {
            rgba.get((height - 1 - y) * rowBytes, upper);
            rgba.get((height - 2 - y) * rowBytes, lower);
            for (int x = 0, i = 0; x < width; x++, i += 4) {
                upperY[x] = (byte) unorm((16.0F + 219.0F * (RED[upper[i] & 0xFF] + GREEN[upper[i + 1] & 0xFF] + BLUE[upper[i + 2] & 0xFF])) / 255.0F);
                lowerY[x] = (byte) unorm((16.0F + 219.0F * (RED[lower[i] & 0xFF] + GREEN[lower[i + 1] & 0xFF] + BLUE[lower[i + 2] & 0xFF])) / 255.0F);
            }
            for (int cx = 0, i = 0; cx < chromaWidth; cx++, i += 8) {
                float r = (UNIT[upper[i] & 0xFF] + UNIT[upper[i + 4] & 0xFF] + UNIT[lower[i] & 0xFF] + UNIT[lower[i + 4] & 0xFF]) * 0.25F;
                float g = (UNIT[upper[i + 1] & 0xFF] + UNIT[upper[i + 5] & 0xFF] + UNIT[lower[i + 1] & 0xFF] + UNIT[lower[i + 5] & 0xFF]) * 0.25F;
                float b = (UNIT[upper[i + 2] & 0xFF] + UNIT[upper[i + 6] & 0xFF] + UNIT[lower[i + 2] & 0xFF] + UNIT[lower[i + 6] & 0xFF]) * 0.25F;
                float l = r * KR + g * KG + b * KB;
                u[cx] = (byte) unorm((128.0F + 224.0F * (b - l) / 1.8556F) / 255.0F);
                v[cx] = (byte) unorm((128.0F + 224.0F * (r - l) / 1.5748F) / 255.0F);
            }
            out.put(y * width, upperY);
            out.put((y + 1) * width, lowerY);
            int chromaRow = (y / 2) * chromaWidth;
            out.put(uOffset + chromaRow, u);
            out.put(vOffset + chromaRow, v);
        }
    }

    private static int unorm(float value) {
        return (int) Math.floor(Math.clamp(value, 0.0F, 1.0F) * 255.0F + 0.5F);
    }
}
