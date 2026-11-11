package de.lardtom.propwash.race.data;

import de.lardtom.propwash.race.AssistReasons;
import de.lardtom.propwash.race.CourseRegistry;
import de.lardtom.propwash.race.EntryGhost;
import de.lardtom.propwash.race.EntryGhosts;
import de.lardtom.propwash.race.GhostCodec;
import de.lardtom.propwash.race.GhostKey;
import de.lardtom.propwash.race.GhostServer;
import de.lardtom.propwash.race.GhostTrack;
import de.lardtom.propwash.race.LapAssist;
import de.lardtom.propwash.race.Leaderboard;
import de.lardtom.propwash.race.RaceRecords;
import de.lardtom.propwash.race.RaceRuleSet;
import de.lardtom.propwash.race.RecordGhosts;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LeaderboardGhostsTest {
    private static final String FRAME = "race_5";

    private static GhostTrack track(long lapNanos, int layout) {
        int samples = (int) (lapNanos / GhostServer.FINE_INTERVAL_NANOS) + 1;
        double[] positions = new double[samples * 3];
        float[] rotations = new float[samples * 4];
        for (int i = 0; i < samples; i++) {
            positions[i * 3] = -30.5 + i * 0.05;
            positions[i * 3 + 1] = -59.0;
            positions[i * 3 + 2] = 16.5 + i * 0.05;
            rotations[i * 4 + 3] = 1.0F;
        }
        return new GhostTrack(GhostServer.FINE_INTERVAL_NANOS, positions, rotations, lapNanos, FRAME, layout);
    }

    private static Leaderboard.Entry submit(CourseRegistry registry, CourseRegistry.Course course, UUID player, String name, long nanos, long date) {
        Leaderboard.Result result = registry.submitTime(course, player, name, FRAME, nanos, date, AssistReasons.NONE, RaceRuleSet.DEFAULTS, true);
        assertTrue(result.stored());
        int layout = course.toData().layoutHash();
        EntryGhost.Info info = new EntryGhost.Info(player, name, FRAME, nanos, date, LapAssist.NONE, layout, 20.0F, "PW3-" + name,
                EntryGhost.Source.SERVER);
        assertTrue(registry.entryGhosts().put(course.key(), new EntryGhost(info, GhostCodec.encode(track(nanos, layout)))));
        for (Leaderboard.Entry entry : registry.board(course).storedTimes()) {
            if (entry.player().equals(player) && entry.nanos() == nanos) {
                return entry;
            }
        }
        throw new AssertionError("Zeit nicht gespeichert");
    }

    @Test
    void ghostLeavesWithItsEntryAndRemovalMovesTheListUp(@TempDir Path dir) throws IOException {
        CourseRegistry registry = TestWorlds.open(dir, TestWorlds.V040);
        registry.useEntryGhosts(new EntryGhosts(dir.resolve("race-data")));
        CourseRegistry.Course course = registry.course(1);
        assertNotNull(course);
        Leaderboard.Entry annaSlow = submit(registry, course, TestWorlds.ANNA, "Anna", 14 * TestWorlds.S, TestWorlds.DAY);
        Leaderboard.Entry ben = submit(registry, course, TestWorlds.BEN, "Ben", 13 * TestWorlds.S, TestWorlds.DAY + 1);
        assertTrue(registry.entryGhosts().has(course.key(), GhostKey.of(annaSlow)));
        Leaderboard.Entry annaFast = submit(registry, course, TestWorlds.ANNA, "Anna", 12 * TestWorlds.S, TestWorlds.DAY + 2);
        assertFalse(registry.entryGhosts().has(course.key(), GhostKey.of(annaSlow)), "alter Geist bleibt nach besserer Zeit liegen");
        assertTrue(registry.entryGhosts().has(course.key(), GhostKey.of(annaFast)));
        assertEquals(TestWorlds.ANNA, registry.board(course).top(null, 1).getFirst().player());
        List<Leaderboard.Entry> removed = GhostServer.remove(registry, course, TestWorlds.ANNA, FRAME, Leaderboard.Section.ALL, "Admin", null,
                "test");
        assertEquals(1, removed.size());
        assertFalse(registry.entryGhosts().has(course.key(), GhostKey.of(annaFast)));
        assertTrue(registry.entryGhosts().has(course.key(), GhostKey.of(ben)));
        assertEquals(TestWorlds.BEN, registry.board(course).top(null, 1).getFirst().player(), "Rekord geht an die nächstbeste Zeit");
        List<String> log = Files.readAllLines(registry.entryGhosts().logFile());
        assertEquals(1, log.size());
        assertTrue(log.getFirst().contains("removed by=Admin"), log.getFirst());
        assertTrue(log.getFirst().contains("pilot=Anna(" + TestWorlds.ANNA + ")"), log.getFirst());
        assertTrue(log.getFirst().contains("class=" + FRAME), log.getFirst());
    }

    @Test
    void removalOfAnUnknownPilotChangesNothing(@TempDir Path dir) throws IOException {
        CourseRegistry registry = TestWorlds.open(dir, TestWorlds.V040);
        registry.useEntryGhosts(new EntryGhosts(dir.resolve("race-data")));
        CourseRegistry.Course course = registry.course(1);
        submit(registry, course, TestWorlds.BEN, "Ben", 13 * TestWorlds.S, TestWorlds.DAY);
        assertTrue(GhostServer.remove(registry, course, TestWorlds.CARL, null, Leaderboard.Section.ALL, "Admin", null, "test").isEmpty());
        assertEquals(1, registry.entryGhosts().count(course.key()));
        assertFalse(Files.exists(registry.entryGhosts().logFile()));
        assertEquals(TestWorlds.BEN, registry.board(course).playerNamed("ben"));
    }

    @Test
    void existingRecordGhostsAreTakenOver(@TempDir Path dir) throws IOException {
        Path file = TestWorlds.write(dir, TestWorlds.V040);
        RaceRecords records = new RaceRecords();
        RecordGhosts legacy = new RecordGhosts();
        CourseRegistry registry = CourseRegistry.offline(file, records, legacy);
        CourseRegistry.Course course = registry.course(1);
        registry.submitTime(course, TestWorlds.BEN, "Ben", FRAME, 13 * TestWorlds.S, TestWorlds.DAY, AssistReasons.NONE, RaceRuleSet.DEFAULTS, true);
        int layout = course.toData().layoutHash();
        legacy.put(course.id, FRAME, layout, "Ben", 13 * TestWorlds.S, GhostCodec.encode(track(13 * TestWorlds.S, layout)));
        legacy.put(course.id, "whoop", layout + 1, "Ben", 9 * TestWorlds.S, GhostCodec.encode(track(9 * TestWorlds.S, layout + 1)));
        registry.useEntryGhosts(new EntryGhosts(dir.resolve("race-data")));
        assertEquals(1, registry.migrateRecordGhosts());
        Leaderboard.Entry entry = registry.board(course).storedTimes().getFirst();
        EntryGhost.Info info = registry.entryGhosts().info(course.key(), GhostKey.of(entry));
        assertNotNull(info);
        assertEquals(EntryGhost.Source.RECORD_GHOST, info.source());
        assertEquals("", info.code());
        assertFalse(info.hasUptilt());
        assertEquals(0, registry.migrateRecordGhosts(), "zweite Übernahme legt nichts doppelt an");
    }
}
