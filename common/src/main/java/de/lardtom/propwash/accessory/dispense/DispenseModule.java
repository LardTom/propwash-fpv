package de.lardtom.propwash.accessory.dispense;

import de.lardtom.propwash.core.event.ServerBus;
import de.lardtom.propwash.drone.LoadedDrones;
import de.lardtom.propwash.drone.QuadcopterEntity;

public final class DispenseModule {
    private static boolean initialized;

    private DispenseModule() {
    }

    public static synchronized void init() {
        if (initialized) {
            return;
        }
        initialized = true;
        QuadcopterEntity.SERVER_TICK.register(OwnShots::tick);
        ServerBus.STARTED.register(server -> OwnShots.clear());
        ServerBus.STOPPED.register(server -> {
            OwnShots.clear();
            LoadedDrones.clear();
        });
        ServerBus.LEVEL_UNLOAD.register(LoadedDrones::forget);
    }
}
