package de.lardtom.propwash.input;

import org.jspecify.annotations.Nullable;

public final class TurtleSelector {
    private boolean selected;
    private boolean fromKey;
    private @Nullable Boolean lastSwitch;

    public boolean selected() {
        return this.selected;
    }

    public boolean fromKey() {
        return this.fromKey;
    }

    public static @Nullable Boolean fromSwitches(int active, int readable) {
        if (!SwitchEvaluator.isSet(readable, SwitchFunction.TURTLE)) {
            return null;
        }
        return SwitchEvaluator.isSet(active, SwitchFunction.TURTLE);
    }

    public boolean observe(boolean device, int active, int readable) {
        if (!device) {
            return false;
        }
        Boolean fromSwitch = fromSwitches(active, readable);
        if (fromSwitch == null ? this.lastSwitch == null : fromSwitch.equals(this.lastSwitch)) {
            return false;
        }
        this.lastSwitch = fromSwitch;
        if (fromSwitch != null) {
            return this.set(fromSwitch, false);
        }
        return !this.fromKey && this.set(false, false);
    }

    public boolean toggle() {
        this.set(!this.selected, true);
        return this.selected;
    }

    public boolean releaseKey() {
        return this.fromKey && this.selected && this.set(false, true);
    }

    public void reset() {
        this.selected = false;
        this.fromKey = false;
        this.lastSwitch = null;
    }

    private boolean set(boolean next, boolean key) {
        boolean changed = next != this.selected;
        this.selected = next;
        this.fromKey = key;
        return changed;
    }
}
