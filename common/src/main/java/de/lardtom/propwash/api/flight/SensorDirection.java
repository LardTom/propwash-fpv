package de.lardtom.propwash.api.flight;

public enum SensorDirection {
    FORWARD(0.0, 0.0, -1.0),
    BACK(0.0, 0.0, 1.0),
    LEFT(-1.0, 0.0, 0.0),
    RIGHT(1.0, 0.0, 0.0),
    UP(0.0, 1.0, 0.0),
    DOWN(0.0, -1.0, 0.0);

    private final double x;
    private final double y;
    private final double z;

    SensorDirection(double x, double y, double z) {
        this.x = x;
        this.y = y;
        this.z = z;
    }

    public double x() {
        return this.x;
    }

    public double y() {
        return this.y;
    }

    public double z() {
        return this.z;
    }
}
