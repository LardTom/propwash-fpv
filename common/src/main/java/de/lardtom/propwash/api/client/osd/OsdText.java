package de.lardtom.propwash.api.client.osd;

import java.util.Objects;

public record OsdText(String text, OsdTone tone) {
    public OsdText {
        Objects.requireNonNull(text, "text");
        Objects.requireNonNull(tone, "tone");
    }

    public static OsdText of(String text, OsdTone tone) {
        return new OsdText(text, tone);
    }

    public static OsdText of(String text) {
        return new OsdText(text, OsdTone.NORMAL);
    }
}
