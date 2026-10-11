package de.lardtom.propwash.input;

import org.jspecify.annotations.Nullable;

public final class Mode3dSelector {
    public enum Source {
        DEFAULT,
        KEY,
        SWITCH
    }

    public enum Block {
        NONE,
        LOCKED,
        FORBIDDEN,
        RESCUE
    }

    private boolean selected;
    private Source source = Source.DEFAULT;
    private @Nullable Boolean lastSwitch;

    public boolean selected() {
        return this.selected;
    }

    public Source source() {
        return this.source;
    }

    public @Nullable Boolean lastSwitch() {
        return this.lastSwitch;
    }

    public static @Nullable Boolean fromSwitches(int active, int readable) {
        if (!SwitchEvaluator.isSet(readable, SwitchFunction.MODE_3D)) {
            return null;
        }
        return SwitchEvaluator.isSet(active, SwitchFunction.MODE_3D);
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
            return this.set(fromSwitch, Source.SWITCH);
        }
        return this.source == Source.SWITCH && this.set(false, Source.DEFAULT);
    }

    public boolean toggle() {
        this.set(!this.selected, Source.KEY);
        return this.selected;
    }

    public boolean keyOverridesSwitch(boolean device, int active, int readable) {
        Boolean fromSwitch = device ? fromSwitches(active, readable) : null;
        return this.source == Source.KEY && fromSwitch != null && fromSwitch != this.selected;
    }

    public void reset() {
        this.selected = false;
        this.source = Source.DEFAULT;
        this.lastSwitch = null;
    }

    public static Block block(boolean unlocked, boolean allowed, boolean rescue) {
        if (!unlocked) {
            return Block.LOCKED;
        }
        if (!allowed) {
            return Block.FORBIDDEN;
        }
        return rescue ? Block.RESCUE : Block.NONE;
    }

    public boolean active(boolean unlocked, boolean allowed, boolean rescue) {
        return this.selected && block(unlocked, allowed, rescue) == Block.NONE;
    }

    private boolean set(boolean next, Source from) {
        boolean changed = next != this.selected;
        this.selected = next;
        this.source = from;
        return changed;
    }
}
