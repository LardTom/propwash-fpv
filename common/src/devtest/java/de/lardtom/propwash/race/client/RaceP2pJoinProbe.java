package de.lardtom.propwash.race.client;

import de.lardtom.propwash.api.race.CourseMode;
import de.lardtom.propwash.dev.RaceP2pJoinCheck;
import de.lardtom.propwash.network.client.ClientNet;
import de.lardtom.propwash.race.CourseData;
import de.lardtom.propwash.race.CourseEdits;
import de.lardtom.propwash.race.RaceCommands;
import de.lardtom.propwash.race.RacePayloads;
import de.lardtom.propwash.ui.client.widget.FlatButton;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.server.permissions.Permissions;
import org.jspecify.annotations.Nullable;

import java.util.Map;
import java.util.Optional;

public final class RaceP2pJoinProbe {
    public static final int TIMEOUT_TICKS = 20 * 110;
    public static final int REVOKE_TICKS = 40;
    private static final String START = "race_p2p_start";
    private static final String LINES = "race_p2p_lines";
    private static final String STAGE = "race_p2p_stage";
    private static final String COURSE = "race_p2p_course";
    private static final String SENT = "race_p2p_sent";
    private static final String DENIALS = "race_p2p_denials";

    private RaceP2pJoinProbe() {
    }

    private static void line(Map<String, Object> state, String text) {
        state.put(LINES, state.getOrDefault(LINES, "") + text + "\n");
    }

    private static String done(Map<String, Object> state) {
        state.put(STAGE, 99);
        return (String) state.get(LINES);
    }

    private static @Nullable String button(Minecraft mc, int course) {
        LeaderboardScreen board = new LeaderboardScreen(null, course);
        mc.gui.setScreen(board);
        FlatButton edit = board.editButton();
        String label = edit == null ? "-" : edit.getMessage().getString();
        mc.gui.setScreen(null);
        if (label.equals(Component.translatable("propwash.race.board.edit").getString())) {
            return "edit";
        }
        return label.equals(Component.translatable("propwash.race.board.view").getString()) ? "view" : "other:" + label;
    }

    private static String denial(CourseData course) {
        return Component.translatable("propwash.race.course_edit.denied.not_owner", Component.literal(course.name()),
                Component.literal(RaceP2pJoinCheck.OWNER), (int) RaceCommands.NEAREST_COURSE_RANGE).getString();
    }

    public static @Nullable String observe(Map<String, Object> state, int tick, Minecraft mc) {
        int start = (int) state.computeIfAbsent(START, key -> tick);
        int stage = (int) state.getOrDefault(STAGE, 0);
        if (stage == 99) {
            return (String) state.get(LINES);
        }
        if (tick - start > TIMEOUT_TICKS) {
            line(state, "race_p2p timeout at stage " + stage);
            return done(state);
        }
        if (mc.player == null) {
            return null;
        }
        switch (stage) {
            case 0 -> {
                Optional<String> server = RaceDataChat.line(mc, RaceP2pJoinCheck.RESULT);
                if (server.isEmpty()) {
                    return null;
                }
                String result = server.get().substring(RaceP2pJoinCheck.RESULT.length());
                line(state, "race_p2p_server=" + result);
                if (!result.startsWith("ok ")) {
                    return done(state);
                }
                int at = result.indexOf("course=");
                state.put(COURSE, Integer.parseInt(result.substring(at + 7, result.indexOf(' ', at))));
                state.put(STAGE, 1);
                return null;
            }
            case 1 -> {
                CourseData course = ClientCourses.get((int) state.get(COURSE));
                if (course == null || course.mode() != CourseMode.POINT_TO_POINT) {
                    return null;
                }
                String label = button(mc, course.id());
                if (label == null) {
                    return null;
                }
                line(state, "race_p2p_client_mode=" + course.mode().key() + " finish=" + course.finish(course.gates().size() - 1));
                line(state, "race_p2p_b_button=" + label + " b_can_edit=" + CourseEditScreen.canEdit(mc, course));
                state.put(DENIALS, RaceDataChat.containing(mc, denial(course)));
                ClientNet.sendToServer(new RacePayloads.CourseEditC2S(course.id(), CourseEdits.Action.MODE, CourseMode.LAPS.ordinal(), 0));
                ClientNet.sendToServer(RacePayloads.CourseEditC2S.rename(course.id(), RaceP2pJoinCheck.STOLEN));
                mc.player.connection.sendCommand("propwash race mode " + course.id() + " " + CourseMode.LAPS.key());
                state.put(SENT, tick);
                state.put(STAGE, 2);
                return null;
            }
            case 2 -> {
                CourseData course = ClientCourses.get((int) state.get(COURSE));
                if (course == null) {
                    line(state, "race_p2p_b_denied=no:course_gone");
                    return done(state);
                }
                int denials = RaceDataChat.containing(mc, denial(course)) - (int) state.get(DENIALS);
                if (denials < 1 && tick - (int) state.get(SENT) < 20 * 6) {
                    return null;
                }
                boolean kept = course.mode() == CourseMode.POINT_TO_POINT && RaceP2pJoinCheck.NAME.equals(course.name());
                line(state, "race_p2p_b_packet_denied=" + (kept ? "yes" : "no:" + course.mode().key() + "," + course.name())
                        + " b_command_denied=" + (denials >= 1 ? "yes" : "no"));
                state.put(STAGE, 3);
                return null;
            }
            case 3 -> {
                if (!mc.player.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER)) {
                    return null;
                }
                CourseData course = ClientCourses.get((int) state.get(COURSE));
                if (course == null) {
                    line(state, "race_p2p_op=no:course_gone");
                    return done(state);
                }
                String label = button(mc, course.id());
                if (label == null) {
                    return null;
                }
                line(state, "race_p2p_op_button=" + label + " op_can_edit=" + CourseEditScreen.canEdit(mc, course));
                ClientNet.sendToServer(new RacePayloads.CourseEditC2S(course.id(), CourseEdits.Action.MODE, CourseMode.LAPS.ordinal(), 0));
                state.put(SENT, tick);
                state.put(STAGE, 4);
                return null;
            }
            case 4 -> {
                CourseData course = ClientCourses.get((int) state.get(COURSE));
                boolean switched = course != null && course.mode() == CourseMode.LAPS;
                if (!switched && tick - (int) state.get(SENT) < 20 * 6) {
                    return null;
                }
                line(state, "race_p2p_op_edit=" + (switched ? "yes" : "no"));
                state.put(SENT, tick);
                state.put(STAGE, 5);
                return null;
            }
            case 5 -> {
                boolean revoked = !mc.player.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER);
                if (!revoked && tick - (int) state.get(SENT) < REVOKE_TICKS) {
                    return null;
                }
                line(state, "race_p2p_op_revoked=" + (revoked ? "yes" : "no"));
                return done(state);
            }
            default -> {
                return (String) state.get(LINES);
            }
        }
    }
}
