package de.lardtom.propwash.barrier.client;

import de.lardtom.propwash.core.client.ClientBus;

public final class BarrierClient {
    private static boolean initialized;

    private BarrierClient() {
    }

    public static void init() {
        if (initialized) {
            return;
        }
        initialized = true;
        ClientBus.TICK_END.register(BarrierMarkers::tick);
    }
}
