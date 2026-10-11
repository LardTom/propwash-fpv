package de.lardtom.propwash.rescue;

import de.lardtom.propwash.build.DroneBuild;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RescueRoutingTest {
    private static void assertLanded(RescueRun run, boolean disarmed, double groundY) {
        String info = run.describe();
        assertTrue(disarmed, "nicht gelandet/disarmed: " + info);
        assertEquals(RescuePhase.LANDED, run.autopilot.phase(), info);
        assertEquals(0, run.crashes, "Crash: " + info);
        assertEquals(0, run.hardImpacts, "harter Aufprall: " + info);
        assertEquals(groundY, run.flight.bottom(), 0.15, "nicht auf dem Boden: " + info);
    }

    @Test
    void homeInPondLandsOnDryGroundBeside() {
        VoxelWorld world = new VoxelWorld(0).mark(Ground.WATER, -3, -2, -3, 3, -1, 3);
        RescueRun run = new RescueRun(world, DroneBuild.FREESTYLE, new RescueSettings(), 0.5, 0.0, 0.5).launch(40.5, 5.0, 0.5, 0.0);
        boolean disarmed = run.runUntilDisarmed(120.0);
        assertLanded(run, disarmed, 0.0);
        double x = run.flight.x();
        double z = run.flight.z();
        assertFalse(x > -3.6 && x < 4.6 && z > -3.6 && z < 4.6, "im Teich gelandet: " + run.describe());
        assertTrue(run.distanceToHome() < 8.0, run.describe());
    }

    @Test
    void homeOnLavaLandsBesideThePool() {
        VoxelWorld world = new VoxelWorld(0).mark(Ground.LAVA, -2, -1, -2, 2, -1, 2);
        RescueRun run = new RescueRun(world, DroneBuild.FREESTYLE, new RescueSettings(), 0.5, 0.0, 0.5).launch(-30.5, 6.0, 10.5, 0.0);
        boolean disarmed = run.runUntilDisarmed(120.0);
        assertLanded(run, disarmed, 0.0);
        double x = run.flight.x();
        double z = run.flight.z();
        assertFalse(x > -2.6 && x < 3.6 && z > -2.6 && z < 3.6, "in der Lava gelandet: " + run.describe());
    }

    @Test
    void homeOnTreeTopLandsOnTheGroundBelow() {
        VoxelWorld world = new VoxelWorld(0).mark(Ground.FOLIAGE, -3, 4, -3, 3, 6, 3).fill(0, 0, 0, 0, 3, 0);
        RescueRun run = new RescueRun(world, DroneBuild.FREESTYLE, new RescueSettings(), 0.5, 7.0, 0.5).launch(40.5, 5.0, 0.5, 0.0);
        boolean disarmed = run.runUntilDisarmed(120.0);
        assertLanded(run, disarmed, 0.0);
        assertTrue(run.distanceToHome() < 13.0, run.describe());
    }

    @Test
    void fliesOutOfClosedHallThroughTheDoor() {
        VoxelWorld world = new VoxelWorld(0)
                .fill(14, 0, -8, 34, 7, 8).clear(15, 0, -7, 33, 6, 7)
                .clear(14, 0, 0, 14, 1, 0);
        RescueRun run = new RescueRun(world, DroneBuild.CINEWHOOP, new RescueSettings(), -6.5, 0.0, 0.5).launch(30.5, 2.0, -4.5, 0.0);
        boolean disarmed = run.runUntilDisarmed(150.0);
        String info = run.describe();
        assertTrue(disarmed, info);
        assertEquals(RescuePhase.LANDED, run.autopilot.phase(), info);
        assertEquals(0, run.crashes, info);
        assertTrue(run.distanceToHome() <= 1.5, info);
        assertEquals(0.0, run.flight.bottom(), 0.15, info);
    }

    @Test
    void wallUnderWideCeilingIsFlownAround() {
        VoxelWorld world = new VoxelWorld(0)
                .fill(-30, 7, -40, 70, 7, 40)
                .fill(20, 0, -12, 21, 6, 12);
        RescueRun run = new RescueRun(world, DroneBuild.FREESTYLE, new RescueSettings(), 0.5, 0.0, 0.5).launch(35.5, 2.0, 0.5, 0.0);
        boolean disarmed = run.runUntilDisarmed(150.0);
        String info = run.describe();
        assertTrue(disarmed, info);
        assertEquals(RescuePhase.LANDED, run.autopilot.phase(), info);
        assertEquals(0, run.crashes, info);
        assertEquals(0, run.hardImpacts, info);
        assertTrue(run.distanceToHome() <= 1.5, info);
    }

    @Test
    void distantRidgeIsClimbedBeforeTheCruise() {
        VoxelWorld world = new VoxelWorld(0).fill(-60, 0, -40, -58, 30, 40);
        RescueRun run = new RescueRun(world, DroneBuild.LONG_RANGE, new RescueSettings(), -100.5, 0.0, 0.5).launch(40.5, 3.0, 0.5, 0.0);
        double lowestCruise = Double.POSITIVE_INFINITY;
        double startX = run.flight.x();
        while (run.time < 150.0 && run.flight.sim.telemetry().armed) {
            run.runFor(0.25);
            if (startX - run.flight.x() > 20.0 && run.flight.x() > -50.0) {
                lowestCruise = Math.min(lowestCruise, run.flight.bottom());
            }
        }
        String info = run.describe();
        assertTrue(lowestCruise >= 31.0, "erst kurz vor dem Kamm gestiegen (tiefste Höhe " + lowestCruise + "): " + info);
        assertEquals(RescuePhase.LANDED, run.autopilot.phase(), info);
        assertEquals(0, run.crashes, info);
        assertTrue(run.distanceToHome() <= 1.5, info);
    }

    @Test
    void closedCaveGivesUpAndHoversWhenPiloted() {
        VoxelWorld world = new VoxelWorld(0).fill(20, 0, -8, 36, 8, 8).clear(21, 0, -7, 35, 7, 7);
        RescueRun run = new RescueRun(world, DroneBuild.FREESTYLE, new RescueSettings(), 0.5, 0.0, 0.5).launch(28.5, 2.0, 0.5, 0.0);
        run.runFor(60.0);
        String info = run.describe();
        assertTrue(run.autopilot.stuck(), info);
        assertEquals(RescuePhase.HOLD, run.autopilot.phase(), info);
        assertTrue(run.flight.sim.telemetry().armed, info);
        assertEquals(0, run.crashes, info);
    }

    @Test
    void closedCaveLandsInPlaceWhenAutonomous() {
        VoxelWorld world = new VoxelWorld(0).fill(20, 0, -8, 36, 8, 8).clear(21, 0, -7, 35, 7, 7);
        RescueRun run = new RescueRun(world, DroneBuild.FREESTYLE, new RescueSettings(), 0.5, 0.0, 0.5).autonomous().launch(28.5, 2.0, 0.5, 0.0);
        boolean disarmed = run.runUntilDisarmed(90.0);
        assertLanded(run, disarmed, 0.0);
        assertTrue(run.flight.x() > 21.0 && run.flight.x() < 36.0, run.describe());
    }

    @Test
    void landNowLandsBelowTheDrone() {
        RescueRun run = new RescueRun(new VoxelWorld(0), DroneBuild.RACER, new RescueSettings(), 0.5, 0.0, 0.5).launch(60.5, 4.0, 0.5, 0.0);
        run.runFor(6.0);
        double x = run.flight.x();
        run.autopilot.landNow(run.world);
        assertEquals(RescuePhase.LAND, run.autopilot.phase(), run.describe());
        boolean disarmed = run.runUntilDisarmed(60.0);
        assertLanded(run, disarmed, 0.0);
        assertTrue(Math.abs(run.flight.x() - x) < 6.0, run.describe());
    }

    @Test
    void autonomousWithoutAutoLandStillLands() {
        RescueSettings cfg = new RescueSettings();
        cfg.autoLand = false;
        RescueRun run = new RescueRun(new VoxelWorld(0), DroneBuild.FREESTYLE, cfg, 0.5, 0.0, 0.5).autonomous().launch(30.5, 3.0, -10.5, 0.0);
        boolean disarmed = run.runUntilDisarmed(90.0);
        assertLanded(run, disarmed, 0.0);
        assertTrue(run.distanceToHome() <= 1.5, run.describe());
    }

    @Test
    void routePlannerFindsWayAroundUShapeWithinBudget() {
        VoxelWorld world = new VoxelWorld(0)
                .fill(-4, 0, 6, 4, 8, 6)
                .fill(-4, 0, 0, -4, 8, 6)
                .fill(4, 0, 0, 4, 8, 6)
                .fill(-12, 9, -12, 12, 9, 12);
        RoutePlanner planner = new RoutePlanner();
        double homeX = 0.5;
        double homeZ = 30.5;
        RoutePlanner.Goal goal = new RoutePlanner.Goal() {
            @Override
            public double estimate(double x, double bottom, double z) {
                return Math.hypot(homeX - x, homeZ - z);
            }

            @Override
            public boolean reached(RescueWorld w, double x, double bottom, double z) {
                return z > 8.0 && WorldProbe.sweep(w, x, bottom, z, 0.25, 0.15, 0.1, 0.05, homeX - x, 0.0, homeZ - z, 8.0) >= 8.0 - 1e-6;
            }
        };
        long started = System.nanoTime();
        boolean found = planner.plan(world, 0.5, 2.3, 2.5, 0.25, 0.15, 0.3, 0.0, 8.0, 2.0, goal);
        long micros = (System.nanoTime() - started) / 1000L;
        assertTrue(found, "kein Weg aus dem U");
        assertTrue(planner.complete(), "Ziel nicht erreicht");
        assertTrue(planner.expansions() <= RoutePlanner.MAX_EXPANSIONS, "Budget überschritten: " + planner.expansions());
        assertTrue(planner.z(planner.size() - 1) > 8.0, "Ende nicht hinter dem U");
        double px = 0.5;
        double pb = 2.3;
        double pz = 2.5;
        for (int i = 0; i < planner.size(); i++) {
            double dx = planner.x(i) - px;
            double dy = planner.bottom(i) - pb;
            double dz = planner.z(i) - pz;
            double len = Math.sqrt(dx * dx + dy * dy + dz * dz);
            double free = WorldProbe.sweep(world, px, pb, pz, 0.25, 0.15, 0.0, 0.0, dx, dy, dz, len);
            assertTrue(free >= len - 1e-6, "Wegstück " + i + " geht durch einen Block");
            px = planner.x(i);
            pb = planner.bottom(i);
            pz = planner.z(i);
        }
        assertTrue(micros < 200_000L, "Planung zu langsam: " + micros + " µs");
    }

    @Test
    void spotQualityRejectsFluidsLeavesAndHazards() {
        VoxelWorld world = new VoxelWorld(0)
                .mark(Ground.WATER, 10, -1, 0, 11, -1, 1)
                .mark(Ground.FOLIAGE, 20, 3, 0, 21, 3, 1)
                .mark(Ground.HAZARD, 30, 0, 0, 30, 0, 0)
                .fill(40, 0, 0, 40, 0, 0);
        assertEquals(1, RescueAutopilot.spotQuality(world, 0.5, 0.5, 0.65, 10.0));
        assertEquals(0, RescueAutopilot.spotQuality(world, 11.0, 1.0, 0.65, 10.0));
        assertEquals(0, RescueAutopilot.spotQuality(world, 21.0, 1.0, 0.65, 10.0));
        assertEquals(-1, RescueAutopilot.spotQuality(world, 30.5, 0.5, 0.3, 10.0));
        assertEquals(0, RescueAutopilot.spotQuality(world, 40.0, 0.5, 0.65, 10.0));
    }
}
