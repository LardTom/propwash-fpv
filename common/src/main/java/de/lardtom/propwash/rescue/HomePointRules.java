package de.lardtom.propwash.rescue;

import org.jspecify.annotations.Nullable;

public final class HomePointRules {
    public static final double HEIGHT_SLACK = 64.0;

    private HomePointRules() {
    }

    public static @Nullable Problem check(double x, double y, double z, String dimension, String droneDimension, int minY, int maxY,
                                          boolean insideBorder) {
        if (!Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(z)) {
            return Problem.INVALID;
        }
        if (!dimension.equals(droneDimension)) {
            return Problem.DIMENSION;
        }
        if (y < lowest(minY) || y > highest(maxY)) {
            return Problem.HEIGHT;
        }
        return insideBorder ? null : Problem.BORDER;
    }

    public static Edit edit(@Nullable HomePoint current, HomePoint edited, String droneDimension, int minY, int maxY,
                            boolean insideBorder, long now) {
        HomePoint target = edited.resolved(droneDimension, now);
        if (current != null && current.samePlace(target)) {
            return new Edit(current, null);
        }
        if (current != null && target.dimension().equals(current.dimension()) && !target.dimension().equals(droneDimension)) {
            target = new HomePoint(target.x(), target.y(), target.z(), droneDimension, target.gameTime(), target.source());
        }
        Problem problem = check(target.x(), target.y(), target.z(), target.dimension(), droneDimension, minY, maxY, insideBorder);
        return problem != null ? new Edit(null, problem) : new Edit(target.stamped(now, HomePoint.Source.COMMAND), null);
    }

    public static double lowest(int minY) {
        return minY - HEIGHT_SLACK;
    }

    public static double highest(int maxY) {
        return maxY + HEIGHT_SLACK;
    }

    public enum Problem {
        INVALID,
        DIMENSION,
        HEIGHT,
        BORDER
    }

    public record Edit(@Nullable HomePoint home, @Nullable Problem problem) {
    }
}
