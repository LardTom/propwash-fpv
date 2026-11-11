package de.lardtom.propwash.drone.client;

import de.lardtom.propwash.drone.QuadcopterEntity;
import de.lardtom.propwash.input.StickPositions;
import de.lardtom.propwash.sim.DroneSim;
import de.lardtom.propwash.sim.StabMode;
import de.lardtom.propwash.sim.Telemetry;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.Map;

final class CovJoinFlight {
    private CovJoinFlight() {
    }

    static boolean flying() {
        PilotSession session = PilotSession.INSTANCE;
        return session.isFpv() && session.sim() != null && session.drone() != null;
    }

    static Telemetry telemetry() {
        DroneSim sim = PilotSession.INSTANCE.sim();
        return sim == null ? new Telemetry() : sim.telemetry();
    }

    static double y() {
        QuadcopterEntity drone = PilotSession.INSTANCE.drone();
        return drone == null ? Double.NaN : drone.getY();
    }

    static void sticks(float roll, float pitch, float yaw, float throttle, boolean arm) {
        PilotSession.INSTANCE.setTestInput(new StickPositions(roll, pitch, yaw, throttle), arm, StabMode.ANGLE);
    }

    static float holdThrottle(double targetY, double extra) {
        Telemetry t = telemetry();
        double hover = Math.clamp(t.hoverThrottleStick, 0.05, 0.95);
        double wanted = Math.clamp(1.2 * (targetY - y()), -3.0, 3.0);
        return (float) Math.clamp(hover + 0.08 * (wanted - t.verticalSpeed) + extra, 0.0, 1.0);
    }

    static void hold(double targetY, float pitch) {
        sticks(0.0F, pitch, 0.0F, holdThrottle(targetY, pitch != 0.0F ? 0.03 : 0.0), true);
    }

    static double tiltDeg() {
        QuadcopterEntity drone = PilotSession.INSTANCE.drone();
        if (drone == null) {
            return Double.NaN;
        }
        Vector3f up = new Quaternionf(drone.orientation()).transform(new Vector3f(0.0F, 1.0F, 0.0F));
        return Math.toDegrees(Math.acos(Math.clamp(up.y, -1.0F, 1.0F)));
    }

    static String phase(Map<String, Object> state, String key, String initial) {
        return (String) state.computeIfAbsent(key, k -> initial);
    }

    static void next(Map<String, Object> state, String key, String phase, int tick) {
        state.put(key, phase);
        state.put(key + "_at", tick);
    }

    static int since(Map<String, Object> state, String key, int tick) {
        return tick - (int) state.getOrDefault(key + "_at", tick);
    }
}
