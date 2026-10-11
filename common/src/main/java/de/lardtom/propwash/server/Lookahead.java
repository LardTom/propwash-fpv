package de.lardtom.propwash.server;

public final class Lookahead {
    public static final double SECONDS = 4.0;
    public static final double MIN_SPEED = 3.0;
    public static final double SMOOTHING = 0.3;
    public static final double TELEPORT_PER_TICK = 48.0;
    public static final int MIN_RADIUS = AnchorRules.DRONE_TICKET_RADIUS + 1;

    private double lastX;
    private double lastZ;
    private double velocityX;
    private double velocityZ;
    private boolean tracking;

    public void track(double x, double z) {
        if (!Double.isFinite(x) || !Double.isFinite(z)) {
            return;
        }
        if (this.tracking) {
            double dx = x - this.lastX;
            double dz = z - this.lastZ;
            if (dx * dx + dz * dz > TELEPORT_PER_TICK * TELEPORT_PER_TICK) {
                this.velocityX = 0.0;
                this.velocityZ = 0.0;
            } else {
                this.velocityX += (dx - this.velocityX) * SMOOTHING;
                this.velocityZ += (dz - this.velocityZ) * SMOOTHING;
            }
        }
        this.lastX = x;
        this.lastZ = z;
        this.tracking = true;
    }

    public void reset() {
        this.tracking = false;
        this.velocityX = 0.0;
        this.velocityZ = 0.0;
    }

    public double speedPerSecond() {
        return Math.sqrt(this.velocityX * this.velocityX + this.velocityZ * this.velocityZ) * 20.0;
    }

    public double velocityX() {
        return this.velocityX;
    }

    public double velocityZ() {
        return this.velocityZ;
    }

    public static int aheadChunks(double speedPerSecond, int viewDistance) {
        if (!(speedPerSecond >= MIN_SPEED) || viewDistance <= 0) {
            return 0;
        }
        double chunks = Math.min(speedPerSecond * SECONDS / 16.0, 2.0 * viewDistance);
        return (int) Math.round(chunks);
    }

    public static int stable(int previousChunks, int wantedChunks) {
        if (wantedChunks <= 0) {
            return 0;
        }
        if (previousChunks > 0 && Math.abs(wantedChunks - previousChunks) <= 1) {
            return previousChunks;
        }
        return wantedChunks;
    }

    public static boolean keepCenter(long previous, long wanted) {
        return Math.abs(unpackX(previous) - unpackX(wanted)) <= 1 && Math.abs(unpackZ(previous) - unpackZ(wanted)) <= 1;
    }

    public static int radius(int viewDistance, int aheadChunks) {
        if (aheadChunks <= 0) {
            return -1;
        }
        return Math.max(MIN_RADIUS, StreamBudget.viewTicketRadius(viewDistance) - (aheadChunks + 1) / 2);
    }

    public static long center(double x, double z, double velocityX, double velocityZ, int aheadChunks) {
        double length = Math.sqrt(velocityX * velocityX + velocityZ * velocityZ);
        double offset = aheadChunks * 16.0;
        double cx = x;
        double cz = z;
        if (length > 1.0E-9 && aheadChunks > 0) {
            cx += velocityX / length * offset;
            cz += velocityZ / length * offset;
        }
        return pack(Math.floorDiv((long) Math.floor(cx), 16L), Math.floorDiv((long) Math.floor(cz), 16L));
    }

    public static long pack(long chunkX, long chunkZ) {
        return (chunkX & 0xFFFFFFFFL) | (chunkZ & 0xFFFFFFFFL) << 32;
    }

    public static int unpackX(long packed) {
        return (int) packed;
    }

    public static int unpackZ(long packed) {
        return (int) (packed >>> 32);
    }
}
