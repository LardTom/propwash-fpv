package de.lardtom.propwash.race;

public final class GhostAccess {
    public static final int OFF = 0;
    public static final int ADMINS = 1;
    public static final int ALL = 2;
    public static final int REPLAY = 1;
    public static final int OPPONENT = 2;
    public static final int REMOVE = 4;

    public enum Purpose {
        REPLAY,
        OPPONENT;

        public static Purpose of(int index) {
            return index == OPPONENT.ordinal() ? OPPONENT : REPLAY;
        }
    }

    private GhostAccess() {
    }

    public static boolean allowed(int rule, boolean own, boolean admin, Purpose purpose) {
        if (own) {
            return true;
        }
        return switch (rule) {
            case ALL -> true;
            case ADMINS -> admin;
            default -> admin && purpose == Purpose.REPLAY;
        };
    }

    public static int flags(int rule, boolean admin) {
        int flags = 0;
        if (allowed(rule, false, admin, Purpose.REPLAY)) {
            flags |= REPLAY;
        }
        if (allowed(rule, false, admin, Purpose.OPPONENT)) {
            flags |= OPPONENT;
        }
        if (admin) {
            flags |= REMOVE;
        }
        return flags;
    }

    public static boolean mayRemove(boolean admin) {
        return admin;
    }
}
