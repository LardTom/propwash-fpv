package de.lardtom.propwash.osd;

import de.lardtom.propwash.build.VideoSystem;
import org.jspecify.annotations.Nullable;

public final class OsdInterference {
    public static final double JITTER_BELOW = 0.3;
    public static final float MAX_JITTER = 1.0F;
    public static final double SHIFT_SHARE = 0.12;
    public static final float MAX_SHIFT = 3.0F;

    private OsdInterference() {
    }

    public static float jitterAmplitude(@Nullable VideoSystem system, double signal) {
        if (system != VideoSystem.ANALOG || !Double.isFinite(signal) || signal >= JITTER_BELOW) {
            return 0.0F;
        }
        return (float) Math.min(MAX_JITTER, (JITTER_BELOW - Math.max(0.0, signal)) / JITTER_BELOW * MAX_JITTER);
    }

    public static float sharedShift(@Nullable VideoSystem system, double videoShift, int screenWidth) {
        if (system != VideoSystem.ANALOG || !Double.isFinite(videoShift) || screenWidth <= 0) {
            return 0.0F;
        }
        float shift = (float) (videoShift * screenWidth * SHIFT_SHARE);
        return Math.min(Math.max(shift, -MAX_SHIFT), MAX_SHIFT);
    }

    public static double noise(int element, long frame) {
        long h = frame * 0x9E3779B97F4A7C15L + element * 0xC2B2AE3D27D4EB4FL;
        h ^= h >>> 33;
        h *= 0xFF51AFD7ED558CCDL;
        h ^= h >>> 33;
        h *= 0xC4CEB9FE1A85EC53L;
        h ^= h >>> 33;
        return (h >>> 11) * 0x1.0p-53;
    }
}
