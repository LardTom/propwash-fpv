package de.lardtom.propwash.server.rules;

import java.util.Locale;
import java.util.Objects;
import java.util.regex.Pattern;

public record RuleSpec(String name, Kind kind, int defaultValue, int min, int max, Group group, Display display, int step,
                       boolean zeroSpecial) {
    public static final String NAMESPACE = "propwash";
    private static final Pattern NAME = Pattern.compile("[a-z0-9_]+");

    public enum Kind {
        BOOL,
        INT
    }

    public enum Group {
        PHYSICS,
        WEATHER,
        BATTERY,
        CRASH,
        VIDEO,
        GAMEPLAY;

        public String key() {
            return "propwash.server.world_rules.group." + this.name().toLowerCase(Locale.ROOT);
        }
    }

    public enum Display {
        TOGGLE,
        PERCENT,
        LEVELS,
        NUMBER
    }

    public RuleSpec {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(group, "group");
        Objects.requireNonNull(display, "display");
        if (!NAME.matcher(name).matches()) {
            throw new IllegalArgumentException("Ungültiger Regelname: " + name);
        }
        if (kind == Kind.BOOL && (min != 0 || max != 1 || display != Display.TOGGLE)) {
            throw new IllegalArgumentException(name + ": Schalter brauchen 0/1 und TOGGLE");
        }
        if (min > max || defaultValue < min || defaultValue > max) {
            throw new IllegalArgumentException(name + ": Standard " + defaultValue + " außerhalb " + min + "–" + max);
        }
        if (step < 1) {
            throw new IllegalArgumentException(name + ": Schrittweite " + step);
        }
        if (zeroSpecial && min != 0) {
            throw new IllegalArgumentException(name + ": Sonderwert 0 braucht Minimum 0");
        }
    }

    public static RuleSpec bool(String name, boolean defaultValue, Group group) {
        return new RuleSpec(name, Kind.BOOL, defaultValue ? 1 : 0, 0, 1, group, Display.TOGGLE, 1, false);
    }

    public static RuleSpec percent(String name, int defaultValue, int min, int max, int step, boolean zeroSpecial, Group group) {
        return new RuleSpec(name, Kind.INT, defaultValue, min, max, group, Display.PERCENT, step, zeroSpecial);
    }

    public static RuleSpec levels(String name, int defaultValue, int max, Group group) {
        return new RuleSpec(name, Kind.INT, defaultValue, 0, max, group, Display.LEVELS, 1, false);
    }

    public static RuleSpec number(String name, int defaultValue, int min, int max, boolean zeroSpecial, Group group) {
        return new RuleSpec(name, Kind.INT, defaultValue, min, max, group, Display.NUMBER, 1, zeroSpecial);
    }

    public String id() {
        return NAMESPACE + ":" + this.name;
    }

    public String translationKey() {
        return "gamerule." + NAMESPACE + "." + this.name;
    }

    public String descriptionKey() {
        return this.translationKey() + ".description";
    }

    public String valueKey(int value) {
        return this.translationKey() + ".value." + value;
    }

    public boolean defaultOn() {
        return this.defaultValue != 0;
    }

    public int clamp(int value) {
        return Math.max(this.min, Math.min(this.max, value));
    }

    public boolean valid(int value) {
        return value >= this.min && value <= this.max;
    }

    public boolean labelled(int value) {
        return this.display == Display.LEVELS || this.zeroSpecial && value == 0;
    }

    public String serialize(int value) {
        return this.kind == Kind.BOOL ? Boolean.toString(value != 0) : Integer.toString(value);
    }

    public int parse(String text) {
        if (text == null) {
            throw new IllegalArgumentException(this.name + ": kein Wert");
        }
        String trimmed = text.trim();
        if (this.kind == Kind.BOOL) {
            if ("true".equals(trimmed)) {
                return 1;
            }
            if ("false".equals(trimmed)) {
                return 0;
            }
            throw new IllegalArgumentException(this.name + ": kein Schalterwert: " + text);
        }
        int value;
        try {
            value = Integer.parseInt(trimmed);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(this.name + ": keine Zahl: " + text, e);
        }
        if (!this.valid(value)) {
            throw new IllegalArgumentException(this.name + ": " + value + " außerhalb " + this.min + "–" + this.max);
        }
        return value;
    }
}
