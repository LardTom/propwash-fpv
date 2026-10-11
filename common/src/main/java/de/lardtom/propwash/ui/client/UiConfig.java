package de.lardtom.propwash.ui.client;

import de.lardtom.propwash.config.JsonConfig;
import de.lardtom.propwash.core.FlightTune;
import de.lardtom.propwash.ui.SpeedUnit;
import de.lardtom.propwash.ui.TuneKeys;
import de.lardtom.propwash.ui.TuneParam;

import java.util.Map;
import java.util.TreeMap;

public final class UiConfig {
    public static final JsonConfig<UiConfig> CONFIG = JsonConfig.create("ui", UiConfig.class, UiConfig::new, UiConfig::validate);

    public SpeedUnit speedUnit = SpeedUnit.KMH;
    public boolean applyTemplateToNewDrones = true;
    public String lastTab = "overview";
    public Map<String, Float> defaultTune = new TreeMap<>();

    public static UiConfig get() {
        return CONFIG.get();
    }

    public static SpeedUnit speedUnit() {
        return get().speedUnit;
    }

    private static void validate(UiConfig c) {
        if (c.speedUnit == null) {
            c.speedUnit = SpeedUnit.KMH;
        }
        if (c.lastTab == null) {
            c.lastTab = "overview";
        }
        TreeMap<String, Float> clean = new TreeMap<>();
        if (c.defaultTune != null) {
            for (Map.Entry<String, Float> e : c.defaultTune.entrySet()) {
                TuneParam p = e.getKey() == null ? null : TuneKeys.param(e.getKey());
                Float v = e.getValue();
                if (p != null && v != null && Float.isFinite(v) && clean.size() < FlightTune.MAX_ENTRIES) {
                    clean.put(e.getKey(), (float) p.sanitize(v));
                }
            }
        }
        c.defaultTune = TuneModel.templateSafe(clean);
    }
}
