package de.lardtom.propwash.race;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CourseNamesTest {
    @Test
    void trimsAndCollapsesSpaces() {
        assertEquals("Canyon Sprint", CourseNames.clean("  Canyon \t  Sprint  "));
        assertEquals("", CourseNames.clean("   "));
        assertEquals("", CourseNames.clean(null));
    }

    @Test
    void keepsUmlautsAndDropsFormatting() {
        assertEquals("Große Schleife über Öl", CourseNames.clean("Große Schleife über Öl"));
        assertEquals("cRot", CourseNames.clean("§cRot"));
        assertEquals("ab", CourseNames.clean("a\u0000\u0007b"));
        assertEquals("ab", CourseNames.clean("a​b"));
        assertEquals("a b", CourseNames.clean("a\nb"));
    }

    @Test
    void cutsAfterTheLimitWithoutATrailingSpace() {
        String cut = CourseNames.clean("x".repeat(80));
        assertEquals(CourseNames.MAX, cut.length());
        String spaced = CourseNames.clean("y".repeat(CourseNames.MAX - 1) + " zzz");
        assertEquals("y".repeat(CourseNames.MAX - 1), spaced);
        assertEquals(CourseNames.MAX, CourseNames.clean("🏁".repeat(40)).codePointCount(0, CourseNames.clean("🏁".repeat(40)).length()));
    }

    @Test
    void cleaningIsStable() {
        String once = CourseNames.clean("  Wald   Slalom §l 2 ");
        assertEquals(once, CourseNames.clean(once));
        assertTrue(CourseNames.named(once));
        assertFalse(CourseNames.named(""));
        assertFalse(CourseNames.named(null));
    }
}
