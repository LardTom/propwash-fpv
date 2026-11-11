package de.lardtom.propwash.capture;

import de.lardtom.propwash.api.drone.CameraChunkSupply;
import org.jspecify.annotations.Nullable;

import java.util.Collection;

public final class CaptureRules {
    public static final int MAX_RADIUS = CameraChunkSupply.MAX_RADIUS;
    public static final int MAX_PER_PLAYER = CameraChunkSupply.MAX_PER_PLAYER;
    public static final int MAX_DISTANCE = CameraChunkSupply.MAX_DISTANCE_CHUNKS;
    public static final int MAX_SERVER = 16;
    public static final int TICKET_MARGIN = 2;
    public static final int STORAGE_STEP = 8;
    public static final int STORAGE_MARGIN = 3;
    public static final int SHRINK_SLACK = 16;
    public static final long OVERLOAD_NANOS = 45_000_000L;
    public static final int REFRESH_TICKS = 20;
    public static final int REQUEST_WINDOW_TICKS = 20;
    public static final int REQUESTS_PER_WINDOW = 10;
    private static final int[] COSTS = new int[MAX_RADIUS + 1];

    static {
        for (int r = 1; r <= MAX_RADIUS; r++) {
            COSTS[r] = new CameraArea(0, 0, r).chunks();
        }
    }

    private CaptureRules() {
    }

    public enum Admission {
        ADMITTED,
        TOO_MANY,
        SERVER_BUSY
    }

    public enum End {
        CLOSED,
        DISCONNECTED,
        DIED,
        DRONE_GONE,
        DIMENSION,
        NOT_OWN_DRONE,
        RULE_OFF
    }

    public record Check(boolean online, boolean alive, boolean droneLoaded, boolean sameLevel, boolean ownDrone, int ruleRadius) {
    }

    public static int granted(int requested, int ruleRadius) {
        if (ruleRadius <= 0) {
            return 0;
        }
        return Math.max(1, Math.min(requested, Math.min(MAX_RADIUS, ruleRadius)));
    }

    public static int cost(int radius) {
        if (radius < 1) {
            return 0;
        }
        return radius <= MAX_RADIUS ? COSTS[radius] : new CameraArea(0, 0, radius).chunks();
    }

    public static Admission admit(int own, int server, long serverChunks, int cost, int budget, boolean overloaded) {
        if (own >= MAX_PER_PLAYER) {
            return Admission.TOO_MANY;
        }
        if (overloaded || server >= MAX_SERVER || serverChunks + cost > budget) {
            return Admission.SERVER_BUSY;
        }
        return Admission.ADMITTED;
    }

    public static @Nullable End end(Check check) {
        if (!check.online()) {
            return End.DISCONNECTED;
        }
        if (!check.alive()) {
            return End.DIED;
        }
        if (!check.droneLoaded()) {
            return End.DRONE_GONE;
        }
        if (!check.sameLevel()) {
            return End.DIMENSION;
        }
        if (!check.ownDrone()) {
            return End.NOT_OWN_DRONE;
        }
        if (check.ruleRadius() <= 0) {
            return End.RULE_OFF;
        }
        return null;
    }

    public static boolean tooFar(int baseX, int baseZ, CameraArea area) {
        return area.distanceFrom(baseX, baseZ) > MAX_DISTANCE;
    }

    public static int storageRadius(int baseX, int baseZ, Collection<CameraArea> areas) {
        int extent = 0;
        for (CameraArea area : areas) {
            extent = Math.max(extent, area.extentFrom(baseX, baseZ));
        }
        if (extent == 0) {
            return 0;
        }
        int wanted = extent + STORAGE_MARGIN;
        return (wanted + STORAGE_STEP - 1) / STORAGE_STEP * STORAGE_STEP;
    }

    public static int grown(int sent, int needed) {
        return Math.max(sent, needed);
    }

    public static int shrunk(int sent, int needed) {
        if (needed == 0) {
            return 0;
        }
        return needed + SHRINK_SLACK <= sent ? needed : sent;
    }

    public static int ticketRadius(int radius) {
        return radius + TICKET_MARGIN;
    }

    public static boolean overloaded(long averageTickNanos) {
        return averageTickNanos > OVERLOAD_NANOS;
    }

    public static boolean allowRequest(int[] window, int tick) {
        if (tick - window[0] >= REQUEST_WINDOW_TICKS || tick < window[0]) {
            window[0] = tick;
            window[1] = 0;
        }
        if (window[1] >= REQUESTS_PER_WINDOW) {
            return false;
        }
        window[1]++;
        return true;
    }
}
