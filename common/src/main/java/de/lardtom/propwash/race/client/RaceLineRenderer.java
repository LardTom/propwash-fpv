package de.lardtom.propwash.race.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import de.lardtom.propwash.drone.QuadcopterEntity;
import de.lardtom.propwash.drone.client.FpvView;
import de.lardtom.propwash.drone.client.PilotSession;
import de.lardtom.propwash.race.RaceLine;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.level.LevelRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.util.ARGB;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import org.jspecify.annotations.Nullable;

import java.util.List;

public final class RaceLineRenderer {
    public static final int LINE_COLOR = 0xFFA8E6FF;
    public static final int NEXT_COLOR = GateRenderer.LIGHT_GREEN;
    private static final float CORE_HALF = 0.035F;
    private static final float GLOW_HALF = 0.15F;
    private static final float CORE_ALPHA = 0.9F;
    private static final float GLOW_ALPHA = 0.22F;
    private static final double CHEVRON_SPACING = 2.4;
    private static final double CHEVRON_SPEED = 3.2;
    private static final float CHEVRON_HALF = 0.2F;
    private static final float CHEVRON_LENGTH = 0.22F;
    private static final float CHEVRON_THICKNESS = 0.07F;
    private static final float CHEVRON_ALPHA = 0.95F;
    private static final float CHEVRON_WHITEN = 0.35F;
    private static final double NEAR_CAMERA = 0.9;
    private static final double NEAR_FADE = 2.2;
    private static final double MAX_DISTANCE = 224.0;
    private static final double FAR_FADE = 48.0;
    private static final double SIGHT_SLACK = 0.75;
    private static final float SIGHT_FADE_PER_SECOND = 5.0F;

    private static @Nullable RenderType type;
    private static long framesDrawn;
    private static int lastSamples;
    private static float sight = 1.0F;
    private static long sightNanos;

    private RaceLineRenderer() {
    }

    public static boolean enabled() {
        return RaceConfig.CONFIG.get().raceLine;
    }

    public static boolean active() {
        return enabled() && ClientRaceTracker.INSTANCE.tracker().racing() && !FpvView.spectating();
    }

    public static long framesDrawn() {
        return framesDrawn;
    }

    public static int lastSamples() {
        return lastSamples;
    }

    public static @Nullable Vec3 anchor(float partialTick) {
        PilotSession session = PilotSession.INSTANCE;
        QuadcopterEntity drone = session.isActive() ? session.drone() : null;
        if (drone != null) {
            return drone.getPosition(partialTick).add(0.0, drone.getBbHeight() * 0.5, 0.0);
        }
        return ClientRaceTracker.INSTANCE.lastPosition();
    }

    public static RaceLine.Path currentPath(float partialTick) {
        if (!enabled()) {
            return RaceLine.Path.EMPTY;
        }
        Vec3 anchor = anchor(partialTick);
        if (anchor == null) {
            return RaceLine.Path.EMPTY;
        }
        int ahead = RaceConfig.CONFIG.get().raceLineGates;
        if (active()) {
            return RaceLine.of(ClientRaceTracker.INSTANCE.tracker(), ahead, anchor.x, anchor.y, anchor.z);
        }
        if (!PilotSession.INSTANCE.isActive()) {
            return RaceLine.Path.EMPTY;
        }
        return RaceLine.approach(ClientRaceTracker.INSTANCE.tracker(), ahead, anchor.x, anchor.y, anchor.z);
    }

    public static float sight() {
        return sight;
    }

    public static boolean targetInSight(Level level, Vec3 anchor, RaceLine.Path path) {
        RaceLine.Sample target = null;
        for (RaceLine.Sample sample : path.ahead()) {
            if (Math.abs(sample.gate()) < 1.0E-9) {
                target = sample;
                break;
            }
        }
        if (target == null) {
            return true;
        }
        Vec3 to = new Vec3(target.x(), target.y(), target.z());
        double length = anchor.distanceTo(to);
        if (length < SIGHT_SLACK) {
            return true;
        }
        BlockHitResult hit = level.clip(new ClipContext(anchor, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, CollisionContext.empty()));
        return hit.getType() == HitResult.Type.MISS || hit.getLocation().distanceTo(to) <= SIGHT_SLACK;
    }

    private static void updateSight(Level level, Vec3 anchor, RaceLine.Path path) {
        long now = ClientRaceTracker.INSTANCE.now();
        float dt = sightNanos == 0L ? 1.0F : Math.max(0.0F, Math.min(1.0F, (now - sightNanos) / 1.0E9F));
        sightNanos = now;
        float target = targetInSight(level, anchor, path) ? 1.0F : 0.0F;
        float step = SIGHT_FADE_PER_SECOND * dt;
        sight = sight < target ? Math.min(target, sight + step) : Math.max(target, sight - step);
    }

    private static RenderType type() {
        if (type == null) {
            type = RenderTypes.eyes(BoxDraw.WHITE_TEXTURE);
        }
        return type;
    }

    static void submit(SubmitNodeCollector collector, PoseStack poseStack, LevelRenderState levelState) {
        lastSamples = 0;
        Minecraft minecraft = Minecraft.getInstance();
        if (!enabled() || minecraft.gui.hud.isHidden()) {
            return;
        }
        Vec3 camera = levelState.cameraRenderState.pos;
        if (camera == null) {
            return;
        }
        float partialTick = minecraft.getDeltaTracker().getGameTimeDeltaPartialTick(false);
        RaceLine.Path path = currentPath(partialTick);
        Vec3 anchor = anchor(partialTick);
        if (path.isEmpty() || anchor == null || minecraft.level == null) {
            sight = 1.0F;
            sightNanos = 0L;
            return;
        }
        updateSight(minecraft.level, anchor, path);
        if (sight <= 0.01F) {
            return;
        }
        double seconds = ClientRaceTracker.INSTANCE.now() / 1.0E9;
        submitPath(collector, poseStack, camera, path, seconds, sight);
        framesDrawn++;
        lastSamples = path.ahead().size() + path.behind().size();
    }

    public static void submitPath(SubmitNodeCollector collector, PoseStack poseStack, Vec3 camera, RaceLine.Path path, double seconds,
                                  float fade) {
        if (path.isEmpty() || fade <= 0.01F) {
            return;
        }
        double phase = (seconds * CHEVRON_SPEED) % CHEVRON_SPACING;
        List<RaceLine.Sample> ahead = path.ahead();
        List<RaceLine.Sample> behind = path.behind();
        collector.order(1).submitCustomGeometry(poseStack, type(), (pose, buffer) -> {
            ribbon(pose, buffer, behind, camera, GLOW_HALF, GLOW_ALPHA, fade);
            ribbon(pose, buffer, behind, camera, CORE_HALF, CORE_ALPHA, fade);
            ribbon(pose, buffer, ahead, camera, GLOW_HALF, GLOW_ALPHA, fade);
            ribbon(pose, buffer, ahead, camera, CORE_HALF, CORE_ALPHA, fade);
            chevrons(pose, buffer, ahead, camera, phase, fade);
        });
    }

    private static float visibility(RaceLine.Sample sample, Vec3 camera, float fade) {
        double dx = sample.x() - camera.x;
        double dy = sample.y() - camera.y;
        double dz = sample.z() - camera.z;
        double distance = Math.sqrt(dx * dx + dy * dy + dz * dz);
        double near = RaceLine.smooth((distance - NEAR_CAMERA) / NEAR_FADE);
        double far = 1.0 - RaceLine.smooth((distance - (MAX_DISTANCE - FAR_FADE)) / FAR_FADE);
        return (float) (near * far) * sample.alpha() * fade;
    }

    static int color(float highlight, float alpha) {
        int rgb = ARGB.srgbLerp(Math.max(0.0F, Math.min(1.0F, highlight)), LINE_COLOR, NEXT_COLOR);
        return ARGB.color(Math.max(0.0F, Math.min(1.0F, alpha)), rgb);
    }

    private static void ribbon(PoseStack.Pose pose, VertexConsumer buffer, List<RaceLine.Sample> samples, Vec3 camera, float half, float alpha,
                               float fade) {
        int count = samples.size();
        if (count < 2) {
            return;
        }
        float[] left = new float[count * 3];
        float[] right = new float[count * 3];
        int[] colors = new int[count];
        for (int i = 0; i < count; i++) {
            RaceLine.Sample sample = samples.get(i);
            RaceLine.Sample prev = samples.get(Math.max(0, i - 1));
            RaceLine.Sample next = samples.get(Math.min(count - 1, i + 1));
            double tx = next.x() - prev.x();
            double ty = next.y() - prev.y();
            double tz = next.z() - prev.z();
            double vx = sample.x() - camera.x;
            double vy = sample.y() - camera.y;
            double vz = sample.z() - camera.z;
            double sx = ty * vz - tz * vy;
            double sy = tz * vx - tx * vz;
            double sz = tx * vy - ty * vx;
            double length = Math.sqrt(sx * sx + sy * sy + sz * sz);
            if (length < 1.0E-9) {
                sx = 0.0;
                sy = half;
                sz = 0.0;
            } else {
                sx = sx / length * half;
                sy = sy / length * half;
                sz = sz / length * half;
            }
            float cx = (float) vx;
            float cy = (float) vy;
            float cz = (float) vz;
            left[i * 3] = cx - (float) sx;
            left[i * 3 + 1] = cy - (float) sy;
            left[i * 3 + 2] = cz - (float) sz;
            right[i * 3] = cx + (float) sx;
            right[i * 3 + 1] = cy + (float) sy;
            right[i * 3 + 2] = cz + (float) sz;
            colors[i] = color(sample.highlight(), visibility(sample, camera, fade) * alpha);
        }
        for (int i = 0; i + 1 < count; i++) {
            if (ARGB.alpha(colors[i]) == 0 && ARGB.alpha(colors[i + 1]) == 0) {
                continue;
            }
            int a = i * 3;
            int b = (i + 1) * 3;
            quad(pose, buffer,
                    left[a], left[a + 1], left[a + 2], colors[i],
                    right[a], right[a + 1], right[a + 2], colors[i],
                    right[b], right[b + 1], right[b + 2], colors[i + 1],
                    left[b], left[b + 1], left[b + 2], colors[i + 1]);
        }
    }

    private static void chevrons(PoseStack.Pose pose, VertexConsumer buffer, List<RaceLine.Sample> samples, Vec3 camera, double phase,
                                 float fade) {
        int count = samples.size();
        if (count < 2) {
            return;
        }
        double total = samples.getLast().distance();
        int segment = 0;
        for (double at = phase; at <= total; at += CHEVRON_SPACING) {
            while (segment + 2 < count && samples.get(segment + 1).distance() < at) {
                segment++;
            }
            RaceLine.Sample a = samples.get(segment);
            RaceLine.Sample b = samples.get(segment + 1);
            double span = b.distance() - a.distance();
            double t = span < 1.0E-9 ? 0.0 : Math.max(0.0, Math.min(1.0, (at - a.distance()) / span));
            double px = a.x() + (b.x() - a.x()) * t;
            double py = a.y() + (b.y() - a.y()) * t;
            double pz = a.z() + (b.z() - a.z()) * t;
            RaceLine.Sample here = new RaceLine.Sample(px, py, pz, at, a.gate() + (b.gate() - a.gate()) * t,
                    a.alpha() + (b.alpha() - a.alpha()) * (float) t, a.highlight() + (b.highlight() - a.highlight()) * (float) t);
            float alpha = visibility(here, camera, fade) * CHEVRON_ALPHA;
            if (alpha < 0.02F) {
                continue;
            }
            double tx = b.x() - a.x();
            double ty = b.y() - a.y();
            double tz = b.z() - a.z();
            double tl = Math.sqrt(tx * tx + ty * ty + tz * tz);
            if (tl < 1.0E-9) {
                continue;
            }
            tx /= tl;
            ty /= tl;
            tz /= tl;
            double vx = px - camera.x;
            double vy = py - camera.y;
            double vz = pz - camera.z;
            double sx = ty * vz - tz * vy;
            double sy = tz * vx - tx * vz;
            double sz = tx * vy - ty * vx;
            double sl = Math.sqrt(sx * sx + sy * sy + sz * sz);
            if (sl < 1.0E-9) {
                continue;
            }
            sx /= sl;
            sy /= sl;
            sz /= sl;
            int color = ARGB.srgbLerp(CHEVRON_WHITEN, color(here.highlight(), alpha), ARGB.color(alpha, 0xFFFFFF));
            float cx = (float) vx;
            float cy = (float) vy;
            float cz = (float) vz;
            float tipX = cx + (float) tx * CHEVRON_LENGTH * 0.5F;
            float tipY = cy + (float) ty * CHEVRON_LENGTH * 0.5F;
            float tipZ = cz + (float) tz * CHEVRON_LENGTH * 0.5F;
            float backX = cx - (float) tx * CHEVRON_LENGTH * 0.5F;
            float backY = cy - (float) ty * CHEVRON_LENGTH * 0.5F;
            float backZ = cz - (float) tz * CHEVRON_LENGTH * 0.5F;
            float thX = (float) tx * CHEVRON_THICKNESS;
            float thY = (float) ty * CHEVRON_THICKNESS;
            float thZ = (float) tz * CHEVRON_THICKNESS;
            for (int side = -1; side <= 1; side += 2) {
                float ox = backX + (float) sx * CHEVRON_HALF * side;
                float oy = backY + (float) sy * CHEVRON_HALF * side;
                float oz = backZ + (float) sz * CHEVRON_HALF * side;
                quad(pose, buffer,
                        ox - thX, oy - thY, oz - thZ, color,
                        ox, oy, oz, color,
                        tipX, tipY, tipZ, color,
                        tipX - thX, tipY - thY, tipZ - thZ, color);
            }
        }
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

    static void vertex(PoseStack.Pose pose, VertexConsumer buffer, float x, float y, float z, int color) {
        buffer.addVertex(pose, x, y, z).setColor(color).setUv(0.5F, 0.5F).setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(LightCoordsUtil.FULL_BRIGHT).setNormal(pose, 0.0F, 1.0F, 0.0F);
    }
}
