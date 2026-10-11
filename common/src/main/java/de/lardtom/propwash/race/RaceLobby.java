package de.lardtom.propwash.race;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class RaceLobby {
    public static final long WAIT_NANOS = 10L * LapTracker.SECOND;
    public static final long SOLO_GRACE_NANOS = 500_000_000L;
    public static final int MIN_COUNTDOWN_SECONDS = 3;
    public static final int MAX_COUNTDOWN_SECONDS = 10;
    public static final int MAX_MEMBERS = 16;
    public static final long HEAT_TIMEOUT_NANOS = 15L * 60L * LapTracker.SECOND;

    private final int course;
    private final Map<UUID, String> members = new LinkedHashMap<>();
    private long openedAt;
    private long deadline;
    private int countdownSeconds = MIN_COUNTDOWN_SECONDS;

    public RaceLobby(int course) {
        this.course = course;
    }

    public int course() {
        return this.course;
    }

    public boolean isEmpty() {
        return this.members.isEmpty();
    }

    public List<UUID> members() {
        return List.copyOf(this.members.keySet());
    }

    public List<String> names() {
        return List.copyOf(this.members.values());
    }

    public boolean contains(UUID player) {
        return this.members.containsKey(player);
    }

    public int countdownSeconds() {
        return this.countdownSeconds;
    }

    public long remainingNanos(long now) {
        return Math.max(0L, this.deadline - now);
    }

    public boolean join(UUID player, String name, int countdownSeconds, long now) {
        if (this.members.containsKey(player)) {
            return true;
        }
        if (this.members.size() >= MAX_MEMBERS) {
            return false;
        }
        if (this.members.isEmpty()) {
            this.openedAt = now;
            this.deadline = now + WAIT_NANOS;
            this.countdownSeconds = Math.clamp(countdownSeconds, MIN_COUNTDOWN_SECONDS, MAX_COUNTDOWN_SECONDS);
        }
        this.members.put(player, name);
        return true;
    }

    public boolean leave(UUID player) {
        return this.members.remove(player) != null;
    }

    public boolean shouldStart(Collection<UUID> pilotsAtStart, long now) {
        if (this.members.isEmpty()) {
            return false;
        }
        if (now - this.deadline >= 0L) {
            return true;
        }
        if (now - this.openedAt < SOLO_GRACE_NANOS) {
            return false;
        }
        for (UUID pilot : pilotsAtStart) {
            if (!this.members.containsKey(pilot)) {
                return false;
            }
        }
        return true;
    }

    public Heat start(long now) {
        Heat heat = new Heat(this.course, new ArrayList<>(this.members.keySet()), now);
        this.members.clear();
        return heat;
    }

    public static final class Heat {
        private final int course;
        private final List<UUID> pilots;
        private final List<UUID> finished = new ArrayList<>();
        private final List<UUID> dropped = new ArrayList<>();
        private final long startedAt;

        Heat(int course, List<UUID> pilots, long startedAt) {
            this.course = course;
            this.pilots = List.copyOf(pilots);
            this.startedAt = startedAt;
        }

        public int course() {
            return this.course;
        }

        public List<UUID> pilots() {
            return this.pilots;
        }

        public boolean multiplayer() {
            return this.pilots.size() > 1;
        }

        public int finish(UUID pilot) {
            if (!this.pilots.contains(pilot) || this.finished.contains(pilot) || this.dropped.contains(pilot)) {
                return -1;
            }
            this.finished.add(pilot);
            return this.finished.size();
        }

        public boolean done(long now) {
            return this.finished.size() + this.dropped.size() >= this.pilots.size() || now - this.startedAt > HEAT_TIMEOUT_NANOS;
        }

        public void drop(UUID pilot) {
            if (this.pilots.contains(pilot) && !this.finished.contains(pilot) && !this.dropped.contains(pilot)) {
                this.dropped.add(pilot);
            }
        }
    }
}
