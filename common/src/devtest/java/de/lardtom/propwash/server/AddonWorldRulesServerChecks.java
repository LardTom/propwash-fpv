package de.lardtom.propwash.server;

import de.lardtom.propwash.api.worldrule.WorldRule;
import de.lardtom.propwash.dev.example.ExampleRules;
import de.lardtom.propwash.server.rules.RulePreset;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.gamerules.GameRule;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Optional;

public final class AddonWorldRulesServerChecks {
    private static String summary = "";

    private AddonWorldRulesServerChecks() {
    }

    public static @Nullable String check(MinecraftServer server) {
        if (!ExampleRules.registered()) {
            summary = "Beispiel-Add-on aus, nichts zu prüfen";
            return null;
        }
        ServerLevel level = server.overworld();
        List<WorldRule<?>> rules = List.of(ExampleRules.cooldown(), ExampleRules.refill());
        try {
            if (!AddonWorldRules.pending().isEmpty()) {
                return "nicht ins Register übernommen: " + AddonWorldRules.pending();
            }
            for (WorldRule<?> rule : rules) {
                Optional<GameRule<?>> game = AddonWorldRules.gameRule(rule.id());
                if (game.isEmpty() || !rule.id().equals(BuiltInRegistries.GAME_RULE.getKey(game.get())) || !WorldRules.isPropwash(game.get())) {
                    return rule.id() + " fehlt als Gamerule in Propwashs Kategorie";
                }
                if (!WorldRules.propwashRules().contains(game.get())) {
                    return rule.id() + " fehlt im Abgleich der Welt-Regeln";
                }
                reset(server, rule);
            }
            WorldRules.applyPreset(server, RulePreset.TRAINING);
            if (ExampleRules.cooldown().get(level) != ExampleRules.COOLDOWN_TRAINING || ExampleRules.refill().get(level)) {
                return "Training setzt " + ExampleRules.cooldown().get(level) + " und " + ExampleRules.refill().get(level);
            }
            if (WorldRules.matchingPreset(server).orElse(null) != RulePreset.TRAINING) {
                return "nach Training aktiv: " + WorldRules.matchingPreset(server);
            }
            if (WorldRules.entries(server).stream().noneMatch(entry -> entry.id().equals(ExampleRules.LAUNCHER_COOLDOWN)
                    && entry.value().equals(Integer.toString(ExampleRules.COOLDOWN_TRAINING)))) {
                return "Abgleich an die Clients ohne den Wert der Beispiel-Regel";
            }
            WorldRules.applyPreset(server, RulePreset.ARCADE);
            WorldRules.applyPreset(server, RulePreset.REALISTIC);
            if (ExampleRules.cooldown().get(level) != ExampleRules.COOLDOWN_ARCADE || !ExampleRules.refill().get(level)
                    || WorldRules.matchingPreset(server).orElse(null) != RulePreset.REALISTIC) {
                return "Realistisch ohne Eintrag ändert die Beispiel-Regeln: " + ExampleRules.cooldown().get(level) + ", "
                        + ExampleRules.refill().get(level) + ", aktiv " + WorldRules.matchingPreset(server);
            }
            summary = "Beispiel-Regeln als Gamerules, Training setzt " + ExampleRules.COOLDOWN_TRAINING + ", Realistisch ohne Eintrag lässt "
                    + ExampleRules.COOLDOWN_ARCADE + " stehen, Abgleich enthält die Werte";
            return null;
        } finally {
            for (WorldRule<?> rule : rules) {
                reset(server, rule);
            }
        }
    }

    private static void reset(MinecraftServer server, WorldRule<?> rule) {
        AddonWorldRules.gameRule(rule.id()).ifPresent(game -> WorldRules.setSerialized(server, game,
                AddonWorldRules.serialize(rule, rule.defaultValue())));
    }

    public static String summary() {
        return summary;
    }
}
