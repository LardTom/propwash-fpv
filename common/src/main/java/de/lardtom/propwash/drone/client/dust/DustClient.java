package de.lardtom.propwash.drone.client.dust;

import de.lardtom.propwash.core.client.ClientBus;
import de.lardtom.propwash.drone.QuadcopterEntity;

public final class DustClient {
    private static boolean initialized;

    private DustClient() {
    }

    public static synchronized void init() {
        if (initialized) {
            return;
        }
        initialized = true;
        DustConfig.CONFIG.load();
        QuadcopterEntity.CLIENT_TICK.register(DustEffects::onDroneTick);
        ClientBus.TICK_END.register(DustEffects::onClientTick);
        ClientBus.DISCONNECT.register(minecraft -> DustEffects.reset());
    }
}
