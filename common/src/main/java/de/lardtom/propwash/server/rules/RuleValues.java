package de.lardtom.propwash.server.rules;

import java.util.HashMap;
import java.util.Map;

public final class RuleValues {
    public static final RuleValues DEFAULTS = new RuleValues(Map.of());

    private final Map<String, Integer> values;

    public RuleValues(Map<String, Integer> values) {
        this.values = Map.copyOf(values);
    }

    public int get(RuleSpec spec) {
        Integer value = this.values.get(spec.name());
        return value == null || !spec.valid(value) ? spec.defaultValue() : value;
    }

    public boolean on(RuleSpec spec) {
        return this.get(spec) != 0;
    }

    public RuleValues with(RuleSpec spec, int value) {
        Map<String, Integer> copy = new HashMap<>(this.values);
        copy.put(spec.name(), spec.clamp(value));
        return new RuleValues(copy);
    }

    public boolean isDefault(RuleSpec spec) {
        return this.get(spec) == spec.defaultValue();
    }

    public Map<String, Integer> asMap() {
        return this.values;
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof RuleValues other && this.values.equals(other.values);
    }

    @Override
    public int hashCode() {
        return this.values.hashCode();
    }

    @Override
    public String toString() {
        return "RuleValues" + this.values;
    }
}
