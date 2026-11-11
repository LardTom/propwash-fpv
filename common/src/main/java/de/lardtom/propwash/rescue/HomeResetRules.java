package de.lardtom.propwash.rescue;

import de.lardtom.propwash.server.GeneratedArea;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.joml.Quaternionfc;
import org.joml.Vector3f;
import org.jspecify.annotations.Nullable;

import java.util.Locale;

public final class HomeResetRules {
    public static final int COOLDOWN_TICKS = 20;
    public static final int SEARCH_RADIUS = 3;
    public static final double RISE = 1.0;
    public static final double STEP_DOWN = 1.0;
    public static final double LIFT_STEP = 0.125;
    private static final double EPS = 1.0E-6;

    private HomeResetRules() {
    }

    public static Target choose(boolean allowed, boolean pilot, long now, long lastReset, @Nullable HomePoint home, @Nullable Vec3 launch,
                                String dimension) {
        if (!pilot) {
            return Target.refused(Outcome.NOT_PILOT);
        }
        if (!allowed) {
            return Target.refused(Outcome.DISABLED);
        }
        if (lastReset != Long.MIN_VALUE && now >= lastReset && now - lastReset < COOLDOWN_TICKS) {
            return Target.refused(Outcome.COOLDOWN);
        }
        if (home != null && home.dimension().equals(dimension) && finite(home.x(), home.y(), home.z())) {
            return new Target(Outcome.HOME, home.x(), home.y(), home.z());
        }
        if (launch != null && finite(launch.x, launch.y, launch.z)) {
            return new Target(Outcome.LAUNCH, launch.x, launch.y, launch.z);
        }
        return Target.refused(home != null ? Outcome.OTHER_DIMENSION : Outcome.NO_HOME);
    }

    public static @Nullable Outcome restriction(boolean inRange, boolean protectedArea, GeneratedArea.Status generated, boolean mayGenerate) {
        if (!inRange) {
            return Outcome.OUT_OF_RANGE;
        }
        if (protectedArea) {
            return Outcome.PROTECTED;
        }
        if (mayGenerate || generated == GeneratedArea.Status.GENERATED) {
            return null;
        }
        return generated == GeneratedArea.Status.MISSING ? Outcome.UNEXPLORED : Outcome.LOADING;
    }

    public static boolean inside(double y, int minY, int maxY, boolean insideBorder) {
        return insideBorder && Double.isFinite(y) && y >= minY && y <= maxY;
    }

    public static @Nullable Spot spot(double x, double y, double z, Space space) {
        if (space.free(x, y, z)) {
            return new Spot(x, y, z, false);
        }
        Spot best = null;
        double bestScore = Double.POSITIVE_INFINITY;
        double bestRise = Double.POSITIVE_INFINITY;
        int limit = SEARCH_RADIUS * SEARCH_RADIUS;
        for (int dx = -SEARCH_RADIUS; dx <= SEARCH_RADIUS; dx++) {
            for (int dz = -SEARCH_RADIUS; dz <= SEARCH_RADIUS; dz++) {
                int flat = dx * dx + dz * dz;
                if (flat > limit) {
                    continue;
                }
                double cx = x + dx;
                double cz = z + dz;
                double standing = stand(cx, y, cz, space);
                if (Double.isNaN(standing)) {
                    continue;
                }
                double rise = Math.abs(standing - y);
                double score = flat + rise * rise;
                if (score < bestScore - EPS || score <= bestScore + EPS && rise < bestRise - EPS) {
                    bestScore = score;
                    bestRise = rise;
                    best = new Spot(cx, standing, cz, flat != 0 || standing - y >= RISE - EPS);
                }
            }
        }
        return best;
    }

    public static double ground(double x, double y, double z, Space space, double depth) {
        double start = lift(x, y, z, space);
        if (Double.isNaN(start)) {
            return Double.NaN;
        }
        double fall = space.fall(x, start, z, depth);
        return fall < depth - EPS ? start - fall : start;
    }

    public static double lift(double x, double y, double z, Space space) {
        int steps = (int) Math.round(RISE / LIFT_STEP);
        for (int i = 0; i <= steps; i++) {
            double candidate = y + i * LIFT_STEP;
            if (space.free(x, candidate, z)) {
                return candidate;
            }
        }
        return Double.NaN;
    }

    static double stand(double x, double y, double z, Space space) {
        double start = lift(x, y, z, space);
        if (Double.isNaN(start)) {
            return Double.NaN;
        }
        double depth = start - (y - STEP_DOWN);
        double fall = space.fall(x, start, z, depth + LIFT_STEP);
        return fall <= depth + EPS ? start - fall : Double.NaN;
    }

    public static Quaternionf level(Quaternionfc orientation) {
        Vector3f forward = orientation.transform(new Vector3f(0.0F, 0.0F, -1.0F));
        if (forward.x * forward.x + forward.z * forward.z < 1.0E-6F) {
            Vector3f up = orientation.transform(new Vector3f(0.0F, 1.0F, 0.0F));
            float sign = Math.signum(forward.y);
            forward.set(-up.x * sign, 0.0F, -up.z * sign);
        }
        if (forward.x * forward.x + forward.z * forward.z < 1.0E-6F) {
            return new Quaternionf();
        }
        return new Quaternionf().rotationY((float) Math.atan2(-forward.x, -forward.z));
    }

    private static boolean finite(double x, double y, double z) {
        return Double.isFinite(x) && Double.isFinite(y) && Double.isFinite(z);
    }

    public enum Outcome {
        HOME,
        LAUNCH,
        NOT_PILOT,
        DISABLED,
        COOLDOWN,
        NO_HOME,
        OTHER_DIMENSION,
        OUTSIDE,
        OUT_OF_RANGE,
        PROTECTED,
        UNEXPLORED,
        LOADING,
        UNLOADED,
        BLOCKED,
        NEARBY;

        public boolean moves() {
            return this == HOME || this == LAUNCH || this == NEARBY;
        }

        public boolean silent() {
            return this == NOT_PILOT || this == COOLDOWN || this == LOADING;
        }

        public String messageKey() {
            return "propwash.rescue.reset." + this.name().toLowerCase(Locale.ROOT);
        }
    }

    public interface Space {
        boolean free(double x, double y, double z);

        double fall(double x, double y, double z, double depth);
    }

    public record Spot(double x, double y, double z, boolean nearby) {
    }

    public record Target(Outcome outcome, double x, double y, double z) {
        static Target refused(Outcome outcome) {
            return new Target(outcome, Double.NaN, Double.NaN, Double.NaN);
        }
    }
}
