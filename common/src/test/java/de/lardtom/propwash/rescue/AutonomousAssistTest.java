package de.lardtom.propwash.rescue;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import de.lardtom.propwash.api.drone.AutonomyEnd;
import de.lardtom.propwash.api.drone.AutonomyOptions;
import de.lardtom.propwash.api.flight.AssistOutput;
import de.lardtom.propwash.api.flight.FlightAssist;
import de.lardtom.propwash.api.flight.FlightState;
import de.lardtom.propwash.build.DroneBuild;
import de.lardtom.propwash.server.EdgeFence;
import de.lardtom.propwash.server.rules.RuleSpec;
import de.lardtom.propwash.server.rules.WorldRuleSpecs;
import de.lardtom.propwash.sim.DisarmReason;
import de.lardtom.propwash.sim.DroneSim;
import de.lardtom.propwash.sim.FlightControllerConfig;
import de.lardtom.propwash.sim.SimSnapshot;
import de.lardtom.propwash.sim.SimTuning;
import de.lardtom.propwash.zone.Zone;
import de.lardtom.propwash.zone.ZoneRelease;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.resources.Identifier;
import org.joml.Quaternionf;
import org.joml.Vector3d;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

class AutonomousAssistTest {
    private static final Identifier HOLD = Identifier.fromNamespaceAndPath("propwash_example", "hold");
    private static final Identifier FOLLOW = Identifier.fromNamespaceAndPath("propwash_example", "follow");
    private static final long SLICE_NANOS = 12_500_000L;
    private static final double SLICE = SLICE_NANOS * 1.0E-9;
    private static final double WALK_SPEED = 4.3;

    private static final class Run {
        final RescueWorld world;
        final DroneSim sim;
        final VoxelFlight flight;
        final ServerFlightAssist logic = new ServerFlightAssist();
        double time;
        double disarmAt = -1.0;
        AutonomyEnd ended;

        Run(DroneBuild build, SimTuning tuning, RescueWorld world) {
            this.world = world;
            this.sim = new DroneSim(build, new FlightControllerConfig(), tuning, 11L);
            this.flight = new VoxelFlight(this.sim, world);
        }

        Run(DroneBuild build) {
            this(build, new SimTuning(), new VoxelWorld(0));
        }

        Run hovering(double x, double bottom, double z, double vx, double vz) {
            Handover.place(this.sim, new SimSnapshot(), x, bottom, z, new Quaternionf(), vx, 0.0, vz, true);
            this.flight.adopt(x, bottom, z);
            this.flight.input.armSwitch = true;
            return this;
        }

        Run onGround(double x, double z) {
            this.flight.place(x, 0.0, z, 0.0);
            this.flight.arm();
            assertTrue(this.sim.telemetry().armed, "am Boden nicht scharf");
            return this;
        }

        void tick() {
            for (int i = 0; i < 4; i++) {
                this.logic.update(SLICE, this.sim, this.flight.x(), this.flight.bottom(), this.flight.z(), this.world, this.flight.input);
                if (this.logic.wantsDisarm()) {
                    if (this.disarmAt < 0.0) {
                        this.disarmAt = this.time;
                    } else if (this.time - this.disarmAt >= 0.5) {
                        this.sim.forceDisarm(DisarmReason.FORCED);
                    }
                }
                this.flight.frame(SLICE_NANOS);
                this.time += SLICE;
                if (!this.sim.telemetry().armed) {
                    break;
                }
            }
            AutonomyEnd end = this.logic.consumeEnded();
            if (end != null && this.ended == null) {
                this.ended = end;
            }
        }

        boolean flyFor(double seconds) {
            double end = this.time + seconds;
            while (this.time < end) {
                this.tick();
                if (!this.sim.telemetry().armed) {
                    this.logic.disarmed(this.logic.landed());
                    AutonomyEnd last = this.logic.consumeEnded();
                    if (last != null && this.ended == null) {
                        this.ended = last;
                    }
                    return true;
                }
            }
            return false;
        }

        double comY() {
            Vector3d p = new Vector3d();
            this.sim.getPosition(p);
            return p.y;
        }

        String describe() {
            return String.format("t=%.1f phase=%s pos=(%.2f, %.2f, %.2f) armed=%s ended=%s charge=%.3f", this.time, this.logic.phase(),
                    this.flight.x(), this.flight.bottom(), this.flight.z(), this.sim.telemetry().armed, this.ended,
                    this.sim.telemetry().batteryCharge);
        }
    }

    private static final class Hold implements FlightAssist {
        double x = Double.NaN;
        double y;
        double z;
        boolean land;
        boolean home;
        RuntimeException fail;

        @Override
        public void update(FlightState state, AssistOutput output) {
            if (this.fail != null) {
                throw this.fail;
            }
            if (Double.isNaN(this.x)) {
                this.x = state.x();
                this.y = state.y();
                this.z = state.z();
            }
            output.holdPosition(this.x, this.z);
            output.holdAltitude(this.y);
            output.label("HOLD");
            if (this.land) {
                output.land();
            }
            if (this.home) {
                output.requestReturnHome();
            }
        }
    }

    private static final class Follow implements FlightAssist {
        static final double DISTANCE = 2.5;
        static final double GAIN = 1.2;
        final double[] owner;
        double lastTime = Double.NaN;
        double lastX;
        double lastZ;
        double vx;
        double vz;
        double height = Double.NaN;

        Follow(double[] owner) {
            this.owner = owner;
        }

        @Override
        public void update(FlightState state, AssistOutput output) {
            double t = state.time();
            if (Double.isNaN(this.height)) {
                this.height = state.y() - this.owner[1];
            }
            if (!Double.isNaN(this.lastTime) && t - this.lastTime >= 0.05) {
                double dt = t - this.lastTime;
                this.vx += 0.5 * ((this.owner[0] - this.lastX) / dt - this.vx);
                this.vz += 0.5 * ((this.owner[2] - this.lastZ) / dt - this.vz);
            }
            if (Double.isNaN(this.lastTime) || t - this.lastTime >= 0.05) {
                this.lastTime = t;
                this.lastX = this.owner[0];
                this.lastZ = this.owner[2];
            }
            double dx = state.x() - this.owner[0];
            double dz = state.z() - this.owner[2];
            double d = Math.hypot(dx, dz);
            double tx = this.owner[0] + (d > 1e-3 ? dx / d : 0.0) * DISTANCE;
            double tz = this.owner[2] + (d > 1e-3 ? dz / d : 1.0) * DISTANCE;
            output.velocity(this.vx + GAIN * (tx - state.x()), this.vz + GAIN * (tz - state.z()));
            output.maxSpeed(12.0);
            output.holdAltitude(this.owner[1] + this.height);
            output.heading(Math.toDegrees(Math.atan2(-dx, dz)));
            output.label("FOLLOW");
        }
    }

    @Test
    void holdKeepsTheDroneWithinHalfAMetreForTenSeconds() {
        Run run = new Run(DroneBuild.FREESTYLE).hovering(0.5, 6.0, 0.5, 0.0, 0.0);
        run.logic.startInFlight(run.sim, run.flight.x(), run.flight.bottom(), run.flight.z(), new Hold(), AutonomyOptions.of(HOLD));
        double worst = 0.0;
        for (int tick = 0; tick < 200; tick++) {
            run.tick();
            worst = Math.max(worst, Math.sqrt(sq(run.flight.x() - 0.5) + sq(run.flight.bottom() - 6.0) + sq(run.flight.z() - 0.5)));
        }
        assertTrue(worst <= 0.5, "Halten weicht ab: " + worst + " " + run.describe());
        assertEquals(ServerFlightAssist.Phase.ASSIST, run.logic.phase(), run.describe());
        assertEquals("HOLD", run.logic.label());
        assertNull(run.ended, run.describe());
    }

    @Test
    void serverAssistSeesAddonSwitchesAndAxes() {
        Run run = new Run(DroneBuild.FREESTYLE).hovering(0.5, 6.0, 0.5, 0.0, 0.0);
        Hold hold = new Hold();
        String[] seen = new String[1];
        FlightAssist probe = (state, output) -> {
            seen[0] = state.addonSwitch("gimbalmod:ghost") + " " + state.addonAxis("gimbalmod:tilt");
            hold.update(state, output);
        };
        run.logic.startInFlight(run.sim, run.flight.x(), run.flight.bottom(), run.flight.z(), probe, AutonomyOptions.of(HOLD));
        run.tick();
        assertEquals("false 0.0", seen[0]);
        run.sim.setAddonInput("gimbalmod:ghost"::equals, id -> "gimbalmod:tilt".equals(id) ? -0.25 : 0.0);
        run.tick();
        assertEquals("true -0.25", seen[0]);
        run.sim.setAddonInput(id -> false, id -> 0.0);
        run.tick();
        assertEquals("false 0.0", seen[0]);
        assertEquals(ServerFlightAssist.Phase.ASSIST, run.logic.phase(), run.describe());
    }

    @Test
    void followStaysWithinFiveMetresWhileTheOwnerWalksTwentyBlocks() {
        double[] owner = {0.5, 0.0, -2.5};
        Run run = new Run(DroneBuild.FREESTYLE).hovering(0.5, 4.0, 0.5, 0.0, 0.0);
        run.logic.startInFlight(run.sim, run.flight.x(), run.flight.bottom(), run.flight.z(), new Follow(owner), AutonomyOptions.of(FOLLOW));
        double walked = 0.0;
        double worst = 0.0;
        for (int tick = 0; tick < 400; tick++) {
            if (tick >= 20 && walked < 20.0) {
                double step = Math.min(WALK_SPEED * 0.05, 20.0 - walked);
                owner[2] -= step;
                walked += step;
            }
            run.tick();
            worst = Math.max(worst, Math.hypot(run.flight.x() - owner[0], run.flight.z() - owner[2]));
        }
        assertEquals(20.0, walked, 1e-9);
        assertTrue(worst <= 5.0, "Folgen zu weit weg: " + worst + " " + run.describe());
        assertEquals(4.0, run.flight.bottom(), 0.3, run.describe());
    }

    @Test
    void handoverAfterThePilotLeavesKeepsTheHeight() {
        for (double speed : new double[]{0.0, 4.0, 9.0}) {
            VoxelWorld world = new VoxelWorld(0);
            RescueSettings cfg = new RescueSettings();
            RescueRun piloted = new RescueRun(world, DroneBuild.FREESTYLE, cfg, 0.5, 0.0, 0.5).launch(80.5, 7.0, 0.5, 1.0);
            piloted.runFor(speed == 0.0 ? 1.0 : speed == 4.0 ? 5.0 : 9.0);
            Vector3d v = new Vector3d();
            piloted.flight.sim.getVelocity(v);
            Quaternionf body = Handover.body(piloted.flight.sim);
            Run run = new Run(DroneBuild.FREESTYLE, new SimTuning(), world);
            Handover.place(run.sim, new SimSnapshot(), piloted.flight.x(), piloted.flight.bottom(), piloted.flight.z(), body, v.x, v.y, v.z, true);
            run.flight.adopt(piloted.flight.x(), piloted.flight.bottom(), piloted.flight.z());
            double start = run.flight.bottom();
            Follow follow = new Follow(new double[]{piloted.flight.x(), 0.0, piloted.flight.z() + 3.0});
            run.logic.startInFlight(run.sim, run.flight.x(), run.flight.bottom(), run.flight.z(), follow, AutonomyOptions.of(FOLLOW));
            double lowest = start;
            for (int tick = 0; tick < 60; tick++) {
                run.tick();
                lowest = Math.min(lowest, run.flight.bottom());
            }
            assertTrue(start - lowest <= 0.3, "sackt nach der Übergabe ab (" + v.length() + " m/s): " + start + " → " + lowest);
            assertTrue(run.sim.telemetry().armed, run.describe());
        }
    }

    private static final class Takeover {
        static final long FRAME_NANOS = 16_666_667L;
        final DroneSim sim;
        final VoxelFlight flight;
        final FlightControllerConfig config = new FlightControllerConfig();
        boolean pending;
        float throttle = Float.NaN;

        Takeover(Run server, boolean seamless, FlightAssist assist) {
            this(server, seamless, assist, 13L);
        }

        Takeover(Run server, boolean seamless, FlightAssist assist, long seed) {
            Vector3d v = new Vector3d();
            server.sim.getVelocity(v);
            Quaternionf body = Handover.body(server.sim);
            this.sim = new DroneSim(DroneBuild.FREESTYLE, this.config, new SimTuning(), seed);
            this.flight = new VoxelFlight(this.sim, server.world);
            if (seamless) {
                PilotTakeover.place(this.sim, new SimSnapshot(), server.flight.x(), server.flight.bottom(), server.flight.z(), body, v.x, v.y, v.z);
            } else {
                Handover.place(this.sim, new SimSnapshot(), server.flight.x(), server.flight.bottom(), server.flight.z(), body, 0.0, 0.0, 0.0, false);
            }
            this.flight.adopt(server.flight.x(), server.flight.bottom(), server.flight.z());
            this.sim.setAssist(assist);
            this.flight.input.armSwitch = seamless;
            this.flight.input.throttle = seamless ? PilotTakeover.throttle(false, false, this.sim.telemetry().hoverThrottleStick, this.config.deadband3d) : 0.0;
            this.pending = seamless;
        }

        double lowestOver(double seconds) {
            double lowest = this.flight.bottom();
            for (double t = 0.0; t < seconds; t += FRAME_NANOS * 1.0E-9) {
                this.flight.frame(FRAME_NANOS);
                if (this.pending && PilotTakeover.resolved(this.sim)) {
                    this.pending = false;
                    this.throttle = PilotTakeover.throttle(this.sim.assistOwnsHold(), false, this.sim.telemetry().hoverThrottleStick,
                            this.config.deadband3d);
                    this.flight.input.throttle = this.throttle;
                }
                lowest = Math.min(lowest, this.flight.bottom());
            }
            return lowest;
        }
    }

    private static final class CenterClimb implements FlightAssist {
        @Override
        public void update(FlightState state, AssistOutput output) {
            if (state.armed()) {
                output.climb((state.stickThrottle() - 0.5) * 4.0);
            }
        }
    }

    private static final class Cruise implements FlightAssist {
        final double vx;
        final double climb;
        double y = Double.NaN;

        Cruise(double vx, double climb) {
            this.vx = vx;
            this.climb = climb;
        }

        @Override
        public void update(FlightState state, AssistOutput output) {
            if (Double.isNaN(this.y)) {
                this.y = state.y();
            }
            output.velocity(this.vx, 0.0);
            output.maxSpeed(12.0);
            if (this.climb != 0.0) {
                output.climb(this.climb);
            } else {
                output.holdAltitude(this.y);
            }
        }
    }

    private static final class HoldEstimate implements FlightAssist {
        double y = Double.NaN;
        double x;
        double z;
        int calls;
        boolean firstArmed;
        double firstTrueY;
        double firstTrueVy;
        double firstPitch;
        double firstRoll;
        double firstHeading;
        double firstY;
        double firstVy;
        double secondY = Double.NaN;

        @Override
        public void update(FlightState state, AssistOutput output) {
            if (this.calls == 0) {
                this.firstArmed = state.armed();
                this.firstTrueY = state.y();
                this.firstTrueVy = state.vy();
                this.firstPitch = state.pitchDeg();
                this.firstRoll = state.rollDeg();
                this.firstHeading = state.headingDeg();
                this.firstY = state.estimatedY();
                this.firstVy = state.estimatedVy();
                this.y = state.estimatedY();
                this.x = state.x();
                this.z = state.z();
            } else if (this.calls == 1) {
                this.secondY = state.estimatedY();
            }
            this.calls++;
            output.holdPosition(this.x, this.z);
            output.holdAltitude(this.y);
        }
    }

    private static Run hoveringServerFlight(double speed) {
        Run server = new Run(DroneBuild.FREESTYLE).hovering(0.5, 5.0, 0.5, speed, 0.0);
        server.logic.startInFlight(server.sim, server.flight.x(), server.flight.bottom(), server.flight.z(), new Hold(), AutonomyOptions.of(HOLD));
        server.flyFor(2.0);
        return server;
    }

    private static Run cruisingServerFlight(double speed, double climb) {
        Run server = new Run(DroneBuild.FREESTYLE).hovering(0.5, 5.0, 0.5, speed, 0.0);
        server.logic.startInFlight(server.sim, server.flight.x(), server.flight.bottom(), server.flight.z(), new Cruise(speed, climb),
                AutonomyOptions.of(FOLLOW));
        server.flyFor(3.0);
        return server;
    }

    @Test
    void takeoverAssistSeesHeightAndClimbEstimateOnItsFirstCall() {
        Run[] servers = {hoveringServerFlight(0.0), cruisingServerFlight(6.0, 0.0), cruisingServerFlight(0.0, 1.5)};
        String[] labels = {"Schweben", "6 m/s", "Steigen 1,5 m/s"};
        for (int k = 0; k < servers.length; k++) {
            ServerFlightState truth = new ServerFlightState();
            truth.read(servers[k].sim, 0.0);
            if (k == 2) {
                assertTrue(truth.vy() > 1.0, "Server-Flug steigt nicht: " + truth.vy());
            }
            for (long seed = 1; seed <= 200; seed++) {
                HoldEstimate assist = new HoldEstimate();
                Takeover pilot = new Takeover(servers[k], true, assist, seed * 104729L);
                pilot.lowestOver(0.05);
                String at = labels[k] + ", Seed " + seed + ": wahr y=" + assist.firstTrueY + " vy=" + assist.firstTrueVy + ", geschätzt y="
                        + assist.firstY + " vy=" + assist.firstVy + ", danach y=" + assist.secondY;
                assertTrue(assist.calls >= 2 && assist.firstArmed, at);
                assertEquals(truth.y(), assist.firstTrueY, 0.01, "Höhe beim ersten Aufruf (" + at + ")");
                assertEquals(truth.vy(), assist.firstTrueVy, 0.01, "Steiggeschwindigkeit beim ersten Aufruf (" + at + ")");
                assertEquals(truth.pitchDeg(), assist.firstPitch, 0.5, "Nicken beim ersten Aufruf (" + at + ")");
                assertEquals(truth.rollDeg(), assist.firstRoll, 0.5, "Rollen beim ersten Aufruf (" + at + ")");
                assertEquals(0.0, ((assist.firstHeading - truth.headingDeg()) % 360.0 + 540.0) % 360.0 - 180.0, 0.5,
                        "Kurs beim ersten Aufruf (" + at + ")");
                assertEquals(assist.secondY, assist.firstY, 0.05, "Höhenschätzung springt nach dem ersten Aufruf (" + at + ")");
                assertEquals(assist.firstTrueY, assist.firstY, 1.5, "Höhenschätzung beim ersten Aufruf leer (" + at + ")");
                assertEquals(assist.firstTrueVy, assist.firstVy, 0.1, "Steiggeschwindigkeit beim ersten Aufruf leer (" + at + ")");
            }
        }
    }

    @Test
    void takeoverAssistHoldingTheEstimatedHeightDoesNotSink() {
        Run[] servers = {hoveringServerFlight(0.0), cruisingServerFlight(6.0, 0.0)};
        String[] labels = {"Schweben", "6 m/s"};
        for (int k = 0; k < servers.length; k++) {
            double before = servers[k].flight.bottom();
            double worst = 0.0;
            double sum = 0.0;
            int over = 0;
            for (long seed = 1; seed <= 200; seed++) {
                Takeover pilot = new Takeover(servers[k], true, new HoldEstimate(), seed * 104729L);
                double loss = before - pilot.lowestOver(3.0);
                assertTrue(pilot.sim.telemetry().armed, "nach der Übernahme entschärft: " + pilot.sim.telemetry().lastDisarmReason);
                assertEquals(0.5F, pilot.throttle, "Gas nicht in der Mitte");
                worst = Math.max(worst, loss);
                sum += loss;
                if (loss > 0.5) {
                    over++;
                }
            }
            String at = labels[k] + ": im Mittel " + sum / 200 + " m, bis " + worst + " m, " + over + " von 200 über 0,5 m in 3 s";
            assertTrue(worst <= 1.0, "Flughilfe mit geschätzter Höhe sinkt nach der Übernahme (" + at + ")");
            assertTrue(sum / 200 <= 0.3, "Flughilfe mit geschätzter Höhe sackt im Mittel ab (" + at + ")");
            assertTrue(over <= 4, "Flughilfe mit geschätzter Höhe sackt zu oft über 0,5 m ab (" + at + ")");
        }
    }

    @Test
    void pilotTakeoverStaysArmedAtHoverThrottleAndLosesAtMostHalfAMetre() {
        for (double speed : new double[]{0.0, 5.0}) {
            Run server = hoveringServerFlight(speed);
            double before = server.flight.bottom();
            for (long seed = 1; seed <= 20; seed++) {
                Takeover pilot = new Takeover(server, true, null, seed * 7919L);
                assertTrue(pilot.sim.telemetry().armed, "Übernahme nicht scharf");
                double lowest = pilot.lowestOver(3.0);
                assertTrue(pilot.sim.telemetry().armed, "nach der Übernahme entschärft: " + pilot.sim.telemetry().lastDisarmReason);
                assertEquals(pilot.sim.telemetry().hoverThrottleStick, pilot.throttle, 0.02, "Gas nicht auf Schwebegas");
                assertTrue(before - lowest <= 0.5, "sackt bei der Übernahme ab (" + speed + " m/s, Seed " + seed + "): " + before + " → " + lowest);
            }
        }
    }

    @Test
    void pilotTakeoverWithAnAltitudeAssistCentresTheThrottle() {
        Run server = hoveringServerFlight(0.0);
        double before = server.flight.bottom();
        for (long seed = 1; seed <= 20; seed++) {
            Takeover pilot = new Takeover(server, true, new CenterClimb(), seed * 7919L);
            double lowest = pilot.lowestOver(3.0);
            assertTrue(pilot.sim.telemetry().armed, "nach der Übernahme entschärft: " + pilot.sim.telemetry().lastDisarmReason);
            assertTrue(pilot.sim.assistOwnsHold(), "Flughilfe hält die Höhe nicht");
            assertEquals(0.5F, pilot.throttle, "Gas nicht in der Mitte");
            assertTrue(before - lowest <= 0.5, "sackt mit Flughilfe ab (Seed " + seed + "): " + before + " → " + lowest);
        }
    }

    @Test
    void takeoverStartsWithTheBatteryUnderHoverLoad() {
        Run server = hoveringServerFlight(0.0);
        Vector3d v = new Vector3d();
        Quaternionf body = Handover.body(server.sim);
        DroneSim fresh = new DroneSim(DroneBuild.FREESTYLE, new FlightControllerConfig(), new SimTuning(), 5L);
        Handover.place(fresh, new SimSnapshot(), server.flight.x(), server.flight.bottom(), server.flight.z(), body, v.x, v.y, v.z, true);
        DroneSim settled = new DroneSim(DroneBuild.FREESTYLE, new FlightControllerConfig(), new SimTuning(), 5L);
        PilotTakeover.place(settled, new SimSnapshot(), server.flight.x(), server.flight.bottom(), server.flight.z(), body, v.x, v.y, v.z);
        assertTrue(settled.telemetry().armed);
        assertTrue(settled.telemetry().hoverThrottleStick > fresh.telemetry().hoverThrottleStick,
                "Schwebegas ohne Spannungsabfall: " + settled.telemetry().hoverThrottleStick + " / " + fresh.telemetry().hoverThrottleStick);
        assertEquals(server.sim.telemetry().hoverThrottleStick, settled.telemetry().hoverThrottleStick, 0.005);
    }

    @Test
    void withoutSeamlessTakeoverTheDisarmedDroneFalls() {
        Run server = hoveringServerFlight(0.0);
        double before = server.flight.bottom();
        Takeover pilot = new Takeover(server, false, new CenterClimb());
        double lowest = pilot.lowestOver(1.0);
        assertFalse(pilot.sim.telemetry().armed);
        assertTrue(before - lowest > 0.5, "entschärfte Drohne fällt nicht: " + before + " → " + lowest);
    }

    @Test
    void takeoverThrottleMatchesTheAssistAndTheThrottleMode() {
        assertEquals(0.5F, PilotTakeover.throttle(true, false, 0.21, 0.04));
        assertEquals(0.5F, PilotTakeover.throttle(true, true, 0.21, 0.04));
        assertEquals(0.21F, PilotTakeover.throttle(false, false, 0.21, 0.04), 1.0E-6F);
        assertEquals(0.54F + 0.21F * 0.46F, PilotTakeover.throttle(false, true, 0.21, 0.04), 1.0E-6F);
        assertEquals(0.5F, PilotTakeover.throttle(false, false, Double.NaN, 0.04));
        assertEquals(1.0F, PilotTakeover.throttle(false, false, 1.7, 0.04));
        assertEquals(0.0F, PilotTakeover.throttle(false, false, -0.3, 0.04));
        assertTrue(PilotTakeover.seamless(true, 3.0));
        assertTrue(PilotTakeover.seamless(true, Double.POSITIVE_INFINITY));
        assertFalse(PilotTakeover.seamless(true, 0.0));
        assertFalse(PilotTakeover.seamless(true, Double.NaN));
        assertFalse(PilotTakeover.seamless(false, 3.0));
    }

    @Test
    void replacingTheAssistKeepsFlyingWithoutHeightLoss() {
        Run run = new Run(DroneBuild.FREESTYLE).hovering(0.5, 5.0, 0.5, 3.0, 0.0);
        run.logic.startInFlight(run.sim, run.flight.x(), run.flight.bottom(), run.flight.z(), new Hold(), AutonomyOptions.of(HOLD));
        run.flyFor(3.0);
        double before = run.flight.bottom();
        run.logic.replace(new Follow(new double[]{run.flight.x(), 0.0, run.flight.z() - 3.0}), AutonomyOptions.of(FOLLOW).label("FOLLOW"));
        double lowest = before;
        for (int tick = 0; tick < 60; tick++) {
            run.tick();
            lowest = Math.min(lowest, run.flight.bottom());
        }
        assertTrue(before - lowest <= 0.3, "Wechsel sackt ab: " + before + " → " + lowest);
        assertEquals(ServerFlightAssist.Phase.ASSIST, run.logic.phase());
        assertNull(run.ended);
    }

    @Test
    void batteryEndsTheFlightAfterMoreThanTenMinutesAndLeavesAReserve() {
        Run run = new Run(DroneBuild.FREESTYLE).hovering(0.5, 6.0, 0.5, 0.0, 0.0);
        run.logic.startInFlight(run.sim, run.flight.x(), run.flight.bottom(), run.flight.z(), new Hold(), AutonomyOptions.of(HOLD));
        while (run.ended == null && run.time < 3600.0) {
            run.tick();
        }
        assertEquals(AutonomyEnd.BATTERY, run.ended, run.describe());
        assertTrue(run.time > 600.0, "endet vor 10 Minuten: " + run.describe());
        assertTrue(run.sim.telemetry().batteryCharge > 0.1, "keine Reserve: " + run.describe());
        assertFalse(run.logic.addonActive());
        assertEquals(ServerFlightAssist.Phase.LAND, run.logic.phase());
        assertEquals(ServerFlightAssist.FollowUp.HOME, ServerFlightAssist.followUp(AutonomyEnd.BATTERY, false, false));
        assertTrue(run.flyFor(60.0), "landet nach dem Akku-Ende nicht: " + run.describe());
        assertEquals(0.0, run.flight.bottom(), 0.15, run.describe());
        assertEquals(AutonomyEnd.BATTERY, run.ended);
    }

    @Test
    void maxDurationEndsWithTimeoutAndSixtyMinutesIsTheHardLimit() {
        Run run = new Run(DroneBuild.FREESTYLE).hovering(0.5, 6.0, 0.5, 0.0, 0.0);
        run.logic.startInFlight(run.sim, run.flight.x(), run.flight.bottom(), run.flight.z(), new Hold(),
                AutonomyOptions.of(HOLD).maxDuration(Duration.ofSeconds(5)));
        while (run.ended == null && run.time < 30.0) {
            run.tick();
        }
        assertEquals(AutonomyEnd.TIMEOUT, run.ended, run.describe());
        assertEquals(5.0, run.time, 0.1, run.describe());
        assertEquals(ServerFlightAssist.FollowUp.HOME, ServerFlightAssist.followUp(AutonomyEnd.TIMEOUT, false, false));
        assertEquals(3600.0, ServerFlightAssist.HARD_LIMIT_SECONDS);
        assertFalse(ServerFlightAssist.timedOut(3599.0, 3599.0, 3600.0));
        assertTrue(ServerFlightAssist.timedOut(3600.0, 3600.0, 3600.0));
        assertTrue(ServerFlightAssist.timedOut(10.0, 3600.0, 3600.0), "Wechsel der Flughilfe umgeht die harte Grenze");
        assertThrows(IllegalArgumentException.class, () -> AutonomyOptions.of(HOLD).maxDuration(Duration.ofMinutes(61)));
        assertThrows(IllegalArgumentException.class, () -> AutonomyOptions.of(HOLD).maxDuration(Duration.ofMillis(999)));
        assertEquals(Duration.ofMinutes(60), AutonomyOptions.of(HOLD).maxDuration());
    }

    @Test
    void takeoffFromTheGroundClimbsThenLandRequestLandsAndDisarms() {
        Run run = new Run(DroneBuild.FREESTYLE).onGround(0.5, 0.5);
        Hold hold = new Hold();
        double ground = run.comY();
        run.logic.startFromGround(run.sim, run.flight.x(), run.flight.bottom(), run.flight.z(), hold, AutonomyOptions.of(HOLD).takeoff(5.0));
        assertEquals(ServerFlightAssist.Phase.TAKEOFF, run.logic.phase());
        while (run.logic.phase() == ServerFlightAssist.Phase.TAKEOFF && run.time < 20.0) {
            run.tick();
        }
        assertEquals(ServerFlightAssist.Phase.ASSIST, run.logic.phase(), run.describe());
        assertEquals(5.0, run.comY() - ground, 0.35, run.describe());
        assertTrue(Math.hypot(run.flight.x() - 0.5, run.flight.z() - 0.5) < 0.5, "steigt nicht senkrecht: " + run.describe());
        run.flyFor(2.0);
        hold.land = true;
        assertTrue(run.flyFor(30.0), "landet nicht: " + run.describe());
        assertEquals(AutonomyEnd.LANDED, run.ended, run.describe());
        assertEquals(0.0, run.flight.bottom(), 0.15, run.describe());
        assertTrue(Math.hypot(run.flight.x() - 0.5, run.flight.z() - 0.5) < 1.5, run.describe());
        assertThrows(IllegalArgumentException.class, () -> AutonomyOptions.of(HOLD).takeoff(0.5));
        assertThrows(IllegalArgumentException.class, () -> AutonomyOptions.of(HOLD).takeoff(31.0));
    }

    private static final class PartialBlocks implements RescueWorld {
        final VoxelWorld base = new VoxelWorld(0);
        final Map<Long, Double> tops = new HashMap<>();
        final Set<Long> thin = new HashSet<>();

        PartialBlocks block(int x, int y, int z, double top, boolean motionBlocking) {
            this.base.fill(x, y, z, x, y, z);
            this.tops.put(key(x, y, z), top);
            if (!motionBlocking) {
                this.thin.add(key(x, y, z));
            }
            return this;
        }

        static long key(int x, int y, int z) {
            return ((long) x & 0x1FFFFFL) << 42 | ((long) y & 0x1FFFFFL) << 21 | ((long) z & 0x1FFFFFL);
        }

        @Override
        public boolean solid(int x, int y, int z) {
            return this.base.solid(x, y, z);
        }

        @Override
        public int surfaceY(int x, int z) {
            for (int y = VoxelWorld.MAX_Y; y >= VoxelWorld.MIN_Y; y--) {
                if (this.base.solid(x, y, z) && !this.thin.contains(key(x, y, z))) {
                    return y + 1;
                }
            }
            return VoxelWorld.MIN_Y;
        }

        @Override
        public Ground ground(int x, int y, int z) {
            return this.base.ground(x, y, z);
        }

        @Override
        public double groundTop(int x, int y, int z) {
            return this.tops.getOrDefault(key(x, y, z), 1.0);
        }

        double top(int x, int z) {
            for (int y = VoxelWorld.MAX_Y; y >= VoxelWorld.MIN_Y; y--) {
                if (this.base.solid(x, y, z)) {
                    return y + this.groundTop(x, y, z);
                }
            }
            return VoxelWorld.MIN_Y;
        }

        @Override
        public int minY() {
            return this.base.minY();
        }

        @Override
        public int maxY() {
            return this.base.maxY();
        }
    }

    private static Map<String, PartialBlocks> partialBlocks() {
        Map<String, PartialBlocks> worlds = new LinkedHashMap<>();
        worlds.put("Landeplatz im Boden eingelassen", new PartialBlocks().block(0, -1, 0, 2.0 / 16.0, false));
        worlds.put("Teppich auf dem Boden", new PartialBlocks().block(0, 0, 0, 1.0 / 16.0, false));
        worlds.put("Stufe auf dem Boden", new PartialBlocks().block(0, 0, 0, 0.5, true));
        return worlds;
    }

    private static RescueAutopilot flyHome(VoxelFlight flight, RescueWorld world, boolean autonomous, double homeX, double homeY, double homeZ) {
        RescueAutopilot autopilot = new RescueAutopilot();
        autopilot.setAutonomous(autonomous);
        RescueSettings cfg = new RescueSettings();
        flight.place(12.5, 6.0, 0.5, 0.0);
        flight.arm();
        autopilot.start(flight.sim, flight.x(), flight.bottom(), flight.z(), homeX, homeY, homeZ, world, cfg);
        double dt = 1.0 / 60.0;
        for (double t = 0.0; t < 90.0 && flight.sim.telemetry().armed; t += dt) {
            autopilot.update(dt, flight.sim, flight.x(), flight.bottom(), flight.z(), world, cfg);
            flight.apply(autopilot);
            flight.frame(Math.round(dt * 1.0E9));
        }
        return autopilot;
    }

    private static String where(RescueAutopilot autopilot, VoxelFlight flight) {
        return String.format(Locale.ROOT, "Phase %s, scharf %s, Pos (%.2f, %.2f, %.2f)", autopilot.phase(), flight.sim.telemetry().armed,
                flight.x(), flight.bottom(), flight.z());
    }

    @Test
    void homeLandingOnAPartialHeightBlockTouchesDownOnTheHomepoint() {
        double homeX = 0.72;
        double homeZ = 0.5;
        List<String> misses = new ArrayList<>();
        for (Map.Entry<String, PartialBlocks> entry : partialBlocks().entrySet()) {
            PartialBlocks world = entry.getValue();
            double homeY = world.top(0, 0);
            for (DroneBuild build : List.of(DroneBuild.FREESTYLE, DroneBuild.LONG_RANGE)) {
                for (boolean autonomous : new boolean[] {true, false}) {
                    VoxelFlight flight = new VoxelFlight(new DroneSim(build, new FlightControllerConfig(), new SimTuning(), 7L), world);
                    RescueAutopilot autopilot = flyHome(flight, world, autonomous, homeX, homeY, homeZ);
                    String what = entry.getKey() + ", " + build.frame() + (autonomous ? ", Heimflug ohne Pilot" : ", GPS Rescue des Piloten");
                    if (autopilot.phase() != RescuePhase.LANDED || flight.sim.telemetry().armed) {
                        misses.add(what + ": landet nicht: " + where(autopilot, flight));
                        continue;
                    }
                    double off = Math.hypot(flight.x() - homeX, flight.z() - homeZ);
                    boolean onBlock = flight.x() > 0.0 && flight.x() < 1.0 && flight.z() > 0.0 && flight.z() < 1.0;
                    if (off > 0.3 || !onBlock) {
                        misses.add(String.format(Locale.ROOT, "%s: setzt %.2f m neben dem Homepoint auf%s: %s", what, off,
                                onBlock ? "" : ", nicht auf dem Block", where(autopilot, flight)));
                    }
                }
            }
        }
        assertTrue(misses.isEmpty(), String.join("\n", misses));
        PartialBlocks roof = new PartialBlocks();
        for (int x = -1; x <= 1; x++) {
            for (int z = -1; z <= 1; z++) {
                roof.block(x, 2, z, 0.5, true);
            }
        }
        VoxelFlight flight = new VoxelFlight(new DroneSim(DroneBuild.FREESTYLE, new FlightControllerConfig(), new SimTuning(), 7L), roof);
        RescueAutopilot autopilot = flyHome(flight, roof, true, homeX, 0.0, homeZ);
        assertEquals(RescuePhase.LANDED, autopilot.phase(), where(autopilot, flight));
        assertTrue(flight.bottom() < 0.5, "Dach aus Stufen 2 Blöcke über dem Homepoint gilt als Landeplatz: " + where(autopilot, flight));
    }

    @Test
    void landOnAPartialHeightBlockTouchesDownWhereLandWasCalled() {
        List<String> misses = new ArrayList<>();
        for (Map.Entry<String, PartialBlocks> entry : partialBlocks().entrySet()) {
            for (DroneBuild build : List.of(DroneBuild.FREESTYLE, DroneBuild.LONG_RANGE)) {
                Run run = new Run(build, new SimTuning(), entry.getValue()).hovering(0.68, 6.0, 0.5, 0.0, 0.0);
                Hold hold = new Hold();
                run.logic.startInFlight(run.sim, run.flight.x(), run.flight.bottom(), run.flight.z(), hold, AutonomyOptions.of(HOLD));
                run.flyFor(3.0);
                double x = run.flight.x();
                double z = run.flight.z();
                hold.land = true;
                String what = entry.getKey() + ", " + build.frame();
                if (!run.flyFor(40.0) || run.ended != AutonomyEnd.LANDED) {
                    misses.add(what + ": landet nicht: " + run.describe());
                    continue;
                }
                double off = Math.hypot(run.flight.x() - x, run.flight.z() - z);
                boolean onBlock = run.flight.x() > 0.0 && run.flight.x() < 1.0 && run.flight.z() > 0.0 && run.flight.z() < 1.0;
                if (off > 0.3 || !onBlock) {
                    misses.add(String.format(Locale.ROOT, "%s: setzt %.2f m neben dem Punkt von land() auf%s: %s", what, off,
                            onBlock ? "" : ", nicht auf dem Block", run.describe()));
                }
            }
        }
        assertTrue(misses.isEmpty(), String.join("\n", misses));
        Run stairs = new Run(DroneBuild.FREESTYLE, new SimTuning(), new PartialBlocks().block(0, 0, 0, Double.NaN, true)).hovering(0.68, 6.0, 0.5, 0.0, 0.0);
        Hold hold = new Hold();
        stairs.logic.startInFlight(stairs.sim, stairs.flight.x(), stairs.flight.bottom(), stairs.flight.z(), hold, AutonomyOptions.of(HOLD));
        stairs.flyFor(3.0);
        hold.land = true;
        assertTrue(stairs.flyFor(40.0), "Treppe: landet nicht: " + stairs.describe());
        assertEquals(AutonomyEnd.LANDED, stairs.ended, stairs.describe());
        assertFalse(stairs.flight.x() > 0.0 && stairs.flight.x() < 1.0 && stairs.flight.z() > 0.0 && stairs.flight.z() < 1.0,
                "Treppe ohne ebene Oberseite gilt als eben: " + stairs.describe());
    }

    @Test
    void everyEndReasonEndsTheAddonPart() {
        Set<AutonomyEnd> seen = EnumSet.noneOf(AutonomyEnd.class);
        for (AutonomyEnd reason : AutonomyEnd.values()) {
            Run run = new Run(DroneBuild.FREESTYLE).hovering(0.5, 6.0, 0.5, 0.0, 0.0);
            run.logic.startInFlight(run.sim, run.flight.x(), run.flight.bottom(), run.flight.z(), new Hold(), AutonomyOptions.of(HOLD));
            run.flyFor(0.5);
            run.logic.end(reason);
            assertEquals(reason, run.logic.consumeEnded());
            assertFalse(run.logic.addonActive());
            assertNotNull(ServerFlightAssist.followUp(reason, false, false));
            seen.add(reason);
        }
        assertEquals(EnumSet.allOf(AutonomyEnd.class), seen);
        assertEquals(ServerFlightAssist.FollowUp.NONE, ServerFlightAssist.followUp(AutonomyEnd.PILOT, false, false));
        assertEquals(ServerFlightAssist.FollowUp.NONE, ServerFlightAssist.followUp(AutonomyEnd.REPLACED, false, false));
        assertEquals(ServerFlightAssist.FollowUp.LAND, ServerFlightAssist.followUp(AutonomyEnd.OUT_OF_RANGE, false, false));
        assertEquals(ServerFlightAssist.FollowUp.LAND, ServerFlightAssist.followUp(AutonomyEnd.OWNER_OFFLINE, false, false));
        assertEquals(ServerFlightAssist.FollowUp.LAND, ServerFlightAssist.followUp(AutonomyEnd.ERROR, false, false));
        assertEquals(ServerFlightAssist.FollowUp.LAND, ServerFlightAssist.followUp(AutonomyEnd.STOPPED, false, false));
        assertEquals(ServerFlightAssist.FollowUp.HOME, ServerFlightAssist.followUp(AutonomyEnd.STOPPED, true, false));
    }

    @Test
    void outOfRangeFliesHomeLikeAGpsDroneAndLandsInPlaceWithoutGps() {
        Map<AutonomyEnd, ServerFlightAssist.FollowUp[]> table = new EnumMap<>(AutonomyEnd.class);
        table.put(AutonomyEnd.PILOT, new ServerFlightAssist.FollowUp[] {ServerFlightAssist.FollowUp.NONE, ServerFlightAssist.FollowUp.NONE});
        table.put(AutonomyEnd.LANDED, new ServerFlightAssist.FollowUp[] {ServerFlightAssist.FollowUp.NONE, ServerFlightAssist.FollowUp.NONE});
        table.put(AutonomyEnd.REPLACED, new ServerFlightAssist.FollowUp[] {ServerFlightAssist.FollowUp.NONE, ServerFlightAssist.FollowUp.NONE});
        table.put(AutonomyEnd.BATTERY, new ServerFlightAssist.FollowUp[] {ServerFlightAssist.FollowUp.HOME, ServerFlightAssist.FollowUp.HOME});
        table.put(AutonomyEnd.TIMEOUT, new ServerFlightAssist.FollowUp[] {ServerFlightAssist.FollowUp.HOME, ServerFlightAssist.FollowUp.HOME});
        table.put(AutonomyEnd.OUT_OF_RANGE, new ServerFlightAssist.FollowUp[] {ServerFlightAssist.FollowUp.LAND, ServerFlightAssist.FollowUp.HOME});
        table.put(AutonomyEnd.OWNER_OFFLINE, new ServerFlightAssist.FollowUp[] {ServerFlightAssist.FollowUp.LAND, ServerFlightAssist.FollowUp.LAND});
        table.put(AutonomyEnd.STOPPED, new ServerFlightAssist.FollowUp[] {ServerFlightAssist.FollowUp.LAND, ServerFlightAssist.FollowUp.LAND});
        table.put(AutonomyEnd.ERROR, new ServerFlightAssist.FollowUp[] {ServerFlightAssist.FollowUp.LAND, ServerFlightAssist.FollowUp.LAND});
        table.put(AutonomyEnd.TERRAIN_LIMIT, new ServerFlightAssist.FollowUp[] {ServerFlightAssist.FollowUp.LAND, ServerFlightAssist.FollowUp.HOME});
        assertEquals(EnumSet.allOf(AutonomyEnd.class), table.keySet());
        for (Map.Entry<AutonomyEnd, ServerFlightAssist.FollowUp[]> row : table.entrySet()) {
            assertEquals(row.getValue()[0], ServerFlightAssist.followUp(row.getKey(), false, false), row.getKey() + " ohne GPS-Fix");
            assertEquals(row.getValue()[1], ServerFlightAssist.followUp(row.getKey(), false, true), row.getKey() + " mit GPS-Fix");
            assertEquals(row.getValue()[0] != row.getValue()[1], ServerFlightAssist.needsGps(row.getKey(), false), row.getKey() + " misst den GPS-Empfang");
        }
        assertTrue(ServerFlightAssist.needsGps(AutonomyEnd.OUT_OF_RANGE, false));
        assertTrue(ServerFlightAssist.needsGps(AutonomyEnd.TERRAIN_LIMIT, false));
        assertFalse(ServerFlightAssist.needsGps(AutonomyEnd.BATTERY, false));
        assertFalse(ServerFlightAssist.needsGps(AutonomyEnd.STOPPED, true));
        assertEquals(ServerFlightAssist.FollowUp.HOME, ServerFlightAssist.followUp(AutonomyEnd.STOPPED, true, false),
                "requestReturnHome fliegt heim, auch ohne GPS-Fix");
        assertEquals(ServerFlightAssist.FollowUp.HOME, ServerFlightAssist.followUp(AutonomyEnd.STOPPED, true, true));
        assertEquals(ServerFlightAssist.FollowUp.HOME, ServerFlightAssist.followUp(AutonomyEnd.OUT_OF_RANGE, true, true));
        assertEquals(ServerFlightAssist.FollowUp.LAND, ServerFlightAssist.followUp(AutonomyEnd.OUT_OF_RANGE, true, false),
                "außer Reichweite ohne GPS-Fix landet an Ort und Stelle");
    }

    @Test
    void anExceptionInTheAssistEndsWithErrorAndLandsInPlace() {
        Run run = new Run(DroneBuild.FREESTYLE).hovering(0.5, 6.0, 0.5, 0.0, 0.0);
        Hold hold = new Hold();
        run.logic.startInFlight(run.sim, run.flight.x(), run.flight.bottom(), run.flight.z(), hold, AutonomyOptions.of(HOLD));
        run.flyFor(1.0);
        hold.fail = new IllegalStateException("Testfehler");
        run.tick();
        assertEquals(AutonomyEnd.ERROR, run.ended);
        assertNotNull(run.logic.consumeFailure());
        assertTrue(run.flyFor(30.0), "landet nach dem Fehler nicht: " + run.describe());
        assertEquals(0.0, run.flight.bottom(), 0.15, run.describe());
        assertTrue(Math.hypot(run.flight.x() - 0.5, run.flight.z() - 0.5) < 1.5, run.describe());
        Run reset = new Run(DroneBuild.FREESTYLE).hovering(0.5, 6.0, 0.5, 0.0, 0.0);
        reset.logic.startInFlight(reset.sim, 0.5, 6.0, 0.5, new FlightAssist() {
            @Override
            public void update(FlightState state, AssistOutput output) {
            }

            @Override
            public void reset() {
                throw new IllegalStateException("reset");
            }
        }, AutonomyOptions.of(HOLD));
        assertEquals(AutonomyEnd.ERROR, reset.logic.consumeEnded());
        Run sensor = new Run(DroneBuild.FREESTYLE).hovering(0.5, 6.0, 0.5, 0.0, 0.0);
        sensor.logic.startInFlight(sensor.sim, 0.5, 6.0, 0.5, new FlightAssist() {
            @Override
            public void update(FlightState state, AssistOutput output) {
            }

            @Override
            public double sensorRange() {
                throw new IllegalStateException("sensor");
            }
        }, AutonomyOptions.of(HOLD));
        assertEquals(0.0, sensor.logic.sensorRange());
        assertEquals(AutonomyEnd.ERROR, sensor.logic.consumeEnded());
    }

    @Test
    void returnHomeRequestStopsTheAddonFlightForPropwashsHomeFlight() {
        Run run = new Run(DroneBuild.FREESTYLE).hovering(0.5, 6.0, 0.5, 0.0, 0.0);
        Hold hold = new Hold();
        run.logic.startInFlight(run.sim, run.flight.x(), run.flight.bottom(), run.flight.z(), hold, AutonomyOptions.of(HOLD));
        run.flyFor(1.0);
        hold.home = true;
        run.tick();
        assertEquals(AutonomyEnd.STOPPED, run.ended);
        assertTrue(run.logic.homeRequested());
        assertEquals(ServerFlightAssist.FollowUp.HOME, ServerFlightAssist.followUp(AutonomyEnd.STOPPED, run.logic.homeRequested(), false));
    }

    @Test
    void windAndLowGravityActLikeInTheFpvFlight() {
        VoxelWorld world = new VoxelWorld(0);
        Run windy = new Run(DroneBuild.FREESTYLE, new SimTuning(), world).hovering(0.5, 6.0, 0.5, 0.0, 0.0);
        windy.flight.wind(6.0, 0.0, 0.0, 0.4);
        windy.logic.startInFlight(windy.sim, windy.flight.x(), windy.flight.bottom(), windy.flight.z(), new Hold(), AutonomyOptions.of(HOLD));
        windy.flyFor(4.0);
        double worst = 0.0;
        double tilt = 0.0;
        for (int tick = 0; tick < 200; tick++) {
            windy.tick();
            worst = Math.max(worst, Math.hypot(windy.flight.x() - 0.5, windy.flight.z() - 0.5));
            tilt = Math.max(tilt, Math.abs(windy.sim.telemetry().rollDeg) + Math.abs(windy.sim.telemetry().pitchDeg));
        }
        assertTrue(worst <= 0.5, "Halten im Wind weicht ab: " + worst + " " + windy.describe());
        assertTrue(tilt > 3.0, "Wind wirkt nicht: Neigung " + tilt);
        Run drifting = new Run(DroneBuild.FREESTYLE, new SimTuning(), new VoxelWorld(0)).hovering(0.5, 6.0, 0.5, 0.0, 0.0);
        drifting.flight.wind(6.0, 0.0, 0.0, 0.4);
        drifting.logic.startInFlight(drifting.sim, 0.5, 6.0, 0.5, (state, output) -> output.sticks(0.0, 0.0, 0.0, state.hoverThrottle()),
                AutonomyOptions.of(HOLD));
        drifting.flyFor(5.0);
        assertTrue(drifting.flight.x() - 0.5 > 2.0, "ohne Halten treibt der Wind nicht ab: " + drifting.describe());
        SimTuning moon = new SimTuning();
        moon.gravityScale = 0.3;
        Run low = new Run(DroneBuild.FREESTYLE, moon, new VoxelWorld(0)).hovering(0.5, 6.0, 0.5, 0.0, 0.0);
        Run normal = new Run(DroneBuild.FREESTYLE).hovering(0.5, 6.0, 0.5, 0.0, 0.0);
        low.logic.startInFlight(low.sim, 0.5, 6.0, 0.5, new Hold(), AutonomyOptions.of(HOLD));
        normal.logic.startInFlight(normal.sim, 0.5, 6.0, 0.5, new Hold(), AutonomyOptions.of(HOLD));
        low.flyFor(10.0);
        normal.flyFor(10.0);
        assertEquals(0.3 * 9.81, low.sim.gravity(), 0.01);
        assertEquals(6.0, low.flight.bottom(), 0.3, low.describe());
        assertTrue(low.flight.input.throttle < normal.flight.input.throttle - 0.05,
                "geringe Schwerkraft ändert das Gas nicht: " + low.flight.input.throttle + " / " + normal.flight.input.throttle);
        double[] owner = {0.5, 0.0, -2.5};
        Run follow = new Run(DroneBuild.FREESTYLE, moon, new VoxelWorld(0)).hovering(0.5, 4.0, 0.5, 0.0, 0.0);
        follow.flight.wind(2.5, 0.0, 2.5, 0.2);
        follow.logic.startInFlight(follow.sim, 0.5, 4.0, 0.5, new Follow(owner), AutonomyOptions.of(FOLLOW));
        double walked = 0.0;
        double far = 0.0;
        for (int tick = 0; tick < 400; tick++) {
            if (tick >= 20 && walked < 24.0) {
                double step = Math.min(WALK_SPEED * 0.05, 24.0 - walked);
                owner[2] -= step;
                walked += step;
            }
            follow.tick();
            far = Math.max(far, Math.hypot(follow.flight.x() - owner[0], follow.flight.z() - owner[2]));
        }
        assertTrue(far <= 7.0, "Folgen bei geringer Schwerkraft und Wind zu weit weg: " + far + " " + follow.describe());
        assertEquals(4.0, follow.flight.bottom(), 0.3, follow.describe());
    }

    @Test
    void addonFlightsGetAtMostOneOfTwoPerOwnerAndSixOfEightPerServer() {
        AutonomyBudget budget = new AutonomyBudget();
        UUID a = UUID.randomUUID();
        Object a1 = new Object();
        Object a2 = new Object();
        assertEquals(AutonomyBudget.Admission.ADMITTED, budget.admit(a1, a, AutonomyBudget.Kind.ADDON));
        assertEquals(AutonomyBudget.Admission.OWNER_FULL, budget.admit(a2, a, AutonomyBudget.Kind.ADDON));
        assertEquals(AutonomyBudget.Admission.ADMITTED, budget.admit(a2, a, AutonomyBudget.Kind.HOME), "Heimflug bekommt den zweiten Platz");
        assertEquals(AutonomyBudget.Admission.OWNER_FULL, budget.admit(new Object(), a, AutonomyBudget.Kind.HOME));
        budget.clear();
        for (int i = 0; i < 6; i++) {
            assertEquals(AutonomyBudget.Admission.ADMITTED, budget.admit(new Object(), UUID.randomUUID(), AutonomyBudget.Kind.ADDON));
        }
        assertEquals(AutonomyBudget.Admission.SERVER_FULL, budget.admit(new Object(), UUID.randomUUID(), AutonomyBudget.Kind.ADDON));
        assertEquals(AutonomyBudget.Admission.ADMITTED, budget.admit(new Object(), UUID.randomUUID(), AutonomyBudget.Kind.HOME));
        assertEquals(AutonomyBudget.Admission.ADMITTED, budget.admit(new Object(), UUID.randomUUID()));
        assertEquals(AutonomyBudget.Admission.SERVER_FULL, budget.admit(new Object(), UUID.randomUUID(), AutonomyBudget.Kind.HOME));
        assertEquals(8, budget.size());
        assertEquals(6, budget.size(AutonomyBudget.Kind.ADDON));
        assertEquals(6, budget.addonServerLimit());
        assertEquals(1, budget.addonOwnerLimit());
    }

    @Test
    void homeFlightsKeepPriorityWhenAddonFlightsFillTheirShare() {
        AutonomyBudget budget = new AutonomyBudget();
        UUID owner = UUID.randomUUID();
        Object drone = new Object();
        assertEquals(AutonomyBudget.Admission.ADMITTED, budget.admit(drone, owner, AutonomyBudget.Kind.ADDON));
        assertEquals(AutonomyBudget.Admission.ADMITTED, budget.check(drone, owner, AutonomyBudget.Kind.ADDON));
        budget.release(drone);
        assertEquals(AutonomyBudget.Admission.ADMITTED, budget.admit(drone, owner), "nach dem Ende des Add-on-Flugs fliegt der Heimflug");
        assertEquals(AutonomyBudget.Admission.ADMITTED, budget.admit(new Object(), owner));
        assertEquals(AutonomyBudget.Admission.OWNER_FULL, budget.check(new Object(), owner, AutonomyBudget.Kind.ADDON));
        UUID other = UUID.randomUUID();
        assertEquals(AutonomyBudget.Admission.ADMITTED, budget.admit(drone, other, AutonomyBudget.Kind.ADDON), "anderer Besitzer übernimmt den Platz");
        assertEquals(AutonomyBudget.SERVER_LIMIT, AutonomyBudget.SERVER.addonServerLimit() + AutonomyBudget.HOME_SERVER_RESERVE);
    }

    @Test
    void worldRulesForAutonomousFlightsAreNeutralAndNotChangeableInZones() {
        RuleSpec perPlayer = WorldRuleSpecs.AUTONOMOUS_FLIGHTS_PER_PLAYER;
        RuleSpec total = WorldRuleSpecs.AUTONOMOUS_FLIGHTS_TOTAL;
        assertEquals("propwash:autonomous_flights_per_player", perPlayer.id());
        assertEquals("propwash:autonomous_flights_total", total.id());
        AutonomyBudget budget = new AutonomyBudget();
        assertEquals(budget.addonOwnerLimit(), perPlayer.defaultValue(), "Standard = bisheriger Wert je Spieler");
        assertEquals(budget.addonServerLimit(), total.defaultValue(), "Standard = bisheriger Wert am Server");
        for (RuleSpec spec : List.of(perPlayer, total)) {
            assertEquals(RuleSpec.Kind.INT, spec.kind(), spec.name());
            assertEquals(0, spec.min(), spec.name());
            assertTrue(spec.zeroSpecial(), spec.name());
            assertEquals(RuleSpec.Group.GAMEPLAY, spec.group(), spec.name());
            assertFalse(spec.raceRelevant(), spec.name());
            assertFalse(Zone.overridable(spec), spec.name());
            assertFalse(ZoneRelease.capable(spec.id()), spec.name());
        }
        assertEquals(8, perPlayer.max());
        assertEquals(32, total.max());
        budget.addonLimits(perPlayer.defaultValue(), total.defaultValue());
        assertEquals(AutonomyBudget.OWNER_LIMIT, budget.ownerLimit());
        assertEquals(AutonomyBudget.SERVER_LIMIT, budget.serverLimit());
    }

    @Test
    void addonBudgetFollowsTheWorldRulesAndHomeFlightsKeepTheirReserve() {
        AutonomyBudget budget = new AutonomyBudget();
        UUID a = UUID.randomUUID();
        budget.addonLimits(2, 6);
        assertEquals(AutonomyBudget.Admission.ADMITTED, budget.admit(new Object(), a, AutonomyBudget.Kind.ADDON));
        assertEquals(AutonomyBudget.Admission.ADMITTED, budget.admit(new Object(), a, AutonomyBudget.Kind.ADDON), "Regelwert 2: zweiter Flug");
        assertEquals(AutonomyBudget.Admission.OWNER_FULL, budget.admit(new Object(), a, AutonomyBudget.Kind.ADDON));
        assertEquals(AutonomyBudget.Admission.ADMITTED, budget.admit(new Object(), a, AutonomyBudget.Kind.HOME), "Heimflug behält seinen Platz");
        assertEquals(AutonomyBudget.Admission.OWNER_FULL, budget.admit(new Object(), a, AutonomyBudget.Kind.HOME));
        budget.clear();
        budget.addonLimits(1, 6);
        Object running = new Object();
        assertEquals(AutonomyBudget.Admission.ADMITTED, budget.admit(running, a, AutonomyBudget.Kind.ADDON));
        assertEquals(AutonomyBudget.Admission.OWNER_FULL, budget.admit(new Object(), a, AutonomyBudget.Kind.ADDON), "Regelwert 1: zweiter Flug abgelehnt");
        budget.addonLimits(0, 6);
        assertEquals(AutonomyBudget.Admission.ADMITTED, budget.check(running, a, AutonomyBudget.Kind.ADDON), "laufender Flug bleibt");
        budget.clear();
        assertEquals(AutonomyBudget.Admission.OWNER_FULL, budget.admit(new Object(), a, AutonomyBudget.Kind.ADDON), "Regelwert 0: keine");
        assertEquals(AutonomyBudget.Admission.ADMITTED, budget.admit(new Object(), a, AutonomyBudget.Kind.HOME));
        assertEquals(AutonomyBudget.Admission.ADMITTED, budget.admit(new Object(), a, AutonomyBudget.Kind.HOME), "Heimflug weiter 2 je Spieler");
        assertEquals(AutonomyBudget.Admission.OWNER_FULL, budget.admit(new Object(), a, AutonomyBudget.Kind.HOME));
        budget.clear();
        budget.addonLimits(2, 1);
        assertEquals(AutonomyBudget.Admission.ADMITTED, budget.admit(new Object(), a, AutonomyBudget.Kind.ADDON));
        assertEquals(AutonomyBudget.Admission.SERVER_FULL, budget.admit(new Object(), UUID.randomUUID(), AutonomyBudget.Kind.ADDON), "gesamt 1");
        for (int i = 0; i < 7; i++) {
            assertEquals(AutonomyBudget.Admission.ADMITTED, budget.admit(new Object(), UUID.randomUUID(), AutonomyBudget.Kind.HOME));
        }
        assertEquals(AutonomyBudget.Admission.SERVER_FULL, budget.admit(new Object(), UUID.randomUUID(), AutonomyBudget.Kind.HOME));
        budget.clear();
        budget.addonLimits(3, 10);
        assertEquals(4, budget.ownerLimit());
        assertEquals(12, budget.serverLimit());
        for (int i = 0; i < 10; i++) {
            assertEquals(AutonomyBudget.Admission.ADMITTED, budget.admit(new Object(), UUID.randomUUID(), AutonomyBudget.Kind.ADDON));
        }
        assertEquals(AutonomyBudget.Admission.SERVER_FULL, budget.admit(new Object(), UUID.randomUUID(), AutonomyBudget.Kind.ADDON));
        assertEquals(AutonomyBudget.Admission.ADMITTED, budget.admit(new Object(), UUID.randomUUID(), AutonomyBudget.Kind.HOME));
        assertEquals(AutonomyBudget.Admission.ADMITTED, budget.admit(new Object(), UUID.randomUUID(), AutonomyBudget.Kind.HOME));
        assertEquals(AutonomyBudget.Admission.SERVER_FULL, budget.admit(new Object(), UUID.randomUUID(), AutonomyBudget.Kind.HOME));
    }

    @Test
    void fullBudgetMessageNamesTheRuleValue() throws IOException {
        AutonomyBudget budget = new AutonomyBudget();
        Component drone = Component.literal("Quad");
        budget.addonLimits(1, 6);
        assertRefusal(budget.refusal(drone, AutonomyBudget.Admission.OWNER_FULL), AutonomyBudget.FULL_OWNER_KEY, 1,
                WorldRuleSpecs.AUTONOMOUS_FLIGHTS_PER_PLAYER);
        budget.addonLimits(2, 3);
        assertRefusal(budget.refusal(drone, AutonomyBudget.Admission.SERVER_FULL), AutonomyBudget.FULL_SERVER_KEY, 3,
                WorldRuleSpecs.AUTONOMOUS_FLIGHTS_TOTAL);
        assertRefusal(budget.refusal(drone, AutonomyBudget.Admission.OWNER_FULL), AutonomyBudget.FULL_OWNER_KEY, 2,
                WorldRuleSpecs.AUTONOMOUS_FLIGHTS_PER_PLAYER);
        budget.addonLimits(0, 6);
        assertRefusal(budget.refusal(drone, AutonomyBudget.Admission.OWNER_FULL), AutonomyBudget.OFF_KEY, -1,
                WorldRuleSpecs.AUTONOMOUS_FLIGHTS_PER_PLAYER);
        budget.addonLimits(2, 0);
        assertRefusal(budget.refusal(drone, AutonomyBudget.Admission.SERVER_FULL), AutonomyBudget.OFF_KEY, -1,
                WorldRuleSpecs.AUTONOMOUS_FLIGHTS_TOTAL);
        for (String language : List.of("en_us", "de_de")) {
            JsonObject texts = lang(language);
            for (String key : List.of(AutonomyBudget.FULL_OWNER_KEY, AutonomyBudget.FULL_SERVER_KEY)) {
                assertTrue(texts.has(key), language + ": " + key);
                String shown = String.format(Locale.ROOT, texts.get(key).getAsString(), "Quad", 4, "Regel");
                assertTrue(shown.startsWith("Quad: ") && shown.contains(" 4 ") && shown.contains("Regel"), language + ": " + shown);
            }
            assertTrue(texts.has(AutonomyBudget.OFF_KEY), language);
            String off = String.format(Locale.ROOT, texts.get(AutonomyBudget.OFF_KEY).getAsString(), "Quad", "Regel");
            assertTrue(off.startsWith("Quad: ") && off.contains("Regel") && off.contains("0"), language + ": " + off);
        }
    }

    private static void assertRefusal(Component text, String key, int value, RuleSpec rule) {
        TranslatableContents contents = assertInstanceOf(TranslatableContents.class, text.getContents());
        assertEquals(key, contents.getKey());
        Object[] args = contents.getArgs();
        assertEquals(value < 0 ? 2 : 3, args.length, key);
        assertEquals("Quad", assertInstanceOf(Component.class, args[0]).getString());
        if (value >= 0) {
            assertEquals(value, args[1], key);
        }
        TranslatableContents name = assertInstanceOf(TranslatableContents.class, assertInstanceOf(Component.class, args[args.length - 1]).getContents());
        assertEquals(rule.translationKey(), name.getKey());
    }

    private static final class EdgeCruise implements FlightAssist {
        final double targetX;
        boolean turnAtLimit;
        boolean turned;
        double firstLimit = Double.NaN;

        EdgeCruise(double targetX) {
            this.targetX = targetX;
        }

        @Override
        public void update(FlightState state, AssistOutput output) {
            if (state.terrainLimit() && Double.isNaN(this.firstLimit)) {
                this.firstLimit = state.time();
            }
            if (state.terrainLimit() && this.turnAtLimit) {
                this.turned = true;
            }
            output.holdAltitude(6.0);
            output.holdPosition(this.turned ? 0.5 : this.targetX, 0.5);
            output.maxSpeed(8.0);
        }
    }

    private static final class Fenced {
        final Run run = new Run(DroneBuild.FREESTYLE).hovering(0.5, 6.0, 0.5, 0.0, 0.0);
        final FlightFence fence = new FlightFence();
        final boolean generates;
        double furthest;
        double limitAt = Double.NaN;
        double endAt = Double.NaN;

        Fenced(boolean generates, FlightAssist assist) {
            this.generates = generates;
            this.fence.use(FlightFence.of(0, 0, (x, z) -> x < 3));
            this.run.logic.startInFlight(this.run.sim, this.run.flight.x(), this.run.flight.bottom(), this.run.flight.z(), assist, AutonomyOptions.of(HOLD));
        }

        void fly(double seconds) {
            double end = this.run.time + seconds;
            while (this.run.time < end && this.run.sim.telemetry().armed) {
                for (int i = 0; i < 4; i++) {
                    this.run.logic.update(SLICE, this.run.sim, this.run.flight.x(), this.run.flight.bottom(), this.run.flight.z(), this.run.world,
                            this.run.flight.input);
                    boolean held = this.fence.limit(this.run.sim) && this.fence.atLimit();
                    if (held && Double.isNaN(this.limitAt)) {
                        this.limitAt = this.run.time;
                    }
                    this.run.logic.terrainLimit(held && !this.generates, SLICE);
                    this.run.flight.frame(SLICE_NANOS);
                    this.run.time += SLICE;
                    this.furthest = Math.max(this.furthest, this.run.flight.x());
                }
                AutonomyEnd ended = this.run.logic.consumeEnded();
                if (ended != null && this.run.ended == null) {
                    this.run.ended = ended;
                    this.endAt = this.run.time;
                }
            }
        }
    }

    @Test
    void withoutGenerationTheFlightEndsAtTheTerrainLimitAfterOneSecondAndGoesHome() {
        EdgeCruise cruise = new EdgeCruise(200.0);
        Fenced fenced = new Fenced(false, cruise);
        fenced.fly(30.0);
        assertEquals(AutonomyEnd.TERRAIN_LIMIT, fenced.run.ended, fenced.run.describe());
        assertFalse(fenced.run.logic.addonActive());
        assertTrue(fenced.furthest < 48.0, "über die Kante bei 48 m: " + fenced.furthest);
        assertTrue(fenced.furthest > 48.0 - EdgeFence.STOP_DISTANCE - 2.0, "hält zu früh: " + fenced.furthest);
        assertFalse(Double.isNaN(cruise.firstLimit), "Flughilfe sieht terrainLimit() nie");
        assertTrue(cruise.firstLimit < fenced.endAt, "terrainLimit() erst nach dem Ende");
        double held = fenced.endAt - fenced.limitAt;
        assertTrue(held >= ServerFlightAssist.TERRAIN_LIMIT_SECONDS - 0.06 && held <= ServerFlightAssist.TERRAIN_LIMIT_SECONDS + 0.5,
                "Ende " + held + " s nach dem Erreichen der Grenze");
        assertEquals(ServerFlightAssist.FollowUp.HOME, ServerFlightAssist.followUp(AutonomyEnd.TERRAIN_LIMIT, false, true),
                "am Weltrand mit GPS-Empfang heim");
        assertEquals(ServerFlightAssist.FollowUp.LAND, ServerFlightAssist.followUp(AutonomyEnd.TERRAIN_LIMIT, false, false),
                "am Weltrand ohne GPS-Empfang an Ort und Stelle landen");
        assertNotEquals(ServerFlightAssist.Phase.ASSIST, fenced.run.logic.phase(), "ohne Heimflug landet die Drohne an Ort und Stelle");
    }

    @Test
    void withGenerationTheFlightWaitsAtTheEdgeAndNeverEndsThere() {
        EdgeCruise cruise = new EdgeCruise(200.0);
        Fenced fenced = new Fenced(true, cruise);
        fenced.fly(30.0);
        assertNull(fenced.run.ended, fenced.run.describe());
        assertTrue(fenced.run.logic.addonActive());
        assertFalse(Double.isNaN(fenced.limitAt), "Drohne erreicht die Kante nicht: " + fenced.furthest);
        assertTrue(Double.isNaN(cruise.firstLimit), "terrainLimit() mit erlaubtem Erzeugen");
        assertTrue(fenced.furthest < 48.0, "über die Kante bei 48 m: " + fenced.furthest);
    }

    @Test
    void anAssistThatTurnsAtTheLimitKeepsFlying() {
        EdgeCruise cruise = new EdgeCruise(200.0);
        cruise.turnAtLimit = true;
        Fenced fenced = new Fenced(false, cruise);
        fenced.fly(30.0);
        assertTrue(cruise.turned);
        assertNull(fenced.run.ended, fenced.run.describe());
        assertTrue(fenced.run.logic.addonActive());
        assertEquals(0.5, fenced.run.flight.x(), 1.0, fenced.run.describe());
    }

    @Test
    void everyEndTheOwnerIsToldAboutHasATextInBothLanguages() throws IOException {
        Set<AutonomyEnd> silent = EnumSet.of(AutonomyEnd.REPLACED, AutonomyEnd.OWNER_OFFLINE);
        for (String language : List.of("en_us", "de_de")) {
            JsonObject texts = lang(language);
            for (AutonomyEnd reason : AutonomyEnd.values()) {
                if (silent.contains(reason)) {
                    continue;
                }
                String key = "propwash.drone.autonomy.ended." + reason.name().toLowerCase(Locale.ROOT);
                assertTrue(texts.has(key), language + ": Meldung für " + reason + " fehlt (" + key + ")");
                assertTrue(texts.get(key).getAsString().startsWith("%s"), language + ": " + key + " nennt die Drohne nicht vorn");
            }
        }
        assertTrue(lang("de_de").get("propwash.drone.autonomy.ended.terrain_limit").getAsString().contains("Weltrand"));
        assertTrue(lang("en_us").get("propwash.drone.autonomy.ended.terrain_limit").getAsString().contains("world edge"));
    }

    private static JsonObject lang(String language) throws IOException {
        for (Path dir : List.of(Path.of("common/src/lang/addon-autonomy"), Path.of("src/lang/addon-autonomy"))) {
            Path file = dir.resolve(language + ".json");
            if (Files.exists(file)) {
                try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
                    return JsonParser.parseReader(reader).getAsJsonObject();
                }
            }
        }
        fail("Sprachquellen des autonomen Flugs nicht gefunden");
        return new JsonObject();
    }

    private static double sq(double v) {
        return v * v;
    }
}
