package de.lardtom.propwash.drone;

import de.lardtom.propwash.api.part.FrameRole;
import de.lardtom.propwash.build.DroneBuild;
import de.lardtom.propwash.build.FrameType;
import de.lardtom.propwash.build.StackType;
import org.junit.jupiter.api.Test;

import java.util.EnumMap;
import java.util.Map;
import java.util.TreeMap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CovCameraMountTest {
    private static final Map<FrameRole, float[]> TABLE = new EnumMap<>(FrameRole.class);

    static {
        TABLE.put(FrameRole.FREESTYLE, new float[]{0.0F, 50.0F});
        TABLE.put(FrameRole.RACE, new float[]{15.0F, 70.0F});
        TABLE.put(FrameRole.WHOOP, new float[]{10.0F, 35.0F});
        TABLE.put(FrameRole.CINEWHOOP, new float[]{0.0F, 35.0F});
        TABLE.put(FrameRole.LONG_RANGE, new float[]{0.0F, 40.0F});
        TABLE.put(FrameRole.TOOTHPICK, new float[]{10.0F, 40.0F});
        TABLE.put(FrameRole.CINELIFTER, new float[]{0.0F, 30.0F});
        TABLE.put(FrameRole.X_CLASS, new float[]{0.0F, 50.0F});
    }

    @Test
    void rangesPerFrameRole() {
        assertEquals(FrameRole.values().length, TABLE.size(), "jede Rahmenklasse steht in der Tabelle");
        for (Map.Entry<FrameRole, float[]> row : TABLE.entrySet()) {
            CameraMount.Range range = CameraMount.roleRange(row.getKey());
            String role = row.getKey().key();
            assertEquals(row.getValue()[0], range.minDeg(), role + " untere Grenze");
            assertEquals(row.getValue()[1], range.maxDeg(), role + " obere Grenze");
            assertEquals(row.getValue()[1], range.clamp(90.0F), role + " 90° wird auf die obere Grenze gesetzt");
            assertEquals(row.getValue()[0], range.clamp(-10.0F), role + " unter 0° wird auf die untere Grenze gesetzt");
        }
        Map<FrameType, FrameRole> builtin = Map.of(FrameType.WHOOP_65, FrameRole.WHOOP, FrameType.CINEWHOOP_3, FrameRole.CINEWHOOP,
                FrameType.FREESTYLE_5, FrameRole.FREESTYLE, FrameType.RACE_5, FrameRole.RACE, FrameType.LONG_RANGE_7, FrameRole.LONG_RANGE);
        for (Map.Entry<FrameType, FrameRole> e : builtin.entrySet()) {
            FrameType frame = e.getKey();
            assertEquals(e.getValue(), frame.definition().role(), frame.key());
            CameraMount.Range range = CameraMount.range(frame);
            float[] expected = TABLE.get(e.getValue());
            assertEquals(expected[0], range.minDeg(), frame.key());
            assertEquals(expected[1], range.maxDeg(), frame.key());
            assertEquals(expected[1], CameraMount.withUptilt(Map.of(), frame, 90.0F).get(TuneMapping.CAMERA_UPTILT), frame.key() + " 90° begrenzt");
        }
        for (DroneBuild build : DroneBuild.PRESETS) {
            float[] expected = TABLE.get(build.frame().definition().role());
            assertEquals(expected[1], CameraMount.uptilt(build, Map.of(TuneMapping.CAMERA_UPTILT, 90.0F)), build.frame().key());
            assertEquals(expected[0], CameraMount.uptilt(build, Map.of(TuneMapping.CAMERA_UPTILT, -5.0F)), build.frame().key());
        }
    }

    @Test
    void creativeDroneUsesTheFullRange() {
        for (DroneBuild preset : DroneBuild.PRESETS) {
            DroneBuild creative = new DroneBuild(preset.frame(), StackType.CREATIVE_FC, preset.motor(), preset.prop(), preset.video(), preset.battery());
            assertTrue(creative.isCreative(), preset.frame().key());
            CameraMount.Range range = CameraMount.range(creative);
            assertEquals(0.0F, range.minDeg(), preset.frame().key());
            assertEquals(80.0F, range.maxDeg(), preset.frame().key());
            assertEquals(CameraMount.defaultUptilt(preset.frame()), range.defaultDeg(), preset.frame().key());
            assertEquals(80.0F, CameraMount.uptilt(creative, Map.of(TuneMapping.CAMERA_UPTILT, 90.0F)), "Kreativ 90° begrenzt");
            assertEquals(60.0F, CameraMount.uptilt(creative, Map.of(TuneMapping.CAMERA_UPTILT, 60.0F)));
            Map<String, Float> stored = CameraMount.withUptilt(new TreeMap<>(), creative, 90.0F);
            assertEquals(80.0F, stored.get(TuneMapping.CAMERA_UPTILT));
        }
    }
}
