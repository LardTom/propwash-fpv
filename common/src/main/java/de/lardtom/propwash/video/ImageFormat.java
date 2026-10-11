package de.lardtom.propwash.video;

import de.lardtom.propwash.build.VideoSystem;
import org.jspecify.annotations.Nullable;

public enum ImageFormat {
    AUTO("auto"),
    WIDE("wide"),
    FOUR_THREE("four_three");

    public static final double FOUR_THREE_ASPECT = 4.0 / 3.0;

    private final String key;

    ImageFormat(String key) {
        this.key = key;
    }

    public ImageFormat resolve(@Nullable VideoSystem transmitter) {
        if (this != AUTO) {
            return this;
        }
        return transmitter == VideoSystem.ANALOG ? FOUR_THREE : WIDE;
    }

    public boolean boxed() {
        return this == FOUR_THREE;
    }

    public String key() {
        return this.key;
    }

    public String translationKey() {
        return "propwash.video.format." + this.key;
    }
}
