package de.lardtom.propwash.video;

public final class AnalogBreakup {
    public static final double CLEAN_ABOVE = 0.80;
    public static final double SPARKLE_BELOW = 0.78;
    public static final double JITTER_BELOW = 0.72;
    public static final double COLOUR_BELOW = 0.60;
    public static final double CHROMA_FADE_BELOW = 0.58;
    public static final double GREY_BELOW = 0.50;
    public static final double SNOW_BELOW = 0.40;
    public static final double GRAIN_BELOW = 0.56;
    public static final double GRAIN = 0.14;
    public static final double SYNC_BELOW = 0.36;
    public static final double STATIC_BELOW = 0.10;
    public static final double STATIC_FULL = 0.03;
    public static final double SNOW_FULL = 0.08;
    public static final double MAX_GLITCH_RATE = 18.0;
    public static final double MAX_SHIFT_RATE = 5.0;
    public static final double MAX_ROLL_RATE = 0.8;
    public static final double MAX_COLOUR_FLICKER_RATE = 2.5;
    public static final double MAX_LINE_JITTER = 0.004;
    public static final double MAX_SNOW = 0.85;

    private AnalogBreakup() {
    }

    public static Stage stage(double link) {
        double q = clamp(link);
        if (q >= CLEAN_ABOVE) {
            return Stage.CLEAN;
        }
        if (q >= COLOUR_BELOW) {
            return Stage.GLITCHES;
        }
        if (q >= SNOW_BELOW) {
            return Stage.COLOUR_LOSS;
        }
        if (q >= STATIC_BELOW) {
            return Stage.SNOW;
        }
        return Stage.STATIC;
    }

    public static double softness(double link) {
        return ramp(1.0, 0.3, link);
    }

    public static double glitchRate(double link) {
        return MAX_GLITCH_RATE * ramp(CLEAN_ABOVE, 0.25, link);
    }

    public static double sparkle(double link) {
        return Math.pow(ramp(SPARKLE_BELOW, 0.15, link), 1.3);
    }

    public static double lineJitter(double link) {
        return MAX_LINE_JITTER * ramp(JITTER_BELOW, 0.15, link);
    }

    public static double chromaBands(double link) {
        return ramp(COLOUR_BELOW, GREY_BELOW, link);
    }

    public static double colour(double link) {
        return 1.0 - ramp(CHROMA_FADE_BELOW, GREY_BELOW, link);
    }

    public static double chromaNoise(double link) {
        return ramp(JITTER_BELOW, CHROMA_FADE_BELOW, link) * colour(link);
    }

    public static double colourFlickerRate(double link) {
        double c = colour(link);
        return MAX_COLOUR_FLICKER_RATE * 4.0 * c * (1.0 - c);
    }

    public static double snow(double link) {
        return GRAIN * ramp(GRAIN_BELOW, SNOW_BELOW, link) + (MAX_SNOW - GRAIN) * ramp(SNOW_BELOW, SNOW_FULL, link);
    }

    public static double sync(double link) {
        return ramp(SYNC_BELOW, SNOW_FULL, link);
    }

    public static double shiftRate(double link) {
        return MAX_SHIFT_RATE * sync(link);
    }

    public static double rollRate(double link) {
        return MAX_ROLL_RATE * Math.pow(sync(link), 1.5);
    }

    public static double staticMix(double link) {
        return ramp(STATIC_BELOW, STATIC_FULL, link);
    }

    public static Levels levels(double link) {
        return new Levels(stage(link), glitchRate(link), sparkle(link), lineJitter(link), chromaBands(link), colour(link),
                chromaNoise(link), snow(link), sync(link), staticMix(link));
    }

    private static double ramp(double from, double to, double link) {
        return LinkBudget.smoothstep(from, to, clamp(link));
    }

    private static double clamp(double link) {
        if (Double.isNaN(link)) {
            return 0.0;
        }
        return Math.min(Math.max(link, 0.0), 1.0);
    }

    public record Levels(Stage stage, double glitchRate, double sparkle, double lineJitter, double chromaBands, double colour,
                         double chromaNoise, double snow, double sync, double staticMix) {
    }

    public enum Stage {
        CLEAN,
        GLITCHES,
        COLOUR_LOSS,
        SNOW,
        STATIC;

        public int number() {
            return this.ordinal() + 1;
        }
    }
}
