package de.lardtom.propwash.guide;

public final class GuideModule {
    private static boolean initialized;

    private GuideModule() {
    }

    public static synchronized void init() {
        if (initialized) {
            return;
        }
        initialized = true;
        GuideScenes.bootstrap();
    }
}
