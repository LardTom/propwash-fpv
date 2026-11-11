package de.lardtom.propwash.video.record;

import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class RgbaI420Test {
    private static ByteBuffer solid(int width, int height, int r, int g, int b) {
        ByteBuffer rgba = ByteBuffer.allocateDirect(width * height * 4);
        for (int i = 0; i < width * height; i++) {
            rgba.put(i * 4, (byte) r).put(i * 4 + 1, (byte) g).put(i * 4 + 2, (byte) b).put(i * 4 + 3, (byte) 255);
        }
        return rgba;
    }

    private static int[] yuv(ByteBuffer i420, int width, int height, int x, int y) {
        int luma = width * height;
        int chroma = (y / 2) * (width / 2) + x / 2;
        return new int[]{i420.get(y * width + x) & 0xFF, i420.get(luma + chroma) & 0xFF, i420.get(luma + luma / 4 + chroma) & 0xFF};
    }

    private static int[] convertSolid(int r, int g, int b) {
        ByteBuffer out = ByteBuffer.allocateDirect(8 * 4 * 3 / 2);
        RgbaI420.convert(solid(8, 4, r, g, b), 8, 4, out);
        return yuv(out, 8, 4, 3, 1);
    }

    @Test
    void limitedRangeBt709LikeThePackShader() {
        assertArrayEquals(new int[]{16, 128, 128}, convertSolid(0, 0, 0));
        assertArrayEquals(new int[]{235, 128, 128}, convertSolid(255, 255, 255));
        assertArrayEquals(new int[]{63, 102, 240}, convertSolid(255, 0, 0));
        assertArrayEquals(new int[]{32, 240, 118}, convertSolid(0, 0, 255));
        assertArrayEquals(new int[]{173, 42, 26}, convertSolid(0, 255, 0));
    }

    @Test
    void framebufferRowsAreBottomUpTheVideoTopDown() {
        int width = 4;
        int height = 2;
        ByteBuffer rgba = ByteBuffer.allocateDirect(width * height * 4);
        for (int x = 0; x < width; x++) {
            rgba.put(x * 4, (byte) 255).put(x * 4 + 3, (byte) 255);
            int top = (width + x) * 4;
            rgba.put(top + 2, (byte) 255).put(top + 3, (byte) 255);
        }
        ByteBuffer out = ByteBuffer.allocateDirect(width * height * 3 / 2);
        RgbaI420.convert(rgba, width, height, out);
        assertEquals(32, yuv(out, width, height, 0, 0)[0], "oben im Video: die obere Zeile des Bildpuffers (blau)");
        assertEquals(63, yuv(out, width, height, 0, 1)[0], "unten im Video: die untere Zeile des Bildpuffers (rot)");
    }

    @Test
    void sameBytesAsTheGpuPackingForARandomPicture() {
        int width = 32;
        int height = 16;
        Random random = new Random(42L);
        ByteBuffer rgba = ByteBuffer.allocateDirect(width * height * 4);
        for (int i = 0; i < width * height; i++) {
            rgba.put(i * 4, (byte) random.nextInt(256)).put(i * 4 + 1, (byte) random.nextInt(256)).put(i * 4 + 2, (byte) random.nextInt(256))
                    .put(i * 4 + 3, (byte) 255);
        }
        ByteBuffer out = ByteBuffer.allocateDirect(width * height * 3 / 2);
        RgbaI420.convert(rgba, width, height, out);
        byte[] packed = ShaderPacking.pack(rgba, width, height);
        for (int i = 0; i < packed.length; i++) {
            assertEquals(packed[i], out.get(i), "Byte " + i);
        }
    }

    @Test
    void rejectsOddSizesAndShortBuffers() {
        assertThrows(IllegalArgumentException.class, () -> RgbaI420.convert(ByteBuffer.allocateDirect(4 * 3 * 4), 4, 3,
                ByteBuffer.allocateDirect(18)));
        assertThrows(IllegalArgumentException.class, () -> RgbaI420.convert(ByteBuffer.allocateDirect(10), 4, 2, ByteBuffer.allocateDirect(12)));
        assertThrows(IllegalArgumentException.class, () -> RgbaI420.convert(ByteBuffer.allocateDirect(32), 4, 2, ByteBuffer.allocateDirect(11)));
    }

    private static final class ShaderPacking {
        static byte[] pack(ByteBuffer rgba, int width, int height) {
            int texWidth = width / 4;
            int texHeight = height * 3 / 2;
            byte[] out = new byte[texWidth * texHeight * 4];
            for (int ty = 0; ty < texHeight; ty++) {
                for (int tx = 0; tx < texWidth; tx++) {
                    float[] result = new float[4];
                    if (ty < height) {
                        for (int k = 0; k < 4; k++) {
                            result[k] = toY(pixel(rgba, width, tx * 4 + k, ty, height));
                        }
                    } else {
                        int row = ty - height;
                        int quarter = height / 4;
                        boolean red = row >= quarter;
                        if (red) {
                            row -= quarter;
                        }
                        int texelsPerRow = width / 8;
                        int chromaRow = 2 * row + (tx >= texelsPerRow ? 1 : 0);
                        int chromaX = 4 * (tx >= texelsPerRow ? tx - texelsPerRow : tx);
                        for (int k = 0; k < 4; k++) {
                            int x = 2 * (chromaX + k);
                            int y = 2 * chromaRow;
                            float[] a = pixel(rgba, width, x, y, height);
                            float[] b = pixel(rgba, width, x + 1, y, height);
                            float[] c = pixel(rgba, width, x, y + 1, height);
                            float[] d = pixel(rgba, width, x + 1, y + 1, height);
                            float[] mean = {(a[0] + b[0] + c[0] + d[0]) * 0.25F, (a[1] + b[1] + c[1] + d[1]) * 0.25F,
                                    (a[2] + b[2] + c[2] + d[2]) * 0.25F};
                            result[k] = red ? toCr(mean) : toCb(mean);
                        }
                    }
                    for (int k = 0; k < 4; k++) {
                        out[(ty * texWidth + tx) * 4 + k] = (byte) unorm(result[k]);
                    }
                }
            }
            return out;
        }

        private static float[] pixel(ByteBuffer rgba, int width, int x, int y, int height) {
            int index = ((height - 1 - y) * width + x) * 4;
            return new float[]{(rgba.get(index) & 0xFF) / 255.0F, (rgba.get(index + 1) & 0xFF) / 255.0F, (rgba.get(index + 2) & 0xFF) / 255.0F};
        }

        private static float luma(float[] c) {
            return c[0] * 0.2126F + c[1] * 0.7152F + c[2] * 0.0722F;
        }

        private static float toY(float[] c) {
            return (16.0F + 219.0F * luma(c)) / 255.0F;
        }

        private static float toCb(float[] c) {
            return Math.clamp((128.0F + 224.0F * (c[2] - luma(c)) / 1.8556F) / 255.0F, 0.0F, 1.0F);
        }

        private static float toCr(float[] c) {
            return Math.clamp((128.0F + 224.0F * (c[0] - luma(c)) / 1.5748F) / 255.0F, 0.0F, 1.0F);
        }

        private static int unorm(float value) {
            return (int) Math.floor(Math.clamp(value, 0.0F, 1.0F) * 255.0F + 0.5F);
        }
    }
}
