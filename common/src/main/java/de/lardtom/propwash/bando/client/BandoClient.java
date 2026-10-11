package de.lardtom.propwash.bando.client;

import de.lardtom.propwash.bando.BandoScene;
import de.lardtom.propwash.guide.GuideScenes;

public final class BandoClient {
    private static boolean initialized;

    private BandoClient() {
    }

    public static synchronized void init() {
        if (initialized) {
            return;
        }
        initialized = true;
        GuideScenes.register(BandoScene::create);
    }
}
