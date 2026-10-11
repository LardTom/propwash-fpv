package de.lardtom.propwash.drone.client;

import de.lardtom.propwash.config.JsonConfig;

public final class DroneClientConfig {
    public static final JsonConfig<DroneClientConfig> CONFIG = JsonConfig.create("drone", DroneClientConfig.class, DroneClientConfig::new,
            DroneClientConfig::validate);

    public int stateSendHz = 20;

    public DroneClientConfig() {
    }

    public DroneClientConfig copy() {
        DroneClientConfig c = new DroneClientConfig();
        c.stateSendHz = this.stateSendHz;
        return c;
    }

    static void validate(DroneClientConfig c) {
        c.stateSendHz = Math.max(5, Math.min(40, c.stateSendHz));
    }
}
