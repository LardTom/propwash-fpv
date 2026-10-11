package de.lardtom.propwash.ui;

import de.lardtom.propwash.build.DroneBuild;
import de.lardtom.propwash.sim.Axis;
import de.lardtom.propwash.sim.RateCurve;
import de.lardtom.propwash.sim.RatesType;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RateGraphTest {
    private final Map<String, Float> defaults = TuneDefaults.compute(DroneBuild.FREESTYLE, Map.of());

    @Test
    void defaultActualRatesReachTheirMaximum() {
        RateCurve roll = RateGraph.axisRates(Map.of(), this.defaults, Axis.ROLL);
        assertEquals(RatesType.ACTUAL, RateGraph.type(Map.of(), this.defaults));
        assertEquals(670.0, RateGraph.maxRate(RatesType.ACTUAL, roll), 1e-6);
        assertEquals(0.0, RateGraph.rate(RatesType.ACTUAL, roll, 0.0), 1e-9);
        assertEquals(-RateGraph.rate(RatesType.ACTUAL, roll, 0.4), RateGraph.rate(RatesType.ACTUAL, roll, -0.4), 1e-9);
    }

    @Test
    void betaflightRatesUseRcAndSuperRate() {
        Map<String, Float> tune = RateTemplate.CLASSIC.apply(Map.of(), this.defaults);
        assertEquals(RatesType.BETAFLIGHT, RateGraph.type(tune, this.defaults));
        RateCurve pitch = RateGraph.axisRates(tune, this.defaults, Axis.PITCH);
        assertEquals(200.0 / 0.3, RateGraph.maxRate(RatesType.BETAFLIGHT, pitch), 1e-3);
        assertTrue(RateTemplate.CLASSIC.matches(tune, this.defaults));
        assertFalse(RateTemplate.RACING.matches(tune, this.defaults));
    }

    @Test
    void curveSamplesAreMonotonicForEveryPreset() {
        for (RateTemplate preset : RateTemplate.values()) {
            Map<String, Float> tune = preset.apply(Map.of(), this.defaults);
            assertTrue(preset.matches(tune, this.defaults), preset.name());
            RatesType type = RateGraph.type(tune, this.defaults);
            for (Axis axis : Axis.values()) {
                double[] s = RateGraph.samples(type, RateGraph.axisRates(tune, this.defaults, axis), 41);
                assertEquals(41, s.length);
                for (int i = 1; i < s.length; i++) {
                    assertTrue(s[i] >= s[i - 1] - 1e-9, preset + " " + axis + " " + i);
                }
                assertEquals(0.0, s[20], 1e-9);
            }
        }
    }

    @Test
    void standardPresetStoresOnlyForeignKeys() {
        Map<String, Float> tune = RateTemplate.STANDARD.apply(Map.of(TuneKeys.RC_DEADBAND, 0.02F), this.defaults);
        assertEquals(Map.of(TuneKeys.RC_DEADBAND, 0.02F), tune);
    }

    @Test
    void niceScaleRoundsUp() {
        assertEquals(800.0, RateGraph.niceScale(670));
        assertEquals(100.0, RateGraph.niceScale(0));
        assertEquals(100.0, RateGraph.niceScale(Double.NaN));
        assertEquals(2000.0, RateGraph.niceScale(1998));
        assertEquals(100.0, RateGraph.gridStep(400));
        assertEquals(250.0, RateGraph.gridStep(800));
    }

    @Test
    void pixelMappingCoversTheGraph() {
        assertEquals(10, RateGraph.xPixel(-1, 10, 101));
        assertEquals(110, RateGraph.xPixel(1, 10, 101));
        assertEquals(60, RateGraph.xPixel(0, 10, 101));
        assertEquals(110, RateGraph.xPixel(7, 10, 101));
        assertEquals(0.0, RateGraph.stickAt(60, 10, 101), 1e-9);
        assertEquals(20, RateGraph.yPixel(800, 800, 20, 81));
        assertEquals(100, RateGraph.yPixel(-800, 800, 20, 81));
        assertEquals(60, RateGraph.yPixel(0, 800, 20, 81));
        assertEquals(20, RateGraph.yPixel(5000, 800, 20, 81));
        assertEquals(60, RateGraph.yPixel(Double.NaN, 800, 20, 81));
    }
}
