package de.lardtom.propwash.video.link;

import java.util.HashMap;
import java.util.Map;

final class ScenarioWorld implements ObstructionTracer.World {
    static final double LEGACY_SOLID = 7.0;
    static final Medium GROUND = Medium.full(RfMaterial.EARTH);
    static final Medium LOG = Medium.full(RfMaterial.TRUNK);
    static final Medium LEAVES = new Medium(RfMaterial.FOLIAGE, null, 1.2 / LEGACY_SOLID);
    static final Medium PLANKS = Medium.full(RfMaterial.TIMBER);
    static final Medium COBBLE = Medium.full(RfMaterial.MASONRY);
    static final Medium STONE = Medium.full(RfMaterial.EARTH);
    static final Medium WINDOW = new Medium(RfMaterial.GLASS, null, 1.5 / LEGACY_SOLID);
    static final Medium SLAB = new Medium(RfMaterial.TIMBER, new double[]{0.0, 0.0, 0.0, 1.0, 0.5, 1.0}, 1.0);

    private final Map<Long, Medium> blocks = new HashMap<>();
    private final boolean ground;

    ScenarioWorld(boolean ground) {
        this.ground = ground;
    }

    static ScenarioWorld flat() {
        return new ScenarioWorld(true);
    }

    @Override
    public Medium medium(int x, int y, int z) {
        Medium placed = this.blocks.get(key(x, y, z));
        if (placed != null) {
            return placed;
        }
        return this.ground && y < 0 ? GROUND : Medium.AIR;
    }

    ScenarioWorld set(int x, int y, int z, Medium medium) {
        this.blocks.put(key(x, y, z), medium);
        return this;
    }

    ScenarioWorld oak(int x, int z, int trunkHeight) {
        for (int layer = -3; layer <= 0; layer++) {
            int radius = layer >= -1 ? 1 : 2;
            int y = trunkHeight + layer;
            for (int dx = -radius; dx <= radius; dx++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    boolean corner = Math.abs(dx) == radius && Math.abs(dz) == radius;
                    if (corner && (layer == 0 || ((x + dx * 31 + z * 17 + dz * 7 + y) & 1) == 0)) {
                        continue;
                    }
                    this.set(x + dx, y, z + dz, LEAVES);
                }
            }
        }
        for (int y = 0; y < trunkHeight; y++) {
            this.set(x, y, z, LOG);
        }
        return this;
    }

    ScenarioWorld house(int x0, int z0, int width, int depth, int wallHeight, Medium wall, boolean windows) {
        for (int x = x0; x < x0 + width; x++) {
            for (int z = z0; z < z0 + depth; z++) {
                boolean edge = x == x0 || x == x0 + width - 1 || z == z0 || z == z0 + depth - 1;
                for (int y = 0; y < wallHeight; y++) {
                    if (!edge) {
                        continue;
                    }
                    boolean corner = (x == x0 || x == x0 + width - 1) && (z == z0 || z == z0 + depth - 1);
                    boolean window = windows && !corner && y == 1 && ((x - x0) % 3 == 1 || (z - z0) % 3 == 1);
                    this.set(x, y, z, window ? WINDOW : wall);
                }
                this.set(x, wallHeight, z, PLANKS);
            }
        }
        for (int x = x0 + 1; x < x0 + width - 1; x++) {
            for (int z = z0 + 1; z < z0 + depth - 1; z++) {
                this.set(x, wallHeight + 1, z, SLAB);
            }
        }
        return this;
    }

    ScenarioWorld forest(int x0, int x1, int z0, int z1, int spacing) {
        for (int x = x0; x <= x1; x += spacing) {
            for (int z = z0; z <= z1; z += spacing) {
                int jx = Math.floorMod(x * 7 + z * 13, 3) - 1;
                int jz = Math.floorMod(x * 11 + z * 5, 3) - 1;
                int height = 4 + Math.floorMod(x * 3 + z * 5, 3);
                this.oak(x + jx, z + jz, height);
            }
        }
        return this;
    }

    double legacyDb(double x0, double y0, double z0, double x1, double y1, double z1) {
        double[] losses = new double[5];
        double dx = x1 - x0;
        double dy = y1 - y0;
        double dz = z1 - z0;
        double length = Math.sqrt(dx * dx + dy * dy + dz * dz);
        double fx = dx / length;
        double fy = dy / length;
        double fz = dz / length;
        double rx = -fz;
        double ry = 0.0;
        double rz = fx;
        double rl = Math.sqrt(rx * rx + rz * rz);
        rx /= rl;
        rz /= rl;
        double ux = ry * fz - rz * fy;
        double uy = rz * fx - rx * fz;
        double uz = rx * fy - ry * fx;
        double offset = Math.min(Math.max(0.5 * Math.sqrt(0.052 * length), 0.5), 3.0);
        double[][] dirs = {{ux, uy, uz}, {-ux, -uy, -uz}, {rx, ry, rz}, {-rx, -ry, -rz}};
        losses[0] = this.legacyRay(x0, y0, z0, x1, y1, z1);
        for (int i = 0; i < 4; i++) {
            double mx = (x0 + x1) * 0.5 + dirs[i][0] * offset;
            double my = (y0 + y1) * 0.5 + dirs[i][1] * offset;
            double mz = (z0 + z1) * 0.5 + dirs[i][2] * offset;
            double first = this.legacyRay(x0, y0, z0, mx, my, mz);
            double second = first >= 60.0 ? 0.0 : this.legacyRay(mx, my, mz, x1, y1, z1);
            losses[i + 1] = Math.min(first + second, 60.0);
        }
        double[] weights = {2.0, 1.0, 1.0, 1.0, 1.0};
        double power = 0.0;
        double sum = 0.0;
        for (int i = 0; i < 5; i++) {
            power += weights[i] * Math.pow(10.0, -Math.min(losses[i], 60.0) / 10.0);
            sum += weights[i];
        }
        return Math.min(Math.max(-10.0 * Math.log10(Math.max(power / sum, 1.0E-12)), 0.0), 60.0);
    }

    private double legacyRay(double x0, double y0, double z0, double x1, double y1, double z1) {
        double dx = x1 - x0;
        double dy = y1 - y0;
        double dz = z1 - z0;
        double length = Math.sqrt(dx * dx + dy * dy + dz * dz);
        int steps = (int) Math.ceil(length / 0.02);
        double total = 0.0;
        for (int i = 0; i < steps; i++) {
            double t = (i + 0.5) / steps;
            Medium m = this.medium((int) Math.floor(x0 + dx * t), (int) Math.floor(y0 + dy * t), (int) Math.floor(z0 + dz * t));
            if (!m.empty()) {
                total += m.reflectivity() * LEGACY_SOLID * length / steps;
            }
        }
        return Math.min(total, 60.0);
    }

    private static long key(int x, int y, int z) {
        return ((long) (x & 0x3FFFFFF) << 38) | ((long) (z & 0x3FFFFFF) << 12) | (y & 0xFFF);
    }
}
