package de.lardtom.propwash.guide.client.training;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import de.lardtom.propwash.Constants;
import de.lardtom.propwash.drone.QuadcopterEntity;
import de.lardtom.propwash.drone.client.PilotSession;
import de.lardtom.propwash.guide.training.Course;
import de.lardtom.propwash.guide.training.Goal;
import de.lardtom.propwash.guide.training.Lesson;
import de.lardtom.propwash.guide.training.LessonRun;
import de.lardtom.propwash.guide.training.TrainingFrame;
import de.lardtom.propwash.guide.training.Zone;
import de.lardtom.propwash.race.GateGeometry;
import de.lardtom.propwash.race.RaceLine;
import de.lardtom.propwash.race.client.RaceLineRenderer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.level.LevelRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ARGB;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.util.Util;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

import java.util.List;

public final class TrainingRenderer {
    public static final Identifier WHITE = Constants.id("textures/entity/race/white.png");
    public static final int NEXT = 0xFFFFBB00;
    public static final int UPCOMING = 0xFF9FD8FF;
    public static final int DONE = 0xFF5BC85B;
    public static final int TRICK = 0xFFC792EA;
    public static final int PAD = 0xFF9FD8FF;
    public static final double EDGE = 0.03;
    public static final double GLOW = 0.07;
    public static final int CIRCLE = 40;
    public static final double BEACON = 7.0;
    private static final double MAX_DISTANCE = 192.0;
    private static final double NEAR_FADE_START = 1.0;
    private static final double NEAR_FADE_LENGTH = 4.0;
    private static final float NEAR_FADE_MIN = 0.15F;

    private static @Nullable RenderType type;
    private static long framesDrawn;
    private static int ringsDrawn;
    private static int trickZonesDrawn;
    private static int padsDrawn;

    private TrainingRenderer() {
    }

    public static long framesDrawn() {
        return framesDrawn;
    }

    public static int ringsDrawn() {
        return ringsDrawn;
    }

    public static int trickZonesDrawn() {
        return trickZonesDrawn;
    }

    public static int padsDrawn() {
        return padsDrawn;
    }

    private static RenderType type() {
        if (type == null) {
            type = RenderTypes.eyes(WHITE);
        }
        return type;
    }

    public static void submit(SubmitNodeCollector collector, PoseStack poseStack, LevelRenderState levelState) {
        TrainingSession session = TrainingSession.INSTANCE;
        Course course = session.course();
        LessonRun run = session.run();
        Minecraft minecraft = Minecraft.getInstance();
        Vec3 camera = levelState.cameraRenderState.pos;
        if (course == null || run == null || camera == null || minecraft.gui.hud.isHidden()) {
            return;
        }
        TrainingFrame frame = course.frame();
        if (Math.hypot(frame.x() - camera.x, frame.z() - camera.z) > MAX_DISTANCE) {
            return;
        }
        double seconds = (Util.getNanos() % 3_600_000_000_000L) / 1.0E9;
        float pulse = (float) (0.75 + 0.25 * Math.sin(seconds * Math.PI * 2.0 * 1.2));
        Lesson lesson = course.lesson();
        Goal goal = run.goal();
        boolean success = run.state() == LessonRun.State.SUCCESS;
        int active = run.activeRing();
        List<GateGeometry> rings = course.rings();
        int visible = 0;
        for (int i = 0; i < rings.size(); i++) {
            if (success || !run.ringDone(i)) {
                visible++;
            }
        }
        ringsDrawn = visible;
        int trickZones = 0;
        for (Zone zone : lesson.zones()) {
            if (zone.kind() == Zone.Kind.TRICK) {
                trickZones++;
            }
        }
        trickZonesDrawn = trickZones;
        padsDrawn = lesson.pads().size();
        collector.order(1).submitCustomGeometry(poseStack, type(), (pose, buffer) -> {
            Draw draw = new Draw(pose, buffer, camera);
            for (int i = 0; i < lesson.pads().size(); i++) {
                pad(draw, course, i, goal, pulse, success);
            }
            for (int i = 0; i < lesson.zones().size(); i++) {
                zone(draw, course, i, goal, run, pulse, success);
            }
            for (int i = 0; i < lesson.markers().size(); i++) {
                marker(draw, course, i, goal, run, pulse);
            }
            for (int i = 0; i < lesson.pylons().size(); i++) {
                draw.beam(course.pylonX(i), frame.y(), course.pylonZ(i), course.pylonX(i), frame.y() + course.pylonHeight(i), course.pylonZ(i),
                        0.09 * frame.scale(), ARGB.color(0.7F, TRICK));
            }
            for (int i = 0; i < rings.size(); i++) {
                boolean done = run.ringDone(i);
                if (done && !success) {
                    continue;
                }
                int color;
                float alpha;
                if (success) {
                    color = DONE;
                    alpha = 0.45F;
                } else if (i == active || active < 0 && i == 0) {
                    color = NEXT;
                    alpha = pulse;
                } else {
                    int ahead = active < 0 ? i : i - active;
                    if (goal instanceof Goal.Laps && ahead < 0) {
                        ahead += rings.size();
                    }
                    color = UPCOMING;
                    alpha = ahead <= 1 ? 0.7F : ahead == 2 ? 0.5F : 0.32F;
                }
                ring(draw, rings.get(i), color, alpha, i == active && !success);
            }
        });
        if (!success && !run.finished()) {
            guideLine(collector, poseStack, camera, run, seconds);
        }
        framesDrawn++;
    }

    private static void guideLine(SubmitNodeCollector collector, PoseStack poseStack, Vec3 camera, LessonRun run, double seconds) {
        List<GateGeometry> upcoming = run.upcomingRings();
        PilotSession pilot = PilotSession.INSTANCE;
        QuadcopterEntity drone = pilot.drone();
        if (upcoming.isEmpty() || drone == null) {
            return;
        }
        float partial = Minecraft.getInstance().getDeltaTracker().getGameTimeDeltaPartialTick(false);
        Vec3 anchor = drone.getPosition(partial).add(0.0, drone.getBbHeight() * 0.5, 0.0);
        boolean loop = run.goal() instanceof Goal.Laps;
        int remaining = loop ? RaceLine.ENDLESS : upcoming.size();
        RaceLine.Path path = RaceLine.build(upcoming, 0, RaceLine.DEFAULT_AHEAD, remaining, 0, anchor.x, anchor.y, anchor.z);
        RaceLineRenderer.submitPath(collector, poseStack, camera, path, seconds, 1.0F);
    }

    private static void ring(Draw draw, GateGeometry ring, int rgb, float alpha, boolean next) {
        double half = ring.width() * 0.5;
        double f = ring.frame();
        double h = ring.height();
        double d = ring.depth() * 0.5;
        int core = ARGB.color(Math.clamp(alpha, 0.0F, 1.0F), rgb);
        int glow = ARGB.color(Math.clamp(alpha * 0.28F, 0.0F, 1.0F), rgb);
        bar(draw, ring, -half, -half + f, 0.0, h, d, core);
        bar(draw, ring, half - f, half, 0.0, h, d, core);
        bar(draw, ring, -half + f, half - f, 0.0, f, d, core);
        bar(draw, ring, -half + f, half - f, h - f, h, d, core);
        double g = GLOW;
        bar(draw, ring, -half - g, -half + f + g, -g, h + g, d + g, glow);
        bar(draw, ring, half - f - g, half + g, -g, h + g, d + g, glow);
        bar(draw, ring, -half + f, half - f, -g, f + g, d + g, glow);
        bar(draw, ring, -half + f, half - f, h - f - g, h + g, d + g, glow);
        if (!next) {
            return;
        }
        int veil = ARGB.color(0.06F, rgb);
        double inner = half - f;
        draw.quad(ring.worldX(-inner, f, 0.0), ring.worldY(-inner, f, 0.0), ring.worldZ(-inner, f, 0.0),
                ring.worldX(inner, f, 0.0), ring.worldY(inner, f, 0.0), ring.worldZ(inner, f, 0.0),
                ring.worldX(inner, h - f, 0.0), ring.worldY(inner, h - f, 0.0), ring.worldZ(inner, h - f, 0.0),
                ring.worldX(-inner, h - f, 0.0), ring.worldY(-inner, h - f, 0.0), ring.worldZ(-inner, h - f, 0.0), veil);
    }

    private static void bar(Draw draw, GateGeometry ring, double s0, double s1, double u0, double u1, double d, int color) {
        double[] c = new double[24];
        for (int i = 0; i < 8; i++) {
            double s = (i & 1) == 0 ? s0 : s1;
            double u = (i & 2) == 0 ? u0 : u1;
            double z = (i & 4) == 0 ? -d : d;
            c[i * 3] = ring.worldX(s, u, z);
            c[i * 3 + 1] = ring.worldY(s, u, z);
            c[i * 3 + 2] = ring.worldZ(s, u, z);
        }
        draw.cuboidFaces(c, color);
    }

    private static void zone(Draw draw, Course course, int index, @Nullable Goal goal, LessonRun run, float pulse, boolean success) {
        Zone zone = course.lesson().zones().get(index);
        TrainingFrame frame = course.frame();
        boolean target = goal instanceof Goal.Hover hover && hover.zone() == index || goal instanceof Goal.Heading heading && heading.zone() == index;
        int rgb;
        float alpha;
        if (zone.kind() == Zone.Kind.TRICK) {
            rgb = TRICK;
            alpha = goal instanceof Goal.Tricks || goal instanceof Goal.Combo ? 0.55F : 0.25F;
        } else if (success) {
            rgb = DONE;
            alpha = 0.4F;
        } else if (target && run.inZone()) {
            rgb = DONE;
            alpha = 0.9F;
        } else if (target) {
            rgb = NEXT;
            alpha = pulse;
        } else {
            rgb = UPCOMING;
            alpha = 0.35F;
        }
        double[] c = new double[24];
        for (int i = 0; i < 8; i++) {
            double lx = (i & 1) == 0 ? zone.minX() : zone.maxX();
            double ly = (i & 2) == 0 ? zone.minY() : zone.maxY();
            double lz = (i & 4) == 0 ? zone.minZ() : zone.maxZ();
            c[i * 3] = frame.worldX(lx, lz);
            c[i * 3 + 1] = frame.worldY(ly);
            c[i * 3 + 2] = frame.worldZ(lx, lz);
        }
        int edge = ARGB.color(alpha, rgb);
        int[][] edges = {{0, 1}, {2, 3}, {4, 5}, {6, 7}, {0, 2}, {1, 3}, {4, 6}, {5, 7}, {0, 4}, {1, 5}, {2, 6}, {3, 7}};
        double thick = EDGE * Math.max(0.6, frame.scale());
        for (int[] e : edges) {
            draw.beam(c[e[0] * 3], c[e[0] * 3 + 1], c[e[0] * 3 + 2], c[e[1] * 3], c[e[1] * 3 + 1], c[e[1] * 3 + 2], thick, edge);
        }
        draw.cuboidFaces(c, ARGB.color(alpha * 0.08F, rgb));
    }

    private static void pad(Draw draw, Course course, int index, @Nullable Goal goal, float pulse, boolean success) {
        TrainingFrame frame = course.frame();
        double cx = course.padX(index);
        double cz = course.padZ(index);
        double y = course.padHeight(index) + 0.03;
        double r = course.padRadius(index);
        boolean target = goal instanceof Goal.Land land && land.pad() == index;
        int rgb = success && target ? DONE : index == 0 ? PAD : NEXT;
        float alpha = target ? pulse : index == 0 ? 0.6F : 0.75F;
        int line = ARGB.color(alpha, rgb);
        double thick = 0.035 * Math.max(0.6, frame.scale());
        circle(draw, cx, y, cz, r, thick, line);
        if (index > 0) {
            circle(draw, cx, y, cz, r * 0.53, thick, ARGB.color(alpha * 0.8F, rgb));
            circle(draw, cx, y, cz, r * 0.27, thick, ARGB.color(alpha * 0.8F, rgb));
        }
        int fill = ARGB.color(alpha * 0.12F, rgb);
        for (int i = 0; i < CIRCLE; i++) {
            double a0 = Math.PI * 2.0 * i / CIRCLE;
            double a1 = Math.PI * 2.0 * (i + 1) / CIRCLE;
            draw.quad(cx, y, cz, cx + Math.cos(a0) * r, y, cz + Math.sin(a0) * r, cx + Math.cos(a1) * r, y, cz + Math.sin(a1) * r,
                    cx + Math.cos(a1) * r, y, cz + Math.sin(a1) * r, fill);
        }
        if (target && !success) {
            draw.beam(cx, y, cz, cx, y + BEACON * frame.scale(), cz, 0.06 * frame.scale(), ARGB.color(alpha * 0.45F, rgb));
        }
    }

    private static void circle(Draw draw, double cx, double y, double cz, double r, double thick, int color) {
        for (int i = 0; i < CIRCLE; i++) {
            double a0 = Math.PI * 2.0 * i / CIRCLE;
            double a1 = Math.PI * 2.0 * (i + 1) / CIRCLE;
            draw.beam(cx + Math.cos(a0) * r, y, cz + Math.sin(a0) * r, cx + Math.cos(a1) * r, y, cz + Math.sin(a1) * r, thick, color);
        }
    }

    private static void marker(Draw draw, Course course, int index, @Nullable Goal goal, LessonRun run, float pulse) {
        TrainingFrame frame = course.frame();
        double x = course.markerX(index);
        double z = course.markerZ(index);
        double h = course.markerHeight(index);
        int rgb = UPCOMING;
        float alpha = 0.4F;
        if (goal instanceof Goal.Heading heading) {
            int position = heading.markers().indexOf(index);
            if (position >= 0 && position < run.markerStep()) {
                rgb = DONE;
                alpha = 0.8F;
            } else if (position == run.markerStep()) {
                rgb = NEXT;
                alpha = pulse;
            }
        } else if (run.state() == LessonRun.State.SUCCESS) {
            rgb = DONE;
            alpha = 0.6F;
        }
        double w = 0.12 * frame.scale();
        draw.beam(x, frame.y(), z, x, frame.y() + h, z, w, ARGB.color(alpha, rgb));
        draw.beam(x, frame.y(), z, x, frame.y() + h, z, w * 2.6, ARGB.color(alpha * 0.25F, rgb));
        double cap = 0.35 * frame.scale();
        double top = frame.y() + h;
        double[] c = new double[24];
        for (int i = 0; i < 8; i++) {
            c[i * 3] = x + ((i & 1) == 0 ? -cap : cap);
            c[i * 3 + 1] = top + ((i & 2) == 0 ? -cap : cap);
            c[i * 3 + 2] = z + ((i & 4) == 0 ? -cap : cap);
        }
        draw.cuboidFaces(c, ARGB.color(alpha, rgb));
    }

    private static final class Draw {
        private final PoseStack.Pose pose;
        private final VertexConsumer buffer;
        private final Vec3 camera;

        Draw(PoseStack.Pose pose, VertexConsumer buffer, Vec3 camera) {
            this.pose = pose;
            this.buffer = buffer;
            this.camera = camera;
        }

        void vertex(double x, double y, double z, int color) {
            this.buffer.addVertex(this.pose, (float) (x - this.camera.x), (float) (y - this.camera.y), (float) (z - this.camera.z)).setColor(color)
                    .setUv(0.5F, 0.5F).setOverlay(OverlayTexture.NO_OVERLAY).setLight(LightCoordsUtil.FULL_BRIGHT).setNormal(this.pose, 0.0F, 1.0F, 0.0F);
        }

        void quad(double ax, double ay, double az, double bx, double by, double bz, double cx, double cy, double cz, double dx, double dy,
                  double dz, int color) {
            if (ARGB.alpha(color) == 0) {
                return;
            }
            this.vertex(ax, ay, az, color);
            this.vertex(bx, by, bz, color);
            this.vertex(cx, cy, cz, color);
            this.vertex(dx, dy, dz, color);
            this.vertex(dx, dy, dz, color);
            this.vertex(cx, cy, cz, color);
            this.vertex(bx, by, bz, color);
            this.vertex(ax, ay, az, color);
        }

        void face(double[] c, int a, int b, int d, int e, int color) {
            this.quad(c[a * 3], c[a * 3 + 1], c[a * 3 + 2], c[b * 3], c[b * 3 + 1], c[b * 3 + 2], c[d * 3], c[d * 3 + 1], c[d * 3 + 2],
                    c[e * 3], c[e * 3 + 1], c[e * 3 + 2], color);
        }

        void cuboidFaces(double[] c, int color) {
            this.face(c, 0, 1, 3, 2, color);
            this.face(c, 4, 5, 7, 6, color);
            this.face(c, 0, 1, 5, 4, color);
            this.face(c, 2, 3, 7, 6, color);
            this.face(c, 0, 2, 6, 4, color);
            this.face(c, 1, 3, 7, 5, color);
        }

        void beam(double ax, double ay, double az, double bx, double by, double bz, double half, int color) {
            double dx = bx - ax;
            double dy = by - ay;
            double dz = bz - az;
            double length = Math.sqrt(dx * dx + dy * dy + dz * dz);
            if (length < 1.0E-6 || half <= 0.0) {
                return;
            }
            double along = Math.clamp(((this.camera.x - ax) * dx + (this.camera.y - ay) * dy + (this.camera.z - az) * dz) / (length * length), 0.0,
                    1.0);
            double nearest = Math.sqrt(Math.pow(ax + dx * along - this.camera.x, 2) + Math.pow(ay + dy * along - this.camera.y, 2)
                    + Math.pow(az + dz * along - this.camera.z, 2));
            float fade = (float) Math.clamp((nearest - NEAR_FADE_START) / NEAR_FADE_LENGTH, NEAR_FADE_MIN, 1.0);
            color = ARGB.color(Math.round(ARGB.alpha(color) * fade), color);
            dx /= length;
            dy /= length;
            dz /= length;
            double px;
            double py;
            double pz;
            if (Math.abs(dy) < 0.9) {
                px = -dz;
                py = 0.0;
                pz = dx;
            } else {
                px = 1.0;
                py = 0.0;
                pz = 0.0;
            }
            double pl = Math.sqrt(px * px + py * py + pz * pz);
            px /= pl;
            py /= pl;
            pz /= pl;
            double qx = dy * pz - dz * py;
            double qy = dz * px - dx * pz;
            double qz = dx * py - dy * px;
            double[] c = new double[24];
            for (int i = 0; i < 8; i++) {
                double s = (i & 1) == 0 ? -half : half;
                double t = (i & 2) == 0 ? -half : half;
                boolean end = (i & 4) != 0;
                c[i * 3] = (end ? bx : ax) + px * s + qx * t;
                c[i * 3 + 1] = (end ? by : ay) + py * s + qy * t;
                c[i * 3 + 2] = (end ? bz : az) + pz * s + qz * t;
            }
            this.cuboidFaces(c, color);
        }
    }
}
