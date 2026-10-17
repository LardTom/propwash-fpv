package de.lardtom.propwash.rescue;

public final class RescueModule {
    private RescueModule() {
    }

    public static void init() {
        AutonomousReturns.init();
        HomePoints.init();
        HomeResets.init();
    }
}
