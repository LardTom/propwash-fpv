package de.lardtom.propwash.input;

public final class SwitchEvaluator {
    private static final SwitchFunction[] FUNCTIONS = SwitchFunction.values();

    private final boolean[] previousRaw = new boolean[FUNCTIONS.length];
    private final boolean[] latched = new boolean[FUNCTIONS.length];
    private boolean primed;

    public void reset() {
        for (int i = 0; i < FUNCTIONS.length; i++) {
            this.previousRaw[i] = false;
            this.latched[i] = false;
        }
        this.primed = false;
    }

    public int evaluate(DeviceProfile profile, float[] axes, boolean[] buttons) {
        int bits = 0;
        for (int i = 0; i < FUNCTIONS.length; i++) {
            SwitchAssignment binding = profile.binding(FUNCTIONS[i]);
            boolean raw = binding.engaged(axes, buttons);
            boolean active;
            if (binding.toggle) {
                if (!this.primed) {
                    this.latched[i] = binding.startOn;
                } else if (raw && !this.previousRaw[i]) {
                    this.latched[i] = !this.latched[i];
                }
                boolean flips = binding.flipsOutput();
                active = flips ? binding.readable(axes, buttons) && !this.latched[i] : this.latched[i];
            } else {
                this.latched[i] = false;
                active = binding.rawActive(axes, buttons);
            }
            this.previousRaw[i] = raw;
            if (active) {
                bits |= bit(FUNCTIONS[i]);
            }
        }
        this.primed = true;
        return bits;
    }

    public static int readable(DeviceProfile profile, float[] axes, boolean[] buttons) {
        int bits = 0;
        for (SwitchFunction function : FUNCTIONS) {
            SwitchAssignment binding = profile.binding(function);
            if (binding.bound() && binding.readable(axes, buttons)) {
                bits |= bit(function);
            }
        }
        return bits;
    }

    public static int bit(SwitchFunction function) {
        return 1 << function.ordinal();
    }

    public static boolean isSet(int bits, SwitchFunction function) {
        return (bits & bit(function)) != 0;
    }
}
