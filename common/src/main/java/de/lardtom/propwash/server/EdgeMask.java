package de.lardtom.propwash.server;

public record EdgeMask(int centerX, int centerZ, long low, long mid, long high) {
    public static final int RADIUS = 6;
    public static final int SIDE = 2 * RADIUS + 1;
    public static final EdgeMask NONE = new EdgeMask(0, 0, 0L, 0L, 0L);

    public static EdgeMask scan(GeneratedArea.Lookup lookup, int centerX, int centerZ) {
        int outer = SIDE + 2;
        boolean[] missing = new boolean[outer * outer];
        for (int dz = -RADIUS - 1; dz <= RADIUS + 1; dz++) {
            for (int dx = -RADIUS - 1; dx <= RADIUS + 1; dx++) {
                missing[(dz + RADIUS + 1) * outer + dx + RADIUS + 1] = lookup.status(centerX + dx, centerZ + dz) == GeneratedArea.Status.MISSING;
            }
        }
        long[] bits = new long[3];
        for (int dz = -RADIUS; dz <= RADIUS; dz++) {
            for (int dx = -RADIUS; dx <= RADIUS; dx++) {
                boolean blocked = false;
                for (int nz = -1; nz <= 1 && !blocked; nz++) {
                    for (int nx = -1; nx <= 1 && !blocked; nx++) {
                        blocked = missing[(dz + nz + RADIUS + 1) * outer + dx + nx + RADIUS + 1];
                    }
                }
                if (blocked) {
                    int index = index(dx, dz);
                    bits[index >>> 6] |= 1L << (index & 63);
                }
            }
        }
        return new EdgeMask(centerX, centerZ, bits[0], bits[1], bits[2]);
    }

    private static int index(int dx, int dz) {
        return (dz + RADIUS) * SIDE + dx + RADIUS;
    }

    public boolean any() {
        return (this.low | this.mid | this.high) != 0L;
    }

    public boolean blocked(int chunkX, int chunkZ) {
        int dx = chunkX - this.centerX;
        int dz = chunkZ - this.centerZ;
        if (Math.abs(dx) > RADIUS || Math.abs(dz) > RADIUS) {
            return false;
        }
        int index = index(dx, dz);
        long word = switch (index >>> 6) {
            case 0 -> this.low;
            case 1 -> this.mid;
            default -> this.high;
        };
        return (word & 1L << (index & 63)) != 0L;
    }

    public int count() {
        return Long.bitCount(this.low) + Long.bitCount(this.mid) + Long.bitCount(this.high);
    }
}
