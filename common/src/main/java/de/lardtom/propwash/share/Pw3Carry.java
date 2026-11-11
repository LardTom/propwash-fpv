package de.lardtom.propwash.share;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

public record Pw3Carry(Map<String, Head> namespaces) {
    public static final Pw3Carry EMPTY = new Pw3Carry(Map.of());

    public Pw3Carry {
        namespaces = Map.copyOf(Objects.requireNonNullElse(namespaces, Map.of()));
    }

    public record Head(int shortId, Optional<String> namespace, boolean hasTable, int checksum, Optional<ShareVersion> version,
                       Optional<Info> info, List<String> refs) {
        public Head {
            namespace = Objects.requireNonNullElse(namespace, Optional.empty());
            version = Objects.requireNonNullElse(version, Optional.empty());
            info = Objects.requireNonNullElse(info, Optional.empty());
            refs = List.copyOf(Objects.requireNonNullElse(refs, List.of()));
        }
    }

    public record Info(String name, int linkType, Optional<String> link) {
        public Info {
            Objects.requireNonNull(name, "name");
            link = Objects.requireNonNullElse(link, Optional.empty());
        }
    }

    public Optional<Head> head(String key) {
        return Optional.ofNullable(this.namespaces.get(key));
    }

    public boolean isEmpty() {
        return this.namespaces.isEmpty();
    }
}
