package de.lardtom.propwash.sim;

import de.lardtom.propwash.build.DroneBuild;
import de.lardtom.propwash.calibration.BatteryFit;
import de.lardtom.propwash.calibration.BatteryFitTest;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RealLogBatteryTest {
    static BatteryFit.Series flight(AirframeSpec p, double startSoc, double[] socOut) {
        double[] amps = BatteryFitTest.blocks(3000, 2.0, 40.0, 21L);
        double[] t = new double[amps.length];
        for (int k = 0; k < t.length; k++) {
            t[k] = k * BatteryFit.STEP_SECONDS;
        }
        BatteryFit.Series s = new BatteryFit.Series(t, new double[amps.length], amps);
        return s.withVolts(RealLogCalibration.batteryTrace(p, s, startSoc, socOut));
    }

    @Test
    void startChargeIsFoundFromTheVoltage() {
        AirframeSpec p = AirframeSpec.of(DroneBuild.FREESTYLE);
        for (double soc : new double[]{0.95, 0.7, 0.45}) {
            assertEquals(soc, RealLogCalibration.batteryStartSoc(p, flight(p, soc, null)), 0.005);
        }
    }

    @Test
    void fitOfTheModelFindsItsOwnBranches() {
        AirframeSpec p = AirframeSpec.of(DroneBuild.FREESTYLE);
        double[] soc = new double[3000];
        BatteryFit.Series s = flight(p, 0.95, soc);
        assertTrue(soc[soc.length - 1] > ModelConstants.LOW_SOC_KNEE, "Testflug gerät in den Anstieg bei leerem Akku: " + soc[soc.length - 1]);
        BatteryFit.Result r = BatteryFit.fit(s).orElseThrow();
        double pack = p.packResistance / p.cells;
        assertEquals((1.0 - ModelConstants.SAG_FAST_FRACTION) * pack, r.r0(), 0.1 * pack);
        assertEquals(ModelConstants.SAG_FAST_FRACTION * pack, r.r1(), 0.1 * pack);
        assertEquals(ModelConstants.SAG_FAST_TAU, r.tau1(), 1e-9);
        assertTrue(r.r2() > 0.3 * pack && r.r2() < pack, "langsamer Anteil " + r.r2() / pack + " × Pack (τ " + r.tau2() + " s)");
        assertTrue(r.sum() > 1.3 * pack && r.sum() < 1.9 * pack, "Summe " + r.sum() / pack + " × Pack");
    }

    @Test
    void ocvLookupInvertsTheCellCurve() {
        AirframeSpec p = AirframeSpec.of(DroneBuild.FREESTYLE);
        for (double soc : new double[]{0.05, 0.2, 0.5, 0.8, 0.99}) {
            assertEquals(soc, RealLogCalibration.socForOcv(p, CellCurve.ocv(p.chemistry, soc)), 1e-6);
        }
    }
}
