package de.lardtom.propwash.drone;

public final class PilotLook {
    public static final float FPV_HEAD_PITCH = 20.0F;
    public static final double HEAD_SETTLE_SECONDS = 0.25;
    public static final double MAX_HEAD_STEP_SECONDS = 0.1;
    private static final float SETTLED = 1.0E-3F;

    private PilotLook() {
    }

    public static boolean viewFollowsDrone(PilotMode mode) {
        return mode == PilotMode.LINE_OF_SIGHT;
    }

    public static LosAim.Angles view(PilotMode mode, float yaw, float pitch, double dx, double dy, double dz, double dtSeconds) {
        if (!viewFollowsDrone(mode)) {
            return new LosAim.Angles(yaw, pitch);
        }
        return LosAim.aim(yaw, pitch, dx, dy, dz, dtSeconds);
    }

    public static float headWeight(float weight, boolean fpv, double dtSeconds) {
        float current = clamp01(weight);
        if (!Double.isFinite(dtSeconds) || dtSeconds <= 0.0) {
            return current;
        }
        float target = fpv ? 1.0F : 0.0F;
        double dt = Math.min(dtSeconds, MAX_HEAD_STEP_SECONDS);
        double alpha = 1.0 - Math.exp(-dt / HEAD_SETTLE_SECONDS);
        float next = (float) (current + (target - current) * alpha);
        return Math.abs(target - next) < SETTLED ? target : clamp01(next);
    }

    public static float headPitch(float pitch, float weight, boolean upsideDown) {
        float target = upsideDown ? -FPV_HEAD_PITCH : FPV_HEAD_PITCH;
        return pitch + (target - pitch) * clamp01(weight);
    }

    private static float clamp01(float value) {
        if (!(value > 0.0F)) {
            return 0.0F;
        }
        return Math.min(1.0F, value);
    }
}
