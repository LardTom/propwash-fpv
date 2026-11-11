package de.lardtom.propwash.interact.client;

import de.lardtom.propwash.Constants;
import de.lardtom.propwash.drone.QuadcopterEntity;
import de.lardtom.propwash.drone.client.PilotSession;
import de.lardtom.propwash.input.StickPositions;
import de.lardtom.propwash.interact.CovVineArena;
import de.lardtom.propwash.sim.DroneSim;
import de.lardtom.propwash.sim.StabMode;
import net.minecraft.client.Minecraft;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public final class CovVineJoinProbe {
    public static final int TIMEOUT_TICKS = 20 * 150;
    private static final String KEY = "cov_vine_phase";
    private static final int LATCH_TICKS = 3;

    private CovVineJoinProbe() {
    }

    private static final class Probe {
        final List<CovVineArena.Flight> flights = CovVineArena.joinFlights();
        final List<String> findings = new ArrayList<>();
        final int start;
        String phase = "wait";
        int mark;
        int epoch;
        int index = -1;
        int good;
        @Nullable CovVineDropWatch watch;

        Probe(int start) {
            this.start = start;
        }

        void next(String phase, int tick) {
            this.phase = phase;
            this.mark = tick;
        }
    }

    public static @Nullable String observe(Map<String, Object> state, int tick, Minecraft mc) {
        Probe probe = (Probe) state.computeIfAbsent(KEY, key -> new Probe(tick));
        PilotSession session = PilotSession.INSTANCE;
        QuadcopterEntity drone = session.drone();
        DroneSim sim = session.sim();
        boolean flying = session.isFpv() && drone != null && sim != null;
        if (tick - probe.start > TIMEOUT_TICKS) {
            return report(probe, "timeout in " + probe.phase);
        }
        if (!probe.phase.equals("wait") && !flying) {
            return report(probe, "session ended in " + probe.phase);
        }
        int since = tick - probe.mark;
        switch (probe.phase) {
            case "wait" -> {
                if (flying) {
                    probe.next("arm", tick);
                }
                return null;
            }
            case "arm" -> {
                session.setTestInput(new StickPositions(0.0F, 0.0F, 0.0F, 0.0F), since > 5, StabMode.ANGLE);
                if (since > 15 && sim.telemetry().armed) {
                    probe.epoch = drone.resetEpoch();
                    probe.next("ready", tick);
                }
                return since > 200 ? report(probe, "not armed: " + sim.telemetry().armBlockers) : null;
            }
            case "ready" -> {
                session.setTestInput(new StickPositions(0.0F, 0.0F, 0.0F, 0.0F), true, StabMode.ANGLE);
                if (drone.resetEpoch() != probe.epoch) {
                    probe.epoch = drone.resetEpoch();
                    probe.index++;
                    probe.next("latch", tick);
                }
                return null;
            }
            case "latch" -> {
                float throttle = (float) Math.max(CovVineBeamTopSelfTests.LATCH_THROTTLE, sim.telemetry().hoverThrottleStick);
                session.setTestInput(new StickPositions(0.0F, 0.0F, 0.0F, throttle), true, StabMode.ANGLE);
                if (since < LATCH_TICKS) {
                    return null;
                }
                if (probe.index >= probe.flights.size()) {
                    return report(probe, "more resets than flights");
                }
                CovVineArena.Flight flight = probe.flights.get(probe.index);
                CovVineArena.launchFlight(sim, flight);
                session.setTestInput(new StickPositions(0.0F, 0.0F, 0.0F, 0.0F), true, StabMode.ACRO);
                probe.watch = new CovVineDropWatch(flight, drone, sim);
                probe.next("watch", tick);
                return null;
            }
            case "watch" -> {
                CovVineDropWatch w = probe.watch;
                if (w == null || mc.level == null) {
                    return report(probe, "watch lost");
                }
                session.setTestInput(new StickPositions(0.0F, 0.0F, 0.0F, 0.0F), true, StabMode.ACRO);
                w.tick(mc.level, drone, sim, mc.gameRenderer.mainCamera().position());
                if (!w.done()) {
                    return null;
                }
                String line = w.line(drone, sim, new int[3]);
                Constants.LOG.info("[Beitrittstest] Ranken-Balken von oben: Sturz {}/{}: {}", probe.index + 1, probe.flights.size(), line);
                if (w.bad(drone)) {
                    probe.findings.add(line);
                } else {
                    probe.good++;
                }
                if (probe.index + 1 >= probe.flights.size()) {
                    return report(probe, "done");
                }
                probe.epoch = drone.resetEpoch();
                probe.next("ready", tick);
                return null;
            }
            default -> {
                return report(probe, "unknown phase " + probe.phase);
            }
        }
    }

    private static String report(Probe probe, String end) {
        PilotSession.INSTANCE.clearTestInput();
        return "vine_top=" + probe.good + "/" + probe.flights.size() + "\nvine_top_detail " + end
                + (probe.findings.isEmpty() ? "" : ": " + String.join(" | ", probe.findings)) + "\n";
    }
}
