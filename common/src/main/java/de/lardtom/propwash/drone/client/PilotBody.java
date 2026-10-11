package de.lardtom.propwash.drone.client;

import de.lardtom.propwash.drone.PilotBodyRules;
import de.lardtom.propwash.mixin.drone.client.ClientInputAccessor;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.SectionPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Input;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec2;

public final class PilotBody {
    private static long heldTicks;
    private static boolean lastHeld;

    private PilotBody() {
    }

    public static boolean idle(Object entity) {
        return FpvView.isPilotBody(entity);
    }

    public static void clearInput(LocalPlayer player) {
        player.input.keyPresses = Input.EMPTY;
        ((ClientInputAccessor) player.input).propwash$setMoveVector(Vec2.ZERO);
        player.xxa = 0.0F;
        player.yya = 0.0F;
        player.zza = 0.0F;
        player.setJumping(false);
    }

    public static boolean held(Object entity) {
        if (!(entity instanceof Entity body) || !FpvView.isPilotBody(entity)) {
            return false;
        }
        boolean held = !surroundingsLoaded(body);
        lastHeld = held;
        if (held) {
            heldTicks++;
        }
        return held;
    }

    public static boolean reportsPosition(LocalPlayer player) {
        return FpvView.isPilotBody(player) && surroundingsLoaded(player);
    }

    public static boolean surroundingsLoaded(Entity body) {
        Level level = body.level();
        int cx = SectionPos.blockToSectionCoord(body.getBlockX());
        int cz = SectionPos.blockToSectionCoord(body.getBlockZ());
        for (int dx = -PilotBodyRules.LOADED_RADIUS; dx <= PilotBodyRules.LOADED_RADIUS; dx++) {
            for (int dz = -PilotBodyRules.LOADED_RADIUS; dz <= PilotBodyRules.LOADED_RADIUS; dz++) {
                if (!level.getChunkSource().hasChunk(cx + dx, cz + dz)) {
                    return false;
                }
            }
        }
        return true;
    }

    public static long heldTicks() {
        return heldTicks;
    }

    public static boolean lastHeld() {
        return lastHeld;
    }
}
