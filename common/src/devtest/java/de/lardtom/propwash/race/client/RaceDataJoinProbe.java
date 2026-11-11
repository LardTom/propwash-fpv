package de.lardtom.propwash.race.client;

import com.mojang.blaze3d.platform.InputConstants;
import de.lardtom.propwash.dev.RaceDataJoinCheck;
import de.lardtom.propwash.race.BoardIndex;
import de.lardtom.propwash.race.CourseData;
import de.lardtom.propwash.race.RacePayloads;
import net.minecraft.client.Minecraft;
import net.minecraft.client.input.KeyEvent;
import org.jspecify.annotations.Nullable;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public final class RaceDataJoinProbe {
    public static final int TIMEOUT_TICKS = 20 * 180;
    public static final String OPEN = "racedata_open";
    private static final String START = "racedata_start";
    private static final String RESULT = "racedata_result";
    private static final String SERVER = "racedata_server";
    private static final String SENT = "racedata_sent";
    private static final String BOARD = "racedata_board";
    private static final String PAGES = "racedata_pages";
    private static final String BOARD_RESULT = "racedata_board_result";
    private static final String COURSE = "racedata_course";
    private static final String SERVER_LINE = "racedata_server_line";

    private RaceDataJoinProbe() {
    }

    private static String finish(Map<String, Object> state, String text) {
        String result = text + "\n";
        state.put(RESULT, result);
        return result;
    }

    private static @Nullable CourseData course(UUID key) {
        for (CourseData course : ClientCourses.all()) {
            if (key.equals(course.key())) {
                return course;
            }
        }
        return null;
    }

    public static @Nullable String observe(Map<String, Object> state, int tick, Minecraft mc) {
        if (state.containsKey(RESULT)) {
            return (String) state.get(RESULT);
        }
        int start = (int) state.computeIfAbsent(START, key -> tick);
        if (tick - start > TIMEOUT_TICKS) {
            return finish(state, "racedata timeout server=" + state.getOrDefault(SERVER, "-"));
        }
        if (!state.containsKey(SERVER)) {
            Optional<String> line = RaceDataChat.line(mc, RaceDataJoinCheck.RESULT);
            if (line.isEmpty()) {
                return null;
            }
            state.put(SERVER, line.get().substring(RaceDataJoinCheck.RESULT.length()));
        }
        String server = (String) state.get(SERVER);
        if (!server.startsWith("ok ")) {
            return finish(state, "racedata server " + server);
        }
        int at = server.indexOf("key=");
        UUID key = UUID.fromString(server.substring(at + 4, server.indexOf(' ', at)));
        CourseData course = course(key);
        if (course == null || mc.player == null) {
            return null;
        }
        if (!state.containsKey(SENT)) {
            mc.player.connection.sendCommand("propwash course id " + course.id());
            state.put(SENT, tick);
            return null;
        }
        if (RaceDataChat.copyLinks(mc, key.toString()) == 0) {
            return tick - (int) state.get(SENT) > 20 * 20 ? finish(state, "racedata command without copy link for " + key) : null;
        }
        if (!state.containsKey(BOARD)) {
            state.put(BOARD, tick);
            state.put(COURSE, course.id());
            state.put(SERVER_LINE, server);
            state.put(PAGES, ClientLeaderboards.pageRequests());
            state.put(OPEN, Boolean.TRUE);
            mc.gui.setScreen(new LeaderboardScreen(null, course.id()));
            return null;
        }
        String board = (String) state.get(BOARD_RESULT);
        if (board == null) {
            return null;
        }
        if (!board.startsWith("racedata board ok ")) {
            return finish(state, board + " | server " + server);
        }
        return finish(state, "racedata ok client key=" + key + " number=" + course.id() + " | " + board + " | server " + server);
    }

    private static String field(String line, String name) {
        int at = line.indexOf(name + "=");
        if (at < 0) {
            return "";
        }
        int end = line.indexOf(' ', at);
        return line.substring(at + name.length() + 1, end < 0 ? line.length() : end);
    }

    private static long sum(BoardIndex index) {
        long sum = 0L;
        for (RacePayloads.BoardRow row : index.rows()) {
            sum += row.nanos() / 1_000_000L;
        }
        return sum;
    }

    public static void whileOpen(Map<String, Object> state, int tick, Minecraft mc) {
        String result = board(state, tick, mc, (int) state.get(COURSE), (String) state.get(SERVER_LINE));
        if (result != null) {
            state.put(BOARD_RESULT, result);
            state.remove(OPEN);
            mc.gui.setScreen(null);
        }
    }

    private static @Nullable String board(Map<String, Object> state, int tick, Minecraft mc, int course, String server) {
        LeaderboardScreen screen = LeaderboardScreen.active();
        RacePayloads.LeaderboardS2C payload = ClientLeaderboards.get(course);
        if (screen == null || mc.gui.screen() != screen || screen.frames() < 3 || payload == null
                || payload.total(null, false) != RaceDataJoinCheck.BOARD_RECORDS || payload.total(null, true) != RaceDataJoinCheck.BOARD_ASSISTED) {
            return tick - (int) state.get(BOARD) > 20 * 40 ? "racedata board missing " + (payload == null ? "-" : payload.total(null, false) + "/"
                    + payload.total(null, true)) : null;
        }
        BoardIndex clean = screen.index(false);
        BoardIndex assisted = screen.index(true);
        if (!clean.complete() || !assisted.complete() || screen.table().offset() < screen.table().maxOffset()) {
            if (tick - (int) state.get(BOARD) > 20 * 60) {
                return "racedata board incomplete " + clean.loaded() + "/" + clean.total() + " " + assisted.loaded() + "/" + assisted.total();
            }
            if (screen.placeholdersDrawn() == 0 && !ClientLeaderboards.loading(course, null, false) && !ClientLeaderboards.loading(course, null, true)) {
                screen.keyPressed(new KeyEvent(InputConstants.KEY_PAGEDOWN, 0, 0));
            }
            return null;
        }
        String own = clean.selfRank() + "/" + assisted.selfRank();
        String sums = sum(clean) + "/" + sum(assisted);
        int pages = ClientLeaderboards.pageRequests() - (int) state.get(PAGES);
        String shown = "records=" + clean.loaded() + " assisted=" + assisted.loaded() + " pages=" + pages + " own=" + own + " sum=" + sums;
        if (!own.equals(field(server, "own")) || !sums.equals(field(server, "sum")) || pages < 2) {
            return "racedata board wrong " + shown + " server own=" + field(server, "own") + " sum=" + field(server, "sum");
        }
        return "racedata board ok " + shown;
    }
}
