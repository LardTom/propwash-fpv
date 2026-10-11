package de.lardtom.propwash.video.record;

public final class RecordTrigger {
    private boolean last;
    private boolean primed;

    public void reset() {
        this.last = false;
        this.primed = false;
    }

    public Action update(Kind kind, boolean session, boolean active) {
        if (!session) {
            this.reset();
            return Action.NONE;
        }
        boolean previous = this.last;
        this.last = active;
        if (!this.primed) {
            this.primed = true;
            return kind == Kind.SWITCH && active ? Action.START : Action.NONE;
        }
        if (active == previous) {
            return Action.NONE;
        }
        return switch (kind) {
            case SWITCH -> active ? Action.START : Action.STOP;
            case BUTTON -> active ? Action.TOGGLE : Action.NONE;
            case LATCH -> Action.TOGGLE;
            case NONE -> Action.NONE;
        };
    }

    public enum Kind {
        NONE,
        SWITCH,
        BUTTON,
        LATCH
    }

    public enum Action {
        NONE,
        START,
        STOP,
        TOGGLE
    }
}
