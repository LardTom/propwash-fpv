package de.lardtom.propwash.osd;

public final class OsdOutline {
    private OsdOutline() {
    }

    public static int textCopies(OsdConfig.Outline outline) {
        return switch (outline) {
            case NONE -> 0;
            case SHADOW -> 1;
            case THIN -> 4;
            case THICK -> 8;
        };
    }

    public static final float FULL_OUTLINE_OPACITY = 0.75F;

    public static float stackAlpha(float alpha, OsdConfig.Outline outline) {
        if (!(alpha > 0.0F) || outline == OsdConfig.Outline.NONE) {
            return 0.0F;
        }
        return Math.min(alpha / FULL_OUTLINE_OPACITY, 1.0F) * outline.alphaFactor();
    }

    public static float copyAlpha(float stack) {
        return stack > 0.0F ? Math.min(stack, 1.0F) : 0.0F;
    }

    public static float layerAlpha(float stack, int layers) {
        if (!(stack > 0.0F) || layers <= 0) {
            return 0.0F;
        }
        if (stack >= 1.0F) {
            return 1.0F;
        }
        return (float) (1.0 - Math.pow(1.0 - stack, 1.0 / layers));
    }

    public static float fillAlpha(float alpha, float under) {
        if (!(alpha > 0.0F)) {
            return 0.0F;
        }
        if (alpha >= 1.0F) {
            return 1.0F;
        }
        if (!(under > 0.0F)) {
            return alpha;
        }
        float clear = 1.0F - alpha;
        return 1.0F - clear * clear;
    }

    public static float coverage(float below, float above) {
        return 1.0F - (1.0F - below) * (1.0F - above);
    }

    public static float stacked(float layer, int layers) {
        return (float) (1.0 - Math.pow(1.0 - layer, layers));
    }

    public static float[] ring(float x0, float y0, float x1, float y1, float e) {
        return new float[]{
                x0 - e, y0 - e, x1 + e, y0,
                x0 - e, y1, x1 + e, y1 + e,
                x0 - e, y0, x0, y1,
                x1, y0, x1 + e, y1};
    }

    public static float[] shadow(float x0, float y0, float x1, float y1, float e) {
        return new float[]{
                x1, y0 + e, x1 + e, y1 + e,
                x0 + e, y1, x1, y1 + e};
    }
}
