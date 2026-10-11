package de.lardtom.propwash.apiimpl;

import de.lardtom.propwash.core.event.ServerBus;
import de.lardtom.propwash.network.PropwashNet;

public final class ApiModule {
    private static boolean initialized;

    private ApiModule() {
    }

    public static synchronized void init() {
        if (initialized) {
            return;
        }
        initialized = true;
        PropwashNet.registerC2S(ImportPayloads.ImportC2S.TYPE, ImportPayloads.ImportC2S.CODEC, ImportNet::onImport);
        PropwashNet.registerS2C(ImportPayloads.ImportResultS2C.TYPE, ImportPayloads.ImportResultS2C.CODEC);
        ServerBus.PLAYER_LEAVE.register(player -> ImportNet.RATE.forget(player.getUUID()));
    }
}
