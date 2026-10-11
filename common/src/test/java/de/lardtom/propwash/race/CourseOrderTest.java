package de.lardtom.propwash.race;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CourseOrderTest {
    private static final List<CourseElement> KINDS = List.of(CourseElement.GATE, CourseElement.FLAG, CourseElement.GATE, CourseElement.POLE,
            CourseElement.FREEFORM);

    private static List<String> letters() {
        return new ArrayList<>(List.of("a", "b", "c", "d", "e"));
    }

    @Test
    void moveShiftsTheOthers() {
        List<String> list = letters();
        assertTrue(CourseOrder.move(list, 3, 1));
        assertEquals(List.of("a", "d", "b", "c", "e"), list);
        assertTrue(CourseOrder.move(list, 0, 4));
        assertEquals(List.of("d", "b", "c", "e", "a"), list);
    }

    @Test
    void moveRejectsNonsense() {
        List<String> list = letters();
        assertFalse(CourseOrder.move(list, 2, 2));
        assertFalse(CourseOrder.move(list, -1, 2));
        assertFalse(CourseOrder.move(list, 1, 5));
        assertEquals(letters(), list);
    }

    @Test
    void rotateKeepsTheOrderAndMovesTheStart() {
        List<String> list = letters();
        assertTrue(CourseOrder.rotate(list, 2));
        assertEquals(List.of("c", "d", "e", "a", "b"), list);
        assertFalse(CourseOrder.rotate(list, 0));
        assertFalse(CourseOrder.rotate(list, 5));
        assertEquals(List.of("c", "d", "e", "a", "b"), list);
    }

    @Test
    void theStartMustStayAGate() {
        assertTrue(CourseOrder.canMove(KINDS, 2, 0));
        assertTrue(CourseOrder.canMove(KINDS, 4, 0));
        assertFalse(CourseOrder.canMove(KINDS, 1, 0));
        assertFalse(CourseOrder.canMove(KINDS, 3, 0));
        assertFalse(CourseOrder.canMove(KINDS, 0, 1));
        assertFalse(CourseOrder.canMove(KINDS, 0, 4));
        assertTrue(CourseOrder.canMove(KINDS, 1, 3));
        assertTrue(CourseOrder.canMove(List.of(CourseElement.GATE, CourseElement.GATE, CourseElement.FLAG), 0, 2));
    }

    @Test
    void onlyGatesBecomeTheStart() {
        assertFalse(CourseOrder.canStart(KINDS, 0));
        assertFalse(CourseOrder.canStart(KINDS, 1));
        assertTrue(CourseOrder.canStart(KINDS, 2));
        assertFalse(CourseOrder.canStart(KINDS, 3));
        assertTrue(CourseOrder.canStart(KINDS, 4));
        assertFalse(CourseOrder.canStart(KINDS, 9));
    }

    @Test
    void removingTheStartNeedsAGateBehindIt() {
        assertFalse(CourseOrder.canRemove(KINDS, 0));
        assertTrue(CourseOrder.canRemove(KINDS, 1));
        assertTrue(CourseOrder.canRemove(KINDS, 4));
        assertFalse(CourseOrder.canRemove(KINDS, 5));
        assertTrue(CourseOrder.canRemove(List.of(CourseElement.GATE, CourseElement.GATE), 0));
        assertTrue(CourseOrder.canRemove(List.of(CourseElement.GATE), 0));
    }
}
