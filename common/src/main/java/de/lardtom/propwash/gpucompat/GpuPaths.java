package de.lardtom.propwash.gpucompat;

import org.jspecify.annotations.Nullable;

import java.util.Locale;

public final class GpuPaths {
    public static final int WIDTH = 16;
    public static final int HEIGHT = 8;
    public static final int BYTES_PER_PIXEL = 4;

    private GpuPaths() {
    }

    public static int bytes() {
        return WIDTH * HEIGHT * BYTES_PER_PIXEL;
    }

    public static byte[] pattern() {
        byte[] data = new byte[bytes()];
        for (int y = 0; y < HEIGHT; y++) {
            for (int x = 0; x < WIDTH; x++) {
                int o = (y * WIDTH + x) * BYTES_PER_PIXEL;
                data[o] = (byte) (32 + x * 13);
                data[o + 1] = (byte) (40 + y * 25);
                data[o + 2] = (byte) (200 - x * 7 - y * 9);
                data[o + 3] = (byte) 255;
            }
        }
        return data;
    }

    public static boolean zero(byte @Nullable [] data) {
        if (data == null) {
            return false;
        }
        for (byte value : data) {
            if (value != 0) {
                return false;
            }
        }
        return true;
    }

    public static boolean rgbZero(byte @Nullable [] data, int pixels) {
        if (data == null) {
            return false;
        }
        int count = Math.min(pixels, data.length / BYTES_PER_PIXEL);
        for (int i = 0; i < count; i++) {
            int o = i * BYTES_PER_PIXEL;
            if (data[o] != 0 || data[o + 1] != 0 || data[o + 2] != 0) {
                return false;
            }
        }
        return count > 0;
    }

    public static Result compare(byte[] expected, byte @Nullable [] actual) {
        if (actual == null || actual.length < expected.length) {
            return Result.FAILED;
        }
        boolean same = true;
        for (int i = 0; i < expected.length; i++) {
            if (expected[i] != actual[i]) {
                same = false;
                break;
            }
        }
        if (same) {
            return Result.OK;
        }
        for (int i = 0; i < expected.length; i++) {
            if (actual[i] != 0) {
                return Result.WRONG;
            }
        }
        return Result.ZERO;
    }

    public enum Result {
        UNTESTED("nicht geprüft"),
        OK("ok"),
        ZERO("nur Nullen"),
        WRONG("falsche Werte"),
        FAILED("Fehler");

        private final String text;

        Result(String text) {
            this.text = text;
        }

        public boolean broken() {
            return this == ZERO || this == WRONG || this == FAILED;
        }

        public String text() {
            return this.text;
        }
    }

    public enum ReadPath {
        GPU("gpu"),
        CLASSIC("klassisch"),
        NONE("keiner");

        private final String text;

        ReadPath(String text) {
            this.text = text;
        }

        public String text() {
            return this.text;
        }
    }

    public record Health(Result gpuRead, Result classicRead, Result blit, Result shaderCopy, @Nullable String detail) {
        public static final Health UNKNOWN = new Health(Result.UNTESTED, Result.UNTESTED, Result.UNTESTED, Result.UNTESTED, null);

        public boolean tested() {
            return this.gpuRead != Result.UNTESTED;
        }

        public ReadPath readPath() {
            if (!this.gpuRead.broken()) {
                return ReadPath.GPU;
            }
            return this.classicRead.broken() ? ReadPath.NONE : ReadPath.CLASSIC;
        }

        public boolean blitWorks() {
            return !this.blit.broken();
        }

        public boolean anyBroken() {
            return this.gpuRead.broken() || this.classicRead.broken() || this.blit.broken() || this.shaderCopy.broken();
        }

        public String describe() {
            String text = String.format(Locale.ROOT, "Rücklesen über die GPU %s, klassisch %s; Kopie per Blit %s, per Shader %s; Rücklesen über %s",
                    this.gpuRead.text(), this.classicRead.text(), this.blit.text(), this.shaderCopy.text(), this.readPath().text());
            return this.detail == null || this.detail.isBlank() ? text : text + " (" + this.detail + ")";
        }
    }
}
