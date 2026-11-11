package de.lardtom.propwash.zone;

import com.mojang.serialization.Codec;
import de.lardtom.propwash.server.rules.PhysicsRules;
import de.lardtom.propwash.server.rules.RuleSpec;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

public record ZoneRelease(Map<String, Boolean> explicit) {
    public static final int MAX_ENTRIES = 256;
    public static final int MAX_ID = 128;
    public static final ZoneRelease DEFAULT = new ZoneRelease(Map.of());
    public static final Codec<ZoneRelease> CODEC = Codec.unboundedMap(Codec.STRING, Codec.BOOL).xmap(ZoneRelease::new, ZoneRelease::explicit);

    public ZoneRelease {
        Map<String, Boolean> out = new TreeMap<>();
        if (explicit != null) {
            for (Map.Entry<String, Boolean> entry : new TreeMap<>(explicit).entrySet()) {
                String id = entry.getKey();
                Boolean on = entry.getValue();
                if (id == null || on == null || id.isEmpty() || id.length() > MAX_ID || on == defaultReleased(id) || out.size() >= MAX_ENTRIES) {
                    continue;
                }
                out.put(id, on);
            }
        }
        explicit = Collections.unmodifiableMap(out);
    }

    public static boolean capable(RuleSpec spec) {
        return Zone.overridable(spec);
    }

    public static boolean capable(String id) {
        return spec(id) != null;
    }

    public static @Nullable RuleSpec spec(String id) {
        for (RuleSpec spec : PhysicsRules.SPECS) {
            if (spec.id().equals(id)) {
                return spec;
            }
        }
        return null;
    }

    public static boolean defaultReleased(String id) {
        return capable(id);
    }

    public boolean released(RuleSpec spec) {
        return this.released(spec.id());
    }

    public boolean released(String id) {
        return capable(id) && this.explicit.getOrDefault(id, Boolean.TRUE);
    }

    public ZoneRelease with(String id, boolean on) {
        Map<String, Boolean> copy = new LinkedHashMap<>(this.explicit);
        copy.put(id, on);
        return new ZoneRelease(copy);
    }

    public boolean isDefault() {
        return this.explicit.isEmpty();
    }

    public List<RuleSpec> blocked(Zone zone) {
        List<RuleSpec> out = new ArrayList<>();
        for (RuleSpec spec : PhysicsRules.SPECS) {
            if (zone.sets(spec) && !this.released(spec)) {
                out.add(spec);
            }
        }
        return out;
    }

    public Zone effective(Zone zone) {
        if (this.isDefault() || !zone.setsAny()) {
            return zone;
        }
        Map<String, Integer> kept = new LinkedHashMap<>();
        boolean dropped = false;
        for (Map.Entry<String, Integer> entry : zone.overrides().entrySet()) {
            if (this.released(RuleSpec.NAMESPACE + ":" + entry.getKey())) {
                kept.put(entry.getKey(), entry.getValue());
            } else {
                dropped = true;
            }
        }
        Set<String> forced = new LinkedHashSet<>();
        for (String rule : zone.world()) {
            if (this.released(RuleSpec.NAMESPACE + ":" + rule)) {
                forced.add(rule);
            } else {
                dropped = true;
            }
        }
        return dropped ? zone.withRules(kept, forced) : zone;
    }

    public List<Zone> effective(List<Zone> zones) {
        if (this.isDefault()) {
            return zones;
        }
        List<Zone> out = new ArrayList<>(zones.size());
        for (Zone zone : zones) {
            out.add(this.effective(zone));
        }
        return List.copyOf(out);
    }

    public Zone enforce(Zone candidate, @Nullable Zone stored) {
        Zone out = candidate;
        for (RuleSpec spec : PhysicsRules.SPECS) {
            if (this.released(spec)) {
                continue;
            }
            if (stored != null && stored.overrides(spec)) {
                int value = stored.value(spec, spec.defaultValue());
                if (!out.overrides(spec) || out.value(spec, value) != value) {
                    out = out.withOverride(spec, value);
                }
            } else if (stored != null && stored.forcesWorld(spec)) {
                if (!out.forcesWorld(spec)) {
                    out = out.withWorld(spec);
                }
            } else if (out.sets(spec)) {
                out = out.without(spec);
            }
        }
        return out;
    }
}
