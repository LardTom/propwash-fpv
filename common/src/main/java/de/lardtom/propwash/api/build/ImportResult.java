package de.lardtom.propwash.api.build;

import net.minecraft.resources.Identifier;

import java.util.Collections;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

public record ImportResult(ImportStatus status, Set<ImportLayer> applied, List<Identifier> missingParts, Map<String, Integer> missingDyes) {
    public ImportResult {
        Objects.requireNonNull(status, "status");
        EnumSet<ImportLayer> copy = EnumSet.noneOf(ImportLayer.class);
        copy.addAll(Objects.requireNonNullElse(applied, Set.of()));
        applied = Collections.unmodifiableSet(copy);
        missingParts = List.copyOf(Objects.requireNonNullElse(missingParts, List.of()));
        missingDyes = Collections.unmodifiableMap(new LinkedHashMap<>(Objects.requireNonNullElse(missingDyes, Map.of())));
    }

    public static ImportResult of(ImportStatus status) {
        return new ImportResult(status, Set.of(), List.of(), Map.of());
    }

    public static ImportResult applied(Set<ImportLayer> layers) {
        return new ImportResult(ImportStatus.APPLIED, layers, List.of(), Map.of());
    }

    public static ImportResult missingParts(List<Identifier> parts) {
        return new ImportResult(ImportStatus.MISSING_PARTS, Set.of(), parts, Map.of());
    }

    public static ImportResult missingDyes(Map<String, Integer> dyes) {
        return new ImportResult(ImportStatus.MISSING_DYES, Set.of(), List.of(), dyes);
    }
}
