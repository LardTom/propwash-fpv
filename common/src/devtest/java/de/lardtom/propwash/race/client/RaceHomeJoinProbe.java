package de.lardtom.propwash.race.client;

import de.lardtom.propwash.drone.QuadcopterEntity;
import de.lardtom.propwash.drone.client.PilotSession;
import de.lardtom.propwash.input.InputConfig;
import de.lardtom.propwash.input.StickPositions;
import de.lardtom.propwash.race.LapTracker;
import de.lardtom.propwash.rescue.client.HomeResetInput;
import de.lardtom.propwash.sim.StabMode;
import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

import java.util.Map;

public final class RaceHomeJoinProbe {
    public static final int TIMEOUT_TICKS = 20 * 120;
    private static final String START = "racehome_start";
    private static final String PHASE = "racehome_phase";
    private static final String SINCE = "racehome_since";
    private static final String RESULT = "racehome_result";
    private static final String LINES = "racehome_lines";
    private static final String SETTING = "racehome_setting";
    private static final String AUTO_ARM = "racehome_auto_arm";
    private static final String HOME = "racehome_home";
    private static final String SENT = "racehome_sent";
    private static final int QUIET_TICKS = 100;

    private RaceHomeJoinProbe() {
    }

    private static void next(Map<String, Object> state, int tick, int phase) {
        state.put(PHASE, phase);
        state.put(SINCE, tick);
    }

    private static void line(Map<String, Object> state, String text) {
        state.put(LINES, state.getOrDefault(LINES, "") + text + "\n");
    }

    private static String finish(Map<String, Object> state, String last) {
        PilotSession.INSTANCE.setTestInput(StickPositions.IDLE, false, StabMode.ANGLE);
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

    public static @Nullable String observe(Map<String, Object> state, int tick, Minecraft mc) {
        if (state.containsKey(RESULT)) {
            return (String) state.get(RESULT);
        }
        int start = (int) state.computeIfAbsent(START, key -> tick);
        if (tick - start > TIMEOUT_TICKS) {
            return finish(state, "racehome timeout phase=" + state.getOrDefault(PHASE, 0));
        }
        PilotSession session = PilotSession.INSTANCE;
        ClientRaceTracker tracker = ClientRaceTracker.INSTANCE;
        QuadcopterEntity drone = session.isActive() ? session.drone() : null;
        int phase = (int) state.getOrDefault(PHASE, -1);
        int since = tick - (int) state.getOrDefault(SINCE, tick);
        boolean started = tracker.lobbyMember() || tracker.countdownActive() || tracker.tracker().phase() == LapTracker.Phase.RACING;
        if (phase >= 0) {
            boolean arm = phase == 1 || phase == 2 || phase == 3 || phase == 5 && since >= 10 || phase == 7 && since >= 10;
            session.setTestInput(StickPositions.IDLE, arm, StabMode.ANGLE);
        }
        switch (phase) {
            case -1 -> {
                if (drone == null || !session.isFpv() || tracker.courseId() == LapTracker.NO_COURSE) {
                    return null;
                }
                state.put(SETTING, RaceConfig.CONFIG.get().countdown);
                state.put(AUTO_ARM, InputConfig.get().autoArmAfterReset);
                InputConfig.get().autoArmAfterReset = false;
                RaceConfig.CONFIG.get().countdown = false;
                state.put(HOME, drone.position());
                line(state, "racehome course=" + tracker.courseId());
                next(state, tick, 1);
            }
            case 1 -> {
                if (drone == null || !drone.isArmed()) {
                    return since > 200 ? finish(state, "racehome arm failed") : null;
                }
                if (started) {
                    return finish(state, "racehome arm without setting started");
                }
                if (since >= 30) {
                    next(state, tick, 2);
                }
            }
            case 2 -> {
                if (!state.containsKey(SENT)) {
                    state.put(SENT, HomeResetInput.request());
                }
                if (started) {
                    return finish(state, "racehome reset started a countdown");
                }
                if (since >= QUIET_TICKS) {
                    Vec3 home = (Vec3) state.get(HOME);
                    if (drone == null || drone.isArmed() || drone.position().distanceTo(home) > 1.5) {
                        return finish(state, "racehome reset not home armed=" + (drone != null && drone.isArmed()));
                    }
                    line(state, "racehome reset no start");
                    next(state, tick, 3);
                }
            }
            case 3, 4 -> {
                if (started) {
                    return finish(state, "racehome reset started a countdown later");
                }
                if (since >= 10) {
                    next(state, tick, phase == 3 ? 4 : 5);
                }
            }
            case 5 -> {
                if (started) {
                    return finish(state, "racehome rearm without setting started");
                }
                if (since >= QUIET_TICKS) {
                    if (drone == null || !drone.isArmed()) {
                        return finish(state, "racehome rearm failed");
                    }
                    line(state, "racehome rearm no start");
                    RaceConfig.CONFIG.get().countdown = true;
                    next(state, tick, 6);
                }
            }
            case 6 -> {
                if (drone != null && !drone.isArmed()) {
                    next(state, tick, 7);
                } else if (since > 100) {
                    return finish(state, "racehome disarm failed");
                }
            }
            case 7 -> {
                if (tracker.lobbyMember() || tracker.countdownActive()) {
                    return finish(state, "racehome arm start ok");
                }
                if (since > 200) {
                    return finish(state, "racehome arm with setting no lobby");
                }
            }
            default -> {
                return finish(state, "racehome unknown phase " + phase);
            }
        }
        return null;
    }
}
