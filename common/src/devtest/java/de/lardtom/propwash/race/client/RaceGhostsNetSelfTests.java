package de.lardtom.propwash.race.client;

import de.lardtom.propwash.dev.BadNetwork;
import de.lardtom.propwash.dev.FreezeMeter;
import de.lardtom.propwash.dev.SelfTest;
import de.lardtom.propwash.dev.SelfTest.StepResult;
import de.lardtom.propwash.drone.DroneModule;
import de.lardtom.propwash.drone.QuadcopterEntity;
import de.lardtom.propwash.race.CourseRegistry;
import de.lardtom.propwash.race.CourseToolItem;
import de.lardtom.propwash.race.EntryGhost;
import de.lardtom.propwash.race.GateEntity;
import de.lardtom.propwash.race.GateGeometry;
import de.lardtom.propwash.race.GateSize;
import de.lardtom.propwash.race.GhostAccess;
import de.lardtom.propwash.race.GhostKey;
import de.lardtom.propwash.race.GhostRecorder;
import de.lardtom.propwash.race.GhostTrack;
import de.lardtom.propwash.race.Leaderboard;
import de.lardtom.propwash.race.RaceModule;
import de.lardtom.propwash.race.RacePayloads;
import de.lardtom.propwash.race.RaceServer;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.jspecify.annotations.Nullable;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;

public final class RaceGhostsNetSelfTests {
    public static final String STEP = "race: Replay ohne Standbilder bei schlechter Verbindung des Piloten";
    private static final long MS = 1_000_000L;
    private static final long SECOND = 1_000L * MS;
    private static final long SEND_NANOS = 50L * MS;
    private static final long FRAME_NANOS = SECOND / 60L;
    private static final long LAP_NANOS = 8L * SECOND;
    private static final double GATE_GAP = 24.0;
    private static final int WAIT_TICKS = 160;
    private static final AtomicReference<Scene> SCENE = new AtomicReference<>();
    private static final AtomicReference<Flight> FLIGHT = new AtomicReference<>();
    private static final AtomicReference<String> PROBLEM = new AtomicReference<>();
    private static final AtomicReference<EntryGhost.Info> STORED = new AtomicReference<>();

    private RaceGhostsNetSelfTests() {
    }

    record Scene(int course, int gateA, int gateB, int drone, String frame, int layout, List<Vec3> path, double[] at, long startNanos, double speed,
                 double startDistance) {
        Vec3 position(long nanos) {
            double distance = this.startDistance + (nanos - this.startNanos) * 1.0E-9 * this.speed;
            int leg = 1;
            while (leg < this.at.length - 1 && this.at[leg] < distance) {
                leg++;
            }
            Vec3 from = this.path.get(leg - 1);
            Vec3 to = this.path.get(leg);
            double span = this.at[leg] - this.at[leg - 1];
            double f = span <= 0.0 ? 1.0 : Math.clamp((distance - this.at[leg - 1]) / span, 0.0, 1.0);
            return from.lerp(to, f);
        }

        Quaternionf orientation(long nanos) {
            Vec3 a = this.position(nanos - 20L * MS);
            Vec3 b = this.position(nanos + 20L * MS);
            float yaw = (float) Math.atan2(-(b.x - a.x), -(b.z - a.z));
            return new Quaternionf().rotateY(yaw).rotateX((float) Math.toRadians(-25.0));
        }

        long first() {
            return this.startNanos - Math.round(this.startDistance / this.speed * 1.0E9);
        }
    }

    record Flight(GhostKey key, String server, int stalls) {
    }

    public static void register() {
        SelfTest.clientStep(STEP, 900, RaceGhostsNetSelfTests::step);
        RaceStartAgainSelfTests.register();
    }

    private static StepResult step(SelfTest.StepContext ctx) {
        String stage = (String) ctx.state().getOrDefault("stage", "build");
        CompletableFuture<?> future = (CompletableFuture<?>) ctx.state().get("future");
        if (future != null) {
            if (future.isCompletedExceptionally()) {
                cleanup(ctx);
                return StepResult.fail("Server-Aktion fehlgeschlagen: " + future.exceptionNow());
            }
            if (!future.isDone()) {
                return StepResult.pending();
            }
            ctx.state().remove("future");
            String problem = PROBLEM.getAndSet(null);
            if (problem != null) {
                cleanup(ctx);
                return StepResult.fail(problem);
            }
        }
        switch (stage) {
            case "build" -> {
                SCENE.set(null);
                FLIGHT.set(null);
                STORED.set(null);
                ctx.state().put("future", ctx.onServer(server -> PROBLEM.set(build(server))));
                ctx.state().put("stage", "record");
                return StepResult.pending();
            }
            case "record" -> {
                Scene scene = SCENE.get();
                if (scene == null) {
                    return StepResult.fail("Szene fehlt");
                }
                record(scene);
                ctx.state().put("future", ctx.onServer(server -> PROBLEM.set(fly(server, scene))));
                ctx.state().put("stage", "wait");
                ctx.state().put("since", ctx.tick());
                return StepResult.pending();
            }
            case "wait" -> {
                EntryGhost.Info info = STORED.get();
                int waited = ctx.tick() - (int) ctx.state().get("since");
                if (info != null && info.source() != EntryGhost.Source.SERVER || waited > WAIT_TICKS) {
                    ctx.state().put("stage", "fetch");
                    return StepResult.pending();
                }
                if (waited % 10 == 0) {
                    Flight flight = FLIGHT.get();
                    if (flight == null) {
                        return StepResult.fail("Runde nicht geflogen");
                    }
                    ctx.state().put("future", ctx.onServer(server -> STORED.set(stored(flight.key()))));
                }
                return StepResult.pending();
            }
            case "fetch" -> {
                Flight flight = FLIGHT.get();
                Scene scene = SCENE.get();
                if (flight == null || scene == null) {
                    return StepResult.fail("Runde fehlt");
                }
                AtomicReference<GhostDownloads.Result> replay = new AtomicReference<>();
                GhostDownloads.INSTANCE.forget(scene.course(), flight.key());
                GhostDownloads.INSTANCE.fetch(scene.course(), flight.key(), GhostAccess.Purpose.REPLAY, replay::set);
                ctx.state().put("replay", replay);
                ctx.state().put("stage", "measure");
                ctx.state().put("since", ctx.tick());
                return StepResult.pending();
            }
            case "measure" -> {
                @SuppressWarnings("unchecked")
                GhostDownloads.Result result = ((AtomicReference<GhostDownloads.Result>) ctx.state().get("replay")).get();
                if (result == null) {
                    return ctx.tick() - (int) ctx.state().get("since") > 300 ? StepResult.fail("Replay lädt nicht") : StepResult.pending();
                }
                Flight flight = FLIGHT.get();
                Scene scene = SCENE.get();
                EntryGhost.Info info = STORED.get();
                cleanup(ctx);
                if (!result.loaded() || result.track() == null || flight == null || scene == null) {
                    return StepResult.fail("Replay nicht geladen: " + result.status());
                }
                FreezeMeter.Result replay = measure(result.track(), scene.speed());
                String source = info == null ? "?" : info.source().name();
                ctx.log(BadNetwork.PILOT.text() + "; " + flight.stalls() + " Stockungen beim Server; Spur nur vom Server: " + flight.server()
                        + "; Replay (Herkunft " + source + ", " + result.track().samples() + " Proben zu " + result.track().intervalNanos() / MS + " ms): "
                        + replay.text());
                if (!replay.smooth()) {
                    return StepResult.fail("Replay ruckelt: " + replay.text() + " (Herkunft " + source + ", Server-Spur " + flight.server() + ")");
                }
                if (info == null || info.source() == EntryGhost.Source.SERVER) {
                    return StepResult.fail("Spur des Piloten nicht übernommen: Herkunft " + source);
                }
                return StepResult.pass();
            }
            default -> {
                return StepResult.fail("Unbekannte Phase " + stage);
            }
        }
    }

    static FreezeMeter.Result measure(GhostTrack track, double speed) {
        FreezeMeter meter = new FreezeMeter(speed);
        double[] at = new double[3];
        Quaternionf rotation = new Quaternionf();
        for (long t = track.beginNanos(); t <= track.durationNanos(); t += FRAME_NANOS) {
            if (track.smoothPose(t, at, rotation)) {
                meter.frame(t, at[0], at[1], at[2]);
            }
        }
        return meter.result();
    }

    private static void record(Scene scene) {
        GhostRecorder recorder = ClientRaceTracker.INSTANCE.recorder();
        recorder.reset();
        long end = scene.startNanos() + LAP_NANOS;
        boolean started = false;
        for (long t = scene.first(); ; t += FRAME_NANOS) {
            if (!started && t >= scene.startNanos()) {
                recorder.onLapStart(scene.course(), scene.startNanos());
                started = true;
            }
            Vec3 at = scene.position(t);
            recorder.sample(t, at.x, at.y, at.z, scene.orientation(t));
            if (t >= end) {
                recorder.onLapComplete(LAP_NANOS, end, scene.frame(), scene.layout());
                recorder.stop();
                return;
            }
        }
    }

    private static @Nullable String build(MinecraftServer server) {
        ServerPlayer player = server.getPlayerList().getPlayers().getFirst();
        ServerLevel level = player.level();
        CourseRegistry registry = CourseRegistry.get();
        if (registry == null || !registry.entryGhosts().enabled()) {
            return "Ablage der Geister fehlt";
        }
        GateGeometry probe = GateGeometry.of(0.0, 0.0, 0.0, 0.0F, GateSize.MEDIUM);
        double x = player.getX() - 30.0;
        double z = player.getZ() + 20.0;
        GateEntity a = gate(level, x, z);
        GateEntity b = gate(level, x + probe.normalX() * GATE_GAP, z + probe.normalZ() * GATE_GAP);
        ItemStack tool = new ItemStack(RaceModule.COURSE_TOOL.get());
        CourseToolItem.useOnGate(tool, player, a);
        CourseToolItem.useOnGate(tool, player, b);
        int id = CourseToolItem.courseOf(tool);
        CourseRegistry.Course course = registry.course(id);
        if (course == null || course.gates.size() != 2) {
            return "Kurs nicht angelegt";
        }
        List<GateGeometry> gates = course.toData().geometry();
        QuadcopterEntity drone = DroneModule.DRONE.get().create(level, EntitySpawnReason.COMMAND);
        if (drone == null) {
            return "Drohne nicht erzeugt";
        }
        GateGeometry start = gates.getFirst();
        drone.snapTo(start.x(), start.centerY(), start.z(), 0.0F, 0.0F);
        drone.setNoGravity(true);
        if (!level.addFreshEntity(drone)) {
            return "Drohne nicht gespawnt";
        }
        drone.setPilot(player);
        List<Vec3> path = path(gates.get(0), gates.get(1));
        double[] at = new double[path.size()];
        for (int i = 1; i < path.size(); i++) {
            at[i] = at[i - 1] + path.get(i).distanceTo(path.get(i - 1));
        }
        double startDistance = at[1] + path.get(1).distanceTo(path.get(2)) * 0.5;
        double finishDistance = at[7] + path.get(7).distanceTo(path.get(8)) * 0.5;
        double speed = (finishDistance - startDistance) / (LAP_NANOS * 1.0E-9);
        long t0 = System.nanoTime() + 120L * SECOND;
        if (RaceServer.minimumLapNanos(course, player) * 2L > LAP_NANOS) {
            return "Runde zu kurz für die Mindestzeit: " + RaceServer.minimumLapNanos(course, player) / MS + " ms";
        }
        SCENE.set(new Scene(id, a.getId(), b.getId(), drone.getId(), drone.build().frame().key(), course.toData().layoutHash(), path, at, t0, speed,
                startDistance));
        return null;
    }

    private static List<Vec3> path(GateGeometry a, GateGeometry b) {
        Vec3 n = new Vec3(a.normalX(), 0.0, a.normalZ()).normalize();
        Vec3 side = new Vec3(n.z, 0.0, -n.x);
        Vec3 ca = new Vec3(a.x(), a.centerY(), a.z());
        Vec3 cb = new Vec3(b.x(), b.centerY(), b.z());
        List<Vec3> path = new ArrayList<>();
        path.add(ca.subtract(n.scale(12.0)));
        path.add(ca.subtract(n));
        path.add(ca.add(n));
        path.add(cb.subtract(n));
        path.add(cb.add(n));
        path.add(cb.add(n.scale(5.0)).add(side.scale(7.0)));
        path.add(ca.subtract(n.scale(5.0)).add(side.scale(7.0)));
        path.add(ca.subtract(n));
        path.add(ca.add(n));
        path.add(ca.add(n.scale(12.0)));
        return path;
    }

    private static GateEntity gate(ServerLevel level, double x, double z) {
        GateEntity gate = RaceModule.GATE.get().create(level, EntitySpawnReason.COMMAND);
        if (gate == null) {
            throw new IllegalStateException("Gate nicht erzeugt");
        }
        int top = level.getHeight(Heightmap.Types.MOTION_BLOCKING, (int) Math.floor(x), (int) Math.floor(z));
        gate.setSize(GateSize.MEDIUM);
        gate.snapTo(x, top, z, 0.0F, 0.0F);
        if (!level.addFreshEntity(gate)) {
            throw new IllegalStateException("Gate nicht gespawnt");
        }
        return gate;
    }

    private record Send(long nanos, RacePayloads.@Nullable RaceReportC2S report) {
    }

    private static @Nullable String fly(MinecraftServer server, Scene scene) {
        ServerPlayer player = server.getPlayerList().getPlayers().getFirst();
        CourseRegistry registry = CourseRegistry.get();
        CourseRegistry.Course course = registry == null ? null : registry.course(scene.course());
        if (course == null) {
            return "Kurs fehlt";
        }
        long t0 = scene.startNanos();
        long end = t0 + LAP_NANOS;
        List<Send> sends = new ArrayList<>();
        for (long t = scene.first(); t <= end + 600L * MS; t += SEND_NANOS) {
            sends.add(new Send(t, null));
        }
        sends.add(new Send(t0 + 1L, new RacePayloads.RaceReportC2S(RacePayloads.ReportKind.START, course.id, 0, 0L, 0L)));
        sends.add(new Send(end + 1L, new RacePayloads.RaceReportC2S(RacePayloads.ReportKind.LAP, course.id, 1, LAP_NANOS, 0L)));
        sends.add(new Send(end + 400L * MS, new RacePayloads.RaceReportC2S(RacePayloads.ReportKind.ABORT, course.id, 0, 0L, 0L)));
        sends.sort((p, q) -> Long.compare(p.nanos(), q.nanos()));
        long[] times = new long[sends.size()];
        for (int i = 0; i < times.length; i++) {
            times[i] = sends.get(i).nanos();
        }
        long[] arrivals = BadNetwork.arrivals(times, BadNetwork.PILOT, t0 + 700L * MS, end - 700L * MS);
        Vec3 last = null;
        RaceServer.Verdict lap = null;
        List<Long> moves = new ArrayList<>();
        for (int i = 0; i < sends.size(); i++) {
            Send send = sends.get(i);
            if (send.report() != null) {
                RaceServer.Verdict verdict = RaceServer.handle(send.report(), player, arrivals[i]);
                if (send.report().kind() == RacePayloads.ReportKind.LAP) {
                    lap = verdict;
                    RaceServer.flush(player, arrivals[i]);
                } else if (send.report().kind() == RacePayloads.ReportKind.START && verdict != RaceServer.Verdict.ACCEPTED) {
                    return "Start abgelehnt: " + verdict;
                }
                continue;
            }
            Vec3 at = scene.position(send.nanos());
            RaceServer.onDroneMoved(player, last == null ? at : last, at, scene.orientation(send.nanos()), arrivals[i]);
            last = at;
            moves.add(arrivals[i]);
        }
        Leaderboard.Entry entry = registry.board(course).best(player.getUUID(), null);
        if (entry == null) {
            return "Zeit fehlt in der Bestenliste (Rundenmeldung: " + lap + ")";
        }
        GhostKey key = GhostKey.of(entry);
        EntryGhost ghost = registry.entryGhosts().get(course.key(), key);
        if (ghost == null) {
            return "Geist der Runde fehlt";
        }
        GhostTrack track;
        try {
            track = ghost.decodeTrack();
        } catch (IOException e) {
            return "Geist nicht lesbar: " + e.getMessage();
        }
        long[] moved = new long[moves.size()];
        for (int i = 0; i < moved.length; i++) {
            moved[i] = moves.get(i);
        }
        FLIGHT.set(new Flight(key, measure(track, scene.speed()).text(), BadNetwork.stalls(moved, SEND_NANOS)));
        return null;
    }

    private static EntryGhost.@Nullable Info stored(GhostKey key) {
        Scene scene = SCENE.get();
        CourseRegistry registry = CourseRegistry.get();
        CourseRegistry.Course course = registry == null || scene == null ? null : registry.course(scene.course());
        return course == null ? null : registry.entryGhosts().info(course.key(), key);
    }

    private static void cleanup(SelfTest.StepContext ctx) {
        Scene scene = SCENE.getAndSet(null);
        if (scene == null) {
            return;
        }
        ctx.onServer(server -> {
            ServerLevel level = server.getPlayerList().getPlayers().getFirst().level();
            for (int entity : new int[]{scene.drone(), scene.gateA(), scene.gateB()}) {
                if (level.getEntity(entity) instanceof QuadcopterEntity drone) {
                    drone.setPilot(null);
                    drone.discard();
                } else if (level.getEntity(entity) != null) {
                    level.getEntity(entity).discard();
                }
            }
            CourseRegistry registry = CourseRegistry.get();
            if (registry != null) {
                registry.deleteCourse(scene.course());
            }
        });
    }
}
