package de.lardtom.propwash.apiimpl;

import de.lardtom.propwash.api.build.BuildCheck;
import de.lardtom.propwash.api.build.BuildSpec;
import de.lardtom.propwash.api.build.BuildStats;
import de.lardtom.propwash.api.build.CameraView;
import de.lardtom.propwash.api.build.DronePreset;
import de.lardtom.propwash.api.build.TuneGroup;
import de.lardtom.propwash.api.build.TuneSpec;
import de.lardtom.propwash.api.part.AccessorySlot;
import de.lardtom.propwash.api.part.PartKind;
import de.lardtom.propwash.build.AccessoryType;
import de.lardtom.propwash.build.DroneBuild;
import de.lardtom.propwash.build.MotorType;
import de.lardtom.propwash.drone.CameraMount;
import de.lardtom.propwash.drone.TuneMapping;
import de.lardtom.propwash.drone.led.LedSettings;
import de.lardtom.propwash.sim.BuildAnalysis;
import de.lardtom.propwash.sim.CameraOcclusion;
import de.lardtom.propwash.sim.FpvRig;
import de.lardtom.propwash.video.FovMath;
import net.minecraft.resources.Identifier;
import org.junit.jupiter.api.Test;

import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BuildAccessTest {
    private static final BuildAccessImpl BUILDS = BuildAccessImpl.INSTANCE;
    private static final Identifier UNKNOWN = Identifier.fromNamespaceAndPath("somewhere", "else");

    @Test
    void checkReportsProblemsAndWarningsByStableKeys() {
        for (DroneBuild preset : DroneBuild.PRESETS) {
            BuildCheck check = BUILDS.check(Conversions.spec(preset));
            assertTrue(check.buildable(), check.toString());
        }
        DroneBuild mismatched = new DroneBuild(DroneBuild.WHOOP.frame(), DroneBuild.WHOOP.stack(), MotorType.M2207_1750,
                DroneBuild.WHOOP.prop(), DroneBuild.WHOOP.video(), DroneBuild.WHOOP.battery(), DroneBuild.WHOOP.accessories());
        BuildCheck check = BUILDS.check(Conversions.spec(mismatched));
        assertTrue(check.known());
        assertFalse(check.buildable());
        assertEquals(mismatched.problems().stream().map(i -> i.name().toLowerCase(Locale.ROOT)).toList(), check.problems());
        assertEquals(mismatched.warnings().stream().map(i -> i.name().toLowerCase(Locale.ROOT)).toList(), check.warnings());
        assertTrue(check.problems().contains("motor_mount"));
        assertEquals("propwash.build.issue.motor_mount", BuildCheck.translationKey("motor_mount"));
    }

    @Test
    void unknownPartsAndWrongSlotsAreReportedWithoutThrowing() {
        BuildSpec unknown = Conversions.spec(DroneBuild.RACER).withPart(PartKind.VIDEO, UNKNOWN);
        BuildCheck check = BUILDS.check(unknown);
        assertEquals(List.of(UNKNOWN), check.unknownParts());
        assertTrue(check.problems().isEmpty());
        assertFalse(check.buildable());
        assertEquals(Optional.empty(), BUILDS.analyze(unknown));
        BuildSpec wrongSlot = Conversions.spec(DroneBuild.FREESTYLE).withAccessory(AccessorySlot.TOP, AccessoryType.LED_STRIP.id());
        assertEquals(List.of(BuildAccessImpl.ACCESSORY_SLOT_ISSUE), BUILDS.check(wrongSlot).problems());
        assertThrows(IllegalArgumentException.class, () -> BuildAccessImpl.requireBuild(unknown));
        assertThrows(IllegalArgumentException.class, () -> BuildAccessImpl.requireBuild(wrongSlot));
        assertThrows(IllegalArgumentException.class, () -> BUILDS.defaultTune(unknown));
    }

    @Test
    void analysisMatchesTheWorkbenchNumbers() {
        DroneBuild build = DroneBuild.FREESTYLE.withAccessory(AccessoryType.ACTION_CAM_LIGHT);
        BuildStats stats = BUILDS.analyze(Conversions.spec(build)).orElseThrow();
        BuildAnalysis analysis = BuildAnalysis.of(build);
        assertEquals(analysis.massGrams(), stats.massGrams());
        assertEquals(analysis.thrustToWeight(), stats.thrustToWeight());
        assertEquals(analysis.topSpeedAcroKmh(), stats.topSpeedKmh());
        assertEquals(analysis.hoverFlightTimeMin(), stats.hoverFlightTimeMin());
        assertEquals(analysis.warnings().size(), stats.warnings().size());
        for (String warning : stats.warnings()) {
            assertTrue(BuildStats.warningTranslationKey(warning).startsWith("propwash.content.workbench.warning."));
        }
    }

    @Test
    void cameraViewDescribesTheFpvImageOfTheAnalysis() {
        for (DroneBuild build : DroneBuild.PRESETS) {
            CameraView view = BUILDS.cameraView(Conversions.spec(build)).orElseThrow();
            FpvRig rig = FpvRig.of(build);
            assertEquals(BuildAnalysis.of(build).propsInViewPercent(), view.propsInViewPercent());
            assertEquals(BUILDS.analyze(Conversions.spec(build)).orElseThrow().propsInViewPercent(), view.propsInViewPercent());
            assertEquals(rig.tiltDeg(), view.uptiltDeg());
            assertEquals(FovMath.cameraHorizontalDegrees(build.video()), view.horizontalFovDeg());
            assertEquals(build.video().definition().cameraFovDeg().orElseThrow(), view.horizontalFovDeg());
            CameraMount.Range range = CameraMount.range(build.frame());
            assertEquals(range.minDeg(), view.uptiltMinDeg());
            assertEquals(range.maxDeg(), view.uptiltMaxDeg());
            assertEquals(rig.tiltDeg(), view.defaultUptiltDeg());
            assertTrue(view.uptiltMinDeg() <= view.uptiltDeg() && view.uptiltDeg() <= view.uptiltMaxDeg());
            assertEquals(16.0 / 9.0, view.aspect(), 1.0E-12);
            assertEquals(FpvRig.SLOTS, view.props().size());
            for (int slot = 0; slot < FpvRig.SLOTS; slot++) {
                CameraView.PropDisc disc = view.props().get(slot);
                assertEquals(build.prop().diameterMm / 2.0, disc.radiusMm(), 1.0E-9);
                assertEquals(rig.discCenterMm(slot)[1], disc.centerMm().y(), 1.0E-9);
            }
            assertEquals(rig.lensMm()[2], view.lensMm().z(), 1.0E-9);
        }
        assertEquals(Optional.empty(), BUILDS.cameraView(Conversions.spec(DroneBuild.RACER).withPart(PartKind.VIDEO, UNKNOWN)));
    }

    @Test
    void cameraViewAtAnUptiltStaysInsideTheCameraMount() {
        BuildSpec spec = Conversions.spec(DroneBuild.FREESTYLE);
        CameraView standard = BUILDS.cameraView(spec).orElseThrow();
        CameraView steep = BUILDS.cameraView(spec, 45.0).orElseThrow();
        CameraView beyond = BUILDS.cameraView(spec, 79.0).orElseThrow();
        assertEquals(45.0, steep.uptiltDeg(), 1.0E-6);
        assertEquals(standard.uptiltMaxDeg(), beyond.uptiltDeg(), 1.0E-6);
        assertEquals(standard.defaultUptiltDeg(), steep.defaultUptiltDeg());
        FpvRig rig = FpvRig.of(DroneBuild.FREESTYLE);
        assertEquals(CameraOcclusion.percent(rig, 45.0, rig.horizontalFovDeg(), FpvRig.ASPECT, CameraOcclusion.ROWS), steep.propsInViewPercent(), 1.0E-9);
        assertTrue(steep.propsInViewPercent() > standard.propsInViewPercent(), "5″: mehr Uptilt zeigt mehr Props");
        assertEquals(standard, BUILDS.cameraView(spec, standard.uptiltDeg()).orElseThrow());
        CameraView.UptiltRange range = BUILDS.uptiltRange(DroneBuild.FREESTYLE.frame().id()).orElseThrow();
        assertEquals(standard.uptiltRange(), range);
        assertEquals(new CameraView.UptiltRange(15.0, 70.0, 38.0), BUILDS.uptiltRange(DroneBuild.RACER.frame().id()).orElseThrow());
        assertEquals(Optional.empty(), BUILDS.uptiltRange(UNKNOWN));
    }

    @Test
    void defaultTuneIsTheCompleteFlightControllerTable() {
        TuneSpec tune = BUILDS.defaultTune(Conversions.spec(DroneBuild.FREESTYLE));
        for (String key : TuneMapping.keys()) {
            assertTrue(tune.values().containsKey(key), key);
        }
        for (String key : LedSettings.KEYS) {
            assertTrue(tune.values().containsKey(key), key);
        }
        assertEquals((float) DroneBuild.FREESTYLE.frame().definition().geometry().cameraTiltDeg(), tune.values().get(TuneMapping.CAMERA_UPTILT));
        assertEquals(28.0F, tune.values().get(TuneMapping.CAMERA_UPTILT));
        assertFalse(tune.only(TuneGroup.FLIGHT).values().isEmpty());
        assertTrue(tune.only(EnumSet.of(TuneGroup.VTX)).values().isEmpty());
    }

    @Test
    void builtinPresetsKeepTheirIdsAndOrder() {
        List<DronePreset> presets = BUILDS.presets();
        assertEquals(List.of("whoop", "cinewhoop", "freestyle", "racer", "long_range"),
                presets.subList(0, 5).stream().map(p -> p.id().getPath()).toList());
        for (int i = 0; i < DroneBuild.PRESETS.size(); i++) {
            assertEquals(Conversions.spec(DroneBuild.PRESETS.get(i)), presets.get(i).data().build());
            assertEquals("propwash.content.preset." + presets.get(i).id().getPath(), presets.get(i).translationKey());
        }
        assertThrows(IllegalArgumentException.class,
                () -> BUILDS.registerPreset(DronePreset.of(Identifier.fromNamespaceAndPath("propwash", "fremd"), presets.getFirst().data())));
    }
}
