package de.lardtom.propwash.video.record.path;

public enum KeyframeDensity {
    EVERY_TICK("every_tick", 0.0, 0.0),
    REDUCED("reduced", 0.01, 0.5);

    private final String key;
    private final double positionTolerance;
    private final double angleTolerance;

    KeyframeDensity(String key, double positionTolerance, double angleTolerance) {
        this.key = key;
        this.positionTolerance = positionTolerance;
        this.angleTolerance = angleTolerance;
    }

    public String key() {
        return this.key;
    }

    public double positionTolerance() {
        return this.positionTolerance;
    }

    public double angleTolerance() {
        return this.angleTolerance;
    }

    public String translationKey() {
        return "propwash.video.flashback.density." + this.key;
    }
}
