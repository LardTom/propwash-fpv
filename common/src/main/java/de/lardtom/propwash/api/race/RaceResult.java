package de.lardtom.propwash.api.race;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

public record RaceResult(UUID course, int number, String courseName, int layout, UUID player, String playerName, int laps, long timeMillis,
                         long timeNanos, String frame, Category category, List<String> assistReasons, List<AssistInfo> assistDetails, boolean saved,
                         boolean personalBest, int heatPlace) {
    public RaceResult {
        Objects.requireNonNull(course, "course");
        Objects.requireNonNull(player, "player");
        courseName = courseName == null ? "" : courseName;
        playerName = playerName == null ? "" : playerName;
        frame = frame == null ? "" : frame;
        Objects.requireNonNull(category, "category");
        assistReasons = List.copyOf(assistReasons);
        assistDetails = List.copyOf(assistDetails);
    }
}
