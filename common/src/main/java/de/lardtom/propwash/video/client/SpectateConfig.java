package de.lardtom.propwash.video.client;

import de.lardtom.propwash.config.JsonConfig;

public final class SpectateConfig {
    public static final JsonConfig<SpectateConfig> CONFIG = JsonConfig.create("spectate", SpectateConfig.class, SpectateConfig::new, c -> {
    });

    public boolean showPilotOsd = true;
    public boolean showBanner = true;

    public static SpectateConfig get() {
        return CONFIG.get();
    }

    public SpectateConfig copy() {
        SpectateConfig copy = new SpectateConfig();
        copy.showPilotOsd = this.showPilotOsd;
        copy.showBanner = this.showBanner;
        return copy;
    }
}
