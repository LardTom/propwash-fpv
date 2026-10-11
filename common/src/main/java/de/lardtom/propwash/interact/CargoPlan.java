package de.lardtom.propwash.interact;

import java.util.List;

public final class CargoPlan {
    private CargoPlan() {
    }

    public static double totalGrams(List<Load> cargo) {
        double sum = 0.0;
        for (Load load : cargo) {
            sum += load.grams();
        }
        return sum;
    }

    public static int takeable(List<Load> cargo, Load offered, double capacityGrams, int maxStacks) {
        if (offered.count() <= 0 || !(capacityGrams > 0.0)) {
            return 0;
        }
        double free = capacityGrams - totalGrams(cargo);
        if (free <= 0.0) {
            return 0;
        }
        double each = Math.max(offered.gramsEach(), 1.0E-3);
        long byWeight = (long) Math.floor(free / each + 1.0E-9);
        long room = 0L;
        int stacks = 0;
        for (Load load : cargo) {
            stacks++;
            if (load.key().equals(offered.key())) {
                room += Math.max(0, load.maxStack() - load.count());
            }
        }
        room += (long) Math.max(0, maxStacks - stacks) * Math.max(1, offered.maxStack());
        return (int) Math.max(0L, Math.min(offered.count(), Math.min(byWeight, room)));
    }

    public record Load(String key, double gramsEach, int count, int maxStack) {
        public double grams() {
            return this.gramsEach * this.count;
        }
    }
}
