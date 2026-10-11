package de.lardtom.propwash.video;

public final class DigitalBreakup {
    public static final double CLEAN_ABOVE = 0.85;
    public static final double SMOOTH_ABOVE = 0.7;
    public static final double FREEZE_BELOW = 0.3;
    public static final double LOSS_ENTER = 0.06;
    public static final double LOSS_EXIT = 0.12;
    public static final double RESOLUTION_HYSTERESIS = 0.04;
    private static final double[] RESOLUTION_STEPS = {1.0, 0.66, 0.5, 0.33};
    private static final double[] RESOLUTION_BELOW = {0.75, 0.55, 0.35};

    private DigitalBreakup() {
    }

    public static Stage stage(double link) {
        double q = clamp(link);
        if (q >= CLEAN_ABOVE) {
            return Stage.CLEAN;
        }
        if (q >= SMOOTH_ABOVE) {
            return Stage.SOFT;
        }
        if (q >= FREEZE_BELOW) {
            return Stage.BLOCKY;
        }
        if (q >= LOSS_ENTER) {
            return Stage.FREEZES;
        }
        return Stage.LOST;
    }

    public static double blur(double link) {
        return LinkBudget.smoothstep(CLEAN_ABOVE, 0.3, clamp(link));
    }

    public static double blockiness(double link) {
        return LinkBudget.smoothstep(SMOOTH_ABOVE, 0.2, clamp(link));
    }

    public static double blockPx(double link) {
        return 8.0 + blockiness(link) * 24.0;
    }

    public static double posterize(double link) {
        double q = clamp(link);
        return q < 0.55 ? lerp(12.0, 64.0, LinkBudget.smoothstep(0.1, 0.55, q)) : 0.0;
    }

    public static double frameArrival(double link) {
        return lerp(0.3, 1.0, LinkBudget.smoothstep(0.15, SMOOTH_ABOVE, clamp(link)));
    }

    public static double burstStart(double link) {
        return LinkBudget.smoothstep(0.6, 0.1, clamp(link)) * 0.02;
    }

    public static double burstEnd(double link) {
        return lerp(0.012, 0.25, LinkBudget.smoothstep(0.1, 0.6, clamp(link)));
    }

    public static int resolutionLevels() {
        return RESOLUTION_STEPS.length;
    }

    public static double resolutionScale(int level) {
        return RESOLUTION_STEPS[Math.min(Math.max(level, 0), RESOLUTION_STEPS.length - 1)];
    }

    public static int targetLevel(double link) {
        double q = clamp(link);
        int level = 0;
        for (double below : RESOLUTION_BELOW) {
            if (q < below) {
                level++;
            }
        }
        return level;
    }

    public static int resolutionLevel(double link, int current) {
        int down = targetLevel(link);
        if (down > current) {
            return down;
        }
        int up = targetLevel(clamp(link) - RESOLUTION_HYSTERESIS);
        return Math.min(Math.max(up, 0), Math.min(current, RESOLUTION_STEPS.length - 1));
    }

    private static double lerp(double a, double b, double t) {
        return a + (b - a) * t;
    }

    private static double clamp(double link) {
        if (Double.isNaN(link)) {
            return 0.0;
        }
        return Math.min(Math.max(link, 0.0), 1.0);
    }

    public enum Stage {
        CLEAN,
        SOFT,
        BLOCKY,
        FREEZES,
        LOST
    }
}
