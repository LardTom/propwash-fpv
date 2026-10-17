package de.lardtom.propwash.dev;

import de.lardtom.propwash.Constants;
import de.lardtom.propwash.server.WorldRules;
import de.lardtom.propwash.server.rules.RuleSpec;
import de.lardtom.propwash.server.rules.WorldRuleSpecs;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class AssistJoinCheck {
    public static final int CHANGE_AFTER_TICKS = 60;
    public static final int RESET_AFTER_TICKS = 400;
    public static final int GRAVITY = 130;
    public static final int CRASH_TOLERANCE = 200;
    private static final List<RuleSpec> RULES = List.of(WorldRuleSpecs.GRAVITY_SCALE, WorldRuleSpecs.CRASH_TOLERANCE, WorldRuleSpecs.DRONE_TRAILS);
    private static final Map<UUID, Integer> JOINED = new HashMap<>();

    private AssistJoinCheck() {
    }

    static void tick(MinecraftServer server, int tick) {
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            int joined = JOINED.computeIfAbsent(player.getUUID(), id -> tick);
            if (tick - joined == CHANGE_AFTER_TICKS) {
                WorldRules.set(server, WorldRuleSpecs.GRAVITY_SCALE, GRAVITY);
                WorldRules.set(server, WorldRuleSpecs.CRASH_TOLERANCE, CRASH_TOLERANCE);
                WorldRules.set(server, WorldRuleSpecs.DRONE_TRAILS, 1);
                Constants.LOG.info("[Beitrittstest] Flughilfe: Welt-Regeln für {} gesetzt (Schwerkraft {} %, Crash-Toleranz {} %, Drohnen-Trails an)",
                        player.getPlainTextName(), GRAVITY, CRASH_TOLERANCE);
            } else if (tick - joined == RESET_AFTER_TICKS) {
                for (RuleSpec spec : RULES) {
                    WorldRules.set(server, spec, spec.defaultValue());
                }
                Constants.LOG.info("[Beitrittstest] Flughilfe: Welt-Regeln für {} zurückgesetzt", player.getPlainTextName());
            }
        }
    }
}
