package de.lardtom.propwash.ui;

import de.lardtom.propwash.Constants;
import de.lardtom.propwash.build.DroneBuild;
import de.lardtom.propwash.drone.TuneMapping;
import de.lardtom.propwash.sim.AirframeSpec;
import de.lardtom.propwash.sim.FlightControllerConfig;

import java.util.Map;
import java.util.TreeMap;

public final class TuneDefaults {
    private static final String DERIVATION_INPUT_PREFIX = "filters.";

    private TuneDefaults() {
    }

    public static Map<String, Float> compute(DroneBuild build, Map<String, Float> tune) {
        try {
            AirframeSpec params = AirframeSpec.of(build);
            Map<String, Float> inputs = new TreeMap<>();
            for (Map.Entry<String, Float> e : tune.entrySet()) {
                if (affectsDefaults(e.getKey()) && e.getValue() != null && Float.isFinite(e.getValue())) {
                    inputs.put(e.getKey(), e.getValue());
                }
            }
            TreeMap<String, Float> out = new TreeMap<>(TuneMapping.defaults(params));
            Map<String, Float> derived = TuneMapping.effective(inputs, params);
            for (String key : TuneMapping.keys()) {
                if (!affectsDefaults(key)) {
                    out.put(key, derived.get(key));
                }
            }
            return out;
        } catch (RuntimeException e) {
            Constants.LOG.warn("Standardwerte für den Aufbau konnten nicht abgeleitet werden: {}", e.toString());
            return new TreeMap<>(TuneMapping.fromConfig(new FlightControllerConfig()));
        }
    }

    public static FlightControllerConfig config(DroneBuild build, Map<String, Float> tune) {
        try {
            return TuneMapping.toConfig(tune, AirframeSpec.of(build));
        } catch (RuntimeException e) {
            Constants.LOG.warn("Tuning konnte nicht in eine Reglerkonfiguration übersetzt werden: {}", e.toString());
            return new FlightControllerConfig();
        }
    }

    public static Map<String, Float> merge(Map<String, Float> local, Map<String, Float> provided) {
        TreeMap<String, Float> out = new TreeMap<>(local);
        for (Map.Entry<String, Float> e : provided.entrySet()) {
            if (e.getValue() == null || !Float.isFinite(e.getValue())) {
                continue;
            }
            if (TuneKeys.isDerivedKey(e.getKey()) && local.containsKey(e.getKey())) {
                continue;
            }
            out.put(e.getKey(), e.getValue());
        }
        return out;
    }

    public static boolean affectsDefaults(String key) {
        return key.startsWith(DERIVATION_INPUT_PREFIX);
    }
}
