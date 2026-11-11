package de.lardtom.propwash.capture;

import net.minecraft.server.level.ChunkTrackingView;
import net.minecraft.world.level.ChunkPos;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CameraTrackingViewTest {
    static Set<Long> brute(ChunkTrackingView view, int min, int max) {
        Set<Long> out = new HashSet<>();
        for (int x = min; x <= max; x++) {
            for (int z = min; z <= max; z++) {
                if (view.contains(x, z)) {
                    out.add(ChunkPos.pack(x, z));
                }
            }
        }
        return out;
    }

    static List<Long> listed(ChunkTrackingView view) {
        List<Long> out = new ArrayList<>();
        view.forEach(pos -> out.add(pos.pack()));
        return out;
    }

    @Test
    void withoutAreasTheViewStaysVanilla() {
        ChunkTrackingView view = CameraTrackingView.of(new ChunkPos(3, 4), 10, List.of());
        assertInstanceOf(ChunkTrackingView.Positioned.class, view);
        assertEquals(ChunkTrackingView.of(new ChunkPos(3, 4), 10), view);
        assertEquals(new ChunkPos(3, 4), CameraTrackingView.center(view));
        assertTrue(CameraTrackingView.areas(view).isEmpty());
        assertNull(CameraTrackingView.center(ChunkTrackingView.EMPTY));
    }

    @Test
    void containsThePlayerViewAndEveryArea() {
        CameraArea far = new CameraArea(19, 0, 3);
        ChunkTrackingView view = CameraTrackingView.of(new ChunkPos(0, 0), 8, List.of(far));
        CameraTrackingView camera = assertInstanceOf(CameraTrackingView.class, view);
        assertEquals(new ChunkPos(0, 0), CameraTrackingView.center(view));
        assertEquals(List.of(far), CameraTrackingView.areas(view));
        assertTrue(view.contains(0, 0));
        assertTrue(view.contains(8, 0));
        assertTrue(view.contains(19, 0));
        assertTrue(view.contains(19, 4));
        assertFalse(view.contains(13, 0));
        assertFalse(view.contains(19, 6));
        assertFalse(view.contains(-19, 0));
        assertTrue(camera.isInViewDistance(19, 2));
        assertFalse(camera.isInViewDistance(19, 4));
        assertTrue(view.contains(19, 4, true));
    }

    @Test
    void listsEveryChunkExactlyOnce() {
        ChunkTrackingView view = CameraTrackingView.of(new ChunkPos(0, 0), 6,
                List.of(new CameraArea(9, 2, 4), new CameraArea(12, 5, 3), new CameraArea(-30, -30, 2)));
        List<Long> listed = listed(view);
        Set<Long> unique = new HashSet<>(listed);
        assertEquals(unique.size(), listed.size(), "doppelt gelistet");
        assertEquals(brute(view, -45, 45), unique);
    }

    @Test
    void equalViewsCompareEqual() {
        ChunkTrackingView a = CameraTrackingView.of(new ChunkPos(1, 1), 8, List.of(new CameraArea(30, 0, 4)));
        ChunkTrackingView b = CameraTrackingView.of(new ChunkPos(1, 1), 8, new ArrayList<>(List.of(new CameraArea(30, 0, 4))));
        ChunkTrackingView moved = CameraTrackingView.of(new ChunkPos(1, 1), 8, List.of(new CameraArea(31, 0, 4)));
        assertEquals(a, b);
        assertEquals(a.hashCode(), b.hashCode());
        assertNotEquals(a, moved);
        assertNotEquals(a, ChunkTrackingView.of(new ChunkPos(1, 1), 8));
    }

    @Test
    void areaHelpers() {
        CameraArea area = CameraArea.of(new ChunkPos(-4, 7), 2);
        assertEquals(new ChunkPos(-4, 7), area.center());
        assertEquals(-7, area.minX());
        assertEquals(-1, area.maxX());
        assertEquals(4, area.minZ());
        assertEquals(10, area.maxZ());
        assertEquals(10, area.distanceFrom(6, 0));
        assertEquals(13, area.extentFrom(6, 0));
        List<Long> listed = listed(new CameraTrackingView(new ChunkTrackingView.Positioned(new ChunkPos(100, 100), 2), List.of(area)));
        assertTrue(listed.contains(ChunkPos.pack(-4, 10)));
        assertTrue(listed.contains(ChunkPos.pack(-7, 10)));
        assertFalse(listed.contains(ChunkPos.pack(-8, 7)));
        assertEquals(49, area.chunks());
        List<CameraArea> mutable = new ArrayList<>(List.of(area));
        CameraTrackingView view = new CameraTrackingView(new ChunkTrackingView.Positioned(new ChunkPos(0, 0), 2), mutable);
        mutable.clear();
        assertEquals(1, view.areas().size());
        assertSame(view.areas(), view.areas());
    }
}
