package de.lardtom.propwash.stats.client;

import de.lardtom.propwash.api.part.FrameRole;
import de.lardtom.propwash.dev.FpvAdvancementCases;
import de.lardtom.propwash.dev.StatsJoinCheck;
import de.lardtom.propwash.drone.QuadcopterEntity;
import de.lardtom.propwash.drone.client.PilotSession;
import de.lardtom.propwash.guide.training.Curriculum;
import de.lardtom.propwash.network.client.ClientNet;
import de.lardtom.propwash.race.client.RaceDataChat;
import de.lardtom.propwash.stats.PropwashStats;
import de.lardtom.propwash.stats.StatFormats;
import de.lardtom.propwash.stats.StatNames;
import de.lardtom.propwash.stats.net.GhostBeatenC2S;
import de.lardtom.propwash.stats.net.TrainingC2S;
import de.lardtom.propwash.stats.net.TrickC2S;
import de.lardtom.propwash.trick.TrickType;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.advancements.AdvancementProgress;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.ObjectSelectionList;
import net.minecraft.client.gui.screens.achievement.StatsScreen;
import net.minecraft.client.multiplayer.ClientAdvancements;
import net.minecraft.stats.StatsCounter;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

public final class StatsJoinProbe {
    public static final int TIMEOUT_TICKS = 20 * 90;
    public static final String OPEN = "stats_join_open";
    private static final String START = "stats_join_start";
    private static final String PHASE = "stats_join_phase";
    private static final String SINCE = "stats_join_since";
    private static final String RESULT = "stats_join_result";
    private static final String CRASHES = "stats_join_crashes";
    private static final String BASE = "stats_join_base";
    private static final String SERVER = "stats_join_server";
    private static final String DELTA = "stats_join_delta";
    private static final String REPORTS = "stats_join_reports";
    private static final double DROP_HEIGHT = 10.0;
    private static final int REPORT_TICK = 20;
    private static final int COMBO = 5;
    private static final int GHOST_COURSE = 1;

    private StatsJoinProbe() {
    }

    private static @Nullable String next(Map<String, Object> state, int tick, int phase) {
        state.put(PHASE, phase);
        state.put(SINCE, tick);
        return null;
    }

    private static String finish(Map<String, Object> state, Minecraft mc, String line) {
        PilotSession.INSTANCE.clearTestInput();
        if (mc.gui.screen() instanceof StatsScreen) {
            mc.gui.setScreen(null);
        }
        String result = line + "\n";
        state.remove(OPEN);
        state.put(RESULT, result);
        return result;
    }

    private static boolean clientReports(QuadcopterEntity drone) {
        boolean sent = ClientNet.sendToServer(new TrickC2S(drone.getId(), TrickType.FLIP.key(), 0));
        sent &= ClientNet.sendToServer(new TrickC2S(drone.getId(), TrickType.POWER_LOOP.key(), 0));
        sent &= ClientNet.sendToServer(new TrickC2S(drone.getId(), "", COMBO));
        sent &= ClientNet.sendToServer(new TrainingC2S(Curriculum.chapters().size(), true));
        sent &= ClientNet.sendToServer(new GhostBeatenC2S(GHOST_COURSE));
        return sent;
    }

    private static int shownDone(Minecraft mc) {
        if (mc.getConnection() == null) {
            return 0;
        }
        ClientAdvancements advancements = mc.getConnection().getAdvancements();
        int done = 0;
        for (String name : FpvAdvancementCases.NAMES) {
            AdvancementHolder holder = advancements.get(FpvAdvancementCases.id(name));
            AdvancementProgress progress = holder == null ? null : advancements.progress().get(holder);
            if (progress != null && progress.isDone()) {
                done++;
            }
        }
        return done;
    }

    @SuppressWarnings("unchecked")
    public static @Nullable String observe(Map<String, Object> state, int tick, Minecraft mc) {
        if (state.containsKey(RESULT)) {
            return (String) state.get(RESULT);
        }
        int start = (int) state.computeIfAbsent(START, key -> tick);
        int phase = (int) state.getOrDefault(PHASE, -1);
        if (tick - start > TIMEOUT_TICKS) {
            return finish(state, mc, "stats timeout phase=" + phase);
        }
        int since = tick - (int) state.getOrDefault(SINCE, tick);
        PilotSession session = PilotSession.INSTANCE;
        QuadcopterEntity drone = session.isActive() ? session.drone() : null;
        switch (phase) {
            case -1 -> {
                if (drone == null || !session.isFpv()) {
                    return null;
                }
                StatsFlight.ground(drone.getY());
                return next(state, tick, 0);
            }
            case 0 -> {
                if (drone == null) {
                    return finish(state, mc, "stats session lost before arming");
                }
                StatsFlight.arm(since);
                if (since >= StatsFlight.ARM_TICKS && StatsFlight.armed()) {
                    return next(state, tick, 1);
                }
                return since > 200 ? finish(state, mc, "stats arm failed") : null;
            }
            case 1 -> {
                if (drone == null) {
                    return finish(state, mc, "stats session lost in flight");
                }
                StatsFlight.fly(DROP_HEIGHT, since < 60 ? 0.3F : 0.0F);
                if (since >= REPORT_TICK && !state.containsKey(REPORTS)) {
                    state.put(REPORTS, Boolean.TRUE);
                    if (!clientReports(drone)) {
                        return finish(state, mc, "stats client reports not sent");
                    }
                }
                if (since > 80 && StatsFlight.height() > DROP_HEIGHT - 0.8) {
                    state.put(BASE, session.crashCount());
                    StatsFlight.disarm();
                    return next(state, tick, 2);
                }
                return since > 500 ? finish(state, mc, "stats climb failed height=" + String.format(Locale.ROOT, "%.2f", StatsFlight.height())) : null;
            }
            case 2 -> {
                StatsFlight.disarm();
                int crashes = session.crashCount() - (int) state.get(BASE);
                if (crashes > 0 && (drone == null || StatsFlight.height() < 0.5)) {
                    return next(state, tick, 3);
                }
                return since > 300 ? finish(state, mc, "stats no crash osd=" + crashes) : null;
            }
            case 3 -> {
                Optional<String> line = RaceDataChat.line(mc, StatsJoinCheck.SERVER);
                if (line.isEmpty()) {
                    return since > 400 ? finish(state, mc, "stats server never reported") : null;
                }
                state.put(CRASHES, session.crashCount() - (int) state.get(BASE));
                String text = line.get().substring(StatsJoinCheck.SERVER.length());
                int bar = text.indexOf(" | delta ");
                state.put(SERVER, StatsJoinCheck.decode(bar < 0 ? text : text.substring(0, bar)));
                state.put(DELTA, bar < 0 ? Map.of() : StatsJoinCheck.decode(text.substring(bar + " | delta ".length())));
                if (mc.player == null) {
                    return finish(state, mc, "stats no player");
                }
                state.put(OPEN, Boolean.TRUE);
                mc.gui.setScreen(new StatsScreen(null, mc.player.getStats()));
                return next(state, tick, 4);
            }
            case 4 -> {
                return null;
            }
            default -> {
                return finish(state, mc, "stats unknown phase " + phase);
            }
        }
    }

    @SuppressWarnings("unchecked")
    public static void whileOpen(Map<String, Object> state, int tick, Minecraft mc) {
        int since = tick - (int) state.getOrDefault(SINCE, tick);
        ObjectSelectionList<?> list = StatsSelfTests.generalList(mc.gui.screen());
        if (list == null || mc.player == null) {
            if (since > 400) {
                finish(state, mc, "stats window did not load");
            }
            return;
        }
        Map<String, Integer> server = (Map<String, Integer>) state.get(SERVER);
        Map<String, Integer> delta = (Map<String, Integer>) state.get(DELTA);
        StatsCounter stats = mc.player.getStats();
        List<String> problems = new ArrayList<>();
        for (PropwashStats.Entry entry : PropwashStats.all()) {
            int client = stats.getValue(entry.stat());
            Integer expected = server.get(entry.name());
            if (expected == null || client != expected) {
                problems.add(entry.name() + " client=" + client + " server=" + expected);
            }
        }
        List<Integer> rows = StatsSelfTests.rows(list, stats, problems);
        if (rows.size() != PropwashStats.all().size() || rows.getLast() - rows.getFirst() != rows.size() - 1) {
            problems.add("rows " + rows);
        }
        int osd = (int) state.get(CRASHES);
        if (delta.getOrDefault(StatNames.CRASHES, 0) != osd) {
            problems.add("crashes server=" + delta.getOrDefault(StatNames.CRASHES, 0) + " osd=" + osd);
        }
        if (delta.getOrDefault(StatNames.ARMS, 0) != 1 || delta.getOrDefault(StatNames.FLIGHT_TIME, 0) <= 0
                || delta.getOrDefault(StatNames.DISTANCE, 0) <= 0 || server.getOrDefault(StatNames.TOP_SPEED, 0) <= 0
                || !delta.getOrDefault(StatNames.FLIGHT_TIME, 0).equals(delta.getOrDefault(StatNames.classTime(FrameRole.FREESTYLE), -1))) {
            problems.add("flight not counted " + delta);
        }
        int names = FpvAdvancementCases.NAMES.size();
        if (delta.getOrDefault(StatsJoinCheck.FPV_LOADED, 0) != names || delta.getOrDefault(StatsJoinCheck.FPV_FLOWN, 0) != 1
                || delta.getOrDefault(StatsJoinCheck.FPV_DONE, 0) != names) {
            problems.add("advancements server " + delta.get(StatsJoinCheck.FPV_LOADED) + " loaded, flown " + delta.get(StatsJoinCheck.FPV_FLOWN)
                    + ", done " + delta.get(StatsJoinCheck.FPV_DONE) + " of " + names);
        }
        int shown = shownDone(mc);
        if (shown != names) {
            problems.add("advancements tab " + shown + " of " + names);
        }
        if (!problems.isEmpty()) {
            finish(state, mc, "stats fail " + String.join("; ", problems));
            return;
        }
        finish(state, mc, "stats ok window=server " + StatsJoinCheck.encode(delta) + " top="
                + StatFormats.speed(server.getOrDefault(StatNames.TOP_SPEED, 0)) + " time="
                + PropwashStats.FLIGHT_TIME.stat().format(server.getOrDefault(StatNames.FLIGHT_TIME, 0)) + " rows=" + rows.getFirst() + "-"
                + rows.getLast() + " of " + list.children().size());
    }
}
