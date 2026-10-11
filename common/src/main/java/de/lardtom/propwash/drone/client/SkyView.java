package de.lardtom.propwash.drone.client;

import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;

final class SkyView {
    static final long INTERVAL_NANOS = 500_000_000L;
    static final double RAY_LENGTH = 96.0;
    static final int AZIMUTHS = 8;
    static final double[] ELEVATIONS_DEG = {90.0, 60.0, 30.0};
    static final double[] WEIGHTS = {1.5, 1.0, 0.6};

    private long nextNanos;
    private double value = 1.0;
    private boolean primed;

    void reset() {
        this.primed = false;
        this.nextNanos = 0L;
        this.value = 1.0;
    }

    double sample(Level level, double x, double y, double z, long now) {
        if (!this.primed || now - this.nextNanos >= 0L) {
            this.value = measure(level, x, y, z);
            this.nextNanos = now + INTERVAL_NANOS;
            this.primed = true;
        }
        return this.value;
    }

    static double measure(Level level, double x, double y, double z) {
        if (level.dimensionType().hasCeiling()) {
            return 0.0;
        }
        if (y > level.getMaxY() + 1) {
            return 1.0;
        }
        Vec3 from = new Vec3(x, y, z);
        double open = 0.0;
        double total = 0.0;
        for (int e = 0; e < ELEVATIONS_DEG.length; e++) {
            double elevation = Math.toRadians(ELEVATIONS_DEG[e]);
            int count = ELEVATIONS_DEG[e] >= 90.0 ? 1 : AZIMUTHS;
            for (int a = 0; a < count; a++) {
                double azimuth = (a + 0.5 * (e % 2)) * 2.0 * Math.PI / AZIMUTHS;
                double horizontal = Math.cos(elevation);
                Vec3 dir = new Vec3(horizontal * Math.sin(azimuth), Math.sin(elevation), horizontal * Math.cos(azimuth));
                total += WEIGHTS[e];
                if (clear(level, from, from.add(dir.scale(RAY_LENGTH)))) {
                    open += WEIGHTS[e];
                }
            }
        }
        return total > 0.0 ? open / total : 1.0;
    }

    private static boolean clear(Level level, Vec3 from, Vec3 to) {
        BlockHitResult hit = level.clip(new ClipContext(from, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.ANY, CollisionContext.empty()));
        return hit.getType() == HitResult.Type.MISS;
    }
}
