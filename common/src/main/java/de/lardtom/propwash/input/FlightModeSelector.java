package de.lardtom.propwash.input;

import de.lardtom.propwash.sim.StabMode;
import org.jspecify.annotations.Nullable;

public final class FlightModeSelector {
    public enum Source {
        DEFAULT,
        KEY,
        SWITCH
    }

    private StabMode mode = StabMode.ACRO;
    private Source source = Source.DEFAULT;
    private @Nullable StabMode lastSwitch;

    public StabMode mode() {
        return this.mode;
    }

    public Source source() {
        return this.source;
    }

    public @Nullable StabMode lastSwitch() {
        return this.lastSwitch;
    }

    public static @Nullable StabMode fromSwitches(int active, int readable) {
        if (!SwitchEvaluator.isSet(readable, SwitchFunction.ANGLE) && !SwitchEvaluator.isSet(readable, SwitchFunction.HORIZON)) {
            return null;
        }
        if (SwitchEvaluator.isSet(active, SwitchFunction.ANGLE)) {
            return StabMode.ANGLE;
        }
        return SwitchEvaluator.isSet(active, SwitchFunction.HORIZON) ? StabMode.HORIZON : StabMode.ACRO;
    }

    public static StabMode next(StabMode mode) {
        return switch (mode) {
            case ACRO -> StabMode.ANGLE;
            case ANGLE -> StabMode.HORIZON;
            case HORIZON -> StabMode.ACRO;
        };
    }

    public boolean observe(boolean device, int active, int readable) {
        if (!device) {
            return false;
        }
        StabMode fromSwitches = fromSwitches(active, readable);
        if (fromSwitches == this.lastSwitch) {
            return false;
        }
        this.lastSwitch = fromSwitches;
        if (fromSwitches != null) {
            return this.set(fromSwitches, Source.SWITCH);
        }
        return this.source == Source.SWITCH && this.set(StabMode.ACRO, Source.DEFAULT);
    }

    public StabMode preview(boolean device, int active, int readable) {
        if (!device) {
            return this.mode;
        }
        StabMode fromSwitches = fromSwitches(active, readable);
        if (fromSwitches == this.lastSwitch) {
            return this.mode;
        }
        if (fromSwitches != null) {
            return fromSwitches;
        }
        return this.source == Source.SWITCH ? StabMode.ACRO : this.mode;
    }

    public void select(StabMode next) {
        this.set(next, Source.KEY);
    }

    public StabMode cycle() {
        this.select(next(this.mode));
        return this.mode;
    }

    public void startKeyboard(boolean angle) {
        this.set(angle ? StabMode.ANGLE : StabMode.ACRO, Source.DEFAULT);
        this.lastSwitch = null;
    }

    public boolean keyOverridesSwitch(boolean device, int active, int readable) {
        StabMode fromSwitches = device ? fromSwitches(active, readable) : null;
        return this.source == Source.KEY && fromSwitches != null && fromSwitches == this.lastSwitch && fromSwitches != this.mode;
    }

    public void reset() {
        this.mode = StabMode.ACRO;
        this.source = Source.DEFAULT;
        this.lastSwitch = null;
    }

    private boolean set(StabMode next, Source from) {
        boolean changed = next != this.mode;
        this.mode = next;
        this.source = from;
        return changed;
    }
}
