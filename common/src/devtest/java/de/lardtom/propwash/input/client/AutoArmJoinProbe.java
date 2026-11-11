package de.lardtom.propwash.input.client;

import de.lardtom.propwash.dev.AutoArmJoinCheck;
import de.lardtom.propwash.drone.QuadcopterEntity;
import de.lardtom.propwash.drone.client.PilotSession;
import de.lardtom.propwash.input.InputConfig;
import de.lardtom.propwash.input.StickPositions;
import de.lardtom.propwash.osd.OsdData;
import de.lardtom.propwash.osd.OsdWarnings;
import de.lardtom.propwash.osd.client.OsdHud;
import de.lardtom.propwash.race.LapTracker;
import de.lardtom.propwash.race.client.ClientRaceTracker;
import de.lardtom.propwash.race.client.RaceConfig;
import de.lardtom.propwash.race.client.RaceDataChat;
import de.lardtom.propwash.rescue.client.HomeResetInput;
import de.lardtom.propwash.sim.StabMode;
import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public final class AutoArmJoinProbe {
    public static final int TIMEOUT_TICKS = 20 * 90;
    private static final String START = "autoarm_start";
    private static final String PHASE = "autoarm_phase";
    private static final String SINCE = "autoarm_since";
    private static final String RESULT = "autoarm_result";
    private static final String LINES = "autoarm_lines";
    private static final String COUNTDOWN = "autoarm_countdown";
    private static final String AUTO_ARM = "autoarm_setting";
    private static final String HOME = "autoarm_home";
    private static final String SENT = "autoarm_sent";
    private static final StickPositions HIGH = new StickPositions(0.0F, 0.0F, 0.0F, 0.35F);
    private static final int WAIT_TICKS = 40;
    private static final int QUIET_TICKS = 80;

    private AutoArmJoinProbe() {
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
        if (state.containsKey(COUNTDOWN)) {
            RaceConfig.CONFIG.get().countdown = (boolean) state.get(COUNTDOWN);
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
            return finish(state, "autoarm timeout phase=" + state.getOrDefault(PHASE, -1));
        }
        PilotSession session = PilotSession.INSTANCE;
        ClientRaceTracker tracker = ClientRaceTracker.INSTANCE;
        QuadcopterEntity drone = session.isActive() ? session.drone() : null;
        int phase = (int) state.getOrDefault(PHASE, -1);
        int since = tick - (int) state.getOrDefault(SINCE, tick);
        boolean started = tracker.lobbyMember() || tracker.countdownActive() || tracker.tracker().phase() == LapTracker.Phase.RACING;
        if (phase >= 1 && phase <= 5) {
            session.setTestInput(phase == 2 || phase == 3 ? HIGH : StickPositions.IDLE, true, StabMode.ANGLE);
        }
        switch (phase) {
            case -1 -> {
                if (drone == null || !session.isFpv() || tracker.courseId() == LapTracker.NO_COURSE) {
                    return null;
                }
                state.put(COUNTDOWN, RaceConfig.CONFIG.get().countdown);
                state.put(AUTO_ARM, InputConfig.get().autoArmAfterReset);
                RaceConfig.CONFIG.get().countdown = false;
                InputConfig.get().autoArmAfterReset = true;
                state.put(HOME, drone.position());
                line(state, "autoarm course=" + tracker.courseId());
                next(state, tick, 1);
            }
            case 1 -> {
                if (drone == null || !drone.isArmed()) {
                    return since > 200 ? finish(state, "autoarm arm failed") : null;
                }
                if (since >= 30) {
                    RaceConfig.CONFIG.get().countdown = true;
                    next(state, tick, 2);
                }
            }
            case 2 -> {
                if (drone == null || !drone.isArmed()) {
                    return finish(state, "autoarm disarmed before reset");
                }
                if (since >= 15) {
                    state.put(SENT, HomeResetInput.request());
                    next(state, tick, 3);
                }
            }
            case 3 -> {
                Vec3 home = (Vec3) state.get(HOME);
                if (drone == null) {
                    return finish(state, "autoarm session lost");
                }
                boolean back = !drone.isArmed() && drone.position().distanceTo(home) < 1.5 && session.autoArmPending();
                if (!back) {
                    if (state.containsKey("autoarm_back")) {
                        return finish(state, "autoarm armed with throttle up armed=" + drone.isArmed() + " waiting=" + session.autoArmPending());
                    }
                    return since > 100 ? finish(state, "autoarm reset not home sent=" + state.get(SENT) + " armed=" + drone.isArmed()) : null;
                }
                if (!state.containsKey("autoarm_back")) {
                    state.put("autoarm_back", tick);
                }
                if (tick - (int) state.get("autoarm_back") < WAIT_TICKS) {
                    return null;
                }
                OsdData osd = OsdHud.sampler().data();
                List<OsdWarnings.Warning> warnings = new ArrayList<>();
                OsdWarnings.collect(osd, warnings);
                line(state, "autoarm waiting throttle up " + WAIT_TICKS + " ticks osd=" + (osd.autoArmPending && warnings.contains(OsdWarnings.Warning.AUTO_ARM)));
                next(state, tick, 4);
            }
            case 4 -> {
                if (started) {
                    return finish(state, "autoarm reset started a countdown");
                }
                if (drone == null || !drone.isArmed() || !session.autoArmed()) {
                    return since > 60 ? finish(state, "autoarm throttle down did not arm") : null;
                }
                line(state, "autoarm armed after reset");
                next(state, tick, 5);
            }
            case 5 -> {
                if (started) {
                    return finish(state, "autoarm armed by reset started a countdown");
                }
                if (since < QUIET_TICKS) {
                    return null;
                }
                Optional<String> server = RaceDataChat.line(mc, AutoArmJoinCheck.RESULT + "armed after reset");
                if (server.isEmpty()) {
                    return since > QUIET_TICKS + 200 ? finish(state, "autoarm server never saw armed") : null;
                }
                line(state, "autoarm no countdown");
                String text = server.get().substring(AutoArmJoinCheck.RESULT.length());
                return finish(state, "autoarm server " + text);
            }
            default -> {
                return finish(state, "autoarm unknown phase " + phase);
            }
        }
        return null;
    }
}
