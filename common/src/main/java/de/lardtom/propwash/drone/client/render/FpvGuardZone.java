package de.lardtom.propwash.drone.client.render;

import org.joml.Vector3fc;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

public final class FpvGuardZone {
    public static final float INNER_FACTOR = 0.97F;
    public static final float OUTER_MARGIN_MM = 6.0F;
    public static final float OUTER_MARGIN_FACTOR = 0.2F;
    public static final int QUAD_FLOATS = 12;
    public static final int MIN_COPIES = 5;
    public static final float SIGNATURE_TOLERANCE_MM = 0.02F;

    private final float[] axisX;
    private final float[] axisZ;
    private final float inner;
    private final float outer;

    public FpvGuardZone(float[] axisX, float[] axisZ, float propRadiusMm) {
        if (axisX.length != axisZ.length || axisX.length == 0) {
            throw new IllegalArgumentException("Achsen ungleich lang");
        }
        float radius = Math.max(1.0F, propRadiusMm);
        this.axisX = axisX.clone();
        this.axisZ = axisZ.clone();
        this.inner = radius * INNER_FACTOR;
        this.outer = radius + Math.max(OUTER_MARGIN_MM, OUTER_MARGIN_FACTOR * radius);
    }

    public static @Nullable FpvGuardZone of(boolean ducted, Vector3fc[] motorsMm, float maxPropMm) {
        if (!ducted || motorsMm.length == 0 || !(maxPropMm > 0.0F)) {
            return null;
        }
        float[] x = new float[motorsMm.length];
        float[] z = new float[motorsMm.length];
        for (int i = 0; i < motorsMm.length; i++) {
            x[i] = motorsMm[i].x();
            z[i] = motorsMm[i].z();
        }
        return new FpvGuardZone(x, z, maxPropMm * 0.5F);
    }

    public boolean contains(float[] quad) {
        return this.contains(quad, 0, quad.length / 3);
    }

    public boolean contains(float[] positions, int offset, int vertices) {
        if (vertices <= 0) {
            return false;
        }
        for (int axis = 0; axis < this.axisX.length; axis++) {
            if (this.around(axis, positions, offset, vertices)) {
                return true;
            }
        }
        return false;
    }

    public boolean[] select(float[] quads) {
        int count = quads.length / QUAD_FLOATS;
        boolean[] selected = new boolean[count];
        List<Cluster> clusters = new ArrayList<>();
        for (int q = 0; q < count; q++) {
            int offset = q * QUAD_FLOATS;
            int axis = this.axisOf(quads, offset, 4);
            if (axis < 0) {
                continue;
            }
            float[] signature = this.signature(axis, quads, offset);
            Cluster cluster = null;
            for (Cluster candidate : clusters) {
                if (candidate.axis == axis && candidate.matches(signature)) {
                    cluster = candidate;
                    break;
                }
            }
            if (cluster == null) {
                cluster = new Cluster(axis, signature);
                clusters.add(cluster);
            }
            cluster.members.add(q);
        }
        for (Cluster cluster : clusters) {
            if (cluster.members.size() >= MIN_COPIES) {
                for (int q : cluster.members) {
                    selected[q] = true;
                }
            }
        }
        return selected;
    }

    private int axisOf(float[] positions, int offset, int vertices) {
        for (int axis = 0; axis < this.axisX.length; axis++) {
            if (this.around(axis, positions, offset, vertices)) {
                return axis;
            }
        }
        return -1;
    }

    private float[] signature(int axis, float[] positions, int offset) {
        float rMin = Float.POSITIVE_INFINITY;
        float rMax = Float.NEGATIVE_INFINITY;
        float yMin = Float.POSITIVE_INFINITY;
        float yMax = Float.NEGATIVE_INFINITY;
        for (int v = 0; v < 4; v++) {
            int p = offset + v * 3;
            float dx = positions[p] - this.axisX[axis];
            float dz = positions[p + 2] - this.axisZ[axis];
            float r = (float) Math.sqrt(dx * dx + dz * dz);
            rMin = Math.min(rMin, r);
            rMax = Math.max(rMax, r);
            yMin = Math.min(yMin, positions[p + 1]);
            yMax = Math.max(yMax, positions[p + 1]);
        }
        return new float[]{rMin, rMax, yMin, yMax};
    }

    private boolean around(int axis, float[] positions, int offset, int vertices) {
        float inner2 = this.inner * this.inner;
        float outer2 = this.outer * this.outer;
        for (int v = 0; v < vertices; v++) {
            int p = offset + v * 3;
            float x = positions[p];
            float z = positions[p + 2];
            if (!Float.isFinite(x) || !Float.isFinite(z)) {
                return false;
            }
            float dx = x - this.axisX[axis];
            float dz = z - this.axisZ[axis];
            float d2 = dx * dx + dz * dz;
            if (d2 < inner2 || d2 > outer2) {
                return false;
            }
        }
        return true;
    }

    public float innerRadiusMm() {
        return this.inner;
    }

    public float outerRadiusMm() {
        return this.outer;
    }

    public int axes() {
        return this.axisX.length;
    }

    private static final class Cluster {
        final int axis;
        final float[] signature;
        final List<Integer> members = new ArrayList<>();

        Cluster(int axis, float[] signature) {
            this.axis = axis;
            this.signature = signature;
        }

        boolean matches(float[] other) {
            for (int i = 0; i < this.signature.length; i++) {
                if (Math.abs(this.signature[i] - other[i]) > SIGNATURE_TOLERANCE_MM) {
                    return false;
                }
            }
            return true;
        }
    }
}
