package de.lardtom.propwash.api.race;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

public record BoardSnapshot(UUID course, int number, int layout, boolean current, long archivedAtMillis, int elements, BoardQuery query,
                            int total, List<BoardEntry> entries, List<String> frames, List<Integer> raceLapCounts) {
    public BoardSnapshot {
        Objects.requireNonNull(course, "course");
        Objects.requireNonNull(query, "query");
        entries = List.copyOf(entries);
        frames = List.copyOf(frames);
        raceLapCounts = List.copyOf(raceLapCounts);
    }

    public Optional<BoardEntry> first() {
        return this.entries.isEmpty() ? Optional.empty() : Optional.of(this.entries.getFirst());
    }

    public Optional<BoardEntry> entry(UUID player) {
        for (BoardEntry entry : this.entries) {
            if (entry.player().equals(player)) {
                return Optional.of(entry);
            }
        }
        return Optional.empty();
    }
}
