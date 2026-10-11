package de.lardtom.propwash.drone.client.render;

import de.lardtom.propwash.build.BatteryType;
import de.lardtom.propwash.build.FrameType;
import de.lardtom.propwash.build.MotorType;
import de.lardtom.propwash.build.PropType;
import de.lardtom.propwash.build.StackType;
import de.lardtom.propwash.build.VideoType;
import org.joml.Vector3fc;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AddonShapesTest {
    private static final float MM = DroneVisualScale.MM_TO_UNITS;

    private static void assertFinite(Shape shape, String what) {
        assertFalse(shape.isEmpty(), what + " leer");
        for (Vector3fc v : new Vector3fc[]{shape.min(), shape.max()}) {
            assertTrue(Float.isFinite(v.x()) && Float.isFinite(v.y()) && Float.isFinite(v.z()), what + " NaN/unendlich");
        }
    }

    @Test
    void addonFramesStayInsideTheirPropHull() {
        for (FrameType frame : RenderTestParts.FRAMES) {
            DroneLayout layout = DroneLayout.fromDefinition(frame);
            Shape shape = FallbackShapes.buildFrame(frame, layout);
            assertFinite(shape, frame.key());
            float minX = Float.POSITIVE_INFINITY;
            float maxX = Float.NEGATIVE_INFINITY;
            float minZ = Float.POSITIVE_INFINITY;
            float maxZ = Float.NEGATIVE_INFINITY;
            for (int slot = 0; slot < DroneLayout.SLOTS; slot++) {
                Vector3fc m = layout.motor(slot);
                minX = Math.min(minX, m.x());
                maxX = Math.max(maxX, m.x());
                minZ = Math.min(minZ, m.z());
                maxZ = Math.max(maxZ, m.z());
            }
            float margin = frame.maxPropMm * 0.5F + 5.0F;
            String key = frame.key();
            assertTrue(shape.min().x() / MM >= minX - margin && shape.max().x() / MM <= maxX + margin, key + " breiter als die Prop-Kreise");
            assertTrue(shape.min().z() / MM >= minZ - margin && shape.max().z() / MM <= maxZ + margin, key + " länger als die Prop-Kreise");
            assertTrue(shape.max().x() / MM >= maxX - 1.0F && shape.min().x() / MM <= minX + 1.0F, key + ": Arme reichen nicht bis zu den Motoren");
            assertTrue(shape.max().z() / MM >= maxZ - 1.0F && shape.min().z() / MM <= minZ + 1.0F, key + ": Arme reichen nicht bis zu den Motoren");
            assertTrue(shape.max().y() / MM < 400.0F && shape.min().y() / MM > -100.0F, key + " Höhe");
            assertFinite(FallbackShapes.buildLeds(frame, layout), key + " LEDs");
        }
    }

    @Test
    void ductsSurroundEveryPropOfDuctedFrames() {
        FrameType frame = RenderTestParts.DUCTED;
        DroneLayout layout = DroneLayout.fromDefinition(frame);
        Shape shape = FallbackShapes.buildFrame(frame, layout);
        float reach = Math.max(shape.max().x(), -shape.min().x()) / MM;
        assertTrue(reach >= layout.motor(0).x() * -1.0F + frame.maxPropMm * 0.5F, "Duct-Ring fehlt: " + reach);
    }

    @Test
    void hFrameHasSideRails() {
        FrameType frame = RenderTestParts.H_FRAME;
        Shape h = FallbackShapes.buildFrame(frame, DroneLayout.fromDefinition(frame));
        Shape x = FallbackShapes.buildFrame(RenderTestParts.SQUASHED, DroneLayout.fromDefinition(RenderTestParts.SQUASHED));
        assertTrue(h.vertexCount() != x.vertexCount(), "H-Rahmen zeichnet dieselben Diagonalarme wie ein X");
    }

    @Test
    void xClassArmsAreClampedToReadableSizes() {
        FrameType frame = RenderTestParts.X_CLASS;
        assertEquals(FallbackShapes.MAX_ARM_WIDTH_MM, FallbackShapes.armWidth(frame), 0.0F);
        assertEquals(6.0F, FallbackShapes.armThickness(frame), 0.0F);
        assertTrue(FallbackShapes.bodySpan(frame) < frame.wheelbaseMm);
        for (FrameType builtin : FrameType.values()) {
            assertEquals(builtin.wheelbaseMm, FallbackShapes.bodySpan(builtin), 0.0F, builtin.key());
            assertEquals(Math.max(4.0F, 0.06F * builtin.wheelbaseMm), FallbackShapes.armWidth(builtin), 0.0F, builtin.key());
        }
    }

    @Test
    void addonPartsHaveFiniteShapes() {
        for (MotorType motor : new MotorType[]{RenderTestParts.M2806, RenderTestParts.M4214}) {
            Shape shape = FallbackShapes.buildMotor(motor);
            assertFinite(shape, motor.key());
            assertEquals(DroneLayout.bellDiameter(motor) * 0.5F, shape.max().x() / MM, 1.5F, motor.key());
            assertEquals(0.0F, shape.min().y(), 1.0E-4F, motor.key());
        }
        PropType prop = RenderTestParts.P13;
        for (boolean ccw : new boolean[]{false, true}) {
            Shape shape = FallbackShapes.buildProp(prop, ccw);
            assertFinite(shape, prop.key());
            float radius = Math.max(shape.max().x(), -shape.min().x()) / MM;
            assertTrue(radius > prop.diameterMm * 0.35F && radius <= prop.diameterMm * 0.52F, "Radius " + radius);
        }
        assertFinite(FallbackShapes.buildBlur(prop), prop.key() + " Scheibe");
        assertArrayEquals(new float[]{45.0F, 70.0F, 140.0F}, FallbackShapes.batteryDimensions(RenderTestParts.B8S), 0.0F);
        assertFinite(FallbackShapes.buildBattery(RenderTestParts.B8S), "8S");
        for (StackType stack : new StackType[]{RenderTestParts.STACK_8S, RenderTestParts.AIO_25}) {
            assertFinite(FallbackShapes.buildStack(stack), stack.key());
        }
        assertEquals(36.0F, FallbackShapes.boardSize(RenderTestParts.STACK_8S), 0.0F);
        assertEquals(10.6F, FallbackShapes.stackHeight(RenderTestParts.STACK_8S), 0.0F);
        assertEquals(25.0F, FallbackShapes.boardSize(RenderTestParts.AIO_25), 0.0F);
        assertEquals(3.6F, FallbackShapes.stackHeight(RenderTestParts.AIO_25), 0.0F);
        assertFinite(FallbackShapes.buildCamera(RenderTestParts.HD_PRO), "Kamera");
        assertFinite(FallbackShapes.buildTransmitter(RenderTestParts.HD_PRO), "Sender");
        assertTrue(FallbackShapes.largeTransmitter(RenderTestParts.HD_PRO));
    }

    @Test
    void builtinShapesKeepTheirOldValues() {
        assertEquals(10.6F, FallbackShapes.stackHeight(StackType.STACK_30), 0.0F);
        assertEquals(4.6F, FallbackShapes.stackHeight(StackType.AIO_20), 0.0F);
        assertEquals(3.6F, FallbackShapes.stackHeight(StackType.AIO_WHOOP), 0.0F);
        assertEquals(36.0F, FallbackShapes.boardSize(StackType.STACK_30), 0.0F);
        assertEquals(27.0F, FallbackShapes.boardSize(StackType.AIO_20), 0.0F);
        assertEquals(26.0F, FallbackShapes.boardSize(StackType.AIO_WHOOP), 0.0F);
        assertTrue(FallbackShapes.largeTransmitter(VideoType.DIGITAL_HD));
        assertFalse(FallbackShapes.largeTransmitter(VideoType.DIGITAL_LITE));
        assertTrue(FallbackShapes.smallTransmitter(VideoType.ANALOG_NANO));
        assertFalse(FallbackShapes.smallTransmitter(VideoType.ANALOG_RACE));
        assertEquals(0xFF1F1F23, FallbackShapes.batteryColor(BatteryType.B6S_1300));
        assertEquals(0xFF2F6D3A, FallbackShapes.batteryColor(BatteryType.B6S_4000_LION));
        assertEquals(0xFF6A3FA0, FallbackShapes.batteryColor(BatteryType.B1S_300_HV));
        assertEquals(0xFFFF7A1A, FallbackShapes.propColor(PropType.P51_3B));
        assertEquals(0xFFE8B622, FallbackShapes.motorColor(MotorType.M2207_1750));
        float[] lion = FallbackShapes.batteryDimensions(BatteryType.B6S_4000_LION);
        double height = Math.cbrt(420.0 / 2.4 / 2.86) * 10.0;
        assertEquals((float) height, lion[1], 0.0F);
    }

    @Test
    void addonColorsComeFromAccentAndMaterial() {
        assertEquals(0xFF3A7BD5, FallbackShapes.motorColor(RenderTestParts.M4214));
        assertEquals(0xFF2B2C31, FallbackShapes.propColor(RenderTestParts.P13));
        assertEquals(0xFF1A8F8B, FallbackShapes.batteryColor(RenderTestParts.B8S));
    }
}
