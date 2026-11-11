package de.lardtom.propwash.race;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CourseRightsTest {
    private static final UUID OWNER = UUID.fromString("00000000-0000-4000-8000-0000000000a1");
    private static final UUID GUEST = UUID.fromString("00000000-0000-4000-8000-0000000000b2");

    private static CourseRegistry.Course course(String owner) {
        CourseRegistry.Course course = new CourseRegistry.Course();
        course.id = 3;
        course.owner = owner;
        return course;
    }

    private static CourseAccess edit(CourseRegistry.Course course, UUID player, boolean operator) {
        return CourseAccess.edit(true, true, course.owner.isEmpty(), course.ownedBy(player), operator);
    }

    @Test
    void onlyOwnerAndOperatorMayChangeACourse() {
        CourseRegistry.Course owned = course(OWNER.toString());
        assertEquals(CourseAccess.ALLOWED, edit(owned, OWNER, false));
        assertEquals(CourseAccess.NOT_OWNER, edit(owned, GUEST, false));
        assertEquals(CourseAccess.ALLOWED, edit(owned, GUEST, true));
        assertTrue(CourseAccess.mayChange(true, false));
        assertTrue(CourseAccess.mayChange(false, true));
        assertFalse(CourseAccess.mayChange(false, false));
    }

    @Test
    void oldCourseWithoutOwnerBelongsToTheOperators() {
        CourseRegistry.Course old = course("");
        assertEquals(CourseAccess.OWNERLESS, edit(old, GUEST, false));
        assertEquals(CourseAccess.OWNERLESS, edit(old, OWNER, false));
        assertEquals(CourseAccess.ALLOWED, edit(old, GUEST, true));
    }

    @Test
    void deletingStaysAsBefore() {
        assertEquals(CourseAccess.ALLOWED, CourseAccess.delete(true, true, true, false, false));
        assertEquals(CourseAccess.NOT_OWNER, CourseAccess.delete(true, true, false, false, false));
        assertEquals(CourseAccess.WORLD_COURSE, CourseAccess.delete(true, true, true, false, true));
        assertEquals(CourseAccess.ALLOWED, CourseAccess.delete(true, true, false, true, true));
    }

    @Test
    void newEditActionsGoAtTheEndOfThePacketList() {
        CourseEdits.Action[] actions = CourseEdits.Action.values();
        assertEquals(CourseEdits.Action.RACE_LINE, actions[actions.length - 1]);
        assertEquals(CourseEdits.Action.MODE, actions[actions.length - 2]);
        assertEquals(CourseEdits.Action.REVERSE, actions[actions.length - 3]);
        assertEquals(CourseEdits.Action.MODE, CourseEdits.Action.byOrdinal(CourseEdits.Action.MODE.ordinal()));
    }
}
