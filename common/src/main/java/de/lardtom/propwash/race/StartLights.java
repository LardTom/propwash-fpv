package de.lardtom.propwash.race;

public final class StartLights {
    public static final int MIN_LAMPS = 3;
    public static final int MAX_LAMPS = 5;

    private StartLights() {
    }

    public static int lamps(long totalNanos) {
        long seconds = (Math.max(0L, totalNanos) + LapTracker.SECOND - 1L) / LapTracker.SECOND;
        return (int) Math.clamp(seconds, MIN_LAMPS, MAX_LAMPS);
    }

    public static int lit(long totalNanos, long remainingNanos) {
        int lamps = lamps(totalNanos);
        long seconds = (Math.max(0L, remainingNanos) + LapTracker.SECOND - 1L) / LapTracker.SECOND;
        return (int) Math.clamp(lamps - seconds + 1L, 0L, lamps);
    }
}
