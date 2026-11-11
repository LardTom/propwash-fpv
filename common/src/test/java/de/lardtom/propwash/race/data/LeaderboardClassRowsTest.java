package de.lardtom.propwash.race.data;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import de.lardtom.propwash.api.race.BoardEntry;
import de.lardtom.propwash.api.race.BoardQuery;
import de.lardtom.propwash.api.race.BoardSnapshot;
import de.lardtom.propwash.api.race.CourseInfo;
import de.lardtom.propwash.api.race.LapResult;
import de.lardtom.propwash.api.race.PersonalBest;
import de.lardtom.propwash.api.race.RaceEvents;
import de.lardtom.propwash.race.AssistReasons;
import de.lardtom.propwash.race.BoardIndex;
import de.lardtom.propwash.race.BoardPager;
import de.lardtom.propwash.race.CourseRegistry;
import de.lardtom.propwash.race.EntryGhost;
import de.lardtom.propwash.race.EntryGhosts;
import de.lardtom.propwash.race.GhostCodec;
import de.lardtom.propwash.race.GhostServer;
import de.lardtom.propwash.race.GhostTrack;
import de.lardtom.propwash.race.LapAssist;
import de.lardtom.propwash.race.Leaderboard;
import de.lardtom.propwash.race.LeaderboardPages;
import de.lardtom.propwash.race.RacePayloads;
import de.lardtom.propwash.race.RaceRuleSet;
import de.lardtom.propwash.race.RaceServer;
import de.lardtom.propwash.race.export.RaceDataEvents;
import de.lardtom.propwash.race.export.RaceDataJson;
import de.lardtom.propwash.race.export.RaceSnapshots;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LeaderboardClassRowsTest {
    private static final long S = TestWorlds.S;
    private static final long MS = 1_000_000L;
    private static final long DAY = TestWorlds.DAY;
    private static final UUID ME = new UUID(0xC1A55L, 1L);
    private static final UUID ANNA = TestWorlds.ANNA;
    private static final UUID BEN = TestWorlds.BEN;
    private static final UUID CARL = TestWorlds.CARL;
    private static final int HOLD = LapAssist.HOLD_MODE.bit();
    private static final List<Object> SEEN = new CopyOnWriteArrayList<>();
    private static boolean registered;

    @TempDir
    Path temp;

    @BeforeAll
    static synchronized void listen() {
        if (registered) {
            return;
        }
        registered = true;
        RaceEvents.PERSONAL_BEST.register(SEEN::add);
        RaceEvents.LAP_COMPLETED.register(SEEN::add);
    }

    @BeforeEach
    void clear() {
        SEEN.clear();
    }

    static Leaderboard owner() {
        Leaderboard board = new Leaderboard();
        board.submit(ME, "Ich", "race_5", 30 * S, DAY);
        board.submit(ME, "Ich", "whoop_65", 20 * S, DAY + 1);
        board.submit(ANNA, "Anna", "race_5", 25 * S, DAY + 2);
        board.submit(BEN, "Ben", "whoop_65", 22 * S, DAY + 3);
        board.submit(BEN, "Ben", "race_5", 27 * S, DAY + 4);
        board.submit(CARL, "Carl", "freestyle_5", 35 * S, DAY + 5);
        board.submit(ME, "Ich", "race_5", 31 * S, DAY + 6);
        board.submitAssisted(ME, "Ich", "whoop_65", 18 * S, DAY + 7, HOLD);
        board.submitAssisted(ME, "Ich", "race_5", 28 * S, DAY + 8, HOLD);
        board.submitAssisted(ANNA, "Anna", "race_5", 24 * S, DAY + 9, HOLD);
        return board;
    }

    private static List<String> rows(List<Leaderboard.Entry> entries) {
        return entries.stream().map(entry -> entry.name() + " " + entry.frame() + " " + entry.nanos() / S).toList();
    }

    private static List<String> ranks(List<Leaderboard.Ranked> ranked) {
        return ranked.stream().map(r -> r.entry().name() + " " + r.entry().frame() + " " + r.classRank() + "/" + r.overallRank()).toList();
    }

    @Test
    void allListsTheBestTimeOfEveryPilotInEveryClassSortedByTime() {
        Leaderboard board = owner();
        assertEquals(List.of("Ich whoop_65 20", "Ben whoop_65 22", "Anna race_5 25", "Ben race_5 27", "Ich race_5 30", "Carl freestyle_5 35"),
                rows(board.top(null, Integer.MAX_VALUE)));
        assertEquals(List.of("Ich whoop_65 20", "Ben whoop_65 22", "Anna race_5 25"), rows(board.top(null, 3)));
        assertEquals(List.of("Anna race_5 25", "Ben race_5 27", "Ich race_5 30"), rows(board.top("race_5", Integer.MAX_VALUE)),
                "innerhalb einer Klasse bleibt je Pilot nur die Bestzeit");
        assertEquals(List.of("Ich whoop_65 1/1", "Ben whoop_65 2/2", "Anna race_5 1/3", "Ben race_5 2/4", "Ich race_5 3/5", "Carl freestyle_5 1/6"),
                ranks(board.ranked(false)));
        assertEquals(1, board.rank(ME, null), "eigener Platz in „Alle“ ist die beste eigene Zeile");
        assertEquals(2, board.rank(BEN, null));
        assertEquals(3, board.rank(ME, "race_5"));
        assertEquals("whoop_65", board.record(null).orElseThrow().frame());
        assertEquals("whoop_65", board.best(ME, null).frame());
        assertEquals(6, board.size());
        board.submit(ME, "Ich", "race_5", 26 * S, DAY + 20);
        assertEquals(List.of("Ich whoop_65 20", "Ben whoop_65 22", "Anna race_5 25", "Ich race_5 26", "Ben race_5 27", "Carl freestyle_5 35"),
                rows(board.top(null, Integer.MAX_VALUE)), "schnellere Zeit ersetzt die Zeile der Klasse");
        assertEquals(List.of(1, 2, 3, 4, 5, 6), board.ranked(false).stream().map(Leaderboard.Ranked::overallRank).toList());
    }

    @Test
    void assistedFollowsTheSameRule() {
        Leaderboard board = owner();
        assertEquals(List.of("Ich whoop_65 18", "Anna race_5 24", "Ich race_5 28"), rows(board.assistedTop(null, Integer.MAX_VALUE)));
        assertEquals(List.of("Ich whoop_65 1/1", "Anna race_5 1/2", "Ich race_5 2/3"), ranks(board.ranked(true)));
        assertEquals(1, board.assistedRank(ME, null));
        assertEquals(2, board.assistedRank(ANNA, null));
        assertEquals(3, board.assistedSize());
    }

    static Leaderboard crowded() {
        Leaderboard board = new Leaderboard();
        for (int i = 0; i < 70; i++) {
            UUID pilot = LeaderboardAllTest.pilot(i);
            board.submit(pilot, "Pilot" + i, "race_5", (30_000L + i * 20L) * MS, DAY + i);
            if (i < 40) {
                board.submit(pilot, "Pilot" + i, "whoop_65", (30_010L + i * 20L) * MS, DAY + 100 + i);
            }
        }
        board.submit(LeaderboardAllTest.VIEWER, "Ich", "race_5", 31_005L * MS, DAY + 300);
        board.submit(LeaderboardAllTest.VIEWER, "Ich", "whoop_65", 30_405L * MS, DAY + 301);
        for (int i = 0; i < 30; i++) {
            UUID pilot = new UUID(0x1BA12L, i);
            board.submitAssisted(pilot, "Hilfe" + i, "race_5", (20_000L + i * 20L) * MS, DAY + i, HOLD);
            board.submitAssisted(pilot, "Hilfe" + i, "whoop_65", (20_010L + i * 20L) * MS, DAY + i, HOLD);
        }
        return board;
    }

    private static RacePayloads.LeaderboardS2C load(Leaderboard board, CourseRegistry.Course course, @Nullable String frame, boolean assisted) {
        RacePayloads.LeaderboardS2C client = RaceServer.leaderboardPayload(board, course, LeaderboardAllTest.VIEWER, false, true);
        BoardPager pager = new BoardPager();
        long now = 0L;
        for (int guard = 0; guard < 100; guard++) {
            RacePayloads.LeaderboardPageC2S request = pager.next(client, frame, assisted, 1, client.total(frame, assisted), now);
            if (request == null) {
                return client;
            }
            RacePayloads.LeaderboardPageS2C page = RaceServer.leaderboardPage(board, course, LeaderboardAllTest.VIEWER, request, true);
            pager.received(page.courseId());
            client = client.merge(page);
            now += BoardPager.INTERVAL_NANOS;
        }
        throw new AssertionError("Bestenliste lädt nicht fertig");
    }

    @Test
    void pagesFirstPacketAndOwnTimeShowEveryClassRow() {
        Leaderboard board = crowded();
        CourseRegistry.Course course = new CourseRegistry.Course();
        RacePayloads.LeaderboardS2C first = RaceServer.leaderboardPayload(board, course, LeaderboardAllTest.VIEWER, false, true);
        assertEquals(112, first.total(null, false), "70 + 40 Zeilen der Piloten und 2 eigene");
        assertEquals(60, first.total(null, true));
        assertEquals(172, first.times());
        List<Integer> own = first.rows().stream().filter(row -> row.self() && !row.assisted()).map(RacePayloads.BoardRow::overallRank).sorted().toList();
        assertEquals(List.of(42, 93), own, "beide eigenen Zeilen mit eigenem Platz im ersten Paket");
        RacePayloads.LeaderboardS2C all = load(board, course, null, false);
        BoardIndex index = BoardIndex.of(all, null, false);
        assertEquals(112, index.loaded());
        assertTrue(index.complete());
        List<Leaderboard.Entry> server = board.top(null, Integer.MAX_VALUE);
        for (int rank = 1; rank <= 112; rank++) {
            RacePayloads.BoardRow row = index.row(rank);
            assertNotNull(row, "Platz " + rank + " fehlt");
            assertEquals(server.get(rank - 1).nanos(), row.nanos(), "Platz " + rank);
            assertEquals(server.get(rank - 1).frame(), row.frame(), "Klasse auf Platz " + rank);
        }
        assertEquals(42, index.selfRank(), "„Meine Zeit“ springt auf die beste eigene Zeile");
        assertEquals("whoop_65", index.row(42).frame());
        assertEquals("race_5", index.row(93).frame());
        assertTrue(index.row(93).self());
        BoardIndex race = BoardIndex.of(load(board, course, "race_5", false), "race_5", false);
        assertEquals(71, race.loaded());
        assertTrue(race.row(race.selfRank()).self());
        BoardIndex assisted = BoardIndex.of(load(board, course, null, true), null, true);
        assertEquals(60, assisted.loaded());
        assertTrue(assisted.complete());
        assertEquals("race_5", assisted.row(1).frame());
        assertEquals("whoop_65", assisted.row(2).frame());
        assertEquals(assisted.row(1).name(), assisted.row(2).name());
    }

    private static GhostTrack track(long lapNanos, String frame, int layout) {
        int samples = (int) (lapNanos / GhostServer.FINE_INTERVAL_NANOS) + 1;
        double[] positions = new double[samples * 3];
        float[] rotations = new float[samples * 4];
        for (int i = 0; i < samples; i++) {
            positions[i * 3] = -30.5 + i * 0.05;
            positions[i * 3 + 1] = -59.0;
            positions[i * 3 + 2] = 16.5 + i * 0.05;
            rotations[i * 4 + 3] = 1.0F;
        }
        return new GhostTrack(GhostServer.FINE_INTERVAL_NANOS, positions, rotations, lapNanos, frame, layout);
    }

    private static void lap(CourseRegistry registry, CourseRegistry.Course course, UUID player, String name, String frame, long nanos, long date) {
        assertTrue(registry.submitTime(course, player, name, frame, nanos, date, AssistReasons.NONE, RaceRuleSet.DEFAULTS, true).stored());
        int layout = course.toData().layoutHash();
        EntryGhost.Info info = new EntryGhost.Info(player, name, frame, nanos, date, LapAssist.NONE, layout, 20.0F, "PW3-" + name + "-" + frame,
                EntryGhost.Source.SERVER);
        assertTrue(registry.entryGhosts().put(course.key(), new EntryGhost(info, GhostCodec.encode(track(nanos, frame, layout)))));
    }

    @Test
    void everyClassRowCarriesItsOwnRankCodeAndGhost() throws IOException {
        CourseRegistry registry = TestWorlds.open(this.temp, TestWorlds.V040);
        registry.useEntryGhosts(new EntryGhosts(this.temp.resolve("race-data")));
        CourseRegistry.Course course = registry.course(1);
        lap(registry, course, ME, "Ich", "race_5", 30 * S, DAY);
        lap(registry, course, ME, "Ich", "whoop_65", 20 * S, DAY + 1);
        lap(registry, course, ANNA, "Anna", "race_5", 25 * S, DAY + 2);
        Leaderboard board = registry.board(course);
        List<LeaderboardPages.Pick> picks = LeaderboardPages.first(board.ranked(false), board.ranked(true), ME);
        List<RacePayloads.BoardRow> rows = new ArrayList<>();
        List<Leaderboard.Entry> entries = new ArrayList<>();
        for (LeaderboardPages.Pick pick : picks) {
            rows.add(LeaderboardPages.row(pick, ME));
            entries.add(pick.ranked().entry());
        }
        List<RacePayloads.BoardRow> decorated = GhostServer.decorate(registry, course, rows, entries);
        List<String> shown = decorated.stream().filter(row -> row.overallRank() > 0).sorted((a, b) -> a.overallRank() - b.overallRank())
                .map(row -> row.overallRank() + " " + row.name() + " " + row.frame() + " " + row.code() + " " + row.ghost()).toList();
        assertEquals(List.of("1 Ich whoop_65 PW3-Ich-whoop_65 true", "2 Anna race_5 PW3-Anna-race_5 true", "3 Ich race_5 PW3-Ich-race_5 true"), shown);
    }

    @Test
    void apiJsonAndEventsUseTheSameRule() throws IOException {
        CourseRegistry registry = RaceDataJsonTest.fixture(this.temp);
        CourseRegistry.Course course = registry.course(1);
        BoardSnapshot all = RaceSnapshots.board(course, registry.records(), BoardQuery.laps().limit(BoardQuery.MAX_LIMIT)).orElseThrow();
        assertEquals(List.of("1 Anna race_5", "2 Ben <B&B> race_5", "3 Anna whoop_65"),
                all.entries().stream().map(entry -> entry.rank() + " " + entry.name() + " " + entry.frame()).toList());
        assertEquals(3, all.total());
        CourseInfo info = RaceSnapshots.info(course, registry.records(), List.of());
        JsonObject root = JsonParser.parseString(RaceDataJson.leaderboard(info, course, registry.records())).getAsJsonObject();
        JsonArray clean = root.getAsJsonArray("layouts").get(0).getAsJsonObject().getAsJsonObject("laps").getAsJsonArray("clean");
        assertEquals(3, clean.size());
        assertEquals("whoop_65", clean.get(2).getAsJsonObject().get("frame").getAsString());
        assertEquals(3, clean.get(2).getAsJsonObject().get("rank").getAsInt());
        SEEN.clear();
        registry.submitLap(course, ME, "Ich", "whoop_65", 20 * S, DAY + 100);
        registry.submitLap(course, ME, "Ich", "race_5", 30 * S, DAY + 101);
        List<PersonalBest> bests = SEEN.stream().filter(PersonalBest.class::isInstance).map(PersonalBest.class::cast).toList();
        assertEquals(2, bests.size());
        BoardEntry race = bests.get(1).entry();
        assertEquals("race_5", race.frame());
        assertEquals(30_000L, race.timeMillis());
        assertEquals(2, race.rank(), "Platz der 30-s-Zeile in „Alle“, nicht der der besten eigenen Zeile");
        RaceDataEvents.lap(registry, course, ME, "Ich", 2, 30 * S, "race_5", AssistReasons.NONE, true, true, false, false);
        LapResult lap = SEEN.stream().filter(LapResult.class::isInstance).map(LapResult.class::cast).toList().getLast();
        assertEquals(2, lap.rank());
        RaceDataEvents.lap(registry, course, ME, "Ich", 3, 20 * S, "whoop_65", AssistReasons.NONE, true, true, false, false);
        assertEquals(1, SEEN.stream().filter(LapResult.class::isInstance).map(LapResult.class::cast).toList().getLast().rank());
    }
}
