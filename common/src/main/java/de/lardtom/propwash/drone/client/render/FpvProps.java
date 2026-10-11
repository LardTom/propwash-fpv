package de.lardtom.propwash.drone.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import de.lardtom.propwash.Constants;
import de.lardtom.propwash.build.DroneBuild;
import de.lardtom.propwash.build.PaintChannel;
import de.lardtom.propwash.build.PropType;
import de.lardtom.propwash.compat.iris.client.IrisCompat;
import de.lardtom.propwash.drone.QuadcopterEntity;
import de.lardtom.propwash.drone.client.FpvView;
import de.lardtom.propwash.drone.client.PilotSession;
import de.lardtom.propwash.video.client.SpectateSession;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.world.entity.Entity;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector3fc;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.OptionalInt;

import static de.lardtom.propwash.drone.client.render.DroneVisualScale.MM_TO_UNITS;

public final class FpvProps {
    private static final int MAX_CACHED = 16;
    private static final Map<Key, FpvPropGeometry> GEOMETRY = new HashMap<>();
    private static final Map<DroneBuild, Vector3fc> LENSES = new HashMap<>();
    private static final Map<BlurKey, Shape> BLURS = new HashMap<>();
    private static final int BLUR_SEGMENTS = 64;
    static final float CLIP_DEPTH_MM = 0.25F;
    static final int VERTEX_FLOATS = 10;
    private static final float BLUR_SHADE = 0.85F;
    private static final float BLUR_MID = 0.6F;
    private static final float BLUR_EDGE = 0.85F;
    private static final int BLUR_HUB_ALPHA = 0x58;
    private static final int BLUR_MID_ALPHA = 0x4C;
    private static final int BLUR_EDGE_ALPHA = 0x40;
    private static final int BLUR_RIM_ALPHA = 0x1C;
    private static volatile int maskRgb = -1;
    private static volatile int guardMaskRgb = -1;
    private static int generation = Integer.MIN_VALUE;
    private static boolean warned;
    static int framesDrawn;
    static int propsDrawn;
    static int blursDrawn;
    static int guardFrames;
    static int guardQuadsSubmitted;
    static int shadowPassCalls;

    private FpvProps() {
    }

    public static boolean enabled() {
        DroneRenderConfig config = DroneRenderConfig.get();
        return config.fpvProps || config.fpvDucts;
    }

    public static boolean propsEnabled() {
        return DroneRenderConfig.get().fpvProps;
    }

    public static boolean ductsEnabled() {
        return DroneRenderConfig.get().fpvDucts;
    }

    public static boolean extractsCameraDrone(Entity entity) {
        return entity instanceof QuadcopterEntity drone && enabled() && QuadcopterRenderer.isOwnFpvDrone(drone);
    }

    public static float cameraTilt(QuadcopterEntity drone) {
        if (PilotSession.INSTANCE.isFpv() && PilotSession.INSTANCE.drone() == drone) {
            return PilotSession.INSTANCE.cameraUptiltDeg();
        }
        if (FpvView.spectating() && SpectateSession.INSTANCE.drone() == drone) {
            return SpectateSession.INSTANCE.cameraUptiltDeg(drone);
        }
        return Float.NaN;
    }

    public static synchronized @Nullable FpvPropGeometry geometry(DroneBuild build, float tiltDegrees) {
        if (!Float.isFinite(tiltDegrees)) {
            return null;
        }
        DroneRenderResources res = DroneVisuals.resources();
        if (res.generation() != generation) {
            generation = res.generation();
            GEOMETRY.clear();
            LENSES.clear();
        }
        Key key = new Key(build, Math.round(tiltDegrees * 10.0F), ductsEnabled());
        FpvPropGeometry geometry = GEOMETRY.get(key);
        if (geometry == null) {
            geometry = compute(build, res, key.tenthDegrees() / 10.0F, key.ducts());
            if (GEOMETRY.size() >= MAX_CACHED) {
                GEOMETRY.clear();
            }
            GEOMETRY.put(key, geometry);
        }
        return geometry;
    }

    static FpvPropGeometry compute(DroneBuild build, DroneRenderResources res, float tiltDegrees) {
        return compute(build, res, tiltDegrees, true);
    }

    static FpvPropGeometry compute(DroneBuild build, DroneRenderResources res, float tiltDegrees, boolean ducts) {
        DroneAssembly assembly = res.assembly(build);
        Vector3fc[] hubs = new Vector3fc[DroneLayout.SLOTS];
        for (int slot = 0; slot < DroneLayout.SLOTS; slot++) {
            hubs[slot] = assembly.prop(slot).position();
        }
        FpvGuardMesh guards = ducts ? FpvGuards.mesh(assembly.frame(), build.frame()) : FpvGuardMesh.EMPTY;
        return new FpvPropGeometry(hubs, build.prop().diameterMm * 0.5F, lens(build), tiltDegrees, guards.points());
    }

    public static synchronized Vector3fc lens(DroneBuild build) {
        Vector3fc cached = LENSES.get(build);
        if (cached == null) {
            cached = lensOf(build);
            if (LENSES.size() >= MAX_CACHED) {
                LENSES.clear();
            }
            LENSES.put(build, cached);
        }
        return cached;
    }

    static Vector3fc lensOf(DroneBuild build) {
        return FpvPropGeometry.lens(build);
    }

    public static boolean prepare(DroneRenderState state, QuadcopterEntity drone) {
        return prepare(state, enabled() ? cameraTilt(drone) : Float.NaN);
    }

    static boolean prepare(DroneRenderState state, float tiltDegrees) {
        state.fpvGeometry = null;
        state.fpvGuards = FpvGuardMesh.EMPTY;
        if (!enabled()) {
            return false;
        }
        try {
            state.fpvGeometry = geometry(state.build, tiltDegrees);
            if (state.fpvGeometry != null && ductsEnabled()) {
                state.fpvGuards = FpvGuards.mesh(state.frame, state.build.frame());
            }
        } catch (RuntimeException e) {
            warn(e);
            state.fpvGeometry = null;
            state.fpvGuards = FpvGuardMesh.EMPTY;
        }
        return state.fpvGeometry != null;
    }

    public static void submit(DroneRenderState state, PoseStack ps, SubmitNodeCollector collector, CameraRenderState camera) {
        draw(state, ps, collector, camera, true);
    }

    public static void submitAtCamera(DroneRenderState state, PoseStack ps, SubmitNodeCollector collector, CameraRenderState camera) {
        draw(state, ps, collector, camera, false);
    }

    private static void draw(DroneRenderState state, PoseStack ps, SubmitNodeCollector collector, CameraRenderState camera, boolean fromDrone) {
        if (IrisCompat.renderingShadowPass()) {
            shadowPassCalls++;
            return;
        }
        FpvPropGeometry geometry = state.fpvGeometry;
        if (geometry == null || camera == null) {
            return;
        }
        try {
            Matrix4f projection = camera.projectionMatrix;
            float tanX = projection.m00() > 1.0E-4F ? 1.0F / projection.m00() : FpvPropGeometry.tanHalfFromDegrees(170.0F);
            float tanY = projection.m11() > 1.0E-4F ? 1.0F / projection.m11() : FpvPropGeometry.tanHalfFromDegrees(170.0F);
            tanX *= 1.05F;
            tanY *= 1.05F;
            boolean guardMask = guardMaskRgb >= 0;
            boolean props = false;
            boolean showProps = maskRgb >= 0 || propsEnabled();
            for (int slot = 0; slot < DroneLayout.SLOTS && !props && !guardMask && showProps; slot++) {
                props = geometry.visible(slot, tanX, tanY);
            }
            FpvGuardMesh guards = maskRgb >= 0 ? FpvGuardMesh.EMPTY : state.fpvGuards;
            boolean showGuards = !guards.isEmpty() && geometry.guardsVisible(tanX, tanY);
            if (!props && !showGuards) {
                return;
            }
            ps.pushPose();
            int drawn = 0;
            try {
                if (fromDrone) {
                    ps.translate((float) (camera.pos.x - state.x), (float) (camera.pos.y - state.y), (float) (camera.pos.z - state.z));
                }
                ps.rotate(camera.orientation);
                if (showGuards) {
                    submitGuards(state, ps, collector, geometry, guards);
                }
                if (props) {
                    drawn += submitDiscs(state, ps, collector, geometry, tanX, tanY);
                    float near = geometry.nearScale();
                    ps.scale(near, near, near);
                    ps.rotateDegrees(Axis.XP, -geometry.tiltDegrees());
                    float unit = DroneVisualScale.MODEL_UNIT_SCALE;
                    ps.scale(unit, unit, unit);
                    Vector3fc lens = geometry.lensMm();
                    ps.translate(-lens.x() * MM_TO_UNITS, -lens.y() * MM_TO_UNITS, -lens.z() * MM_TO_UNITS);
                    for (int slot = 0; slot < DroneLayout.SLOTS; slot++) {
                        if (drawsDisc(state, slot) || !geometry.visible(slot, tanX, tanY)) {
                            continue;
                        }
                        DroneRenderState.PartSlot prop = state.props[slot];
                        ps.pushPose();
                        try {
                            Vector3fc hub = prop.position;
                            ps.translate(hub.x() * MM_TO_UNITS, hub.y() * MM_TO_UNITS, hub.z() * MM_TO_UNITS);
                            ps.rotate(Axis.YP, state.rotorAngle[slot]);
                            DroneVisuals.submitPart(prop, ps, collector, state.lightCoords, 0);
                        } finally {
                            ps.popPose();
                        }
                        drawn++;
                    }
                }
            } finally {
                ps.popPose();
            }
            framesDrawn++;
            propsDrawn += drawn;
        } catch (RuntimeException e) {
            warn(e);
        }
    }

    private static void submitGuards(DroneRenderState state, PoseStack ps, SubmitNodeCollector collector, FpvPropGeometry geometry, FpvGuardMesh guards) {
        ps.pushPose();
        try {
            ps.rotateDegrees(Axis.XP, -geometry.tiltDegrees());
            float unit = DroneVisualScale.MODEL_UNIT_SCALE;
            ps.scale(unit, unit, unit);
            Vector3fc lens = geometry.lensMm();
            float tilt = geometry.tiltDegrees();
            float near = geometry.nearScale();
            int key = guardMaskRgb;
            int light = key >= 0 ? LightCoordsUtil.FULL_BRIGHT : state.lightCoords;
            for (FpvGuardMesh.Batch batch : guards.batches()) {
                RenderType type = key >= 0 ? DroneVisuals.solidType() : batch.type();
                collector.submitCustomGeometry(ps, type, (pose, buffer) -> emitGuards(batch, pose, buffer, lens, tilt, near, light, key));
                guardQuadsSubmitted += batch.quads();
            }
            guardFrames++;
        } finally {
            ps.popPose();
        }
    }

    static void emitGuards(FpvGuardMesh.Batch batch, PoseStack.Pose pose, VertexConsumer buffer, Vector3fc lensMm, float tiltDegrees, float nearScale,
                           int light, int keyRgb) {
        double tilt = Math.toRadians(tiltDegrees);
        float sin = (float) Math.sin(tilt);
        float cos = (float) Math.cos(tilt);
        float[] xyz = batch.xyz();
        float[] uv = batch.uv();
        float[] normals = batch.normals();
        int[] argb = batch.argb();
        float[][] quad = new float[4][];
        List<float[]> polygon = new ArrayList<>(8);
        Vector3f normal = new Vector3f();
        int quads = batch.quads();
        for (int q = 0; q < quads; q++) {
            for (int k = 0; k < 4; k++) {
                int p = q * FpvGuardZone.QUAD_FLOATS + k * 3;
                int v = q * 4 + k;
                float x = xyz[p] - lensMm.x();
                float y = xyz[p + 1] - lensMm.y();
                float z = xyz[p + 2] - lensMm.z();
                int color = keyRgb >= 0 ? 0xFF000000 | keyRgb : argb[v];
                float u = keyRgb >= 0 ? 0.5F : uv[v * 2];
                float w = keyRgb >= 0 ? 0.5F : uv[v * 2 + 1];
                quad[k] = vertex(x, y, z, y * sin - z * cos, color, u, w);
            }
            normal.set(normals[q * 3], normals[q * 3 + 1], normals[q * 3 + 2]);
            emitClipped(quad, polygon, normal, pose, buffer, nearScale, light);
        }
    }

    private static float[] vertex(float x, float y, float z, float depth, int argb, float u, float v) {
        return new float[]{x, y, z, depth, argb >>> 24, (argb >> 16) & 0xFF, (argb >> 8) & 0xFF, argb & 0xFF, u, v};
    }

    static void emitClipped(float[][] quad, List<float[]> polygon, Vector3f normal, PoseStack.Pose pose, VertexConsumer buffer, float nearScale,
                            int light) {
        clip(quad, polygon);
        if (polygon.size() < 3) {
            return;
        }
        for (float[] vertex : polygon) {
            float scale = FpvPropGeometry.pushScale(vertex[3], nearScale);
            vertex[0] *= scale;
            vertex[1] *= scale;
            vertex[2] *= scale;
        }
        int n = polygon.size();
        for (int i = 1; i + 1 < n; i += 2) {
            emitVertex(polygon.get(0), pose, buffer, normal, light);
            emitVertex(polygon.get(i), pose, buffer, normal, light);
            emitVertex(polygon.get(i + 1), pose, buffer, normal, light);
            emitVertex(polygon.get(Math.min(i + 2, n - 1)), pose, buffer, normal, light);
        }
    }

    private static boolean drawsDisc(DroneRenderState state, int slot) {
        return maskRgb >= 0 || state.propBlur[slot];
    }

    private static int submitDiscs(DroneRenderState state, PoseStack ps, SubmitNodeCollector collector, FpvPropGeometry geometry, float tanX,
                                   float tanY) {
        int drawn = 0;
        ps.pushPose();
        try {
            ps.rotateDegrees(Axis.XP, -geometry.tiltDegrees());
            float unit = DroneVisualScale.MODEL_UNIT_SCALE;
            ps.scale(unit, unit, unit);
            Vector3fc lens = geometry.lensMm();
            float tilt = geometry.tiltDegrees();
            float near = geometry.nearScale();
            for (int slot = 0; slot < DroneLayout.SLOTS; slot++) {
                if (!drawsDisc(state, slot) || !geometry.visible(slot, tanX, tanY)) {
                    continue;
                }
                int mask = maskRgb;
                Shape disc = mask >= 0 ? mask(state.build.prop(), mask) : blur(state.build.prop(), blurRgb(state, slot));
                int light = mask >= 0 ? LightCoordsUtil.FULL_BRIGHT : state.lightCoords;
                Vector3fc hub = new Vector3f(state.props[slot].position);
                collector.submitCustomGeometry(ps, AfterWaterRenderTypes.fpvDisc(),
                        (pose, buffer) -> emitAtLens(disc, pose, buffer, hub, lens, tilt, near, light));
                if (mask < 0) {
                    blursDrawn++;
                }
                drawn++;
            }
        } finally {
            ps.popPose();
        }
        return drawn;
    }

    static void emitAtLens(Shape shape, PoseStack.Pose pose, VertexConsumer buffer, Vector3fc hubMm, Vector3fc lensMm, float tiltDegrees,
                           float nearScale, int light) {
        double tilt = Math.toRadians(tiltDegrees);
        float sin = (float) Math.sin(tilt);
        float cos = (float) Math.cos(tilt);
        Vector3f p = new Vector3f();
        Vector3f normal = new Vector3f();
        float[][] quad = new float[4][];
        List<float[]> polygon = new ArrayList<>(8);
        for (int v = 0; v + 3 < shape.vertexCount(); v += 4) {
            for (int k = 0; k < 4; k++) {
                shape.position(v + k, p);
                float x = p.x / MM_TO_UNITS + hubMm.x() - lensMm.x();
                float y = p.y / MM_TO_UNITS + hubMm.y() - lensMm.y();
                float z = p.z / MM_TO_UNITS + hubMm.z() - lensMm.z();
                quad[k] = vertex(x, y, z, y * sin - z * cos, shape.color(v + k), 0.5F, 0.5F);
            }
            shape.normal(v, normal);
            emitClipped(quad, polygon, normal, pose, buffer, nearScale, light);
        }
    }

    static void clip(float[][] quad, List<float[]> out) {
        out.clear();
        for (int i = 0; i < quad.length; i++) {
            float[] current = quad[i];
            float[] previous = quad[(i + quad.length - 1) % quad.length];
            boolean currentIn = current[3] >= CLIP_DEPTH_MM;
            boolean previousIn = previous[3] >= CLIP_DEPTH_MM;
            if (currentIn != previousIn) {
                float t = (CLIP_DEPTH_MM - previous[3]) / (current[3] - previous[3]);
                float[] cut = new float[current.length];
                for (int c = 0; c < cut.length; c++) {
                    cut[c] = previous[c] + t * (current[c] - previous[c]);
                }
                cut[3] = CLIP_DEPTH_MM;
                out.add(cut);
            }
            if (currentIn) {
                out.add(current.clone());
            }
        }
    }

    private static void emitVertex(float[] v, PoseStack.Pose pose, VertexConsumer buffer, Vector3f normal, int light) {
        int argb = Math.round(v[4]) << 24 | Math.round(v[5]) << 16 | Math.round(v[6]) << 8 | Math.round(v[7]);
        buffer.addVertex(pose, v[0] * MM_TO_UNITS, v[1] * MM_TO_UNITS, v[2] * MM_TO_UNITS)
                .setColor(argb)
                .setUv(v[8], v[9])
                .setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(light)
                .setNormal(pose, normal.x, normal.y, normal.z);
    }

    static int blurRgb(DroneRenderState state, int slot) {
        OptionalInt painted = state.paint.color(PaintChannel.PROP, slot);
        if (painted.isPresent()) {
            return painted.getAsInt() & 0xFFFFFF;
        }
        PropType prop = state.build.prop();
        for (DroneRenderResources.TintGroup group : DroneVisuals.resources().paintGroups(PartModel.of(prop))) {
            if (group.channel() == PaintChannel.PROP) {
                return group.base() & 0xFFFFFF;
            }
        }
        return FallbackShapes.propColor(prop) & 0xFFFFFF;
    }

    public static void maskColor(int rgb) {
        maskRgb = rgb < 0 ? -1 : rgb & 0xFFFFFF;
    }

    public static int maskColor() {
        return maskRgb;
    }

    public static void guardMaskColor(int rgb) {
        guardMaskRgb = rgb < 0 ? -1 : rgb & 0xFFFFFF;
    }

    public static int guardMaskColor() {
        return guardMaskRgb;
    }

    static synchronized Shape mask(PropType prop, int rgb) {
        return shape(prop, rgb, true);
    }

    static synchronized Shape blur(PropType prop, int rgb) {
        return shape(prop, rgb, false);
    }

    private static Shape shape(PropType prop, int rgb, boolean opaque) {
        BlurKey key = new BlurKey(prop, rgb, opaque);
        Shape shape = BLURS.get(key);
        if (shape == null) {
            shape = opaque ? buildMask(prop, rgb) : buildBlur(prop, rgb);
            if (BLURS.size() >= MAX_CACHED) {
                BLURS.clear();
            }
            BLURS.put(key, shape);
        }
        return shape;
    }

    static Shape buildBlur(PropType prop, int rgb) {
        ShapeBuilder b = new ShapeBuilder(MM_TO_UNITS);
        float radius = prop.diameterMm * 0.5F;
        float hub = FallbackShapes.hubDiameter(prop) * 0.5F;
        float y = FallbackShapes.hubHeight(prop) * 0.55F;
        int shaded = shade(rgb, BLUR_SHADE);
        ring(b, y, hub, radius * BLUR_MID, shaded, BLUR_HUB_ALPHA, BLUR_MID_ALPHA);
        ring(b, y, radius * BLUR_MID, radius * BLUR_EDGE, shaded, BLUR_MID_ALPHA, BLUR_EDGE_ALPHA);
        ring(b, y, radius * BLUR_EDGE, radius, shaded, BLUR_EDGE_ALPHA, BLUR_RIM_ALPHA);
        return b.build();
    }

    static Shape buildMask(PropType prop, int rgb) {
        ShapeBuilder b = new ShapeBuilder(MM_TO_UNITS);
        float radius = prop.diameterMm * 0.5F;
        float hub = FallbackShapes.hubDiameter(prop) * 0.5F;
        float y = FallbackShapes.hubHeight(prop) * 0.55F;
        ring(b, y, hub, radius, rgb, 0xFF, 0xFF);
        return b.build();
    }

    private static void ring(ShapeBuilder b, float y, float inner, float outer, int rgb, int innerAlpha, int outerAlpha) {
        b.annulus(ShapeBuilder.at(0.0F, y, 0.0F).scale(outer * 2.0F, 1.0F, outer * 2.0F), inner / outer, BLUR_SEGMENTS,
                innerAlpha << 24 | rgb, outerAlpha << 24 | rgb);
    }

    private static int shade(int rgb, float factor) {
        int r = Math.round(((rgb >> 16) & 0xFF) * factor);
        int g = Math.round(((rgb >> 8) & 0xFF) * factor);
        int b = Math.round((rgb & 0xFF) * factor);
        return r << 16 | g << 8 | b;
    }

    public static int framesDrawn() {
        return framesDrawn;
    }

    public static int propsDrawn() {
        return propsDrawn;
    }

    public static int blursDrawn() {
        return blursDrawn;
    }

    public static int shadowPassCalls() {
        return shadowPassCalls;
    }

    public static int guardFrames() {
        return guardFrames;
    }

    public static int guardQuadsSubmitted() {
        return guardQuadsSubmitted;
    }

    private static void warn(RuntimeException e) {
        if (!warned) {
            warned = true;
            Constants.LOG.error("Props und Guards im FPV-Bild konnten nicht gezeichnet werden", e);
        }
    }

    private record Key(DroneBuild build, int tenthDegrees, boolean ducts) {
    }

    private record BlurKey(PropType prop, int rgb, boolean opaque) {
    }
}
