package de.lardtom.propwash.ui;

import de.lardtom.propwash.sim.Axis;
import de.lardtom.propwash.sim.RatesType;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

public enum RateTemplate {
    STANDARD(RatesType.ACTUAL, actual(70, 670, 0.0), actual(70, 670, 0.0)),
    GENTLE(RatesType.ACTUAL, actual(60, 400, 0.30), actual(60, 330, 0.20)),
    FREESTYLE(RatesType.ACTUAL, actual(70, 720, 0.50), actual(70, 600, 0.40)),
    RACING(RatesType.ACTUAL, actual(150, 700, 0.20), actual(120, 550, 0.10)),
    CINE(RatesType.ACTUAL, actual(40, 300, 0.40), actual(30, 200, 0.30)),
    CLASSIC(RatesType.BETAFLIGHT, classic(1.0, 0.70, 0.0), classic(1.0, 0.70, 0.0));

    private final RatesType type;
    private final Map<String, Double> rollPitchRate;
    private final Map<String, Double> yaw;

    RateTemplate(RatesType type, Map<String, Double> rollPitchRate, Map<String, Double> yaw) {
        this.type = type;
        this.rollPitchRate = rollPitchRate;
        this.yaw = yaw;
    }

    public RatesType type() {
        return this.type;
    }

    public String translationKey() {
        return "propwash.ui.rates.preset." + this.name().toLowerCase(Locale.ROOT);
    }

    public Map<String, Float> tuneValues() {
        Map<String, Float> out = new LinkedHashMap<>();
        out.put(TuneKeys.RATES_TYPE, (float) this.type.ordinal());
        for (Axis axis : Axis.values()) {
            Map<String, Double> source = axis == Axis.YAW ? this.yaw : this.rollPitchRate;
            for (Map.Entry<String, Double> e : source.entrySet()) {
                out.put(TuneKeys.rateKey(axis, e.getKey()), e.getValue().floatValue());
            }
        }
        return out;
    }

    public Map<String, Float> apply(Map<String, Float> tune, Map<String, Float> defaults) {
        Map<String, Float> cleared = TuneEdits.resetWhere(tune, key -> key.startsWith("rates.") && !key.equals(TuneKeys.RC_DEADBAND)
                && !key.equals(TuneKeys.YAW_DEADBAND));
        Map<String, Float> out = cleared;
        for (Map.Entry<String, Float> e : this.tuneValues().entrySet()) {
            if (e.getKey().endsWith(".maxRateDps")) {
                continue;
            }
            out = TuneEdits.set(out, defaults, e.getKey(), e.getValue());
        }
        for (Map.Entry<String, Float> e : this.tuneValues().entrySet()) {
            if (e.getKey().endsWith(".maxRateDps")) {
                out = TuneEdits.set(out, defaults, e.getKey(), e.getValue());
            }
        }
        return out;
    }

    public boolean matches(Map<String, Float> tune, Map<String, Float> defaults) {
        for (Map.Entry<String, Float> e : this.tuneValues().entrySet()) {
            TuneParam p = TuneKeys.param(e.getKey());
            double v = TuneEdits.value(tune, defaults, e.getKey());
            if (p == null || !p.sameValue(v, e.getValue())) {
                return false;
            }
        }
        return true;
    }

    private static Map<String, Double> actual(double center, double max, double expo) {
        Map<String, Double> m = new LinkedHashMap<>();
        m.put("centerRateDps", center);
        m.put("maxRateDps", max);
        m.put("actualExpo", expo);
        return m;
    }

    private static Map<String, Double> classic(double rcRate, double superRate, double expo) {
        Map<String, Double> m = new LinkedHashMap<>();
        m.put("rcRate", rcRate);
        m.put("superRate", superRate);
        m.put("rcExpo", expo);
        return m;
    }
}
