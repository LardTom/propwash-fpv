package de.lardtom.propwash.sim;

import de.lardtom.propwash.build.DroneBuild;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

class PhysicsReviewTest {
    @Test
    void currentLimitedEscConservesEnergy() {
        AirframeSpec p = AirframeSpec.of(DroneBuild.FREESTYLE);
        PowerSystem power = new PowerSystem();
        double h = DroneSim.H;
        power.configure(p, h);
        power.setStateOfCharge(1.0);
        double[] lambda = new double[4];
        double[] idle = {0.055, 0.055, 0.055, 0.055};
        for (int k = 0; k < 4000; k++) {
            power.solveBus(idle, true, true);
            power.stepRotors(h, lambda, 1.0, true);
            power.integrateBattery(h, true);
        }
        double[] full = {1.0, 1.0, 1.0, 1.0};
        double batteryEnergy = 0.0;
        double loadEnergy = 0.0;
        double firstBattery = Double.NaN;
        double firstBalanced = Double.NaN;
        int limitedSteps = 0;
        for (int k = 0; k < 400; k++) {
            power.solveBus(full, true, true);
            double v = power.busVoltage;
            double motorPower = 0.0;
            for (int i = 0; i < 4; i++) {
                double c = power.current[i];
                motorPower += c * c * power.resistance[i] + p.kt * power.omega[i] * c;
            }
            double auxPower = v * p.auxPower / Math.max(power.lastBusVoltage, 1.0);
            if (k == 0) {
                firstBattery = power.batteryCurrent;
                firstBalanced = (motorPower + auxPower) / v;
            }
            if (power.anyLimited) {
                limitedSteps++;
            }
            batteryEnergy += v * power.batteryCurrent * h;
            loadEnergy += (motorPower + auxPower) * h;
            power.stepRotors(h, lambda, 1.0, true);
            power.integrateBattery(h, true);
        }
        assertTrue(limitedSteps > 100, "limited steps " + limitedSteps);
        assertTrue(firstBattery <= 1.05 * firstBalanced,
                "battery current " + firstBattery + " A but motors+aux only absorb " + firstBalanced + " A worth of power");
        assertTrue(batteryEnergy <= 1.02 * loadEnergy,
                "battery delivered " + batteryEnergy + " J, motors (I^2 R + Ke w I) + aux received " + loadEnergy + " J");
    }

    @Test
    void bounceNeverGainsEnergy() {
        SimTuning t = Tests.quietTuning();
        t.dragScale = 0.0;
        t.restitutionScale = 4.0;
        t.sanitize();
        FlatWorld w = FlatWorld.of(DroneBuild.CINEWHOOP, t);
        double foot = w.sim.params().hitboxFootOffset;
        w.place(0.0, 1.0 - foot, 0.0, 0.0);
        RigidBody b = w.sim.body;
        double e0 = b.kineticEnergy() + b.mass * ModelConstants.G * (b.py + foot);
        double worst = 0.0;
        for (int f = 0; f < 432; f++) {
            w.frame(1.0 / 144.0);
            worst = Math.max(worst, b.kineticEnergy() + b.mass * ModelConstants.G * (b.py + foot));
        }
        assertTrue(worst <= 1.01 * e0, "mechanical energy rose from " + e0 + " J to " + worst + " J");
    }
}
