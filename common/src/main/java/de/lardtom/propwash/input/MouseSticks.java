package de.lardtom.propwash.input;

public final class MouseSticks {
    public static final float CM_PER_INCH = 2.54F;
    public static final float RATE_FULL_CM_PER_SECOND = 30.0F;
    public static final float HOLD_FULL_CM = 6.0F;
    static final int HISTORY = 1024;
    private static final int SPREAD = 8;
    private static final long MAX_AGE_NANOS = 1_000_000_000L;
    private static final long ORDER_SLACK_NANOS = 50_000_000L;

    private final long[] times = new long[HISTORY];
    private final float[] countsX = new float[HISTORY];
    private final float[] countsY = new float[HISTORY];
    private int newest = -1;
    private int filled;
    private float pendingX;
    private float pendingY;
    private long clock;
    private float holdX;
    private float holdY;
    private boolean holdingX;
    private boolean holdingY;
    private Output last = Output.CENTER;

    public void reset() {
        this.center();
        this.holdingX = false;
        this.holdingY = false;
        this.last = Output.CENTER;
    }

    public void center() {
        this.newest = -1;
        this.filled = 0;
        this.pendingX = 0.0F;
        this.pendingY = 0.0F;
        this.holdX = 0.0F;
        this.holdY = 0.0F;
        this.last = new Output(0.0F, 0.0F, 0.0F, this.last.holdX(), this.last.holdY());
    }

    public Output last() {
        return this.last;
    }

    public void motion(long timeNanos, float dx, float dy) {
        if (!Float.isFinite(dx) || !Float.isFinite(dy) || dx == 0.0F && dy == 0.0F) {
            return;
        }
        this.newest = (this.newest + 1) % HISTORY;
        this.times[this.newest] = timeNanos;
        this.countsX[this.newest] = dx;
        this.countsY[this.newest] = dy;
        this.filled = Math.min(HISTORY, this.filled + 1);
        this.pendingX += dx;
        this.pendingY += dy;
    }

    public Output update(float dx, float dy, float dt, MouseSettings settings, boolean selfLevel) {
        if (!(dt > 0.0F) || !Float.isFinite(dt)) {
            return this.last;
        }
        long step = Math.max(1L, Math.round(dt * 1.0E9));
        long start = this.clock;
        this.clock += step;
        if (Float.isFinite(dx) && Float.isFinite(dy) && (dx != 0.0F || dy != 0.0F)) {
            for (int i = 0; i < SPREAD; i++) {
                this.motion(start + step * (2L * i + 1L) / (2L * SPREAD), dx / SPREAD, dy / SPREAD);
            }
        }
        return this.update(this.clock, dt, settings, selfLevel);
    }

    public Output update(long nowNanos, float dt, MouseSettings settings, boolean selfLevel) {
        if (!(dt > 0.0F) || !Float.isFinite(dt)) {
            return this.last;
        }
        float frameX = this.pendingX;
        float frameY = this.pendingY;
        this.pendingX = 0.0F;
        this.pendingY = 0.0F;
        float sign = settings.invertY ? 1.0F : -1.0F;
        float window = settings.smoothingMs / 1000.0F;
        float rateX;
        float rateY;
        boolean idleX;
        boolean idleY;
        if (window <= dt) {
            rateX = frameX / dt;
            rateY = sign * frameY / dt;
            idleX = frameX == 0.0F;
            idleY = frameY == 0.0F;
        } else {
            float[] sums = this.kernel(nowNanos, window);
            rateX = sums[0];
            rateY = sign * sums[1];
            idleX = sums[2] == 0.0F;
            idleY = sums[3] == 0.0F;
        }
        boolean holdForward = settings.mode.holds(selfLevel);
        boolean holdSide = holdForward && settings.xAxis == MouseAxis.ROLL;
        float x = this.axis(true, holdSide, frameX, rateX, idleX, dt, settings);
        float y = this.axis(false, holdForward, sign * frameY, rateY, idleY, dt, settings);
        float shapedX = shape(x, settings.deadzone, settings.expo);
        float shapedY = shape(y, settings.deadzone, settings.expo);
        Output out = settings.xAxis == MouseAxis.YAW
                ? new Output(0.0F, shapedY, shapedX, holdSide, holdForward)
                : new Output(shapedX, shapedY, 0.0F, holdSide, holdForward);
        this.last = out;
        return out;
    }

    private float[] kernel(long nowNanos, float window) {
        float sumX = 0.0F;
        float sumY = 0.0F;
        float movedX = 0.0F;
        float movedY = 0.0F;
        float half = window * 0.5F;
        float scale = 4.0F / (window * window);
        long oldest = Math.min(MAX_AGE_NANOS, (long) (window * 1.0E9F) + ORDER_SLACK_NANOS);
        int index = this.newest;
        for (int i = 0; i < this.filled; i++) {
            long ageNanos = nowNanos - this.times[index];
            if (ageNanos > oldest) {
                break;
            }
            float age = Math.max(0.0F, ageNanos / 1.0E9F);
            if (age <= window) {
                float weight = scale * (age <= half ? age : window - age);
                sumX += this.countsX[index] * weight;
                sumY += this.countsY[index] * weight;
                movedX += Math.abs(this.countsX[index]);
                movedY += Math.abs(this.countsY[index]);
            }
            index = (index - 1 + HISTORY) % HISTORY;
        }
        return new float[]{sumX, sumY, movedX, movedY};
    }

    private float axis(boolean xAxis, boolean hold, float counts, float countsPerSecond, boolean idle, float dt, MouseSettings settings) {
        boolean wasHolding = xAxis ? this.holdingX : this.holdingY;
        float position = xAxis ? this.holdX : this.holdY;
        if (!hold) {
            this.store(xAxis, false, 0.0F);
            return rate(countsPerSecond, settings);
        }
        if (!wasHolding) {
            position = 0.0F;
        }
        position = clamp(position + travel(counts, settings));
        if (idle && settings.returnSpeed > 0.0F) {
            float step = settings.returnSpeed * dt;
            position = Math.abs(position) <= step ? 0.0F : position - Math.signum(position) * step;
        }
        this.store(xAxis, true, position);
        return position;
    }

    private void store(boolean xAxis, boolean holding, float position) {
        if (xAxis) {
            this.holdingX = holding;
            this.holdX = position;
        } else {
            this.holdingY = holding;
            this.holdY = position;
        }
    }

    public static float rate(float countsPerSecond, MouseSettings settings) {
        float cmPerSecond = countsPerSecond * CM_PER_INCH / settings.dpi;
        return clamp(cmPerSecond * settings.sensitivity / RATE_FULL_CM_PER_SECOND);
    }

    public static float travel(float counts, MouseSettings settings) {
        return counts * CM_PER_INCH / settings.dpi * settings.sensitivity / HOLD_FULL_CM;
    }

    public static float shape(float value, float deadzone, float expo) {
        float v = clamp(value);
        float magnitude = Math.abs(v);
        float dz = ChannelMapping.clamp(deadzone, 0.0F, 0.95F);
        if (magnitude <= dz) {
            return 0.0F;
        }
        float scaled = (magnitude - dz) / (1.0F - dz);
        float e = ChannelMapping.clamp(expo, 0.0F, 1.0F);
        float curved = (1.0F - e) * scaled + e * scaled * scaled * scaled;
        return Math.copySign(curved, v);
    }

    private static float clamp(float v) {
        return Float.isNaN(v) ? 0.0F : ChannelMapping.clamp(v, -1.0F, 1.0F);
    }

    public record Output(float roll, float pitch, float yaw, boolean holdX, boolean holdY) {
        public static final Output CENTER = new Output(0.0F, 0.0F, 0.0F, false, false);
    }
}
