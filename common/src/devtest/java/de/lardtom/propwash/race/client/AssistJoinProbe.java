package de.lardtom.propwash.race.client;

import de.lardtom.propwash.dev.AssistJoinCheck;
import de.lardtom.propwash.race.AssistReasons;
import de.lardtom.propwash.race.AssistServerChecks;
import de.lardtom.propwash.server.client.ClientWorldRules;
import de.lardtom.propwash.server.rules.WorldRuleSpecs;
import net.minecraft.client.Minecraft;
import org.jspecify.annotations.Nullable;

import java.util.Map;

public final class AssistJoinProbe {
    private static final String START = "assistStart";
    private static final String KNOWN = "assistKnown";
    private static final String RULES = "assistRules";
    private static final int TIMEOUT_TICKS = 20 * 60;

    private AssistJoinProbe() {
    }

    public static @Nullable String observe(Map<String, Object> state, int tick, Minecraft minecraft) {
        if (!state.containsKey(START)) {
            state.put(START, tick);
            state.put(KNOWN, ClientWorldRules.INSTANCE.known());
        }
        int waited = tick - (int) state.get(START);
        ClientWorldRules rules = ClientWorldRules.INSTANCE;
        boolean changed = rules.get(WorldRuleSpecs.GRAVITY_SCALE) == AssistJoinCheck.GRAVITY
                && rules.get(WorldRuleSpecs.CRASH_TOLERANCE) == AssistJoinCheck.CRASH_TOLERANCE;
        boolean reset = rules.get(WorldRuleSpecs.GRAVITY_SCALE) == WorldRuleSpecs.GRAVITY_SCALE.defaultValue()
                && rules.get(WorldRuleSpecs.CRASH_TOLERANCE) == WorldRuleSpecs.CRASH_TOLERANCE.defaultValue();
        String head = "assist_rules_known_at_join=" + state.get(KNOWN) + "\n";
        if (!state.containsKey(RULES)) {
            if (changed) {
                state.put(RULES, AssistServerChecks.text(AssistPreflight.check(minecraft).reasons()));
                return null;
            }
            return waited > TIMEOUT_TICKS ? head + "assist_rules=timeout\n" : null;
        }
        String during = "assist_rules=" + state.get(RULES) + "\n";
        if (reset) {
            AssistReasons after = AssistPreflight.check(minecraft).reasons();
            return head + during + "assist_after_reset=" + (after.none() ? "clean" : AssistServerChecks.text(after)) + "\n";
        }
        return waited > TIMEOUT_TICKS ? head + during + "assist_after_reset=timeout\n" : null;
    }
}
