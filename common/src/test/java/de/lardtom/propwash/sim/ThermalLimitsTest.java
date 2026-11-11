package de.lardtom.propwash.sim;

import de.lardtom.propwash.build.DroneBuild;
import de.lardtom.propwash.build.MotorType;
import de.lardtom.propwash.build.PropType;
import de.lardtom.propwash.build.VideoType;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ThermalLimitsTest {
    static DroneBuild overloaded() {
        return new DroneBuild(AddonParts.longRange8s(), AddonParts.stack3to8(), MotorType.M2207_1750, PropType.P7_2B, VideoType.DIGITAL_HD,
                AddonParts.lipo(8, 1300, 280.0, 0.0055, 120));
    }

    private static final class Run {
        int escOn;
        int escOff;
        int hotOn;
        int hotOff;
        int overheat;
        double wear;
        double maxTemp;
        double maxEsc;
        double minLimit = 1.0;
        double minTorque = 1.0;
        double limitWhileEscCold = 1.0;
        double maxDutyOverLimit;
    }

    private static Run stand(DroneBuild build, boolean limits, double seconds, double coolSeconds) {
        SimTuning tuning = Tests.quietTuning();
        tuning.infiniteBattery = true;
        tuning.thermalLimits = limits;
        TestStand stand = new TestStand(build, tuning);
        Run run = new Run();
        stand.spinUp();
        stand.duty(1.0);
        int total = (int) Math.round((seconds + coolSeconds) * DroneSim.PHYSICS_HZ);
        for (int k = 0; k < total; k++) {
            if (k == (int) Math.round(seconds * DroneSim.PHYSICS_HZ)) {
                stand.duty(0.055);
            }
            stand.substeps(1);
            EventQueue events = stand.sim.events();
            for (int e = 0; e < events.size(); e++) {
                boolean on = events.value(e) > 0.5;
                switch (events.type(e)) {
                    case ESC_DERATE -> {
                        if (on) {
                            run.escOn++;
                        } else {
                            run.escOff++;
                        }
                    }
                    case MOTOR_HOT -> {
                        if (on) {
                            run.hotOn++;
                        } else {
                            run.hotOff++;
                        }
                    }
                    case MOTOR_THERMAL_WEAR -> run.wear += events.value(e);
                    case MOTOR_OVERHEAT -> run.overheat++;
                    default -> {
                    }
                }
            }
            Telemetry t = stand.sim.telemetry();
            Thermal th = stand.sim.thermal;
            for (int i = 0; i < AirframeSpec.MOTORS; i++) {
                run.maxTemp = Math.max(run.maxTemp, t.motorTempC[i]);
                run.maxEsc = Math.max(run.maxEsc, t.escTempC[i]);
                run.minLimit = Math.min(run.minLimit, t.motorPowerLimit[i]);
                run.minTorque = Math.min(run.minTorque, th.torque[i]);
                if (th.escTemp[i] <= Thermal.ESC_LIMIT_C) {
                    run.limitWhileEscCold = Math.min(run.limitWhileEscCold, th.limit[i]);
                }
                run.maxDutyOverLimit = Math.max(run.maxDutyOverLimit, stand.sim.dutyCur[i] - th.limit[i]);
            }
        }
        return run;
    }

    @Test
    void sustainedFullThrottleDeratesOnlyInTheEscWhileHotMotorsWear() {
        DroneBuild build = overloaded();
        assertTrue(build.buildable(), build.problems().toString());
        AirframeSpec p = AirframeSpec.of(build);
        assertTrue(BuildAnalysis.of(build).warnings().contains(BuildWarning.MOTOR_THERMAL));
        assertTrue(BuildAnalysis.of(build).sustainedMotorTempC() > p.motorDerateStartC);
        Run limited = stand(build, true, 90.0, 120.0);
        assertEquals(4, limited.escOn, "jeder ESC meldet den Beginn der Abregelung");
        assertEquals(4, limited.escOff, "und nach dem Abkühlen das Ende");
        assertEquals(4, limited.hotOn, "jeder Motor meldet die Hitze");
        assertEquals(4, limited.hotOff, "und nach dem Abkühlen das Ende");
        assertTrue(limited.minLimit < 1.0 && limited.minLimit >= Thermal.ESC_MIN_POWER_LIMIT, "Grenze " + limited.minLimit);
        assertEquals(1.0, limited.limitWhileEscCold, "unter " + Thermal.ESC_LIMIT_C + " °C am ESC regelt nichts ab");
        assertTrue(limited.maxDutyOverLimit <= 0.005, "Tastverhältnis über der Grenze: " + limited.maxDutyOverLimit);
        assertTrue(limited.maxEsc < Thermal.ESC_LIMIT_C + Thermal.ESC_DERATE_SPAN_K, "Abregelung hält den ESC unten: " + limited.maxEsc);
        assertTrue(limited.minTorque < 1.0 && limited.minTorque >= 1.0 - Thermal.MAX_TORQUE_LOSS, "Drehmoment " + limited.minTorque);
        assertTrue(limited.wear > 0.0, "Verschleiß " + limited.wear);
        Run free = stand(build, false, 90.0, 0.0);
        assertEquals(0, free.escOn);
        assertEquals(0, free.hotOn);
        assertEquals(0.0, free.wear);
        assertEquals(1.0, free.minLimit);
        assertEquals(1.0, free.minTorque);
        assertTrue(free.maxEsc > limited.maxEsc, free.maxEsc + " > " + limited.maxEsc);
    }

    private static double fullThrottleOmega(DroneBuild build, double motorC, double escC) {
        TestStand stand = TestStand.of(build);
        stand.spinUp();
        stand.duty(1.0);
        int settle = DroneSim.PHYSICS_HZ / 2;
        for (int k = 0; k < settle; k++) {
            SimSnapshot snapshot = new SimSnapshot();
            stand.sim.copyStateTo(snapshot);
            for (int i = 0; i < AirframeSpec.MOTORS; i++) {
                snapshot.motorTempC[i] = motorC;
                snapshot.escTempC[i] = escC;
            }
            stand.sim.copyStateFrom(snapshot);
            stand.substeps(1);
        }
        return stand.omega(0);
    }

    @Test
    void hotMotorLosesTorqueButKeepsTheFullPowerLimit() {
        DroneBuild build = DroneBuild.FREESTYLE;
        AirframeSpec p = AirframeSpec.of(build);
        double cold = fullThrottleOmega(build, 60.0, 40.0);
        double hot = fullThrottleOmega(build, p.motorMaxTempC, 40.0);
        double escHot = fullThrottleOmega(build, 60.0, Thermal.ESC_LIMIT_C + Thermal.ESC_DERATE_SPAN_K);
        assertTrue(hot < 0.99 * cold, "heißer Motor dreht langsamer: " + hot + " < " + cold);
        assertTrue(hot > 0.9 * cold, "aber höchstens um den Drehmomentverlust: " + hot + " / " + cold);
        assertTrue(escHot < 0.8 * cold, "heißer ESC regelt deutlich ab: " + escHot + " / " + cold);
    }

    @Test
    void escPowerLimitFollowsBlheli32() {
        assertEquals(1.0, Thermal.escPowerLimit(Thermal.ESC_LIMIT_C));
        assertEquals(1.0, Thermal.escPowerLimit(25.0));
        assertEquals(0.625, Thermal.escPowerLimit(Thermal.ESC_LIMIT_C + 0.5 * Thermal.ESC_DERATE_SPAN_K), 1e-12);
        assertEquals(Thermal.ESC_MIN_POWER_LIMIT, Thermal.escPowerLimit(Thermal.ESC_LIMIT_C + Thermal.ESC_DERATE_SPAN_K), 1e-12);
        assertEquals(Thermal.ESC_MIN_POWER_LIMIT, Thermal.escPowerLimit(250.0), 1e-12);
        assertEquals(1.0, Thermal.escPowerLimit(Double.NaN));
    }

    @Test
    void torqueLossAndWearFollowTheMotorDefinition() {
        AirframeSpec p = AirframeSpec.of(AddonParts.xClassBuild());
        assertEquals(140.0, p.motorDerateStartC);
        assertEquals(160.0, p.motorMaxTempC);
        assertEquals(1.0, Thermal.torqueFactor(p, 139.9));
        assertEquals(1.0 - 0.5 * Thermal.MAX_TORQUE_LOSS, Thermal.torqueFactor(p, 150.0), 1e-12);
        assertEquals(1.0 - Thermal.MAX_TORQUE_LOSS, Thermal.torqueFactor(p, 175.0), 1e-12);
        assertEquals(0.0, Thermal.wearRate(p, 120.0));
        assertEquals(0.25 * p.motorHeatWearPerSecond, Thermal.wearRate(p, 150.0), 1e-15);
        assertEquals(p.motorHeatWearPerSecond, Thermal.wearRate(p, 200.0), 1e-15);
        AirframeSpec builtin = AirframeSpec.of(DroneBuild.FREESTYLE);
        assertEquals(130.0, builtin.motorDerateStartC);
        assertEquals(Thermal.OVERHEAT_C, builtin.motorMaxTempC);
        assertEquals(Thermal.MOTOR_HEAT_CAPACITY * builtin.motorMassKg * 1000.0, builtin.motorHeatCapacity);
        assertEquals(Thermal.MOTOR_CONDUCTANCE_BASE, builtin.motorCoolingBase);
        assertEquals(Thermal.MOTOR_CONDUCTANCE_PER_SPEED * Thermal.bellRadius(0.022), builtin.motorCoolingPerOmega, 1e-15);
        assertEquals(Thermal.eddyLoss(0.022, 0.007), builtin.motorEddyLoss, 1e-15);
    }

    @Test
    void bellSpeedCoolsAndFlightAirflowCools() {
        AirframeSpec whoop = AirframeSpec.of(DroneBuild.WHOOP);
        AirframeSpec five = AirframeSpec.of(DroneBuild.FREESTYLE);
        double perArea = Thermal.motorCooling(whoop, 3000.0) / whoop.motorCoolingScale;
        double perAreaFive = Thermal.motorCooling(five, 3000.0) / five.motorCoolingScale;
        assertTrue(perArea < perAreaFive, "kleine Glocke läuft bei gleicher Drehzahl langsamer an der Luft entlang");
        assertEquals(1.0, Thermal.airflow(0.0));
        assertEquals(1.0, Thermal.airflow(-3.0));
        assertEquals(1.0, Thermal.airflow(Double.NaN));
        assertEquals(1.0 + Thermal.AIRFLOW_COOLING, Thermal.airflow(Thermal.AIRFLOW_REFERENCE_MS), 1e-12);
        assertEquals(Thermal.airflow(100.0), Thermal.airflow(1.0e6));
    }

    @Test
    void thermalLimitsSwitchIsCopied() {
        SimTuning t = new SimTuning();
        assertTrue(t.thermalLimits);
        assertTrue(t.escOvervoltage);
        t.thermalLimits = false;
        t.escOvervoltage = false;
        SimTuning c = t.copy();
        assertFalse(c.thermalLimits);
        assertFalse(c.escOvervoltage);
    }
}
