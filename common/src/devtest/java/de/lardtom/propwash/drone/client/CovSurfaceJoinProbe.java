package de.lardtom.propwash.drone.client;

import de.lardtom.propwash.sim.DroneSim;
import de.lardtom.propwash.sim.SimSnapshot;
import net.minecraft.client.Minecraft;
import org.jspecify.annotations.Nullable;

import java.util.Locale;
import java.util.Map;

public final class CovSurfaceJoinProbe {
    public static final int TIMEOUT_TICKS = 20 * 120;
    private static final String KEY = "cov_surface_phase";
    private static final double DROP = 10.0;
    private static final double BOUNCE = 1.0;
    private static final double WALL_SPEED = 12.0;

    private CovSurfaceJoinProbe() {
    }

    private static double vx() {
        DroneSim sim = PilotSession.INSTANCE.sim();
        if (sim == null) {
            return Double.NaN;
        }
        SimSnapshot snapshot = new SimSnapshot();
        sim.copyStateTo(snapshot);
        return snapshot.vx;
    }

    public static @Nullable String observe(Map<String, Object> state, int tick, Minecraft mc) {
        int start = (int) state.computeIfAbsent("cov_surface_start", key -> tick);
        String phase = CovJoinFlight.phase(state, KEY, "wait");
        int since = CovJoinFlight.since(state, KEY, tick);
        if (tick - start > TIMEOUT_TICKS) {
            return report(state, "timeout in " + phase);
        }
        if (!phase.equals("wait") && !CovJoinFlight.flying()) {
            return report(state, "session ended in " + phase);
        }
        PilotSession session = PilotSession.INSTANCE;
        switch (phase) {
            case "wait" -> {
                if (CovJoinFlight.flying()) {
                    state.put("cov_surface_ground", CovJoinFlight.y());
                    state.put("cov_surface_crashes0", session.crashCount());
                    CovJoinFlight.next(state, KEY, "arm", tick);
                }
                return null;
            }
            case "arm" -> {
                CovJoinFlight.sticks(0.0F, 0.0F, 0.0F, 0.0F, since > 5);
                if (since > 15 && CovJoinFlight.telemetry().armed) {
                    CovJoinFlight.next(state, KEY, "climb", tick);
                }
                return since > 200 ? report(state, "not armed") : null;
            }
            case "climb" -> {
                double ground = (double) state.get("cov_surface_ground");
                CovJoinFlight.hold(ground + DROP, 0.0F);
                if (Math.abs(CovJoinFlight.y() - ground - DROP) < 0.4 && Math.abs(CovJoinFlight.telemetry().verticalSpeed) < 0.3 && since > 40) {
                    CovJoinFlight.next(state, KEY, "drop", tick);
                    state.put("cov_surface_touch", false);
                    state.put("cov_surface_apex", 0.0);
                }
                return since > 600 ? report(state, "drop height not reached") : null;
            }
            case "drop" -> {
                double ground = (double) state.get("cov_surface_ground");
                CovJoinFlight.sticks(0.0F, 0.0F, 0.0F, 0.0F, true);
                double height = CovJoinFlight.y() - ground;
                double vs = CovJoinFlight.telemetry().verticalSpeed;
                if (!(boolean) state.get("cov_surface_touch")) {
                    if (height < 0.5 && vs > 0.5) {
                        state.put("cov_surface_touch", true);
                    }
                } else {
                    state.merge("cov_surface_apex", height, (a, b) -> Math.max((double) a, (double) b));
                }
                if (since < 80) {
                    return null;
                }
                state.put("cov_surface_slime", (boolean) state.get("cov_surface_touch") && (double) state.get("cov_surface_apex") > BOUNCE);
                CovJoinFlight.next(state, KEY, "reclimb", tick);
                return null;
            }
            case "reclimb" -> {
                double ground = (double) state.get("cov_surface_ground");
                boolean armed = CovJoinFlight.telemetry().armed;
                CovJoinFlight.sticks(0.0F, 0.0F, 0.0F, armed ? CovJoinFlight.holdThrottle(ground + 3.0, 0.0) : 0.0F, armed || since > 10);
                if (armed && Math.abs(CovJoinFlight.y() - ground - 3.0) < 0.5 && since > 40) {
                    CovJoinFlight.next(state, KEY, "wall", tick);
                }
                return since > 400 ? report(state, "wall height not reached") : null;
            }
            case "wall" -> {
                double ground = (double) state.get("cov_surface_ground");
                double v = vx();
                state.merge("cov_surface_vmax", v, (a, b) -> Math.max((double) a, (double) b));
                double fastest = (double) state.get("cov_surface_vmax");
                if (fastest < WALL_SPEED && !state.containsKey("cov_surface_hit")) {
                    CovJoinFlight.sticks(0.0F, 0.45F, 0.0F, CovJoinFlight.holdThrottle(ground + 3.0, 0.05), true);
                } else {
                    CovJoinFlight.sticks(0.0F, 0.0F, 0.0F, CovJoinFlight.holdThrottle(ground + 3.0, 0.0), true);
                }
                if (fastest > 5.0 && v < 1.0 && !state.containsKey("cov_surface_hit")) {
                    state.put("cov_surface_hit", tick);
                    state.put("cov_surface_impact", fastest);
                }
                if (state.containsKey("cov_surface_hit") && tick - (int) state.get("cov_surface_hit") > 30) {
                    int crashes = session.crashCount() - (int) state.get("cov_surface_crashes0");
                    state.put("cov_surface_wool", crashes == 0 && CovJoinFlight.telemetry().armed);
                    return report(state, "done");
                }
                return since > 400 ? report(state, "wool wall not reached") : null;
            }
            default -> {
                return report(state, "unknown phase " + phase);
            }
        }
    }

    private static String report(Map<String, Object> state, String end) {
        PilotSession.INSTANCE.clearTestInput();
        int crashes = PilotSession.INSTANCE.crashCount() - (int) state.getOrDefault("cov_surface_crashes0", PilotSession.INSTANCE.crashCount());
        return "surface_slime=" + (Boolean.TRUE.equals(state.get("cov_surface_slime")) ? "bounce" : "no_bounce") + "\nsurface_wool="
                + (Boolean.TRUE.equals(state.get("cov_surface_wool")) ? "caught" : "not_caught") + "\nsurface_crashes=" + crashes
                + String.format(Locale.ROOT, "%nsurface_detail second apex %.2f m, wall impact %.1f m/s, %s%n", (double) state.getOrDefault("cov_surface_apex", Double.NaN),
                (double) state.getOrDefault("cov_surface_impact", Double.NaN), end);
    }
}
