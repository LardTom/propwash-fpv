package de.lardtom.propwash.race;

import de.lardtom.propwash.api.worldrule.WorldRule;
import de.lardtom.propwash.apiimpl.Api17Registries;
import de.lardtom.propwash.server.AddonWorldRules;
import de.lardtom.propwash.server.WorldRules;
import de.lardtom.propwash.server.rules.PhysicsRules;
import de.lardtom.propwash.server.rules.RuleSpec;
import de.lardtom.propwash.server.rules.RuleValues;
import de.lardtom.propwash.server.rules.WorldRuleSpecs;
import de.lardtom.propwash.zone.Zone;
import de.lardtom.propwash.zone.ZoneBlend;
import de.lardtom.propwash.zone.ZoneField;
import de.lardtom.propwash.zone.ZoneResolution;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public final class RaceRules {
    public static final String PREFIX = RuleSpec.NAMESPACE + ":";
    private static volatile @Nullable Cache cache;

    private RaceRules() {
    }

    public record Rule(String id, String subject, int defaultValue, int min, int max, boolean bool, @Nullable RuleSpec spec, boolean relevant) {
        public boolean zoned() {
            return this.spec != null && PhysicsRules.SPECS.contains(this.spec);
        }

        public boolean valid(int value) {
            return value >= this.min && value <= this.max;
        }

        public boolean addon() {
            return this.spec == null;
        }
    }

    private record Cache(int version, List<Rule> all, List<Rule> relevant, Map<String, Rule> byKey) {
    }

    public static Rule of(RuleSpec spec) {
        return new Rule(spec.id(), spec.name(), spec.defaultValue(), spec.min(), spec.max(), spec.kind() == RuleSpec.Kind.BOOL, spec,
                spec.raceRelevant());
    }

    public static Rule of(WorldRule<?> rule, boolean relevant) {
        boolean bool = rule.type() == WorldRule.Type.BOOLEAN;
        int fallback = bool ? (Boolean.TRUE.equals(rule.defaultValue()) ? 1 : 0) : rule.defaultValue() instanceof Integer number ? number : 0;
        String id = rule.id().toString();
        return new Rule(id, id, fallback, bool ? 0 : rule.min(), bool ? 1 : rule.max(), bool, null, relevant);
    }

    private static Cache cache() {
        int version = Api17Registries.WORLD_RULES.version();
        Cache current = cache;
        if (current != null && current.version() == version) {
            return current;
        }
        List<Rule> all = new ArrayList<>();
        for (RuleSpec spec : WorldRuleSpecs.all()) {
            all.add(of(spec));
        }
        for (WorldRule<?> rule : Api17Registries.WORLD_RULES.registered()) {
            all.add(of(rule, Api17Registries.WORLD_RULES.raceRelevant(rule)));
        }
        List<Rule> relevant = new ArrayList<>();
        Map<String, Rule> byKey = new LinkedHashMap<>();
        for (Rule rule : all) {
            if (rule.relevant()) {
                relevant.add(rule);
            }
            byKey.putIfAbsent(rule.id(), rule);
            byKey.putIfAbsent(rule.subject(), rule);
        }
        Cache fresh = new Cache(version, Collections.unmodifiableList(all), Collections.unmodifiableList(relevant),
                Collections.unmodifiableMap(byKey));
        cache = fresh;
        return fresh;
    }

    public static List<Rule> all() {
        return cache().all();
    }

    public static List<Rule> relevant() {
        return cache().relevant();
    }

    public static Optional<Rule> find(@Nullable String key) {
        if (key == null || key.isEmpty()) {
            return Optional.empty();
        }
        return Optional.ofNullable(cache().byKey().get(key));
    }

    public static boolean relevant(RuleSpec spec) {
        return spec.raceRelevant();
    }

    public static boolean neutral(@Nullable String key) {
        return find(key).map(rule -> !rule.relevant()).orElse(false);
    }

    public static String id(String key) {
        return find(key).map(Rule::id).orElse(key);
    }

    public static int parse(Rule rule, @Nullable String raw) {
        if (raw == null) {
            return rule.defaultValue();
        }
        String text = raw.trim();
        if (rule.bool()) {
            if ("true".equals(text)) {
                return 1;
            }
            return "false".equals(text) ? 0 : rule.defaultValue();
        }
        try {
            int value = Integer.parseInt(text);
            return rule.valid(value) ? value : rule.defaultValue();
        } catch (NumberFormatException e) {
            return rule.defaultValue();
        }
    }

    public static String format(Rule rule, int value) {
        return rule.bool() ? Boolean.toString(value != 0) : Integer.toString(value);
    }

    public static Map<String, Integer> values(RuleValues propwash, Map<Identifier, String> addon) {
        Map<String, Integer> out = new LinkedHashMap<>();
        for (Rule rule : all()) {
            if (rule.spec() != null) {
                out.put(rule.id(), propwash.get(rule.spec()));
            } else {
                Identifier id = Identifier.tryParse(rule.id());
                out.put(rule.id(), parse(rule, id == null ? null : addon.get(id)));
            }
        }
        return out;
    }

    public static Map<String, Integer> values(@Nullable MinecraftServer server) {
        if (server == null) {
            return values(RuleValues.DEFAULTS, Map.of());
        }
        return values(WorldRules.values(server), Api17Registries.WORLD_RULES.registered().isEmpty() ? Map.of() : AddonWorldRules.values(server));
    }

    public static int value(Map<String, Integer> values, Rule rule) {
        Integer value = values.get(rule.id());
        return value == null ? rule.defaultValue() : value;
    }

    public static RaceRuleSet world(Map<String, Integer> values) {
        return observe(values, null, null);
    }

    public static RaceRuleSet observe(Map<String, Integer> values, @Nullable ZoneResolution resolution, @Nullable ZoneField field) {
        List<RaceRuleSet.Seen> seen = new ArrayList<>();
        for (Rule rule : relevant()) {
            if (rule.zoned() && resolution != null) {
                RuleSpec spec = rule.spec();
                if (field != null && ZoneBlend.BLENDED.contains(spec)) {
                    field.shares(spec, (value, zone) -> seen.add(seen(rule, value, zone)));
                    continue;
                }
                seen.add(seen(rule, resolution.rules().value(spec), resolution.source(spec)));
                continue;
            }
            seen.add(new RaceRuleSet.Seen(rule.id(), value(values, rule), RaceRuleSet.WORLD, false));
        }
        return RaceRuleSet.of(seen);
    }

    private static RaceRuleSet.Seen seen(Rule rule, int value, @Nullable Zone zone) {
        return zone == null ? new RaceRuleSet.Seen(rule.id(), value, RaceRuleSet.WORLD, false)
                : new RaceRuleSet.Seen(rule.id(), value, zone.id(), zone.raceLegal());
    }

    public static boolean modified(ZoneField field, RaceStandard standard) {
        ZoneResolution resolution = field.resolution();
        for (Rule rule : relevant()) {
            if (!rule.zoned()) {
                continue;
            }
            RuleSpec spec = rule.spec();
            int wanted = standard.value(rule);
            if (ZoneBlend.BLENDED.contains(spec)) {
                boolean[] off = {false};
                field.shares(spec, (value, zone) -> off[0] |= (zone == null || !zone.raceLegal()) && value != wanted);
                if (off[0]) {
                    return true;
                }
                continue;
            }
            Zone zone = resolution.source(spec);
            if ((zone == null || !zone.raceLegal()) && resolution.rules().value(spec) != wanted) {
                return true;
            }
        }
        return false;
    }

    public static boolean differs(Zone zone, RaceStandard standard) {
        return !zone.raceLegal() && changes(zone, standard);
    }

    public static boolean changes(Zone zone, RaceStandard standard) {
        for (Rule rule : relevant()) {
            if (rule.zoned() && zone.overrides(rule.spec()) && zone.value(rule.spec(), rule.defaultValue()) != standard.value(rule)) {
                return true;
            }
        }
        return false;
    }
}
