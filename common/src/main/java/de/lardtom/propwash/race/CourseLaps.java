package de.lardtom.propwash.race;

public final class CourseLaps {
    public static final int UNSET = -1;
    public static final int ENDLESS = 0;
    public static final int MAX = 99;
    public static final int FALLBACK = 3;

    private CourseLaps() {
    }

    public static boolean set(int laps) {
        return laps >= ENDLESS;
    }

    public static int clamp(int laps) {
        return Math.max(ENDLESS, Math.min(MAX, laps));
    }

    public static int stored(int laps) {
        return laps < ENDLESS ? UNSET : Math.min(MAX, laps);
    }

    public static int resolve(int course, int personal) {
        return set(course) ? Math.min(MAX, course) : clamp(personal);
    }

    public static boolean finishAllowed(int course, int flown) {
        return course <= ENDLESS || flown >= Math.min(MAX, course);
    }

    public static int step(int laps, int direction) {
        return clamp(clamp(laps) + Integer.signum(direction));
    }
}
