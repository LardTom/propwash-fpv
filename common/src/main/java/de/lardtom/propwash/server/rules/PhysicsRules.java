package de.lardtom.propwash.server.rules;

import de.lardtom.propwash.sim.SimTuning;

import java.util.ArrayList;
import java.util.List;

import static de.lardtom.propwash.server.rules.WorldRuleSpecs.BATTERY_SAG;
import static de.lardtom.propwash.server.rules.WorldRuleSpecs.BATTERY_SCALE;
import static de.lardtom.propwash.server.rules.WorldRuleSpecs.CRASH_DISARM;
import static de.lardtom.propwash.server.rules.WorldRuleSpecs.CRASH_TOLERANCE;
import static de.lardtom.propwash.server.rules.WorldRuleSpecs.GRAVITY_SCALE;
import static de.lardtom.propwash.server.rules.WorldRuleSpecs.GROUND_EFFECT;
import static de.lardtom.propwash.server.rules.WorldRuleSpecs.HARDWARE_LIMITS;
import static de.lardtom.propwash.server.rules.WorldRuleSpecs.PROP_STRIKES;
import static de.lardtom.propwash.server.rules.WorldRuleSpecs.PROP_WASH;
import static de.lardtom.propwash.server.rules.WorldRuleSpecs.TURBULENCE;
import static de.lardtom.propwash.server.rules.WorldRuleSpecs.WIND;

public record PhysicsRules(int gravityPercent, int batteryPercent, int wind, boolean propWash, boolean groundEffect, boolean batterySag,
                           boolean crashDisarm, boolean propStrikes, boolean turbulence, int crashTolerancePercent, boolean hardwareLimits) {
    public static final PhysicsRules DEFAULT = of(RuleValues.DEFAULTS);
    public static final List<RuleSpec> SPECS = List.of(GRAVITY_SCALE, PROP_WASH, GROUND_EFFECT, HARDWARE_LIMITS, WIND, TURBULENCE, BATTERY_SCALE,
            BATTERY_SAG, CRASH_TOLERANCE, CRASH_DISARM, PROP_STRIKES);

    public static PhysicsRules of(RuleValues values) {
        return new PhysicsRules(values.get(GRAVITY_SCALE), values.get(BATTERY_SCALE), values.get(WIND), values.on(PROP_WASH),
                values.on(GROUND_EFFECT), values.on(BATTERY_SAG), values.on(CRASH_DISARM), values.on(PROP_STRIKES), values.on(TURBULENCE),
                values.get(CRASH_TOLERANCE), values.on(HARDWARE_LIMITS));
    }

    public boolean infiniteBattery() {
        return this.batteryPercent == 0;
    }

    public boolean modified() {
        return !this.equals(DEFAULT);
    }

    public int value(RuleSpec spec) {
        if (spec == GRAVITY_SCALE) {
            return this.gravityPercent;
        }
        if (spec == BATTERY_SCALE) {
            return this.batteryPercent;
        }
        if (spec == WIND) {
            return this.wind;
        }
        if (spec == CRASH_TOLERANCE) {
            return this.crashTolerancePercent;
        }
        boolean on;
        if (spec == PROP_WASH) {
            on = this.propWash;
        } else if (spec == GROUND_EFFECT) {
            on = this.groundEffect;
        } else if (spec == BATTERY_SAG) {
            on = this.batterySag;
        } else if (spec == CRASH_DISARM) {
            on = this.crashDisarm;
        } else if (spec == PROP_STRIKES) {
            on = this.propStrikes;
        } else if (spec == TURBULENCE) {
            on = this.turbulence;
        } else if (spec == HARDWARE_LIMITS) {
            on = this.hardwareLimits;
        } else {
            return spec.defaultValue();
        }
        return on ? 1 : 0;
    }

    public List<RuleSpec> changedSpecs() {
        List<RuleSpec> out = new ArrayList<>();
        for (RuleSpec spec : SPECS) {
            if (this.value(spec) != spec.defaultValue()) {
                out.add(spec);
            }
        }
        return out;
    }

    public static List<RuleSpec> changed(RuleValues values) {
        List<RuleSpec> out = new ArrayList<>();
        for (RuleSpec spec : SPECS) {
            if (values.get(spec) != spec.defaultValue()) {
                out.add(spec);
            }
        }
        return out;
    }

    public SimTuning toSimTuning() {
        SimTuning t = new SimTuning();
        t.propWash = this.propWash;
        t.wakeField = this.propWash;
        t.groundEffect = this.groundEffect;
        t.batterySag = this.batterySag;
        t.infiniteBattery = this.infiniteBattery();
        t.batteryScale = this.infiniteBattery() ? 1.0 : this.batteryPercent / 100.0;
        t.crashDisarm = this.crashDisarm;
        t.propStrikes = this.propStrikes;
        t.turbulenceScale = this.turbulence ? 1.0 : 0.0;
        t.windScale = this.wind;
        t.crashSpeedScale = this.crashTolerancePercent / 100.0;
        t.thermalLimits = this.hardwareLimits;
        t.escOvervoltage = this.hardwareLimits;
        t.gravityScale = this.gravityPercent / 100.0;
        t.sanitize();
        return t;
    }
}
