package de.lardtom.propwash.bando;

import de.lardtom.propwash.bando.plan.LeafDistance;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.function.LongUnaryOperator;

import static org.junit.jupiter.api.Assertions.assertEquals;

class LeafDistanceTest {
    private static final LongUnaryOperator[] LINE = {at -> at - 1, at -> at + 1};

    private static Set<Long> range(long from, long to) {
        Set<Long> leaves = new HashSet<>();
        for (long at = from; at <= to; at++) {
            leaves.add(at);
        }
        return leaves;
    }

    @Test
    void leavesCountStepsToTheNearestLog() {
        Map<Long, Integer> distance = LeafDistance.settle(range(1, 9), LINE, at -> at == 0 ? 0 : LeafDistance.DECAY);
        for (long at = 1; at <= 9; at++) {
            assertEquals(Math.min(LeafDistance.DECAY, (int) at), distance.get(at), "Blatt " + at);
        }
    }

    @Test
    void leavesCutOffFromTheirLogDecay() {
        Map<Long, Integer> distance = LeafDistance.settle(range(5, 8), LINE, at -> at == 0 ? 0 : LeafDistance.DECAY);
        for (long at = 5; at <= 8; at++) {
            assertEquals(LeafDistance.DECAY, distance.get(at), "Blatt " + at + " hängt an nichts mehr");
        }
    }

    @Test
    void leavesOutsideTheAreaKeepTheirDistance() {
        Map<Long, Integer> distance = LeafDistance.settle(range(3, 6), LINE, at -> at == 2 ? 2 : at == 7 ? 1 : LeafDistance.DECAY);
        assertEquals(3, distance.get(3L));
        assertEquals(4, distance.get(4L));
        assertEquals(3, distance.get(5L));
        assertEquals(2, distance.get(6L));
    }
}
