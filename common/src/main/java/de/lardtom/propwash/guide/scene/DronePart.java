package de.lardtom.propwash.guide.scene;

public enum DronePart {
    FRAME(false),
    STACK(false),
    MOTORS(true),
    PROPS(true),
    VIDEO(false),
    BATTERY(false);

    public static final int SLOTS = 4;

    private final boolean perSlot;

    DronePart(boolean perSlot) {
        this.perSlot = perSlot;
    }

    public boolean perSlot() {
        return this.perSlot;
    }

    public int slots() {
        return this.perSlot ? SLOTS : 1;
    }
}
