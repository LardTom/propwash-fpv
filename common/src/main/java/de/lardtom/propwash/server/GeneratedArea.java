package de.lardtom.propwash.server;

public final class GeneratedArea {
    public static final int NONE = Integer.MAX_VALUE;

    private GeneratedArea() {
    }

    public static Scan scan(Lookup lookup, int centerX, int centerZ, int maxRadius) {
        int generatedRadius = maxRadius;
        boolean blocked = false;
        int missingDistance = NONE;
        boolean complete = true;
        for (int ring = 0; ring <= maxRadius; ring++) {
            RingResult result = scanRing(lookup, centerX, centerZ, ring);
            if (result.missing && missingDistance == NONE) {
                missingDistance = ring;
            }
            if (result.unknown) {
                complete = false;
            }
            if (!blocked && (result.missing || result.unknown)) {
                blocked = true;
                generatedRadius = ring - 1;
            }
        }
        return new Scan(generatedRadius, missingDistance, complete);
    }

    private static RingResult scanRing(Lookup lookup, int centerX, int centerZ, int ring) {
        RingResult result = new RingResult();
        if (ring == 0) {
            result.accept(lookup.status(centerX, centerZ));
            return result;
        }
        for (int d = -ring; d <= ring; d++) {
            result.accept(lookup.status(centerX + d, centerZ - ring));
            result.accept(lookup.status(centerX + d, centerZ + ring));
        }
        for (int d = -ring + 1; d <= ring - 1; d++) {
            result.accept(lookup.status(centerX - ring, centerZ + d));
            result.accept(lookup.status(centerX + ring, centerZ + d));
        }
        return result;
    }

    public static Scan unrestricted() {
        return new Scan(Integer.MAX_VALUE, NONE, true);
    }

    private static final class RingResult {
        private boolean missing;
        private boolean unknown;

        private void accept(Status status) {
            if (status == Status.MISSING) {
                this.missing = true;
            } else if (status != Status.GENERATED) {
                this.unknown = true;
            }
        }
    }

    public enum Status {
        GENERATED,
        MISSING,
        UNKNOWN
    }

    @FunctionalInterface
    public interface Lookup {
        Status status(int chunkX, int chunkZ);
    }

    public record Scan(int generatedRadius, int missingDistance, boolean complete) {
        public boolean missingWithin(int radius) {
            return this.missingDistance <= radius;
        }

        public boolean generatedWithin(int radius) {
            return this.generatedRadius >= radius;
        }
    }
}
