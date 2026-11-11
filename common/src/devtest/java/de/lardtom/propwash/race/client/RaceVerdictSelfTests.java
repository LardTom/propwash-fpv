package de.lardtom.propwash.race.client;

import de.lardtom.propwash.dev.RaceDataJoinCheck;
import de.lardtom.propwash.dev.SelfTest;
import de.lardtom.propwash.dev.SelfTest.StepResult;
import de.lardtom.propwash.dev.client.Screenshots;
import de.lardtom.propwash.drone.DroneModule;
import de.lardtom.propwash.drone.QuadcopterEntity;
import de.lardtom.propwash.race.CourseData;
import de.lardtom.propwash.race.CourseElement;
import de.lardtom.propwash.race.CourseRegistry;
import de.lardtom.propwash.race.CourseToolItem;
import de.lardtom.propwash.race.GateEntity;
import de.lardtom.propwash.race.GateGeometry;
import de.lardtom.propwash.race.GateMounting;
import de.lardtom.propwash.race.GateSize;
import de.lardtom.propwash.race.GateTilt;
import de.lardtom.propwash.race.LapTracker;
import de.lardtom.propwash.race.PassZoneOverlap;
import de.lardtom.propwash.race.RaceModule;
import de.lardtom.propwash.race.RacePayloads;
import de.lardtom.propwash.race.RaceServer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;

public final class RaceVerdictSelfTests {
    private static final String GROUP = "race-verdict: ";
    private static final String GERMAN = "de_de";
    private static final long MS = 1_000_000L;
    private static final double SPEED = 10.0;
    private static final double FLY = 2.0;
    private static final double GATE_Z = 12.0;
    private static final double POLE_X = -3.0;
    private static final int LAPS = 3;
    private static final int WAVE_TICKS = 40;
    private static final int[] WAVE_PHASES = {7, 14, 0};
    private static final double[][] SHORTCUT = {{0.0, FLY, -3.0}, {0.0, FLY, 14.0}, {6.0, FLY, 14.0}, {6.0, FLY, -3.0}, {0.0, FLY, -3.0}};
    private static final double[][] FULL = {{0.0, FLY, -3.0}, {-2.25, FLY, 10.0}, {-2.25, FLY, 14.0}, {-5.0, FLY, 14.0}, {-5.0, FLY, 8.0}, {0.0, FLY, 8.0}, {0.0, FLY, 14.0},
            {6.0, FLY, 14.0}, {6.0, FLY, -3.0}, {0.0, FLY, -3.0}};
    private static final AtomicReference<Site> SITE = new AtomicReference<>();
    private static final AtomicReference<String> RESULT = new AtomicReference<>("");
    private static final AtomicLong CLOCK = new AtomicLong(Long.MIN_VALUE);

    private record Site(int course, Vec3 origin, List<Integer> gates, int pole, Vec3 home) {
    }

    private record Pending(CompletableFuture<Void> future, AtomicReference<String> result) {
    }

    private RaceVerdictSelfTests() {
    }

    public static void register() {
        SelfTest.clientStep(GROUP + "Kurs mit Stange direkt neben dem nächsten Gate – Warnung mit Knopf im Chat", 600, RaceVerdictSelfTests::setupStep);
        SelfTest.clientStep(GROUP + "Abkürzung zählt auf beiden Seiten – drei Runden, Urteil „gewertet“ am Client, Bestenliste", 400, RaceVerdictSelfTests::shortcutStep);
        SelfTest.clientStep(GROUP + "Knopf kürzt die Reichweite – Warnung weg, Kurs beim Client aktuell", 400, RaceVerdictSelfTests::trimStep);
        SelfTest.clientStep(GROUP + "Gekürzt: Abkürzung meldet „verpasst“ und zählt nicht, spätere Runden zählen wieder", 400, RaceVerdictSelfTests::verdictStep);
        SelfTest.clientStep(GROUP + "Gate-Menü warnt bei langer Reichweite und kürzt mit dem Knopf", 400, RaceVerdictSelfTests::menuStep);
        SelfTest.clientStep(GROUP + "Ideallinie je Kurs abschaltbar – Client-Einstellung und Kurs-Merker", 400, RaceVerdictSelfTests::raceLineStep);
        SelfTest.clientStep(GROUP + "Lauflicht vor dem Gate wandert zum Gate", WAVE_TICKS + 40, RaceVerdictSelfTests::waveStep);
        if (Screenshots.enabled()) {
            for (int phase : WAVE_PHASES) {
                Screenshots.step(GROUP + "Bildfolge Lauflicht, Takt " + phase, "race_verdict_chevrons_" + phase, 4, new WaveScene(phase));
            }
            shots("en", "");
            String back = GROUP + "Bildschirmfotos Sprache zurückstellen";
            SelfTest.switchStep(GROUP + "Bildschirmfotos Sprache Deutsch", back, 2400, ctx -> Screenshots.language(ctx, GERMAN));
            shots("de", " deutsch");
            SelfTest.clientStep(back, 2400, Screenshots::restoreLanguage);
        }
        SelfTest.clientStep(GROUP + "Aufräumen", 300, RaceVerdictSelfTests::cleanupStep);
    }

    private static void shots(String language, String label) {
        Screenshots.step(GROUP + "Bildschirmfoto Urteil im HUD und Chat" + label, "race_verdict_hud_" + language, 20, new VerdictScene());
        Screenshots.step(GROUP + "Bildschirmfoto Gate-Menü mit Warnung" + label, "race_verdict_gate_menu_" + language, 20, new MenuScene());
        Screenshots.step(GROUP + "Bildschirmfoto Kurs-Fenster mit Ideallinie" + label, "race_verdict_course_menu_" + language, 20, new CourseScene());
    }

    private static ServerPlayer player(MinecraftServer server) {
        return server.getPlayerList().getPlayers().getFirst();
    }

    private static @Nullable StepResult once(SelfTest.StepContext ctx, String key, Function<MinecraftServer, String> action) {
        Object stored = ctx.state().get(key);
        if (stored == null) {
            AtomicReference<String> result = new AtomicReference<>();
            CompletableFuture<Void> future = ctx.onServer(server -> result.set(action.apply(server)));
            ctx.state().put(key, new Pending(future, result));
            return StepResult.pending();
        }
        Pending pending = (Pending) stored;
        if (pending.future().isCompletedExceptionally()) {
            return StepResult.fail(key + ": " + pending.future().exceptionNow());
        }
        if (!pending.future().isDone()) {
            return StepResult.pending();
        }
        String problem = pending.result().get();
        return problem == null ? null : StepResult.fail(key + ": " + problem);
    }

    private static GateEntity spawn(ServerLevel level, GateGeometry geometry) {
        GateEntity gate = RaceModule.GATE.get().create(level, EntitySpawnReason.COMMAND);
        if (gate == null) {
            throw new IllegalStateException("Element nicht erzeugt");
        }
        gate.setSize(GateSize.MEDIUM);
        gate.applyGeometry(geometry);
        if (!level.addFreshEntity(gate)) {
            throw new IllegalStateException("Element nicht gespawnt");
        }
        return gate;
    }

    private static List<Vec3> path(Vec3 origin, double[][] points) {
        List<Vec3> out = new ArrayList<>(points.length);
        for (double[] point : points) {
            out.add(origin.add(point[0], point[1], point[2]));
        }
        return out;
    }

    private static List<Vec3> laps(Vec3 origin, double[][]... rounds) {
        List<Vec3> out = new ArrayList<>();
        for (double[][] round : rounds) {
            out.addAll(path(origin, round));
        }
        out.add(origin.add(0.0, FLY, 2.0));
        return out;
    }

    private static CourseRegistry.@Nullable Course course(Site site) {
        CourseRegistry registry = CourseRegistry.get();
        return registry == null ? null : registry.course(site.course());
    }

    private static StepResult setupStep(SelfTest.StepContext ctx) {
        StepResult wait = once(ctx, "Aufbau", server -> {
            ServerPlayer player = player(server);
            ServerLevel level = player.level();
            Vec3 origin = RaceDataJoinCheck.origin(player);
            Vec3 home = player.position();
            GateEntity start = RaceDataJoinCheck.gate(level, origin);
            GateEntity pole = spawn(level, GateGeometry.element(origin.x + POLE_X, origin.y, origin.z + GATE_Z, 0.0F, CourseElement.POLE, CourseElement.LEFT, 4.0, 3.0, GateTilt.NONE));
            GateEntity next = RaceDataJoinCheck.gate(level, origin.add(0.0, 0.0, GATE_Z));
            ItemStack tool = new ItemStack(RaceModule.COURSE_TOOL.get());
            player.setYRot(0.0F);
            player.setXRot(0.0F);
            player.setYHeadRot(0.0F);
            CourseToolItem.useOnGate(tool, player, start);
            CourseToolItem.useOnGate(tool, player, pole);
            CourseToolItem.useOnGate(tool, player, next);
            CourseRegistry registry = CourseRegistry.get();
            CourseRegistry.Course course = registry == null ? null : registry.course(CourseToolItem.courseOf(tool));
            if (course == null || course.gates.size() != 3) {
                return "Kurs nicht angelegt";
            }
            registry.setLaps(course, LAPS);
            SITE.set(new Site(course.id, origin, List.of(start.getId(), pole.getId(), next.getId()), pole.getId(), home));
            return null;
        });
        if (wait != null) {
            return wait;
        }
        Site site = SITE.get();
        Minecraft mc = Minecraft.getInstance();
        CourseData data = ClientCourses.get(site.course());
        if (data == null || data.gates().size() != 3) {
            return ctx.tick() > 400 ? StepResult.fail("Kurs kommt beim Client nicht an") : StepResult.pending();
        }
        String command = "/propwash race trim " + site.course() + " 2";
        for (Component message : RaceDataChat.messages(mc)) {
            if (clicks(message, command)) {
                ctx.log("Warnung im Chat: " + message.getString());
                return StepResult.pass();
            }
        }
        return ctx.tick() > 400 ? StepResult.fail("Warnung mit Knopf fehlt im Chat, gesucht: " + command) : StepResult.pending();
    }

    private static boolean clicks(Component message, String command) {
        if (message.getStyle().getClickEvent() instanceof ClickEvent.RunCommand run && command.equals(run.command())) {
            return true;
        }
        for (Component sibling : message.getSiblings()) {
            if (clicks(sibling, command)) {
                return true;
            }
        }
        return false;
    }

    private static @Nullable String flyClient(Minecraft mc, Site site, List<Vec3> points, int target, List<LapTracker.Event> events) {
        RaceConfig config = RaceConfig.CONFIG.get();
        boolean report = config.reportResults;
        config.reportResults = false;
        ClientRaceTracker tracker = ClientRaceTracker.INSTANCE;
        tracker.setEventListener(events::add);
        try {
            tracker.tracker().abort(tracker.now(), LapTracker.AbortReason.RESTART);
            tracker.tracker().resetPosition();
            tracker.selectCourseAt(mc, points.getFirst());
            if (tracker.courseId() != site.course()) {
                return "Kurs nicht gewählt: " + tracker.courseId();
            }
            tracker.tracker().setLapTarget(target);
            tracker.ghosts().setPersist(false);
            tracker.ghosts().clear();
            long now = Math.max(tracker.now(), CLOCK.get());
            tracker.feed(now, points.getFirst());
            for (int i = 1; i < points.size(); i++) {
                Vec3 from = points.get(i - 1);
                Vec3 to = points.get(i);
                double length = from.distanceTo(to);
                int steps = Math.max(1, (int) Math.ceil(length / 0.25));
                long stepNanos = Math.round(length / SPEED / steps * 1.0E9);
                for (int s = 1; s <= steps; s++) {
                    now += stepNanos;
                    tracker.feed(now, from.lerp(to, s / (double) steps));
                }
            }
            CLOCK.set(now + LapTracker.RESTART_COOLDOWN_NANOS + LapTracker.SECOND);
            return null;
        } finally {
            tracker.tracker().abort(tracker.now(), LapTracker.AbortReason.RESTART);
            tracker.tracker().drainEvents();
            tracker.setEventListener(null);
            config.reportResults = report;
            tracker.ghosts().setPersist(true);
        }
    }

    private static long count(List<LapTracker.Event> events, LapTracker.EventType type) {
        return events.stream().filter(event -> event.type() == type).count();
    }

    private static final class ServerRun {
        final CourseRegistry.Course course;
        final ServerPlayer pilot;
        final QuadcopterEntity drone;
        final GateGeometry start;
        long now;
        long lastCross = Long.MIN_VALUE;
        long total;
        int lap;
        final List<RaceServer.Verdict> verdicts = new ArrayList<>();

        ServerRun(CourseRegistry.Course course, ServerPlayer pilot, Vec3 begin) {
            this.course = course;
            this.pilot = pilot;
            this.start = course.toData().geometry().getFirst();
            ServerLevel level = pilot.level();
            this.drone = DroneModule.DRONE.get().create(level, EntitySpawnReason.COMMAND);
            if (this.drone == null) {
                throw new IllegalStateException("Drohne nicht erzeugt");
            }
            this.drone.snapTo(begin.x, begin.y, begin.z, 0.0F, 0.0F);
            this.drone.setNoGravity(true);
            this.drone.setPilot(pilot);
            if (!level.addFreshEntity(this.drone)) {
                throw new IllegalStateException("Drohne nicht gespawnt");
            }
            this.now = Math.max(System.nanoTime() + 60_000L * MS, CLOCK.get());
            RaceServer.Verdict started = RaceServer.handle(new RacePayloads.RaceReportC2S(RacePayloads.ReportKind.START, course.id, 0, 0L, 0L), pilot, this.now);
            if (started != RaceServer.Verdict.ACCEPTED) {
                throw new IllegalStateException("Start abgelehnt: " + started);
            }
        }

        void fly(List<Vec3> points) {
            for (int i = 1; i < points.size(); i++) {
                Vec3 from = points.get(i - 1);
                Vec3 to = points.get(i);
                double length = from.distanceTo(to);
                int steps = Math.max(1, (int) Math.ceil(length / 0.5));
                long stepNanos = Math.round(length / SPEED / steps * 1.0E9);
                for (int s = 1; s <= steps; s++) {
                    Vec3 a = from.lerp(to, (s - 1) / (double) steps);
                    Vec3 b = from.lerp(to, s / (double) steps);
                    double t = this.start.crossing(a.x, a.y, a.z, b.x, b.y, b.z, true);
                    long next = this.now + stepNanos;
                    RaceServer.onDroneMoved(this.pilot, a, b, next);
                    if (t >= 0.0) {
                        this.cross(this.now + Math.round(stepNanos * t), next);
                    }
                    this.now = next;
                }
            }
        }

        private void cross(long at, long reportAt) {
            if (this.lastCross != Long.MIN_VALUE) {
                this.lap++;
                long lapNanos = at - this.lastCross;
                this.total += lapNanos;
                this.drone.snapTo(this.start.centerX(), this.start.centerY(), this.start.centerZ(), 0.0F, 0.0F);
                this.verdicts.add(RaceServer.handle(new RacePayloads.RaceReportC2S(RacePayloads.ReportKind.LAP, this.course.id, this.lap, lapNanos, 0L), this.pilot, reportAt));
            }
            this.lastCross = at;
        }

        void expirePending() {
            this.now += RaceServer.PENDING_TIMEOUT_NANOS + 100L * MS;
            this.verdicts.set(this.verdicts.size() - 1, RaceServer.flush(this.pilot, this.now));
        }

        RaceServer.Verdict finish() {
            this.now += 50L * MS;
            return RaceServer.handle(new RacePayloads.RaceReportC2S(RacePayloads.ReportKind.FINISH, this.course.id, this.lap, 0L, this.total), this.pilot, this.now);
        }

        void end() {
            CLOCK.set(this.now + LapTracker.SECOND);
            this.drone.setPilot(null);
            this.drone.discard();
        }
    }

    private static StepResult shortcutStep(SelfTest.StepContext ctx) {
        Site site = SITE.get();
        if (site == null) {
            return StepResult.fail("kein Kurs aus dem Aufbau");
        }
        Minecraft mc = Minecraft.getInstance();
        if (ctx.state().putIfAbsent("Client", Boolean.TRUE) == null) {
            List<LapTracker.Event> events = new ArrayList<>();
            String problem = flyClient(mc, site, laps(site.origin(), SHORTCUT, SHORTCUT, SHORTCUT), LAPS, events);
            if (problem != null) {
                return StepResult.fail(problem);
            }
            if (count(events, LapTracker.EventType.LAP) != LAPS || count(events, LapTracker.EventType.FINISH) != 1 || count(events, LapTracker.EventType.MISSED) != 0) {
                return StepResult.fail("Client wertet die Abkürzung nicht: " + events);
            }
            ctx.log("Client: " + LAPS + " Runden ohne Meldung „verpasst“");
        }
        StepResult wait = once(ctx, "Server", server -> {
            CourseRegistry.Course course = course(site);
            CourseRegistry registry = CourseRegistry.get();
            if (course == null || registry == null) {
                return "Kurs fehlt";
            }
            ServerPlayer pilot = player(server);
            ServerRun run = new ServerRun(course, pilot, site.origin().add(0.0, FLY, -3.0));
            try {
                run.fly(laps(site.origin(), SHORTCUT, SHORTCUT, SHORTCUT));
                for (RaceServer.Verdict verdict : run.verdicts) {
                    if (verdict != RaceServer.Verdict.ACCEPTED) {
                        return "Runde abgelehnt: " + run.verdicts;
                    }
                }
                if (run.lap != LAPS) {
                    return "Runden am Server: " + run.lap;
                }
                RaceServer.Verdict finish = run.finish();
                if (finish != RaceServer.Verdict.ACCEPTED) {
                    return "Ziel abgelehnt: " + finish;
                }
            } finally {
                run.end();
            }
            UUID id = pilot.getUUID();
            if (registry.board(course).lapsOf(id) != LAPS) {
                return "Bestenliste zählt " + registry.board(course).lapsOf(id) + " Runden";
            }
            return null;
        });
        if (wait != null) {
            return wait;
        }
        List<RacePayloads.LapVerdictS2C> verdicts = ClientRaceTracker.INSTANCE.verdicts();
        long counted = verdicts.stream().filter(verdict -> verdict.result() == RacePayloads.LapResult.COUNTED && verdict.courseId() == site.course()).count();
        boolean race = verdicts.stream().anyMatch(verdict -> verdict.result() == RacePayloads.LapResult.RACE && verdict.counted() == LAPS && verdict.lap() == LAPS);
        if (counted != LAPS || !race) {
            return ctx.tick() > 300 ? StepResult.fail("Urteile am Client: " + verdicts) : StepResult.pending();
        }
        ctx.log("Server wertet " + LAPS + " Runden, Urteile am Client: " + verdicts.size());
        return StepResult.pass();
    }

    private static StepResult trimStep(SelfTest.StepContext ctx) {
        Site site = SITE.get();
        if (site == null) {
            return StepResult.fail("kein Kurs aus dem Aufbau");
        }
        StepResult wait = once(ctx, "Kürzen", server -> {
            ServerPlayer player = player(server);
            Vec3 at = site.origin().add(POLE_X - 2.0, 2.0, GATE_Z - 2.0);
            player.getAbilities().flying = true;
            player.onUpdateAbilities();
            player.teleportTo(player.level(), at.x, at.y, at.z, Set.of(), 0.0F, 0.0F, true);
            player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(RaceModule.COURSE_TOOL.get()));
            server.getCommands().performPrefixedCommand(player.createCommandSourceStack(), "propwash race trim " + site.course() + " 2");
            return null;
        });
        if (wait != null) {
            return wait;
        }
        CourseData data = ClientCourses.get(site.course());
        if (data == null || data.gates().size() != 3) {
            return StepResult.fail("Kurs fehlt beim Client");
        }
        List<GateGeometry> gates = data.geometry();
        if (Math.abs(gates.get(1).scoreWidth() - 1.5) > 1.0E-6) {
            return ctx.tick() > 300 ? StepResult.fail("Reichweite nicht gekürzt: " + gates.get(1).scoreWidth()) : StepResult.pending();
        }
        if (!PassZoneOverlap.around(gates, 1, false).isEmpty()) {
            return StepResult.fail("Warnung bleibt nach dem Kürzen: " + PassZoneOverlap.around(gates, 1, false));
        }
        ctx.log("Reichweite 4 auf " + gates.get(1).scoreWidth() + " gekürzt, keine Überdeckung mehr");
        return StepResult.pass();
    }

    private static StepResult verdictStep(SelfTest.StepContext ctx) {
        Site site = SITE.get();
        if (site == null) {
            return StepResult.fail("kein Kurs aus dem Aufbau");
        }
        Minecraft mc = Minecraft.getInstance();
        if (ctx.state().putIfAbsent("Client", Boolean.TRUE) == null) {
            RaceHud.clear();
            List<LapTracker.Event> shortcut = new ArrayList<>();
            String problem = flyClient(mc, site, laps(site.origin(), SHORTCUT), LAPS, shortcut);
            if (problem != null) {
                return StepResult.fail(problem);
            }
            if (count(shortcut, LapTracker.EventType.LAP) != 0 || shortcut.stream().noneMatch(event -> event.type() == LapTracker.EventType.MISSED && event.gate() == 1)) {
                return StepResult.fail("Gekürzte Stange: Client wertet die Abkürzung oder meldet nichts: " + shortcut);
            }
            String missed = Component.translatable("propwash.race.msg.missed_element", Component.translatable("propwash.race.element.pole"), 2).getString();
            if (RaceHud.recentMessages().stream().noneMatch(message -> message.getString().equals(missed))) {
                return StepResult.fail("Meldung fehlt im HUD: " + missed);
            }
            List<LapTracker.Event> full = new ArrayList<>();
            problem = flyClient(mc, site, laps(site.origin(), FULL), LAPS, full);
            if (problem != null) {
                return StepResult.fail(problem);
            }
            if (count(full, LapTracker.EventType.LAP) != 1 || count(full, LapTracker.EventType.MISSED) != 0) {
                return StepResult.fail("Volle Linie nicht gewertet: " + full);
            }
            ctx.log("Client: Abkürzung meldet „" + missed + "“, volle Linie zählt");
        }
        StepResult wait = once(ctx, "Server", server -> {
            CourseRegistry.Course course = course(site);
            CourseRegistry registry = CourseRegistry.get();
            if (course == null || registry == null) {
                return "Kurs fehlt";
            }
            ServerPlayer pilot = player(server);
            int before = registry.board(course).lapsOf(pilot.getUUID());
            ServerRun run = new ServerRun(course, pilot, site.origin().add(0.0, FLY, -3.0));
            try {
                run.fly(path(site.origin(), SHORTCUT));
                run.fly(List.of(site.origin().add(0.0, FLY, -3.0), site.origin().add(0.0, FLY, 2.0)));
                if (run.verdicts.size() != 1 || run.verdicts.getFirst() != RaceServer.Verdict.PENDING) {
                    return "Abkürzung sofort entschieden: " + run.verdicts;
                }
                run.expirePending();
                if (run.verdicts.getFirst() != RaceServer.Verdict.UNVERIFIED) {
                    return "Abkürzung nicht abgelehnt: " + run.verdicts;
                }
                if (registry.board(course).lapsOf(pilot.getUUID()) != before) {
                    return "Abkürzung zählt in der Bestenliste";
                }
                List<Vec3> back = path(site.origin(), FULL);
                back.set(0, site.origin().add(0.0, FLY, 2.0));
                run.fly(back);
                back = path(site.origin(), FULL);
                back.add(site.origin().add(0.0, FLY, 2.0));
                run.fly(back);
                if (run.lap != LAPS || run.verdicts.get(1) != RaceServer.Verdict.ACCEPTED || run.verdicts.get(2) != RaceServer.Verdict.ACCEPTED) {
                    return "Spätere Runden zählen nicht: " + run.verdicts + " Runde " + run.lap;
                }
                RaceServer.Verdict finish = run.finish();
                if (finish != RaceServer.Verdict.UNVERIFIED) {
                    return "Ziel trotz nicht gewerteter Runde: " + finish;
                }
            } finally {
                run.end();
            }
            int after = registry.board(course).lapsOf(pilot.getUUID());
            if (after != before + 2) {
                return "Bestenliste zählt " + (after - before) + " statt 2 neue Runden";
            }
            RESULT.set("Runden am Server: " + (after - before) + " von " + LAPS);
            return null;
        });
        if (wait != null) {
            return wait;
        }
        List<RacePayloads.LapVerdictS2C> verdicts = ClientRaceTracker.INSTANCE.verdicts();
        boolean missed = verdicts.stream().anyMatch(verdict -> verdict.result() == RacePayloads.LapResult.MISSED && verdict.lap() == 1 && verdict.element() == 1);
        long counted = verdicts.stream().filter(verdict -> verdict.result() == RacePayloads.LapResult.COUNTED).count();
        boolean race = verdicts.stream().anyMatch(verdict -> verdict.result() == RacePayloads.LapResult.RACE && verdict.counted() == 2 && verdict.lap() == LAPS);
        if (!missed || counted != 2 || !race) {
            return ctx.tick() > 300 ? StepResult.fail("Urteile am Client: " + verdicts) : StepResult.pending();
        }
        CourseData data = ClientCourses.get(site.course());
        String lapLine = ClientRaceTracker.verdictText(new RacePayloads.LapVerdictS2C(site.course(), 1, RacePayloads.LapResult.MISSED, 1, 0), data).getString();
        String summary = Component.translatable("propwash.race.verdict.summary", 2, LAPS).getString();
        if (RaceDataChat.containing(mc, lapLine) == 0 || RaceDataChat.containing(mc, summary) == 0) {
            return ctx.tick() > 300 ? StepResult.fail("Chat ohne „" + lapLine + "“ oder „" + summary + "“") : StepResult.pending();
        }
        ctx.log(RESULT.get() + " · Chat: " + lapLine + " · " + summary);
        return StepResult.pass();
    }

    private static StepResult menuStep(SelfTest.StepContext ctx) {
        Site site = SITE.get();
        Minecraft mc = Minecraft.getInstance();
        if (site == null || mc.level == null) {
            return StepResult.fail("kein Kurs aus dem Aufbau");
        }
        StepResult wait = once(ctx, "Lang", server -> widen(server, site));
        if (wait != null) {
            return wait;
        }
        if (!(mc.level.getEntity(site.pole()) instanceof GateEntity pole) || Math.abs(pole.geometry().scoreWidth() - 4.0) > 1.0E-6) {
            return ctx.tick() > 300 ? StepResult.fail("Stange nicht auf 4 gesetzt") : StepResult.pending();
        }
        CourseData data = ClientCourses.get(site.course());
        if (data == null || Math.abs(data.geometry().get(1).scoreWidth() - 4.0) > 1.0E-6) {
            return ctx.tick() > 300 ? StepResult.fail("Kurs nicht auf 4 synchronisiert") : StepResult.pending();
        }
        if (GateEditor.current() == null) {
            GateEditor.start(pole);
            GateEditDriver.cursorHeld(mc, true);
            return StepResult.pending();
        }
        GateEditor editor = GateEditor.current();
        GateEditPanel panel = GateEditDriver.panel(mc);
        if (panel == null) {
            return ctx.tick() > 200 ? fail(mc, "Maus-Menü öffnet nicht") : StepResult.pending();
        }
        if (ctx.state().putIfAbsent("Geklickt", Boolean.TRUE) == null) {
            PassZoneOverlap.Finding own = editor.ownOverlap();
            if (own == null || !own.shortenable() || own.neighbour() != 2) {
                return fail(mc, "Gate-Menü warnt nicht: " + editor.overlaps());
            }
            AbstractWidget trim = trimButton(panel, own.shortened());
            if (trim == null) {
                return ctx.tick() > 200 ? fail(mc, "Knopf „Kürzen“ fehlt") : again(ctx);
            }
            for (AbstractWidget other : panel.widgets()) {
                if (other != trim && trim.getX() < other.getX() + other.getWidth() && other.getX() < trim.getX() + trim.getWidth()
                        && trim.getY() < other.getY() + other.getHeight() && other.getY() < trim.getY() + trim.getHeight()) {
                    return fail(mc, "Knopf „Kürzen“ überdeckt " + other.getMessage().getString());
                }
            }
            if (!GateEditDriver.click(mc, trim)) {
                return fail(mc, "Klick auf „Kürzen“ nicht angenommen");
            }
            return StepResult.pending();
        }
        if (Math.abs(editor.model().width() - 1.5) > 1.0E-6 || !editor.overlaps().isEmpty()) {
            return fail(mc, "Knopf kürzt nicht: Breite " + editor.model().width() + " " + editor.overlaps());
        }
        if (trimButton(panel, 1.5) != null) {
            return ctx.tick() > 200 ? fail(mc, "Knopf bleibt nach dem Kürzen") : StepResult.pending();
        }
        ctx.log("Gate-Menü: Warnung bei Reichweite 4, Knopf setzt " + editor.model().width() + ", Warnung und Knopf weg");
        close(mc);
        return StepResult.pass();
    }

    private static StepResult again(SelfTest.StepContext ctx) {
        ctx.state().remove("Geklickt");
        return StepResult.pending();
    }

    private static StepResult fail(Minecraft mc, String reason) {
        close(mc);
        return StepResult.fail(reason);
    }

    private static void close(Minecraft mc) {
        GateEditDriver.cursorHeld(mc, false);
        GateEditor.stop();
        mc.gui.setScreen(null);
    }

    private static @Nullable AbstractWidget trimButton(GateEditPanel panel, double value) {
        String label = Component.translatable("propwash.race.overlap.trim", GateEditPanel.blocksText(value)).getString();
        for (AbstractWidget widget : panel.widgets()) {
            if (label.equals(widget.getMessage().getString())) {
                return widget;
            }
        }
        return null;
    }

    private static @Nullable String widen(MinecraftServer server, Site site) {
        ServerPlayer player = player(server);
        if (!(player.level().getEntity(site.pole()) instanceof GateEntity pole)) {
            return "Stange fehlt am Server";
        }
        GateMounting.Result result = GateMounting.shortenReach(player, pole, 4.0);
        return result == GateMounting.Result.EDITED ? null : "Stange nicht verlängert: " + result;
    }

    private static StepResult raceLineStep(SelfTest.StepContext ctx) {
        Site site = SITE.get();
        Minecraft mc = Minecraft.getInstance();
        if (site == null) {
            return StepResult.fail("kein Kurs aus dem Aufbau");
        }
        ClientRaceTracker tracker = ClientRaceTracker.INSTANCE;
        RaceConfig config = RaceConfig.CONFIG.get();
        boolean setting = config.raceLine;
        config.raceLine = true;
        try {
            int stage = (int) ctx.state().getOrDefault("Stufe", 0);
            CourseData data = ClientCourses.get(site.course());
            if (data == null) {
                return StepResult.fail("Kurs fehlt beim Client");
            }
            tracker.selectCourseAt(mc, site.origin().add(0.0, FLY, -3.0));
            if (tracker.courseId() != site.course()) {
                return StepResult.fail("Kurs nicht gewählt: " + tracker.courseId());
            }
            if (!(mc.gui.screen() instanceof CourseMenuScreen screen)) {
                if (stage == 3) {
                    return StepResult.pass();
                }
                CourseMenuScreen.open(null, site.course());
                return StepResult.pending();
            }
            switch (stage) {
                case 0 -> {
                    if (!data.raceLine() || !RaceLineRenderer.enabled() || screen.raceLineButton(false) == null || !screen.raceLineButton(false).active) {
                        return ctx.tick() > 100 ? StepResult.fail("Knopf oder Ausgangslage falsch: " + data.raceLine() + " " + RaceLineRenderer.enabled()) : StepResult.pending();
                    }
                    screen.setRaceLine(false);
                    ctx.state().put("Stufe", 1);
                }
                case 1 -> {
                    if (data.raceLine()) {
                        return ctx.tick() > 300 ? StepResult.fail("Server stellt die Ideallinie nicht ab") : StepResult.pending();
                    }
                    if (RaceLineRenderer.enabled() || !RaceLineRenderer.currentPath(0.0F).isEmpty()) {
                        return StepResult.fail("Linie bleibt trotz Kurs-Merker");
                    }
                    screen.setRaceLine(true);
                    ctx.state().put("Stufe", 2);
                }
                default -> {
                    if (!data.raceLine()) {
                        return ctx.tick() > 300 ? StepResult.fail("Server stellt die Ideallinie nicht wieder an") : StepResult.pending();
                    }
                    if (!RaceLineRenderer.enabled()) {
                        return StepResult.fail("Linie bleibt aus");
                    }
                    config.raceLine = false;
                    boolean personal = !RaceLineRenderer.enabled();
                    config.raceLine = true;
                    if (!personal) {
                        return StepResult.fail("Client-Einstellung wirkt nicht mehr");
                    }
                    ctx.state().put("Stufe", 3);
                    mc.gui.setScreen(null);
                }
            }
            return StepResult.pending();
        } finally {
            config.raceLine = setting;
        }
    }

    private static StepResult waveStep(SelfTest.StepContext ctx) {
        Site site = SITE.get();
        Minecraft mc = Minecraft.getInstance();
        if (site == null || mc.level == null || !(mc.level.getEntity(site.gates().getFirst()) instanceof GateEntity gate)) {
            return StepResult.fail("Start-Gate fehlt");
        }
        @SuppressWarnings("unchecked")
        List<Integer> seen = (List<Integer>) ctx.state().computeIfAbsent("Folge", key -> new ArrayList<Integer>());
        int best = 0;
        float value = -1.0F;
        for (int i = 0; i < GateRenderer.CHEVRONS; i++) {
            float brightness = GateRenderer.chevronBrightness(ChevronWave.approach(i), gate.tickCount);
            if (brightness > value) {
                value = brightness;
                best = i;
            }
        }
        if (seen.isEmpty() || seen.getLast() != best) {
            seen.add(best);
        }
        if (ctx.tick() < WAVE_TICKS) {
            return StepResult.pending();
        }
        for (int i = 1; i < seen.size(); i++) {
            if (seen.get(i) != Math.floorMod(seen.get(i - 1) - 1, GateRenderer.CHEVRONS)) {
                return StepResult.fail("Licht läuft nicht zum Gate (0 = Pfeil am Gate): " + seen);
            }
        }
        ctx.log("Hellster Pfeil im Lauf der Zeit (0 = am Gate): " + seen);
        return seen.size() >= 3 ? StepResult.pass() : StepResult.fail("Licht bewegt sich nicht: " + seen);
    }

    private static StepResult cleanupStep(SelfTest.StepContext ctx) {
        Site site = SITE.get();
        if (site == null) {
            return StepResult.pass();
        }
        StepResult wait = once(ctx, "Aufräumen", server -> {
            CourseRegistry registry = CourseRegistry.get();
            if (registry != null) {
                registry.deleteCourse(site.course());
            }
            ServerPlayer player = player(server);
            ServerLevel level = player.level();
            for (int id : site.gates()) {
                Entity entity = level.getEntity(id);
                if (entity != null) {
                    entity.discard();
                }
            }
            player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
            player.getAbilities().flying = false;
            player.onUpdateAbilities();
            player.teleportTo(level, site.home().x, site.home().y, site.home().z, Set.of(), player.getYRot(), player.getXRot(), true);
            return null;
        });
        if (wait != null) {
            return wait;
        }
        SITE.set(null);
        RaceHud.clear();
        return StepResult.pass();
    }

    private static void aim(LocalPlayer player, Vec3 point) {
        Vec3 eye = player.getEyePosition();
        double dx = point.x - eye.x;
        double dy = point.y - eye.y;
        double dz = point.z - eye.z;
        float yaw = (float) Math.toDegrees(Math.atan2(-dx, dz));
        float pitch = (float) -Math.toDegrees(Math.atan2(dy, Math.sqrt(dx * dx + dz * dz)));
        player.setYRot(yaw);
        player.setXRot(pitch);
        player.yRotO = yaw;
        player.xRotO = pitch;
        player.setYHeadRot(yaw);
        player.yHeadRotO = yaw;
    }

    private static @Nullable StepResult goTo(SelfTest.StepContext ctx, Vec3 at) {
        StepResult wait = once(ctx, "Blickpunkt", server -> {
            ServerPlayer player = player(server);
            player.getAbilities().flying = true;
            player.onUpdateAbilities();
            player.teleportTo(player.level(), at.x, at.y, at.z, Set.of(), 0.0F, 0.0F, true);
            return null;
        });
        if (wait != null) {
            return wait;
        }
        LocalPlayer local = Minecraft.getInstance().player;
        if (local == null || local.position().distanceTo(at) > 0.6) {
            return ctx.tick() > 300 ? StepResult.fail("Spieler nicht am Blickpunkt") : StepResult.pending();
        }
        return null;
    }

    private static final class WaveScene implements Screenshots.Scene {
        private final int phase;

        WaveScene(int phase) {
            this.phase = phase;
        }

        @Override
        public StepResult prepare(SelfTest.StepContext ctx) {
            Site site = SITE.get();
            Minecraft mc = Minecraft.getInstance();
            if (site == null || mc.level == null || !(mc.level.getEntity(site.gates().getFirst()) instanceof GateEntity gate)) {
                return StepResult.fail("kein Kurs für das Bildschirmfoto");
            }
            StepResult wait = goTo(ctx, site.origin().add(2.5, 2.0, -7.0));
            if (wait != null) {
                return wait;
            }
            mc.gui.setScreen(null);
            RaceHud.clear();
            aim(mc.player, site.origin().add(0.0, 0.0, -1.5));
            return (gate.tickCount + 4) % 20 == this.phase ? StepResult.pass() : StepResult.pending();
        }
    }

    private static final class VerdictScene implements Screenshots.Scene {
        @Override
        public StepResult prepare(SelfTest.StepContext ctx) {
            Site site = SITE.get();
            Minecraft mc = Minecraft.getInstance();
            if (site == null || mc.player == null) {
                return StepResult.fail("kein Kurs für das Bildschirmfoto");
            }
            StepResult wait = goTo(ctx, site.origin().add(2.5, 2.0, GATE_Z - 7.0));
            if (wait != null) {
                return wait;
            }
            mc.gui.setScreen(null);
            aim(mc.player, site.origin().add(-1.0, 2.0, GATE_Z));
            return StepResult.pass();
        }

        @Override
        public void hold(SelfTest.StepContext ctx) {
            Site site = SITE.get();
            Minecraft mc = Minecraft.getInstance();
            if (site == null || mc.player == null) {
                return;
            }
            aim(mc.player, site.origin().add(-1.0, 2.0, GATE_Z));
            if (ctx.state().putIfAbsent("gezeigt", Boolean.TRUE) == null) {
                RaceHud.clear();
                ClientRaceTracker tracker = ClientRaceTracker.INSTANCE;
                tracker.onVerdict(mc, new RacePayloads.LapVerdictS2C(site.course(), 1, RacePayloads.LapResult.MISSED, 1, 0));
                tracker.onVerdict(mc, new RacePayloads.LapVerdictS2C(site.course(), LAPS, RacePayloads.LapResult.RACE, RacePayloads.LapVerdictS2C.NO_ELEMENT, 2));
            }
        }
    }

    private static final class MenuScene implements Screenshots.Scene {
        @Override
        public StepResult prepare(SelfTest.StepContext ctx) {
            Site site = SITE.get();
            Minecraft mc = Minecraft.getInstance();
            if (site == null || mc.level == null || !(mc.level.getEntity(site.pole()) instanceof GateEntity pole)) {
                return StepResult.fail("kein Kurs für das Bildschirmfoto");
            }
            StepResult wait = goTo(ctx, site.origin().add(POLE_X - 2.0, 2.0, GATE_Z - 3.0));
            if (wait != null) {
                return wait;
            }
            RaceHud.clear();
            aim(mc.player, site.origin().add(POLE_X + 1.0, 1.5, GATE_Z));
            if (GateEditor.current() == null) {
                GateEditor.start(pole);
                GateEditDriver.cursorHeld(mc, true);
                return StepResult.pending();
            }
            if (GateEditDriver.panel(mc) == null) {
                return ctx.tick() > 200 ? StepResult.fail("Maus-Menü öffnet nicht") : StepResult.pending();
            }
            Screenshots.parkMouse(mc);
            GateEditor editor = GateEditor.current();
            return editor.ownOverlap() == null ? StepResult.fail("Keine Warnung im Gate-Menü: " + editor.overlaps()) : StepResult.pass();
        }

        @Override
        public void finish(SelfTest.StepContext ctx) {
            close(Minecraft.getInstance());
        }
    }

    private static final class CourseScene implements Screenshots.Scene {
        @Override
        public StepResult prepare(SelfTest.StepContext ctx) {
            Site site = SITE.get();
            Minecraft mc = Minecraft.getInstance();
            if (site == null) {
                return StepResult.fail("kein Kurs für das Bildschirmfoto");
            }
            if (!(mc.gui.screen() instanceof CourseMenuScreen)) {
                CourseMenuScreen.open(null, site.course());
                Screenshots.parkMouse(mc);
                return StepResult.pending();
            }
            return StepResult.pass();
        }

        @Override
        public void finish(SelfTest.StepContext ctx) {
            Minecraft.getInstance().gui.setScreen(null);
        }
    }
}
