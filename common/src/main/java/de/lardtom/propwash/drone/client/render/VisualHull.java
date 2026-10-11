package de.lardtom.propwash.drone.client.render;

import de.lardtom.propwash.build.AccessoryMount;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.world.phys.AABB;
import org.joml.Quaternionf;
import org.joml.Quaternionfc;
import org.joml.Vector3f;
import org.joml.Vector3fc;

import java.util.Arrays;

public final class VisualHull {
    public static final double MAX_PART_EXTENT_BLOCKS = 8.0;

    public static final int MAX_CONTACT_POINTS = 4096;
    public static final int SUPPORT_DIRECTIONS = 4096;
    public static final int RING_POINTS = 32;
    public static final int PROFILE_DIRECTIONS = 17;

    public static final int BLUR = PropDamage.values().length;
    private static final int[] PRISTINE = new int[DroneLayout.SLOTS];
    private static final float[][][] NO_PROPS = new float[0][][];

    private final float[] points;
    private final float[] contact;
    private final float[][][] props;
    private final Vector3f min = new Vector3f();
    private final Vector3f max = new Vector3f();

    VisualHull(float[] points) {
        this(points, points, NO_PROPS);
    }

    VisualHull(float[] points, float[] contact, float[][][] props) {
        this.points = points;
        this.contact = contact.length == 0 && props.length == 0 ? points : contact;
        this.props = props;
        this.min.set(Float.POSITIVE_INFINITY);
        this.max.set(Float.NEGATIVE_INFINITY);
        for (int i = 0; i + 2 < points.length; i += 3) {
            this.min.set(Math.min(this.min.x, points[i]), Math.min(this.min.y, points[i + 1]), Math.min(this.min.z, points[i + 2]));
            this.max.set(Math.max(this.max.x, points[i]), Math.max(this.max.y, points[i + 1]), Math.max(this.max.z, points[i + 2]));
        }
        if (points.length == 0) {
            this.min.set(-50.0F, -10.0F, -50.0F);
            this.max.set(50.0F, 30.0F, 50.0F);
        }
    }

    static VisualHull compute(DroneAssembly assembly) {
        Builder b = new Builder();
        b.part(assembly.frame(), null);
        b.part(assembly.stack(), null);
        b.part(assembly.battery(), null);
        b.part(assembly.video(), new Quaternionf().rotationX((float) Math.toRadians(assembly.cameraTiltDegrees())));
        b.part(assembly.transmitter(), null);
        for (AccessoryMount mount : AccessoryMount.values()) {
            DroneAssembly.Accessory accessory = assembly.accessory(mount);
            if (accessory.present()) {
                b.part(accessory.base(), null);
                b.part(accessory.body(), new Quaternionf().rotationX((float) Math.toRadians(accessory.tiltDegrees())), accessory.stretchX());
            }
        }
        float propRadius = assembly.build().prop().diameterMm * 0.5F;
        for (int i = 0; i < DroneLayout.SLOTS; i++) {
            b.part(assembly.motor(i), null);
            Vector3fc hub = assembly.prop(i).position();
            for (int k = 0; k < 8; k++) {
                double a = Math.PI * 2.0 * k / 8.0;
                b.point(hub.x() + (float) Math.cos(a) * propRadius, hub.y() + 2.5F, hub.z() + (float) Math.sin(a) * propRadius);
            }
        }
        return new VisualHull(b.build(), fixed(assembly), props(assembly, propRadius));
    }

    private static float[] fixed(DroneAssembly assembly) {
        Builder c = new Builder();
        c.mesh(assembly.frame(), null, 1.0F);
        c.mesh(assembly.stack(), null, 1.0F);
        c.mesh(assembly.battery(), null, 1.0F);
        c.mesh(assembly.video(), new Quaternionf().rotationX((float) Math.toRadians(assembly.cameraTiltDegrees())), 1.0F);
        c.mesh(assembly.transmitter(), null, 1.0F);
        for (AccessoryMount mount : AccessoryMount.values()) {
            DroneAssembly.Accessory accessory = assembly.accessory(mount);
            if (accessory.present()) {
                c.mesh(accessory.base(), null, 1.0F);
                c.mesh(accessory.body(), new Quaternionf().rotationX((float) Math.toRadians(accessory.tiltDegrees())), accessory.stretchX());
            }
        }
        for (int i = 0; i < DroneLayout.SLOTS; i++) {
            c.mesh(assembly.motor(i), null, 1.0F);
        }
        return HullMath.reduce(c.build(), MAX_CONTACT_POINTS, SUPPORT_DIRECTIONS);
    }

    private static float[][][] props(DroneAssembly assembly, float propRadius) {
        float[][][] out = new float[DroneLayout.SLOTS][BLUR + 1][];
        for (int slot = 0; slot < DroneLayout.SLOTS; slot++) {
            for (int variant = 0; variant <= BLUR; variant++) {
                DroneAssembly.Part part = variant == BLUR ? assembly.blur(slot) : assembly.prop(slot, PropDamage.values()[variant]);
                Builder profile = new Builder();
                profile.profile(part, variant == BLUR);
                float[] outline = profile.build();
                if (outline.length == 0) {
                    outline = variant == PropDamage.BROKEN.ordinal() ? new float[]{0.0F, 2.5F, 0.0F} : new float[]{propRadius, 2.5F, 0.0F};
                }
                Vector3fc hub = part.position();
                float[] rings = HullMath.rings(HullMath.outline(outline, PROFILE_DIRECTIONS), RING_POINTS);
                for (int k = 0; k + 2 < rings.length; k += 3) {
                    rings[k] += hub.x();
                    rings[k + 1] += hub.y();
                    rings[k + 2] += hub.z();
                }
                out[slot][variant] = rings;
            }
        }
        return out;
    }

    public static AABB clampPartBounds(AABB box) {
        double limit = MAX_PART_EXTENT_BLOCKS;
        if (box.getXsize() > 2.0 * limit || box.getYsize() > 2.0 * limit || box.getZsize() > 2.0 * limit) {
            return box.intersect(new AABB(-limit, -limit, -limit, limit, limit, limit));
        }
        return box;
    }

    public Vector3fc min() {
        return this.min;
    }

    public Vector3fc max() {
        return this.max;
    }

    public Vector3f center(Vector3f dest) {
        return dest.set(this.min).add(this.max).mul(0.5F);
    }

    public float lowestPoint(Quaternionfc orientation, Vector3fc pivot) {
        return this.lowestPoint(orientation, pivot, PRISTINE);
    }

    public float lowestPoint(Quaternionfc orientation, Vector3fc pivot, int[] propVariants) {
        float ax = DronePhysique.rotatedY(orientation, 1.0F, 0.0F, 0.0F);
        float ay = DronePhysique.rotatedY(orientation, 0.0F, 1.0F, 0.0F);
        float az = DronePhysique.rotatedY(orientation, 0.0F, 0.0F, 1.0F);
        float lowest = lowest(this.contact, ax, ay, az, pivot, Float.POSITIVE_INFINITY);
        for (int slot = 0; slot < this.props.length; slot++) {
            int variant = slot < propVariants.length ? Math.max(0, Math.min(BLUR, propVariants[slot])) : 0;
            lowest = lowest(this.props[slot][variant], ax, ay, az, pivot, lowest);
        }
        return Float.isFinite(lowest) ? lowest : 0.0F;
    }

    private static float lowest(float[] data, float ax, float ay, float az, Vector3fc pivot, float lowest) {
        for (int i = 0; i + 2 < data.length; i += 3) {
            float y = ax * (data[i] - pivot.x()) + ay * (data[i + 1] - pivot.y()) + az * (data[i + 2] - pivot.z());
            lowest = Math.min(lowest, y);
        }
        return lowest;
    }

    public int contactPoints() {
        int count = this.contact.length / 3;
        for (float[][] slot : this.props) {
            count += slot[0].length / 3;
        }
        return count;
    }

    public float radius(Vector3fc pivot) {
        float best = 0.0F;
        for (int i = 0; i + 2 < this.points.length; i += 3) {
            float dx = this.points[i] - pivot.x();
            float dy = this.points[i + 1] - pivot.y();
            float dz = this.points[i + 2] - pivot.z();
            best = Math.max(best, dx * dx + dy * dy + dz * dz);
        }
        return best > 0.0F ? (float) Math.sqrt(best) : 100.0F;
    }

    public float horizontalRadius(Vector3fc pivot) {
        float best = 0.0F;
        for (int i = 0; i + 2 < this.points.length; i += 3) {
            float dx = this.points[i] - pivot.x();
            float dz = this.points[i + 2] - pivot.z();
            best = Math.max(best, dx * dx + dz * dz);
        }
        return best > 0.0F ? (float) Math.sqrt(best) : 100.0F;
    }

    public float projectedExtent(Quaternionfc rotation, Vector3fc pivot) {
        float x0 = Float.POSITIVE_INFINITY;
        float x1 = Float.NEGATIVE_INFINITY;
        float y0 = Float.POSITIVE_INFINITY;
        float y1 = Float.NEGATIVE_INFINITY;
        Vector3f p = new Vector3f();
        for (int i = 0; i + 2 < this.points.length; i += 3) {
            rotation.transform(p.set(this.points[i] - pivot.x(), this.points[i + 1] - pivot.y(), this.points[i + 2] - pivot.z()));
            x0 = Math.min(x0, p.x);
            x1 = Math.max(x1, p.x);
            y0 = Math.min(y0, p.y);
            y1 = Math.max(y1, p.y);
        }
        return x1 > x0 ? Math.max(x1 - x0, y1 - y0) : 0.0F;
    }

    public float halfHeight(Vector3fc pivot) {
        return Math.max(Math.abs(this.max.y() - pivot.y()), Math.abs(this.min.y() - pivot.y()));
    }

    private static final class Builder {
        private float[] data = new float[192];
        private int size;
        private final Vector3f scratch = new Vector3f();

        void part(DroneAssembly.Part part, Quaternionf rotation) {
            this.part(part, rotation, 1.0F);
        }

        void part(DroneAssembly.Part part, Quaternionf rotation, float stretchX) {
            float x0;
            float y0;
            float z0;
            float x1;
            float y1;
            float z1;
            float mm = DroneVisualScale.MILLIMETRES_PER_MODEL_BLOCK;
            if (part.item() != null && !part.item().isEmpty()) {
                AABB box = boundsOf(part.item());
                x0 = (float) box.minX * mm;
                y0 = (float) box.minY * mm;
                z0 = (float) box.minZ * mm;
                x1 = (float) box.maxX * mm;
                y1 = (float) box.maxY * mm;
                z1 = (float) box.maxZ * mm;
            } else if (!part.solid().isEmpty()) {
                x0 = part.solid().min().x() * mm;
                y0 = part.solid().min().y() * mm;
                z0 = part.solid().min().z() * mm;
                x1 = part.solid().max().x() * mm;
                y1 = part.solid().max().y() * mm;
                z1 = part.solid().max().z() * mm;
            } else {
                return;
            }
            for (int c = 0; c < 8; c++) {
                this.scratch.set(((c & 1) == 0 ? x0 : x1) * stretchX, (c & 2) == 0 ? y0 : y1, (c & 4) == 0 ? z0 : z1);
                if (rotation != null) {
                    rotation.transform(this.scratch);
                }
                Vector3fc at = part.position();
                this.point(this.scratch.x + at.x(), this.scratch.y + at.y(), this.scratch.z + at.z());
            }
        }

        void mesh(DroneAssembly.Part part, Quaternionf rotation, float stretchX) {
            ItemStackRenderState item = part.item();
            float mm = DroneVisualScale.MILLIMETRES_PER_MODEL_BLOCK;
            if (item == null || item.isEmpty()) {
                Shape shape = part.solid();
                Vector3fc position = part.position();
                Vector3f vertex = new Vector3f();
                for (int v = 0; v < shape.vertexCount(); v++) {
                    shape.position(v, vertex);
                    vertex.mul(mm);
                    vertex.x *= stretchX;
                    if (rotation != null) {
                        rotation.transform(vertex);
                    }
                    this.point(vertex.x + position.x(), vertex.y + position.y(), vertex.z + position.z());
                }
                return;
            }
            AABB raw = item.getModelBoundingBox();
            AABB box = clampPartBounds(raw);
            boolean clamp = box != raw;
            int before = this.size;
            Vector3fc at = part.position();
            item.visitExtents(vertex -> {
                float x = vertex.x();
                float y = vertex.y();
                float z = vertex.z();
                if (clamp) {
                    x = (float) Math.max(box.minX, Math.min(box.maxX, x));
                    y = (float) Math.max(box.minY, Math.min(box.maxY, y));
                    z = (float) Math.max(box.minZ, Math.min(box.maxZ, z));
                }
                this.scratch.set(x * mm * stretchX, y * mm, z * mm);
                if (rotation != null) {
                    rotation.transform(this.scratch);
                }
                this.point(this.scratch.x + at.x(), this.scratch.y + at.y(), this.scratch.z + at.z());
            });
            if (this.size == before) {
                this.part(part, rotation, stretchX);
            }
        }

        void profile(DroneAssembly.Part part, boolean square) {
            ItemStackRenderState item = part.item();
            float mm = DroneVisualScale.MILLIMETRES_PER_MODEL_BLOCK;
            float limit = (float) MAX_PART_EXTENT_BLOCKS * mm;
            if (item == null || item.isEmpty()) {
                Shape shape = !part.solid().isEmpty() ? part.solid() : part.translucent();
                for (int v = 0; v < shape.vertexCount(); v++) {
                    shape.position(v, this.scratch);
                    float x = this.scratch.x * mm;
                    float z = this.scratch.z * mm;
                    float radius = square ? Math.max(Math.abs(x), Math.abs(z)) : (float) Math.sqrt(x * x + z * z);
                    this.point(Math.min(radius, limit), Math.max(-limit, Math.min(limit, this.scratch.y * mm)), 0.0F);
                }
                return;
            }
            item.visitExtents(vertex -> {
                float x = vertex.x() * mm;
                float z = vertex.z() * mm;
                float radius = square ? Math.max(Math.abs(x), Math.abs(z)) : (float) Math.sqrt(x * x + z * z);
                this.point(Math.min(radius, limit), Math.max(-limit, Math.min(limit, vertex.y() * mm)), 0.0F);
            });
        }

        void point(float x, float y, float z) {
            if (!Float.isFinite(x) || !Float.isFinite(y) || !Float.isFinite(z)) {
                return;
            }
            if (this.size + 3 > this.data.length) {
                this.data = Arrays.copyOf(this.data, this.data.length * 2);
            }
            this.data[this.size++] = x;
            this.data[this.size++] = y;
            this.data[this.size++] = z;
        }

        float[] build() {
            return Arrays.copyOf(this.data, this.size);
        }

        private static AABB boundsOf(ItemStackRenderState item) {
            return clampPartBounds(item.getModelBoundingBox());
        }
    }
}
