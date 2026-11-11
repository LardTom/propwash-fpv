package de.lardtom.propwash.zone;

import de.lardtom.propwash.server.rules.PhysicsRules;
import de.lardtom.propwash.server.rules.RuleBase;
import de.lardtom.propwash.server.rules.RuleSpec;
import de.lardtom.propwash.server.rules.RuleValues;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class ZoneResolver {
    public static final Comparator<Zone> ASCENDING = Comparator.comparingInt(Zone::priority).thenComparingInt(Zone::id);
    public static final Comparator<Zone> DESCENDING = ASCENDING.reversed();

    private ZoneResolver() {
    }

    public static List<Zone> inDimension(Collection<Zone> zones, String dimension) {
        List<Zone> out = new ArrayList<>();
        for (Zone zone : zones) {
            if (zone.dimension().equals(dimension)) {
                out.add(zone);
            }
        }
        return out;
    }

    public static List<Zone> containing(Collection<Zone> zones, double x, double y, double z) {
        List<Zone> out = new ArrayList<>();
        for (Zone zone : zones) {
            if (zone.contains(x, y, z)) {
                out.add(zone);
            }
        }
        out.sort(DESCENDING);
        return out;
    }

    public static ZoneResolution resolve(Collection<Zone> zones, String dimension, PhysicsRules world, double x, double y, double z) {
        return resolve(inDimension(zones, dimension), RuleBase.of(world), x, y, z);
    }

    public static ZoneResolution resolve(Collection<Zone> zones, String dimension, RuleBase base, double x, double y, double z) {
        return resolve(inDimension(zones, dimension), base, x, y, z);
    }

    public static ZoneResolution resolve(Collection<Zone> sameDimension, PhysicsRules world, double x, double y, double z) {
        return resolve(sameDimension, RuleBase.of(world), x, y, z);
    }

    public static ZoneResolution resolve(Collection<Zone> sameDimension, RuleBase base, double x, double y, double z) {
        List<Zone> inside = containing(sameDimension, x, y, z);
        if (inside.isEmpty()) {
            return ZoneResolution.world(base);
        }
        PhysicsRules world = base.server();
        Map<String, Integer> values = new HashMap<>();
        List<Zone> sources = new ArrayList<>(PhysicsRules.SPECS.size());
        List<Zone> forcing = new ArrayList<>(PhysicsRules.SPECS.size());
        for (RuleSpec spec : PhysicsRules.SPECS) {
            Zone decider = null;
            if (base.zones(spec)) {
                for (Zone zone : inside) {
                    if (zone.sets(spec)) {
                        decider = zone;
                        break;
                    }
                }
            }
            boolean own = decider != null && decider.overrides(spec);
            sources.add(own ? decider : null);
            forcing.add(own ? null : decider);
            int value;
            if (own) {
                value = decider.value(spec, world.value(spec));
            } else if (decider != null) {
                value = world.value(spec);
            } else {
                value = base.baseValue(spec);
            }
            values.put(spec.name(), value);
        }
        return new ZoneResolution(world, PhysicsRules.of(new RuleValues(values)), inside, sources, forcing);
    }

    public static ZoneResolution below(Collection<Zone> sameDimension, Zone zone, PhysicsRules world, double x, double y, double z) {
        return below(sameDimension, zone, RuleBase.of(world), x, y, z);
    }

    public static ZoneResolution below(Collection<Zone> sameDimension, Zone zone, RuleBase world, double x, double y, double z) {
        List<Zone> lower = new ArrayList<>();
        for (Zone other : sameDimension) {
            boolean lowerZone = zone.id() == Zone.NEW ? other.priority() <= zone.priority() : ASCENDING.compare(other, zone) < 0;
            if (other.id() != zone.id() && other.dimension().equals(zone.dimension()) && lowerZone) {
                lower.add(other);
            }
        }
        return resolve(lower, world, x, y, z);
    }
}
