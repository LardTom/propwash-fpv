package de.lardtom.propwash.drone;

import de.lardtom.propwash.api.part.AirframeGeometry;
import de.lardtom.propwash.api.part.FrameRole;
import de.lardtom.propwash.build.DroneBuild;
import de.lardtom.propwash.build.FrameType;
import de.lardtom.propwash.sim.AirframeSpec;
import de.lardtom.propwash.sim.CameraOcclusion;
import de.lardtom.propwash.sim.FpvRig;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.TreeMap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CameraMountTest {
    @Test
    void everyFrameClassHasARangeInsideTheGeometryLimits() {
        for (FrameRole role : FrameRole.values()) {
            CameraMount.Range range = CameraMount.roleRange(role);
            assertTrue(range.minDeg() >= AirframeGeometry.MIN_CAMERA_TILT && range.maxDeg() <= AirframeGeometry.MAX_CAMERA_TILT, role.key());
            assertTrue(range.maxDeg() - range.minDeg() >= 25.0F, role.key() + " lässt genug Spielraum");
            assertTrue(range.contains(range.defaultDeg()), role.key() + " typischer Winkel im Bereich");
        }
        assertTrue(CameraMount.roleRange(FrameRole.RACE).maxDeg() > CameraMount.roleRange(FrameRole.FREESTYLE).maxDeg(), "Race-Halter steiler");
        assertTrue(CameraMount.roleRange(FrameRole.CINEWHOOP).maxDeg() < CameraMount.roleRange(FrameRole.FREESTYLE).maxDeg(), "Cinewhoop flacher");
    }

    @Test
    void theFrameDefaultAlwaysLiesInsideItsRange() {
        for (FrameType frame : FrameType.values()) {
            CameraMount.Range range = CameraMount.range(frame);
            float standard = TuneMapping.clampUptilt((float) frame.definition().geometry().cameraTiltDeg());
            assertEquals(standard, range.defaultDeg(), frame.key());
            assertTrue(range.contains(standard), frame.key());
            assertEquals(standard, CameraMount.defaultUptilt(frame), frame.key());
        }
        CameraMount.Range race = CameraMount.range(FrameType.RACE_5);
        assertEquals(15.0F, race.minDeg());
        assertEquals(70.0F, race.maxDeg());
        assertEquals(38.0F, race.defaultDeg());
    }

    @Test
    void aMissingValueMeansTheFrameDefaultSoOldDronesStayTheSame() {
        for (DroneBuild build : DroneBuild.PRESETS) {
            assertEquals(CameraMount.defaultUptilt(build.frame()), CameraMount.uptilt(build, Map.of()), build.frame().key());
            assertEquals((float) FpvRig.of(build).tiltDeg(), CameraMount.uptilt(build, Map.of()), build.frame().key());
            assertFalse(CameraMount.custom(build, Map.of()));
        }
    }

    @Test
    void storedValuesAreClampedToTheCameraMount() {
        DroneBuild whoop = DroneBuild.WHOOP;
        assertEquals(35.0F, CameraMount.uptilt(whoop, Map.of(TuneMapping.CAMERA_UPTILT, 60.0F)));
        assertEquals(10.0F, CameraMount.uptilt(whoop, Map.of(TuneMapping.CAMERA_UPTILT, 0.0F)));
        assertEquals(22.0F, CameraMount.uptilt(whoop, Map.of(TuneMapping.CAMERA_UPTILT, 22.0F)));
        assertEquals(CameraMount.defaultUptilt(whoop.frame()), CameraMount.uptilt(whoop, Map.of(TuneMapping.CAMERA_UPTILT, Float.NaN)));
        assertTrue(CameraMount.custom(whoop, Map.of(TuneMapping.CAMERA_UPTILT, 22.0F)));
    }

    @Test
    void sanitizeStoresOnlyRealChanges() {
        FrameType frame = FrameType.FREESTYLE_5;
        Map<String, Float> tune = new TreeMap<>(Map.of("rates.roll.rcRate", 1.2F));
        assertEquals(tune, CameraMount.sanitize(tune, frame));
        Map<String, Float> same = CameraMount.withUptilt(tune, frame, CameraMount.defaultUptilt(frame));
        assertNull(same.get(TuneMapping.CAMERA_UPTILT), "Standardwert wird nicht gespeichert");
        assertEquals(1.2F, same.get("rates.roll.rcRate"));
        Map<String, Float> steep = CameraMount.withUptilt(tune, frame, 75.0F);
        assertEquals(50.0F, steep.get(TuneMapping.CAMERA_UPTILT));
        Map<String, Float> custom = CameraMount.withUptilt(tune, frame, 35.0F);
        assertEquals(35.0F, custom.get(TuneMapping.CAMERA_UPTILT));
    }

    @Test
    void uptiltChangesOnlyTheCameraNotTheFlightModel() {
        DroneBuild build = DroneBuild.FREESTYLE;
        FpvRig rig = FpvRig.of(build);
        double standard = CameraOcclusion.percent(build);
        double steep = CameraOcclusion.percent(rig, 45.0, rig.horizontalFovDeg(), FpvRig.ASPECT, CameraOcclusion.ROWS);
        assertTrue(steep > standard, "5″ mit 45° zeigt mehr Props als mit 28°");
        Map<String, Float> tune = CameraMount.withUptilt(Map.of(), build.frame(), 45.0F);
        AirframeSpec spec = AirframeSpec.of(build);
        assertEquals(TuneMapping.fromConfig(TuneMapping.toConfig(Map.of(), spec)), TuneMapping.fromConfig(TuneMapping.toConfig(tune, spec)));
    }
}
