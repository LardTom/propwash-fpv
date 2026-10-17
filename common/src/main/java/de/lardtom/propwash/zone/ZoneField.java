package de.lardtom.propwash.zone;

import de.lardtom.propwash.race.AssistDetail;
import de.lardtom.propwash.server.rules.PhysicsRules;
import de.lardtom.propwash.server.rules.WorldRuleSpecs;
import de.lardtom.propwash.sim.SimTuning;

import java.util.Collection;
import java.util.List;

public final class ZoneField {
    private final ZoneBlend blend = new ZoneBlend();
    private ZoneResolution resolution = ZoneResolution.world(PhysicsRules.DEFAULT);
    private double gravityPercent = WorldRuleSpecs.GRAVITY_SCALE.defaultValue();
    private double wind = WorldRuleSpecs.WIND.defaultValue();
    private List<AssistDetail> blendDetails = List.of();
    private boolean started;

    public void reset() {
        this.blend.clear();
        this.started = false;
        this.resolution = ZoneResolution.world(PhysicsRules.DEFAULT);
        this.gravityPercent = WorldRuleSpecs.GRAVITY_SCALE.defaultValue();
        this.wind = WorldRuleSpecs.WIND.defaultValue();
        this.blendDetails = List.of();
    }

    public boolean update(Collection<Zone> zones, String dimension, PhysicsRules world, double x, double y, double z, double seconds) {
        List<Zone> here = ZoneResolver.inDimension(zones, dimension);
        ZoneResolution next = ZoneResolver.resolve(here, world, x, y, z);
        this.blend.advance(here, next, seconds, !this.started);
        double gravity = this.blend.value(WorldRuleSpecs.GRAVITY_SCALE, world);
        double windLevel = this.blend.value(WorldRuleSpecs.WIND, world);
        List<AssistDetail> details = List.copyOf(this.blend.details(world));
        boolean changed = !this.started || !next.sameEffect(this.resolution) || gravity != this.gravityPercent || windLevel != this.wind;
        this.started = true;
        this.blendDetails = details;
        this.resolution = next;
        this.gravityPercent = gravity;
        this.wind = windLevel;
        return changed;
    }

    public ZoneResolution resolution() {
        return this.resolution;
    }

    public PhysicsRules rules() {
        return this.resolution.rules();
    }

    public boolean assisted() {
        return this.resolution.assisted() || !this.blendDetails.isEmpty();
    }

    public List<AssistDetail> blendDetails() {
        return this.blendDetails;
    }

    public double gravityPercent() {
        return this.gravityPercent;
    }

    public double wind() {
        return this.wind;
    }

    public boolean blending() {
        return !this.blend.settled();
    }

    public double weight(int zoneId) {
        return this.blend.weight(zoneId);
    }

    public SimTuning apply(SimTuning tuning) {
        tuning.gravityScale = this.gravityPercent / 100.0;
        tuning.windScale = this.wind;
        tuning.sanitize();
        return tuning;
    }
}
