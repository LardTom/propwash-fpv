package de.lardtom.propwash.api.accessory;

import de.lardtom.propwash.api.part.Vec3Mm;

import java.util.Objects;

public record ItemDisplay(Vec3Mm offsetMm, float yawDegrees, float pitchDegrees, float rollDegrees, float scale) {
    public static final double MAX_OFFSET_MM = 500.0;
    public static final float MAX_SCALE = 4.0F;

    public ItemDisplay {
        Objects.requireNonNull(offsetMm, "offsetMm");
        if (Math.abs(offsetMm.x()) > MAX_OFFSET_MM || Math.abs(offsetMm.y()) > MAX_OFFSET_MM || Math.abs(offsetMm.z()) > MAX_OFFSET_MM) {
            throw new IllegalArgumentException("Versatz außerhalb von ±" + (int) MAX_OFFSET_MM + " mm: " + offsetMm);
        }
        if (!Float.isFinite(yawDegrees) || !Float.isFinite(pitchDegrees) || !Float.isFinite(rollDegrees)) {
            throw new IllegalArgumentException("Drehung ungültig");
        }
        if (!(scale > 0.0F && scale <= MAX_SCALE)) {
            throw new IllegalArgumentException("Skalierung muss größer 0 und höchstens " + MAX_SCALE + " sein: " + scale);
        }
    }

    public static ItemDisplay at(double xMm, double yMm, double zMm) {
        return new ItemDisplay(new Vec3Mm(xMm, yMm, zMm), 0.0F, 0.0F, 0.0F, 1.0F);
    }

    public ItemDisplay rotated(float yaw, float pitch, float roll) {
        return new ItemDisplay(this.offsetMm, yaw, pitch, roll, this.scale);
    }

    public ItemDisplay scaled(float value) {
        return new ItemDisplay(this.offsetMm, this.yawDegrees, this.pitchDegrees, this.rollDegrees, value);
    }
}
