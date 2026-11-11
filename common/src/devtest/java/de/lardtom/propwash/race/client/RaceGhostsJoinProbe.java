package de.lardtom.propwash.race.client;

import de.lardtom.propwash.dev.RaceGhostsJoinCheck;
import de.lardtom.propwash.network.client.ClientNet;
import de.lardtom.propwash.race.GhostAccess;
import de.lardtom.propwash.race.GhostRecorder;
import de.lardtom.propwash.race.RacePayloads;
import net.minecraft.client.Minecraft;
import org.jspecify.annotations.Nullable;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

public final class RaceGhostsJoinProbe {
    public static final int TIMEOUT_TICKS = 20 * 120;
    private static final int REMOVE_WAIT = 60;
    private static final String START = "race_ghosts_start";
    private static final String LINES = "race_ghosts_lines";
    private static final String STAGE = "race_ghosts_stage";
    private static final String COURSE = "race_ghosts_course";
    private static final String AT = "race_ghosts_at";
    private static final String SECOND = "race_ghosts_second";
    private static final String LONG = "race_ghosts_long";
    private static final String RESULT = "race_ghosts_result";

    private RaceGhostsJoinProbe() {
    }

    private static void line(Map<String, Object> state, String text) {
        state.put(LINES, state.getOrDefault(LINES, "") + text + "\n");
    }

    private static String finish(Map<String, Object> state, String last) {
        line(state, last);
        String result = (String) state.get(LINES);
        state.put(RESULT, result);
        return result;
    }

    private static RacePayloads.@Nullable BoardRow row(RacePayloads.LeaderboardS2C board, String name) {
        for (RacePayloads.BoardRow row : board.rows()) {
            if (!row.assisted() && row.name().equals(name)) {
                return row;
            }
        }
        return null;
    }

    private static RacePayloads.@Nullable BoardRow own(RacePayloads.LeaderboardS2C board) {
        for (RacePayloads.BoardRow row : board.rows()) {
            if (!row.assisted() && row.self()) {
                return row;
            }
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    public static @Nullable String observe(Map<String, Object> state, int tick, Minecraft mc) {
        if (state.containsKey(RESULT)) {
            return (String) state.get(RESULT);
        }
        int start = (int) state.computeIfAbsent(START, key -> tick);
        int stage = (int) state.getOrDefault(STAGE, 0);
        if (tick - start > TIMEOUT_TICKS) {
            return finish(state, "raceghosts timeout at stage " + stage);
        }
        if (mc.player == null) {
            return null;
        }
        switch (stage) {
            case 0 -> {
                Optional<String> server = RaceDataChat.line(mc, RaceGhostsJoinCheck.RESULT);
                if (server.isEmpty()) {
                    return null;
                }
                String text = server.get().substring(RaceGhostsJoinCheck.RESULT.length());
                line(state, "raceghosts server " + text);
                if (!text.startsWith("ok course=")) {
                    return finish(state, "raceghosts fail server");
                }
                int course = Integer.parseInt(text.substring("ok course=".length()).split(" ")[0]);
                state.put(COURSE, course);
                ClientLeaderboards.request(course);
                state.put(AT, tick);
                state.put(STAGE, 1);
                return null;
            }
            case 1 -> {
                int course = (int) state.get(COURSE);
                RacePayloads.LeaderboardS2C board = ClientLeaderboards.get(course);
                RacePayloads.BoardRow own = board == null ? null : own(board);
                RacePayloads.BoardRow second = board == null ? null : row(board, RaceGhostsJoinCheck.SECOND);
                RacePayloads.BoardRow longRow = board == null ? null : row(board, RaceGhostsJoinCheck.LONG_NAME);
                if (own == null || second == null || longRow == null) {
                    if (tick - (int) state.get(AT) > 100) {
                        ClientLeaderboards.request(course);
                        state.put(AT, tick);
                    }
                    return null;
                }
                if (!own.ghost() || !second.ghost() || !longRow.ghost()) {
                    return finish(state, "raceghosts fail rows without ghost own=" + own.ghost() + " second=" + second.ghost() + " long=" + longRow.ghost());
                }
                if (!own.code().startsWith("PW3-") || !second.code().startsWith("PW3-")) {
                    return finish(state, "raceghosts fail rows without share code");
                }
                line(state, "raceghosts rows ok two pilots with share code");
                int asked = GhostUploader.INSTANCE.declined() + GhostUploader.INSTANCE.finished();
                line(state, "raceghosts upload asked " + (asked > 0 ? "ok" : "none") + " declined=" + GhostUploader.INSTANCE.declined()
                        + " sent=" + GhostUploader.INSTANCE.finished());
                boolean admin = board.mayRemove();
                if (!board.mayReplayOthers() || !board.mayRaceOthers()) {
                    return finish(state, "raceghosts fail access " + board.access());
                }
                line(state, "raceghosts access " + (admin ? "admin" : "player") + " replay opponent");
                AtomicReference<GhostDownloads.Result> secondResult = new AtomicReference<>();
                AtomicReference<GhostDownloads.Result> longResult = new AtomicReference<>();
                GhostDownloads.INSTANCE.clear();
                GhostDownloads.INSTANCE.fetch(course, second.key(), GhostAccess.Purpose.REPLAY, secondResult::set);
                state.put(SECOND, secondResult);
                state.put("race_ghosts_long_key", longRow.key());
                state.put("race_ghosts_second_row", second);
                state.put("race_ghosts_admin", admin);
                state.put(STAGE, 2);
                state.put(LONG, longResult);
                return null;
            }
            case 2 -> {
                GhostDownloads.Result second = ((AtomicReference<GhostDownloads.Result>) state.get(SECOND)).get();
                if (second == null) {
                    return null;
                }
                if (!second.loaded() || second.track() == null || second.track().samples() < 10) {
                    return finish(state, "raceghosts fail replay fetch " + second.status());
                }
                if (second.track().leadNanos() != GhostRecorder.LEAD_NANOS) {
                    return finish(state, "raceghosts fail replay lead " + second.track().leadNanos() / 1_000_000L + "ms");
                }
                line(state, "raceghosts replay ok lead=" + second.track().leadNanos() / 1_000_000L + "ms");
                int course = (int) state.get(COURSE);
                AtomicReference<GhostDownloads.Result> longResult = (AtomicReference<GhostDownloads.Result>) state.get(LONG);
                GhostDownloads.INSTANCE.fetch(course, (de.lardtom.propwash.race.GhostKey) state.get("race_ghosts_long_key"),
                        GhostAccess.Purpose.OPPONENT, longResult::set);
                state.put(STAGE, 3);
                return null;
            }
            case 3 -> {
                GhostDownloads.Result longResult = ((AtomicReference<GhostDownloads.Result>) state.get(LONG)).get();
                if (longResult == null) {
                    return null;
                }
                int parts = GhostDownloads.INSTANCE.lastParts();
                if (!longResult.loaded() || longResult.track() == null || parts < 2) {
                    return finish(state, "raceghosts fail chunked fetch " + longResult.status() + " parts=" + parts);
                }
                if (longResult.track().leadNanos() != GhostRecorder.LEAD_NANOS) {
                    return finish(state, "raceghosts fail lead " + longResult.track().leadNanos());
                }
                line(state, "raceghosts fetch ok parts=" + parts + " samples=" + longResult.track().samples() + " lead="
                        + longResult.track().leadNanos() / 1_000_000L + "ms");
                int course = (int) state.get(COURSE);
                RacePayloads.BoardRow second = (RacePayloads.BoardRow) state.get("race_ghosts_second_row");
                ClientNet.sendToServer(new RacePayloads.TimeRemoveC2S(course, second.player(), second.frame(), false));
                state.put(AT, tick);
                state.put(STAGE, 4);
                return null;
            }
            case 4 -> {
                int course = (int) state.get(COURSE);
                if (tick - (int) state.get(AT) < REMOVE_WAIT) {
                    return null;
                }
                RacePayloads.LeaderboardS2C board = ClientLeaderboards.get(course);
                boolean admin = (boolean) state.get("race_ghosts_admin");
                boolean present = board != null && row(board, RaceGhostsJoinCheck.SECOND) != null;
                if (admin == present) {
                    return finish(state, "raceghosts fail remove " + (admin ? "admin could not delete" : "player could delete"));
                }
                line(state, admin ? "raceghosts remove admin ok" : "raceghosts remove refused ok");
                return finish(state, "raceghosts ok");
            }
            default -> {
                return finish(state, "raceghosts fail stage " + stage);
            }
        }
    }
}
