package de.lardtom.propwash.video.record;

import java.nio.ByteBuffer;

public final class Thumbnail {
    public static final int WIDTH = 256;
    public static final int HEIGHT = 144;
    private static final int TAPS = 3;

    private Thumbnail() {
    }

    public static int[] fromI420(byte[] i420, int width, int height, int outWidth, int outHeight) {
        return fromI420(ByteBuffer.wrap(i420), width, height, outWidth, outHeight);
    }

    public static int[] fromI420(ByteBuffer i420, int width, int height, int outWidth, int outHeight) {
        if (width <= 0 || height <= 0 || outWidth <= 0 || outHeight <= 0 || i420.limit() < width * height * 3 / 2) {
            throw new IllegalArgumentException("bad frame " + width + "x" + height + " -> " + outWidth + "x" + outHeight);
        }
        int chromaWidth = width / 2;
        int chromaHeight = height / 2;
        int uBase = width * height;
        int vBase = uBase + chromaWidth * chromaHeight;
        int[] argb = new int[outWidth * outHeight];
        double cellWidth = width / (double) outWidth;
        double cellHeight = height / (double) outHeight;
        for (int oy = 0; oy < outHeight; oy++) {
            for (int ox = 0; ox < outWidth; ox++) {
                int ySum = 0;
                int uSum = 0;
                int vSum = 0;
                for (int ty = 0; ty < TAPS; ty++) {
                    int sy = Math.min(height - 1, (int) ((oy + (ty + 0.5) / TAPS) * cellHeight));
                    for (int tx = 0; tx < TAPS; tx++) {
                        int sx = Math.min(width - 1, (int) ((ox + (tx + 0.5) / TAPS) * cellWidth));
                        ySum += i420.get(sy * width + sx) & 0xFF;
                        int chroma = Math.min(chromaHeight - 1, sy / 2) * chromaWidth + Math.min(chromaWidth - 1, sx / 2);
                        uSum += i420.get(uBase + chroma) & 0xFF;
                        vSum += i420.get(vBase + chroma) & 0xFF;
                    }
                }
                int taps = TAPS * TAPS;
                argb[oy * outWidth + ox] = rgb709((double) ySum / taps, (double) uSum / taps, (double) vSum / taps);
            }
        }
        return argb;
    }

    public static int rgb709(double y, double u, double v) {
        double luma = (y - 16.0) / 219.0;
        double cb = (u - 128.0) / 224.0;
        double cr = (v - 128.0) / 224.0;
        int r = channel(luma + 1.5748 * cr);
        int g = channel(luma - 0.1873 * cb - 0.4681 * cr);
        int b = channel(luma + 1.8556 * cb);
        return 0xFF000000 | r << 16 | g << 8 | b;
    }

    private static int channel(double value) {
        return (int) Math.round(Math.max(0.0, Math.min(1.0, value)) * 255.0);
    }
}
