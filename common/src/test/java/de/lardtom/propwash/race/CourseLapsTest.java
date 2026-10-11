package de.lardtom.propwash.race;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CourseLapsTest {
    @Test
    void courseLapsWinOverThePersonalDefault() {
        assertEquals(5, CourseLaps.resolve(5, 3));
        assertEquals(CourseLaps.ENDLESS, CourseLaps.resolve(CourseLaps.ENDLESS, 3));
        assertEquals(1, CourseLaps.resolve(1, 0));
    }

    @Test
    void coursesWithoutOwnLapsFollowThePersonalDefault() {
        assertEquals(3, CourseLaps.resolve(CourseLaps.UNSET, 3));
        assertEquals(CourseLaps.ENDLESS, CourseLaps.resolve(CourseLaps.UNSET, 0));
        assertEquals(CourseLaps.MAX, CourseLaps.resolve(CourseLaps.UNSET, 500));
        assertEquals(CourseLaps.ENDLESS, CourseLaps.resolve(CourseLaps.UNSET, -7));
    }

    @Test
    void storedValuesStayInRange() {
        assertEquals(CourseLaps.UNSET, CourseLaps.stored(-1));
        assertEquals(CourseLaps.UNSET, CourseLaps.stored(-40));
        assertEquals(0, CourseLaps.stored(0));
        assertEquals(12, CourseLaps.stored(12));
        assertEquals(CourseLaps.MAX, CourseLaps.stored(1000));
        assertFalse(CourseLaps.set(CourseLaps.UNSET));
        assertTrue(CourseLaps.set(CourseLaps.ENDLESS));
    }

    @Test
    void stepsStopAtTheEnds() {
        assertEquals(4, CourseLaps.step(3, 1));
        assertEquals(2, CourseLaps.step(3, -1));
        assertEquals(CourseLaps.ENDLESS, CourseLaps.step(0, -1));
        assertEquals(CourseLaps.MAX, CourseLaps.step(CourseLaps.MAX, 1));
        assertEquals(1, CourseLaps.step(CourseLaps.UNSET, 1));
    }

    @Test
    void aRaceMayOnlyFinishAfterTheLapsOfTheCourse() {
        assertTrue(CourseLaps.finishAllowed(3, 3));
        assertTrue(CourseLaps.finishAllowed(3, 4));
        assertFalse(CourseLaps.finishAllowed(3, 2));
        assertFalse(CourseLaps.finishAllowed(5, 1));
        assertTrue(CourseLaps.finishAllowed(CourseLaps.UNSET, 1));
        assertTrue(CourseLaps.finishAllowed(CourseLaps.ENDLESS, 7));
    }
}
