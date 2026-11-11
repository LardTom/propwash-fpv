package de.lardtom.propwash.dev.example;

import de.lardtom.propwash.api.PropwashApi;
import de.lardtom.propwash.api.worldrule.WorldRule;
import de.lardtom.propwash.api.worldrule.WorldRules;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.Nullable;

public final class ExampleRules {
    public static final Identifier LAUNCHER_COOLDOWN = Identifier.fromNamespaceAndPath(ExampleAddon.NAMESPACE, "launcher_cooldown");
    public static final Identifier LAUNCHER_REFILL = Identifier.fromNamespaceAndPath(ExampleAddon.NAMESPACE, "launcher_refill");
    public static final int COOLDOWN_DEFAULT = 10;
    public static final int COOLDOWN_MAX = 200;
    public static final int COOLDOWN_TRAINING = 4;
    public static final int COOLDOWN_ARCADE = 0;

    private static @Nullable WorldRule<Integer> cooldown;
    private static @Nullable WorldRule<Boolean> refill;

    private ExampleRules() {
    }

    public static synchronized void register() {
        if (cooldown != null) {
            return;
        }
        WorldRules rules = PropwashApi.worldRules();
        WorldRule<Integer> ticks = rules.registerInt(LAUNCHER_COOLDOWN, COOLDOWN_DEFAULT, 0, COOLDOWN_MAX);
        WorldRule<Boolean> free = rules.registerBoolean(LAUNCHER_REFILL, false);
        rules.presetValue(ticks, WorldRules.PRESET_TRAINING, COOLDOWN_TRAINING);
        rules.presetValue(ticks, WorldRules.PRESET_ARCADE, COOLDOWN_ARCADE);
        rules.presetValue(free, WorldRules.PRESET_ARCADE, true);
        rules.raceNeutral(ticks);
        rules.raceNeutral(free);
        cooldown = ticks;
        refill = free;
    }

    public static WorldRule<Integer> cooldown() {
        WorldRule<Integer> rule = cooldown;
        if (rule == null) {
            throw new IllegalStateException("Beispiel-Regeln nicht angemeldet");
        }
        return rule;
    }

    public static WorldRule<Boolean> refill() {
        WorldRule<Boolean> rule = refill;
        if (rule == null) {
            throw new IllegalStateException("Beispiel-Regeln nicht angemeldet");
        }
        return rule;
    }

    public static boolean registered() {
        return cooldown != null;
    }
}
