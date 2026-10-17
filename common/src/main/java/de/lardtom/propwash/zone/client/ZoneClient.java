package de.lardtom.propwash.zone.client;

import de.lardtom.propwash.Constants;
import de.lardtom.propwash.core.client.ClientBus;
import de.lardtom.propwash.network.client.ClientNet;
import de.lardtom.propwash.registry.client.ClientRegistrar;
import de.lardtom.propwash.zone.ZoneNames;
import de.lardtom.propwash.zone.ZonesS2C;

public final class ZoneClient {
    private static boolean initialized;

    private ZoneClient() {
    }

    public static void init() {
        if (initialized) {
            return;
        }
        initialized = true;
        ClientNet.handle(ZonesS2C.TYPE, (payload, ctx) -> ClientZones.INSTANCE.accept(payload));
        ZoneNames.client(ClientZones.INSTANCE::name);
        ClientBus.DISCONNECT.register(minecraft -> {
            ClientZones.INSTANCE.clear();
            ZoneHud.clear();
            ZoneEditor.stop();
        });
        ClientBus.TICK_END.register(ZoneHud::tick);
        ClientBus.TICK_END.register(ZoneEditor::tick);
        ClientBus.SUBMIT_WORLD_GEOMETRY.register(ZoneRenderer::submit);
        ClientBus.REGISTER_HUD_LAYERS.register(() -> {
            ClientRegistrar.hudLayer(Constants.id("zone_notice"), ZoneHud::extract);
            ClientRegistrar.hudLayer(Constants.id("zone_edit"), ZoneEditor::extractHud);
        });
    }
}
