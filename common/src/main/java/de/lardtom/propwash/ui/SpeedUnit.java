package de.lardtom.propwash.ui;

import java.util.Locale;

public enum SpeedUnit {
    KMH(1.0, "km/h", false),
    MPH(0.621371192, "mph", true);

    public static final double KMH_PER_METER_PER_SECOND = 3.6;
    public static final double FEET_PER_METER = 3.280839895;
    public static final double FEET_PER_MILE = 5280.0;
    public static final double METERS_PER_KILOMETER = 1000.0;

    private final double perKmh;
    private final String symbol;
    private final boolean imperial;

    SpeedUnit(double perKmh, String symbol, boolean imperial) {
        this.perKmh = perKmh;
        this.symbol = symbol;
        this.imperial = imperial;
    }

    public double fromKmh(double kmh) {
        return kmh * this.perKmh;
    }

    public double fromMetersPerSecond(double ms) {
        return this.fromKmh(ms * KMH_PER_METER_PER_SECOND);
    }

    public boolean imperial() {
        return this.imperial;
    }

    public double lengthFromMeters(double meters) {
        return this.imperial ? meters * FEET_PER_METER : meters;
    }

    public double longDistanceFromMeters(double meters) {
        return this.imperial ? meters * FEET_PER_METER / FEET_PER_MILE : meters / METERS_PER_KILOMETER;
    }

    public String symbol() {
        return this.symbol;
    }

    public String format(double kmh) {
        if (!Double.isFinite(kmh)) {
            return "–";
        }
        return String.format(Locale.ROOT, "%.0f %s", this.fromKmh(kmh), this.symbol);
    }

    public String translationKey() {
        return "propwash.ui.unit." + this.name().toLowerCase(Locale.ROOT);
    }
}
