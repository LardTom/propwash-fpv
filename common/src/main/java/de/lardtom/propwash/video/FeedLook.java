package de.lardtom.propwash.video;

import de.lardtom.propwash.build.VideoSystem;

public enum FeedLook {
    CLEAN(0),
    ANALOG(1),
    DIGITAL(2);

    private final int shaderMode;

    FeedLook(int shaderMode) {
        this.shaderMode = shaderMode;
    }

    public int shaderMode() {
        return this.shaderMode;
    }

    public static FeedLook of(VideoSystem system) {
        return switch (system) {
            case ANALOG -> ANALOG;
            case DIGITAL -> DIGITAL;
            case CREATIVE -> CLEAN;
        };
    }
}
