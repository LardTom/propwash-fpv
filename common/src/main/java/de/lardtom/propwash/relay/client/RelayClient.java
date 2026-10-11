package de.lardtom.propwash.relay.client;

import de.lardtom.propwash.core.client.ClientBus;
import de.lardtom.propwash.relay.RelayRules;
import de.lardtom.propwash.server.client.ClientWorldRules;
import de.lardtom.propwash.server.rules.WorldRuleSpecs;

public final class RelayClient {
    private RelayClient() {
    }

    public static void init() {
        ClientBus.DISCONNECT.register(minecraft -> RelayControl.INSTANCE.clear());
    }

    public static int relayLimit() {
        return RelayRules.clampRelays(ClientWorldRules.INSTANCE.get(WorldRuleSpecs.SIGNAL_REPEATERS));
    }
}
