package de.lardtom.propwash.video.record.path;

public final class FlashbackBudget {
    public static final int VERTEX_LIMIT = 1 << 16;
    public static final int RESERVE = 6_000;
    public static final int USABLE = VERTEX_LIMIT - 1 - RESERVE;
    public static final float MIN_ASSUMED_SCALE = 2.0F;
    public static final float MIN_SCALE = 0.125F;
    public static final float MAX_SCALE = 32.0F;
    public static final int KEYFRAME_SIZE = 10;
    public static final int HOLD_KEYFRAME_VERTICES = 32;
    public static final float CIRCLE_MAX_ERROR = 0.30F;
    public static final int ARC_TABLE_SIZE = 48;
    public static final double CAUTION_FILL = 0.75;

    private FlashbackBudget() {
    }

    public static int circleSegments(int radius) {
        if (radius <= 0) {
            return ARC_TABLE_SIZE;
        }
        float r = radius;
        float error = Math.min(CIRCLE_MAX_ERROR, r);
        int segments = (int) Math.ceil(Math.PI / (float) Math.acos(1.0F - error / r));
        segments = (segments + 1) / 2 * 2;
        return Math.max(4, Math.min(512, segments));
    }

    public static int circlePoints(int radius) {
        int step = Math.max(1, Math.min(ARC_TABLE_SIZE / 4, ARC_TABLE_SIZE / circleSegments(radius)));
        return ARC_TABLE_SIZE / step + (ARC_TABLE_SIZE % step > 0 ? 1 : 0);
    }

    public static int circleVertices(int radius) {
        return 2 * circlePoints(radius);
    }

    public static float quantize(float scale) {
        if (!Float.isFinite(scale)) {
            return 1.0F;
        }
        float q = (int) (scale * 16.0F) / 16.0F;
        return Math.max(MIN_SCALE, Math.min(MAX_SCALE, q));
    }

    public static int keyframeRadius(float uiScale) {
        return (int) (KEYFRAME_SIZE * uiScale);
    }

    public static int keyframeVertices(float uiScale) {
        return circleVertices(keyframeRadius(uiScale));
    }

    public static float assumedScale(float estimate) {
        if (!Float.isFinite(estimate)) {
            return MIN_ASSUMED_SCALE;
        }
        return Math.max(MIN_ASSUMED_SCALE, Math.min(MAX_SCALE, estimate));
    }

    public static int vertices(int cameraKeyframes, int holdKeyframes, float uiScale) {
        return Math.max(0, cameraKeyframes) * keyframeVertices(uiScale) + Math.max(0, holdKeyframes) * HOLD_KEYFRAME_VERTICES;
    }

    public static int maxCameraKeyframes(float uiScale, int holdKeyframes) {
        int free = USABLE - Math.max(0, holdKeyframes) * HOLD_KEYFRAME_VERTICES;
        return Math.max(2, free / keyframeVertices(uiScale));
    }

    public static double fill(int cameraKeyframes, int holdKeyframes, float uiScale) {
        return vertices(cameraKeyframes, holdKeyframes, uiScale) / (double) USABLE;
    }

    public static boolean fits(int cameraKeyframes, int holdKeyframes, float uiScale) {
        return cameraKeyframes <= maxCameraKeyframes(uiScale, holdKeyframes);
    }

    public static double secondsThatFit(int keyframes, double seconds, float uiScale, int holdKeyframes) {
        if (keyframes <= 1 || !(seconds > 0.0)) {
            return Double.NaN;
        }
        double perSecond = (keyframes - 1) / seconds;
        return maxCameraKeyframes(uiScale, holdKeyframes) / perSecond;
    }
}
