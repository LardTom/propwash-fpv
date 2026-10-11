package de.lardtom.propwash.guide.training;

public record StarRule(Metric metric, double three, double two) {
    public static final int MAX_STARS = 3;

    public enum Metric {
        TIME,
        PAD_DISTANCE
    }

    public StarRule {
        if (!(three > 0.0) || !(two >= three)) {
            throw new IllegalArgumentException("Sterne-Grenzen falsch: " + three + " / " + two);
        }
    }

    public static StarRule time(double three, double two) {
        return new StarRule(Metric.TIME, three, two);
    }

    public static StarRule padDistance(double three, double two) {
        return new StarRule(Metric.PAD_DISTANCE, three, two);
    }

    public double threeFor(TrainingScale scale) {
        return this.three * this.factor(scale);
    }

    public double twoFor(TrainingScale scale) {
        return this.two * this.factor(scale);
    }

    private double factor(TrainingScale scale) {
        return this.metric == Metric.TIME ? scale.time() : scale.geometry();
    }

    public int stars(double value, TrainingScale scale, boolean assisted) {
        if (!Double.isFinite(value)) {
            return 1;
        }
        int stars = value <= this.threeFor(scale) + 1.0E-9 ? 3 : value <= this.twoFor(scale) + 1.0E-9 ? 2 : 1;
        return assisted ? Math.min(1, stars) : stars;
    }
}
