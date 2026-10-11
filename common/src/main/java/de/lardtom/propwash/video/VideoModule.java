package de.lardtom.propwash.video;

public final class VideoModule {
    private VideoModule() {
    }

    public static void init() {
        SpectateServer.init();
    }
}
