package de.lardtom.propwash.server;

import de.lardtom.propwash.api.worldrule.WorldRule;
import de.lardtom.propwash.network.ServerPayloadContext;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.util.List;

public final class CovAddonRulesJoinAccess {
    private CovAddonRulesJoinAccess() {
    }

    public static String changeAsOperator(MinecraftServer server, Identifier id, String value) {
        ServerPlayer operator = WorldRulesRights.guest(server);
        server.getPlayerList().op(operator.nameAndId());
        try {
            boolean taken = WorldRules.edit(new WorldRuleEditC2S(List.of(new WorldRulesS2C.Entry(id, value))), new ServerPayloadContext(operator));
            return taken ? "angenommen" : "abgelehnt";
        } finally {
            server.getPlayerList().deop(operator.nameAndId());
        }
    }

    public static void reset(MinecraftServer server, WorldRule<?> rule) {
        AddonWorldRules.gameRule(rule.id()).ifPresent(game -> WorldRules.setSerialized(server, game, AddonWorldRules.serialize(rule, rule.defaultValue())));
        WorldRules.sync(server);
    }
}
