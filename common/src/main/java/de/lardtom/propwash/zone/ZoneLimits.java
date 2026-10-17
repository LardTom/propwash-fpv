package de.lardtom.propwash.zone;

public final class ZoneLimits {
    public static final int MAX_ZONES = 128;
    public static final int MAX_NAME = 32;
    public static final int MIN_SIZE = 1;
    public static final int MAX_SIZE = 1024;
    public static final int MIN_Y = -4096;
    public static final int MAX_Y = 4096;
    public static final int MAX_COORDINATE = 30_000_000;
    public static final int MIN_PRIORITY = -99;
    public static final int MAX_PRIORITY = 99;
    public static final int MAX_TRANSITION_TENTHS = 50;
    public static final int DEFAULT_TRANSITION_TENTHS = 5;
    public static final int MAX_DIMENSION = 128;

    private ZoneLimits() {
    }
}
