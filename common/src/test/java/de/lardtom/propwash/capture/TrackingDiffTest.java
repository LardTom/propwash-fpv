package de.lardtom.propwash.capture;

import net.minecraft.server.level.ChunkTrackingView;
import net.minecraft.world.level.ChunkPos;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TrackingDiffTest {
    private record Diff(Set<Long> entered, Set<Long> left) {
    }

    private static Diff diff(ChunkTrackingView from, ChunkTrackingView to) {
        Set<Long> entered = new HashSet<>();
        Set<Long> left = new HashSet<>();
        TrackingDiff.difference(from, to, pos -> assertTrue(entered.add(pos.pack()), "doppelt betreten " + pos),
                pos -> assertTrue(left.add(pos.pack()), "doppelt verlassen " + pos));
        return new Diff(entered, left);
    }

    private static void matchesBruteForce(ChunkTrackingView from, ChunkTrackingView to) {
        Set<Long> before = CameraTrackingViewTest.brute(from, -80, 80);
        Set<Long> after = CameraTrackingViewTest.brute(to, -80, 80);
        Set<Long> entered = new HashSet<>(after);
        entered.removeAll(before);
        Set<Long> left = new HashSet<>(before);
        left.removeAll(after);
        Diff diff = diff(from, to);
        assertEquals(entered, diff.entered(), "betreten");
        assertEquals(left, diff.left(), "verlassen");
    }

    @Test
    void openingAnAreaOnlyAddsItsChunks() {
        ChunkTrackingView base = ChunkTrackingView.of(new ChunkPos(0, 0), 10);
        CameraArea far = new CameraArea(19, 0, 4);
        ChunkTrackingView camera = CameraTrackingView.of(new ChunkPos(0, 0), 10, List.of(far));
        Diff diff = diff(base, camera);
        assertTrue(diff.left().isEmpty(), "Chunks des Spielers bleiben");
        assertEquals(CameraTrackingViewTest.brute(camera, -40, 40).size() - CameraTrackingViewTest.brute(base, -40, 40).size(), diff.entered().size());
        for (long pos : diff.entered()) {
            assertTrue(far.contains(ChunkPos.getX(pos), ChunkPos.getZ(pos), true));
        }
        matchesBruteForce(base, camera);
    }

    @Test
    void closingAnAreaOnlyDropsItsChunks() {
        ChunkTrackingView base = ChunkTrackingView.of(new ChunkPos(0, 0), 10);
        ChunkTrackingView camera = CameraTrackingView.of(new ChunkPos(0, 0), 10, List.of(new CameraArea(19, 0, 4)));
        Diff diff = diff(camera, base);
        assertTrue(diff.entered().isEmpty());
        for (long pos : diff.left()) {
            assertTrue(!base.contains(ChunkPos.getX(pos), ChunkPos.getZ(pos)), "Chunk des Spielers verlassen: " + pos);
        }
        matchesBruteForce(camera, base);
    }

    @Test
    void movingAreaAndPlayerChangesOnlyTheEdges() {
        ChunkTrackingView a = CameraTrackingView.of(new ChunkPos(0, 0), 8, List.of(new CameraArea(30, 0, 4)));
        ChunkTrackingView b = CameraTrackingView.of(new ChunkPos(0, 0), 8, List.of(new CameraArea(31, 0, 4)));
        Diff diff = diff(a, b);
        assertTrue(diff.entered().size() <= 2 * 4 + 3);
        assertTrue(diff.left().size() <= 2 * 4 + 3);
        matchesBruteForce(a, b);
        ChunkTrackingView c = CameraTrackingView.of(new ChunkPos(1, 0), 8, List.of(new CameraArea(31, 1, 4), new CameraArea(-20, 5, 2)));
        matchesBruteForce(b, c);
        matchesBruteForce(c, ChunkTrackingView.of(new ChunkPos(40, 40), 8));
        matchesBruteForce(c, ChunkTrackingView.EMPTY);
        matchesBruteForce(ChunkTrackingView.EMPTY, c);
    }

    @Test
    void vanillaViewsKeepTheVanillaDifference() {
        matchesBruteForce(ChunkTrackingView.of(new ChunkPos(0, 0), 6), ChunkTrackingView.of(new ChunkPos(2, -1), 6));
        matchesBruteForce(ChunkTrackingView.of(new ChunkPos(0, 0), 6), ChunkTrackingView.of(new ChunkPos(50, 0), 6));
        Diff same = diff(CameraTrackingView.of(new ChunkPos(0, 0), 6, List.of(new CameraArea(20, 0, 3))),
                CameraTrackingView.of(new ChunkPos(0, 0), 6, List.of(new CameraArea(20, 0, 3))));
        assertTrue(same.entered().isEmpty() && same.left().isEmpty());
    }

    @Test
    void vanillaFallbackWouldDropThePlayersChunks() {
        ChunkTrackingView base = ChunkTrackingView.of(new ChunkPos(0, 0), 4);
        ChunkTrackingView camera = CameraTrackingView.of(new ChunkPos(0, 0), 4, List.of(new CameraArea(20, 0, 2)));
        Set<Long> left = new HashSet<>();
        ChunkTrackingView.difference(base, camera, pos -> {
        }, pos -> left.add(pos.pack()));
        assertTrue(left.contains(ChunkPos.pack(0, 0)), "Vanilla gibt beim Wechsel alle Chunks frei");
        assertTrue(diff(base, camera).left().isEmpty());
    }
}
