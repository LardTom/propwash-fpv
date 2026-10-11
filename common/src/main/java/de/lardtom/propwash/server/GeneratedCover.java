package de.lardtom.propwash.server;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public final class GeneratedCover {
    public static final int MAX_SQUARES = 256;
    private static final int SEARCH = 16;

    private GeneratedCover() {
    }

    public static List<Square> cover(GeneratedArea.Lookup lookup, int centerX, int centerZ, int radius) {
        if (radius < 1) {
            return List.of();
        }
        int outer = 2 * radius + 1;
        boolean[] stored = new boolean[outer * outer];
        for (int z = 0; z < outer; z++) {
            for (int x = 0; x < outer; x++) {
                stored[z * outer + x] = lookup.status(centerX - radius + x, centerZ - radius + z) == GeneratedArea.Status.GENERATED;
            }
        }
        int inner = radius - 1;
        int side = 2 * inner + 1;
        int originX = centerX - inner;
        int originZ = centerZ - inner;
        boolean[] generated = new boolean[side * side];
        for (int z = 0; z < side; z++) {
            for (int x = 0; x < side; x++) {
                boolean all = true;
                for (int dz = 0; dz <= 2 && all; dz++) {
                    for (int dx = 0; dx <= 2 && all; dx++) {
                        all = stored[(z + dz) * outer + x + dx];
                    }
                }
                generated[z * side + x] = all;
            }
        }
        int[] size = new int[side * side];
        for (int z = side - 1; z >= 0; z--) {
            for (int x = side - 1; x >= 0; x--) {
                int i = z * side + x;
                if (!generated[i]) {
                    size[i] = 0;
                } else if (x == side - 1 || z == side - 1) {
                    size[i] = 1;
                } else {
                    size[i] = 1 + Math.min(size[i + 1], Math.min(size[i + side], size[i + side + 1]));
                }
            }
        }
        boolean[] covered = new boolean[side * side];
        List<Square> squares = new ArrayList<>();
        for (int z = 0; z < side; z++) {
            for (int x = 0; x < side; x++) {
                int i = z * side + x;
                if (!generated[i] || covered[i]) {
                    continue;
                }
                int bestSide = 1;
                int bestX = x;
                int bestZ = z;
                for (int tz = z; tz >= Math.max(0, z - SEARCH); tz--) {
                    for (int tx = x; tx >= Math.max(0, x - SEARCH); tx--) {
                        int s = odd(size[tz * side + tx]);
                        if (s > bestSide && s > Math.max(x - tx, z - tz)) {
                            bestSide = s;
                            bestX = tx;
                            bestZ = tz;
                        }
                    }
                }
                for (int dz = 0; dz < bestSide; dz++) {
                    for (int dx = 0; dx < bestSide; dx++) {
                        covered[(bestZ + dz) * side + bestX + dx] = true;
                    }
                }
                int half = (bestSide - 1) / 2;
                squares.add(new Square(originX + bestX + half, originZ + bestZ + half, half + 1));
            }
        }
        if (squares.size() > MAX_SQUARES) {
            squares.sort(Comparator.comparingLong(square -> square.distanceSquared(centerX, centerZ)));
            return List.copyOf(squares.subList(0, MAX_SQUARES));
        }
        return List.copyOf(squares);
    }

    private static int odd(int side) {
        return (side & 1) == 0 ? side - 1 : side;
    }

    public static boolean interior(GeneratedArea.Lookup lookup, int chunkX, int chunkZ) {
        for (int dz = -1; dz <= 1; dz++) {
            for (int dx = -1; dx <= 1; dx++) {
                if (lookup.status(chunkX + dx, chunkZ + dz) != GeneratedArea.Status.GENERATED) {
                    return false;
                }
            }
        }
        return true;
    }

    public static int coveredChunks(List<Square> squares) {
        int total = 0;
        for (Square square : squares) {
            int side = 2 * square.radius() + 1;
            total += side * side;
        }
        return total;
    }

    public record Square(int centerX, int centerZ, int radius) {
        public boolean contains(int chunkX, int chunkZ) {
            return Math.abs(chunkX - this.centerX) <= this.radius && Math.abs(chunkZ - this.centerZ) <= this.radius;
        }

        public boolean ticks(int chunkX, int chunkZ) {
            return Math.abs(chunkX - this.centerX) < this.radius && Math.abs(chunkZ - this.centerZ) < this.radius;
        }

        long distanceSquared(int x, int z) {
            long dx = this.centerX - x;
            long dz = this.centerZ - z;
            return dx * dx + dz * dz;
        }
    }
}
