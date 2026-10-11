package de.lardtom.propwash.sim;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Function;

public final class BoundedCache<K, V> {
    private final int capacity;
    private final LinkedHashMap<K, V> entries;

    public BoundedCache(int capacity) {
        this.capacity = Math.max(1, capacity);
        this.entries = new LinkedHashMap<>(16, 0.75F, true) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<K, V> eldest) {
                return this.size() > BoundedCache.this.capacity;
            }
        };
    }

    public V get(K key, Function<? super K, ? extends V> compute) {
        synchronized (this.entries) {
            V cached = this.entries.get(key);
            if (cached != null) {
                return cached;
            }
        }
        V value = compute.apply(key);
        synchronized (this.entries) {
            V raced = this.entries.get(key);
            if (raced != null) {
                return raced;
            }
            this.entries.put(key, value);
        }
        return value;
    }

    public int size() {
        synchronized (this.entries) {
            return this.entries.size();
        }
    }

    public void clear() {
        synchronized (this.entries) {
            this.entries.clear();
        }
    }
}
