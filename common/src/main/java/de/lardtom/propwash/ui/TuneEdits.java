package de.lardtom.propwash.ui;

import de.lardtom.propwash.drone.TuneMapping;
import de.lardtom.propwash.sim.Axis;
import de.lardtom.propwash.sim.FlightControllerConfig;
import de.lardtom.propwash.sim.PidSource;

import java.util.Map;
import java.util.TreeMap;
import java.util.function.Predicate;

public final class TuneEdits {
    public static final float PID_DERIVED = PidSource.DERIVED.ordinal();
    public static final float PID_MANUAL = PidSource.CUSTOM.ordinal();

    private static final Map<String, Float> FALLBACK = TuneMapping.fromConfig(new FlightControllerConfig());

    private TuneEdits() {
    }

    public static double value(Map<String, Float> tune, Map<String, Float> defaults, String key) {
        Float v = tune.get(key);
        if (v != null) {
            return v;
        }
        Float d = defaults.get(key);
        if (d != null) {
            return d;
        }
        Float f = FALLBACK.get(key);
        return f != null ? f : 0.0;
    }

    public static double defaultValue(Map<String, Float> defaults, String key) {
        Float d = defaults.get(key);
        if (d != null) {
            return d;
        }
        Float f = FALLBACK.get(key);
        return f != null ? f : 0.0;
    }

    public static boolean isModified(Map<String, Float> tune, Map<String, Float> defaults, String key) {
        Float v = tune.get(key);
        if (v == null) {
            return false;
        }
        TuneParam p = TuneKeys.param(key);
        double d = defaultValue(defaults, key);
        return p != null ? !p.sameValue(v, d) : Math.abs(v - d) > 1e-6;
    }

    public static boolean manualPid(Map<String, Float> tune, Map<String, Float> defaults) {
        return Math.rint(value(tune, defaults, TuneKeys.PID_SOURCE)) == PID_MANUAL;
    }

    public static Map<String, Float> set(Map<String, Float> tune, Map<String, Float> defaults, String key, double value) {
        if (!Double.isFinite(value)) {
            return tune;
        }
        TuneParam param = TuneKeys.param(key);
        double v = param != null ? param.sanitize(value) : value;
        TreeMap<String, Float> out = new TreeMap<>(tune);
        if (TuneKeys.isPidTermKey(key)) {
            pinPid(out, defaults);
            out.put(key, (float) v);
            return out;
        }
        if (key.equals(TuneKeys.PID_SOURCE)) {
            if (Math.rint(v) == PID_DERIVED) {
                return resetPidToDerived(out);
            }
            pinPid(out, defaults);
            return out;
        }
        putOrRemove(out, defaults, key, v);
        linkRates(out, defaults, key);
        return out;
    }

    public static Map<String, Float> step(Map<String, Float> tune, Map<String, Float> defaults, String key, int steps) {
        TuneParam param = TuneKeys.param(key);
        if (param == null) {
            return tune;
        }
        return set(tune, defaults, key, param.stepBy(value(tune, defaults, key), steps));
    }

    public static Map<String, Float> reset(Map<String, Float> tune, Map<String, Float> defaults, String key) {
        if (TuneKeys.isPidTermKey(key)) {
            if (!manualPid(tune, defaults)) {
                return tune;
            }
            return set(tune, defaults, key, defaultValue(defaults, key));
        }
        if (!tune.containsKey(key)) {
            return tune;
        }
        TreeMap<String, Float> out = new TreeMap<>(tune);
        out.remove(key);
        linkRates(out, defaults, key);
        return out;
    }

    public static Map<String, Float> resetPidToDerived(Map<String, Float> tune) {
        TreeMap<String, Float> out = new TreeMap<>(tune);
        for (String k : TuneKeys.pidTermKeys()) {
            out.remove(k);
        }
        out.remove(TuneKeys.PID_SOURCE);
        return out;
    }

    public static Map<String, Float> resetWhere(Map<String, Float> tune, Predicate<String> keys) {
        TreeMap<String, Float> out = new TreeMap<>();
        for (Map.Entry<String, Float> e : tune.entrySet()) {
            if (!keys.test(e.getKey())) {
                out.put(e.getKey(), e.getValue());
            }
        }
        return out;
    }

    public static boolean hasOwnTuning(Map<String, Float> stored) {
        for (String key : stored.keySet()) {
            if (!TuneMapping.isDroneSetting(key)) {
                return true;
            }
        }
        return false;
    }

    public static Map<String, Float> templateFor(Map<String, Float> template, Map<String, Float> stored) {
        TreeMap<String, Float> out = new TreeMap<>(template);
        for (Map.Entry<String, Float> e : stored.entrySet()) {
            if (e.getValue() != null && TuneMapping.isDroneSetting(e.getKey())) {
                out.put(e.getKey(), e.getValue());
            }
        }
        return out;
    }

    public static Map<String, Float> clean(Map<String, Float> tune, Map<String, Float> defaults) {
        TreeMap<String, Float> out = new TreeMap<>();
        boolean manual = manualPid(tune, defaults);
        for (Map.Entry<String, Float> e : tune.entrySet()) {
            String key = e.getKey();
            Float v = e.getValue();
            if (v == null || !Float.isFinite(v) || key.isEmpty() || key.length() > 64) {
                continue;
            }
            TuneParam p = TuneKeys.param(key);
            if (p == null) {
                out.put(key, v);
                continue;
            }
            if (TuneKeys.isPidTermKey(key) && !manual) {
                continue;
            }
            out.put(key, (float) p.sanitize(v));
        }
        if (!manual) {
            out.remove(TuneKeys.PID_SOURCE);
        } else {
            pinPid(out, defaults);
        }
        return out;
    }

    private static void pinPid(TreeMap<String, Float> out, Map<String, Float> defaults) {
        boolean manual = Math.rint(value(out, defaults, TuneKeys.PID_SOURCE)) == PID_MANUAL;
        for (String k : TuneKeys.pidTermKeys()) {
            if (!manual || !out.containsKey(k)) {
                out.put(k, (float) defaultValue(defaults, k));
            }
        }
        out.put(TuneKeys.PID_SOURCE, PID_MANUAL);
    }

    private static void putOrRemove(TreeMap<String, Float> out, Map<String, Float> defaults, String key, double v) {
        TuneParam p = TuneKeys.param(key);
        double d = defaultValue(defaults, key);
        boolean same = p != null ? p.sameValue(v, d) : Math.abs(v - d) < 1e-6;
        if (same && defaults.containsKey(key)) {
            out.remove(key);
        } else {
            out.put(key, (float) v);
        }
    }

    private static void linkRates(TreeMap<String, Float> out, Map<String, Float> defaults, String key) {
        for (Axis axis : Axis.values()) {
            String center = TuneKeys.rateKey(axis, "centerRateDps");
            String max = TuneKeys.rateKey(axis, "maxRateDps");
            if (!key.equals(center) && !key.equals(max)) {
                continue;
            }
            double c = value(out, defaults, center);
            double m = value(out, defaults, max);
            if (m < c) {
                if (key.equals(center)) {
                    putOrRemove(out, defaults, max, c);
                } else {
                    putOrRemove(out, defaults, center, m);
                }
            }
        }
    }
}
