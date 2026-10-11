package de.lardtom.propwash.interact;

import de.lardtom.propwash.server.WorldRules;
import de.lardtom.propwash.server.rules.RuleSpec;
import de.lardtom.propwash.server.rules.WorldRuleSpecs;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.gamerules.GameRule;
import org.jspecify.annotations.Nullable;

public final class InteractRules {
    public static final RuleSpec DRONE_HITS_ENTITIES = WorldRuleSpecs.DRONE_HITS_ENTITIES;
    public static final RuleSpec DRONES_BREAK_BLOCKS = WorldRuleSpecs.DRONES_BREAK_BLOCKS;
    public static final RuleSpec BREAK_IMPACT_SCALE = WorldRuleSpecs.BREAK_IMPACT_SCALE;

    private InteractRules() {
    }

    public static boolean dronesHitEntities(@Nullable MinecraftServer server) {
        return WorldRules.on(server, DRONE_HITS_ENTITIES);
    }

    public static boolean dronesBreakBlocks(@Nullable MinecraftServer server) {
        return WorldRules.on(server, DRONES_BREAK_BLOCKS);
    }

    public static double breakImpact(int percent) {
        return Math.max(BREAK_IMPACT_SCALE.min(), Math.min(BREAK_IMPACT_SCALE.max(), percent)) / 100.0;
    }

    @SuppressWarnings("unchecked")
    public static GameRule<Boolean> rule() {
        return (GameRule<Boolean>) WorldRules.rule(DRONE_HITS_ENTITIES).orElseThrow(() -> new IllegalStateException("Welt-Regel "
                + DRONE_HITS_ENTITIES.id() + " nicht registriert"));
    }

    @SuppressWarnings("unchecked")
    public static GameRule<Boolean> breakRule() {
        return (GameRule<Boolean>) WorldRules.rule(DRONES_BREAK_BLOCKS).orElseThrow(() -> new IllegalStateException("Welt-Regel "
                + DRONES_BREAK_BLOCKS.id() + " nicht registriert"));
    }
}
