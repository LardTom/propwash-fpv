package de.lardtom.propwash.sim;

import com.google.gson.JsonObject;
import com.mojang.serialization.JsonOps;
import de.lardtom.propwash.build.Accessories;
import de.lardtom.propwash.build.AccessoryType;
import de.lardtom.propwash.build.BatteryType;
import de.lardtom.propwash.build.DroneBuild;
import de.lardtom.propwash.build.FrameType;
import de.lardtom.propwash.build.MotorType;
import de.lardtom.propwash.build.PartCompatibility;
import de.lardtom.propwash.build.PartInfo;
import de.lardtom.propwash.build.PropType;
import de.lardtom.propwash.build.StackType;
import de.lardtom.propwash.build.VideoType;
import de.lardtom.propwash.core.DroneBuildCodecs;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.lang.reflect.RecordComponent;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Random;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class PartFreedomFlightTest {
    private static final double DT = 1.0 / 120.0;
    private static final Set<String> MAY_BE_UNAVAILABLE = Set.of("hoverThrottlePercent", "hoverCurrentA", "hoverFlightTimeMin",
            "cruiseFlightTimeMin", "mixedFlightTimeMin", "aggressiveFlightTimeMin", "cruiseCurrentA", "mixedCurrentA", "cruiseSpeedKmh",
            "landingCellVoltage", "sustainedMotorTempC", "motorTempMinuteC", "mixedMotorTempC", "propsInViewPercent");

    private static SimTuning tuning(boolean partCompatibility, boolean hardwareLimits) {
        SimTuning t = new SimTuning();
        t.partCompatibility = partCompatibility;
        t.thermalLimits = hardwareLimits;
        t.escOvervoltage = hardwareLimits;
        return t;
    }

    private static <T extends PartInfo> List<T> builtins(T[] values) {
        List<T> out = new ArrayList<>();
        for (T value : values) {
            if (value.builtin() && !(value instanceof StackType stack && stack.creative())) {
                out.add(value);
            }
        }
        return out;
    }

    private static DroneBuild whoopWithSevenInchProps() {
        return DroneBuild.WHOOP.withProp(PropType.P7_2B);
    }

    private static DroneBuild thirteenInchWhoop() {
        return DroneBuild.WHOOP.withProp(AddonParts.prop13x7());
    }

    private static DroneBuild eightCellsOnWhoopAio() {
        return DroneBuild.WHOOP.withBattery(AddonParts.lipo(8, 2200, 450.0, 0.008, 75));
    }

    private static DroneBuild missingMotor() {
        JsonObject json = DroneBuildCodecs.CODEC.encodeStart(JsonOps.INSTANCE, DroneBuild.FREESTYLE).getOrThrow().getAsJsonObject();
        json.addProperty("motor", "justmoreparts:not_installed");
        return DroneBuildCodecs.CODEC.parse(JsonOps.INSTANCE, json).getOrThrow();
    }

    private static void assertFinite(DroneSim sim, String what) {
        RigidBody b = sim.body;
        assertTrue(Double.isFinite(b.px + b.py + b.pz + b.vx + b.vy + b.vz + b.qw + b.qx + b.qy + b.qz + b.wbx + b.wby + b.wbz), what);
        PowerSystem p = sim.power;
        assertTrue(Double.isFinite(p.busVoltage + p.soc + p.mAhUsed), what);
        Telemetry t = sim.telemetry();
        assertTrue(Double.isFinite(t.gLoad + t.tiltDeg + t.hoverThrottleStick), what);
        for (int i = 0; i < AirframeSpec.MOTORS; i++) {
            assertTrue(Double.isFinite(p.omega[i] + t.motorThrustN[i] + t.motorDuty[i] + t.motorPowerLimit[i]), what + " Motor " + i);
        }
    }

    private static void assertAnalysisUsable(DroneBuild build, String what) {
        BuildAnalysis a = BuildAnalysis.of(build);
        for (RecordComponent component : BuildAnalysis.class.getRecordComponents()) {
            if (component.getType() != double.class) {
                continue;
            }
            double value;
            try {
                value = (double) component.getAccessor().invoke(a);
            } catch (ReflectiveOperationException e) {
                throw new AssertionError(e);
            }
            if (MAY_BE_UNAVAILABLE.contains(component.getName())) {
                assertFalse(Double.isInfinite(value), what + " " + component.getName() + " = " + value);
            } else {
                assertTrue(Double.isFinite(value), what + " " + component.getName() + " = " + value);
            }
        }
    }

    private static void assertBitIdentical(SimSnapshot a, SimSnapshot b, String what) {
        int compared = 0;
        for (Field field : SimSnapshot.class.getDeclaredFields()) {
            if (Modifier.isStatic(field.getModifiers())) {
                continue;
            }
            field.setAccessible(true);
            try {
                Object va = field.get(a);
                Object vb = field.get(b);
                if (va instanceof double[] da && vb instanceof double[] db) {
                    assertEquals(da.length, db.length, what + " " + field.getName());
                    for (int i = 0; i < da.length; i++) {
                        assertEquals(Double.doubleToRawLongBits(da[i]), Double.doubleToRawLongBits(db[i]), what + " " + field.getName() + "[" + i + "]");
                    }
                } else if (va instanceof Double da && vb instanceof Double db) {
                    assertEquals(Double.doubleToRawLongBits(da), Double.doubleToRawLongBits(db), what + " " + field.getName());
                } else if (va != null && va.getClass().isArray()) {
                    assertTrue(Objects.deepEquals(va, vb), what + " " + field.getName());
                } else {
                    assertEquals(va, vb, what + " " + field.getName());
                }
            } catch (IllegalAccessException e) {
                throw new AssertionError(e);
            }
            compared++;
        }
        assertTrue(compared > 20, "zu wenige Felder: " + compared);
    }

    private static FlatWorld armed(DroneBuild build, SimTuning tuning) {
        FlatWorld w = FlatWorld.of(build, tuning);
        w.placeOnGround(0.0, 0.0, 0.0);
        w.input.mode = StabMode.ACRO;
        w.input.throttle = 0.0;
        w.arm();
        return w;
    }

    private static int flyAggressively(FlatWorld w, double seconds, String what) {
        int faults = 0;
        int frames = (int) Math.round(seconds / DT);
        for (int f = 0; f < frames; f++) {
            double time = f * DT;
            w.input.throttle = time < 1.0 ? time : time < 3.0 ? 0.6 : 1.0;
            w.input.roll = time > 2.0 && time < 2.5 ? 1.0 : time > 4.5 && time < 4.8 ? -1.0 : 0.0;
            w.input.pitch = time > 3.0 && time < 3.5 ? -1.0 : 0.0;
            w.input.yaw = time > 4.0 ? 1.0 : 0.0;
            w.frame(DT);
            if (w.sim.events().contains(SimEventType.SIM_FAULT)) {
                faults++;
            }
            assertFinite(w.sim, what + " t=" + time);
        }
        return faults;
    }

    @Test
    void armingFollowsTheWorldRule() {
        DroneBuild build = whoopWithSevenInchProps();
        assertFalse(AirframeSpec.of(build).buildable);
        assertTrue(AirframeSpec.of(build).assemblable);
        FlatWorld strict = armed(build, tuning(true, true));
        assertFalse(strict.sim.telemetry().armed);
        assertTrue(ArmBlocker.BUILD_INVALID.isSet(strict.sim.telemetry().armBlockers));
        assertFalse(strict.sim.buildable());
        FlatWorld free = armed(build, tuning(false, true));
        assertTrue(free.sim.telemetry().armed, "Sperren " + free.sim.telemetry().armBlockers);
        assertTrue(free.sim.buildable());
        free.input.throttle = 0.7;
        free.run(1.0, 120.0);
        assertTrue(free.sim.telemetry().armed);
        free.sim.setTuning(tuning(true, true));
        free.run(0.5, 120.0);
        assertTrue(free.sim.telemetry().armed, "wer fliegt, fliegt weiter");
        free.input.throttle = 0.0;
        free.input.armSwitch = false;
        free.frame(DT);
        assertFalse(free.sim.telemetry().armed);
        free.input.armSwitch = true;
        free.frame(DT);
        assertFalse(free.sim.telemetry().armed, "nach dem Einschalten der Regel nicht wieder scharf");
        assertTrue(ArmBlocker.BUILD_INVALID.isSet(free.sim.telemetry().armBlockers));
    }

    @Test
    void validBuildsArmAndFlyBitIdenticallyWithTheRuleOff() {
        for (DroneBuild preset : DroneBuild.PRESETS) {
            FlatWorld on = armed(preset, tuning(true, true));
            FlatWorld off = armed(preset, tuning(false, true));
            assertTrue(on.sim.telemetry().armed && off.sim.telemetry().armed, preset.frame().key());
            flyAggressively(on, 5.0, preset.frame().key());
            flyAggressively(off, 5.0, preset.frame().key());
            SimSnapshot a = new SimSnapshot();
            SimSnapshot b = new SimSnapshot();
            on.sim.copyStateTo(a);
            off.sim.copyStateTo(b);
            assertBitIdentical(a, b, preset.frame().key());
            assertEquals(Double.doubleToLongBits(on.y), Double.doubleToLongBits(off.y), preset.frame().key());
        }
    }

    @Test
    void missingPartsNeverArm() {
        DroneBuild build = missingMotor();
        FlatWorld w = armed(build, tuning(false, false));
        assertFalse(w.sim.telemetry().armed);
        assertTrue(ArmBlocker.BUILD_INVALID.isSet(w.sim.telemetry().armBlockers));
    }

    @Test
    void thirteenInchPropsOnAWhoopFrameStayFinite() {
        DroneBuild build = thirteenInchWhoop();
        assertAnalysisUsable(build, "13 Zoll auf Whoop");
        FlatWorld w = armed(build, tuning(false, true));
        assertTrue(w.sim.telemetry().armed, "Sperren " + w.sim.telemetry().armBlockers);
        assertEquals(0, flyAggressively(w, 8.0, "13 Zoll auf Whoop"));
        assertTrue(w.sim.params().propDiameter > 0.3);
    }

    @Test
    void eightCellsOnAOneCellAioBurnTheEscOnlyWithHardwareLimits() {
        DroneBuild build = eightCellsOnWhoopAio();
        assertAnalysisUsable(build, "8S auf 1S-AIO");
        assertTrue(AirframeSpec.of(build).escOvervoltage);
        FlatWorld limited = armed(build, tuning(false, true));
        assertFalse(limited.sim.telemetry().armed);
        assertTrue(limited.sim.telemetry().escBurned);
        assertTrue(ArmBlocker.HARDWARE_FAULT.isSet(limited.sim.telemetry().armBlockers));
        assertFalse(ArmBlocker.BUILD_INVALID.isSet(limited.sim.telemetry().armBlockers));
        FlatWorld free = armed(build, tuning(false, false));
        assertTrue(free.sim.telemetry().armed, "Sperren " + free.sim.telemetry().armBlockers);
        assertFalse(free.sim.telemetry().escBurned);
        assertEquals(0, flyAggressively(free, 8.0, "8S auf 1S-AIO ohne Hardware-Grenzen"));
        double maxOmega = 0.0;
        for (double omega : free.sim.power.omega) {
            maxOmega = Math.max(maxOmega, omega);
        }
        assertTrue(maxOmega > AirframeSpec.of(DroneBuild.WHOOP).omegaMax, "8S dreht schneller als 1S: " + maxOmega);
        FlatWorld strict = armed(build, tuning(true, false));
        assertFalse(strict.sim.telemetry().armed);
        assertTrue(ArmBlocker.BUILD_INVALID.isSet(strict.sim.telemetry().armBlockers));
    }

    @Test
    void absurdCombinationsStayFiniteWithAndWithoutHardwareLimits() {
        List<FrameType> frames = List.of(FrameType.WHOOP_65, FrameType.LONG_RANGE_7, AddonParts.xClass13());
        List<StackType> stacks = List.of(StackType.AIO_WHOOP, StackType.STACK_30);
        List<MotorType> motors = List.of(MotorType.M0802_22000, MotorType.M2807_1300, AddonParts.motor4214());
        List<PropType> props = List.of(PropType.P31_4B, PropType.P7_2B, AddonParts.prop13x7());
        List<BatteryType> batteries = List.of(BatteryType.B1S_300_HV, BatteryType.B6S_4000_LION, AddonParts.lipo(8, 2200, 450.0, 0.008, 75),
                AddonParts.lipo(12, 5000, 1300.0, 0.004, 100));
        int flown = 0;
        for (FrameType frame : frames) {
            for (StackType stack : stacks) {
                for (MotorType motor : motors) {
                    for (PropType prop : props) {
                        for (BatteryType battery : batteries) {
                            DroneBuild build = new DroneBuild(frame, stack, motor, prop, VideoType.ANALOG_NANO, battery,
                                    Accessories.NONE.with(AccessoryType.ACTION_CAM_HEAVY).with(AccessoryType.GRIPPER));
                            String what = AirframeSpec.of(build).buildKey;
                            assertTrue(build.buildable(PartCompatibility.LIFTED), what);
                            assertAnalysisUsable(build, what);
                            for (boolean limits : new boolean[]{true, false}) {
                                FlatWorld w = armed(build, tuning(false, limits));
                                boolean burns = limits && AirframeSpec.of(build).escOvervoltage;
                                assertEquals(!burns, w.sim.telemetry().armed, what + " Grenzen " + limits + " Sperren " + w.sim.telemetry().armBlockers);
                                assertEquals(0, flyAggressively(w, 5.0, what), what + " Grenzen " + limits);
                                flown++;
                            }
                        }
                    }
                }
            }
        }
        assertEquals(432, flown);
    }

    @Test
    void everyBuiltinCombinationHasAUsableAnalysis() {
        int checked = 0;
        for (FrameType frame : builtins(FrameType.values())) {
            for (StackType stack : builtins(StackType.values())) {
                for (MotorType motor : builtins(MotorType.values())) {
                    for (PropType prop : builtins(PropType.values())) {
                        for (BatteryType battery : builtins(BatteryType.values())) {
                            DroneBuild build = new DroneBuild(frame, stack, motor, prop, VideoType.DIGITAL_HD, battery);
                            if (!build.buildable(PartCompatibility.LIFTED)) {
                                fail("nicht baubar mit Teile-Freiheit: " + build.problems());
                            }
                            assertAnalysisUsable(build, AirframeSpec.of(build).buildKey);
                            checked++;
                        }
                    }
                }
            }
        }
        assertTrue(checked >= 2250, "nur " + checked);
    }

    @Test
    @Tag(CalibrationHarness.ADDON_TAG)
    void everyAddonPartStaysFiniteInAbsurdCombinations() {
        assumeTrue(CalibrationHarness.addonRoot().isPresent(), CalibrationHarness.ADDON_HINT);
        assertFalse(CalibrationHarness.addons().isEmpty());
        FrameType[] frames = FrameType.values();
        StackType[] stacks = StackType.values();
        MotorType[] motors = MotorType.values();
        PropType[] props = PropType.values();
        BatteryType[] batteries = BatteryType.values();
        VideoType[] videos = VideoType.values();
        assertTrue(frames.length > builtins(frames).size() && props.length > builtins(props).size(), "keine Addon-Teile geladen");
        int analysed = 0;
        int i = 0;
        for (FrameType frame : frames) {
            for (PropType prop : props) {
                DroneBuild build = new DroneBuild(frame, stacks[i % stacks.length], motors[i % motors.length], prop, videos[i % videos.length],
                        batteries[i % batteries.length]);
                assertTrue(build.buildable(PartCompatibility.LIFTED), AirframeSpec.of(build).buildKey);
                assertAnalysisUsable(build, AirframeSpec.of(build).buildKey);
                analysed++;
                i++;
            }
        }
        for (MotorType motor : motors) {
            for (BatteryType battery : batteries) {
                DroneBuild build = new DroneBuild(frames[i % frames.length], stacks[i % stacks.length], motor, props[i % props.length],
                        videos[i % videos.length], battery);
                assertAnalysisUsable(build, AirframeSpec.of(build).buildKey);
                analysed++;
                i++;
            }
        }
        Random random = new Random(5L);
        int flown = 0;
        for (int n = 0; n < 120; n++) {
            DroneBuild build = new DroneBuild(frames[random.nextInt(frames.length)], stacks[random.nextInt(stacks.length)],
                    motors[random.nextInt(motors.length)], props[random.nextInt(props.length)], videos[random.nextInt(videos.length)],
                    batteries[random.nextInt(batteries.length)]);
            String what = AirframeSpec.of(build).buildKey;
            for (boolean limits : new boolean[]{true, false}) {
                FlatWorld w = armed(build, tuning(false, limits));
                boolean burns = limits && AirframeSpec.of(build).escOvervoltage;
                assertEquals(!burns, w.sim.telemetry().armed, what + " Grenzen " + limits + " Sperren " + w.sim.telemetry().armBlockers);
                assertEquals(0, flyAggressively(w, 3.0, what), what + " Grenzen " + limits);
                flown++;
            }
        }
        assertTrue(analysed > frames.length * props.length, "nur " + analysed);
        assertEquals(240, flown);
    }
}
