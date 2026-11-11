package de.lardtom.propwash.race.client;

import com.mojang.blaze3d.platform.InputConstants;
import de.lardtom.propwash.api.build.DroneData;
import de.lardtom.propwash.api.race.BoardEntry;
import de.lardtom.propwash.api.race.BoardQuery;
import de.lardtom.propwash.api.race.BoardSnapshot;
import de.lardtom.propwash.apiimpl.Conversions;
import de.lardtom.propwash.build.DroneBuild;
import de.lardtom.propwash.dev.SelfTest;
import de.lardtom.propwash.dev.SelfTest.StepResult;
import de.lardtom.propwash.dev.client.Screenshots;
import de.lardtom.propwash.race.AssistReasons;
import de.lardtom.propwash.race.BoardIndex;
import de.lardtom.propwash.race.CourseRegistry;
import de.lardtom.propwash.race.EntryGhost;
import de.lardtom.propwash.race.GhostCodec;
import de.lardtom.propwash.race.GhostServer;
import de.lardtom.propwash.race.GhostTrack;
import de.lardtom.propwash.race.LapAssist;
import de.lardtom.propwash.race.Leaderboard;
import de.lardtom.propwash.race.RacePayloads;
import de.lardtom.propwash.race.RaceRuleSet;
import de.lardtom.propwash.race.export.RaceSnapshots;
import de.lardtom.propwash.server.WorldRules;
import de.lardtom.propwash.server.rules.WorldRuleSpecs;
import de.lardtom.propwash.share.ShareLayer;
import de.lardtom.propwash.share.ShareService;
import de.lardtom.propwash.ui.LayoutAudit;
import de.lardtom.propwash.ui.client.LayoutProbe;
import de.lardtom.propwash.ui.client.widget.ScrollArea;
import net.minecraft.client.Minecraft;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.input.MouseButtonInfo;
import net.minecraft.commands.CommandSource;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

public final class RaceDataBoardSelfTests {
    public static final int RECORDS = 120;
    public static final int ASSISTED = 60;
    public static final int OWN_RANK = 87;
    public static final int OWN_ASSISTED_RANK = 40;
    public static final int SLOW_CLASS = 15;
    public static final int ALL = RECORDS + SLOW_CLASS;
    private static final String GROUP = "race-data: ";
    private static final String SLOW = "freestyle_5";
    private static final long MS = 1_000_000L;
    private static final int OTHER_COURSE = 987_654;
    private static final AtomicReference<List<Long>> EXPECTED = new AtomicReference<>(List.of());
    private static final AtomicReference<List<Long>> EXPECTED_ASSISTED = new AtomicReference<>(List.of());
    private static final AtomicInteger SAVED_RULE = new AtomicInteger(-1);
    private static final String FUTURE = "server";
    private static final String WHOOP = "whoop_65";
    private static final String RACE = "race_5";
    private static final String FREESTYLE = "freestyle_5";
    private static final int CLASS_ROWS = 6;
    private static final int CLASS_ASSISTED = 3;
    private static final int OWN_SLOW_RANK = 5;
    private static final AtomicReference<Map<String, String>> CODES = new AtomicReference<>(Map.of());

    private record Row(String name, String frame, int classRank) {
    }

    private RaceDataBoardSelfTests() {
    }

    static void register() {
        SelfTest.clientStep(GROUP + "Bestenliste mit 135 Zeiten ohne (120 Piloten, 15 davon in zwei Klassen) und 60 mit Flughilfe – alle Plätze, Seiten "
                + "beim Scrollen, Ränge wie am Server", 1800, RaceDataBoardSelfTests::allStep);
        SelfTest.clientStep(GROUP + "Abschnitte einklappen – Pfeil und Zahl, Zustand bleibt bei Klasse, Kurs und Neuöffnen", 600,
                RaceDataBoardSelfTests::collapseStep);
        SelfTest.clientStep(GROUP + "„Meine Zeit“ springt zur eigenen Zeile auf Platz 87 und 40", 600, RaceDataBoardSelfTests::mineStep);
        SelfTest.clientStep(GROUP + "Bestenliste bei GUI-Skalierung 2, 3 und 4 – Kopfzeilen und „Meine Zeit“ ganz sichtbar", 900,
                RaceDataBoardSelfTests::scaleStep);
        SelfTest.clientStep(GROUP + "„Alle“ mit Bestzeit je Pilot und Klasse – 20 s in Whoop oben, 30 s in Race unten, je Zeile Klasse, Platz, "
                + "Teilen-Code, Replay und Geist, Befehl und Schnittstelle gleich", 900, RaceDataBoardSelfTests::classesStep);
    }

    static void shots(String language, String label) {
        Screenshots.step(GROUP + "Bildschirmfoto Bestenliste ausgeklappt" + label, "race_data_board_expanded_" + language, 30, new BoardScene(false, false));
        Screenshots.step(GROUP + "Bildschirmfoto Bestenliste eingeklappt" + label, "race_data_board_collapsed_" + language, 30, new BoardScene(true, false));
        Screenshots.step(GROUP + "Bildschirmfoto Bestenliste gescrollt zur eigenen Zeit" + label, "race_data_board_scrolled_" + language, 40,
                new BoardScene(false, true));
        for (int scale = 2; scale <= 4; scale++) {
            Screenshots.step(GROUP + "Bildschirmfoto Bestenliste GUI-Skalierung " + scale + label, "race_data_board_scale" + scale + "_" + language, 30,
                    Screenshots.guiScale(scale, new BoardScene(false, false)));
        }
        Screenshots.step(GROUP + "Bildschirmfoto „Alle“ mit zwei Klassen eines Piloten" + label, "race_data_board_classes_" + language, 30,
                new ClassScene());
    }

    static StepResult cleanupStep(SelfTest.StepContext ctx) {
        BoardSections.reset();
        int saved = SAVED_RULE.getAndSet(-1);
        if (saved >= 0) {
            ctx.onServer(server -> WorldRules.set(server, WorldRuleSpecs.SAVE_ASSISTED_LAPS, saved));
        }
        return StepResult.pass();
    }

    private static @Nullable StepResult server(SelfTest.StepContext ctx, java.util.function.Function<MinecraftServer, @Nullable String> action) {
        Object stored = ctx.state().get(FUTURE);
        if (stored == null) {
            AtomicReference<String> result = new AtomicReference<>();
            CompletableFuture<Void> future = ctx.onServer(server -> result.set(action.apply(server)));
            ctx.state().put(FUTURE, new Object[]{future, result});
            return StepResult.pending();
        }
        Object[] pending = (Object[]) stored;
        @SuppressWarnings("unchecked") CompletableFuture<Void> future = (CompletableFuture<Void>) pending[0];
        @SuppressWarnings("unchecked") AtomicReference<String> result = (AtomicReference<String>) pending[1];
        if (future.isCompletedExceptionally()) {
            return StepResult.fail("Server: " + future.exceptionNow());
        }
        if (!future.isDone()) {
            return StepResult.pending();
        }
        return result.get() == null ? null : StepResult.fail(result.get());
    }

    private static @Nullable String seedIfNeeded(MinecraftServer server) {
        CourseRegistry registry = CourseRegistry.get();
        CourseRegistry.Course course = registry == null ? null : registry.course(RaceDataSelfTests.siteCourse());
        ServerPlayer player = server.getPlayerList().getPlayers().getFirst();
        if (course != null) {
            Leaderboard board = registry.board(course);
            if (board.size() == ALL && board.assistedSize() == ASSISTED && board.rank(player.getUUID(), null) == OWN_RANK) {
                return null;
            }
        }
        return seed(server);
    }

    private static @Nullable String seed(MinecraftServer server) {
        int id = RaceDataSelfTests.siteCourse();
        CourseRegistry registry = CourseRegistry.get();
        CourseRegistry.Course course = registry == null ? null : registry.course(id);
        if (course == null) {
            return "kein Kurs aus dem Aufbau";
        }
        ServerPlayer player = server.getPlayerList().getPlayers().getFirst();
        SAVED_RULE.compareAndSet(-1, WorldRules.get(server, WorldRuleSpecs.SAVE_ASSISTED_LAPS));
        WorldRules.set(server, WorldRuleSpecs.SAVE_ASSISTED_LAPS, 1);
        Leaderboard board = registry.board(course);
        board.clear();
        registry.pruneGhosts(course);
        long now = System.currentTimeMillis();
        long hour = 3_600_000L;
        int other = 0;
        for (int rank = 1; rank <= RECORDS; rank++) {
            long nanos = (40_000L + rank * 100L) * MS;
            if (rank == OWN_RANK) {
                board.submit(player.getUUID(), player.getGameProfile().name(), "race_5", nanos, now - hour);
                continue;
            }
            UUID pilot = new UUID(0x1BA11L, other);
            String name = String.format("Pilot_%03d", rank);
            board.submit(pilot, name, other % 3 == 0 ? "whoop_65" : "race_5", nanos, now - rank * hour);
            if (other < SLOW_CLASS) {
                board.submit(pilot, name, SLOW, (60_000L + other * 100L) * MS, now - rank * hour);
            }
            other++;
        }
        for (int rank = 1; rank <= ASSISTED; rank++) {
            long nanos = (35_000L + rank * 100L) * MS;
            int assist = LapAssist.values()[rank % LapAssist.values().length].bit();
            if (rank == OWN_ASSISTED_RANK) {
                board.submitAssisted(player.getUUID(), player.getGameProfile().name(), "whoop_65", nanos, now - hour, assist);
            } else {
                board.submitAssisted(new UUID(0x1BA12L, rank), String.format("Hilfe_%02d", rank), rank % 2 == 0 ? "race_5" : "whoop_65", nanos,
                        now - rank * hour, assist);
            }
        }
        registry.records().changed();
        EXPECTED.set(board.top(null, Integer.MAX_VALUE).stream().map(Leaderboard.Entry::nanos).toList());
        EXPECTED_ASSISTED.set(board.assistedTop(null, Integer.MAX_VALUE).stream().map(Leaderboard.Entry::nanos).toList());
        if (EXPECTED.get().size() != ALL || EXPECTED_ASSISTED.get().size() != ASSISTED || board.rank(player.getUUID(), null) != OWN_RANK) {
            return "Testdaten am Server: " + EXPECTED.get().size() + " ohne, " + EXPECTED_ASSISTED.get().size() + " mit Flughilfe, eigener Platz "
                    + board.rank(player.getUUID(), null);
        }
        return null;
    }

    private static @Nullable LeaderboardScreen open(SelfTest.StepContext ctx, String key) {
        return open(ctx, key, ALL, ASSISTED);
    }

    private static @Nullable LeaderboardScreen open(SelfTest.StepContext ctx, String key, int clean, int assisted) {
        Minecraft mc = Minecraft.getInstance();
        int course = RaceDataSelfTests.siteCourse();
        if (!ctx.state().containsKey(key)) {
            ctx.state().put(key, ctx.tick());
            mc.gui.setScreen(new LeaderboardScreen(null, course));
            return null;
        }
        LeaderboardScreen screen = LeaderboardScreen.active();
        RacePayloads.LeaderboardS2C board = ClientLeaderboards.get(course);
        if (screen == null || mc.gui.screen() != screen || screen.frames() < 3 || board == null || board.total(null, false) != clean
                || board.total(null, true) != assisted) {
            return null;
        }
        return screen;
    }

    private static boolean click(LeaderboardScreen screen, double x, double y) {
        MouseButtonEvent event = new MouseButtonEvent(x, y, new MouseButtonInfo(InputConstants.MOUSE_BUTTON_LEFT, 0));
        boolean taken = screen.mouseClicked(event, false);
        screen.mouseReleased(event);
        return taken;
    }

    private static @Nullable String ranked(BoardIndex index, int total, List<Long> expected, @Nullable String frame) {
        if (index.total() != total || index.loaded() != total) {
            return index.loaded() + " von " + total + " Plätzen geladen (Anzeige " + index.total() + ")";
        }
        for (int rank = 1; rank <= total; rank++) {
            RacePayloads.BoardRow row = index.row(rank);
            if (row == null || row.rank(frame) != rank) {
                return "Platz " + rank + " fehlt oder trägt Platz " + (row == null ? "-" : row.rank(frame));
            }
            if (!expected.isEmpty() && row.nanos() != expected.get(rank - 1)) {
                return "Platz " + rank + " mit " + row.nanos() + " statt " + expected.get(rank - 1) + " wie am Server";
            }
        }
        return null;
    }

    private static @Nullable String secondClass(BoardIndex index) {
        for (int rank = RECORDS + 1; rank <= ALL; rank++) {
            RacePayloads.BoardRow row = index.row(rank);
            if (row == null || !row.frame().equals(SLOW)) {
                return "Platz " + rank + " in „Alle“ ist nicht die Zeile der zweiten Klasse " + SLOW;
            }
            boolean first = false;
            for (int other = 1; other <= RECORDS; other++) {
                RacePayloads.BoardRow faster = index.row(other);
                first |= faster != null && faster.name().equals(row.name()) && !faster.frame().equals(SLOW);
            }
            if (!first) {
                return "Pilot " + row.name() + " auf Platz " + rank + " fehlt mit seiner schnelleren Klasse weiter oben";
            }
        }
        return null;
    }

    private static StepResult allStep(SelfTest.StepContext ctx) {
        StepResult wait = server(ctx, RaceDataBoardSelfTests::seed);
        if (wait != null) {
            return wait;
        }
        LeaderboardScreen screen = open(ctx, "offen");
        if (screen == null) {
            return ctx.tick() > 600 ? StepResult.fail("Bestenliste mit 135 und 60 Zeiten kommt nicht an: " + ClientLeaderboards.get(RaceDataSelfTests.siteCourse()))
                    : StepResult.pending();
        }
        if (!ctx.state().containsKey("start")) {
            BoardIndex first = screen.index(false);
            if (first.loaded() < 50 || first.complete()) {
                return StepResult.fail("erstes Paket mit " + first.loaded() + " von " + ALL + " Zeilen statt einer Seite von 50 und Nachladen");
            }
            if (screen.layout().height() <= screen.table().view().height()) {
                return StepResult.fail("Liste ist nicht länger als das Fenster");
            }
            ctx.state().put("start", new int[]{ClientLeaderboards.pageRequests(), ClientLeaderboards.pagesReceived(), first.loaded()});
            ctx.log("erstes Paket: " + first.loaded() + " von " + ALL + " ohne, " + screen.index(true).loaded() + " von " + ASSISTED + " mit Flughilfe");
            return StepResult.pending();
        }
        String filter = (String) ctx.state().get("klasse");
        ScrollArea table = screen.table();
        boolean done = screen.index(false).complete() && screen.index(true).complete();
        if (!done || table.offset() < table.maxOffset()) {
            if (screen.placeholdersDrawn() == 0 && !ClientLeaderboards.loading(screen.courseId(), filter, false)
                    && !ClientLeaderboards.loading(screen.courseId(), filter, true)) {
                screen.keyPressed(new KeyEvent(InputConstants.KEY_PAGEDOWN, 0, 0));
            }
            return ctx.tick() > 1600 ? StepResult.fail("Scrollen lädt nicht alle Plätze: " + screen.index(false).loaded() + " und "
                    + screen.index(true).loaded() + ", Versatz " + table.offset() + " von " + table.maxOffset()) : StepResult.pending();
        }
        if (filter == null) {
            String problem = ranked(screen.index(false), ALL, EXPECTED.get(), null);
            if (problem == null) {
                problem = secondClass(screen.index(false));
            }
            if (problem == null) {
                problem = ranked(screen.index(true), ASSISTED, EXPECTED_ASSISTED.get(), null);
            }
            if (problem == null && (screen.index(false).selfRank() != OWN_RANK || screen.index(true).selfRank() != OWN_ASSISTED_RANK)) {
                problem = "eigene Zeit auf Platz " + screen.index(false).selfRank() + " und " + screen.index(true).selfRank();
            }
            int[] start = (int[]) ctx.state().get("start");
            int requests = ClientLeaderboards.pageRequests() - start[0];
            int pages = ClientLeaderboards.pagesReceived() - start[1];
            if (problem == null && (requests < 3 || pages < 3)) {
                problem = "nur " + requests + " Anfragen und " + pages + " Seiten für 135 und 60 Zeiten";
            }
            if (problem != null) {
                Minecraft.getInstance().gui.setScreen(null);
                return StepResult.fail(problem);
            }
            ctx.log("alle Plätze geladen: 135 ohne (15 Piloten mit zweiter Klasse auf Platz 121 bis 135), 60 mit Flughilfe, " + requests
                    + " Anfragen, " + pages + " Seiten, eigene Zeit Platz " + OWN_RANK + " und " + OWN_ASSISTED_RANK);
            screen.selectFilter(SLOW);
            ctx.state().put("klasse", SLOW);
            return StepResult.pending();
        }
        String problem = ranked(screen.index(false), SLOW_CLASS, List.of(), SLOW);
        if (problem == null && screen.index(false).rows().stream()
                .anyMatch(row -> !row.frame().equals(SLOW) || row.overallRank() != RECORDS + row.classRank())) {
            problem = "Klasse " + SLOW + " zeigt fremde Zeilen oder Zeilen ohne eigenen Platz in „Alle“";
        }
        Minecraft.getInstance().gui.setScreen(null);
        if (problem != null) {
            return StepResult.fail("Klasse " + SLOW + ": " + problem);
        }
        ctx.log("Klasse " + SLOW + ": " + SLOW_CLASS + " Plätze nachgeladen, jede Zeile mit eigenem Platz in „Alle“ (" + (RECORDS + 1) + " bis " + ALL + ")");
        return StepResult.pass();
    }

    private static String code(String frame) {
        DroneBuild build = switch (frame) {
            case WHOOP -> DroneBuild.WHOOP;
            case RACE -> DroneBuild.RACER;
            default -> DroneBuild.FREESTYLE;
        };
        return ShareService.encode(DroneData.of(Conversions.spec(build)), ShareLayer.DEFAULT_EXPORT, true, Optional.empty());
    }

    private static GhostTrack track(CourseRegistry.Course course, long nanos, String frame, int layout) {
        CourseRegistry.GateRecord gate = course.gates.isEmpty() ? null : course.gates.getFirst();
        int samples = (int) (nanos / GhostServer.FINE_INTERVAL_NANOS) + 1;
        double[] positions = new double[samples * 3];
        float[] rotations = new float[samples * 4];
        for (int i = 0; i < samples; i++) {
            positions[i * 3] = (gate == null ? 0.0 : gate.x) + i * 0.01;
            positions[i * 3 + 1] = gate == null ? 0.0 : gate.y;
            positions[i * 3 + 2] = gate == null ? 0.0 : gate.z;
            rotations[i * 4 + 3] = 1.0F;
        }
        return new GhostTrack(GhostServer.FINE_INTERVAL_NANOS, positions, rotations, nanos, frame, layout);
    }

    private static @Nullable String classLap(CourseRegistry registry, CourseRegistry.Course course, UUID player, String name, String frame, long millis,
                                             long date, boolean assisted) {
        long nanos = millis * MS;
        int assist = assisted ? LapAssist.HOLD_MODE.bit() : LapAssist.NONE;
        Leaderboard.Result result = registry.submitTime(course, player, name, frame, nanos, date, AssistReasons.of(assist), RaceRuleSet.DEFAULTS, true);
        if (!result.stored()) {
            return name + " in " + frame + " nicht gespeichert";
        }
        int layout = course.toData().layoutHash();
        EntryGhost.Info info = new EntryGhost.Info(player, name, frame, nanos, date, assist, layout, 20.0F, CODES.get().get(frame),
                EntryGhost.Source.SERVER);
        return registry.entryGhosts().put(course.key(), new EntryGhost(info, GhostCodec.encode(track(course, nanos, frame, layout)))) ? null
                : "Geist von " + name + " in " + frame + " nicht abgelegt";
    }

    private static List<Row> cleanRows(String me) {
        return List.of(new Row(me, WHOOP, 1), new Row("Ben", WHOOP, 2), new Row("Anna", RACE, 1), new Row("Ben", RACE, 2), new Row(me, RACE, 3),
                new Row("Cleo", FREESTYLE, 1));
    }

    private static List<Row> assistedRows(String me) {
        return List.of(new Row(me, WHOOP, 1), new Row("Dana", RACE, 1), new Row(me, RACE, 2));
    }

    private static @Nullable String seedClasses(MinecraftServer server) {
        CourseRegistry registry = CourseRegistry.get();
        CourseRegistry.Course course = registry == null ? null : registry.course(RaceDataSelfTests.siteCourse());
        if (course == null) {
            return "kein Kurs aus dem Aufbau";
        }
        if (!registry.entryGhosts().enabled()) {
            return "Ablage der Geister im Weltordner fehlt";
        }
        ServerPlayer player = server.getPlayerList().getPlayers().getFirst();
        UUID me = player.getUUID();
        String name = player.getGameProfile().name();
        SAVED_RULE.compareAndSet(-1, WorldRules.get(server, WorldRuleSpecs.SAVE_ASSISTED_LAPS));
        WorldRules.set(server, WorldRuleSpecs.SAVE_ASSISTED_LAPS, 1);
        CODES.set(Map.of(WHOOP, code(WHOOP), RACE, code(RACE), FREESTYLE, code(FREESTYLE)));
        if (CODES.get().values().stream().anyMatch(code -> code.isEmpty() || code.length() > EntryGhost.MAX_CODE)) {
            return "Teilen-Codes der Testdrohnen leer oder zu lang";
        }
        Leaderboard board = registry.board(course);
        board.clear();
        registry.pruneGhosts(course);
        long now = System.currentTimeMillis();
        long hour = 3_600_000L;
        List<String> problems = new ArrayList<>();
        UUID anna = new UUID(0xC1A55L, 1L);
        UUID ben = new UUID(0xC1A55L, 2L);
        UUID cleo = new UUID(0xC1A55L, 3L);
        UUID dana = new UUID(0xC1A55L, 4L);
        for (String problem : new String[]{
                classLap(registry, course, me, name, RACE, 30_000L, now - 2 * hour, false),
                classLap(registry, course, me, name, WHOOP, 20_000L, now - hour, false),
                classLap(registry, course, anna, "Anna", RACE, 25_000L, now - 3 * hour, false),
                classLap(registry, course, ben, "Ben", WHOOP, 22_500L, now - 4 * hour, false),
                classLap(registry, course, ben, "Ben", RACE, 27_500L, now - 5 * hour, false),
                classLap(registry, course, cleo, "Cleo", FREESTYLE, 35_000L, now - 6 * hour, false),
                classLap(registry, course, me, name, WHOOP, 18_000L, now - 7 * hour, true),
                classLap(registry, course, me, name, RACE, 28_000L, now - 8 * hour, true),
                classLap(registry, course, dana, "Dana", RACE, 24_000L, now - 9 * hour, true)}) {
            if (problem != null) {
                problems.add(problem);
            }
        }
        registry.records().changed();
        List<String> expected = cleanRows(name).stream().map(row -> row.name() + " " + row.frame()).toList();
        List<String> listed = board.top(null, Integer.MAX_VALUE).stream().map(entry -> entry.name() + " " + entry.frame()).toList();
        if (!listed.equals(expected)) {
            problems.add("„Alle“ am Server " + listed + " statt " + expected);
        }
        if (board.rank(me, null) != 1 || board.allRank(me, RACE, false) != OWN_SLOW_RANK || board.assistedRank(me, null) != 1) {
            problems.add("eigene Plätze am Server " + board.rank(me, null) + ", " + board.allRank(me, RACE, false) + ", " + board.assistedRank(me, null));
        }
        BoardSnapshot api = RaceSnapshots.board(course, registry.records(), BoardQuery.laps().limit(BoardQuery.MAX_LIMIT)).orElse(null);
        List<String> apiRows = api == null ? List.of() : api.entries().stream().map(entry -> entry.rank() + " " + entry.name() + " " + entry.frame()).toList();
        BoardEntry ownSlow = api == null || api.entries().size() < OWN_SLOW_RANK ? null : api.entries().get(OWN_SLOW_RANK - 1);
        if (api == null || api.total() != CLASS_ROWS || ownSlow == null || !ownSlow.player().equals(me) || !ownSlow.frame().equals(RACE)) {
            problems.add("Schnittstelle „Alle“: " + apiRows);
        }
        List<String> chat = new ArrayList<>();
        CommandSource capture = new CommandSource() {
            @Override
            public void sendSystemMessage(Component message) {
                chat.add(message.getString());
            }

            @Override
            public boolean acceptsSuccess() {
                return true;
            }

            @Override
            public boolean acceptsFailure() {
                return true;
            }

            @Override
            public boolean shouldInformAdmins() {
                return false;
            }
        };
        CommandSourceStack source = server.createCommandSourceStack().withSource(capture).withLevel(player.level());
        server.getCommands().performPrefixedCommand(source, "propwash race leaderboard " + course.key());
        List<String> places = new ArrayList<>();
        for (String line : chat) {
            int dash = line.indexOf(" – ");
            if (dash > 0 && Character.isDigit(line.charAt(0))) {
                places.add(line.substring(0, dash));
            }
        }
        List<String> wanted = new ArrayList<>();
        for (int i = 0; i < expected.size(); i++) {
            wanted.add((i + 1) + ". " + cleanRows(name).get(i).name());
        }
        if (!places.equals(wanted)) {
            problems.add("Befehl /propwash race leaderboard: " + places + " statt " + wanted + " (" + String.join(" | ", chat) + ")");
        }
        return problems.isEmpty() ? null : String.join("; ", problems);
    }

    private static @Nullable String classRows(BoardIndex index, String me, List<Row> expected, @Nullable String frame) {
        if (index.total() != expected.size() || index.loaded() != expected.size()) {
            return index.loaded() + " von " + expected.size() + " Zeilen (Anzeige " + index.total() + ")";
        }
        long previous = Long.MIN_VALUE;
        for (int rank = 1; rank <= expected.size(); rank++) {
            Row want = expected.get(rank - 1);
            RacePayloads.BoardRow row = index.row(rank);
            if (row == null || !row.name().equals(want.name()) || !row.frame().equals(want.frame()) || row.classRank() != want.classRank()
                    || row.self() != want.name().equals(me)) {
                return "Platz " + rank + ": " + (row == null ? "fehlt" : row.name() + " " + row.frame() + " Klassenplatz " + row.classRank())
                        + " statt " + want.name() + " " + want.frame() + " Klassenplatz " + want.classRank();
            }
            if (frame == null && row.overallRank() != rank) {
                return "Platz " + rank + " trägt Gesamtplatz " + row.overallRank();
            }
            if (row.nanos() < previous) {
                return "Platz " + rank + " schneller als Platz " + (rank - 1);
            }
            previous = row.nanos();
            if (!row.ghost() || !row.code().equals(CODES.get().get(row.frame()))) {
                return "Platz " + rank + " (" + row.name() + ", " + row.frame() + ") ohne eigenen Geist oder Teilen-Code: Geist " + row.ghost()
                        + ", Code " + row.code().length() + " Zeichen";
            }
        }
        return null;
    }

    private static @Nullable String actions(LeaderboardScreen screen, RacePayloads.BoardRow want) {
        RacePayloads.BoardRow picked = screen.selectedRow();
        if (picked == null || !picked.key().equals(want.key())) {
            return "Auswahl " + (picked == null ? "leer" : picked.name() + " " + picked.frame()) + " statt " + want.frame();
        }
        if (!picked.code().equals(CODES.get().get(want.frame()))) {
            return "Teilen-Code der Zeile " + want.frame() + " ist nicht der eigene";
        }
        if (screen.copyButton() == null || !screen.copyButton().active || screen.replayButton() == null || !screen.replayButton().active
                || screen.opponentButton() == null || !screen.opponentButton().active) {
            return "Kopieren, Replay oder Geist für die Zeile " + want.frame() + " nicht bereit";
        }
        return null;
    }

    private static StepResult classesStep(SelfTest.StepContext ctx) {
        StepResult wait = server(ctx, RaceDataBoardSelfTests::seedClasses);
        if (wait != null) {
            return wait;
        }
        Minecraft mc = Minecraft.getInstance();
        LeaderboardScreen screen = open(ctx, "offen", CLASS_ROWS, CLASS_ASSISTED);
        if (screen == null || mc.player == null) {
            return ctx.tick() > 600 ? StepResult.fail("Bestenliste mit 6 und 3 Zeiten kommt nicht an") : StepResult.pending();
        }
        int stage = (int) ctx.state().getOrDefault("stufe", 0);
        int since = ctx.tick() - (int) ctx.state().getOrDefault("seit", 0);
        if (since < 3) {
            return StepResult.pending();
        }
        ctx.state().put("seit", ctx.tick());
        ctx.state().put("stufe", stage + 1);
        String me = mc.player.getGameProfile().name();
        String problem = null;
        switch (stage) {
            case 0 -> {
                BoardSections.reset();
                screen.selectFilter(null);
                return StepResult.pending();
            }
            case 1 -> {
                problem = classRows(screen.index(false), me, cleanRows(me), null);
                if (problem == null) {
                    problem = classRows(screen.index(true), me, assistedRows(me), null);
                }
                if (problem == null && (screen.index(false).selfRank() != 1 || screen.index(true).selfRank() != 1)) {
                    problem = "„Meine Zeit“ auf Platz " + screen.index(false).selfRank() + " und " + screen.index(true).selfRank() + " statt 1";
                }
                String mine = Component.translatable("propwash.race.board.mine", 1).getString();
                for (String text : List.of(mine, LeaderboardScreen.className(WHOOP).getString(), LeaderboardScreen.className(RACE).getString())) {
                    if (problem == null && !visible(mc, screen, text)) {
                        problem = "„" + text + "“ nicht ganz zu sehen";
                    }
                }
                if (problem == null) {
                    screen.select(screen.index(false).row(OWN_SLOW_RANK));
                    return StepResult.pending();
                }
            }
            case 2 -> {
                problem = actions(screen, screen.index(false).row(OWN_SLOW_RANK));
                if (problem == null) {
                    screen.select(screen.index(false).row(1));
                    return StepResult.pending();
                }
            }
            case 3 -> {
                problem = actions(screen, screen.index(false).row(1));
                if (problem == null) {
                    screen.select(screen.index(true).row(3));
                    return StepResult.pending();
                }
            }
            case 4 -> {
                problem = actions(screen, screen.index(true).row(3));
                if (problem == null) {
                    screen.select(null);
                    screen.selectFilter(RACE);
                    return StepResult.pending();
                }
            }
            default -> {
                BoardIndex race = screen.index(false);
                problem = classRows(race, me, List.of(new Row("Anna", RACE, 1), new Row("Ben", RACE, 2), new Row(me, RACE, 3)), RACE);
                if (problem == null && (race.selfRank() != 3 || race.row(3).overallRank() != OWN_SLOW_RANK)) {
                    problem = "Klasse " + RACE + ": eigene Zeile auf Platz " + race.selfRank() + ", in „Alle“ " + race.row(3).overallRank();
                }
                if (problem == null) {
                    screen.selectFilter(null);
                    mc.gui.setScreen(null);
                    ctx.log("„Alle“: " + me + " mit Whoop 20 s auf Platz 1 und Race 30 s auf Platz " + OWN_SLOW_RANK + ", Ben zweimal, je Zeile "
                            + "eigener Teilen-Code, Replay und Geist; mit Flughilfe Platz 1 und 3; Klasse Race 3 Zeilen; Befehl und Schnittstelle gleich");
                    return StepResult.pass();
                }
            }
        }
        mc.gui.setScreen(null);
        return StepResult.fail(problem);
    }

    private static StepResult collapseStep(SelfTest.StepContext ctx) {
        Minecraft mc = Minecraft.getInstance();
        LeaderboardScreen screen = open(ctx, "offen");
        if (screen == null) {
            return ctx.tick() > 300 ? StepResult.fail("Bestenliste öffnet nicht") : StepResult.pending();
        }
        int stage = (int) ctx.state().getOrDefault("stufe", 0);
        int since = ctx.tick() - (int) ctx.state().getOrDefault("seit", 0);
        if (since < 3) {
            return StepResult.pending();
        }
        ctx.state().put("seit", ctx.tick());
        ctx.state().put("stufe", stage + 1);
        int x = screen.tableCenterX();
        switch (stage) {
            case 0 -> {
                BoardSections.reset();
                screen.keyPressed(new KeyEvent(InputConstants.KEY_HOME, 0, 0));
                return StepResult.pending();
            }
            case 1 -> {
                if (screen.headerY(false) == Integer.MIN_VALUE || screen.headerY(true) != Integer.MIN_VALUE) {
                    return StepResult.fail("Kopfzeile „Bestzeiten“ fehlt oder „Mit Flughilfe“ schon oben: " + screen.headerY(false) + " "
                            + screen.headerY(true));
                }
                String label = Component.translatable("propwash.race.board.section_count", Component.translatable("propwash.race.board.records_section"),
                        ALL).getString();
                if (!visible(mc, screen, label)) {
                    return StepResult.fail("Kopfzeile „" + label + "“ nicht ganz zu sehen");
                }
                if (!click(screen, x, screen.headerY(false) + 4) || !BoardSections.collapsed(false)) {
                    return StepResult.fail("Klick auf die Kopfzeile klappt „Bestzeiten“ nicht ein");
                }
                return StepResult.pending();
            }
            case 2 -> {
                if (screen.layout().open(false) || screen.headerY(true) == Integer.MIN_VALUE || screen.index(false).total() != ALL) {
                    return StepResult.fail("eingeklappt, aber Zeilen sichtbar oder „Mit Flughilfe“ nicht nach oben gerückt: " + screen.headerY(true));
                }
                String label = Component.translatable("propwash.race.board.section_count", Component.translatable("propwash.race.board.assisted_section"),
                        ASSISTED).getString();
                if (!visible(mc, screen, label)) {
                    return StepResult.fail("Kopfzeile „" + label + "“ nicht ganz zu sehen");
                }
                screen.selectFilter("whoop_65");
                return StepResult.pending();
            }
            case 3 -> {
                if (screen.layout().open(false) || screen.filter() == null) {
                    return StepResult.fail("Klassenwechsel klappt „Bestzeiten“ wieder auf");
                }
                screen.selectCourse(OTHER_COURSE);
                return StepResult.pending();
            }
            case 4 -> {
                if (!BoardSections.collapsed(false) || screen.layout().open(false)) {
                    return StepResult.fail("Kurswechsel klappt „Bestzeiten“ wieder auf");
                }
                screen.selectCourse(RaceDataSelfTests.siteCourse());
                return StepResult.pending();
            }
            case 5 -> {
                mc.gui.setScreen(null);
                ctx.state().remove("offen");
                return StepResult.pending();
            }
            case 6 -> {
                if (screen.layout().open(false)) {
                    return StepResult.fail("neu geöffnete Bestenliste zeigt „Bestzeiten“ wieder ausgeklappt");
                }
                if (!click(screen, x, screen.headerY(true) + 4) || !BoardSections.collapsed(true)) {
                    return StepResult.fail("Klick klappt „Mit Flughilfe“ nicht ein");
                }
                return StepResult.pending();
            }
            case 7 -> {
                if (screen.layout().open(true) || screen.rowsDrawn() != 0) {
                    return StepResult.fail("beide eingeklappt, aber " + screen.rowsDrawn() + " Zeilen gezeichnet");
                }
                click(screen, x, screen.headerY(false) + 4);
                click(screen, x, screen.headerY(true) + 4);
                return StepResult.pending();
            }
            default -> {
                boolean open = screen.layout().open(false) && screen.layout().open(true) && screen.rowsDrawn() > 0;
                mc.gui.setScreen(null);
                if (!open) {
                    return StepResult.fail("zweiter Klick klappt die Abschnitte nicht wieder auf");
                }
                ctx.log("Bestzeiten und Mit Flughilfe ein- und ausgeklappt, Zustand blieb bei Klasse, Kurs und Neuöffnen");
                return StepResult.pass();
            }
        }
    }

    private static boolean visible(Minecraft mc, LeaderboardScreen screen, String text) {
        LayoutProbe.Frame frame = LayoutProbe.capture(mc, screen);
        for (LayoutAudit.Text shown : frame.texts()) {
            LayoutAudit.Box box = shown.visible();
            if (shown.value().equals(text) && box != null && box.width() >= shown.full().width() && box.height() >= shown.full().height()) {
                return true;
            }
        }
        return false;
    }

    private static boolean ownVisible(LeaderboardScreen screen, boolean assisted) {
        BoardIndex index = screen.index(assisted);
        RacePayloads.BoardRow own = index.row(index.selfRank());
        if (own == null) {
            return false;
        }
        int y = screen.rowY(own);
        return y != Integer.MIN_VALUE && y >= screen.table().view().top() && y + 16 <= screen.table().view().bottom();
    }

    private static StepResult mineStep(SelfTest.StepContext ctx) {
        Minecraft mc = Minecraft.getInstance();
        LeaderboardScreen screen = open(ctx, "offen");
        if (screen == null) {
            return ctx.tick() > 300 ? StepResult.fail("Bestenliste öffnet nicht") : StepResult.pending();
        }
        int stage = (int) ctx.state().getOrDefault("stufe", 0);
        int since = ctx.tick() - (int) ctx.state().getOrDefault("seit", 0);
        if (since < 3) {
            return StepResult.pending();
        }
        ctx.state().put("seit", ctx.tick());
        ctx.state().put("stufe", stage + 1);
        switch (stage) {
            case 0 -> {
                BoardSections.reset();
                screen.selectFilter(null);
                return StepResult.pending();
            }
            case 1 -> {
                if (ownVisible(screen, false)) {
                    return StepResult.fail("eigene Zeit schon ohne Sprung sichtbar");
                }
                String mine = Component.translatable("propwash.race.board.mine", OWN_RANK).getString();
                if (screen.mineX(false) == Integer.MIN_VALUE || !visible(mc, screen, mine)) {
                    return StepResult.fail("„" + mine + "“ fehlt in der Kopfzeile");
                }
                click(screen, screen.mineX(false), screen.headerY(false) + 4);
                return StepResult.pending();
            }
            case 2 -> {
                if (!ownVisible(screen, false) || screen.table().offset() == 0) {
                    return StepResult.fail("„Meine Zeit“ springt nicht zu Platz " + OWN_RANK + ": Versatz " + screen.table().offset());
                }
                ctx.log("„Meine Zeit“ zeigt Platz " + OWN_RANK + " bei Versatz " + screen.table().offset());
                BoardSections.set(true, true);
                screen.jumpToMine(true);
                return StepResult.pending();
            }
            default -> {
                boolean shown = ownVisible(screen, true) && screen.layout().open(true);
                mc.gui.setScreen(null);
                if (!shown) {
                    return StepResult.fail("„Meine Zeit“ klappt „Mit Flughilfe“ nicht auf oder zeigt Platz " + OWN_ASSISTED_RANK + " nicht");
                }
                ctx.log("„Meine Zeit“ klappt „Mit Flughilfe“ auf und zeigt Platz " + OWN_ASSISTED_RANK);
                return StepResult.pass();
            }
        }
    }

    private static StepResult scaleStep(SelfTest.StepContext ctx) {
        Minecraft mc = Minecraft.getInstance();
        if (!ctx.state().containsKey("skala")) {
            ctx.state().put("skala", mc.options.guiScale().get());
            ctx.state().put("stufe", 2);
            BoardSections.reset();
        }
        int scale = (int) ctx.state().get("stufe");
        if (scale > 4) {
            mc.options.guiScale().set((int) ctx.state().get("skala"));
            mc.resizeGui();
            mc.gui.setScreen(null);
            @SuppressWarnings("unchecked") List<String> seen = (List<String>) ctx.state().getOrDefault("gesehen", new ArrayList<String>());
            ctx.log("GUI-Skalierung: " + String.join(", ", seen));
            return StepResult.pass();
        }
        if (mc.options.guiScale().get() != scale) {
            mc.options.guiScale().set(scale);
            mc.resizeGui();
            ctx.state().remove("offen");
            ctx.state().put("gesetzt", ctx.tick());
            return StepResult.pending();
        }
        LeaderboardScreen screen = open(ctx, "offen");
        if (screen == null || ctx.tick() - (int) ctx.state().getOrDefault("gesetzt", 0) < 8) {
            return ctx.tick() > 800 ? StepResult.fail("Bestenliste bei Skalierung " + scale + " öffnet nicht") : StepResult.pending();
        }
        List<String> wanted = List.of(
                Component.translatable("propwash.race.board.section_count", Component.translatable("propwash.race.board.records_section"), ALL).getString(),
                Component.translatable("propwash.race.board.mine", OWN_RANK).getString());
        for (String text : wanted) {
            if (!visible(mc, screen, text)) {
                mc.options.guiScale().set((int) ctx.state().get("skala"));
                mc.resizeGui();
                mc.gui.setScreen(null);
                return StepResult.fail("GUI-Skalierung " + scale + " (" + mc.getWindow().getGuiScaledWidth() + "x" + mc.getWindow().getGuiScaledHeight()
                        + "): „" + text + "“ nicht ganz zu sehen");
            }
        }
        int rows = screen.rowsDrawn();
        if (rows < 3) {
            mc.options.guiScale().set((int) ctx.state().get("skala"));
            mc.resizeGui();
            mc.gui.setScreen(null);
            return StepResult.fail("GUI-Skalierung " + scale + ": nur " + rows + " Zeilen sichtbar");
        }
        List<LayoutAudit.Finding> findings = LayoutProbe.audit(mc, screen);
        if (!findings.isEmpty()) {
            mc.options.guiScale().set((int) ctx.state().get("skala"));
            mc.resizeGui();
            mc.gui.setScreen(null);
            return StepResult.fail("GUI-Skalierung " + scale + ": " + findings.getFirst().describe() + (findings.size() > 1 ? " (+" + (findings.size() - 1) + ")" : ""));
        }
        @SuppressWarnings("unchecked") List<String> seen = (List<String>) ctx.state().computeIfAbsent("gesehen", key -> new ArrayList<String>());
        seen.add(scale + " (" + mc.getWindow().getGuiScaledWidth() + "x" + mc.getWindow().getGuiScaledHeight() + ", " + rows + " Zeilen)");
        ctx.state().put("stufe", scale + 1);
        ctx.state().remove("offen");
        mc.gui.setScreen(null);
        return StepResult.pending();
    }

    private static final class BoardScene implements Screenshots.Scene {
        private final boolean collapsed;
        private final boolean scrolled;

        BoardScene(boolean collapsed, boolean scrolled) {
            this.collapsed = collapsed;
            this.scrolled = scrolled;
        }

        @Override
        public StepResult prepare(SelfTest.StepContext ctx) {
            StepResult wait = server(ctx, RaceDataBoardSelfTests::seedIfNeeded);
            if (wait != null) {
                return wait;
            }
            if (!ctx.state().containsKey("vorbereitet")) {
                ctx.state().put("vorbereitet", true);
                BoardSections.reset();
                BoardSections.set(false, this.collapsed);
            }
            LeaderboardScreen screen = open(ctx, "offen");
            if (screen == null) {
                return ctx.tick() > 400 ? StepResult.fail("Bestenliste für das Bildschirmfoto öffnet nicht") : StepResult.pending();
            }
            if (this.scrolled && !ctx.state().containsKey("gesprungen")) {
                ctx.state().put("gesprungen", true);
                screen.jumpToMine(false);
            }
            Screenshots.parkMouse(Minecraft.getInstance());
            return StepResult.pass();
        }

        @Override
        public void finish(SelfTest.StepContext ctx) {
            BoardSections.reset();
            Minecraft.getInstance().gui.setScreen(null);
        }
    }

    private static final class ClassScene implements Screenshots.Scene {
        @Override
        public StepResult prepare(SelfTest.StepContext ctx) {
            StepResult wait = server(ctx, RaceDataBoardSelfTests::seedClasses);
            if (wait != null) {
                return wait;
            }
            if (!ctx.state().containsKey("vorbereitet")) {
                ctx.state().put("vorbereitet", true);
                BoardSections.reset();
            }
            LeaderboardScreen screen = open(ctx, "offen", CLASS_ROWS, CLASS_ASSISTED);
            if (screen == null) {
                return ctx.tick() > 400 ? StepResult.fail("Bestenliste mit zwei Klassen für das Bildschirmfoto öffnet nicht") : StepResult.pending();
            }
            RacePayloads.BoardRow own = screen.index(false).row(OWN_SLOW_RANK);
            if (own == null || !own.self() || !own.frame().equals(RACE)) {
                return StepResult.fail("eigene Race-Zeile fehlt auf Platz " + OWN_SLOW_RANK);
            }
            if (!ctx.state().containsKey("gewählt")) {
                ctx.state().put("gewählt", true);
                screen.select(own);
                return StepResult.pending();
            }
            Screenshots.parkMouse(Minecraft.getInstance());
            return StepResult.pass();
        }

        @Override
        public void finish(SelfTest.StepContext ctx) {
            BoardSections.reset();
            Minecraft.getInstance().gui.setScreen(null);
        }
    }
}
