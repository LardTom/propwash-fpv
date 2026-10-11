package de.lardtom.propwash.video;

public final class HeadlightCheck {
    public static final int BYTES_PER_PIXEL = 4;
    public static final int BAND_ROWS = 4;
    public static final int MIN_LEVEL = 4;
    public static final int TOLERANCE = 2;
    public static final int MIN_ELIGIBLE = 64;
    public static final int MIN_DARKENED = 8;
    public static final double DARKENED_SHARE = 0.01;
    public static final int FIRST_FRAME = 3;
    public static final int SETTLE_INTERVAL = 30;
    public static final int WATCH_INTERVAL = 600;
    public static final int SETTLE_PASSES = 3;
    public static final int STALE_FRAMES = 240;
    private static final double[] BANDS = {0.5, 0.35, 0.65};

    private long frames;
    private long next = FIRST_FRAME;
    private long issuedAt = -1L;
    private int passes;
    private int issued;

    public static Result compare(byte[] before, byte[] after, int pixels) {
        int count = Math.max(0, Math.min(pixels, Math.min(before.length, after.length) / BYTES_PER_PIXEL));
        int eligible = 0;
        int darkened = 0;
        int brightened = 0;
        for (int i = 0; i < count; i++) {
            int o = i * BYTES_PER_PIXEL;
            int inMax = 0;
            boolean darker = false;
            boolean brighter = false;
            for (int c = 0; c < 3; c++) {
                int in = before[o + c] & 0xFF;
                int out = after[o + c] & 0xFF;
                inMax = Math.max(inMax, in);
                darker |= out < in - TOLERANCE;
                brighter |= out > in + TOLERANCE;
            }
            if (inMax < MIN_LEVEL) {
                continue;
            }
            eligible++;
            if (darker) {
                darkened++;
            } else if (brighter) {
                brightened++;
            }
        }
        Verdict verdict;
        if (eligible < MIN_ELIGIBLE) {
            verdict = Verdict.INCONCLUSIVE;
        } else if (darkened >= MIN_DARKENED && darkened >= eligible * DARKENED_SHARE) {
            verdict = Verdict.FAIL;
        } else {
            verdict = Verdict.PASS;
        }
        return new Result(verdict, count, eligible, darkened, brightened);
    }

    public static int bandStart(int height, int index) {
        int rows = Math.min(BAND_ROWS, Math.max(1, height));
        double share = BANDS[Math.floorMod(index, BANDS.length)];
        int start = (int) Math.round(height * share) - rows / 2;
        return Math.max(0, Math.min(height - rows, start));
    }

    public static int bandRows(int height) {
        return Math.min(BAND_ROWS, Math.max(1, height));
    }

    public void frame() {
        this.frames++;
    }

    public boolean due() {
        if (this.issuedAt >= 0L && this.frames - this.issuedAt > STALE_FRAMES) {
            this.issuedAt = -1L;
        }
        return this.issuedAt < 0L && this.frames >= this.next;
    }

    public int issue() {
        this.issuedAt = this.frames;
        return this.issued++;
    }

    public void record(Result result) {
        this.issuedAt = -1L;
        if (result.verdict() == Verdict.PASS) {
            this.passes++;
        }
        this.next = this.frames + (this.passes < SETTLE_PASSES ? SETTLE_INTERVAL : WATCH_INTERVAL);
    }

    public void abandon() {
        this.issuedAt = -1L;
        this.next = this.frames + SETTLE_INTERVAL;
    }

    public void restart() {
        this.frames = 0L;
        this.next = FIRST_FRAME;
        this.issuedAt = -1L;
        this.passes = 0;
    }

    public boolean inFlight() {
        return this.issuedAt >= 0L;
    }

    public int passes() {
        return this.passes;
    }

    public long frames() {
        return this.frames;
    }

    public enum Verdict {
        PASS,
        FAIL,
        INCONCLUSIVE
    }

    public record Result(Verdict verdict, int pixels, int eligible, int darkened, int brightened) {
    }
}
