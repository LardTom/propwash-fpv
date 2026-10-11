package de.lardtom.propwash.sim;

import de.lardtom.propwash.api.part.AirframeGeometry;
import de.lardtom.propwash.build.DroneBuild;
import de.lardtom.propwash.build.FrameType;
import de.lardtom.propwash.build.PartWear;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RaceBatteryTest {
    @Test
    void onlyTheRaceFrameMovesItsBatteryUnderTheFrame() {
        for (FrameType frame : FrameType.values()) {
            if (!frame.builtin()) {
                continue;
            }
            AirframeGeometry geometry = frame.definition().geometry();
            boolean race = frame == FrameType.RACE_5;
            boolean usual = AirframeGeometry.defaultBatteryOnTop(frame.wheelbaseMm);
            assertEquals(!race && usual, geometry.batteryOnTop(), frame.key());
        }
        assertEquals(-2.8, FrameType.RACE_5.definition().geometry().batteryPlateMm(), 1e-9);
    }

    @Test
    void batteryUnderTheFrameLowersTheCenterOfGravityBelowTheRotors() {
        AirframeSpec race = AirframeSpec.of(DroneBuild.RACER);
        AirframeSpec freestyle = AirframeSpec.of(DroneBuild.FREESTYLE);
        assertTrue(race.comHeight < 0.0, "Schwerpunkt " + race.comHeight);
        assertTrue(race.rotorPlaneOffset > 0.015, "Props über dem Schwerpunkt: " + race.rotorPlaneOffset);
        assertTrue(freestyle.rotorPlaneOffset < 0.0, "Freestyle: Akku oben, Schwerpunkt über den Props");
        double plate = FrameType.RACE_5.definition().geometry().batteryPlateMm() / 1000.0;
        double lowest = race.comHeight + race.hitboxFootOffset;
        assertTrue(lowest < plate - 0.025, "der Akku ist der tiefste Punkt: " + lowest);
    }

    @Test
    void landingOnTheBellyHitsTheRaceBatteryAndATopHitDoesNot() {
        double hard = FrameType.RACE_5.impactTolerance * 0.9;
        float belly = wearAfter(DroneBuild.RACER, hard, true);
        float roof = wearAfter(DroneBuild.RACER, hard, false);
        assertTrue(belly < roof, "Akku nach Bauchlandung " + belly + ", nach Treffer von oben " + roof);
        float freestyleRoof = wearAfter(DroneBuild.FREESTYLE, hard, false);
        float freestyleBelly = wearAfter(DroneBuild.FREESTYLE, hard, true);
        assertTrue(freestyleRoof < freestyleBelly, "Freestyle: Akku oben fängt Treffer von oben");
    }

    private static float wearAfter(DroneBuild build, double speed, boolean fromBelow) {
        float sum = 0.0F;
        for (long seed = 0; seed < 40; seed++) {
            ImpactSample impact = new ImpactSample((float) speed, 0.0F, 0.0F, fromBelow ? 1.0F : -1.0F, 0.0F,
                    fromBelow ? 1 << ImpactSample.HULL_BOTTOM : 0, 0, 0.0F);
            sum += CrashDamage.apply(PartWear.PRISTINE, build, impact, 1.0, seed).battery();
        }
        return sum / 40.0F;
    }

    @Test
    void geometryRecordKeepsTheBatterySideForTheFlightModel() {
        assertFalse(FrameGeometry.of(FrameType.RACE_5).batteryOnTop());
        assertTrue(FrameGeometry.of(FrameType.FREESTYLE_5).batteryOnTop());
    }
}
