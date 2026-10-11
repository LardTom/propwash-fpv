package de.lardtom.propwash.blackbox.client;

import de.lardtom.propwash.blackbox.BlackboxConfig;
import de.lardtom.propwash.config.JsonConfig;
import de.lardtom.propwash.core.client.ClientBus;
import de.lardtom.propwash.ui.client.config.ConfigTabs;

public final class BlackboxClient {
    public static final JsonConfig<BlackboxConfig> CONFIG =
            JsonConfig.create("blackbox", BlackboxConfig.class, BlackboxConfig::new, BlackboxConfig::sanitize);

    private static boolean initialized;

    private BlackboxClient() {
    }

    public static synchronized void init() {
        if (initialized) {
            return;
        }
        initialized = true;
        CONFIG.load();
        BlackboxKeys.bootstrap();
        ClientBus.FRAME_START.register(BlackboxSession.INSTANCE::onFrame);
        ClientBus.DISCONNECT.register(BlackboxSession.INSTANCE::onDisconnect);
        ConfigTabs.register(BlackboxTab::new);
    }
}
