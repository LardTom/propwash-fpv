package de.lardtom.propwash.sim;

import de.lardtom.propwash.build.DroneBuild;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HoldModesTest {
    private static final double DT = 1.0 / 144.0;
    private static final long[] SEEDS = {1L, 2L, 3L, 4L, 5L, 6L};

    private static FlatWorld hover(long seed, double skyView) {
        FlatWorld w = new FlatWorld(new DroneSim(DroneBuild.FREESTYLE, new FlightControllerConfig(), new SimTuning(), seed));
        w.env.skyView = skyView;
        w.place(0.0, 20.0, 0.0, 0.0);
        w.input.mode = StabMode.ANGLE;
        w.arm();
        w.sim.nav.headingOffsetRad = 4.0 * SimMath.RAD;
        AltitudeHold hold = new AltitudeHold(20.0);
        for (int i = 0; i < 144 * 12; i++) {
            hold.apply(w, DT);
            w.frame(DT);
        }
        return w;
    }

    private static double horizontal(FlatWorld w, double x0, double z0) {
        return Math.hypot(w.sim.body.px - x0, w.sim.body.pz - z0);
    }

    @Test
    void altHoldKeepsAltitudeInRealisticBandButNeverPerfect() {
        for (long seed : SEEDS) {
            FlatWorld w = hover(seed, 1.0);
            w.input.altHold = true;
            double y0 = w.sim.body.py;
            double min = Double.MAX_VALUE;
            double max = -Double.MAX_VALUE;
            double sum = 0.0;
            double sq = 0.0;
            int n = 0;
            for (int i = 0; i < 144 * 30; i++) {
                w.frame(DT);
                assertEquals(HoldMode.ALTHOLD, w.sim.telemetry().holdMode, "seed " + seed);
                double y = w.sim.body.py;
                min = Math.min(min, y);
                max = Math.max(max, y);
                sum += y;
                sq += y * y;
                n++;
            }
            double mean = sum / n;
            double sd = Math.sqrt(Math.max(0.0, sq / n - mean * mean));
            assertTrue(max - y0 < 2.0 && y0 - min < 2.0, "seed " + seed + " left the band: " + min + ".." + max);
            assertTrue(max - min > 0.2, "seed " + seed + " holds suspiciously perfect: " + (max - min));
            assertTrue(sd > 0.03, "seed " + seed + " no bobbing: " + sd);
            assertTrue(w.sim.telemetry().armed);
        }
    }

    @Test
    void altHoldCorrectsGustsWithDelay() {
        FlatWorld w = hover(11L, 1.0);
        w.input.altHold = true;
        w.run(5.0, 144.0);
        double y0 = w.sim.body.py;
        w.env.windY = -3.0;
        double minY = y0;
        for (int i = 0; i < 144 * 2; i++) {
            w.frame(DT);
            minY = Math.min(minY, w.sim.body.py);
        }
        w.env.windY = 0.0;
        w.run(12.0, 144.0);
        assertTrue(y0 - minY > 0.1, "downdraft did not push the drone: " + (y0 - minY));
        assertTrue(Math.abs(w.sim.body.py - y0) < 1.5, "did not recover: " + (w.sim.body.py - y0));
    }

    @Test
    void throttleOutsideDeadbandClimbsAndDescendsAtLimitedRate() {
        FlatWorld w = hover(3L, 1.0);
        w.input.altHold = true;
        w.run(3.0, 144.0);
        double thr = w.input.throttle;
        double y0 = w.sim.body.py;
        w.input.throttle = Math.min(1.0, thr + 0.05);
        w.run(3.0, 144.0);
        assertTrue(Math.abs(w.sim.body.py - y0) < 1.5, "inside deadband should hold");
        w.input.throttle = 1.0;
        double peak = 0.0;
        y0 = w.sim.body.py;
        for (int i = 0; i < 144 * 4; i++) {
            w.frame(DT);
            peak = Math.max(peak, w.sim.body.vy);
        }
        double climbed = w.sim.body.py - y0;
        assertTrue(climbed > 2.5, "climb too slow: " + climbed);
        assertTrue(peak < 2.6, "climb rate not limited: " + peak);
        w.input.throttle = 0.0;
        double low = 0.0;
        y0 = w.sim.body.py;
        for (int i = 0; i < 144 * 4; i++) {
            w.frame(DT);
            low = Math.min(low, w.sim.body.vy);
        }
        assertTrue(w.sim.body.py < y0 - 2.5, "descent too slow");
        assertTrue(low > -2.6, "descent rate not limited: " + low);
    }

    @Test
    void posHoldStaysWithinFewMetresWithFixAndDrifts() {
        double drifted = 0.0;
        for (long seed : SEEDS) {
            FlatWorld w = hover(seed, 1.0);
            assertTrue(w.sim.telemetry().gpsSatellites >= 8, "no satellites after warm-up: " + w.sim.telemetry().gpsSatellites);
            w.input.altHold = true;
            w.input.posHold = true;
            w.run(3.0, 144.0);
            assertEquals(HoldMode.POSHOLD, w.sim.telemetry().holdMode);
            double x0 = w.sim.body.px;
            double z0 = w.sim.body.pz;
            double max = 0.0;
            for (int i = 0; i < 144 * 30; i++) {
                w.frame(DT);
                max = Math.max(max, horizontal(w, x0, z0));
            }
            assertTrue(max < 4.0, "seed " + seed + " wandered " + max + " m");
            drifted = Math.max(drifted, max);
            assertTrue(w.sim.telemetry().gpsPositionError > 0.0);
        }
        assertTrue(drifted > 0.3, "position hold is perfect: " + drifted);
    }

    @Test
    void posHoldRefusesWithoutSkyAndFallsBackToAltHold() {
        FlatWorld w = hover(5L, 0.15);
        Telemetry t = w.sim.telemetry();
        assertTrue(t.gpsSatellites < GpsReceiver.FIX_SATELLITES, "cave still has " + t.gpsSatellites + " satellites");
        assertFalse(t.gpsFix);
        w.input.posHold = true;
        w.run(2.0, 144.0);
        assertEquals(HoldMode.ALTHOLD, t.holdMode);
        assertEquals(HoldMode.POSHOLD, t.holdRequested);
        assertTrue(t.posHoldDenied);
    }

    @Test
    void posHoldAbortsWhenSatellitesAreLost() {
        FlatWorld w = hover(4L, 1.0);
        w.input.altHold = true;
        w.input.posHold = true;
        w.run(3.0, 144.0);
        assertEquals(HoldMode.POSHOLD, w.sim.telemetry().holdMode);
        w.env.skyView = 0.0;
        w.run(6.0, 144.0);
        assertEquals(HoldMode.ALTHOLD, w.sim.telemetry().holdMode);
        assertTrue(w.sim.telemetry().posHoldDenied);
        w.env.skyView = 1.0;
        w.run(20.0, 144.0);
        assertEquals(HoldMode.POSHOLD, w.sim.telemetry().holdMode);
        assertFalse(w.sim.telemetry().posHoldDenied);
    }

    @Test
    void sticksMoveTheDroneAndReleaseHoldsTheNewPosition() {
        FlatWorld w = hover(7L, 1.0);
        w.input.altHold = true;
        w.input.posHold = true;
        w.run(4.0, 144.0);
        double x0 = w.sim.body.px;
        double z0 = w.sim.body.pz;
        w.input.pitch = 0.5;
        w.run(2.0, 144.0);
        w.input.pitch = 0.0;
        w.run(4.0, 144.0);
        double x1 = w.sim.body.px;
        double z1 = w.sim.body.pz;
        assertTrue(horizontal(w, x0, z0) > 5.0, "sticks did not move the drone");
        double max = 0.0;
        for (int i = 0; i < 144 * 15; i++) {
            w.frame(DT);
            max = Math.max(max, horizontal(w, x1, z1));
        }
        assertTrue(max < 4.0, "new position not held: " + max);
        assertTrue(Math.hypot(w.sim.body.px - x0, w.sim.body.pz - z0) > 5.0, "drone flew back to the old position");
    }

    @Test
    void windPushesTheDroneOffAndItCreepsBack() {
        FlatWorld calm = hover(2L, 1.0);
        FlatWorld windy = hover(2L, 1.0);
        for (FlatWorld w : new FlatWorld[]{calm, windy}) {
            w.input.altHold = true;
            w.input.posHold = true;
            w.run(4.0, 144.0);
        }
        windy.env.windX = 6.0;
        double peak = 0.0;
        double late = 0.0;
        int lateSamples = 0;
        int frames = 144 * 30;
        for (int i = 0; i < frames; i++) {
            calm.frame(DT);
            windy.frame(DT);
            double excess = windy.sim.body.px - calm.sim.body.px;
            peak = Math.max(peak, excess);
            if (i >= frames - 144 * 5) {
                late += excess;
                lateSamples++;
            }
        }
        late /= lateSamples;
        assertTrue(peak > 0.5, "wind had no effect: " + peak);
        assertTrue(late < 0.6 * peak, "did not creep back: late " + late + " peak " + peak);
    }

    @Test
    void largeCompassErrorMakesTheDroneCircle() {
        FlatWorld calm = hover(9L, 1.0);
        FlatWorld bad = hover(9L, 1.0);
        bad.sim.nav.headingOffsetRad = 50.0 * SimMath.RAD;
        double calmTurns = turns(calm);
        double badTurns = turns(bad);
        assertTrue(Math.abs(badTurns) > 1.0, "no toilet bowl: " + badTurns);
        assertTrue(Math.abs(badTurns) > Math.abs(calmTurns) + 0.5);
    }

    private static double turns(FlatWorld w) {
        w.input.altHold = true;
        w.input.posHold = true;
        w.run(3.0, 144.0);
        double x0 = w.sim.nav.targetX;
        double z0 = w.sim.nav.targetZ;
        double angle = 0.0;
        double last = Double.NaN;
        for (int i = 0; i < 144 * 60; i++) {
            w.frame(DT);
            double dx = w.sim.body.px - x0;
            double dz = w.sim.body.pz - z0;
            double a = Math.atan2(dz, dx);
            if (!Double.isNaN(last) && Math.hypot(dx, dz) > 0.3) {
                angle += SimMath.wrapPi(a - last);
            }
            last = a;
        }
        return angle / (2.0 * Math.PI);
    }

    @Test
    void takeoffAndLandingWithAltHoldOn() {
        FlatWorld w = new FlatWorld(new DroneSim(DroneBuild.FREESTYLE, new FlightControllerConfig(), new SimTuning(), 12L));
        w.placeOnGround(0.0, 0.0, 0.0);
        w.input.mode = StabMode.ANGLE;
        w.input.altHold = true;
        w.arm();
        double ground = w.y;
        double top = ground;
        for (int i = 0; i < 144 * 5; i++) {
            w.frame(DT);
            top = Math.max(top, w.y);
        }
        assertEquals(HoldMode.ALTHOLD, w.sim.telemetry().holdMode);
        assertTrue(top - ground < 0.2, "hops on the ground: " + (top - ground));
        w.input.throttle = 0.7;
        w.run(4.0, 144.0);
        assertTrue(w.y - ground > 2.0, "no takeoff: " + (w.y - ground));
        w.input.throttle = 0.23;
        w.run(2.0, 144.0);
        double held = w.y;
        w.run(5.0, 144.0);
        assertTrue(Math.abs(w.y - held) < 1.5, "no hold after takeoff: " + (w.y - held));
        w.input.throttle = 0.0;
        w.run(15.0, 144.0);
        assertTrue(w.y - ground < 0.05, "did not land: " + (w.y - ground));
        assertTrue(w.sim.telemetry().armed);
        assertTrue(w.sim.telemetry().throttleOut < 0.1, "motors still working on the ground: " + w.sim.telemetry().throttleOut);
    }

    @Test
    void holdNeedsArmingAndYieldsToFailsafe() {
        FlatWorld w = FlatWorld.of(DroneBuild.FREESTYLE);
        w.placeOnGround(0.0, 0.0, 0.0);
        w.input.altHold = true;
        w.input.posHold = true;
        w.run(1.0, 144.0);
        assertEquals(HoldMode.OFF, w.sim.telemetry().holdMode);
        FlatWorld f = hover(8L, 1.0);
        f.input.altHold = true;
        f.run(1.0, 144.0);
        assertEquals(HoldMode.ALTHOLD, f.sim.telemetry().holdMode);
        f.input.linkOk = false;
        f.run(0.6, 144.0);
        assertEquals(HoldMode.OFF, f.sim.telemetry().holdMode);
        f.input.linkOk = true;
        f.input.altHold = false;
        f.run(0.2, 144.0);
        assertEquals(HoldMode.OFF, f.sim.telemetry().holdMode);
    }

    @Test
    void satellitesFollowSkyVisibility() {
        assertTrue(GpsReceiver.satellitesFor(1.0, 0.0) >= 16.0);
        assertTrue(GpsReceiver.satellitesFor(0.6, 0.0) >= 6.0);
        assertTrue(GpsReceiver.satellitesFor(0.3, 0.0) < GpsReceiver.FIX_SATELLITES);
        assertTrue(GpsReceiver.satellitesFor(0.0, 0.0) == 0.0);
        assertTrue(GpsReceiver.hdopFor(18, 1.0) < GpsReceiver.hdopFor(8, 1.0));
        assertTrue(GpsReceiver.hdopFor(8, 0.5) > GpsReceiver.hdopFor(8, 1.0));
        assertTrue(Barometer.dynamicPressureError(20.0) > Barometer.dynamicPressureError(5.0));
        assertTrue(Barometer.groundError(0.2, 1.0) < Barometer.groundError(2.0, 1.0));
        assertEquals(0.0, Barometer.groundError(Double.POSITIVE_INFINITY, 1.0));
    }

    @Test
    void settingsSanitizeAndCopy() {
        HoldSettings s = new HoldSettings();
        s.altP = Double.NaN;
        s.climbRateCms = 5000;
        s.minSatellites = 3.4;
        s.sanitize();
        assertEquals(50.0, s.altP);
        assertEquals(1000.0, s.climbRateCms);
        assertEquals(5.0, s.minSatellites);
        HoldSettings c = s.copy();
        assertEquals(s.climbRateCms, c.climbRateCms);
        FlightControllerConfig config = new FlightControllerConfig();
        config.hold = null;
        config.sanitize();
        assertTrue(config.hold != null);
        assertEquals(0.0, DroneSim.undeadband(0.0, 0.1));
        assertEquals(0.1, DroneSim.undeadband(RcProcessor.deadband(0.1, 0.05), 0.05), 1e-9);
    }
}
