package de.lardtom.propwash.accessory.fix;

import de.lardtom.propwash.accessory.AccessoryBehaviors;
import de.lardtom.propwash.accessory.AccessoryPayload;
import de.lardtom.propwash.api.accessory.AccessoryBehavior;
import de.lardtom.propwash.api.part.AccessoryDefinition;
import de.lardtom.propwash.api.part.AccessorySlot;
import de.lardtom.propwash.api.part.PartBasics;
import de.lardtom.propwash.build.AccessoryType;
import de.lardtom.propwash.build.DroneBuild;
import de.lardtom.propwash.build.PartIndex;
import de.lardtom.propwash.sim.AirframeSpec;
import de.lardtom.propwash.sim.BuildAnalysis;
import de.lardtom.propwash.sim.DroneSim;
import de.lardtom.propwash.sim.FlightControllerConfig;
import de.lardtom.propwash.sim.MoveReport;
import de.lardtom.propwash.sim.PilotInput;
import de.lardtom.propwash.sim.SimEnvironment;
import de.lardtom.propwash.sim.SimTuning;
import de.lardtom.propwash.sim.StabMode;
import de.lardtom.propwash.sim.StepResult;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import org.joml.Quaterniond;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.OptionalDouble;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PayloadLimitTest {
    private static final Identifier HEAVY = Identifier.fromNamespaceAndPath("apiaccfix", "heavy_mount");
    private static final double FPS = 120.0;
    private static final double GROUND = 64.0;
    private static volatile double custom;

    static {
        AccessoryBehaviors.INSTANCE.register(HEAVY, new AccessoryBehavior() {
            @Override
            public int slots() {
                return 4;
            }

            @Override
            public OptionalDouble payloadGrams(List<ItemStack> contents) {
                return OptionalDouble.of(custom);
            }
        });
    }

    private static AccessoryType mount(AccessorySlot slot, double massGrams) {
        return (AccessoryType) PartIndex.detached(AccessoryDefinition.builder(HEAVY).basics(PartBasics.of(massGrams)).slot(slot)
                .size(62.0, 58.0, 64.0).frontalArea(0.0036).build());
    }

    private static double grams(AccessoryType type, double value) {
        custom = value;
        return AccessoryBehaviors.INSTANCE.payloadGrams(type, List.of());
    }

    @Test
    void noSilentCapAtTwoKilograms() {
        AccessoryType type = mount(AccessorySlot.FRONT, 150.0);
        assertEquals(3870.0, grams(type, 3870.0), 0.0);
        assertEquals(9600.0, grams(type, 9600.0), 0.0);
        assertFalse(AccessoryPayload.overloaded(type, 9600.0));
        assertEquals(9600.0, AccessoryPayload.flightGrams(type, 9600.0), 0.0);
        assertEquals(3870L, AccessoryPayload.massStep(type, 3870.0));
        AccessoryType laden = type.laden(AccessoryPayload.massStep(type, 3870.0));
        assertEquals(150.0 + 3870.0, laden.massGrams(), 1.0E-9);
        assertEquals(3870.0, laden.payloadGrams(), 1.0E-9);
        assertEquals(type.key(), laden.key());
    }

    @Test
    void realMassReachesTheFlightModel() {
        AccessoryType type = mount(AccessorySlot.FRONT, 150.0);
        DroneBuild base = DroneBuild.FREESTYLE.withAccessory(type);
        DroneBuild loaded = DroneBuild.FREESTYLE.withAccessory(type.laden(AccessoryPayload.massStep(type, 3870.0)));
        DroneBuild oldCap = DroneBuild.FREESTYLE.withAccessory(type.laden(2000L));
        assertEquals(AirframeSpec.of(base).massKg + 3.870, AirframeSpec.of(loaded).massKg, 1.0E-9);
        assertTrue(BuildAnalysis.of(loaded).thrustToWeight() < BuildAnalysis.of(oldCap).thrustToWeight() * 0.8,
                BuildAnalysis.of(loaded).thrustToWeight() + " gegen " + BuildAnalysis.of(oldCap).thrustToWeight());
    }

    @Test
    void theLimitIsTheLargePartLimitAndIsNeverSilent() {
        AccessoryType type = mount(AccessorySlot.FRONT, 150.0);
        assertEquals(50000.0, grams(type, 50000.0), 0.0);
        assertTrue(AccessoryPayload.overloaded(type, 50000.0));
        assertEquals(19850.0, AccessoryPayload.limitGrams(type), 0.0);
        assertEquals(19850.0, AccessoryPayload.flightGrams(type, 50000.0), 0.0);
        assertFalse(AccessoryPayload.overloaded(type, 19850.0));
        assertTrue(AccessoryPayload.overloaded(type, 19850.5));
        assertEquals(AccessoryPayload.MAX_LADEN_GRAMS, type.laden(AccessoryPayload.massStep(type, 1.0E12)).massGrams(), 1.0E-9);
        AccessoryType odd = mount(AccessorySlot.TOP, 42.0);
        long step = AccessoryPayload.massStep(odd, 1.0E9);
        assertEquals(19955L, step);
        assertDoesNotThrow(() -> odd.laden(step));
        assertTrue(odd.laden(step).massGrams() <= AccessoryPayload.MAX_LADEN_GRAMS);
        assertDoesNotThrow(() -> PartBasics.of(AccessoryPayload.MAX_LADEN_GRAMS));
        assertThrows(IllegalArgumentException.class, () -> PartBasics.of(Math.nextUp(AccessoryPayload.MAX_LADEN_GRAMS)));
        AccessoryType full = mount(AccessorySlot.BOTTOM, AccessoryPayload.MAX_LADEN_GRAMS);
        assertEquals(0.0, AccessoryPayload.limitGrams(full), 0.0);
        assertEquals(0L, AccessoryPayload.massStep(full, 500.0));
        assertTrue(AccessoryPayload.overloaded(full, 1.0));
    }

    @Test
    void brokenCustomWeightsFallBackToTheItems() {
        AccessoryType type = mount(AccessorySlot.FRONT, 150.0);
        assertEquals(0.0, grams(type, Double.NaN), 0.0);
        assertEquals(0.0, grams(type, Double.POSITIVE_INFINITY), 0.0);
        assertEquals(0.0, grams(type, -500.0), 0.0);
        assertEquals(0.0, AccessoryPayload.flightGrams(type, Double.NaN), 0.0);
        assertEquals(0L, AccessoryPayload.massStep(type, -3.0));
        assertFalse(AccessoryPayload.overloaded(type, Double.NaN));
    }

    @Test
    void overloadedDronesStayStableAndStayOnTheGround() {
        for (DroneBuild preset : DroneBuild.PRESETS) {
            for (AccessorySlot slot : AccessorySlot.values()) {
                AccessoryType type = mount(slot, 150.0);
                if (!preset.frame().accessoryMounts.contains(type.mount)) {
                    continue;
                }
                DroneBuild limit = preset.withAccessory(type.laden(AccessoryPayload.massStep(type, 1.0E9)));
                Flight flight = Flight.fullThrottle(limit, 3.0);
                String what = preset.frame().key() + " " + slot;
                assertTrue(flight.finite, what + " nicht endlich");
                assertTrue(flight.armed, what + " nicht scharf");
                assertTrue(flight.maxHeight < 0.1, what + " hebt mit 20 kg ab: " + flight.maxHeight);
                assertTrue(flight.minBottom > -0.02, what + " sinkt ein: " + flight.minBottom);
                assertTrue(flight.maxTiltDeg < 30.0, what + " kippt: " + flight.maxTiltDeg);
            }
        }
    }

    @Test
    void heavierThanTheOldCapClimbsSlowerButStillFlies() {
        AccessoryType type = mount(AccessorySlot.BOTTOM, 150.0);
        DroneBuild oldCap = DroneBuild.LONG_RANGE.withAccessory(type.laden(2000L));
        DroneBuild real = DroneBuild.LONG_RANGE.withAccessory(type.laden(3200L));
        DroneBuild tooHeavy = DroneBuild.LONG_RANGE.withAccessory(type.laden(9600L));
        Flight capped = Flight.fullThrottle(oldCap, 3.0);
        Flight loaded = Flight.fullThrottle(real, 3.0);
        Flight grounded = Flight.fullThrottle(tooHeavy, 3.0);
        assertTrue(loaded.maxHeight > 2.0, "3,2 kg am 7-Zöller fliegt noch: " + loaded.maxHeight);
        assertTrue(loaded.maxHeight < 0.75 * capped.maxHeight, loaded.maxHeight + " gegen " + capped.maxHeight + " mit 2 kg");
        assertTrue(grounded.maxHeight < 0.1, "9,6 kg hebt ab: " + grounded.maxHeight);
        assertTrue(BuildAnalysis.of(tooHeavy).thrustToWeight() < 1.0);
    }

    private static final class Flight {
        boolean finite = true;
        boolean armed;
        double maxHeight;
        double minBottom = Double.POSITIVE_INFINITY;
        double maxTiltDeg;

        static Flight fullThrottle(DroneBuild build, double seconds) {
            Flight out = new Flight();
            DroneSim sim = new DroneSim(build, new FlightControllerConfig(), new SimTuning(), 42L);
            PilotInput input = new PilotInput();
            SimEnvironment env = new SimEnvironment();
            StepResult result = new StepResult();
            MoveReport report = new MoveReport();
            Quaterniond attitude = new Quaterniond();
            double foot = sim.params().hitboxFootOffset;
            double y = GROUND - foot;
            sim.reset(0.0, y, 0.0, 0.0);
            sim.setBatteryCharge(1.0);
            input.mode = StabMode.ANGLE;
            long frame = Math.round(1.0E9 / FPS);
            for (int i = 0; i < (int) (seconds * FPS); i++) {
                input.armSwitch = i >= 30;
                input.throttle = i >= 60 ? 1.0 : 0.0;
                env.groundDistance = Math.max(0.0, y + foot - GROUND);
                sim.step(frame, input, env, result);
                double dy = result.dy;
                double actual = dy < 0.0 ? Math.max(dy, Math.min(0.0, GROUND - (y + foot))) : dy;
                y += actual;
                report.clear();
                report.requestedX = result.dx;
                report.requestedY = dy;
                report.requestedZ = result.dz;
                report.actualX = result.dx;
                report.actualY = actual;
                report.actualZ = result.dz;
                report.hitY = Math.abs(actual - dy) > 1.0E-7;
                report.supportGap = Math.min(0.05, Math.max(0.0, y + foot - GROUND));
                sim.afterMove(report);
                sim.getAttitude(attitude);
                double up = 1.0 - 2.0 * (attitude.x * attitude.x + attitude.z * attitude.z);
                out.maxTiltDeg = Math.max(out.maxTiltDeg, Math.toDegrees(Math.acos(Math.clamp(up, -1.0, 1.0))));
                out.finite &= Double.isFinite(y) && Double.isFinite(result.dx) && Double.isFinite(result.dz) && Double.isFinite(attitude.w);
                out.armed |= sim.telemetry().armed;
                out.maxHeight = Math.max(out.maxHeight, y + foot - GROUND);
                out.minBottom = Math.min(out.minBottom, y + foot - GROUND);
            }
            return out;
        }
    }
}
