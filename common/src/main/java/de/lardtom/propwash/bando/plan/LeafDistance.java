package de.lardtom.propwash.bando.plan;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.function.LongPredicate;
import java.util.function.LongToIntFunction;
import java.util.function.LongUnaryOperator;

public final class LeafDistance {
    public static final int DECAY = 7;

    private LeafDistance() {
    }

    public static Set<Long> orphans(Set<Long> leaves, LongUnaryOperator[] neighbours, LongPredicate trunk) {
        Map<Long, Integer> distance = settle(leaves, neighbours, at -> trunk.test(at) ? 0 : DECAY);
        Set<Long> orphans = new HashSet<>();
        for (Map.Entry<Long, Integer> leaf : distance.entrySet()) {
            if (leaf.getValue() >= DECAY) {
                orphans.add(leaf.getKey());
            }
        }
        return orphans;
    }

    public static Map<Long, Integer> settle(Set<Long> leaves, LongUnaryOperator[] neighbours, LongToIntFunction outside) {
        Map<Long, Integer> distance = new HashMap<>();
        for (long leaf : leaves) {
            distance.put(leaf, DECAY);
        }
        boolean changed = true;
        for (int round = 0; changed && round <= DECAY * 2; round++) {
            changed = false;
            for (Map.Entry<Long, Integer> leaf : distance.entrySet()) {
                int best = leaf.getValue();
                for (LongUnaryOperator step : neighbours) {
                    long next = step.applyAsLong(leaf.getKey());
                    Integer inner = distance.get(next);
                    int value = inner != null ? inner : Math.min(DECAY, outside.applyAsInt(next));
                    best = Math.min(best, value + 1);
                }
                if (best < leaf.getValue()) {
                    leaf.setValue(best);
                    changed = true;
                }
            }
        }
        return distance;
    }
}
