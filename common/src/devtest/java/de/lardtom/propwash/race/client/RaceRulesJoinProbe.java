package de.lardtom.propwash.race.client;

import de.lardtom.propwash.dev.JoinTestEnv;
import de.lardtom.propwash.dev.RaceRulesJoinCheck;
import de.lardtom.propwash.race.AssistDetail;
import de.lardtom.propwash.race.AssistReasons;
import de.lardtom.propwash.race.LapAssist;
import de.lardtom.propwash.server.PersonalRulesServer;
import de.lardtom.propwash.server.client.ClientPersonalRules;
import de.lardtom.propwash.server.client.ClientWorldRules;
import de.lardtom.propwash.server.rules.RuleTier;
import de.lardtom.propwash.server.rules.WorldRuleSpecs;
import de.lardtom.propwash.sim.HoldMode;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import org.jspecify.annotations.Nullable;

import java.util.Map;
import java.util.Optional;

public final class RaceRulesJoinProbe {
    public static final int TIMEOUT_TICKS = JoinTestEnv.raceRulesMode() == RaceRulesJoinCheck.TWO_CLIENTS ? RaceRulesTwoJoinProbe.TIMEOUT_TICKS : 20 * 90;
    private static final String START = "race_rules_start";
    private static final String LINES = "race_rules_lines";
    private static final String STAGE = "race_rules_stage";
    private static final String SENT = "race_rules_sent";

    private RaceRulesJoinProbe() {
    }

    private static void line(Map<String, Object> state, String text) {
        state.put(LINES, state.getOrDefault(LINES, "") + text + "\n");
    }

    private static boolean message(Minecraft mc, String key) {
        for (Component message : RaceDataChat.messages(mc)) {
            if (message.getContents() instanceof TranslatableContents text && text.getKey().equals(key)) {
                return true;
            }
        }
        return false;
    }

    private static boolean battery() {
        ClientPersonalRules rules = ClientPersonalRules.INSTANCE;
        return rules.known() && rules.tier(WorldRuleSpecs.BATTERY_SCALE) == RuleTier.PLAYERS
                && rules.own().value(WorldRuleSpecs.BATTERY_SCALE).orElse(-1) == 0;
    }

    public static @Nullable String observe(Map<String, Object> state, int tick, Minecraft mc) {
        if (JoinTestEnv.raceRulesMode() == RaceRulesJoinCheck.TWO_CLIENTS) {
            return RaceRulesTwoJoinProbe.observe(state, tick, mc);
        }
        int start = (int) state.computeIfAbsent(START, key -> tick);
        int stage = (int) state.getOrDefault(STAGE, 0);
        if (tick - start > TIMEOUT_TICKS) {
            line(state, "race_rules timeout at stage " + stage);
            return (String) state.get(LINES);
        }
        if (mc.player == null || !ClientWorldRules.INSTANCE.known()) {
            return null;
        }
        switch (stage) {
            case 0 -> {
                Optional<String> server = RaceDataChat.line(mc, RaceRulesJoinCheck.RESULT);
                if (server.isEmpty()) {
                    return null;
                }
                String result = server.get().substring(RaceRulesJoinCheck.RESULT.length());
                line(state, "race_rules_server=" + (result.startsWith("ok ") ? "ok" : result));
                state.put(STAGE, 1);
                return null;
            }
            case 1 -> {
                if (!battery() || ClientWorldRules.INSTANCE.get(WorldRuleSpecs.BATTERY_SCALE) != 100) {
                    return null;
                }
                line(state, "race_rules_synced=battery0");
                line(state, "race_rules_editable=" + ClientPersonalRules.INSTANCE.editable());
                ClientPersonalRules.INSTANCE.setTier(WorldRuleSpecs.GRAVITY_SCALE, RuleTier.PLAYERS);
                ClientPersonalRules.INSTANCE.set(WorldRuleSpecs.GRAVITY_SCALE, 50);
                state.put(SENT, tick);
                state.put(STAGE, 2);
                return null;
            }
            case 2 -> {
                int sent = (int) state.get(SENT);
                boolean denied = message(mc, PersonalRulesServer.KEY + "tier.denied");
                boolean refused = message(mc, PersonalRulesServer.KEY + "personal.refused");
                if ((!denied || !refused) && tick - sent < 20 * 5) {
                    return null;
                }
                ClientPersonalRules rules = ClientPersonalRules.INSTANCE;
                line(state, "race_rules_denied=" + (denied && rules.tier(WorldRuleSpecs.GRAVITY_SCALE) == RuleTier.ZONES ? "yes" : "no:" + rules.tiers()));
                line(state, "race_rules_refused=" + (refused && !rules.own().has(WorldRuleSpecs.GRAVITY_SCALE) ? "yes" : "no:" + rules.own()));
                state.put(STAGE, 3);
                return null;
            }
            case 3 -> {
                AssistReasons reasons = AssistText.preflight(null, null, HoldMode.OFF);
                boolean named = reasons.details().contains(new AssistDetail(LapAssist.PERSONAL_RULES, "battery_scale", 0, 100))
                        && !reasons.has(LapAssist.WORLD_PHYSICS);
                line(state, "race_rules_preflight=" + (named ? "battery" : "other:" + reasons));
                ClientPersonalRules.INSTANCE.reset(WorldRuleSpecs.BATTERY_SCALE);
                state.put(SENT, tick);
                state.put(STAGE, 4);
                return null;
            }
            case 4 -> {
                int sent = (int) state.get(SENT);
                if (!ClientPersonalRules.INSTANCE.own().isEmpty() && tick - sent < 20 * 5) {
                    return null;
                }
                boolean clean = ClientPersonalRules.INSTANCE.own().isEmpty() && AssistText.preflight(null, null, HoldMode.OFF).none();
                line(state, "race_rules_reset=" + (clean ? "clean" : "assisted:" + AssistText.preflight(null, null, HoldMode.OFF)));
                state.put(STAGE, 5);
                return (String) state.get(LINES);
            }
            default -> {
                return (String) state.get(LINES);
            }
        }
    }
}
