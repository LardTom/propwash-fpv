package de.lardtom.propwash.race;

public final class ArmEdge {
    private static final int NO_DRONE = Integer.MIN_VALUE;

    private int drone = NO_DRONE;
    private boolean before;

    public void clear() {
        this.drone = NO_DRONE;
        this.before = false;
    }

    public void afterReset() {
        this.before = true;
    }

    public boolean update(int droneId, boolean armed) {
        if (droneId != this.drone) {
            this.drone = droneId;
            this.before = armed;
            return false;
        }
        boolean rising = armed && !this.before;
        this.before = armed;
        return rising;
    }

    public boolean armedBefore() {
        return this.before;
    }
}
