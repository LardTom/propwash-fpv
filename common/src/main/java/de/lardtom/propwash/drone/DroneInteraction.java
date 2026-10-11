package de.lardtom.propwash.drone;

public final class DroneInteraction {
    public enum HeldKind {
        EMPTY,
        REMOTE,
        GOGGLES,
        BATTERY,
        PROP,
        OTHER
    }

    public enum Action {
        PASS,
        BIND_REMOTE,
        CHECK_GOGGLES,
        QUICK_SWAP,
        REJECT_BATTERY,
        HINT_BATTERY,
        HINT_PROPS,
        OPEN_SERVICE_MENU,
        LOCKED,
        PICK_UP,
        OCCUPIED
    }

    private DroneInteraction() {
    }

    public static Action resolve(HeldKind held, boolean sneaking, boolean pilotedByOther, boolean pilotedBySelf, boolean locked,
                                 boolean heldFits) {
        if (sneaking) {
            if (locked) {
                return Action.LOCKED;
            }
            if (held == HeldKind.BATTERY) {
                return heldFits ? Action.QUICK_SWAP : Action.REJECT_BATTERY;
            }
            return Action.OPEN_SERVICE_MENU;
        }
        return switch (held) {
            case REMOTE -> pilotedByOther ? Action.OCCUPIED : Action.BIND_REMOTE;
            case GOGGLES -> Action.CHECK_GOGGLES;
            case BATTERY -> Action.HINT_BATTERY;
            case PROP -> Action.HINT_PROPS;
            case EMPTY -> pilotedByOther ? Action.OCCUPIED : pilotedBySelf ? Action.PASS : Action.PICK_UP;
            case OTHER -> Action.PASS;
        };
    }
}
