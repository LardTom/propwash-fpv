package de.lardtom.propwash.drone.client.trail;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.renderpearl.api.pipeline.BlendFunction;
import com.mojang.renderpearl.api.pipeline.ColorTargetState;
import com.mojang.renderpearl.api.pipeline.CompareOp;
import com.mojang.renderpearl.api.pipeline.DepthStencilState;
import com.mojang.renderpearl.api.pipeline.PrimitiveTopology;
import com.mojang.renderpearl.api.pipeline.RenderPipeline;
import de.lardtom.propwash.Constants;
import de.lardtom.propwash.compat.flashback.client.ReplayView;
import de.lardtom.propwash.compat.iris.client.IrisCompat;
import de.lardtom.propwash.drone.QuadcopterEntity;
import de.lardtom.propwash.drone.client.render.DroneRenderConfig;
import de.lardtom.propwash.drone.client.render.DroneRenderResources;
import de.lardtom.propwash.drone.client.render.QuadcopterRenderer;
import de.lardtom.propwash.drone.led.DroneLeds;
import de.lardtom.propwash.drone.led.LedColor;
import de.lardtom.propwash.drone.led.LedMode;
import de.lardtom.propwash.drone.led.LedSettings;
import de.lardtom.propwash.drone.trail.TrailBuffer;
import de.lardtom.propwash.drone.trail.TrailColor;
import de.lardtom.propwash.drone.trail.TrailEmitter;
import de.lardtom.propwash.drone.trail.TrailModel;
import de.lardtom.propwash.mixin.drone.client.RenderTypeAccessor;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.BindGroupLayouts;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.rendertype.RenderSetup;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.state.level.LevelRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.ARGB;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3fc;
import org.jspecify.annotations.Nullable;

import java.util.UUID;

public final class TrailRenderer {
    private static final int COLOR_REFRESH_TICKS = 20;
    private static final String IRIS_PROGRAM = "ENTITIES_TRANSLUCENT";
    private static final String IRIS_SHADOW_PROGRAM = "SHADOW_ENTITIES";
    static final RenderPipeline PIPELINE = RenderPipeline.builder()
            .withLocation(Constants.id("pipeline/drone_trail"))
            .withBindGroupLayout(BindGroupLayouts.GLOBALS)
            .withBindGroupLayout(BindGroupLayouts.PROJECTION)
            .withBindGroupLayout(BindGroupLayouts.DYNAMIC_TRANSFORMS)
            .withBindGroupLayout(BindGroupLayouts.FOG)
            .withBindGroupLayout(BindGroupLayouts.SAMPLER0)
            .withVertexShader("core/entity")
            .withFragmentShader("core/entity")
            .withVertexBinding(0, DefaultVertexFormat.ENTITY)
            .withPrimitiveTopology(PrimitiveTopology.QUADS)
            .withShaderDefine("EMISSIVE")
            .withShaderDefine("NO_OVERLAY")
            .withShaderDefine("NO_CARDINAL_LIGHTING")
            .withColorTargetState(new ColorTargetState(BlendFunction.TRANSLUCENT))
            .withDepthStencilState(new DepthStencilState(CompareOp.GREATER_THAN_OR_EQUAL, false))
            .build();
    private static final double GROUND_PROBE = 0.35;
    private static final int POINTS = TrailModel.SAMPLES_PER_TRAIL + 1;
    private static final Trail[] TRAILS = new Trail[TrailModel.MAX_TRAILS];
    private static final float[] PX = new float[POINTS];
    private static final float[] PY = new float[POINTS];
    private static final float[] PZ = new float[POINTS];
    private static final float[] SX = new float[POINTS];
    private static final float[] SY = new float[POINTS];
    private static final float[] SZ = new float[POINTS];
    private static final float[] DIST = new float[POINTS];
    private static final float[] FRESH = new float[POINTS];
    private static final float[] VIS = new float[POINTS];
    private static final BlockPos.MutableBlockPos PROBE = new BlockPos.MutableBlockPos();
    private static final SubmitNodeCollector.CustomGeometryRenderer GEOMETRY = TrailRenderer::emit;

    private static @Nullable RenderType type;
    private static long clockTicks;
    private static int used;
    private static double camX;
    private static double camY;
    private static double camZ;
    private static double frameTime;
    private static double frameRange;
    private static long framesSubmitted;
    private static long shadowPassCalls;
    private static long refusedForCap;
    private static int lastTrailsDrawn;
    private static int lastQuads;

    private TrailRenderer() {
    }

    private static final class Trail {
        private final TrailEmitter emitter = new TrailEmitter(TrailModel.SAMPLES_PER_TRAIL);
        private @Nullable QuadcopterEntity drone;
        private int entityId = -1;
        private int color;
        private int halo;
        private int colorAge = COLOR_REFRESH_TICKS;
        private boolean groundBelow;
        private boolean visible;
        private boolean head;
        private double headX;
        private double headY;
        private double headZ;
        private int pointsDrawn;

        private boolean free() {
            return this.entityId < 0;
        }

        private void release() {
            this.emitter.reset();
            this.drone = null;
            this.entityId = -1;
            this.colorAge = COLOR_REFRESH_TICKS;
            this.groundBelow = false;
            this.visible = false;
            this.head = false;
            this.pointsDrawn = 0;
        }
    }

    public static boolean active() {
        return TrailClient.ruleOn() && TrailConfig.get().enabled;
    }

    static void onDroneTick(QuadcopterEntity drone) {
        if (!active()) {
            return;
        }
        Trail trail = find(drone.getId());
        if (trail == null) {
            if (!drone.isArmed()) {
                return;
            }
            trail = claim(drone);
            if (trail == null) {
                return;
            }
        }
        trail.drone = drone;
        trail.groundBelow = groundBelow(drone);
        if (++trail.colorAge >= COLOR_REFRESH_TICKS) {
            trail.colorAge = 0;
            trail.color = colorOf(drone);
            trail.halo = TrailColor.halo(trail.color);
        }
    }

    static void onClientTick(Minecraft minecraft) {
        ClientLevel level = minecraft.level;
        if (level != null && !minecraft.isPaused() && level.tickRateManager().runsNormally()) {
            clockTicks++;
        }
        if (used == 0) {
            return;
        }
        if (!active() || minecraft.level == null) {
            reset();
            return;
        }
        for (Trail trail : TRAILS) {
            if (trail == null || trail.free()) {
                continue;
            }
            QuadcopterEntity drone = trail.drone;
            boolean gone = drone == null || drone.isRemoved() || drone.level() != minecraft.level;
            if (gone) {
                trail.drone = null;
            }
            if ((gone || !drone.isArmed()) && trail.emitter.isEmpty()) {
                trail.release();
                used--;
            }
        }
    }

    public static void reset() {
        for (Trail trail : TRAILS) {
            if (trail != null) {
                trail.release();
            }
        }
        used = 0;
    }

    private static @Nullable Trail find(int entityId) {
        if (used == 0) {
            return null;
        }
        for (Trail trail : TRAILS) {
            if (trail != null && trail.entityId == entityId) {
                return trail;
            }
        }
        return null;
    }

    private static @Nullable Trail claim(QuadcopterEntity drone) {
        for (int i = 0; i < TRAILS.length; i++) {
            Trail trail = TRAILS[i];
            if (trail == null) {
                trail = new Trail();
                TRAILS[i] = trail;
            }
            if (trail.free()) {
                trail.release();
                trail.entityId = drone.getId();
                trail.drone = drone;
                used++;
                return trail;
            }
        }
        refusedForCap++;
        return null;
    }

    private static boolean groundBelow(QuadcopterEntity drone) {
        if (drone.onGround()) {
            return true;
        }
        AABB box = drone.getBoundingBox();
        double probeY = box.minY - GROUND_PROBE;
        PROBE.set(Mth.floor(drone.getX()), Mth.floor(probeY), Mth.floor(drone.getZ()));
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null || drone.level() != level || !level.isLoaded(PROBE)) {
            return false;
        }
        BlockState state = level.getBlockState(PROBE);
        if (state.isAir()) {
            return false;
        }
        double top = state.getCollisionShape(level, PROBE).max(Direction.Axis.Y);
        return Double.isFinite(top) && PROBE.getY() + top >= probeY;
    }

    static int colorOf(QuadcopterEntity drone) {
        LedColor led = null;
        if (DroneLeds.strip(drone).isPresent() && !drone.ledsOff() && drone.hasBattery()) {
            LedSettings settings = DroneLeds.settings(drone);
            if (settings.mode() != LedMode.RAINBOW) {
                led = settings.color();
            }
        }
        UUID id = drone.getUUID();
        return TrailColor.pick(led, drone.paint(), id.getMostSignificantBits() ^ id.getLeastSignificantBits());
    }

    private static RenderType type() {
        if (type == null) {
            IrisCompat.assignPipeline(PIPELINE, IRIS_PROGRAM, IRIS_SHADOW_PROGRAM);
            type = RenderTypeAccessor.propwash$create("propwash_drone_trail", RenderSetup.builder(PIPELINE)
                    .setOitPipelines(RenderPipelines.OIT_EYES)
                    .withTexture("Sampler0", DroneRenderResources.WHITE_TEXTURE)
                    .sortOnUpload()
                    .createRenderSetup());
        }
        return type;
    }

    public static void submit(SubmitNodeCollector collector, PoseStack poseStack, LevelRenderState levelState) {
        if (IrisCompat.renderingShadowPass()) {
            shadowPassCalls++;
            return;
        }
        if (used == 0 || !active()) {
            lastTrailsDrawn = 0;
            lastQuads = 0;
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        ClientLevel level = minecraft.level;
        Vec3 camera = levelState.cameraRenderState.pos;
        if (level == null || camera == null) {
            return;
        }
        float partialTick = minecraft.getDeltaTracker().getGameTimeDeltaPartialTick(false);
        double now = (clockTicks + (double) partialTick) / 20.0;
        frameTime = now;
        double range = DroneRenderConfig.get().renderDistance * Entity.getViewScale();
        Vector3fc forward = minecraft.gameRenderer.mainCamera().forwardVector();
        int visible = 0;
        for (Trail trail : TRAILS) {
            if (trail == null || trail.free()) {
                continue;
            }
            QuadcopterEntity drone = trail.drone;
            if (drone != null && !drone.isRemoved()) {
                trail.headX = Mth.lerp(partialTick, drone.xOld, drone.getX());
                trail.headY = Mth.lerp(partialTick, drone.yOld, drone.getY()) + drone.getBbHeight() * 0.5;
                trail.headZ = Mth.lerp(partialTick, drone.zOld, drone.getZ());
                boolean landed = TrailModel.landed(trail.groundBelow, trail.emitter.speed());
                trail.emitter.update(now, trail.headX, trail.headY, trail.headZ, TrailModel.emits(drone.isArmed(), landed));
                trail.head = trail.emitter.emitting();
                trail.visible = !trail.emitter.isEmpty() && shows(drone, camera, forward, range);
            } else {
                trail.emitter.update(now, trail.headX, trail.headY, trail.headZ, false);
                trail.head = false;
                trail.visible = !trail.emitter.isEmpty();
            }
            if (trail.visible) {
                visible++;
            }
        }
        if (visible == 0) {
            lastTrailsDrawn = 0;
            lastQuads = 0;
            return;
        }
        camX = camera.x;
        camY = camera.y;
        camZ = camera.z;
        frameRange = range;
        collector.order(1).submitCustomGeometry(poseStack, type(), GEOMETRY);
        framesSubmitted++;
    }

    private static boolean shows(QuadcopterEntity drone, Vec3 camera, Vector3fc forward, double range) {
        if (QuadcopterRenderer.isOwnFpvDrone(drone) || drone.isInvisible()) {
            return false;
        }
        if (ReplayView.hides(drone, camera.x, camera.y, camera.z, forward)) {
            return false;
        }
        double dx = drone.getX() - camera.x;
        double dy = drone.getY() - camera.y;
        double dz = drone.getZ() - camera.z;
        return dx * dx + dy * dy + dz * dz <= range * range;
    }

    private static void emit(PoseStack.Pose pose, VertexConsumer buffer) {
        int trails = 0;
        int quads = 0;
        for (Trail trail : TRAILS) {
            if (trail == null || trail.free() || !trail.visible) {
                if (trail != null) {
                    trail.pointsDrawn = 0;
                }
                continue;
            }
            int count = prepare(trail);
            trail.pointsDrawn = count;
            if (count < 2) {
                continue;
            }
            quads += ribbon(pose, buffer, count, TrailModel.GLOW_HALF_WIDTH, TrailModel.GLOW_MIN_ANGLE, TrailModel.GLOW_ALPHA, trail.halo, false);
            quads += ribbon(pose, buffer, count, TrailModel.CORE_HALF_WIDTH, TrailModel.CORE_MIN_ANGLE, TrailModel.CORE_ALPHA, trail.color, true);
            trails++;
        }
        lastTrailsDrawn = trails;
        lastQuads = quads;
    }

    private static int prepare(Trail trail) {
        TrailBuffer samples = trail.emitter.buffer();
        int count = 0;
        for (int i = 0; i < samples.size() && count < POINTS; i++) {
            point(count++, samples.x(i), samples.y(i), samples.z(i), TrailModel.freshness(frameTime - samples.time(i), samples.life(i)));
        }
        if (trail.head && count > 0 && count < POINTS) {
            point(count, trail.headX, trail.headY, trail.headZ, 1.0F);
            VIS[count] = 0.0F;
            count++;
        }
        for (int i = 0; i < count; i++) {
            int prev = Math.max(0, i - 1);
            int next = Math.min(count - 1, i + 1);
            float tx = PX[next] - PX[prev];
            float ty = PY[next] - PY[prev];
            float tz = PZ[next] - PZ[prev];
            float sx = ty * PZ[i] - tz * PY[i];
            float sy = tz * PX[i] - tx * PZ[i];
            float sz = tx * PY[i] - ty * PX[i];
            float length = (float) Math.sqrt(sx * sx + sy * sy + sz * sz);
            if (length < 1.0E-6F) {
                if (i > 0) {
                    SX[i] = SX[i - 1];
                    SY[i] = SY[i - 1];
                    SZ[i] = SZ[i - 1];
                } else {
                    SX[i] = 0.0F;
                    SY[i] = 1.0F;
                    SZ[i] = 0.0F;
                }
                continue;
            }
            SX[i] = sx / length;
            SY[i] = sy / length;
            SZ[i] = sz / length;
        }
        return count;
    }

    private static void point(int i, double x, double y, double z, float freshness) {
        float px = (float) (x - camX);
        float py = (float) (y - camY);
        float pz = (float) (z - camZ);
        float distance = (float) Math.sqrt(px * px + py * py + pz * pz);
        PX[i] = px;
        PY[i] = py;
        PZ[i] = pz;
        DIST[i] = distance;
        FRESH[i] = freshness;
        VIS[i] = TrailModel.nearFade(distance) * TrailModel.farFade(distance, frameRange);
    }

    private static int ribbon(PoseStack.Pose pose, VertexConsumer buffer, int count, float base, double minAngle, float alpha, int rgb,
                              boolean pulled) {
        int quads = 0;
        float ax0 = 0.0F;
        float ay0 = 0.0F;
        float az0 = 0.0F;
        float ax1 = 0.0F;
        float ay1 = 0.0F;
        float az1 = 0.0F;
        int ac = 0;
        for (int i = 0; i < count; i++) {
            float distance = DIST[i];
            float half = TrailModel.halfWidth(base, minAngle, distance, FRESH[i]);
            float cx = PX[i];
            float cy = PY[i];
            float cz = PZ[i];
            if (pulled && distance > 1.0E-4F) {
                float pull = (float) TrailModel.corePull(distance) / distance;
                cx -= cx * pull;
                cy -= cy * pull;
                cz -= cz * pull;
            }
            float lx = cx - SX[i] * half;
            float ly = cy - SY[i] * half;
            float lz = cz - SZ[i] * half;
            float rx = cx + SX[i] * half;
            float ry = cy + SY[i] * half;
            float rz = cz + SZ[i] * half;
            float a = alpha * TrailModel.alpha(FRESH[i]) * VIS[i];
            int color = ARGB.color(Math.max(0.0F, Math.min(1.0F, a)), rgb);
            if (i > 0 && (ARGB.alpha(ac) > 0 || ARGB.alpha(color) > 0)) {
                quad(pose, buffer, ax0, ay0, az0, ac, ax1, ay1, az1, ac, rx, ry, rz, color, lx, ly, lz, color);
                quads++;
            }
            ax0 = lx;
            ay0 = ly;
            az0 = lz;
            ax1 = rx;
            ay1 = ry;
            az1 = rz;
            ac = color;
        }
        return quads;
    }

    private static void quad(PoseStack.Pose pose, VertexConsumer buffer,
                             float ax, float ay, float az, int ac, float bx, float by, float bz, int bc,
                             float cx, float cy, float cz, int cc, float dx, float dy, float dz, int dc) {
        vertex(pose, buffer, ax, ay, az, ac);
        vertex(pose, buffer, bx, by, bz, bc);
        vertex(pose, buffer, cx, cy, cz, cc);
        vertex(pose, buffer, dx, dy, dz, dc);
        vertex(pose, buffer, dx, dy, dz, dc);
        vertex(pose, buffer, cx, cy, cz, cc);
        vertex(pose, buffer, bx, by, bz, bc);
        vertex(pose, buffer, ax, ay, az, ac);
    }

    private static void vertex(PoseStack.Pose pose, VertexConsumer buffer, float x, float y, float z, int color) {
        buffer.addVertex(pose, x, y, z).setColor(color).setUv(0.5F, 0.5F).setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(LightCoordsUtil.FULL_BRIGHT).setNormal(pose, 0.0F, 1.0F, 0.0F);
    }

    public static int trailsTracked() {
        return used;
    }

    public static int lastTrailsDrawn() {
        return lastTrailsDrawn;
    }

    public static int lastQuads() {
        return lastQuads;
    }

    public static long framesSubmitted() {
        return framesSubmitted;
    }

    public static long shadowPassCalls() {
        return shadowPassCalls;
    }

    public static long refusedForCap() {
        return refusedForCap;
    }

    public static float newestFreshness(int entityId) {
        Trail trail = find(entityId);
        if (trail == null || trail.emitter.isEmpty()) {
            return 0.0F;
        }
        TrailBuffer samples = trail.emitter.buffer();
        int newest = samples.newest();
        return TrailModel.freshness(frameTime - samples.time(newest), samples.life(newest));
    }

    public static int samples(int entityId) {
        Trail trail = find(entityId);
        return trail == null ? 0 : trail.emitter.buffer().size();
    }

    public static boolean drawn(int entityId) {
        Trail trail = find(entityId);
        return trail != null && trail.visible && trail.pointsDrawn >= 2;
    }

    public static boolean emitting(int entityId) {
        Trail trail = find(entityId);
        return trail != null && trail.emitter.emitting();
    }

    public static String describe(int entityId) {
        Trail trail = find(entityId);
        if (trail == null) {
            return "kein Platz";
        }
        return "Proben " + trail.emitter.buffer().size() + ", sendet " + trail.emitter.emitting() + ", Tempo "
                + Math.round(trail.emitter.speed() * 10.0) / 10.0 + ", Boden " + trail.groundBelow + ", sichtbar " + trail.visible;
    }

    public static int color(int entityId) {
        Trail trail = find(entityId);
        return trail == null ? -1 : trail.color;
    }
}
