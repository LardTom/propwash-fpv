package de.lardtom.propwash.api.part;

public record AnchorSpec(double x, double y, double z, double tiltDeg, double lengthMm) {
    public static final double MIN_TILT = -45.0;
    public static final double MAX_TILT = 90.0;

    public AnchorSpec {
        if (!Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(z)) {
            throw new IllegalArgumentException("Anker-Position muss endlich sein");
        }
        if (!(tiltDeg >= MIN_TILT && tiltDeg <= MAX_TILT)) {
            throw new IllegalArgumentException("Anker-Neigung muss zwischen " + MIN_TILT + " und " + MAX_TILT + " Grad liegen: " + tiltDeg);
        }
        if (!(lengthMm >= 0.0 && lengthMm <= 2000.0)) {
            throw new IllegalArgumentException("Anker-Länge muss zwischen 0 und 2000 mm liegen: " + lengthMm);
        }
    }
}
