package de.lardtom.propwash.race;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CourseAccessTest {
    @Test
    void ownerAndOperatorEditFromAnywhereInTheDimension() {
        assertEquals(CourseAccess.ALLOWED, CourseAccess.edit(true, true, false, true, false, false));
        assertEquals(CourseAccess.ALLOWED, CourseAccess.edit(true, true, false, false, true, false));
        assertEquals(CourseAccess.ALLOWED, CourseAccess.edit(true, true, true, false, true, false));
    }

    @Test
    void foreignCourseStaysClosedEvenNextToIt() {
        assertEquals(CourseAccess.NOT_OWNER, CourseAccess.edit(true, true, false, false, false, true));
        assertEquals(CourseAccess.NOT_OWNER, CourseAccess.edit(true, true, false, false, false, false));
    }

    @Test
    void courseWithoutCreatorNeedsThePlayerNearby() {
        assertEquals(CourseAccess.ALLOWED, CourseAccess.edit(true, true, true, false, false, true));
        assertEquals(CourseAccess.TOO_FAR, CourseAccess.edit(true, true, true, false, false, false));
    }

    @Test
    void gameModeAndDimensionComeFirstForEveryone() {
        assertEquals(CourseAccess.NO_BUILD, CourseAccess.edit(false, true, false, true, true, true));
        assertEquals(CourseAccess.OTHER_DIMENSION, CourseAccess.edit(true, false, false, true, true, true));
        assertEquals(CourseAccess.NO_BUILD, CourseAccess.delete(false, true, true, true, false));
        assertEquals(CourseAccess.OTHER_DIMENSION, CourseAccess.delete(true, false, true, true, false));
    }

    @Test
    void deleteIsForCreatorAndOperatorOnly() {
        assertEquals(CourseAccess.ALLOWED, CourseAccess.delete(true, true, true, false, false));
        assertEquals(CourseAccess.ALLOWED, CourseAccess.delete(true, true, false, true, false));
        assertEquals(CourseAccess.NOT_OWNER, CourseAccess.delete(true, true, false, false, false));
    }

    @Test
    void foundCourseIsDeletedByOperatorsOnly() {
        assertEquals(CourseAccess.WORLD_COURSE, CourseAccess.delete(true, true, true, false, true));
        assertEquals(CourseAccess.ALLOWED, CourseAccess.delete(true, true, false, true, true));
        assertEquals(CourseAccess.NOT_OWNER, CourseAccess.delete(true, true, false, false, true));
    }

    @Test
    void nearCountsTheRangeInclusive() {
        assertTrue(CourseAccess.near(256.0 * 256.0, 256.0));
        assertFalse(CourseAccess.near(256.0 * 256.0 + 1.0, 256.0));
        assertTrue(CourseAccess.ALLOWED.allowed());
        assertFalse(CourseAccess.TOO_FAR.allowed());
    }
}
