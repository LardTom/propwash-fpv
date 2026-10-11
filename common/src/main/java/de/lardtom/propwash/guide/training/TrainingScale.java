package de.lardtom.propwash.guide.training;

public record TrainingScale(String key, double geometry, double time) {
    public static final int SMALL_WHEELBASE_MM = 100;
    public static final int MEDIUM_WHEELBASE_MM = 180;
    public static final TrainingScale SMALL = new TrainingScale("small", 0.6, 1.25);
    public static final TrainingScale MEDIUM = new TrainingScale("medium", 0.8, 1.1);
    public static final TrainingScale FULL = new TrainingScale("full", 1.0, 1.0);

    public static TrainingScale of(int wheelbaseMm) {
        if (wheelbaseMm > 0 && wheelbaseMm < SMALL_WHEELBASE_MM) {
            return SMALL;
        }
        if (wheelbaseMm > 0 && wheelbaseMm < MEDIUM_WHEELBASE_MM) {
            return MEDIUM;
        }
        return FULL;
    }
}
