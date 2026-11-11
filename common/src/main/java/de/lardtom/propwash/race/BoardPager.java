package de.lardtom.propwash.race;

import org.jspecify.annotations.Nullable;

import java.util.Objects;

public final class BoardPager {
    public static final long TIMEOUT_NANOS = 3_000_000_000L;
    public static final long INTERVAL_NANOS = 120_000_000L;
    public static final int MAX_TRIES = 5;
    public static final int AHEAD = 10;

    private record Key(int course, int revision, @Nullable String frame, boolean assisted, int offset) {
    }

    private @Nullable Key pending;
    private long sentAt;
    private long lastSent;
    private boolean sent;
    private @Nullable Key lastKey;
    private int tries;
    private int requests;

    public RacePayloads.@Nullable LeaderboardPageC2S next(RacePayloads.@Nullable LeaderboardS2C board, @Nullable String frame, boolean assisted,
                                                          int from, int to, long now) {
        if (board == null || !board.totals().paged()) {
            return null;
        }
        int missing = BoardIndex.of(board, frame, assisted).firstMissing(from, to + AHEAD);
        if (missing < 0) {
            return null;
        }
        if (this.pending != null && now - this.sentAt < TIMEOUT_NANOS) {
            return null;
        }
        if (this.sent && now - this.lastSent < INTERVAL_NANOS) {
            return null;
        }
        Key key = new Key(board.courseId(), board.revision(), frame, assisted, missing - 1);
        if (key.equals(this.lastKey)) {
            if (this.tries >= MAX_TRIES) {
                return null;
            }
            this.tries++;
        } else {
            this.lastKey = key;
            this.tries = 1;
        }
        this.pending = key;
        this.sentAt = now;
        this.lastSent = now;
        this.sent = true;
        this.requests++;
        return RacePayloads.LeaderboardPageC2S.of(key.course(), key.revision(), frame, assisted, key.offset());
    }

    public void received(int course) {
        if (this.pending != null && this.pending.course() == course) {
            this.pending = null;
        }
    }

    public void reset(int course, int revision) {
        if (this.pending != null && this.pending.course() == course) {
            this.pending = null;
        }
        if (this.lastKey != null && this.lastKey.course() == course && this.lastKey.revision() != revision) {
            this.lastKey = null;
            this.tries = 0;
        }
    }

    public void clear() {
        this.pending = null;
        this.lastKey = null;
        this.tries = 0;
        this.sent = false;
    }

    public boolean waiting() {
        return this.pending != null;
    }

    public boolean waiting(int course, @Nullable String frame, boolean assisted) {
        Key key = this.pending;
        return key != null && key.course() == course && Objects.equals(key.frame(), frame) && key.assisted() == assisted;
    }

    public int requests() {
        return this.requests;
    }
}
