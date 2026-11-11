package de.lardtom.propwash.api.part;

import java.util.Locale;
import java.util.Optional;

public enum AntennaCap {
    WHIP("whip"),
    STUBBY("stubby"),
    LOLLIPOP("lollipop"),
    PAGODA("pagoda"),
    PATCH("patch");

    private final String key;

    AntennaCap(String key) {
        this.key = key;
    }

    public String key() {
        return this.key;
    }

    public static Optional<AntennaCap> byKey(String key) {
        if (key == null) {
            return Optional.empty();
        }
        String wanted = key.toLowerCase(Locale.ROOT);
        for (AntennaCap cap : values()) {
            if (cap.key.equals(wanted)) {
                return Optional.of(cap);
            }
        }
        return Optional.empty();
    }
}
