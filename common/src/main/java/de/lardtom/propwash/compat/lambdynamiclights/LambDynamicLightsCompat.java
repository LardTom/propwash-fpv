package de.lardtom.propwash.compat.lambdynamiclights;

import de.lardtom.propwash.platform.Services;

public final class LambDynamicLightsCompat {
    public static final String[] MOD_IDS = {"lambdynlights", "lambdynamiclights"};

    private static boolean checked;
    private static boolean present;
    private static volatile boolean initialized;

    private LambDynamicLightsCompat() {
    }

    public static boolean present() {
        if (!checked) {
            checked = true;
            for (String id : MOD_IDS) {
                present |= Services.PLATFORM.isModLoaded(id);
            }
        }
        return present || initialized;
    }

    public static boolean initialized() {
        return initialized;
    }

    static void markInitialized() {
        initialized = true;
    }
}
