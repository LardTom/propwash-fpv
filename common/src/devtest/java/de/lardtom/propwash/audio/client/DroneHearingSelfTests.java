package de.lardtom.propwash.audio.client;

import de.lardtom.propwash.audio.DroneSoundStage;
import de.lardtom.propwash.audio.DroneSoundStage.Phase;
import de.lardtom.propwash.dev.SelfTest;
import de.lardtom.propwash.dev.SelfTest.StepResult;
import de.lardtom.propwash.drone.QuadcopterEntity;
import de.lardtom.propwash.drone.client.PilotSession;
import net.minecraft.client.Minecraft;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

public final class DroneHearingSelfTests {
    public static final String STEP = "audio: an der Drohne – FPV-Pilot hört Werfer, Geräusche und Explosion bei Körper 200 Blöcke entfernt";
    public static final int FPV_TICKS = 400;
    public static final int FPV_SETTLE_TICKS = 20;
    public static final int SETTLE_TICKS = 60;
    public static final int MOVE_TICKS = 900;
    public static final int LISTEN_TICKS = 40;
    public static final int STEP_TICKS = 5;
    public static final double STEP_BLOCKS = 8.0;
    public static final int LOST_TICKS = 40;
    public static final double ARRIVED = 4.0;
    private static final AtomicInteger DRONE = new AtomicInteger(-1);
    private static final AtomicReference<@Nullable Vec3> FAR = new AtomicReference<>();
    private static final AtomicReference<@Nullable Vec3> NEAR = new AtomicReference<>();
    private static final AtomicReference<DroneSoundStage.@Nullable Emission> EMISSION = new AtomicReference<>();

    private DroneHearingSelfTests() {
    }

    public static void register() {
        SelfTest.clientStep(STEP, 2400, DroneHearingSelfTests::step);
    }

    private static final class Run {
        String phase = "fpv";
        int mark;
        int flyingSince = -1;
        int lastStep = -STEP_TICKS;
        int arrivedAt = -1;
        int lostAt = -1;
        long log;
        @Nullable CompletableFuture<?> future;
        @Nullable String hearing;
        boolean hearingOk = true;
        final List<DroneSoundCheck.Counts> counts = new ArrayList<>();
        @Nullable String problem;

        void next(String phase, int tick) {
            this.phase = phase;
            this.mark = tick;
        }
    }

    private static StepResult step(SelfTest.StepContext ctx) {
        Map<String, Object> state = ctx.state();
        Minecraft mc = Minecraft.getInstance();
        Run run = (Run) state.get("run");
        if (run == null) {
            if (!DroneSoundStage.available()) {
                ctx.log("übersprungen: Beispiel-Werfer nicht angemeldet");
                return StepResult.pass();
            }
            run = new Run();
            state.put("run", run);
            run.mark = ctx.tick();
            run.future = ctx.onServer(DroneHearingSelfTests::start);
            return StepResult.pending();
        }
        int since = ctx.tick() - run.mark;
        CompletableFuture<?> future = run.future;
        if (future != null && future.isCompletedExceptionally() && !run.phase.equals("cleanup")) {
            return finish(ctx, run, "Server: " + future.exceptionNow());
        }
        boolean waiting = future != null && !future.isDone();
        PilotSession session = PilotSession.INSTANCE;
        QuadcopterEntity drone = session.drone();
        boolean flying = session.isFpv() && drone != null && drone.getId() == DRONE.get();
        switch (run.phase) {
            case "fpv" -> {
                if (!waiting && flying) {
                    if (run.flyingSince < 0) {
                        run.flyingSince = ctx.tick();
                    }
                    if (ctx.tick() - run.flyingSince >= FPV_SETTLE_TICKS) {
                        run.next("far", ctx.tick());
                    }
                    return StepResult.pending();
                }
                run.flyingSince = -1;
                return since > FPV_TICKS ? finish(ctx, run, "keine FPV-Sitzung mit der Werfer-Drohne") : StepResult.pending();
            }
            case "far", "near" -> {
                Vec3 target = run.phase.equals("far") ? FAR.get() : NEAR.get();
                if (since > MOVE_TICKS || target == null) {
                    return finish(ctx, run, "Drohne kommt nicht an (" + run.phase + "): " + where(drone, target));
                }
                if (!flying) {
                    if (run.lostAt < 0) {
                        run.lostAt = ctx.tick();
                    }
                    return ctx.tick() - run.lostAt > LOST_TICKS ? finish(ctx, run, "FPV-Sitzung beim Verlegen beendet (" + run.phase + ")") : StepResult.pending();
                }
                run.lostAt = -1;
                boolean arrived = Math.hypot(drone.getX() - target.x, drone.getZ() - target.z) <= ARRIVED;
                if (since % 100 == 0) {
                    ctx.log("unterwegs (" + run.phase + "): " + where(drone, target));
                }
                if (!arrived) {
                    run.arrivedAt = -1;
                    if (!waiting && ctx.tick() - run.lastStep >= STEP_TICKS) {
                        run.lastStep = ctx.tick();
                        run.future = ctx.onServer(server -> step(server, target));
                    }
                    return StepResult.pending();
                }
                if (run.arrivedAt < 0) {
                    run.arrivedAt = ctx.tick();
                }
                if (waiting || ctx.tick() - run.arrivedAt < SETTLE_TICKS) {
                    return StepResult.pending();
                }
                Phase phase = run.phase.equals("far") ? Phase.FAR : Phase.NEAR;
                if (phase == Phase.FAR) {
                    HearingProbe.Result hearing = HearingProbe.check(mc, drone);
                    run.hearing = hearing.line();
                    run.hearingOk = hearing.ok();
                }
                run.log = SoundPacketLog.mark();
                EMISSION.set(null);
                run.future = ctx.onServer(server -> emit(server, phase));
                run.next(phase == Phase.FAR ? "far.listen" : "near.listen", ctx.tick());
                return StepResult.pending();
            }
            case "far.listen", "near.listen" -> {
                if (waiting || since < LISTEN_TICKS) {
                    return StepResult.pending();
                }
                Phase phase = run.phase.equals("far.listen") ? Phase.FAR : Phase.NEAR;
                DroneSoundStage.Emission emission = EMISSION.get();
                if (emission == null || mc.player == null) {
                    return finish(ctx, run, "Server hat nichts ausgelöst (" + phase + ")");
                }
                DroneSoundCheck.Counts counts = DroneSoundCheck.count(SoundPacketLog.since(run.log), mc.player.position(), phase);
                run.counts.add(counts);
                ctx.log(String.format(Locale.ROOT, "%s: Drohne %.0f Blöcke vom Körper, Werfer %s – %s", phase, emission.distance(), emission.fired(), counts.line()));
                if (phase == Phase.FAR) {
                    run.arrivedAt = -1;
                    run.next("near", ctx.tick());
                    return StepResult.pending();
                }
                return finish(ctx, run, null);
            }
            case "cleanup" -> {
                if (waiting || session.isActive()) {
                    return since > 200 ? StepResult.fail("Aufräumen hängt") : StepResult.pending();
                }
                if (run.hearing != null) {
                    ctx.log(run.hearing);
                }
                if (run.problem != null) {
                    return StepResult.fail(run.problem);
                }
                List<String> bad = new ArrayList<>();
                for (DroneSoundCheck.Counts counts : run.counts) {
                    if (!counts.exact()) {
                        bad.add(counts.line());
                    }
                }
                if (!run.hearingOk) {
                    bad.add(run.hearing);
                }
                return bad.isEmpty() ? StepResult.pass() : StepResult.fail(String.join("; ", bad));
            }
            default -> {
                return finish(ctx, run, "unbekannte Phase " + run.phase);
            }
        }
    }

    private static String where(@Nullable QuadcopterEntity drone, @Nullable Vec3 target) {
        String at = drone == null ? "keine Drohne im Client" : String.format(Locale.ROOT, "Drohne im Client bei %.1f %.1f %.1f", drone.getX(), drone.getY(), drone.getZ());
        return target == null ? at : at + String.format(Locale.ROOT, ", Ziel %.1f %.1f %.1f", target.x, target.y, target.z);
    }

    private static StepResult finish(SelfTest.StepContext ctx, Run run, @Nullable String problem) {
        if (run.problem == null) {
            run.problem = problem;
        }
        run.future = ctx.onServer(DroneHearingSelfTests::release);
        run.next("cleanup", ctx.tick());
        return StepResult.pending();
    }

    private static void start(MinecraftServer server) {
        ServerPlayer player = server.getPlayerList().getPlayers().getFirst();
        FAR.set(DroneSoundStage.spot(player, Phase.FAR));
        NEAR.set(DroneSoundStage.spot(player, Phase.NEAR));
        QuadcopterEntity drone = DroneSoundStage.start(player);
        DRONE.set(drone.getId());
    }

    private static void step(MinecraftServer server, Vec3 target) {
        QuadcopterEntity drone = drone(server);
        if (drone == null) {
            throw new IllegalStateException("Werfer-Drohne fehlt");
        }
        DroneSoundStage.stepToward(drone, target, STEP_BLOCKS);
    }

    private static void emit(MinecraftServer server, Phase phase) {
        QuadcopterEntity drone = drone(server);
        if (drone == null) {
            throw new IllegalStateException("Werfer-Drohne fehlt");
        }
        EMISSION.set(DroneSoundStage.emit(server.getPlayerList().getPlayers().getFirst(), drone, phase));
    }

    private static void release(MinecraftServer server) {
        QuadcopterEntity drone = drone(server);
        List<ServerPlayer> players = server.getPlayerList().getPlayers();
        if (drone != null && !players.isEmpty()) {
            DroneSoundStage.release(players.getFirst(), drone);
        }
        DRONE.set(-1);
    }

    private static @Nullable QuadcopterEntity drone(MinecraftServer server) {
        int id = DRONE.get();
        for (ServerLevel level : server.getAllLevels()) {
            if (id >= 0 && level.getEntity(id) instanceof QuadcopterEntity drone) {
                return drone;
            }
        }
        return null;
    }
}
