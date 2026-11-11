package de.lardtom.propwash.sim;

import de.lardtom.propwash.build.DroneBuild;
import de.lardtom.propwash.drone.CauldronContent;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CauldronStillWaterTest {
    private static final double FPS = 250.0;
    private static final double MAX_DRIFT = 0.05;
    private static final double CALM_WIND = 0.79;
    private static final double RAIN_WIND = 3.1;
    private static final double CALM_GUSTS = 0.12;
    private static final double RAIN_GUSTS = 0.42;
    private static final double SETTLE = 0.5;
    private static final double OBSERVE = 3.0;
    private static final double WINDOW = 0.25;
    private static final int LEVEL = CauldronContent.MAX_LEVEL;
    private static final double TOP = CauldronContent.WATER.top(LEVEL);

    private enum Water {
        CAULDRON,
        BLOCK
    }

    private static FlatWorld inWater(DroneBuild build, boolean waterproof, Water water, double comY) {
        FlatWorld w = FlatWorld.of(build);
        w.groundY = water == Water.CAULDRON ? CauldronContent.FLOOR : -100.0;
        w.place(0.5, comY, 0.5, 0.0);
        w.sim.setWaterproof(waterproof);
        w.input.mode = StabMode.ANGLE;
        return w;
    }

    private static void frame(FlatWorld w, Water water, double wind, double gusts) {
        double y = w.sim.body.py;
        if (water == Water.CAULDRON) {
            w.env.fluid = CauldronContent.WATER.fluidAt(LEVEL, 0.5, y, 0.5);
            w.env.waterSurface = CauldronContent.WATER.waterSurface(LEVEL, 0.5, y, 0.5);
        } else {
            w.env.fluid = y < TOP ? Fluid.WATER : Fluid.NONE;
            w.env.waterSurface = TOP - y;
        }
        w.env.windX = wind * 0.6;
        w.env.windZ = wind * 0.8;
        w.env.gustIntensity = gusts;
        w.frame(1.0 / FPS);
    }

    private static void arm(FlatWorld w, Water water, double wind, double gusts) {
        w.input.armSwitch = false;
        frame(w, water, wind, gusts);
        w.input.armSwitch = true;
        frame(w, water, wind, gusts);
    }

    private static double neutralDrift(FlatWorld w, Water water, double wind, double gusts) {
        int settle = (int) Math.round(SETTLE * FPS);
        int frames = settle + (int) Math.round(OBSERVE * FPS);
        int window = (int) Math.round(WINDOW * FPS);
        double[] xs = new double[frames];
        double[] zs = new double[frames];
        double peak = 0.0;
        for (int i = 0; i < frames; i++) {
            frame(w, water, wind, gusts);
            xs[i] = w.x;
            zs[i] = w.z;
            if (i - window >= settle) {
                peak = Math.max(peak, Math.hypot(xs[i] - xs[i - window], zs[i] - zs[i - window]) / WINDOW);
            }
        }
        return peak;
    }

    @Test
    void waterproofDroneHoversInStillWaterWithoutDrift() {
        for (DroneBuild build : new DroneBuild[]{DroneBuild.WHOOP, DroneBuild.FREESTYLE}) {
            for (Water water : Water.values()) {
                for (double[] weather : new double[][]{{CALM_WIND, CALM_GUSTS}, {RAIN_WIND, RAIN_GUSTS}}) {
                    FlatWorld w = inWater(build, true, water, 0.55);
                    arm(w, water, weather[0], weather[1]);
                    assertTrue(w.sim.telemetry().armed, "wasserdicht scharf im " + water);
                    double drift = neutralDrift(w, water, weather[0], weather[1]);
                    assertTrue(w.sim.telemetry().armed, "bleibt scharf im " + water);
                    assertTrue(drift <= MAX_DRIFT, String.format("%s im %s bei Wind %.2f m/s: seitlich %.3f m/s ohne Knüppel",
                            build.frame(), water, weather[0], drift));
                }
            }
        }
    }

    @Test
    void ordinaryDroneDrownsAndSinksStraightDown() {
        for (Water water : Water.values()) {
            FlatWorld w = inWater(DroneBuild.WHOOP, false, water, TOP + 0.3);
            arm(w, water, CALM_WIND, CALM_GUSTS);
            assertTrue(w.sim.telemetry().armed, "über dem Wasser scharf");
            double drift = neutralDrift(w, water, CALM_WIND, CALM_GUSTS);
            assertFalse(w.sim.telemetry().armed);
            assertEquals(DisarmReason.FLUID, w.sim.telemetry().lastDisarmReason);
            assertTrue(drift <= MAX_DRIFT, String.format("ohne Wasserschutz im %s: seitlich %.3f m/s", water, drift));
        }
    }

    @Test
    void sticksStillSteerInTheCauldronAndFullThrottleFliesOut() {
        FlatWorld pitched = inWater(DroneBuild.WHOOP, true, Water.CAULDRON, 0.55);
        arm(pitched, Water.CAULDRON, CALM_WIND, CALM_GUSTS);
        neutralDrift(pitched, Water.CAULDRON, CALM_WIND, CALM_GUSTS);
        double x0 = pitched.x;
        double z0 = pitched.z;
        pitched.input.pitch = 1.0;
        for (double t = 0.0; t < 1.0; t += 1.0 / FPS) {
            frame(pitched, Water.CAULDRON, CALM_WIND, CALM_GUSTS);
        }
        double moved = Math.hypot(pitched.x - x0, pitched.z - z0);
        assertTrue(pitched.sim.body.tiltRad() * SimMath.DEG > 10.0, "Nicken kippt die Drohne: " + pitched.sim.body.tiltRad() * SimMath.DEG);
        assertTrue(moved > 0.02, "Nicken bewegt die Drohne im Kessel: " + moved + " m");

        FlatWorld out = inWater(DroneBuild.WHOOP, true, Water.CAULDRON, 0.55);
        arm(out, Water.CAULDRON, CALM_WIND, CALM_GUSTS);
        neutralDrift(out, Water.CAULDRON, CALM_WIND, CALM_GUSTS);
        out.input.throttle = 1.0;
        double left = Double.NaN;
        for (double t = 0.0; t < 4.0 && Double.isNaN(left); t += 1.0 / FPS) {
            frame(out, Water.CAULDRON, CALM_WIND, CALM_GUSTS);
            if (out.sim.body.py > 1.5) {
                left = t;
            }
        }
        assertFalse(Double.isNaN(left), "Vollgas trägt die Drohne aus dem Kessel, Höhe " + out.sim.body.py);
        assertTrue(out.sim.telemetry().armed);
    }

    @Test
    void windStillCarriesDronesInTheAir() {
        FlatWorld w = Tests.hovering(DroneBuild.WHOOP, Tests.quietTuning(), 20.0, StabMode.ANGLE, 1.0);
        w.env.windX = 3.0;
        AltitudeHold hold = new AltitudeHold(20.0);
        double dt = 1.0 / FPS;
        for (double t = 0.0; t < 3.0; t += dt) {
            hold.apply(w, dt);
            w.frame(dt);
        }
        assertTrue(w.sim.body.vx > 1.0, "Wind trägt die Drohne in der Luft: " + w.sim.body.vx + " m/s");
    }
}
