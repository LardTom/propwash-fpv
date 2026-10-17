package de.lardtom.propwash.sim;

import de.lardtom.propwash.build.AccessoryType;
import de.lardtom.propwash.build.BatteryType;
import de.lardtom.propwash.build.DroneBuild;
import de.lardtom.propwash.build.FrameType;
import de.lardtom.propwash.build.MotorType;
import de.lardtom.propwash.build.PartInfo;
import de.lardtom.propwash.build.PropType;
import de.lardtom.propwash.build.StackType;
import de.lardtom.propwash.build.VideoType;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;

class AirframeGoldenTest {
    private static final String FILE = "de/lardtom/propwash/sim/airframe_golden.txt";
    private static final List<String> FIELDS = List.of("buildKey", "buildable", "massKg", "armLength", "motorOffset", "propDiameter",
            "propRadius", "discArea", "propPitch", "ducted", "comHeight", "rotorPlaneOffset", "inertiaXx", "inertiaYy", "inertiaZz", "kv", "kt",
            "motorResistance", "escResistance", "phaseResistance", "idleCurrent", "escCurrentLimit", "escInputLimit", "rotorInertia",
            "rotorInertiaEff", "omegaMax", "hoverOmega", "motorTauPhys", "motorTau", "kThrust", "kTorque", "ductGain", "zeroThrustPerOmega",
            "translationalLiftGain", "hoverInducedVelocity", "washNoiseHz", "cells", "capacityMah", "chemistry", "packResistance",
            "polarizationResistance", "continuousCurrent", "fullPackVoltage", "auxPower", "accessoryPower", "accessoryMassKg", "brownoutVoltage",
            "areaX", "areaY", "areaZ", "dragCoefficient", "rotationalDamping", "impactTolerance", "restitution", "friction", "hitboxWidth",
            "hitboxHeight", "hitboxFootOffset", "authorityRoll", "authorityPitch", "authorityYawSteady", "authorityYawInstant", "hoverVoltage",
            "lensRight", "lensUp", "lensForward", "washLoss", "washNoise", "motorMassKg", "stackMassKg", "motorX", "motorY", "motorZ", "spin",
            "hullX", "hullY", "hullZ");

    private static <T extends PartInfo> List<T> builtins(T[] values) {
        List<T> out = new ArrayList<>();
        for (T value : values) {
            if (value.builtin() && !(value instanceof StackType stack && stack.creative())) {
                out.add(value);
            }
        }
        return out;
    }

    static Map<String, String> groups() throws ReflectiveOperationException {
        Map<String, String> out = new LinkedHashMap<>();
        for (FrameType frame : builtins(FrameType.values())) {
            for (MotorType motor : builtins(MotorType.values())) {
                for (PropType prop : builtins(PropType.values())) {
                    StringBuilder dump = new StringBuilder();
                    for (StackType stack : builtins(StackType.values())) {
                        for (VideoType video : builtins(VideoType.values())) {
                            for (BatteryType battery : builtins(BatteryType.values())) {
                                dump(dump, AirframeSpec.of(new DroneBuild(frame, stack, motor, prop, video, battery)));
                            }
                        }
                    }
                    out.put(frame.key() + "/" + motor.key() + "/" + prop.key(), hash(dump));
                }
            }
        }
        for (DroneBuild preset : BuiltinRegressionTest.goldenPresets()) {
            for (DroneBuild build : List.of(preset, preset.withAccessory(AccessoryType.LED_STRIP), preset.withAccessory(AccessoryType.ACTION_CAM_HEAVY),
                    preset.withAccessory(AccessoryType.ACTION_CAM_LIGHT).withAccessory(AccessoryType.LED_STRIP))) {
                if (!build.buildable() && build != preset) {
                    continue;
                }
                StringBuilder dump = new StringBuilder();
                dump(dump, AirframeSpec.of(build));
                BuildAnalysis a = BuildAnalysis.of(build);
                for (double v : new double[]{a.massGrams(), a.thrustToWeight(), a.maxThrustPerMotorGrams(), a.hoverThrottlePercent(), a.hoverCurrentA(),
                        a.hoverFlightTimeMin(), a.cruiseFlightTimeMin(), a.fullThrottleCurrentA(), a.fullThrottleCellVoltage(), a.escLoadPercent(),
                        a.batteryLoadPercent(), a.topSpeedAcroKmh(), a.topSpeedAngleKmh(), a.motorResponseMs(), a.rollAccelDps2(), a.crashSpeedMs()}) {
                    dump.append(Long.toHexString(Double.doubleToRawLongBits(v))).append(',');
                }
                TreeSet<String> warnings = new TreeSet<>();
                a.warnings().forEach(w -> warnings.add(w.name()));
                dump.append(warnings).append(build.problems()).append(build.warnings());
                out.put("analysis:" + AirframeSpec.of(build).buildKey, hash(dump));
            }
        }
        return out;
    }

    private static void dump(StringBuilder out, AirframeSpec spec) throws ReflectiveOperationException {
        for (String name : FIELDS) {
            Field field = AirframeSpec.class.getDeclaredField(name);
            field.setAccessible(true);
            Object value = field.get(spec);
            out.append(name).append('=');
            switch (value) {
                case Double d -> out.append(Long.toHexString(Double.doubleToRawLongBits(d)));
                case double[] array -> {
                    for (double d : array) {
                        out.append(Long.toHexString(Double.doubleToRawLongBits(d))).append(',');
                    }
                }
                case int[] array -> out.append(Arrays.toString(array));
                case BatteryType.Chemistry chemistry -> out.append(chemistry.name());
                default -> out.append(value);
            }
            out.append(';');
        }
        out.append('\n');
    }

    private static String hash(CharSequence text) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(text.toString().getBytes(StandardCharsets.UTF_8))).substring(0, 24);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    private static Path file() {
        for (Path candidate : List.of(Path.of("common/src/test/resources", FILE), Path.of("src/test/resources", FILE))) {
            if (Files.isDirectory(candidate.getParent())) {
                return candidate;
            }
        }
        return Path.of("common/src/test/resources", FILE);
    }

    @Test
    void builtinAirframesStayBitIdentical() throws Exception {
        Map<String, String> actual = groups();
        Path path = file();
        if (!Files.exists(path)) {
            Files.createDirectories(path.getParent());
            StringBuilder text = new StringBuilder();
            actual.forEach((key, hash) -> text.append(key).append(' ').append(hash).append('\n'));
            Files.writeString(path, text.toString(), StandardCharsets.UTF_8);
            fail("Golden-Datei fehlte und wurde neu geschrieben: " + path + " — nur einchecken, wenn die Änderung gewollt ist");
        }
        Map<String, String> expected = new LinkedHashMap<>();
        try {
            for (String line : Files.readAllLines(path, StandardCharsets.UTF_8)) {
                if (!line.isBlank()) {
                    int space = line.lastIndexOf(' ');
                    expected.put(line.substring(0, space), line.substring(space + 1));
                }
            }
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
        List<String> changed = new ArrayList<>();
        for (Map.Entry<String, String> entry : expected.entrySet()) {
            if (!entry.getValue().equals(actual.get(entry.getKey()))) {
                changed.add(entry.getKey());
            }
        }
        assertEquals(List.of(), changed, "Eingebaute Drohnen fliegen anders als in 0.3.2");
        assertEquals(expected.keySet(), actual.keySet());
    }
}
