package de.lardtom.propwash.core;

import de.lardtom.propwash.Constants;
import de.lardtom.propwash.platform.Services;

public final class CoreModule {
    private CoreModule() {
    }

    public static void init() {
        CoreConfig.CONFIG.load();
        CoreComponents.bootstrap();
        DevExtensions.common();
        if (CoreConfig.get().debugLogging) {
            Constants.LOG.info("Propwash Core auf {} ({}), Selbsttest: {}", Services.PLATFORM.getPlatformName(),
                    Services.PLATFORM.getEnvironmentName(), DevExtensions.requested());
        }
    }
}
