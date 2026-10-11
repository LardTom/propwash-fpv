package de.lardtom.propwash.video.client;

import de.lardtom.propwash.build.VideoSystem;
import de.lardtom.propwash.build.VideoType;
import de.lardtom.propwash.video.CameraShake;
import de.lardtom.propwash.video.FovMath;
import de.lardtom.propwash.video.ImageFormat;
import org.jspecify.annotations.Nullable;

public final class VideoConfig {
    public static final int DEFAULT_FOV_CREATIVE = FovMath.DEFAULT_CREATIVE_DEGREES;
    public static final int MAX_CREATIVE_LATENCY_MS = 500;
    public static final int MAX_CREATIVE_RANGE_BLOCKS = 2000;

    public boolean creativeFovFromModule = true;
    public int fovCreative = DEFAULT_FOV_CREATIVE;
    public float effectStrength = 0.7F;
    public boolean reduceFlicker = true;
    public EffectRenderer renderer = EffectRenderer.AUTO;
    public boolean creativeLatencyFromModule = true;
    public int creativeLatencyMs = 0;
    public int creativeRangeBlocks = 0;
    public float creativeNoise = 0.0F;
    public boolean creativeObstruction = false;
    public ImageFormat imageFormat = ImageFormat.WIDE;
    public boolean ledLighting = true;
    public float cameraShake = CameraShake.DEFAULT_INTENSITY;

    public double horizontalFov(@Nullable VideoSystem goggles, @Nullable VideoType camera) {
        if (goggles == VideoSystem.CREATIVE && !this.creativeFovFromModule) {
            return this.fovCreative;
        }
        return FovMath.cameraHorizontalDegrees(camera);
    }

    public int latencyMs(@Nullable VideoSystem goggles, int moduleLatencyMs) {
        return goggles == VideoSystem.CREATIVE && !this.creativeLatencyFromModule ? this.creativeLatencyMs : moduleLatencyMs;
    }

    public float extraNoise(@Nullable VideoSystem goggles) {
        return goggles == VideoSystem.CREATIVE ? this.creativeNoise : 0.0F;
    }

    public boolean obstructs(@Nullable VideoSystem goggles) {
        return goggles != VideoSystem.CREATIVE || this.creativeObstruction;
    }

    public double rangeBlocks(@Nullable VideoSystem goggles, double moduleRangeBlocks) {
        if (goggles != VideoSystem.CREATIVE) {
            return moduleRangeBlocks;
        }
        return this.creativeRangeBlocks > 0 ? this.creativeRangeBlocks : Double.POSITIVE_INFINITY;
    }

    public void keepCreative(VideoConfig from) {
        this.creativeFovFromModule = from.creativeFovFromModule;
        this.fovCreative = from.fovCreative;
        this.creativeLatencyFromModule = from.creativeLatencyFromModule;
        this.creativeLatencyMs = from.creativeLatencyMs;
        this.creativeRangeBlocks = from.creativeRangeBlocks;
        this.creativeNoise = from.creativeNoise;
        this.creativeObstruction = from.creativeObstruction;
    }

    public VideoConfig copy() {
        VideoConfig copy = new VideoConfig();
        copy.creativeFovFromModule = this.creativeFovFromModule;
        copy.fovCreative = this.fovCreative;
        copy.effectStrength = this.effectStrength;
        copy.reduceFlicker = this.reduceFlicker;
        copy.renderer = this.renderer;
        copy.creativeLatencyFromModule = this.creativeLatencyFromModule;
        copy.creativeLatencyMs = this.creativeLatencyMs;
        copy.creativeRangeBlocks = this.creativeRangeBlocks;
        copy.creativeNoise = this.creativeNoise;
        copy.creativeObstruction = this.creativeObstruction;
        copy.imageFormat = this.imageFormat;
        copy.ledLighting = this.ledLighting;
        copy.cameraShake = this.cameraShake;
        return copy;
    }

    public void validate() {
        this.fovCreative = clampFov(this.fovCreative);
        this.effectStrength = clampUnit(this.effectStrength, 0.7F);
        if (this.renderer == null) {
            this.renderer = EffectRenderer.AUTO;
        }
        this.creativeLatencyMs = Math.min(Math.max(this.creativeLatencyMs, 0), MAX_CREATIVE_LATENCY_MS);
        this.creativeRangeBlocks = Math.min(Math.max(this.creativeRangeBlocks, 0), MAX_CREATIVE_RANGE_BLOCKS);
        this.creativeNoise = clampUnit(this.creativeNoise, 0.0F);
        if (this.imageFormat == null) {
            this.imageFormat = ImageFormat.WIDE;
        }
        this.cameraShake = Float.isFinite(this.cameraShake) ? Math.min(Math.max(this.cameraShake, 0.0F), CameraShake.MAX_INTENSITY)
                : CameraShake.DEFAULT_INTENSITY;
    }

    private static int clampFov(int degrees) {
        return Math.min(Math.max(degrees, FovMath.MIN_HORIZONTAL_DEGREES), FovMath.MAX_HORIZONTAL_DEGREES);
    }

    private static float clampUnit(float value, float fallback) {
        if (!Float.isFinite(value)) {
            return fallback;
        }
        return Math.min(Math.max(value, 0.0F), 1.0F);
    }

    public enum EffectRenderer {
        AUTO("auto"),
        OVERLAY("overlay");

        private final String key;

        EffectRenderer(String key) {
            this.key = key;
        }

        public String translationKey() {
            return "propwash.video.config.renderer." + this.key;
        }
    }
}
