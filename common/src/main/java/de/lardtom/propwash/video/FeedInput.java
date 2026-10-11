package de.lardtom.propwash.video;

public final class FeedInput {
    public FeedLook look = FeedLook.CLEAN;
    public boolean connected;
    public boolean snowOnLoss;
    public float quality;
    public float imageQuality = 1.0F;
    public long baseLatencyNanos;
    public float strength = 0.7F;
    public boolean reduceFlicker = true;
    public float extraNoise;
    public float cameraCondition = 1.0F;
    public float multipath;

    public FeedInput set(FeedLook look, boolean connected, float quality, long baseLatencyNanos) {
        this.look = look;
        this.connected = connected;
        this.quality = quality;
        this.baseLatencyNanos = baseLatencyNanos;
        return this;
    }
}
