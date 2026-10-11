package de.lardtom.propwash.api.part;

import java.util.Locale;
import java.util.Optional;

public enum VideoLink {
    ANALOG("analog"),
    DIGITAL("digital");

    private final String key;

    VideoLink(String key) {
        this.key = key;
    }

    public String key() {
        return this.key;
    }

    public static Optional<VideoLink> byKey(String key) {
        if (key == null) {
            return Optional.empty();
        }
        String wanted = key.toLowerCase(Locale.ROOT);
        for (VideoLink link : values()) {
            if (link.key.equals(wanted)) {
                return Optional.of(link);
            }
        }
        return Optional.empty();
    }
}
