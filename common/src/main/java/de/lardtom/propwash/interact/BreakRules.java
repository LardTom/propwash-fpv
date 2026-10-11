package de.lardtom.propwash.interact;

import de.lardtom.propwash.race.FreeformRegion;

public final class BreakRules {
    public static final int BREAKS_PER_SECOND = 20;
    public static final double PROTECTED_RADIUS = 24.0;
    public static final int PROTECTED_MEMORY_TICKS = 2400;
    public static final int BLOCK_MEMORY_TICKS = 200;

    private BreakRules() {
    }

    public static Outcome decide(boolean ruleEnabled, boolean breakable, boolean withinBudget, boolean withinReach, boolean strongEnough,
                                 boolean courseFrame, boolean gameModeRestricted, boolean mayInteract) {
        if (!ruleEnabled) {
            return Outcome.RULE_OFF;
        }
        if (!breakable) {
            return Outcome.NOT_BREAKABLE;
        }
        if (!withinBudget) {
            return Outcome.BUDGET;
        }
        if (!withinReach) {
            return Outcome.TOO_FAR;
        }
        if (!strongEnough) {
            return Outcome.TOO_WEAK;
        }
        if (courseFrame) {
            return Outcome.COURSE;
        }
        if (gameModeRestricted) {
            return Outcome.GAME_MODE;
        }
        if (!mayInteract) {
            return Outcome.PROTECTED;
        }
        return Outcome.BREAK;
    }

    public static boolean frame(FreeformRegion region, int a, int b) {
        if (region == null || region.contains(a, b)) {
            return false;
        }
        return region.contains(a - 1, b) || region.contains(a + 1, b) || region.contains(a, b - 1) || region.contains(a, b + 1);
    }

    public static int memoryTicks(Outcome outcome) {
        return outcome == Outcome.PROTECTED ? PROTECTED_MEMORY_TICKS : BLOCK_MEMORY_TICKS;
    }

    public static double memoryRadius(Outcome outcome) {
        return outcome == Outcome.PROTECTED ? PROTECTED_RADIUS : 0.0;
    }

    public enum Outcome {
        BREAK,
        RULE_OFF,
        NOT_BREAKABLE,
        BUDGET,
        TOO_FAR,
        TOO_WEAK,
        COURSE,
        GAME_MODE,
        PROTECTED;

        private static final Outcome[] VALUES = values();

        public static Outcome byId(int id) {
            return id >= 0 && id < VALUES.length ? VALUES[id] : PROTECTED;
        }

        public boolean told() {
            return this == COURSE || this == GAME_MODE || this == PROTECTED;
        }

        public String messageKey() {
            return switch (this) {
                case COURSE -> "propwash.interact.break.denied.course";
                case GAME_MODE -> "propwash.interact.break.denied.game_mode";
                default -> "propwash.interact.break.denied.protected";
            };
        }
    }
}
