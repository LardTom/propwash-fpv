package de.lardtom.propwash.drone.collision.client;

final class RigArming {
    static final int ARM_DELAY = 3;
    static final int REARM_TICKS = 20;

    private RigArming() {
    }

    static boolean armSwitch(int waited, boolean armed, boolean resting) {
        return armed || resting && waited >= ARM_DELAY && waited % REARM_TICKS != 0;
    }
}
