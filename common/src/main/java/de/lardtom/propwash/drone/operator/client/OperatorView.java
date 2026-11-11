package de.lardtom.propwash.drone.operator.client;

import de.lardtom.propwash.drone.QuadcopterEntity;
import de.lardtom.propwash.drone.client.PilotSession;
import de.lardtom.propwash.rescue.client.AutonomyLabels;
import de.lardtom.propwash.video.client.SpectateSession;
import de.lardtom.propwash.video.net.ChannelListS2C;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import org.jspecify.annotations.Nullable;

import java.util.UUID;

public final class OperatorView {
    private static int spectateCalls;

    private OperatorView() {
    }

    static void begin(Minecraft minecraft, UUID drone, int entityId) {
        if (PilotSession.INSTANCE.isActive()) {
            return;
        }
        SpectateSession.INSTANCE.operate(minecraft, drone, entityId);
    }

    static boolean end(Minecraft minecraft, int entityId, @Nullable UUID drone) {
        SpectateSession session = SpectateSession.INSTANCE;
        boolean same = drone != null && drone.equals(session.target()) || connecting(entityId) || watching(entityId);
        if (session.operator() && same) {
            session.stop(minecraft, false);
            return true;
        }
        return false;
    }

    public static boolean watching(int entityId) {
        SpectateSession session = SpectateSession.INSTANCE;
        QuadcopterEntity drone = session.drone();
        return session.operator() && session.watching() && drone != null && drone.getId() == entityId;
    }

    static boolean connecting(int entityId) {
        SpectateSession session = SpectateSession.INSTANCE;
        return session.operator() && session.phase() == SpectateSession.Phase.CONNECTING;
    }

    public static boolean ownDrone(@Nullable Entity drone) {
        return drone instanceof QuadcopterEntity && AutonomyLabels.active(drone);
    }

    public static @Nullable QuadcopterEntity recordingDrone() {
        PilotSession pilot = PilotSession.INSTANCE;
        if (pilot.isActive()) {
            return pilot.isFpv() ? pilot.drone() : null;
        }
        SpectateSession session = SpectateSession.INSTANCE;
        QuadcopterEntity watched = session.watching() ? session.drone() : null;
        if (watched == null) {
            return null;
        }
        if (session.operator() && OperatorClient.operatorDrone() == watched.getId()) {
            return watched;
        }
        return ownDrone(watched) ? watched : null;
    }

    public static boolean recordable(@Nullable Entity drone) {
        return drone != null && recordingDrone() == drone;
    }

    public static boolean spectate(Entity drone) {
        spectateCalls++;
        Minecraft minecraft = Minecraft.getInstance();
        if (!(drone instanceof QuadcopterEntity quad) || quad.isRemoved() || minecraft.level == null || quad.level() != minecraft.level
                || PilotSession.INSTANCE.isActive() || !ownDrone(quad)) {
            return false;
        }
        SpectateSession session = SpectateSession.INSTANCE;
        if (!session.operator() && quad.getUUID().equals(session.target()) && session.active()) {
            return true;
        }
        ChannelListS2C.Entry entry = session.entry(quad.getUUID());
        return entry != null && session.request(minecraft, entry);
    }

    public static int spectateCalls() {
        return spectateCalls;
    }
}
