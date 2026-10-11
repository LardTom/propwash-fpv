package de.lardtom.propwash.drone;

import de.lardtom.propwash.api.part.FrameDefinition;
import de.lardtom.propwash.api.part.FrameRole;
import de.lardtom.propwash.build.DroneBuild;
import de.lardtom.propwash.build.FrameType;

import java.util.Map;
import java.util.TreeMap;

public final class CameraMount {
    private CameraMount() {
    }

    public static Range range(FrameType frame) {
        return range(frame.definition());
    }

    public static Range range(FrameDefinition frame) {
        float standard = TuneMapping.clampUptilt((float) frame.geometry().cameraTiltDeg());
        Range role = roleRange(frame.role());
        return new Range(Math.min(role.minDeg(), standard), Math.max(role.maxDeg(), standard), standard);
    }

    public static Range roleRange(FrameRole role) {
        return switch (role) {
            case WHOOP -> new Range(10.0F, 35.0F, 15.0F);
            case TOOTHPICK -> new Range(10.0F, 40.0F, 25.0F);
            case CINEWHOOP -> new Range(0.0F, 35.0F, 15.0F);
            case FREESTYLE -> new Range(0.0F, 50.0F, 25.0F);
            case RACE -> new Range(15.0F, 70.0F, 50.0F);
            case LONG_RANGE -> new Range(0.0F, 40.0F, 20.0F);
            case CINELIFTER -> new Range(0.0F, 30.0F, 10.0F);
            case X_CLASS -> new Range(0.0F, 50.0F, 30.0F);
        };
    }

    public static Range range(DroneBuild build) {
        Range frame = range(build.frame());
        if (!build.isCreative()) {
            return frame;
        }
        return new Range(TuneMapping.CAMERA_UPTILT_MIN, TuneMapping.CAMERA_UPTILT_MAX, frame.defaultDeg());
    }

    public static float defaultUptilt(FrameType frame) {
        return range(frame).defaultDeg();
    }

    public static float uptilt(DroneBuild build, Map<String, Float> tune) {
        Range range = range(build);
        Float value = tune.get(TuneMapping.CAMERA_UPTILT);
        return value != null && Float.isFinite(value) ? range.clamp(value) : range.defaultDeg();
    }

    public static boolean custom(DroneBuild build, Map<String, Float> tune) {
        return uptilt(build, tune) != defaultUptilt(build.frame());
    }

    public static Map<String, Float> sanitize(Map<String, Float> tune, FrameType frame) {
        return sanitize(tune, range(frame));
    }

    public static Map<String, Float> sanitize(Map<String, Float> tune, DroneBuild build) {
        return sanitize(tune, range(build));
    }

    public static Map<String, Float> withUptilt(Map<String, Float> tune, DroneBuild build, float degrees) {
        Map<String, Float> out = new TreeMap<>(tune);
        out.put(TuneMapping.CAMERA_UPTILT, degrees);
        return sanitize(out, build);
    }

    private static Map<String, Float> sanitize(Map<String, Float> tune, Range range) {
        Float value = tune.get(TuneMapping.CAMERA_UPTILT);
        if (value == null) {
            return tune;
        }
        Map<String, Float> out = new TreeMap<>(tune);
        float clean = Float.isFinite(value) ? range.clamp(value) : range.defaultDeg();
        if (clean == range.defaultDeg()) {
            out.remove(TuneMapping.CAMERA_UPTILT);
        } else {
            out.put(TuneMapping.CAMERA_UPTILT, clean);
        }
        return out;
    }

    public static Map<String, Float> withUptilt(Map<String, Float> tune, FrameType frame, float degrees) {
        Map<String, Float> out = new TreeMap<>(tune);
        out.put(TuneMapping.CAMERA_UPTILT, degrees);
        return sanitize(out, frame);
    }

    public record Range(float minDeg, float maxDeg, float defaultDeg) {
        public float clamp(float value) {
            if (!Float.isFinite(value)) {
                return this.defaultDeg;
            }
            return Math.max(this.minDeg, Math.min(this.maxDeg, value));
        }

        public boolean contains(float value) {
            return value >= this.minDeg && value <= this.maxDeg;
        }
    }
}
