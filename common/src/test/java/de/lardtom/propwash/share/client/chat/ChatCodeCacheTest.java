package de.lardtom.propwash.share.client.chat;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ChatCodeCacheTest {
    private final AtomicLong clock = new AtomicLong(1_000L);
    private final List<String> loaded = new ArrayList<>();

    private ChatCodeCache<String> cache(int capacity, int budget) {
        return new ChatCodeCache<>(capacity, budget, 100L, code -> {
            this.loaded.add(code);
            return "decoded " + code;
        }, this.clock::get);
    }

    @Test
    void decodesEachCodeOnce() {
        ChatCodeCache<String> cache = this.cache(4, 10);
        for (int frame = 0; frame < 60; frame++) {
            assertEquals("decoded PW3-a", cache.get("PW3-a", true));
            assertEquals("decoded PW3-b", cache.get("PW3-b", false));
        }
        assertEquals(List.of("PW3-a", "PW3-b"), this.loaded);
        assertEquals(2, cache.loads());
    }

    @Test
    void smallCacheDropsTheOldestCode() {
        ChatCodeCache<String> cache = this.cache(2, 10);
        cache.get("a", true);
        cache.get("b", true);
        cache.get("a", true);
        cache.get("c", true);
        assertEquals(2, cache.size());
        assertNotNull(cache.peek("a"));
        assertNull(cache.peek("b"));
        cache.get("b", true);
        assertEquals(List.of("a", "b", "c", "b"), this.loaded);
    }

    @Test
    void newCodesAreRateLimited() {
        ChatCodeCache<String> cache = this.cache(16, 3);
        assertNotNull(cache.get("1", true));
        assertNotNull(cache.get("2", true));
        assertNotNull(cache.get("3", true));
        assertNull(cache.get("4", true));
        assertNotNull(cache.get("1", true));
        assertEquals(1, cache.refused());
        assertNotNull(cache.get("5", false));
        this.clock.addAndGet(100L);
        assertNotNull(cache.get("4", true));
        assertEquals(List.of("1", "2", "3", "5", "4"), this.loaded);
    }

    @Test
    void sizesMustBePositive() {
        assertThrows(IllegalArgumentException.class, () -> this.cache(0, 1));
        assertThrows(IllegalArgumentException.class, () -> this.cache(1, 0));
    }
}
