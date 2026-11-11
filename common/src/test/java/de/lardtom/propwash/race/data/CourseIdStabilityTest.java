package de.lardtom.propwash.race.data;

import de.lardtom.propwash.api.race.BoardQuery;
import de.lardtom.propwash.api.race.BoardSnapshot;
import de.lardtom.propwash.race.CourseRegistry;
import de.lardtom.propwash.race.export.RaceSnapshots;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CourseIdStabilityTest {
    @TempDir
    Path temp;

    @Test
    void idSurvivesRenameLapsReorderReverseRemoveAndNewLayouts() throws IOException {
        CourseRegistry registry = TestWorlds.open(this.temp, TestWorlds.V040);
        CourseRegistry.Course course = registry.course(1);
        assertNotNull(course);
        UUID key = course.key();
        assertTrue(registry.rename(course, "Canyon"));
        assertEquals(key, course.key());
        assertTrue(registry.setLaps(course, 5));
        assertEquals(key, course.key());
        registry.submitLap(course, TestWorlds.ANNA, "Anna", "race_5", 30 * TestWorlds.S, TestWorlds.DAY);
        registry.submitLap(course, TestWorlds.BEN, "Ben", "race_5", 32 * TestWorlds.S, TestWorlds.DAY + 1);
        assertEquals(1, course.layout);
        assertTrue(registry.moveGate(course, 1, 2), "Umbau: Reihenfolge ändern");
        assertEquals(2, course.layout, "neue Layout-Nummer, weil die Bestenliste archiviert wurde");
        assertEquals(key, course.key());
        assertTrue(registry.reversePass(course, 1), "Umbau: Durchgang umdrehen");
        assertEquals(key, course.key());
        assertEquals(2, course.layout, "ohne Zeiten bleibt die Nummer");
        registry.submitLap(course, TestWorlds.CARL, "Carl", "whoop_65", 41 * TestWorlds.S, TestWorlds.DAY + 2);
        assertTrue(registry.detachGate(course, 2), "Umbau: Element entfernen");
        assertEquals(3, course.layout);
        assertEquals(key, course.key());
        assertEquals(key, registry.course(key).key());
        assertEquals(List.of(1, 2, 3), RaceSnapshots.layouts(course, registry.records()));
        BoardSnapshot first = RaceSnapshots.board(course, registry.records(), BoardQuery.laps().layout(1)).orElseThrow();
        assertFalse(first.current());
        assertEquals(key, first.course());
        assertEquals(List.of(TestWorlds.ANNA, TestWorlds.BEN), first.entries().stream().map(e -> e.player()).toList());
        BoardSnapshot second = RaceSnapshots.board(course, registry.records(), BoardQuery.laps().layout(2)).orElseThrow();
        assertEquals(List.of(TestWorlds.CARL), second.entries().stream().map(e -> e.player()).toList());
        BoardSnapshot current = RaceSnapshots.board(course, registry.records(), BoardQuery.laps()).orElseThrow();
        assertTrue(current.current());
        assertEquals(3, current.layout());
        assertTrue(current.entries().isEmpty());
        assertTrue(RaceSnapshots.board(course, registry.records(), BoardQuery.laps().layout(9)).isEmpty());
        registry.save();
        CourseRegistry reloaded = TestWorlds.reopen(this.temp.resolve(TestWorlds.FILE));
        assertEquals(key, reloaded.course(1).key());
        assertEquals("Canyon", reloaded.course(key).name);
    }

    @Test
    void newCoursesGetNewIdsAndNumbersStayInternal() throws IOException {
        CourseRegistry registry = TestWorlds.open(this.temp, TestWorlds.V052);
        Set<UUID> seen = new HashSet<>();
        for (CourseRegistry.Course course : registry.courses()) {
            seen.add(course.key());
        }
        int number = registry.nextCourseId();
        int created = registry.createCourse(TestWorlds.OVERWORLD, null);
        assertEquals(number, created, "die fortlaufende Nummer bleibt");
        CourseRegistry.Course fresh = registry.course(created);
        assertNotNull(fresh);
        assertTrue(seen.add(fresh.key()), "neuer Kurs, neue ID");
        UUID gone = registry.course(1).key();
        assertTrue(registry.deleteCourse(1));
        assertNull(registry.course(gone));
        int again = registry.createCourse(TestWorlds.OVERWORLD, null);
        CourseRegistry.Course another = registry.course(again);
        assertNotEquals(gone, another.key(), "ein gelöschter Kurs vererbt seine ID nicht");
        assertTrue(seen.add(another.key()));
        assertNotEquals(1, again, "Nummern werden nicht wiederverwendet");
    }
}
