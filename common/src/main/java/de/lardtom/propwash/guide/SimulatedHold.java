package de.lardtom.propwash.guide;

import org.jspecify.annotations.Nullable;

import java.util.List;

public final class SimulatedHold {
    public static final int NONE = -1;

    private int slot = NONE;

    public boolean hold(int slot, boolean development) {
        if (!development || slot < 0) {
            return false;
        }
        this.slot = slot;
        return true;
    }

    public void release() {
        this.slot = NONE;
    }

    public boolean active() {
        return this.slot != NONE;
    }

    public <S> @Nullable S slotOr(List<S> slots, @Nullable S hovered) {
        if (!this.active()) {
            return hovered;
        }
        return this.slot < slots.size() ? slots.get(this.slot) : null;
    }

    public boolean keyDown(boolean realKey) {
        return this.active() || realKey;
    }
}
