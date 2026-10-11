package de.lardtom.propwash.bando.plan;

import java.util.Arrays;
import java.util.Optional;

public final class BandoSite {
    public static final int PLATFORM_DEPTH = 8;
    public static final float PLATFORM_CHANCE = 0.3F;
    public static final int PIER_DEPTH = 3;
    public static final int MAX_WATER_DEPTH = 40;
    public static final int PIER_SHIFT = 16;
    public static final int SHORE_BANK = 2;
    public static final int KEEP_AWAY = 12;
    public static final int LATTICE = 4;
    public static final int LEVEL_GRID = 5;
    public static final int LEVEL_PERCENT = 40;
    public static final int CHUNK = 16;
    public static final int MAX_REACH = 128;
    private static final int INSET = 2;

    public interface Sampler {
        int surface(int x, int z);

        int floor(int x, int z);

        boolean biome(int x, int y, int z);

        default boolean keepAway(int x, int y, int z) {
            return false;
        }
    }

    private BandoSite() {
    }

    public static BandoVariant coastVariant(int centerDepth) {
        return centerDepth >= PLATFORM_DEPTH ? BandoVariant.PLATFORM : BandoVariant.PIER;
    }

    public static int boxBottom(BandoPlan plan) {
        return plan.variant().family().terrain() == BandoFamily.Terrain.SHORE ? 0 : plan.ground() + 1;
    }

    public static int clearFrom(BandoPlan plan) {
        return plan.ground() + 1 + (plan.variant().family().terrain() == BandoFamily.Terrain.SHORE ? SHORE_BANK : 0);
    }

    public static boolean[] siteColumns(BandoPlan plan) {
        int g = plan.ground();
        boolean[] site = new boolean[plan.sizeX() * plan.sizeZ()];
        for (int z = 0; z < plan.sizeZ(); z++) {
            for (int x = 0; x < plan.sizeX(); x++) {
                for (int y = g; y < plan.sizeY(); y++) {
                    if (plan.mat(x, y, z) != Mat.EMPTY) {
                        site[z * plan.sizeX() + x] = true;
                        break;
                    }
                }
            }
        }
        return site;
    }

    public static int[] reachChunks(int minX, int minZ, int maxX, int maxZ, int reach) {
        return new int[]{Math.floorDiv(minX - reach, CHUNK), Math.floorDiv(minZ - reach, CHUNK), Math.floorDiv(maxX + reach, CHUNK),
                Math.floorDiv(maxZ + reach, CHUNK)};
    }

    public static int[] footprint(BandoPlan plan) {
        int g = plan.ground();
        int minX = Integer.MAX_VALUE;
        int minZ = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE;
        int maxZ = Integer.MIN_VALUE;
        for (int z = 0; z < plan.sizeZ(); z++) {
            for (int x = 0; x < plan.sizeX(); x++) {
                if (plan.mat(x, g, z).kind() == Mat.Kind.STRUCTURE || plan.mat(x, g + 1, z).kind() == Mat.Kind.STRUCTURE) {
                    minX = Math.min(minX, x);
                    minZ = Math.min(minZ, z);
                    maxX = Math.max(maxX, x);
                    maxZ = Math.max(maxZ, z);
                }
            }
        }
        if (minX > maxX) {
            return new int[]{INSET, INSET, plan.sizeX() - 1 - INSET, plan.sizeZ() - 1 - INSET};
        }
        return new int[]{minX, minZ, maxX, maxZ};
    }

    public static int level(int[] heights) {
        int[] sorted = heights.clone();
        Arrays.sort(sorted);
        return sorted[sorted.length * LEVEL_PERCENT / 100];
    }

    private static int[] heights(BandoPlan plan, BandoTransform t, Sampler s) {
        int[] f = footprint(plan);
        int[] heights = new int[LEVEL_GRID * LEVEL_GRID];
        int n = 0;
        for (int i = 0; i < LEVEL_GRID; i++) {
            int x = f[0] + (f[2] - f[0]) * i / (LEVEL_GRID - 1);
            for (int j = 0; j < LEVEL_GRID; j++) {
                int z = f[1] + (f[3] - f[1]) * j / (LEVEL_GRID - 1);
                heights[n++] = s.surface(t.worldX(x, z), t.worldZ(x, z));
            }
        }
        return heights;
    }

    public static Optional<BandoTransform> place(BandoPlan plan, int turns, int centerX, int centerZ, int seaLevel, int minY, int maxY, Sampler sampler) {
        BandoVariant variant = plan.variant();
        Optional<BandoTransform> site = switch (variant.family().terrain()) {
            case LAND -> land(plan, turns, centerX, centerZ, seaLevel, minY, maxY, sampler);
            case WETLAND -> wetland(plan, turns, centerX, centerZ, seaLevel, minY, maxY, sampler);
            case SHORE -> variant == BandoVariant.PIER
                    ? pier(plan, turns, centerX, centerZ, seaLevel, minY, maxY, sampler)
                    : platform(plan, turns, centerX, centerZ, seaLevel, minY, maxY, sampler);
        };
        return site.filter(t -> keepsAway(t, t.worldY(plan.ground()), sampler));
    }

    public static boolean keepsAway(BandoTransform t, int y, Sampler s) {
        int x0 = t.originX() - KEEP_AWAY;
        int z0 = t.originZ() - KEEP_AWAY;
        int x1 = t.originX() + t.widthX() - 1 + KEEP_AWAY;
        int z1 = t.originZ() + t.widthZ() - 1 + KEEP_AWAY;
        for (int x = x0; x < x1 + LATTICE; x += LATTICE) {
            for (int z = z0; z < z1 + LATTICE; z += LATTICE) {
                if (s.keepAway(Math.min(x, x1), y, Math.min(z, z1))) {
                    return false;
                }
            }
        }
        return true;
    }

    public static boolean below(BandoPlan plan, BandoTransform t, int top, Sampler s) {
        int[] f = footprint(plan);
        int x0 = Math.max(0, f[0] - 1);
        int x1 = Math.min(plan.sizeX() - 1, f[2] + 1);
        int[] xs = {x0, (x0 + x1) / 2, x1};
        for (int z = f[1]; z < f[3] + LATTICE; z += LATTICE) {
            int lz = Math.min(z, f[3]);
            for (int x : xs) {
                if (s.surface(t.worldX(x, lz), t.worldZ(x, lz)) > top) {
                    return false;
                }
            }
        }
        return true;
    }

    private static int[][] grid(BandoTransform t, int inset) {
        int x0 = t.originX() + inset;
        int x1 = t.originX() + t.widthX() - 1 - inset;
        int z0 = t.originZ() + inset;
        int z1 = t.originZ() + t.widthZ() - 1 - inset;
        int[] xs = {x0, (x0 + x1) / 2, x1};
        int[] zs = {z0, (z0 + z1) / 2, z1};
        int[][] points = new int[9][];
        int n = 0;
        for (int x : xs) {
            for (int z : zs) {
                points[n++] = new int[]{x, z};
            }
        }
        return points;
    }

    private static boolean fits(int originY, BandoPlan plan, int minY, int maxY) {
        return originY > minY && originY + plan.sizeY() < maxY;
    }

    private static Optional<BandoTransform> land(BandoPlan plan, int turns, int cx, int cz, int sea, int minY, int maxY, Sampler s) {
        BandoTransform t = BandoTransform.centered(plan, turns, cx, 0, cz);
        int[] heights = new int[9];
        int n = 0;
        for (int[] p : grid(t, INSET)) {
            int surface = s.surface(p[0], p[1]);
            int floor = s.floor(p[0], p[1]);
            if (surface != floor || !s.biome(p[0], surface, p[1])) {
                return Optional.empty();
            }
            heights[n++] = surface;
        }
        Arrays.sort(heights);
        if (heights[8] - heights[0] > plan.variant().family().maxSlope()) {
            return Optional.empty();
        }
        int[] footprint = heights(plan, t, s);
        int low = Integer.MAX_VALUE;
        int high = Integer.MIN_VALUE;
        for (int h : footprint) {
            low = Math.min(low, h);
            high = Math.max(high, h);
        }
        if (high - low > plan.variant().family().maxSlope()) {
            return Optional.empty();
        }
        int site = level(footprint);
        if (site < sea) {
            return Optional.empty();
        }
        int originY = site - plan.ground();
        if (!fits(originY, plan, minY, maxY)) {
            return Optional.empty();
        }
        return Optional.of(new BandoTransform(t.originX(), originY, t.originZ(), t.turns(), plan.sizeX(), plan.sizeZ()));
    }

    private static Optional<BandoTransform> wetland(BandoPlan plan, int turns, int cx, int cz, int sea, int minY, int maxY, Sampler s) {
        BandoTransform t = BandoTransform.centered(plan, turns, cx, 0, cz);
        int low = Integer.MAX_VALUE;
        int high = Integer.MIN_VALUE;
        for (int[] p : grid(t, INSET)) {
            int floor = s.floor(p[0], p[1]);
            if (!s.biome(p[0], Math.max(floor, sea - 1), p[1])) {
                return Optional.empty();
            }
            low = Math.min(low, floor);
            high = Math.max(high, floor);
        }
        if (high > sea + 2 || low < sea - 6 || high - low > plan.variant().family().maxSlope()) {
            return Optional.empty();
        }
        int originY = sea - 3 - plan.ground();
        if (!fits(originY, plan, minY, maxY)) {
            return Optional.empty();
        }
        return Optional.of(new BandoTransform(t.originX(), originY, t.originZ(), t.turns(), plan.sizeX(), plan.sizeZ()));
    }

    private static Optional<BandoTransform> pier(BandoPlan plan, int turns, int cx, int cz, int sea, int minY, int maxY, Sampler s) {
        for (int k = 0; k < 4; k++) {
            int along = Meta.rotateFacing(Meta.SOUTH, turns + k);
            for (int shift = 0; shift <= PIER_SHIFT; shift += 2) {
                for (int sign = -1; sign <= 1; sign += 2) {
                    if (shift == 0 && sign > 0) {
                        continue;
                    }
                    int ox = cx + Meta.dx(along) * shift * sign;
                    int oz = cz + Meta.dz(along) * shift * sign;
                    Optional<BandoTransform> site = pierAt(plan, BandoTransform.centered(plan, turns + k, ox, 0, oz), sea, minY, maxY, s);
                    if (site.isPresent()) {
                        return site;
                    }
                }
            }
        }
        return Optional.empty();
    }

    private static Optional<BandoTransform> pierAt(BandoPlan plan, BandoTransform t, int sea, int minY, int maxY, Sampler s) {
        int water = sea - 1;
        int mid = plan.sizeX() / 2;
        int landX = t.worldX(mid, 2);
        int landZ = t.worldZ(mid, 2);
        int land = s.surface(landX, landZ);
        if (land != s.floor(landX, landZ) || land < water || land > sea + 1 || !s.biome(landX, land, landZ)) {
            return Optional.empty();
        }
        int shoreX = t.worldX(mid, CoastBandos.PIER_LAND + 3);
        int shoreZ = t.worldZ(mid, CoastBandos.PIER_LAND + 3);
        if (s.surface(shoreX, shoreZ) != water || s.floor(shoreX, shoreZ) >= water) {
            return Optional.empty();
        }
        int endX = t.worldX(mid, plan.sizeZ() - 2);
        int endZ = t.worldZ(mid, plan.sizeZ() - 2);
        int endFloor = s.floor(endX, endZ);
        if (s.surface(endX, endZ) != water || water - endFloor < PIER_DEPTH || water - endFloor > MAX_WATER_DEPTH || !s.biome(endX, water, endZ)) {
            return Optional.empty();
        }
        int halfX = t.worldX(mid, plan.sizeZ() / 2);
        int halfZ = t.worldZ(mid, plan.sizeZ() / 2);
        if (s.surface(halfX, halfZ) != water || s.floor(halfX, halfZ) >= water || !centered(t, water, s)) {
            return Optional.empty();
        }
        int originY = water - plan.ground();
        if (!fits(originY, plan, minY, maxY) || !below(plan, t, water + SHORE_BANK, s)) {
            return Optional.empty();
        }
        return Optional.of(new BandoTransform(t.originX(), originY, t.originZ(), t.turns(), plan.sizeX(), plan.sizeZ()));
    }

    public static boolean centered(BandoTransform t, int y, Sampler s) {
        int cx = t.originX() + t.widthX() / 2;
        int cz = t.originZ() + t.widthZ() / 2;
        for (int dx = -2; dx <= 2; dx += 4) {
            for (int dy = -2; dy <= 2; dy += 4) {
                for (int dz = -2; dz <= 2; dz += 4) {
                    if (!s.biome(cx + dx, y + dy, cz + dz)) {
                        return false;
                    }
                }
            }
        }
        return true;
    }

    private static Optional<BandoTransform> platform(BandoPlan plan, int turns, int cx, int cz, int sea, int minY, int maxY, Sampler s) {
        int water = sea - 1;
        BandoTransform t = BandoTransform.centered(plan, turns, cx, 0, cz);
        for (int[] p : grid(t, 4)) {
            int surface = s.surface(p[0], p[1]);
            int floor = s.floor(p[0], p[1]);
            if (surface != water || water - floor < PLATFORM_DEPTH - 2 || water - floor > MAX_WATER_DEPTH || !s.biome(p[0], water, p[1])) {
                return Optional.empty();
            }
        }
        if (!centered(t, water, s)) {
            return Optional.empty();
        }
        int originY = water - plan.ground();
        if (!fits(originY, plan, minY, maxY) || !below(plan, t, water + SHORE_BANK, s)) {
            return Optional.empty();
        }
        return Optional.of(new BandoTransform(t.originX(), originY, t.originZ(), t.turns(), plan.sizeX(), plan.sizeZ()));
    }
}
