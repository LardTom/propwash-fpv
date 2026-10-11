package de.lardtom.propwash.trick.client;

import de.lardtom.propwash.config.JsonConfig;
import de.lardtom.propwash.core.client.ClientBus;
import de.lardtom.propwash.trick.TrickConfig;
import de.lardtom.propwash.ui.client.config.ConfigTabs;

public final class TrickClient {
    public static final JsonConfig<TrickConfig> CONFIG = JsonConfig.create("tricks", TrickConfig.class, TrickConfig::new, TrickConfig::sanitize);

    private static boolean initialized;

    private TrickClient() {
    }

    public static synchronized void init() {
        if (initialized) {
            return;
        }
        initialized = true;
        CONFIG.load();
        ClientBus.FRAME_START.register(TrickTracker.INSTANCE::onFrame);
        ClientBus.DISCONNECT.register(TrickTracker.INSTANCE::onDisconnect);
        ConfigTabs.register(TrickConfigTab::new);
    }
}
