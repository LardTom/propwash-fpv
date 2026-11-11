package de.lardtom.propwash.race.client;

import com.mojang.blaze3d.platform.InputConstants;
import de.lardtom.propwash.api.PropwashApi;
import de.lardtom.propwash.api.race.CourseInfo;
import de.lardtom.propwash.api.race.CourseMode;
import de.lardtom.propwash.dev.RaceDataJoinCheck;
import de.lardtom.propwash.dev.RaceP2pJoinCheck;
import de.lardtom.propwash.dev.SelfTest;
import de.lardtom.propwash.dev.SelfTest.StepResult;
import de.lardtom.propwash.dev.client.Screenshots;
import de.lardtom.propwash.race.CourseData;
import de.lardtom.propwash.race.CourseRegistry;
import de.lardtom.propwash.race.GateEntity;
import de.lardtom.propwash.race.GateGeometry;
import de.lardtom.propwash.race.GhostTrack;
import de.lardtom.propwash.race.LapTracker;
import de.lardtom.propwash.race.Leaderboard;
import de.lardtom.propwash.race.RaceModule;
import de.lardtom.propwash.race.RacePayloads;
import de.lardtom.propwash.race.RaceRecords;
import de.lardtom.propwash.race.RaceServer;
import de.lardtom.propwash.race.export.RaceDataJson;
import de.lardtom.propwash.race.export.RaceDataMirror;
import de.lardtom.propwash.ui.LayoutAudit;
import de.lardtom.propwash.ui.client.LayoutProbe;
import de.lardtom.propwash.ui.client.widget.FlatButton;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.input.MouseButtonInfo;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;

public final class RaceP2pSelfTests {
    private static final String GROUP = "race-p2p: ";
    private static final String GERMAN = "de_de";
    private static final String LOOP_PILOT = "PwRunde";
    private static final String PILOT = "PwP2pFlieger";
    private static final String GUEST = "PwP2pGast";
    private static final long MS = 1_000_000L;
    private static final double SPEED = 20.0;
    private static final AtomicReference<Site> SITE = new AtomicReference<>();
    private static final AtomicReference<String> RESULT = new AtomicReference<>("");
    private static final AtomicLong CLOCK = new AtomicLong(Long.MIN_VALUE);

    private record Site(int course, UUID key, Vec3 origin, List<Integer> gates, Vec3 home) {
    }

    private record Pending(CompletableFuture<Void> future, AtomicReference<String> result) {
    }

    private RaceP2pSelfTests() {
    }

    public static void register() {
        SelfTest.clientStep(GROUP + "Kurs mit dem Kurs-Werkzeug bauen – alte Kurse und neue Kurse fahren Runden", 600, RaceP2pSelfTests::setupStep);
        SelfTest.clientStep(GROUP + "Umschalten im Kurs-Fenster – Rundenzahl ausgeblendet, Ziel markiert, Bestenliste archiviert", 400,
                RaceP2pSelfTests::toggleStep);
        SelfTest.clientStep(GROUP + "Start und Ziel in der Welt gekennzeichnet", 200, RaceP2pSelfTests::worldStep);
        SelfTest.clientStep(GROUP + "P2P-Kurs fliegen – Zwischenzeiten an den Elementen, Ziel statt Rundenzähler", 300, RaceP2pSelfTests::flyStep);
        SelfTest.clientStep(GROUP + "Server prüft den Lauf von Start bis Ziel – Bestenliste unter der Kurs-ID, Renndaten mit Modus", 900,
                RaceP2pSelfTests::serverStep);
        SelfTest.clientStep(GROUP + "Replay zählt Start und Ziel mit wie Kurs-Einstellungen und Bestenliste", 400, RaceP2pSelfTests::replayGatesStep);
        if (Screenshots.enabled()) {
            shots("en", "");
            String back = GROUP + "Bildschirmfotos Sprache zurückstellen";
            SelfTest.switchStep(GROUP + "Bildschirmfotos Sprache Deutsch", back, 2400, ctx -> Screenshots.language(ctx, GERMAN));
            shots("de", " deutsch");
            SelfTest.clientStep(back, 2400, Screenshots::restoreLanguage);
        }
        SelfTest.clientStep(GROUP + "Zurück auf Runden – Rundenzahl wieder da, neue Layout-Nummer", 400, RaceP2pSelfTests::backStep);
        SelfTest.clientStep(GROUP + "Rechte – fremde Spieler sehen den Kurs nur an, Server lehnt Paket und Befehl ab", 400,
                RaceP2pSelfTests::rightsStep);
        SelfTest.clientStep(GROUP + "Aufräumen", 300, RaceP2pSelfTests::cleanupStep);
    }

    private static void shots(String language, String label) {
        Screenshots.step(GROUP + "Bildschirmfoto HUD mit „Ziel“" + label, "race_p2p_hud_finish_" + language, 20, new HudScene());
        Screenshots.step(GROUP + "Bildschirmfoto Kurs-Fenster Punkt zu Punkt" + label, "race_p2p_course_window_" + language, 20, new WindowScene());
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

    private static CourseRegistry.@Nullable Course course(Site site) {
        CourseRegistry registry = CourseRegistry.get();
        return registry == null ? null : registry.course(site.course());
    }

    private static @Nullable CourseEditScreen screen() {
        return Minecraft.getInstance().gui.screen() instanceof CourseEditScreen screen ? screen : null;
    }

    private static boolean click(CourseEditScreen screen, @Nullable FlatButton button) {
        if (button == null) {
            return false;
        }
        double x = button.getX() + button.getWidth() / 2.0;
        double y = button.getY() + button.getHeight() / 2.0;
        MouseButtonEvent event = new MouseButtonEvent(x, y, new MouseButtonInfo(InputConstants.MOUSE_BUTTON_LEFT, 0));
        boolean taken = screen.mouseClicked(event, false);
        screen.mouseReleased(event);
        return taken;
    }

    private static boolean lapsButtons(CourseEditScreen screen) {
        for (GuiEventListener child : screen.children()) {
            if (child instanceof FlatButton button && "−".equals(button.getMessage().getString())) {
                return true;
            }
        }
        return false;
    }

    private static StepResult setupStep(SelfTest.StepContext ctx) {
        StepResult wait = once(ctx, "Aufbau", server -> {
            ServerPlayer player = player(server);
            Vec3 origin = RaceDataJoinCheck.origin(player);
            List<GateEntity> spawned = new ArrayList<>();
            CourseRegistry.Course course = RaceP2pJoinCheck.build(player, origin, spawned);
            CourseRegistry registry = CourseRegistry.get();
            if (course == null || registry == null) {
                return "Kurs nicht angelegt";
            }
            if (course.mode() != CourseMode.LAPS || course.gates.size() != 3) {
                return "neuer Kurs fährt nicht Runden: " + course.mode() + " mit " + course.gates.size() + " Elementen";
            }
            for (CourseRegistry.Course other : registry.courses()) {
                if (other.mode == null && other.mode() != CourseMode.LAPS) {
                    return "Kurs " + other.id + " ohne Modus fährt nicht Runden";
                }
            }
            registry.rename(course, RaceP2pJoinCheck.NAME);
            ServerPlayer loop = RaceDataJoinCheck.secondPilot(server, player.level(), LOOP_PILOT);
            registry.submitLap(course, loop.getUUID(), LOOP_PILOT, "race_5", 31_000L * MS, System.currentTimeMillis());
            List<Integer> ids = new ArrayList<>();
            for (GateEntity gate : spawned) {
                ids.add(gate.getId());
            }
            SITE.set(new Site(course.id, course.key(), origin, ids, player.position()));
            return null;
        });
        if (wait != null) {
            return wait;
        }
        Site site = SITE.get();
        CourseData data = ClientCourses.get(site.course());
        if (data == null || data.gates().size() != 3 || data.mode() != CourseMode.LAPS) {
            return ctx.tick() > 400 ? StepResult.fail("Kurs kommt beim Client nicht an: " + data) : StepResult.pending();
        }
        ctx.log("Kurs " + site.course() + " mit drei Gates im Abstand von " + (int) RaceP2pJoinCheck.SPACING + " Blöcken, Modus Runden");
        return StepResult.pass();
    }

    private static StepResult toggleStep(SelfTest.StepContext ctx) {
        Site site = SITE.get();
        if (site == null) {
            return StepResult.fail("kein Kurs aus dem Aufbau");
        }
        CourseEditScreen screen = screen();
        if (screen == null) {
            if (ctx.state().containsKey("geöffnet")) {
                return ctx.tick() > 100 ? StepResult.fail("Kurs-Fenster öffnet nicht") : StepResult.pending();
            }
            ctx.state().put("geöffnet", true);
            CourseEditScreen.open(null, site.course());
            return StepResult.pending();
        }
        if (!ctx.state().containsKey("geklickt")) {
            FlatButton point = screen.pointModeButton();
            if (!screen.editable() || point == null || !point.active || screen.lapsModeButton() == null || !lapsButtons(screen)) {
                return StepResult.fail("Umschalter oder Rundenzahl fehlen: bearbeitbar=" + screen.editable() + " knopf=" + point);
            }
            if (!click(screen, point)) {
                return StepResult.fail("Klick auf „Punkt zu Punkt“ nicht angenommen");
            }
            ctx.state().put("geklickt", ctx.tick());
            return StepResult.pending();
        }
        CourseData data = ClientCourses.get(site.course());
        if (data == null || data.mode() != CourseMode.POINT_TO_POINT || screen.shownMode() != CourseMode.POINT_TO_POINT) {
            return ctx.tick() > 300 ? StepResult.fail("Modus kommt nicht zurück: " + (data == null ? "-" : data.mode())) : StepResult.pending();
        }
        if (lapsButtons(screen)) {
            return StepResult.fail("Rundenzahl bei Punkt zu Punkt noch einstellbar");
        }
        if (!ctx.state().containsKey("geprüft")) {
            ctx.state().put("geprüft", true);
            List<LayoutAudit.Finding> findings = LayoutProbe.audit(Minecraft.getInstance(), screen);
            if (!findings.isEmpty()) {
                return StepResult.fail(findings.size() + " Befunde im Kurs-Fenster: " + findings.getFirst().describe());
            }
        }
        StepResult wait = once(ctx, "Archiv", server -> {
            CourseRegistry.Course course = course(site);
            CourseRegistry registry = CourseRegistry.get();
            if (course == null || registry == null) {
                return "Kurs fehlt";
            }
            List<RaceRecords.Archived> archive = registry.records().archive(course.id);
            if (course.mode() != CourseMode.POINT_TO_POINT || course.layout != 2 || archive.size() != 1
                    || archive.getFirst().board().top(null, 10).isEmpty() || !registry.board(course).top(null, 10).isEmpty()) {
                return "Moduswechsel ist kein Layout-Wechsel: Layout " + course.layout + ", Archiv " + archive.size();
            }
            return null;
        });
        if (wait != null) {
            return wait;
        }
        Minecraft.getInstance().gui.setScreen(null);
        ctx.log("Kurs-Fenster: Punkt zu Punkt, Rundenzahl ausgeblendet, Bestenliste der Runden als Layout 1 archiviert");
        return StepResult.pass();
    }

    private static StepResult worldStep(SelfTest.StepContext ctx) {
        Site site = SITE.get();
        Minecraft mc = Minecraft.getInstance();
        if (site == null || mc.level == null) {
            return StepResult.fail("kein Kurs aus dem Aufbau");
        }
        List<String> tags = new ArrayList<>();
        for (int id : site.gates()) {
            if (!(mc.level.getEntity(id) instanceof GateEntity gate)) {
                return ctx.tick() > 100 ? StepResult.fail("Gate " + id + " fehlt beim Client") : StepResult.pending();
            }
            Component tag = GateRenderer.finishTag(gate);
            tags.add(tag == null ? "-" : tag.getString());
        }
        List<String> expected = List.of(Component.translatable("propwash.race.p2p.gate.start_tag").getString(), "-",
                Component.translatable("propwash.race.p2p.gate.finish_tag").getString());
        if (!tags.equals(expected)) {
            return ctx.tick() > 100 ? StepResult.fail("Kennzeichnung " + tags + " statt " + expected) : StepResult.pending();
        }
        ctx.log("Namensschilder: " + tags);
        return StepResult.pass();
    }

    private static final class Flight {
        private final ClientRaceTracker tracker;
        long now;
        Vec3 position;

        Flight(ClientRaceTracker tracker, long now, Vec3 start) {
            this.tracker = tracker;
            this.now = now;
            this.position = start;
            tracker.feed(now, start);
        }

        void to(Vec3 target, double speed) {
            Vec3 from = this.position;
            double length = from.distanceTo(target);
            int steps = Math.max(1, (int) Math.ceil(length / 0.25));
            long stepNanos = Math.round(length / speed / steps * 1.0E9);
            for (int i = 1; i <= steps; i++) {
                this.now += stepNanos;
                this.position = from.lerp(target, i / (double) steps);
                this.tracker.feed(this.now, this.position);
            }
        }
    }

    static @Nullable String flyClient(Minecraft mc, int courseId, List<LapTracker.Event> events) {
        CourseData course = ClientCourses.get(courseId);
        if (course == null || mc.level == null) {
            return "Kurs fehlt auf dem Client";
        }
        List<GateGeometry> gates = ClientCourses.geometry(course);
        GateGeometry first = gates.getFirst();
        GateGeometry last = gates.getLast();
        Vec3 start = new Vec3(first.centerX(), first.centerY(), first.centerZ() - 4.0);
        Vec3 end = new Vec3(last.centerX(), last.centerY(), last.centerZ() + 4.0);
        RaceConfig config = RaceConfig.CONFIG.get();
        boolean report = config.reportResults;
        config.reportResults = false;
        ClientRaceTracker tracker = ClientRaceTracker.INSTANCE;
        tracker.setEventListener(events::add);
        try {
            tracker.selectCourseAt(mc, start);
            if (tracker.courseId() != courseId || !tracker.tracker().pointToPoint()) {
                return "Kurs nicht als Punkt zu Punkt gewählt: " + tracker.courseId() + " p2p=" + tracker.tracker().pointToPoint();
            }
            tracker.ghosts().setPersist(false);
            tracker.ghosts().clear();
            long begin = Math.max(tracker.now(), CLOCK.get());
            Flight flight = new Flight(tracker, begin, start);
            flight.to(end, SPEED);
            CLOCK.set(flight.now + LapTracker.RESTART_COOLDOWN_NANOS + LapTracker.SECOND);
            return null;
        } finally {
            tracker.setEventListener(null);
            config.reportResults = report;
            tracker.ghosts().setPersist(true);
        }
    }

    private static StepResult flyStep(SelfTest.StepContext ctx) {
        Site site = SITE.get();
        Minecraft mc = Minecraft.getInstance();
        if (site == null) {
            return StepResult.fail("kein Kurs aus dem Aufbau");
        }
        RaceHud.clear();
        List<LapTracker.Event> events = new ArrayList<>();
        String problem = flyClient(mc, site.course(), events);
        if (problem != null) {
            return StepResult.fail(problem);
        }
        long starts = events.stream().filter(e -> e.type() == LapTracker.EventType.START).count();
        List<LapTracker.Event> splits = events.stream().filter(e -> e.type() == LapTracker.EventType.GATE).toList();
        List<LapTracker.Event> laps = events.stream().filter(e -> e.type() == LapTracker.EventType.LAP).toList();
        List<LapTracker.Event> finish = events.stream().filter(e -> e.type() == LapTracker.EventType.FINISH).toList();
        if (starts != 1 || splits.size() != 1 || laps.size() != 1 || finish.size() != 1) {
            return StepResult.fail("Ereignisse falsch: start=" + starts + " zwischen=" + splits.size() + " ziel=" + laps.size() + "/" + finish.size());
        }
        long expected = Math.round(2.0 * RaceP2pJoinCheck.SPACING / SPEED * 1.0E9);
        if (Math.abs(laps.getFirst().valueNanos() - expected) > 2 * MS || Math.abs(splits.getFirst().valueNanos() - expected / 2) > 2 * MS) {
            return StepResult.fail("Zeiten falsch: Zwischenzeit " + splits.getFirst().valueNanos() + ", Ziel " + laps.getFirst().valueNanos()
                    + " erwartet " + expected);
        }
        RaceState state = RaceState.INSTANCE;
        if (!state.finished() || !state.pointToPoint() || state.lapTarget() != 1) {
            return StepResult.fail("Renn-Zustand nach dem Ziel: fertig=" + state.finished() + " p2p=" + state.pointToPoint() + " ziel=" + state.lapTarget());
        }
        String finishPrefix = Component.translatable("propwash.race.p2p.msg.finish_time", "").getString().trim();
        String lapPrefix = Component.translatable("propwash.race.msg.lap", 1, "").getString().trim();
        boolean finishShown = false;
        for (Component message : RaceHud.recentMessages()) {
            String text = message.getString();
            if (text.startsWith(lapPrefix)) {
                return StepResult.fail("HUD zeigt einen Rundenzähler: " + text);
            }
            finishShown |= text.startsWith(finishPrefix);
        }
        if (!finishShown) {
            return StepResult.fail("HUD zeigt kein „Ziel“: " + RaceHud.recentMessages());
        }
        ctx.log("Zwischenzeit " + splits.getFirst().valueNanos() / 1.0E6 + " ms, Ziel nach " + laps.getFirst().valueNanos() / 1.0E6 + " ms");
        return StepResult.pass();
    }

    private static StepResult serverStep(SelfTest.StepContext ctx) {
        Site site = SITE.get();
        if (site == null) {
            return StepResult.fail("kein Kurs aus dem Aufbau");
        }
        StepResult wait = once(ctx, "Lauf", server -> {
            CourseRegistry.Course course = course(site);
            CourseRegistry registry = CourseRegistry.get();
            if (course == null || registry == null) {
                return "Kurs fehlt";
            }
            ServerLevel level = player(server).level();
            ServerPlayer pilot = RaceDataJoinCheck.secondPilot(server, level, PILOT);
            String problem = RaceP2pJoinCheck.fly(course, pilot, site.origin(), 9_000L * MS);
            if (problem != null) {
                return problem;
            }
            Leaderboard.Entry best = registry.board(course).best(pilot.getUUID(), null);
            if (best == null || Math.abs(best.nanos() - 9_000L * MS) > MS) {
                return "Zeit von Start bis Ziel fehlt in der Bestenliste: " + best;
            }
            CourseInfo info = PropwashApi.race().course(server, site.key()).orElse(null);
            if (info == null || info.mode() != CourseMode.POINT_TO_POINT || info.layout() != course.layout) {
                return "Renndaten melden " + info;
            }
            RaceDataMirror mirror = RaceDataMirror.get();
            if (mirror == null) {
                return "kein JSON-Spiegel";
            }
            mirror.flushNow();
            try {
                String courses = Files.readString(mirror.root().resolve(RaceDataJson.COURSES), StandardCharsets.UTF_8);
                Path board = mirror.root().resolve(RaceDataJson.LEADERBOARDS).resolve(site.key() + ".json");
                String leaderboard = Files.readString(board, StandardCharsets.UTF_8);
                if (!courses.contains("\"mode\": \"point_to_point\"") || !leaderboard.contains("\"mode\": \"point_to_point\"")
                        || !leaderboard.contains(PILOT)) {
                    return "JSON-Spiegel ohne Modus oder Zeit";
                }
            } catch (IOException e) {
                return "JSON nicht lesbar: " + e;
            }
            RESULT.set("Lauf von " + PILOT + " in " + best.nanos() / MS + " ms bestätigt (Bericht am Ziel " + (int) (2 * RaceP2pJoinCheck.SPACING)
                    + " Blöcke vom Start), Renndaten mode=" + info.mode().key());
            return null;
        });
        if (wait != null) {
            return wait;
        }
        ctx.log(RESULT.get());
        return StepResult.pass();
    }

    private static RacePayloads.@Nullable BoardRow pilotRow(int course) {
        RacePayloads.LeaderboardS2C board = ClientLeaderboards.get(course);
        if (board == null) {
            return null;
        }
        for (RacePayloads.BoardRow row : board.rows()) {
            if (PILOT.equals(row.name())) {
                return row;
            }
        }
        return null;
    }

    private static StepResult replayGatesStep(SelfTest.StepContext ctx) {
        Minecraft mc = Minecraft.getInstance();
        Site site = SITE.get();
        if (site == null) {
            return StepResult.fail("kein Kurs aus dem Aufbau");
        }
        StepResult wait = once(ctx, "Bestenliste", server -> RaceServer.sendLeaderboard(player(server), site.course(), false) ? null
                : "Bestenliste nicht geschickt");
        if (wait != null) {
            return wait;
        }
        ReplaySession session = ReplaySession.INSTANCE;
        if (!ctx.state().containsKey("geöffnet")) {
            RacePayloads.BoardRow row = pilotRow(site.course());
            if (row == null || !row.ghost()) {
                return ctx.tick() > 150 ? StepResult.fail("Zeile des Piloten ohne Geist: " + row) : StepResult.pending();
            }
            mc.gui.setScreen(new ReplayScreen(null, site.course(), row));
            ctx.state().put("geöffnet", true);
            return StepResult.pending();
        }
        ReplayScreen replay = ReplayScreen.active();
        if (replay == null || !replay.loaded() || replay.frames() < 3) {
            if (replay != null && replay.failure() != null) {
                return StepResult.fail("Replay nicht geladen: " + replay.failure());
            }
            return ctx.tick() > 300 ? StepResult.fail("Replay lädt nicht") : StepResult.pending();
        }
        CourseData data = ClientCourses.get(site.course());
        GhostTrack track = session.track();
        if (data == null || data.mode() != CourseMode.POINT_TO_POINT || track == null) {
            return StepResult.fail("Kurs nicht Punkt zu Punkt oder Spur fehlt");
        }
        String problem = RaceGhostsSelfTests.gateCount(replay, session, data, track.lapNanos(), true);
        if (problem != null) {
            return StepResult.fail(problem);
        }
        ctx.log("Punkt zu Punkt: " + data.gates().size() + " Gates in Bestenliste, Kurs und Replay, Marken bei "
                + RaceGhostsSelfTests.marksText(session.marks()));
        mc.gui.setScreen(null);
        return StepResult.pass();
    }

    private static StepResult backStep(SelfTest.StepContext ctx) {
        Site site = SITE.get();
        if (site == null) {
            return StepResult.fail("kein Kurs aus dem Aufbau");
        }
        CourseEditScreen screen = screen();
        if (screen == null) {
            if (ctx.state().containsKey("geöffnet")) {
                return ctx.tick() > 100 ? StepResult.fail("Kurs-Fenster öffnet nicht") : StepResult.pending();
            }
            ctx.state().put("geöffnet", true);
            CourseEditScreen.open(null, site.course());
            return StepResult.pending();
        }
        if (!ctx.state().containsKey("geklickt")) {
            if (!click(screen, screen.lapsModeButton())) {
                return StepResult.fail("Klick auf „Runden“ nicht angenommen");
            }
            ctx.state().put("geklickt", ctx.tick());
            return StepResult.pending();
        }
        CourseData data = ClientCourses.get(site.course());
        if (data == null || data.mode() != CourseMode.LAPS || !lapsButtons(screen)) {
            return ctx.tick() > 300 ? StepResult.fail("Runden kommen nicht zurück: " + (data == null ? "-" : data.mode())) : StepResult.pending();
        }
        if (!ctx.state().containsKey("geprüft")) {
            ctx.state().put("geprüft", true);
            List<LayoutAudit.Finding> findings = LayoutProbe.audit(Minecraft.getInstance(), screen);
            if (!findings.isEmpty()) {
                return StepResult.fail(findings.size() + " Befunde im Kurs-Fenster: " + findings.getFirst().describe());
            }
        }
        StepResult wait = once(ctx, "Layout", server -> {
            CourseRegistry.Course course = course(site);
            CourseRegistry registry = CourseRegistry.get();
            if (course == null || registry == null) {
                return "Kurs fehlt";
            }
            if (course.layout != 3 || registry.records().archive(course.id).size() != 2) {
                return "zurück auf Runden ist kein Layout-Wechsel: Layout " + course.layout;
            }
            return null;
        });
        if (wait != null) {
            return wait;
        }
        Minecraft.getInstance().gui.setScreen(null);
        ctx.log("Runden wieder eingestellt, Bestenliste von Punkt zu Punkt als Layout 2 archiviert, jetzt Layout 3");
        return StepResult.pass();
    }

    private static StepResult rightsStep(SelfTest.StepContext ctx) {
        Site site = SITE.get();
        if (site == null) {
            return StepResult.fail("kein Kurs aus dem Aufbau");
        }
        StepResult wait = once(ctx, "Rechte", server -> {
            CourseRegistry.Course course = course(site);
            CourseRegistry registry = CourseRegistry.get();
            if (course == null || registry == null) {
                return "Kurs fehlt";
            }
            registry.setMode(course, CourseMode.POINT_TO_POINT);
            ServerPlayer owner = player(server);
            ServerPlayer guest = RaceDataJoinCheck.secondPilot(server, owner.level(), GUEST);
            String rights = RaceP2pJoinCheck.rights(registry, course, owner, guest);
            String expected = "other=not_owner other_denied=yes ownerless=ownerless,allowed owner=allowed";
            if (!rights.equals(expected)) {
                return "Rechte " + rights + " statt " + expected;
            }
            server.getCommands().performPrefixedCommand(guest.createCommandSourceStack(), "propwash race mode " + course.id + " laps");
            server.getCommands().performPrefixedCommand(guest.createCommandSourceStack(), "propwash race name " + course.id + " " + RaceP2pJoinCheck.STOLEN);
            if (course.mode() != CourseMode.POINT_TO_POINT || !RaceP2pJoinCheck.NAME.equals(course.name)) {
                return "Befehl eines fremden Spielers ändert den Kurs: " + course.mode() + " " + course.name;
            }
            server.getCommands().performPrefixedCommand(owner.createCommandSourceStack(), "propwash race mode " + course.id + " laps");
            if (course.mode() != CourseMode.LAPS) {
                return "Besitzer kann per Befehl nicht umschalten";
            }
            RESULT.set(rights + " befehl_gast=abgelehnt befehl_besitzer=ok");
            return null;
        });
        if (wait != null) {
            return wait;
        }
        ctx.log(RESULT.get());
        return StepResult.pass();
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

    private static final class HudScene implements Screenshots.Scene {
        @Override
        public StepResult prepare(SelfTest.StepContext ctx) {
            Site site = SITE.get();
            Minecraft mc = Minecraft.getInstance();
            if (site == null || mc.player == null) {
                return StepResult.fail("kein Kurs für das Bildschirmfoto");
            }
            Vec3 finish = site.origin().add(0.0, 0.0, 2.0 * RaceP2pJoinCheck.SPACING);
            Vec3 at = finish.add(2.5, 1.5, -7.0);
            StepResult wait = once(ctx, "Blickpunkt", server -> {
                ServerPlayer player = player(server);
                player.getAbilities().flying = true;
                player.onUpdateAbilities();
                player.teleportTo(player.level(), at.x, at.y, at.z, Set.of(), 0.0F, 0.0F, true);
                player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(RaceModule.COURSE_TOOL.get()));
                return null;
            });
            if (wait != null) {
                return wait;
            }
            if (mc.player.position().distanceTo(at) > 0.6) {
                return ctx.tick() > 300 ? StepResult.fail("Spieler nicht am Blickpunkt") : StepResult.pending();
            }
            mc.gui.setScreen(null);
            aim(mc.player, finish.add(0.0, 3.0, 0.0));
            return StepResult.pass();
        }

        @Override
        public void hold(SelfTest.StepContext ctx) {
            Site site = SITE.get();
            Minecraft mc = Minecraft.getInstance();
            if (site == null || mc.player == null) {
                return;
            }
            aim(mc.player, site.origin().add(0.0, 3.0, 2.0 * RaceP2pJoinCheck.SPACING));
            if (ctx.state().putIfAbsent("geflogen", Boolean.TRUE) == null) {
                RaceHud.clear();
                flyClient(mc, site.course(), new ArrayList<>());
            }
        }

        @Override
        public void finish(SelfTest.StepContext ctx) {
            ctx.onServer(server -> player(server).setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY));
        }
    }

    private static final class WindowScene implements Screenshots.Scene {
        @Override
        public StepResult prepare(SelfTest.StepContext ctx) {
            Site site = SITE.get();
            Minecraft mc = Minecraft.getInstance();
            if (site == null) {
                return StepResult.fail("kein Kurs für das Bildschirmfoto");
            }
            if (!(mc.gui.screen() instanceof CourseEditScreen)) {
                CourseEditScreen.open(null, site.course());
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
