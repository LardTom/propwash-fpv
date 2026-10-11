package de.lardtom.propwash.drone.client.render;

import com.google.gson.JsonParser;
import de.lardtom.propwash.api.part.MotorArm;
import de.lardtom.propwash.api.part.Vec3Mm;
import de.lardtom.propwash.build.AccessoryAnchor;
import de.lardtom.propwash.build.AccessoryMount;
import de.lardtom.propwash.build.FrameType;
import de.lardtom.propwash.build.MotorType;
import org.joml.Vector3fc;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LayoutFromDefinitionTest {
    private static final Map<FrameType, float[]> OLD_FALLBACK = Map.of(
            FrameType.WHOOP_65, new float[]{-3.0F, 4.0F},
            FrameType.CINEWHOOP_3, new float[]{20.0F, 10.0F},
            FrameType.FREESTYLE_5, new float[]{30.0F, 12.0F},
            FrameType.RACE_5, new float[]{-2.8F, 10.0F},
            FrameType.LONG_RANGE_7, new float[]{32.0F, 14.0F});

    static Path resources() {
        return Files.isDirectory(Path.of("common/src/main/resources")) ? Path.of("common/src/main/resources") : Path.of("src/main/resources");
    }

    static DroneLayoutParser.Result shippedLayouts() throws IOException {
        return DroneLayoutParser.parse(JsonParser.parseString(Files.readString(resources().resolve("assets/propwash/drone_layout.json"))));
    }

    @Test
    void builtinFramesGiveTheOldFallbackLayout() {
        for (FrameType frame : FrameType.values()) {
            float[] old = OLD_FALLBACK.get(frame);
            DroneLayout layout = DroneLayout.fromDefinition(frame);
            String key = frame.key();
            float wb = frame.wheelbaseMm;
            float s = (float) (wb / (2.0 * Math.sqrt(2.0)));
            float motorY = wb >= 200 ? 3.0F : wb >= 100 ? 2.5F : 1.5F;
            float[][] motors = {{-s, motorY, -s}, {s, motorY, -s}, {-s, motorY, s}, {s, motorY, s}};
            for (int slot = 0; slot < DroneLayout.SLOTS; slot++) {
                Vector3fc motor = layout.motor(slot);
                assertEquals(motors[slot][0], motor.x(), 0.0F, key);
                assertEquals(motors[slot][1], motor.y(), 0.0F, key);
                assertEquals(motors[slot][2], motor.z(), 0.0F, key);
                assertEquals(DroneLayout.propsInCounterClockwise(slot), layout.counterClockwise(slot), key);
            }
            assertEquals(old[0], DroneLayout.batteryPlateHeight(frame), 0.0F, key);
            assertEquals(old[1], DroneLayout.electronicsHeight(frame), 0.0F, key);
            boolean top = frame.definition().geometry().batteryOnTop();
            assertEquals(!top, layout.batteryBelow(), key);
            assertEquals(top ? old[0] + 2.0F : old[0], layout.battery().y(), 0.0F, key);
            assertEquals(top ? 0.04F * wb : 0.0F, layout.battery().z(), 0.0F, key);
            Vec3Mm lens = frame.definition().geometry().cameraMm().orElseThrow();
            assertEquals((float) lens.x(), layout.video().x(), 0.0F, key);
            assertEquals((float) lens.y(), layout.video().y(), 0.0F, key);
            assertEquals((float) lens.z() + DroneLayout.CAMERA_BEHIND_LENS_MM, layout.video().z(), 0.0F, key);
            assertEquals(motorY, layout.stack().y(), 0.0F, key);
            assertEquals(DroneLayout.defaultPropHeight(DroneLayout.typicalMotor(frame)), layout.propHeight(), 0.0F, key);
            assertFalse(layout.fromFile());
            assertEquals(frame.accessoryMounts, layout.accessories().keySet(), key);
            for (AccessoryMount mount : frame.accessoryMounts) {
                assertEquals(AccessoryAnchor.of(frame, mount), layout.accessory(mount).orElseThrow(), key);
            }
        }
    }

    @Test
    void shippedCameraLooksThroughTheLensOfTheFrame() throws IOException {
        DroneLayoutParser.Result result = shippedLayouts();
        for (FrameType frame : FrameType.values()) {
            if (!frame.builtin()) {
                continue;
            }
            DroneLayout file = result.layouts().get(frame);
            Vec3Mm lens = frame.definition().geometry().cameraMm().orElseThrow();
            double tilt = Math.toRadians(file.cameraTiltDegrees());
            double axis = DroneLayout.CAMERA_BEHIND_LENS_MM;
            assertEquals(file.video().x(), lens.x(), 1.0E-6, frame.key());
            assertEquals(file.video().y() + axis * Math.sin(tilt), lens.y(), 0.06, frame.key());
            assertEquals(file.video().z() - axis * Math.cos(tilt), lens.z(), 0.06, frame.key());
        }
    }

    @Test
    void cameraUptiltOfTheDefinitionsEqualsTheShippedLayout() throws IOException {
        DroneLayoutParser.Result result = shippedLayouts();
        for (FrameType frame : FrameType.values()) {
            DroneLayout file = result.layouts().get(frame);
            assertEquals(file.cameraTiltDegrees(), DroneLayout.defaultCameraTilt(frame), 0.0F, frame.key());
            assertEquals(file.cameraTiltDegrees(), DroneLayout.fromDefinition(frame).cameraTiltDegrees(), 0.0F, frame.key());
        }
    }

    @Test
    void shippedLayoutStaysCloseToTheDefinitionGeometry() throws IOException {
        DroneLayoutParser.Result result = shippedLayouts();
        for (FrameType frame : FrameType.values()) {
            DroneLayout file = result.layouts().get(frame);
            DroneLayout definition = DroneLayout.fromDefinition(frame);
            assertEquals(definition.motorOffset(), file.motorOffset(), 0.6F, frame.key());
            assertEquals(definition.batteryBelow(), file.batteryBelow(), frame.key());
        }
    }

    @Test
    void explicitArmsDriveMotorsSpinAndCamera() {
        DroneLayout layout = DroneLayout.fromDefinition(RenderTestParts.DEADCAT);
        List<MotorArm> arms = RenderTestParts.DEADCAT.definition().geometry().arms();
        for (int slot = 0; slot < DroneLayout.SLOTS; slot++) {
            assertEquals((float) arms.get(slot).position().x(), layout.motor(slot).x(), 0.0F);
            assertEquals((float) arms.get(slot).position().z(), layout.motor(slot).z(), 0.0F);
            assertEquals(arms.get(slot).spin() == MotorArm.Spin.CCW, layout.counterClockwise(slot));
        }
        assertEquals(-92.0F + DroneLayout.CAMERA_BEHIND_LENS_MM, layout.video().z(), 0.0F);
        assertEquals(16.0F, layout.video().y(), 0.0F);
        assertEquals(30.0F, layout.cameraTiltDegrees(), 0.0F);
        assertEquals(32.0F, layout.battery().y(), 0.0F);
        assertFalse(layout.hasPropHeight(), "fremde Rahmen rechnen die Prop-Höhe je Motor");
        assertEquals(RenderTestParts.DEADCAT.accessoryMounts, layout.accessories().keySet());
    }

    @Test
    void everyAddonFrameGivesAFiniteLayout() {
        for (FrameType frame : RenderTestParts.FRAMES) {
            DroneLayout layout = DroneLayout.fromDefinition(frame);
            for (int slot = 0; slot < DroneLayout.SLOTS; slot++) {
                Vector3fc m = layout.motor(slot);
                assertTrue(Float.isFinite(m.x()) && Float.isFinite(m.y()) && Float.isFinite(m.z()), frame.key());
                assertEquals(DroneLayout.isFront(slot), m.z() < 0.0F, frame.key() + " Platz " + slot);
            }
            assertTrue(Float.isFinite(layout.battery().y()) && Float.isFinite(layout.video().z()), frame.key());
            assertTrue(layout.video().z() < 0.0F, frame.key() + ": Kamera vorn");
            assertEquals(frame.wheelbaseMm * 0.5F, DroneLayout.fromDefinition(frame).motorOffset(), frame.wheelbaseMm * 0.2F, frame.key());
        }
    }

    @Test
    void typicalMotorFollowsPresetsThenMounts() {
        for (FrameType frame : FrameType.values()) {
            assertTrue(frame.motorMounts.contains(DroneLayout.typicalMotor(frame).mount), frame.key());
        }
        assertSame(MotorType.M2207_1750, DroneLayout.typicalMotor(RenderTestParts.DEADCAT));
        assertSame(MotorType.M2807_1300, DroneLayout.typicalMotor(RenderTestParts.X_CLASS));
    }

    @Test
    void statorSizesComeFromTheDefinition() {
        assertEquals(28.0F, DroneLayout.statorDiameter(RenderTestParts.M2806), 0.0F);
        assertEquals(6.5F, DroneLayout.statorHeight(RenderTestParts.M2806), 0.0F);
        assertEquals(42.0F, DroneLayout.statorDiameter(RenderTestParts.M4214), 0.0F);
        assertEquals(47.0F, DroneLayout.bellDiameter(RenderTestParts.M4214), 0.0F);
        for (MotorType motor : MotorType.values()) {
            float[] parsed = DroneLayout.parseStator(motor.statorLabel);
            assertEquals(parsed[0], DroneLayout.statorDiameter(motor), 0.0F, motor.key());
            assertEquals(parsed[1], DroneLayout.statorHeight(motor), 0.0F, motor.key());
        }
    }
}
