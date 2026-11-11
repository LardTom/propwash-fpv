package de.lardtom.propwash.freecam.client;

import de.lardtom.propwash.config.JsonConfig;
import de.lardtom.propwash.core.FlightTune;
import de.lardtom.propwash.drone.CameraMount;
import de.lardtom.propwash.drone.TuneMapping;
import de.lardtom.propwash.freecam.FreecamPreset;
import de.lardtom.propwash.ui.TuneKeys;
import de.lardtom.propwash.ui.TuneParam;

import java.util.Map;
import java.util.TreeMap;

public final class FreecamConfig {
    public static final String NAME = "freecam";
    public static final float FOV_DEFAULT = 120.0F;
    public static final float FOV_MIN = 60.0F;
    public static final float FOV_MAX = 160.0F;
    public static final JsonConfig<FreecamConfig> CONFIG = JsonConfig.create(NAME, FreecamConfig.class, FreecamConfig::new,
            FreecamConfig::validate);

    public String preset = FreecamPreset.DEFAULT.key();
    public Map<String, Map<String, Float>> tunes = new TreeMap<>();
    public Map<String, Float> uptilt = new TreeMap<>();
    public float fovDeg = FOV_DEFAULT;
    public boolean osd = true;

    public static FreecamConfig get() {
        if (!CONFIG.isLoaded()) {
            CONFIG.load();
        }
        return CONFIG.get();
    }

    public static void save() {
        CONFIG.save();
    }

    public FreecamPreset preset() {
        return FreecamPreset.byKey(this.preset);
    }

    public Map<String, Float> tune(FreecamPreset preset) {
        Map<String, Float> stored = this.tunes.get(preset.key());
        return stored == null ? Map.of() : Map.copyOf(stored);
    }

    public void setTune(FreecamPreset preset, Map<String, Float> values) {
        TreeMap<String, Float> clean = cleanTune(values);
        if (clean.isEmpty()) {
            this.tunes.remove(preset.key());
        } else {
            this.tunes.put(preset.key(), clean);
        }
    }

    public float uptilt(FreecamPreset preset) {
        Float stored = this.uptilt.get(preset.key());
        return stored == null ? CameraMount.defaultUptilt(preset.build().frame())
                : CameraMount.uptilt(preset.build(), Map.of(TuneMapping.CAMERA_UPTILT, stored));
    }

    public void setUptilt(FreecamPreset preset, float degrees) {
        if (Float.isFinite(degrees)) {
            this.uptilt.put(preset.key(), clampUptilt(degrees));
        }
    }

    public float fov() {
        return clampFov(this.fovDeg);
    }

    public static float clampFov(float degrees) {
        return Float.isFinite(degrees) ? Math.clamp(degrees, FOV_MIN, FOV_MAX) : FOV_DEFAULT;
    }

    static float clampUptilt(float degrees) {
        return Math.clamp(degrees, TuneMapping.CAMERA_UPTILT_MIN, TuneMapping.CAMERA_UPTILT_MAX);
    }

    static TreeMap<String, Float> cleanTune(Map<String, Float> values) {
        TreeMap<String, Float> clean = new TreeMap<>();
        if (values == null) {
            return clean;
        }
        for (Map.Entry<String, Float> e : values.entrySet()) {
            String key = e.getKey();
            Float value = e.getValue();
            if (key == null || value == null || !Float.isFinite(value) || TuneMapping.CAMERA_UPTILT.equals(key)) {
                continue;
            }
            TuneParam param = TuneKeys.param(key);
            if (param != null && clean.size() < FlightTune.MAX_ENTRIES) {
                clean.put(key, (float) param.sanitize(value));
            }
        }
        return clean;
    }

    static void validate(FreecamConfig c) {
        c.preset = FreecamPreset.byKey(c.preset).key();
        TreeMap<String, Map<String, Float>> tunes = new TreeMap<>();
        if (c.tunes != null) {
            for (FreecamPreset preset : FreecamPreset.values()) {
                Map<String, Float> stored = c.tunes.get(preset.key());
                TreeMap<String, Float> clean = cleanTune(stored);
                if (!clean.isEmpty()) {
                    tunes.put(preset.key(), clean);
                }
            }
        }
        c.tunes = tunes;
        TreeMap<String, Float> uptilt = new TreeMap<>();
        if (c.uptilt != null) {
            for (FreecamPreset preset : FreecamPreset.values()) {
                Float value = c.uptilt.get(preset.key());
                if (value != null && Float.isFinite(value)) {
                    uptilt.put(preset.key(), clampUptilt(value));
                }
            }
        }
        c.uptilt = uptilt;
        c.fovDeg = clampFov(c.fovDeg);
    }
}
