package de.lardtom.propwash.drone.client;

import de.lardtom.propwash.drone.QuadcopterEntity;
import de.lardtom.propwash.input.StickPositions;
import de.lardtom.propwash.server.client.ClientWorldRules;
import de.lardtom.propwash.server.rules.PhysicsRules;
import de.lardtom.propwash.sim.DroneSim;
import de.lardtom.propwash.sim.SimSnapshot;
import de.lardtom.propwash.sim.StabMode;
import de.lardtom.propwash.sim.Telemetry;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.joml.Vector3d;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class CovCrashRulesRun {
    public static final int TIMEOUT_TICKS = 20 * 180;
    public static final double MAX_TRAVEL = 1.0;
    public static final double MAX_LATE_THRUST = 0.2;
    public static final double MAX_LATE_ITERM = 50.0;
    public static final double LYING_DEG = 60.0;
    private static final String KEY = "cov_crashrules_phase";
    private static final String PREFIX = "cov_crashrules_";
    private static final double LATCH_THROTTLE = 0.35;
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

    record Case(String name, StabMode mode, double tiltDeg, double axisDeg, double dx, double height, double vx, double vy, double vz,
                double throttle) {
        boolean idle() {
            return this.throttle <= 0.0;
        }
    }

    static final List<Case> CASES = List.of(
            new Case("Acro, 75° schräg auf Stein, Gas 0", StabMode.ACRO, 75.0, 45.0, 0.0, 0.6, -0.5, -2.0, 0.0, 0.0),
            new Case("Angle, auf dem Rücken auf Stein, Gas 0", StabMode.ANGLE, 180.0, 45.0, 0.0, 0.6, -0.5, -2.0, 0.0, 0.0),
            new Case("Acro, hart auf den Rücken, Gas 0", StabMode.ACRO, 180.0, 0.0, 0.0, 1.0, -1.0, -8.0, 0.0, 0.0),
            new Case("Acro, gegen die Wand, Gas 0", StabMode.ACRO, 30.0, 0.0, 1.8, 0.6, 3.0, 0.0, 0.0, 0.0),
            new Case("Acro, auf die Seite, Gas 30 %", StabMode.ACRO, 90.0, 0.0, 0.0, 0.6, -0.5, -2.0, 0.0, 0.3));

    private CovCrashRulesRun() {
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
                if (!CovJoinFlight.flying() || drone == null) {
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
                    CovJoinFlight.next(state, KEY, "place", tick);
                }
                return null;
            }
            case "place" -> {
                input(session, 0.0, false, c.mode());
                if (since == 1) {
                    double[] center = (double[]) state.get(PREFIX + "center");
                    relocate(drone, sim, center[0], center[1] + 0.05, center[2], new Quaternionf(), 0.0, 0.0, 0.0);
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
                    relocate(drone, sim, center[0] + c.dx(), center[1] + c.height(), center[2], lean, c.vx(), c.vy(), c.vz());
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
                input(session, latch ? Math.max(LATCH_THROTTLE, sim.telemetry().hoverThrottleStick) : c.throttle(), true, c.mode());
                countResets(state, drone);
                Telemetry t = sim.telemetry();
                if (!latch && (t.grounded || t.impactCount > (int) state.get(PREFIX + "impacts"))) {
                    state.put(PREFIX + "travel", 0.0);
                    state.put(PREFIX + "speed", 0.0);
                    state.put(PREFIX + "thrust", 0.0);
                    state.put(PREFIX + "iterm", 0.0);
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
                input(session, c.throttle(), true, c.mode());
                countResets(state, drone);
                Telemetry t = sim.telemetry();
                Vector3d p = new Vector3d();
                sim.getPosition(p);
                if (since == SETTLED_TICKS) {
                    state.put(PREFIX + "settled", new double[]{p.x, p.z});
                }
                if (since > SETTLED_TICKS) {
                    double[] settled = (double[]) state.get(PREFIX + "settled");
                    state.put(PREFIX + "travel", Math.max((double) state.get(PREFIX + "travel"), Math.hypot(p.x - settled[0], p.z - settled[1])));
                    state.put(PREFIX + "speed", Math.max((double) state.get(PREFIX + "speed"), t.speed));
                }
                if (since > MEASURE_TICKS - LATE_TICKS) {
                    state.put(PREFIX + "thrust", (double) state.get(PREFIX + "thrust") + t.thrustToWeight / LATE_TICKS);
                    state.put(PREFIX + "iterm", Math.max((double) state.get(PREFIX + "iterm"), iterm(t)));
                }
                if (since < MEASURE_TICKS) {
                    return null;
                }
                finish(state, c, t);
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

    static void relocate(QuadcopterEntity drone, DroneSim sim, double x, double y, double z, Quaternionf simOrientation, double vx, double vy,
                         double vz) {
        SimSnapshot snapshot = new SimSnapshot();
        sim.copyStateTo(snapshot);
        snapshot.px = x;
        snapshot.py = y - sim.params().hitboxFootOffset;
        snapshot.pz = z;
        snapshot.qw = simOrientation.w;
        snapshot.qx = simOrientation.x;
        snapshot.qy = simOrientation.y;
        snapshot.qz = simOrientation.z;
        snapshot.vx = vx;
        snapshot.vy = vy;
        snapshot.vz = vz;
        snapshot.lx = 0.0;
        snapshot.ly = 0.0;
        snapshot.lz = 0.0;
        snapshot.sleeping = false;
        snapshot.groundedFrames = 0;
        sim.copyStateFrom(snapshot);
        drone.snapClientPose(new Vec3(x, y, z), new Quaternionf(simOrientation).rotateY((float) Math.PI));
    }

    private static void finish(Map<String, Object> state, Case c, Telemetry t) {
        double travel = (double) state.get(PREFIX + "travel");
        double thrust = (double) state.get(PREFIX + "thrust");
        double i = (double) state.get(PREFIX + "iterm");
        int resets = (int) state.getOrDefault(PREFIX + "resets_case", 0);
        boolean lying = t.tiltDeg > LYING_DEG;
        boolean ok = t.armed && resets == 0 && (c.idle() ? travel <= MAX_TRAVEL && thrust <= MAX_LATE_THRUST && i <= MAX_LATE_ITERM
                : !lying || i <= MAX_LATE_ITERM);
        String line = String.format(Locale.ROOT, "Weg nach 1 s %.2f Blöcke, schnellstens %.1f m/s, Schub/Gewicht in den letzten 2 s %.2f, "
                + "I-Anteil bis %.0f ‰, Neigung %.0f°, scharf %s, erkannte Abstürze %d, Rücksetzer vom Server %d", travel,
                (double) state.get(PREFIX + "speed"), thrust, i, t.tiltDeg, t.armed, t.crashCount - (int) state.get(PREFIX + "crashes"), resets);
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
        out.append("crasharm_rules=").append(state.containsKey(PREFIX + "rules") ? "applied" : "missing").append('\n');
        out.append("crasharm_rest=").append(passed(state)).append('/').append(CASES.size()).append('\n');
        out.append("crasharm_resets=").append(resets(state)).append('\n');
        for (String line : lines(state)) {
            out.append("crasharm_case ").append(line).append('\n');
        }
        out.append("crasharm_end ").append(end).append('\n');
        return out.toString();
    }
}
