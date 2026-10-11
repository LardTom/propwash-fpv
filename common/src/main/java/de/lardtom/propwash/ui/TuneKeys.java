package de.lardtom.propwash.ui;

import de.lardtom.propwash.drone.TuneMapping;
import de.lardtom.propwash.sim.Axis;
import de.lardtom.propwash.sim.FlightControllerConfig;
import de.lardtom.propwash.sim.ItermRelaxMode;
import de.lardtom.propwash.sim.PidLoopRate;
import de.lardtom.propwash.sim.PidSource;
import de.lardtom.propwash.sim.RatesType;
import de.lardtom.propwash.sim.ThrottleLimitType;
import de.lardtom.propwash.sim.TpaMode;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class TuneKeys {
    public static final String RATES_TYPE = "rates.type";
    public static final String RC_DEADBAND = "rates.rcDeadband";
    public static final String YAW_DEADBAND = "rates.yawDeadband";
    public static final String PID_SOURCE = TuneMapping.PID_SOURCE;
    public static final String ANGLE_STRENGTH = "pid.angleStrength";
    public static final String ANGLE_LIMIT = "pid.angleLimitDeg";
    public static final String HORIZON_STRENGTH = "pid.horizonStrength";
    public static final String ITERM_RELAX = "pid.itermRelax";
    public static final String ANTI_GRAVITY = "pid.antiGravityGain";
    public static final String TPA_MODE = "pid.tpaMode";
    public static final String TPA_RATE = "pid.tpaRatePercent";
    public static final String TPA_BREAKPOINT = "pid.tpaBreakpoint";
    public static final String AIRMODE = "pid.airmode";
    public static final String MOTOR_IDLE = "pid.motorIdlePercent";
    public static final String THROTTLE_MID = "pid.throttleMid";
    public static final String THROTTLE_EXPO = "pid.throttleExpo";
    public static final String THROTTLE_LIMIT_TYPE = "pid.throttleLimitType";
    public static final String THROTTLE_LIMIT = "pid.throttleLimitPercent";
    public static final String THRUST_LINEAR = "pid.thrustLinearPercent";
    public static final String VBAT_SAG = "pid.vbatSagCompensationPercent";
    public static final String ARM_MAX_ANGLE = TuneMapping.ARM_MAX_ANGLE;
    public static final String PID_LOOP = "filters.pidLoop";
    public static final String GYRO_LPF = "filters.gyroLpfHz";
    public static final String DTERM_LPF1 = "filters.dtermLpf1Hz";
    public static final String DTERM_LPF2 = "filters.dtermLpf2Hz";
    public static final String RC_SMOOTHING = "filters.rcSmoothingHz";
    public static final String HOLD_ALT_P = "hold.altP";
    public static final String HOLD_ALT_I = "hold.altI";
    public static final String HOLD_ALT_D = "hold.altD";
    public static final String HOLD_CLIMB_RATE = "hold.climbRateCms";
    public static final String HOLD_THROTTLE_DEADBAND = "hold.throttleDeadbandPercent";
    public static final String HOLD_MID_THROTTLE = "hold.midThrottle";
    public static final String HOLD_POS_P = "hold.posP";
    public static final String HOLD_POS_I = "hold.posI";
    public static final String HOLD_POS_D = "hold.posD";
    public static final String HOLD_MAX_ANGLE = "hold.posMaxAngleDeg";
    public static final String HOLD_STICK_DEADBAND = "hold.stickDeadbandPercent";
    public static final String HOLD_MIN_SATS = "hold.minSatellites";

    public static final List<String> PID_TERMS = List.of("p", "i", "d", "ff");
    public static final List<String> RATE_FIELDS = List.of("rcRate", "superRate", "rcExpo", "centerRateDps", "maxRateDps", "actualExpo");

    private static final Map<String, TuneParam> PARAMS;

    static {
        Map<String, TuneParam> p = new LinkedHashMap<>();
        add(p, TuneParam.choice(RATES_TYPE, RatesType.class));
        for (Axis axis : Axis.values()) {
            add(p, TuneParam.number(rateKey(axis, "rcRate"), 0.01, 2.55, 0.01, 2, ""));
            add(p, TuneParam.number(rateKey(axis, "superRate"), 0.0, 0.99, 0.01, 2, ""));
            add(p, TuneParam.number(rateKey(axis, "rcExpo"), 0.0, 1.0, 0.01, 2, ""));
            add(p, TuneParam.number(rateKey(axis, "centerRateDps"), 10.0, 1998.0, 5.0, 0, "°/s"));
            add(p, TuneParam.number(rateKey(axis, "maxRateDps"), 10.0, 1998.0, 10.0, 0, "°/s"));
            add(p, TuneParam.number(rateKey(axis, "actualExpo"), 0.0, 1.0, 0.01, 2, ""));
        }
        add(p, TuneParam.scaled(RC_DEADBAND, 0.0, 0.2, 0.005, 1, 100.0, "%"));
        add(p, TuneParam.scaled(YAW_DEADBAND, 0.0, 0.2, 0.005, 1, 100.0, "%"));
        add(p, TuneParam.choice(PID_SOURCE, PidSource.class));
        for (Axis axis : Axis.values()) {
            add(p, TuneParam.number(pidKey(axis, "p"), 0, 250, 1, 0, ""));
            add(p, TuneParam.number(pidKey(axis, "i"), 0, 250, 1, 0, ""));
            add(p, TuneParam.number(pidKey(axis, "d"), 0, 200, 1, 0, ""));
            add(p, TuneParam.number(pidKey(axis, "ff"), 0, 500, 1, 0, ""));
        }
        add(p, TuneParam.number(ANGLE_STRENGTH, 0, 200, 1, 0, ""));
        add(p, TuneParam.number(ANGLE_LIMIT, 10, 85, 1, 0, "°"));
        add(p, TuneParam.number(HORIZON_STRENGTH, 0, 200, 1, 0, ""));
        add(p, TuneParam.choice(ITERM_RELAX, ItermRelaxMode.class));
        add(p, TuneParam.number(ANTI_GRAVITY, 0, 250, 1, 0, ""));
        add(p, TuneParam.choice(TPA_MODE, TpaMode.class));
        add(p, TuneParam.number(TPA_RATE, 0, 100, 1, 0, "%"));
        add(p, TuneParam.scaled(TPA_BREAKPOINT, 0.0, 0.95, 0.01, 0, 100.0, "%"));
        add(p, TuneParam.toggle(AIRMODE));
        add(p, TuneParam.number(MOTOR_IDLE, 0, 20, 0.1, 1, "%"));
        add(p, TuneParam.number(THROTTLE_MID, 0.05, 0.95, 0.01, 2, ""));
        add(p, TuneParam.number(THROTTLE_EXPO, 0.0, 1.0, 0.01, 2, ""));
        add(p, TuneParam.choice(THROTTLE_LIMIT_TYPE, ThrottleLimitType.class));
        add(p, TuneParam.number(THROTTLE_LIMIT, 25, 100, 1, 0, "%"));
        add(p, TuneParam.number(THRUST_LINEAR, 0, 100, 1, 0, "%"));
        add(p, TuneParam.number(VBAT_SAG, 0, 100, 1, 0, "%"));
        add(p, TuneParam.number(ARM_MAX_ANGLE, FlightControllerConfig.ARM_ANGLE_MIN, FlightControllerConfig.ARM_ANGLE_MAX, 1, 0, "°"));
        add(p, TuneParam.choice(PID_LOOP, PidLoopRate.class));
        add(p, TuneParam.filter(GYRO_LPF, 20, 1000, 5));
        add(p, TuneParam.filter(DTERM_LPF1, 20, 1000, 5));
        add(p, TuneParam.filter(DTERM_LPF2, 20, 1000, 5));
        add(p, TuneParam.filter(RC_SMOOTHING, 5, 200, 1));
        add(p, TuneParam.number(HOLD_ALT_P, 0, 200, 1, 0, ""));
        add(p, TuneParam.number(HOLD_ALT_I, 0, 200, 1, 0, ""));
        add(p, TuneParam.number(HOLD_ALT_D, 0, 200, 1, 0, ""));
        add(p, TuneParam.number(HOLD_CLIMB_RATE, 20, 1000, 10, 0, "cm/s"));
        add(p, TuneParam.number(HOLD_THROTTLE_DEADBAND, 2, 40, 1, 0, "%"));
        add(p, TuneParam.toggle(HOLD_MID_THROTTLE));
        add(p, TuneParam.number(HOLD_POS_P, 0, 200, 1, 0, ""));
        add(p, TuneParam.number(HOLD_POS_I, 0, 200, 1, 0, ""));
        add(p, TuneParam.number(HOLD_POS_D, 0, 200, 1, 0, ""));
        add(p, TuneParam.number(HOLD_MAX_ANGLE, 5, 45, 1, 0, "°"));
        add(p, TuneParam.number(HOLD_STICK_DEADBAND, 2, 30, 1, 0, "%"));
        add(p, TuneParam.number(HOLD_MIN_SATS, 5, 12, 1, 0, ""));
        PARAMS = Collections.unmodifiableMap(p);
    }

    private TuneKeys() {
    }

    private static void add(Map<String, TuneParam> map, TuneParam param) {
        map.put(param.key(), param);
    }

    public static String axisName(Axis axis) {
        return axis.name().toLowerCase(Locale.ROOT);
    }

    public static String rateKey(Axis axis, String field) {
        return "rates." + axisName(axis) + "." + field;
    }

    public static String pidKey(Axis axis, String term) {
        return "pid." + axisName(axis) + "." + term;
    }

    public static List<String> pidTermKeys() {
        List<String> keys = new ArrayList<>(12);
        for (Axis axis : Axis.values()) {
            for (String term : PID_TERMS) {
                keys.add(pidKey(axis, term));
            }
        }
        return keys;
    }

    public static boolean isPidTermKey(String key) {
        for (Axis axis : Axis.values()) {
            String prefix = "pid." + axisName(axis) + ".";
            if (key.startsWith(prefix) && PID_TERMS.contains(key.substring(prefix.length()))) {
                return true;
            }
        }
        return false;
    }

    public static boolean isDerivedKey(String key) {
        return isPidTermKey(key) || key.equals(ANGLE_STRENGTH) || key.equals(TPA_BREAKPOINT);
    }

    public static Map<String, TuneParam> params() {
        return PARAMS;
    }

    public static @Nullable TuneParam param(String key) {
        return PARAMS.get(key);
    }
}
