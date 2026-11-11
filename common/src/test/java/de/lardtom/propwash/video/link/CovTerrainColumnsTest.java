package de.lardtom.propwash.video.link;

import org.junit.jupiter.api.Test;

import java.util.Locale;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CovTerrainColumnsTest {
    private static double scan(ObstructionTracer.World world, double[] p) {
        ObstructionScan scan = new ObstructionScan();
        scan.start(p[0], p[1], p[2], p[3], p[4], p[5]);
        return scan.evaluate(world);
    }

    @Test
    void pathOverTheRememberedRidgeIsClear() {
        ScenarioWorld world = ScenarioWorld.flat().ridge(160, 24, 30, -40, 40);
        TerrainColumnsTest.RememberedWorld remembered = TerrainColumnsTest.RememberedWorld.of(world, -1, 14, -4, 3);
        double[] behind = {0.5, 6.5, 0.5, 220.5, 6.5, 0.5};
        double loadedBehind = scan(world, behind);
        double memoryBehind = scan(remembered, behind);
        String behindLabel = String.format(Locale.ROOT, "hinter dem Rücken: geladen %.2f dB, gemerkt %.2f dB", loadedBehind, memoryBehind);
        assertTrue(loadedBehind > 20.0, behindLabel);
        assertEquals(loadedBehind, memoryBehind, 3.0, behindLabel);
        for (double height : new double[]{40.5, 50.5, 70.5}) {
            double[] over = {0.5, height, 0.5, 220.5, height, 0.5};
            double[] climbing = {0.5, 6.5, 0.5, 220.5, height + 10.0, 0.5};
            for (double[] p : new double[][]{over, climbing}) {
                double loaded = scan(world, p);
                double memory = scan(remembered, p);
                String label = String.format(Locale.ROOT, "%.1f/%.1f → %.1f/%.1f über die Kuppe (24 m hoch): geladen %.2f dB, gemerkt %.2f dB",
                        p[0], p[1], p[3], p[4], loaded, memory);
                if (p == over || height >= 50.0) {
                    assertTrue(loaded < 1.0, label);
                    assertTrue(memory < 1.0, label);
                }
                assertEquals(loaded, memory, 1.0, label);
            }
        }
        double crest = scan(remembered, new double[]{0.5, 26.5, 0.5, 220.5, 26.5, 0.5});
        assertTrue(crest < memoryBehind, String.format(Locale.ROOT, "knapp über der Kuppe weniger Verlust (%.2f dB) als dahinter (%.2f dB)", crest,
                memoryBehind));
    }
}
