package de.lardtom.propwash.creative;

import de.lardtom.propwash.build.DroneBuild;
import de.lardtom.propwash.drone.net.StateValidation;
import de.lardtom.propwash.sim.AirframeSpec;
import de.lardtom.propwash.sim.BuildAnalysis;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.OptionalDouble;

public final class CreativeCheck {
    public static final double INERTIA_LOW_FACTOR = 0.25;
    public static final double INERTIA_HIGH_FACTOR = 4.0;
    public static final double EXTREME_THRUST_TO_WEIGHT = 25.0;

    private CreativeCheck() {
    }

    public enum Warning {
        PROPS_OVERLAP,
        INERTIA_LOW,
        INERTIA_HIGH,
        EXTREME_THRUST,
        OVER_SERVER_SPEED;

        public String translationKey() {
            return "propwash.creative.warning." + this.name().toLowerCase(Locale.ROOT);
        }
    }

    public static List<Warning> check(DroneBuild build) {
        List<Warning> warnings = new ArrayList<>();
        AirframeSpec spec = AirframeSpec.of(build);
        BuildAnalysis analysis = BuildAnalysis.of(build);
        if (spec.propDiameter > spec.motorSpacing + 1.0E-3) {
            warnings.add(Warning.PROPS_OVERLAP);
        }
        boolean low = false;
        boolean high = false;
        for (CreativeParam param : CreativeParam.INERTIA) {
            OptionalDouble value = build.creative().value(param);
            if (value.isEmpty()) {
                continue;
            }
            double auto = CreativeFlight.baseValue(build, param);
            double set = param.clamp(value.getAsDouble());
            low |= set < INERTIA_LOW_FACTOR * auto;
            high |= set > INERTIA_HIGH_FACTOR * auto;
        }
        if (low) {
            warnings.add(Warning.INERTIA_LOW);
        }
        if (high) {
            warnings.add(Warning.INERTIA_HIGH);
        }
        if (analysis.thrustToWeight() > EXTREME_THRUST_TO_WEIGHT) {
            warnings.add(Warning.EXTREME_THRUST);
        }
        if (analysis.topSpeedAcroKmh() > StateValidation.PHYSICS_MAX_SPEED * 3.6) {
            warnings.add(Warning.OVER_SERVER_SPEED);
        }
        return warnings;
    }
}
