package de.lardtom.propwash.interact;

import de.lardtom.propwash.build.DroneBuild;
import de.lardtom.propwash.sim.AirframeSpec;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BreakMathTest {
    private static final double GLASS = 0.3;
    private static final double PANE_DEPTH = 2.0 / 16.0;
    private static final double PANE = BreakMath.threshold(GLASS, PANE_DEPTH);
    private static final double GLASS_BLOCK = BreakMath.threshold(GLASS, 1.0);
    private static final double POT = BreakMath.threshold(0.0, 14.0 / 16.0);
    private static final double WALKING = 4.317;
    private static final double FULL_SPEED_5 = 140.0 / 3.6;

    private static double mass(DroneBuild build) {
        return AirframeSpec.of(build).massKg;
    }

    @Test
    void thresholdsFollowHardnessAndThickness() {
        assertEquals(36.0, PANE, 1.0E-9);
        assertEquals(120.0, GLASS_BLOCK, 1.0E-9);
        assertEquals(BreakMath.MIN_ENERGY, POT, 1.0E-9);
        assertTrue(BreakMath.threshold(GLASS, 0.5) > PANE && BreakMath.threshold(GLASS, 0.5) < GLASS_BLOCK);
        assertEquals(BreakMath.threshold(GLASS, 0.0), BreakMath.threshold(GLASS, BreakMath.MIN_DEPTH), 1.0E-9);
        assertEquals(GLASS_BLOCK, BreakMath.threshold(GLASS, 7.0), 1.0E-9);
        assertEquals(GLASS_BLOCK, BreakMath.threshold(GLASS, Double.NaN), 1.0E-9);
    }

    @Test
    void strongBlocksNeverBreak() {
        assertTrue(BreakMath.fragile(BreakMath.MAX_HARDNESS));
        assertFalse(BreakMath.fragile(1.5), "Stein");
        assertFalse(BreakMath.fragile(50.0), "Obsidian");
        assertFalse(BreakMath.fragile(-1.0), "Grundgestein");
        assertFalse(BreakMath.fragile(Double.NaN));
        assertEquals(Double.POSITIVE_INFINITY, BreakMath.threshold(1.5, 0.125));
        assertFalse(BreakMath.of(100.0, 150.0, BreakMath.threshold(1.5, 0.125)).breaks());
        assertFalse(BreakMath.of(100.0, 150.0, Double.NaN).breaks());
    }

    @Test
    void whoopBouncesOffEvenAtTopSpeed() {
        double m = mass(DroneBuild.WHOOP);
        assertFalse(BreakMath.of(m, WALKING, PANE).breaks(), "Whoop im Schritttempo");
        assertFalse(BreakMath.of(m, 15.0, PANE).breaks(), "Whoop schneller als seine Höchstgeschwindigkeit");
        assertFalse(BreakMath.of(m, 15.0, POT).breaks(), "Whoop gegen einen Topf");
    }

    @Test
    void freestyleSmashesThroughAtFullSpeed() {
        double m = mass(DroneBuild.FREESTYLE);
        assertFalse(BreakMath.of(m, WALKING, PANE).breaks(), "5-Zoll im Schritttempo");
        assertFalse(BreakMath.of(m, 8.0, PANE).breaks(), "5-Zoll mit 29 km/h");
        BreakMath.Impact pane = BreakMath.of(m, FULL_SPEED_5, PANE);
        BreakMath.Impact block = BreakMath.of(m, FULL_SPEED_5, GLASS_BLOCK);
        assertTrue(pane.breaks() && block.breaks());
        assertTrue(pane.speedLost() < 2.0, "Scheibe bremst bei Vollgas kaum: " + pane.speedLost());
        assertTrue(block.speedLost() > pane.speedLost(), "ein Glasblock bremst stärker als eine Scheibe");
        assertTrue(block.speedLost() < 8.0, "bei Vollgas durch den Glasblock noch kein Absturz: " + block.speedLost());
        double paneKmh = BreakMath.breakSpeed(m, PANE) * 3.6;
        assertTrue(paneKmh > 38.0 && paneKmh < 46.0, "Scheibe ab etwa 42 km/h: " + paneKmh);
        assertFalse(BreakMath.of(m, 15.0, GLASS_BLOCK).breaks());
        assertTrue(BreakMath.of(m, 6.0, POT).breaks(), "Topf zerspringt schon bei 22 km/h");
    }

    @Test
    void energyLossKeepsTheTangentAndTheRest() {
        double m = mass(DroneBuild.FREESTYLE);
        BreakMath.Impact impact = BreakMath.of(m, 25.0, PANE);
        assertTrue(impact.breaks());
        assertEquals(0.5 * m * 625.0, impact.energy(), 1.0E-9);
        assertEquals(PANE, impact.absorbed(), 1.0E-9);
        assertEquals(Math.sqrt(625.0 - 2.0 * PANE / m), impact.speedAfter(), 1.0E-9);
        assertEquals(25.0 - impact.speedAfter(), impact.speedLost(), 1.0E-9);
        BreakMath.Impact barely = BreakMath.of(m, BreakMath.breakSpeed(m, PANE) * 1.001, PANE);
        assertTrue(barely.breaks());
        assertTrue(barely.speedAfter() < 1.0, "knapp durch: fast alles Tempo weg (" + barely.speedAfter() + ")");
        assertTrue(BreakMath.of(m, 35.0, PANE).speedLost() < BreakMath.of(m, 20.0, PANE).speedLost(), "schneller verliert weniger");
    }

    @Test
    void heavierDronesAndCargoBreakEarlier() {
        double m = mass(DroneBuild.FREESTYLE);
        assertTrue(BreakMath.breakSpeed(mass(DroneBuild.LONG_RANGE), PANE) < BreakMath.breakSpeed(m, PANE));
        assertTrue(BreakMath.breakSpeed(m + 0.4, PANE) < BreakMath.breakSpeed(m, PANE));
        assertTrue(BreakMath.breakSpeed(mass(DroneBuild.CINEWHOOP), PANE) > BreakMath.breakSpeed(m, PANE));
    }

    @Test
    void slowTapsNeverBreakAndTheServerAllowsRounding() {
        assertFalse(BreakMath.of(100.0, BreakMath.MIN_SPEED - 0.01, POT).breaks(), "schwer, aber zu langsam");
        double m = mass(DroneBuild.FREESTYLE);
        double v = BreakMath.breakSpeed(m, PANE) * 0.97;
        assertFalse(BreakMath.of(m, v, PANE).breaks());
        assertTrue(BreakMath.of(m, v, PANE, BreakMath.SERVER_SLACK).breaks());
        assertFalse(BreakMath.of(m, -30.0, PANE).breaks(), "Wegfliegen bricht nichts");
        assertFalse(BreakMath.of(Double.NaN, 30.0, PANE).breaks());
        assertFalse(BreakMath.of(m, Double.POSITIVE_INFINITY, PANE).breaks());
        assertEquals(BreakMath.MIN_SPEED, BreakMath.breakSpeed(100.0, POT), 1.0E-9);
    }

    @Test
    void shardMassFollowsTheThreshold() {
        assertEquals(10.0, BreakMath.shardMass(BreakMath.threshold(0.3, 0.125)), 1.0E-9, "Scheibe: 4 mm Fensterglas, 10 kg/m²");
        assertEquals(33.333, BreakMath.shardMass(BreakMath.threshold(0.3, 1.0)), 1.0E-3, "Glasblock: gut dreimal so viel");
        assertEquals(1.389, BreakMath.shardMass(BreakMath.threshold(0.0, 0.875)), 1.0E-3, "verzierter Topf: dünner Ton");
        assertEquals(0.0, BreakMath.shardMass(Double.POSITIVE_INFINITY), 0.0);
        assertEquals(0.0, BreakMath.shardMass(-1.0), 0.0);
    }
}
