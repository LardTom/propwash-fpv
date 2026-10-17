package de.lardtom.propwash.interact;

import de.lardtom.propwash.core.event.ServerBus;
import de.lardtom.propwash.drone.QuadcopterEntity;
import de.lardtom.propwash.interact.net.CargoReleaseC2S;
import de.lardtom.propwash.interact.net.DroneBellC2S;
import de.lardtom.propwash.interact.net.DroneBreakC2S;
import de.lardtom.propwash.interact.net.DroneBreakClearC2S;
import de.lardtom.propwash.interact.net.DroneBreakClearS2C;
import de.lardtom.propwash.interact.net.DroneBreakDeniedS2C;
import de.lardtom.propwash.interact.net.DroneStrikeC2S;
import de.lardtom.propwash.interact.net.SnagWearC2S;
import de.lardtom.propwash.network.PropwashNet;
import de.lardtom.propwash.network.ServerPayloadContext;
import net.minecraft.server.level.ServerPlayer;

public final class InteractModule {
    private static boolean initialized;

    private InteractModule() {
    }

    public static synchronized void init() {
        if (initialized) {
            return;
        }
        initialized = true;
        PropwashNet.registerC2S(DroneStrikeC2S.TYPE, DroneStrikeC2S.CODEC, DroneStrikes::onStrike);
        PropwashNet.registerC2S(CargoReleaseC2S.TYPE, CargoReleaseC2S.CODEC, InteractModule::onRelease);
        PropwashNet.registerC2S(DroneBreakC2S.TYPE, DroneBreakC2S.CODEC, DroneBreaks::onBreak);
        PropwashNet.registerS2C(DroneBreakDeniedS2C.TYPE, DroneBreakDeniedS2C.CODEC);
        PropwashNet.registerC2S(DroneBreakClearC2S.TYPE, DroneBreakClearC2S.CODEC, DroneBreaks::onClear);
        PropwashNet.registerS2C(DroneBreakClearS2C.TYPE, DroneBreakClearS2C.CODEC);
        PropwashNet.registerC2S(SnagWearC2S.TYPE, SnagWearC2S.CODEC, DroneSnags::onWear);
        PropwashNet.registerC2S(DroneBellC2S.TYPE, DroneBellC2S.CODEC, DroneBells::onBell);
        ServerBus.STARTED.register(server -> {
            DroneStrikes.reset();
            DroneBreaks.reset();
            DroneSnags.reset();
            DroneBells.reset();
        });
        ServerBus.STOPPED.register(server -> {
            DroneStrikes.reset();
            DroneBreaks.reset();
            DroneSnags.reset();
            DroneBells.reset();
        });
        QuadcopterEntity.SERVER_MOVED.register((drone, pilot, from, to) -> DroneStrikes.onMoved(drone));
        QuadcopterEntity.HOME_RESET.register((drone, pilot) -> DroneCargo.release(drone, CargoReleaseReason.COMMAND));
    }

    private static void onRelease(CargoReleaseC2S payload, ServerPayloadContext context) {
        ServerPlayer player = context.player();
        if (!(player.level().getEntity(payload.droneId()) instanceof QuadcopterEntity drone) || drone.isRemoved()) {
            return;
        }
        if (!drone.isFlownBy(player.getUUID()) || payload.epoch() != drone.resetEpoch()) {
            return;
        }
        DroneCargo.release(drone, payload.reason());
    }
}
