package de.lardtom.propwash.trick;

import de.lardtom.propwash.build.DroneBuild;
import de.lardtom.propwash.build.FrameType;
import org.jspecify.annotations.Nullable;

import java.util.Locale;

public enum TrickMode {
    AUTO,
    ON,
    OFF;

    private final String key = this.name().toLowerCase(Locale.ROOT);

    public String translationKey() {
        return "propwash.trick.mode." + this.key;
    }

    public boolean enabledFor(@Nullable DroneBuild build) {
        return switch (this) {
            case ON -> true;
            case OFF -> false;
            case AUTO -> build != null && freestyleFrame(build.frame());
        };
    }

    public static boolean freestyleFrame(@Nullable FrameType frame) {
        return frame != null && frame.definition().role().tricksByDefault();
    }
}
