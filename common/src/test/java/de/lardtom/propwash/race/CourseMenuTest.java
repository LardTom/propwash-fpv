package de.lardtom.propwash.race;

import io.netty.buffer.Unpooled;
import net.minecraft.network.FriendlyByteBuf;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CourseMenuTest {
    private static final long MS = 1_000_000L;
    private static final Set<CourseMenu.Row> CHANGES = EnumSet.of(CourseMenu.Row.NAME, CourseMenu.Row.MODE, CourseMenu.Row.LAPS, CourseMenu.Row.RACE_LINE, CourseMenu.Row.BIND,
            CourseMenu.Row.CREATIVE_FC, CourseMenu.Row.RESET, CourseMenu.Row.DELETE);

    @Test
    void everyCourseOptionSitsInOneGroupAndTheGroupsFollowEachOther() {
        List<CourseMenu.Row> laps = CourseMenu.rows(false);
        assertEquals(List.of(CourseMenu.Row.NAME, CourseMenu.Row.KEY, CourseMenu.Row.MODE, CourseMenu.Row.LAPS, CourseMenu.Row.GATES,
                CourseMenu.Row.RACE_LINE, CourseMenu.Row.BIND, CourseMenu.Row.OWNER, CourseMenu.Row.RIGHTS, CourseMenu.Row.BOARD, CourseMenu.Row.GHOSTS,
                CourseMenu.Row.CREATIVE_FC, CourseMenu.Row.RESET, CourseMenu.Row.DELETE), laps);
        List<CourseMenu.Row> point = CourseMenu.rows(true);
        assertTrue(point.contains(CourseMenu.Row.FINISH));
        assertFalse(point.contains(CourseMenu.Row.LAPS));
        assertEquals(point.indexOf(CourseMenu.Row.MODE) + 1, point.indexOf(CourseMenu.Row.FINISH));
        for (boolean pointToPoint : new boolean[]{false, true}) {
            List<CourseMenu.Section> order = new ArrayList<>();
            for (CourseMenu.Row row : CourseMenu.rows(pointToPoint)) {
                if (order.isEmpty() || order.getLast() != row.section()) {
                    order.add(row.section());
                }
            }
            assertEquals(List.of(CourseMenu.Section.values()), order, "Gruppen verstreut bei pointToPoint=" + pointToPoint);
            int total = 0;
            for (CourseMenu.Section section : CourseMenu.Section.values()) {
                List<CourseMenu.Row> rows = CourseMenu.rows(section, pointToPoint);
                assertFalse(rows.isEmpty(), "leere Gruppe " + section);
                total += rows.size();
            }
            assertEquals(CourseMenu.rows(pointToPoint).size(), total);
        }
    }

    @Test
    void someoneElsesCourseIsReadOnly() {
        for (CourseAccess denied : List.of(CourseAccess.NOT_OWNER, CourseAccess.OWNERLESS, CourseAccess.NO_BUILD, CourseAccess.OTHER_DIMENSION)) {
            CourseMenu.Rights rights = CourseMenu.Rights.of(denied, denied, false, false);
            assertTrue(rights.readOnly(), denied.name());
            assertEquals(CourseMenu.Role.VIEWER, rights.role());
            for (CourseMenu.Row row : CourseMenu.Row.values()) {
                assertEquals(!CHANGES.contains(row), CourseMenu.active(row, rights), row + " bei " + denied);
            }
        }
        CourseMenu.Rights guest = CourseMenu.Rights.of(CourseAccess.edit(true, true, false, false, false),
                CourseAccess.delete(true, true, false, false, false), false, false);
        assertEquals(CourseMenu.Rights.VIEW, guest);
    }

    @Test
    void ownerChangesTheCourseButOnlyOperatorsResetTheLeaderboard() {
        CourseMenu.Rights owner = CourseMenu.Rights.of(CourseAccess.ALLOWED, CourseAccess.ALLOWED, true, false);
        assertEquals(CourseMenu.Role.OWNER, owner.role());
        assertFalse(owner.readOnly());
        for (CourseMenu.Row row : CourseMenu.Row.values()) {
            assertEquals(row != CourseMenu.Row.RESET && row != CourseMenu.Row.CREATIVE_FC, CourseMenu.active(row, owner), row.name());
        }
        CourseMenu.Rights found = CourseMenu.Rights.of(CourseAccess.ALLOWED, CourseAccess.WORLD_COURSE, true, false);
        assertFalse(CourseMenu.active(CourseMenu.Row.DELETE, found));
        assertTrue(CourseMenu.active(CourseMenu.Row.NAME, found));
    }

    @Test
    void operatorMayChangeEverything() {
        CourseMenu.Rights operator = CourseMenu.Rights.of(CourseAccess.edit(true, true, false, false, true),
                CourseAccess.delete(true, true, false, true, true), false, true);
        assertEquals(CourseMenu.Role.OPERATOR, operator.role());
        for (CourseMenu.Row row : CourseMenu.Row.values()) {
            assertTrue(CourseMenu.active(row, operator), row.name());
        }
    }

    @Test
    void resetUsesTheExistingCommand() {
        assertEquals("propwash race reset 7", CourseMenu.resetCommand(7));
    }

    @Test
    void boardCountsEveryTimeOfTheCourseNotOnlyTheRowsOfThePacket() {
        Leaderboard board = new Leaderboard();
        for (int i = 0; i < 15; i++) {
            board.submit(new UUID(31L, i), "Pilot" + i, "race_5", (20_000L + i * 100L) * MS, 1_000L + i);
        }
        for (int i = 0; i < 3; i++) {
            board.submit(new UUID(32L, i), "Whoop" + i, "whoop_65", (25_000L + i * 100L) * MS, 2_000L + i);
        }
        for (int i = 0; i < 4; i++) {
            board.submitAssisted(new UUID(33L, i), "Hold" + i, "race_5", (18_000L + i * 100L) * MS, 3_000L + i, LapAssist.HOLD_MODE.bit());
        }
        CourseRegistry.Course course = new CourseRegistry.Course();
        UUID viewer = new UUID(31L, 14L);
        RacePayloads.LeaderboardS2C plain = roundTrip(RaceServer.leaderboardPayload(board, course, viewer, false, false));
        assertEquals(15 + 3, plain.rows().size());
        assertEquals(15 + 3, plain.total(null, false));
        assertEquals(18, CourseMenu.times(plain));
        RacePayloads.LeaderboardS2C assisted = roundTrip(RaceServer.leaderboardPayload(board, course, viewer, false, true));
        assertEquals(22, CourseMenu.times(assisted));
        Leaderboard few = new Leaderboard();
        few.submit(new UUID(34L, 1L), "Solo", "race_5", 20_000L * MS, 1_000L);
        assertEquals(1, CourseMenu.times(roundTrip(RaceServer.leaderboardPayload(few, course, viewer, false, true))));
        assertEquals(0, CourseMenu.times(roundTrip(RaceServer.leaderboardPayload(new Leaderboard(), course, viewer, false, true))));
        assertEquals(2, CourseMenu.times(new RacePayloads.LeaderboardS2C(5, 1, 3, false, 0, List.of(
                new RacePayloads.BoardRow("Anna", "race_5", 20_000L * MS, 1L, false, 1, 1),
                new RacePayloads.BoardRow("Ben", "race_5", 21_000L * MS, 2L, false, 2, 2)))));
    }

    private static RacePayloads.LeaderboardS2C roundTrip(RacePayloads.LeaderboardS2C payload) {
        FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());
        RacePayloads.LeaderboardS2C.CODEC.encode(buffer, payload);
        RacePayloads.LeaderboardS2C decoded = RacePayloads.LeaderboardS2C.CODEC.decode(buffer);
        assertEquals(0, buffer.readableBytes());
        assertEquals(payload, decoded);
        return decoded;
    }
}
