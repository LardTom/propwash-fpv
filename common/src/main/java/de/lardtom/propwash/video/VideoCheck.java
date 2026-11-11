package de.lardtom.propwash.video;

import java.util.Locale;

public final class VideoCheck {
    public static final int WIDTH = 96;
    public static final int HEIGHT = 54;
    public static final int BYTES_PER_PIXEL = 4;
    public static final int TOLERANCE = 40;
    public static final int DARK_LUMA = 24;
    public static final double NOISY_MIN_SHARE = 0.35;
    public static final float NOISE = 0.5F;
    private static final int[] COLORS = {0xFFDC5A28, 0xFF28A0DC, 0xFF46C850, 0xFFE6DC5A};
    private static final double[] SAMPLE_SHARES = {0.3, 0.7};

    private VideoCheck() {
    }

    public static FisheyeLens lens() {
        return FisheyeLens.of(FovMath.DEFAULT_ANALOG_DEGREES, FisheyeLens.DEFAULT_STRENGTH, WIDTH, HEIGHT, WIDTH, HEIGHT);
    }

    public static int expectedArgb(int x, int y) {
        int column = x < WIDTH / 2 ? 0 : 1;
        int row = y < HEIGHT / 2 ? 0 : 1;
        return COLORS[row * 2 + column];
    }

    public static int[][] samples() {
        int[][] points = new int[SAMPLE_SHARES.length * SAMPLE_SHARES.length][];
        int i = 0;
        for (double y : SAMPLE_SHARES) {
            for (double x : SAMPLE_SHARES) {
                points[i++] = new int[]{(int) Math.floor(x * WIDTH), (int) Math.floor(y * HEIGHT)};
            }
        }
        return points;
    }

    public static int bytes() {
        return WIDTH * HEIGHT * BYTES_PER_PIXEL;
    }

    public static double luma(int red, int green, int blue) {
        return 0.299 * red + 0.587 * green + 0.114 * blue;
    }

    public static double expectedMeanLuma() {
        double sum = 0.0;
        int[][] points = samples();
        for (int[] p : points) {
            int argb = expectedArgb(p[0], p[1]);
            sum += luma((argb >> 16) & 0xFF, (argb >> 8) & 0xFF, argb & 0xFF);
        }
        return sum / points.length;
    }

    public static Reading read(byte[] rgba) {
        int[][] points = samples();
        int matched = 0;
        int dark = 0;
        double sum = 0.0;
        StringBuilder pixels = new StringBuilder();
        for (int[] p : points) {
            int o = (p[1] * WIDTH + p[0]) * BYTES_PER_PIXEL;
            if (rgba.length < o + BYTES_PER_PIXEL) {
                return new Reading(points.length, 0, points.length, 0.0, "zu kurz (" + rgba.length + " Byte)");
            }
            int red = rgba[o] & 0xFF;
            int green = rgba[o + 1] & 0xFF;
            int blue = rgba[o + 2] & 0xFF;
            int expected = expectedArgb(p[0], p[1]);
            boolean close = Math.abs(red - ((expected >> 16) & 0xFF)) <= TOLERANCE && Math.abs(green - ((expected >> 8) & 0xFF)) <= TOLERANCE
                    && Math.abs(blue - (expected & 0xFF)) <= TOLERANCE;
            double l = luma(red, green, blue);
            if (close) {
                matched++;
            }
            if (l < DARK_LUMA) {
                dark++;
            }
            sum += l;
            if (!pixels.isEmpty()) {
                pixels.append(' ');
            }
            pixels.append(String.format(Locale.ROOT, "%d,%d=%d/%d/%d", p[0], p[1], red, green, blue));
        }
        return new Reading(points.length, matched, dark, sum / points.length, pixels.toString());
    }

    public static Result evaluate(byte[] control, byte[] full, byte[] noisy, byte[] simple) {
        Reading c = read(control);
        Reading f = read(full);
        Reading n = read(noisy);
        Reading s = read(simple);
        boolean noisyOk = noisyVisible(n);
        boolean fullOk = f.exact() && noisyOk;
        Outcome outcome;
        if (!c.exact()) {
            outcome = Outcome.UNAVAILABLE;
        } else if (fullOk) {
            outcome = Outcome.PASS;
        } else if (s.exact()) {
            outcome = Outcome.FULL_BROKEN;
        } else {
            outcome = Outcome.ALL_BROKEN;
        }
        String detail = String.format(Locale.ROOT,
                "Testbild %dx%d: Kontrolle %s, voller Durchgang %s, mit Rauschen %s, einfacher Durchgang %s",
                WIDTH, HEIGHT, c.describe(), f.describe(), n.describeVisible(noisyOk), s.describe());
        return new Result(outcome, c, f, n, s, detail);
    }

    public static boolean noisyVisible(Reading reading) {
        return reading.meanLuma() >= expectedMeanLuma() * NOISY_MIN_SHARE && reading.dark() * 2 < reading.samples();
    }

    public enum Outcome {
        PENDING,
        PASS,
        FULL_BROKEN,
        ALL_BROKEN,
        UNAVAILABLE;

        public boolean simplifies() {
            return this == FULL_BROKEN || this == ALL_BROKEN;
        }
    }

    public enum Level {
        FULL,
        SIMPLE,
        NONE
    }

    public record Reading(int samples, int matched, int dark, double meanLuma, String pixels) {
        public boolean exact() {
            return this.samples > 0 && this.matched == this.samples;
        }

        public boolean black() {
            return this.samples > 0 && this.dark * 2 >= this.samples;
        }

        public String describeVisible(boolean visible) {
            return String.format(Locale.ROOT, "%s (%d von %d Punkten dunkel, Helligkeit %.0f, verlangt mindestens %.0f: %s)", visible ? "sichtbar"
                    : "schwarz oder NaN", this.dark, this.samples, this.meanLuma, expectedMeanLuma() * NOISY_MIN_SHARE, this.pixels);
        }

        public String describe() {
            String state = this.exact() ? "richtig" : this.black() ? "schwarz oder NaN" : "falsch";
            return String.format(Locale.ROOT, "%s (%d von %d Punkten richtig, %d dunkel, Helligkeit %.0f: %s)", state, this.matched, this.samples,
                    this.dark, this.meanLuma, this.pixels);
        }
    }

    public record Result(Outcome outcome, Reading control, Reading full, Reading noisy, Reading simple, String detail) {
    }
}
