package de.lardtom.propwash.rx;

public final class RxLink {
    public static final int MIN_RANGE = 1;
    public static final int MAX_RANGE = 128;
    public static final int DEFAULT_RANGE = 64;
    public static final double BLOCK_DAMPING = 0.5;
    public static final int MAX_COUNTED = 64;
    public static final int MAX_STEPS = 1024;

    private RxLink() {
    }

    public interface Cells {
        boolean loaded(int chunkX, int chunkZ);

        boolean opaque(int x, int y, int z);
    }

    public record Result(double distance, int blocks, double effective) {
        public static final Result FAR = new Result(Double.POSITIVE_INFINITY, 0, Double.POSITIVE_INFINITY);

        public boolean within(int range) {
            return this.effective <= clampRange(range);
        }
    }

    public static int clampRange(int range) {
        return Math.clamp(range, MIN_RANGE, MAX_RANGE);
    }

    public static boolean validRange(int range) {
        return range >= MIN_RANGE && range <= MAX_RANGE;
    }

    public static boolean linked(boolean unlimited, double eyeX, double eyeY, double eyeZ, int blockX, int blockY, int blockZ, int range, Cells cells) {
        return unlimited || inRange(eyeX, eyeY, eyeZ, blockX, blockY, blockZ, range, cells);
    }

    public static boolean demands(boolean unlimited, double distanceSq, int range, double margin) {
        if (unlimited) {
            return true;
        }
        double reach = clampRange(range) + margin;
        return distanceSq <= reach * reach;
    }

    public static boolean inRange(double eyeX, double eyeY, double eyeZ, int blockX, int blockY, int blockZ, int range, Cells cells) {
        return measure(eyeX, eyeY, eyeZ, blockX, blockY, blockZ, range, cells).within(range);
    }

    public static Result measure(double eyeX, double eyeY, double eyeZ, int blockX, int blockY, int blockZ, int range, Cells cells) {
        double tx = blockX + 0.5;
        double ty = blockY + 0.5;
        double tz = blockZ + 0.5;
        double dx = tx - eyeX;
        double dy = ty - eyeY;
        double dz = tz - eyeZ;
        double distance = Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (!Double.isFinite(distance)) {
            return Result.FAR;
        }
        int limit = clampRange(range);
        if (distance > limit) {
            return new Result(distance, 0, distance);
        }
        int blocks = count(eyeX, eyeY, eyeZ, blockX, blockY, blockZ, distance, limit, cells);
        return new Result(distance, blocks, distance + BLOCK_DAMPING * blocks);
    }

    static int count(double x0, double y0, double z0, int targetX, int targetY, int targetZ, double distance, int limit, Cells cells) {
        double dx = targetX + 0.5 - x0;
        double dy = targetY + 0.5 - y0;
        double dz = targetZ + 0.5 - z0;
        if (!(distance > 1.0E-9)) {
            return 0;
        }
        int x = (int) Math.floor(x0);
        int y = (int) Math.floor(y0);
        int z = (int) Math.floor(z0);
        int stepX = Double.compare(dx, 0.0);
        int stepY = Double.compare(dy, 0.0);
        int stepZ = Double.compare(dz, 0.0);
        double deltaX = stepX != 0 ? Math.abs(1.0 / dx) : Double.POSITIVE_INFINITY;
        double deltaY = stepY != 0 ? Math.abs(1.0 / dy) : Double.POSITIVE_INFINITY;
        double deltaZ = stepZ != 0 ? Math.abs(1.0 / dz) : Double.POSITIVE_INFINITY;
        double nextX = boundary(x0, x, stepX, dx);
        double nextY = boundary(y0, y, stepY, dy);
        double nextZ = boundary(z0, z, stepZ, dz);
        int blocks = 0;
        int chunkX = Integer.MIN_VALUE;
        int chunkZ = Integer.MIN_VALUE;
        boolean loaded = false;
        for (int step = 0; step < MAX_STEPS; step++) {
            if (nextX <= nextY && nextX <= nextZ) {
                if (nextX >= 1.0) {
                    return blocks;
                }
                x += stepX;
                nextX += deltaX;
            } else if (nextY <= nextZ) {
                if (nextY >= 1.0) {
                    return blocks;
                }
                y += stepY;
                nextY += deltaY;
            } else {
                if (nextZ >= 1.0) {
                    return blocks;
                }
                z += stepZ;
                nextZ += deltaZ;
            }
            if (x == targetX && y == targetY && z == targetZ) {
                return blocks;
            }
            int cx = x >> 4;
            int cz = z >> 4;
            if (cx != chunkX || cz != chunkZ) {
                chunkX = cx;
                chunkZ = cz;
                loaded = cells.loaded(cx, cz);
            }
            if (loaded && cells.opaque(x, y, z)) {
                blocks++;
                if (blocks >= MAX_COUNTED || distance + BLOCK_DAMPING * blocks > limit) {
                    return blocks;
                }
            }
        }
        return blocks;
    }

    private static double boundary(double origin, int cell, int step, double delta) {
        if (step > 0) {
            return (cell + 1 - origin) / delta;
        }
        if (step < 0) {
            return (cell - origin) / delta;
        }
        return Double.POSITIVE_INFINITY;
    }
}
