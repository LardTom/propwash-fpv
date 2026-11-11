package de.lardtom.propwash.race.client;

import de.lardtom.propwash.dev.RaceRulesJoinCheck;
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
import de.lardtom.propwash.race.GhostKey;
import de.lardtom.propwash.race.GhostRecorder;
import de.lardtom.propwash.race.GhostTrack;
import de.lardtom.propwash.race.GhostUploads;
import de.lardtom.propwash.race.LapTracker;
import de.lardtom.propwash.race.Leaderboard;
import de.lardtom.propwash.race.RaceFormat;
import de.lardtom.propwash.race.RaceModule;
import de.lardtom.propwash.race.RacePayloads;
import de.lardtom.propwash.race.RaceServer;
import net.minecraft.client.Minecraft;
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
import java.util.Locale;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;

public final class RaceStartAgainSelfTests {
    public static final String STEP = "race: Replay nach erneutem Durchflug durch das Startgate beginnt mit der Uhr";
    private static final long MS = 1_000_000L;
    private static final long SECOND = 1_000L * MS;
    private static final long FRAME_NANOS = SECOND / 60L;
    private static final long SEND_NANOS = 50L * MS;
    private static final long LATENCY = 100L * MS;
    private static final double SPEED = 20.0;
    private static final double NEAR = 1.0;
    private static final long SERVER_SLACK = 60L * MS;
    private static final int WAIT_TICKS = 160;
    private static final long AHEAD_NANOS = 120L * SECOND;
    private static final AtomicReference<Scene> SCENE = new AtomicReference<>();
    private static final AtomicReference<Timed> TIMED = new AtomicReference<>();
    private static final AtomicReference<Fallback> FALLBACK = new AtomicReference<>();
    private static final AtomicReference<String> PROBLEM = new AtomicReference<>();
    private static final AtomicReference<EntryGhost.Info> STORED = new AtomicReference<>();
    private static final AtomicReference<String> UPLOAD = new AtomicReference<>("");

    private RaceStartAgainSelfTests() {
    }

    record Scene(int course, int[] entities, String frame, int layout, List<GateGeometry> gates, List<Vec3> path, double[] at, long t0) {
        long end() {
            return this.t0 + Math.round(this.at[this.at.length - 1] / SPEED * 1.0E9);
        }

        Vec3 position(long nanos) {
            double distance = Math.clamp((nanos - this.t0) * 1.0E-9 * SPEED, 0.0, this.at[this.at.length - 1]);
            int leg = 1;
            while (leg < this.at.length - 1 && this.at[leg] < distance) {
                leg++;
            }
            Vec3 from = this.path.get(leg - 1);
            Vec3 to = this.path.get(leg);
            double span = this.at[leg] - this.at[leg - 1];
            return from.lerp(to, span <= 0.0 ? 1.0 : Math.clamp((distance - this.at[leg - 1]) / span, 0.0, 1.0));
        }

        Quaternionf orientation(long nanos) {
            Vec3 a = this.position(nanos - 20L * MS);
            Vec3 b = this.position(nanos + 20L * MS);
            return new Quaternionf().rotateY((float) Math.atan2(-(b.x - a.x), -(b.z - a.z))).rotateX((float) Math.toRadians(-25.0));
        }
    }

    record Timed(long startNanos, long againNanos, long lapNanos, int starts, int missed) {
        long endNanos() {
            return this.startNanos + this.lapNanos;
        }
    }

    record Fallback(GhostKey key, long entryNanos, long ghostNanos, @Nullable String follows) {
    }

    public static void register() {
        SelfTest.clientStep(STEP, 900, RaceStartAgainSelfTests::step);
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
                TIMED.set(null);
                FALLBACK.set(null);
                STORED.set(null);
                UPLOAD.set("");
                ctx.state().put("future", ctx.onServer(server -> PROBLEM.set(build(server))));
                ctx.state().put("stage", "record");
                return StepResult.pending();
            }
            case "record" -> {
                Scene scene = SCENE.get();
                if (scene == null) {
                    return StepResult.fail("Szene fehlt");
                }
                Timed timed = record(scene);
                if (timed.starts() != 1 || timed.missed() < 1 || timed.againNanos() <= 3L * SECOND) {
                    cleanup(ctx);
                    return StepResult.fail("Flug passt nicht: " + timed.starts() + " Starts, " + timed.missed() + " ausgelassen, zweiter Start-Durchflug nach "
                            + timed.againNanos() / MS + " ms");
                }
                TIMED.set(timed);
                ctx.state().put("future", ctx.onServer(server -> PROBLEM.set(fly(server, scene, timed))));
                ctx.state().put("stage", "fallback");
                return StepResult.pending();
            }
            case "fallback" -> {
                Fallback fallback = FALLBACK.get();
                Timed timed = TIMED.get();
                if (fallback == null || timed == null) {
                    cleanup(ctx);
                    return StepResult.fail("Runde nicht gespeichert");
                }
                if (fallback.entryNanos() != timed.lapNanos()) {
                    cleanup(ctx);
                    return StepResult.fail("Gewertete Zeit " + RaceFormat.time(fallback.entryNanos()) + " statt der Uhr " + RaceFormat.time(timed.lapNanos()));
                }
                if (Math.abs(fallback.ghostNanos() - timed.lapNanos()) > SERVER_SLACK || fallback.follows() != null) {
                    ctx.state().put("server", "Spur des Servers passt nicht zur Zeit " + RaceFormat.time(timed.lapNanos()) + ": Dauer "
                            + RaceFormat.time(fallback.ghostNanos()) + (fallback.follows() == null ? "" : ", " + fallback.follows()));
                }
                ctx.state().put("stage", "wait");
                ctx.state().put("since", ctx.tick());
                return StepResult.pending();
            }
            case "wait" -> {
                EntryGhost.Info info = STORED.get();
                int waited = ctx.tick() - (int) ctx.state().get("since");
                if (info != null && info.source() != EntryGhost.Source.SERVER || waited > WAIT_TICKS) {
                    ctx.state().put("future", ctx.onServer(server -> {
                        GhostUploads.Outcome last = GhostUploads.last();
                        UPLOAD.set(last == null ? "kein Upload" : last.result().name());
                        Scene scene = SCENE.get();
                        if (scene != null && !RaceServer.sendLeaderboard(server.getPlayerList().getPlayers().getFirst(), scene.course(), false)) {
                            PROBLEM.set("Bestenliste nicht geschickt");
                        }
                    }));
                    ctx.state().put("stage", "open");
                    ctx.state().put("since", ctx.tick());
                    return StepResult.pending();
                }
                if (waited % 10 == 0) {
                    Fallback fallback = FALLBACK.get();
                    ctx.state().put("future", ctx.onServer(server -> STORED.set(fallback == null ? null : stored(fallback.key()))));
                }
                return StepResult.pending();
            }
            case "open" -> {
                Scene scene = SCENE.get();
                Fallback fallback = FALLBACK.get();
                RacePayloads.BoardRow row = scene == null || fallback == null ? null : row(scene.course(), fallback.entryNanos());
                if (row == null) {
                    if (ctx.tick() - (int) ctx.state().get("since") > 100) {
                        cleanup(ctx);
                        return StepResult.fail("Zeile in der Bestenliste fehlt");
                    }
                    return StepResult.pending();
                }
                GhostDownloads.INSTANCE.forget(scene.course(), fallback.key());
                Minecraft.getInstance().gui.setScreen(new ReplayScreen(null, scene.course(), row));
                ctx.state().put("stage", "replay");
                ctx.state().put("since", ctx.tick());
                return StepResult.pending();
            }
            case "replay" -> {
                ReplayScreen replay = ReplayScreen.active();
                if (replay == null || !replay.loaded()) {
                    if (replay != null && replay.failure() != null) {
                        cleanup(ctx);
                        return StepResult.fail("Replay nicht geladen: " + replay.failure());
                    }
                    if (ctx.tick() - (int) ctx.state().get("since") > 300) {
                        cleanup(ctx);
                        return StepResult.fail("Replay lädt nicht");
                    }
                    return StepResult.pending();
                }
                String problem = checkReplay(ctx, replay);
                Minecraft.getInstance().gui.setScreen(null);
                cleanup(ctx);
                String server = (String) ctx.state().get("server");
                if (server != null) {
                    return StepResult.fail(problem == null ? server : server + "; " + problem);
                }
                return problem == null ? StepResult.pass() : StepResult.fail(problem);
            }
            default -> {
                return StepResult.fail("Unbekannte Phase " + stage);
            }
        }
    }

    private static @Nullable String checkReplay(SelfTest.StepContext ctx, ReplayScreen replay) {
        ReplaySession session = ReplaySession.INSTANCE;
        Scene scene = SCENE.get();
        Timed timed = TIMED.get();
        Fallback fallback = FALLBACK.get();
        GhostTrack track = session.track();
        EntryGhost ghost = session.ghost();
        RacePayloads.BoardRow row = session.row();
        if (scene == null || timed == null || fallback == null || track == null || ghost == null || row == null) {
            return "Replay ohne Spur";
        }
        session.setPlaying(false);
        String follows = follows(scene, track, timed.startNanos(), timed.lapNanos(), "Replay");
        session.seek(session.end());
        long clockAtEnd = session.lapTime();
        String shown = replay.clock();
        session.seek(0L);
        long clockAtStart = session.lapTime();
        ctx.log(String.format(Locale.ROOT, "Uhr ab dem ersten Start-Durchflug %s, zweiter Durchflug nach %s; Bestenliste %s; Server-Spur %s; Upload %s, "
                        + "Herkunft %s; Replay mit %d ms Vorlauf bis %s, Uhr am Ende %s", RaceFormat.time(timed.lapNanos()), RaceFormat.time(timed.againNanos()),
                RaceFormat.time(row.nanos()), RaceFormat.time(fallback.ghostNanos()), UPLOAD.get(), ghost.info().source(),
                -session.begin() / MS, RaceFormat.time(session.end()), shown));
        if (row.nanos() != timed.lapNanos()) {
            return "Bestenliste zeigt " + RaceFormat.time(row.nanos()) + " statt " + RaceFormat.time(timed.lapNanos());
        }
        if (ghost.info().source() != EntryGhost.Source.PILOT) {
            return "Spur des Piloten nicht übernommen (" + UPLOAD.get() + "), Replay zeigt die Spur des Servers";
        }
        if (Math.abs(session.end() - timed.lapNanos()) > 2L * track.intervalNanos() + 5L * MS) {
            return "Replay dauert " + RaceFormat.time(session.end()) + " statt " + RaceFormat.time(timed.lapNanos());
        }
        if (clockAtStart != 0L || clockAtEnd != row.nanos() || !RaceFormat.time(row.nanos()).equals(shown)) {
            return "Uhr im Replay: am Startgate " + RaceFormat.time(clockAtStart) + ", am Ende " + shown + " statt " + RaceFormat.time(row.nanos());
        }
        return follows;
    }

    private static @Nullable String follows(Scene scene, GhostTrack track, long startNanos, long lapNanos, String what) {
        double[] at = new double[3];
        Quaternionf rotation = new Quaternionf();
        for (long t = 0L; t <= Math.min(track.durationNanos(), lapNanos); t += 250L * MS) {
            if (!track.pose(t, at, rotation)) {
                return what + ": keine Pose bei " + RaceFormat.time(t);
            }
            Vec3 want = scene.position(startNanos + t);
            double off = want.distanceTo(new Vec3(at[0], at[1], at[2]));
            if (off > NEAR) {
                return String.format(Locale.ROOT, "%s bei %s: %.1f Blöcke neben dem Flug ab dem ersten Start-Durchflug", what, RaceFormat.time(t), off);
            }
        }
        return null;
    }

    private static Timed record(Scene scene) {
        LapTracker tracker = new LapTracker();
        tracker.setCourse(scene.course(), scene.gates(), false, scene.t0());
        tracker.setLapTarget(1);
        GhostRecorder recorder = ClientRaceTracker.INSTANCE.recorder();
        recorder.reset();
        long start = LapTracker.NONE;
        long again = LapTracker.NONE;
        long lap = LapTracker.NONE;
        int starts = 0;
        int missed = 0;
        for (long t = scene.t0(); t <= scene.end(); t += FRAME_NANOS) {
            Vec3 at = scene.position(t);
            tracker.update(t, at.x, at.y, at.z);
            recorder.sample(t, at.x, at.y, at.z, scene.orientation(t));
            for (LapTracker.Event event : tracker.drainEvents()) {
                switch (event.type()) {
                    case START, GO -> {
                        starts++;
                        start = event.nanos();
                        recorder.onLapStart(scene.course(), event.nanos());
                    }
                    case MISSED -> {
                        missed++;
                        if (event.valueNanos() == 0L && again == LapTracker.NONE) {
                            again = event.nanos() - start;
                        }
                    }
                    case LAP -> {
                        lap = event.valueNanos();
                        recorder.onLapComplete(event.valueNanos(), event.nanos(), scene.frame(), scene.layout());
                    }
                    case FINISH, ABORT -> recorder.stop();
                    default -> {
                    }
                }
            }
        }
        recorder.stop();
        return new Timed(start, again, lap, starts, missed);
    }

    private static @Nullable String build(MinecraftServer server) {
        ServerPlayer player = server.getPlayerList().getPlayers().getFirst();
        ServerLevel level = player.level();
        CourseRegistry registry = CourseRegistry.get();
        if (registry == null || !registry.entryGhosts().enabled()) {
            return "Ablage der Geister fehlt";
        }
        GateGeometry probe = GateGeometry.of(0.0, 0.0, 0.0, 0.0F, GateSize.MEDIUM);
        Vec3 n = new Vec3(probe.normalX(), 0.0, probe.normalZ()).normalize();
        Vec3 side = new Vec3(n.z, 0.0, -n.x);
        Vec3 a = new Vec3(player.getX() + 30.0, 0.0, player.getZ() - 20.0);
        Vec3 b = a.add(n.scale(30.0));
        Vec3 c = a.add(n.scale(15.0)).add(side.scale(20.0));
        int top = 0;
        for (Vec3 spot : List.of(a, b, c)) {
            top = Math.max(top, level.getHeight(Heightmap.Types.MOTION_BLOCKING, (int) Math.floor(spot.x), (int) Math.floor(spot.z)));
        }
        GateEntity ga = gate(level, a, top, 0.0F);
        GateEntity gb = gate(level, b, top, 0.0F);
        GateEntity gc = gate(level, c, top, 180.0F);
        ItemStack tool = new ItemStack(RaceModule.COURSE_TOOL.get());
        CourseToolItem.useOnGate(tool, player, ga);
        CourseToolItem.useOnGate(tool, player, gb);
        CourseToolItem.useOnGate(tool, player, gc);
        int id = CourseToolItem.courseOf(tool);
        CourseRegistry.Course course = registry.course(id);
        if (course == null || course.gates.size() != 3) {
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
        List<Vec3> path = path(gates);
        if (path == null) {
            return "Gates stehen nicht wie geplant: " + gates;
        }
        double[] at = new double[path.size()];
        for (int i = 1; i < path.size(); i++) {
            at[i] = at[i - 1] + path.get(i).distanceTo(path.get(i - 1));
        }
        long flight = Math.round(at[at.length - 1] / SPEED * 1.0E9);
        long t0 = RaceRulesJoinCheck.nextStart(AHEAD_NANOS + flight + 5L * SECOND) + AHEAD_NANOS;
        SCENE.set(new Scene(id, new int[]{drone.getId(), ga.getId(), gb.getId(), gc.getId()}, drone.build().frame().key(), course.toData().layoutHash(), gates,
                path, at, t0));
        return null;
    }

    private static @Nullable List<Vec3> path(List<GateGeometry> gates) {
        GateGeometry ga = gates.get(0);
        GateGeometry gc = gates.get(2);
        Vec3 n = new Vec3(ga.normalX(), 0.0, ga.normalZ()).normalize();
        if (new Vec3(gc.normalX(), 0.0, gc.normalZ()).normalize().dot(n) > -0.99) {
            return null;
        }
        Vec3 side = new Vec3(n.z, 0.0, -n.x);
        Vec3 a = new Vec3(ga.x(), ga.centerY(), ga.z());
        Vec3 c = new Vec3(gc.x(), ga.centerY(), gc.z());
        if (c.subtract(a).dot(side) < 0.0) {
            side = side.scale(-1.0);
        }
        List<Vec3> path = new ArrayList<>();
        path.add(a.subtract(n.scale(15.0)));
        path.add(a.add(n));
        path.add(a.add(n.scale(31.0)));
        path.add(a.add(n.scale(35.0)).subtract(side.scale(10.0)));
        path.add(a.subtract(n.scale(12.0)).subtract(side.scale(10.0)));
        path.add(a.subtract(n.scale(12.0)));
        path.add(a.add(n));
        path.add(a.add(n.scale(31.0)));
        path.add(a.add(n.scale(35.0)).add(side.scale(20.0)));
        path.add(a.subtract(n.scale(8.0)).add(side.scale(20.0)));
        path.add(a.subtract(n.scale(8.0)));
        path.add(a.add(n));
        path.add(a.add(n.scale(10.0)));
        return path;
    }

    private static GateEntity gate(ServerLevel level, Vec3 at, int top, float yaw) {
        GateEntity gate = RaceModule.GATE.get().create(level, EntitySpawnReason.COMMAND);
        if (gate == null) {
            throw new IllegalStateException("Gate nicht erzeugt");
        }
        gate.setSize(GateSize.MEDIUM);
        gate.snapTo(at.x, top, at.z, yaw, 0.0F);
        if (!level.addFreshEntity(gate)) {
            throw new IllegalStateException("Gate nicht gespawnt");
        }
        return gate;
    }

    private static @Nullable String fly(MinecraftServer server, Scene scene, Timed timed) {
        ServerPlayer player = server.getPlayerList().getPlayers().getFirst();
        CourseRegistry registry = CourseRegistry.get();
        CourseRegistry.Course course = registry == null ? null : registry.course(scene.course());
        if (course == null) {
            return "Kurs fehlt";
        }
        boolean started = false;
        boolean reported = false;
        RaceServer.Verdict lap = null;
        Vec3 last = null;
        for (long t = scene.t0(); t <= scene.end() + 600L * MS; t += SEND_NANOS) {
            if (!started && t >= timed.startNanos()) {
                RaceServer.Verdict verdict = RaceServer.handle(new RacePayloads.RaceReportC2S(RacePayloads.ReportKind.START, course.id, 0, 0L, 0L), player,
                        timed.startNanos() + LATENCY + MS);
                if (verdict != RaceServer.Verdict.ACCEPTED) {
                    return "Start abgelehnt: " + verdict;
                }
                started = true;
            }
            if (started && !reported && t >= timed.endNanos()) {
                lap = RaceServer.handle(new RacePayloads.RaceReportC2S(RacePayloads.ReportKind.LAP, course.id, 1, timed.lapNanos(), 0L), player,
                        timed.endNanos() + LATENCY + MS);
                reported = true;
            }
            Vec3 at = scene.position(t);
            RaceServer.onDroneMoved(player, last == null ? at : last, at, scene.orientation(t), t + LATENCY);
            last = at;
        }
        RaceServer.flush(player, scene.end() + 700L * MS + LATENCY);
        RaceServer.handle(new RacePayloads.RaceReportC2S(RacePayloads.ReportKind.ABORT, course.id, 0, 0L, 0L), player, scene.end() + 800L * MS + LATENCY);
        Leaderboard.Entry entry = registry.board(course).best(player.getUUID(), null);
        if (entry == null) {
            return "Zeit fehlt in der Bestenliste (Rundenmeldung: " + lap + ")";
        }
        GhostKey key = GhostKey.of(entry);
        EntryGhost ghost = registry.entryGhosts().get(course.key(), key);
        if (ghost == null || ghost.info().source() != EntryGhost.Source.SERVER) {
            return "Geist des Servers fehlt: " + (ghost == null ? "keiner" : ghost.info().source());
        }
        GhostTrack track;
        try {
            track = ghost.decodeTrack();
        } catch (IOException e) {
            return "Geist nicht lesbar: " + e.getMessage();
        }
        FALLBACK.set(new Fallback(key, entry.nanos(), track.durationNanos(), follows(scene, track, timed.startNanos(), timed.lapNanos(), "Spur des Servers")));
        return null;
    }

    private static RacePayloads.@Nullable BoardRow row(int courseId, long nanos) {
        RacePayloads.LeaderboardS2C board = ClientLeaderboards.get(courseId);
        if (board == null) {
            return null;
        }
        for (RacePayloads.BoardRow row : board.rows()) {
            if (row.self() && !row.assisted() && row.nanos() == nanos) {
                return row;
            }
        }
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
            for (int entity : scene.entities()) {
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
