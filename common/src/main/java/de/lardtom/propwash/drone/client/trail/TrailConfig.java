package de.lardtom.propwash.drone.client.trail;

import de.lardtom.propwash.config.JsonConfig;

public final class TrailConfig {
    public static final JsonConfig<TrailConfig> CONFIG = JsonConfig.create("trails", TrailConfig.class, TrailConfig::new);

    public boolean enabled = true;

    public static TrailConfig get() {
        return CONFIG.get();
    }

    public TrailConfig copy() {
        TrailConfig copy = new TrailConfig();
        copy.enabled = this.enabled;
        return copy;
    }
}
