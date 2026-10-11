package de.lardtom.propwash.race.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import de.lardtom.propwash.drone.client.PilotSession;
import de.lardtom.propwash.drone.QuadcopterEntity;
import de.lardtom.propwash.race.FreeformBlocks;
import de.lardtom.propwash.race.FreeformFill;
import de.lardtom.propwash.race.FreeformRegion;
import de.lardtom.propwash.race.GateEntity;
import de.lardtom.propwash.race.GateGeometry;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.network.chat.Component;
import net.minecraft.util.ARGB;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix3f;
import org.joml.Quaternionf;
import org.jspecify.annotations.Nullable;

import java.util.Map;
import java.util.WeakHashMap;

public final class FreeformGateRenderer {
    public static final int WARNING_COLOR = 0xFFFFA726;
    public static final int IDLE_COLOR = 0xFFB4B4B4;
    public static final long CHECK_INTERVAL_TICKS = 20L;
    public static final float FILL_ALPHA = 0.16F;
    public static final float NEAR = 3.0F;
    public static final float FAR = 12.0F;
    private static final float INSET = 0.06F;
    private static final float CORE = 0.035F;
    private static final float HALO = 0.34F;
    private static final float HALO_ALPHA = 0.42F;
    private static final float WASH = 0.42F;
    private static final float WASH_ALPHA = 0.36F;
    private static final float GAP = 0.014F;
    private static final float CHECKER = 0.5F;
    private static final double DETAIL_DISTANCE = 64.0;
    private static final float WARNING_SIZE = 0.32F;
    private static final double WARNING_DISTANCE = 32.0;
    private static final float FLOOR_LIFT = 0.04F;
    private static final float PLATE_GAP = 0.14F;
    private static final float PLATE_OFFSET = 0.12F;
    private static final Map<GateEntity, Cached> ENTITY_VIEWS = new WeakHashMap<>();
    private static final Map<FreeformRegion, FreeformView> PLAIN_VIEWS = new WeakHashMap<>();

    private record Cached(FreeformRegion region, long checkedAt, FreeformView view) {
    }

    private FreeformGateRenderer() {
    }

    public static FreeformView view(FreeformRegion region) {
        synchronized (PLAIN_VIEWS) {
            return PLAIN_VIEWS.computeIfAbsent(region, FreeformView::of);
        }
    }

    public static FreeformView view(GateEntity entity, FreeformRegion region) {
        Level level = entity.level();
        long now = level.getGameTime();
        synchronized (ENTITY_VIEWS) {
            Cached cached = ENTITY_VIEWS.get(entity);
            if (cached != null && cached.region().equals(region) && now - cached.checkedAt() < CHECK_INTERVAL_TICKS && now >= cached.checkedAt()) {
                return cached.view();
            }
            GateGeometry geometry = entity.geometry();
            FreeformFill.Probe probe = FreeformBlocks.probe(level);
            int plane = FreeformBlocks.plane(geometry);
            int minA = FreeformBlocks.minA(geometry);
            int minB = FreeformBlocks.minB(geometry);
            FreeformFill.Integrity integrity = FreeformFill.check(probe, region, plane, minA, minB);
            FreeformView view = FreeformView.of(region, (oa, ob) -> FreeformFill.outsideOpen(probe, region, plane, minA, minB, oa, ob),
                    integrity.blockedCells());
            ENTITY_VIEWS.put(entity, new Cached(region, now, view));
            return view;
        }
    }

    public static void forget() {
        synchronized (ENTITY_VIEWS) {
            ENTITY_VIEWS.clear();
        }
    }

    static void fill(GateRenderState state, GateGeometry local, @Nullable GateEntity entity, int lightColor, float fill) {
        FreeformRegion region = local.region();
        if (region == null) {
            state.freeform = null;
            return;
        }
        state.freeform = entity == null ? view(region) : view(entity, region);
        state.freeformSign = local.variant() == 0 ? 1.0F : -1.0F;
        state.freeformFill = fill;
        state.lightColor = lightColor;
        state.width = region.width();
        state.height = region.height();
        Quaternionf rotation = state.rotation;
        frame(rotation, region.axis(), state.freeformSign);
    }

    static float approach(GateGeometry geometry, Vec3 camera) {
        double dx = geometry.centerX() - camera.x;
        double dy = geometry.centerY() - camera.y;
        double dz = geometry.centerZ() - camera.z;
        double distance = Math.sqrt(dx * dx + dy * dy + dz * dz);
        return (float) Math.clamp(1.0 - (distance - NEAR) / (FAR - NEAR), 0.0, 1.0);
    }

    static @Nullable Vec3 viewer(float partialTick) {
        PilotSession session = PilotSession.INSTANCE;
        QuadcopterEntity drone = session.isActive() ? session.drone() : null;
        if (drone != null) {
            return drone.getPosition(partialTick);
        }
        Minecraft minecraft = Minecraft.getInstance();
        return minecraft.player == null ? null : minecraft.player.getEyePosition(partialTick);
    }

    static void frame(Quaternionf out, int axis, float sign) {
        float[] f = axisVector(axis, sign);
        float[] u = axisVector(FreeformRegion.bAxis(axis), 1.0F);
        float sx = u[1] * f[2] - u[2] * f[1];
        float sy = u[2] * f[0] - u[0] * f[2];
        float sz = u[0] * f[1] - u[1] * f[0];
        out.setFromNormalized(new Matrix3f(sx, sy, sz, u[0], u[1], u[2], f[0], f[1], f[2])).normalize();
    }

    private static float[] axisVector(int axis, float sign) {
        return new float[]{axis == FreeformRegion.AXIS_X ? sign : 0.0F, axis == FreeformRegion.AXIS_Y ? sign : 0.0F,
                axis == FreeformRegion.AXIS_Z ? sign : 0.0F};
    }

    private record Basis(float sx, float sy, float sz, float ux, float uy, float uz, float nx, float ny, float nz, float ox, float oy, float oz) {
        static Basis of(FreeformRegion region) {
            int axis = region.axis();
            float[] s = axisVector(FreeformRegion.aAxis(axis), 1.0F);
            float[] u = axisVector(FreeformRegion.bAxis(axis), 1.0F);
            float[] n = axisVector(axis, 1.0F);
            float hw = region.width() * 0.5F;
            float hh = region.height() * 0.5F;
            return new Basis(s[0], s[1], s[2], u[0], u[1], u[2], n[0], n[1], n[2], -s[0] * hw - u[0] * hh, -s[1] * hw - u[1] * hh,
                    -s[2] * hw - u[2] * hh);
        }

        float x(float a, float b, float d) {
            return this.ox + this.sx * a + this.ux * b + this.nx * d;
        }

        float y(float a, float b, float d) {
            return this.oy + this.sy * a + this.uy * b + this.ny * d;
        }

        float z(float a, float b, float d) {
            return this.oz + this.sz * a + this.uz * b + this.nz * d;
        }
    }

    static void submit(GateRenderer renderer, GateRenderState state, PoseStack poseStack, SubmitNodeCollector collector) {
        FreeformView view = state.freeform;
        if (view == null) {
            return;
        }
        FreeformRegion region = view.region();
        Basis basis = Basis.of(region);
        float age = state.ageInTicks;
        int color = state.lightColor;
        float pulse = 0.55F + 0.45F * (float) Math.sin(age * 0.35F);
        boolean start = state.marking == GateRenderer.Marking.START;
        boolean detail = state.distanceToCameraSq < DETAIL_DISTANCE * DETAIL_DISTANCE;
        float fill = state.freeformFill;
        collector.order(1).submitCustomGeometry(poseStack, GateRenderer.glowType(), (pose, buffer) -> {
            float[] segments = view.segments();
            for (int i = 0; i < view.segmentCount(); i++) {
                int o = i * FreeformView.SEGMENT_FLOATS;
                boolean leak = segments[o + 6] > 0.5F;
                int core = leak ? ARGB.color(pulse, GateRenderer.LIGHT_RED) : ARGB.opaque(color);
                edge(pose, buffer, basis, segments[o], segments[o + 1], segments[o + 2], segments[o + 3], segments[o + 4], segments[o + 5], core,
                        start && !leak, detail);
            }
            if (fill > 0.01F) {
                int tint = ARGB.color(FILL_ALPHA * fill, color);
                float[] runs = view.runs();
                for (int r = 0; r < view.runCount(); r++) {
                    int o = r * FreeformView.RUN_FLOATS;
                    float a0 = runs[o];
                    float a1 = runs[o + 1];
                    float b0 = runs[o + 2];
                    GateRenderer.quad(pose, buffer,
                            basis.x(a0, b0, 0.0F), basis.y(a0, b0, 0.0F), basis.z(a0, b0, 0.0F), tint,
                            basis.x(a1, b0, 0.0F), basis.y(a1, b0, 0.0F), basis.z(a1, b0, 0.0F), tint,
                            basis.x(a1, b0 + 1.0F, 0.0F), basis.y(a1, b0 + 1.0F, 0.0F), basis.z(a1, b0 + 1.0F, 0.0F), tint,
                            basis.x(a0, b0 + 1.0F, 0.0F), basis.y(a0, b0 + 1.0F, 0.0F), basis.z(a0, b0 + 1.0F, 0.0F), tint);
                }
            }
        });
        float ta = region.targetA() + 0.5F;
        float tb = region.targetB() + 0.5F;
        float sign = state.freeformSign;
        float half = Math.min(0.42F, Math.max(0.16F, view.clearance() * 0.55F));
        poseStack.pushPose();
        if (region.horizontal()) {
            poseStack.translate(basis.x(ta, tb, 0.0F), basis.y(ta, tb, 0.0F), basis.z(ta, tb, 0.0F));
            poseStack.rotate(state.rotation);
            collector.order(1).submitCustomGeometry(poseStack, GateRenderer.glowType(), (pose, buffer) -> {
                for (int i = 0; i < GateRenderer.CHEVRONS; i++) {
                    float z = -(0.55F + 0.7F * i);
                    int shade = ARGB.color(CourseElementRenderer.brightness(GateRenderer.CHEVRONS - 1 - i, age), color);
                    chevron(pose, buffer, z, half, shade);
                }
            });
        } else {
            float floor = view.floorB();
            poseStack.translate(basis.x(ta, floor, 0.0F), basis.y(ta, floor, 0.0F), basis.z(ta, floor, 0.0F));
            poseStack.rotate(state.rotation);
            collector.order(1).submitCustomGeometry(poseStack, GateRenderer.glowType(), (pose, buffer) -> {
                for (int i = 0; i < GateRenderer.CHEVRONS; i++) {
                    float z = -(0.7F + 0.75F * i);
                    int shade = ARGB.color(CourseElementRenderer.brightness(GateRenderer.CHEVRONS - 1 - i, age), color);
                    CourseElementRenderer.chevron(pose, buffer, 0.0F, FLOOR_LIFT, z, 0.0F, 1.0F, shade, half);
                }
            });
        }
        poseStack.popPose();
        if (!view.intact() && state.distanceToCameraSq < WARNING_DISTANCE * WARNING_DISTANCE) {
            poseStack.pushPose();
            poseStack.translate(basis.x(ta, tb, 0.0F), basis.y(ta, tb, 0.0F), basis.z(ta, tb, 0.0F));
            poseStack.rotate(state.rotation);
            int amber = ARGB.color(pulse, WARNING_COLOR);
            collector.order(1).submitCustomGeometry(poseStack, GateRenderer.glowType(), (pose, buffer) -> warning(pose, buffer, amber));
            FormattedCharSequence mark = Component.literal("!").getVisualOrderText();
            int markWidth = Minecraft.getInstance().font.width(mark);
            float scale = WARNING_SIZE * 0.9F / 7.0F;
            renderer.text(poseStack, collector, mark, markWidth, 0.0F, -WARNING_SIZE * 0.12F, -GAP, true, false, scale, ARGB.opaque(WARNING_COLOR));
            renderer.text(poseStack, collector, mark, markWidth, 0.0F, -WARNING_SIZE * 0.12F, GAP, false, false, scale, ARGB.opaque(WARNING_COLOR));
            poseStack.popPose();
        }
        if (state.label != null && state.labelWidth > 0) {
            if (view.columnHeight() >= 2) {
                hangingPlate(renderer, state, poseStack, collector, basis, ta, view.plateB() - PLATE_GAP - CourseElementRenderer.PLATE_HEIGHT * 0.5F, sign,
                        start);
            } else {
                plate(renderer, state, poseStack, collector, basis, ta, view.plateB() + 0.5F, sign, start);
            }
        }
    }

    private static void hangingPlate(GateRenderer renderer, GateRenderState state, PoseStack poseStack, SubmitNodeCollector collector, Basis basis,
                                     float a, float b, float sign, boolean start) {
        float width = start ? 0.96F : CourseElementRenderer.PLATE_WIDTH;
        float height = CourseElementRenderer.PLATE_HEIGHT;
        float depth = CourseElementRenderer.PLATE_DEPTH;
        int color = state.lightColor;
        int light = state.lightCoords;
        float d = -sign * PLATE_OFFSET;
        poseStack.pushPose();
        poseStack.translate(basis.x(a, b, d), basis.y(a, b, d), basis.z(a, b, d));
        poseStack.rotate(state.rotation);
        float half = width * 0.5F;
        float halfH = height * 0.5F;
        float halfD = depth * 0.5F;
        collector.submitCustomGeometry(poseStack, GateRenderer.solidType(), (pose, buffer) -> BoxDraw.box(pose, buffer, -half, -halfH, -halfD, half,
                halfH, halfD, GateRenderer.FRAME_COLOR, light));
        int rim = ARGB.opaque(color);
        int back = ARGB.color(0.75F, GateRenderer.EXIT_COLOR);
        collector.order(1).submitCustomGeometry(poseStack, GateRenderer.glowType(), (pose, buffer) -> {
            GateRenderer.frameBand(pose, buffer, -half, -halfH, half, halfH, 0.05F, -halfD - GAP, rim, rim);
            GateRenderer.frameBand(pose, buffer, -half, -halfH, half, halfH, 0.05F, halfD + GAP, back, back);
        });
        float scale = Math.min(CourseElementRenderer.NUMBER_HEIGHT / 7.0F, (width - 0.12F) / state.labelWidth);
        renderer.text(poseStack, collector, state.label, state.labelWidth, 0.0F, 0.0F, -halfD - GAP, true, false, scale, ARGB.opaque(color));
        renderer.text(poseStack, collector, state.label, state.labelWidth, 0.0F, 0.0F, halfD + GAP, false, false, scale * 0.7F,
                GateRenderer.EXIT_TEXT_COLOR);
        poseStack.popPose();
    }

    private static void plate(GateRenderer renderer, GateRenderState state, PoseStack poseStack, SubmitNodeCollector collector, Basis basis, float a,
                              float b, float sign, boolean start) {
        float width = start ? 0.96F : CourseElementRenderer.PLATE_WIDTH;
        float height = CourseElementRenderer.PLATE_HEIGHT;
        float depth = CourseElementRenderer.PLATE_DEPTH;
        int color = state.lightColor;
        int light = state.lightCoords;
        for (int side = -1; side <= 1; side += 2) {
            boolean entry = side < 0;
            float d = side * sign * 0.5F;
            poseStack.pushPose();
            poseStack.translate(basis.x(a, b, d), basis.y(a, b, d), basis.z(a, b, d));
            poseStack.rotate(state.rotation);
            float back = entry ? -depth : 0.0F;
            float front = entry ? 0.0F : depth;
            float half = width * 0.5F;
            float halfH = height * 0.5F;
            collector.submitCustomGeometry(poseStack, GateRenderer.solidType(), (pose, buffer) -> BoxDraw.box(pose, buffer, -half, -halfH, back, half,
                    halfH, front, GateRenderer.FRAME_COLOR, light));
            float face = entry ? back - GAP : front + GAP;
            int rim = entry ? ARGB.opaque(color) : ARGB.color(0.75F, GateRenderer.EXIT_COLOR);
            collector.order(1).submitCustomGeometry(poseStack, GateRenderer.glowType(), (pose, buffer) -> GateRenderer.frameBand(pose, buffer, -half,
                    -halfH, half, halfH, 0.05F, face, rim, rim));
            float scale = Math.min(CourseElementRenderer.NUMBER_HEIGHT / 7.0F, (width - 0.12F) / state.labelWidth);
            renderer.text(poseStack, collector, state.label, state.labelWidth, 0.0F, 0.0F, face, entry, false, entry ? scale : scale * 0.7F,
                    entry ? ARGB.opaque(color) : GateRenderer.EXIT_TEXT_COLOR);
            poseStack.popPose();
        }
    }

    private static void edge(PoseStack.Pose pose, VertexConsumer buffer, Basis basis, float a0, float b0, float a1, float b1, float inA, float inB,
                             int core, boolean checker, boolean detail) {
        float length = Math.abs(a1 - a0) + Math.abs(b1 - b0);
        float da = length < 1.0E-6F ? 0.0F : (a1 - a0) / length;
        float db = length < 1.0E-6F ? 0.0F : (b1 - b0) / length;
        int pieces = checker ? Math.max(1, Math.round(length / CHECKER)) : 1;
        float step = length / pieces;
        for (int p = 0; p < pieces; p++) {
            if (checker && p % 2 == 1) {
                continue;
            }
            float s0 = step * p;
            float s1 = step * (p + 1);
            tube(pose, buffer, basis, a0 + da * s0, b0 + db * s0, a0 + da * s1, b0 + db * s1, inA, inB, core);
        }
        if (!detail) {
            return;
        }
        int glow = ARGB.color(HALO_ALPHA * ARGB.alpha(core) / 255.0F, core);
        int clear = ARGB.color(0.0F, core);
        float ia = inA * GAP;
        float ib = inB * GAP;
        float oa = inA * HALO;
        float ob = inB * HALO;
        GateRenderer.quad(pose, buffer,
                basis.x(a0 + ia, b0 + ib, 0.0F), basis.y(a0 + ia, b0 + ib, 0.0F), basis.z(a0 + ia, b0 + ib, 0.0F), glow,
                basis.x(a1 + ia, b1 + ib, 0.0F), basis.y(a1 + ia, b1 + ib, 0.0F), basis.z(a1 + ia, b1 + ib, 0.0F), glow,
                basis.x(a1 + oa, b1 + ob, 0.0F), basis.y(a1 + oa, b1 + ob, 0.0F), basis.z(a1 + oa, b1 + ob, 0.0F), clear,
                basis.x(a0 + oa, b0 + ob, 0.0F), basis.y(a0 + oa, b0 + ob, 0.0F), basis.z(a0 + oa, b0 + ob, 0.0F), clear);
        int wash = ARGB.color(WASH_ALPHA * ARGB.alpha(core) / 255.0F, core);
        for (int side = -1; side <= 1; side += 2) {
            float d = side * WASH;
            GateRenderer.quad(pose, buffer,
                    basis.x(a0 + ia, b0 + ib, 0.0F), basis.y(a0 + ia, b0 + ib, 0.0F), basis.z(a0 + ia, b0 + ib, 0.0F), wash,
                    basis.x(a1 + ia, b1 + ib, 0.0F), basis.y(a1 + ia, b1 + ib, 0.0F), basis.z(a1 + ia, b1 + ib, 0.0F), wash,
                    basis.x(a1 + ia, b1 + ib, d), basis.y(a1 + ia, b1 + ib, d), basis.z(a1 + ia, b1 + ib, d), clear,
                    basis.x(a0 + ia, b0 + ib, d), basis.y(a0 + ia, b0 + ib, d), basis.z(a0 + ia, b0 + ib, d), clear);
        }
    }

    private static void tube(PoseStack.Pose pose, VertexConsumer buffer, Basis basis, float a0, float b0, float a1, float b1, float inA, float inB,
                             int color) {
        float near = INSET - CORE;
        float far = INSET + CORE;
        float na0 = a0 + inA * near;
        float nb0 = b0 + inB * near;
        float na1 = a1 + inA * near;
        float nb1 = b1 + inB * near;
        float fa0 = a0 + inA * far;
        float fb0 = b0 + inB * far;
        float fa1 = a1 + inA * far;
        float fb1 = b1 + inB * far;
        for (int side = -1; side <= 1; side += 2) {
            float d = side * CORE;
            GateRenderer.quad(pose, buffer,
                    basis.x(na0, nb0, d), basis.y(na0, nb0, d), basis.z(na0, nb0, d), color,
                    basis.x(na1, nb1, d), basis.y(na1, nb1, d), basis.z(na1, nb1, d), color,
                    basis.x(fa1, fb1, d), basis.y(fa1, fb1, d), basis.z(fa1, fb1, d), color,
                    basis.x(fa0, fb0, d), basis.y(fa0, fb0, d), basis.z(fa0, fb0, d), color);
        }
        GateRenderer.quad(pose, buffer,
                basis.x(fa0, fb0, -CORE), basis.y(fa0, fb0, -CORE), basis.z(fa0, fb0, -CORE), color,
                basis.x(fa1, fb1, -CORE), basis.y(fa1, fb1, -CORE), basis.z(fa1, fb1, -CORE), color,
                basis.x(fa1, fb1, CORE), basis.y(fa1, fb1, CORE), basis.z(fa1, fb1, CORE), color,
                basis.x(fa0, fb0, CORE), basis.y(fa0, fb0, CORE), basis.z(fa0, fb0, CORE), color);
    }

    private static void chevron(PoseStack.Pose pose, VertexConsumer buffer, float z, float half, int color) {
        float length = half * 0.6F;
        float thick = half * 0.42F;
        for (int plane = 0; plane < 2; plane++) {
            for (int side = -1; side <= 1; side += 2) {
                float ox = plane == 0 ? half * side : 0.0F;
                float oy = plane == 1 ? half * side : 0.0F;
                float tipZ = z + length;
                GateRenderer.quad(pose, buffer,
                        ox, oy, z - length, color,
                        ox, oy, z - length + thick, color,
                        0.0F, 0.0F, tipZ + thick, color,
                        0.0F, 0.0F, tipZ, color);
            }
        }
    }

    private static void warning(PoseStack.Pose pose, VertexConsumer buffer, int color) {
        float r = WARNING_SIZE;
        float w = r * 0.16F;
        float[][] corners = {{0.0F, r}, {r * 0.87F, -r * 0.5F}, {-r * 0.87F, -r * 0.5F}};
        for (int i = 0; i < 3; i++) {
            float[] p = corners[i];
            float[] q = corners[(i + 1) % 3];
            float dx = q[0] - p[0];
            float dy = q[1] - p[1];
            float l = (float) Math.sqrt(dx * dx + dy * dy);
            float nx = -dy / l * w;
            float ny = dx / l * w;
            GateRenderer.quad(pose, buffer,
                    p[0], p[1], 0.0F, color,
                    q[0], q[1], 0.0F, color,
                    q[0] + nx, q[1] + ny, 0.0F, color,
                    p[0] + nx, p[1] + ny, 0.0F, color);
        }
    }
}
