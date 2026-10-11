package de.lardtom.propwash.api.build;

import net.minecraft.resources.Identifier;

import java.util.List;
import java.util.Objects;

public record BuildCheck(BuildSpec spec, List<Identifier> unknownParts, List<String> problems, List<String> warnings) {
    public BuildCheck {
        Objects.requireNonNull(spec, "spec");
        unknownParts = List.copyOf(Objects.requireNonNullElse(unknownParts, List.of()));
        problems = List.copyOf(Objects.requireNonNullElse(problems, List.of()));
        warnings = List.copyOf(Objects.requireNonNullElse(warnings, List.of()));
    }

    public boolean known() {
        return this.unknownParts.isEmpty();
    }

    public boolean buildable() {
        return this.unknownParts.isEmpty() && this.problems.isEmpty();
    }

    public static String translationKey(String issue) {
        return "propwash.build.issue." + issue;
    }
}
