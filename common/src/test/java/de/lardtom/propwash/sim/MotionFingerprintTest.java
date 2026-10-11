package de.lardtom.propwash.sim;

import de.lardtom.propwash.build.BatteryType;
import de.lardtom.propwash.build.DroneBuild;
import de.lardtom.propwash.build.PartWear;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class MotionFingerprintTest {
    static String spec(DroneBuild build, List<String> fields) {
        AirframeSpec s = AirframeSpec.of(build);
        StringBuilder out = new StringBuilder();
        try {
            for (String name : fields) {
                Field field = AirframeSpec.class.getDeclaredField(name);
                field.setAccessible(true);
                Object value = field.get(s);
                out.append(name).append('=');
                switch (value) {
                    case Double d -> GoldenFile.bits(out, d);
                    case double[] array -> {
                        for (double d : array) {
                            GoldenFile.bits(out, d);
                        }
                    }
                    case int[] array -> out.append(Arrays.toString(array));
                    case BatteryType.Chemistry chemistry -> out.append(chemistry.name());
                    default -> out.append(value);
                }
                out.append(';');
            }
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
        return GoldenFile.hash(out);
    }

    static String line(String key, DroneBuild build, List<String> fields) {
        return key + " spec " + spec(build, fields) + " acro " + GoldenFile.hash(BuiltinRegressionTest.acroFlight(build, PartWear.PRISTINE))
                + " angle " + GoldenFile.hash(BuiltinRegressionTest.angleFlight(build)) + " drop " + GoldenFile.hash(BuiltinRegressionTest.dropAndWall(build));
    }

    @Test
    @Tag(CalibrationHarness.ADDON_TAG)
    void everySingleSwapOfEveryPresetHasAMotionFingerprint() throws ReflectiveOperationException {
        assumeTrue(CalibrationHarness.addonRoot().isPresent(), CalibrationHarness.ADDON_HINT);
        List<CalibrationHarness.Entry> presets = new ArrayList<>(CalibrationHarness.builtins());
        presets.addAll(CalibrationHarness.addons());
        Field f = AirframeGoldenTest.class.getDeclaredField("FIELDS");
        f.setAccessible(true);
        @SuppressWarnings("unchecked")
        List<String> fields = (List<String>) f.get(null);
        Map<String, DroneBuild> builds = new LinkedHashMap<>();
        for (CalibrationHarness.Entry preset : presets) {
            for (DroneBuild build : FlightTimeOrderTest.singleSwaps(preset.build())) {
                if (build.buildable()) {
                    builds.putIfAbsent(FlightTimeOrderTest.key(build), build);
                }
            }
        }
        List<String> lines = new ArrayList<>(builds.entrySet()).parallelStream().map(e -> line(e.getKey(), e.getValue(), fields)).sorted().toList();
        CalibrationHarness.write("motion.txt", String.join("\n", lines) + "\n");
        assertTrue(lines.size() > 1000, "Varianten " + lines.size());
        assertEquals(lines.size(), lines.stream().map(l -> l.substring(0, l.indexOf(' '))).distinct().count());
    }
}
