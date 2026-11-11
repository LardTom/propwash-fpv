package de.lardtom.propwash.server;

import de.lardtom.propwash.api.worldrule.WorldRule;
import net.minecraft.server.MinecraftServer;

public final class CovComboRuleAccess {
    private CovComboRuleAccess() {
    }

    public static boolean set(MinecraftServer server, WorldRule<?> rule, Object value) {
        boolean done = AddonWorldRules.gameRule(rule.id()).map(game -> WorldRules.setSerialized(server, game, AddonWorldRules.serialize(rule, value)))
                .orElse(false);
        WorldRules.sync(server);
        return done;
    }
}
