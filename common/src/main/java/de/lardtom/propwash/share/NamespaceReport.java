package de.lardtom.propwash.share;

import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;

public record NamespaceReport(int index, Optional<String> namespace, int shortId, boolean hasTable, OptionalInt checksum, Optional<String> minVersion,
                              Optional<String> codeName, int linkType, Optional<String> link, Optional<String> installedVersion, Result result,
                              boolean used) {
    public enum Result {
        MATCH,
        MISMATCH,
        NEWER,
        MISSING,
        NO_TABLE;

        public String key() {
            return this.name().toLowerCase(Locale.ROOT);
        }

        public String translationKey() {
            return "propwash.share.namespace." + this.key();
        }
    }

    public NamespaceReport {
        namespace = Objects.requireNonNullElse(namespace, Optional.empty());
        checksum = Objects.requireNonNullElse(checksum, OptionalInt.empty());
        minVersion = Objects.requireNonNullElse(minVersion, Optional.empty());
        codeName = Objects.requireNonNullElse(codeName, Optional.empty());
        link = Objects.requireNonNullElse(link, Optional.empty());
        installedVersion = Objects.requireNonNullElse(installedVersion, Optional.empty());
        Objects.requireNonNull(result, "result");
    }

    public String key() {
        return this.namespace.orElse(Pw3Params.SHORT_KEY_PREFIX + this.shortId);
    }

    public boolean unknownShortId() {
        return this.namespace.isEmpty();
    }

    public boolean blocksParts() {
        return this.result == Result.MISMATCH;
    }
}
