package de.lardtom.propwash.interact;

public final class StrikeMath {
    public static final double MIN_CLOSING_SPEED = 2.0;
    public static final double MIN_MOMENTUM = 0.4;
    public static final double DAMAGE_PER_MOMENTUM = 0.4;
    public static final double MAX_DAMAGE = 10.0;
    public static final double KNOCKBACK_PER_MOMENTUM = 0.05;
    public static final double MIN_KNOCKBACK = 0.1;
    public static final double MAX_KNOCKBACK = 1.2;

    private StrikeMath() {
    }

    public static Strike of(double massKg, double closingSpeed) {
        if (!(massKg > 0.0) || !Double.isFinite(massKg) || !Double.isFinite(closingSpeed) || closingSpeed < MIN_CLOSING_SPEED) {
            return Strike.NONE;
        }
        double momentum = massKg * closingSpeed;
        if (momentum < MIN_MOMENTUM) {
            return Strike.NONE;
        }
        double damage = Math.min(MAX_DAMAGE, DAMAGE_PER_MOMENTUM * momentum);
        double knockback = Math.max(MIN_KNOCKBACK, Math.min(MAX_KNOCKBACK, KNOCKBACK_PER_MOMENTUM * momentum));
        return new Strike(momentum, (float) damage, (float) knockback);
    }

    public static double closingSpeed(double rvx, double rvy, double rvz, double nx, double ny, double nz) {
        double length = Math.sqrt(nx * nx + ny * ny + nz * nz);
        if (!(length > 1.0E-9) || !Double.isFinite(length)) {
            return 0.0;
        }
        double along = (rvx * nx + rvy * ny + rvz * nz) / length;
        return Double.isFinite(along) ? Math.max(0.0, along) : 0.0;
    }

    public record Strike(double momentum, float damage, float knockback) {
        public static final Strike NONE = new Strike(0.0, 0.0F, 0.0F);

        public boolean hits() {
            return this.damage > 0.0F;
        }
    }
}
