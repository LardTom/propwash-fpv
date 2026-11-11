package de.lardtom.propwash.race.client;

import de.lardtom.propwash.dev.CreativeFcJoinCheck;
import de.lardtom.propwash.network.client.ClientNet;
import de.lardtom.propwash.race.CourseData;
import de.lardtom.propwash.race.CreativeFcRule;
import de.lardtom.propwash.race.RacePayloads;
import de.lardtom.propwash.server.client.ClientWorldRules;
import de.lardtom.propwash.ui.client.widget.FlatButton;
import net.minecraft.client.Minecraft;
import org.jspecify.annotations.Nullable;

import java.util.Map;
import java.util.Optional;

public final class CreativeFcJoinProbe {
    public static final int TIMEOUT_TICKS = 20 * 150;
    public static final String KEY = "creative_fc";
    private static final String START = "creative_fc_start";
    private static final String LINES = "creative_fc_lines";
    private static final String STAGE = "creative_fc_stage";
    private static final String COURSE = "creative_fc_course";
    private static final String FAILED = "creative_fc_failed";
    public static final String OPEN = "creative_fc_open";

    private CreativeFcJoinProbe() {
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

    private static String done(Map<String, Object> state, Minecraft mc) {
        state.put(STAGE, 99);
        if (mc.gui.screen() instanceof CourseMenuScreen) {
            mc.gui.setScreen(null);
        }
        Object failed = state.get(FAILED);
        line(state, failed == null ? "creative_fc=ok" : "creative_fc=fail " + failed);
        return (String) state.get(LINES);
    }

    private static @Nullable CourseMenuScreen menu(Minecraft mc, int course) {
        return mc.gui.screen() instanceof CourseMenuScreen menu && menu.courseId() == course && menu.frames() >= 3 ? menu : null;
    }

    private static void open(Map<String, Object> state, int course, int stage) {
        CourseMenuScreen.open(null, course);
        state.put(OPEN, Boolean.TRUE);
        state.put(STAGE, stage);
    }

    private static void close(Map<String, Object> state, Minecraft mc, int stage) {
        if (mc.gui.screen() instanceof CourseMenuScreen) {
            mc.gui.setScreen(null);
        }
        state.remove(OPEN);
        state.put(STAGE, stage);
    }

    private static String chips(CourseMenuScreen menu) {
        StringBuilder out = new StringBuilder();
        for (CreativeFcRule rule : CreativeFcRule.values()) {
            FlatButton chip = menu.creativeButton(rule);
            out.append(rule.key()).append(':').append(chip == null ? "none" : chip.active ? "active" : "locked").append(' ');
        }
        return out.toString().trim();
    }

    public static void whileOpen(Map<String, Object> state, int tick, Minecraft mc) {
        int start = (int) state.computeIfAbsent(START, key -> tick);
        int stage = (int) state.getOrDefault(STAGE, 0);
        if (tick - start > TIMEOUT_TICKS) {
            check(state, "creative_fc_menu_timeout", false, "stage " + stage);
            close(state, mc, 98);
            return;
        }
        int id = (int) state.get(COURSE);
        CourseMenuScreen menu = menu(mc, id);
        if (menu == null) {
            if (!(mc.gui.screen() instanceof CourseMenuScreen)) {
                CourseMenuScreen.open(null, id);
            }
            return;
        }
        String shown = chips(menu);
        if (stage == 2) {
            check(state, "creative_fc_owner_locked", !menu.rights().admin() && menu.rights().edit() && shown.equals("world:locked assist:locked legal:locked"),
                    shown + " rights=" + menu.rights());
            check(state, "creative_fc_owner_menu_refuses", !menu.setCreativeFc(CreativeFcRule.LEGAL), "menu sent");
            ClientNet.sendToServer(new RacePayloads.CourseCreativeFcC2S(id, CreativeFcRule.LEGAL));
            close(state, mc, 3);
            return;
        }
        check(state, "creative_fc_admin_unlocked", menu.rights().admin() && shown.equals("world:active assist:active legal:active"), shown);
        check(state, "creative_fc_admin_menu_sends", menu.setCreativeFc(CreativeFcRule.LEGAL), "not sent");
        close(state, mc, 5);
    }

    public static @Nullable String observe(Map<String, Object> state, int tick, Minecraft mc) {
        int start = (int) state.computeIfAbsent(START, key -> tick);
        int stage = (int) state.getOrDefault(STAGE, 0);
        if (stage == 99) {
            return (String) state.get(LINES);
        }
        if (stage == 98 || tick - start > TIMEOUT_TICKS) {
            check(state, "creative_fc_timeout", false, "stage " + stage);
            return done(state, mc);
        }
        if (mc.player == null) {
            return null;
        }
        Optional<String> result = RaceDataChat.line(mc, CreativeFcJoinCheck.RESULT + CreativeFcJoinCheck.DONE);
        if (stage < 5 && result.isPresent()) {
            check(state, "creative_fc_server", false, result.get());
            return done(state, mc);
        }
        switch (stage) {
            case 0 -> {
                Optional<String> ready = RaceDataChat.line(mc, CreativeFcJoinCheck.RESULT + CreativeFcJoinCheck.READY);
                if (ready.isEmpty()) {
                    return null;
                }
                state.put(COURSE, Integer.parseInt(ready.get().substring((CreativeFcJoinCheck.RESULT + CreativeFcJoinCheck.READY).length()).trim()));
                state.put(STAGE, 1);
                return null;
            }
            case 1 -> {
                int id = (int) state.get(COURSE);
                CourseData course = ClientCourses.get(id);
                if (course != null && ClientWorldRules.INSTANCE.known()) {
                    check(state, "creative_fc_synced_world", course.creativeFc() == CreativeFcRule.WORLD && !ClientCreativeFc.legal(id),
                            course.creativeFc().key());
                    open(state, id, 2);
                }
                return null;
            }
            case 3 -> {
                if (RaceDataChat.line(mc, CreativeFcJoinCheck.RESULT + CreativeFcJoinCheck.OPPED).isEmpty() || !ClientWorldRules.INSTANCE.editable()) {
                    return null;
                }
                int id = (int) state.get(COURSE);
                CourseData course = ClientCourses.get(id);
                check(state, "creative_fc_owner_rejected", course != null && course.creativeFc() == CreativeFcRule.WORLD,
                        course == null ? "gone" : course.creativeFc().key());
                open(state, id, 4);
                return null;
            }
            case 5 -> {
                if (result.isEmpty()) {
                    return null;
                }
                String text = result.get().substring((CreativeFcJoinCheck.RESULT + CreativeFcJoinCheck.DONE).length());
                line(state, "creative_fc_server=" + text);
                check(state, "creative_fc_server_ok", text.startsWith("ok "), text);
                state.put(STAGE, 6);
                return null;
            }
            case 6 -> {
                int id = (int) state.get(COURSE);
                CourseData course = ClientCourses.get(id);
                if (course == null || course.creativeFc() != CreativeFcRule.LEGAL) {
                    return null;
                }
                check(state, "creative_fc_client_legal", ClientCreativeFc.legal(id) && !ClientCreativeFc.world(), "legal=" + ClientCreativeFc.legal(id));
                return done(state, mc);
            }
            default -> {
                return null;
            }
        }
    }
}
