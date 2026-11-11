package de.lardtom.propwash.drone.collision;

import java.util.function.BooleanSupplier;

public final class GhostLatch {
    private boolean ghost;
    private boolean inside;

    public boolean update(boolean wanted, BooleanSupplier hullBlocked) {
        if (wanted) {
            this.ghost = true;
            this.inside = false;
        } else if (this.ghost && hullBlocked.getAsBoolean()) {
            this.inside = true;
        } else {
            this.ghost = false;
            this.inside = false;
        }
        return this.ghost;
    }

    public boolean ghost() {
        return this.ghost;
    }

    public boolean inside() {
        return this.inside;
    }

    public void reset() {
        this.ghost = false;
        this.inside = false;
    }
}
