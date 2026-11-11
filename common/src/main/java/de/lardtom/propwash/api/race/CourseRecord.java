package de.lardtom.propwash.api.race;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

public record CourseRecord(UUID course, int number, String courseName, int layout, BoardEntry entry, Optional<BoardEntry> previous) {
    public CourseRecord {
        Objects.requireNonNull(course, "course");
        courseName = courseName == null ? "" : courseName;
        Objects.requireNonNull(entry, "entry");
        Objects.requireNonNull(previous, "previous");
    }
}
