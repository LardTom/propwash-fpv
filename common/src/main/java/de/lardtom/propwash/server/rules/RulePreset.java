package de.lardtom.propwash.server.rules;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

public enum RulePreset {
    REALISTIC(Map.of()),
    TRAINING(training()),
    ARCADE(arcade());

    public static final int MAX_ID_LENGTH = 32;
    private static final String KEY = "propwash.server.world_rules.preset.";

    private final Map<String, Integer> overrides;

    RulePreset(Map<String, Integer> overrides) {
        for (Map.Entry<String, Integer> entry : overrides.entrySet()) {
            RuleSpec spec = WorldRuleSpecs.byName(entry.getKey())
                    .orElseThrow(() -> new IllegalArgumentException("Voreinstellung mit unbekannter Regel: " + entry.getKey()));
            if (!spec.valid(entry.getValue())) {
                throw new IllegalArgumentException("Voreinstellung " + this.name() + ": " + spec.name() + " = " + entry.getValue());
            }
        }
        this.overrides = Map.copyOf(overrides);
    }

    private static Map<String, Integer> training() {
        Map<String, Integer> out = new LinkedHashMap<>();
        out.put(WorldRuleSpecs.HARDWARE_LIMITS.name(), 0);
        out.put(WorldRuleSpecs.BATTERY_SCALE.name(), 0);
        out.put(WorldRuleSpecs.BATTERY_SAG.name(), 0);
        out.put(WorldRuleSpecs.CRASH_DAMAGE.name(), 0);
        out.put(WorldRuleSpecs.PROP_STRIKES.name(), 0);
        out.put(WorldRuleSpecs.SAVE_ASSISTED_LAPS.name(), 1);
        out.put(WorldRuleSpecs.DRONE_TRAILS.name(), 1);
        return out;
    }

    private static Map<String, Integer> arcade() {
        Map<String, Integer> out = new LinkedHashMap<>(training());
        out.put(WorldRuleSpecs.PROP_WASH.name(), 0);
        out.put(WorldRuleSpecs.WIND.name(), 0);
        out.put(WorldRuleSpecs.TURBULENCE.name(), 0);
        out.put(WorldRuleSpecs.CRASH_TOLERANCE.name(), 300);
        out.put(WorldRuleSpecs.CRASH_DISARM.name(), 0);
        out.put(WorldRuleSpecs.INFINITE_RANGE_ANALOG.name(), 1);
        out.put(WorldRuleSpecs.INFINITE_RANGE_DIGITAL.name(), 1);
        out.put(WorldRuleSpecs.DRONE_HITS_ENTITIES.name(), 0);
        out.put(WorldRuleSpecs.PART_COMPATIBILITY.name(), 0);
        return out;
    }

    public String id() {
        return this.name().toLowerCase(Locale.ROOT);
    }

    public String nameKey() {
        return KEY + this.id();
    }

    public String descriptionKey() {
        return this.nameKey() + ".description";
    }

    public int value(RuleSpec spec) {
        Integer value = this.overrides.get(spec.name());
        return value != null ? value : spec.defaultValue();
    }

    public RuleValues ruleValues() {
        Map<String, Integer> out = new LinkedHashMap<>();
        for (RuleSpec spec : WorldRuleSpecs.all()) {
            out.put(spec.name(), this.value(spec));
        }
        return new RuleValues(out);
    }

    public List<RuleSpec> differences() {
        List<RuleSpec> out = new ArrayList<>();
        for (RuleSpec spec : WorldRuleSpecs.all()) {
            if (this.value(spec) != spec.defaultValue()) {
                out.add(spec);
            }
        }
        return out;
    }

    public boolean matches(RuleValues current) {
        for (RuleSpec spec : WorldRuleSpecs.all()) {
            if (current.get(spec) != this.value(spec)) {
                return false;
            }
        }
        return true;
    }

    public List<RuleSpec> changes(RuleValues current) {
        List<RuleSpec> out = new ArrayList<>();
        for (RuleSpec spec : WorldRuleSpecs.all()) {
            if (current.get(spec) != this.value(spec)) {
                out.add(spec);
            }
        }
        return out;
    }

    public static Optional<RulePreset> match(RuleValues current) {
        for (RulePreset preset : values()) {
            if (preset.matches(current)) {
                return Optional.of(preset);
            }
        }
        return Optional.empty();
    }

    public static Optional<RulePreset> byId(String id) {
        if (id == null) {
            return Optional.empty();
        }
        for (RulePreset preset : values()) {
            if (preset.id().equals(id)) {
                return Optional.of(preset);
            }
        }
        return Optional.empty();
    }
}
