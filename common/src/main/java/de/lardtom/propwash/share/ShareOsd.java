package de.lardtom.propwash.share;

import java.util.Objects;

public record ShareOsd(int version, String json) {
    public ShareOsd {
        Objects.requireNonNull(json, "json");
        if (version < 1) {
            throw new ShareException(ShareStatus.CORRUPT, "propwash.share.error.osd_version", version);
        }
        if (json.length() > Pw3Params.MAX_OSD_CHARS) {
            throw new ShareException(ShareStatus.TOO_LARGE, "propwash.share.error.osd_length", json.length(), Pw3Params.MAX_OSD_CHARS);
        }
    }
}
