package de.lardtom.propwash.rescue.client;

import de.lardtom.propwash.core.client.ClientBus;
import de.lardtom.propwash.network.client.ClientNet;
import de.lardtom.propwash.rescue.net.HomePointS2C;
import de.lardtom.propwash.ui.client.config.ConfigTabs;

public final class RescueClient {
    private RescueClient() {
    }

    public static void init() {
        RescueConfigs.CONFIG.load();
        RescueKeys.bootstrap();
        ClientBus.TICK_END.register(RescueController.INSTANCE::onClientTick);
        ClientBus.TICK_END.register(HomeResetInput::tick);
        ClientBus.FRAME_START.register((minecraft, deltaTracker) -> RescueController.INSTANCE.onFrame(minecraft));
        ClientBus.DISCONNECT.register(minecraft -> RescueController.INSTANCE.onDisconnect());
        ClientBus.DISCONNECT.register(minecraft -> HomeResetInput.clear());
        ClientNet.handle(HomePointS2C.TYPE, (payload, context) -> RescueController.INSTANCE.onServerHome(context.minecraft(), payload));
        ClientBus.SUBMIT_WORLD_GEOMETRY.register(HomeMarker::submit);
        ConfigTabs.register(RescueConfigTab::new);
    }
}
