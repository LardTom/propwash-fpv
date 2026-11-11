package de.lardtom.propwash.drone.client.render;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import de.lardtom.propwash.build.AccessoryAnchor;
import de.lardtom.propwash.build.AccessoryMount;
import de.lardtom.propwash.build.AccessoryType;
import net.minecraft.client.resources.model.geometry.BakedQuad;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Quaternionfc;
import org.joml.Vector3f;
import org.jspecify.annotations.Nullable;

import java.util.Arrays;
import java.util.Locale;

public final class AccessoryFit {
    public static final float MAX_GAP_MM = 1.0F;
    public static final float MAX_OVERLAP_MM = 2.0F;
    static final float NEAR_MM = 80.0F;
    static final float COARSE_MM = 2.0F;
    static final float FINE_MM = 0.25F;
    static final float BODY_STEP_MM = 0.5F;
    static final int QUAD = 12;
    private static final int[][] FACES = {{0, 2, 6, 4}, {1, 5, 7, 3}, {0, 4, 5, 1}, {2, 3, 7, 6}, {0, 1, 3, 2}, {4, 6, 7, 5}};

    private AccessoryFit() {
    }

    public record Fit(float gapMm, float boxGapMm, float overlapMm, float wantedOverlapMm, float @Nullable [] targetMm) {
        public boolean ok() {
            return this.gapMm <= MAX_GAP_MM && this.overlapMm <= MAX_OVERLAP_MM;
        }

        public String describe(AccessoryAnchor anchor) {
            String text = String.format(Locale.ROOT, "Abstand %.1f mm", this.gapMm);
            if (Math.abs(this.boxGapMm - this.gapMm) >= 0.05F) {
                text += String.format(Locale.ROOT, " (Quader aus size_mm %.1f mm)", this.boxGapMm);
            }
            text += String.format(Locale.ROOT, ", Überschneidung %.1f mm", this.overlapMm);
            if (this.wantedOverlapMm >= 0.05F) {
                text += String.format(Locale.ROOT, " (gewollt eingerückt %.1f mm)", this.wantedOverlapMm);
            }
            if (!this.ok() && this.targetMm != null) {
                text += String.format(Locale.ROOT, ", Anker (%.1f; %.1f; %.1f) → Soll (%.1f; %.1f; %.1f)", anchor.x(), anchor.y(), anchor.z(),
                        this.targetMm[0], this.targetMm[1], this.targetMm[2]);
            }
            return text;
        }
    }

    public static float[] boxQuads(AccessoryAnchor anchor, AccessoryType type) {
        return cuboid(anchor.corners(type));
    }

    static float[] cuboid(double[][] corners) {
        float[] out = new float[FACES.length * QUAD];
        int k = 0;
        for (int[] face : FACES) {
            for (int c : face) {
                out[k++] = (float) corners[c][0];
                out[k++] = (float) corners[c][1];
                out[k++] = (float) corners[c][2];
            }
        }
        return out;
    }

    public static float[] concat(float[]... parts) {
        int size = 0;
        for (float[] part : parts) {
            size += part.length;
        }
        float[] out = new float[size];
        int at = 0;
        for (float[] part : parts) {
            System.arraycopy(part, 0, out, at, part.length);
            at += part.length;
        }
        return out;
    }

    static float[] quads(DroneAssembly.Part part, @Nullable Quaternionfc rotation, float stretchX) {
        float mm = DroneVisualScale.MILLIMETRES_PER_MODEL_BLOCK;
        Vector3f p = new Vector3f();
        Builder out = new Builder();
        if (part.item() != null && !part.item().isEmpty()) {
            QuadCapture.Result captured = QuadCapture.of(part.item());
            for (QuadCapture.Layer layer : captured.layers()) {
                Matrix4f pose = layer.pose();
                for (BakedQuad baked : layer.quads().all()) {
                    for (int v = 0; v < 4; v++) {
                        pose.transformPosition(baked.position(v), p);
                        out.vertex(place(p.mul(mm), part, rotation, stretchX));
                    }
                }
            }
            return out.build();
        }
        Shape shape = part.solid();
        for (int v = 0; v + 3 < shape.vertexCount(); v += 4) {
            for (int k = 0; k < 4; k++) {
                shape.position(v + k, p);
                out.vertex(place(p.mul(mm), part, rotation, stretchX));
            }
        }
        return out.build();
    }

    private static Vector3f place(Vector3f mm, DroneAssembly.Part part, @Nullable Quaternionfc rotation, float stretchX) {
        mm.x *= stretchX;
        if (rotation != null) {
            rotation.transform(mm);
        }
        return mm.add(part.position());
    }

    public static float[] elementQuads(JsonObject model) {
        Builder out = new Builder();
        JsonElement elements = model.get("elements");
        if (elements == null || !elements.isJsonArray()) {
            return out.build();
        }
        for (JsonElement entry : elements.getAsJsonArray()) {
            JsonObject element = entry.getAsJsonObject();
            float[] from = triple(element.getAsJsonArray("from"));
            float[] to = triple(element.getAsJsonArray("to"));
            JsonObject rotation = element.get("rotation") instanceof JsonObject r ? r : null;
            Matrix3f matrix = rotation == null ? null : rotation(rotation);
            float[] origin = rotation == null || !rotation.has("origin") ? new float[]{8.0F, 8.0F, 8.0F} : triple(rotation.getAsJsonArray("origin"));
            float[] rescale = rotation == null ? new float[]{1.0F, 1.0F, 1.0F} : rescale(rotation);
            double[][] corners = new double[8][];
            Vector3f c = new Vector3f();
            for (int i = 0; i < 8; i++) {
                c.set((i & 1) == 0 ? from[0] : to[0], (i & 2) == 0 ? from[1] : to[1], (i & 4) == 0 ? from[2] : to[2]);
                if (matrix != null) {
                    c.sub(origin[0], origin[1], origin[2]);
                    matrix.transform(c);
                    c.mul(rescale[0], rescale[1], rescale[2]);
                    c.add(origin[0], origin[1], origin[2]);
                }
                float unit = DroneVisualScale.MILLIMETRES_PER_MODEL_UNIT;
                corners[i] = new double[]{(c.x - 8.0F) * unit, (c.y - 8.0F) * unit, (c.z - 8.0F) * unit};
            }
            out.append(cuboid(corners));
        }
        return out.build();
    }

    private static float[] triple(JsonArray array) {
        return new float[]{array.get(0).getAsFloat(), array.get(1).getAsFloat(), array.get(2).getAsFloat()};
    }

    private static @Nullable Matrix3f rotation(JsonObject rotation) {
        float x = 0.0F;
        float y = 0.0F;
        float z = 0.0F;
        if (rotation.get("axis") instanceof JsonPrimitive axis && axis.isString()) {
            float angle = rotation.has("angle") ? rotation.get("angle").getAsFloat() : 0.0F;
            switch (axis.getAsString()) {
                case "x" -> x = angle;
                case "y" -> y = angle;
                case "z" -> z = angle;
                default -> {
                    return null;
                }
            }
        } else {
            x = rotation.has("x") ? rotation.get("x").getAsFloat() : 0.0F;
            y = rotation.has("y") ? rotation.get("y").getAsFloat() : 0.0F;
            z = rotation.has("z") ? rotation.get("z").getAsFloat() : 0.0F;
        }
        return new Matrix3f().rotationZ((float) Math.toRadians(z)).rotateY((float) Math.toRadians(y)).rotateX((float) Math.toRadians(x));
    }

    private static float[] rescale(JsonObject rotation) {
        float[] out = {1.0F, 1.0F, 1.0F};
        if (!(rotation.get("rescale") instanceof JsonPrimitive flag) || !flag.getAsBoolean() || !(rotation.get("axis") instanceof JsonPrimitive axis)) {
            return out;
        }
        float angle = rotation.has("angle") ? rotation.get("angle").getAsFloat() : 0.0F;
        float cos = (float) Math.abs(Math.cos(Math.toRadians(angle)));
        float factor = cos > 1.0E-6F ? 1.0F / cos : 1.0F;
        switch (axis.getAsString()) {
            case "x" -> {
                out[1] = factor;
                out[2] = factor;
            }
            case "y" -> {
                out[0] = factor;
                out[2] = factor;
            }
            default -> {
                out[0] = factor;
                out[1] = factor;
            }
        }
        return out;
    }

    public static @Nullable Fit measure(DroneAssembly assembly, AccessoryMount mount, boolean frameOnly, boolean asBox) {
        DroneAssembly.Accessory accessory = assembly.accessory(mount);
        if (!accessory.present() || accessory.anchor() == null || accessory.type() == null) {
            return null;
        }
        Quaternionf tilt = new Quaternionf().rotationX((float) Math.toRadians(accessory.tiltDegrees()));
        float[] shape = asBox ? boxQuads(accessory.anchor(), accessory.type())
                : concat(quads(accessory.base(), null, 1.0F), quads(accessory.body(), tilt, accessory.stretchX()));
        float[] body = quads(assembly.frame(), null, 1.0F);
        if (!frameOnly) {
            Quaternionf camera = new Quaternionf().rotationX((float) Math.toRadians(assembly.cameraTiltDegrees()));
            body = concat(body, quads(assembly.stack(), null, 1.0F), quads(assembly.battery(), null, 1.0F), quads(assembly.video(), camera, 1.0F),
                    quads(assembly.transmitter(), null, 1.0F));
            for (int slot = 0; slot < DroneLayout.SLOTS; slot++) {
                body = concat(body, quads(assembly.motor(slot), null, 1.0F));
            }
        }
        return measure(body, shape, accessory.anchor(), accessory.type());
    }

    public static Fit measure(float[] body, float[] accessory, AccessoryAnchor anchor, AccessoryType type) {
        float[] box = boxQuads(anchor, type);
        float[] shape = accessory.length >= QUAD ? accessory : box;
        float[] volume = bounds(box, null);
        float[] near = within(body, bounds(shape, volume), NEAR_MM);
        if (near.length == 0) {
            return new Fit(Float.POSITIVE_INFINITY, Float.POSITIVE_INFINITY, 0.0F, 0.0F, null);
        }
        float gap = gap(near, shape);
        float boxGap = Arrays.equals(shape, box) ? gap : gap(near, box);
        float[] inside = within(near, volume, 0.0F);
        return new Fit(gap, boxGap, overlap(inside, anchor, type), wantedOverlap(inside, anchor, type), target(near, anchor, type));
    }

    static float gap(float[] body, float[] accessory) {
        int quads = accessory.length / QUAD;
        float[] coarse = new float[quads];
        float best = Float.POSITIVE_INFINITY;
        for (int q = 0; q < quads; q++) {
            coarse[q] = minDistance(accessory, q, COARSE_MM, body, Float.POSITIVE_INFINITY);
            best = Math.min(best, coarse[q]);
        }
        float limit = best + 2.0F * COARSE_MM;
        for (int q = 0; q < quads; q++) {
            if (coarse[q] <= limit) {
                float[] own = Arrays.copyOfRange(accessory, q * QUAD, q * QUAD + QUAD);
                float[] close = within(body, bounds(own, null), coarse[q] + COARSE_MM);
                best = Math.min(best, minDistance(accessory, q, FINE_MM, close, best));
            }
        }
        return best;
    }

    private static float minDistance(float[] quads, int q, float step, float[] body, float best) {
        float min = Float.POSITIVE_INFINITY;
        int base = q * QUAD;
        int nu = Math.max(1, (int) Math.ceil(edge(quads, base, 0, 1) / step));
        int nv = Math.max(1, (int) Math.ceil(edge(quads, base, 0, 3) / step));
        Vector3f p = new Vector3f();
        for (int i = 0; i <= nu; i++) {
            float u = (float) i / nu;
            for (int j = 0; j <= nv; j++) {
                float v = (float) j / nv;
                bilinear(quads, base, u, v, p);
                min = Math.min(min, distanceToQuads(p, body, min));
                if (min <= 0.0F) {
                    return 0.0F;
                }
            }
        }
        return Math.min(min, best);
    }

    static float overlap(float[] body, AccessoryAnchor anchor, AccessoryType type) {
        return deepest(body, anchor, type, type.boxMaxMm()[2] - type.recessMm);
    }

    static float wantedOverlap(float[] body, AccessoryAnchor anchor, AccessoryType type) {
        return type.recessMm > 0.0 ? deepest(body, anchor, type, type.boxMaxMm()[2]) : 0.0F;
    }

    private static float deepest(float[] body, AccessoryAnchor anchor, AccessoryType type, double rearMm) {
        double[] lo = type.boxMinMm();
        double[] hi = type.boxMaxMm();
        float stretch = (float) anchor.stretch(type);
        float tilt = (float) Math.toRadians(anchor.tiltFor(type));
        float cos = (float) Math.cos(tilt);
        float sin = (float) Math.sin(tilt);
        float deepest = 0.0F;
        Vector3f p = new Vector3f();
        for (int q = 0; q < body.length / QUAD; q++) {
            int base = q * QUAD;
            int nu = Math.max(1, (int) Math.ceil(edge(body, base, 0, 1) / BODY_STEP_MM));
            int nv = Math.max(1, (int) Math.ceil(edge(body, base, 0, 3) / BODY_STEP_MM));
            for (int i = 0; i <= nu; i++) {
                for (int j = 0; j <= nv; j++) {
                    bilinear(body, base, (float) i / nu, (float) j / nv, p);
                    float dx = p.x - (float) anchor.x();
                    float dy = p.y - (float) anchor.y();
                    float dz = p.z - (float) anchor.z();
                    float lx = dx / stretch;
                    float ly = dy * cos + dz * sin;
                    float lz = -dy * sin + dz * cos;
                    float depth = Math.min((float) Math.min(lx - lo[0], hi[0] - lx), (float) Math.min(ly - lo[1], hi[1] - ly));
                    depth = Math.min(depth, (float) Math.min(lz - lo[2], rearMm - lz));
                    deepest = Math.max(deepest, depth);
                }
            }
        }
        return deepest;
    }

    static float @Nullable [] target(float[] body, AccessoryAnchor anchor, AccessoryType type) {
        if (type.mount == AccessoryMount.TOP) {
            return null;
        }
        double[] lo = type.boxMinMm();
        double[] hi = type.boxMaxMm();
        lo[2] -= type.recessMm;
        hi[2] -= type.recessMm;
        float stretch = (float) anchor.stretch(type);
        float tilt = (float) Math.toRadians(anchor.tiltFor(type));
        float cos = (float) Math.cos(tilt);
        float sin = (float) Math.sin(tilt);
        float behind = Float.POSITIVE_INFINITY;
        float above = Float.POSITIVE_INFINITY;
        Vector3f p = new Vector3f();
        for (int q = 0; q < body.length / QUAD; q++) {
            int base = q * QUAD;
            int nu = Math.max(1, (int) Math.ceil(edge(body, base, 0, 1)));
            int nv = Math.max(1, (int) Math.ceil(edge(body, base, 0, 3)));
            for (int i = 0; i <= nu; i++) {
                for (int j = 0; j <= nv; j++) {
                    bilinear(body, base, (float) i / nu, (float) j / nv, p);
                    float dx = p.x - (float) anchor.x();
                    float dy = p.y - (float) anchor.y();
                    float dz = p.z - (float) anchor.z();
                    float lx = dx / stretch;
                    float ly = dy * cos + dz * sin;
                    float lz = -dy * sin + dz * cos;
                    if (lx < lo[0] || lx > hi[0]) {
                        continue;
                    }
                    if (type.mount == AccessoryMount.FRONT && ly >= lo[1] && ly <= hi[1] && lz >= lo[2]) {
                        behind = Math.min(behind, lz - (float) hi[2]);
                    }
                    if (lz >= lo[2] && lz <= hi[2] && ly >= lo[1]) {
                        above = Math.min(above, ly - (float) hi[1]);
                    }
                }
            }
        }
        if (!Float.isFinite(behind) && !Float.isFinite(above)) {
            return null;
        }
        boolean back = Float.isFinite(behind) && (!Float.isFinite(above) || Math.abs(behind) <= Math.abs(above));
        float shift = back ? behind : above;
        float ly = back ? 0.0F : shift;
        float lz = back ? shift : 0.0F;
        float y = ly * cos - lz * sin;
        float z = ly * sin + lz * cos;
        return new float[]{round(anchor.x()), round(anchor.y() + y), round(anchor.z() + z)};
    }

    private static float round(double mm) {
        return Math.round(mm * 10.0) / 10.0F;
    }

    static float[] within(float[] quads, float[] box, float margin) {
        Builder out = new Builder();
        for (int q = 0; q < quads.length / QUAD; q++) {
            float[] own = bounds(Arrays.copyOfRange(quads, q * QUAD, q * QUAD + QUAD), null);
            if (own[0] <= box[3] + margin && own[3] >= box[0] - margin && own[1] <= box[4] + margin && own[4] >= box[1] - margin
                    && own[2] <= box[5] + margin && own[5] >= box[2] - margin) {
                out.append(Arrays.copyOfRange(quads, q * QUAD, q * QUAD + QUAD));
            }
        }
        return out.build();
    }

    static float[] bounds(float[] quads, float @Nullable [] seed) {
        float[] b = seed != null ? seed.clone() : new float[]{Float.POSITIVE_INFINITY, Float.POSITIVE_INFINITY, Float.POSITIVE_INFINITY,
                Float.NEGATIVE_INFINITY, Float.NEGATIVE_INFINITY, Float.NEGATIVE_INFINITY};
        for (int i = 0; i + 2 < quads.length; i += 3) {
            for (int a = 0; a < 3; a++) {
                b[a] = Math.min(b[a], quads[i + a]);
                b[a + 3] = Math.max(b[a + 3], quads[i + a]);
            }
        }
        return b;
    }

    private static float edge(float[] q, int base, int a, int b) {
        float dx = q[base + b * 3] - q[base + a * 3];
        float dy = q[base + b * 3 + 1] - q[base + a * 3 + 1];
        float dz = q[base + b * 3 + 2] - q[base + a * 3 + 2];
        return (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
    }

    private static void bilinear(float[] q, int base, float u, float v, Vector3f out) {
        for (int a = 0; a < 3; a++) {
            float p0 = q[base + a];
            float p1 = q[base + 3 + a];
            float p2 = q[base + 6 + a];
            float p3 = q[base + 9 + a];
            float value = (1 - u) * (1 - v) * p0 + u * (1 - v) * p1 + u * v * p2 + (1 - u) * v * p3;
            if (a == 0) {
                out.x = value;
            } else if (a == 1) {
                out.y = value;
            } else {
                out.z = value;
            }
        }
    }

    static float distanceToQuads(Vector3f p, float[] quads, float best) {
        float min = best;
        for (int q = 0; q < quads.length / QUAD; q++) {
            int base = q * QUAD;
            min = Math.min(min, triangle(p, quads, base, base + 3, base + 6, min));
            min = Math.min(min, triangle(p, quads, base, base + 6, base + 9, min));
            if (min <= 0.0F) {
                return 0.0F;
            }
        }
        return min;
    }

    private static float triangle(Vector3f p, float[] q, int ia, int ib, int ic, float best) {
        float ax = q[ia];
        float ay = q[ia + 1];
        float az = q[ia + 2];
        float minX = Math.min(ax, Math.min(q[ib], q[ic]));
        float maxX = Math.max(ax, Math.max(q[ib], q[ic]));
        float minY = Math.min(ay, Math.min(q[ib + 1], q[ic + 1]));
        float maxY = Math.max(ay, Math.max(q[ib + 1], q[ic + 1]));
        float minZ = Math.min(az, Math.min(q[ib + 2], q[ic + 2]));
        float maxZ = Math.max(az, Math.max(q[ib + 2], q[ic + 2]));
        float ox = Math.max(0.0F, Math.max(minX - p.x, p.x - maxX));
        float oy = Math.max(0.0F, Math.max(minY - p.y, p.y - maxY));
        float oz = Math.max(0.0F, Math.max(minZ - p.z, p.z - maxZ));
        if (ox * ox + oy * oy + oz * oz >= best * best) {
            return best;
        }
        double abx = q[ib] - ax;
        double aby = q[ib + 1] - ay;
        double abz = q[ib + 2] - az;
        double acx = q[ic] - ax;
        double acy = q[ic + 1] - ay;
        double acz = q[ic + 2] - az;
        double apx = p.x - ax;
        double apy = p.y - ay;
        double apz = p.z - az;
        double d1 = abx * apx + aby * apy + abz * apz;
        double d2 = acx * apx + acy * apy + acz * apz;
        double cx;
        double cy;
        double cz;
        if (d1 <= 0.0 && d2 <= 0.0) {
            cx = ax;
            cy = ay;
            cz = az;
        } else {
            double bpx = p.x - q[ib];
            double bpy = p.y - q[ib + 1];
            double bpz = p.z - q[ib + 2];
            double d3 = abx * bpx + aby * bpy + abz * bpz;
            double d4 = acx * bpx + acy * bpy + acz * bpz;
            double cpx = p.x - q[ic];
            double cpy = p.y - q[ic + 1];
            double cpz = p.z - q[ic + 2];
            double d5 = abx * cpx + aby * cpy + abz * cpz;
            double d6 = acx * cpx + acy * cpy + acz * cpz;
            double vc = d1 * d4 - d3 * d2;
            double vb = d5 * d2 - d1 * d6;
            double va = d3 * d6 - d5 * d4;
            if (d3 >= 0.0 && d4 <= d3) {
                cx = q[ib];
                cy = q[ib + 1];
                cz = q[ib + 2];
            } else if (vc <= 0.0 && d1 >= 0.0 && d3 <= 0.0) {
                double t = d1 / (d1 - d3);
                cx = ax + t * abx;
                cy = ay + t * aby;
                cz = az + t * abz;
            } else if (d6 >= 0.0 && d5 <= d6) {
                cx = q[ic];
                cy = q[ic + 1];
                cz = q[ic + 2];
            } else if (vb <= 0.0 && d2 >= 0.0 && d6 <= 0.0) {
                double t = d2 / (d2 - d6);
                cx = ax + t * acx;
                cy = ay + t * acy;
                cz = az + t * acz;
            } else if (va <= 0.0 && d4 - d3 >= 0.0 && d5 - d6 >= 0.0) {
                double t = (d4 - d3) / ((d4 - d3) + (d5 - d6));
                cx = q[ib] + t * (q[ic] - q[ib]);
                cy = q[ib + 1] + t * (q[ic + 1] - q[ib + 1]);
                cz = q[ib + 2] + t * (q[ic + 2] - q[ib + 2]);
            } else {
                double denom = 1.0 / (va + vb + vc);
                double v = vb * denom;
                double w = vc * denom;
                cx = ax + abx * v + acx * w;
                cy = ay + aby * v + acy * w;
                cz = az + abz * v + acz * w;
            }
        }
        double ex = p.x - cx;
        double ey = p.y - cy;
        double ez = p.z - cz;
        return (float) Math.sqrt(ex * ex + ey * ey + ez * ez);
    }

    private static final class Builder {
        private float[] data = new float[QUAD * 16];
        private int size;

        void vertex(Vector3f v) {
            this.ensure(3);
            this.data[this.size++] = v.x;
            this.data[this.size++] = v.y;
            this.data[this.size++] = v.z;
        }

        void append(float[] values) {
            this.ensure(values.length);
            System.arraycopy(values, 0, this.data, this.size, values.length);
            this.size += values.length;
        }

        private void ensure(int more) {
            if (this.size + more > this.data.length) {
                this.data = Arrays.copyOf(this.data, Math.max(this.data.length * 2, this.size + more));
            }
        }

        float[] build() {
            return Arrays.copyOf(this.data, this.size);
        }
    }
}
