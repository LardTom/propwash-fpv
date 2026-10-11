package de.lardtom.propwash.api.client;

import java.util.Objects;

public record OsdLayoutSpec(int version, String json) {
    public static final int CURRENT_VERSION = 1;
    public static final int MAX_JSON_LENGTH = 8192;

    public OsdLayoutSpec {
        Objects.requireNonNull(json, "json");
        if (version < 1) {
            throw new IllegalArgumentException("OSD-Layout-Version muss mindestens 1 sein: " + version);
        }
        if (json.length() > MAX_JSON_LENGTH) {
            throw new IllegalArgumentException("OSD-Layout zu groß: " + json.length() + " Zeichen");
        }
    }
}
