package de.lardtom.propwash.drone.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import de.lardtom.propwash.accessory.AccessoryBehaviors;
import de.lardtom.propwash.accessory.client.AddonCamera;
import de.lardtom.propwash.accessory.display.ItemDisplays;
import de.lardtom.propwash.api.accessory.AccessoryBehavior;
import de.lardtom.propwash.api.accessory.ItemDisplay;
import de.lardtom.propwash.build.AccessoryMount;
import de.lardtom.propwash.build.AccessoryType;
import de.lardtom.propwash.drone.DroneScale;
import de.lardtom.propwash.drone.QuadcopterEntity;
import de.lardtom.propwash.drone.client.FpvView;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.item.ItemModelResolver;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;
import org.joml.Quaternionf;
import org.joml.Vector3d;
import org.joml.Vector3f;
import org.joml.Vector3fc;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Optional;

import static de.lardtom.propwash.drone.client.render.DroneVisualScale.MM_TO_UNITS;

public final class AccessoryDisplayState {
    public static final int MOUNTS = DroneAssembly.MOUNTS;
    public static final int CAPACITY = MOUNTS * AccessoryBehavior.MAX_SLOTS;
    static final float VIEW_MARGIN = 1.05F;
    static final float TEXELS = 16.0F;
    static final float MM_PER_BLOCK = DroneVisualScale.MILLIMETRES_PER_BLOCK;
    private static volatile float lastNearScale = 1.0F;

    public final Quaternionf[] gimbal = new Quaternionf[MOUNTS];
    private final Entry[] entries = new Entry[CAPACITY];
    private final Matrix4f scratch = new Matrix4f();
    private final Vector3d lensCom = new Vector3d();
    private final Quaternionf lensBody = new Quaternionf();
    private final Quaternionf lensRotation = new Quaternionf();
    private final Matrix4f lensPose = new Matrix4f();
    private final PoseStack.Pose unpushed = new PoseStack.Pose();
    private final PoseStack.Pose itemNormals = new PoseStack.Pose();
    private final Matrix4f toEye = new Matrix4f();
    private final Quaternionf view = new Quaternionf();
    private final Vector3f point = new Vector3f();
    private float tanX;
    private float tanY;
    private float marginMm;
    private float nearestMm;
    private float lensScale;
    private float lensTilt = Float.NaN;
    private boolean followCamera;
    private boolean atLens;
    private int count;

    public AccessoryDisplayState() {
        for (int i = 0; i < MOUNTS; i++) {
            this.gimbal[i] = new Quaternionf();
        }
        for (int i = 0; i < CAPACITY; i++) {
            this.entries[i] = new Entry();
        }
    }

    public int count() {
        return this.count;
    }

    public int count(AccessoryMount mount) {
        int n = 0;
        for (int i = 0; i < this.count; i++) {
            if (this.entries[i].mount == mount.ordinal()) {
                n++;
            }
        }
        return n;
    }

    public @Nullable ItemStack shown(AccessoryMount mount, int slot) {
        for (int i = 0; i < this.count; i++) {
            Entry entry = this.entries[i];
            if (entry.mount == mount.ordinal() && entry.slot == slot) {
                return entry.stack;
            }
        }
        return null;
    }

    public boolean atLens() {
        return this.atLens && this.count > 0;
    }

    public static float lastNearScale() {
        return lastNearScale;
    }

    public static boolean shownInOwnFpv(QuadcopterEntity drone) {
        return AccessoryBehaviors.INSTANCE.any() && !drone.accessoryContents().isEmpty();
    }

    public void extract(QuadcopterEntity drone, DroneRenderState state, ItemModelResolver resolver) {
        for (Quaternionf pose : this.gimbal) {
            pose.identity();
        }
        this.atLens = state.hidden && !state.shadowPass && QuadcopterRenderer.isOwnFpvDrone(drone) && FpvView.displayedBody(this.lensCom, this.lensBody);
        if (this.atLens) {
            float scale = drone.scale();
            this.lensScale = DroneVisualScale.poseScale(1.0F) * (DroneScale.scaled(scale) ? scale : 1.0F);
            this.lensTilt = FpvProps.cameraTilt(drone);
            this.followCamera = Float.isFinite(this.lensTilt) && AddonCamera.provider(drone) == null;
        }
        int used = 0;
        if ((!state.hidden || this.atLens) && AccessoryBehaviors.INSTANCE.any()) {
            for (AccessoryMount mount : AccessoryMount.values()) {
                int m = mount.ordinal();
                AccessoryType type = state.accessoryType[m];
                if (type == null) {
                    continue;
                }
                AccessoryType unladen = type.unladen();
                AccessoryBehavior behavior = AccessoryBehaviors.INSTANCE.of(unladen);
                if (behavior == null) {
                    continue;
                }
                List<ItemStack> stacks = drone.accessoryContents().at(mount);
                int slots = Math.min(AccessoryBehaviors.INSTANCE.slots(unladen), stacks.size());
                for (int slot = 0; slot < slots && used < CAPACITY; slot++) {
                    ItemStack stack = stacks.get(slot);
                    if (stack.isEmpty()) {
                        continue;
                    }
                    Optional<ItemDisplay> display = ItemDisplays.display(unladen.id(), behavior, slot);
                    if (display.isEmpty()) {
                        continue;
                    }
                    Entry entry = this.entries[used++];
                    resolver.updateForNonLiving(entry.item, stack, ItemDisplayContext.FIXED, drone);
                    entry.mount = m;
                    entry.slot = slot;
                    entry.stack = stack;
                    entry.display = display.get();
                }
            }
        }
        for (int i = used; i < this.count; i++) {
            this.entries[i].clear();
        }
        this.count = used;
    }

    public void submit(DroneRenderState s, PoseStack ps, SubmitNodeCollector collector) {
        if (this.count == 0 || s.hidden || s.isInvisible) {
            return;
        }
        ps.pushPose();
        ps.translate(0.0F, s.originY, 0.0F);
        ps.rotate(s.orientation);
        ps.scale(s.poseScale, s.poseScale, s.poseScale);
        ps.translate(-s.pivot.x * MM_TO_UNITS, -s.pivot.y * MM_TO_UNITS, -s.pivot.z * MM_TO_UNITS);
        this.submitItems(s, ps, collector, null);
        ps.popPose();
    }

    public void submitAtLens(DroneRenderState s, PoseStack ps, SubmitNodeCollector collector, @Nullable CameraRenderState camera) {
        if (!this.atLens() || s.isInvisible || camera == null) {
            return;
        }
        ps.pushPose();
        ps.translate((float) (camera.pos.x - s.x), (float) (camera.pos.y - s.y), (float) (camera.pos.z - s.z));
        this.submitLens(s, ps, collector, camera);
        ps.popPose();
    }

    public void submitAtCamera(DroneRenderState s, PoseStack ps, SubmitNodeCollector collector, @Nullable CameraRenderState camera) {
        if (!this.atLens() || s.isInvisible || camera == null) {
            return;
        }
        ps.pushPose();
        this.submitLens(s, ps, collector, camera);
        ps.popPose();
    }

    private void submitLens(DroneRenderState s, PoseStack ps, SubmitNodeCollector collector, CameraRenderState camera) {
        Vector3fc lens = FpvProps.lens(s.build);
        if (this.followCamera) {
            this.lensRotation.set(camera.orientation).rotateX((float) Math.toRadians(-this.lensTilt));
        } else {
            this.lensRotation.set(this.lensBody);
        }
        this.lensPose.rotation(this.lensRotation).scale(this.lensScale).translate(-lens.x() * MM_TO_UNITS, -lens.y() * MM_TO_UNITS, -lens.z() * MM_TO_UNITS);
        float near = this.nearScale(s, camera);
        lastNearScale = near;
        this.unpushed.set(ps.last());
        this.toLens(this.unpushed, lens);
        ps.scale(near, near, near);
        this.toLens(ps.last(), lens);
        this.submitItems(s, ps, collector, near != 1.0F ? this.unpushed : null);
    }

    private void toLens(PoseStack.Pose pose, Vector3fc lens) {
        pose.rotate(this.lensRotation);
        pose.scale(this.lensScale, this.lensScale, this.lensScale);
        pose.translate(-lens.x() * MM_TO_UNITS, -lens.y() * MM_TO_UNITS, -lens.z() * MM_TO_UNITS);
    }

    static void keepNormals(PoseStack.Pose pushed, PoseStack.Pose unpushed, Matrix4fc item, PoseStack.Pose scratch) {
        scratch.set(unpushed);
        scratch.mulPose(item);
        pushed.normal().set(scratch.normal());
    }

    private float nearScale(DroneRenderState s, CameraRenderState camera) {
        Matrix4f projection = camera.projectionMatrix;
        this.tanX = (projection.m00() > 1.0E-4F ? 1.0F / projection.m00() : FpvPropGeometry.tanHalfFromDegrees(170.0F)) * VIEW_MARGIN;
        this.tanY = (projection.m11() > 1.0E-4F ? 1.0F / projection.m11() : FpvPropGeometry.tanHalfFromDegrees(170.0F)) * VIEW_MARGIN;
        this.view.set(camera.orientation).conjugate();
        float itemMm = ItemDisplays.ITEM_SIZE_MM * this.lensScale / DroneVisualScale.MODEL_UNIT_SCALE;
        this.nearestMm = Float.POSITIVE_INFINITY;
        for (int i = 0; i < this.count; i++) {
            Entry entry = this.entries[i];
            if (entry.display == null || entry.item.isEmpty()) {
                continue;
            }
            int m = entry.mount;
            DroneRenderState.PartSlot body = s.accessories[m];
            ItemDisplays.model(body.position.x(), body.position.y(), body.position.z(), s.accessoryTilt[m], this.gimbal[m], entry.display, MM_TO_UNITS,
                    this.scratch);
            this.toEye.set(this.lensPose).mul(this.scratch);
            this.marginMm = entry.display.scale() * itemMm / TEXELS;
            entry.item.visitExtents(this::extent);
        }
        return nearScale(this.nearestMm);
    }

    private void extent(Vector3fc extent) {
        this.point.set(extent).mulPosition(this.toEye).rotate(this.view).mul(MM_PER_BLOCK);
        this.nearestMm = Math.min(this.nearestMm, visibleDepthMm(this.point.x, this.point.y, this.point.z, this.tanX, this.tanY, this.marginMm));
    }

    static float visibleDepthMm(float x, float y, float z, float tanX, float tanY, float marginMm) {
        float depth = -z;
        if (!(depth > FpvPropGeometry.MIN_DEPTH_MM) || Math.abs(x) > depth * tanX + marginMm || Math.abs(y) > depth * tanY + marginMm) {
            return Float.POSITIVE_INFINITY;
        }
        return depth;
    }

    static float nearScale(float nearestMm) {
        return Float.isFinite(nearestMm) ? Math.clamp(FpvPropGeometry.NEAR_TARGET_MM / nearestMm, 1.0F, FpvPropGeometry.MAX_NEAR_SCALE) : 1.0F;
    }

    private void submitItems(DroneRenderState s, PoseStack ps, SubmitNodeCollector collector, PoseStack.@Nullable Pose unpushed) {
        for (int i = 0; i < this.count; i++) {
            Entry entry = this.entries[i];
            if (entry.display == null || entry.item.isEmpty()) {
                continue;
            }
            int m = entry.mount;
            DroneRenderState.PartSlot body = s.accessories[m];
            ItemDisplays.model(body.position.x(), body.position.y(), body.position.z(), s.accessoryTilt[m], this.gimbal[m], entry.display, MM_TO_UNITS,
                    this.scratch);
            ps.pushPose();
            ps.mulPose(this.scratch);
            if (unpushed != null) {
                keepNormals(ps.last(), unpushed, this.scratch, this.itemNormals);
            }
            entry.item.submit(ps, collector, s.lightCoords, OverlayTexture.NO_OVERLAY, s.outlineColor);
            ps.popPose();
        }
    }

    private static final class Entry {
        private final ItemStackRenderState item = new ItemStackRenderState();
        private int mount = -1;
        private int slot = -1;
        private @Nullable ItemStack stack;
        private @Nullable ItemDisplay display;

        private void clear() {
            this.item.clear();
            this.mount = -1;
            this.slot = -1;
            this.stack = null;
            this.display = null;
        }
    }
}
