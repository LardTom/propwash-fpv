package de.lardtom.propwash.interact;

public final class ItemWeights {
    public static final double SINGLE_GRAMS = 150.0;
    public static final double FEW_GRAMS = 40.0;
    public static final double BLOCK_GRAMS = 50.0;
    public static final double SMALL_GRAMS = 10.0;

    private ItemWeights() {
    }

    public static double gramsEach(int maxStackSize, boolean block, double knownGrams) {
        if (Double.isFinite(knownGrams) && knownGrams > 0.0) {
            return knownGrams;
        }
        if (maxStackSize <= 1) {
            return SINGLE_GRAMS;
        }
        if (maxStackSize <= 16) {
            return FEW_GRAMS;
        }
        return block ? BLOCK_GRAMS : SMALL_GRAMS;
    }
}
