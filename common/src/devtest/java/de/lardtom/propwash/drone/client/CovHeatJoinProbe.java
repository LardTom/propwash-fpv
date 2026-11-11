package de.lardtom.propwash.drone.client;

import de.lardtom.propwash.osd.OsdWarnings;
import de.lardtom.propwash.osd.client.OsdHud;
import de.lardtom.propwash.sim.AirframeSpec;
import de.lardtom.propwash.sim.DroneSim;
import de.lardtom.propwash.sim.SimSnapshot;
import de.lardtom.propwash.sim.Telemetry;
import net.minecraft.client.Minecraft;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class CovHeatJoinProbe {
    public static final int TIMEOUT_TICKS = 20 * 120;
    private static final String KEY = "cov_heat_phase";
    private static final double HEIGHT = 18.0;
    private static final double ESC_HOT_C = 147.5;
    private static final int HOT_TICKS = 40;
    private static final double SINK = 1.0;

    private CovHeatJoinProbe() {
    }

    private static void heat(DroneSim sim) {
        SimSnapshot snapshot = new SimSnapshot();
        sim.copyStateTo(snapshot);
        for (int i = 0; i < AirframeSpec.MOTORS; i++) {
            snapshot.escTempC[i] = ESC_HOT_C;
        }
        sim.copyStateFrom(snapshot);
    }

    public static @Nullable String observe(Map<String, Object> state, int tick, Minecraft mc) {
        int start = (int) state.computeIfAbsent("cov_heat_start", key -> tick);
        String phase = CovJoinFlight.phase(state, KEY, "wait");
        int since = CovJoinFlight.since(state, KEY, tick);
        if (tick - start > TIMEOUT_TICKS) {
            return report(state, "timeout in " + phase);
        }
        if (!phase.equals("wait") && !CovJoinFlight.flying()) {
            return report(state, "session ended in " + phase);
        }
        switch (phase) {
            case "wait" -> {
                if (CovJoinFlight.flying()) {
                    state.put("cov_heat_ground", CovJoinFlight.y());
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
                double ground = (double) state.get("cov_heat_ground");
                CovJoinFlight.hold(ground + HEIGHT, 0.0F);
                Telemetry t = CovJoinFlight.telemetry();
                if (Math.abs(CovJoinFlight.y() - ground - HEIGHT) < 0.5 && Math.abs(t.verticalSpeed) < 0.3 && since > 60) {
                    state.put("cov_heat_hover", t.hoverThrottleStick);
                    state.put("cov_heat_before", CovJoinFlight.y() - ground);
                    CovJoinFlight.next(state, KEY, "hot", tick);
                }
                return since > 600 ? report(state, "hover not reached") : null;
            }
            case "hot" -> {
                double ground = (double) state.get("cov_heat_ground");
                DroneSim sim = PilotSession.INSTANCE.sim();
                if (sim != null) {
                    heat(sim);
                }
                float throttle = CovJoinFlight.holdThrottle(ground + HEIGHT, 0.0);
                CovJoinFlight.sticks(0.0F, 0.0F, 0.0F, throttle, true);
                Telemetry t = CovJoinFlight.telemetry();
                double limit = 1.0;
                for (double l : t.motorPowerLimit) {
                    limit = Math.min(limit, l);
                }
                state.merge("cov_heat_limit", limit, (a, b) -> Math.min((double) a, (double) b));
                List<OsdWarnings.Warning> warnings = new ArrayList<>();
                OsdWarnings.collect(OsdHud.sampler().data(), warnings);
                if (warnings.contains(OsdWarnings.Warning.ESC_HOT)) {
                    state.put("cov_heat_osd", true);
                }
                if (t.escDerating) {
                    state.put("cov_heat_derate", true);
                }
                state.merge("cov_heat_throttle", (double) throttle, (a, b) -> Math.max((double) a, (double) b));
                double height = CovJoinFlight.y() - ground;
                state.merge("cov_heat_low", height, (a, b) -> Math.min((double) a, (double) b));
                if (since < HOT_TICKS && height > HEIGHT - 6.0) {
                    return null;
                }
                CovJoinFlight.sticks(0.0F, 0.0F, 0.0F, 0.0F, false);
                return report(state, "done");
            }
            default -> {
                return report(state, "unknown phase " + phase);
            }
        }
    }

    private static String report(Map<String, Object> state, String end) {
        PilotSession.INSTANCE.clearTestInput();
        boolean osd = state.containsKey("cov_heat_osd");
        double limit = (double) state.getOrDefault("cov_heat_limit", 1.0);
        double before = (double) state.getOrDefault("cov_heat_before", Double.NaN);
        double low = (double) state.getOrDefault("cov_heat_low", Double.NaN);
        boolean sink = before - low > SINK;
        return "heat_osd=" + (osd ? "esc_overheat" : "none") + "\nheat_limit=" + (limit < 1.0 ? "reduced" : "full") + "\nheat_sink=" + (sink ? "yes" : "no")
                + String.format(Locale.ROOT, "%nheat_detail ESC %.1f C, limit %.2f, hover throttle %.2f, max throttle %.2f, height %.2f -> %.2f m, derate %s, %s%n",
                ESC_HOT_C, limit, (double) state.getOrDefault("cov_heat_hover", Double.NaN), (double) state.getOrDefault("cov_heat_throttle", Double.NaN), before, low,
                state.containsKey("cov_heat_derate"), end);
    }
}
