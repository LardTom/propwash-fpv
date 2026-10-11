package de.lardtom.propwash.rescue;

import de.lardtom.propwash.build.DroneBuild;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RescueAutopilotTest {
    private static final double LANDING_TOLERANCE = 1.5;

    private static void assertLandedAtHome(RescueRun run, boolean disarmed) {
        String info = run.describe();
        assertTrue(disarmed, "nicht gelandet/disarmed: " + info);
        assertEquals(RescuePhase.LANDED, run.autopilot.phase(), info);
        assertEquals(0, run.crashes, "Crash: " + info);
        assertEquals(0, run.hardImpacts, "harter Aufprall: " + info);
        assertTrue(run.distanceToHome() <= LANDING_TOLERANCE, "zu weit vom Homepoint: " + info);
        assertEquals(run.homeY, run.flight.bottom(), 0.15, "nicht auf dem Boden: " + info);
    }

    @Test
    void flatGroundReturnsAndLandsForEveryPreset() {
        for (DroneBuild build : DroneBuild.PRESETS) {
            RescueRun run = new RescueRun(new VoxelWorld(0), build, new RescueSettings(), 0.5, 0.0, 0.5).launch(40.5, 5.0, 0.5, 1.0);
            boolean disarmed = run.runUntilDisarmed(90.0);
            assertLandedAtHome(run, disarmed);
            assertTrue(run.visited.contains(RescuePhase.CLIMB) && run.visited.contains(RescuePhase.CRUISE)
                    && run.visited.contains(RescuePhase.LAND), build + ": " + run.describe());
            assertTrue(run.maxBottom >= 14.0, build + " zu niedrig: " + run.describe());
        }
    }

    @Test
    void climbsOverTwentyFiveBlockWall() {
        VoxelWorld world = new VoxelWorld(0).fill(18, 0, -30, 19, 24, 30);
        RescueRun run = new RescueRun(world, DroneBuild.FREESTYLE, new RescueSettings(), 0.5, 0.0, 0.5).launch(40.5, 5.0, 0.5, 0.0);
        boolean disarmed = run.runUntilDisarmed(120.0);
        assertLandedAtHome(run, disarmed);
        assertTrue(run.maxBottom > 25.5, "nicht über die Mauer: " + run.describe());
    }

    @Test
    void escapesTreeCanopyWithTrunkTowardHome() {
        VoxelWorld world = treeWorld();
        RescueRun run = new RescueRun(world, DroneBuild.FREESTYLE, new RescueSettings(), 0.5, 0.0, 0.5).launch(32.5, 1.5, 0.5, 0.0);
        boolean disarmed = run.runUntilDisarmed(120.0);
        assertLandedAtHome(run, disarmed);
        assertTrue(run.visited.contains(RescuePhase.ESCAPE), run.describe());
    }

    @Test
    void takesOffUnderCanopyFromTheGround() {
        VoxelWorld world = treeWorld();
        RescueRun run = new RescueRun(world, DroneBuild.CINEWHOOP, new RescueSettings(), 0.5, 0.0, 0.5).launch(32.5, 0.0, 2.5, 0.0);
        boolean disarmed = run.runUntilDisarmed(120.0);
        assertLandedAtHome(run, disarmed);
    }

    @Test
    void closedCaveHoldsWithoutCrashing() {
        VoxelWorld world = new VoxelWorld(0).fill(20, 0, -8, 36, 8, 8).clear(21, 0, -7, 35, 7, 7);
        RescueRun run = new RescueRun(world, DroneBuild.FREESTYLE, new RescueSettings(), 0.5, 0.0, 0.5).launch(28.5, 2.0, 0.5, 0.0);
        run.runFor(30.0);
        String info = run.describe();
        assertEquals(RescuePhase.HOLD, run.autopilot.phase(), info);
        assertTrue(run.flight.sim.telemetry().armed, info);
        assertEquals(0, run.crashes, info);
        assertEquals(0, run.hardImpacts, info);
        assertFalse(run.visited.contains(RescuePhase.LAND), info);
        assertTrue(run.flight.x() > 21.0 && run.flight.x() < 36.0 && run.flight.bottom() > 0.3 && run.flight.bottom() < 8.0, info);
    }

    @Test
    void closeToHomeLandsDirectly() {
        RescueRun run = new RescueRun(new VoxelWorld(0), DroneBuild.FREESTYLE, new RescueSettings(), 0.5, 0.0, 0.5).launch(3.5, 2.0, 2.5, 2.0);
        boolean disarmed = run.runUntilDisarmed(40.0);
        assertLandedAtHome(run, disarmed);
        assertFalse(run.visited.contains(RescuePhase.CLIMB), run.describe());
        assertTrue(run.maxBottom < 3.0, run.describe());
    }

    @Test
    void withoutAutoLandHoldsAboveHome() {
        RescueSettings cfg = new RescueSettings();
        cfg.autoLand = false;
        RescueRun run = new RescueRun(new VoxelWorld(0), DroneBuild.RACER, cfg, 0.5, 0.0, 0.5).launch(30.5, 3.0, -10.5, 0.0);
        run.runFor(45.0);
        String info = run.describe();
        assertEquals(RescuePhase.HOLD, run.autopilot.phase(), info);
        assertTrue(run.distanceToHome() < 1.5, info);
        assertTrue(run.flight.bottom() > 12.0, info);
        assertEquals(0, run.crashes, info);
    }

    @Test
    void worksWithObstacleAvoidanceDisabled() {
        RescueSettings cfg = new RescueSettings();
        cfg.obstacleAvoidance = false;
        cfg.altitude = 8.0;
        cfg.speed = 12.0;
        RescueRun run = new RescueRun(new VoxelWorld(0), DroneBuild.LONG_RANGE, cfg, 0.5, 0.0, 0.5).launch(-60.5, 4.0, 45.5, 3.0);
        boolean disarmed = run.runUntilDisarmed(90.0);
        assertLandedAtHome(run, disarmed);
    }

    @Test
    void landsPreciselyInCrosswindWithGusts() {
        RescueRun run = new RescueRun(new VoxelWorld(0), DroneBuild.FREESTYLE, new RescueSettings(), 0.5, 0.0, 0.5).launch(-35.5, 6.0, 20.5, 0.5);
        run.flight.wind(0.0, 0.0, 4.0, 0.5);
        boolean disarmed = run.runUntilDisarmed(90.0);
        assertLandedAtHome(run, disarmed);
    }

    @Test
    void worksAtLowAndHighJitteryFrameRates() {
        double[] rates = {24.0, 144.0};
        for (double fps : rates) {
            VoxelWorld world = new VoxelWorld(0).fill(18, 0, -30, 19, 20, 30);
            RescueRun run = new RescueRun(world, DroneBuild.RACER, new RescueSettings(), 0.5, 0.0, 0.5)
                    .timing(fps, 0.4).launch(40.5, 5.0, 3.5, 0.0);
            boolean disarmed = run.runUntilDisarmed(120.0);
            assertLandedAtHome(run, disarmed);
        }
    }

    @Test
    void fliesOutFromUnderWideOverhang() {
        VoxelWorld world = new VoxelWorld(0).fill(10, 5, -12, 34, 5, 12);
        RescueRun run = new RescueRun(world, DroneBuild.FREESTYLE, new RescueSettings(), 0.5, 0.0, 0.5).launch(22.5, 1.5, 0.5, 0.0);
        boolean disarmed = run.runUntilDisarmed(120.0);
        assertLandedAtHome(run, disarmed);
    }

    @Test
    void homeInsideClosedCaveUsesDirectRoute() {
        VoxelWorld world = new VoxelWorld(0).fill(-8, 0, -8, 30, 7, 8).clear(-7, 0, -7, 29, 6, 7);
        RescueRun run = new RescueRun(world, DroneBuild.CINEWHOOP, new RescueSettings(), 0.5, 0.0, 0.5).launch(24.5, 2.0, 3.5, 0.0);
        boolean disarmed = run.runUntilDisarmed(90.0);
        assertLandedAtHome(run, disarmed);
        assertTrue(run.maxBottom < 6.0, run.describe());
    }

    @Test
    void homeOnHillBehindValley() {
        VoxelWorld world = new VoxelWorld(0).fill(-6, 0, -6, 6, 11, 6);
        RescueRun run = new RescueRun(world, DroneBuild.LONG_RANGE, new RescueSettings(), 0.5, 12.0, 0.5).launch(60.5, 3.0, -30.5, 0.0);
        boolean disarmed = run.runUntilDisarmed(120.0);
        assertLandedAtHome(run, disarmed);
    }

    @Test
    void homeUnderTreeLandsBesideTheCanopyOnTheGround() {
        VoxelWorld world = new VoxelWorld(0).fill(-4, 4, -4, 4, 6, 4).fill(0, 0, 0, 0, 5, 0);
        RescueRun run = new RescueRun(world, DroneBuild.FREESTYLE, new RescueSettings(), 1.5, 0.0, 1.5).launch(40.5, 5.0, 0.5, 0.0);
        boolean disarmed = run.runUntilDisarmed(120.0);
        String info = run.describe();
        assertTrue(disarmed, info);
        assertEquals(RescuePhase.LANDED, run.autopilot.phase(), info);
        assertEquals(0, run.crashes, info);
        assertEquals(0.0, run.flight.bottom(), 0.15, "auf der Baumkrone gelandet: " + info);
        assertTrue(run.distanceToHome() < 8.0, info);
    }

    private static VoxelWorld treeWorld() {
        return new VoxelWorld(0).fill(26, 4, -4, 34, 6, 4).fill(30, 0, 0, 30, 5, 0);
    }
}
