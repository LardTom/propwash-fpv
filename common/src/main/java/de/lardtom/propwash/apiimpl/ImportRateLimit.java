package de.lardtom.propwash.apiimpl;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

public final class ImportRateLimit {
    public static final int REQUESTS_PER_WINDOW = 2;
    public static final long WINDOW_TICKS = 20L;
    private static final int PRUNE_THRESHOLD = 64;

    private final int limit;
    private final long window;
    private final Map<UUID, long[]> recent = new HashMap<>();

    public ImportRateLimit() {
        this(REQUESTS_PER_WINDOW, WINDOW_TICKS);
    }

    public ImportRateLimit(int limit, long windowTicks) {
        if (limit < 1 || windowTicks < 1) {
            throw new IllegalArgumentException("Ratenbegrenzung braucht mindestens eine Anfrage und einen Tick");
        }
        this.limit = limit;
        this.window = windowTicks;
    }

    public synchronized boolean tryAcquire(UUID player, long tick) {
        if (this.recent.size() > PRUNE_THRESHOLD) {
            this.prune(tick);
        }
        long[] times = this.recent.computeIfAbsent(player, id -> newSlots(this.limit));
        int oldest = 0;
        for (int i = 0; i < times.length; i++) {
            if (times[i] < times[oldest]) {
                oldest = i;
            }
        }
        if (tick - times[oldest] < this.window) {
            return false;
        }
        times[oldest] = tick;
        return true;
    }

    public synchronized void forget(UUID player) {
        this.recent.remove(player);
    }

    public synchronized int tracked() {
        return this.recent.size();
    }

    private void prune(long tick) {
        Iterator<long[]> it = this.recent.values().iterator();
        while (it.hasNext()) {
            long newest = Long.MIN_VALUE;
            for (long time : it.next()) {
                newest = Math.max(newest, time);
            }
            if (tick - newest >= this.window) {
                it.remove();
            }
        }
    }

    private static long[] newSlots(int limit) {
        long[] slots = new long[limit];
        Arrays.fill(slots, Long.MIN_VALUE / 2);
        return slots;
    }
}
