package de.lardtom.propwash.drone.client.render;

import org.joml.Vector3fc;
import org.jspecify.annotations.Nullable;

public final class FpvArmZone implements FpvQuadSelector {
    public static final float TOP_MARGIN_MM = 0.6F;
    public static final float CENTRE_TOLERANCE_MM = 3.0F;
    public static final float MIN_REACH = 0.5F;
    public static final float ATTACH_RADIUS = 0.25F;

    private final float[] motorX;
    private final float[] motorZ;
    private final float top;
    private final float reach;
    private final @Nullable FpvGuardZone guards;

    public FpvArmZone(Vector3fc[] motorsMm, @Nullable FpvGuardZone guards) {
        if (motorsMm.length == 0) {
            throw new IllegalArgumentException("Rahmen ohne Motoren");
        }
        this.motorX = new float[motorsMm.length];
        this.motorZ = new float[motorsMm.length];
        float highest = Float.NEGATIVE_INFINITY;
        float narrowest = Float.POSITIVE_INFINITY;
        for (int i = 0; i < motorsMm.length; i++) {
            this.motorX[i] = motorsMm[i].x();
            this.motorZ[i] = motorsMm[i].z();
            highest = Math.max(highest, motorsMm[i].y());
            narrowest = Math.min(narrowest, Math.abs(motorsMm[i].x()));
        }
        this.top = highest + TOP_MARGIN_MM;
        this.reach = narrowest * MIN_REACH;
        this.guards = guards;
    }

    @Override
    public boolean contains(float[] quad) {
        return this.contains(quad, 0, quad.length / 3);
    }

    private boolean contains(float[] positions, int offset, int vertices) {
        if (vertices <= 0) {
            return false;
        }
        float highest = Float.NEGATIVE_INFINITY;
        float minX = Float.POSITIVE_INFINITY;
        float maxX = Float.NEGATIVE_INFINITY;
        for (int v = 0; v < vertices; v++) {
            int p = offset + v * 3;
            float x = positions[p];
            float y = positions[p + 1];
            if (!Float.isFinite(x) || !Float.isFinite(y) || !Float.isFinite(positions[p + 2])) {
                return false;
            }
            highest = Math.max(highest, y);
            minX = Math.min(minX, x);
            maxX = Math.max(maxX, x);
        }
        boolean central = minX < -CENTRE_TOLERANCE_MM && maxX > CENTRE_TOLERANCE_MM;
        return highest <= this.top && !central && Math.max(Math.abs(minX), Math.abs(maxX)) >= this.reach;
    }

    @Override
    public boolean[] select(float[] quads) {
        boolean[] rings = this.guards != null ? this.guards.select(quads) : null;
        boolean[] selected = new boolean[quads.length / FpvGuardZone.QUAD_FLOATS];
        for (int q = 0; q < selected.length; q++) {
            selected[q] = (rings == null || !rings[q]) && this.contains(quads, q * FpvGuardZone.QUAD_FLOATS, 4);
        }
        return selected;
    }

    public boolean[] attached(float[] quads) {
        boolean[] attached = new boolean[this.motorX.length];
        for (int motor = 0; motor < attached.length; motor++) {
            float radius = ATTACH_RADIUS * (float) Math.hypot(this.motorX[motor], this.motorZ[motor]);
            float radius2 = radius * radius;
            for (int p = 0; p + 2 < quads.length && !attached[motor]; p += 3) {
                float dx = quads[p] - this.motorX[motor];
                float dz = quads[p + 2] - this.motorZ[motor];
                attached[motor] = dx * dx + dz * dz <= radius2;
            }
        }
        return attached;
    }

    public float topMm() {
        return this.top;
    }

    public float reachMm() {
        return this.reach;
    }
}
