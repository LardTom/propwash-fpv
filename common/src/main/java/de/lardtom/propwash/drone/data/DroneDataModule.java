package de.lardtom.propwash.drone.data;

public final class DroneDataModule {
    private static boolean initialized;

    private DroneDataModule() {
    }

    public static synchronized void init() {
        if (initialized) {
            return;
        }
        initialized = true;
        DroneDataComponents.bootstrap();
    }
}
