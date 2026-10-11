package de.lardtom.propwash.server;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StreamBudgetTest {
    @Test
    void costCountsTheWholeViewTicketSquare() {
        assertEquals(81, StreamBudget.cost(2));
        assertEquals(625, StreamBudget.cost(10));
        assertEquals(12, StreamBudget.viewTicketRadius(10));
    }

    @Test
    void singleAnchorGetsWhatItWantsWhenTheBudgetAllows() {
        assertArrayEquals(new int[]{10}, StreamBudget.allocate(new int[]{10}, 4096));
    }

    @Test
    void singleAnchorIsCappedByTheBudget() {
        int[] result = StreamBudget.allocate(new int[]{32}, 1000);
        assertEquals(1, result.length);
        assertTrue(StreamBudget.cost(result[0]) <= 1000);
        assertTrue(StreamBudget.cost(result[0] + 1) > 1000);
    }

    @Test
    void budgetIsSharedFairly() {
        int[] result = StreamBudget.allocate(new int[]{10, 10, 10, 10}, 2000);
        assertTrue(StreamBudget.totalCost(result) <= 2000);
        int min = Integer.MAX_VALUE;
        int max = Integer.MIN_VALUE;
        for (int r : result) {
            min = Math.min(min, r);
            max = Math.max(max, r);
        }
        assertTrue(max - min <= 1, "radii differ by more than one: " + java.util.Arrays.toString(result));
    }

    @Test
    void smallWishesLeaveRoomForOthers() {
        int[] result = StreamBudget.allocate(new int[]{3, 12}, 1200);
        assertEquals(3, result[0]);
        assertTrue(result[1] > 3);
        assertTrue(StreamBudget.totalCost(result) <= 1200);
    }

    @Test
    void everyAnchorKeepsTheMinimumEvenOverBudget() {
        int[] result = StreamBudget.allocate(new int[]{8, 8, 8}, 100);
        assertArrayEquals(new int[]{2, 2, 2}, result);
    }

    @Test
    void wishesAreClampedToTheValidRange() {
        assertArrayEquals(new int[]{2, 32}, StreamBudget.allocate(new int[]{0, 99}, Integer.MAX_VALUE));
    }

    @Test
    void emptyInputGivesEmptyResult() {
        assertEquals(0, StreamBudget.allocate(new int[0], 4096).length);
    }

    @Test
    void wantedViewDistanceRespectsPlayerServerAndConfig() {
        assertEquals(8, StreamBudget.wantedViewDistance(8, 12, 10));
        assertEquals(10, StreamBudget.wantedViewDistance(16, 12, 10));
        assertEquals(6, StreamBudget.wantedViewDistance(16, 6, 10));
        assertEquals(2, StreamBudget.wantedViewDistance(0, 12, 10));
        assertEquals(2, StreamBudget.wantedViewDistance(12, 12, 1));
    }
}
