package de.lardtom.propwash.input;

import net.minecraft.util.Mth;

public record StickPositions(float roll, float pitch, float yaw, float throttle) {
    public static final StickPositions IDLE = new StickPositions(0.0F, 0.0F, 0.0F, 0.0F);

    public StickPositions clamped() {
        return new StickPositions(axis(this.roll), axis(this.pitch), axis(this.yaw), Float.isNaN(this.throttle) ? 0.0F : Mth.clamp(this.throttle, 0.0F, 1.0F));
    }

    private static float axis(float v) {
        return Float.isNaN(v) ? 0.0F : Mth.clamp(v, -1.0F, 1.0F);
    }
}
