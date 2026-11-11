package de.lardtom.propwash.race;

import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class LeaderboardPages {
    public static final int PAGE = RacePayloads.LeaderboardPageS2C.PAGE;
    public static final int MAX_BYTES = RacePayloads.LeaderboardPageS2C.MAX_BYTES;

    public record Pick(Leaderboard.Ranked ranked, boolean assisted) {
    }

    private LeaderboardPages() {
    }

    public static List<Leaderboard.Ranked> view(List<Leaderboard.Ranked> ranked, @Nullable String frame) {
        List<Leaderboard.Ranked> out = new ArrayList<>();
        for (Leaderboard.Ranked entry : ranked) {
            if (frame == null ? entry.overallRank() > 0 : entry.entry().frame().equals(frame)) {
                out.add(entry);
            }
        }
        return out;
    }

    public static List<Leaderboard.Ranked> page(List<Leaderboard.Ranked> view, int offset) {
        int from = Math.clamp(offset, 0, view.size());
        return view.subList(from, Math.min(view.size(), from + PAGE));
    }

    public static List<Pick> first(List<Leaderboard.Ranked> clean, List<Leaderboard.Ranked> assisted, UUID viewer) {
        List<Pick> out = new ArrayList<>();
        out.addAll(first(clean, viewer, false));
        out.addAll(first(assisted, viewer, true));
        return out;
    }

    private static List<Pick> first(List<Leaderboard.Ranked> ranked, UUID viewer, boolean assisted) {
        Set<Leaderboard.Ranked> picked = new LinkedHashSet<>(page(view(ranked, null), 0));
        for (Leaderboard.Ranked entry : ranked) {
            if (entry.entry().player().equals(viewer)) {
                picked.add(entry);
            }
        }
        for (Leaderboard.Ranked entry : ranked) {
            if (entry.classRank() == 1) {
                picked.add(entry);
            }
        }
        List<Pick> out = new ArrayList<>(picked.size());
        for (Leaderboard.Ranked entry : picked) {
            out.add(new Pick(entry, assisted));
        }
        return out;
    }

    public static List<Pick> picks(List<Leaderboard.Ranked> ranked, boolean assisted) {
        List<Pick> out = new ArrayList<>(ranked.size());
        for (Leaderboard.Ranked entry : ranked) {
            out.add(new Pick(entry, assisted));
        }
        return out;
    }

    public static RacePayloads.BoardRow row(Pick pick, UUID viewer) {
        Leaderboard.Entry entry = pick.ranked().entry();
        boolean self = entry.player().equals(viewer);
        if (!pick.assisted()) {
            return new RacePayloads.BoardRow(entry.name(), entry.frame(), entry.nanos(), entry.dateMillis(), self, pick.ranked().classRank(),
                    pick.ranked().overallRank()).withServer(entry.server());
        }
        return new RacePayloads.BoardRow(entry.name(), entry.frame(), entry.nanos(), entry.dateMillis(), self, pick.ranked().classRank(),
                pick.ranked().overallRank(), entry.assist(), entry.details()).withServer(entry.server());
    }

    public static List<RacePayloads.BoardRow> fit(List<RacePayloads.BoardRow> rows, int maxRows, int maxBytes) {
        int bytes = 0;
        int count = 0;
        for (RacePayloads.BoardRow row : rows) {
            bytes += row.bytes();
            if (count >= maxRows || count > 0 && bytes > maxBytes) {
                break;
            }
            count++;
        }
        return count == rows.size() ? rows : List.copyOf(rows.subList(0, count));
    }

    public static RacePayloads.BoardTotals totals(int revision, List<Leaderboard.Ranked> clean, List<Leaderboard.Ranked> assisted) {
        Map<String, int[]> classes = new LinkedHashMap<>();
        int all = count(clean, classes, 0);
        int flagged = count(assisted, classes, 1);
        List<RacePayloads.ClassTotal> list = new ArrayList<>(classes.size());
        classes.forEach((frame, count) -> list.add(new RacePayloads.ClassTotal(frame, count[0], count[1])));
        return new RacePayloads.BoardTotals(revision, all, flagged, list);
    }

    private static int count(List<Leaderboard.Ranked> ranked, Map<String, int[]> classes, int slot) {
        int overall = 0;
        for (Leaderboard.Ranked entry : ranked) {
            classes.computeIfAbsent(entry.entry().frame(), frame -> new int[2])[slot]++;
            if (entry.overallRank() > 0) {
                overall++;
            }
        }
        return overall;
    }
}
