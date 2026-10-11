package de.lardtom.propwash.race.client;

public final class GateKeyRoute {
    public enum Action {
        NONE,
        FLIGHT_MODE,
        TURN,
        NEXT_MOUNT,
        ROTATE_GATE,
        NEXT_MOUNT_GATE,
        NEED_TOOL
    }

    public record Hands(boolean gate, boolean courseTool, boolean remote) {
        public static final Hands EMPTY = new Hands(false, false, false);

        public boolean tool() {
            return this.gate || this.courseTool;
        }
    }

    public record Context(boolean screenOpen, boolean piloting, Hands hands, boolean lookingAtGate, boolean sneaking, boolean sharedWithFlightMode) {
    }

    private GateKeyRoute() {
    }

    public static Action route(Context context) {
        if (context.screenOpen()) {
            return Action.NONE;
        }
        if (context.piloting()) {
            return context.sharedWithFlightMode() ? Action.FLIGHT_MODE : Action.NONE;
        }
        Hands hands = context.hands();
        if (context.lookingAtGate()) {
            if (hands.tool()) {
                return context.sneaking() ? Action.NEXT_MOUNT_GATE : Action.ROTATE_GATE;
            }
            return context.sharedWithFlightMode() && hands.remote() ? Action.NONE : Action.NEED_TOOL;
        }
        if (hands.gate()) {
            return context.sneaking() ? Action.NEXT_MOUNT : Action.TURN;
        }
        return Action.NONE;
    }
}
