package de.lardtom.propwash.interact.client;

import de.lardtom.propwash.interact.InteractRules;
import de.lardtom.propwash.server.client.ClientWorldRules;

public final class ClientInteractRules {
    private ClientInteractRules() {
    }

    public static boolean dronesHitEntities() {
        return ClientWorldRules.INSTANCE.known() && ClientWorldRules.INSTANCE.on(InteractRules.DRONE_HITS_ENTITIES);
    }

    public static boolean dronesBreakBlocks() {
        return ClientWorldRules.INSTANCE.known() && ClientWorldRules.INSTANCE.on(InteractRules.DRONES_BREAK_BLOCKS);
    }

    public static double breakImpact() {
        return ClientWorldRules.INSTANCE.known() ? InteractRules.breakImpact(ClientWorldRules.INSTANCE.get(InteractRules.BREAK_IMPACT_SCALE)) : 1.0;
    }
}
