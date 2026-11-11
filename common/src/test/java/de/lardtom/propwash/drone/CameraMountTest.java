package de.lardtom.propwash.drone;

import de.lardtom.propwash.api.part.AirframeGeometry;
import de.lardtom.propwash.api.part.FrameRole;
import de.lardtom.propwash.build.DroneBuild;
import de.lardtom.propwash.build.FrameType;
import de.lardtom.propwash.core.FlightTune;
import de.lardtom.propwash.sim.AirframeSpec;
import de.lardtom.propwash.sim.CameraOcclusion;
import de.lardtom.propwash.sim.FpvRig;
import de.lardtom.propwash.ui.TuneEdits;
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

    private static final String RC_RATE = "rates.roll.rcRate";

    private static Map<String, Float> menuDefaults(DroneBuild build) {
        Map<String, Float> defaults = new TreeMap<>(TuneMapping.defaults(AirframeSpec.of(build)));
        defaults.put(TuneMapping.CAMERA_UPTILT, CameraMount.defaultUptilt(build.frame()));
        return defaults;
    }

    private static Map<String, Float> menuWorking(DroneBuild build, Map<String, Float> stored) {
        Map<String, Float> working = TuneMapping.effective(stored, AirframeSpec.of(build));
        working.putIfAbsent(TuneMapping.CAMERA_UPTILT, CameraMount.defaultUptilt(build.frame()));
        return working;
    }

    private static Map<String, Float> server(DroneBuild build, Map<String, Float> stored, Map<String, Float> edited) {
        return DronePiloting.sanitize(new FlightTune(stored), new FlightTune(edited), build).values();
    }

    @Test
    void theConfiguratorPathStoresTheAngleWithoutTouchingTheRest() {
        DroneBuild build = DroneBuild.FREESTYLE;
        Map<String, Float> stored = Map.of(RC_RATE, 1.2F);
        Map<String, Float> defaults = menuDefaults(build);
        Map<String, Float> edited = TuneEdits.set(menuWorking(build, stored), defaults, TuneMapping.CAMERA_UPTILT, 40.0);
        assertEquals(40.0F, edited.get(TuneMapping.CAMERA_UPTILT));
        Map<String, Float> client = TuneMapping.applyEdit(stored, edited, AirframeSpec.of(build));
        assertEquals(40.0F, client.get(TuneMapping.CAMERA_UPTILT), "Client wendet den Winkel sofort an");
        Map<String, Float> result = server(build, stored, edited);
        assertEquals(40.0F, CameraMount.uptilt(build, result));
        assertEquals(1.2F, result.get(RC_RATE), "Rates bleiben beim Ändern des Kamerawinkels erhalten");
        assertTrue(CameraMount.custom(build, result));
    }

    @Test
    void theServerClampsAngleFromTheConfiguratorToTheCameraMount() {
        for (DroneBuild build : DroneBuild.PRESETS) {
            CameraMount.Range range = CameraMount.range(build);
            Map<String, Float> working = menuWorking(build, Map.of());
            Map<String, Float> defaults = menuDefaults(build);
            Map<String, Float> steep = TuneEdits.set(working, defaults, TuneMapping.CAMERA_UPTILT, TuneMapping.CAMERA_UPTILT_MAX + 15.0);
            assertEquals(range.maxDeg(), CameraMount.uptilt(build, server(build, Map.of(), steep)), build.frame().key());
            Map<String, Float> flat = TuneEdits.set(working, defaults, TuneMapping.CAMERA_UPTILT, -20.0);
            assertEquals(range.minDeg(), CameraMount.uptilt(build, server(build, Map.of(), flat)), build.frame().key());
            Map<String, Float> inside = TuneEdits.set(working, defaults, TuneMapping.CAMERA_UPTILT, range.minDeg() + 1.0);
            assertEquals(range.minDeg() + 1.0F, CameraMount.uptilt(build, server(build, Map.of(), inside)), build.frame().key());
        }
    }

    @Test
    void defaultAndResetInTheConfiguratorRemoveTheStoredAngle() {
        DroneBuild build = DroneBuild.RACER;
        CameraMount.Range range = CameraMount.range(build);
        Map<String, Float> stored = Map.of(TuneMapping.CAMERA_UPTILT, 52.0F, RC_RATE, 1.1F);
        Map<String, Float> defaults = menuDefaults(build);
        Map<String, Float> working = menuWorking(build, stored);
        assertEquals(52.0F, working.get(TuneMapping.CAMERA_UPTILT));
        Map<String, Float> standard = TuneEdits.set(working, defaults, TuneMapping.CAMERA_UPTILT, range.defaultDeg());
        assertNull(standard.get(TuneMapping.CAMERA_UPTILT), "Standardwert wird im K-Menü nicht gespeichert");
        Map<String, Float> afterStandard = server(build, stored, standard);
        assertNull(afterStandard.get(TuneMapping.CAMERA_UPTILT));
        assertEquals(range.defaultDeg(), CameraMount.uptilt(build, afterStandard));
        assertEquals(1.1F, afterStandard.get(RC_RATE));
        Map<String, Float> reset = TuneEdits.reset(working, defaults, TuneMapping.CAMERA_UPTILT);
        assertNull(server(build, stored, reset).get(TuneMapping.CAMERA_UPTILT), "↺ setzt auf den Standard des Rahmens zurück");
        Map<String, Float> untouched = TuneEdits.set(working, defaults, RC_RATE, 1.3);
        assertEquals(52.0F, server(build, stored, untouched).get(TuneMapping.CAMERA_UPTILT), "andere Änderungen im K-Menü behalten den Winkel");
    }
}
