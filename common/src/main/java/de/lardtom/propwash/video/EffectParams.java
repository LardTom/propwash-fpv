package de.lardtom.propwash.video;

public final class EffectParams {
    private static final float EPSILON = 1.0E-3F;

    public float mode;
    public float timeSeconds;
    public float seed;
    public float blockSeed;
    public float link = 1.0F;
    public float brightness = 1.0F;
    public float noSignal;
    public float noSignalBlack;
    public float bleedPx;
    public float scanlines;
    public float vignette;
    public float noiseContrast = 1.0F;
    public float glitch;
    public float glitchSeed;
    public float sparkle;
    public float lineJitter;
    public float desaturation;
    public float chromaBands;
    public float bandPhase;
    public float chromaNoise;
    public float noise;
    public float syncShift;
    public float shiftTop;
    public float shiftBottom = 1.0F;
    public float roll;
    public float resolution = 1.0F;
    public float blockiness;
    public float blockPx;
    public float blurPx;
    public float posterize;
    public float osdShift;
    public boolean frameAdvanced = true;
    public boolean continuous = true;
    public long latencyNanos;
    public long displayedCaptureNanos;

    public void clearEffects() {
        this.mode = 0.0F;
        this.link = 1.0F;
        this.brightness = 1.0F;
        this.noSignal = 0.0F;
        this.noSignalBlack = 0.0F;
        this.bleedPx = 0.0F;
        this.scanlines = 0.0F;
        this.vignette = 0.0F;
        this.noiseContrast = 1.0F;
        this.glitch = 0.0F;
        this.glitchSeed = 0.0F;
        this.sparkle = 0.0F;
        this.lineJitter = 0.0F;
        this.desaturation = 0.0F;
        this.chromaBands = 0.0F;
        this.bandPhase = 0.0F;
        this.chromaNoise = 0.0F;
        this.noise = 0.0F;
        this.syncShift = 0.0F;
        this.shiftTop = 0.0F;
        this.shiftBottom = 1.0F;
        this.roll = 0.0F;
        this.resolution = 1.0F;
        this.blockiness = 0.0F;
        this.blockPx = 0.0F;
        this.blurPx = 0.0F;
        this.posterize = 0.0F;
        this.osdShift = 0.0F;
    }

    public boolean visuallyClean() {
        return this.noise <= EPSILON
                && this.glitch <= EPSILON
                && this.sparkle <= EPSILON
                && this.lineJitter <= 1.0E-5F
                && this.bleedPx <= 0.25F
                && this.desaturation <= EPSILON
                && this.chromaBands <= EPSILON
                && this.chromaNoise <= EPSILON
                && Math.abs(this.syncShift) <= 1.0E-4F
                && this.vignette <= EPSILON
                && Math.abs(this.roll) <= EPSILON
                && this.scanlines <= EPSILON
                && this.resolution >= 0.999F
                && this.blockiness <= EPSILON
                && this.blurPx <= 0.25F
                && this.posterize <= 1.0F
                && this.noSignal <= EPSILON
                && Math.abs(this.brightness - 1.0F) <= EPSILON;
    }

    public boolean needsPass() {
        return !this.frameAdvanced || !this.visuallyClean();
    }

    public boolean blackout() {
        return this.noSignal >= 0.999F && this.noSignalBlack > 0.5F;
    }
}
