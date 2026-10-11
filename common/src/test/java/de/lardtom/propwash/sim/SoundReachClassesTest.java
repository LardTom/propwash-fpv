package de.lardtom.propwash.sim;

import de.lardtom.propwash.api.part.FrameRole;
import de.lardtom.propwash.audio.MotorTimbre;
import de.lardtom.propwash.audio.SoundReach;
import de.lardtom.propwash.build.DroneBuild;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class SoundReachClassesTest {
    private static final double SIZE_STEP = 1.3;

    private static double cruise(DroneBuild build) {
        MotorTimbre timbre = MotorTimbre.of(build);
        double output = Math.min(1.0, timbre.hoverOutput() + 0.15);
        return SoundReach.of(build).rangeBlocks(SoundReach.amplitude(timbre.rpmForOutput(output), timbre.maxRpm(), output));
    }

    @Test
    void ownerWhoopsFadeLikeTheBuiltinWhoop() {
        for (CalibrationHarness.Entry entry : CalibrationHarness.owner()) {
            double hover = SoundReach.of(entry.build()).hoverRange();
            assertTrue(hover >= 12.0 && hover <= 25.0, entry.name() + " schwebend " + hover);
            assertTrue(cruise(entry.build()) <= 25.0, entry.name() + " im Flug " + cruise(entry.build()));
        }
    }

    @Test
    @Tag(CalibrationHarness.ADDON_TAG)
    void addonClassesKeepTheirOrder() {
        assumeTrue(CalibrationHarness.addonRoot().isPresent(), CalibrationHarness.ADDON_HINT);
        List<CalibrationHarness.Entry> all = new ArrayList<>(CalibrationHarness.builtins());
        all.addAll(CalibrationHarness.addons());
        double loudestFive = 0.0;
        double quietestX = Double.MAX_VALUE;
        for (CalibrationHarness.Entry a : all) {
            SoundReach first = SoundReach.of(a.build());
            assertTrue(Math.abs(first.levelDb() - SoundReach.sizeLevelDb(first.propDiameterMm())) <= SoundReach.ROTOR_BAND_DB + 1.0e-9,
                    a.name() + " " + first.levelDb() + " dB(A) neben der Größenkurve");
            for (CalibrationHarness.Entry b : all) {
                SoundReach second = SoundReach.of(b.build());
                if (second.propDiameterMm() >= SIZE_STEP * first.propDiameterMm()) {
                    assertTrue(second.hoverRange() > first.hoverRange() || second.hoverRange() >= SoundReach.MAX_RANGE_BLOCKS,
                            b.name() + " (" + second.hoverRange() + ") muss weiter tragen als " + a.name() + " (" + first.hoverRange() + ")");
                }
            }
        }
        for (CalibrationHarness.Entry entry : all) {
            DroneBuild build = entry.build();
            AirframeSpec spec = AirframeSpec.of(build);
            SoundReach reach = SoundReach.of(build);
            double hover = reach.hoverRange();
            double millimetres = spec.propDiameter * 1000.0;
            if (spec.role == FrameRole.WHOOP && millimetres <= 41.0) {
                assertTrue(hover >= 15.0 && cruise(build) <= 25.0, entry.name() + " schwebend " + hover + ", im Flug " + cruise(build));
            }
            if (spec.role == FrameRole.CINEWHOOP) {
                assertTrue(hover >= 30.0 && hover <= 65.0, entry.name() + " schwebend " + hover);
            }
            if ((spec.role == FrameRole.FREESTYLE || spec.role == FrameRole.RACE) && millimetres >= 120.0 && millimetres <= 135.0) {
                assertTrue(hover >= 40.0 && hover <= 85.0, entry.name() + " schwebend " + hover);
                loudestFive = Math.max(loudestFive, hover);
            }
            if (spec.role == FrameRole.X_CLASS) {
                quietestX = Math.min(quietestX, hover);
                assertTrue(reach.levelDb() >= 84.0, entry.name() + " " + reach.levelDb() + " dB(A)");
            }
        }
        assertTrue(quietestX > loudestFive && quietestX >= 100.0, "X-Class " + quietestX + " vs 5\" " + loudestFive);
    }
}
