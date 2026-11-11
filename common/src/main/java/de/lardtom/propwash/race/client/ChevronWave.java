package de.lardtom.propwash.race.client;

public final class ChevronWave {
    public static final int PERIOD = 3;
    public static final float SPEED = 0.05F;
    public static final float MIN_GLOW = 0.35F;

    private ChevronWave() {
    }

    public static float brightness(int stepsLeft, float age) {
        float wave = age * SPEED + stepsLeft / (float) PERIOD;
        wave -= (float) Math.floor(wave);
        float fall = 1.0F - wave;
        return MIN_GLOW + (1.0F - MIN_GLOW) * fall * fall;
    }

    public static int approach(int fromTarget) {
        return fromTarget;
    }

    public static int leaving(int fromElement, int count) {
        return count - 1 - fromElement;
    }
}
