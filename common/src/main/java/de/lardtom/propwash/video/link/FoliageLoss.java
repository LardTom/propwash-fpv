package de.lardtom.propwash.video.link;

public final class FoliageLoss {
    public static final double IN_LEAF_A = 0.2;
    public static final double IN_LEAF_B = 1.27;
    public static final double IN_LEAF_C = 0.63;
    public static final double IN_LEAF_K0 = 6.57;
    public static final double IN_LEAF_RF = 0.0002;
    public static final double IN_LEAF_A0 = 10.0;
    public static final double ILLUMINATED_AREA_M2 = 10.0;
    public static final double INITIAL_SLOPE = initialSlope(RadioBand.FREQUENCY_GHZ);
    public static final double FINAL_SLOPE = finalSlope(RadioBand.FREQUENCY_GHZ);
    public static final double TURNOVER_DB = turnover(RadioBand.FREQUENCY_GHZ, ILLUMINATED_AREA_M2);
    public static final double MAX_DB = maxWoodlandDb(RadioBand.FREQUENCY_GHZ);

    private FoliageLoss() {
    }

    public static double initialSlope(double frequencyGhz) {
        return IN_LEAF_A * frequencyGhz;
    }

    public static double finalSlope(double frequencyGhz) {
        return IN_LEAF_B / Math.pow(frequencyGhz, IN_LEAF_C);
    }

    public static double turnover(double frequencyGhz, double illuminatedArea) {
        double area = 1.0 - Math.exp(-illuminatedArea / IN_LEAF_A0);
        double frequency = 1.0 - Math.exp(-IN_LEAF_RF * frequencyGhz);
        return IN_LEAF_K0 - 10.0 * Math.log10(IN_LEAF_A0 * area * frequency);
    }

    public static double maxWoodlandDb(double frequencyGhz) {
        double megahertz = frequencyGhz * 1000.0;
        double mulhouse = 1.15 * Math.pow(megahertz, 0.43);
        double petersburg = 1.37 * Math.pow(megahertz, 0.42);
        return 0.5 * (mulhouse + petersburg);
    }

    public static double db(double depthMeters) {
        if (!(depthMeters > 0.0)) {
            return 0.0;
        }
        double nzg = FINAL_SLOPE * depthMeters
                + TURNOVER_DB * (1.0 - Math.exp(-(INITIAL_SLOPE - FINAL_SLOPE) * depthMeters / TURNOVER_DB));
        return Math.min(nzg, MAX_DB);
    }
}
