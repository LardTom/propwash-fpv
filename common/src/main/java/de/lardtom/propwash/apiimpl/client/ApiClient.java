package de.lardtom.propwash.apiimpl.client;

import de.lardtom.propwash.api.build.ImportStatus;
import de.lardtom.propwash.apiimpl.ImportPayloads;
import de.lardtom.propwash.core.client.ClientBus;
import de.lardtom.propwash.network.client.ClientNet;

public final class ApiClient {
    private static boolean initialized;

    private ApiClient() {
    }

    public static synchronized void init() {
        if (initialized) {
            return;
        }
        initialized = true;
        ClientNet.handle(ImportPayloads.ImportResultS2C.TYPE, (payload, context) -> ClientApiImpl.onResult(payload));
        ClientBus.TICK_END.register(minecraft -> ClientApiImpl.PENDING.tick());
        ClientBus.DISCONNECT.register(minecraft -> ClientApiImpl.PENDING.failAll(ImportStatus.NO_TARGET));
    }
}
