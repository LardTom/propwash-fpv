package de.lardtom.propwash.osd.client;

import de.lardtom.propwash.osd.OsdConfig;
import de.lardtom.propwash.osd.OsdElement;
import org.jspecify.annotations.Nullable;

public final class SpectateBannerOsdProbe {
    private SpectateBannerOsdProbe() {
    }

    public static void override(@Nullable OsdConfig config) {
        OsdHud.overrideConfig(config);
    }

    public static float @Nullable [] drawn(OsdElement element) {
        return OsdHud.drewLastFrame(element) ? OsdHud.renderer().bounds(element).clone() : null;
    }
}
