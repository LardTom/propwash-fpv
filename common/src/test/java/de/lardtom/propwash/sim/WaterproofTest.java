package de.lardtom.propwash.sim;

import de.lardtom.propwash.build.DroneBuild;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WaterproofTest {
    private static final double FPS = 250.0;
    private static final double SURFACE = 0.0;

    static FlatWorld underwater(DroneBuild build, boolean waterproof, double depth) {
        FlatWorld w = FlatWorld.of(build);
        w.groundY = -100.0;
        w.place(0.0, SURFACE - depth, 0.0, 0.0);
        w.sim.setWaterproof(waterproof);
        w.input.mode = StabMode.ANGLE;
        return w;
    }

    static void frame(FlatWorld w) {
        double y = w.sim.body.py;
        w.env.waterSurface = SURFACE - y < -2.0 ? Double.NEGATIVE_INFINITY : SURFACE - y;
        w.env.fluid = y < SURFACE ? Fluid.WATER : Fluid.NONE;
        w.frame(1.0 / FPS);
    }

    static void run(FlatWorld w, double seconds) {
        for (double t = 0.0; t < seconds; t += 1.0 / FPS) {
            frame(w);
        }
    }

    static void arm(FlatWorld w) {
        w.input.armSwitch = false;
        frame(w);
        w.input.armSwitch = true;
        frame(w);
    }

    @Test
    void ordinaryDronesStillShortOut() {
        FlatWorld w = underwater(DroneBuild.FREESTYLE, false, 1.0);
        arm(w);
        assertFalse(w.sim.telemetry().armed);
        assertTrue(ArmBlocker.IN_FLUID.isSet(w.sim.telemetry().armBlockers));
        FlatWorld air = Tests.hovering(DroneBuild.FREESTYLE, Tests.quietTuning(), 20.0, StabMode.ANGLE, 1.0);
        air.env.fluid = Fluid.WATER;
        air.run(0.5, FPS);
        assertFalse(air.sim.telemetry().armed);
        assertEquals(DisarmReason.FLUID, air.sim.telemetry().lastDisarmReason);
    }

    @Test
    void waterproofDronesArmFlyAndDisarmUnderWater() {
        FlatWorld w = underwater(DroneBuild.FREESTYLE, true, 1.0);
        arm(w);
        assertTrue(w.sim.telemetry().armed, "Blocker " + w.sim.telemetry().armBlockers);
        assertTrue(w.sim.telemetry().waterproof);
        assertEquals(1.0, w.sim.telemetry().submerged, 1e-9);
        w.input.throttle = 0.3;
        run(w, 1.0);
        assertTrue(w.sim.telemetry().armed, "kein Kurzschluss");
        w.input.throttle = 0.0;
        w.input.armSwitch = false;
        run(w, 0.1);
        assertFalse(w.sim.telemetry().armed);
        assertEquals(DisarmReason.SWITCH, w.sim.telemetry().lastDisarmReason);
    }

    @Test
    void propsBogDownInWater() {
        FlatWorld wet = underwater(DroneBuild.FREESTYLE, true, 1.0);
        arm(wet);
        wet.input.throttle = 0.5;
        run(wet, 0.5);
        FlatWorld dry = underwater(DroneBuild.FREESTYLE, true, -20.0);
        arm(dry);
        dry.input.throttle = 0.5;
        run(dry, 0.5);
        double wetRpm = Math.abs(wet.sim.telemetry().motorRpm[0]);
        double dryRpm = Math.abs(dry.sim.telemetry().motorRpm[0]);
        assertTrue(wetRpm < 0.25 * dryRpm, "im Wasser " + wetRpm + " U/min, in der Luft " + dryRpm);
        assertTrue(wet.sim.telemetry().totalCurrentA > dry.sim.telemetry().totalCurrentA, "Wasser bremst, der Strom steigt");
    }

    @Test
    void disarmedDronesSinkSlowlyAndBuoyancySlowsThem() {
        FlatWorld proof = underwater(DroneBuild.FREESTYLE, true, 3.0);
        FlatWorld plain = underwater(DroneBuild.FREESTYLE, false, 3.0);
        run(proof, 2.0);
        run(plain, 2.0);
        double vProof = proof.sim.body.vy;
        double vPlain = plain.sim.body.vy;
        assertTrue(vProof < -0.05, "sinkt: " + vProof);
        assertTrue(vProof > -1.5, "langsam: " + vProof);
        assertTrue(vProof > vPlain, "Auftrieb bremst: " + vProof + " gegen " + vPlain);
    }

    @Test
    void fullThrottleClimbsSlowlyThroughWaterAndOutIntoTheAir() {
        FlatWorld w = underwater(DroneBuild.FREESTYLE, true, 1.0);
        arm(w);
        w.input.throttle = 1.0;
        double surfaced = Double.NaN;
        double maxUnderwaterSpeed = 0.0;
        for (double t = 0.0; t < 6.0; t += 1.0 / FPS) {
            frame(w);
            if (w.sim.body.py < SURFACE - 0.2) {
                maxUnderwaterSpeed = Math.max(maxUnderwaterSpeed, Math.abs(w.sim.body.vy));
            }
            if (Double.isNaN(surfaced) && w.sim.body.py > SURFACE + 1.0) {
                surfaced = t;
            }
        }
        assertFalse(Double.isNaN(surfaced), "kommt aus dem Wasser, Höhe " + w.sim.body.py);
        assertTrue(surfaced > 0.4, "unter Wasser langsam: nach " + surfaced + " s einen Meter über der Oberfläche");
        assertTrue(maxUnderwaterSpeed < 4.0, "unter Wasser höchstens " + maxUnderwaterSpeed + " m/s");
        assertTrue(w.sim.telemetry().armed);
    }

    @Test
    void propsAboveTheSurfaceSpinFreely() {
        FlatWorld w = underwater(DroneBuild.FREESTYLE, true, -0.5);
        w.env.waterSurface = -0.5;
        w.env.fluid = Fluid.NONE;
        w.frame(1.0 / FPS);
        for (double f : w.sim.fluidThrust) {
            assertEquals(1.0, f, 0.0);
        }
        w.env.waterSurface = 0.5;
        w.env.fluid = Fluid.WATER;
        w.frame(1.0 / FPS);
        for (double f : w.sim.fluidThrust) {
            assertTrue(f > 50.0, "Schub unter Wasser " + f);
        }
        w.sim.setWaterproof(false);
        for (double f : w.sim.fluidThrust) {
            assertEquals(1.0, f, 0.0);
        }
    }

    @Test
    void lavaStillDisarmsWaterproofDrones() {
        FlatWorld w = Tests.hovering(DroneBuild.FREESTYLE, Tests.quietTuning(), 20.0, StabMode.ANGLE, 1.0);
        w.sim.setWaterproof(true);
        w.env.fluid = Fluid.LAVA;
        w.run(0.1, FPS);
        assertFalse(w.sim.telemetry().armed);
        assertEquals(DisarmReason.FLUID, w.sim.telemetry().lastDisarmReason);
    }

    @Test
    void whoopsAndCinewhoopsSurviveADip() {
        for (DroneBuild build : new DroneBuild[]{DroneBuild.WHOOP, DroneBuild.CINEWHOOP, DroneBuild.LONG_RANGE}) {
            FlatWorld w = underwater(build, true, 0.5);
            arm(w);
            assertTrue(w.sim.telemetry().armed, build.frame() + " Blocker " + w.sim.telemetry().armBlockers);
            w.input.throttle = 1.0;
            run(w, 6.0);
            assertTrue(w.sim.body.py > SURFACE + 0.5, build.frame() + " kommt heraus, Höhe " + w.sim.body.py);
            assertTrue(Double.isFinite(w.sim.body.vy));
        }
    }
}
