package de.lardtom.propwash.build;

import de.lardtom.propwash.api.part.AirframeGeometry;
import de.lardtom.propwash.api.part.AnchorSpec;
import de.lardtom.propwash.api.part.AntennaCap;
import de.lardtom.propwash.api.part.FrameDefinition;
import de.lardtom.propwash.api.part.Vec3Mm;
import de.lardtom.propwash.api.part.VideoAntenna;
import de.lardtom.propwash.api.part.VideoDefinition;
import de.lardtom.propwash.api.part.VideoLink;

public final class AntennaRules {
    public static final int LONG_RANGE_BLOCKS = 700;
    public static final double WHOOP_MAX_GRAMS = 6.0;
    public static final double HD_MIN_QUALITY = 0.95;
    public static final int RACE_MAX_LATENCY_MS = 16;
    public static final double RACE_MIN_MM = 45.0;
    public static final double RACE_MAX_MM = 60.0;
    public static final double MAX_LENGTH_OF_WHEELBASE = 1.2;
    public static final double PAIR_LEAN_DEG = 15.0;
    public static final double MIN_PAIR_LEAN_DEG = 5.0;
    public static final double NARROW_SPREAD_WHEELBASE_MM = 100.0;
    public static final double FULL_SPREAD_WHEELBASE_MM = 200.0;
    public static final double WHOOP_REAR_OF_WHEELBASE = 0.18;
    public static final double REAR_OF_WHEELBASE = 0.22;
    public static final double LARGE_WHEELBASE_MM = 300.0;
    public static final double LARGE_REAR_GROWTH = 0.065;
    public static final double BEHIND_PLATE_MM = 12.0;
    public static final double ABOVE_ELECTRONICS = 2.4;
    public static final double ABOVE_DUCTED_CAMERA_MM = 6.0;

    public enum Kind {
        WHOOP("whoop"),
        RACE("race"),
        LITE("lite"),
        HD("hd"),
        LONG_RANGE("long_range");

        private final String key;

        Kind(String key) {
            this.key = key;
        }

        public String key() {
            return this.key;
        }
    }

    private AntennaRules() {
    }

    public static VideoAntenna antenna(VideoDefinition video) {
        return video.antenna().orElseGet(() -> derived(video));
    }

    public static VideoAntenna antenna(VideoDefinition video, FrameDefinition frame) {
        VideoAntenna antenna = antenna(video);
        double max = maxLengthMm(frame);
        return antenna.lengthMm() <= max ? antenna : new VideoAntenna(antenna.count(), max, antenna.cap());
    }

    public static double pairLeanDeg(FrameDefinition frame) {
        if (frame.ducted() || frame.wheelbaseMm() >= FULL_SPREAD_WHEELBASE_MM) {
            return PAIR_LEAN_DEG;
        }
        double share = (frame.wheelbaseMm() - NARROW_SPREAD_WHEELBASE_MM) / (FULL_SPREAD_WHEELBASE_MM - NARROW_SPREAD_WHEELBASE_MM);
        return Math.round(Math.max(MIN_PAIR_LEAN_DEG, PAIR_LEAN_DEG * share) * 10.0) / 10.0;
    }

    public static double maxLengthMm(FrameDefinition frame) {
        double max = Math.round(MAX_LENGTH_OF_WHEELBASE * frame.wheelbaseMm() * 2.0) / 2.0;
        return clamp(max, VideoAntenna.MIN_LENGTH_MM, VideoAntenna.MAX_LENGTH_MM);
    }

    public static Kind kind(VideoDefinition video) {
        if (video.rangeBlocks() >= LONG_RANGE_BLOCKS) {
            return Kind.LONG_RANGE;
        }
        if (video.basics().massGrams() <= WHOOP_MAX_GRAMS) {
            return Kind.WHOOP;
        }
        if (video.link() == VideoLink.DIGITAL && video.imageQuality() >= HD_MIN_QUALITY) {
            return Kind.HD;
        }
        if (video.link() == VideoLink.ANALOG || video.latencyMs() <= RACE_MAX_LATENCY_MS) {
            return Kind.RACE;
        }
        return Kind.LITE;
    }

    public static VideoAntenna derived(VideoDefinition video) {
        int range = video.rangeBlocks();
        return switch (kind(video)) {
            case WHOOP -> {
                int power = video.vtxPowerMw() > 0 ? video.vtxPowerMw() : 25;
                yield new VideoAntenna(1, 30.0 + 5.0 * clamp((power - 25) / 175.0, 0.0, 1.0), AntennaCap.WHIP);
            }
            case RACE -> new VideoAntenna(1, clamp(50.0 + (range - 380) / 14.0, RACE_MIN_MM, RACE_MAX_MM),
                    video.link() == VideoLink.ANALOG ? AntennaCap.LOLLIPOP : AntennaCap.STUBBY);
            case LITE -> new VideoAntenna(1, 60.0, AntennaCap.LOLLIPOP);
            case HD -> new VideoAntenna(2, 90.0 + clamp((range - 520) / 13.0, 0.0, 10.0), AntennaCap.LOLLIPOP);
            case LONG_RANGE -> new VideoAntenna(video.link() == VideoLink.DIGITAL ? 2 : 1, clamp(120.0 + (range - LONG_RANGE_BLOCKS) * 0.15, 120.0, 150.0),
                    AntennaCap.PAGODA);
        };
    }

    public static AnchorSpec anchor(FrameDefinition frame) {
        return frame.geometry().antennaAnchor().orElseGet(() -> fallbackAnchor(frame));
    }

    public static boolean explicitAnchor(FrameDefinition frame) {
        return frame.geometry().antennaAnchor().isPresent();
    }

    public static AnchorSpec fallbackAnchor(FrameDefinition frame) {
        AirframeGeometry geometry = frame.geometry();
        double wheelbase = frame.wheelbaseMm();
        boolean whoop = !geometry.batteryOnTop() && frame.ducted();
        double z;
        if (whoop) {
            z = WHOOP_REAR_OF_WHEELBASE * wheelbase;
        } else if (wheelbase <= LARGE_WHEELBASE_MM) {
            z = REAR_OF_WHEELBASE * wheelbase + BEHIND_PLATE_MM;
        } else {
            z = REAR_OF_WHEELBASE * LARGE_WHEELBASE_MM + LARGE_REAR_GROWTH * (wheelbase - LARGE_WHEELBASE_MM) + BEHIND_PLATE_MM;
        }
        double y;
        if (geometry.batteryOnTop()) {
            y = geometry.batteryPlateMm();
        } else {
            double camera = geometry.cameraMm().map(Vec3Mm::y).orElse(0.0) + (frame.ducted() ? ABOVE_DUCTED_CAMERA_MM : 0.0);
            y = Math.max(ABOVE_ELECTRONICS * geometry.electronicsMm(), camera);
        }
        return new AnchorSpec(0.0, round(y), round(z), AirframeGeometry.DEFAULT_ANTENNA_TILT, 0.0);
    }

    private static double round(double mm) {
        return Math.round(mm * 10.0) / 10.0;
    }

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }
}
