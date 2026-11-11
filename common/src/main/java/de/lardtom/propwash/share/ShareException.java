package de.lardtom.propwash.share;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;

public final class ShareException extends IllegalArgumentException {
    public static final String REJECTED = "propwash.share.error.rejected";
    public static final List<String> KEYS = List.of(REJECTED, "propwash.share.error.osd_version", "propwash.share.error.osd_length",
            "propwash.share.error.name_length", "propwash.share.error.part_id", "propwash.share.error.namespace",
            "propwash.share.error.accessory_slot", "propwash.share.error.paint_slot", "propwash.share.error.tune_key",
            "propwash.share.error.too_many_namespaces", "propwash.share.error.unknown_entries", "propwash.share.error.legacy_unknown",
            "propwash.share.error.too_large");

    private final ShareStatus status;
    private final String key;
    private final Object[] args;

    public ShareException(ShareStatus status, String key, Object... args) {
        super(key + " " + Arrays.toString(args), null);
        this.status = Objects.requireNonNull(status, "status");
        this.key = Objects.requireNonNull(key, "key");
        this.args = args.clone();
    }

    public ShareStatus status() {
        return this.status;
    }

    public String key() {
        return this.key;
    }

    public Object[] args() {
        return this.args.clone();
    }
}
