package de.lardtom.propwash.drone.client.render;

import net.minecraft.client.renderer.rendertype.RenderType;

import java.util.List;

public final class FpvGuardMesh {
    public static final FpvGuardMesh EMPTY = new FpvGuardMesh(List.of());

    private final List<Batch> batches;
    private final float[] points;
    private final int quads;

    FpvGuardMesh(List<Batch> batches) {
        this.batches = List.copyOf(batches);
        int total = 0;
        for (Batch batch : this.batches) {
            total += batch.quads();
        }
        this.quads = total;
        this.points = new float[total * FpvGuardZone.QUAD_FLOATS];
        int at = 0;
        for (Batch batch : this.batches) {
            System.arraycopy(batch.xyz(), 0, this.points, at, batch.xyz().length);
            at += batch.xyz().length;
        }
    }

    public boolean isEmpty() {
        return this.quads == 0;
    }

    public int quads() {
        return this.quads;
    }

    public List<Batch> batches() {
        return this.batches;
    }

    public float[] pointsMm() {
        return this.points.clone();
    }

    float[] points() {
        return this.points;
    }

    public record Batch(RenderType type, float[] xyz, float[] uv, float[] normals, int[] argb) {
        public int quads() {
            return this.xyz.length / FpvGuardZone.QUAD_FLOATS;
        }
    }
}
