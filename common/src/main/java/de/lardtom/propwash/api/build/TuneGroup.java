package de.lardtom.propwash.api.build;

import java.util.Collections;
import java.util.EnumSet;
import java.util.Locale;
import java.util.Set;

public enum TuneGroup {
    RATES,
    PID,
    FEEDFORWARD,
    TPA,
    FILTERS,
    THROTTLE,
    HOLD,
    CAMERA,
    LED,
    VTX,
    OTHER;

    public static final Set<TuneGroup> FLIGHT = Collections.unmodifiableSet(EnumSet.of(RATES, PID, FEEDFORWARD, TPA, FILTERS, THROTTLE));

    public String translationKey() {
        return "propwash.api.tune_group." + this.name().toLowerCase(Locale.ROOT);
    }

    public static TuneGroup of(String key) {
        if (key == null) {
            return OTHER;
        }
        if (key.startsWith("rates.")) {
            return RATES;
        }
        if (key.startsWith("filters.")) {
            return FILTERS;
        }
        if (key.startsWith("hold.")) {
            return HOLD;
        }
        if (key.startsWith("camera.")) {
            return CAMERA;
        }
        if (key.startsWith("led.")) {
            return LED;
        }
        if (key.startsWith("vtx.")) {
            return VTX;
        }
        if (key.startsWith("pid.")) {
            if (key.endsWith(".ff")) {
                return FEEDFORWARD;
            }
            if (key.startsWith("pid.tpa")) {
                return TPA;
            }
            if (key.startsWith("pid.throttle") || key.equals("pid.motorIdlePercent") || key.equals("pid.thrustLinearPercent")
                    || key.equals("pid.vbatSagCompensationPercent")) {
                return THROTTLE;
            }
            return PID;
        }
        return OTHER;
    }
}
