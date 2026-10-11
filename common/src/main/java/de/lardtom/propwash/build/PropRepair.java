package de.lardtom.propwash.build;

public final class PropRepair {
    public static final float BONUS = 0.05F;
    public static final int BONUS_STEPS = Math.round(BONUS * PartWear.STEPS);

    private PropRepair() {
    }

    public static boolean repairable(float condition) {
        return Float.isFinite(condition) && PartWear.quantize(condition) < PartWear.NEW;
    }

    public static boolean combinable(float first, float second) {
        return repairable(first) && repairable(second);
    }

    public static float combine(float first, float second) {
        int steps = PartWear.toSteps(first) + PartWear.toSteps(second) + BONUS_STEPS;
        return PartWear.fromSteps(Math.min(PartWear.STEPS, steps));
    }
}
