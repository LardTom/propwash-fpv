package de.lardtom.propwash.rescue;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HomePointRulesTest {
    private static final String OVERWORLD = "minecraft:overworld";
    private static final String NETHER = "minecraft:the_nether";
    private static final int MIN_Y = -64;
    private static final int MAX_Y = 320;
    private static final HomePoint ARMED = new HomePoint(12.5, 64.0, -3.25, OVERWORLD, 1200L, HomePoint.Source.ARM);

    @Test
    void acceptsPointsInsideTheWorld() {
        assertNull(HomePointRules.check(12.5, 64.0, -3.25, OVERWORLD, OVERWORLD, MIN_Y, MAX_Y, true));
        assertNull(HomePointRules.check(0.0, MIN_Y - 64.0, 0.0, OVERWORLD, OVERWORLD, MIN_Y, MAX_Y, true));
        assertNull(HomePointRules.check(0.0, MAX_Y + 64.0, 0.0, OVERWORLD, OVERWORLD, MIN_Y, MAX_Y, true));
    }

    @Test
    void rejectsBrokenOrForeignPoints() {
        assertEquals(HomePointRules.Problem.INVALID, HomePointRules.check(Double.NaN, 64.0, 0.0, OVERWORLD, OVERWORLD, MIN_Y, MAX_Y, true));
        assertEquals(HomePointRules.Problem.INVALID,
                HomePointRules.check(0.0, Double.POSITIVE_INFINITY, 0.0, OVERWORLD, OVERWORLD, MIN_Y, MAX_Y, true));
        assertEquals(HomePointRules.Problem.DIMENSION, HomePointRules.check(0.0, 64.0, 0.0, NETHER, OVERWORLD, MIN_Y, MAX_Y, true));
        assertEquals(HomePointRules.Problem.HEIGHT, HomePointRules.check(0.0, MIN_Y - 64.5, 0.0, OVERWORLD, OVERWORLD, MIN_Y, MAX_Y, true));
        assertEquals(HomePointRules.Problem.HEIGHT, HomePointRules.check(0.0, MAX_Y + 64.5, 0.0, OVERWORLD, OVERWORLD, MIN_Y, MAX_Y, true));
        assertEquals(HomePointRules.Problem.BORDER, HomePointRules.check(0.0, 64.0, 0.0, OVERWORLD, OVERWORLD, MIN_Y, MAX_Y, false));
    }

    @Test
    void commandEditStampsTimeAndSource() {
        HomePoint typed = new HomePoint(40.0, 70.0, 8.0, "", HomePoint.UNKNOWN_TIME, HomePoint.Source.COMMAND);
        HomePointRules.Edit edit = HomePointRules.edit(ARMED, typed, OVERWORLD, MIN_Y, MAX_Y, true, 5000L);
        assertNull(edit.problem());
        assertEquals(new HomePoint(40.0, 70.0, 8.0, OVERWORLD, 5000L, HomePoint.Source.COMMAND), edit.home());
    }

    @Test
    void mergedOldStampIsReplacedWhenThePointMoves() {
        HomePoint merged = new HomePoint(40.0, 64.0, -3.25, OVERWORLD, 1200L, HomePoint.Source.ARM);
        HomePointRules.Edit edit = HomePointRules.edit(ARMED, merged, OVERWORLD, MIN_Y, MAX_Y, true, 7000L);
        assertEquals(new HomePoint(40.0, 64.0, -3.25, OVERWORLD, 7000L, HomePoint.Source.COMMAND), edit.home());
    }

    @Test
    void unchangedPlaceKeepsTheStoredStamp() {
        HomePoint retimed = new HomePoint(12.5, 64.0, -3.25, OVERWORLD, 1L, HomePoint.Source.COMMAND);
        HomePointRules.Edit edit = HomePointRules.edit(ARMED, retimed, OVERWORLD, MIN_Y, MAX_Y, true, 9000L);
        assertSame(ARMED, edit.home());
        assertNull(edit.problem());
    }

    @Test
    void unchangedForeignPointStaysEditable() {
        HomePoint nether = new HomePoint(1.0, 70.0, 1.0, NETHER, 300L, HomePoint.Source.MANUAL);
        HomePointRules.Edit edit = HomePointRules.edit(nether, nether, OVERWORLD, MIN_Y, MAX_Y, false, 9000L);
        assertSame(nether, edit.home());
    }

    @Test
    void mergedOldForeignDimensionMovesPointToDrone() {
        HomePoint nether = new HomePoint(1.0, 70.0, 1.0, NETHER, 300L, HomePoint.Source.MANUAL);
        HomePoint merged = new HomePoint(20.5, 80.0, -4.5, NETHER, 300L, HomePoint.Source.MANUAL);
        HomePointRules.Edit edit = HomePointRules.edit(nether, merged, OVERWORLD, MIN_Y, MAX_Y, true, 9000L);
        assertNull(edit.problem());
        assertEquals(OVERWORLD, edit.home().dimension());
        assertEquals(20.5, edit.home().x());
        assertEquals(HomePoint.Source.COMMAND, edit.home().source());
        HomePoint end = new HomePoint(20.5, 80.0, -4.5, "minecraft:the_end", 300L, HomePoint.Source.MANUAL);
        assertEquals(HomePointRules.Problem.DIMENSION, HomePointRules.edit(nether, end, OVERWORLD, MIN_Y, MAX_Y, true, 9000L).problem());
    }

    @Test
    void invalidEditsLeaveNothing() {
        HomePoint other = new HomePoint(40.0, 64.0, 0.0, NETHER, 0L, HomePoint.Source.COMMAND);
        HomePointRules.Edit edit = HomePointRules.edit(ARMED, other, OVERWORLD, MIN_Y, MAX_Y, true, 9000L);
        assertEquals(HomePointRules.Problem.DIMENSION, edit.problem());
        assertNull(edit.home());
        HomePoint outside = new HomePoint(9.0e6, 64.0, 0.0, "", HomePoint.UNKNOWN_TIME, HomePoint.Source.COMMAND);
        assertEquals(HomePointRules.Problem.BORDER, HomePointRules.edit(null, outside, OVERWORLD, MIN_Y, MAX_Y, false, 1L).problem());
    }

    @Test
    void firstPointFromCommand() {
        HomePoint typed = new HomePoint(0.5, 100.0, 0.5, OVERWORLD, HomePoint.UNKNOWN_TIME, HomePoint.Source.ARM);
        HomePointRules.Edit edit = HomePointRules.edit(null, typed, OVERWORLD, MIN_Y, MAX_Y, true, 42L);
        assertEquals(HomePoint.Source.COMMAND, edit.home().source());
        assertEquals(42L, edit.home().gameTime());
    }

    @Test
    void sourcesHaveStableIds() {
        assertEquals("arm", HomePoint.Source.ARM.id());
        assertEquals("manual", HomePoint.Source.MANUAL.id());
        assertEquals("pilot", HomePoint.Source.PILOT.id());
        assertEquals("command", HomePoint.Source.COMMAND.id());
        for (HomePoint.Source source : HomePoint.Source.values()) {
            assertSame(source, HomePoint.Source.byId(source.id()));
            assertSame(source, HomePoint.Source.byOrdinal(source.ordinal()));
        }
        assertNull(HomePoint.Source.byId("ARM"));
        assertNull(HomePoint.Source.byOrdinal(-1));
        assertNull(HomePoint.Source.byOrdinal(HomePoint.Source.values().length));
        assertTrue(HomePoint.Source.PILOT.fromPilot());
        assertTrue(!HomePoint.Source.COMMAND.fromPilot());
    }

    @Test
    void resolveFillsOnlyMissingParts() {
        HomePoint raw = new HomePoint(1.0, 2.0, 3.0, "", HomePoint.UNKNOWN_TIME, HomePoint.Source.COMMAND);
        assertEquals(new HomePoint(1.0, 2.0, 3.0, OVERWORLD, 77L, HomePoint.Source.COMMAND), raw.resolved(OVERWORLD, 77L));
        assertSame(ARMED.dimension(), ARMED.resolved(NETHER, 99L).dimension());
        assertEquals(1200L, ARMED.resolved(NETHER, 99L).gameTime());
    }
}
