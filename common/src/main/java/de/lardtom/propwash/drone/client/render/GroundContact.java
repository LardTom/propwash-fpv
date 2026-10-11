package de.lardtom.propwash.drone.client.render;

public final class GroundContact {
    public static final float MIN_OFFSET = 5.0E-4F;

    private static volatile boolean enabled = true;

    private GroundContact() {
    }

    public static boolean enabled() {
        return enabled;
    }

    public static void setEnabled(boolean value) {
        enabled = value;
    }

    public static float lowest(float comAboveFoot, float lowestBelowCom) {
        float lowest = comAboveFoot + lowestBelowCom;
        return Float.isFinite(lowest) ? lowest : 0.0F;
    }

    public static float reach(float lowestAboveFoot, double rise) {
        float distance = Math.abs(lowestAboveFoot);
        if (!(distance > MIN_OFFSET) || !Double.isFinite(rise)) {
            return 0.0F;
        }
        double reach = distance - rise;
        return reach > MIN_OFFSET ? (float) reach : 0.0F;
    }

    public static float offset(float lowestAboveFoot, double rise, double gap) {
        if (reach(lowestAboveFoot, rise) <= 0.0F || !Double.isFinite(gap)) {
            return 0.0F;
        }
        double clearance = Math.max(0.0, Math.max(0.0, gap) + rise);
        double distance = Math.abs(lowestAboveFoot) - clearance;
        if (!(distance > 0.0)) {
            return 0.0F;
        }
        return lowestAboveFoot < 0.0F ? (float) distance : (float) -distance;
    }
}
