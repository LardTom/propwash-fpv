package de.lardtom.propwash.audio;

public final class AudioModule {
    private static boolean initialized;

    private AudioModule() {
    }

    public static synchronized void init() {
        if (initialized) {
            return;
        }
        initialized = true;
        AudioSounds.bootstrap();
    }
}
