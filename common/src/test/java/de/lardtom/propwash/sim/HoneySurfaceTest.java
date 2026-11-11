package de.lardtom.propwash.sim;

import de.lardtom.propwash.build.DroneBuild;
import de.lardtom.propwash.rescue.RescueWorld;
import de.lardtom.propwash.rescue.VoxelFlight;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HoneySurfaceTest {
    private static final double DT = 1.0 / 144.0;
    private static final long FRAME = Math.round(DT * 1e9);
    private static final int WALL_X = 2;
    private static final int FLOOR_TOP = 64;
    private static final VoxelFlight.SurfaceCells HONEY_WALL = (x, y, z) -> x >= WALL_X ? SurfaceResponse.STICKY : null;
    private static final VoxelFlight.SurfaceCells HONEY_FLOOR = (x, y, z) -> y == FLOOR_TOP - 1 ? SurfaceResponse.STICKY : null;
    private static final VoxelFlight.SurfaceCells STONE = (x, y, z) -> null;

    private static final RescueWorld ROOM = new RescueWorld() {
        @Override
        public boolean solid(int x, int y, int z) {
            return y < FLOOR_TOP || x >= WALL_X && y < FLOOR_TOP + 12;
        }

        @Override
        public int surfaceY(int x, int z) {
            return x >= WALL_X ? FLOOR_TOP + 12 : FLOOR_TOP;
        }

        @Override
        public int minY() {
            return 0;
        }

        @Override
        public int maxY() {
            return 128;
        }
    };

    private static final class Watch {
        boolean crash;
        boolean hard;
        boolean bump;
        boolean strike;
        int impacts;

        void collect(DroneSim s) {
            EventQueue q = s.events();
            this.crash |= q.contains(SimEventType.CRASH);
            this.hard |= q.contains(SimEventType.HARD_IMPACT);
            this.bump |= q.contains(SimEventType.BUMP);
            this.strike |= q.contains(SimEventType.PROP_STRIKE);
            this.impacts += s.impacts().size();
        }

        boolean quiet() {
            return !this.crash && !this.hard && !this.bump && !this.strike && this.impacts == 0;
        }
    }

    private static DroneSim sim() {
        return new DroneSim(DroneBuild.FREESTYLE, new FlightControllerConfig(), Tests.quietTuning(), 42L);
    }

    private static VoxelFlight atWall(VoxelFlight.SurfaceCells cells, double gap, double height) {
        DroneSim sim = sim();
        VoxelFlight flight = new VoxelFlight(sim, ROOM);
        flight.surfaces(cells);
        flight.place(WALL_X - 0.5 * sim.params().hitboxWidth - gap, FLOOR_TOP + height, 0.5, 0.0);
        return flight;
    }

    private static double speed(DroneSim s) {
        return Math.sqrt(s.body.vx * s.body.vx + s.body.vy * s.body.vy + s.body.vz * s.body.vz);
    }

    @Test
    void honeyWallStopsTheDroneWithoutReboundOrCrash() {
        VoxelFlight honey = atWall(HONEY_WALL, 0.03, 4.0);
        VoxelFlight stone = atWall(STONE, 0.03, 4.0);
        Watch honeyWatch = new Watch();
        Watch stoneWatch = new Watch();
        for (VoxelFlight f : new VoxelFlight[]{honey, stone}) {
            f.sim.body.vx = 10.0;
            f.sim.body.vz = 6.0;
        }
        double honeyLowest = Double.POSITIVE_INFINITY;
        double stoneLowest = Double.POSITIVE_INFINITY;
        for (int i = 0; i < 36; i++) {
            honey.frame(FRAME);
            stone.frame(FRAME);
            honeyWatch.collect(honey.sim);
            stoneWatch.collect(stone.sim);
            honeyLowest = Math.min(honeyLowest, honey.sim.body.vx);
            stoneLowest = Math.min(stoneLowest, stone.sim.body.vx);
        }
        assertTrue(honeyWatch.quiet(), "Ereignisse an der Honigwand");
        assertTrue(honeyLowest > -0.1, "Abprall von der Honigwand: vx " + honeyLowest);
        assertTrue(honey.sim.stickySides() != 0, "Drohne löst sich von der Honigwand");
        assertTrue(speed(honey.sim) < 1.0, "Honigwand bremst nicht: " + speed(honey.sim) + " m/s");
        assertTrue(stoneLowest < -0.3, "Steinwand ohne Abprall: vx " + stoneLowest);
        assertTrue(stoneWatch.crash, "Steinwand mit 10 m/s ohne Absturz");
    }

    @Test
    void droneSlidesSlowlyDownAHoneyWall() {
        VoxelFlight honey = atWall(HONEY_WALL, 0.001, 4.0);
        VoxelFlight stone = atWall(STONE, 0.001, 4.0);
        double honeyStart = honey.bottom();
        double stoneStart = stone.bottom();
        Watch watch = new Watch();
        double slowest = 0.0;
        for (int i = 0; i < 144; i++) {
            honey.frame(FRAME);
            stone.frame(FRAME);
            watch.collect(honey.sim);
            if (i >= 72) {
                slowest = Math.min(slowest, honey.sim.body.vy);
            }
        }
        double honeyFell = honeyStart - honey.bottom();
        double stoneFell = stoneStart - stone.bottom();
        assertTrue(watch.quiet(), "Ereignisse beim Rutschen");
        assertTrue(honeyFell > 0.2, "Drohne klebt fest statt zu rutschen: " + honeyFell + " m");
        assertTrue(honeyFell < 1.0, "Drohne rutscht zu schnell: " + honeyFell + " m in 1 s");
        assertTrue(slowest > -0.8 && honey.sim.body.vy < -0.2, "vy beim Rutschen " + honey.sim.body.vy);
        assertTrue(stoneFell > 3.0, "an Stein fällt die Drohne frei: " + stoneFell + " m");
    }

    private static VoxelFlight onFloor(VoxelFlight.SurfaceCells cells) {
        VoxelFlight flight = new VoxelFlight(sim(), ROOM);
        flight.surfaces(cells);
        flight.place(0.5, FLOOR_TOP, 0.5, 0.0);
        for (int i = 0; i < 72; i++) {
            flight.frame(FRAME);
        }
        flight.input.mode = StabMode.ANGLE;
        flight.arm();
        assertTrue(flight.sim.telemetry().armed);
        return flight;
    }

    private static double liftOff(VoxelFlight flight, double throttle, Watch watch) {
        double start = flight.bottom();
        flight.input.throttle = throttle;
        for (int i = 0; i < 432; i++) {
            flight.frame(FRAME);
            watch.collect(flight.sim);
            if (flight.bottom() - start > 0.05) {
                return (i + 1) * DT;
            }
        }
        return Double.POSITIVE_INFINITY;
    }

    @Test
    void honeyHoldsTheDroneForAboutOneSecondOnTakeoff() {
        VoxelFlight probe = onFloor(STONE);
        double gentle = Math.min(1.0, probe.sim.telemetry().hoverThrottleStick + 0.05);
        Watch stoneWatch = new Watch();
        double stone = liftOff(probe, gentle, stoneWatch);
        Watch honeyWatch = new Watch();
        VoxelFlight honey = onFloor(HONEY_FLOOR);
        assertEquals(MoveReport.STICKY_FLOOR, honey.sim.stickySides() & MoveReport.STICKY_FLOOR, "Honigboden nicht erkannt");
        double sticky = liftOff(honey, gentle, honeyWatch);
        Watch punchWatch = new Watch();
        double punch = liftOff(onFloor(HONEY_FLOOR), 1.0, punchWatch);
        assertTrue(stone < 0.35, "von Stein erst nach " + stone + " s");
        assertTrue(sticky > 0.8 && sticky < 1.3, "von Honig nach " + sticky + " s");
        assertTrue(punch < 0.35, "mit Vollgas von Honig erst nach " + punch + " s");
        assertTrue(stoneWatch.quiet() && honeyWatch.quiet() && punchWatch.quiet(), "Ereignisse beim Start");
        assertFalse(honey.sim.telemetry().grounded);
    }
}
