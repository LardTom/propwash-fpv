package de.lardtom.propwash.drone;

import de.lardtom.propwash.build.VideoSystem;
import de.lardtom.propwash.content.ContentIds;
import net.minecraft.world.entity.player.Player;
import org.jspecify.annotations.Nullable;

import java.util.function.BooleanSupplier;
import java.util.function.DoubleSupplier;

public final class RadioLink {
    public static final double FULL_RANGE = 800.0;
    public static final double FADE_RANGE = 400.0;
    public static final double START_RANGE = 800.0;
    public static final double RELEASE_RANGE = 1250.0;
    private static final double SERVER_FADE_START = 0.8;
    private static final double SERVER_FADE_END = 0.95;

    public static volatile DoubleSupplier SERVER_LIMIT = () -> Double.POSITIVE_INFINITY;
    public static volatile BooleanSupplier LOCAL_FREE_RANGE = () -> false;

    private RadioLink() {
    }

    public static boolean freeRange(@Nullable Player player) {
        if (player == null) {
            return false;
        }
        return player.isCreative() || ContentIds.wornGogglesSystem(player).filter(system -> system == VideoSystem.CREATIVE).isPresent();
    }

    public static int displayRange() {
        return (int) Math.round(startRange(SERVER_LIMIT.getAsDouble()));
    }

    public static float quality(double distance) {
        return quality(distance, Double.POSITIVE_INFINITY);
    }

    public static float quality(double distance, double serverLimit) {
        return quality(distance, serverLimit, false);
    }

    public static float quality(double distance, double serverLimit, boolean freeRange) {
        if (!Double.isFinite(distance)) {
            return 0.0F;
        }
        double fadeStart = freeRange ? Double.POSITIVE_INFINITY : FULL_RANGE;
        double fadeEnd = freeRange ? Double.POSITIVE_INFINITY : FULL_RANGE + FADE_RANGE;
        if (Double.isFinite(serverLimit) && serverLimit > 0.0 && serverLimit * SERVER_FADE_END < fadeEnd) {
            fadeEnd = serverLimit * SERVER_FADE_END;
            fadeStart = Math.min(fadeStart, serverLimit * SERVER_FADE_START);
        }
        if (!Double.isFinite(fadeEnd)) {
            return 1.0F;
        }
        double fade = (distance - fadeStart) / Math.max(1.0E-3, fadeEnd - fadeStart);
        return (float) Math.max(0.0, Math.min(1.0, 1.0 - fade));
    }

    public static float quality(double distance, double serverLimit, boolean freeRange, double creativeRange) {
        float base = quality(distance, serverLimit, freeRange);
        if (!freeRange || !(creativeRange > 0.0) || !Double.isFinite(creativeRange)) {
            return base;
        }
        double fade = (distance - creativeRange) / (creativeRange * FADE_RANGE / FULL_RANGE);
        return Math.min(base, (float) Math.max(0.0, Math.min(1.0, 1.0 - fade)));
    }

    public static double startRange(double serverLimit) {
        return startRange(serverLimit, false);
    }

    public static double startRange(double serverLimit, boolean freeRange) {
        if (freeRange) {
            return serverLimit;
        }
        return Double.isFinite(serverLimit) ? Math.min(serverLimit, START_RANGE) : START_RANGE;
    }

    public static double releaseRange(double serverLimit, boolean freeRange) {
        return freeRange ? serverLimit : Math.min(serverLimit, RELEASE_RANGE);
    }
}
