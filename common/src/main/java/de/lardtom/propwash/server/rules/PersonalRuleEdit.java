package de.lardtom.propwash.server.rules;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;

public final class PersonalRuleEdit {
    private PersonalRuleEdit() {
    }

    public enum Op {
        SET,
        RESET,
        APPLY;

        public String key() {
            return this.name().toLowerCase(Locale.ROOT);
        }
    }

    public record Result(PersonalRules rules, List<String> refused) {
        public Result {
            refused = List.copyOf(refused);
        }

        public boolean changed(PersonalRules before) {
            return !this.rules.equals(before);
        }
    }

    public static Result apply(PersonalRules current, RuleTiers tiers, PhysicsRules server, Op op, Map<String, Integer> values) {
        PersonalRules next = current;
        List<String> refused = new ArrayList<>();
        Map<String, Integer> wanted = new TreeMap<>(values);
        switch (op) {
            case SET -> {
                for (Map.Entry<String, Integer> entry : wanted.entrySet()) {
                    Optional<RuleSpec> spec = playerRule(entry.getKey(), tiers);
                    Integer value = entry.getValue();
                    if (spec.isEmpty() || value == null || !spec.get().valid(value)) {
                        refused.add(entry.getKey());
                        continue;
                    }
                    next = value == server.value(spec.get()) ? next.without(spec.get()) : next.with(spec.get(), value);
                }
            }
            case RESET -> {
                for (String name : wanted.keySet()) {
                    Optional<RuleSpec> spec = WorldRuleSpecs.byName(name).filter(RuleTiers::tierable);
                    if (spec.isEmpty()) {
                        refused.add(name);
                        continue;
                    }
                    next = next.without(spec.get());
                }
            }
            case APPLY -> {
                for (Map.Entry<String, Integer> entry : wanted.entrySet()) {
                    Optional<RuleSpec> spec = playerRule(entry.getKey(), tiers);
                    Integer value = entry.getValue();
                    if (spec.isEmpty() || value == null || !spec.get().valid(value)) {
                        refused.add(entry.getKey());
                    }
                }
                for (RuleSpec spec : PhysicsRules.SPECS) {
                    if (!tiers.players(spec)) {
                        continue;
                    }
                    Integer value = wanted.get(spec.name());
                    if (value == null || !spec.valid(value) || value == server.value(spec)) {
                        next = next.without(spec);
                    } else {
                        next = next.with(spec, value);
                    }
                }
            }
        }
        return new Result(next, refused);
    }

    public static Optional<RuleSpec> playerRule(String name, RuleTiers tiers) {
        return WorldRuleSpecs.byName(name == null ? "" : name).filter(spec -> tiers.players(spec));
    }

    public static Optional<RuleSpec> tierRule(String name) {
        return WorldRuleSpecs.byName(name == null ? "" : name).filter(RuleTiers::tierable);
    }
}
