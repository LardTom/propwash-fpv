package de.lardtom.propwash.api.race;

import java.util.Objects;

public record CourseChange(ChangeKind kind, CourseInfo course, int archivedLayout) {
    public CourseChange {
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(course, "course");
    }

    public boolean archived() {
        return this.archivedLayout > 0;
    }
}
