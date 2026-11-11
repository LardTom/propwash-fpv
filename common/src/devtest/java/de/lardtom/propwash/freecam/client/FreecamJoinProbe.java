package de.lardtom.propwash.freecam.client;

import de.lardtom.propwash.Constants;
import de.lardtom.propwash.freecam.FreecamRules;
import de.lardtom.propwash.input.StickPositions;
import de.lardtom.propwash.network.client.ClientNet;
import de.lardtom.propwash.server.client.ClientWorldRules;
import de.lardtom.propwash.server.rules.WorldRuleSpecs;
import de.lardtom.propwash.sim.DroneSim;
import de.lardtom.propwash.sim.StabMode;
import net.minecraft.client.Minecraft;
import org.joml.Vector3d;
import org.jspecify.annotations.Nullable;

import java.util.Locale;
import java.util.Map;

public final class FreecamJoinProbe {
    public static final int TIMEOUT_TICKS = 20 * 90;
    private static final String START = "freecam_start";
    private static final String PROBE = "freecam_probe";
    private static final int FLY_TICKS = 20 * 10;

    private FreecamJoinProbe() {
    }

    private static final class Probe {
        int stage;
        int mark;
        String denied = "none";
        boolean started;
        int sent;
        int packets = -1;
        int offTick = -1;
        String ruleOff = "running";
        double flown;
        final Vector3d from = new Vector3d();
    }

    public static @Nullable String observe(Map<String, Object> state, int tick, Minecraft mc) {
        int start = (int) state.computeIfAbsent(START, key -> tick);
        Probe p = (Probe) state.computeIfAbsent(PROBE, key -> new Probe());
        if (tick - start > TIMEOUT_TICKS) {
            FreecamSession.INSTANCE.clearTestInput();
            return report(p, "timeout at stage " + p.stage);
        }
        ClientWorldRules rules = ClientWorldRules.INSTANCE;
        FreecamSession session = FreecamSession.INSTANCE;
        switch (p.stage) {
            case 0 -> {
                if (!rules.known() || mc.player == null) {
                    return null;
                }
                FreecamRules.Denial denial = session.toggle(mc);
                p.denied = switch (denial) {
                    case RULE_OFF -> "default_off";
                    case NONE -> "none";
                    default -> denial.name().toLowerCase(Locale.ROOT);
                };
                if (FreecamSession.active()) {
                    session.end(mc, FreecamRules.End.BUTTON);
                }
                Constants.LOG.info("[Beitrittstest] Freecam: Start bei Regel im Standard: {}", denial);
                p.stage = 1;
                p.mark = tick;
            }
            case 1 -> {
                if (!rules.on(WorldRuleSpecs.ALLOW_FREECAM)) {
                    return null;
                }
                p.sent = ClientNet.sentCount();
                session.toggle(mc);
                p.started = FreecamSession.active();
                Constants.LOG.info("[Beitrittstest] Freecam: Regel an, Start {}", p.started ? "ja" : "nein (" + session.lastDenial() + ")");
                if (!p.started) {
                    return report(p, "");
                }
                session.position(p.from);
                p.stage = 2;
                p.mark = tick;
            }
            case 2 -> {
                DroneSim sim = session.sim();
                if (!FreecamSession.active() || sim == null) {
                    p.stage = 3;
                    return null;
                }
                float hover = (float) sim.telemetry().hoverThrottleStick;
                boolean forward = tick - p.mark > 40 && tick - p.mark < FLY_TICKS - 40;
                if (!sim.telemetry().armed) {
                    session.setTestInput(new StickPositions(0.0F, 0.0F, 0.0F, 0.0F), true, StabMode.ANGLE);
                } else {
                    session.setTestInput(new StickPositions(0.0F, forward ? 0.25F : 0.0F, 0.0F, hover > 0.05F ? hover : 0.3F), true, StabMode.ANGLE);
                }
                if (tick - p.mark >= FLY_TICKS) {
                    p.flown = session.position(new Vector3d()).distance(p.from);
                    Constants.LOG.info("[Beitrittstest] Freecam: {} s geflogen, {} Blöcke", FLY_TICKS / 20,
                            String.format(Locale.ROOT, "%.1f", p.flown));
                    p.stage = 3;
                    p.mark = tick;
                }
            }
            case 3 -> {
                boolean off = !rules.on(WorldRuleSpecs.ALLOW_FREECAM);
                if (off && p.offTick < 0) {
                    p.offTick = tick;
                }
                if (FreecamSession.active()) {
                    DroneSim sim = session.sim();
                    float hover = sim == null ? 0.3F : (float) sim.telemetry().hoverThrottleStick;
                    session.setTestInput(new StickPositions(0.0F, 0.0F, 0.0F, hover), true, StabMode.ANGLE);
                    if (p.offTick >= 0 && tick - p.offTick > 1) {
                        p.ruleOff = "running";
                        session.clearTestInput();
                        return report(p, "still running " + (tick - p.offTick) + " ticks after rule off");
                    }
                    return null;
                }
                session.clearTestInput();
                p.packets = ClientNet.sentCount() - p.sent;
                p.ruleOff = session.lastEnd() == FreecamRules.End.RULE_OFF ? "ended" : "ended_" + session.lastEnd().name().toLowerCase(Locale.ROOT);
                Constants.LOG.info("[Beitrittstest] Freecam: Ende {} nach {} Tick(s), {} Pakete an den Server", session.lastEnd(),
                        p.offTick < 0 ? -1 : tick - p.offTick, p.packets);
                return report(p, "");
            }
            default -> {
                return report(p, "");
            }
        }
        return null;
    }

    private static String report(Probe p, String problem) {
        StringBuilder out = new StringBuilder();
        if (!problem.isEmpty()) {
            out.append("freecam ").append(problem).append('\n');
        }
        out.append("freecam_denied=").append(p.denied).append('\n');
        out.append("freecam_started=").append(p.started ? "yes" : "no").append('\n');
        out.append("freecam_server_packets=").append(p.packets).append('\n');
        out.append("freecam_rule_off=").append(p.ruleOff).append('\n');
        out.append(String.format(Locale.ROOT, "freecam_flown=%.1f%n", p.flown));
        return out.toString();
    }
}
