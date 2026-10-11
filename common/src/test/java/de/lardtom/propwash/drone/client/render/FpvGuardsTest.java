package de.lardtom.propwash.drone.client.render;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import de.lardtom.propwash.build.DroneBuild;
import de.lardtom.propwash.build.FrameType;
import de.lardtom.propwash.drone.CameraMount;
import de.lardtom.propwash.sim.FpvRig;
import de.lardtom.propwash.video.FovMath;
import org.joml.Matrix3f;
import org.joml.Vector3f;
import org.joml.Vector3fc;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.TreeMap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FpvGuardsTest {
    private static final float ASPECT = 16.0F / 9.0F;
    private static final float[] TILTS = {0.0F, 20.0F, 40.0F};
    private static final double MAX_FOREIGN_FACE_MM2 = 30.0;
    private static final float MIN_LENS_ABOVE_DUCTS_MM = 3.0F;
    private static final double[] MAX_COVER_PERCENT = {30.0, 18.0, 5.0};
    private static final double MAX_UPPER_PERCENT = 0.05;
    private static final int RASTER_X = 192;
    private static final int RASTER_Y = 108;
    private static final String[][] FACES = {
            {"down", "0", "1", "5", "4"}, {"up", "2", "3", "7", "6"}, {"north", "0", "2", "6", "4"},
            {"south", "1", "3", "7", "5"}, {"west", "0", "1", "3", "2"}, {"east", "4", "5", "7", "6"}};

    record Face(String element, float[] quad) {
    }

    static List<Face> faces(FrameType frame) throws IOException {
        JsonObject model = JsonParser.parseString(Files.readString(LayoutFromDefinitionTest.resources()
                .resolve("assets/propwash/models/part/frame/" + frame.key() + ".json"))).getAsJsonObject();
        List<Face> out = new ArrayList<>();
        for (JsonElement entry : model.getAsJsonArray("elements")) {
            JsonObject element = entry.getAsJsonObject();
            float[] from = vec(element.getAsJsonArray("from"));
            float[] to = vec(element.getAsJsonArray("to"));
            Matrix3f rotation = new Matrix3f();
            Vector3f origin = new Vector3f();
            if (element.has("rotation")) {
                JsonObject rot = element.getAsJsonObject("rotation");
                float[] o = vec(rot.getAsJsonArray("origin"));
                origin.set(o[0], o[1], o[2]);
                float x = 0.0F;
                float y = 0.0F;
                float z = 0.0F;
                if (rot.has("axis")) {
                    float angle = rot.get("angle").getAsFloat();
                    switch (rot.get("axis").getAsString()) {
                        case "x" -> x = angle;
                        case "y" -> y = angle;
                        default -> z = angle;
                    }
                } else {
                    x = rot.has("x") ? rot.get("x").getAsFloat() : 0.0F;
                    y = rot.has("y") ? rot.get("y").getAsFloat() : 0.0F;
                    z = rot.has("z") ? rot.get("z").getAsFloat() : 0.0F;
                }
                rotation.rotationZ((float) Math.toRadians(z)).rotateY((float) Math.toRadians(y)).rotateX((float) Math.toRadians(x));
            }
            Vector3f[] corners = new Vector3f[8];
            for (int i = 0; i < 8; i++) {
                Vector3f p = new Vector3f((i & 4) == 0 ? from[0] : to[0], (i & 2) == 0 ? from[1] : to[1], (i & 1) == 0 ? from[2] : to[2]);
                p.sub(origin).mul(rotation).add(origin);
                corners[i] = p.sub(8.0F, 8.0F, 8.0F).mul(DroneVisualScale.MILLIMETRES_PER_MODEL_UNIT);
            }
            JsonObject faces = element.getAsJsonObject("faces");
            for (String[] face : FACES) {
                if (!faces.has(face[0])) {
                    continue;
                }
                float[] quad = new float[FpvGuardZone.QUAD_FLOATS];
                for (int k = 0; k < 4; k++) {
                    Vector3f c = corners[Integer.parseInt(face[k + 1])];
                    quad[k * 3] = c.x;
                    quad[k * 3 + 1] = c.y;
                    quad[k * 3 + 2] = c.z;
                }
                out.add(new Face(element.get("name").getAsString(), quad));
            }
        }
        return out;
    }

    private static float[] vec(JsonArray array) {
        return new float[]{array.get(0).getAsFloat(), array.get(1).getAsFloat(), array.get(2).getAsFloat()};
    }

    private static DroneLayout layout(FrameType frame) throws IOException {
        return LayoutFromDefinitionTest.shippedLayouts().layouts().get(frame);
    }

    private static boolean guardElement(String name) {
        return name.equals("duct") || name.equals("duct_lip") || name.equals("duct_rim");
    }

    @Test
    void zoneTakesEveryDuctFaceOfTheShippedWhoopsAndLittleElse() throws IOException {
        for (FrameType frame : List.of(FrameType.WHOOP_65, FrameType.CINEWHOOP_3)) {
            FpvGuardZone zone = FpvGuards.zone(frame, layout(frame));
            assertTrue(zone != null, frame.key());
            double ductArea = 0.0;
            double foreignArea = 0.0;
            TreeMap<String, Integer> others = new TreeMap<>();
            List<Face> faces = faces(frame);
            boolean[] selected = select(zone, faces);
            for (int i = 0; i < faces.size(); i++) {
                Face face = faces.get(i);
                boolean inside = selected[i];
                double area = area(face.quad());
                if (guardElement(face.element())) {
                    assertTrue(inside, frame.key() + ": Fläche von " + face.element() + " fehlt im Guard");
                    ductArea += area;
                } else if (inside) {
                    others.merge(face.element(), 1, Integer::sum);
                    foreignArea += area;
                    assertTrue(area <= MAX_FOREIGN_FACE_MM2, frame.key() + ": große Fläche von " + face.element() + " im Guard (" + area + " mm²)");
                    assertFalse(face.element().startsWith("canopy") || face.element().startsWith("battery") || face.element().endsWith("plate")
                            || face.element().equals("floor"), frame.key() + ": " + face.element() + " im Guard");
                }
            }
            assertTrue(ductArea > 0.0, frame.key() + ": Modell ohne Ducts");
            assertTrue(foreignArea <= 0.01 * ductArea, String.format(Locale.ROOT, "%s: fremde Flächen %.1f mm² von %.1f mm² im Guard %s",
                    frame.key(), foreignArea, ductArea, others));
        }
    }

    static boolean[] select(FpvGuardZone zone, List<Face> faces) {
        float[] all = new float[faces.size() * FpvGuardZone.QUAD_FLOATS];
        for (int i = 0; i < faces.size(); i++) {
            System.arraycopy(faces.get(i).quad(), 0, all, i * FpvGuardZone.QUAD_FLOATS, FpvGuardZone.QUAD_FLOATS);
        }
        return zone.select(all);
    }

    private static double area(float[] q) {
        Vector3f a = new Vector3f(q[3] - q[0], q[4] - q[1], q[5] - q[2]);
        Vector3f b = new Vector3f(q[9] - q[0], q[10] - q[1], q[11] - q[2]);
        return a.cross(b).length();
    }

    @Test
    void framesWithoutDuctsHaveNoGuards() throws IOException {
        for (FrameType frame : List.of(FrameType.FREESTYLE_5, FrameType.RACE_5, FrameType.LONG_RANGE_7)) {
            assertNull(FpvGuards.zone(frame, layout(frame)), frame.key());
        }
        assertNull(FpvGuardZone.of(true, new Vector3fc[]{new Vector3f()}, 0.0F));
    }

    @Test
    void zoneIsARingAroundEachMotor() {
        FpvGuardZone zone = new FpvGuardZone(new float[]{0.0F, 100.0F}, new float[]{0.0F, 0.0F}, 20.0F);
        assertEquals(19.4F, zone.innerRadiusMm(), 1.0E-4F);
        assertEquals(26.0F, zone.outerRadiusMm(), 1.0E-4F);
        assertTrue(zone.contains(new float[]{21.0F, 0.0F, 0.0F, 21.0F, 5.0F, 0.0F, 0.0F, 0.0F, 22.0F, 0.0F, 5.0F, 22.0F}));
        assertTrue(zone.contains(new float[]{121.0F, 0.0F, 0.0F, 121.0F, 5.0F, 0.0F, 100.0F, 0.0F, 22.0F, 100.0F, 5.0F, 22.0F}));
        assertFalse(zone.contains(new float[]{5.0F, 0.0F, 0.0F, 21.0F, 0.0F, 0.0F, 21.0F, 5.0F, 0.0F, 5.0F, 5.0F, 0.0F}), "Speiche zur Motormitte");
        assertFalse(zone.contains(new float[]{21.0F, 0.0F, 0.0F, 79.0F, 0.0F, 0.0F, 79.0F, 1.0F, 0.0F, 21.0F, 1.0F, 0.0F}), "Brücke zwischen zwei Ducts");
        assertFalse(zone.contains(new float[]{21.0F, 0.0F, 0.0F, 121.0F, 0.0F, 0.0F, 121.0F, 1.0F, 0.0F, 21.0F, 1.0F, 0.0F}), "Ecken an zwei Motoren");
        assertFalse(zone.contains(new float[]{Float.NaN, 0.0F, 0.0F, 21.0F, 0.0F, 0.0F, 21.0F, 1.0F, 0.0F, 21.0F, 1.0F, 0.0F}));
        assertFalse(zone.contains(new float[0]));
    }

    @Test
    void pushKeepsTheDepthOrderAndStaysBehindTheNearPlane() {
        for (float near : new float[]{1.0F, 2.5F, 12.0F}) {
            float previous = 0.0F;
            for (float depth = FpvProps.CLIP_DEPTH_MM; depth < 400.0F; depth += 0.05F) {
                float mapped = depth * FpvPropGeometry.pushScale(depth, near);
                assertTrue(mapped >= FpvPropGeometry.NEAR_FLOOR_MM - 1.0E-3F, depth + " mm → " + mapped);
                assertTrue(mapped > previous, "Reihenfolge bei " + depth + " mm (× " + near + ")");
                previous = mapped;
            }
            assertEquals(near, FpvPropGeometry.pushScale(FpvPropGeometry.NEAR_TARGET_MM / near + 1.0F, near), 1.0E-6F);
            float below = FpvPropGeometry.NEAR_TARGET_MM / near - 1.0E-3F;
            assertEquals(FpvPropGeometry.NEAR_TARGET_MM, below * FpvPropGeometry.pushScale(below, near), 1.0E-2F);
        }
    }

    static float[] guardPoints(FrameType frame) throws IOException {
        FpvGuardZone zone = FpvGuards.zone(frame, layout(frame));
        List<Float> points = new ArrayList<>();
        List<Face> faces = faces(frame);
        boolean[] selected = zone == null ? new boolean[faces.size()] : select(zone, faces);
        for (int i = 0; i < faces.size(); i++) {
            Face face = faces.get(i);
            if (selected[i]) {
                for (float value : face.quad()) {
                    points.add(value);
                }
            }
        }
        float[] out = new float[points.size()];
        for (int i = 0; i < out.length; i++) {
            out[i] = points.get(i);
        }
        return out;
    }

    static FpvPropGeometry geometry(DroneBuild build, float tilt) throws IOException {
        DroneLayout layout = layout(build.frame());
        Vector3fc[] hubs = new Vector3fc[DroneLayout.SLOTS];
        for (int slot = 0; slot < DroneLayout.SLOTS; slot++) {
            hubs[slot] = new Vector3f(layout.motor(slot)).add(0.0F, layout.propHeight(), 0.0F);
        }
        return new FpvPropGeometry(hubs, build.prop().diameterMm * 0.5F, FpvPropGeometry.lens(build), tilt, guardPoints(build.frame()));
    }

    @Test
    void frontDuctsShowInTheImageWithoutUptilt() throws IOException {
        for (DroneBuild build : List.of(DroneBuild.WHOOP, DroneBuild.CINEWHOOP)) {
            float fov = (float) FpvRig.of(build).horizontalFovDeg();
            float tanX = FpvPropGeometry.tanHalfFromDegrees(fov);
            float tanY = FpvPropGeometry.tanHalfFromDegrees(FovMath.verticalFromHorizontal(fov, ASPECT));
            for (float tilt : TILTS) {
                FpvPropGeometry geometry = geometry(build, tilt);
                String label = String.format(Locale.ROOT, "%s bei %.0f° und %.0f°", build.frame().key(), tilt, fov);
                assertTrue(geometry.guardPoints() > 0, label);
                if (tilt == TILTS[0]) {
                    assertTrue(geometry.guardsVisible(tanX, tanY), label + ": keine Ducts im Bild");
                }
                assertTrue(geometry.visibleGuardPoints(tanX, tanY) < geometry.guardPoints() / 2, label + ": hintere Ducts im Bild");
                assertTrue(geometry.nearScale() >= 1.0F && geometry.nearScale() <= FpvPropGeometry.MAX_NEAR_SCALE, label);
            }
            FpvPropGeometry standard = geometry(build, CameraMount.defaultUptilt(build.frame()));
            assertFalse(standard.guardsVisible(FpvPropGeometry.tanHalfFromDegrees(30.0F), FpvPropGeometry.tanHalfFromDegrees(17.0F)),
                    build.frame().key() + ": Ducts schon bei 30° im Bild");
        }
    }

    @Test
    void lensSitsAboveTheDuctsAndDuctsStayInTheLowerCorners() throws IOException {
        for (DroneBuild build : List.of(DroneBuild.WHOOP, DroneBuild.CINEWHOOP)) {
            float[] points = guardPoints(build.frame());
            float top = -Float.MAX_VALUE;
            for (int i = 1; i < points.length; i += 3) {
                top = Math.max(top, points[i]);
            }
            Vector3fc lens = FpvPropGeometry.lens(build);
            assertTrue(lens.y() >= top + MIN_LENS_ABOVE_DUCTS_MM,
                    build.frame().key() + ": Linse " + lens.y() + " mm, Oberkante der Ducts " + top + " mm");
            float fov = (float) FpvRig.of(build).horizontalFovDeg();
            float tanX = FpvPropGeometry.tanHalfFromDegrees(fov);
            float tanY = FpvPropGeometry.tanHalfFromDegrees(FovMath.verticalFromHorizontal(fov, ASPECT));
            List<float[]> quads = guardQuads(build.frame());
            for (int t = 0; t < TILTS.length; t++) {
                double[] cover = coverage(quads, geometry(build, TILTS[t]), tanX, tanY);
                String label = String.format(Locale.ROOT, "%s bei %.0f°: %.1f %% Ducts, %.2f %% in der oberen Hälfte", build.frame().key(), TILTS[t],
                        cover[0], cover[1]);
                assertTrue(cover[0] <= MAX_COVER_PERCENT[t], label);
                assertTrue(cover[1] <= MAX_UPPER_PERCENT, label);
                if (t == 0) {
                    assertTrue(cover[0] > 1.0, label + ": keine Ducts im Bild");
                }
            }
        }
    }

    static List<float[]> guardQuads(FrameType frame) throws IOException {
        FpvGuardZone zone = FpvGuards.zone(frame, layout(frame));
        List<Face> faces = faces(frame);
        boolean[] selected = zone == null ? new boolean[faces.size()] : select(zone, faces);
        List<float[]> out = new ArrayList<>();
        for (int i = 0; i < faces.size(); i++) {
            if (selected[i]) {
                out.add(faces.get(i).quad());
            }
        }
        return out;
    }

    static double[] coverage(List<float[]> quads, FpvPropGeometry geometry, float tanX, float tanY) {
        boolean[] covered = new boolean[RASTER_X * RASTER_Y];
        Vector3f camera = new Vector3f();
        for (float[] quad : quads) {
            List<float[]> polygon = new ArrayList<>();
            float[][] corners = new float[4][];
            for (int k = 0; k < 4; k++) {
                geometry.toCamera(quad[k * 3], quad[k * 3 + 1], quad[k * 3 + 2], camera);
                corners[k] = new float[]{camera.x, camera.y, -camera.z};
            }
            for (int k = 0; k < 4; k++) {
                float[] current = corners[k];
                float[] previous = corners[(k + 3) % 4];
                boolean currentIn = current[2] >= 1.0F;
                if (currentIn != previous[2] >= 1.0F) {
                    float f = (1.0F - previous[2]) / (current[2] - previous[2]);
                    polygon.add(new float[]{previous[0] + f * (current[0] - previous[0]), previous[1] + f * (current[1] - previous[1]), 1.0F});
                }
                if (currentIn) {
                    polygon.add(current);
                }
            }
            if (polygon.size() < 3) {
                continue;
            }
            int n = polygon.size();
            float[] u = new float[n];
            float[] v = new float[n];
            float minU = Float.MAX_VALUE;
            float maxU = -Float.MAX_VALUE;
            float minV = Float.MAX_VALUE;
            float maxV = -Float.MAX_VALUE;
            for (int k = 0; k < n; k++) {
                float[] p = polygon.get(k);
                u[k] = p[0] / (p[2] * tanX);
                v[k] = p[1] / (p[2] * tanY);
                minU = Math.min(minU, u[k]);
                maxU = Math.max(maxU, u[k]);
                minV = Math.min(minV, v[k]);
                maxV = Math.max(maxV, v[k]);
            }
            int x0 = Math.max(0, (int) Math.floor((minU + 1.0F) * 0.5F * RASTER_X));
            int x1 = Math.min(RASTER_X - 1, (int) Math.ceil((maxU + 1.0F) * 0.5F * RASTER_X));
            int y0 = Math.max(0, (int) Math.floor((minV + 1.0F) * 0.5F * RASTER_Y));
            int y1 = Math.min(RASTER_Y - 1, (int) Math.ceil((maxV + 1.0F) * 0.5F * RASTER_Y));
            for (int y = y0; y <= y1; y++) {
                float cv = (y + 0.5F) / RASTER_Y * 2.0F - 1.0F;
                for (int x = x0; x <= x1; x++) {
                    float cu = (x + 0.5F) / RASTER_X * 2.0F - 1.0F;
                    if (inside(u, v, cu, cv)) {
                        covered[y * RASTER_X + x] = true;
                    }
                }
            }
        }
        int total = 0;
        int upper = 0;
        for (int y = 0; y < RASTER_Y; y++) {
            for (int x = 0; x < RASTER_X; x++) {
                if (covered[y * RASTER_X + x]) {
                    total++;
                    if (y >= RASTER_Y / 2) {
                        upper++;
                    }
                }
            }
        }
        double cells = (double) RASTER_X * RASTER_Y;
        return new double[]{100.0 * total / cells, 100.0 * upper / cells};
    }

    private static boolean inside(float[] u, float[] v, float x, float y) {
        boolean in = false;
        for (int i = 0, j = u.length - 1; i < u.length; j = i++) {
            if ((v[i] > y) != (v[j] > y) && x < (u[j] - u[i]) * (y - v[i]) / (v[j] - v[i]) + u[i]) {
                in = !in;
            }
        }
        return in;
    }

    @Test
    void rearDuctsNeverShow() throws IOException {
        for (DroneBuild build : List.of(DroneBuild.WHOOP, DroneBuild.CINEWHOOP)) {
            DroneLayout layout = layout(build.frame());
            float[] all = guardPoints(build.frame());
            List<Float> rear = new ArrayList<>();
            for (int i = 0; i + 2 < all.length; i += 3) {
                if (all[i + 2] > Math.max(layout.motor(DroneLayout.REAR_LEFT).z(), layout.motor(DroneLayout.REAR_RIGHT).z()) - 1.0F) {
                    rear.add(all[i]);
                    rear.add(all[i + 1]);
                    rear.add(all[i + 2]);
                }
            }
            float[] points = new float[rear.size()];
            for (int i = 0; i < points.length; i++) {
                points[i] = rear.get(i);
            }
            Vector3fc[] hubs = new Vector3fc[DroneLayout.SLOTS];
            for (int slot = 0; slot < DroneLayout.SLOTS; slot++) {
                hubs[slot] = new Vector3f(layout.motor(slot)).add(0.0F, layout.propHeight(), 0.0F);
            }
            float tan = FpvPropGeometry.tanHalfFromDegrees(FovMath.MAX_HORIZONTAL_DEGREES);
            for (float tilt : TILTS) {
                FpvPropGeometry geometry = new FpvPropGeometry(hubs, build.prop().diameterMm * 0.5F, FpvPropGeometry.lens(build), tilt, points);
                assertTrue(points.length > 0, build.frame().key());
                assertFalse(geometry.guardsVisible(tan, tan / ASPECT), build.frame().key() + ": hintere Ducts im Bild bei " + tilt + "°");
            }
        }
    }
}
