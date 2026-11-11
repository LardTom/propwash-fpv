package de.lardtom.propwash.race;

import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.TreeMap;

public final class BoardIndex {
    private final TreeMap<Integer, RacePayloads.BoardRow> rows;
    private final int total;
    private final int self;

    private BoardIndex(TreeMap<Integer, RacePayloads.BoardRow> rows, int total, int self) {
        this.rows = rows;
        this.total = total;
        this.self = self;
    }

    public static BoardIndex of(RacePayloads.@Nullable LeaderboardS2C board, @Nullable String frame, boolean assisted) {
        TreeMap<Integer, RacePayloads.BoardRow> rows = new TreeMap<>();
        if (board == null) {
            return new BoardIndex(rows, 0, -1);
        }
        int total = board.total(frame, assisted);
        int self = -1;
        for (RacePayloads.BoardRow row : board.rows()) {
            if (row.assisted() != assisted) {
                continue;
            }
            int rank = row.rank(frame);
            if (rank < 1 || rank > total || rows.putIfAbsent(rank, row) != null) {
                continue;
            }
            if (row.self() && self < 0) {
                self = rank;
            }
        }
        return new BoardIndex(rows, total, self);
    }

    public int total() {
        return this.total;
    }

    public int loaded() {
        return this.rows.size();
    }

    public boolean complete() {
        return this.rows.size() == this.total;
    }

    public RacePayloads.@Nullable BoardRow row(int rank) {
        return this.rows.get(rank);
    }

    public int selfRank() {
        return this.self;
    }

    public long best() {
        RacePayloads.BoardRow first = this.row(1);
        return first == null ? 0L : first.nanos();
    }

    public int firstMissing(int from, int to) {
        int last = Math.min(to, this.total);
        for (int rank = Math.max(1, from); rank <= last; rank++) {
            if (!this.rows.containsKey(rank)) {
                return rank;
            }
        }
        return -1;
    }

    public int contiguous() {
        int rank = 0;
        while (rank < this.total && this.rows.containsKey(rank + 1)) {
            rank++;
        }
        return rank;
    }

    public List<RacePayloads.BoardRow> rows() {
        return new ArrayList<>(this.rows.values());
    }
}
