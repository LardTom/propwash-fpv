package de.lardtom.propwash.video;

import de.lardtom.propwash.api.part.VideoDefinition;
import de.lardtom.propwash.build.VideoSystem;
import de.lardtom.propwash.build.VideoType;

public final class FovMath {
    public static final int MIN_HORIZONTAL_DEGREES = 60;
    public static final int MAX_HORIZONTAL_DEGREES = 160;
    public static final int DEFAULT_ANALOG_DEGREES = 120;
    public static final int DEFAULT_DIGITAL_DEGREES = 130;
    public static final int DEFAULT_CREATIVE_DEGREES = 120;

    private FovMath() {
    }

    public static float verticalFromHorizontal(double horizontalDegrees, double aspect) {
        double horizontal = Math.toRadians(Math.min(Math.max(horizontalDegrees, 1.0), 179.0));
        double ratio = aspect > 1.0E-3 && Double.isFinite(aspect) ? aspect : 16.0 / 9.0;
        double vertical = 2.0 * Math.atan(Math.tan(horizontal / 2.0) / ratio);
        return (float) Math.toDegrees(vertical);
    }

    public static float verticalFromHorizontal(double horizontalDegrees, ImageArea area) {
        return verticalFromHorizontal(horizontalDegrees, area.spanAspect());
    }

    public static float verticalWithin(double verticalDegrees, double heightShare) {
        if (!Double.isFinite(verticalDegrees) || !(heightShare > 0.0) || !(heightShare < 1.0)) {
            return (float) verticalDegrees;
        }
        double half = Math.toRadians(Math.min(Math.max(verticalDegrees, 1.0), 179.0)) / 2.0;
        return (float) Math.toDegrees(2.0 * Math.atan(Math.tan(half) * heightShare));
    }

    public static float horizontalFromVertical(double verticalDegrees, double aspect) {
        double vertical = Math.toRadians(Math.min(Math.max(verticalDegrees, 1.0), 179.0));
        double ratio = aspect > 1.0E-3 && Double.isFinite(aspect) ? aspect : 16.0 / 9.0;
        return (float) Math.toDegrees(2.0 * Math.atan(Math.tan(vertical / 2.0) * ratio));
    }

    public static int defaultHorizontalDegrees(VideoSystem system) {
        if (system == null) {
            return DEFAULT_ANALOG_DEGREES;
        }
        return switch (system) {
            case ANALOG -> DEFAULT_ANALOG_DEGREES;
            case DIGITAL -> DEFAULT_DIGITAL_DEGREES;
            case CREATIVE -> DEFAULT_CREATIVE_DEGREES;
        };
    }

    public static double declaredHorizontalDegrees(VideoDefinition video) {
        if (video == null) {
            return DEFAULT_ANALOG_DEGREES;
        }
        return video.cameraFovDeg().orElse(defaultHorizontalDegrees(VideoSystem.of(video.link())));
    }

    public static double cameraHorizontalDegrees(VideoDefinition video) {
        double fov = declaredHorizontalDegrees(video);
        return Double.isFinite(fov) ? Math.min(Math.max(fov, MIN_HORIZONTAL_DEGREES), MAX_HORIZONTAL_DEGREES) : DEFAULT_ANALOG_DEGREES;
    }

    public static double cameraHorizontalDegrees(VideoType video) {
        return video == null ? DEFAULT_ANALOG_DEGREES : cameraHorizontalDegrees(video.definition());
    }

    public static boolean fromModule(VideoType video) {
        return video != null && video.definition().cameraFovDeg().isPresent();
    }
}
