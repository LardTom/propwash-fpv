package de.lardtom.propwash.api.race;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

public record BoardEntry(int rank, UUID player, String name, long timeMillis, long timeNanos, long dateMillis, String frame, TimeKind kind,
                         int laps, Category category, List<String> assistReasons, List<AssistInfo> assistDetails, List<RuleValue> rules,
                         Optional<List<RuleValue>> serverRules) {
    public BoardEntry(int rank, UUID player, String name, long timeMillis, long timeNanos, long dateMillis, String frame, TimeKind kind, int laps,
                      Category category, List<String> assistReasons, List<AssistInfo> assistDetails, List<RuleValue> rules) {
        this(rank, player, name, timeMillis, timeNanos, dateMillis, frame, kind, laps, category, assistReasons, assistDetails, rules, Optional.empty());
    }

    public BoardEntry(int rank, UUID player, String name, long timeMillis, long timeNanos, long dateMillis, String frame, TimeKind kind, int laps,
                      Category category, List<String> assistReasons, List<AssistInfo> assistDetails) {
        this(rank, player, name, timeMillis, timeNanos, dateMillis, frame, kind, laps, category, assistReasons, assistDetails, List.of());
    }

    public BoardEntry {
        Objects.requireNonNull(player, "player");
        name = name == null ? "" : name;
        frame = frame == null ? "" : frame;
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(category, "category");
        assistReasons = List.copyOf(assistReasons);
        assistDetails = List.copyOf(assistDetails);
        rules = rules == null ? List.of() : List.copyOf(rules);
        serverRules = serverRules == null ? Optional.empty() : serverRules.map(List::copyOf);
    }

    public boolean serverRulesDiffer() {
        return this.serverRules.map(list -> !list.isEmpty()).orElse(false);
    }

    public static long millis(long nanos) {
        return Math.floorDiv(nanos + 500_000L, 1_000_000L);
    }

    public boolean dateKnown() {
        return this.dateMillis > 0L;
    }

    public boolean assisted() {
        return this.category == Category.ASSISTED;
    }
}
