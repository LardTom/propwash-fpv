package de.lardtom.propwash.trick.client;

import de.lardtom.propwash.trick.ProbeAxes;
import de.lardtom.propwash.trick.TrickSample;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;

final class ClearanceProbe {
    static final double RANGE = 3.0;

    private final double[] axes = new double[TrickSample.PROBES * 3];

    ClearanceProbe() {
    }

    void probe(Level level, TrickSample s) {
        s.clearNearby();
        if (!s.armed) {
            return;
        }
        ProbeAxes.compute(s, this.axes);
        Vec3 from = new Vec3(s.x, s.y, s.z);
        for (int i = 0; i < TrickSample.PROBES; i++) {
            double dx = this.axes[i * 3];
            double dy = this.axes[i * 3 + 1];
            double dz = this.axes[i * 3 + 2];
            Vec3 to = new Vec3(s.x + dx * RANGE, s.y + dy * RANGE, s.z + dz * RANGE);
            BlockHitResult hit = level.clip(new ClipContext(from, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, CollisionContext.empty()));
            s.clearance[i] = hit.getType() == HitResult.Type.MISS ? Double.POSITIVE_INFINITY : hit.getLocation().distanceTo(from);
            s.clearanceDirY[i] = dy;
        }
        s.clearanceKnown = true;
    }
}
