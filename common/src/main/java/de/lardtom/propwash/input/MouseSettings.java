package de.lardtom.propwash.input;

public final class MouseSettings {
    public static final float DEFAULT_SENSITIVITY = 1.0F;
    public static final int DEFAULT_DPI = 800;
    public static final float DEFAULT_SMOOTHING_MS = 20.0F;
    public static final float DEFAULT_DEADZONE = 0.03F;
    public static final float DEFAULT_EXPO = 0.25F;
    public static final float DEFAULT_RETURN_SPEED = 0.4F;
    public static final float DEFAULT_WHEEL_STEP = 0.05F;
    public static final float MIN_SENSITIVITY = 0.1F;
    public static final float MAX_SENSITIVITY = 5.0F;
    public static final int MIN_DPI = 100;
    public static final int MAX_DPI = 32000;
    public static final float MAX_SMOOTHING_MS = 50.0F;
    public static final float MAX_DEADZONE = 0.25F;
    public static final float MAX_RETURN_SPEED = 3.0F;
    public static final float MAX_WHEEL_STEP = 0.2F;

    public MouseMode mode = MouseMode.AUTO;
    public MouseAxis xAxis = MouseAxis.ROLL;
    public float sensitivity = DEFAULT_SENSITIVITY;
    public int dpi = DEFAULT_DPI;
    public float smoothingMs = DEFAULT_SMOOTHING_MS;
    public float deadzone = DEFAULT_DEADZONE;
    public float expo = DEFAULT_EXPO;
    public float returnSpeed = DEFAULT_RETURN_SPEED;
    public boolean invertY = false;
    public float wheelStep = DEFAULT_WHEEL_STEP;

    public MouseSettings copy() {
        MouseSettings copy = new MouseSettings();
        copy.mode = this.mode;
        copy.xAxis = this.xAxis;
        copy.sensitivity = this.sensitivity;
        copy.dpi = this.dpi;
        copy.smoothingMs = this.smoothingMs;
        copy.deadzone = this.deadzone;
        copy.expo = this.expo;
        copy.returnSpeed = this.returnSpeed;
        copy.invertY = this.invertY;
        copy.wheelStep = this.wheelStep;
        return copy;
    }

    public void sanitize() {
        if (this.mode == null) {
            this.mode = MouseMode.AUTO;
        }
        if (this.xAxis == null) {
            this.xAxis = MouseAxis.ROLL;
        }
        this.sensitivity = finite(this.sensitivity, DEFAULT_SENSITIVITY, MIN_SENSITIVITY, MAX_SENSITIVITY);
        this.dpi = this.dpi <= 0 ? DEFAULT_DPI : Math.clamp(this.dpi, MIN_DPI, MAX_DPI);
        this.smoothingMs = finite(this.smoothingMs, DEFAULT_SMOOTHING_MS, 0.0F, MAX_SMOOTHING_MS);
        this.deadzone = finite(this.deadzone, DEFAULT_DEADZONE, 0.0F, MAX_DEADZONE);
        this.expo = finite(this.expo, DEFAULT_EXPO, 0.0F, 1.0F);
        this.returnSpeed = finite(this.returnSpeed, DEFAULT_RETURN_SPEED, 0.0F, MAX_RETURN_SPEED);
        this.wheelStep = finite(this.wheelStep, DEFAULT_WHEEL_STEP, 0.0F, MAX_WHEEL_STEP);
    }

    public boolean wheelThrottle() {
        return this.wheelStep > 0.0F;
    }

    private static float finite(float v, float fallback, float lo, float hi) {
        return Float.isFinite(v) ? ChannelMapping.clamp(v, lo, hi) : fallback;
    }
}
