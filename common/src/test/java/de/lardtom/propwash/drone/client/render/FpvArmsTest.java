package de.lardtom.propwash.drone.client.render;

import de.lardtom.propwash.api.part.PartKind;
import de.lardtom.propwash.build.DroneBuild;
import de.lardtom.propwash.build.FrameType;
import de.lardtom.propwash.build.StackType;
import de.lardtom.propwash.creative.CreativeSpec;
import de.lardtom.propwash.drone.CameraMount;
import de.lardtom.propwash.sim.AirframeSpec;
import de.lardtom.propwash.sim.FpvRig;
import de.lardtom.propwash.video.FovMath;
import org.joml.Vector3f;
import org.joml.Vector3fc;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FpvArmsTest {
    private static final float MM = DroneVisualScale.MM_TO_UNITS;
    private static final float ASPECT = 16.0F / 9.0F;
    private static final Set<String> ARM_PARTS = Set.of("arm", "arm_guard", "motor_pad", "motor_cup", "spoke");
    private static final Set<String> RINGS = Set.of("duct", "duct_lip", "duct_rim");
    private static final List<String> BODY_PARTS = List.of("cam_", "canopy_", "standoff", "top_plate", "bottom_plate", "battery_", "floor",
            "rear_mount", "gps_");
    private static final double MIN_ARM_AREA_SHARE = 0.9;
    private static final double MAX_COVER_PERCENT = 20.0;
    private static final double MAX_UPPER_PERCENT = 1.0;
    private static final float HUB_MARGIN = 0.95F;
    private static final int SEED_CELLS = 3;
    private static final double MOTOR_TOLERANCE_MM = 0.6;
    private static final float[] TILTS = {0.0F, 10.0F, 20.0F, 30.0F, 40.0F, 50.0F, 60.0F};
    private static final float[] FOVS = {100.0F, 120.0F, 130.0F, 150.0F};

    private static DroneLayout layout(FrameType frame) throws IOException {
        return LayoutFromDefinitionTest.shippedLayouts().layouts().get(frame);
    }

    private static float[] quads(List<FpvGuardsTest.Face> faces) {
        float[] all = new float[faces.size() * FpvGuardZone.QUAD_FLOATS];
        for (int i = 0; i < faces.size(); i++) {
            System.arraycopy(faces.get(i).quad(), 0, all, i * FpvGuardZone.QUAD_FLOATS, FpvGuardZone.QUAD_FLOATS);
        }
        return all;
    }

    private static float[] selectedQuads(FpvArmZone zone, float[] all) {
        boolean[] selected = zone.select(all);
        int count = 0;
        for (boolean s : selected) {
            if (s) {
                count++;
            }
        }
        float[] out = new float[count * FpvGuardZone.QUAD_FLOATS];
        int at = 0;
        for (int i = 0; i < selected.length; i++) {
            if (selected[i]) {
                System.arraycopy(all, i * FpvGuardZone.QUAD_FLOATS, out, at, FpvGuardZone.QUAD_FLOATS);
                at += FpvGuardZone.QUAD_FLOATS;
            }
        }
        return out;
    }

    private static float[] shapeQuads(Shape shape) {
        float[] all = new float[shape.vertexCount() / 4 * FpvGuardZone.QUAD_FLOATS];
        Vector3f p = new Vector3f();
        for (int v = 0; v + 3 < shape.vertexCount(); v++) {
            shape.position(v, p);
            all[v * 3] = p.x / MM;
            all[v * 3 + 1] = p.y / MM;
            all[v * 3 + 2] = p.z / MM;
        }
        return all;
    }

    private static double area(float[] q) {
        Vector3f a = new Vector3f(q[3] - q[0], q[4] - q[1], q[5] - q[2]);
        Vector3f b = new Vector3f(q[9] - q[0], q[10] - q[1], q[11] - q[2]);
        return a.cross(b).length();
    }

    private static List<float[]> armAndMotorQuads(DroneBuild build) throws IOException {
        FrameType frame = build.frame();
        DroneLayout layout = layout(frame);
        List<float[]> out = new ArrayList<>();
        float[] arms = selectedQuads(FpvGuards.armZone(frame, layout), quads(FpvGuardsTest.faces(frame)));
        for (int q = 0; q + FpvGuardZone.QUAD_FLOATS <= arms.length; q += FpvGuardZone.QUAD_FLOATS) {
            float[] quad = new float[FpvGuardZone.QUAD_FLOATS];
            System.arraycopy(arms, q, quad, 0, FpvGuardZone.QUAD_FLOATS);
            out.add(quad);
        }
        List<FpvGuardsTest.Face> motor = FpvGuardsTest.faces("assets/propwash/models/part/motor/" + build.motor().key() + ".json");
        for (int slot = 0; slot < DroneLayout.SLOTS; slot++) {
            Vector3fc at = layout.motor(slot);
            for (FpvGuardsTest.Face face : motor) {
                float[] quad = face.quad().clone();
                for (int k = 0; k < 4; k++) {
                    quad[k * 3] += at.x();
                    quad[k * 3 + 1] += at.y();
                    quad[k * 3 + 2] += at.z();
                }
                out.add(quad);
            }
        }
        return out;
    }

    private static Vector3fc[] hubs(DroneBuild build) throws IOException {
        DroneLayout layout = layout(build.frame());
        Vector3fc[] hubs = new Vector3fc[DroneLayout.SLOTS];
        for (int slot = 0; slot < DroneLayout.SLOTS; slot++) {
            hubs[slot] = new Vector3f(layout.motor(slot)).add(0.0F, layout.propHeight(), 0.0F);
        }
        return hubs;
    }

    @Test
    void zoneTakesTheArmsAndPadsOfTheShippedFramesAndNoBodyPart() throws IOException {
        for (DroneBuild build : DroneBuild.PRESETS) {
            FrameType frame = build.frame();
            FpvArmZone zone = FpvGuards.armZone(frame, layout(frame));
            List<FpvGuardsTest.Face> faces = FpvGuardsTest.faces(frame);
            boolean[] selected = zone.select(quads(faces));
            double armArea = 0.0;
            double takenArea = 0.0;
            for (int i = 0; i < faces.size(); i++) {
                FpvGuardsTest.Face face = faces.get(i);
                String label = frame.key() + ": " + face.element();
                if (ARM_PARTS.contains(face.element())) {
                    double area = area(face.quad());
                    armArea += area;
                    takenArea += selected[i] ? area : 0.0;
                    if (face.element().startsWith("motor_")) {
                        assertTrue(selected[i], label + " fehlt");
                    }
                }
                if (RINGS.contains(face.element())) {
                    assertFalse(selected[i], label + " gehört zum Schalter Ducts");
                }
                for (String body : BODY_PARTS) {
                    assertFalse(selected[i] && face.element().startsWith(body), label + " wäre eine Wand vor der Linse");
                }
            }
            assertTrue(takenArea >= MIN_ARM_AREA_SHARE * armArea, String.format(Locale.ROOT, "%s: nur %.0f von %.0f mm² Armfläche", frame.key(),
                    takenArea, armArea));
        }
    }

    @Test
    void everyMotorOfAShippedFrameHangsOnAnArm() throws IOException {
        for (DroneBuild build : DroneBuild.PRESETS) {
            FrameType frame = build.frame();
            FpvArmZone zone = FpvGuards.armZone(frame, layout(frame));
            boolean[] attached = zone.attached(selectedQuads(zone, quads(FpvGuardsTest.faces(frame))));
            for (int slot = 0; slot < DroneLayout.SLOTS; slot++) {
                assertTrue(attached[slot], frame.key() + ": Motor " + slot + " ohne Arm");
            }
        }
    }

    @Test
    void fallbackFramesOfAddonsKeepArmsAndLoseTheirBody() {
        for (FrameType frame : RenderTestParts.FRAMES) {
            DroneLayout layout = DroneLayout.fromDefinition(frame);
            FpvArmZone zone = FpvGuards.armZone(frame, layout);
            float[] selected = selectedQuads(zone, shapeQuads(FallbackShapes.buildFrame(frame, layout)));
            boolean[] attached = zone.attached(selected);
            for (int slot = 0; slot < DroneLayout.SLOTS; slot++) {
                assertTrue(attached[slot], frame.key() + ": Motor " + slot + " ohne Arm");
            }
            for (int i = 1; i < selected.length; i += 3) {
                assertTrue(selected[i] <= zone.topMm() + 1.0E-3F, frame.key() + ": Fläche über den Motoren");
            }
        }
    }

    @Test
    void motorWithoutAnyQuadNearItIsNotAttached() {
        Vector3fc[] motors = {new Vector3f(-80.0F, 0.0F, -80.0F), new Vector3f(80.0F, 0.0F, -80.0F), new Vector3f(-80.0F, 0.0F, 80.0F),
                new Vector3f(80.0F, 0.0F, 80.0F)};
        FpvArmZone zone = new FpvArmZone(motors, null);
        boolean[] none = zone.attached(new float[0]);
        boolean[] one = zone.attached(new float[]{-20.0F, -3.0F, -20.0F, -80.0F, -3.0F, -80.0F, -82.0F, -3.0F, -78.0F, -22.0F, -3.0F, -18.0F});
        for (int slot = 0; slot < DroneLayout.SLOTS; slot++) {
            assertFalse(none[slot], "ohne Flächen, Motor " + slot);
            assertEquals(slot == 0, one[slot], "Arm nur an Motor 0, Motor " + slot);
        }
    }

    @Test
    void zoneJudgesBodyPartsByGeometry() {
        Vector3fc[] motors = {new Vector3f(-80.0F, 0.0F, -80.0F), new Vector3f(80.0F, 0.0F, -80.0F), new Vector3f(-80.0F, 0.0F, 80.0F),
                new Vector3f(80.0F, 0.0F, 80.0F)};
        FpvArmZone zone = new FpvArmZone(motors, null);
        assertTrue(zone.contains(new float[]{-20.0F, -3.0F, -20.0F, -80.0F, -3.0F, -80.0F, -82.0F, -3.0F, -78.0F, -22.0F, -3.0F, -18.0F}), "Arm");
        assertFalse(zone.contains(new float[]{-16.0F, -3.0F, -50.0F, 16.0F, -3.0F, -50.0F, 16.0F, -3.0F, 50.0F, -16.0F, -3.0F, 50.0F}),
                "Bodenplatte quer über die Mitte");
        assertFalse(zone.contains(new float[]{-12.0F, -3.0F, -30.0F, -12.0F, 20.0F, -30.0F, -12.0F, 20.0F, -50.0F, -12.0F, -3.0F, -50.0F}),
                "Kamerawange ragt über die Motoren");
        assertFalse(zone.contains(new float[]{-12.0F, -3.0F, -30.0F, -14.0F, -3.0F, -30.0F, -14.0F, -3.0F, -50.0F, -12.0F, -3.0F, -50.0F}),
                "Fläche am Rumpf erreicht den Arm nicht");
        assertFalse(zone.contains(new float[]{Float.NaN, -3.0F, -20.0F, -80.0F, -3.0F, -80.0F, -82.0F, -3.0F, -78.0F, -22.0F, -3.0F, -18.0F}), "NaN");
    }

    @Test
    void armsCoverLittleAndNothingInTheUpperImage() throws IOException {
        for (DroneBuild build : DroneBuild.PRESETS) {
            List<float[]> quads = armAndMotorQuads(build);
            Vector3fc lens = FpvPropGeometry.lens(build);
            float fov = (float) FpvRig.of(build).horizontalFovDeg();
            float tanX = FpvPropGeometry.tanHalfFromDegrees(fov);
            float tanY = FpvPropGeometry.tanHalfFromDegrees(FovMath.verticalFromHorizontal(fov, ASPECT));
            for (float tilt : TILTS) {
                FpvPropGeometry geometry = new FpvPropGeometry(hubs(build), build.prop().diameterMm * 0.5F, lens, tilt);
                double[] cover = FpvGuardsTest.coverage(quads, geometry, tanX, tanY);
                String label = String.format(Locale.ROOT, "%s bei %.0f° und %.0f°: %.1f %% im Bild, %.2f %% in der oberen Hälfte", build.frame().key(), tilt, fov,
                        cover[0], cover[1]);
                assertTrue(cover[0] <= MAX_COVER_PERCENT, label);
                assertTrue(cover[1] <= MAX_UPPER_PERCENT, label);
            }
        }
    }

    @Test
    void everyVisibleHubReachesTheImageEdgeOverMotorAndArm() throws IOException {
        int checked = 0;
        for (DroneBuild build : DroneBuild.PRESETS) {
            List<float[]> quads = armAndMotorQuads(build);
            Vector3fc lens = FpvPropGeometry.lens(build);
            Vector3fc[] hubs = hubs(build);
            for (float fov : FOVS) {
                float tanX = FpvPropGeometry.tanHalfFromDegrees(fov);
                float tanY = FpvPropGeometry.tanHalfFromDegrees(FovMath.verticalFromHorizontal(fov, ASPECT));
                for (float tilt : TILTS) {
                    FpvPropGeometry geometry = new FpvPropGeometry(hubs, build.prop().diameterMm * 0.5F, lens, tilt);
                    boolean[] covered = FpvGuardsTest.raster(quads, geometry, tanX, tanY);
                    Vector3f camera = new Vector3f();
                    for (int slot = 0; slot < DroneLayout.SLOTS; slot++) {
                        geometry.toCamera(hubs[slot].x(), hubs[slot].y(), hubs[slot].z(), camera);
                        float depth = -camera.z;
                        if (depth <= FpvPropGeometry.MIN_DEPTH_MM) {
                            continue;
                        }
                        float u = camera.x / (depth * tanX);
                        float v = camera.y / (depth * tanY);
                        if (Math.abs(u) >= HUB_MARGIN || Math.abs(v) >= HUB_MARGIN) {
                            continue;
                        }
                        checked++;
                        assertTrue(reachesEdge(covered, u, v), String.format(Locale.ROOT, "%s, Prop %d bei %.0f° und %.0f° Sichtfeld schwebt", build.frame().key(), slot, tilt, fov));
                    }
                }
            }
        }
        assertTrue(checked >= 10, "nur " + checked + " Naben im Bild");
    }

    private static boolean reachesEdge(boolean[] covered, float u, float v) {
        int width = FpvGuardsTest.RASTER_X;
        int height = FpvGuardsTest.RASTER_Y;
        int cx = Math.round((u + 1.0F) * 0.5F * width);
        int cy = Math.round((v + 1.0F) * 0.5F * height);
        boolean[] seen = new boolean[covered.length];
        ArrayDeque<Integer> open = new ArrayDeque<>();
        for (int y = Math.max(0, cy - SEED_CELLS); y <= Math.min(height - 1, cy + SEED_CELLS); y++) {
            for (int x = Math.max(0, cx - SEED_CELLS); x <= Math.min(width - 1, cx + SEED_CELLS); x++) {
                if (covered[y * width + x]) {
                    seen[y * width + x] = true;
                    open.add(y * width + x);
                }
            }
        }
        while (!open.isEmpty()) {
            int at = open.poll();
            int x = at % width;
            int y = at / width;
            if (x == 0 || y == 0 || x == width - 1 || y == height - 1) {
                return true;
            }
            for (int dy = -1; dy <= 1; dy++) {
                for (int dx = -1; dx <= 1; dx++) {
                    int next = (y + dy) * width + x + dx;
                    if (covered[next] && !seen[next]) {
                        seen[next] = true;
                        open.add(next);
                    }
                }
            }
        }
        return false;
    }

    @Test
    void hubsSitOnTheMotorsOfTheFlightModel() throws IOException {
        for (DroneBuild build : DroneBuild.PRESETS) {
            checkHubs(build, layout(build.frame()));
        }
        checkHubs(RenderTestParts.DEADCAT_BUILD, DroneLayout.fromDefinition(RenderTestParts.DEADCAT_BUILD.frame()));
        checkHubs(RenderTestParts.X_CLASS_BUILD, DroneLayout.fromDefinition(RenderTestParts.X_CLASS_BUILD.frame()));
    }

    private static void checkHubs(DroneBuild build, DroneLayout layout) {
        AirframeSpec spec = AirframeSpec.of(build);
        Vector3fc lens = FpvPropGeometry.lens(build);
        for (int slot = 0; slot < DroneLayout.SLOTS; slot++) {
            int motor = DroneLayout.motorForSlot(slot);
            String label = build.frame().key() + ", Platz " + slot;
            assertEquals(-(spec.motorX(motor) + spec.lensRight) * 1000.0, layout.motor(slot).x() - lens.x(), MOTOR_TOLERANCE_MM, label + " seitlich");
            assertEquals(-(spec.motorZ(motor) - spec.lensForward) * 1000.0, layout.motor(slot).z() - lens.z(), MOTOR_TOLERANCE_MM, label + " längs");
        }
    }

    @Test
    void creativeBaseKeepsPropsAndArmsOnTheInstalledFrame() throws IOException {
        DroneBuild racer = DroneBuild.RACER;
        DroneBuild creative = new DroneBuild(racer.frame(), StackType.CREATIVE_FC, racer.motor(), racer.prop(), racer.video(), racer.battery())
                .withCreative(CreativeSpec.EMPTY.withBase(PartKind.FRAME, FrameType.LONG_RANGE_7.key()));
        assertEquals(FrameType.LONG_RANGE_7.wheelbaseMm, creative.flight().frame().wheelbaseMm, "Flugmodell rechnet mit der Basis");
        assertEquals(FrameType.RACE_5, creative.frame());
        Vector3fc plain = FpvPropGeometry.lens(racer);
        assertEquals(plain.z(), FpvPropGeometry.lens(creative).z(), 0.0F, "Linse des eingebauten Rahmens");
        FpvRig rig = FpvRig.of(creative);
        DroneLayout installed = layout(FrameType.RACE_5);
        for (int slot = 0; slot < DroneLayout.SLOTS; slot++) {
            assertEquals(installed.motor(slot).x(), rig.hubMm(slot)[0], 1.0E-3, "Nabe am eingebauten Rahmen");
            assertEquals(installed.motor(slot).z(), rig.hubMm(slot)[2], 1.0E-3, "Nabe am eingebauten Rahmen");
        }
    }

    @Test
    void visibilitySeesAQuadThatCrossesTheViewWithoutACornerInIt() {
        Vector3fc[] hubs = {new Vector3f(-500.0F, 0.0F, 0.0F), new Vector3f(500.0F, 0.0F, 0.0F), new Vector3f(-500.0F, 0.0F, 500.0F), new Vector3f(500.0F, 0.0F, 500.0F)};
        boolean[] all = {true, true, true, true};
        float[] crossing = {-400.0F, -2.0F, -100.0F, 400.0F, -2.0F, -100.0F, 400.0F, 2.0F, -100.0F, -400.0F, 2.0F, -100.0F};
        float[] aside = {300.0F, -2.0F, -100.0F, 400.0F, -2.0F, -100.0F, 400.0F, 2.0F, -100.0F, 300.0F, 2.0F, -100.0F};
        float[] behind = {-400.0F, -2.0F, 100.0F, 400.0F, -2.0F, 100.0F, 400.0F, 2.0F, 100.0F, -400.0F, 2.0F, 100.0F};
        Vector3f lens = new Vector3f();
        assertTrue(new FpvPropGeometry(hubs, 10.0F, lens, 0.0F, new float[0], crossing, all).armsVisible(1.0F, 1.0F), "quer durchs Bild");
        assertFalse(new FpvPropGeometry(hubs, 10.0F, lens, 0.0F, new float[0], aside, all).armsVisible(1.0F, 1.0F), "neben dem Bild");
        assertFalse(new FpvPropGeometry(hubs, 10.0F, lens, 0.0F, new float[0], behind, all).armsVisible(1.0F, 1.0F), "hinter der Linse");
        assertFalse(new FpvPropGeometry(hubs, 10.0F, lens, 0.0F).armsVisible(1.0F, 1.0F), "ohne Arme");
    }

    @Test
    void armsLeaveTheNearScaleAlone() throws IOException {
        for (DroneBuild build : DroneBuild.PRESETS) {
            float tilt = CameraMount.defaultUptilt(build.frame());
            Vector3fc lens = FpvPropGeometry.lens(build);
            FpvPropGeometry without = new FpvPropGeometry(hubs(build), build.prop().diameterMm * 0.5F, lens, tilt);
            float[] arms = quads(FpvGuardsTest.faces(build.frame()));
            boolean[] all = {true, true, true, true};
            FpvPropGeometry with = new FpvPropGeometry(hubs(build), build.prop().diameterMm * 0.5F, lens, tilt, new float[0], arms, all);
            assertEquals(without.nearScale(), with.nearScale(), 0.0F, build.frame().key());
            assertTrue(with.armQuads() > 0, build.frame().key());
        }
    }
}
