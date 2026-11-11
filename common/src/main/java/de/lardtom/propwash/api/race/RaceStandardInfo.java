package de.lardtom.propwash.api.race;

import java.util.List;
import java.util.Objects;
import java.util.OptionalInt;

public record RaceStandardInfo(List<Rule> rules, List<String> neutral, long changedAtMillis, String changedBy) {
    public static final RaceStandardInfo DEFAULTS = new RaceStandardInfo(List.of(), List.of(), 0L, "");

    public record Rule(String rule, int value, int defaultValue) {
        public Rule {
            Objects.requireNonNull(rule, "rule");
        }

        public boolean atDefault() {
            return this.value == this.defaultValue;
        }
    }

    public RaceStandardInfo {
        rules = List.copyOf(rules);
        neutral = List.copyOf(neutral);
        changedBy = changedBy == null ? "" : changedBy;
    }

    public boolean defaults() {
        for (Rule rule : this.rules) {
            if (!rule.atDefault()) {
                return false;
            }
        }
        return true;
    }

    public OptionalInt value(String rule) {
        for (Rule entry : this.rules) {
            if (entry.rule().equals(rule)) {
                return OptionalInt.of(entry.value());
            }
        }
        return OptionalInt.empty();
    }

    public boolean relevant(String rule) {
        return this.value(rule).isPresent();
    }

    public boolean changedKnown() {
        return this.changedAtMillis > 0L;
    }
}
