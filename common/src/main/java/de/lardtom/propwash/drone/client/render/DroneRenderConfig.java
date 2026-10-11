package de.lardtom.propwash.drone.client.render;

import de.lardtom.propwash.config.JsonConfig;

public final class DroneRenderConfig {
    public static final int VERSION = 2;
    public static final float DEFAULT_VISUAL_SCALE = 1.0F;
    public static final float LEGACY_DEFAULT_VISUAL_SCALE = 1.75F;
    public static final float MIN_VISUAL_SCALE = 1.0F;
    public static final float MAX_VISUAL_SCALE = 3.0F;

    public static final JsonConfig<DroneRenderConfig> CONFIG = JsonConfig.create("drone_render", DroneRenderConfig.class,
            DroneRenderConfig::defaults, DroneRenderConfig::validate);

    public int version;

    public boolean sizeNotice;

    public float visualScale = DEFAULT_VISUAL_SCALE;

    public float propBlurThreshold = 0.12F;

    public boolean armLeds = true;

    public boolean shadow = true;

    public int renderDistance = 128;

    public boolean fpvProps = true;

    public boolean fpvDucts = true;

    public static DroneRenderConfig get() {
        return CONFIG.get();
    }

    public static DroneRenderConfig defaults() {
        DroneRenderConfig config = new DroneRenderConfig();
        config.version = VERSION;
        return config;
    }

    public DroneRenderConfig copy() {
        DroneRenderConfig copy = new DroneRenderConfig();
        copy.version = this.version;
        copy.sizeNotice = this.sizeNotice;
        copy.visualScale = this.visualScale;
        copy.propBlurThreshold = this.propBlurThreshold;
        copy.armLeds = this.armLeds;
        copy.shadow = this.shadow;
        copy.renderDistance = this.renderDistance;
        copy.fpvProps = this.fpvProps;
        copy.fpvDucts = this.fpvDucts;
        return copy;
    }

    static void validate(DroneRenderConfig config) {
        migrate(config);
        config.visualScale = clampOr(config.visualScale, MIN_VISUAL_SCALE, MAX_VISUAL_SCALE, DEFAULT_VISUAL_SCALE);
        config.propBlurThreshold = clampOr(config.propBlurThreshold, 0.0F, 1.0F, 0.12F);
        config.renderDistance = Math.max(16, Math.min(512, config.renderDistance));
    }

    static void migrate(DroneRenderConfig config) {
        if (config.version >= VERSION) {
            return;
        }
        if (config.visualScale == LEGACY_DEFAULT_VISUAL_SCALE) {
            config.visualScale = DEFAULT_VISUAL_SCALE;
            config.sizeNotice = true;
        }
        config.version = VERSION;
    }

    private static float clampOr(float value, float min, float max, float fallback) {
        if (!Float.isFinite(value)) {
            return fallback;
        }
        return Math.max(min, Math.min(max, value));
    }
}
