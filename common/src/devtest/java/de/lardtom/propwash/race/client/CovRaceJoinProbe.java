package de.lardtom.propwash.race.client;

import com.mojang.blaze3d.platform.InputConstants;
import de.lardtom.propwash.Constants;
import de.lardtom.propwash.dev.CovRaceJoinCheck;
import de.lardtom.propwash.dev.JoinTestEnv;
import de.lardtom.propwash.drone.QuadcopterEntity;
import de.lardtom.propwash.drone.client.PilotSession;
import de.lardtom.propwash.input.InputConfig;
import de.lardtom.propwash.input.StickPositions;
import de.lardtom.propwash.race.CourseData;
import de.lardtom.propwash.race.CourseElement;
import de.lardtom.propwash.race.GateEntity;
import de.lardtom.propwash.race.GateGeometry;
import de.lardtom.propwash.race.GateMounting;
import de.lardtom.propwash.race.GateOpenings;
import de.lardtom.propwash.race.LapTracker;
import de.lardtom.propwash.race.Passage;
import de.lardtom.propwash.rescue.client.HomeResetInput;
import de.lardtom.propwash.sim.StabMode;
import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Map;
import java.util.Optional;

public final class CovRaceJoinProbe {
    public static final int TIMEOUT_TICKS = 20 * 200;
    private static final String START = "covrace_start";
    private static final String PHASE = "covrace_phase";
    private static final String SINCE = "covrace_since";
    private static final String LINES = "covrace_lines";
    private static final String RESULT = "covrace_result";
    private static final String SEEN = "covrace_seen";
    private static final String SETTING = "covrace_setting";
    private static final String AUTO_ARM = "covrace_auto_arm";
    private static final String HOME = "covrace_home";
    private static final double EPS = 1.0E-4;

    private CovRaceJoinProbe() {
    }

    private static void next(Map<String, Object> state, int tick, int phase) {
        state.put(PHASE, phase);
        state.put(SINCE, tick);
        Constants.LOG.info("[Beitrittstest] cov-race Client: Phase {} bei Tick {}", phase, tick);
    }

    private static void line(Map<String, Object> state, String text) {
        state.put(LINES, state.getOrDefault(LINES, "") + text + "\n");
    }

    private static String finish(Map<String, Object> state, String last) {
        PilotSession.INSTANCE.clearTestInput();
        GateEditor.stop();
        if (state.containsKey(SETTING)) {
            RaceConfig.CONFIG.get().countdown = (boolean) state.get(SETTING);
        }
        if (state.containsKey(AUTO_ARM)) {
            InputConfig.get().autoArmAfterReset = (boolean) state.get(AUTO_ARM);
        }
        line(state, last);
        String result = (String) state.get(LINES);
        state.put(RESULT, result);
        return result;
    }

    private static Optional<String> chat(Minecraft mc, String prefix) {
        return RaceDataChat.line(mc, CovRaceJoinCheck.RESULT + prefix);
    }

    private static boolean startSeen() {
        ClientRaceTracker tracker = ClientRaceTracker.INSTANCE;
        return tracker.lobbyMember() || tracker.countdownActive() || !tracker.lobbyPilots().isEmpty() || tracker.lobbyCourse() != LapTracker.NO_COURSE
                || tracker.tracker().phase() == LapTracker.Phase.COUNTDOWN;
    }

    private static int id(String line, String key) {
        int at = line.indexOf(key + "=");
        if (at < 0) {
            return -1;
        }
        int end = line.indexOf(' ', at);
        try {
            return Integer.parseInt(line.substring(at + key.length() + 1, end < 0 ? line.length() : end).trim());
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    private static @Nullable GateEntity gate(Minecraft mc, int id) {
        return mc.level != null && id >= 0 && mc.level.getEntity(id) instanceof GateEntity gate && !gate.isRemoved() ? gate : null;
    }

    public static @Nullable String observe(Map<String, Object> state, int tick, Minecraft mc) {
        if (state.containsKey(RESULT)) {
            return (String) state.get(RESULT);
        }
        int start = (int) state.computeIfAbsent(START, key -> tick);
        if (tick - start > TIMEOUT_TICKS) {
            return finish(state, "covrace timeout phase=" + state.getOrDefault(PHASE, 0));
        }
        if (JoinTestEnv.covRace() == 2) {
            return restart(state, tick, mc);
        }
        int phase = (int) state.getOrDefault(PHASE, 0);
        int since = tick - (int) state.getOrDefault(SINCE, tick);
        PilotSession session = PilotSession.INSTANCE;
        QuadcopterEntity drone = session.isActive() ? session.drone() : null;
        if (phase == 0) {
            if (startSeen()) {
                state.put(SEEN, true);
            }
            Optional<String> spectator = chat(mc, "spectator ");
            if (spectator.isEmpty()) {
                return null;
            }
            boolean clean = spectator.get().contains("spectator clean") && !state.containsKey(SEEN);
            line(state, clean ? "covrace spectator no start" : "covrace spectator saw start (" + spectator.get() + ", Client " + state.containsKey(SEEN) + ")");
            line(state, "covrace spectator " + spectator.get().substring(CovRaceJoinCheck.RESULT.length()));
            next(state, tick, 1);
            return null;
        }
        if (phase >= 1 && phase <= 7) {
            boolean arm = phase == 2 || phase == 3 || phase == 6 && since >= 10;
            if (phase >= 2) {
                session.setTestInput(StickPositions.IDLE, arm, StabMode.ANGLE);
            }
            if (phase >= 2 && startSeen()) {
                return finish(state, "covrace pilot start seen in phase " + phase);
            }
        }
        switch (phase) {
            case 1 -> {
                if (drone == null || !session.isFpv() || ClientRaceTracker.INSTANCE.courseId() == LapTracker.NO_COURSE) {
                    return since > 600 ? finish(state, "covrace pilot no fpv") : null;
                }
                state.put(SETTING, RaceConfig.CONFIG.get().countdown);
                state.put(AUTO_ARM, InputConfig.get().autoArmAfterReset);
                InputConfig.get().autoArmAfterReset = false;
                RaceConfig.CONFIG.get().countdown = false;
                state.put(HOME, drone.position());
                next(state, tick, 2);
            }
            case 2 -> {
                if (drone == null || !drone.isArmed()) {
                    return since > 200 ? finish(state, "covrace pilot arm failed") : null;
                }
                if (since >= 30) {
                    HomeResetInput.request();
                    next(state, tick, 3);
                }
            }
            case 3 -> {
                if (since >= 100) {
                    Vec3 home = (Vec3) state.get(HOME);
                    if (drone == null || drone.isArmed() || drone.position().distanceTo(home) > 1.5) {
                        return finish(state, "covrace pilot reset not home");
                    }
                    line(state, "covrace pilot reset no start");
                    next(state, tick, 4);
                }
            }
            case 4, 5 -> {
                if (since >= 10) {
                    next(state, tick, phase == 4 ? 5 : 6);
                }
            }
            case 6 -> {
                if (since >= 110) {
                    if (drone == null || !drone.isArmed()) {
                        return finish(state, "covrace pilot rearm failed");
                    }
                    line(state, "covrace pilot rearm no start");
                    next(state, tick, 7);
                }
            }
            case 7 -> {
                session.setTestInput(StickPositions.IDLE, false, StabMode.ANGLE);
                Optional<String> pilot = chat(mc, "pilot ");
                if (pilot.isEmpty()) {
                    return null;
                }
                session.clearTestInput();
                line(state, pilot.get().contains("pilot clean") ? "covrace server lobby clean" : "covrace server lobby seen " + pilot.get());
                state.put("covrace_gb", id(pilot.get(), "gb"));
                state.put("covrace_kn", id(pilot.get(), "kn"));
                next(state, tick, 8);
            }
            case 8 -> {
                GateEntity theirs = gate(mc, (int) state.get("covrace_gb"));
                if (session.isActive() || mc.player == null || !GateMounting.holdsTool(mc.player) || theirs == null) {
                    return since > 300 ? finish(state, "covrace edit not ready session=" + session.isActive() + " tool="
                            + (mc.player != null && GateMounting.holdsTool(mc.player)) + " gate=" + (theirs != null) + " screen=" + mc.gui.screen())
                            : null;
                }
                state.put("covrace_gb_at", theirs.position());
                mc.player.setYRot(0.0F);
                mc.player.yRotO = 0.0F;
                GateEditor.start(theirs);
                GateEditor editor = GateEditor.current();
                if (editor == null) {
                    line(state, "covrace guest refused (Menü öffnet nicht)");
                    next(state, tick, 10);
                    return null;
                }
                editor.set(GateEditModel.Row.MOVE_X, 1.0);
                editor.apply();
                next(state, tick, 9);
            }
            case 9 -> {
                GateEntity theirs = gate(mc, (int) state.get("covrace_gb"));
                Vec3 before = (Vec3) state.get("covrace_gb_at");
                if (theirs == null || theirs.position().distanceTo(before) > EPS) {
                    return finish(state, "covrace guest moved " + (theirs == null ? "-" : theirs.position().subtract(before)));
                }
                if (since >= 80) {
                    line(state, "covrace guest refused");
                    next(state, tick, 10);
                }
            }
            case 10 -> {
                GateEntity mine = gate(mc, (int) state.get("covrace_kn"));
                if (mine == null || mc.player == null) {
                    return finish(state, "covrace own gate missing");
                }
                GateEditor.stop();
                state.put("covrace_kn_at", mine.position());
                mc.player.setYRot(0.0F);
                mc.player.yRotO = 0.0F;
                RaceConfig.CONFIG.get().nudgeStep = GateEditModel.DEFAULT_NUDGE_STEP;
                GateEditor.start(mine);
                GateEditor editor = GateEditor.current();
                if (editor == null) {
                    return finish(state, "covrace own editor missing");
                }
                GateEditDriver.press(mc, InputConstants.KEY_RIGHT);
                GateEditDriver.press(mc, InputConstants.KEY_RIGHT);
                GateEditDriver.press(mc, InputConstants.KEY_PAGEUP);
                GateEditModel model = editor.model();
                if (Math.abs(model.dx() - CovRaceJoinCheck.NUDGE_DX) > EPS || Math.abs(model.dy() - CovRaceJoinCheck.NUDGE_DY) > EPS
                        || Math.abs(model.dz()) > EPS) {
                    return finish(state, "covrace keys moved " + model.dx() + "/" + model.dy() + "/" + model.dz());
                }
                GateEditDriver.press(mc, InputConstants.KEY_RETURN);
                next(state, tick, 11);
            }
            case 11 -> {
                GateEntity mine = gate(mc, (int) state.get("covrace_kn"));
                Vec3 before = (Vec3) state.get("covrace_kn_at");
                Vec3 shift = mine == null ? Vec3.ZERO : mine.position().subtract(before);
                if (Math.abs(shift.x - CovRaceJoinCheck.NUDGE_DX) > EPS || Math.abs(shift.y - CovRaceJoinCheck.NUDGE_DY) > EPS) {
                    return since > 200 ? finish(state, "covrace nudge not applied " + shift) : null;
                }
                line(state, "covrace nudged");
                next(state, tick, 12);
            }
            default -> {
                Optional<String> saved = chat(mc, "phase1 saved");
                if (saved.isEmpty()) {
                    return null;
                }
                String text = saved.get();
                boolean ok = text.contains("nudge=ok") && text.contains("record=true") && text.contains("guest=unchanged");
                return finish(state, (ok ? "covrace phase1 saved" : "covrace phase1 wrong") + " | " + text.substring(CovRaceJoinCheck.RESULT.length()));
            }
        }
        return null;
    }

    private static @Nullable String restart(Map<String, Object> state, int tick, Minecraft mc) {
        Optional<String> result = chat(mc, "restart ");
        if (result.isEmpty()) {
            return null;
        }
        String text = result.get();
        if (!text.contains("restart ok")) {
            return finish(state, "covrace restart server failed | " + text);
        }
        int course = id(text, "course");
        CourseData data = ClientCourses.get(course);
        if (data == null || data.gates().size() != 3) {
            int since = (int) state.computeIfAbsent("covrace_wait", key -> tick);
            return tick - since > 400 ? finish(state, "covrace restart course missing " + course) : null;
        }
        CourseData.Gate pole = data.gates().get(1);
        CourseData.Gate moved = data.gates().get(2);
        if (pole.kind() != CourseElement.GATE_POLE || pole.scoringKind() != CourseElement.POLE) {
            return finish(state, "covrace restart pole not scoring " + pole.kind() + " " + pole.scoringKind());
        }
        if (Math.abs(moved.y() - Math.floor(moved.y()) - CovRaceJoinCheck.NUDGE_DY) > EPS) {
            return finish(state, "covrace restart moved gate y " + moved.y());
        }
        List<GateGeometry> gates = data.geometry();
        List<Vec3> path = CovRaceSite.waypointPath(gates, 3.0);
        String problem = CovRaceSite.clientLap(course, gates, path, GateOpenings.NONE);
        if (problem != null) {
            return finish(state, "covrace restart client lap " + problem);
        }
        Passage stage = gates.get(1).stages().getFirst();
        double out = stage.side1() + 0.6;
        Vec3 beside = new Vec3(stage.ox() + stage.sx() * out, gates.get(1).y() + 1.5, stage.oz() + stage.sz() * out);
        List<Vec3> wide = CovRaceSite.waypointPath(gates, 3.0, 1, beside, new Vec3(stage.nx(), 0.0, stage.nz()));
        List<LapTracker.Event> events = CovRaceSite.fly(course, gates, wide, GateOpenings.NONE);
        if (CovRaceSite.of(events, LapTracker.EventType.GATE).contains(1) || !CovRaceSite.of(events, LapTracker.EventType.LAP).isEmpty()) {
            return finish(state, "covrace restart beside counts " + events);
        }
        line(state, "covrace restart course ok");
        return finish(state, "covrace restart lap ok | " + text.substring(CovRaceJoinCheck.RESULT.length()));
    }
}
