package de.lardtom.propwash.share;

import de.lardtom.propwash.api.part.PartKind;
import net.minecraft.resources.Identifier;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;

public record ShareTable(String namespace, OptionalInt shortId, Map<PartKind, List<Entry>> parts) {
    public ShareTable {
        Objects.requireNonNull(namespace, "namespace");
        shortId = Objects.requireNonNullElse(shortId, OptionalInt.empty());
        EnumMap<PartKind, List<Entry>> copy = new EnumMap<>(PartKind.class);
        for (Map.Entry<PartKind, List<Entry>> entry : Objects.requireNonNullElse(parts, Map.<PartKind, List<Entry>>of()).entrySet()) {
            copy.put(entry.getKey(), List.copyOf(entry.getValue()));
        }
        parts = java.util.Collections.unmodifiableMap(copy);
    }

    public record Entry(String id, String since, Optional<String> deprecated, Optional<Identifier> replacedBy, Optional<Identifier> fallback,
                        Map<String, String> names) {
        public Entry {
            Objects.requireNonNull(id, "id");
            Objects.requireNonNull(since, "since");
            deprecated = Objects.requireNonNullElse(deprecated, Optional.empty());
            replacedBy = Objects.requireNonNullElse(replacedBy, Optional.empty());
            fallback = Objects.requireNonNullElse(fallback, Optional.empty());
            names = Map.copyOf(Objects.requireNonNullElse(names, Map.of()));
        }

        public static Entry of(String id, String since) {
            return new Entry(id, since, Optional.empty(), Optional.empty(), Optional.empty(), Map.of());
        }
    }

    public record Located(PartKind kind, int number, Entry entry) {
    }

    public List<Entry> entries(PartKind kind) {
        return this.parts.getOrDefault(kind, List.of());
    }

    public int size(PartKind kind) {
        return this.entries(kind).size();
    }

    public int number(PartKind kind, String path) {
        List<Entry> entries = this.entries(kind);
        for (int i = 0; i < entries.size(); i++) {
            if (entries.get(i).id().equals(path)) {
                return i + 1;
            }
        }
        return 0;
    }

    public Optional<Entry> entry(PartKind kind, int number) {
        List<Entry> entries = this.entries(kind);
        return number >= 1 && number <= entries.size() ? Optional.of(entries.get(number - 1)) : Optional.empty();
    }

    public Optional<Located> find(Identifier id) {
        if (!id.getNamespace().equals(this.namespace)) {
            return Optional.empty();
        }
        for (PartKind kind : Pw3Params.KINDS) {
            int number = this.number(kind, id.getPath());
            if (number > 0) {
                return Optional.of(new Located(kind, number, this.entries(kind).get(number - 1)));
            }
        }
        return Optional.empty();
    }

    public Identifier id(Entry entry) {
        return Identifier.fromNamespaceAndPath(this.namespace, entry.id());
    }

    public int totalEntries() {
        int total = 0;
        for (List<Entry> entries : this.parts.values()) {
            total += entries.size();
        }
        return total;
    }
}
