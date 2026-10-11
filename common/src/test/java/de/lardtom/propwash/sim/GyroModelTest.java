package de.lardtom.propwash.sim;

import de.lardtom.propwash.build.DroneBuild;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GyroModelTest {
    static final double H = DroneSim.H;
    static final double HOVER = 1000.0;

    static double[] rms(double[] omega, int decimation, int samples) {
        GyroModel gyro = new GyroModel();
        gyro.configure(H);
        gyro.reset();
        SimRandom rng = new SimRandom(1L);
        long before = rng.state();
        double[] sq = new double[3];
        for (int n = 0; n < samples; n++) {
            gyro.sample(rng, 0.0, 0.0, 0.0, 0.0, 1.0, omega, HOVER, 0.0, decimation);
            for (int a = 0; a < 3; a++) {
                sq[a] += gyro.raw[a] * gyro.raw[a];
            }
        }
        assertEquals(before, rng.state(), "Vibration darf keine Zufallszahlen ziehen");
        return new double[]{Math.sqrt(sq[0] / samples), Math.sqrt(sq[1] / samples), Math.sqrt(sq[2] / samples)};
    }

    @Test
    void vibrationKeepsItsLevelAtDifferentRotorSpeeds() {
        double[] r = rms(new double[]{1000.0, 1030.0, 970.0, 1015.0}, 1, 200_000);
        assertEquals(1.0, r[FcAxes.ROLL], 0.1);
        assertEquals(1.0, r[FcAxes.PITCH], 0.1);
        assertEquals(0.5, r[FcAxes.YAW], 0.05);
    }

    @Test
    void vibrationGrowsWithRotorSpeedSquared() {
        double[] slow = rms(new double[]{1000.0, 1030.0, 970.0, 1015.0}, 1, 200_000);
        double[] fast = rms(new double[]{1500.0, 1545.0, 1455.0, 1522.5}, 1, 200_000);
        double expected = 2.25 * GyroModel.antiAliasGain(1500.0, H) / GyroModel.antiAliasGain(1000.0, H);
        assertEquals(expected, fast[FcAxes.ROLL] / slow[FcAxes.ROLL], 0.15 * expected);
    }

    @Test
    void rotorsAboveNyquistDoNotFoldIntoTheControlBand() {
        double nyquist = Math.PI / H;
        double[] r = rms(new double[]{1.05 * nyquist, 1.3 * nyquist, 1.6 * nyquist, 1.95 * nyquist}, 1, 10_000);
        assertEquals(0.0, r[0], 0.0);
        assertEquals(0.0, r[1], 0.0);
        assertEquals(0.0, r[2], 0.0);
        assertTrue(GyroModel.antiAliasGain(0.9 * nyquist, H) < 0.05);
    }

    @Test
    void slowerPidLoopAveragesTheRotorToneAwayAtItsOwnRate() {
        double fcNyquist2 = 2.0 * Math.PI / (2.0 * H);
        assertEquals(0.0, GyroModel.decimationGain(fcNyquist2, H, 2), 1.0E-9);
        assertEquals(0.0, GyroModel.decimationGain(2.0 * Math.PI / (4.0 * H), H, 4), 1.0E-9);
        assertEquals(1.0, GyroModel.decimationGain(10.0, H, 1), 0.0);
        assertTrue(GyroModel.decimationGain(200.0, H, 4) > 0.99);
    }

    @Test
    void rotorVibrationDoesNotShakeTheCameraAtFullThrottle() {
        double[] rates = new double[2];
        for (int k = 0; k < 2; k++) {
            SimTuning t = Tests.quietTuning();
            t.gyroVibrationDps = k == 0 ? 0.0 : 1.0;
            FlatWorld w = Tests.hovering(DroneBuild.FREESTYLE, t, 100.0, StabMode.ACRO, 1.0);
            w.input.throttle = 1.0;
            double sq = 0.0;
            int n = 0;
            for (int f = 0; f < 288; f++) {
                w.frame(1.0 / 144.0);
                if (f >= 72) {
                    double wx = w.sim.body.wbx * SimMath.DEG;
                    double wz = w.sim.body.wbz * SimMath.DEG;
                    sq += wx * wx + wz * wz;
                    n++;
                }
            }
            rates[k] = Math.sqrt(sq / n);
        }
        assertTrue(rates[1] - rates[0] < 1.0, "Vibration schüttelt den Rahmen: " + rates[1] + " °/s gegen " + rates[0] + " °/s ohne");
    }
}
