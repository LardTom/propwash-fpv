package de.lardtom.propwash.video.vtx;

import java.util.Arrays;
import java.util.List;
import java.util.TreeSet;

public final class VtxTable {
    public static final int DEFAULT_RATED_MW = 200;
    public static final int MAX_MW = 10000;
    public static final int MAX_LEVELS = 8;
    public static final double ANALOG_SUPPLY_PER_RF = 4.3;
    public static final double DIGITAL_SUPPLY_PER_RF = 2.5;
    public static final double MIN_SUPPLY_W = 0.25;
    public static final double MIN_MASS_G = 1.0;
    static final int[] ANALOG_SMALL_LADDER = {25, 100, 200, 400};
    static final int[] ANALOG_LADDER = {25, 200, 400, 800, 1600, 2000};
    static final int[] DIGITAL_LADDER = {25, 100, 400, 700, 1200};
    static final int SMALL_ANALOG_LIMIT_MW = 400;

    private final boolean digital;
    private final int[] levels;
    private final int ratedIndex;
    private final double ratedSupplyW;
    private final double massGrams;

    private VtxTable(boolean digital, int[] levels, int ratedIndex, double ratedSupplyW, double massGrams) {
        this.digital = digital;
        this.levels = levels;
        this.ratedIndex = ratedIndex;
        this.ratedSupplyW = ratedSupplyW;
        this.massGrams = massGrams;
    }

    public static VtxTable of(boolean digital, int ratedMw, List<Integer> declared, double ratedSupplyW, double massGrams) {
        int rated = ratedMw > 0 ? Math.min(ratedMw, MAX_MW) : DEFAULT_RATED_MW;
        TreeSet<Integer> set = new TreeSet<>();
        if (declared != null) {
            for (Integer value : declared) {
                if (value != null && value > 0 && value <= MAX_MW) {
                    set.add(value);
                }
            }
        }
        if (set.isEmpty()) {
            for (int value : ladder(digital, rated)) {
                if (value <= rated) {
                    set.add(value);
                }
            }
        }
        set.add(rated);
        while (set.size() > MAX_LEVELS) {
            set.remove(set.first() == rated ? set.higher(rated) : set.first());
        }
        int[] levels = set.stream().mapToInt(Integer::intValue).toArray();
        int index = Arrays.binarySearch(levels, rated);
        double supply = Double.isFinite(ratedSupplyW) && ratedSupplyW > 0.0 ? ratedSupplyW : MIN_SUPPLY_W;
        double mass = Double.isFinite(massGrams) ? Math.max(MIN_MASS_G, massGrams) : MIN_MASS_G;
        return new VtxTable(digital, levels, index, supply, mass);
    }

    static int[] ladder(boolean digital, int rated) {
        if (digital) {
            return DIGITAL_LADDER;
        }
        return rated <= SMALL_ANALOG_LIMIT_MW ? ANALOG_SMALL_LADDER : ANALOG_LADDER;
    }

    public boolean digital() {
        return this.digital;
    }

    public int count() {
        return this.levels.length;
    }

    public int levelMw(int index) {
        return this.levels[this.clamp(index)];
    }

    public int ratedIndex() {
        return this.ratedIndex;
    }

    public int ratedMw() {
        return this.levels[this.ratedIndex];
    }

    public int maxIndex() {
        return this.levels.length - 1;
    }

    public int minMw() {
        return this.levels[0];
    }

    public int maxMw() {
        return this.levels[this.levels.length - 1];
    }

    public double ratedSupplyW() {
        return this.ratedSupplyW;
    }

    public double massGrams() {
        return this.massGrams;
    }

    public boolean supportsAuto() {
        return this.levels.length > 1;
    }

    public int clamp(int index) {
        return Math.max(0, Math.min(this.levels.length - 1, index));
    }

    public int nearestIndex(double milliwatts) {
        if (!(milliwatts > 0.0) || !Double.isFinite(milliwatts)) {
            return this.ratedIndex;
        }
        int best = 0;
        double bestDistance = Double.POSITIVE_INFINITY;
        double target = Math.log(milliwatts);
        for (int i = 0; i < this.levels.length; i++) {
            double distance = Math.abs(Math.log(this.levels[i]) - target);
            if (distance < bestDistance - 1.0E-9) {
                best = i;
                bestDistance = distance;
            }
        }
        return best;
    }

    public double gainDb(int index) {
        int i = this.clamp(index);
        if (i == this.ratedIndex) {
            return 0.0;
        }
        return 10.0 * Math.log10((double) this.levels[i] / this.levels[this.ratedIndex]);
    }

    public double reachFactor(int index) {
        int i = this.clamp(index);
        if (i == this.ratedIndex) {
            return 1.0;
        }
        return Math.sqrt((double) this.levels[i] / this.levels[this.ratedIndex]);
    }

    public double maxReachFactor() {
        return Math.max(1.0, this.reachFactor(this.maxIndex()));
    }

    public double supplyW(int index) {
        int i = this.clamp(index);
        if (i == this.ratedIndex) {
            return this.ratedSupplyW;
        }
        double slope = this.digital ? DIGITAL_SUPPLY_PER_RF : ANALOG_SUPPLY_PER_RF;
        double value = this.ratedSupplyW + slope * (this.levels[i] - this.levels[this.ratedIndex]) / 1000.0;
        return Math.max(Math.min(MIN_SUPPLY_W, this.ratedSupplyW), value);
    }

    public double extraW(int index) {
        int i = this.clamp(index);
        if (i == this.ratedIndex) {
            return 0.0;
        }
        return this.supplyW(i) - this.ratedSupplyW;
    }

    public double heatW(int index) {
        int i = this.clamp(index);
        return Math.max(0.0, this.supplyW(i) - this.levels[i] / 1000.0);
    }

    public int[] levels() {
        return this.levels.clone();
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof VtxTable t && t.digital == this.digital && t.ratedIndex == this.ratedIndex
                && Arrays.equals(t.levels, this.levels) && Double.compare(t.ratedSupplyW, this.ratedSupplyW) == 0
                && Double.compare(t.massGrams, this.massGrams) == 0;
    }

    @Override
    public int hashCode() {
        return Arrays.hashCode(this.levels) * 31 + this.ratedIndex + (this.digital ? 7 : 0) + Double.hashCode(this.ratedSupplyW);
    }

    @Override
    public String toString() {
        return (this.digital ? "digital" : "analog") + Arrays.toString(this.levels) + "@" + this.levels[this.ratedIndex];
    }
}
