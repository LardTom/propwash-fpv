package de.lardtom.propwash.drone.client.render;

import de.lardtom.propwash.core.client.ClientBus;
import de.lardtom.propwash.drone.DroneModule;
import de.lardtom.propwash.registry.client.ClientRegistrar;
import de.lardtom.propwash.ui.client.config.ConfigTabs;

public final class DroneRenderClient {
    private static boolean initialized;

    private DroneRenderClient() {
    }

    public static synchronized void init() {
        if (initialized) {
            return;
        }
        initialized = true;
        DroneRenderConfig.CONFIG.load();
        ClientRegistrar.entityRenderer(DroneModule.DRONE, QuadcopterRenderer::new);
        ClientRegistrar.specialModelRenderer(PartItemRenderer.TYPE, PartItemRenderer.Unbaked.MAP_CODEC);
        ConfigTabs.register(DroneRenderConfigTab::new);
        ClientBus.TICK_END.register(DroneSizeNotice::onClientTick);
    }
}
