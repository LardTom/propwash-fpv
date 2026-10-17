package de.lardtom.propwash.zone;

import de.lardtom.propwash.race.AssistDetail;
import de.lardtom.propwash.server.rules.PhysicsRules;
import de.lardtom.propwash.server.rules.RuleSpec;
import de.lardtom.propwash.server.rules.WorldRuleSpecs;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class ZoneBlend {
    public static final List<RuleSpec> BLENDED = List.of(WorldRuleSpecs.GRAVITY_SCALE, WorldRuleSpecs.WIND);
    private static final Comparator<Entry> ORDER = Comparator.<Entry, Zone>comparing(entry -> entry.zone, ZoneResolver.ASCENDING)
            .thenComparing(entry -> !entry.fading);
    private final Map<Integer, Entry> live = new HashMap<>();
    private final List<Entry> fading = new ArrayList<>();
    private final List<Entry> ordered = new ArrayList<>();

    private static final class Entry {
        private Zone zone;
        private double weight;
        private boolean inside;
        private final boolean fading;

        private Entry(Zone zone, boolean fading) {
            this.zone = zone;
            this.fading = fading;
        }
    }

    public void clear() {
        this.live.clear();
        this.fading.clear();
        this.ordered.clear();
    }

    public void advance(Collection<Zone> sameDimension, ZoneResolution resolution, double seconds, boolean snap) {
        Set<Integer> inside = new HashSet<>();
        for (Zone zone : resolution.inside()) {
            inside.add(zone.id());
        }
        Set<Integer> present = new HashSet<>();
        for (Zone zone : sameDimension) {
            present.add(zone.id());
            Entry entry = this.live.get(zone.id());
            if (entry == null) {
                if (!inside.contains(zone.id())) {
                    continue;
                }
                entry = new Entry(zone, false);
                this.live.put(zone.id(), entry);
            } else if (!snap && entry.weight > 0.0 && zone.transitionTenths() > 0 && !entry.zone.overrides().equals(zone.overrides())) {
                Entry old = new Entry(entry.zone, true);
                old.weight = entry.weight;
                this.fading.add(old);
                entry.weight = 0.0;
            }
            entry.zone = zone;
            entry.inside = inside.contains(zone.id());
        }
        double dt = Double.isFinite(seconds) && seconds > 0.0 ? seconds : 0.0;
        Iterator<Map.Entry<Integer, Entry>> iterator = this.live.entrySet().iterator();
        while (iterator.hasNext()) {
            Entry entry = iterator.next().getValue();
            if (!present.contains(entry.zone.id())) {
                entry.inside = false;
            }
            step(entry, dt, snap);
            if (!entry.inside && entry.weight <= 0.0) {
                iterator.remove();
            }
        }
        this.fading.removeIf(entry -> {
            step(entry, dt, snap);
            return entry.weight <= 0.0;
        });
        this.ordered.clear();
        this.ordered.addAll(this.fading);
        this.ordered.addAll(this.live.values());
        this.ordered.sort(ORDER);
    }

    private static void step(Entry entry, double dt, boolean snap) {
        double target = entry.inside ? 1.0 : 0.0;
        double time = entry.zone.transitionSeconds();
        if (snap || time <= 0.0) {
            entry.weight = target;
        } else if (entry.weight < target) {
            entry.weight = Math.min(target, entry.weight + dt / time);
        } else if (entry.weight > target) {
            entry.weight = Math.max(target, entry.weight - dt / time);
        }
    }

    public double value(RuleSpec spec, PhysicsRules world) {
        double value = world.value(spec);
        for (Entry entry : this.ordered) {
            if (entry.weight > 0.0 && entry.zone.overrides(spec)) {
                double target = entry.zone.value(spec, world.value(spec));
                value = entry.weight >= 1.0 ? target : value + (target - value) * entry.weight;
            }
        }
        return value;
    }

    public List<AssistDetail> details(PhysicsRules world) {
        List<AssistDetail> out = new ArrayList<>();
        List<Zone> shares = new ArrayList<>();
        for (RuleSpec spec : BLENDED) {
            shares.clear();
            boolean worldShare = true;
            for (Entry entry : this.ordered) {
                if (entry.weight > 0.0 && entry.zone.overrides(spec)) {
                    if (entry.weight >= 1.0) {
                        shares.clear();
                        worldShare = false;
                    }
                    shares.add(entry.zone);
                }
            }
            int worldValue = world.value(spec);
            if (worldShare && worldValue != spec.defaultValue()) {
                out.add(AssistDetail.rule(spec, worldValue));
            }
            for (Zone zone : shares) {
                int value = zone.value(spec, worldValue);
                if (!zone.raceLegal() && value != spec.defaultValue()) {
                    out.add(AssistDetail.zone(zone.id(), spec, value));
                }
            }
        }
        return out;
    }

    public double weight(int zoneId) {
        Entry entry = this.live.get(zoneId);
        return entry == null ? 0.0 : entry.weight;
    }

    public boolean settled() {
        if (!this.fading.isEmpty()) {
            return false;
        }
        for (Entry entry : this.live.values()) {
            if (entry.weight != (entry.inside ? 1.0 : 0.0)) {
                return false;
            }
        }
        return true;
    }
}
