package de.lardtom.propwash.server.client;

import de.lardtom.propwash.core.client.ClientBus;
import de.lardtom.propwash.network.client.ClientNet;
import de.lardtom.propwash.server.GenerationEdgeS2C;
import de.lardtom.propwash.server.RangeStatusS2C;
import de.lardtom.propwash.server.ServerRulesS2C;
import de.lardtom.propwash.server.WorldRules;
import de.lardtom.propwash.server.WorldRulesS2C;
import de.lardtom.propwash.ui.client.config.ConfigTabs;

public final class ServerClient {
    private static boolean initialized;

    private ServerClient() {
    }

    public static void init() {
        if (initialized) {
            return;
        }
        initialized = true;
        ClientNet.handle(ServerRulesS2C.TYPE, (payload, ctx) -> ClientServerRules.INSTANCE.accept(payload));
        ClientNet.handle(RangeStatusS2C.TYPE, (payload, ctx) -> ClientServerRules.INSTANCE.accept(payload));
        ClientNet.handle(GenerationEdgeS2C.TYPE, (payload, ctx) -> ClientGenerationEdge.INSTANCE.accept(payload));
        ClientNet.handle(WorldRulesS2C.TYPE, (payload, ctx) -> ClientWorldRules.INSTANCE.accept(payload));
        WorldRules.clientValues(ClientWorldRules.INSTANCE::values);
        ClientBus.DISCONNECT.register(minecraft -> {
            ClientServerRules.INSTANCE.clear();
            ClientWorldRules.INSTANCE.clear();
            ClientGenerationEdge.INSTANCE.clear();
        });
        ConfigTabs.register(ServerRulesTab::new);
        ConfigTabs.register(WorldRulesTab::new);
    }
}
