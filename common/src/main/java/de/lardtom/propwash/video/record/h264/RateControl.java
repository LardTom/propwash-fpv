package de.lardtom.propwash.video.record.h264;

final class RateControl {
    static final int KEYFRAME_BONUS = 2;
    private static final double MAX_BOOST = 18.0;
    private static final double TARGET_FILL = 0.9;

    private final int minQp;
    private final int maxQp;
    private final int quality;
    private final double capPerFrame;
    private final double buffer;
    private final double[] lastBits = new double[2];
    private final int[] lastQp = new int[2];
    private double fill;
    private double boost;
    private int current;

    RateControl(long maxBitrate, int fps, int quality, int minQp, int maxQp) {
        int rate = Math.max(1, fps);
        this.capPerFrame = maxBitrate / (double) rate;
        this.buffer = maxBitrate;
        this.quality = quality;
        this.minQp = minQp;
        this.maxQp = maxQp;
        this.current = quality;
    }

    int qp() {
        return this.current;
    }

    double fill() {
        return this.fill / this.buffer;
    }

    int qpFor(boolean keyframe) {
        int type = keyframe ? 1 : 0;
        int qp = this.clamp((int) Math.round(this.quality + this.boost) - (keyframe ? KEYFRAME_BONUS : 0));
        if (this.lastBits[type] > 0.0) {
            double room = Math.max(this.capPerFrame * 0.5, this.buffer * TARGET_FILL - this.fill + this.capPerFrame);
            while (qp < this.maxQp && this.estimate(type, qp) > room) {
                qp++;
            }
        }
        this.current = qp;
        return qp;
    }

    void update(boolean keyframe, int qp, long bits) {
        int type = keyframe ? 1 : 0;
        this.lastBits[type] = Math.max(1.0, bits);
        this.lastQp[type] = qp;
        this.fill = Math.max(0.0, this.fill + bits - this.capPerFrame);
        double level = this.fill / this.buffer;
        if (level > 0.5) {
            this.boost = Math.min(MAX_BOOST, this.boost + (level - 0.5) * 4.0 + 0.25);
        } else if (level > 0.25) {
            this.boost = Math.min(MAX_BOOST, this.boost + 0.125);
        } else if (level < 0.1) {
            this.boost = Math.max(0.0, this.boost - 0.25);
        }
    }

    void repeated() {
        this.fill = Math.max(0.0, this.fill - this.capPerFrame);
    }

    private double estimate(int type, int qp) {
        return this.lastBits[type] * Math.pow(2.0, (this.lastQp[type] - qp) / 6.0);
    }

    private int clamp(int qp) {
        return Math.max(this.minQp, Math.min(this.maxQp, qp));
    }
}
