package de.lardtom.propwash.drone.client;

import de.lardtom.propwash.dev.CovCrashWallJoinCheck;
import de.lardtom.propwash.drone.QuadcopterEntity;
import de.lardtom.propwash.input.StickPositions;
import de.lardtom.propwash.server.client.ClientWorldRules;
import de.lardtom.propwash.server.rules.PhysicsRules;
import de.lardtom.propwash.sim.AirframeSpec;
import de.lardtom.propwash.sim.DroneSim;
import de.lardtom.propwash.sim.StabMode;
import de.lardtom.propwash.sim.Telemetry;
import org.joml.Quaternionf;
import org.joml.Vector3d;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class CovCrashWallRun {
    public static final int TIMEOUT_TICKS = 20 * 240;
    public static final double MAX_TRAVEL = 1.0;
    public static final double MAX_THRUST = 0.2;
    public static final double MAX_ITERM = 50.0;
    public static final int TWITCH_TICKS = 6;
    public static final double NEAR_BLOCK = 0.03;
    private static final String KEY = "cov_crashwall_phase";
    private static final String PREFIX = "cov_crashwall_";
    private static final double LATCH_THROTTLE = 0.35;
    private static final double START_HEIGHT = 0.6;
    private static final double WALL_GAP = 0.6;
    private static final int SETTLE_TICKS = 20;
    private static final int RULES_TICKS = 200;
    private static final int PLACE_TICKS = 10;
    private static final int ARM_TICKS = 60;
    private static final int LATCH_TICKS = 2;
    private static final int TOUCH_TICKS = 80;
    private static final int MEASURE_TICKS = 100;
    private static final int SETTLED_TICKS = 20;
    private static final int LATE_TICKS = 40;
    private static final int DISARM_TICKS = 10;

    record Case(StabMode mode, double speed, double tiltDeg, double axisDeg) {
        String name() {
            return String.format(Locale.ROOT, "Racer %s, %.1f m/s gegen die Wand, %.0f° Neigung, Achse %.0f°", this.mode, this.speed, this.tiltDeg,
                    this.axisDeg);
        }
    }

    static final List<Case> CASES = List.of(
            new Case(StabMode.ACRO, 4.0, 3.0, 60.0),
            new Case(StabMode.ACRO, 4.0, 12.0, 15.0),
            new Case(StabMode.ACRO, 0.5, 18.0, 75.0),
            new Case(StabMode.ACRO, 1.0, 15.0, 45.0),
            new Case(StabMode.ACRO, 0.5, 24.0, 270.0),
            new Case(StabMode.ACRO, 1.0, 18.0, 270.0),
            new Case(StabMode.ACRO, 5.0, 9.0, 270.0),
            new Case(StabMode.ACRO, 6.0, 12.0, 300.0),
            new Case(StabMode.ACRO, 3.0, 9.0, 255.0),
            new Case(StabMode.HORIZON, 5.0, 18.0, 225.0));

    private CovCrashWallRun() {
    }

    public static int cases() {
        return CASES.size();
    }

    public static @Nullable String observe(Map<String, Object> state, int tick) {
        int start = (int) state.computeIfAbsent(PREFIX + "start", key -> tick);
        String phase = CovJoinFlight.phase(state, KEY, "wait");
        int since = CovJoinFlight.since(state, KEY, tick);
        if (tick - start > TIMEOUT_TICKS) {
            return report(state, "timeout in " + phase);
        }
        PilotSession session = PilotSession.INSTANCE;
        QuadcopterEntity drone = session.drone();
        DroneSim sim = session.sim();
        if (!phase.equals("wait") && (!CovJoinFlight.flying() || drone == null || sim == null)) {
            return report(state, "session ended in " + phase);
        }
        int index = (int) state.getOrDefault(PREFIX + "case", 0);
        Case c = CASES.get(Math.min(index, CASES.size() - 1));
        switch (phase) {
            case "wait" -> {
                if (!CovJoinFlight.flying() || drone == null || sim == null) {
                    return null;
                }
                int seen = (int) state.computeIfAbsent(PREFIX + "seen", key -> tick);
                PhysicsRules rules = session.appliedPhysics();
                if (rules.crashDisarm() || rules.propStrikes() || !rules.infiniteBattery() || ClientWorldRules.INSTANCE.crashDamage()) {
                    return tick - seen > RULES_TICKS ? report(state, "rules not applied on client: " + rules + ", crash_damage "
                            + ClientWorldRules.INSTANCE.crashDamage()) : null;
                }
                state.put(PREFIX + "rules", true);
                if (tick - seen >= SETTLE_TICKS) {
                    state.put(PREFIX + "center", new double[]{Math.floor(drone.getX()) + 0.5, Math.floor(drone.getY() + 0.01),
                            Math.floor(drone.getZ()) + 0.5});
                    state.put(PREFIX + "build", drone.build().frame().key());
                    CovJoinFlight.next(state, KEY, "place", tick);
                }
                return null;
            }
            case "place" -> {
                input(session, 0.0, false, c.mode());
                if (since == 1) {
                    double[] center = (double[]) state.get(PREFIX + "center");
                    CovCrashRulesRun.relocate(drone, sim, center[0], center[1] + 0.05, center[2], new Quaternionf(), 0.0, 0.0, 0.0);
                }
                if (since >= PLACE_TICKS) {
                    CovJoinFlight.next(state, KEY, "arm", tick);
                }
                return null;
            }
            case "arm" -> {
                if (sim.telemetry().armed) {
                    input(session, Math.max(LATCH_THROTTLE, sim.telemetry().hoverThrottleStick), true, c.mode());
                    double[] center = (double[]) state.get(PREFIX + "center");
                    double axis = Math.toRadians(c.axisDeg());
                    Quaternionf lean = new Quaternionf().rotationAxis((float) Math.toRadians(c.tiltDeg()), (float) Math.cos(axis), 0.0F,
                            (float) Math.sin(axis));
                    double x = wallX(center) - WALL_GAP;
                    CovCrashRulesRun.relocate(drone, sim, x, center[1] + START_HEIGHT, center[2], lean, c.speed(), 0.0, 0.0);
                    state.put(PREFIX + "impacts", sim.telemetry().impactCount);
                    state.put(PREFIX + "crashes", sim.telemetry().crashCount);
                    state.put(PREFIX + "epoch", drone.resetEpoch());
                    state.put(PREFIX + "resets_case", 0);
                    CovJoinFlight.next(state, KEY, "launch", tick);
                    return null;
                }
                input(session, 0.0, since > 3, c.mode());
                return since > ARM_TICKS ? report(state, "not armed: " + sim.telemetry().armBlockers) : null;
            }
            case "launch" -> {
                boolean latch = since < LATCH_TICKS;
                input(session, latch ? Math.max(LATCH_THROTTLE, sim.telemetry().hoverThrottleStick) : 0.0, true, c.mode());
                countResets(state, drone);
                Telemetry t = sim.telemetry();
                if (!latch && (t.grounded || t.impactCount > (int) state.get(PREFIX + "impacts") || nearBlock(state, sim))) {
                    state.put(PREFIX + "travel", 0.0);
                    state.put(PREFIX + "thrust", 0.0);
                    state.put(PREFIX + "late_thrust", 0.0);
                    state.put(PREFIX + "iterm", 0.0);
                    state.put(PREFIX + "worst_tick", -1);
                    state.put(PREFIX + "impact_tick", 0);
                    state.put(PREFIX + "seen_impacts", t.impactCount);
                    state.put(PREFIX + "seen_crashes", t.crashCount);
                    state.put(PREFIX + "was_grounded", t.grounded);
                    CovJoinFlight.next(state, KEY, "measure", tick);
                    return null;
                }
                if (since > TOUCH_TICKS) {
                    addLine(state, c, "schlägt nicht auf", false);
                    CovJoinFlight.next(state, KEY, "disarm", tick);
                }
                return null;
            }
            case "measure" -> {
                input(session, 0.0, true, c.mode());
                countResets(state, drone);
                measure(state, sim, since);
                if (since < MEASURE_TICKS) {
                    return null;
                }
                finish(state, c, sim.telemetry());
                CovJoinFlight.next(state, KEY, "disarm", tick);
                return null;
            }
            case "disarm" -> {
                input(session, 0.0, false, c.mode());
                if (since < DISARM_TICKS) {
                    return null;
                }
                state.put(PREFIX + "case", index + 1);
                if (index + 1 >= CASES.size()) {
                    return report(state, "done");
                }
                CovJoinFlight.next(state, KEY, "place", tick);
                return null;
            }
            default -> {
                return report(state, "unknown phase " + phase);
            }
        }
    }

    private static double wallX(double[] center) {
        return center[0] - 0.5 + CovCrashWallJoinCheck.WALL_DX;
    }

    private static boolean nearBlock(Map<String, Object> state, DroneSim sim) {
        double[] center = (double[]) state.get(PREFIX + "center");
        AirframeSpec p = sim.params();
        double reach = p.armLength + p.propRadius + NEAR_BLOCK;
        Vector3d pos = new Vector3d();
        sim.getPosition(pos);
        return sim.telemetry().grounded || wallX(center) - pos.x <= reach || pos.y - center[1] <= reach;
    }

    private static void measure(Map<String, Object> state, DroneSim sim, int since) {
        Telemetry t = sim.telemetry();
        Vector3d p = new Vector3d();
        sim.getPosition(p);
        boolean landed = t.grounded && !(boolean) state.get(PREFIX + "was_grounded");
        if (t.impactCount > (int) state.get(PREFIX + "seen_impacts") || t.crashCount > (int) state.get(PREFIX + "seen_crashes") || landed) {
            state.put(PREFIX + "impact_tick", since);
        }
        state.put(PREFIX + "seen_impacts", t.impactCount);
        state.put(PREFIX + "seen_crashes", t.crashCount);
        state.put(PREFIX + "was_grounded", t.grounded);
        double i = iterm(t);
        if (since - (int) state.get(PREFIX + "impact_tick") >= TWITCH_TICKS && nearBlock(state, sim)) {
            if (t.thrustToWeight > (double) state.get(PREFIX + "thrust")) {
                state.put(PREFIX + "thrust", t.thrustToWeight);
                state.put(PREFIX + "worst_tick", since);
            }
            state.put(PREFIX + "iterm", Math.max((double) state.get(PREFIX + "iterm"), i));
        }
        if (since == SETTLED_TICKS) {
            state.put(PREFIX + "settled", new double[]{p.x, p.z});
        }
        if (since > SETTLED_TICKS) {
            double[] settled = (double[]) state.get(PREFIX + "settled");
            state.put(PREFIX + "travel", Math.max((double) state.get(PREFIX + "travel"), Math.hypot(p.x - settled[0], p.z - settled[1])));
        }
        if (since > MEASURE_TICKS - LATE_TICKS) {
            state.put(PREFIX + "late_thrust", (double) state.get(PREFIX + "late_thrust") + t.thrustToWeight / LATE_TICKS);
            state.put(PREFIX + "iterm", Math.max((double) state.get(PREFIX + "iterm"), i));
        }
    }

    private static void input(PilotSession session, double throttle, boolean arm, StabMode mode) {
        session.setTestInput(new StickPositions(0.0F, 0.0F, 0.0F, (float) throttle), arm, mode);
    }

    private static double iterm(Telemetry t) {
        return Math.max(Math.abs(t.iTerm[0]), Math.max(Math.abs(t.iTerm[1]), Math.abs(t.iTerm[2])));
    }

    private static void countResets(Map<String, Object> state, QuadcopterEntity drone) {
        int epoch = drone.resetEpoch();
        if (epoch != (int) state.get(PREFIX + "epoch")) {
            state.put(PREFIX + "epoch", epoch);
            state.merge(PREFIX + "resets_case", 1, (a, b) -> (int) a + (int) b);
            state.merge(PREFIX + "resets", 1, (a, b) -> (int) a + (int) b);
        }
    }

    private static void finish(Map<String, Object> state, Case c, Telemetry t) {
        double travel = (double) state.get(PREFIX + "travel");
        double thrust = (double) state.get(PREFIX + "thrust");
        double late = (double) state.get(PREFIX + "late_thrust");
        double i = (double) state.get(PREFIX + "iterm");
        int worst = (int) state.get(PREFIX + "worst_tick");
        int resets = (int) state.getOrDefault(PREFIX + "resets_case", 0);
        boolean ok = t.armed && resets == 0 && travel <= MAX_TRAVEL && thrust <= MAX_THRUST && late <= MAX_THRUST && i <= MAX_ITERM;
        String line = String.format(Locale.ROOT, "am Block ab 0,3 s nach dem Aufprall Schub bis %.2f × Gewicht (Tick %d), I-Anteil bis %.0f ‰, "
                        + "letzte 2 s Schub %.2f × Gewicht, Weg nach 1 s %.2f Blöcke, Neigung %.0f°, scharf %s, Rücksetzer vom Server %d", thrust, worst,
                i, late, travel, t.tiltDeg, t.armed, resets);
        addLine(state, c, line, ok);
    }

    @SuppressWarnings("unchecked")
    private static void addLine(Map<String, Object> state, Case c, String line, boolean ok) {
        List<String> lines = (List<String>) state.computeIfAbsent(PREFIX + "lines", key -> new ArrayList<String>());
        lines.add((ok ? "ok " : "FEHLER ") + c.name() + ": " + line);
        if (ok) {
            state.merge(PREFIX + "ok", 1, (a, b) -> (int) a + (int) b);
        }
    }

    @SuppressWarnings("unchecked")
    static List<String> lines(Map<String, Object> state) {
        return (List<String>) state.getOrDefault(PREFIX + "lines", List.of());
    }

    static int passed(Map<String, Object> state) {
        return (int) state.getOrDefault(PREFIX + "ok", 0);
    }

    static int resets(Map<String, Object> state) {
        return (int) state.getOrDefault(PREFIX + "resets", 0);
    }

    private static String report(Map<String, Object> state, String end) {
        PilotSession.INSTANCE.clearTestInput();
        StringBuilder out = new StringBuilder();
        out.append("crashwall_rules=").append(state.containsKey(PREFIX + "rules") ? "applied" : "missing").append('\n');
        out.append("crashwall_build=").append(state.getOrDefault(PREFIX + "build", "?")).append('\n');
        out.append("crashwall_rest=").append(passed(state)).append('/').append(CASES.size()).append('\n');
        out.append("crashwall_resets=").append(resets(state)).append('\n');
        for (String line : lines(state)) {
            out.append("crashwall_case ").append(line).append('\n');
        }
        out.append("crashwall_end ").append(end).append('\n');
        return out.toString();
    }
}
