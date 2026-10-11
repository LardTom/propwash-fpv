package de.lardtom.propwash.drone.client.render;

import de.lardtom.propwash.build.FrameType;
import net.minecraft.client.model.geom.builders.UVPair;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.resources.model.geometry.BakedQuad;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector3fc;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static de.lardtom.propwash.drone.client.render.DroneVisualScale.MM_TO_UNITS;

public final class FpvGuards {
    private static final int MAX_CACHED = 32;
    private static final int WHITE = 0xFFFFFFFF;
    private static final Map<Key, FpvGuardMesh> MESHES = new HashMap<>();
    private static int generation = Integer.MIN_VALUE;

    private FpvGuards() {
    }

    public static @Nullable FpvGuardZone zone(FrameType frame, DroneLayout layout) {
        Vector3fc[] motors = new Vector3fc[DroneLayout.SLOTS];
        for (int slot = 0; slot < DroneLayout.SLOTS; slot++) {
            motors[slot] = layout.motor(slot);
        }
        return FpvGuardZone.of(frame.ducted, motors, frame.maxPropMm);
    }

    public static FpvGuardMesh mesh(DroneRenderState.PartSlot frame, FrameType type) {
        return mesh(frame.useItem ? frame.item : null, frame.solid, type);
    }

    public static FpvGuardMesh mesh(DroneAssembly.Part frame, FrameType type) {
        return mesh(frame.item(), frame.solid(), type);
    }

    static synchronized FpvGuardMesh mesh(@Nullable ItemStackRenderState item, Shape solid, FrameType type) {
        if (!type.ducted) {
            return FpvGuardMesh.EMPTY;
        }
        DroneRenderResources res = DroneVisuals.resources();
        if (res.generation() != generation) {
            generation = res.generation();
            MESHES.clear();
        }
        Key key = new Key(item != null ? item : solid, type);
        FpvGuardMesh mesh = MESHES.get(key);
        if (mesh == null) {
            FpvGuardZone zone = zone(type, res.layout(type));
            mesh = zone == null ? FpvGuardMesh.EMPTY : item != null ? fromItem(item, zone) : fromShape(solid, zone);
            if (MESHES.size() >= MAX_CACHED) {
                MESHES.clear();
            }
            MESHES.put(key, mesh);
        }
        return mesh;
    }

    static FpvGuardMesh fromItem(ItemStackRenderState item, FpvGuardZone zone) {
        QuadCapture.Result captured = QuadCapture.of(item);
        if (captured.special()) {
            return FpvGuardMesh.EMPTY;
        }
        List<Candidate> candidates = new ArrayList<>();
        Vector3f p = new Vector3f();
        Vector3f n = new Vector3f();
        for (QuadCapture.Layer layer : captured.layers()) {
            Matrix4f pose = layer.pose();
            int[] tints = layer.tints();
            for (BakedQuad baked : layer.quads().all()) {
                float[] quad = new float[FpvGuardZone.QUAD_FLOATS];
                for (int v = 0; v < 4; v++) {
                    pose.transformPosition(baked.position(v), p);
                    quad[v * 3] = p.x / MM_TO_UNITS;
                    quad[v * 3 + 1] = p.y / MM_TO_UNITS;
                    quad[v * 3 + 2] = p.z / MM_TO_UNITS;
                }
                if (!zone.contains(quad)) {
                    continue;
                }
                BakedQuad.MaterialInfo material = baked.materialInfo();
                int index = material.tintIndex();
                int color = material.isTinted() && index >= 0 && index < tints.length ? tints[index] : WHITE;
                pose.transformDirection(baked.direction().getUnitVec3f(), n);
                if (n.lengthSquared() > 1.0E-12F) {
                    n.normalize();
                }
                float[] uv = new float[8];
                for (int v = 0; v < 4; v++) {
                    long packed = baked.packedUV(v);
                    uv[v * 2] = UVPair.unpackU(packed);
                    uv[v * 2 + 1] = UVPair.unpackV(packed);
                }
                candidates.add(new Candidate(material.itemRenderType(), quad, uv, new int[]{color, color, color, color}, new Vector3f(n)));
            }
        }
        return build(candidates, zone);
    }

    static FpvGuardMesh fromShape(Shape shape, FpvGuardZone zone) {
        if (shape.isEmpty()) {
            return FpvGuardMesh.EMPTY;
        }
        List<Candidate> candidates = new ArrayList<>();
        Vector3f p = new Vector3f();
        RenderType type = DroneVisuals.solidType();
        float[] uv = {0.5F, 0.5F, 0.5F, 0.5F, 0.5F, 0.5F, 0.5F, 0.5F};
        for (int v = 0; v + 3 < shape.vertexCount(); v += 4) {
            float[] quad = new float[FpvGuardZone.QUAD_FLOATS];
            for (int k = 0; k < 4; k++) {
                shape.position(v + k, p);
                quad[k * 3] = p.x / MM_TO_UNITS;
                quad[k * 3 + 1] = p.y / MM_TO_UNITS;
                quad[k * 3 + 2] = p.z / MM_TO_UNITS;
            }
            if (!zone.contains(quad)) {
                continue;
            }
            int[] colors = {shape.color(v), shape.color(v + 1), shape.color(v + 2), shape.color(v + 3)};
            candidates.add(new Candidate(type, quad, uv, colors, new Vector3f(shape.normal(v, new Vector3f()))));
        }
        return build(candidates, zone);
    }

    private static FpvGuardMesh build(List<Candidate> candidates, FpvGuardZone zone) {
        if (candidates.isEmpty()) {
            return FpvGuardMesh.EMPTY;
        }
        float[] all = new float[candidates.size() * FpvGuardZone.QUAD_FLOATS];
        for (int i = 0; i < candidates.size(); i++) {
            System.arraycopy(candidates.get(i).quad(), 0, all, i * FpvGuardZone.QUAD_FLOATS, FpvGuardZone.QUAD_FLOATS);
        }
        boolean[] selected = zone.select(all);
        Map<RenderType, Builder> batches = new LinkedHashMap<>();
        for (int i = 0; i < selected.length; i++) {
            if (!selected[i]) {
                continue;
            }
            Candidate candidate = candidates.get(i);
            Builder builder = batches.computeIfAbsent(candidate.type(), type -> new Builder());
            builder.add(candidate.quad(), candidate.normal());
            for (int v = 0; v < 4; v++) {
                builder.uv(candidate.uv()[v * 2], candidate.uv()[v * 2 + 1]);
                builder.color(candidate.argb()[v]);
            }
        }
        return build(batches);
    }

    private static FpvGuardMesh build(Map<RenderType, Builder> batches) {
        if (batches.isEmpty()) {
            return FpvGuardMesh.EMPTY;
        }
        List<FpvGuardMesh.Batch> out = new ArrayList<>(batches.size());
        for (Map.Entry<RenderType, Builder> entry : batches.entrySet()) {
            out.add(entry.getValue().build(entry.getKey()));
        }
        return new FpvGuardMesh(out);
    }

    private record Candidate(RenderType type, float[] quad, float[] uv, int[] argb, Vector3f normal) {
    }

    private record Key(Object source, FrameType frame) {
        @Override
        public boolean equals(Object other) {
            return other instanceof Key key && key.source == this.source && key.frame == this.frame;
        }

        @Override
        public int hashCode() {
            return System.identityHashCode(this.source) * 31 + System.identityHashCode(this.frame);
        }
    }

    private static final class Builder {
        private final FloatList xyz = new FloatList();
        private final FloatList uv = new FloatList();
        private final FloatList normals = new FloatList();
        private int[] argb = new int[64];
        private int colors;

        void add(float[] quad, Vector3f normal) {
            for (float value : quad) {
                this.xyz.add(value);
            }
            this.normals.add(normal.x);
            this.normals.add(normal.y);
            this.normals.add(normal.z);
        }

        void uv(float u, float v) {
            this.uv.add(u);
            this.uv.add(v);
        }

        void color(int color) {
            if (this.colors == this.argb.length) {
                this.argb = Arrays.copyOf(this.argb, this.argb.length * 2);
            }
            this.argb[this.colors++] = color;
        }

        int quads() {
            return this.xyz.size() / FpvGuardZone.QUAD_FLOATS;
        }

        FpvGuardMesh.Batch build(RenderType type) {
            return new FpvGuardMesh.Batch(type, this.xyz.toArray(), this.uv.toArray(), this.normals.toArray(), Arrays.copyOf(this.argb, this.colors));
        }
    }

    private static final class FloatList {
        private float[] values = new float[256];
        private int size;

        void add(float value) {
            if (this.size == this.values.length) {
                this.values = Arrays.copyOf(this.values, this.values.length * 2);
            }
            this.values[this.size++] = value;
        }

        int size() {
            return this.size;
        }

        float[] toArray() {
            return Arrays.copyOf(this.values, this.size);
        }
    }
}
