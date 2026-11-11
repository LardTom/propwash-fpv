package de.lardtom.propwash.stats.client;

import de.lardtom.propwash.dev.FpvAdvancementCases;
import de.lardtom.propwash.dev.RaceDataJoinCheck;
import de.lardtom.propwash.dev.SelfTest;
import de.lardtom.propwash.dev.SelfTest.StepResult;
import de.lardtom.propwash.dev.client.Screenshots;
import de.lardtom.propwash.drone.QuadcopterEntity;
import de.lardtom.propwash.drone.client.PilotSession;
import de.lardtom.propwash.guide.client.training.TrainingStore;
import de.lardtom.propwash.guide.training.Curriculum;
import de.lardtom.propwash.guide.training.Lesson;
import de.lardtom.propwash.guide.training.StarRule;
import de.lardtom.propwash.guide.training.TrainingProgress;
import de.lardtom.propwash.network.client.ClientNet;
import de.lardtom.propwash.race.CourseRegistry;
import de.lardtom.propwash.race.GateEntity;
import de.lardtom.propwash.race.LapTracker;
import de.lardtom.propwash.race.client.ClientCourses;
import de.lardtom.propwash.race.client.ClientRaceTracker;
import de.lardtom.propwash.race.client.RaceConfig;
import de.lardtom.propwash.sim.Axis;
import de.lardtom.propwash.sim.DroneSim;
import de.lardtom.propwash.sim.StabMode;
import de.lardtom.propwash.sim.StickMath;
import de.lardtom.propwash.input.StickPositions;
import de.lardtom.propwash.stats.net.TrickC2S;
import de.lardtom.propwash.trick.Trick;
import de.lardtom.propwash.trick.TrickType;
import de.lardtom.propwash.trick.client.TrickTracker;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.advancements.AdvancementProgress;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.advancements.AdvancementsScreen;
import net.minecraft.client.multiplayer.ClientAdvancements;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaterniond;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class FpvAdvancementSelfTests {
    public static final String GROUP = StatsSelfTests.GROUP;
    public static final String LOADED = GROUP + "Fortschritte geladen – Tab Propwash FPV mit Wurzel, Hintergrund und 27 Fortschritten, sechs eigene Auslöser, Stand zurückgesetzt";
    public static final String FLOWN = GROUP + "Fortschritte aus dem Flug – Abheben und FPV-Flug mit Brille aus dem echten Flug am Server";
    public static final String CASES = GROUP + "Fortschritte auslösen – echte Wege am Server: Rennen, Werkbank mit Klick, Shift-Klick, Zifferntaste und Wegwerfen samt DRONE_ASSEMBLED mit der gebauten Drohne, Ladegerät mit Klick und Shift-Klick, Akkutausch, Strecke, Höhe, Sturz, Crashes, Fracht, Lackierung, Sharecode, Zuschauen, Enderman per Treffer-Paket, Drache und Wither";
    public static final String CLIENT_PATHS = GROUP + "Fortschritte aus den Client-Wegen – Flip über die Trick-Erkennung, Geist geschlagen über den Renn-Tracker, Flugschule über StatsClient, Power Loop und Kombo als Paket";
    public static final String TAB = GROUP + "Fortschritte im Tab – Client zeigt den Tab Propwash FPV mit allen Fortschritten erreicht";
    public static final List<String> NAMES = FpvAdvancementCases.NAMES;
    public static final Identifier ROOT = FpvAdvancementCases.ROOT;
    private static final String ASKED = "fpv_asked";
    private static final String SITE = "fpv_site";
    private static final String SAVED_LESSONS = "fpv_lessons";
    private static final String ROTATED = "fpv_rotated";
    private static final String ATTITUDE = "fpv_attitude";
    private static final double TRICK_HEIGHT = 12.0;
    private static final double FLIP_DEGREES = 340.0;
    private static final double FLIP_RATE = 650.0;
    private static final float FLIP_THROTTLE = 0.3F;
    private static final double SLOW_BLOCKS_PER_SECOND = 7.0;
    private static final double FAST_BLOCKS_PER_SECOND = 10.0;
    private static final double FEED_STEP = 0.25;
    private static final int COMBO = 5;
    private static final int WAIT_TICKS = 300;

    private FpvAdvancementSelfTests() {
    }

    public static Identifier id(String name) {
        return FpvAdvancementCases.id(name);
    }

    public static void registerLoaded() {
        SelfTest.clientStep(LOADED, 200, FpvAdvancementSelfTests::loadedStep);
    }

    public static void register() {
        SelfTest.clientStep(FLOWN, 200, ctx -> serverStep(ctx, FpvAdvancementSelfTests::flown));
        SelfTest.clientStep(CASES, 400, ctx -> serverStep(ctx, server -> FpvAdvancementCases.cases(server, StatsFlight.player(server))));
        SelfTest.clientStep(CLIENT_PATHS, 3600, FpvAdvancementSelfTests::clientPathsStep);
        SelfTest.clientStep(TAB, 600, FpvAdvancementSelfTests::tabStep);
    }

    public static void registerScreenshot(String title, String file) {
        Screenshots.step(GROUP + title, file, 20, new TabScene());
    }

    private static StepResult loadedStep(SelfTest.StepContext ctx) {
        return serverStep(ctx, server -> {
            List<String> problems = FpvAdvancementCases.loadProblems(server);
            if (!problems.isEmpty()) {
                return problems;
            }
            ServerPlayer player = StatsFlight.player(server);
            int revoked = FpvAdvancementCases.revokeAll(server, player);
            if (!FpvAdvancementCases.missing(server, player, NAMES).equals(NAMES)) {
                return List.of("nach dem Zurücksetzen noch erreicht: " + FpvAdvancementCases.done(server, player));
            }
            return List.of("OK " + FpvAdvancementCases.loaded(server) + " Fortschritte, " + revoked + " Kriterien zurückgesetzt");
        });
    }

    private static List<String> flown(MinecraftServer server) {
        List<String> missing = FpvAdvancementCases.missing(server, StatsFlight.player(server), FpvAdvancementCases.FLOWN);
        return missing.isEmpty() ? List.of("OK Abheben und FPV-Flug aus dem echten Flug erreicht")
                : List.of("aus dem Flug nicht erreicht: " + missing);
    }

    private static StepResult tabStep(SelfTest.StepContext ctx) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.getConnection() == null) {
            return StepResult.fail("kein Spieler");
        }
        ClientAdvancements advancements = mc.getConnection().getAdvancements();
        List<String> missing = new ArrayList<>();
        for (String name : NAMES) {
            AdvancementHolder holder = advancements.get(id(name));
            AdvancementProgress progress = holder == null ? null : advancements.progress().get(holder);
            if (progress == null || !progress.isDone()) {
                missing.add(name);
            }
        }
        if (!missing.isEmpty()) {
            return ctx.tick() > 400 ? StepResult.fail("im Tab nicht erreicht: " + missing) : StepResult.pending();
        }
        AdvancementHolder root = advancements.get(ROOT);
        var node = advancements.tree().get(ROOT);
        if (root == null || node == null || node.root() != node) {
            return StepResult.fail("Wurzel propwash:fpv/root ist kein eigener Tab");
        }
        int below = 0;
        for (var each : advancements.tree().nodes()) {
            if (each.root() == node && each != node) {
                below++;
            }
        }
        if (below != NAMES.size() - 1) {
            return StepResult.fail(below + " Fortschritte unter der Wurzel statt " + (NAMES.size() - 1));
        }
        String title = root.value().display().map(display -> display.title().getString()).orElse("");
        ctx.log("Tab „" + title + "“ mit " + below + " Fortschritten, alle erreicht");
        return StepResult.pass();
    }

    record Site(int course, Vec3 origin, List<Integer> gates) {
    }

    private static Site prepare(MinecraftServer server) {
        ServerPlayer player = StatsFlight.player(server);
        FpvAdvancementCases.revoke(server, player, FpvAdvancementCases.CLIENT);
        Vec3 origin = RaceDataJoinCheck.origin(player);
        List<GateEntity> gates = new ArrayList<>();
        CourseRegistry.Course course = RaceDataJoinCheck.build(player, origin, gates);
        if (course == null) {
            gates.forEach(GateEntity::discard);
            throw new IllegalStateException("Kurs nicht angelegt");
        }
        return new Site(course.id, origin, gates.stream().map(Entity::getId).toList());
    }

    private static Boolean removeSite(MinecraftServer server, Site site) {
        CourseRegistry registry = CourseRegistry.get();
        if (registry != null) {
            registry.deleteCourse(site.course());
        }
        ServerPlayer player = StatsFlight.player(server);
        for (int id : site.gates()) {
            Entity gate = player.level().getEntity(id);
            if (gate != null) {
                gate.discard();
            }
        }
        return Boolean.TRUE;
    }

    static List<String> shownMissing(List<String> names) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.getConnection() == null) {
            return names;
        }
        ClientAdvancements advancements = mc.getConnection().getAdvancements();
        List<String> missing = new ArrayList<>();
        for (String name : names) {
            AdvancementHolder holder = advancements.get(id(name));
            AdvancementProgress progress = holder == null ? null : advancements.progress().get(holder);
            if (progress == null || !progress.isDone()) {
                missing.add(name);
            }
        }
        return missing;
    }

    private static StepResult clientPathsStep(SelfTest.StepContext ctx) {
        Map<String, Object> state = ctx.state();
        String phase = StatsFlight.phase(ctx, "prep");
        int since = StatsFlight.since(ctx);
        if (!phase.equals("prep") && !phase.equals("prepared") && !phase.equals("cleanup") && !phase.equals("fpv")
                && !phase.equals("arm") && !StatsFlight.flying()) {
            return end(ctx, StepResult.fail("FPV-Sitzung in Phase " + phase + " verloren"));
        }
        switch (phase) {
            case "prep" -> {
                StatsClient.forget();
                StatsFlight.ask(ctx, SITE, FpvAdvancementSelfTests::prepare);
                return StatsFlight.next(ctx, "prepared");
            }
            case "prepared" -> {
                StatsFlight.Answer<Site> answer = StatsFlight.answer(ctx, SITE);
                if (answer == null) {
                    return StepResult.pending();
                }
                if (answer.error() != null || answer.value() == null) {
                    return StepResult.fail("Vorbereitung: " + answer.error());
                }
                if (!shownMissing(FpvAdvancementCases.CLIENT).equals(FpvAdvancementCases.CLIENT)) {
                    return since > 200 ? end(ctx, StepResult.fail("Zurücksetzen kommt am Client nicht an: " + shownMissing(FpvAdvancementCases.CLIENT)))
                            : StepResult.pending();
                }
                return StatsFlight.start(ctx, 1.0F, -2.0, 2.0);
            }
            case "fpv" -> {
                StepResult waiting = StatsFlight.awaitFpv(ctx, "arm");
                return waiting == null ? StepResult.pending() : waiting;
            }
            case "arm" -> {
                StatsFlight.arm(since);
                if (since >= StatsFlight.ARM_TICKS && StatsFlight.armed()) {
                    TrickTracker.INSTANCE.session().keepHistory(16);
                    return StatsFlight.next(ctx, "climb");
                }
                return since > 200 ? end(ctx, StepResult.fail("nicht scharf")) : StepResult.pending();
            }
            case "climb" -> {
                StatsFlight.fly(TRICK_HEIGHT, 0.0F);
                if (since > 40 && StatsFlight.height() > TRICK_HEIGHT - 1.0) {
                    state.put(ROTATED, 0.0);
                    state.remove(ATTITUDE);
                    return StatsFlight.next(ctx, "flip");
                }
                return since > 400 ? end(ctx, StepResult.fail(String.format(Locale.ROOT, "Steigflug auf %.0f m: %.1f m", TRICK_HEIGHT,
                        StatsFlight.height()))) : StepResult.pending();
            }
            case "flip" -> {
                DroneSim sim = PilotSession.INSTANCE.sim();
                if (sim == null) {
                    return end(ctx, StepResult.fail("keine Simulation"));
                }
                Quaterniond now = new Quaterniond();
                sim.getAttitude(now);
                double rotated = (double) state.get(ROTATED);
                Quaterniond last = (Quaterniond) state.get(ATTITUDE);
                if (last != null) {
                    rotated += Math.toDegrees(2.0 * Math.acos(Math.min(1.0, Math.abs(last.dot(now)))));
                    state.put(ROTATED, rotated);
                }
                state.put(ATTITUDE, now);
                if (rotated >= FLIP_DEGREES) {
                    StatsFlight.fly(TRICK_HEIGHT, 0.0F);
                    ctx.log(String.format(Locale.ROOT, "Flip mit %.0f° in %d Ticks", rotated, since));
                    return StatsFlight.next(ctx, "trick");
                }
                float pitch = (float) StickMath.rateToStick(FLIP_RATE, sim.config().rates, Axis.PITCH);
                PilotSession.INSTANCE.setTestInput(new StickPositions(0.0F, pitch, 0.0F, FLIP_THROTTLE), true, StabMode.ACRO);
                return since > 80 ? end(ctx, StepResult.fail(String.format(Locale.ROOT, "Flip nach %d Ticks erst %.0f°", since, rotated)))
                        : StepResult.pending();
            }
            case "trick" -> {
                StatsFlight.fly(TRICK_HEIGHT, 0.0F);
                if (shownMissing(List.of("first_trick")).isEmpty()) {
                    ctx.log("Trick-Erkennung meldet " + TrickTracker.INSTANCE.session().history().stream().map(Trick::type).toList()
                            + ", Fortschritt Erster Trick am Client");
                    return StatsFlight.next(ctx, "ghost");
                }
                return since > WAIT_TICKS ? end(ctx, StepResult.fail("Erster Trick nicht erreicht: Tricks "
                        + TrickTracker.INSTANCE.session().score().tricks() + " " + TrickTracker.INSTANCE.session().history().stream().map(Trick::type).toList()
                        + ", Meldungen " + StatsClient.reports())) : StepResult.pending();
            }
            case "ghost" -> {
                StatsFlight.fly(TRICK_HEIGHT, 0.0F);
                Site site = (Site) StatsFlight.answer(ctx, SITE).value();
                if (ClientCourses.get(site.course()) == null) {
                    return since > 400 ? end(ctx, StepResult.fail("Kurs kommt beim Client nicht an")) : StepResult.pending();
                }
                if (ClientRaceTracker.INSTANCE.reasonsNow().any()) {
                    return since > 100 ? end(ctx, StepResult.fail("Flughilfe aktiv: " + ClientRaceTracker.INSTANCE.reasonsNow())) : StepResult.pending();
                }
                String problem = ghostLaps(Minecraft.getInstance(), site);
                if (problem != null) {
                    return end(ctx, StepResult.fail(problem));
                }
                return StatsFlight.next(ctx, "ghost_wait");
            }
            case "ghost_wait" -> {
                StatsFlight.fly(TRICK_HEIGHT, 0.0F);
                if (shownMissing(List.of("ghost_beaten")).isEmpty()) {
                    return StatsFlight.next(ctx, "training");
                }
                return since > WAIT_TICKS ? end(ctx, StepResult.fail("Geisterjäger nicht erreicht, Meldungen " + StatsClient.reports()))
                        : StepResult.pending();
            }
            case "training" -> {
                StatsFlight.fly(TRICK_HEIGHT, 0.0F);
                TrainingProgress progress = TrainingStore.progress();
                state.put(SAVED_LESSONS, progress.lessons);
                Map<String, TrainingProgress.LessonRecord> full = new LinkedHashMap<>();
                for (Lesson lesson : Curriculum.lessons()) {
                    TrainingProgress.LessonRecord record = new TrainingProgress.LessonRecord();
                    record.stars = StarRule.MAX_STARS;
                    record.completions = 1;
                    full.put(lesson.id(), record);
                }
                progress.lessons = full;
                return StatsFlight.next(ctx, "training_wait");
            }
            case "training_wait" -> {
                StatsFlight.fly(TRICK_HEIGHT, 0.0F);
                if (shownMissing(List.of("school_chapter", "school_perfect")).isEmpty()) {
                    restoreLessons(state);
                    return StatsFlight.next(ctx, "packets");
                }
                return since > WAIT_TICKS ? end(ctx, StepResult.fail("Flugschule nicht erreicht: " + shownMissing(List.of("school_chapter",
                        "school_perfect")) + ", Meldungen " + StatsClient.reports())) : StepResult.pending();
            }
            case "packets" -> {
                StatsFlight.fly(TRICK_HEIGHT, 0.0F);
                QuadcopterEntity drone = PilotSession.INSTANCE.drone();
                if (drone == null) {
                    return end(ctx, StepResult.fail("keine Drohne"));
                }
                boolean sent = ClientNet.sendToServer(new TrickC2S(drone.getId(), TrickType.POWER_LOOP.key(), 0))
                        & ClientNet.sendToServer(new TrickC2S(drone.getId(), "", COMBO));
                if (!sent) {
                    return end(ctx, StepResult.fail("stats_trick nicht gesendet"));
                }
                return StatsFlight.next(ctx, "packets_wait");
            }
            case "packets_wait" -> {
                StatsFlight.fly(TRICK_HEIGHT, 0.0F);
                List<String> missing = shownMissing(FpvAdvancementCases.CLIENT);
                if (missing.isEmpty()) {
                    ctx.log("Erster Trick, Geisterjäger, Flugschüler und Klassenbester aus den Client-Wegen, Power Loop und Kombo-König als Paket");
                    return end(ctx, StepResult.pass());
                }
                return since > WAIT_TICKS ? end(ctx, StepResult.fail("nicht erreicht: " + missing)) : StepResult.pending();
            }
            case "cleanup" -> {
                return StatsFlight.awaitCleanup(ctx);
            }
            default -> {
                return end(ctx, StepResult.fail("unbekannte Phase " + phase));
            }
        }
    }

    @SuppressWarnings("unchecked")
    private static void restoreLessons(Map<String, Object> state) {
        Object saved = state.remove(SAVED_LESSONS);
        if (saved != null) {
            TrainingStore.progress().lessons = (Map<String, TrainingProgress.LessonRecord>) saved;
        }
    }

    private static StepResult end(SelfTest.StepContext ctx, StepResult result) {
        restoreLessons(ctx.state());
        TrickTracker.INSTANCE.session().keepHistory(0);
        StatsFlight.Answer<Site> site = StatsFlight.answer(ctx, SITE);
        if (site != null && site.value() != null) {
            Site value = site.value();
            ctx.onServer(server -> removeSite(server, value));
        }
        return StatsFlight.finish(ctx, result);
    }

    private static @Nullable String ghostLaps(Minecraft mc, Site site) {
        ClientRaceTracker tracker = ClientRaceTracker.INSTANCE;
        RaceConfig config = RaceConfig.CONFIG.get();
        boolean report = config.reportResults;
        config.reportResults = false;
        List<LapTracker.Event> events = new ArrayList<>();
        tracker.setEventListener(events::add);
        List<Vec3> loop = new ArrayList<>();
        for (double[] point : RaceDataJoinCheck.PATH) {
            loop.add(site.origin().add(point[0], point[1], point[2]));
        }
        List<Vec3> again = new ArrayList<>();
        again.add(loop.getLast());
        again.addAll(loop.subList(1, loop.size()));
        try {
            tracker.tracker().abort(tracker.now(), LapTracker.AbortReason.RESTART);
            tracker.tracker().resetPosition();
            tracker.selectCourseAt(mc, loop.getFirst());
            if (tracker.courseId() != site.course()) {
                return "Kurs nicht gewählt: " + tracker.courseId() + " statt " + site.course();
            }
            tracker.tracker().setLapTarget(2);
            tracker.ghosts().setPersist(false);
            tracker.ghosts().clear();
            tracker.syncGhostsFor(mc, tracker.frame());
            long now = tracker.now() + LapTracker.RESTART_COOLDOWN_NANOS + LapTracker.SECOND;
            tracker.feed(now, loop.getFirst());
            now = feed(tracker, loop, now, SLOW_BLOCKS_PER_SECOND);
            now = feed(tracker, again, now, FAST_BLOCKS_PER_SECOND);
            List<Long> laps = events.stream().filter(event -> event.type() == LapTracker.EventType.LAP).map(LapTracker.Event::valueNanos).toList();
            if (laps.size() != 2 || laps.get(1) >= laps.get(0)) {
                return "Runden am Client: " + laps + ", Ereignisse " + events.stream().map(LapTracker.Event::type).toList();
            }
            return null;
        } finally {
            tracker.tracker().abort(tracker.now(), LapTracker.AbortReason.RESTART);
            tracker.tracker().resetPosition();
            tracker.tracker().drainEvents();
            tracker.setEventListener(null);
            tracker.ghosts().clear();
            tracker.ghosts().setPersist(true);
            config.reportResults = report;
        }
    }

    private static long feed(ClientRaceTracker tracker, List<Vec3> points, long start, double blocksPerSecond) {
        long now = start;
        for (int i = 1; i < points.size(); i++) {
            Vec3 from = points.get(i - 1);
            Vec3 to = points.get(i);
            double length = from.distanceTo(to);
            int steps = Math.max(1, (int) Math.ceil(length / FEED_STEP));
            long stepNanos = Math.round(length / blocksPerSecond / steps * 1.0E9);
            for (int s = 1; s <= steps; s++) {
                now += stepNanos;
                tracker.feed(now, from.lerp(to, s / (double) steps));
            }
        }
        return now;
    }

    private interface ServerCheck {
        List<String> run(MinecraftServer server);
    }

    private static StepResult serverStep(SelfTest.StepContext ctx, ServerCheck check) {
        if (!ctx.state().containsKey(ASKED)) {
            ctx.state().put(ASKED, true);
            StatsFlight.ask(ctx, "fpv", check::run);
            return StepResult.pending();
        }
        StatsFlight.Answer<List<String>> answer = StatsFlight.answer(ctx, "fpv");
        if (answer == null) {
            return StepResult.pending();
        }
        if (answer.error() != null) {
            return StepResult.fail(answer.error());
        }
        List<String> lines = answer.value() == null ? List.of("keine Antwort") : answer.value();
        if (lines.size() == 1 && lines.getFirst().startsWith("OK")) {
            ctx.log(lines.getFirst().substring(2).strip());
            return StepResult.pass();
        }
        return StepResult.fail(String.join("; ", lines));
    }

    static List<String> missing(MinecraftServer server, ServerPlayer player, List<String> names) {
        List<String> out = new ArrayList<>();
        for (String name : names) {
            AdvancementHolder holder = server.getAdvancements().get(id(name));
            if (holder == null || !player.getAdvancements().getOrStartProgress(holder).isDone()) {
                out.add(name);
            }
        }
        return out;
    }

    static List<String> done(MinecraftServer server, ServerPlayer player) {
        List<String> out = new ArrayList<>(NAMES);
        out.removeAll(missing(server, player, NAMES));
        return out;
    }

    static int revokeAll(MinecraftServer server, ServerPlayer player) {
        int revoked = 0;
        for (String name : NAMES) {
            AdvancementHolder holder = server.getAdvancements().get(id(name));
            if (holder == null) {
                continue;
            }
            AdvancementProgress progress = player.getAdvancements().getOrStartProgress(holder);
            for (String criterion : progress.getCompletedCriteria()) {
                if (player.getAdvancements().revoke(holder, criterion)) {
                    revoked++;
                }
            }
        }
        return revoked;
    }

    private static final class TabScene implements Screenshots.Scene {
        @Override
        public StepResult prepare(SelfTest.StepContext ctx) {
            Minecraft mc = Minecraft.getInstance();
            if (mc.player == null || mc.getConnection() == null) {
                return StepResult.fail("kein Spieler");
            }
            ClientAdvancements advancements = mc.getConnection().getAdvancements();
            AdvancementHolder root = advancements.get(ROOT);
            if (root == null) {
                return StepResult.fail("Tab Propwash FPV fehlt");
            }
            if (!(mc.gui.screen() instanceof AdvancementsScreen)) {
                mc.gui.setScreen(new AdvancementsScreen(advancements));
                return StepResult.pending();
            }
            advancements.setSelectedTab(root, true);
            Screenshots.parkMouse(mc);
            return StepResult.pass();
        }

        @Override
        public void finish(SelfTest.StepContext ctx) {
            Minecraft mc = Minecraft.getInstance();
            if (mc.gui.screen() instanceof AdvancementsScreen) {
                mc.gui.setScreen(null);
            }
        }
    }
}
