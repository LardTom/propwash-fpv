package de.lardtom.propwash.video.link;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TerrainCacheTest {
    @Test
    void keysPackLikeChunkPositions() {
        assertEquals(0L, TerrainCache.key(0, 0));
        assertEquals(0xFFFFFFFFL, TerrainCache.key(-1, 0));
        assertEquals(0xFFFFFFFF00000000L, TerrainCache.key(0, -1));
        assertEquals(3L | 7L << 32, TerrainCache.key(3, 7));
        assertEquals((-1_875_000 & 0xFFFFFFFFL) | (1_875_000L << 32), TerrainCache.key(-1_875_000, 1_875_000));
        assertNotEquals(TerrainCache.key(1, 2), TerrainCache.key(2, 1));
        assertNotEquals(TerrainCache.key(-1, 2), TerrainCache.key(1, -2));
    }

    @Test
    void storesAndReplacesColumns() {
        TerrainCache cache = new TerrainCache();
        TerrainColumns a = new TerrainColumns();
        TerrainColumns b = new TerrainColumns();
        assertNull(cache.get(TerrainCache.key(4, -9)));
        cache.put(TerrainCache.key(4, -9), a);
        assertSame(a, cache.get(TerrainCache.key(4, -9)));
        assertTrue(cache.contains(TerrainCache.key(4, -9)));
        assertFalse(cache.contains(TerrainCache.key(-9, 4)));
        cache.put(TerrainCache.key(4, -9), b);
        assertSame(b, cache.get(TerrainCache.key(4, -9)));
        assertEquals(1, cache.size());
        assertThrows(NullPointerException.class, () -> cache.put(1L, null));
    }

    @Test
    void evictsTheLeastRecentlyUsedChunk() {
        TerrainCache cache = new TerrainCache(3);
        cache.put(1L, new TerrainColumns());
        cache.put(2L, new TerrainColumns());
        cache.put(3L, new TerrainColumns());
        cache.get(1L);
        cache.put(4L, new TerrainColumns());
        assertEquals(3, cache.size());
        assertTrue(cache.contains(1L));
        assertFalse(cache.contains(2L));
        assertTrue(cache.contains(3L));
        assertTrue(cache.contains(4L));
        cache.put(3L, new TerrainColumns());
        cache.put(5L, new TerrainColumns());
        assertFalse(cache.contains(1L));
        assertTrue(cache.contains(3L));
        assertTrue(cache.contains(4L));
        assertTrue(cache.contains(5L));
    }

    @Test
    void containsDoesNotCountAsUse() {
        TerrainCache cache = new TerrainCache(2);
        cache.put(1L, new TerrainColumns());
        cache.put(2L, new TerrainColumns());
        assertTrue(cache.contains(1L));
        cache.put(3L, new TerrainColumns());
        assertFalse(cache.contains(1L));
        assertTrue(cache.contains(2L));
    }

    @Test
    void neverHoldsMoreThan4096Chunks() {
        TerrainCache cache = new TerrainCache();
        assertEquals(4096, cache.capacity());
        assertEquals(TerrainCache.CAPACITY, cache.capacity());
        for (int i = 0; i < 5000; i++) {
            cache.put(TerrainCache.key(i % 100, i / 100), new TerrainColumns());
            assertTrue(cache.size() <= TerrainCache.CAPACITY);
        }
        assertEquals(TerrainCache.CAPACITY, cache.size());
        assertFalse(cache.contains(TerrainCache.key(0, 0)));
        assertFalse(cache.contains(TerrainCache.key(3, 9)));
        assertTrue(cache.contains(TerrainCache.key(4, 9)));
        assertTrue(cache.contains(TerrainCache.key(99, 49)));
    }

    @Test
    void clearForgetsEverything() {
        TerrainCache cache = new TerrainCache(8);
        for (int i = 0; i < 8; i++) {
            cache.put(i, new TerrainColumns());
        }
        cache.clear();
        assertEquals(0, cache.size());
        for (int i = 0; i < 8; i++) {
            assertNull(cache.get(i));
        }
        cache.put(1L, new TerrainColumns());
        assertEquals(1, cache.size());
    }

    @Test
    void rejectsAnEmptyCapacity() {
        assertThrows(IllegalArgumentException.class, () -> new TerrainCache(0));
        assertThrows(IllegalArgumentException.class, () -> new TerrainCache(-4));
    }
}
