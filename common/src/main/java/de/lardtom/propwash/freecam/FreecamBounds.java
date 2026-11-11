package de.lardtom.propwash.freecam;

public final class FreecamBounds {
    public static final double FLOOR_MARGIN = 16.0;
    public static final double CEILING_MARGIN = 64.0;

    @FunctionalInterface
    public interface Columns {
        boolean loaded(int chunkX, int chunkZ);
    }

    public static final class Step {
        public double x;
        public double y;
        public double z;
        public boolean blockedX;
        public boolean blockedY;
        public boolean blockedZ;

        public boolean edge() {
            return this.blockedX || this.blockedY || this.blockedZ;
        }
    }

    private final Columns columns;
    private final double minY;
    private final double maxY;
    private final double anchorX;
    private final double anchorZ;

    public FreecamBounds(Columns columns, double worldMinY, double buildTopY, double anchorX, double anchorZ) {
        this.columns = columns;
        this.minY = worldMinY - FLOOR_MARGIN;
        this.maxY = buildTopY + CEILING_MARGIN;
        this.anchorX = anchorX;
        this.anchorZ = anchorZ;
    }

    public double minY() {
        return this.minY;
    }

    public double maxY() {
        return this.maxY;
    }

    public static int chunk(double coordinate) {
        return Math.floorDiv((int) Math.floor(coordinate), 16);
    }

    public boolean columnLoaded(double x, double z) {
        return Double.isFinite(x) && Double.isFinite(z) && this.columns.loaded(chunk(x), chunk(z));
    }

    public boolean heightAllowed(double y) {
        return y >= this.minY && y <= this.maxY;
    }

    public boolean inside(double x, double y, double z) {
        return this.heightAllowed(y) && this.columnLoaded(x, z);
    }

    public Step step(double fromX, double fromY, double fromZ, double toX, double toY, double toZ, Step out) {
        double x = fromX;
        double y = fromY;
        double z = fromZ;
        out.blockedX = false;
        out.blockedY = false;
        out.blockedZ = false;
        if (toX != fromX) {
            if (this.horizontal(fromX, fromZ, toX, fromZ, toX - fromX, fromX - this.anchorX)) {
                x = toX;
            } else {
                out.blockedX = true;
            }
        }
        if (toY != fromY) {
            if (this.vertical(fromY, toY)) {
                y = toY;
            } else {
                out.blockedY = true;
            }
        }
        if (toZ != fromZ) {
            if (this.horizontal(x, fromZ, x, toZ, toZ - fromZ, fromZ - this.anchorZ)) {
                z = toZ;
            } else {
                out.blockedZ = true;
            }
        }
        out.x = x;
        out.y = y;
        out.z = z;
        return out;
    }

    private boolean horizontal(double fromX, double fromZ, double toX, double toZ, double delta, double offset) {
        if (!Double.isFinite(toX) || !Double.isFinite(toZ)) {
            return false;
        }
        if (this.columnLoaded(toX, toZ)) {
            return true;
        }
        return !this.columnLoaded(fromX, fromZ) && delta * offset < 0.0;
    }

    private boolean vertical(double fromY, double toY) {
        if (!Double.isFinite(toY)) {
            return false;
        }
        if (this.heightAllowed(toY)) {
            return true;
        }
        if (fromY > this.maxY) {
            return toY < fromY;
        }
        if (fromY < this.minY) {
            return toY > fromY;
        }
        return false;
    }
}
