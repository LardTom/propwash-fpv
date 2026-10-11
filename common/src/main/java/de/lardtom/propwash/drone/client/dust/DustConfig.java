package de.lardtom.propwash.drone.client.dust;

import de.lardtom.propwash.config.JsonConfig;
import de.lardtom.propwash.drone.dust.DustModel;

public final class DustConfig {
    public static final JsonConfig<DustConfig> CONFIG = JsonConfig.create("dust", DustConfig.class, DustConfig::new, DustConfig::validate);

    public boolean enabled = true;

    public float intensity = DustModel.DEFAULT_INTENSITY;

    public static DustConfig get() {
        return CONFIG.get();
    }

    public DustConfig copy() {
        DustConfig copy = new DustConfig();
        copy.enabled = this.enabled;
        copy.intensity = this.intensity;
        return copy;
    }

    static void validate(DustConfig config) {
        config.intensity = (float) DustModel.clampIntensity(config.intensity);
    }
}
