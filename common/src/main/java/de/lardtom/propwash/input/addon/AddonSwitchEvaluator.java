package de.lardtom.propwash.input.addon;

import de.lardtom.propwash.api.input.SwitchRegistry;
import de.lardtom.propwash.api.input.SwitchSpec;
import de.lardtom.propwash.input.DeviceProfile;
import de.lardtom.propwash.input.SwitchAssignment;

import java.util.Arrays;
import java.util.List;

public final class AddonSwitchEvaluator {
    public static final int MAX = SwitchRegistry.MAX_SWITCHES;

    private final boolean[] previousRaw = new boolean[MAX];
    private final boolean[] latched = new boolean[MAX];
    private final boolean[] keyLatched = new boolean[MAX];
    private boolean primed;

    public void reset() {
        Arrays.fill(this.previousRaw, false);
        Arrays.fill(this.latched, false);
        this.primed = false;
    }

    public void resetKeys() {
        Arrays.fill(this.keyLatched, false);
    }

    public void copyFrom(AddonSwitchEvaluator other) {
        System.arraycopy(other.previousRaw, 0, this.previousRaw, 0, MAX);
        System.arraycopy(other.latched, 0, this.latched, 0, MAX);
        this.primed = other.primed;
    }

    public long evaluate(DeviceProfile profile, List<SwitchSpec> specs, float[] axes, boolean[] buttons) {
        long bits = 0L;
        int count = Math.min(MAX, specs.size());
        for (int i = 0; i < count; i++) {
            SwitchSpec spec = specs.get(i);
            SwitchAssignment binding = profile.addonBinding(spec.id());
            boolean raw = binding.engaged(axes, buttons);
            boolean active;
            if (binding.toggle && spec.toggle()) {
                if (!this.primed) {
                    this.latched[i] = binding.startOn;
                } else if (raw && !this.previousRaw[i]) {
                    this.latched[i] = !this.latched[i];
                }
                active = binding.flipsOutput() ? binding.readable(axes, buttons) && !this.latched[i] : this.latched[i];
            } else {
                this.latched[i] = false;
                active = binding.rawActive(axes, buttons);
            }
            this.previousRaw[i] = raw;
            if (active) {
                bits |= bit(i);
            }
        }
        this.primed = true;
        return bits;
    }

    public long keys(List<SwitchSpec> specs, boolean[] down, int[] clicks) {
        long bits = 0L;
        int count = Math.min(MAX, specs.size());
        for (int i = 0; i < count; i++) {
            boolean held = i < down.length && down[i];
            int pressed = i < clicks.length ? clicks[i] : 0;
            if (specs.get(i).toggle()) {
                if (pressed % 2 == 1) {
                    this.keyLatched[i] = !this.keyLatched[i];
                }
                if (this.keyLatched[i]) {
                    bits |= bit(i);
                }
            } else {
                this.keyLatched[i] = false;
                if (held || pressed > 0) {
                    bits |= bit(i);
                }
            }
        }
        return bits;
    }

    public static long readable(DeviceProfile profile, List<SwitchSpec> specs, float[] axes, boolean[] buttons) {
        long bits = 0L;
        int count = Math.min(MAX, specs.size());
        for (int i = 0; i < count; i++) {
            SwitchAssignment binding = profile.addonBinding(specs.get(i).id());
            if (binding.bound() && binding.readable(axes, buttons)) {
                bits |= bit(i);
            }
        }
        return bits;
    }

    public static long bit(int index) {
        return index < 0 || index >= MAX ? 0L : 1L << index;
    }

    public static boolean isSet(long bits, int index) {
        return (bits & bit(index)) != 0L;
    }
}
