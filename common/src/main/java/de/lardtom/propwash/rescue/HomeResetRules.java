package de.lardtom.propwash.rescue;

import de.lardtom.propwash.server.GeneratedArea;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.joml.Quaternionfc;
import org.joml.Vector3f;
import org.jspecify.annotations.Nullable;

import java.util.Locale;
import java.util.function.DoublePredicate;

public final class HomeResetRules {
    public static final int COOLDOWN_TICKS = 20;
    public static final double CLEARANCE = 4.0;
    public static final double CLEARANCE_STEP = 0.25;

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

    public static double clearY(double y, DoublePredicate free) {
        int steps = (int) Math.round(CLEARANCE / CLEARANCE_STEP);
        for (int i = 0; i <= steps; i++) {
            double candidate = y + i * CLEARANCE_STEP;
            if (free.test(candidate)) {
                return candidate;
            }
        }
        return Double.NaN;
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
        BLOCKED;

        public boolean moves() {
            return this == HOME || this == LAUNCH;
        }

        public boolean silent() {
            return this == NOT_PILOT || this == COOLDOWN || this == LOADING;
        }

        public String messageKey() {
            return "propwash.rescue.reset." + this.name().toLowerCase(Locale.ROOT);
        }
    }

    public record Target(Outcome outcome, double x, double y, double z) {
        static Target refused(Outcome outcome) {
            return new Target(outcome, Double.NaN, Double.NaN, Double.NaN);
        }
    }
}
