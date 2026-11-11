package de.lardtom.propwash.drone.client;

import de.lardtom.propwash.drone.client.CrashFallSelfTests.FallenPlacement;
import de.lardtom.propwash.drone.client.CrashFallSelfTests.FallenPlacement.Kind;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

import java.util.function.IntPredicate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

class CrashFallPlacementTest {
    private static final double DT = 0.05;
    private static final double FRICTION = 5.0;
    private static final double LOOK_ERROR = 0.3;
    private static final double WALL_X = 7.6;
    private static final int NEVER = -1;

    private record Outcome(Kind kind, int tick, int movedAt, Vec3 drone, Vec3 player) {
        String describe() {
            return kind + " bei Tick " + tick + ", versetzt bei Tick " + movedAt + ", Drohne " + drone + ", Spieler " + player;
        }
    }

    private static Outcome run(double speed, IntPredicate airborne, int latency) {
        FallenPlacement placement = new FallenPlacement();
        double x = WALL_X;
        double v = speed;
        Vec3 player = new Vec3(0.5, 0.0, 0.5);
        Vec3 target = null;
        int movedAt = -1;
        for (int tick = 1; tick <= 1200; tick++) {
            boolean arrived = target != null && latency != NEVER && tick >= movedAt + latency;
            if (arrived) {
                player = target;
            }
            Vec3 drone = new Vec3(x, 0.0, 0.5);
            FallenPlacement.Decision decision = placement.next(new FallenPlacement.Sample(tick, !airborne.test(tick), v, drone, 0.0,
                    arrived, player, LOOK_ERROR));
            switch (decision.kind()) {
                case MOVE -> {
                    target = decision.target();
                    movedAt = tick;
                }
                case PASS, NOT_LANDED, NOT_THERE -> {
                    return new Outcome(decision.kind(), tick, movedAt, drone, player);
                }
                case WAIT -> {
                }
            }
            x -= v * DT;
            v = Math.max(0.0, v - FRICTION * DT);
        }
        return fail("keine Entscheidung nach 1200 Ticks");
    }

    private static void assertBesideDrone(Outcome outcome) {
        assertEquals(Kind.PASS, outcome.kind(), outcome.describe());
        Vec3 view = new Vec3(outcome.drone().x - FallenPlacement.VIEW_BACK, 0.0, outcome.drone().z + FallenPlacement.VIEW_SIDE);
        assertTrue(outcome.player().distanceTo(view) < FallenPlacement.THERE, outcome.describe());
    }

    @Test
    void restingDroneIsPhotographedRightAway() {
        Outcome outcome = run(0.0, tick -> false, 1);
        assertBesideDrone(outcome);
        assertTrue(outcome.tick() <= 10, outcome.describe());
    }

    @Test
    void slidingDroneThatBouncesOnceStillGetsThePlayerBesideIt() {
        assertBesideDrone(run(2.0, tick -> tick == 2 || tick == 3, 2));
    }

    @Test
    void teleportArrivingLateUnderLoadDoesNotLoseASlidingDrone() {
        for (int latency = 1; latency <= 10; latency++) {
            assertBesideDrone(run(2.0, tick -> false, latency));
        }
    }

    @Test
    void lateLandingKeepsTheFullTimeForTheTeleport() {
        Outcome outcome = run(0.0, tick -> tick < 70, 3);
        assertBesideDrone(outcome);
        assertTrue(outcome.movedAt() >= 70, outcome.describe());
    }

    @Test
    void droneThatNeverLandsFailsAfterTheSettleTime() {
        Outcome outcome = run(0.0, tick -> true, 1);
        assertEquals(Kind.NOT_LANDED, outcome.kind(), outcome.describe());
        assertEquals(1 + FallenPlacement.SETTLE_TICKS, outcome.tick(), outcome.describe());
    }

    @Test
    void teleportThatNeverArrivesFailsAfterTheSyncTimeFromTheTeleport() {
        Outcome outcome = run(0.0, tick -> tick < 70, NEVER);
        assertEquals(Kind.NOT_THERE, outcome.kind(), outcome.describe());
        assertTrue(outcome.movedAt() >= 70, outcome.describe());
        assertEquals(outcome.movedAt() + FallenPlacement.SYNC_TICKS + 1, outcome.tick(), outcome.describe());
    }
}
