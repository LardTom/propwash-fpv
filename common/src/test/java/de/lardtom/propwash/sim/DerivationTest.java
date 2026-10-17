package de.lardtom.propwash.sim;

import de.lardtom.propwash.build.BatteryType;
import de.lardtom.propwash.build.DroneBuild;
import de.lardtom.propwash.build.FrameType;
import de.lardtom.propwash.build.MotorType;
import de.lardtom.propwash.build.PropType;
import de.lardtom.propwash.build.StackType;
import de.lardtom.propwash.build.VideoType;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.lang.management.ManagementFactory;
import java.lang.management.ThreadMXBean;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DerivationTest {
    static final DroneBuild[] PRESETS = {DroneBuild.WHOOP, DroneBuild.CINEWHOOP, DroneBuild.FREESTYLE, DroneBuild.RACER,
            DroneBuild.LONG_RANGE};

    static List<DroneBuild> allCombinations() {
        List<DroneBuild> list = new ArrayList<>();
        for (FrameType f : BuiltinRegressionTest.builtins(FrameType.values())) {
            for (StackType s : BuiltinRegressionTest.builtins(StackType.values())) {
                for (MotorType m : BuiltinRegressionTest.builtins(MotorType.values())) {
                    for (PropType p : BuiltinRegressionTest.builtins(PropType.values())) {
                        for (VideoType v : BuiltinRegressionTest.builtins(VideoType.values())) {
                            for (BatteryType b : BuiltinRegressionTest.builtins(BatteryType.values())) {
                                list.add(new DroneBuild(f, s, m, p, v, b));
                            }
                        }
                    }
                }
            }
        }
        return list;
    }

    static List<DroneBuild> buildableCombinations() {
        List<DroneBuild> list = new ArrayList<>();
        for (DroneBuild b : allCombinations()) {
            if (b.buildable()) {
                list.add(b);
            }
        }
        return list;
    }

    private static void close(double expected, double actual, double relative, double digit, String what) {
        double tol = Math.max(Math.abs(expected) * relative, digit / 2.0 + 1e-12);
        assertEquals(expected, actual, tol, what);
    }

    @Test
    void massMatchesPartsForAllCombinations() {
        List<DroneBuild> all = allCombinations();
        assertEquals(10800, all.size());
        assertEquals(824, buildableCombinations().size());
        for (DroneBuild b : all) {
            assertEquals(b.totalMassGrams(), AirframeSpec.of(b).massKg * 1000.0, 1e-9);
        }
    }

    @Test
    void derivedParametersMatchTable() {
        double[][] inertia = {{7.99, 13.63, 7.75}, {473.08, 820.75, 461.39}, {1469.5, 2398.5, 1408.6}, {1089.6, 1976.3, 1044.6},
                {3567.8, 5934.4, 3396.6}};
        double[] j = {46.4, 893.5, 6346, 6470, 17304};
        double[] kT = {2.8656e-9, 9.3169e-8, 1.0635e-6, 1.0812e-6, 2.8035e-6};
        double[] kQ = {1.2018e-11, 1.0643e-9, 1.1002e-8, 1.1919e-8, 3.1769e-8};
        double[] kt = {0.434, 2.076, 5.457, 5.134, 7.346};
        double[] rm = {233.0, 149.0, 60.7, 65.7, 75.7};
        double[] i0 = {0.105, 0.54, 1.35, 1.26, 1.35};
        double[] ilim = {6.5, 32.5, 71.5, 71.5, 71.5};
        double[] rpack = {43, 43, 36, 39, 75};
        double[] icont = {13.5, 61.8, 156, 132, 44};
        double[] paux = {1.80, 3.15, 7.50, 3.00, 7.50};
        double[] vh = {5.69, 7.42, 6.29, 5.70, 5.83};
        double[] tau = {40.0, 26.0, 12.3, 15.2, 22.6};
        double[] bRoll = {2557, 1353, 1969, 2445, 1491};
        double[] bPitch = {2481, 1320, 1887, 2344, 1419};
        double[] yawStat = {231, 151, 150, 187, 93};
        double[] yawInst = {1928, 949, 3195, 3377, 1301};
        double[] com = {0.7, 10.3, 22.0, -4.2, 28.5};
        double[] ym = {9.3, 1.7, -7.0, 18.7, -13.5};
        for (int k = 0; k < PRESETS.length; k++) {
            AirframeSpec p = AirframeSpec.of(PRESETS[k]);
            String n = PRESETS[k].frame().name();
            close(inertia[k][0], p.inertiaXx * 1e6, 0.005, 0.01, n + " Ixx");
            close(inertia[k][1], p.inertiaYy * 1e6, 0.005, 0.01, n + " Iyy");
            close(inertia[k][2], p.inertiaZz * 1e6, 0.005, 0.01, n + " Izz");
            close(j[k], p.rotorInertia * 1e9, 0.005, 0.1, n + " J");
            close(kT[k], p.kThrust, 0.005, 0.0, n + " kT");
            close(kQ[k], p.kTorque, 0.005, 0.0, n + " kQ");
            close(kt[k], p.kt * 1000.0, 0.005, 0.001, n + " Kt");
            close(rm[k], p.phaseResistance * 1000.0, 0.005, 0.1, n + " Rm");
            close(i0[k], p.idleCurrent, 0.005, 0.001, n + " I0");
            close(ilim[k], p.escCurrentLimit, 0.005, 0.1, n + " Ilim");
            close(rpack[k], p.packResistance * 1000.0, 0.005, 1.0, n + " Rpack");
            close(icont[k], p.continuousCurrent, 0.005, 0.1, n + " Icont");
            close(paux[k], p.auxPower, 0.005, 0.01, n + " Paux");
            close(vh[k], p.hoverInducedVelocity, 0.005, 0.01, n + " vh");
            close(tau[k], p.motorTau * 1000.0, 0.005, 0.1, n + " tau");
            close(bRoll[k], p.authorityRoll, 0.005, 1.0, n + " bRoll");
            close(bPitch[k], p.authorityPitch, 0.005, 1.0, n + " bPitch");
            close(yawStat[k], p.authorityYawSteady, 0.005, 1.0, n + " bYawStat");
            close(yawInst[k], p.authorityYawInstant, 0.005, 1.0, n + " bYawInst");
            close(com[k], p.comHeight * 1000.0, 0.005, 0.1, n + " com");
            close(ym[k], p.rotorPlaneOffset * 1000.0, 0.005, 0.1, n + " ym");
        }
    }

    @Test
    void stationaryValuesMatchCalibrationTable() {
        double[][] hoverStick = {{47.8, 55.0}, {32.2, 36.4}, {20.6, 23.4}, {16.6, 19.0}, {22.8, 27.7}};
        double[] hoverRpm = {43669, 24463, 10509, 9438, 8216};
        double[] hoverCurrent = {3.54, 5.16, 5.19, 4.13, 6.47};
        double[] maxThrust = {22, 335, 1397, 1407, 1227};
        double[] tw = {3.18, 4.67, 10.64, 13.07, 5.80};
        double[] fullRpm = {77915, 52868, 34284, 34119, 19785};
        double[] fullCurrent = {10.6, 67.4, 115.6, 130.3, 84.3};
        double[] fullCell = {4.00, 3.48, 3.54, 3.40, 2.79};
        double[] topAcro = {47.2, 89, 140, 147, 108};
        double[] topTilt = {59.3, 62.8, 71.0, 67.8, 54.3};
        double[] topAngle = {47.2, 84, 108, 123, 108};
        double[] hoverTime = {3.90, 5.57, 11.09, 11.80, 28.46};
        double[] cruiseTime = {3.71, 5.15, 7.93, 7.81, 18.10};
        double[] mixedTime = {2.81, 4.11, 5.67, 2.92, 15.04};
        double[] aggressiveTime = {2.34, 2.68, 4.07, 2.24, 9.70};
        double[] tauPhys = {50.5, 26.0, 12.3, 15.2, 22.6};
        for (int k = 0; k < PRESETS.length; k++) {
            DroneBuild b = PRESETS[k];
            AirframeSpec p = AirframeSpec.of(b);
            BuildAnalysis a = BuildAnalysis.of(b);
            String n = b.frame().name();
            assertEquals(hoverStick[k][0], 100.0 * OperatingPoints.hoverStick(p, 1.0), 1.5, n + " hover soc 1");
            assertEquals(hoverStick[k][1], a.hoverThrottlePercent(), 1.5, n + " hover soc 0.5");
            assertEquals(hoverRpm[k], p.hoverOmega * 60.0 / (2.0 * Math.PI), 0.01 * hoverRpm[k], n + " hover rpm");
            assertEquals(hoverCurrent[k], a.hoverCurrentA(), 0.03 * hoverCurrent[k], n + " hover current");
            assertEquals(maxThrust[k], a.maxThrustPerMotorGrams(), 0.03 * maxThrust[k], n + " max thrust");
            assertEquals(tw[k], a.thrustToWeight(), 0.03 * tw[k], n + " T/W");
            OperatingPoints.Point full = new OperatingPoints.Point();
            OperatingPoints.uniformDuty(p, 1.0, 1.0, false, 0.0, 0.0, full);
            assertEquals(fullRpm[k], full.omega * 60.0 / (2.0 * Math.PI), 0.015 * fullRpm[k], n + " full rpm");
            assertEquals(fullCurrent[k], a.fullThrottleCurrentA(), 0.03 * fullCurrent[k], n + " full current");
            assertEquals(fullCell[k], a.fullThrottleCellVoltage(), 0.05, n + " full cell");
            assertEquals(topAcro[k], a.topSpeedAcroKmh(), 0.05 * topAcro[k], n + " top acro");
            assertEquals(topTilt[k], Math.toDegrees(OperatingPoints.topSpeedTilt(p, BuildAnalysis.TOP_SPEED_SOC)), 2.0, n + " tilt");
            assertEquals(topAngle[k], a.topSpeedAngleKmh(), 0.05 * topAngle[k], n + " top angle");
            assertEquals(hoverTime[k], a.hoverFlightTimeMin(), 0.05 * hoverTime[k], n + " hover time");
            assertEquals(cruiseTime[k], a.cruiseFlightTimeMin(), 0.05 * cruiseTime[k], n + " cruise time");
            assertEquals(mixedTime[k], a.mixedFlightTimeMin(), 0.05 * mixedTime[k], n + " mixed time");
            assertEquals(aggressiveTime[k], a.aggressiveFlightTimeMin(), 0.05 * aggressiveTime[k], n + " aggressive time");
            assertEquals(tauPhys[k], a.motorResponseMs(), 0.05 * tauPhys[k], n + " tau phys");
            assertEquals(p.impactTolerance, a.crashSpeedMs());
        }
    }

    @Tag("slow")
    @Test
    void allCombinationsYieldFiniteParametersAndAnalysis() {
        long start = cpuNanos();
        for (DroneBuild b : allCombinations()) {
            AirframeSpec p = AirframeSpec.of(b);
            double[] values = {p.massKg, p.inertiaXx, p.inertiaYy, p.inertiaZz, p.kt, p.rotorInertiaEff, p.omegaMax, p.hoverOmega,
                    p.motorTau, p.kThrust, p.kTorque, p.hoverInducedVelocity, p.authorityRoll, p.authorityPitch,
                    p.authorityYawSteady, p.authorityYawInstant, p.hitboxWidth, p.hitboxHeight, p.hitboxFootOffset,
                    p.rotationalDamping, p.hoverVoltage};
            for (double v : values) {
                assertTrue(Double.isFinite(v), b.toString());
            }
            BuildAnalysis a = BuildAnalysis.of(b);
            boolean cannotHover = a.warnings().contains(BuildWarning.CANNOT_HOVER);
            double[] always = {a.massGrams(), a.thrustToWeight(), a.maxThrustPerMotorGrams(), a.fullThrottleCurrentA(),
                    a.fullThrottleCellVoltage(), a.escLoadPercent(), a.batteryLoadPercent(), a.topSpeedAcroKmh(),
                    a.topSpeedAngleKmh(), a.motorResponseMs(), a.rollAccelDps2(), a.crashSpeedMs()};
            for (double v : always) {
                assertTrue(Double.isFinite(v), b.toString());
            }
            double[] hoverFields = {a.hoverThrottlePercent(), a.hoverCurrentA(), a.hoverFlightTimeMin(), a.cruiseFlightTimeMin(),
                    a.mixedFlightTimeMin(), a.aggressiveFlightTimeMin(), a.mixedCurrentA(), a.landingCellVoltage()};
            for (double v : hoverFields) {
                assertTrue(Double.isFinite(v) || cannotHover, b.toString());
            }
            PidProfile pid = PidAutoTune.derive(p, new FilterSettings(), new PidProfile());
            assertTrue(Double.isFinite(pid.roll.p + pid.pitch.d + pid.yaw.i + pid.tpaBreakpoint), b.toString());
        }
        double seconds = (cpuNanos() - start) * 1e-9;
        assertTrue(seconds < 45.0, "took " + seconds + " s CPU");
    }

    private static long cpuNanos() {
        ThreadMXBean threads = ManagementFactory.getThreadMXBean();
        return threads.isCurrentThreadCpuTimeSupported() ? threads.getCurrentThreadCpuTime() : System.nanoTime();
    }

    @Test
    void autoTunedPidsMatchTable() {
        double[][] roll = {{21, 17, 52}, {60, 71, 95}, {42, 49, 31}, {33, 39, 31}, {55, 65, 75}};
        double[][] pitch = {{22, 17, 53}, {62, 73, 97}, {43, 51, 32}, {35, 41, 32}, {58, 68, 79}};
        double[][] yaw = {{57, 74}, {145, 190}, {51, 67}, {48, 63}, {126, 165}};
        double[] angle = {71, 107, 107, 107, 107};
        double[] tpa = {0.70, 0.51, 0.38, 0.35, 0.43};
        double[] airmode = {25, 25, 19, 15, 21};
        for (int k = 0; k < PRESETS.length; k++) {
            AirframeSpec p = AirframeSpec.of(PRESETS[k]);
            PidProfile pid = PidAutoTune.derive(p, new FilterSettings(), new PidProfile());
            String n = PRESETS[k].frame().name();
            assertEquals(roll[k][0], pid.roll.p, 1.0, n);
            assertEquals(roll[k][1], pid.roll.i, 1.0, n);
            assertEquals(roll[k][2], pid.roll.d, 1.0, n);
            assertEquals(120, pid.roll.ff, 1.0, n);
            assertEquals(pitch[k][0], pid.pitch.p, 1.0, n);
            assertEquals(pitch[k][1], pid.pitch.i, 1.0, n);
            assertEquals(pitch[k][2], pid.pitch.d, 1.0, n);
            assertEquals(yaw[k][0], pid.yaw.p, 1.0, n);
            assertEquals(yaw[k][1], pid.yaw.i, 1.0, n);
            assertEquals(0, pid.yaw.d, 1.0, n);
            assertEquals(angle[k], pid.angleStrength, 1.0, n);
            assertEquals(tpa[k], pid.tpaBreakpoint, 0.005, n);
            assertEquals(airmode[k], pid.airmodeStartThrottlePercent, 1.0, n);
            assertEquals(PidSource.DERIVED, pid.source);
            assertEquals(p.buildKey, pid.derivedForBuild);
        }
    }

    @Test
    void hitboxAndHullMatchTable() {
        double[][] box = {{83, 24, -14.6}, {182, 47, -13.3}, {289, 66, -25.0}, {282, 49, -29.9}, {387, 74, -31.5}};
        for (int k = 0; k < PRESETS.length; k++) {
            AirframeSpec p = AirframeSpec.of(PRESETS[k]);
            assertEquals(box[k][0], p.hitboxWidth * 1000.0, 1.0);
            assertEquals(box[k][1], p.hitboxHeight * 1000.0, 1.0);
            assertEquals(box[k][2], p.hitboxFootOffset * 1000.0, 1.0);
            assertEquals(12, p.hullCount());
            double min = Double.POSITIVE_INFINITY;
            double max = Double.NEGATIVE_INFINITY;
            for (int h = 0; h < p.hullCount(); h++) {
                min = Math.min(min, p.hullY(h));
                max = Math.max(max, p.hullY(h));
                assertEquals(h < 4 ? h : -1, p.hullMotor(h));
            }
            assertEquals(p.hitboxFootOffset, min, 1e-12);
            assertEquals(p.hitboxHeight, max - min, 1e-12);
            for (int i = 0; i < 4; i++) {
                assertEquals(Math.signum(p.motorX(i)), Math.signum(p.hullX(i)));
                assertEquals(Math.signum(p.motorZ(i)), Math.signum(p.hullZ(i)));
                assertTrue(Math.abs(p.hullX(i)) > p.motorOffset);
            }
        }
    }

    @Test
    void whoopMotorInertiaIsClampedOthersAreNot() {
        AirframeSpec whoop = AirframeSpec.of(DroneBuild.WHOOP);
        assertEquals(40.0, whoop.motorTau * 1000.0, 0.2);
        assertEquals(36.7, whoop.rotorInertiaEff * 1e9, 0.18);
        assertEquals(50.5, whoop.motorTauPhys * 1000.0, 0.25);
        for (int k = 1; k < PRESETS.length; k++) {
            AirframeSpec p = AirframeSpec.of(PRESETS[k]);
            assertEquals(p.rotorInertia, p.rotorInertiaEff, 0.0);
            assertEquals(p.motorTauPhys, p.motorTau, 1e-15);
        }
    }

    @Test
    void workbenchAnalysisIsCachedAndFast() {
        assertSame(BuildAnalysis.of(DroneBuild.RACER), BuildAnalysis.of(DroneBuild.RACER));
        for (DroneBuild b : buildableCombinations().subList(0, 20)) {
            BuildAnalysis.compute(b);
        }
        DroneBuild fresh = new DroneBuild(FrameType.RACE_5, StackType.STACK_30, MotorType.M2207_1750, PropType.P3_3B,
                VideoType.DIGITAL_LITE, BatteryType.B4S_650);
        long start = System.nanoTime();
        BuildAnalysis first = BuildAnalysis.of(fresh);
        double ms = (System.nanoTime() - start) * 1e-6;
        assertSame(first, BuildAnalysis.of(fresh));
        assertTrue(ms < 20.0, "cold analysis took " + ms + " ms");
    }
}
