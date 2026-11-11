package de.lardtom.propwash.osd;

import org.jspecify.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.BiPredicate;

public final class SpectatorCount {
    public static final int MAX = 9999;

    private SpectatorCount() {
    }

    public static <D> void add(Map<D, Integer> counts, UUID player, @Nullable D spectating, @Nullable D linked, BiPredicate<D, UUID> excluded) {
        if (spectating != null && !excluded.test(spectating, player)) {
            counts.merge(spectating, 1, Integer::sum);
        }
        if (linked != null && linked != spectating && !excluded.test(linked, player)) {
            counts.merge(linked, 1, Integer::sum);
        }
    }

    public static <D> int of(Map<D, Integer> counts, D drone) {
        Integer count = counts.get(drone);
        return count == null ? 0 : clamp(count);
    }

    public static <D> @Nullable D shown(@Nullable D flown, @Nullable D spectating, @Nullable D linked) {
        if (flown != null) {
            return flown;
        }
        return spectating != null ? spectating : linked;
    }

    public static int clamp(int count) {
        return Math.clamp(count, 0, MAX);
    }

    public static final class Outbox {
        private final Map<UUID, Long> sent = new HashMap<>();

        public boolean offer(UUID recipient, int entityId, int count) {
            int value = clamp(count);
            Long previous = this.sent.get(recipient);
            if (value == 0 && (previous == null || entity(previous) != entityId)) {
                return false;
            }
            long key = key(entityId, value);
            if (previous != null && previous == key) {
                return false;
            }
            this.sent.put(recipient, key);
            return true;
        }

        public void forget(UUID recipient) {
            this.sent.remove(recipient);
        }

        public void clear() {
            this.sent.clear();
        }

        public int size() {
            return this.sent.size();
        }

        private static long key(int entityId, int count) {
            return ((long) entityId << 32) | (count & 0xFFFFFFFFL);
        }

        private static int entity(long key) {
            return (int) (key >>> 32);
        }
    }
}
