package de.lardtom.propwash.input;

public final class SwitchDetector {
    public static final float AXIS_THRESHOLD = 0.45F;
    public static final float STABLE_TOLERANCE = 0.08F;
    public static final long HOLD_NANOS = 200_000_000L;
    public static final long BUTTON_HOLD_NANOS = 50_000_000L;

    private final float[] baselineAxes;
    private final boolean[] baselineButtons;
    private final boolean[] excludedAxes;
    private SwitchSource candidateSource = SwitchSource.NONE;
    private int candidateIndex = -1;
    private float candidateValue;
    private boolean candidateState;
    private long candidateSince;

    public SwitchDetector(float[] axes, boolean[] buttons, boolean[] excludedAxes) {
        this.baselineAxes = axes.clone();
        this.baselineButtons = buttons.clone();
        this.excludedAxes = excludedAxes == null ? new boolean[0] : excludedAxes.clone();
    }

    public SwitchSource candidateSource() {
        return this.candidateSource;
    }

    public int candidateIndex() {
        return this.candidateIndex;
    }

    public SwitchAssignment update(float[] axes, boolean[] buttons, long nanos) {
        int changed = this.changedButton(buttons, true);
        if (changed < 0) {
            changed = this.changedButton(buttons, false);
        }
        if (changed >= 0) {
            boolean state = buttons[changed];
            if (this.candidateSource != SwitchSource.BUTTON || this.candidateIndex != changed || this.candidateState != state) {
                this.start(SwitchSource.BUTTON, changed, 0.0F, state, nanos);
                return null;
            }
            if (nanos - this.candidateSince >= BUTTON_HOLD_NANOS) {
                return SwitchAssignment.button(changed, !state);
            }
            return null;
        }
        int axisCount = Math.min(axes.length, this.baselineAxes.length);
        int best = -1;
        float bestDelta = 0.0F;
        for (int i = 0; i < axisCount; i++) {
            if ((i < this.excludedAxes.length && this.excludedAxes[i]) || Float.isNaN(axes[i])) {
                continue;
            }
            float delta = Math.abs(axes[i] - this.baselineAxes[i]);
            if (delta > bestDelta) {
                bestDelta = delta;
                best = i;
            }
        }
        if (best < 0 || bestDelta < AXIS_THRESHOLD) {
            this.start(SwitchSource.NONE, -1, 0.0F, false, nanos);
            return null;
        }
        float value = axes[best];
        if (this.candidateSource != SwitchSource.AXIS || this.candidateIndex != best || Math.abs(value - this.candidateValue) > STABLE_TOLERANCE) {
            this.start(SwitchSource.AXIS, best, value, false, nanos);
            return null;
        }
        if (nanos - this.candidateSince >= HOLD_NANOS) {
            return SwitchAssignment.axisAround(best, value);
        }
        return null;
    }

    private int changedButton(boolean[] buttons, boolean pressed) {
        int count = Math.min(buttons.length, this.baselineButtons.length);
        for (int i = 0; i < count; i++) {
            if (buttons[i] == pressed && this.baselineButtons[i] != pressed) {
                return i;
            }
        }
        return -1;
    }

    private void start(SwitchSource source, int index, float value, boolean state, long nanos) {
        this.candidateSource = source;
        this.candidateIndex = index;
        this.candidateValue = value;
        this.candidateState = state;
        this.candidateSince = nanos;
    }
}
