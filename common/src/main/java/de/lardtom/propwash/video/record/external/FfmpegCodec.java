package de.lardtom.propwash.video.record.external;

import de.lardtom.propwash.video.record.RecordingFormat;
import de.lardtom.propwash.video.record.RecordingQuality;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public enum FfmpegCodec {
    VIDEOTOOLBOX("h264_videotoolbox", "VideoToolbox", Platform.MAC),
    NVENC("h264_nvenc", "NVENC", Platform.WINDOWS, Platform.LINUX),
    QSV("h264_qsv", "Quick Sync", Platform.WINDOWS, Platform.LINUX),
    AMF("h264_amf", "AMD AMF", Platform.WINDOWS),
    VAAPI("h264_vaapi", "VA-API", Platform.LINUX),
    MEDIA_FOUNDATION("h264_mf", "Media Foundation", Platform.WINDOWS);

    public static final String VAAPI_DEVICE = "/dev/dri/renderD128";

    private final String encoder;
    private final String label;
    private final List<Platform> platforms;

    FfmpegCodec(String encoder, String label, Platform... platforms) {
        this.encoder = encoder;
        this.label = label;
        this.platforms = List.of(platforms);
    }

    public String encoder() {
        return this.encoder;
    }

    public String label() {
        return this.label;
    }

    public boolean runsOn(Platform platform) {
        return this.platforms.contains(platform);
    }

    public static List<FfmpegCodec> candidates(Platform platform) {
        List<FfmpegCodec> out = new ArrayList<>();
        for (FfmpegCodec codec : values()) {
            if (codec.runsOn(platform)) {
                out.add(codec);
            }
        }
        return out;
    }

    public int variants() {
        return 2;
    }

    public List<String> command(String ffmpeg, RecordingFormat format, RecordingQuality quality, int variant) {
        long cap = quality.maxBitrate(format);
        long target = Math.min(cap, Math.round(quality.typicalBitrate(format) * 1.5));
        String t = Long.toString(target);
        String c = Long.toString(cap);
        List<String> args = new ArrayList<>(List.of(ffmpeg, "-hide_banner", "-nostats", "-loglevel", "error"));
        if (this == VAAPI) {
            args.addAll(List.of("-vaapi_device", VAAPI_DEVICE));
        }
        args.addAll(List.of("-f", "rawvideo", "-pix_fmt", "yuv420p", "-video_size", format.width() + "x" + format.height(),
                "-framerate", Integer.toString(format.fps()), "-color_range", "tv", "-colorspace", "bt709", "-color_primaries", "bt709",
                "-color_trc", "bt709", "-i", "pipe:0", "-an"));
        if (this == VAAPI) {
            args.addAll(List.of("-vf", "format=nv12,hwupload"));
        }
        args.addAll(List.of("-c:v", this.encoder));
        boolean first = variant == 0;
        switch (this) {
            case VIDEOTOOLBOX -> args.addAll(first ? List.of("-profile:v", "main", "-b:v", t, "-maxrate", c, "-bufsize", c) : List.of("-b:v", t));
            case NVENC -> args.addAll(first
                    ? List.of("-preset", "p4", "-tune", "hq", "-profile:v", "main", "-rc", "vbr", "-cq", Integer.toString(quality.qp() + 1),
                    "-b:v", t, "-maxrate", c, "-bufsize", c)
                    : List.of("-preset", "fast", "-profile:v", "main", "-b:v", t, "-maxrate", c, "-bufsize", c));
            case QSV -> args.addAll(first ? List.of("-preset", "veryfast", "-profile:v", "main", "-b:v", t, "-maxrate", c, "-bufsize", c)
                    : List.of("-b:v", t));
            case AMF -> args.addAll(first ? List.of("-usage", "transcoding", "-quality", "balanced", "-profile:v", "main", "-rc", "vbr_peak",
                    "-b:v", t, "-maxrate", c, "-bufsize", c) : List.of("-b:v", t));
            case VAAPI -> args.addAll(first ? List.of("-profile:v", "main", "-b:v", t, "-maxrate", c) : List.of("-b:v", t));
            case MEDIA_FOUNDATION -> args.addAll(first ? List.of("-hw_encoding", "1", "-b:v", t) : List.of("-b:v", t));
        }
        args.addAll(List.of("-g", Integer.toString(format.fps() * 2), "-bf", "0", "-color_range", "tv", "-colorspace", "bt709",
                "-color_primaries", "bt709", "-color_trc", "bt709", "-f", "h264", "pipe:1"));
        return args;
    }

    public enum Platform {
        WINDOWS,
        MAC,
        LINUX,
        OTHER;

        public static Platform current() {
            String name = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
            if (name.contains("win")) {
                return WINDOWS;
            }
            if (name.contains("mac") || name.contains("darwin")) {
                return MAC;
            }
            if (name.contains("nux") || name.contains("nix") || name.contains("bsd")) {
                return LINUX;
            }
            return OTHER;
        }
    }
}
