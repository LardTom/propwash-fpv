package de.lardtom.propwash.rescue.client;

import de.lardtom.propwash.core.client.ClientBus;
import de.lardtom.propwash.ui.client.config.ConfigTabs;

public final class RescueClient {
    private RescueClient() {
    }

    public static void init() {
        RescueConfigs.CONFIG.load();
        RescueKeys.bootstrap();
        ClientBus.TICK_END.register(RescueController.INSTANCE::onClientTick);
        ClientBus.FRAME_START.register((minecraft, deltaTracker) -> RescueController.INSTANCE.onFrame(minecraft));
        ClientBus.DISCONNECT.register(minecraft -> RescueController.INSTANCE.onDisconnect());
        ClientBus.SUBMIT_WORLD_GEOMETRY.register(HomeMarker::submit);
        ConfigTabs.register(RescueConfigTab::new);
    }
}
