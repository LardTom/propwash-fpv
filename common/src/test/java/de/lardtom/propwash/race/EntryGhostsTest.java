package de.lardtom.propwash.race;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EntryGhostsTest {
    static final UUID ANNA = UUID.fromString("00000000-0000-4000-8000-0000000000a1");
    static final UUID BEN = UUID.fromString("00000000-0000-4000-8000-0000000000b2");
    static final UUID COURSE = UUID.fromString("11111111-2222-4333-8444-555555555555");
    static final long S = 1_000_000_000L;

    static GhostTrack track(long lapNanos, String frame, int layout) {
        int samples = (int) (lapNanos / GhostServer.FINE_INTERVAL_NANOS) + 1;
        double[] positions = new double[samples * 3];
        float[] rotations = new float[samples * 4];
        for (int i = 0; i < samples; i++) {
            positions[i * 3] = i * 0.2;
            positions[i * 3 + 1] = 64.0 + Math.sin(i * 0.05);
            positions[i * 3 + 2] = Math.cos(i * 0.03) * 4.0;
            rotations[i * 4 + 3] = 1.0F;
        }
        return new GhostTrack(GhostServer.FINE_INTERVAL_NANOS, positions, rotations, lapNanos, frame, layout);
    }

    static EntryGhost ghost(UUID player, String name, String frame, long nanos, long date, int layout, String code) {
        EntryGhost.Info info = new EntryGhost.Info(player, name, frame, nanos, date, LapAssist.NONE, layout, 25.0F, code, EntryGhost.Source.SERVER);
        return new EntryGhost(info, GhostCodec.encode(track(nanos, frame, layout)));
    }

    @Test
    void entryGhostRoundTripsWithShareCodeAndCameraAngle() throws IOException {
        EntryGhost ghost = ghost(ANNA, "Anna", "race_5", 12 * S, 1_700_000_000_000L, 77, "PW3-ABCDEF");
        byte[] data = ghost.encode();
        EntryGhost back = EntryGhost.decode(data);
        assertEquals(ghost.info(), back.info());
        assertArrayEquals(ghost.track(), back.track());
        assertEquals(25.0F, back.info().uptilt());
        assertTrue(back.info().hasUptilt());
        assertEquals("PW3-ABCDEF", EntryGhost.info(data).code());
        GhostTrack decoded = back.decodeTrack();
        assertEquals(GhostServer.FINE_INTERVAL_NANOS, decoded.intervalNanos());
        assertEquals(12 * S, decoded.lapNanos());
    }

    @Test
    void brokenDataIsRejected() {
        assertThrows(IOException.class, () -> EntryGhost.decode(new byte[0]));
        assertThrows(IOException.class, () -> EntryGhost.decode(new byte[]{1, 2, 3, 4, 5}));
        byte[] data = ghost(ANNA, "Anna", "race_5", 5 * S, 1L, 1, "").encode();
        byte[] cut = java.util.Arrays.copyOf(data, data.length / 2);
        assertThrows(IOException.class, () -> EntryGhost.decode(cut));
    }

    @Test
    void fileNamesAreStableAndCarryThePlayer() {
        GhostKey key = new GhostKey(ANNA, "propwash_jmp:whoop_65", 12 * S, 1234L);
        String name = key.fileName();
        assertTrue(name.endsWith(GhostKey.EXTENSION));
        assertFalse(name.contains(":"));
        assertEquals(ANNA, GhostKey.playerOf(name));
        assertNull(GhostKey.playerOf("kaputt.pwghost"));
        assertEquals(name, new GhostKey(ANNA, "propwash_jmp:whoop_65", 12 * S, 1234L).fileName());
        assertFalse(name.equals(new GhostKey(ANNA, "propwash_jmp:whoop_65", 12 * S, 1235L).fileName()));
    }

    @Test
    void storePutsOneCompressedFilePerEntryAndPrunesTheRest(@TempDir Path dir) throws IOException {
        EntryGhosts store = new EntryGhosts(dir);
        EntryGhost anna = ghost(ANNA, "Anna", "race_5", 12 * S, 100L, 7, "PW3-A");
        EntryGhost ben = ghost(BEN, "Ben", "race_5", 13 * S, 200L, 7, "PW3-B");
        assertTrue(store.put(COURSE, anna));
        assertTrue(store.put(COURSE, ben));
        Path folder = dir.resolve(EntryGhosts.FOLDER).resolve(COURSE.toString());
        assertTrue(Files.isRegularFile(folder.resolve(anna.key().fileName())));
        assertTrue(Files.size(folder.resolve(anna.key().fileName())) < anna.track().length + 512);
        assertEquals(2, store.count(COURSE));
        assertEquals("PW3-B", store.info(COURSE, ben.key()).code());
        EntryGhost read = store.get(COURSE, anna.key());
        assertNotNull(read);
        assertEquals(anna.info(), read.info());
        assertEquals(1, store.prune(COURSE, List.of(ben.key())));
        assertFalse(store.has(COURSE, anna.key()));
        assertFalse(Files.exists(folder.resolve(anna.key().fileName())));
        assertTrue(store.has(COURSE, ben.key()));
        EntryGhosts reopened = new EntryGhosts(dir);
        assertEquals(1, reopened.count(COURSE));
        assertTrue(reopened.has(COURSE, ben.key()));
    }

    @Test
    void layoutChangeArchivesLikeTheLeaderboard(@TempDir Path dir) {
        EntryGhosts store = new EntryGhosts(dir);
        EntryGhost anna = ghost(ANNA, "Anna", "race_5", 12 * S, 100L, 7, "");
        store.put(COURSE, anna);
        assertEquals(1, store.archive(COURSE, 1, List.of(1)));
        assertEquals(0, store.count(COURSE));
        assertEquals(List.of(1), store.archivedLayouts(COURSE));
        assertEquals(1, store.archivedCount(COURSE, 1));
        store.put(COURSE, ghost(BEN, "Ben", "race_5", 11 * S, 300L, 8, ""));
        store.archive(COURSE, 2, List.of(2));
        assertEquals(List.of(2), store.archivedLayouts(COURSE));
        store.removeCourse(COURSE);
        assertFalse(Files.exists(dir.resolve(EntryGhosts.FOLDER).resolve(COURSE.toString())));
    }

    @Test
    void removalLogAppendsOneLinePerRemoval(@TempDir Path dir) throws IOException {
        EntryGhosts store = new EntryGhosts(dir);
        assertTrue(store.log("a\nb"));
        assertTrue(store.log("c"));
        List<String> lines = Files.readAllLines(store.logFile());
        assertEquals(List.of("a b", "c"), lines);
        assertFalse(EntryGhosts.disabled().log("x"));
        assertFalse(EntryGhosts.disabled().put(COURSE, ghost(ANNA, "Anna", "race_5", S, 1L, 1, "")));
    }
}
