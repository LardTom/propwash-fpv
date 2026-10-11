package de.lardtom.propwash.guide.training;

public record Pad(double x, double z, double radius) {
    public static final double START_RADIUS = 1.2;

    public Pad {
        if (!(radius > 0.0)) {
            throw new IllegalArgumentException("Pad ohne Radius");
        }
    }
}
