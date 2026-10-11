package de.lardtom.propwash.drone.client.render;

import de.lardtom.propwash.api.part.Vec3Mm;
import de.lardtom.propwash.build.DroneBuild;
import de.lardtom.propwash.build.FrameType;
import de.lardtom.propwash.build.PropType;
import de.lardtom.propwash.sim.FpvRig;
import de.lardtom.propwash.video.FovMath;
import org.joml.Vector3f;
import org.joml.Vector3fc;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FpvPropGeometryTest {
    private static final float ASPECT = 16.0F / 9.0F;

    private static float tanX(float horizontalDegrees) {
        return FpvPropGeometry.tanHalfFromDegrees(horizontalDegrees);
    }

    private static float tanY(float horizontalDegrees) {
        return FpvPropGeometry.tanHalfFromDegrees(FovMath.verticalFromHorizontal(horizontalDegrees, ASPECT));
    }

    private static FpvPropGeometry shipped(DroneBuild build) throws IOException {
        DroneLayout layout = LayoutFromDefinitionTest.shippedLayouts().layouts().get(build.frame());
        Vector3fc[] hubs = new Vector3fc[DroneLayout.SLOTS];
        for (int slot = 0; slot < DroneLayout.SLOTS; slot++) {
            hubs[slot] = new Vector3f(layout.motor(slot)).add(0.0F, layout.propHeight(), 0.0F);
        }
        return new FpvPropGeometry(hubs, build.prop().diameterMm * 0.5F, FpvPropGeometry.lens(build), layout.cameraTiltDegrees());
    }

    private static FpvPropGeometry fromDefinition(DroneBuild build) {
        DroneLayout layout = DroneLayout.fromDefinition(build.frame());
        float propHeight = layout.hasPropHeight() ? layout.propHeight() : DroneLayout.defaultPropHeight(build.motor());
        Vector3fc[] hubs = new Vector3fc[DroneLayout.SLOTS];
        for (int slot = 0; slot < DroneLayout.SLOTS; slot++) {
            hubs[slot] = new Vector3f(layout.motor(slot)).add(0.0F, propHeight, 0.0F);
        }
        return new FpvPropGeometry(hubs, build.prop().diameterMm * 0.5F, FpvPropGeometry.lens(build), layout.cameraTiltDegrees());
    }

    @Test
    void cameraAxisMapsToScreenCentre() {
        FpvPropGeometry geometry = new FpvPropGeometry(new Vector3fc[]{new Vector3f(), new Vector3f(), new Vector3f(), new Vector3f()}, 10.0F,
                new Vector3f(0.0F, 10.0F, -20.0F), 30.0F);
        double tilt = Math.toRadians(30.0);
        Vector3f ahead = geometry.toCamera(0.0F, 10.0F + (float) (100.0 * Math.sin(tilt)), -20.0F - (float) (100.0 * Math.cos(tilt)), new Vector3f());
        assertEquals(0.0F, ahead.x, 1.0E-4F);
        assertEquals(0.0F, ahead.y, 1.0E-3F);
        assertEquals(-100.0F, ahead.z, 1.0E-3F);
        Vector3f level = geometry.toCamera(0.0F, 10.0F, -120.0F, new Vector3f());
        assertTrue(level.y < 0.0F, "vor der Nase liegt bei Uptilt unter der Bildmitte");
    }

    @Test
    void lensOfBuiltinDronesIsTheCameraMountOfTheFrame() {
        for (DroneBuild build : DroneBuild.PRESETS) {
            Vector3f lens = FpvPropGeometry.lens(build);
            Vec3Mm mount = build.frame().definition().geometry().cameraMm().orElseThrow();
            assertEquals((float) mount.x(), lens.x, 0.0F, build.toString());
            assertEquals((float) mount.y(), lens.y, 0.0F, build.toString());
            assertEquals((float) mount.z(), lens.z, 0.0F, build.toString());
            double[] rig = FpvRig.lens(build);
            assertEquals(rig[1], lens.y, 0.0F, build.toString());
            assertEquals(rig[2], lens.z, 0.0F, build.toString());
        }
    }

    @Test
    void freestylePropsShowAtTheEdgeOfAWideDigitalImage() throws IOException {
        FpvPropGeometry geometry = shipped(DroneBuild.FREESTYLE);
        assertTrue(geometry.anyVisible(tanX(130.0F), tanY(130.0F)), "5-Zoll-Props bei 130° nicht im Bild");
        int visible = geometry.visibleSamples(tanX(130.0F), tanY(130.0F));
        assertTrue(visible < (FpvPropGeometry.RIM_SAMPLES + 1) * 2, "zu viel Prop im Bild: " + visible);
        assertFalse(geometry.visible(DroneLayout.REAR_LEFT, tanX(130.0F), tanY(130.0F)), "hintere Props im Bild");
        assertTrue(geometry.nearScale() >= 1.0F && geometry.nearScale() <= FpvPropGeometry.MAX_NEAR_SCALE);
        assertFalse(geometry.anyVisible(tanX(60.0F), tanY(60.0F)), "Props schon bei 60° im Bild");
    }

    @Test
    void deadcatKeepsPropsOutOfView() {
        FpvPropGeometry geometry = fromDefinition(RenderTestParts.DEADCAT_BUILD);
        assertFalse(geometry.anyVisible(tanX(FovMath.MAX_HORIZONTAL_DEGREES), tanY(FovMath.MAX_HORIZONTAL_DEGREES)),
                "Deadcat zeigt Props bei " + FovMath.MAX_HORIZONTAL_DEGREES + "°");
    }

    @Test
    void everyAddonDroneHasFiniteGeometry() {
        for (FrameType frame : RenderTestParts.FRAMES) {
            DroneBuild build = new DroneBuild(frame, RenderTestParts.STACK_8S, RenderTestParts.M2806, frame == RenderTestParts.X_CLASS
                    ? RenderTestParts.P13 : PropType.P51_3B, RenderTestParts.HD_PRO, RenderTestParts.B8S);
            FpvPropGeometry geometry = fromDefinition(build);
            float scale = geometry.nearScale();
            assertTrue(Float.isFinite(scale) && scale >= 1.0F && scale <= FpvPropGeometry.MAX_NEAR_SCALE, frame.key() + " " + scale);
            Vector3fc lens = geometry.lensMm();
            assertTrue(Float.isFinite(lens.x()) && Float.isFinite(lens.y()) && Float.isFinite(lens.z()), frame.key());
        }
    }
}
