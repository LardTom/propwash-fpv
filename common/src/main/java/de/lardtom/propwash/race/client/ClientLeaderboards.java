package de.lardtom.propwash.race.client;

import de.lardtom.propwash.network.client.ClientNet;
import de.lardtom.propwash.race.RacePayloads;
import net.minecraft.client.Minecraft;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class ClientLeaderboards {
    public static final int TOP = 10;
    public static final int ASSISTED_TOP = 5;
    private static final Map<Integer, RacePayloads.LeaderboardS2C> BOARDS = new HashMap<>();
    private static int version;

    private ClientLeaderboards() {
    }

    static void apply(Minecraft minecraft, RacePayloads.LeaderboardS2C payload) {
        BOARDS.put(payload.courseId(), payload);
        version++;
        if (payload.open() && !(minecraft.gui.screen() instanceof LeaderboardScreen)) {
            minecraft.gui.setScreen(new LeaderboardScreen(minecraft.gui.screen(), payload.courseId()));
        }
    }

    static void clear() {
        BOARDS.clear();
        version++;
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
            if (row.assisted() != assisted) {
                continue;
            }
            boolean inView = frame == null ? row.overallRank() > 0 : row.frame().equals(frame) && row.classRank() > 0;
            if (inView) {
                result.add(row);
            }
        }
        result.sort(Comparator.comparingInt(row -> frame == null ? row.overallRank() : row.classRank()));
        return result;
    }

    public static int rank(RacePayloads.BoardRow row, @Nullable String frame) {
        return frame == null ? row.overallRank() : row.classRank();
    }

    public static List<String> classes(RacePayloads.LeaderboardS2C board) {
        Set<String> seen = new HashSet<>();
        List<String> classes = new ArrayList<>();
        for (RacePayloads.BoardRow row : board.rows()) {
            if (seen.add(row.frame())) {
                classes.add(row.frame());
            }
        }
        return classes;
    }
}
