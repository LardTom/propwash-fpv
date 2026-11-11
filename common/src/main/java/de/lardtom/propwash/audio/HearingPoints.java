package de.lardtom.propwash.audio;

import de.lardtom.propwash.drone.PilotMode;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

public final class HearingPoints {
    private HearingPoints() {
    }

    public enum View {
        BODY,
        LINE_OF_SIGHT_PILOT,
        FPV_PILOT,
        SPECTATOR;

        public boolean atDrone() {
            return this == FPV_PILOT || this == SPECTATOR;
        }
    }

    public record Point(double x, double y, double z) {
        public double distanceSqr(double px, double py, double pz) {
            double dx = px - this.x;
            double dy = py - this.y;
            double dz = pz - this.z;
            return dx * dx + dy * dy + dz * dz;
        }
    }

    public record Listener<T>(T who, Point body, View view, @Nullable Point drone) {
        public Point hearing(double x, double y, double z) {
            return nearest(this.body, this.view.atDrone() ? this.drone : null, x, y, z);
        }
    }

    public static View view(boolean watching, PilotMode piloting) {
        if (watching) {
            return View.SPECTATOR;
        }
        return switch (piloting) {
            case FPV -> View.FPV_PILOT;
            case LINE_OF_SIGHT -> View.LINE_OF_SIGHT_PILOT;
            case NONE -> View.BODY;
        };
    }

    public static boolean droneCloser(double bodyX, double bodyY, double bodyZ, double droneX, double droneY, double droneZ, double x, double y, double z) {
        double dx = x - droneX;
        double dy = y - droneY;
        double dz = z - droneZ;
        double bx = x - bodyX;
        double by = y - bodyY;
        double bz = z - bodyZ;
        return dx * dx + dy * dy + dz * dz < bx * bx + by * by + bz * bz;
    }

    public static Point nearest(Point body, @Nullable Point drone, double x, double y, double z) {
        if (drone == null) {
            return body;
        }
        return droneCloser(body.x(), body.y(), body.z(), drone.x(), drone.y(), drone.z(), x, y, z) ? drone : body;
    }

    public static boolean inRange(Point at, double x, double y, double z, double range) {
        return at.distanceSqr(x, y, z) < range * range;
    }

    public static <T> List<T> recipients(List<Listener<T>> listeners, @Nullable T except, double x, double y, double z, double range) {
        List<T> out = new ArrayList<>();
        for (Listener<T> listener : listeners) {
            if (listener.who() == except) {
                continue;
            }
            if (inRange(listener.hearing(x, y, z), x, y, z, range)) {
                out.add(listener.who());
            }
        }
        return out;
    }
}
