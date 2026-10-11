package de.lardtom.propwash.video.client;

import de.lardtom.propwash.config.JsonConfig;

public final class VideoSettings {
    public static final JsonConfig<VideoConfig> CONFIG =
            JsonConfig.create("video", VideoConfig.class, VideoConfig::new, VideoConfig::validate);

    private VideoSettings() {
    }

    public static VideoConfig get() {
        return CONFIG.get();
    }
}
