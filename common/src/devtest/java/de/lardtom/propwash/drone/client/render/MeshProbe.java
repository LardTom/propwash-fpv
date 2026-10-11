package de.lardtom.propwash.drone.client.render;

import de.lardtom.propwash.build.AccessoryMount;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import org.joml.Quaternionf;
import org.joml.Quaternionfc;
import org.joml.Vector3f;
import org.joml.Vector3fc;

import java.util.ArrayList;
import java.util.List;

final class MeshProbe {
    private static final float MM = DroneVisualScale.MILLIMETRES_PER_MODEL_BLOCK;

    private final List<float[]> fixed = new ArrayList<>();
    private final List<List<List<float[]>>> rings = new ArrayList<>();
    private int boxes;

    private MeshProbe() {
    }

    static MeshProbe of(DroneAssembly a) {
        MeshProbe m = new MeshProbe();
        m.add(a.frame(), null, 1.0F);
        m.add(a.stack(), null, 1.0F);
        m.add(a.battery(), null, 1.0F);
        m.add(a.video(), new Quaternionf().rotationX((float) Math.toRadians(a.cameraTiltDegrees())), 1.0F);
        m.add(a.transmitter(), null, 1.0F);
        for (AccessoryMount mount : AccessoryMount.values()) {
            DroneAssembly.Accessory accessory = a.accessory(mount);
            if (accessory.present()) {
                m.add(accessory.base(), null, 1.0F);
                m.add(accessory.body(), new Quaternionf().rotationX((float) Math.toRadians(accessory.tiltDegrees())), accessory.stretchX());
            }
        }
        for (int slot = 0; slot < DroneLayout.SLOTS; slot++) {
            m.add(a.motor(slot), null, 1.0F);
            List<List<float[]>> variants = new ArrayList<>();
            for (int variant = 0; variant <= VisualHull.BLUR; variant++) {
                DroneAssembly.Part part = variant == VisualHull.BLUR ? a.blur(slot) : a.prop(slot, PropDamage.values()[variant]);
                variants.add(ring(part, variant == VisualHull.BLUR));
            }
            m.rings.add(variants);
        }
        return m;
    }

    int vertices() {
        return this.fixed.size();
    }

    int boxes() {
        return this.boxes;
    }

    boolean hasProp(int slot, int variant) {
        return !this.rings.get(slot).get(variant).isEmpty();
    }

    float lowestFixed(Quaternionfc orientation, Vector3fc pivot) {
        float ax = DronePhysique.rotatedY(orientation, 1.0F, 0.0F, 0.0F);
        float ay = DronePhysique.rotatedY(orientation, 0.0F, 1.0F, 0.0F);
        float az = DronePhysique.rotatedY(orientation, 0.0F, 0.0F, 1.0F);
        float low = Float.POSITIVE_INFINITY;
        for (float[] v : this.fixed) {
            low = Math.min(low, ax * (v[0] - pivot.x()) + ay * (v[1] - pivot.y()) + az * (v[2] - pivot.z()));
        }
        return low;
    }

    float lowestProps(Quaternionfc orientation, Vector3fc pivot, int[] variants) {
        float ax = DronePhysique.rotatedY(orientation, 1.0F, 0.0F, 0.0F);
        float ay = DronePhysique.rotatedY(orientation, 0.0F, 1.0F, 0.0F);
        float az = DronePhysique.rotatedY(orientation, 0.0F, 0.0F, 1.0F);
        float h = (float) Math.sqrt(ax * ax + az * az);
        float low = Float.POSITIVE_INFINITY;
        for (int slot = 0; slot < this.rings.size(); slot++) {
            for (float[] r : this.rings.get(slot).get(variants[slot])) {
                low = Math.min(low, ax * (r[0] - pivot.x()) + ay * (r[1] + r[4] - pivot.y()) + az * (r[2] - pivot.z()) - r[3] * h);
            }
        }
        return low;
    }

    float lowest(Quaternionfc orientation, Vector3fc pivot, int[] variants) {
        return Math.min(this.lowestFixed(orientation, pivot), this.lowestProps(orientation, pivot, variants));
    }

    private void add(DroneAssembly.Part part, Quaternionf rotation, float stretchX) {
        List<Vector3f> local = new ArrayList<>();
        ItemStackRenderState item = part.item();
        if (item != null && !item.isEmpty()) {
            item.visitExtents(v -> local.add(new Vector3f(v).mul(MM)));
        }
        if (local.isEmpty() && !part.solid().isEmpty()) {
            Shape shape = part.solid();
            for (int v = 0; v < shape.vertexCount(); v++) {
                local.add(new Vector3f(shape.position(v, new Vector3f())).mul(MM));
            }
            this.boxes++;
        }
        for (Vector3f v : local) {
            v.x *= stretchX;
            if (rotation != null) {
                rotation.transform(v);
            }
            v.add(part.position());
            this.fixed.add(new float[]{v.x, v.y, v.z});
        }
    }

    private static List<float[]> ring(DroneAssembly.Part part, boolean square) {
        List<float[]> out = new ArrayList<>();
        ItemStackRenderState item = part.item();
        Vector3fc hub = part.position();
        if (item == null || item.isEmpty()) {
            Shape shape = !part.solid().isEmpty() ? part.solid() : part.translucent();
            Vector3f vertex = new Vector3f();
            for (int v = 0; v < shape.vertexCount(); v++) {
                shape.position(v, vertex);
                float x = vertex.x * MM;
                float z = vertex.z * MM;
                out.add(new float[]{hub.x(), hub.y(), hub.z(), square ? Math.max(Math.abs(x), Math.abs(z)) : (float) Math.sqrt(x * x + z * z), vertex.y * MM});
            }
            return out;
        }
        item.visitExtents(v -> {
            float x = v.x() * MM;
            float z = v.z() * MM;
            float r = square ? Math.max(Math.abs(x), Math.abs(z)) : (float) Math.sqrt(x * x + z * z);
            out.add(new float[]{hub.x(), hub.y(), hub.z(), r, v.y() * MM});
        });
        return out;
    }
}
