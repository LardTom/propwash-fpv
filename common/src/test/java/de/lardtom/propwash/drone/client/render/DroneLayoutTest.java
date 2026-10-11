package de.lardtom.propwash.drone.client.render;

import de.lardtom.propwash.build.DroneBuild;
import de.lardtom.propwash.build.FrameType;
import de.lardtom.propwash.build.MotorType;
import de.lardtom.propwash.sim.AirframeSpec;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.joml.Vector3fc;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DroneLayoutTest {
    @Test
    void motorSlotMappingIsABijection() {
        Set<Integer> slots = new HashSet<>();
        for (int motor = 0; motor < DroneLayout.SLOTS; motor++) {
            int slot = DroneLayout.slotForMotor(motor);
            assertTrue(slots.add(slot));
            assertEquals(motor, DroneLayout.motorForSlot(slot));
        }
    }

    @Test
    void slotsMatchFlightModelMotorPositionsAndSpin() {
        for (DroneBuild build : DroneBuild.PRESETS) {
            AirframeSpec params = AirframeSpec.of(build);
            DroneLayout layout = DroneLayout.fallback(build.frame());
            for (int motor = 0; motor < DroneLayout.SLOTS; motor++) {
                int slot = DroneLayout.slotForMotor(motor);
                Vector3fc position = layout.motor(slot);
                float modelX = (float) -params.motorX(motor);
                float modelZ = (float) -params.motorZ(motor);
                assertEquals(Math.signum(modelX), Math.signum(position.x()), build + " Motor " + motor + " x");
                assertEquals(Math.signum(modelZ), Math.signum(position.z()), build + " Motor " + motor + " z");
                assertEquals(params.spin(motor) > 0, layout.counterClockwise(slot), build + " Motor " + motor + " Drehsinn");
                assertEquals(DroneLayout.isFront(slot), position.z() < 0.0F);
            }
        }
    }

    @Test
    void fallbackMotorsSitOnTheWheelbaseDiagonal() {
        for (FrameType frame : FrameType.values()) {
            DroneLayout layout = DroneLayout.fallback(frame);
            assertEquals(frame.wheelbaseMm * 0.5F, layout.motorOffset(), 0.01F, frame.key());
            assertTrue(layout.hasPropHeight());
            assertTrue(layout.propHeight() > layout.motor(0).y());
            assertEquals(frame.definition().geometry().batteryOnTop(), !layout.batteryBelow(), frame.key());
            assertTrue(layout.video().z() < 0.0F, "Kamera vorn");
        }
    }

    @Test
    void freestyleFallbackMatchesAssetContractExample() {
        DroneLayout layout = DroneLayout.fallback(FrameType.FREESTYLE_5);
        assertEquals(79.5F, Math.abs(layout.motor(DroneLayout.FRONT_LEFT).x()), 0.1F);
        assertEquals(-79.5F, layout.motor(DroneLayout.FRONT_LEFT).z(), 0.1F);
        assertEquals(3.0F, layout.motor(DroneLayout.FRONT_LEFT).y(), 0.001F);
        assertEquals(16.0F, layout.propHeight(), 0.001F);
        assertEquals(32.0F, layout.battery().y(), 0.001F);
        assertEquals(28.0F, layout.cameraTiltDegrees(), 0.001F);
    }

    @Test
    void statorLabelsAreParsed() {
        assertArrayEquals(new float[]{23.0F, 6.5F}, DroneLayout.parseStator("2306.5"));
        assertArrayEquals(new float[]{8.0F, 2.0F}, DroneLayout.parseStator("0802"));
        assertArrayEquals(new float[]{22.0F, 7.0F}, DroneLayout.parseStator("xx"));
        assertArrayEquals(new float[]{22.0F, 7.0F}, DroneLayout.parseStator(null));
        for (MotorType motor : MotorType.values()) {
            assertTrue(DroneLayout.bellDiameter(motor) > DroneLayout.statorDiameter(motor));
            assertTrue(DroneLayout.defaultPropHeight(motor) > DroneLayout.bellHeight(motor));
        }
    }

    @Test
    void rotatedYMatchesQuaternionTransform() {
        Quaternionf q = new Quaternionf().rotationXYZ(0.4F, -1.1F, 2.2F);
        Vector3f v = new Vector3f(0.3F, -0.7F, 1.9F);
        float expected = q.transform(new Vector3f(v)).y;
        assertEquals(expected, DronePhysique.rotatedY(q, v.x, v.y, v.z), 1.0E-5F);
    }

    @Test
    void physiqueComesFromTheFlightModel() {
        for (DroneBuild build : DroneBuild.PRESETS) {
            DronePhysique physique = DronePhysique.derive(build);
            assertTrue(physique.hasHull(), build.toString());
            assertTrue(physique.comAboveFoot() > 0.0F);
            assertEquals(-physique.comAboveFoot(), physique.lowestPoint(new Quaternionf()), 1.0E-4F);
            Quaternionf flipped = new Quaternionf().rotationX((float) Math.PI);
            assertTrue(physique.lowestPoint(flipped) < 0.0F);
        }
    }
}
