package de.lardtom.propwash.calibration;

import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class BatteryFitTest {
    static final double R0 = 0.004;
    static final double R1 = 0.002;
    static final double TAU1 = 0.5;
    static final double R2 = 0.003;
    static final double TAU2 = 8.0;

    public static double[] blocks(int samples, double low, double high, long seed) {
        double[] amps = new double[samples];
        long state = seed;
        int k = 0;
        while (k < samples) {
            state = state * 6364136223846793005L + 1442695040888963407L;
            double level = low + (high - low) * ((state >>> 33) % 1000) / 999.0;
            state = state * 6364136223846793005L + 1442695040888963407L;
            int length = 25 + (int) ((state >>> 33) % 175);
            for (int n = 0; n < length && k < samples; n++) {
                amps[k++] = level;
            }
        }
        return amps;
    }

    static BatteryFit.Series pack(double[] amps, double capacityMah) {
        int n = amps.length;
        double[] t = new double[n];
        double[] v = new double[n];
        double fast = R1 * amps[0];
        double slow = R2 * amps[0];
        double used = 0.0;
        double h = BatteryFit.STEP_SECONDS;
        for (int k = 0; k < n; k++) {
            t[k] = k * h;
            used += amps[k] * h / 3.6;
            fast += (R1 * amps[k] - fast) * (1.0 - Math.exp(-h / TAU1));
            slow += (R2 * amps[k] - slow) * (1.0 - Math.exp(-h / TAU2));
            v[k] = 4.1 - 0.5 * used / capacityMah - R0 * amps[k] - fast - slow;
        }
        return new BatteryFit.Series(t, v, amps);
    }

    @Test
    void fitRecoversInstantFastAndSlowResistance() {
        BatteryFit.Series s = pack(blocks(9000, 3.0, 60.0, 11L), 1300.0);
        Optional<BatteryFit.Result> fit = BatteryFit.fit(s);
        assertTrue(fit.isPresent());
        BatteryFit.Result r = fit.get();
        assertEquals(R0, r.r0(), 0.05 * R0);
        assertEquals(R1, r.r1(), 0.15 * R1);
        assertEquals(TAU1, r.tau1(), 1e-9);
        assertEquals(R2, r.r2(), 0.15 * R2);
        assertEquals(TAU2, r.tau2(), 1e-9);
        assertEquals(R0 + R1 + R2, r.sum(), 0.05 * (R0 + R1 + R2));
        assertTrue(r.rmsVolts() < 0.001, "Rest " + r.rmsVolts());
        assertEquals(4.1, r.ocvStart(), 0.01);
        assertEquals(s.mah(0, s.size()), r.mah(), 1.0);
        assertEquals(4.1 - 0.5 * r.mah() / 1300.0, r.ocvEnd(), 0.01);
    }

    @Test
    void shortOrFlatCurrentIsNotUsable() {
        assertFalse(BatteryFit.usableCurrent(pack(blocks(200, 3.0, 60.0, 3L), 1300.0)));
        assertFalse(BatteryFit.usableCurrent(pack(blocks(4000, 12.0, 13.5, 3L), 300.0)));
        assertFalse(BatteryFit.usableCurrent(pack(blocks(4000, 0.1, 1.0, 3L), 300.0)));
        assertTrue(BatteryFit.usableCurrent(pack(blocks(4000, 3.0, 60.0, 3L), 1300.0)));
        assertTrue(BatteryFit.fit(pack(blocks(200, 3.0, 60.0, 3L), 1300.0)).isEmpty());
    }

    @Test
    void stepComparesLowAndHighCurrent() {
        int n = 2000;
        double[] t = new double[n];
        double[] v = new double[n];
        double[] a = new double[n];
        for (int k = 0; k < n; k++) {
            t[k] = k * BatteryFit.STEP_SECONDS;
            a[k] = (k / 100) % 2 == 0 ? 2.0 : 30.0;
            v[k] = 3.9 - 0.005 * a[k];
        }
        BatteryFit.Series s = new BatteryFit.Series(t, v, a);
        BatteryFit.Step step = BatteryFit.step(s, 0, n, 30.0).orElseThrow();
        assertEquals(2.0, step.lowAmps(), 1e-9);
        assertEquals(30.0, step.highAmps(), 1e-9);
        assertEquals(0.14, step.sag(), 1e-9);
        assertEquals(0.005, step.ohms(), 1e-9);
        assertTrue(BatteryFit.step(s, 0, 100, 30.0).isEmpty(), "ohne Abschnitt mit viel Strom gibt es keinen Einbruch");
    }

    @Test
    void scalingTheCurrentKeepsVoltsAndScalesCharge() {
        BatteryFit.Series s = pack(blocks(3000, 3.0, 60.0, 5L), 1300.0);
        BatteryFit.Series half = s.scaled(0.5);
        assertEquals(0.5 * s.mah(0, s.size()), half.mah(0, half.size()), 1e-9);
        assertEquals(s.cellVolts()[1234], half.cellVolts()[1234]);
        assertEquals(BatteryFit.percentile(s.amps(), 0.99) * 0.5, BatteryFit.percentile(half.amps(), 0.99), 1e-9);
        BatteryFit.Result a = BatteryFit.fit(s).orElseThrow();
        BatteryFit.Result b = BatteryFit.fit(half).orElseThrow();
        assertEquals(2.0 * a.r0(), b.r0(), 0.02 * b.r0());
    }
}
