package de.lardtom.propwash.race.data;

import de.lardtom.propwash.race.BoardIndex;
import de.lardtom.propwash.race.BoardPager;
import de.lardtom.propwash.race.CourseRegistry;
import de.lardtom.propwash.race.LapAssist;
import de.lardtom.propwash.race.Leaderboard;
import de.lardtom.propwash.race.LeaderboardPages;
import de.lardtom.propwash.race.RaceCommands;
import de.lardtom.propwash.race.RacePayloads;
import de.lardtom.propwash.race.RaceServer;
import de.lardtom.propwash.race.client.BoardLayout;
import de.lardtom.propwash.race.client.BoardSections;
import io.netty.buffer.Unpooled;
import net.minecraft.network.FriendlyByteBuf;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LeaderboardPagesTest {
    private static final long MS = 1_000_000L;
    private static final long DAY = TestWorlds.DAY;
    private static final UUID VIEWER = LeaderboardAllTest.VIEWER;
    private static final int ROW = 16;
    private static final int GAP = 6;

    @AfterEach
    void resetSections() {
        BoardSections.reset();
    }

    static Leaderboard mixed() {
        Leaderboard board = new Leaderboard();
        for (int i = 0; i < 119; i++) {
            String frame = i < 80 ? "race_5" : "whoop_65";
            board.submit(LeaderboardAllTest.pilot(i), "Pilot" + i, frame, (30_000L + i * 10L) * MS, DAY + i);
        }
        board.submit(VIEWER, "Ich", "race_5", (30_000L + 855L) * MS, DAY + 500);
        for (int i = 0; i < 15; i++) {
            board.submit(LeaderboardAllTest.pilot(i), "Pilot" + i, "whoop_65", (40_000L + i * 10L) * MS, DAY + 600 + i);
        }
        for (int i = 0; i < 60; i++) {
            board.submitAssisted(new UUID(0x1BA12L, i), "Hilfe" + i, i % 3 == 0 ? "whoop_65" : "race_5", (20_000L + i * 10L) * MS, DAY + i,
                    LapAssist.HOLD_MODE.bit());
        }
        return board;
    }

    private static <T> T roundTrip(T payload, net.minecraft.network.codec.StreamCodec<FriendlyByteBuf, T> codec) {
        FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());
        codec.encode(buffer, payload);
        T decoded = codec.decode(buffer);
        assertEquals(0, buffer.readableBytes());
        return decoded;
    }

    private static RacePayloads.LeaderboardS2C load(Leaderboard board, CourseRegistry.Course course, @Nullable String frame, boolean assisted,
                                                    int[] requests) {
        RacePayloads.LeaderboardS2C client = roundTrip(RaceServer.leaderboardPayload(board, course, VIEWER, false, true),
                RacePayloads.LeaderboardS2C.CODEC);
        BoardPager pager = new BoardPager();
        long now = 0L;
        for (int guard = 0; guard < 100; guard++) {
            int total = client.total(frame, assisted);
            RacePayloads.LeaderboardPageC2S request = pager.next(client, frame, assisted, 1, total, now);
            if (request == null) {
                assertTrue(BoardIndex.of(client, frame, assisted).complete(), "Seiten fehlen, obwohl keine Anfrage mehr kommt");
                return client;
            }
            requests[0]++;
            request = roundTrip(request, RacePayloads.LeaderboardPageC2S.CODEC);
            RacePayloads.LeaderboardPageS2C page = roundTrip(RaceServer.leaderboardPage(board, course, VIEWER, request, true),
                    RacePayloads.LeaderboardPageS2C.CODEC);
            assertTrue(page.rows().size() <= LeaderboardPages.PAGE);
            pager.received(page.courseId());
            client = client.merge(page);
            now += BoardPager.INTERVAL_NANOS;
        }
        throw new AssertionError("Bestenliste lädt nicht fertig");
    }

    private static void assertRanked(BoardIndex index, int total) {
        assertEquals(total, index.total());
        assertEquals(total, index.loaded());
        long previous = Long.MIN_VALUE;
        for (int rank = 1; rank <= total; rank++) {
            RacePayloads.BoardRow row = index.row(rank);
            assertNotNull(row, "Platz " + rank + " fehlt");
            assertTrue(row.nanos() >= previous, "Platz " + rank + " schneller als Platz " + (rank - 1));
            previous = row.nanos();
        }
    }

    @Test
    void scrollingLoadsEveryRankAcrossPageBoundaries() {
        Leaderboard board = mixed();
        CourseRegistry.Course course = new CourseRegistry.Course();
        int[] requests = {0};
        RacePayloads.LeaderboardS2C all = load(board, course, null, false, requests);
        assertEquals(2, requests[0], "Platz 51 bis 100 und 101 bis 120 je eine Anfrage");
        BoardIndex clean = BoardIndex.of(all, null, false);
        assertRanked(clean, 135);
        assertEquals(87, clean.selfRank());
        assertTrue(clean.row(87).self());
        assertEquals(clean.row(50).nanos() + 10L * MS, clean.row(51).nanos());
        List<Leaderboard.Entry> server = board.top(null, Integer.MAX_VALUE);
        for (int rank = 1; rank <= 135; rank++) {
            assertEquals(server.get(rank - 1).nanos(), clean.row(rank).nanos(), "Platz " + rank + " weicht vom Server ab");
        }
        requests[0] = 0;
        RacePayloads.LeaderboardS2C whoop = load(board, course, "whoop_65", false, requests);
        BoardIndex cls = BoardIndex.of(whoop, "whoop_65", false);
        assertRanked(cls, 39 + 15);
        assertTrue(requests[0] >= 1);
        assertEquals(121, cls.row(40).overallRank(), "langsamere zweite Klasse steht in „Alle“ als eigene Zeile hinter allen schnelleren");
        assertEquals("whoop_65", clean.row(121).frame());
        assertEquals(clean.row(1).name(), clean.row(121).name());
        requests[0] = 0;
        RacePayloads.LeaderboardS2C assisted = load(board, course, null, true, requests);
        assertRanked(BoardIndex.of(assisted, null, true), 60);
        assertEquals(1, requests[0]);
        assertRanked(BoardIndex.of(load(board, course, "race_5", true, requests), "race_5", true), 40);
    }

    @Test
    void totalsCountEveryViewAndClass() {
        RacePayloads.LeaderboardS2C payload = RaceServer.leaderboardPayload(mixed(), new CourseRegistry.Course(), VIEWER, false, true);
        assertTrue(payload.totals().paged());
        assertEquals(135, payload.total(null, false));
        assertEquals(60, payload.total(null, true));
        assertEquals(81, payload.total("race_5", false));
        assertEquals(54, payload.total("whoop_65", false));
        assertEquals(40, payload.total("race_5", true));
        assertEquals(20, payload.total("whoop_65", true));
        assertEquals(0, payload.total("freestyle_5", false));
        assertTrue(payload.rows().stream().anyMatch(row -> !row.assisted() && row.frame().equals("whoop_65") && row.classRank() == 1),
                "Klassenrekord fehlt im ersten Paket");
        assertTrue(payload.rows().stream().anyMatch(row -> row.assisted() && row.frame().equals("whoop_65") && row.classRank() == 1));
        RacePayloads.LeaderboardS2C off = RaceServer.leaderboardPayload(mixed(), new CourseRegistry.Course(), VIEWER, false, false);
        assertEquals(0, off.total(null, true));
        assertTrue(off.rows().stream().noneMatch(RacePayloads.BoardRow::assisted));
    }

    @Test
    void changedBoardRejectsOldPages() {
        Leaderboard board = mixed();
        CourseRegistry.Course course = new CourseRegistry.Course();
        RacePayloads.LeaderboardS2C client = RaceServer.leaderboardPayload(board, course, VIEWER, false, true);
        RacePayloads.LeaderboardPageC2S request = RacePayloads.LeaderboardPageC2S.of(course.id, client.revision(), null, false, 50);
        int before = board.revision();
        assertEquals(before, client.revision());
        board.submit(new UUID(9L, 9L), "Neu", "race_5", 1_000L * MS, DAY);
        assertTrue(board.revision() != before, "neue Zeit ändert die Revision");
        RacePayloads.LeaderboardPageS2C fresh = RaceServer.leaderboardPage(board, course, VIEWER, request, true);
        assertSame(client, client.merge(fresh), "Seite einer neueren Revision wird verworfen");
        assertSame(client, client.merge(RaceServer.leaderboardPage(mixed(), course, VIEWER, request, true)), "Seite einer anderen Bestenliste");
        RacePayloads.LeaderboardS2C current = RaceServer.leaderboardPayload(board, course, VIEWER, false, true);
        RacePayloads.LeaderboardS2C merged = current.merge(RaceServer.leaderboardPage(board, course, VIEWER,
                RacePayloads.LeaderboardPageC2S.of(course.id, current.revision(), null, false, 50), true));
        assertTrue(merged.rows().size() > current.rows().size());
        assertEquals(136, merged.total(null, false));
        assertTrue(new Leaderboard().revision() != new Leaderboard().revision(), "jede Bestenliste hat eine eigene Revision");
    }

    @Test
    void pagerWaitsRetriesAndGivesUp() {
        RacePayloads.LeaderboardS2C board = RaceServer.leaderboardPayload(mixed(), new CourseRegistry.Course(), VIEWER, false, true);
        BoardPager pager = new BoardPager();
        assertNull(pager.next(board, null, false, 1, 40, 0L), "erste Seite ist schon da");
        RacePayloads.LeaderboardPageC2S first = pager.next(board, null, false, 40, 60, 0L);
        assertNotNull(first);
        assertEquals(50, first.offset());
        assertTrue(first.all());
        assertTrue(pager.waiting(board.courseId(), null, false));
        assertNull(pager.next(board, null, false, 40, 60, BoardPager.TIMEOUT_NANOS - 1L), "wartet auf die Antwort");
        long now = BoardPager.TIMEOUT_NANOS;
        for (int i = 1; i < BoardPager.MAX_TRIES; i++) {
            assertNotNull(pager.next(board, null, false, 40, 60, now), "Versuch " + (i + 1));
            now += BoardPager.TIMEOUT_NANOS;
        }
        assertNull(pager.next(board, null, false, 40, 60, now), "gibt nach " + BoardPager.MAX_TRIES + " Versuchen auf");
        pager.reset(board.courseId(), board.revision() + 1);
        assertNotNull(pager.next(board, null, false, 40, 60, now), "neue Revision fragt wieder");
        pager.received(board.courseId());
        assertNull(pager.next(board, null, false, 40, 60, now + 1L), "Abstand zwischen zwei Anfragen");
        assertEquals(BoardPager.MAX_TRIES + 1, pager.requests());
        RacePayloads.LeaderboardS2C fixture = new RacePayloads.LeaderboardS2C(4, 1, 6, false, 0, List.of(
                new RacePayloads.BoardRow("Anna", "race_5", 20_000L * MS, 1L, false, 1, 1),
                new RacePayloads.BoardRow("Ben", "race_5", 21_000L * MS, 2L, false, 3, 3)));
        assertFalse(fixture.totals().paged());
        assertEquals(3, fixture.total(null, false));
        assertNull(new BoardPager().next(fixture, null, false, 1, 3, 0L), "feste Testdaten fragen nie beim Server nach");
        assertEquals(2, BoardIndex.of(fixture, null, false).firstMissing(1, 3));
    }

    @Test
    void pagesStayFarBelowThePacketLimit() {
        String code = "€".repeat(4096);
        List<RacePayloads.BoardRow> rows = new ArrayList<>();
        for (int i = 0; i < RacePayloads.LeaderboardS2C.MAX_ROWS; i++) {
            rows.add(new RacePayloads.BoardRow("W".repeat(Leaderboard.MAX_NAME), "race_5", (30_000L + i) * MS, DAY, false, i + 1, i + 1)
                    .withGhost(new UUID(1L, i), code, true));
        }
        List<RacePayloads.BoardRow> page = LeaderboardPages.fit(rows, LeaderboardPages.PAGE, LeaderboardPages.MAX_BYTES);
        assertTrue(page.size() >= 1 && page.size() < LeaderboardPages.PAGE, "Seite mit sehr langen Sharecodes wird gekürzt: " + page.size());
        FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());
        RacePayloads.LeaderboardPageS2C.CODEC.encode(buffer, new RacePayloads.LeaderboardPageS2C(4, 1, page));
        assertTrue(buffer.readableBytes() <= LeaderboardPages.MAX_BYTES, "Seite " + buffer.readableBytes() + " Bytes");
        List<RacePayloads.BoardRow> first = LeaderboardPages.fit(rows, RacePayloads.LeaderboardS2C.MAX_ROWS, LeaderboardPages.MAX_BYTES);
        buffer = new FriendlyByteBuf(Unpooled.buffer());
        RacePayloads.LeaderboardS2C.CODEC.encode(buffer, new RacePayloads.LeaderboardS2C(4, 1, 6, false, 0, first));
        assertTrue(buffer.readableBytes() < 1_048_576 / 2, "erstes Paket " + buffer.readableBytes() + " Bytes");
        assertEquals(LeaderboardPages.PAGE, LeaderboardPages.fit(rows.stream().map(row -> row.withGhost(row.player(), "", false)).toList(),
                LeaderboardPages.PAGE, LeaderboardPages.MAX_BYTES).size());
        RacePayloads.BoardTotals huge = new RacePayloads.BoardTotals(7, Integer.MAX_VALUE, Integer.MAX_VALUE, List.of(
                new RacePayloads.ClassTotal("race_5", Integer.MAX_VALUE, -5)));
        assertEquals(RacePayloads.BoardTotals.MAX_TIMES, huge.clean());
        assertEquals(RacePayloads.BoardTotals.MAX_TIMES, huge.total("race_5", false));
        assertEquals(0, huge.total("race_5", true));
        RacePayloads.LeaderboardS2C forged = new RacePayloads.LeaderboardS2C(4, 1, 6, false, 0, page, 0, 0L, 0, 0, 0, huge);
        BoardIndex index = BoardIndex.of(forged, null, false);
        assertEquals(RacePayloads.BoardTotals.MAX_TIMES, index.total());
        assertEquals(page.size(), index.loaded());
        assertEquals(page.size() + 1, index.firstMissing(1, Integer.MAX_VALUE));
    }

    @Test
    void layoutPlacesHeadersRowsAndCollapsedSections() {
        BoardLayout open = new BoardLayout(120, 60, false, false, ROW, GAP);
        assertEquals(0, open.headerY(false));
        assertEquals(ROW, open.rowY(false, 1));
        assertEquals(ROW + 119 * ROW, open.rowY(false, 120));
        assertEquals(ROW + 120 * ROW + GAP, open.headerY(true));
        assertEquals(open.headerY(true) + ROW, open.rowY(true, 1));
        assertEquals(open.headerY(true) + ROW + 60 * ROW, open.height());
        assertEquals(new BoardLayout.Hit(BoardLayout.Kind.HEADER, false, 0), open.hit(3));
        assertEquals(new BoardLayout.Hit(BoardLayout.Kind.ROW, false, 51), open.hit(open.rowY(false, 51) + 5));
        assertEquals(new BoardLayout.Hit(BoardLayout.Kind.HEADER, true, 0), open.hit(open.headerY(true) + 1));
        assertEquals(new BoardLayout.Hit(BoardLayout.Kind.ROW, true, 60), open.hit(open.height() - 1));
        assertEquals(BoardLayout.Hit.MISS, open.hit(open.headerY(true) - 1));
        assertArrayEquals(new int[]{50, 59}, open.visible(false, open.rowY(false, 50), open.rowY(false, 60)));
        assertEquals(BoardLayout.NONE, open.sticky(0));
        assertEquals(BoardLayout.CLEAN, open.sticky(open.rowY(false, 30)));
        assertEquals(BoardLayout.ASSISTED, open.sticky(open.rowY(true, 10)));
        BoardLayout records = new BoardLayout(120, 60, true, false, ROW, GAP);
        assertEquals(ROW + GAP, records.headerY(true));
        assertEquals(Integer.MIN_VALUE, records.rowY(false, 1));
        assertEquals(0, records.visible(false, 0, 10_000).length);
        assertEquals(ROW + GAP + ROW + 60 * ROW, records.height());
        BoardLayout both = new BoardLayout(120, 60, true, true, ROW, GAP);
        assertEquals(ROW + GAP + ROW, both.height());
        BoardLayout assistedOnly = new BoardLayout(0, 3, false, false, ROW, GAP);
        assertTrue(assistedOnly.message());
        assertEquals(new BoardLayout.Hit(BoardLayout.Kind.MESSAGE, false, 0), assistedOnly.hit(ROW + 2));
        BoardLayout plain = new BoardLayout(5, 0, false, false, ROW, GAP);
        assertFalse(plain.has(true));
        assertEquals(6 * ROW, plain.height());
    }

    @Test
    void collapsedSectionsStayCollapsedForOtherCoursesClassesAndNewWindows() {
        assertFalse(BoardSections.collapsed(false));
        assertTrue(BoardSections.toggle(false));
        assertTrue(BoardSections.toggle(true));
        assertFalse(BoardSections.toggle(true));
        for (int[] totals : new int[][]{{120, 60}, {54, 20}, {3, 0}}) {
            BoardLayout layout = new BoardLayout(totals[0], totals[1], BoardSections.collapsed(false), BoardSections.collapsed(true), ROW, GAP);
            assertFalse(layout.open(false), "Bestzeiten bleiben eingeklappt");
            assertTrue(layout.open(true));
        }
        BoardSections.set(false, false);
        assertFalse(BoardSections.collapsed(false));
    }

    @Test
    void commandPagesReachEveryTime() {
        List<Integer> ranking = IntStream.rangeClosed(1, 120).boxed().toList();
        assertEquals(12, RaceCommands.pages(120));
        assertEquals(1, RaceCommands.pages(0));
        assertEquals(13, RaceCommands.pages(121));
        List<Integer> seen = new ArrayList<>();
        for (int page = 1; page <= RaceCommands.pages(ranking.size()); page++) {
            seen.addAll(RaceCommands.page(ranking, page));
        }
        assertEquals(ranking, seen);
        assertEquals(List.of(111, 112, 113, 114, 115, 116, 117, 118, 119, 120), RaceCommands.page(ranking, 99));
        assertEquals(List.of(1, 2, 3, 4, 5, 6, 7, 8, 9, 10), RaceCommands.page(ranking, 0));
        CourseRegistry.Course course = new CourseRegistry.Course();
        assertTrue(RaceCommands.pageCommand(course, 3).startsWith("/propwash race leaderboard page 3 "));
    }
}
