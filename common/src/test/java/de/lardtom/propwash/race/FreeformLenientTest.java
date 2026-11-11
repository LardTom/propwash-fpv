package de.lardtom.propwash.race;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FreeformLenientTest {
    private static final int LIMIT = 20;

    private static FreeformGateTest.Grid goalPost() {
        return new FreeformGateTest.Grid().fill(-10, -1, 0, 10, -1, 0).fill(0, 0, 0, 0, 5, 0).fill(4, 0, 0, 4, 5, 0);
    }

    private static FreeformFill.Result lenient(FreeformFill.Probe probe, int x, int y, int z) {
        return FreeformFill.lenient(probe, FreeformRegion.AXIS_Z, x, y, z, LIMIT);
    }

    private static void assertBox(FreeformFill.Result result, int minA, int minB, int width, int height) {
        assertTrue(result.ok(), result.status().toString());
        FreeformRegion region = result.region();
        assertNotNull(region);
        assertEquals(minA, result.minA(), "minA");
        assertEquals(minB, result.minB(), "minB");
        assertEquals(width, region.width(), "Breite");
        assertEquals(height, region.height(), "Höhe");
    }

    @Test
    void goalPostWithoutTopBarSpansUpToThePosts() {
        FreeformGateTest.Grid grid = goalPost();
        assertEquals(FreeformFill.Status.OPEN, FreeformFill.fill(grid, FreeformRegion.AXIS_Z, 2, 1, 0, LIMIT).status());
        FreeformFill.Result result = lenient(grid, 2, 1, 0);
        assertBox(result, 1, 0, 3, 6);
        assertEquals(18, result.region().cells());
    }

    @Test
    void closedOpeningsStayExactlyAsBefore() {
        FreeformGateTest.Grid grid = goalPost().fill(0, 6, 0, 4, 6, 0);
        FreeformFill.Result strict = FreeformFill.fill(grid, FreeformRegion.AXIS_Z, 2, 1, 0, LIMIT);
        assertEquals(strict, lenient(grid, 2, 1, 0));
    }

    @Test
    void hangingArchWithoutFloorSpansDownToThePosts() {
        FreeformGateTest.Grid grid = new FreeformGateTest.Grid().fill(-2, 8, 0, 6, 8, 0).fill(0, 4, 0, 0, 7, 0).fill(4, 3, 0, 4, 7, 0);
        FreeformFill.Result result = lenient(grid, 2, 6, 0);
        assertBox(result, 1, 3, 3, 5);
    }

    @Test
    void lShapeTakesTheFloorAndTheOnePost() {
        FreeformGateTest.Grid grid = new FreeformGateTest.Grid().fill(0, -1, 0, 6, -1, 0).fill(0, 0, 0, 0, 4, 0);
        FreeformFill.Result result = lenient(grid, 2, 1, 0);
        assertBox(result, 1, 0, 6, 5);
    }

    @Test
    void twoPostsWithoutBarsUseThePostHeight() {
        FreeformGateTest.Grid grid = new FreeformGateTest.Grid().fill(0, 2, 0, 0, 7, 0).fill(5, 3, 0, 5, 8, 0);
        FreeformFill.Result result = lenient(grid, 2, 4, 0);
        assertBox(result, 1, 2, 4, 7);
    }

    @Test
    void blocksInsideTheRectangleStayEdges() {
        FreeformGateTest.Grid grid = goalPost().fill(2, 3, 0, 2, 3, 0);
        FreeformFill.Result result = lenient(grid, 1, 1, 0);
        assertTrue(result.ok());
        assertEquals(17, result.region().cells());
        assertFalse(result.region().contains(2 - result.minA(), 3 - result.minB()));
    }

    @Test
    void singlePostOrNothingStaysOpen() {
        FreeformGateTest.Grid post = new FreeformGateTest.Grid().fill(0, 0, 0, 0, 5, 0);
        assertEquals(FreeformFill.Status.OPEN, lenient(post, 2, 2, 0).status());
        FreeformGateTest.Grid floor = new FreeformGateTest.Grid().fill(-5, -1, 0, 5, -1, 0);
        assertEquals(FreeformFill.Status.OPEN, lenient(floor, 0, 0, 0).status(), "nur Boden ergibt kein Rechteck");
        assertEquals(FreeformFill.Status.OPEN, lenient(new FreeformGateTest.Grid(), 0, 0, 0).status());
    }

    @Test
    void rectangleLargerThanTheRuleIsTooBig() {
        FreeformGateTest.Grid grid = new FreeformGateTest.Grid().fill(-1, -1, 0, 30, -1, 0).fill(-1, 0, 0, -1, 4, 0).fill(26, 0, 0, 26, 4, 0);
        FreeformFill.Result result = lenient(grid, 2, 1, 0);
        assertEquals(FreeformFill.Status.TOO_BIG, result.status());
        assertTrue(result.width() > LIMIT, "Breite " + result.width());
    }

    @Test
    void unloadedPostsAreNotGuessed() {
        FreeformGateTest.Grid grid = goalPost();
        grid.unloaded.add(FreeformGateTest.Grid.key(4, 1, 0));
        assertEquals(FreeformFill.Status.UNLOADED, lenient(grid, 2, 1, 0).status());
    }

    @Test
    void ignoredGapsHideOnlyTheEdgeWarning() {
        FreeformFill.Integrity integrity = new FreeformFill.Integrity(2, 5, 1);
        FreeformFill.Integrity ignored = integrity.ignoringGaps();
        assertEquals(2, ignored.blockedCells());
        assertEquals(0, ignored.openEdges());
        assertFalse(ignored.intact());
        assertTrue(new FreeformFill.Integrity(0, 7, 0).ignoringGaps().intact());
    }
}
