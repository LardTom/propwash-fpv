package de.lardtom.propwash.zone;

import de.lardtom.propwash.server.rules.PhysicsRules;
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
        return resolve(inDimension(zones, dimension), world, x, y, z);
    }

    public static ZoneResolution resolve(Collection<Zone> sameDimension, PhysicsRules world, double x, double y, double z) {
        List<Zone> inside = containing(sameDimension, x, y, z);
        if (inside.isEmpty()) {
            return ZoneResolution.world(world);
        }
        Map<String, Integer> values = new HashMap<>();
        List<Zone> sources = new ArrayList<>(PhysicsRules.SPECS.size());
        for (RuleSpec spec : PhysicsRules.SPECS) {
            Zone source = null;
            for (Zone zone : inside) {
                if (zone.overrides(spec)) {
                    source = zone;
                    break;
                }
            }
            sources.add(source);
            values.put(spec.name(), source == null ? world.value(spec) : source.value(spec, world.value(spec)));
        }
        return new ZoneResolution(world, PhysicsRules.of(new RuleValues(values)), inside, sources);
    }
}
