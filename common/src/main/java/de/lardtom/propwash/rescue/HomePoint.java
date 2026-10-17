package de.lardtom.propwash.rescue;

import org.jspecify.annotations.Nullable;

import java.util.Locale;
import java.util.Objects;

public record HomePoint(double x, double y, double z, String dimension, long gameTime, Source source) {
    public static final long UNKNOWN_TIME = -1L;

    public HomePoint {
        Objects.requireNonNull(dimension, "dimension");
        Objects.requireNonNull(source, "source");
    }

    public boolean samePlace(HomePoint other) {
        return this.samePlace(other.x, other.y, other.z, other.dimension);
    }

    public boolean samePlace(double x, double y, double z, String dimension) {
        return Double.compare(this.x, x) == 0 && Double.compare(this.y, y) == 0 && Double.compare(this.z, z) == 0
                && this.dimension.equals(dimension);
    }

    public HomePoint stamped(long gameTime, Source source) {
        return new HomePoint(this.x, this.y, this.z, this.dimension, gameTime, source);
    }

    public HomePoint resolved(String dimension, long now) {
        return new HomePoint(this.x, this.y, this.z, this.dimension.isEmpty() ? dimension : this.dimension,
                this.gameTime < 0L ? now : this.gameTime, this.source);
    }

    public enum Source {
        ARM,
        MANUAL,
        PILOT,
        COMMAND;

        private static final Source[] VALUES = values();

        public String id() {
            return this.name().toLowerCase(Locale.ROOT);
        }

        public boolean fromPilot() {
            return this != COMMAND;
        }

        public static @Nullable Source byId(String id) {
            for (Source source : VALUES) {
                if (source.id().equals(id)) {
                    return source;
                }
            }
            return null;
        }

        public static @Nullable Source byOrdinal(int ordinal) {
            return ordinal >= 0 && ordinal < VALUES.length ? VALUES[ordinal] : null;
        }
    }
}
