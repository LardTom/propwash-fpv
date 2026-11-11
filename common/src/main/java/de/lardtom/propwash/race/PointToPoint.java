package de.lardtom.propwash.race;

import de.lardtom.propwash.api.race.CourseMode;
import org.jspecify.annotations.Nullable;

import java.util.List;

public final class PointToPoint {
    public static final int MIN_PASSES = 2;

    private PointToPoint() {
    }

    public static boolean active(CourseMode mode, int passes) {
        return mode == CourseMode.POINT_TO_POINT && passes >= MIN_PASSES;
    }

    public static int finish(int passes) {
        return passes - 1;
    }

    public static boolean finish(CourseMode mode, int passes, int index) {
        return active(mode, passes) && index == finish(passes);
    }

    public static int remaining(int passes, int nextGate, boolean holeshot) {
        if (passes <= 0 || nextGate < 0) {
            return 0;
        }
        return holeshot ? passes : Math.max(0, passes - nextGate);
    }

    public static double pathLength(List<GateGeometry> gates) {
        double total = 0.0;
        for (int i = 1; i < gates.size(); i++) {
            GateGeometry a = gates.get(i - 1);
            GateGeometry b = gates.get(i);
            double dx = b.centerX() - a.centerX();
            double dy = b.centerY() - a.centerY();
            double dz = b.centerZ() - a.centerZ();
            total += Math.sqrt(dx * dx + dy * dy + dz * dz);
        }
        return total;
    }

    public static @Nullable String stored(CourseMode mode) {
        return mode == CourseMode.POINT_TO_POINT ? mode.key() : null;
    }

    public static CourseMode read(@Nullable String stored) {
        return stored == null ? CourseMode.LAPS : CourseMode.byKey(stored);
    }
}
