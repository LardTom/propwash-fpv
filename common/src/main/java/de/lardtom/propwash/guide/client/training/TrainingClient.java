package de.lardtom.propwash.guide.client.training;

import de.lardtom.propwash.Constants;
import de.lardtom.propwash.core.client.ClientBus;
import de.lardtom.propwash.registry.client.ClientRegistrar;

public final class TrainingClient {
    private static boolean initialized;

    private TrainingClient() {
    }

    public static synchronized void init() {
        if (initialized) {
            return;
        }
        initialized = true;
        ClientBus.FRAME_START.register((minecraft, deltaTracker) -> TrainingSession.INSTANCE.onFrame(minecraft));
        ClientBus.DISCONNECT.register(TrainingSession.INSTANCE::onDisconnect);
        ClientBus.SUBMIT_WORLD_GEOMETRY.register(TrainingRenderer::submit);
        ClientBus.REGISTER_HUD_LAYERS.register(() -> ClientRegistrar.hudLayer(Constants.id("flight_training"), TrainingHud::extract));
    }
}
