package de.lardtom.propwash.dev;

import java.util.Locale;

public final class FreezeMeter {
    public static final double STILL_SHARE = 0.25;
    public static final double JUMP_FACTOR = 3.0;
    public static final double JUMP_SLACK = 0.05;
    public static final long MIN_FREEZE_NANOS = 50_000_000L;

    private final double speed;
    private boolean started;
    private double lastX;
    private double lastY;
    private double lastZ;
    private long lastTime;
    private long stillSince = -1L;
    private int frames;
    private int freezes;
    private int jumps;
    private long longest;
    private boolean counted;

    public FreezeMeter(double blocksPerSecond) {
        this.speed = blocksPerSecond;
    }

    public record Result(int frames, int freezes, long longestNanos, int jumps) {
        public boolean smooth() {
            return this.freezes == 0 && this.jumps == 0;
        }

        public String text() {
            return String.format(Locale.ROOT, "%d Standbilder (längstes %d ms), %d Sprünge in %d Bildern", this.freezes,
                    this.longestNanos / 1_000_000L, this.jumps, this.frames);
        }
    }

    public void frame(long nanos, double x, double y, double z) {
        if (!this.started) {
            this.started = true;
            this.remember(nanos, x, y, z);
            return;
        }
        long dt = nanos - this.lastTime;
        if (dt <= 0L) {
            return;
        }
        double dx = x - this.lastX;
        double dy = y - this.lastY;
        double dz = z - this.lastZ;
        double moved = Math.sqrt(dx * dx + dy * dy + dz * dz);
        double expected = this.speed * dt * 1.0E-9;
        this.frames++;
        if (moved < STILL_SHARE * expected) {
            if (this.stillSince < 0L) {
                this.stillSince = this.lastTime;
                this.counted = false;
            }
            long still = nanos - this.stillSince;
            if (still >= MIN_FREEZE_NANOS && !this.counted) {
                this.counted = true;
                this.freezes++;
            }
            if (this.counted) {
                this.longest = Math.max(this.longest, still);
            }
        } else {
            this.stillSince = -1L;
            this.counted = false;
        }
        if (moved > JUMP_FACTOR * expected + JUMP_SLACK) {
            this.jumps++;
        }
        this.remember(nanos, x, y, z);
    }

    private void remember(long nanos, double x, double y, double z) {
        this.lastTime = nanos;
        this.lastX = x;
        this.lastY = y;
        this.lastZ = z;
    }

    public Result result() {
        return new Result(this.frames, this.freezes, this.longest, this.jumps);
    }
}
