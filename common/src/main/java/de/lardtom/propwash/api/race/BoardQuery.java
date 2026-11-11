package de.lardtom.propwash.api.race;

import java.util.Objects;
import java.util.Optional;

public record BoardQuery(TimeKind kind, Category category, int layout, int limit, Optional<String> frame, int raceLaps) {
    public static final int CURRENT_LAYOUT = 0;
    public static final int DEFAULT_LIMIT = 10;
    public static final int MAX_LIMIT = Integer.MAX_VALUE;

    public BoardQuery {
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(category, "category");
        Objects.requireNonNull(frame, "frame");
        if (layout < 0) {
            throw new IllegalArgumentException("Layout darf nicht negativ sein: " + layout);
        }
        if (limit < 1) {
            throw new IllegalArgumentException("Limit muss mindestens 1 sein: " + limit);
        }
        if (kind == TimeKind.RACE && raceLaps < 1) {
            throw new IllegalArgumentException("Rennen brauchen eine Rundenzahl ab 1: " + raceLaps);
        }
        if (kind == TimeKind.LAP && raceLaps != 0) {
            throw new IllegalArgumentException("Rundenzahl gilt nur für Rennen");
        }
        frame = frame.filter(value -> !value.isEmpty());
    }

    public static BoardQuery laps() {
        return new BoardQuery(TimeKind.LAP, Category.CLEAN, CURRENT_LAYOUT, DEFAULT_LIMIT, Optional.empty(), 0);
    }

    public static BoardQuery races(int laps) {
        return new BoardQuery(TimeKind.RACE, Category.CLEAN, CURRENT_LAYOUT, DEFAULT_LIMIT, Optional.empty(), laps);
    }

    public BoardQuery category(Category next) {
        return new BoardQuery(this.kind, next, this.layout, this.limit, this.frame, this.raceLaps);
    }

    public BoardQuery assisted() {
        return this.category(Category.ASSISTED);
    }

    public BoardQuery layout(int next) {
        return new BoardQuery(this.kind, this.category, next, this.limit, this.frame, this.raceLaps);
    }

    public BoardQuery limit(int next) {
        return new BoardQuery(this.kind, this.category, this.layout, next, this.frame, this.raceLaps);
    }

    public BoardQuery frame(String next) {
        return new BoardQuery(this.kind, this.category, this.layout, this.limit, Optional.ofNullable(next), this.raceLaps);
    }
}
