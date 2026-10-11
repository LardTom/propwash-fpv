package de.lardtom.propwash.core;

import de.lardtom.propwash.config.JsonConfig;

public final class CoreConfig {
    public static final JsonConfig<CoreConfig> CONFIG = JsonConfig.create("core", CoreConfig.class, CoreConfig::new);

    public boolean debugLogging = false;

    public boolean logPayloads = false;

    public static CoreConfig get() {
        return CONFIG.get();
    }
}
