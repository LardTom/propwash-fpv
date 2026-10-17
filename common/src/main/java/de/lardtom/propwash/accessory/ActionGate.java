package de.lardtom.propwash.accessory;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class ActionGate {
    public static final int BURST = 8;
    public static final int PER_SECOND = 20;

    private final Map<UUID, Bucket> buckets = new HashMap<>();

    public synchronized boolean allow(UUID player, long tick) {
        Bucket bucket = this.buckets.computeIfAbsent(player, id -> new Bucket(BURST, tick));
        if (tick < bucket.tick) {
            bucket.tokens = BURST;
            bucket.tick = tick;
        }
        double refill = (tick - bucket.tick) * (PER_SECOND / 20.0);
        bucket.tokens = Math.min(BURST, bucket.tokens + refill);
        bucket.tick = tick;
        if (bucket.tokens < 1.0) {
            return false;
        }
        bucket.tokens -= 1.0;
        return true;
    }

    public synchronized void forget(UUID player) {
        this.buckets.remove(player);
    }

    public synchronized void clear() {
        this.buckets.clear();
    }

    private static final class Bucket {
        private double tokens;
        private long tick;

        private Bucket(double tokens, long tick) {
            this.tokens = tokens;
            this.tick = tick;
        }
    }
}
