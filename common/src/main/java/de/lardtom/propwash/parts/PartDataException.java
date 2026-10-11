package de.lardtom.propwash.parts;

import java.util.List;

public final class PartDataException extends IllegalStateException {
    private final List<String> problems;

    public PartDataException(String headline, List<String> problems) {
        super(headline + (problems.isEmpty() ? "" : ":\n  - " + String.join("\n  - ", problems)));
        this.problems = List.copyOf(problems);
    }

    public List<String> problems() {
        return this.problems;
    }
}
