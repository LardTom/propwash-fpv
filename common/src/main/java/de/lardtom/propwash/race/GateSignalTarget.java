package de.lardtom.propwash.race;

public record GateSignalTarget(int x, int y, int z, int face) {
    public static final int DOWN = 0;
    public static final int UP = 1;
    public static final int NORTH = 2;
    public static final int SOUTH = 3;
    public static final int WEST = 4;
    public static final int EAST = 5;
    private static final double INSIDE = 1.0 / 32.0;

    public static int stepX(int face) {
        return face == WEST ? -1 : face == EAST ? 1 : 0;
    }

    public static int stepY(int face) {
        return face == DOWN ? -1 : face == UP ? 1 : 0;
    }

    public static int stepZ(int face) {
        return face == NORTH ? -1 : face == SOUTH ? 1 : 0;
    }

    public int emitterX() {
        return this.x + stepX(this.face);
    }

    public int emitterY() {
        return this.y + stepY(this.face);
    }

    public int emitterZ() {
        return this.z + stepZ(this.face);
    }

    public static int horizontal(double dx, double dz) {
        if (Math.abs(dx) > Math.abs(dz)) {
            return dx > 0.0 ? EAST : WEST;
        }
        return dz > 0.0 ? SOUTH : NORTH;
    }

    public static int outward(GateMount.Surface surface, double facingX, double facingZ) {
        return switch (surface) {
            case FLOOR -> UP;
            case CEILING -> DOWN;
            case WALL -> horizontal(facingX, facingZ);
        };
    }

    public static GateSignalTarget mounted(GateMount.Surface surface, double px, double py, double pz, double facingX, double facingZ) {
        int face = outward(surface, facingX, facingZ);
        return new GateSignalTarget((int) Math.floor(px - stepX(face) * INSIDE), (int) Math.floor(py - stepY(face) * INSIDE),
                (int) Math.floor(pz - stepZ(face) * INSIDE), face);
    }

    public static GateSignalTarget of(GateGeometry geometry) {
        FreeformRegion region = geometry.region();
        if (region != null) {
            return freeform(region, FreeformBlocks.plane(geometry), FreeformBlocks.minA(geometry), FreeformBlocks.minB(geometry),
                    geometry.yawDegrees());
        }
        double[] point = GatePlacement.mountPoint(geometry);
        GateMount mount = geometry.mount();
        if (mount == GateMount.FLOOR_FLAT || mount == GateMount.CEILING_FLAT) {
            double bar = geometry.frame() * 0.5;
            return mounted(mount.surface(), geometry.worldX(0.0, bar, 0.0), point[1], geometry.worldZ(0.0, bar, 0.0), geometry.facingX(),
                    geometry.facingZ());
        }
        return mounted(mount.surface(), point[0], point[1], point[2], geometry.facingX(), geometry.facingZ());
    }

    public static GateSignalTarget freeform(FreeformRegion region, int plane, int minA, int minB) {
        return freeform(region, plane, minA, minB, 0.0F);
    }

    public static GateSignalTarget freeform(FreeformRegion region, int plane, int minA, int minB, float yaw) {
        if (region.horizontal()) {
            return lying(region, plane, minA, minB, Math.round(GateGeometry.snapYaw(Float.isFinite(yaw) ? yaw : 0.0F) / 90.0F) & 3);
        }
        int face = UP;
        int[] cells = new int[region.width()];
        for (int b = 0; b < region.height(); b++) {
            int count = 0;
            for (int a = 0; a < region.width(); a++) {
                if (region.contains(a, b)) {
                    cells[count++] = a;
                }
            }
            if (count > 0) {
                int[] support = FreeformBlocks.world(region.axis(), plane, minA + cells[(count - 1) / 2], minB + b - 1);
                return new GateSignalTarget(support[0], support[1], support[2], face);
            }
        }
        int[] seed = FreeformBlocks.world(region.axis(), plane, minA + region.seedA(), minB + region.seedB() - 1);
        return new GateSignalTarget(seed[0], seed[1], seed[2], face);
    }

    private static GateSignalTarget lying(FreeformRegion region, int plane, int minA, int minB, int quarter) {
        int forwardA = quarter == 1 ? -1 : quarter == 3 ? 1 : 0;
        int forwardB = quarter == 0 ? 1 : quarter == 2 ? -1 : 0;
        int face = horizontal(forwardA, forwardB);
        boolean rows = forwardA == 0;
        int lines = rows ? region.height() : region.width();
        int length = rows ? region.width() : region.height();
        boolean lineUp = rows ? forwardB > 0 : forwardA > 0;
        boolean alongUp = rows ? forwardB > 0 : forwardA < 0;
        int[] cells = new int[length];
        for (int step = 0; step < lines; step++) {
            int line = lineUp ? step : lines - 1 - step;
            int count = 0;
            for (int i = 0; i < length; i++) {
                int along = alongUp ? i : length - 1 - i;
                if (rows ? region.contains(along, line) : region.contains(line, along)) {
                    cells[count++] = along;
                }
            }
            if (count > 0) {
                int along = cells[(count - 1) / 2];
                int a = rows ? along : line;
                int b = rows ? line : along;
                return new GateSignalTarget(minA + a - forwardA, plane, minB + b - forwardB, face);
            }
        }
        return new GateSignalTarget(minA + region.seedA() - forwardA, plane, minB + region.seedB() - forwardB, face);
    }
}
