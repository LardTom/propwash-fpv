package de.lardtom.propwash.race.client;

import de.lardtom.propwash.network.client.ClientNet;
import de.lardtom.propwash.race.BoardIndex;
import de.lardtom.propwash.race.BoardPager;
import de.lardtom.propwash.race.GhostKey;
import de.lardtom.propwash.race.RacePayloads;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class ClientLeaderboards {
    private static final Map<Integer, RacePayloads.LeaderboardS2C> BOARDS = new HashMap<>();
    private static final BoardPager PAGER = new BoardPager();
    private static final Map<String, BoardIndex> INDEX = new HashMap<>();
    private static int version;
    private static int pages;

    private ClientLeaderboards() {
    }

    static void apply(Minecraft minecraft, RacePayloads.LeaderboardS2C payload) {
        put(payload);
        PAGER.reset(payload.courseId(), payload.revision());
        retainGhosts(payload);
        Screen shown = minecraft.gui.screen();
        if (payload.open() && !(shown instanceof LeaderboardScreen) && !(shown instanceof CourseMenuScreen)) {
            minecraft.gui.setScreen(ClientCourses.get(payload.courseId()) != null ? new CourseMenuScreen(shown, payload.courseId())
                    : new LeaderboardScreen(shown, payload.courseId()));
        }
    }

    static void applyPage(RacePayloads.LeaderboardPageS2C page) {
        PAGER.received(page.courseId());
        RacePayloads.LeaderboardS2C board = BOARDS.get(page.courseId());
        if (board == null) {
            return;
        }
        RacePayloads.LeaderboardS2C merged = board.merge(page);
        if (merged != board) {
            pages++;
            put(merged);
            retainGhosts(merged);
        }
    }

    private static void put(RacePayloads.LeaderboardS2C payload) {
        BOARDS.put(payload.courseId(), payload);
        INDEX.keySet().removeIf(key -> key.startsWith(payload.courseId() + "|"));
        version++;
    }

    private static void retainGhosts(RacePayloads.LeaderboardS2C payload) {
        List<GhostKey> keys = new ArrayList<>();
        for (RacePayloads.BoardRow row : payload.rows()) {
            if (row.ghost()) {
                keys.add(row.key());
            }
        }
        GhostDownloads.INSTANCE.retain(payload.courseId(), keys);
    }

    static void clear() {
        BOARDS.clear();
        INDEX.clear();
        PAGER.clear();
        version++;
    }

    public static BoardIndex index(int courseId, @Nullable String frame, boolean assisted) {
        RacePayloads.LeaderboardS2C board = BOARDS.get(courseId);
        if (board == null) {
            return BoardIndex.of(null, frame, assisted);
        }
        String key = courseId + "|" + assisted + "|" + (frame == null ? "*" : "=" + frame);
        BoardIndex index = INDEX.get(key);
        if (index == null) {
            index = BoardIndex.of(board, frame, assisted);
            INDEX.put(key, index);
        }
        return index;
    }

    public static boolean ensure(int courseId, @Nullable String frame, boolean assisted, int fromRank, int toRank) {
        RacePayloads.LeaderboardPageC2S request = PAGER.next(BOARDS.get(courseId), frame, assisted, fromRank, toRank, System.nanoTime());
        if (request == null || !ClientNet.canSend(RacePayloads.LeaderboardPageC2S.TYPE)) {
            return false;
        }
        return ClientNet.sendToServer(request);
    }

    public static boolean loading(int courseId, @Nullable String frame, boolean assisted) {
        return PAGER.waiting(courseId, frame, assisted);
    }

    public static int pageRequests() {
        return PAGER.requests();
    }

    public static int pagesReceived() {
        return pages;
    }

    public static int version() {
        return version;
    }

    public static RacePayloads.@Nullable LeaderboardS2C get(int courseId) {
        return BOARDS.get(courseId);
    }

    public static boolean request(int courseId) {
        return ClientNet.canSend(RacePayloads.LeaderboardRequestC2S.TYPE) && ClientNet.sendToServer(new RacePayloads.LeaderboardRequestC2S(courseId));
    }

    public static List<RacePayloads.BoardRow> ranking(RacePayloads.LeaderboardS2C board, @Nullable String frame) {
        return ranking(board, frame, false);
    }

    public static List<RacePayloads.BoardRow> assistedRanking(RacePayloads.LeaderboardS2C board, @Nullable String frame) {
        return ranking(board, frame, true);
    }

    private static List<RacePayloads.BoardRow> ranking(RacePayloads.LeaderboardS2C board, @Nullable String frame, boolean assisted) {
        List<RacePayloads.BoardRow> result = new ArrayList<>();
        for (RacePayloads.BoardRow row : board.rows()) {
            if (row.assisted() == assisted && row.rank(frame) > 0) {
                result.add(row);
            }
        }
        result.sort(Comparator.comparingInt(row -> row.rank(frame)));
        return result;
    }

    public static int rank(RacePayloads.BoardRow row, @Nullable String frame) {
        return frame == null ? row.overallRank() : row.classRank();
    }

    public static List<String> classes(RacePayloads.LeaderboardS2C board) {
        Set<String> seen = new HashSet<>();
        List<String> classes = new ArrayList<>();
        for (RacePayloads.ClassTotal total : board.totals().classes()) {
            if (total.clean() + total.assisted() > 0 && seen.add(total.frame())) {
                classes.add(total.frame());
            }
        }
        for (RacePayloads.BoardRow row : board.rows()) {
            if (seen.add(row.frame())) {
                classes.add(row.frame());
            }
        }
        return classes;
    }
}
