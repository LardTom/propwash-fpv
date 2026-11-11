package de.lardtom.propwash.relay.client;

import de.lardtom.propwash.core.client.ClientBus;
import de.lardtom.propwash.relay.RelayModule;
import de.lardtom.propwash.relay.RelayRules;
import de.lardtom.propwash.registry.client.ClientRegistrar;
import de.lardtom.propwash.server.client.ClientWorldRules;
import de.lardtom.propwash.server.rules.WorldRuleSpecs;

public final class RelayClient {
    private RelayClient() {
    }

    public static void init() {
        ClientBus.DISCONNECT.register(minecraft -> RelayControl.INSTANCE.clear());
        RepeaterSignals.init();
        ClientRegistrar.blockEntityRenderer(RelayModule.SIGNAL_REPEATER_BE, SignalRepeaterRenderer::new);
    }

    public static int relayLimit() {
        return RelayRules.clampRelays(ClientWorldRules.INSTANCE.get(WorldRuleSpecs.SIGNAL_REPEATERS));
    }
}
