package de.lardtom.propwash.gpucompat.client;

import de.lardtom.propwash.Constants;
import de.lardtom.propwash.config.JsonConfig;
import de.lardtom.propwash.gpucompat.GpuCompatConfig;
import de.lardtom.propwash.gpucompat.GpuCompatMode;

public final class GpuCompatSettings {
    public static final JsonConfig<GpuCompatConfig> CONFIG =
            JsonConfig.create("gpu-compat", GpuCompatConfig.class, GpuCompatConfig::new, GpuCompatConfig::validate);

    private GpuCompatSettings() {
    }

    public static GpuCompatMode stored() {
        try {
            if (!CONFIG.isLoaded()) {
                CONFIG.load();
            }
            return CONFIG.get().mode;
        } catch (RuntimeException | LinkageError exception) {
            Constants.LOG.warn("Grafik-Kompatibilität: Einstellung nicht lesbar, nehme Automatisch: {}", exception.toString());
            return GpuCompatMode.AUTO;
        }
    }

    public static void store(GpuCompatMode mode) {
        GpuCompatConfig config = CONFIG.get().copy();
        config.mode = mode;
        CONFIG.set(config);
    }
}
