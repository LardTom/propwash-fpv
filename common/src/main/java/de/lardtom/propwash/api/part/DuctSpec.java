package de.lardtom.propwash.api.part;

import java.util.Objects;
import java.util.OptionalDouble;

public record DuctSpec(OptionalDouble thrustGain, OptionalDouble clearanceMm, OptionalDouble translationalLiftGain, OptionalDouble washLoss,
                       OptionalDouble washNoise, OptionalDouble restitution, OptionalDouble friction) {
    public static final DuctSpec STANDARD = new DuctSpec(OptionalDouble.empty(), OptionalDouble.empty(), OptionalDouble.empty(),
            OptionalDouble.empty(), OptionalDouble.empty(), OptionalDouble.empty(), OptionalDouble.empty());

    public DuctSpec {
        thrustGain = range(thrustGain, 0.5, 2.0, "Schubgewinn");
        clearanceMm = range(clearanceMm, 0.0, 50.0, "Spaltmaß");
        translationalLiftGain = range(translationalLiftGain, 0.0, 1.0, "Translationsauftrieb");
        washLoss = range(washLoss, 0.0, 1.0, "Propwash-Verlust");
        washNoise = range(washNoise, 0.0, 1.0, "Propwash-Rauschen");
        restitution = range(restitution, 0.0, 0.95, "Rückprall");
        friction = range(friction, 0.0, 2.0, "Reibung");
    }

    private static OptionalDouble range(OptionalDouble value, double min, double max, String name) {
        OptionalDouble v = Objects.requireNonNullElse(value, OptionalDouble.empty());
        if (v.isPresent() && !(v.getAsDouble() >= min && v.getAsDouble() <= max)) {
            throw new IllegalArgumentException(name + " muss zwischen " + min + " und " + max + " liegen: " + v.getAsDouble());
        }
        return v;
    }
}
