package de.lardtom.propwash.stats;

import de.lardtom.propwash.stats.advancement.FpvAdvancements;

public final class StatsModule {
    private static boolean initialized;

    private StatsModule() {
    }

    public static void init() {
        if (initialized) {
            return;
        }
        initialized = true;
        PropwashStats.bootstrap();
        PilotStats.init();
        FpvAdvancements.init();
    }
}
