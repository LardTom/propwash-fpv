package de.lardtom.propwash.api.part;

public record Vec3Mm(double x, double y, double z) {
    public static final Vec3Mm ZERO = new Vec3Mm(0.0, 0.0, 0.0);

    public Vec3Mm {
        if (!Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(z)) {
            throw new IllegalArgumentException("Position muss endlich sein: " + x + ", " + y + ", " + z);
        }
    }

    public double horizontalDistance() {
        return Math.sqrt(this.x * this.x + this.z * this.z);
    }
}
