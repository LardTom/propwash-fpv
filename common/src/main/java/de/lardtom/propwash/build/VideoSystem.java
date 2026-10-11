package de.lardtom.propwash.build;

import de.lardtom.propwash.api.part.VideoLink;

public enum VideoSystem {
    ANALOG,
    DIGITAL,
    CREATIVE;

    public boolean receives(VideoSystem transmitter) {
        return this == CREATIVE || this == transmitter;
    }

    public static VideoSystem of(VideoLink link) {
        return switch (link) {
            case ANALOG -> ANALOG;
            case DIGITAL -> DIGITAL;
        };
    }
}
