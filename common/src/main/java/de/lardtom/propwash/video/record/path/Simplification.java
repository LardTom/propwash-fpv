package de.lardtom.propwash.video.record.path;

public record Simplification(double position, double angle, int span) {
    public static final double MIN_POSITION = 0.005;
    public static final double MAX_POSITION = 0.20;
    public static final double DEGREES_PER_METRE = 50.0;
    public static final double SPAN_TICKS_PER_METRE = 500.0;
    public static final int MIN_SPAN = 10;
    public static final int MAX_SPAN = 40;
    public static final double REFINE_FACTOR = 1.5;
    public static final Simplification OFF = new Simplification(0.0, 0.0, 1);
    public static final double REDUCED_POSITION = 0.01;
    public static final double DEFAULT_SLIDER = slider(REDUCED_POSITION);

    public Simplification {
        position = Math.max(0.0, position);
        angle = Math.max(0.0, angle);
        span = Math.max(1, span);
    }

    public static Simplification ofSlider(double slider) {
        if (!(slider > 0.0)) {
            return OFF;
        }
        double s = Math.min(1.0, slider);
        return ofPosition(MIN_POSITION * Math.pow(MAX_POSITION / MIN_POSITION, s));
    }

    public static Simplification ofPosition(double position) {
        if (!(position > 0.0)) {
            return OFF;
        }
        return new Simplification(position, position * DEGREES_PER_METRE, spanFor(position));
    }

    public static int spanFor(double position) {
        return (int) Math.max(MIN_SPAN, Math.min(MAX_SPAN, Math.round(position * SPAN_TICKS_PER_METRE)));
    }

    public static double slider(double position) {
        if (!(position > 0.0)) {
            return 0.0;
        }
        double s = Math.log(position / MIN_POSITION) / Math.log(MAX_POSITION / MIN_POSITION);
        return Math.max(0.0, Math.min(1.0, s));
    }

    public static double sliderOf(KeyframeDensity density) {
        return density == KeyframeDensity.REDUCED ? DEFAULT_SLIDER : 0.0;
    }

    public boolean off() {
        return this.span <= 1 || this.position <= 0.0;
    }

    public Simplification coarser(double factor) {
        double base = this.off() ? MIN_POSITION : this.position;
        double scaled = base * Math.max(1.0, factor);
        double angleBase = this.off() ? MIN_POSITION * DEGREES_PER_METRE : this.angle;
        return new Simplification(scaled, angleBase * Math.max(1.0, factor), Math.max(this.off() ? MIN_SPAN : this.span, spanFor(scaled)));
    }

    public double centimetres() {
        return this.position * 100.0;
    }
}
