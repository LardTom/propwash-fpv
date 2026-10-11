package de.lardtom.propwash.video.record.h264;

public record EncoderSettings(int threads, int slices, long bitrate, int gop, int minQp, int maxQp, boolean deblock, int quality,
                              double adaptiveQuant) {
    public static final int DEFAULT_QUALITY = 23;
    public static final double DEFAULT_AQ = 0.8;

    public EncoderSettings {
        threads = Math.max(1, threads);
        slices = Math.max(1, slices);
        bitrate = Math.max(100_000L, bitrate);
        gop = Math.max(1, gop);
        minQp = Math.max(10, Math.min(51, minQp));
        maxQp = Math.max(minQp, Math.min(51, maxQp));
        quality = Math.max(minQp, Math.min(maxQp, quality));
        adaptiveQuant = Math.max(0.0, Math.min(2.0, adaptiveQuant));
    }

    public EncoderSettings(int threads, int slices, long bitrate, int gop, int minQp, int maxQp, boolean deblock) {
        this(threads, slices, bitrate, gop, minQp, maxQp, deblock, DEFAULT_QUALITY, DEFAULT_AQ);
    }

    public static EncoderSettings of(int threads, long bitrate, int fps) {
        return of(threads, bitrate, fps, DEFAULT_QUALITY);
    }

    public static EncoderSettings of(int threads, long maxBitrate, int fps, int quality) {
        return new EncoderSettings(threads, Math.max(1, threads) * 2, maxBitrate, Math.max(1, fps) * 2, 12, 46, true, quality, DEFAULT_AQ);
    }

    public EncoderSettings withQuality(int quality, double adaptiveQuant) {
        return new EncoderSettings(this.threads, this.slices, this.bitrate, this.gop, this.minQp, this.maxQp, this.deblock, quality,
                adaptiveQuant);
    }
}
