package de.lardtom.propwash.input;

public final class StickMapper {
    public static final float THROTTLE_SNAP = 0.005F;

    private final ThrottleCentering centering = new ThrottleCentering();
    private float integratedThrottle;
    private float throttle3d = ThrottleCentering.CENTER;

    public void reset() {
        this.integratedThrottle = 0.0F;
        this.centering.reset();
        this.throttle3d = ThrottleCentering.CENTER;
    }

    public float integratedThrottle() {
        return this.integratedThrottle;
    }

    public float throttle3d() {
        return this.throttle3d;
    }

    public StickPositions map(DeviceProfile profile, float[] axes, float dtSeconds) {
        return this.map(profile, axes, dtSeconds, false, false);
    }

    public StickPositions map(DeviceProfile profile, float[] axes, float dtSeconds, boolean centered, boolean armed) {
        float roll = centered(profile.roll, axes, profile.deadband);
        float pitch = centered(profile.pitch, axes, profile.deadband);
        float yaw = centered(profile.yaw, axes, profile.deadband);
        float throttle = this.throttle(profile, axes, dtSeconds, centered, armed);
        this.throttle3d = profile.throttleMode == ThrottleMode.INCREMENTAL && profile.throttle.readable(axes) ? throttle
                : throttle3d(profile, axes);
        return new StickPositions(roll, pitch, yaw, throttle).clamped();
    }

    public static float throttle3d(DeviceProfile profile, float[] axes) {
        ChannelMapping m = profile.throttle;
        if (!m.readable(axes)) {
            return ThrottleCentering.CENTER;
        }
        float centered = profile.throttleMode == ThrottleMode.UPPER_HALF ? applyDeadband(m.centered(axes[m.axis]), profile.deadband)
                : m.centered(axes[m.axis]);
        if (Float.isNaN(centered)) {
            return ThrottleCentering.CENTER;
        }
        return snapThrottle(ThrottleCentering.CENTER + ThrottleCentering.CENTER * centered);
    }

    public static float centered(ChannelMapping mapping, float[] axes, float deadband) {
        if (!mapping.readable(axes)) {
            return 0.0F;
        }
        return applyDeadband(mapping.centered(axes[mapping.axis]), deadband);
    }

    public static float applyDeadband(float value, float deadband) {
        if (Float.isNaN(value)) {
            return 0.0F;
        }
        float magnitude = Math.abs(value);
        if (magnitude <= deadband) {
            return 0.0F;
        }
        if (deadband >= 1.0F) {
            return 0.0F;
        }
        float scaled = Math.min(1.0F, (magnitude - deadband) / (1.0F - deadband));
        return Math.copySign(scaled, value);
    }

    public static float snapThrottle(float t) {
        if (Float.isNaN(t) || t <= THROTTLE_SNAP) {
            return 0.0F;
        }
        return t >= 1.0F - THROTTLE_SNAP ? 1.0F : t;
    }

    public static float staticThrottle(DeviceProfile profile, float[] axes) {
        ChannelMapping m = profile.throttle;
        if (!m.readable(axes)) {
            return 0.0F;
        }
        return switch (profile.throttleMode) {
            case FULL_RANGE, INCREMENTAL -> snapThrottle(m.linear(axes[m.axis]));
            case UPPER_HALF -> snapThrottle(Math.max(0.0F, applyDeadband(m.centered(axes[m.axis]), profile.deadband)));
        };
    }

    private float throttle(DeviceProfile profile, float[] axes, float dtSeconds, boolean centered, boolean armed) {
        ChannelMapping m = profile.throttle;
        if (!m.readable(axes)) {
            this.integratedThrottle = 0.0F;
            return 0.0F;
        }
        if (profile.throttleMode != ThrottleMode.INCREMENTAL) {
            return staticThrottle(profile, axes);
        }
        float dt = Float.isFinite(dtSeconds) ? ChannelMapping.clamp(dtSeconds, 0.0F, 0.1F) : 0.0F;
        float rate = applyDeadband(m.centered(axes[m.axis]), Math.max(profile.deadband, 0.05F));
        float previous = this.integratedThrottle;
        float next = ChannelMapping.clamp(previous + rate * profile.throttleRate * dt, 0.0F, 1.0F);
        this.integratedThrottle = this.centering.apply(previous, next, rate != 0.0F, centered, armed);
        return this.integratedThrottle;
    }
}
