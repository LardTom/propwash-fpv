package de.lardtom.propwash.drone;

import de.lardtom.propwash.drone.led.LedSettings;
import de.lardtom.propwash.video.VtxSettings;
import de.lardtom.propwash.sim.AxisPid;
import de.lardtom.propwash.sim.RateCurve;
import de.lardtom.propwash.sim.AirframeSpec;
import de.lardtom.propwash.sim.FlightControllerConfig;
import de.lardtom.propwash.sim.ItermRelaxMode;
import de.lardtom.propwash.sim.PidAutoTune;
import de.lardtom.propwash.sim.PidLoopRate;
import de.lardtom.propwash.sim.PidSource;
import de.lardtom.propwash.sim.RatesType;
import de.lardtom.propwash.sim.ThrottleLimitType;
import de.lardtom.propwash.sim.TpaMode;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.Function;
import java.util.function.ObjDoubleConsumer;
import java.util.function.ToDoubleFunction;

public final class TuneMapping {
    public static final String PID_SOURCE = "pid.source";
    public static final String CAMERA_UPTILT = "camera.uptiltDeg";
    public static final String ARM_MAX_ANGLE = "pid.armMaxAngleDeg";
    public static final float CAMERA_UPTILT_MIN = 0.0F;
    public static final float CAMERA_UPTILT_MAX = 80.0F;

    private static final String[] AXES = {"roll", "pitch", "yaw"};
    private static final double EPSILON = 1.0E-4;
    private static final Map<String, Entry> ENTRIES = buildEntries();
    private static final List<String> KEYS = List.copyOf(ENTRIES.keySet());
    private static final List<String> PID_AXIS_KEYS = buildPidAxisKeys();

    private TuneMapping() {
    }

    public static List<String> keys() {
        return KEYS;
    }

    public static List<String> pidAxisKeys() {
        return PID_AXIS_KEYS;
    }

    public static boolean isKnownKey(String key) {
        return ENTRIES.containsKey(key) || isDroneSetting(key);
    }

    public static boolean isDroneSetting(String key) {
        return CAMERA_UPTILT.equals(key) || LedSettings.isKey(key) || VtxSettings.isKey(key);
    }

    public static FlightControllerConfig toConfig(Map<String, Float> tune, AirframeSpec params) {
        FlightControllerConfig config = new FlightControllerConfig();
        for (Map.Entry<String, Entry> e : ENTRIES.entrySet()) {
            if (e.getKey().startsWith("filters.")) {
                apply(config, tune, e.getKey(), e.getValue());
            }
        }
        config.filters.sanitize();
        config.pid = PidAutoTune.derive(params, config.filters, config.pid);
        boolean custom = sourceOf(tune) == PidSource.CUSTOM;
        for (Map.Entry<String, Entry> e : ENTRIES.entrySet()) {
            String key = e.getKey();
            if (key.startsWith("filters.") || PID_SOURCE.equals(key)) {
                continue;
            }
            if (!custom && PID_AXIS_KEYS.contains(key)) {
                continue;
            }
            apply(config, tune, key, e.getValue());
        }
        config.pid.source = custom ? PidSource.CUSTOM : PidSource.DERIVED;
        config.pid.derivedForBuild = params.buildKey;
        config.sanitize();
        return config;
    }

    public static Map<String, Float> fromConfig(FlightControllerConfig config) {
        Map<String, Float> out = new TreeMap<>();
        for (Map.Entry<String, Entry> e : ENTRIES.entrySet()) {
            out.put(e.getKey(), (float) e.getValue().getter().applyAsDouble(config));
        }
        return out;
    }

    public static Map<String, Float> defaults(AirframeSpec params) {
        return fromConfig(toConfig(Map.of(), params));
    }

    public static Map<String, Float> effective(Map<String, Float> stored, AirframeSpec params) {
        Map<String, Float> out = fromConfig(toConfig(stored, params));
        Float uptilt = stored.get(CAMERA_UPTILT);
        if (uptilt != null && Float.isFinite(uptilt)) {
            out.put(CAMERA_UPTILT, clampUptilt(uptilt));
        }
        for (String key : LedSettings.KEYS) {
            Float value = stored.get(key);
            if (value != null && Float.isFinite(value)) {
                out.put(key, LedSettings.sanitize(key, value));
            }
        }
        VtxSettings.copyClean(stored, out);
        return out;
    }

    public static Map<String, Float> normalize(Map<String, Float> tune, AirframeSpec params) {
        FlightControllerConfig config = toConfig(tune, params);
        Map<String, Float> full = fromConfig(config);
        Map<String, Float> base = defaults(params);
        boolean custom = config.pid.source == PidSource.CUSTOM;
        Map<String, Float> out = new TreeMap<>();
        for (String key : KEYS) {
            float value = full.get(key);
            boolean pidKey = PID_SOURCE.equals(key) || PID_AXIS_KEYS.contains(key);
            if ((custom && pidKey) || !same(value, base.get(key))) {
                out.put(key, value);
            }
        }
        Float uptilt = tune.get(CAMERA_UPTILT);
        if (uptilt != null && Float.isFinite(uptilt)) {
            out.put(CAMERA_UPTILT, clampUptilt(uptilt));
        }
        for (String key : LedSettings.KEYS) {
            Float value = tune.get(key);
            if (value != null && Float.isFinite(value)) {
                float clean = LedSettings.sanitize(key, value);
                if (clean != LedSettings.defaultValue(key)) {
                    out.put(key, clean);
                }
            }
        }
        VtxSettings.copyClean(tune, out);
        return out;
    }

    public static Map<String, Float> applyEdit(Map<String, Float> stored, Map<String, Float> edited, AirframeSpec params) {
        Map<String, Float> before = effective(stored, params);
        Map<String, Float> merged = new TreeMap<>();
        for (Map.Entry<String, Float> e : edited.entrySet()) {
            if (e.getKey() != null && e.getValue() != null && Float.isFinite(e.getValue()) && isKnownKey(e.getKey())) {
                merged.put(e.getKey(), e.getValue());
            }
        }
        boolean axisPresent = false;
        boolean axisChanged = false;
        for (String key : PID_AXIS_KEYS) {
            Float value = merged.get(key);
            if (value != null) {
                axisPresent = true;
                axisChanged |= !same(value, before.get(key));
            }
        }
        Float requested = merged.get(PID_SOURCE);
        boolean sourceSwitched = requested != null && enumValue(PidSource.values(), requested) != sourceOf(before);
        if (!sourceSwitched && axisChanged) {
            merged.put(PID_SOURCE, (float) PidSource.CUSTOM.ordinal());
        } else if (requested == null) {
            merged.put(PID_SOURCE, axisPresent ? before.get(PID_SOURCE) : (float) PidSource.DERIVED.ordinal());
        }
        if (sourceOf(merged) == PidSource.CUSTOM) {
            for (String key : PID_AXIS_KEYS) {
                merged.putIfAbsent(key, before.get(key));
            }
        }
        return normalize(merged, params);
    }

    public static float cameraUptilt(Map<String, Float> tune, float fallback) {
        Float value = tune.get(CAMERA_UPTILT);
        return value != null && Float.isFinite(value) ? clampUptilt(value) : clampUptilt(fallback);
    }

    public static float clampUptilt(float value) {
        if (!Float.isFinite(value)) {
            return CAMERA_UPTILT_MIN;
        }
        return Math.max(CAMERA_UPTILT_MIN, Math.min(CAMERA_UPTILT_MAX, value));
    }

    public static PidSource sourceOf(Map<String, Float> tune) {
        Float source = tune.get(PID_SOURCE);
        if (source != null) {
            return enumValue(PidSource.values(), source);
        }
        for (String key : PID_AXIS_KEYS) {
            if (tune.containsKey(key)) {
                return PidSource.CUSTOM;
            }
        }
        return PidSource.DERIVED;
    }

    private static void apply(FlightControllerConfig config, Map<String, Float> tune, String key, Entry entry) {
        Float value = tune.get(key);
        if (value != null && Float.isFinite(value)) {
            entry.setter().accept(config, value);
        }
    }

    private static boolean same(float a, float b) {
        return Math.abs(a - b) <= EPSILON * Math.max(1.0, Math.abs(b));
    }

    private static <E extends Enum<E>> E enumValue(E[] values, double raw) {
        long index = Math.round(raw);
        if (index < 0) {
            index = 0;
        } else if (index >= values.length) {
            index = values.length - 1;
        }
        return values[(int) index];
    }

    private static List<String> buildPidAxisKeys() {
        List<String> keys = new ArrayList<>();
        for (String axis : AXES) {
            for (String term : new String[]{"p", "i", "d", "ff"}) {
                keys.add("pid." + axis + "." + term);
            }
        }
        return Collections.unmodifiableList(keys);
    }

    private static Map<String, Entry> buildEntries() {
        Map<String, Entry> m = new LinkedHashMap<>();
        enumEntry(m, "rates.type", RatesType.values(), c -> c.rates.type, (c, v) -> c.rates.type = v);
        for (String axis : AXES) {
            Function<FlightControllerConfig, RateCurve> rates = c -> switch (axis) {
                case "roll" -> c.rates.roll;
                case "pitch" -> c.rates.pitch;
                default -> c.rates.yaw;
            };
            String p = "rates." + axis + ".";
            number(m, p + "rcRate", c -> rates.apply(c).rcRate, (c, v) -> rates.apply(c).rcRate = v);
            number(m, p + "superRate", c -> rates.apply(c).superRate, (c, v) -> rates.apply(c).superRate = v);
            number(m, p + "rcExpo", c -> rates.apply(c).rcExpo, (c, v) -> rates.apply(c).rcExpo = v);
            number(m, p + "centerRateDps", c -> rates.apply(c).centerRateDps, (c, v) -> rates.apply(c).centerRateDps = v);
            number(m, p + "maxRateDps", c -> rates.apply(c).maxRateDps, (c, v) -> rates.apply(c).maxRateDps = v);
            number(m, p + "actualExpo", c -> rates.apply(c).actualExpo, (c, v) -> rates.apply(c).actualExpo = v);
        }
        number(m, "rates.rcDeadband", c -> c.rates.rcDeadband, (c, v) -> c.rates.rcDeadband = v);
        number(m, "rates.yawDeadband", c -> c.rates.yawDeadband, (c, v) -> c.rates.yawDeadband = v);
        enumEntry(m, PID_SOURCE, PidSource.values(), c -> c.pid.source, (c, v) -> c.pid.source = v);
        for (String axis : AXES) {
            Function<FlightControllerConfig, AxisPid> pid = c -> switch (axis) {
                case "roll" -> c.pid.roll;
                case "pitch" -> c.pid.pitch;
                default -> c.pid.yaw;
            };
            String p = "pid." + axis + ".";
            number(m, p + "p", c -> pid.apply(c).p, (c, v) -> pid.apply(c).p = v);
            number(m, p + "i", c -> pid.apply(c).i, (c, v) -> pid.apply(c).i = v);
            number(m, p + "d", c -> pid.apply(c).d, (c, v) -> pid.apply(c).d = v);
            number(m, p + "ff", c -> pid.apply(c).ff, (c, v) -> pid.apply(c).ff = v);
        }
        number(m, "pid.angleStrength", c -> c.pid.angleStrength, (c, v) -> c.pid.angleStrength = v);
        number(m, "pid.angleLimitDeg", c -> c.pid.angleLimitDeg, (c, v) -> c.pid.angleLimitDeg = v);
        number(m, "pid.horizonStrength", c -> c.pid.horizonStrength, (c, v) -> c.pid.horizonStrength = v);
        enumEntry(m, "pid.itermRelax", ItermRelaxMode.values(), c -> c.pid.itermRelax, (c, v) -> c.pid.itermRelax = v);
        number(m, "pid.antiGravityGain", c -> c.pid.antiGravityGain, (c, v) -> c.pid.antiGravityGain = v);
        enumEntry(m, "pid.tpaMode", TpaMode.values(), c -> c.pid.tpaMode, (c, v) -> c.pid.tpaMode = v);
        number(m, "pid.tpaRatePercent", c -> c.pid.tpaRatePercent, (c, v) -> c.pid.tpaRatePercent = v);
        number(m, "pid.tpaBreakpoint", c -> c.pid.tpaBreakpoint, (c, v) -> c.pid.tpaBreakpoint = v);
        m.put("pid.airmode", new Entry(c -> c.pid.airmode ? 1.0 : 0.0, (c, v) -> c.pid.airmode = v >= 0.5));
        number(m, "pid.motorIdlePercent", c -> c.pid.motorIdlePercent, (c, v) -> c.pid.motorIdlePercent = v);
        number(m, "pid.throttleMid", c -> c.pid.throttleMid, (c, v) -> c.pid.throttleMid = v);
        number(m, "pid.throttleExpo", c -> c.pid.throttleExpo, (c, v) -> c.pid.throttleExpo = v);
        enumEntry(m, "pid.throttleLimitType", ThrottleLimitType.values(), c -> c.pid.throttleLimitType, (c, v) -> c.pid.throttleLimitType = v);
        number(m, "pid.throttleLimitPercent", c -> c.pid.throttleLimitPercent, (c, v) -> c.pid.throttleLimitPercent = v);
        number(m, "pid.thrustLinearPercent", c -> c.pid.thrustLinearPercent, (c, v) -> c.pid.thrustLinearPercent = v);
        number(m, "pid.vbatSagCompensationPercent", c -> c.pid.vbatSagCompensationPercent, (c, v) -> c.pid.vbatSagCompensationPercent = v);
        number(m, ARM_MAX_ANGLE, c -> c.armMaxTiltDeg, (c, v) -> c.armMaxTiltDeg = v);
        number(m, "hold.altP", c -> c.hold.altP, (c, v) -> c.hold.altP = v);
        number(m, "hold.altI", c -> c.hold.altI, (c, v) -> c.hold.altI = v);
        number(m, "hold.altD", c -> c.hold.altD, (c, v) -> c.hold.altD = v);
        number(m, "hold.climbRateCms", c -> c.hold.climbRateCms, (c, v) -> c.hold.climbRateCms = v);
        number(m, "hold.throttleDeadbandPercent", c -> c.hold.throttleDeadbandPercent, (c, v) -> c.hold.throttleDeadbandPercent = v);
        m.put("hold.midThrottle", new Entry(c -> c.hold.midThrottle ? 1.0 : 0.0, (c, v) -> c.hold.midThrottle = v >= 0.5));
        number(m, "hold.posP", c -> c.hold.posP, (c, v) -> c.hold.posP = v);
        number(m, "hold.posI", c -> c.hold.posI, (c, v) -> c.hold.posI = v);
        number(m, "hold.posD", c -> c.hold.posD, (c, v) -> c.hold.posD = v);
        number(m, "hold.posMaxAngleDeg", c -> c.hold.posMaxAngleDeg, (c, v) -> c.hold.posMaxAngleDeg = v);
        number(m, "hold.stickDeadbandPercent", c -> c.hold.stickDeadbandPercent, (c, v) -> c.hold.stickDeadbandPercent = v);
        number(m, "hold.minSatellites", c -> c.hold.minSatellites, (c, v) -> c.hold.minSatellites = v);
        enumEntry(m, "filters.pidLoop", PidLoopRate.values(), c -> c.filters.pidLoop, (c, v) -> c.filters.pidLoop = v);
        number(m, "filters.gyroLpfHz", c -> c.filters.gyroLpfHz, (c, v) -> c.filters.gyroLpfHz = v);
        number(m, "filters.dtermLpf1Hz", c -> c.filters.dtermLpf1Hz, (c, v) -> c.filters.dtermLpf1Hz = v);
        number(m, "filters.dtermLpf2Hz", c -> c.filters.dtermLpf2Hz, (c, v) -> c.filters.dtermLpf2Hz = v);
        number(m, "filters.rcSmoothingHz", c -> c.filters.rcSmoothingHz, (c, v) -> c.filters.rcSmoothingHz = v);
        return Collections.unmodifiableMap(m);
    }

    private static void number(Map<String, Entry> m, String key, ToDoubleFunction<FlightControllerConfig> getter,
                               ObjDoubleConsumer<FlightControllerConfig> setter) {
        m.put(key, new Entry(getter, setter));
    }

    private static <E extends Enum<E>> void enumEntry(Map<String, Entry> m, String key, E[] values,
                                                      Function<FlightControllerConfig, E> getter, EnumSetter<E> setter) {
        m.put(key, new Entry(c -> {
            E value = getter.apply(c);
            return value == null ? 0.0 : value.ordinal();
        }, (c, v) -> setter.set(c, enumValue(values, v))));
    }

    @FunctionalInterface
    private interface EnumSetter<E> {
        void set(FlightControllerConfig config, E value);
    }

    private record Entry(ToDoubleFunction<FlightControllerConfig> getter, ObjDoubleConsumer<FlightControllerConfig> setter) {
    }
}
