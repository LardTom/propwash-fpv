package de.lardtom.propwash.freecam;

import java.util.Locale;

public final class FreecamRules {
    public enum Denial {
        NONE,
        NO_WORLD,
        RULES_UNKNOWN,
        RULE_OFF,
        DEAD,
        PILOTING,
        SPECTATING,
        SCREEN_OPEN;

        public boolean allowed() {
            return this == NONE;
        }

        public String translationKey() {
            return switch (this) {
                case NONE, NO_WORLD -> "";
                case RULES_UNKNOWN -> "propwash.freecam.denied.unknown";
                case RULE_OFF -> "propwash.freecam.denied";
                default -> "propwash.freecam.denied." + this.name().toLowerCase(Locale.ROOT);
            };
        }

        public boolean ruleRelated() {
            return this == RULES_UNKNOWN || this == RULE_OFF;
        }
    }

    public enum End {
        NONE,
        KEY,
        BUTTON,
        HURT,
        DIED,
        DIMENSION,
        DISCONNECT,
        RULE_OFF,
        PILOTING,
        SPECTATING;

        public String translationKey() {
            return switch (this) {
                case NONE, DISCONNECT -> "";
                case KEY, BUTTON -> "propwash.freecam.ended";
                case RULE_OFF -> "propwash.freecam.ended.rule";
                default -> "propwash.freecam.ended." + this.name().toLowerCase(Locale.ROOT);
            };
        }
    }

    public record StartState(boolean inWorld, boolean rulesKnown, boolean ruleOn, boolean alive, boolean piloting, boolean spectating,
                             boolean screenOpen) {
    }

    public record RunState(boolean inWorld, boolean rulesKnown, boolean ruleOn, boolean alive, float health, float lastHealth,
                           boolean sameDimension, boolean piloting, boolean spectating) {
    }

    private FreecamRules() {
    }

    public static boolean allowedByRule(boolean rulesKnown, boolean ruleOn) {
        return rulesKnown && ruleOn;
    }

    public static Denial start(StartState s) {
        if (!s.inWorld()) {
            return Denial.NO_WORLD;
        }
        if (!s.rulesKnown()) {
            return Denial.RULES_UNKNOWN;
        }
        if (!s.ruleOn()) {
            return Denial.RULE_OFF;
        }
        if (!s.alive()) {
            return Denial.DEAD;
        }
        if (s.piloting()) {
            return Denial.PILOTING;
        }
        if (s.spectating()) {
            return Denial.SPECTATING;
        }
        if (s.screenOpen()) {
            return Denial.SCREEN_OPEN;
        }
        return Denial.NONE;
    }

    public static End check(RunState s) {
        if (!s.inWorld()) {
            return End.DISCONNECT;
        }
        if (!s.alive() || !(s.health() > 0.0F)) {
            return End.DIED;
        }
        if (!s.sameDimension()) {
            return End.DIMENSION;
        }
        if (!allowedByRule(s.rulesKnown(), s.ruleOn())) {
            return End.RULE_OFF;
        }
        if (s.health() < s.lastHealth()) {
            return End.HURT;
        }
        if (s.piloting()) {
            return End.PILOTING;
        }
        if (s.spectating()) {
            return End.SPECTATING;
        }
        return End.NONE;
    }
}
