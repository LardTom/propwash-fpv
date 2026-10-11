package de.lardtom.propwash.drone.client.render;

import de.lardtom.propwash.build.BatteryType;
import de.lardtom.propwash.build.FrameType;
import de.lardtom.propwash.build.MotorType;
import de.lardtom.propwash.build.PropType;
import de.lardtom.propwash.build.StackType;
import de.lardtom.propwash.build.VideoType;
import org.joml.Matrix4f;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FallbackShapesTest {
    private static final float MM = DroneVisualScale.MM_TO_UNITS;

    @Test
    void cubeHasSixOutwardFaces() {
        Shape cube = new ShapeBuilder(1.0F).cube(new Matrix4f().translate(1.0F, 2.0F, 3.0F).rotateY(0.6F).scale(2.0F, 0.5F, 4.0F), 0xFFFFFFFF).build();
        assertEquals(24, cube.vertexCount());
        assertTrue(cube.min().y() < 2.0F && cube.max().y() > 2.0F);
        assertEquals(1.75F, cube.min().y(), 1.0E-5F);
        assertEquals(2.25F, cube.max().y(), 1.0E-5F);
    }

    @Test
    void emptyBuilderGivesEmptyShape() {
        assertTrue(new ShapeBuilder(1.0F).build().isEmpty());
        assertTrue(Shape.EMPTY.isEmpty());
    }

    @Test
    void cylinderAndAnnulusProduceQuads() {
        Shape cylinder = new ShapeBuilder(1.0F).cylinder(new Matrix4f(), 12, 0xFFFFFFFF).build();
        assertEquals(12 * 3 * 4, cylinder.vertexCount());
        assertEquals(-0.5F, cylinder.min().y(), 1.0E-6F);
        assertEquals(0.5F, cylinder.max().x(), 1.0E-6F);
        Shape ring = new ShapeBuilder(1.0F).annulus(new Matrix4f(), 0.2F, 20, 0x80FFFFFF, 0x20FFFFFF).build();
        assertEquals(20 * 4, ring.vertexCount());
    }

    @Test
    void everyPartHasAFallbackShape() {
        for (FrameType frame : FrameType.values()) {
            DroneLayout layout = DroneLayout.fallback(frame);
            assertFalse(FallbackShapes.buildFrame(frame, layout).isEmpty(), frame.key());
            assertFalse(FallbackShapes.buildLeds(frame, layout).isEmpty(), frame.key());
            for (int slot = 0; slot < DroneLayout.SLOTS; slot++) {
                assertFalse(FallbackShapes.buildLedGlow(frame, layout, slot).isEmpty());
            }
            Shape shape = FallbackShapes.buildFrame(frame, layout);
            float reach = Math.max(shape.max().x(), -shape.min().x()) / MM;
            assertTrue(reach > frame.wheelbaseMm * 0.35F && reach < frame.wheelbaseMm, frame.key() + " reach " + reach);
        }
        for (StackType stack : StackType.values()) {
            assertFalse(FallbackShapes.buildStack(stack).isEmpty());
        }
        for (MotorType motor : MotorType.values()) {
            Shape shape = FallbackShapes.buildMotor(motor);
            assertEquals(0.0F, shape.min().y(), 1.0E-4F, motor.key());
            assertTrue(shape.max().y() / MM <= DroneLayout.defaultPropHeight(motor) + 0.01F, motor.key());
        }
        for (PropType prop : PropType.values()) {
            for (boolean ccw : new boolean[]{false, true}) {
                Shape shape = FallbackShapes.buildProp(prop, ccw);
                float radius = Math.max(shape.max().x(), -shape.min().x()) / MM;
                assertTrue(radius > prop.diameterMm * 0.35F && radius <= prop.diameterMm * 0.52F, prop.key() + " radius " + radius);
            }
            Shape blur = FallbackShapes.buildBlur(prop);
            assertEquals(prop.diameterMm * 0.5F, blur.max().x() / MM, 0.5F);
        }
        for (BatteryType battery : BatteryType.values()) {
            Shape shape = FallbackShapes.buildBattery(battery);
            assertTrue(shape.min().y() / MM > -3.0F, battery.key());
            float[] d = FallbackShapes.batteryDimensions(battery);
            assertTrue(d[2] > d[0] && d[0] > d[1]);
        }
        for (VideoType video : VideoType.values()) {
            Shape camera = FallbackShapes.buildCamera(video);
            assertTrue(camera.min().z() < -FallbackShapes.cameraSize(video) * 0.5F * MM, "Objektiv zeigt nach vorn");
            assertFalse(FallbackShapes.buildTransmitter(video).isEmpty());
        }
    }

    @Test
    void tintMultipliesChannels() {
        assertEquals(0xFFFF2A2A, Shape.multiply(0xFFFFFFFF, 0xFFFF2A2A));
        assertEquals(0x80000000, Shape.multiply(0x80FFFFFF, 0xFF000000));
        assertEquals(0x00000000, Shape.multiply(0x00FFFFFF, 0xFFFFFFFF) & 0xFF000000);
        assertEquals(0x7F7F7F7F, Shape.multiply(0xFFFFFFFF, 0x7F7F7F7F));
    }

    @Test
    void motorGlowAndTransmitterSitWhereExpected() {
        for (MotorType motor : MotorType.values()) {
            Shape glow = FallbackShapes.buildMotorGlow(motor);
            assertTrue(glow.max().x() / MM > DroneLayout.bellDiameter(motor) * 0.5F, motor.key());
        }
        for (VideoType video : VideoType.values()) {
            Shape transmitter = FallbackShapes.buildTransmitter(video);
            assertEquals(0.0F, transmitter.min().y(), 1.0E-4F, video.key());
            assertTrue(transmitter.max().z() > 0.0F, "Antennen hinten");
        }
        for (StackType stack : StackType.values()) {
            Shape shape = FallbackShapes.buildStack(stack);
            float height = FallbackShapes.stackHeight(stack);
            assertTrue(height > 3.0F && height < shape.max().y() / MM + 0.01F + 4.0F, stack.key());
        }
    }

    @Test
    void sixSBatteryHasPlausibleSize() {
        float[] d = FallbackShapes.batteryDimensions(BatteryType.B6S_1300);
        assertEquals(33.0F, d[1], 3.0F);
        assertEquals(72.0F, d[2], 6.0F);
    }

    @Test
    void renderItemDefinitionsExistForEveryPart() throws IOException {
        Path root = Files.isDirectory(Path.of("common/src/main/resources")) ? Path.of("common/src/main/resources") : Path.of("src/main/resources");
        Path assets = root.resolve("assets/propwash");
        for (PartModel part : PartModel.all()) {
            Path item = assets.resolve(part.itemModelFile().getPath());
            assertTrue(Files.isRegularFile(item), item.toString());
            assertTrue(Files.readString(item).contains("\"propwash:part/" + part + "\""), item.toString());
            Path model = assets.resolve(part.modelFile().getPath());
            assertTrue(Files.isRegularFile(model), model.toString());
        }
        assertTrue(Files.isRegularFile(root.resolve("assets/propwash/textures/entity/drone_render/white.png")));
    }
}
