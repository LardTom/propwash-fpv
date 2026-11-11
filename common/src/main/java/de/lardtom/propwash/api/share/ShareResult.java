package de.lardtom.propwash.api.share;

import de.lardtom.propwash.api.build.DroneData;
import net.minecraft.resources.Identifier;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;

public record ShareResult(Status status, Optional<Format> format, Optional<DroneData> data, Optional<String> osdJson,
                          List<NamespaceInfo> namespaces, List<Identifier> unknownParts, Optional<String> convertedCode) {
    public enum Status {
        OK,
        EMPTY,
        INVALID_PREFIX,
        BAD_CHARACTER,
        TOO_LONG,
        TRUNCATED,
        CHECKSUM,
        UNSUPPORTED_VERSION,
        TOO_LARGE,
        CORRUPT,
        OTHER_CODE,
        NOT_CONVERTIBLE;

        public boolean ok() {
            return this == OK;
        }
    }

    public enum Format {
        PW1,
        PW2,
        PW3
    }

    public enum Check {
        MATCH,
        MISMATCH,
        NEWER,
        MISSING,
        NO_TABLE
    }

    public record NamespaceInfo(String namespace, OptionalInt shortId, Optional<String> installedVersion, Optional<String> minVersion,
                                Check check, Optional<String> name, Optional<String> link, boolean trustedLink) {
        public NamespaceInfo {
            Objects.requireNonNull(namespace, "namespace");
            shortId = Objects.requireNonNullElse(shortId, OptionalInt.empty());
            installedVersion = Objects.requireNonNullElse(installedVersion, Optional.<String>empty());
            minVersion = Objects.requireNonNullElse(minVersion, Optional.<String>empty());
            Objects.requireNonNull(check, "check");
            name = Objects.requireNonNullElse(name, Optional.<String>empty());
            link = Objects.requireNonNullElse(link, Optional.<String>empty());
        }
    }

    public ShareResult {
        Objects.requireNonNull(status, "status");
        format = Objects.requireNonNullElse(format, Optional.<Format>empty());
        data = Objects.requireNonNullElse(data, Optional.<DroneData>empty());
        osdJson = Objects.requireNonNullElse(osdJson, Optional.<String>empty());
        namespaces = List.copyOf(Objects.requireNonNullElse(namespaces, List.<NamespaceInfo>of()));
        unknownParts = List.copyOf(Objects.requireNonNullElse(unknownParts, List.<Identifier>of()));
        convertedCode = Objects.requireNonNullElse(convertedCode, Optional.<String>empty());
    }

    public static ShareResult failed(Status status) {
        return new ShareResult(status, Optional.empty(), Optional.empty(), Optional.empty(), List.of(), List.of(), Optional.empty());
    }

    public boolean ok() {
        return this.status.ok();
    }
}
