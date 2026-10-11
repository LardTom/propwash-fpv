package de.lardtom.propwash.blackbox;

import de.lardtom.propwash.platform.Services;

public final class BlackboxModule {
    private BlackboxModule() {
    }

    public static void init() {
        BlackboxSetup.producerVersion(Services.PLATFORM.getModVersion());
    }
}
