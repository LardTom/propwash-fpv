package de.lardtom.propwash.drone.client.render;

import de.lardtom.propwash.build.AccessoryMount;
import de.lardtom.propwash.build.DroneBuild;
import de.lardtom.propwash.build.DronePaint;
import de.lardtom.propwash.build.PaintChannel;
import de.lardtom.propwash.build.PaintSlot;
import de.lardtom.propwash.build.PartWear;
import de.lardtom.propwash.client.render.DronePreview;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.world.phys.AABB;
import org.joml.Quaternionf;
import org.joml.Vector2f;
import org.joml.Vector3f;
import org.joml.Vector3fc;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

public final class PaintPicker {
    private static final float MM = DroneVisualScale.MILLIMETRES_PER_MODEL_BLOCK;

    private PaintPicker() {
    }

    public record View(DroneRenderState state, float scale, Quaternionf rotation, float centerX, float centerY) {
        float unitsPerMillimetre() {
            return this.state.poseScale * DroneVisualScale.MM_TO_UNITS;
        }
    }

    public static @Nullable View view(DroneBuild build, PartWear wear, DronePaint paint, int x0, int y0, int x1, int y1, float yawDegrees) {
        if (build == null || x1 - x0 < 8 || y1 - y0 < 8) {
            return null;
        }
        DroneRenderState state = DroneVisuals.previewState(build, wear, paint);
        if (state == null) {
            return null;
        }
        float scale = DroneVisuals.previewScale(state, x1 - x0, y1 - y0);
        return new View(state, scale, DronePreview.rotation(yawDegrees), (x0 + x1) * 0.5F, (y0 + y1) * 0.5F);
    }

    public static Vector2f project(View view, Vector3fc millimetres) {
        float k = view.unitsPerMillimetre();
        Vector3f p = new Vector3f(millimetres).sub(view.state.pivot).mul(k);
        view.rotation.transform(p);
        return new Vector2f(view.centerX + p.x * view.scale, view.centerY + p.y * view.scale);
    }

    public static Optional<PaintSlot> pick(View view, double mouseX, double mouseY) {
        Quaternionf inverse = new Quaternionf(view.rotation).conjugate();
        Vector3f origin = new Vector3f((float) (mouseX - view.centerX) / view.scale, (float) (mouseY - view.centerY) / view.scale, 0.0F);
        inverse.transform(origin);
        Vector3f direction = inverse.transform(new Vector3f(0.0F, 0.0F, 1.0F));
        if (direction.y < 0.0F) {
            direction.negate();
        }
        float k = view.unitsPerMillimetre();
        origin.div(k).add(view.state.pivot);
        DroneRenderState s = view.state;
        for (int slot = 0; slot < DroneLayout.SLOTS; slot++) {
            if (!Float.isNaN(hit(s.motors[slot], origin, direction, 0.0F, 1.0F))) {
                return Optional.of(PaintSlot.MOTORS);
            }
        }
        float radius = s.build.prop().diameterMm * 0.5F;
        float best = Float.NEGATIVE_INFINITY;
        PaintSlot bestProp = null;
        for (int slot = 0; slot < DroneLayout.SLOTS; slot++) {
            Vector3fc hub = s.props[slot].position;
            if (Math.abs(direction.y) < 1.0E-4F) {
                continue;
            }
            float t = (hub.y() + 2.0F - origin.y) / direction.y;
            float dx = origin.x + direction.x * t - hub.x();
            float dz = origin.z + direction.z * t - hub.z();
            if (dx * dx + dz * dz <= radius * radius * 0.9F && t > best) {
                best = t;
                bestProp = PaintSlot.prop(slot);
            }
        }
        PaintSlot front = null;
        float nearest = Float.NEGATIVE_INFINITY;
        for (int i = 0; i < DroneAssembly.MOUNTS; i++) {
            if (s.accessoryType[i] == null) {
                continue;
            }
            float body = hit(s.accessories[i], origin, direction, s.accessoryTilt[i], s.accessoryStretch[i]);
            if (!Float.isNaN(body) && body > nearest) {
                nearest = body;
                front = PaintSlot.ACCESSORIES;
            }
            float base = hit(s.accessoryBases[i], origin, direction, 0.0F, 1.0F);
            if (!Float.isNaN(base) && base > nearest) {
                nearest = base;
                front = PaintSlot.TPU;
            }
        }
        float camera = hit(s.video, origin, direction, s.cameraTiltDegrees, 1.0F);
        if (!Float.isNaN(camera) && camera > nearest) {
            nearest = camera;
            front = PaintSlot.CAMERA;
        }
        float battery = s.batteryMissing ? Float.NaN : hit(s.battery, origin, direction, 0.0F, 1.0F);
        if (!Float.isNaN(battery) && battery > nearest) {
            nearest = battery;
            front = PaintSlot.BATTERY;
        }
        float stack = hit(s.stack, origin, direction, 0.0F, 1.0F);
        if (!Float.isNaN(stack) && stack > nearest) {
            nearest = stack;
            front = PaintSlot.STACK;
        }
        if (bestProp != null && (front == null || best >= nearest)) {
            return Optional.of(bestProp);
        }
        if (front != null) {
            return Optional.of(front);
        }
        if (!Float.isNaN(hit(s.frame, origin, direction, 0.0F, 1.0F))) {
            return Optional.of(PaintSlot.FRAME);
        }
        return Optional.empty();
    }

    public static List<Vector3fc> anchors(View view, PaintSlot slot) {
        DroneRenderState s = view.state;
        List<Vector3fc> out = new ArrayList<>();
        switch (slot) {
            case FRAME -> {
                for (int i = 0; i < DroneLayout.SLOTS; i++) {
                    out.add(new Vector3f(s.motors[i].position).mul(0.55F));
                }
            }
            case TPU -> {
                for (int i = 0; i < DroneLayout.SLOTS; i++) {
                    out.add(new Vector3f(s.motors[i].position).mul(0.85F));
                }
            }
            case MOTORS -> {
                for (int i = 0; i < DroneLayout.SLOTS; i++) {
                    out.add(center(s.motors[i], 0.0F));
                }
            }
            case STACK -> out.add(center(s.stack, 0.0F));
            case BATTERY -> out.add(center(s.battery, 0.0F));
            case CAMERA, ANTENNA -> out.add(center(s.video, s.cameraTiltDegrees));
            case ACCESSORIES -> {
                for (int i = 0; i < DroneAssembly.MOUNTS; i++) {
                    if (s.accessoryType[i] != null) {
                        out.add(center(s.accessories[i], s.accessoryTilt[i]));
                    }
                }
            }
            default -> {
                if (slot.isProp()) {
                    Vector3fc hub = s.props[slot.propPosition()].position;
                    out.add(new Vector3f(hub).add(0.0F, 2.0F, 0.0F));
                }
            }
        }
        return out;
    }

    public static Map<PaintSlot, Integer> defaults(DroneBuild build) {
        DroneRenderResources res = DroneVisuals.resources();
        Map<PaintSlot, Integer> out = new EnumMap<>(PaintSlot.class);
        List<DroneAssembly.Part> parts = parts(res.assembly(build));
        for (int index = 0; index < parts.size(); index++) {
            DroneAssembly.Part part = parts.get(index);
            if (part.model() == null) {
                continue;
            }
            for (DroneRenderResources.TintGroup group : res.paintGroups(part.model())) {
                if (group.channel() == PaintChannel.PROP) {
                    for (int i = 0; i < PaintSlot.PROPS; i++) {
                        out.putIfAbsent(PaintSlot.prop(i), group.base());
                    }
                } else {
                    out.putIfAbsent(PaintSlot.of(group.channel(), 0), group.base());
                }
            }
        }
        return out;
    }

    private static List<DroneAssembly.Part> parts(DroneAssembly assembly) {
        List<DroneAssembly.Part> parts = new ArrayList<>(List.of(assembly.frame(), assembly.stack(), assembly.battery(), assembly.video()));
        for (int i = 0; i < DroneLayout.SLOTS; i++) {
            parts.add(assembly.motor(i));
            parts.add(assembly.prop(i));
        }
        for (AccessoryMount mount : AccessoryMount.values()) {
            DroneAssembly.Accessory accessory = assembly.accessory(mount);
            if (accessory.present()) {
                parts.add(accessory.body());
                parts.add(accessory.base());
            }
        }
        return parts;
    }

    public static Set<PaintSlot> available(DroneBuild build) {
        DroneRenderResources res = DroneVisuals.resources();
        Set<PaintChannel> channels = EnumSet.noneOf(PaintChannel.class);
        List<DroneAssembly.Part> parts = parts(res.assembly(build));
        boolean any = false;
        for (DroneAssembly.Part part : parts) {
            if (part.model() == null) {
                continue;
            }
            for (DroneRenderResources.TintGroup group : res.paintGroups(part.model())) {
                channels.add(group.channel());
                any = true;
            }
        }
        Set<PaintSlot> slots = EnumSet.noneOf(PaintSlot.class);
        for (PaintSlot slot : PaintSlot.values()) {
            if (!any || channels.contains(slot.channel())) {
                slots.add(slot);
            }
        }
        return slots;
    }

    private static Vector3f center(DroneRenderState.PartSlot slot, float tiltDegrees) {
        Vector3f local = new Vector3f();
        if (slot.useItem && !slot.item.isEmpty()) {
            AABB box = slot.item.getModelBoundingBox();
            local.set((float) (box.minX + box.maxX) * 0.5F * MM, (float) (box.minY + box.maxY) * 0.5F * MM,
                    (float) (box.minZ + box.maxZ) * 0.5F * MM);
        } else if (!slot.useItem && !slot.solid.isEmpty()) {
            local.set(slot.solid.min()).add(slot.solid.max()).mul(0.5F * MM);
        }
        if (tiltDegrees != 0.0F) {
            new Quaternionf().rotationX((float) Math.toRadians(tiltDegrees)).transform(local);
        }
        return local.add(slot.position);
    }

    private static float hit(DroneRenderState.PartSlot slot, Vector3fc origin, Vector3fc direction, float tiltDegrees, float stretchX) {
        ItemStackRenderState item = slot.item;
        AABB box;
        if (slot.useItem && item != null && !item.isEmpty()) {
            box = item.getModelBoundingBox();
        } else if (!slot.useItem && !slot.solid.isEmpty()) {
            box = new AABB(slot.solid.min().x(), slot.solid.min().y(), slot.solid.min().z(), slot.solid.max().x(), slot.solid.max().y(),
                    slot.solid.max().z());
        } else {
            return Float.NaN;
        }
        if (box.getXsize() > 4.0 || box.getYsize() > 4.0 || box.getZsize() > 4.0) {
            return Float.NaN;
        }
        Vector3f o = new Vector3f(origin).sub(slot.position);
        Vector3f d = new Vector3f(direction);
        if (tiltDegrees != 0.0F) {
            Quaternionf undo = new Quaternionf().rotationX((float) -Math.toRadians(tiltDegrees));
            undo.transform(o);
            undo.transform(d);
        }
        if (stretchX != 0.0F && stretchX != 1.0F) {
            o.x /= stretchX;
            d.x /= stretchX;
        }
        float[] min = {(float) box.minX * MM, (float) box.minY * MM, (float) box.minZ * MM};
        float[] max = {(float) box.maxX * MM, (float) box.maxY * MM, (float) box.maxZ * MM};
        float[] from = {o.x, o.y, o.z};
        float[] dir = {d.x, d.y, d.z};
        float enter = Float.NEGATIVE_INFINITY;
        float exit = Float.POSITIVE_INFINITY;
        for (int axis = 0; axis < 3; axis++) {
            if (Math.abs(dir[axis]) < 1.0E-6F) {
                if (from[axis] < min[axis] || from[axis] > max[axis]) {
                    return Float.NaN;
                }
                continue;
            }
            float t0 = (min[axis] - from[axis]) / dir[axis];
            float t1 = (max[axis] - from[axis]) / dir[axis];
            enter = Math.max(enter, Math.min(t0, t1));
            exit = Math.min(exit, Math.max(t0, t1));
        }
        return enter <= exit ? exit : Float.NaN;
    }
}
