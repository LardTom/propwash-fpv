package de.lardtom.propwash.guide.scene;

public enum Ease {
    LINEAR,
    IN_OUT,
    OUT,
    IN,
    BACK;

    private static final float OVERSHOOT = 1.70158F;

    public float apply(float t) {
        float x = clamp01(t);
        return switch (this) {
            case LINEAR -> x;
            case IN_OUT -> x < 0.5F ? 4.0F * x * x * x : 1.0F - (float) Math.pow(-2.0F * x + 2.0F, 3.0) / 2.0F;
            case OUT -> 1.0F - (1.0F - x) * (1.0F - x) * (1.0F - x);
            case IN -> x * x * x;
            case BACK -> {
                float c3 = OVERSHOOT + 1.0F;
                float u = x - 1.0F;
                yield 1.0F + c3 * u * u * u + OVERSHOOT * u * u;
            }
        };
    }

    public static float clamp01(float t) {
        if (!(t > 0.0F)) {
            return 0.0F;
        }
        return Math.min(t, 1.0F);
    }
}
