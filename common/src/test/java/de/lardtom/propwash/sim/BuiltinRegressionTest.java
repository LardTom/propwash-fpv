package de.lardtom.propwash.sim;

import de.lardtom.propwash.build.AccessoryType;
import de.lardtom.propwash.build.BatteryType;
import de.lardtom.propwash.build.DroneBuild;
import de.lardtom.propwash.build.FrameType;
import de.lardtom.propwash.build.MotorType;
import de.lardtom.propwash.build.PartInfo;
import de.lardtom.propwash.build.PartWear;
import de.lardtom.propwash.build.PropType;
import de.lardtom.propwash.build.StackType;
import de.lardtom.propwash.build.VideoType;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BuiltinRegressionTest {
    static final Set<String> LEGACY_WARNINGS = Set.of("CANNOT_HOVER", "UNDERPOWERED", "SLUGGISH_MOTORS", "HEAVY_SAG", "ESC_CURRENT_LIMITED",
            "BATTERY_OVERLOAD");
    static final List<String> TELEMETRY = List.of("time", "substeps", "armBlockers", "failsafeStage", "faults", "crashCount", "impactCount",
            "propStrikeCount", "armed", "airmodeActive", "grounded", "sleeping", "motorSaturated", "inGroundEffect", "mode", "lastDisarmReason",
            "batteryState", "motorRpm", "motorOutput", "motorDuty", "motorCurrentA", "motorThrustN", "motorTempC", "escTempC", "totalCurrentA",
            "packVoltage", "cellVoltage", "cellVoltageResting", "mAhUsed", "chargeFraction", "powerW", "propWash", "propWashVrs", "propWashWake",
            "groundEffectFactor", "totalThrustN", "thrustToWeight", "gLoad", "gLoadTotal", "gLoadMax", "speed", "airspeed", "verticalSpeed",
            "heightAboveGround", "rollDeg", "pitchDeg", "headingDeg", "tiltDeg", "gyroDps", "setpointDps", "pTerm", "iTerm", "dTerm", "fTerm",
            "throttleOut", "hoverThrottleStick", "attitudeErrorDeg", "lastImpactSpeed", "vibration", "holdMode", "holdRequested", "posHoldDenied",
            "posHoldReady", "gpsSatellites", "gpsFix", "gpsHdop", "altitudeEstimate", "baroAltitude", "holdTargetAltitude", "holdPositionError",
            "gpsPositionError", "headingErrorDeg");
    static final List<String> LEGACY_EVENTS = List.of("ARMED", "DISARMED", "ARM_BLOCKED", "CRASH", "HARD_IMPACT", "BUMP", "PROP_STRIKE", "LANDED",
            "TAKEOFF", "BATTERY_LOW", "BATTERY_CRITICAL", "PACK_DEPLETED", "BROWNOUT", "ESC_CURRENT_LIMIT", "MOTOR_OVERHEAT", "FAILSAFE",
            "AIRMODE_ACTIVE", "FRAME_HITCH", "SIM_FAULT");
    private static final String FILE = "de/lardtom/propwash/sim/builtin_regression.txt";
    private static final String REST_FILE = "de/lardtom/propwash/sim/builtin_ground_rest.txt";
    private static boolean restFlat;
    private static List<Boolean> flying;

    static SimTuning tuning() {
        SimTuning tuning = new SimTuning();
        tuning.restFlat = restFlat;
        return tuning;
    }
    private static final double FPS = 144.0;

    static <T extends PartInfo> List<T> builtins(T[] values) {
        List<T> out = new ArrayList<>();
        for (T value : values) {
            if (value.builtin() && !(value instanceof StackType stack && stack.creative())) {
                out.add(value);
            }
        }
        return out;
    }

    static List<DroneBuild> presetVariants() {
        List<DroneBuild> out = new ArrayList<>();
        for (DroneBuild preset : DroneBuild.PRESETS) {
            for (DroneBuild build : List.of(preset, preset.withAccessory(AccessoryType.LED_STRIP),
                    preset.withAccessory(AccessoryType.ACTION_CAM_HEAVY),
                    preset.withAccessory(AccessoryType.ACTION_CAM_LIGHT).withAccessory(AccessoryType.LED_STRIP))) {
                if (build == preset || build.buildable()) {
                    out.add(build);
                }
            }
        }
        return out;
    }

    static List<DroneBuild> buildableCombinations() {
        List<DroneBuild> out = new ArrayList<>();
        for (FrameType frame : builtins(FrameType.values())) {
            for (StackType stack : builtins(StackType.values())) {
                for (MotorType motor : builtins(MotorType.values())) {
                    for (PropType prop : builtins(PropType.values())) {
                        for (VideoType video : builtins(VideoType.values())) {
                            for (BatteryType battery : builtins(BatteryType.values())) {
                                DroneBuild build = new DroneBuild(frame, stack, motor, prop, video, battery);
                                if (build.buildable()) {
                                    out.add(build);
                                }
                            }
                        }
                    }
                }
            }
        }
        return out;
    }

    static void analysis(StringBuilder out, DroneBuild build) {
        BuildAnalysis a = BuildAnalysis.of(build);
        for (double v : new double[]{a.massGrams(), a.thrustToWeight(), a.maxThrustPerMotorGrams(), a.hoverThrottlePercent(), a.hoverCurrentA(),
                a.hoverFlightTimeMin(), a.cruiseFlightTimeMin(), a.fullThrottleCurrentA(), a.fullThrottleCellVoltage(), a.escLoadPercent(),
                a.batteryLoadPercent(), a.topSpeedAcroKmh(), a.topSpeedAngleKmh(), a.motorResponseMs(), a.rollAccelDps2(), a.crashSpeedMs()}) {
            GoldenFile.bits(out, v);
        }
        TreeSet<String> warnings = new TreeSet<>();
        a.warnings().forEach(w -> {
            if (LEGACY_WARNINGS.contains(w.name())) {
                warnings.add(w.name());
            }
        });
        out.append(warnings).append(build.problems()).append(build.warnings()).append('\n');
    }

    static void telemetry(StringBuilder out, Telemetry t) {
        try {
            for (String name : TELEMETRY) {
                Field field = Telemetry.class.getField(name);
                Object value = field.get(t);
                switch (value) {
                    case Double d -> GoldenFile.bits(out, d);
                    case double[] array -> {
                        for (double d : array) {
                            GoldenFile.bits(out, d);
                        }
                    }
                    default -> out.append(value).append(',');
                }
            }
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
        out.append('\n');
    }

    static void events(StringBuilder out, DroneSim sim) {
        EventQueue events = sim.events();
        for (int i = 0; i < events.size(); i++) {
            String name = events.type(i).name();
            if (!LEGACY_EVENTS.contains(name)) {
                continue;
            }
            out.append(name).append('@');
            GoldenFile.bits(out, events.time(i));
            GoldenFile.bits(out, events.value(i));
            out.append(events.motor(i)).append(';');
        }
        ImpactLog impacts = sim.impacts();
        for (int i = 0; i < impacts.size(); i++) {
            out.append(impacts.get(i)).append(';');
        }
    }

    static void frame(StringBuilder out, FlatWorld w) {
        w.frame(1.0 / FPS);
        if (flying != null) {
            flying.add(w.sim.arming.armed || w.sim.groundedFrames == 0);
        }
        StepResult r = w.result;
        GoldenFile.bits(out, r.dx);
        GoldenFile.bits(out, r.dy);
        GoldenFile.bits(out, r.dz);
        GoldenFile.bits(out, r.orientation.x);
        GoldenFile.bits(out, r.orientation.y);
        GoldenFile.bits(out, r.orientation.z);
        GoldenFile.bits(out, r.orientation.w);
        out.append(r.armed).append(r.sleeping).append(r.substeps);
        events(out, w.sim);
        telemetry(out, w.sim.telemetry());
    }

    static String acroFlight(DroneBuild build, PartWear wear) {
        FlatWorld w = FlatWorld.of(build, tuning());
        StringBuilder out = new StringBuilder();
        w.placeOnGround(0.0, 0.0, 0.3);
        w.sim.setWear(wear);
        w.input.mode = StabMode.ACRO;
        w.arm();
        double hover = w.sim.telemetry().hoverThrottleStick;
        int frames = (int) Math.round(9.0 * FPS);
        for (int k = 0; k < frames; k++) {
            double t = k / FPS;
            PilotInput in = w.input;
            in.roll = 0.0;
            in.pitch = 0.0;
            in.yaw = 0.0;
            if (t < 0.5) {
                in.throttle = 0.0;
            } else if (t < 1.5) {
                in.throttle = (hover + 0.12) * (t - 0.5);
            } else if (t < 4.0) {
                in.throttle = hover + 0.03;
                if (t >= 2.0 && t < 2.3) {
                    in.roll = 0.35;
                } else if (t >= 3.0 && t < 3.4) {
                    in.pitch = 0.5;
                } else if (t >= 3.4 && t < 4.0) {
                    in.yaw = 0.7;
                }
            } else if (t < 4.6) {
                in.throttle = 1.0;
            } else if (t < 8.0) {
                in.throttle = t < 5.5 ? 0.0 : Math.max(0.0, hover - 0.1);
                in.pitch = t < 6.0 ? -0.2 : 0.0;
            } else {
                in.throttle = 0.0;
                in.armSwitch = t < 8.5;
            }
            frame(out, w);
        }
        return out.toString();
    }

    static String angleFlight(DroneBuild build) {
        FlatWorld w = FlatWorld.of(build, tuning());
        StringBuilder out = new StringBuilder();
        w.placeOnGround(0.0, 0.0, -0.4);
        w.env.windX = 2.0;
        w.env.gustIntensity = 0.3;
        w.input.mode = StabMode.ANGLE;
        w.arm();
        double hover = w.sim.telemetry().hoverThrottleStick;
        int frames = (int) Math.round(10.0 * FPS);
        for (int k = 0; k < frames; k++) {
            double t = k / FPS;
            PilotInput in = w.input;
            in.roll = 0.0;
            in.pitch = 0.0;
            in.yaw = 0.0;
            if (t < 1.0) {
                in.throttle = (hover + 0.15) * t;
            } else if (t < 5.0) {
                in.throttle = hover + 0.02;
                in.roll = 0.5 * Math.sin(2.0 * t);
                in.pitch = -0.4 * Math.cos(1.5 * t);
                in.yaw = t > 4.0 ? 0.3 : 0.0;
            } else if (t < 6.5) {
                in.throttle = hover;
            } else {
                in.throttle = Math.max(0.0, hover - 0.06);
            }
            frame(out, w);
        }
        return out.toString();
    }

    static String dropAndWall(DroneBuild build) {
        FlatWorld w = FlatWorld.of(build, tuning());
        StringBuilder out = new StringBuilder();
        w.place(0.0, 8.0, 0.0, 0.0);
        w.wallX = 1.2;
        w.input.mode = StabMode.ANGLE;
        int frames = (int) Math.round(9.0 * FPS);
        double hover = w.sim.telemetry().hoverThrottleStick;
        for (int k = 0; k < frames; k++) {
            double t = k / FPS;
            PilotInput in = w.input;
            in.roll = 0.0;
            in.pitch = 0.0;
            in.yaw = 0.0;
            in.armSwitch = t >= 3.0 && t < 8.5;
            if (t < 3.2) {
                in.throttle = 0.0;
            } else if (t < 4.0) {
                in.throttle = (hover + 0.1) * (t - 3.2) / 0.8;
            } else if (t < 7.0) {
                in.throttle = hover + 0.01;
                in.roll = t < 6.0 ? -0.6 : 0.0;
            } else {
                in.throttle = Math.max(0.0, hover - 0.08);
            }
            frame(out, w);
        }
        return out.toString();
    }

    static String fullThrottleRun(DroneBuild build, boolean thermalEffects) {
        SimTuning tuning = new SimTuning();
        tuning.thermalEffects = thermalEffects;
        DroneSim sim = new DroneSim(build, new FlightControllerConfig(), tuning, 7L);
        PilotInput input = new PilotInput();
        SimEnvironment env = new SimEnvironment();
        env.ambientTempC = 25.0;
        StepResult result = new StepResult();
        sim.reset(0.0, 50.0, 0.0, 0.0);
        sim.setPoseLocked(true);
        sim.step(0L, input, env, result);
        input.armSwitch = true;
        sim.step(0L, input, env, result);
        sim.setDutyOverride(1.0, 1.0, 1.0, 1.0);
        StringBuilder out = new StringBuilder();
        for (int k = 0; k < 1500 && sim.telemetry().armed; k++) {
            sim.step(100_000_000L, input, env, result);
            events(out, sim);
            telemetry(out, sim.telemetry());
        }
        return out.toString();
    }

    static Map<String, String> collect() {
        Map<String, String> out = new LinkedHashMap<>();
        for (FrameType frame : builtins(FrameType.values())) {
            for (MotorType motor : builtins(MotorType.values())) {
                for (PropType prop : builtins(PropType.values())) {
                    StringBuilder dump = new StringBuilder();
                    for (StackType stack : builtins(StackType.values())) {
                        for (VideoType video : builtins(VideoType.values())) {
                            for (BatteryType battery : builtins(BatteryType.values())) {
                                DroneBuild build = new DroneBuild(frame, stack, motor, prop, video, battery);
                                if (build.buildable()) {
                                    analysis(dump, build);
                                }
                            }
                        }
                    }
                    if (!dump.isEmpty()) {
                        out.put("analysis:" + frame.key() + "/" + motor.key() + "/" + prop.key(), GoldenFile.hash(dump));
                    }
                }
            }
        }
        PartWear worn = PartWear.of(new float[]{0.95F, 0.8F, 1.0F, 0.5F}, 0.6F, new float[]{1.0F, 0.85F, 0.7F, 1.0F},
                new float[]{0.3F, 1.0F, 0.88F, 0.75F}, 0.5F, 0.7F);
        for (DroneBuild build : presetVariants()) {
            String key = AirframeSpec.of(build).buildKey;
            StringBuilder analysis = new StringBuilder();
            analysis(analysis, build);
            out.put("preset:" + key, GoldenFile.hash(analysis));
            out.put("acro:" + key, GoldenFile.hash(acroFlight(build, PartWear.PRISTINE)));
            out.put("angle:" + key, GoldenFile.hash(angleFlight(build)));
            out.put("drop:" + key, GoldenFile.hash(dropAndWall(build)));
        }
        for (DroneBuild build : DroneBuild.PRESETS) {
            String key = AirframeSpec.of(build).buildKey;
            out.put("worn:" + key, GoldenFile.hash(acroFlight(build, worn)));
            out.put("full:" + key, GoldenFile.hash(fullThrottleRun(build, false)));
            out.put("full-thermal:" + key, GoldenFile.hash(fullThrottleRun(build, true)));
            StringBuilder crash = new StringBuilder();
            long seed = 11L;
            for (ImpactSample impact : List.of(new ImpactSample(9.0F, 2.0F, 0.0F, 1.0F, 0.0F, 1 << 4 | 1 << 5 | 1 << 11, 0, 0.0F),
                    new ImpactSample(14.0F, 6.0F, 0.0F, 0.0F, 1.0F, 1 | 1 << 1 | 1 << 10, 3, 1.2F),
                    new ImpactSample(6.0F, 1.0F, 0.0F, -1.0F, 0.0F, 1 << 8 | 1 << 9, 0, 0.0F),
                    new ImpactSample(2.0F, 0.0F, 1.0F, 0.0F, 0.0F, 1 << 2, 4, 0.8F))) {
                for (double strength : new double[]{0.5, 1.0, 3.0}) {
                    PartWear after = CrashDamage.apply(PartWear.PRISTINE, build, impact, strength, seed++);
                    crash.append(after).append(CrashDamage.harmful(build, impact)).append(CrashDamage.crash(build, impact));
                    GoldenFile.bits(crash, CrashDamage.dose(build, impact));
                    crash.append('\n');
                }
            }
            for (PartWear wear : List.of(PartWear.PRISTINE, worn)) {
                for (int position = 0; position < PartWear.POSITIONS; position++) {
                    double brittleness = AirframeSpec.of(build).propBrittleness;
                    GoldenFile.bits(crash, WearEffects.thrust(wear, position, brittleness));
                    GoldenFile.bits(crash, WearEffects.vibration(wear, position, brittleness));
                    GoldenFile.bits(crash, WearEffects.propLoad(wear.prop(position)));
                }
                GoldenFile.bits(crash, WearEffects.batteryResistance(wear));
                GoldenFile.bits(crash, WearEffects.gyroNoiseDps(wear));
            }
            out.put("wear:" + key, GoldenFile.hash(crash));
        }
        return out;
    }

    @Test
    void builtinPartsAnalyseAndFlyBitIdentical() {
        GoldenFile.verify(FILE, collect(), "Eingebaute Teile analysieren oder fliegen anders als in 0.3.2");
    }

    @Test
    void groundRestFliesBitIdentical() {
        Map<String, String> out = new LinkedHashMap<>();
        restFlat = true;
        try {
            for (DroneBuild build : presetVariants()) {
                String key = AirframeSpec.of(build).buildKey;
                out.put("acro:" + key, GoldenFile.hash(acroFlight(build, PartWear.PRISTINE)));
                out.put("angle:" + key, GoldenFile.hash(angleFlight(build)));
                out.put("drop:" + key, GoldenFile.hash(dropAndWall(build)));
            }
        } finally {
            restFlat = false;
        }
        GoldenFile.verify(REST_FILE, out, "Flüge mit flachem Liegen am Boden anders als in 0.5.0");
    }

    @Test
    void groundRestLeavesArmedFlightUntouched() {
        for (DroneBuild build : DroneBuild.PRESETS) {
            String key = AirframeSpec.of(build).buildKey;
            List<Boolean> trace = new ArrayList<>();
            flying = trace;
            String legacy;
            try {
                legacy = acroFlight(build, PartWear.PRISTINE);
            } finally {
                flying = null;
            }
            String rested;
            restFlat = true;
            try {
                rested = acroFlight(build, PartWear.PRISTINE);
            } finally {
                restFlat = false;
            }
            String[] a = legacy.split("\n");
            String[] b = rested.split("\n");
            assertEquals(trace.size(), a.length, key);
            assertEquals(a.length, b.length, key);
            int resting = trace.indexOf(Boolean.FALSE);
            assertTrue(resting > (int) Math.round(1.5 * FPS), key + ": liegt ab Frame " + resting);
            for (int k = 0; k < resting; k++) {
                assertEquals(a[k], b[k], key + " Frame " + k);
            }
            assertNotEquals(legacy, rested, key);
        }
    }
}
