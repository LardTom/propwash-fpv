package de.lardtom.propwash.platform.client;

import de.lardtom.propwash.platform.Services;

public final class ClientServices {
    public static final IClientPlatformHelper PLATFORM = Services.load(IClientPlatformHelper.class);

    private ClientServices() {
    }
}
