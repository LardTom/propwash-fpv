package de.lardtom.propwash.video.record;

import java.util.Locale;

public enum EncoderChoice {
    AUTO,
    BUILTIN;

    public String key() {
        return this.name().toLowerCase(Locale.ROOT);
    }

    public String translationKey() {
        return "propwash.video.record.encoder." + this.key();
    }
}
