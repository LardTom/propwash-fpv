package de.lardtom.propwash.interact;

import de.lardtom.propwash.build.AccessoryAnchor;
import de.lardtom.propwash.build.AccessoryMount;
import de.lardtom.propwash.build.AccessoryType;
import de.lardtom.propwash.build.DroneBuild;
import de.lardtom.propwash.build.FrameType;
import de.lardtom.propwash.core.DroneBuildCodecs;
import de.lardtom.propwash.sim.AirframeSpec;
import de.lardtom.propwash.sim.BuildAnalysis;
import com.mojang.serialization.JsonOps;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GripperTest {
    private static final DroneBuild FREESTYLE = DroneBuild.FREESTYLE.withAccessory(AccessoryType.GRIPPER);
    private static final DroneBuild WHOOP = DroneBuild.WHOOP.withAccessory(AccessoryType.GRIPPER);

    private static double capacity(DroneBuild build) {
        BuildAnalysis a = BuildAnalysis.of(build);
        return GripperSpec.capacityGrams(a.massGrams(), 4.0 * a.maxThrustPerMotorGrams());
    }

    @Test
    void gripperIsABottomAccessoryOnEveryFrame() {
        assertEquals(AccessoryMount.BOTTOM, AccessoryType.GRIPPER.mount);
        assertTrue(AccessoryType.GRIPPER.isGripper());
        assertFalse(AccessoryType.LED_STRIP.isGripper());
        assertEquals(18.0, AccessoryType.GRIPPER.massGrams(), 1.0E-9);
        for (FrameType frame : FrameType.values()) {
            if (frame.builtin()) {
                assertTrue(frame.accessoryMounts.contains(AccessoryMount.BOTTOM), frame.key());
            }
        }
        assertTrue(FREESTYLE.buildable());
        assertTrue(WHOOP.buildable());
    }

    @Test
    void gripperHangsStraightDownInsteadOfFollowingTheLedTilt() {
        AccessoryAnchor bottom = AccessoryAnchor.of(FrameType.FREESTYLE_5, AccessoryMount.BOTTOM);
        assertEquals(0.0, bottom.tiltFor(AccessoryType.GRIPPER), 0.0);
        assertEquals(1.0, bottom.stretch(AccessoryType.GRIPPER), 0.0);
        assertEquals(bottom.tiltDegrees(), bottom.tiltFor(AccessoryType.LED_STRIP), 0.0);
        assertEquals(bottom.lengthMm() / AccessoryType.LED_STRIP.widthMm, bottom.stretch(AccessoryType.LED_STRIP), 1.0E-12);
        double[] center = bottom.centerOf(AccessoryType.GRIPPER);
        assertEquals(bottom.x(), center[0], 1.0E-9);
        assertEquals(bottom.y() - AccessoryType.GRIPPER.heightMm / 2.0, center[1], 1.0E-9);
        assertEquals(bottom.z(), center[2], 1.0E-9);
        AccessoryAnchor whoop = AccessoryAnchor.of(FrameType.WHOOP_65, AccessoryMount.BOTTOM);
        assertEquals(whoop.y() - AccessoryType.GRIPPER.heightMm / 2.0, whoop.centerOf(AccessoryType.GRIPPER)[1], 1.0E-9);
    }

    @Test
    void capacityIsCappedAndNeverMoreThanHalfTheSpareThrust() {
        assertEquals(GripperSpec.MAX_PAYLOAD_GRAMS, capacity(FREESTYLE), 1.0E-9);
        double whoop = capacity(WHOOP);
        assertTrue(whoop > 0.0 && whoop < 60.0, "Whoop trägt nur wenig: " + whoop);
        BuildAnalysis a = BuildAnalysis.of(WHOOP);
        double thrust = 4.0 * a.maxThrustPerMotorGrams();
        assertTrue(thrust / (a.massGrams() + whoop) > 1.2, "Whoop fliegt voll beladen noch");
        assertEquals(0.0, GripperSpec.capacityGrams(500.0, 400.0), 0.0);
        assertEquals(0.0, GripperSpec.capacityGrams(Double.NaN, 400.0), 0.0);
    }

    @Test
    void capacityFollowsTheGravityRuleSoEveryLoadedDroneStillClimbs() {
        for (DroneBuild preset : DroneBuild.PRESETS) {
            DroneBuild build = preset.withAccessory(AccessoryType.GRIPPER);
            BuildAnalysis a = BuildAnalysis.of(build);
            double thrust = 4.0 * a.maxThrustPerMotorGrams();
            double previous = Double.POSITIVE_INFINITY;
            for (double gravity : new double[]{0.8, 1.0, 1.3, 1.5}) {
                double capacity = GripperSpec.capacityGrams(a.massGrams(), thrust, gravity);
                assertTrue(capacity >= 0.0 && capacity <= previous, build.frame() + " bei " + gravity + " g: " + capacity);
                if (capacity > 0.0) {
                    double ratio = thrust / (gravity * (a.massGrams() + capacity));
                    assertTrue(ratio >= GripperSpec.MIN_LOADED_THRUST_RATIO - 1.0E-9, build.frame() + " bei " + gravity + " g beladen " + ratio);
                }
                previous = capacity;
            }
            assertEquals(capacity(build), GripperSpec.capacityGrams(a.massGrams(), thrust, 1.0), 0.0);
        }
        BuildAnalysis whoop = BuildAnalysis.of(WHOOP);
        double thrust = 4.0 * whoop.maxThrustPerMotorGrams();
        assertEquals(21.0, GripperSpec.capacityGrams(whoop.massGrams(), thrust, 1.0), 0.0);
        assertEquals(0.0, GripperSpec.capacityGrams(whoop.massGrams(), thrust, 1.5), 0.0);
        assertEquals(GripperSpec.MAX_PAYLOAD_GRAMS, GripperSpec.capacityGrams(BuildAnalysis.of(FREESTYLE).massGrams(),
                4.0 * BuildAnalysis.of(FREESTYLE).maxThrustPerMotorGrams(), 1.5), 0.0);
        assertEquals(175.0, GripperSpec.capacityGrams(50.0, 400.0, 0.0), 0.0);
        assertEquals(GripperSpec.capacityGrams(50.0, 400.0, 0.01), GripperSpec.capacityGrams(50.0, 400.0, 0.0), 0.0);
        assertEquals(0.0, GripperSpec.capacityGrams(50.0, 400.0, -1.0), 0.0);
        assertEquals(0.0, GripperSpec.capacityGrams(whoop.massGrams(), thrust, 10.0), 0.0);
        assertEquals(0.0, GripperSpec.capacityGrams(50.0, 400.0, Double.NaN), 0.0);
    }

    @Test
    void itemsMeantForAnotherPlayerStayOnTheGround() {
        UUID pilot = UUID.fromString("00000000-0000-4000-8000-000000000001");
        UUID other = UUID.fromString("00000000-0000-4000-8000-000000000002");
        assertTrue(GripperSpec.mayTake(null, pilot));
        assertTrue(GripperSpec.mayTake(pilot, pilot));
        assertFalse(GripperSpec.mayTake(other, pilot));
        assertFalse(GripperSpec.mayTake(other, null));
    }

    @Test
    void cargoAddsMassThroughTheAccessoryWithoutTouchingTheBuild() {
        AirframeSpec bare = AirframeSpec.of(FREESTYLE);
        DroneBuild laden = CargoMass.withPayload(FREESTYLE, 200.0);
        AirframeSpec heavy = AirframeSpec.of(laden);
        assertEquals(bare.massKg + 0.200, heavy.massKg, 1.0E-9);
        assertTrue(heavy.inertiaXx > bare.inertiaXx && heavy.inertiaZz > bare.inertiaZz, "Last unter der Nase träger");
        assertTrue(heavy.comHeight < bare.comHeight, "Schwerpunkt wandert nach unten");
        assertEquals(bare.hitboxHeight, heavy.hitboxHeight, 1.0E-9);
        assertTrue(BuildAnalysis.of(laden).hoverThrottlePercent() > BuildAnalysis.of(FREESTYLE).hoverThrottlePercent());
        assertSame(laden.accessories().at(AccessoryMount.BOTTOM).orElseThrow(), CargoMass.withPayload(FREESTYLE, 199.0).accessories()
                .at(AccessoryMount.BOTTOM).orElseThrow(), "auf 5 g gerastert und wiederverwendet");
        assertEquals(FREESTYLE, CargoMass.unladen(laden));
        assertSame(FREESTYLE, CargoMass.withPayload(FREESTYLE, 0.0));
        assertSame(bare, AirframeSpec.of(CargoMass.withPayload(FREESTYLE, 0.0)), "ohne Fracht exakt das alte Flugmodell");
        assertSame(DroneBuild.FREESTYLE, CargoMass.withPayload(DroneBuild.FREESTYLE, 300.0), "ohne Greifer keine Fracht");
        AccessoryType ladenType = laden.accessories().at(AccessoryMount.BOTTOM).orElseThrow();
        assertNotSame(AccessoryType.GRIPPER, ladenType);
        assertEquals(200.0, ladenType.payloadGrams(), 1.0E-9);
        assertSame(AccessoryType.GRIPPER, ladenType.unladen());
        assertEquals(AccessoryType.GRIPPER.key(), ladenType.key());
        assertEquals(DroneBuildCodecs.CODEC.encodeStart(JsonOps.INSTANCE, FREESTYLE).getOrThrow(),
                DroneBuildCodecs.CODEC.encodeStart(JsonOps.INSTANCE, laden).getOrThrow(), "gespeichert wird nur der Greifer");
    }

    @Test
    void gripperWaitsUntilTheDroneLeftTheDropPoint() {
        assertFalse(GripperSpec.awayFromDrop(0.0, 0.0, 0.0));
        assertFalse(GripperSpec.awayFromDrop(1.0, 0.5, 1.0));
        assertTrue(GripperSpec.awayFromDrop(1.2, 0.0, 1.2));
        assertTrue(GripperSpec.awayFromDrop(0.0, 1.6, 0.0));
        assertTrue(GripperSpec.awayFromDrop(0.0, -2.0, 0.0));
    }

    @Test
    void massStepsAreFiveGrams() {
        assertEquals(0L, GripperSpec.massStep(0.0));
        assertEquals(5L, GripperSpec.massStep(0.4));
        assertEquals(5L, GripperSpec.massStep(6.0));
        assertEquals(400L, GripperSpec.massStep(401.0));
        assertEquals(0L, GripperSpec.massStep(Double.NaN));
    }

    @Test
    void itemWeights() {
        assertEquals(ItemWeights.SINGLE_GRAMS, ItemWeights.gramsEach(1, false, 0.0), 0.0);
        assertEquals(ItemWeights.FEW_GRAMS, ItemWeights.gramsEach(16, false, 0.0), 0.0);
        assertEquals(ItemWeights.BLOCK_GRAMS, ItemWeights.gramsEach(64, true, 0.0), 0.0);
        assertEquals(ItemWeights.SMALL_GRAMS, ItemWeights.gramsEach(64, false, 0.0), 0.0);
        assertEquals(6.0, ItemWeights.gramsEach(16, false, 6.0), 0.0, "echte Teilemasse zählt");
        assertEquals(ItemWeights.SMALL_GRAMS, ItemWeights.gramsEach(64, false, Double.NaN), 0.0);
    }
}
