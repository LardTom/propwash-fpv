package de.lardtom.propwash.race.data;

import de.lardtom.propwash.api.race.Category;
import de.lardtom.propwash.api.race.ChangeKind;
import de.lardtom.propwash.api.race.CourseChange;
import de.lardtom.propwash.api.race.CourseInfo;
import de.lardtom.propwash.api.race.CourseRecord;
import de.lardtom.propwash.api.race.DeletedCourse;
import de.lardtom.propwash.api.race.LapResult;
import de.lardtom.propwash.api.race.PersonalBest;
import de.lardtom.propwash.api.race.RaceEvents;
import de.lardtom.propwash.api.race.RaceResult;
import de.lardtom.propwash.api.race.TimeKind;
import de.lardtom.propwash.race.AssistDetail;
import de.lardtom.propwash.race.AssistReasons;
import de.lardtom.propwash.race.CourseRegistry;
import de.lardtom.propwash.race.export.RaceDataEvents;
import de.lardtom.propwash.sim.HoldMode;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.OptionalLong;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RaceEventsTest {
    private static final long S = TestWorlds.S;
    private static final List<Object> SEEN = new CopyOnWriteArrayList<>();
    private static volatile boolean failing;
    private static boolean registered;

    @TempDir
    Path temp;

    @BeforeAll
    static synchronized void listen() {
        if (registered) {
            return;
        }
        registered = true;
        RaceEvents.LAP_COMPLETED.register(lap -> {
            if (failing) {
                throw new IllegalStateException("Listener kaputt");
            }
        });
        RaceEvents.LAP_COMPLETED.register(SEEN::add);
        RaceEvents.RACE_FINISHED.register(SEEN::add);
        RaceEvents.PERSONAL_BEST.register(SEEN::add);
        RaceEvents.COURSE_RECORD.register(SEEN::add);
        RaceEvents.COURSE_CREATED.register(SEEN::add);
        RaceEvents.COURSE_CHANGED.register(SEEN::add);
        RaceEvents.COURSE_DELETED.register(SEEN::add);
    }

    @BeforeEach
    void clear() {
        SEEN.clear();
        failing = false;
    }

    private static <T> List<T> seen(Class<T> type) {
        return SEEN.stream().filter(type::isInstance).map(type::cast).toList();
    }

    @Test
    void courseLifecycleFiresCreatedChangedDeleted() throws IOException {
        CourseRegistry registry = TestWorlds.open(this.temp, TestWorlds.V040);
        CourseRegistry.Course course = registry.course(1);
        UUID key = course.key();
        registry.rename(course, "Canyon");
        registry.setLaps(course, 4);
        registry.submitLap(course, TestWorlds.ANNA, "Anna", "race_5", 30 * S, TestWorlds.DAY);
        SEEN.clear();
        registry.moveGate(course, 1, 2);
        CourseChange layout = seen(CourseChange.class).getFirst();
        assertEquals(ChangeKind.LAYOUT, layout.kind());
        assertEquals(1, layout.archivedLayout());
        assertTrue(layout.archived());
        assertEquals(2, layout.course().layout());
        assertEquals(key, layout.course().id());
        assertEquals(List.of(1, 2), layout.course().layouts());
        SEEN.clear();
        registry.reversePass(course, 1);
        assertEquals(0, seen(CourseChange.class).getFirst().archivedLayout(), "ohne Zeiten nichts archiviert");
        SEEN.clear();
        int created = registry.createCourse(TestWorlds.OVERWORLD, null);
        CourseInfo info = seen(CourseInfo.class).getFirst();
        assertEquals(created, info.number());
        assertEquals(registry.course(created).key(), info.id());
        assertEquals(0, info.elements());
        SEEN.clear();
        registry.deleteCourse(1);
        DeletedCourse deleted = seen(DeletedCourse.class).getFirst();
        assertEquals(key, deleted.id());
        assertEquals(1, deleted.number());
        assertEquals("Canyon", deleted.name());
    }

    @Test
    void renameAndLapsFireNamedChanges() throws IOException {
        CourseRegistry registry = TestWorlds.open(this.temp, TestWorlds.V040);
        CourseRegistry.Course course = registry.course(1);
        registry.rename(course, "Canyon");
        registry.rename(course, "Canyon");
        registry.setLaps(course, 4);
        assertEquals(List.of(ChangeKind.NAME, ChangeKind.LAPS), seen(CourseChange.class).stream().map(CourseChange::kind).toList(),
                "gleicher Name ändert nichts");
        assertEquals("Canyon", seen(CourseChange.class).getFirst().course().name());
        assertEquals(4, seen(CourseChange.class).get(1).course().laps());
    }

    @Test
    void personalBestsAndRecordsFireOnSubmission() throws IOException {
        CourseRegistry registry = TestWorlds.open(this.temp, TestWorlds.V040);
        CourseRegistry.Course course = registry.course(1);
        registry.submitLap(course, TestWorlds.ANNA, "Anna", "race_5", 30 * S, TestWorlds.DAY);
        PersonalBest first = seen(PersonalBest.class).getFirst();
        assertEquals(course.key(), first.course());
        assertEquals(OptionalLong.empty(), first.previousMillis());
        assertEquals(30_000L, first.entry().timeMillis());
        assertEquals(1, first.entry().rank());
        CourseRecord record = seen(CourseRecord.class).getFirst();
        assertTrue(record.previous().isEmpty());
        assertEquals("Anna", record.entry().name());
        SEEN.clear();
        registry.submitLap(course, TestWorlds.ANNA, "Anna", "race_5", 31 * S, TestWorlds.DAY + 1);
        assertTrue(SEEN.isEmpty(), "langsamer: kein Ereignis bei der Einreichung");
        registry.submitLap(course, TestWorlds.BEN, "Ben", "race_5", 29 * S, TestWorlds.DAY + 2);
        assertEquals(1, seen(PersonalBest.class).size());
        CourseRecord beaten = seen(CourseRecord.class).getFirst();
        assertEquals("Ben", beaten.entry().name());
        assertEquals(TestWorlds.ANNA, beaten.previous().orElseThrow().player());
        assertEquals(30_000L, beaten.previous().orElseThrow().timeMillis());
        SEEN.clear();
        registry.submitLap(course, TestWorlds.CARL, "Carl", "whoop_65", 40 * S, TestWorlds.DAY + 3);
        assertEquals(1, seen(PersonalBest.class).size());
        assertTrue(seen(CourseRecord.class).isEmpty(), "Klassenrekord ist kein Kursrekord");
        assertEquals(3, seen(PersonalBest.class).getFirst().entry().rank());
        SEEN.clear();
        registry.submitAssistedLap(course, TestWorlds.CARL, "Carl", "race_5", 20 * S, TestWorlds.DAY + 4,
                AssistReasons.of(List.of(AssistDetail.hold(HoldMode.POSHOLD))));
        PersonalBest assisted = seen(PersonalBest.class).getFirst();
        assertEquals(Category.ASSISTED, assisted.entry().category());
        assertEquals(List.of("hold_mode"), assisted.entry().assistReasons());
        assertTrue(seen(CourseRecord.class).isEmpty(), "Runden mit Flughilfe werden nie Kursrekord");
        SEEN.clear();
        registry.submitRace(course, TestWorlds.ANNA, "Anna", 3, 95 * S, "race_5", TestWorlds.DAY + 5);
        registry.submitRace(course, TestWorlds.ANNA, "Anna", 3, 96 * S, "race_5", TestWorlds.DAY + 6);
        registry.submitRace(course, TestWorlds.ANNA, "Anna", 3, 94 * S, "race_5", TestWorlds.DAY + 7);
        List<PersonalBest> races = seen(PersonalBest.class);
        assertEquals(2, races.size());
        assertEquals(TimeKind.RACE, races.get(1).entry().kind());
        assertEquals(3, races.get(1).entry().laps());
        assertEquals(OptionalLong.of(95_000L), races.get(1).previousMillis());
    }

    @Test
    void lapAndRaceResultsCarryEverythingAndSurviveBrokenListeners() throws IOException {
        CourseRegistry registry = TestWorlds.open(this.temp, TestWorlds.V040);
        CourseRegistry.Course course = registry.course(1);
        registry.submitLap(course, TestWorlds.ANNA, "Anna", "race_5", 30 * S, TestWorlds.DAY);
        failing = true;
        RaceDataEvents.lap(registry, course, TestWorlds.ANNA, "Anna", 2, 30 * S, "race_5", AssistReasons.NONE, true, true, true, true);
        LapResult lap = seen(LapResult.class).getFirst();
        assertEquals(course.key(), lap.course());
        assertEquals(1, lap.number());
        assertEquals(2, lap.lap());
        assertEquals(30_000L, lap.timeMillis());
        assertEquals(Category.CLEAN, lap.category());
        assertTrue(lap.saved() && lap.personalBest() && lap.courseRecord());
        assertEquals(1, lap.rank());
        AssistReasons hold = AssistReasons.of(List.of(AssistDetail.hold(HoldMode.ALTHOLD)));
        RaceDataEvents.lap(registry, course, TestWorlds.BEN, "Ben", 1, 28 * S, "race_5", hold, false, false, false, false);
        LapResult unsaved = seen(LapResult.class).get(1);
        assertEquals(Category.ASSISTED, unsaved.category());
        assertFalse(unsaved.saved());
        assertEquals(-1, unsaved.rank());
        RaceDataEvents.race(course, TestWorlds.ANNA, "Anna", 3, 92_345_678_900L, "race_5", AssistReasons.NONE, true, true, 1);
        RaceResult race = seen(RaceResult.class).getFirst();
        assertEquals(3, race.laps());
        assertEquals(92_346L, race.timeMillis());
        assertEquals(1, race.heatPlace());
        assertEquals(course.key(), race.course());
    }
}
