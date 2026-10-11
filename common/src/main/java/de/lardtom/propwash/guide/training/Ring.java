package de.lardtom.propwash.guide.training;

public record Ring(double x, double y, double z, double heading, double width, double height, boolean flat) {
    public static final double FRAME = 0.25;
    public static final double DEPTH = 0.25;

    public Ring {
        if (!(width > 2.0 * FRAME) || !(height > 2.0 * FRAME)) {
            throw new IllegalArgumentException("Ring zu klein: " + width + " × " + height);
        }
    }

    public static Ring standing(double x, double y, double z, double heading, double size) {
        return new Ring(x, y, z, heading, size, size, false);
    }

    public static Ring flat(double x, double y, double z, double heading, double size) {
        return new Ring(x, y, z, heading, size, size, true);
    }

    public double directionX() {
        return this.flat ? 0.0 : Math.sin(Math.toRadians(this.heading));
    }

    public double directionY() {
        return this.flat ? -1.0 : 0.0;
    }

    public double directionZ() {
        return this.flat ? 0.0 : Math.cos(Math.toRadians(this.heading));
    }

    public double opening() {
        return Math.min(this.width, this.height) - 2.0 * FRAME;
    }

    public double reach() {
        return Math.max(this.width, this.height) * 0.5;
    }
}
