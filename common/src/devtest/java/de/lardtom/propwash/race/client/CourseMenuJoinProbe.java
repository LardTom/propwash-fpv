package de.lardtom.propwash.race.client;

import com.mojang.blaze3d.platform.InputConstants;
import de.lardtom.propwash.api.race.CourseMode;
import de.lardtom.propwash.dev.CourseMenuJoinCheck;
import de.lardtom.propwash.network.client.ClientNet;
import de.lardtom.propwash.race.CourseData;
import de.lardtom.propwash.race.CourseEdits;
import de.lardtom.propwash.race.CourseMenu;
import de.lardtom.propwash.race.CourseToolItem;
import de.lardtom.propwash.race.RacePayloads;
import de.lardtom.propwash.ui.client.widget.TextField;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.input.MouseButtonInfo;
import net.minecraft.server.permissions.Permissions;
import net.minecraft.world.InteractionHand;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

public final class CourseMenuJoinProbe {
    public static final int TIMEOUT_TICKS = 20 * 120;
    private static final int WAIT_TICKS = 20 * 6;
    private static final String START = "course_menu_start";
    private static final String LINES = "course_menu_lines";
    private static final String STAGE = "course_menu_stage";
    private static final String COURSE = "course_menu_course";
    private static final String SENT = "course_menu_sent";
    private static final String FAILED = "course_menu_failed";
    public static final String OPEN = "course_menu_open";

    private CourseMenuJoinProbe() {
    }

    private static void line(Map<String, Object> state, String text) {
        state.put(LINES, state.getOrDefault(LINES, "") + text + "\n");
    }

    private static void check(Map<String, Object> state, String key, boolean ok, String detail) {
        line(state, key + "=" + (ok ? "yes" : "no:" + detail));
        if (!ok) {
            state.put(FAILED, state.getOrDefault(FAILED, "") + key + " ");
        }
    }

    private static String done(Map<String, Object> state) {
        state.put(STAGE, 99);
        Object failed = state.get(FAILED);
        line(state, failed == null ? "course_menu=ok" : "course_menu=fail " + failed);
        return (String) state.get(LINES);
    }

    private static @Nullable CourseMenuScreen menu(Minecraft mc, int course) {
        return mc.gui.screen() instanceof CourseMenuScreen menu && menu.courseId() == course ? menu : null;
    }

    private static String unchanged(@Nullable CourseData course) {
        if (course == null) {
            return "gone";
        }
        boolean same = CourseMenuJoinCheck.NAME.equals(course.name()) && course.laps() == CourseMenuJoinCheck.LAPS && course.mode() == CourseMode.LAPS
                && course.gates().size() == 3;
        return same ? "" : "name=" + course.name() + " laps=" + course.laps() + " mode=" + course.mode().key() + " gates=" + course.gates().size();
    }

    private static void click(CourseMenuScreen menu, AbstractWidget widget) {
        double x = widget.getX() + widget.getWidth() / 2.0;
        double y = widget.getY() + widget.getHeight() / 2.0;
        MouseButtonEvent event = new MouseButtonEvent(x, y, new MouseButtonInfo(InputConstants.MOUSE_BUTTON_LEFT, 0));
        menu.mouseClicked(event, false);
        menu.mouseReleased(event);
    }

    private static String readOnly(CourseMenuScreen menu) {
        List<String> wrong = new ArrayList<>();
        for (CourseMenu.Row row : menu.rows()) {
            AbstractWidget control = menu.control(row);
            if (control == null || control instanceof TextField) {
                continue;
            }
            boolean expected = CourseMenu.active(row, CourseMenu.Rights.VIEW);
            if (control.active != expected) {
                wrong.add(row.name().toLowerCase(Locale.ROOT) + "=" + control.active);
            }
        }
        if (!menu.readOnly() || !menu.rights().readOnly()) {
            wrong.add("rights=" + menu.rights());
        }
        return String.join(",", wrong);
    }

    private static void tryEverything(Minecraft mc, CourseMenuScreen menu, int course) {
        TextField name = menu.nameField();
        if (name != null) {
            menu.setFocused(name);
            for (char c : CourseMenuJoinCheck.STOLEN.toCharArray()) {
                name.charTyped(new CharacterEvent(c));
            }
            menu.setFocused(null);
        }
        for (AbstractWidget widget : menu.contentWidgets()) {
            if (!widget.active) {
                click(menu, widget);
                click(menu, widget);
            }
        }
        menu.rename(CourseMenuJoinCheck.STOLEN);
        menu.stepLaps(1);
        menu.setMode(CourseMode.POINT_TO_POINT);
        menu.reset();
        menu.reset();
        menu.delete();
        menu.delete();
        menu.flush();
        ClientNet.sendToServer(RacePayloads.CourseEditC2S.rename(course, CourseMenuJoinCheck.STOLEN));
        ClientNet.sendToServer(new RacePayloads.CourseEditC2S(course, CourseEdits.Action.LAPS, 7, 0));
        ClientNet.sendToServer(new RacePayloads.CourseEditC2S(course, CourseEdits.Action.MODE, CourseMode.POINT_TO_POINT.ordinal(), 0));
        ClientNet.sendToServer(new RacePayloads.CourseEditC2S(course, CourseEdits.Action.DELETE));
        if (mc.player != null) {
            mc.player.connection.sendCommand("propwash race name " + course + " " + CourseMenuJoinCheck.STOLEN);
            mc.player.connection.sendCommand(CourseMenu.resetCommand(course));
        }
    }

    private static void close(Map<String, Object> state, Minecraft mc, int stage) {
        if (mc.gui.screen() instanceof CourseMenuScreen) {
            mc.gui.setScreen(null);
        }
        state.remove(OPEN);
        state.put(STAGE, stage);
    }

    public static @Nullable String observe(Map<String, Object> state, int tick, Minecraft mc) {
        int start = (int) state.computeIfAbsent(START, key -> tick);
        int stage = (int) state.getOrDefault(STAGE, 0);
        if (stage == 99) {
            return (String) state.get(LINES);
        }
        if (tick - start > TIMEOUT_TICKS) {
            check(state, "course_menu_timeout", false, "stage " + stage);
            return done(state);
        }
        if (mc.player == null || mc.gameMode == null) {
            return null;
        }
        switch (stage) {
            case 0 -> {
                Optional<String> server = RaceDataChat.line(mc, CourseMenuJoinCheck.RESULT);
                if (server.isEmpty()) {
                    return null;
                }
                String result = server.get().substring(CourseMenuJoinCheck.RESULT.length());
                line(state, "course_menu_server=" + result);
                if (!result.startsWith("ok ")) {
                    check(state, "course_menu_server_ok", false, result);
                    return done(state);
                }
                int at = result.indexOf("course=");
                state.put(COURSE, Integer.parseInt(result.substring(at + 7, result.indexOf(' ', at))));
                state.put(STAGE, 1);
                return null;
            }
            case 1 -> {
                int id = (int) state.get(COURSE);
                CourseData course = ClientCourses.get(id);
                if (course == null || !CourseMenuJoinCheck.NAME.equals(course.name()) || CourseToolItem.courseOf(mc.player.getMainHandItem()) != id) {
                    return null;
                }
                mc.player.setShiftKeyDown(false);
                mc.player.setXRot(-90.0F);
                mc.player.xRotO = -90.0F;
                mc.gameMode.useItem(mc.player, InteractionHand.MAIN_HAND);
                state.put(SENT, tick);
                state.put(OPEN, Boolean.TRUE);
                state.put(STAGE, 2);
                return null;
            }
            case 4 -> {
                if (!mc.player.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER)) {
                    return null;
                }
                int id = (int) state.get(COURSE);
                if (ClientCourses.get(id) == null) {
                    check(state, "course_menu_op", false, "course_gone");
                    return done(state);
                }
                CourseMenuScreen.open(null, id);
                state.put(SENT, tick);
                state.put(OPEN, Boolean.TRUE);
                state.put(STAGE, 5);
                return null;
            }
            case 7 -> {
                boolean revoked = !mc.player.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER);
                if (!revoked && tick - (int) state.get(SENT) < WAIT_TICKS) {
                    return null;
                }
                check(state, "course_menu_op_revoked", revoked, "noch Operator");
                return done(state);
            }
            default -> {
                return state.containsKey(OPEN) ? null : done(state);
            }
        }
    }

    public static void whileOpen(Map<String, Object> state, int tick, Minecraft mc) {
        int start = (int) state.computeIfAbsent(START, key -> tick);
        int stage = (int) state.getOrDefault(STAGE, 0);
        if (tick - start > TIMEOUT_TICKS || mc.player == null) {
            check(state, "course_menu_timeout", false, "stage " + stage + " im Menü");
            close(state, mc, 98);
            return;
        }
        int id = (int) state.get(COURSE);
        CourseMenuScreen menu = menu(mc, id);
        switch (stage) {
            case 2 -> {
                if (menu == null) {
                    if (tick - (int) state.get(SENT) < WAIT_TICKS) {
                        return;
                    }
                    check(state, "course_menu_tool", false, mc.gui.screen() == null ? "none" : mc.gui.screen().getClass().getSimpleName());
                    close(state, mc, 98);
                    return;
                }
                if (menu.frames() < 3) {
                    return;
                }
                check(state, "course_menu_tool", true, "");
                String wrong = readOnly(menu);
                check(state, "course_menu_b_read_only", wrong.isEmpty(), wrong);
                check(state, "course_menu_b_groups", menu.sections().equals(List.of(CourseMenu.Section.values())), String.valueOf(menu.sections()));
                tryEverything(mc, menu, id);
                state.put(SENT, tick);
                state.put(STAGE, 3);
            }
            case 3 -> {
                if (tick - (int) state.get(SENT) < WAIT_TICKS) {
                    return;
                }
                String changed = unchanged(ClientCourses.get(id));
                check(state, "course_menu_b_denied", changed.isEmpty(), changed);
                check(state, "course_menu_b_menu_open", menu != null, mc.gui.screen() == null ? "none" : mc.gui.screen().getClass().getSimpleName());
                close(state, mc, 4);
            }
            case 5 -> {
                if (menu == null || menu.frames() < 3) {
                    if (tick - (int) state.get(SENT) >= WAIT_TICKS) {
                        check(state, "course_menu_op", false, "kein Menü");
                        close(state, mc, 98);
                    }
                    return;
                }
                CourseMenu.Rights rights = menu.rights();
                boolean open = rights.edit() && rights.reset() && rights.role() == CourseMenu.Role.OPERATOR && !menu.readOnly();
                AbstractWidget mode = menu.control(CourseMenu.Row.MODE);
                AbstractWidget delete = menu.control(CourseMenu.Row.DELETE);
                check(state, "course_menu_op_editable", open && mode != null && mode.active && delete != null && delete.active, String.valueOf(rights));
                TextField name = menu.nameField();
                if (name == null) {
                    check(state, "course_menu_op_name", false, "kein Namensfeld");
                    close(state, mc, 98);
                    return;
                }
                name.setValue(CourseMenuJoinCheck.OPERATOR_NAME);
                menu.flush();
                state.put(SENT, tick);
                state.put(STAGE, 6);
            }
            case 6 -> {
                CourseData course = ClientCourses.get(id);
                boolean renamed = course != null && CourseMenuJoinCheck.OPERATOR_NAME.equals(course.name());
                if (!renamed && tick - (int) state.get(SENT) < WAIT_TICKS) {
                    return;
                }
                check(state, "course_menu_op_edit", renamed, course == null ? "gone" : course.name());
                state.put(SENT, tick);
                close(state, mc, 7);
            }
            default -> close(state, mc, 98);
        }
    }
}
