package de.lardtom.propwash.drone.client;

import de.lardtom.propwash.build.PartWear;
import de.lardtom.propwash.dev.SelfTest;
import de.lardtom.propwash.dev.SelfTest.StepResult;
import de.lardtom.propwash.drone.DronePiloting;
import de.lardtom.propwash.drone.QuadcopterEntity;
import de.lardtom.propwash.drone.WindModel;
import de.lardtom.propwash.drone.client.CauldronSelfTests.Kind;
import de.lardtom.propwash.drone.client.CauldronSelfTests.Rig;
import de.lardtom.propwash.drone.client.CauldronSelfTests.Target;
import de.lardtom.propwash.input.StickPositions;
import de.lardtom.propwash.sim.DisarmReason;
import de.lardtom.propwash.sim.DroneSim;
import de.lardtom.propwash.sim.SimEnvironment;
import de.lardtom.propwash.sim.StabMode;
import de.lardtom.propwash.sim.Telemetry;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;

final class CauldronDriftSelfTests {
    private static final double MAX_DRIFT = 0.05;
    private static final double OFFSET_SLACK = 0.02;
    private static final double TICK_SECONDS = 0.05;
    private static final int WINDOW_TICKS = 5;
    private static final int SETTLE_TICKS = 10;
    private static final int RESET_RETRY_TICKS = 30;
    private static final int OBSERVE_TICKS = 50;
    private static final int CLIMB_TICKS = 100;
    private static final double CLIMB_HEIGHT = 1.5;
    private static final double MID_WATER = 0.25;
    private static final String STATE = "drift";

    private CauldronDriftSelfTests() {
    }

    static void register() {
        SelfTest.clientStep(CauldronSelfTests.GROUP + " – stehendes Wasser: wasserdichte Drohne schwebt im Wasserkessel wie im Wasserblock "
                + "ohne Knüppel ohne seitliche Drift (höchstens 0,05 m/s), bleibt scharf, Vollgas trägt sie heraus", 1200,
                ctx -> step(ctx, Variant.WATERPROOF));
        SelfTest.clientStep(CauldronSelfTests.GROUP + " – stehendes Wasser ohne Wasserschutz: Drohne geht im Wasserkessel wie im Wasserblock "
                + "aus und sinkt ohne seitliche Drift (höchstens 0,05 m/s)", 1000, ctx -> step(ctx, Variant.PLAIN));
        SelfTest.clientStep(CauldronSelfTests.GROUP + " – Lavakessel ohne seitliche Drift: Drohne geht wie im Lavablock aus und bleibt "
                + "ohne seitliche Drift (höchstens 0,05 m/s)", 1000, ctx -> step(ctx, Variant.LAVA));
        SelfTest.clientStep(CauldronSelfTests.GROUP + " – Pulverschneekessel ohne seitliche Drift: Drohne sinkt ohne Knüppel wie im "
                + "Pulverschneeblock ohne seitliche Drift (höchstens 0,05 m/s) ein, bleibt scharf, Vollgas trägt sie heraus", 1200,
                ctx -> step(ctx, Variant.POWDER_SNOW));
    }

    private enum Variant {
        WATERPROOF(Kind.WATER, true, true, MID_WATER, false),
        PLAIN(Kind.WATER, false, false, MID_WATER, false),
        LAVA(Kind.LAVA, false, false, 0.0, true),
        POWDER_SNOW(Kind.POWDER_SNOW, false, true, 0.5, false);

        final Kind kind;
        final boolean waterproof;
        final boolean staysArmed;
        final double lift;
        final boolean lying;

        Variant(Kind kind, boolean waterproof, boolean staysArmed, double lift, boolean lying) {
            this.kind = kind;
            this.waterproof = waterproof;
            this.staysArmed = staysArmed;
            this.lift = lift;
            this.lying = lying;
        }

        String label(Target target) {
            String name = target == Target.CAULDRON ? this.kind.cauldronName : this.kind.referenceName;
            return this.waterproof ? name + " (wasserdicht)" : name;
        }
    }

    private record Observation(double peakDrift, double offset, double seconds, int disarmTick, DisarmReason reason, int climbTicks,
                               double wind, double drop, boolean lying) {
        boolean drifts() {
            return this.peakDrift > MAX_DRIFT || this.offset > MAX_DRIFT * this.seconds + OFFSET_SLACK;
        }

        boolean falls() {
            return this.lying && this.drop > OFFSET_SLACK;
        }

        @Override
        public String toString() {
            String armed = this.disarmTick < 0 ? "bleibt scharf" : String.format(Locale.ROOT, "entschärft nach %d Ticks (%s)", this.disarmTick,
                    this.reason);
            String climb = this.climbTicks < 0 ? "" : String.format(Locale.ROOT, ", mit Vollgas nach %d Ticks %.1f m über dem Block",
                    this.climbTicks, CLIMB_HEIGHT);
            String fall = this.lying ? String.format(Locale.ROOT, ", fällt %.3f m", this.drop) : "";
            return String.format(Locale.ROOT, "seitlich höchstens %.3f m/s, Versatz %.3f m in %.1f s, Wind am Ort %.2f m/s, %s%s%s",
                    this.peakDrift, this.offset, this.seconds, this.wind, armed, fall, climb);
        }
    }

    private static final class Scenario {
        final Variant variant;
        int stage;
        int mark;
        Target target = Target.REFERENCE;
        @Nullable CompletableFuture<?> pending;
        int crashes;
        @Nullable PartWear wear;
        final List<Vec3> track = new ArrayList<>();
        int disarmTick = -1;
        DisarmReason reason = DisarmReason.NONE;
        int climbTicks = -1;
        double wind;
        @Nullable Observation reference;
        @Nullable Observation cauldron;

        Scenario(Variant variant) {
            this.variant = variant;
        }

        void enter(int next, int tick) {
            this.stage = next;
            this.mark = tick;
        }
    }

    private static StepResult step(SelfTest.StepContext ctx, Variant variant) {
        Scenario s = (Scenario) ctx.state().computeIfAbsent(STATE, key -> new Scenario(variant));
        if (s.pending != null) {
            if (s.pending.isCompletedExceptionally()) {
                sticks(false, 0.0F);
                return StepResult.fail("Server-Aktion in Stufe " + s.stage + ": " + s.pending.exceptionNow().getMessage());
            }
            if (!s.pending.isDone()) {
                return StepResult.pending();
            }
            s.pending = null;
        }
        StepResult result = run(ctx, s);
        if (result.status() != StepResult.Status.PENDING) {
            sticks(false, 0.0F);
        }
        return result;
    }

    private static StepResult run(SelfTest.StepContext ctx, Scenario s) {
        int waited = ctx.tick() - s.mark;
        Variant v = s.variant;
        PilotSession session = PilotSession.INSTANCE;
        if (s.stage == 0) {
            sticks(false, 0.0F);
            s.pending = ctx.onServer(server -> prepare(server, v));
            s.enter(1, ctx.tick());
            return StepResult.pending();
        }
        Rig rig = CauldronSelfTests.RIG.get();
        if (rig == null) {
            return StepResult.fail("kein Testfeld");
        }
        QuadcopterEntity drone = session.drone();
        DroneSim sim = session.sim();
        if (drone == null || sim == null || drone.getId() != rig.droneId || !session.isFpv()) {
            return waited > CauldronSelfTests.SYNC_TICKS ? StepResult.fail("keine FPV-Sitzung mit der Testdrohne") : StepResult.pending();
        }
        Telemetry t = sim.telemetry();
        switch (s.stage) {
            case 1 -> {
                sticks(false, 0.0F);
                Level level = drone.level();
                boolean field = level.getBlockState(rig.cauldron(v.kind)).equals(v.kind.cauldron())
                        && level.getBlockState(rig.reference(v.kind)).equals(v.kind.reference());
                if (!field || sim.waterproof() != v.waterproof) {
                    return waited > CauldronSelfTests.SYNC_TICKS ? StepResult.fail("Testfeld oder Wasserschutz kommt beim Client nicht an: "
                            + level.getBlockState(rig.cauldron(v.kind)) + ", wasserdicht " + sim.waterproof()) : StepResult.pending();
                }
                s.crashes = session.crashCount();
                s.wear = sim.wear();
                return toArmSpot(ctx, s, rig);
            }
            case 2 -> {
                boolean grounded = t.grounded || t.sleeping;
                Vec3 spot = rig.armSpot(v.kind);
                boolean there = CauldronSelfTests.near(drone, spot);
                sticks(t.armed || there && grounded && waited >= CauldronSelfTests.SWITCH_OFF_TICKS, 0.0F);
                if (!t.armed) {
                    if (waited > CauldronSelfTests.SYNC_TICKS + CauldronSelfTests.ARM_TICKS) {
                        return StepResult.fail(String.format(Locale.ROOT, "auf dem Stein bei %.2f %.2f %.2f nicht scharf geschaltet, Sperren=%d, %s",
                                spot.x, spot.y, spot.z, t.armBlockers, CauldronSelfTests.where(drone, sim)));
                    }
                    if (!there && waited > 0 && waited % RESET_RETRY_TICKS == 0) {
                        s.pending = ctx.onServer(server -> CauldronSelfTests.serverDrone(server, rig.droneId).resetTo(spot, CauldronSelfTests.LEVEL));
                    }
                    return StepResult.pending();
                }
                Vec3 start = start(rig, s);
                s.track.clear();
                s.disarmTick = -1;
                s.reason = DisarmReason.NONE;
                s.climbTicks = -1;
                s.pending = ctx.onServer(server -> CauldronSelfTests.serverDrone(server, rig.droneId).resetTo(start, CauldronSelfTests.LEVEL));
                s.enter(3, ctx.tick());
                return StepResult.pending();
            }
            case 3 -> {
                sticks(true, 0.0F);
                Vec3 start = start(rig, s);
                if (drone.position().distanceTo(start) > CauldronSelfTests.PLACED) {
                    return waited > CauldronSelfTests.SYNC_TICKS ? StepResult.fail("Drohne nicht in " + v.label(s.target) + " gesetzt: "
                            + CauldronSelfTests.where(drone, sim)) : StepResult.pending();
                }
                s.wind = wind(drone.level(), start);
                s.enter(4, ctx.tick());
                return observe(ctx, s, rig, drone, t, 0);
            }
            case 4 -> {
                sticks(true, 0.0F);
                return observe(ctx, s, rig, drone, t, waited);
            }
            case 5 -> {
                sticks(true, 1.0F);
                BlockPos block = s.target == Target.CAULDRON ? rig.cauldron(v.kind) : rig.reference(v.kind);
                if (!t.armed) {
                    return StepResult.fail(String.format(Locale.ROOT, "%s: beim Herausfliegen entschärft (%s), %s", v.label(s.target),
                            t.lastDisarmReason, CauldronSelfTests.where(drone, sim)));
                }
                if (drone.getY() > block.getY() + CLIMB_HEIGHT) {
                    s.climbTicks = waited;
                    return finish(ctx, s, rig);
                }
                return waited > CLIMB_TICKS ? StepResult.fail(String.format(Locale.ROOT, "%s: Vollgas trägt die Drohne nicht heraus: %s",
                        v.label(s.target), CauldronSelfTests.where(drone, sim))) : StepResult.pending();
            }
            case 6 -> {
                sticks(v.staysArmed, 0.0F);
                if (!CauldronSelfTests.near(drone, rig.armSpot(v.kind)) && waited <= CauldronSelfTests.SYNC_TICKS) {
                    return StepResult.pending();
                }
                return evaluate(ctx, s, sim);
            }
            default -> {
                return StepResult.fail("unbekannte Stufe " + s.stage);
            }
        }
    }

    private static Vec3 start(Rig rig, Scenario s) {
        return rig.feet(s.variant.kind, s.target).add(0.0, s.variant.lift, 0.0);
    }

    private static StepResult toArmSpot(SelfTest.StepContext ctx, Scenario s, Rig rig) {
        Vec3 spot = rig.armSpot(s.variant.kind);
        s.pending = ctx.onServer(server -> CauldronSelfTests.serverDrone(server, rig.droneId).resetTo(spot, CauldronSelfTests.LEVEL));
        s.enter(2, ctx.tick());
        return StepResult.pending();
    }

    private static StepResult observe(SelfTest.StepContext ctx, Scenario s, Rig rig, QuadcopterEntity drone, Telemetry t, int since) {
        if (s.disarmTick < 0 && !t.armed) {
            s.disarmTick = since;
            s.reason = t.lastDisarmReason;
        }
        s.track.add(drone.position());
        if (since < OBSERVE_TICKS) {
            return StepResult.pending();
        }
        if (s.variant.staysArmed) {
            s.enter(5, ctx.tick());
            return StepResult.pending();
        }
        return finish(ctx, s, rig);
    }

    private static StepResult finish(SelfTest.StepContext ctx, Scenario s, Rig rig) {
        Observation seen = observation(s);
        sticks(s.variant.staysArmed, 0.0F);
        if (s.target == Target.REFERENCE) {
            s.reference = seen;
            s.target = Target.CAULDRON;
            return toArmSpot(ctx, s, rig);
        }
        s.cauldron = seen;
        Vec3 spot = rig.armSpot(s.variant.kind);
        s.pending = ctx.onServer(server -> CauldronSelfTests.serverDrone(server, rig.droneId).resetTo(spot, CauldronSelfTests.LEVEL));
        s.enter(6, ctx.tick());
        return StepResult.pending();
    }

    private static Observation observation(Scenario s) {
        List<Vec3> track = s.track;
        double peak = 0.0;
        for (int i = SETTLE_TICKS + WINDOW_TICKS; i < track.size(); i++) {
            peak = Math.max(peak, horizontal(track.get(i), track.get(i - WINDOW_TICKS)) / (WINDOW_TICKS * TICK_SECONDS));
        }
        double offset = 0.0;
        double low = track.getFirst().y;
        for (Vec3 p : track) {
            offset = Math.max(offset, horizontal(p, track.getFirst()));
            low = Math.min(low, p.y);
        }
        double seconds = (track.size() - 1) * TICK_SECONDS;
        return new Observation(peak, offset, seconds, s.disarmTick, s.reason, s.climbTicks, s.wind, track.getFirst().y - low,
                s.variant.lying);
    }

    private static double horizontal(Vec3 a, Vec3 b) {
        return Math.hypot(a.x - b.x, a.z - b.z);
    }

    private static StepResult evaluate(SelfTest.StepContext ctx, Scenario s, DroneSim sim) {
        Observation reference = s.reference;
        Observation cauldron = s.cauldron;
        if (reference == null || cauldron == null) {
            return StepResult.fail("Messung unvollständig");
        }
        Variant v = s.variant;
        String state = v.label(Target.REFERENCE) + ": " + reference + "; " + v.label(Target.CAULDRON) + ": " + cauldron;
        for (Target target : Target.values()) {
            Observation seen = target == Target.CAULDRON ? cauldron : reference;
            if (seen.falls()) {
                return StepResult.fail(String.format(Locale.ROOT, "%s: Drohne liegt zu Beginn der Messung nicht am Boden, sondern fällt %.2f m "
                        + "hinein (der Aufprall zählt sonst als Drift): %s", v.label(target), seen.drop, state));
            }
        }
        if (reference.drifts()) {
            return StepResult.fail("Kontrolle " + v.label(Target.REFERENCE) + " treibt seitlich: " + state);
        }
        if (cauldron.drifts()) {
            return StepResult.fail(String.format(Locale.ROOT, "%s treibt seitlich ohne Knüppel (höchstens %.2f m/s): %s", v.label(Target.CAULDRON),
                    MAX_DRIFT, state));
        }
        for (Observation seen : new Observation[]{reference, cauldron}) {
            if (v.staysArmed ? seen.disarmTick >= 0 : seen.disarmTick < 0 || seen.reason != DisarmReason.FLUID) {
                return StepResult.fail((v.staysArmed ? "Drohne geht aus statt scharf zu bleiben: " : "Drohne geht nicht wie in der Flüssigkeit "
                        + "aus: ") + state);
            }
        }
        PilotSession session = PilotSession.INSTANCE;
        if (session.crashCount() != s.crashes) {
            return StepResult.fail("Absturz im Test: " + state);
        }
        if (s.wear != null && !sim.wear().equals(s.wear)) {
            return StepResult.fail("Verschleiß im Test verändert: " + state);
        }
        ctx.log(state + "; Abstürze 0, Verschleiß unverändert");
        return StepResult.pass();
    }

    private static double wind(Level level, Vec3 feet) {
        SimEnvironment env = new SimEnvironment();
        WindModel.fill(level, feet.x, feet.y, feet.z, CauldronSelfTests.FLOOR, env);
        return Math.hypot(env.windX, env.windZ);
    }

    private static void prepare(MinecraftServer server, Variant v) {
        ServerPlayer player = CauldronSelfTests.player(server);
        ServerLevel level = player.level();
        Rig rig = CauldronSelfTests.rig(player);
        if (!(rig.droneId >= 0 && level.getEntity(rig.droneId) instanceof QuadcopterEntity)) {
            CauldronSelfTests.spawn(player, rig);
        }
        QuadcopterEntity current = CauldronSelfTests.serverDrone(server, rig.droneId);
        if (DronePiloting.pilotedBy(player.getUUID()).filter(piloted -> piloted == current).isEmpty()) {
            CauldronSelfTests.fly(player, current, rig);
        }
        if (!rig.built.containsKey(v.kind)) {
            CauldronSelfTests.build(level, rig, v.kind);
        }
        current.setWaterproof(v.waterproof);
        current.setWear(PartWear.PRISTINE);
    }

    private static void sticks(boolean arm, float throttle) {
        PilotSession.INSTANCE.setTestInput(new StickPositions(0.0F, 0.0F, 0.0F, throttle), arm, StabMode.ANGLE);
    }
}
