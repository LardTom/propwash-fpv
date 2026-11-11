package de.lardtom.propwash.interact;

import de.lardtom.propwash.drone.QuadcopterEntity;
import de.lardtom.propwash.server.WorldRules;
import de.lardtom.propwash.server.rules.WorldRuleSpecs;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.gameevent.GameEvent;
import org.jspecify.annotations.Nullable;

public final class DroneVibrations {
    public static final int INTERVAL_TICKS = 10;
    public static final float MOTOR_THRESHOLD = 0.1F;

    private DroneVibrations() {
    }

    public static boolean flying(boolean armed, float motorOutput) {
        return armed && motorOutput >= MOTOR_THRESHOLD;
    }

    public static boolean due(int tickCount) {
        return tickCount % INTERVAL_TICKS == 0;
    }

    public static double fallMoved(double reported, double fromY, double toY) {
        return Double.isFinite(fromY) && Double.isFinite(toY) ? toY - fromY : reported;
    }

    public static void tick(QuadcopterEntity drone, ServerLevel level) {
        if (!due(drone.tickCount) || drone.isRemoved() || !flying(drone.isArmed(), drone.motorOutput()) || !enabled(level)) {
            return;
        }
        level.gameEvent(GameEvent.ELYTRA_GLIDE, drone.position(), GameEvent.Context.of(drone));
    }

    public static void crashed(QuadcopterEntity drone) {
        if (drone.isRemoved() || !(drone.level() instanceof ServerLevel level) || !enabled(level)) {
            return;
        }
        level.gameEvent(GameEvent.HIT_GROUND, drone.position(), GameEvent.Context.of(drone, drone.getBlockStateOn()));
    }

    public static boolean enabled(ServerLevel level) {
        return WorldRules.on(level.getServer(), WorldRuleSpecs.DRONES_TRIGGER_SCULK);
    }

    public static boolean muted(QuadcopterEntity drone) {
        return drone.level() instanceof ServerLevel level && !enabled(level);
    }

    public static @Nullable ServerPlayer pilot(@Nullable Entity source) {
        if (source instanceof QuadcopterEntity drone && drone.level() instanceof ServerLevel && drone.pilotEntity() instanceof ServerPlayer player) {
            return player;
        }
        return null;
    }
}
