package de.lardtom.propwash.guide.training;

public record TrainingFrame(double x, double y, double z, float yaw, double scale) {
    public TrainingFrame {
        yaw = wrap(yaw);
        scale = Double.isFinite(scale) && scale > 0.0 ? scale : 1.0;
    }

    public static float wrap(double degrees) {
        double wrapped = degrees % 360.0;
        if (wrapped < 0.0) {
            wrapped += 360.0;
        }
        return (float) (wrapped >= 360.0 ? wrapped - 360.0 : wrapped);
    }

    public static double delta(double from, double to) {
        double d = (to - from) % 360.0;
        if (d > 180.0) {
            d -= 360.0;
        } else if (d < -180.0) {
            d += 360.0;
        }
        return d;
    }

    public static float yawOf(double dx, double dz) {
        return wrap(Math.toDegrees(Math.atan2(-dx, dz)));
    }

    private double radians() {
        return Math.toRadians(this.yaw);
    }

    public double forwardX() {
        return -Math.sin(this.radians());
    }

    public double forwardZ() {
        return Math.cos(this.radians());
    }

    public double rightX() {
        return -Math.cos(this.radians());
    }

    public double rightZ() {
        return -Math.sin(this.radians());
    }

    public double worldX(double localX, double localZ) {
        return this.x + (this.rightX() * localX + this.forwardX() * localZ) * this.scale;
    }

    public double worldY(double localY) {
        return this.y + localY * this.scale;
    }

    public double worldZ(double localX, double localZ) {
        return this.z + (this.rightZ() * localX + this.forwardZ() * localZ) * this.scale;
    }

    public double localX(double worldX, double worldZ) {
        double dx = worldX - this.x;
        double dz = worldZ - this.z;
        return (dx * this.rightX() + dz * this.rightZ()) / this.scale;
    }

    public double localY(double worldY) {
        return (worldY - this.y) / this.scale;
    }

    public double localZ(double worldX, double worldZ) {
        double dx = worldX - this.x;
        double dz = worldZ - this.z;
        return (dx * this.forwardX() + dz * this.forwardZ()) / this.scale;
    }

    public float worldYaw(double localHeading) {
        return wrap(this.yaw + localHeading);
    }

    public TrainingFrame rotated(double degrees) {
        return new TrainingFrame(this.x, this.y, this.z, wrap(this.yaw + degrees), this.scale);
    }

    public double length(double local) {
        return local * this.scale;
    }
}
