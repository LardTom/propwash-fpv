package de.lardtom.propwash.drone.client;

import net.minecraft.client.Minecraft;
import org.jspecify.annotations.Nullable;

import java.util.Locale;
import java.util.Map;

public final class CovLandingJoinProbe {
    public static final int TIMEOUT_TICKS = 20 * 240;
    public static final int LANDINGS = 10;
    private static final String KEY = "cov_landing_phase";
    private static final double CLIMB = 2.5;
    private static final double DESCENT = 0.6;
    private static final double UPRIGHT_DEG = 30.0;
    private static final double SNAP_DEG = 90.0;
    private static final int REST_TICKS = 20;

    private CovLandingJoinProbe() {
    }

    public static @Nullable String observe(Map<String, Object> state, int tick, Minecraft mc) {
        int start = (int) state.computeIfAbsent("cov_landing_start", key -> tick);
        String phase = CovJoinFlight.phase(state, KEY, "wait");
        int since = CovJoinFlight.since(state, KEY, tick);
        if (tick - start > TIMEOUT_TICKS) {
            return report(state, "timeout in " + phase);
        }
        if (!phase.equals("wait") && !CovJoinFlight.flying()) {
            return report(state, "session ended in " + phase);
        }
        int landings = (int) state.getOrDefault("cov_landing_count", 0);
        switch (phase) {
            case "wait" -> {
                if (CovJoinFlight.flying()) {
                    state.put("cov_landing_ground", CovJoinFlight.y());
                    CovJoinFlight.next(state, KEY, "arm", tick);
                }
                return null;
            }
            case "arm" -> {
                CovJoinFlight.sticks(0.0F, 0.0F, 0.0F, 0.0F, since > 5);
                if (since > 15 && CovJoinFlight.telemetry().armed) {
                    CovJoinFlight.next(state, KEY, "climb", tick);
                }
                return since > 200 ? report(state, "not armed: " + CovJoinFlight.telemetry().armBlockers) : null;
            }
            case "climb" -> {
                double ground = (double) state.get("cov_landing_ground");
                CovJoinFlight.hold(ground + CLIMB, 0.0F);
                if (CovJoinFlight.y() - ground > CLIMB - 0.3 && since > 30) {
                    CovJoinFlight.next(state, KEY, "descend", tick);
                }
                return null;
            }
            case "descend" -> {
                double target = CovJoinFlight.y() - DESCENT / 1.2;
                CovJoinFlight.sticks(0.0F, 0.0F, 0.0F, Math.min(CovJoinFlight.holdThrottle(target, 0.0),
                        (float) CovJoinFlight.telemetry().hoverThrottleStick), true);
                if (CovJoinFlight.telemetry().grounded) {
                    state.put("cov_landing_prev", CovJoinFlight.tiltDeg());
                    CovJoinFlight.next(state, KEY, "rest", tick);
                }
                return since > 600 ? report(state, "no touchdown") : null;
            }
            case "rest" -> {
                CovJoinFlight.sticks(0.0F, 0.0F, 0.0F, 0.0F, true);
                double tilt = CovJoinFlight.tiltDeg();
                double prev = (double) state.get("cov_landing_prev");
                if (Math.abs(tilt - prev) > SNAP_DEG) {
                    state.merge("cov_landing_snap", 1, (a, b) -> (int) a + (int) b);
                }
                state.put("cov_landing_prev", tilt);
                if (since < REST_TICKS) {
                    return null;
                }
                if (tilt < UPRIGHT_DEG) {
                    state.merge("cov_landing_upright", 1, (a, b) -> (int) a + (int) b);
                }
                state.merge("cov_landing_max", tilt, (a, b) -> Math.max((double) a, (double) b));
                state.put("cov_landing_count", landings + 1);
                CovJoinFlight.next(state, KEY, "disarm", tick);
                return null;
            }
            case "disarm" -> {
                CovJoinFlight.sticks(0.0F, 0.0F, 0.0F, 0.0F, false);
                if (since < 10) {
                    return null;
                }
                if ((int) state.get("cov_landing_count") >= LANDINGS) {
                    return report(state, "done");
                }
                CovJoinFlight.next(state, KEY, "arm", tick);
                return null;
            }
            default -> {
                return report(state, "unknown phase " + phase);
            }
        }
    }

    private static String report(Map<String, Object> state, String end) {
        PilotSession.INSTANCE.clearTestInput();
        int count = (int) state.getOrDefault("cov_landing_count", 0);
        int upright = (int) state.getOrDefault("cov_landing_upright", 0);
        int snap = (int) state.getOrDefault("cov_landing_snap", 0);
        double max = (double) state.getOrDefault("cov_landing_max", Double.NaN);
        return "landing_upright=" + upright + "/" + LANDINGS + "\nlanding_snapback=" + snap + "\n"
                + String.format(Locale.ROOT, "landing_detail %d landings, max tilt after 1 s %.1f deg, %s%n", count, max, end);
    }
}
