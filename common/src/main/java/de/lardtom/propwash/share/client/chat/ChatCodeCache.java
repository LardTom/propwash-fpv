package de.lardtom.propwash.share.client.chat;

import org.jspecify.annotations.Nullable;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Function;
import java.util.function.LongSupplier;

public final class ChatCodeCache<V> {
    private final int capacity;
    private final int budget;
    private final long windowNanos;
    private final Function<String, V> loader;
    private final LongSupplier clock;
    private final LinkedHashMap<String, V> entries;
    private long windowStart;
    private int spent;
    private int loads;
    private int refused;

    public ChatCodeCache(int capacity, int budget, long windowNanos, Function<String, V> loader, LongSupplier clock) {
        if (capacity < 1 || budget < 1 || windowNanos < 1) {
            throw new IllegalArgumentException("Größen müssen positiv sein");
        }
        this.capacity = capacity;
        this.budget = budget;
        this.windowNanos = windowNanos;
        this.loader = loader;
        this.clock = clock;
        this.entries = new LinkedHashMap<>(16, 0.75F, true) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<String, V> eldest) {
                return this.size() > ChatCodeCache.this.capacity;
            }
        };
        this.windowStart = clock.getAsLong();
    }

    public synchronized @Nullable V get(String code, boolean limited) {
        V cached = this.entries.get(code);
        if (cached != null) {
            return cached;
        }
        if (limited && !this.spend()) {
            this.refused++;
            return null;
        }
        V value = this.loader.apply(code);
        this.loads++;
        if (value != null) {
            this.entries.put(code, value);
        }
        return value;
    }

    public synchronized @Nullable V peek(String code) {
        return this.entries.get(code);
    }

    private boolean spend() {
        long now = this.clock.getAsLong();
        if (now - this.windowStart >= this.windowNanos || now < this.windowStart) {
            this.windowStart = now;
            this.spent = 0;
        }
        if (this.spent >= this.budget) {
            return false;
        }
        this.spent++;
        return true;
    }

    public synchronized void clear() {
        this.entries.clear();
        this.spent = 0;
        this.windowStart = this.clock.getAsLong();
    }

    public synchronized int size() {
        return this.entries.size();
    }

    public synchronized int loads() {
        return this.loads;
    }

    public synchronized int refused() {
        return this.refused;
    }

    public int capacity() {
        return this.capacity;
    }
}
