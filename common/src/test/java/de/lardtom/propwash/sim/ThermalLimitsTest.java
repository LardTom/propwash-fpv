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
        int derateOn;
        int derateOff;
        int overheat;
        double wear;
        double maxTemp;
        double minLimit = 1.0;
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
                switch (events.type(e)) {
                    case MOTOR_DERATE -> {
                        if (events.value(e) > 0.5) {
                            run.derateOn++;
                        } else {
                            run.derateOff++;
                        }
                    }
                    case MOTOR_THERMAL_WEAR -> run.wear += events.value(e);
                    case MOTOR_OVERHEAT -> run.overheat++;
                    default -> {
                    }
                }
            }
            Telemetry t = stand.sim.telemetry();
            for (int i = 0; i < AirframeSpec.MOTORS; i++) {
                run.maxTemp = Math.max(run.maxTemp, t.motorTempC[i]);
                run.minLimit = Math.min(run.minLimit, t.motorPowerLimit[i]);
                run.maxDutyOverLimit = Math.max(run.maxDutyOverLimit, stand.sim.dutyCur[i] - stand.sim.thermal.limit[i]);
            }
        }
        return run;
    }

    @Test
    void overloadedMotorsDerateWearAndRecover() {
        DroneBuild build = overloaded();
        assertTrue(build.buildable(), build.problems().toString());
        AirframeSpec p = AirframeSpec.of(build);
        assertTrue(BuildAnalysis.of(build).warnings().contains(BuildWarning.MOTOR_THERMAL));
        assertTrue(BuildAnalysis.of(build).sustainedMotorTempC() > p.motorDerateStartC);
        Run limited = stand(build, true, 90.0, 60.0);
        assertEquals(4, limited.derateOn, "jeder Motor meldet den Beginn");
        assertEquals(4, limited.derateOff, "und nach dem Abkühlen das Ende");
        assertTrue(limited.minLimit < 1.0 && limited.minLimit >= Thermal.MIN_POWER_LIMIT, "Grenze " + limited.minLimit);
        assertTrue(limited.maxDutyOverLimit <= 0.005, "Tastverhältnis über der Grenze: " + limited.maxDutyOverLimit);
        assertTrue(limited.maxTemp < p.motorMaxTempC, "Drosselung hält unter dem Maximum: " + limited.maxTemp);
        assertTrue(limited.wear > 0.0 && limited.wear < 4.0 * 90.0 * p.motorHeatWearPerSecond, "Verschleiß " + limited.wear);
        Run free = stand(build, false, 90.0, 0.0);
        assertEquals(0, free.derateOn);
        assertEquals(0.0, free.wear);
        assertEquals(1.0, free.minLimit);
        assertTrue(free.maxTemp > limited.maxTemp, free.maxTemp + " > " + limited.maxTemp);
        assertTrue(free.overheat > 0, "ohne Grenze überhitzt der Motor");
    }

    @Test
    void powerLimitAndWearFollowTheMotorDefinition() {
        AirframeSpec p = AirframeSpec.of(AddonParts.xClassBuild());
        assertEquals(140.0, p.motorDerateStartC);
        assertEquals(160.0, p.motorMaxTempC);
        assertEquals(1.0, Thermal.powerLimit(p, 139.9));
        assertEquals(0.6, Thermal.powerLimit(p, 150.0), 1e-12);
        assertEquals(Thermal.MIN_POWER_LIMIT, Thermal.powerLimit(p, 175.0), 1e-12);
        assertEquals(0.0, Thermal.wearRate(p, 120.0));
        assertEquals(0.25 * p.motorHeatWearPerSecond, Thermal.wearRate(p, 150.0), 1e-15);
        assertEquals(p.motorHeatWearPerSecond, Thermal.wearRate(p, 200.0), 1e-15);
        AirframeSpec builtin = AirframeSpec.of(DroneBuild.FREESTYLE);
        assertEquals(130.0, builtin.motorDerateStartC);
        assertEquals(Thermal.OVERHEAT_C, builtin.motorMaxTempC);
        assertEquals(Thermal.MOTOR_HEAT_CAPACITY * builtin.motorMassKg * 1000.0, builtin.motorHeatCapacity);
        assertEquals(Thermal.MOTOR_CONDUCTANCE_BASE, builtin.motorCoolingBase);
        assertEquals(Thermal.MOTOR_CONDUCTANCE_PER_OMEGA, builtin.motorCoolingPerOmega);
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
