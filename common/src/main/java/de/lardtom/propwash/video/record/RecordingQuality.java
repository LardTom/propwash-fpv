package de.lardtom.propwash.video.record;

import java.util.Locale;

public enum RecordingQuality {
    HIGH(20, 1.5, 0.20),
    STANDARD(23, 1.0, 0.12),
    SMALL(27, 0.5, 0.065);

    public static final RecordingQuality DEFAULT = STANDARD;

    private final int qp;
    private final double capFactor;
    private final double typicalBitsPerPixel;

    RecordingQuality(int qp, double capFactor, double typicalBitsPerPixel) {
        this.qp = qp;
        this.capFactor = capFactor;
        this.typicalBitsPerPixel = typicalBitsPerPixel;
    }

    public int qp() {
        return this.qp;
    }

    public long maxBitrate(RecordingFormat format) {
        return Math.round(format.bitrate() * this.capFactor / 100_000.0) * 100_000L;
    }

    public long typicalBitrate(RecordingFormat format) {
        double pixelsPerSecond = (double) format.width() * format.height() * format.fps();
        double perFrame = format.fps() > 30 ? 1.0 : 1.25;
        return Math.min(this.maxBitrate(format), Math.round(pixelsPerSecond * this.typicalBitsPerPixel * perFrame));
    }

    public int typicalMegabytesPerMinute(RecordingFormat format) {
        return megabytesPerMinute(this.typicalBitrate(format));
    }

    public int maxMegabytesPerMinute(RecordingFormat format) {
        return megabytesPerMinute(this.maxBitrate(format));
    }

    public String key() {
        return this.name().toLowerCase(Locale.ROOT);
    }

    public String translationKey() {
        return "propwash.video.record.quality." + this.key();
    }

    static int megabytesPerMinute(long bitrate) {
        return (int) Math.round(bitrate * 60.0 / 8.0 / 1_000_000.0 / 5.0) * 5;
    }
}
