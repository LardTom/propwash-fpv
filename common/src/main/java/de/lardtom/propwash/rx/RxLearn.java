package de.lardtom.propwash.rx;

import org.jspecify.annotations.Nullable;

import java.util.ArrayDeque;
import java.util.Deque;

public final class RxLearn {
    public static final double WINDOW_SECONDS = 1.5;
    public static final float MIN_TRAVEL = 0.3F;
    public static final float STICK_PREFERENCE = 0.8F;
    public static final double TIMEOUT_SECONDS = 12.0;

    private final Deque<Sample> samples = new ArrayDeque<>();
    private double started = Double.NaN;
    private @Nullable RxSource result;
    private boolean timedOut;

    public record Sample(double time, float[] sticks, float[] axes, boolean[] buttons) {
        public Sample {
            sticks = sticks == null ? new float[0] : sticks.clone();
            axes = axes == null ? new float[0] : axes.clone();
            buttons = buttons == null ? new boolean[0] : buttons.clone();
        }

        public float value(RxSource source) {
            return switch (source.kind()) {
                case STICK -> source.index() < this.sticks.length ? this.sticks[source.index()] : Float.NaN;
                case AXIS -> source.index() < this.axes.length ? this.axes[source.index()] : Float.NaN;
                case BUTTON -> source.index() < this.buttons.length ? (this.buttons[source.index()] ? 1.0F : 0.0F) : Float.NaN;
            };
        }
    }

    public record Movement(RxSource source, float travel) {
    }

    public void reset() {
        this.samples.clear();
        this.started = Double.NaN;
        this.result = null;
        this.timedOut = false;
    }

    public boolean done() {
        return this.result != null || this.timedOut;
    }

    public boolean timedOut() {
        return this.timedOut;
    }

    public @Nullable RxSource result() {
        return this.result;
    }

    public @Nullable RxSource sample(Sample sample) {
        if (this.done()) {
            return this.result;
        }
        if (Double.isNaN(this.started)) {
            this.started = sample.time();
        }
        this.samples.addLast(sample);
        while (!this.samples.isEmpty() && sample.time() - this.samples.peekFirst().time() > WINDOW_SECONDS) {
            this.samples.removeFirst();
        }
        Movement best = best(this.samples);
        if (best != null && best.travel() >= MIN_TRAVEL) {
            this.result = best.source();
        } else if (sample.time() - this.started > TIMEOUT_SECONDS) {
            this.timedOut = true;
        }
        return this.result;
    }

    public static @Nullable Movement best(Iterable<Sample> window) {
        Movement stick = null;
        Movement other = null;
        int axes = 0;
        int buttons = 0;
        for (Sample sample : window) {
            axes = Math.max(axes, Math.min(RxSource.AXES, sample.axes().length));
            buttons = Math.max(buttons, Math.min(RxSource.BUTTONS, sample.buttons().length));
        }
        for (RxSource source : RxSource.choices(axes, buttons)) {
            float travel = travel(window, source);
            if (!(travel > 0.0F)) {
                continue;
            }
            Movement movement = new Movement(source, travel);
            if (source.kind() == RxSource.Kind.STICK) {
                if (stick == null || travel > stick.travel()) {
                    stick = movement;
                }
            } else if (other == null || travel > other.travel()) {
                other = movement;
            }
        }
        if (stick == null) {
            return other;
        }
        if (other == null || stick.travel() >= STICK_PREFERENCE * other.travel()) {
            return stick;
        }
        return other;
    }

    public static float travel(Iterable<Sample> window, RxSource source) {
        float min = Float.POSITIVE_INFINITY;
        float max = Float.NEGATIVE_INFINITY;
        for (Sample sample : window) {
            float v = sample.value(source);
            if (Float.isFinite(v)) {
                min = Math.min(min, v);
                max = Math.max(max, v);
            }
        }
        if (!(max >= min)) {
            return 0.0F;
        }
        return (max - min) / source.kind().travel();
    }
}
