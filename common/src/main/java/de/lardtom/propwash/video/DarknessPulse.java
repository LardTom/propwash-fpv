package de.lardtom.propwash.video;

public final class DarknessPulse {
    public static final double RADIUS = 20.0;
    public static final int BLEND_TICKS = 22;
    public static final int HOLD_TICKS = 140;
    public static final float LIGHT_DEPTH = 0.45F;
    public static final float PULSE_RATE = 0.025F;
    public static final float FEED_BRIGHTNESS = 0.45F;
    public static final float FEED_NOISE = 0.35F;
    public static final float FEED_VIGNETTE = 0.55F;

    private int hold;
    private float blend;
    private float previous;

    public void tick(boolean near) {
        this.previous = this.blend;
        if (near) {
            this.hold = HOLD_TICKS;
        } else if (this.hold > 0) {
            this.hold--;
        }
        float target = this.hold > 0 ? 1.0F : 0.0F;
        float step = 1.0F / BLEND_TICKS;
        this.blend += Math.max(-step, Math.min(step, target - this.blend));
    }

    public float blend() {
        return this.blend;
    }

    public float blend(float partialTick) {
        float t = Math.max(0.0F, Math.min(1.0F, partialTick));
        return this.previous + (this.blend - this.previous) * t;
    }

    public boolean active() {
        return this.blend > 0.0F || this.hold > 0;
    }

    public void reset() {
        this.hold = 0;
        this.blend = 0.0F;
        this.previous = 0.0F;
    }

    public static float lightScale(float blend, float ticks) {
        float b = Math.max(0.0F, Math.min(1.0F, blend));
        return Math.max(0.0F, (float) Math.cos(ticks * Math.PI * PULSE_RATE) * LIGHT_DEPTH * b);
    }

    public static float brightness(float gamma, float blend) {
        return Math.max(0.0F, gamma - Math.max(0.0F, Math.min(1.0F, blend)));
    }

    public static boolean near(double dx, double dy, double dz) {
        return dx * dx + dy * dy + dz * dz <= RADIUS * RADIUS;
    }
}
