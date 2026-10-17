package de.lardtom.propwash.zone.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import de.lardtom.propwash.Constants;
import de.lardtom.propwash.zone.Zone;
import de.lardtom.propwash.zone.ZoneBox;
import de.lardtom.propwash.zone.ZoneShape;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.level.LevelRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ARGB;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

public final class ZoneRenderer {
    public static final Identifier WHITE = Constants.id("textures/entity/race/white.png");
    public static final int RACE_LEGAL = 0x38D6FF;
    public static final int ASSISTED = 0xFFB020;
    public static final int NEUTRAL = 0xD8D8D8;
    public static final int DRAFT = 0x7CFF6B;
    public static final double MAX_DISTANCE = 160.0;
    public static final double GRID_REACH = 14.0;
    public static final double GRID_RADIUS = 7.0;
    private static final double EDGE_HALF = 0.03;
    private static final double EDGE_GROWTH = 0.0012;
    private static final float EDGE_ALPHA = 0.55F;
    private static final float GRID_ALPHA = 0.2F;
    private static final double GRID_HALF = 0.012;
    private static final float FILL_ALPHA = 0.045F;
    private static final double FILL_REACH = 24.0;
    private static final int CIRCLE = 48;
    private static final int PILLARS = 8;
    private static @Nullable RenderType type;
    private static int drawn;
    private static long frames;

    private ZoneRenderer() {
    }

    public static int drawn() {
        return drawn;
    }

    public static long frames() {
        return frames;
    }

    private static RenderType type() {
        if (type == null) {
            type = RenderTypes.eyes(WHITE);
        }
        return type;
    }

    public static int color(Zone zone) {
        if (zone.raceLegal()) {
            return RACE_LEGAL;
        }
        return zone.changesPhysics() ? ASSISTED : NEUTRAL;
    }

    public static void submit(SubmitNodeCollector collector, PoseStack poseStack, LevelRenderState levelState) {
        Minecraft minecraft = Minecraft.getInstance();
        Vec3 camera = levelState.cameraRenderState.pos;
        if (camera == null || minecraft.level == null || minecraft.gui.hud.isHidden()) {
            drawn = 0;
            return;
        }
        ZoneEditor editor = ZoneEditor.current();
        List<Zone> zones = new ArrayList<>();
        for (Zone zone : ClientZones.INSTANCE.zones()) {
            if (editor != null && editor.replaces(zone)) {
                continue;
            }
            if (zone.box().distanceSq(zone.shape(), camera.x, camera.y, camera.z) <= MAX_DISTANCE * MAX_DISTANCE) {
                zones.add(zone);
            }
        }
        Zone draft = editor == null ? null : editor.draft();
        drawn = zones.size();
        frames++;
        if (zones.isEmpty() && draft == null) {
            return;
        }
        collector.order(1).submitCustomGeometry(poseStack, type(), (pose, buffer) -> {
            Draw draw = new Draw(pose, buffer, camera);
            for (Zone zone : zones) {
                zone(draw, zone, color(zone), 1.0F);
            }
            if (draft != null) {
                zone(draw, draft, DRAFT, 1.6F);
            }
        });
    }

    private static void zone(Draw draw, Zone zone, int rgb, float strength) {
        ZoneBox box = zone.box();
        double distance = Math.sqrt(box.distanceSq(zone.shape(), draw.camera.x, draw.camera.y, draw.camera.z));
        float far = (float) Math.clamp(1.0 - distance / MAX_DISTANCE, 0.15, 1.0);
        int edge = ARGB.color(Math.round(255.0F * Math.min(1.0F, EDGE_ALPHA * strength * far)), rgb);
        float fill = distance > FILL_REACH ? 0.0F : (float) (FILL_ALPHA * strength * (1.0 - distance / FILL_REACH));
        int fillColor = ARGB.color(Math.round(255.0F * fill), rgb);
        if (zone.shape() == ZoneShape.CYLINDER) {
            cylinder(draw, box, edge, fillColor, rgb, strength);
        } else {
            box(draw, box, edge, fillColor, rgb, strength);
        }
    }

    private static void box(Draw draw, ZoneBox b, int edge, int fill, int rgb, float strength) {
        double[] xs = {b.minX(), b.maxX()};
        double[] ys = {b.minY(), b.maxY()};
        double[] zs = {b.minZ(), b.maxZ()};
        for (double y : ys) {
            for (double z : zs) {
                draw.line(b.minX(), y, z, b.maxX(), y, z, edge);
            }
            for (double x : xs) {
                draw.line(x, y, b.minZ(), x, y, b.maxZ(), edge);
            }
        }
        for (double x : xs) {
            for (double z : zs) {
                draw.line(x, b.minY(), z, x, b.maxY(), z, edge);
            }
        }
        if (ARGB.alpha(fill) > 0) {
            draw.quad(b.minX(), b.minY(), b.minZ(), b.maxX(), b.minY(), b.minZ(), b.maxX(), b.maxY(), b.minZ(), b.minX(), b.maxY(), b.minZ(), fill);
            draw.quad(b.minX(), b.minY(), b.maxZ(), b.maxX(), b.minY(), b.maxZ(), b.maxX(), b.maxY(), b.maxZ(), b.minX(), b.maxY(), b.maxZ(), fill);
            draw.quad(b.minX(), b.minY(), b.minZ(), b.minX(), b.minY(), b.maxZ(), b.minX(), b.maxY(), b.maxZ(), b.minX(), b.maxY(), b.minZ(), fill);
            draw.quad(b.maxX(), b.minY(), b.minZ(), b.maxX(), b.minY(), b.maxZ(), b.maxX(), b.maxY(), b.maxZ(), b.maxX(), b.maxY(), b.minZ(), fill);
            draw.quad(b.minX(), b.minY(), b.minZ(), b.maxX(), b.minY(), b.minZ(), b.maxX(), b.minY(), b.maxZ(), b.minX(), b.minY(), b.maxZ(), fill);
            draw.quad(b.minX(), b.maxY(), b.minZ(), b.maxX(), b.maxY(), b.minZ(), b.maxX(), b.maxY(), b.maxZ(), b.minX(), b.maxY(), b.maxZ(), fill);
        }
        Vec3 c = draw.camera;
        gridX(draw, b, b.minX(), c, rgb, strength);
        gridX(draw, b, b.maxX(), c, rgb, strength);
        gridZ(draw, b, b.minZ(), c, rgb, strength);
        gridZ(draw, b, b.maxZ(), c, rgb, strength);
        gridY(draw, b, b.minY(), c, rgb, strength);
        gridY(draw, b, b.maxY(), c, rgb, strength);
    }

    private static float gridAlpha(double planeDistance, float strength) {
        if (planeDistance > GRID_REACH) {
            return 0.0F;
        }
        return (float) (GRID_ALPHA * strength * (1.0 - planeDistance / GRID_REACH));
    }

    private static void gridX(Draw draw, ZoneBox b, double x, Vec3 c, int rgb, float strength) {
        float alpha = gridAlpha(Math.abs(c.x - x), strength);
        if (alpha <= 0.0F) {
            return;
        }
        patch(draw, c.z, c.y, b.minZ(), b.maxZ(), b.minY(), b.maxY(), alpha, rgb, (u, v0, v1, color) -> draw.grid(x, v0, u, x, v1, u, color),
                (v, u0, u1, color) -> draw.grid(x, v, u0, x, v, u1, color));
    }

    private static void gridZ(Draw draw, ZoneBox b, double z, Vec3 c, int rgb, float strength) {
        float alpha = gridAlpha(Math.abs(c.z - z), strength);
        if (alpha <= 0.0F) {
            return;
        }
        patch(draw, c.x, c.y, b.minX(), b.maxX(), b.minY(), b.maxY(), alpha, rgb, (u, v0, v1, color) -> draw.grid(u, v0, z, u, v1, z, color),
                (v, u0, u1, color) -> draw.grid(u0, v, z, u1, v, z, color));
    }

    private static void gridY(Draw draw, ZoneBox b, double y, Vec3 c, int rgb, float strength) {
        float alpha = gridAlpha(Math.abs(c.y - y), strength);
        if (alpha <= 0.0F) {
            return;
        }
        patch(draw, c.x, c.z, b.minX(), b.maxX(), b.minZ(), b.maxZ(), alpha, rgb, (u, v0, v1, color) -> draw.grid(u, y, v0, u, y, v1, color),
                (v, u0, u1, color) -> draw.grid(u0, y, v, u1, y, v, color));
    }

    private interface Segment {
        void draw(double at, double from, double to, int color);
    }

    private static void patch(Draw draw, double cu, double cv, double minU, double maxU, double minV, double maxV, float alpha, int rgb,
                              Segment alongV, Segment alongU) {
        double u0 = Math.max(minU, cu - GRID_RADIUS);
        double u1 = Math.min(maxU, cu + GRID_RADIUS);
        double v0 = Math.max(minV, cv - GRID_RADIUS);
        double v1 = Math.min(maxV, cv + GRID_RADIUS);
        if (u0 >= u1 || v0 >= v1) {
            return;
        }
        for (double u = Math.ceil(u0); u <= u1; u += 1.0) {
            double off = Math.abs(u - cu) / GRID_RADIUS;
            double half = GRID_RADIUS * Math.sqrt(Math.max(0.0, 1.0 - off * off));
            double a = Math.max(v0, cv - half);
            double e = Math.min(v1, cv + half);
            if (a < e) {
                alongV.draw(u, a, e, ARGB.color(Math.round(255.0F * alpha * (float) ((1.0 - off) * (1.0 - off))), rgb));
            }
        }
        for (double v = Math.ceil(v0); v <= v1; v += 1.0) {
            double off = Math.abs(v - cv) / GRID_RADIUS;
            double half = GRID_RADIUS * Math.sqrt(Math.max(0.0, 1.0 - off * off));
            double a = Math.max(u0, cu - half);
            double e = Math.min(u1, cu + half);
            if (a < e) {
                alongU.draw(v, a, e, ARGB.color(Math.round(255.0F * alpha * (float) ((1.0 - off) * (1.0 - off))), rgb));
            }
        }
    }

    private static void cylinder(Draw draw, ZoneBox b, int edge, int fill, int rgb, float strength) {
        double cx = b.centerX();
        double cz = b.centerZ();
        double r = b.radius();
        for (int i = 0; i < CIRCLE; i++) {
            double a0 = Math.PI * 2.0 * i / CIRCLE;
            double a1 = Math.PI * 2.0 * (i + 1) / CIRCLE;
            double x0 = cx + Math.cos(a0) * r;
            double z0 = cz + Math.sin(a0) * r;
            double x1 = cx + Math.cos(a1) * r;
            double z1 = cz + Math.sin(a1) * r;
            draw.line(x0, b.minY(), z0, x1, b.minY(), z1, edge);
            draw.line(x0, b.maxY(), z0, x1, b.maxY(), z1, edge);
            if (ARGB.alpha(fill) > 0) {
                draw.quad(x0, b.minY(), z0, x1, b.minY(), z1, x1, b.maxY(), z1, x0, b.maxY(), z0, fill);
            }
        }
        for (int i = 0; i < PILLARS; i++) {
            double a = Math.PI * 2.0 * i / PILLARS;
            draw.line(cx + Math.cos(a) * r, b.minY(), cz + Math.sin(a) * r, cx + Math.cos(a) * r, b.maxY(), cz + Math.sin(a) * r, edge);
        }
        Vec3 c = draw.camera;
        double dx = c.x - cx;
        double dz = c.z - cz;
        double radial = Math.sqrt(dx * dx + dz * dz);
        float alpha = gridAlpha(Math.abs(radial - r), strength);
        if (alpha <= 0.0F || r <= 0.0) {
            return;
        }
        double angle = Math.atan2(dz, dx);
        double arc = GRID_RADIUS / r;
        int steps = (int) Math.ceil(arc * r);
        for (int i = -steps; i <= steps; i++) {
            double a = angle + i / r;
            double off = Math.abs(i / r) / arc;
            if (off > 1.0) {
                continue;
            }
            double half = GRID_RADIUS * Math.sqrt(1.0 - off * off);
            double y0 = Math.max(b.minY(), c.y - half);
            double y1 = Math.min(b.maxY(), c.y + half);
            if (y0 < y1) {
                draw.grid(cx + Math.cos(a) * r, y0, cz + Math.sin(a) * r, cx + Math.cos(a) * r, y1, cz + Math.sin(a) * r,
                        ARGB.color(Math.round(255.0F * alpha * (float) ((1.0 - off) * (1.0 - off))), rgb));
            }
        }
        for (double y = Math.ceil(Math.max(b.minY(), c.y - GRID_RADIUS)); y <= Math.min(b.maxY(), c.y + GRID_RADIUS); y += 1.0) {
            double off = Math.abs(y - c.y) / GRID_RADIUS;
            double halfArc = arc * Math.sqrt(Math.max(0.0, 1.0 - off * off));
            int segments = Math.max(2, (int) Math.ceil(halfArc * r));
            int color = ARGB.color(Math.round(255.0F * alpha * (float) ((1.0 - off) * (1.0 - off))), rgb);
            for (int i = 0; i < segments; i++) {
                double a0 = angle - halfArc + 2.0 * halfArc * i / segments;
                double a1 = angle - halfArc + 2.0 * halfArc * (i + 1) / segments;
                draw.grid(cx + Math.cos(a0) * r, y, cz + Math.sin(a0) * r, cx + Math.cos(a1) * r, y, cz + Math.sin(a1) * r, color);
            }
        }
    }

    private static final class Draw {
        private final PoseStack.Pose pose;
        private final VertexConsumer buffer;
        private final Vec3 camera;

        private Draw(PoseStack.Pose pose, VertexConsumer buffer, Vec3 camera) {
            this.pose = pose;
            this.buffer = buffer;
            this.camera = camera;
        }

        private void vertex(double x, double y, double z, int color) {
            this.buffer.addVertex(this.pose, (float) (x - this.camera.x), (float) (y - this.camera.y), (float) (z - this.camera.z)).setColor(color)
                    .setUv(0.5F, 0.5F).setOverlay(OverlayTexture.NO_OVERLAY).setLight(LightCoordsUtil.FULL_BRIGHT).setNormal(this.pose, 0.0F, 1.0F, 0.0F);
        }

        private void quad(double ax, double ay, double az, double bx, double by, double bz, double cx, double cy, double cz, double dx, double dy,
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

        private void line(double ax, double ay, double az, double bx, double by, double bz, int color) {
            this.line(ax, ay, az, bx, by, bz, color, EDGE_HALF);
        }

        private void grid(double ax, double ay, double az, double bx, double by, double bz, int color) {
            this.line(ax, ay, az, bx, by, bz, color, GRID_HALF);
        }

        private void line(double ax, double ay, double az, double bx, double by, double bz, int color, double base) {
            if (ARGB.alpha(color) == 0) {
                return;
            }
            double dx = bx - ax;
            double dy = by - ay;
            double dz = bz - az;
            double mx = (ax + bx) * 0.5 - this.camera.x;
            double my = (ay + by) * 0.5 - this.camera.y;
            double mz = (az + bz) * 0.5 - this.camera.z;
            double sx = dy * mz - dz * my;
            double sy = dz * mx - dx * mz;
            double sz = dx * my - dy * mx;
            double length = Math.sqrt(sx * sx + sy * sy + sz * sz);
            if (length < 1.0E-9) {
                return;
            }
            double distance = Math.sqrt(mx * mx + my * my + mz * mz);
            double half = base + distance * EDGE_GROWTH;
            sx = sx / length * half;
            sy = sy / length * half;
            sz = sz / length * half;
            this.quad(ax - sx, ay - sy, az - sz, ax + sx, ay + sy, az + sz, bx + sx, by + sy, bz + sz, bx - sx, by - sy, bz - sz, color);
        }
    }
}
