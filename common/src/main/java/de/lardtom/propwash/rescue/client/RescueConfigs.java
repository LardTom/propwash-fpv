package de.lardtom.propwash.rescue.client;

import de.lardtom.propwash.config.JsonConfig;
import de.lardtom.propwash.rescue.RescueSettings;

public final class RescueConfigs {
    public static final JsonConfig<RescueSettings> CONFIG = JsonConfig.create("rescue", RescueSettings.class, RescueSettings::new,
            RescueSettings::sanitize);

    private RescueConfigs() {
    }

    public static RescueSettings get() {
        return CONFIG.get();
    }
}
