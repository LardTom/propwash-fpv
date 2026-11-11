package de.lardtom.propwash.dev;

import de.lardtom.propwash.server.PersonalRulesServer;
import de.lardtom.propwash.server.rules.PersonalRuleEdit;
import de.lardtom.propwash.server.rules.PersonalRules;
import de.lardtom.propwash.server.rules.PhysicsRules;
import de.lardtom.propwash.server.rules.RuleSpec;
import de.lardtom.propwash.server.rules.RuleTier;
import de.lardtom.propwash.zone.ZoneData;
import de.lardtom.propwash.zone.ZoneRelease;
import de.lardtom.propwash.zone.ZoneServer;
import net.minecraft.server.MinecraftServer;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class PersonalRulesDev {
    private static final String BY = "Selbsttest";

    private PersonalRulesDev() {
    }

    public record Snapshot(ZoneRelease release, List<String> playerRules, Map<UUID, PersonalRules> players) {
        public Snapshot {
            playerRules = List.copyOf(playerRules);
            players = Map.copyOf(players);
        }
    }

    public static PersonalRuleEdit.Result personal(MinecraftServer server, UUID player, Map<RuleSpec, Integer> values) {
        Map<String, Integer> names = new LinkedHashMap<>();
        for (Map.Entry<RuleSpec, Integer> entry : values.entrySet()) {
            PersonalRulesServer.setTier(server, entry.getKey(), RuleTier.PLAYERS, null, BY);
            names.put(entry.getKey().name(), entry.getValue());
        }
        PersonalRuleEdit.Result result = PersonalRulesServer.edit(server, player, PersonalRuleEdit.Op.SET, names);
        PersonalRulesServer.sync(server);
        return result;
    }

    public static void clear(MinecraftServer server) {
        PersonalRulesServer.reset(server);
    }

    public static Snapshot snapshot(MinecraftServer server) {
        PersonalRulesServer data = PersonalRulesServer.get(server);
        return new Snapshot(ZoneData.get(server).release(), data.tiers(ZoneRelease.DEFAULT).players(), data.players());
    }

    public static void restore(MinecraftServer server, Snapshot snapshot) {
        PersonalRulesServer.reset(server);
        if (!snapshot.players().isEmpty()) {
            for (RuleSpec spec : PhysicsRules.SPECS) {
                PersonalRulesServer.setTier(server, spec, RuleTier.PLAYERS, null, BY);
            }
            for (Map.Entry<UUID, PersonalRules> entry : snapshot.players().entrySet()) {
                PersonalRulesServer.edit(server, entry.getKey(), PersonalRuleEdit.Op.SET, entry.getValue().values());
            }
        }
        for (RuleSpec spec : PhysicsRules.SPECS) {
            PersonalRulesServer.setTier(server, spec, snapshot.playerRules().contains(spec.name()) ? RuleTier.PLAYERS : RuleTier.ZONES, null, BY);
        }
        if (ZoneData.get(server).setRelease(snapshot.release())) {
            ZoneServer.sync(server);
        }
        PersonalRulesServer.sync(server);
    }
}
