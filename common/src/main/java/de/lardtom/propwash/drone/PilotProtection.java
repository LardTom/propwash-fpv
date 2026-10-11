package de.lardtom.propwash.drone;

import de.lardtom.propwash.server.WorldRules;
import de.lardtom.propwash.server.rules.WorldRuleSpecs;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;

public final class PilotProtection {
    private PilotProtection() {
    }

    public static boolean protects(ServerPlayer player, DamageSource source) {
        boolean piloting = DronePiloting.pilotedBy(player.getUUID()).isPresent();
        if (!piloting) {
            return false;
        }
        return PilotBodyRules.shields(WorldRules.on(player.level().getServer(), WorldRuleSpecs.INVULNERABLE_PILOTS), true,
                source.is(DamageTypeTags.BYPASSES_INVULNERABILITY));
    }
}
