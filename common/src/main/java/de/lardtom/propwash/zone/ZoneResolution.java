package de.lardtom.propwash.zone;

import de.lardtom.propwash.race.AssistDetail;
import de.lardtom.propwash.server.rules.PhysicsRules;
import de.lardtom.propwash.server.rules.RuleSpec;
import de.lardtom.propwash.server.rules.RuleValues;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public record ZoneResolution(PhysicsRules world, PhysicsRules rules, List<Zone> inside, List<@Nullable Zone> sources) {
    public ZoneResolution {
        inside = List.copyOf(inside);
        sources = Collections.unmodifiableList(new ArrayList<>(sources));
    }

    public static ZoneResolution world(PhysicsRules world) {
        List<@Nullable Zone> sources = new ArrayList<>();
        for (int i = 0; i < PhysicsRules.SPECS.size(); i++) {
            sources.add(null);
        }
        return new ZoneResolution(world, world, List.of(), sources);
    }

    public @Nullable Zone source(RuleSpec spec) {
        int index = PhysicsRules.SPECS.indexOf(spec);
        return index < 0 ? null : this.sources.get(index);
    }

    public @Nullable Zone top() {
        return this.inside.isEmpty() ? null : this.inside.getFirst();
    }

    public boolean inZone() {
        return !this.inside.isEmpty();
    }

    public List<AssistDetail> worldDetails() {
        List<AssistDetail> out = new ArrayList<>();
        for (RuleSpec spec : PhysicsRules.SPECS) {
            int value = this.rules.value(spec);
            if (this.source(spec) == null && value != spec.defaultValue()) {
                out.add(AssistDetail.rule(spec, value));
            }
        }
        return out;
    }

    public List<AssistDetail> zoneDetails() {
        List<AssistDetail> out = new ArrayList<>();
        for (RuleSpec spec : PhysicsRules.SPECS) {
            Zone zone = this.source(spec);
            int value = this.rules.value(spec);
            if (zone != null && !zone.raceLegal() && value != spec.defaultValue()) {
                out.add(AssistDetail.zone(zone.id(), spec, value));
            }
        }
        return out;
    }

    public PhysicsRules worldPart() {
        Map<String, Integer> values = new HashMap<>();
        for (RuleSpec spec : PhysicsRules.SPECS) {
            values.put(spec.name(), this.source(spec) == null ? this.rules.value(spec) : spec.defaultValue());
        }
        return PhysicsRules.of(new RuleValues(values));
    }

    public boolean assisted() {
        for (RuleSpec spec : PhysicsRules.SPECS) {
            int value = this.rules.value(spec);
            if (value == spec.defaultValue()) {
                continue;
            }
            Zone zone = this.source(spec);
            if (zone == null || !zone.raceLegal()) {
                return true;
            }
        }
        return false;
    }

    public boolean sameEffect(ZoneResolution other) {
        if (!this.rules.equals(other.rules)) {
            return false;
        }
        for (int i = 0; i < this.sources.size(); i++) {
            Zone a = this.sources.get(i);
            Zone b = other.sources.get(i);
            if (a == null ? b != null : b == null || a.id() != b.id() || a.raceLegal() != b.raceLegal()) {
                return false;
            }
        }
        return true;
    }
}
