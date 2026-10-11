package de.lardtom.propwash.rescue;

import com.mojang.serialization.JsonOps;
import de.lardtom.propwash.build.DroneBuild;
import de.lardtom.propwash.sim.DisarmReason;
import de.lardtom.propwash.sim.DroneSim;
import de.lardtom.propwash.sim.EventQueue;
import de.lardtom.propwash.sim.FlightControllerConfig;
import de.lardtom.propwash.sim.SimEventType;
import de.lardtom.propwash.sim.SimSnapshot;
import de.lardtom.propwash.sim.SimTuning;
import org.joml.Quaternionf;
import org.joml.Vector3d;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AutonomousHandoverTest {
    private static final double TICK = 0.05;
    private static final int SLICES = 4;

    private record Flown(int crashes, int hardImpacts, boolean disarmed, double seconds) {
    }

    private static Flown flyLikeServer(VoxelFlight flight, RescueAutopilot autopilot, VoxelWorld world, RescueSettings cfg, double maxSeconds) {
        return flyLikeServer(flight, autopilot, world, cfg, maxSeconds, () -> {
        }, 0.0);
    }

    private static Flown flyLikeServer(VoxelFlight flight, RescueAutopilot autopilot, VoxelWorld world, RescueSettings cfg, double maxSeconds,
                                       Runnable observer, double observeSeconds) {
        int crashes = 0;
        int hard = 0;
        double time = 0.0;
        double disarmAt = -1.0;
        while (time < maxSeconds) {
            for (int i = 0; i < SLICES; i++) {
                double dt = TICK / SLICES;
                autopilot.update(dt, flight.sim, flight.x(), flight.bottom(), flight.z(), world, cfg);
                flight.apply(autopilot);
                flight.input.armSwitch = true;
                if (autopilot.wantsDisarm()) {
                    if (disarmAt < 0.0) {
                        disarmAt = time;
                    } else if (time - disarmAt >= 0.5) {
                        flight.sim.forceDisarm(DisarmReason.FORCED);
                    }
                }
                flight.frame(Math.round(dt * 1e9));
                EventQueue events = flight.sim.events();
                crashes += events.count(SimEventType.CRASH);
                hard += events.count(SimEventType.HARD_IMPACT);
                time += dt;
                if (time < observeSeconds) {
                    observer.run();
                }
                if (!flight.sim.telemetry().armed) {
                    return new Flown(crashes, hard, true, time);
                }
            }
        }
        return new Flown(crashes, hard, false, time);
    }

    private static RescueRun handOver(RescueRun piloted, VoxelWorld world, DroneBuild build, RescueSettings cfg, double homeX, double homeY,
                                      double homeZ, RescueAutopilot[] out, VoxelFlight[] flightOut) {
        Vector3d v = new Vector3d();
        piloted.flight.sim.getVelocity(v);
        Quaternionf body = Handover.body(piloted.flight.sim);
        DroneSim sim = new DroneSim(build, new FlightControllerConfig(), new SimTuning(), 21L);
        VoxelFlight flight = new VoxelFlight(sim, world);
        Handover.place(sim, new SimSnapshot(), piloted.flight.x(), piloted.flight.bottom(), piloted.flight.z(), body, v.x, v.y, v.z, true);
        flight.adopt(piloted.flight.x(), piloted.flight.bottom(), piloted.flight.z());
        flight.input.armSwitch = true;
        RescueAutopilot autopilot = new RescueAutopilot();
        autopilot.setAutonomous(true);
        autopilot.start(sim, flight.x(), flight.bottom(), flight.z(), homeX, homeY, homeZ, world, ReturnPlan.of(UUID.randomUUID(),
                homeX, homeY, homeZ, false, cfg).settings());
        out[0] = autopilot;
        flightOut[0] = flight;
        return piloted;
    }

    @Test
    void pilotLeavesDuringCruiseAndTheServerFlightLandsAtHome() {
        VoxelWorld world = new VoxelWorld(0);
        RescueSettings cfg = new RescueSettings();
        RescueRun piloted = new RescueRun(world, DroneBuild.FREESTYLE, cfg, 0.5, 0.0, 0.5).launch(60.5, 4.0, 20.5, 1.0);
        piloted.runFor(9.0);
        assertTrue(piloted.autopilot.phase() == RescuePhase.CRUISE || piloted.autopilot.phase() == RescuePhase.CLIMB, piloted.describe());
        assertTrue(piloted.flight.bottom() > 8.0, piloted.describe());
        RescueAutopilot[] autopilot = new RescueAutopilot[1];
        VoxelFlight[] flight = new VoxelFlight[1];
        handOver(piloted, world, DroneBuild.FREESTYLE, cfg, 0.5, 0.0, 0.5, autopilot, flight);
        double startHeight = flight[0].bottom();
        double[] lowest = {startHeight};
        Flown flown = flyLikeServer(flight[0], autopilot[0], world, ReturnPlan.of(UUID.randomUUID(), 0.5, 0.0, 0.5, false, cfg).settings(), 120.0,
                () -> lowest[0] = Math.min(lowest[0], flight[0].bottom()), 1.0);
        assertTrue(startHeight - lowest[0] < 0.15, "sackt nach der Übergabe durch: " + startHeight + " → " + lowest[0]);
        String info = String.format("t=%.1f phase=%s pos=(%.2f, %.2f, %.2f) start=%.2f", flown.seconds(), autopilot[0].phase(),
                flight[0].x(), flight[0].bottom(), flight[0].z(), startHeight);
        assertTrue(flown.disarmed(), "nicht gelandet: " + info);
        assertEquals(RescuePhase.LANDED, autopilot[0].phase(), info);
        assertEquals(0, flown.crashes(), info);
        assertEquals(0, flown.hardImpacts(), info);
        assertTrue(Math.hypot(flight[0].x() - 0.5, flight[0].z() - 0.5) <= 1.5, info);
        assertEquals(0.0, flight[0].bottom(), 0.15, info);
    }

    @Test
    void handoverKeepsAttitudeAndVelocity() {
        RescueRun piloted = new RescueRun(new VoxelWorld(0), DroneBuild.RACER, new RescueSettings(), 0.5, 0.0, 0.5).launch(80.5, 6.0, 0.5, 2.0);
        piloted.runFor(11.0);
        Vector3d before = new Vector3d();
        piloted.flight.sim.getVelocity(before);
        assertTrue(before.length() > 2.0, "zu langsam für den Test: " + piloted.describe());
        Quaternionf body = Handover.body(piloted.flight.sim);
        DroneSim sim = new DroneSim(DroneBuild.RACER, new FlightControllerConfig(), new SimTuning(), 5L);
        Handover.place(sim, new SimSnapshot(), piloted.flight.x(), piloted.flight.bottom(), piloted.flight.z(), body,
                before.x, before.y, before.z, true);
        Vector3d after = new Vector3d();
        sim.getVelocity(after);
        assertTrue(sim.telemetry().armed, "nach der Übergabe nicht scharf");
        assertEquals(before.x, after.x, 1e-9);
        assertEquals(before.z, after.z, 1e-9);
        Quaternionf again = Handover.body(sim);
        assertTrue(Math.abs(again.dot(body)) > 0.99999F, "Lage weicht ab: " + body + " / " + again);
        SimSnapshot state = new SimSnapshot();
        sim.copyStateTo(state);
        for (double omega : state.rotorOmega) {
            assertEquals(sim.params().hoverOmega, omega, 1e-6, "Motoren nicht auf Schwebedrehzahl");
        }
        Vector3d pos = new Vector3d();
        sim.getPosition(pos);
        assertEquals(piloted.flight.x(), pos.x, 1e-9);
        assertEquals(piloted.flight.bottom() - sim.params().hitboxFootOffset, pos.y, 1e-9);
    }

    @Test
    void obstacleBetweenDroneAndHomeIsAvoidedAfterHandover() {
        VoxelWorld world = new VoxelWorld(0).fill(-30, 7, -40, 70, 7, 40).fill(20, 0, -12, 21, 6, 12);
        RescueSettings cfg = new RescueSettings();
        RescueRun piloted = new RescueRun(world, DroneBuild.FREESTYLE, cfg, 0.5, 0.0, 0.5).launch(35.5, 2.0, 0.5, 0.0);
        piloted.runFor(2.0);
        RescueAutopilot[] autopilot = new RescueAutopilot[1];
        VoxelFlight[] flight = new VoxelFlight[1];
        handOver(piloted, world, DroneBuild.FREESTYLE, cfg, 0.5, 0.0, 0.5, autopilot, flight);
        Flown flown = flyLikeServer(flight[0], autopilot[0], world, ReturnPlan.of(UUID.randomUUID(), 0.5, 0.0, 0.5, false, cfg).settings(), 150.0);
        String info = String.format("t=%.1f phase=%s pos=(%.2f, %.2f, %.2f)", flown.seconds(), autopilot[0].phase(),
                flight[0].x(), flight[0].bottom(), flight[0].z());
        assertTrue(flown.disarmed(), info);
        assertEquals(RescuePhase.LANDED, autopilot[0].phase(), info);
        assertEquals(0, flown.crashes(), info);
        assertTrue(Math.hypot(flight[0].x() - 0.5, flight[0].z() - 0.5) <= 1.5, info);
    }

    @Test
    void planSettingsAreSanitizedAndAlwaysLand() {
        RescueSettings wild = new RescueSettings();
        wild.altitude = 9999.0;
        wild.speed = Double.NaN;
        wild.descentRate = -3.0;
        wild.clearance = 100.0;
        wild.autoLand = false;
        ReturnPlan plan = ReturnPlan.of(UUID.randomUUID(), 1.0, 2.0, 3.0, true, wild);
        RescueSettings s = plan.settings();
        assertEquals(RescueSettings.MAX_ALTITUDE, s.altitude);
        assertEquals(8.0, s.speed);
        assertEquals(RescueSettings.MIN_DESCENT, s.descentRate);
        assertEquals(RescueSettings.MAX_CLEARANCE, s.clearance);
        assertTrue(s.autoLand);
        assertFalse(s.stickOverride);
        assertTrue(plan.followOwner());
        ReturnPlan later = plan.withTicks(40).withLandNow().withHome(4.0, 5.0, 6.0);
        assertTrue(later.landNow());
        assertEquals(40, later.ticks());
        assertEquals(5.0, later.homeY());
        assertEquals(5.0, later.horizontalDistance(7.0, 10.0), 1e-9);
    }

    @Test
    void planCodecRoundTrip() {
        ReturnPlan plan = ReturnPlan.of(UUID.fromString("2f1c3c71-2a2b-4c5d-9e8f-0a1b2c3d4e5f"), -12.25, 64.0, 300.5, false, new RescueSettings())
                .withTicks(123).withLandNow();
        ReturnPlan back = ReturnPlan.CODEC.parse(JsonOps.INSTANCE, ReturnPlan.CODEC.encodeStart(JsonOps.INSTANCE, plan).getOrThrow()).getOrThrow();
        assertEquals(plan, back);
    }

    @Test
    void budgetLimitsServerAndOwner() {
        AutonomyBudget budget = new AutonomyBudget(3, 2);
        UUID a = UUID.randomUUID();
        UUID b = UUID.randomUUID();
        Object d1 = new Object();
        Object d2 = new Object();
        Object d3 = new Object();
        Object d4 = new Object();
        assertEquals(AutonomyBudget.Admission.ADMITTED, budget.admit(d1, a));
        assertEquals(AutonomyBudget.Admission.ADMITTED, budget.admit(d2, a));
        assertEquals(AutonomyBudget.Admission.OWNER_FULL, budget.admit(d3, a));
        assertEquals(AutonomyBudget.Admission.ADMITTED, budget.admit(d3, b));
        assertEquals(AutonomyBudget.Admission.SERVER_FULL, budget.admit(d4, b));
        assertEquals(AutonomyBudget.Admission.ADMITTED, budget.admit(d1, a));
        budget.release(d2);
        assertEquals(AutonomyBudget.Admission.ADMITTED, budget.admit(d4, b));
        assertEquals(3, budget.size());
        assertTrue(budget.holds(d4));
        assertEquals(AutonomyBudget.SERVER_LIMIT, 8);
    }

    @Test
    void serverFlightCostStaysSmall() {
        VoxelWorld world = new VoxelWorld(0);
        RescueSettings cfg = new RescueSettings();
        DroneSim sim = new DroneSim(DroneBuild.FREESTYLE, new FlightControllerConfig(), new SimTuning(), 9L);
        VoxelFlight flight = new VoxelFlight(sim, world);
        flight.place(40.5, 5.0, 0.5, 0.0);
        flight.arm();
        RescueAutopilot autopilot = new RescueAutopilot();
        autopilot.setAutonomous(true);
        autopilot.start(sim, flight.x(), flight.bottom(), flight.z(), 0.5, 0.0, 0.5, world, cfg);
        flyLikeServer(flight, autopilot, world, cfg, 3.0);
        long started = System.nanoTime();
        Flown flown = flyLikeServer(flight, autopilot, world, cfg, 20.0);
        double perTickMicros = (System.nanoTime() - started) / 1000.0 / Math.max(1.0, flown.seconds() / TICK);
        assertTrue(perTickMicros < 2000.0, "ein Server-Tick kostet " + perTickMicros + " µs");
    }
}
