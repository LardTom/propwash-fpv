package de.lardtom.propwash.rescue;

public final class WorldProbe {
    public static final double SWEEP_STEP = 0.35;
    private static final double EPS = 1e-7;
    private static final double MIN_INFLATION = 0.02;

    private WorldProbe() {
    }

    public static boolean boxBlocked(RescueWorld world, double minX, double minY, double minZ, double maxX, double maxY, double maxZ) {
        int x0 = floor(minX + EPS);
        int y0 = floor(minY + EPS);
        int z0 = floor(minZ + EPS);
        int x1 = floor(maxX - EPS);
        int y1 = floor(maxY - EPS);
        int z1 = floor(maxZ - EPS);
        for (int y = y0; y <= y1; y++) {
            for (int x = x0; x <= x1; x++) {
                for (int z = z0; z <= z1; z++) {
                    if (world.solid(x, y, z)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    public static boolean droneBlocked(RescueWorld world, double x, double bottom, double z, double halfWidth, double height,
                                       double inflateH, double inflateV) {
        double h = halfWidth + inflateH;
        return boxBlocked(world, x - h, bottom - inflateV, z - h, x + h, bottom + height + inflateV, z + h);
    }

    public static double fitInflation(RescueWorld world, double x, double bottom, double z, double halfWidth, double height,
                                      double desired) {
        double inflation = desired;
        while (inflation > MIN_INFLATION && droneBlocked(world, x, bottom, z, halfWidth, height, inflation, 0.5 * inflation)) {
            inflation *= 0.5;
        }
        return Math.max(MIN_INFLATION, inflation);
    }

    public static double sweep(RescueWorld world, double x, double bottom, double z, double halfWidth, double height,
                               double inflateH, double inflateV, double dirX, double dirY, double dirZ, double maxDistance) {
        double len = Math.sqrt(dirX * dirX + dirY * dirY + dirZ * dirZ);
        if (len < 1e-9 || maxDistance <= 0.0) {
            return Math.max(0.0, maxDistance);
        }
        double ux = dirX / len;
        double uy = dirY / len;
        double uz = dirZ / len;
        double previous = 0.0;
        double s = 0.0;
        while (s < maxDistance) {
            s = Math.min(maxDistance, s + SWEEP_STEP);
            if (droneBlocked(world, x + ux * s, bottom + uy * s, z + uz * s, halfWidth, height, inflateH, inflateV)) {
                return previous;
            }
            previous = s;
        }
        return maxDistance;
    }

    public static double sweepFitted(RescueWorld world, double x, double bottom, double z, double halfWidth, double height,
                                     double clearance, double dirX, double dirY, double dirZ, double maxDistance) {
        double inflation = fitInflation(world, x, bottom, z, halfWidth, height, clearance);
        return sweep(world, x, bottom, z, halfWidth, height, inflation, 0.5 * inflation, dirX, dirY, dirZ, maxDistance);
    }

    public static boolean skyClear(RescueWorld world, double x, double bottom, double z, double half) {
        int x0 = floor(x - half + EPS);
        int x1 = floor(x + half - EPS);
        int z0 = floor(z - half + EPS);
        int z1 = floor(z + half - EPS);
        for (int cx = x0; cx <= x1; cx++) {
            for (int cz = z0; cz <= z1; cz++) {
                int surface = world.surfaceY(cx, cz);
                if (surface == RescueWorld.UNKNOWN_SURFACE || surface > bottom + 1e-3) {
                    return false;
                }
            }
        }
        return true;
    }

    public static double maxSurface(RescueWorld world, double x, double z, double half) {
        int x0 = floor(x - half + EPS);
        int x1 = floor(x + half - EPS);
        int z0 = floor(z - half + EPS);
        int z1 = floor(z + half - EPS);
        double best = Double.NEGATIVE_INFINITY;
        for (int cx = x0; cx <= x1; cx++) {
            for (int cz = z0; cz <= z1; cz++) {
                int surface = world.surfaceY(cx, cz);
                if (surface != RescueWorld.UNKNOWN_SURFACE) {
                    best = Math.max(best, surface);
                }
            }
        }
        return best;
    }

    public static double groundBelow(RescueWorld world, double x, double bottom, double z, double half, int maxDepth) {
        int x0 = floor(x - half + EPS);
        int x1 = floor(x + half - EPS);
        int z0 = floor(z - half + EPS);
        int z1 = floor(z + half - EPS);
        int start = floor(bottom + 1e-4) - 1;
        int end = Math.max(world.minY(), start - maxDepth);
        double best = Double.NEGATIVE_INFINITY;
        for (int cx = x0; cx <= x1; cx++) {
            for (int cz = z0; cz <= z1; cz++) {
                int surface = world.surfaceY(cx, cz);
                if (surface != RescueWorld.UNKNOWN_SURFACE && surface <= bottom + 1e-4) {
                    best = Math.max(best, surface);
                    continue;
                }
                for (int y = start; y >= end; y--) {
                    if (y + 1 <= best) {
                        break;
                    }
                    if (world.solid(cx, y, cz)) {
                        best = Math.max(best, y + 1);
                        break;
                    }
                }
            }
        }
        return best;
    }

    public static int floor(double v) {
        int i = (int) v;
        return v < i ? i - 1 : i;
    }
}
