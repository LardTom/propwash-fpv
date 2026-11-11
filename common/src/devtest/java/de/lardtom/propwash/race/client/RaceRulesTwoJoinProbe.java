package de.lardtom.propwash.race.client;

import de.lardtom.propwash.Constants;
import de.lardtom.propwash.dev.RaceDataJoinCheck;
import de.lardtom.propwash.dev.RaceRulesJoinCheck;
import de.lardtom.propwash.drone.QuadcopterEntity;
import de.lardtom.propwash.drone.client.PilotSession;
import de.lardtom.propwash.guide.client.TrainingSelfTests;
import de.lardtom.propwash.input.StickPositions;
import de.lardtom.propwash.race.AssistDetail;
import de.lardtom.propwash.race.AssistReasons;
import de.lardtom.propwash.race.LapAssist;
import de.lardtom.propwash.race.RacePayloads;
import de.lardtom.propwash.server.client.ClientPersonalRules;
import de.lardtom.propwash.server.rules.RuleSpec;
import de.lardtom.propwash.server.rules.RuleTier;
import de.lardtom.propwash.server.rules.WorldRuleSpecs;
import de.lardtom.propwash.sim.DroneSim;
import de.lardtom.propwash.sim.SimTuning;
import de.lardtom.propwash.sim.StabMode;
import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

public final class RaceRulesTwoJoinProbe {
    public static final int TIMEOUT_TICKS = 20 * 360;
    private static final String RUN = "race_rules_two_run";
    private static final double SPEED = 8.0;
    private static final int SYNC_WAIT = 20 * 15;
    private static final int SESSION_WAIT = 20 * 60;
    private static final int FLIGHT_WAIT = 20 * 10;
    private static final int ARM_WAIT = 20 * 20;
    private static final int CLEAR_WAIT = 20 * 10;
    private static final int GO_WAIT = 20 * 150;
    private static final int LINE_UP = 20;
    private static final int RESULT_WAIT = 20 * 150;
    private static final int BOARD_WAIT = 20 * 30;

    private enum Phase { READY, SYNC, SESSION, FLIGHT, ARM, CLEAR, GO, LINE_UP, RACE, RESULT, BOARD }

    private RaceRulesTwoJoinProbe() {
    }

    public static @Nullable String observe(Map<String, Object> state, int tick, Minecraft mc) {
        Run run = (Run) state.computeIfAbsent(RUN, key -> new Run(tick));
        return run.tick(tick, mc);
    }

    record Role(String name, String other, RuleSpec own, int value, RuleSpec foreign, AssistDetail detail, AssistDetail otherDetail, double[] park) {
        static @Nullable Role of(String name) {
            AssistDetail battery = new AssistDetail(LapAssist.PERSONAL_RULES, WorldRuleSpecs.BATTERY_SCALE.name(), 0, 100);
            AssistDetail wind = new AssistDetail(LapAssist.PERSONAL_RULES, WorldRuleSpecs.WIND.name(), 0, 1);
            if (name.equals(RaceRulesJoinCheck.PILOT_A)) {
                return new Role("a", RaceRulesJoinCheck.PILOT_B, WorldRuleSpecs.BATTERY_SCALE, 0, WorldRuleSpecs.WIND, battery, wind,
                        RaceRulesJoinCheck.PARK_A);
            }
            if (name.equals(RaceRulesJoinCheck.PILOT_B)) {
                return new Role("b", RaceRulesJoinCheck.PILOT_A, WorldRuleSpecs.WIND, 0, WorldRuleSpecs.BATTERY_SCALE, wind, battery,
                        RaceRulesJoinCheck.PARK_B);
            }
            return null;
        }

        boolean ownTuning(SimTuning tuning) {
            boolean battery = this.own == WorldRuleSpecs.BATTERY_SCALE;
            return battery ? tuning.infiniteBattery && Math.abs(tuning.windScale - 1.0) < 1.0E-6
                    : !tuning.infiniteBattery && Math.abs(tuning.windScale) < 1.0E-6;
        }
    }

    private static final class Run {
        Phase phase = Phase.READY;
        int since;
        final int start;
        final StringBuilder lines = new StringBuilder();
        @Nullable Role role;
        int course = -1;
        Vec3 origin = Vec3.ZERO;
        final List<Vec3> path = new ArrayList<>();
        double length;
        double travelled;
        double footOffset;
        int rearms;
        int clearTicks;
        boolean finished;
        @Nullable RaceConfigState config;

        Run(int tick) {
            this.start = tick;
            this.since = tick;
        }

        void line(String text) {
            this.lines.append(text).append('\n');
            Constants.LOG.info("[Beitrittstest] race-rules zwei Clients: {}", text);
        }

        void enter(Phase next, int tick) {
            this.phase = next;
            this.since = tick;
        }

        String done(@Nullable String failure) {
            if (failure != null) {
                this.line("race_rules_two failed " + failure);
            }
            PilotSession.INSTANCE.clearTestInput();
            if (this.config != null) {
                this.config.restore();
                this.config = null;
            }
            return this.lines.toString();
        }

        @Nullable String tick(int tick, Minecraft mc) {
            if (tick - this.start > TIMEOUT_TICKS) {
                return this.done("timeout at " + this.phase);
            }
            if (mc.player == null) {
                return null;
            }
            int in = tick - this.since;
            PilotSession session = PilotSession.INSTANCE;
            switch (this.phase) {
                case READY -> {
                    Optional<String> ready = RaceDataChat.line(mc, RaceRulesJoinCheck.RESULT + RaceRulesJoinCheck.READY);
                    if (ready.isEmpty()) {
                        return null;
                    }
                    Role own = Role.of(mc.getUser().getName());
                    if (own == null) {
                        return this.done("unknown player " + mc.getUser().getName());
                    }
                    this.role = own;
                    String[] parts = ready.get().substring((RaceRulesJoinCheck.RESULT + RaceRulesJoinCheck.READY).length()).trim().split(" ");
                    this.course = Integer.parseInt(parts[0]);
                    this.origin = new Vec3(Double.parseDouble(parts[1]), Double.parseDouble(parts[2]), Double.parseDouble(parts[3]));
                    this.line("race_rules_two role=" + own.name());
                    ClientPersonalRules.INSTANCE.set(own.own(), own.value());
                    this.enter(Phase.SYNC, tick);
                }
                case SYNC -> {
                    Role own = this.role();
                    ClientPersonalRules rules = ClientPersonalRules.INSTANCE;
                    boolean synced = rules.tier(own.own()) == RuleTier.PLAYERS && rules.own().values().size() == 1
                            && rules.own().value(own.own()).orElse(-1) == own.value() && !rules.own().has(own.foreign());
                    if (synced) {
                        this.line("race_rules_two_synced=own");
                        this.line("race_rules_two_own=" + rules.own().values());
                        this.enter(Phase.SESSION, tick);
                    } else if (in > SYNC_WAIT) {
                        this.line("race_rules_two_synced=no:" + rules.tiers() + " " + rules.own().values());
                        this.enter(Phase.SESSION, tick);
                    }
                }
                case SESSION -> {
                    if (session.isFpv() && session.drone() != null && session.sim() != null) {
                        this.config = RaceConfigState.race();
                        QuadcopterEntity drone = session.drone();
                        this.footOffset = drone == null ? 0.0 : drone.getBbHeight() * 0.5;
                        this.plan();
                        this.enter(Phase.FLIGHT, tick);
                    } else if (in > SESSION_WAIT) {
                        return this.done("no session");
                    }
                }
                case FLIGHT -> {
                    this.park(session, false, in);
                    DroneSim sim = session.sim();
                    if (sim != null && this.role().ownTuning(sim.tuning())) {
                        this.line("race_rules_two_flight=own");
                        this.enter(Phase.ARM, tick);
                    } else if (in > FLIGHT_WAIT) {
                        SimTuning tuning = sim == null ? null : sim.tuning();
                        this.line("race_rules_two_flight=other:" + (tuning == null ? "none" : String.format(Locale.ROOT, "infinite=%s wind=%.2f",
                                tuning.infiniteBattery, tuning.windScale)));
                        this.enter(Phase.ARM, tick);
                    }
                }
                case ARM -> {
                    DroneSim sim = session.sim();
                    boolean armed = sim != null && sim.telemetry().armed;
                    this.park(session, armed, in);
                    if (armed && in > 10) {
                        this.enter(Phase.CLEAR, tick);
                    } else if (in > ARM_WAIT) {
                        return this.done("not armed");
                    }
                }
                case CLEAR -> {
                    this.park(session, true, in);
                    AssistReasons now = ClientRaceTracker.INSTANCE.reasonsNow();
                    boolean clear = now.without(LapAssist.PERSONAL_RULES.bit()).none();
                    this.clearTicks = clear ? this.clearTicks + 1 : 0;
                    if (this.clearTicks >= 10) {
                        Role own = this.role();
                        boolean named = now.details(LapAssist.PERSONAL_RULES).equals(List.of(own.detail()));
                        this.line("race_rules_two_assist=" + (named ? "own" : "other:" + now.details()));
                        this.enter(Phase.GO, tick);
                    } else if (in > CLEAR_WAIT && this.rearms < 2) {
                        this.rearms++;
                        Constants.LOG.info("[Beitrittstest] race-rules zwei Clients: Flughilfe vor dem Start {}, neu schärfen", now.details());
                        this.enter(Phase.ARM, tick);
                    } else if (in > CLEAR_WAIT) {
                        this.line("race_rules_two_assist=other:" + now.details());
                        this.enter(Phase.GO, tick);
                    }
                }
                case GO -> {
                    this.park(session, true, in);
                    if (RaceDataChat.line(mc, RaceRulesJoinCheck.RESULT + RaceRulesJoinCheck.GO).isPresent()) {
                        ClientRaceTracker.INSTANCE.selectCourseAt(mc, this.path.getFirst());
                        this.enter(Phase.LINE_UP, tick);
                    } else if (in > GO_WAIT) {
                        return this.done("no go");
                    }
                }
                case LINE_UP -> {
                    this.hold(session, this.path.getFirst(), true);
                    if (in > LINE_UP) {
                        this.enter(Phase.RACE, tick);
                    }
                }
                case RACE -> {
                    this.fly(session);
                    if (RaceState.INSTANCE.finished()) {
                        this.finished = true;
                    }
                    if (this.travelled >= this.length) {
                        this.line("race_rules_two_lap=" + (this.finished ? "finished" : "missing"));
                        this.line("race_rules_two_client_lap=" + ClientRaceTracker.INSTANCE.lastLapReasons().details());
                        this.enter(Phase.RESULT, tick);
                    }
                }
                case RESULT -> {
                    this.park(session, true, in);
                    Optional<String> result = RaceDataChat.line(mc, RaceRulesJoinCheck.RESULT + RaceRulesJoinCheck.DONE);
                    if (result.isPresent()) {
                        String text = result.get().substring((RaceRulesJoinCheck.RESULT + RaceRulesJoinCheck.DONE).length());
                        this.line("race_rules_two_server=" + (text.startsWith("ok ") ? "ok" : text));
                        ClientLeaderboards.request(this.course);
                        this.enter(Phase.BOARD, tick);
                    } else if (in > RESULT_WAIT) {
                        return this.done("no server result");
                    }
                }
                case BOARD -> {
                    this.park(session, true, in);
                    if (in % 40 == 39) {
                        ClientLeaderboards.request(this.course);
                    }
                    String board = this.board();
                    if (board.equals("ok") || in > BOARD_WAIT) {
                        this.line("race_rules_two_board=" + board);
                        return this.done(null);
                    }
                }
            }
            return null;
        }

        private Role role() {
            Role own = this.role;
            if (own == null) {
                throw new IllegalStateException("Rolle fehlt");
            }
            return own;
        }

        private String board() {
            RacePayloads.LeaderboardS2C board = ClientLeaderboards.get(this.course);
            if (board == null) {
                return "none";
            }
            Role own = this.role();
            Optional<RacePayloads.BoardRow> mine = row(board, Minecraft.getInstance().getUser().getName());
            Optional<RacePayloads.BoardRow> theirs = row(board, own.other());
            if (mine.isEmpty() || theirs.isEmpty()) {
                return "rows:" + board.rows().size();
            }
            boolean ok = flagged(mine.get(), own.detail()) && flagged(theirs.get(), own.otherDetail());
            return ok ? "ok" : "other:" + mine.get().details() + "/" + theirs.get().details();
        }

        private static Optional<RacePayloads.BoardRow> row(RacePayloads.LeaderboardS2C board, String name) {
            for (RacePayloads.BoardRow row : board.rows()) {
                if (row.name().equals(name)) {
                    return Optional.of(row);
                }
            }
            return Optional.empty();
        }

        private static boolean flagged(RacePayloads.BoardRow row, AssistDetail detail) {
            return row.assist() == LapAssist.PERSONAL_RULES.bit() && row.details().equals(List.of(detail));
        }

        private void plan() {
            this.path.clear();
            for (double[] point : RaceDataJoinCheck.PATH) {
                this.path.add(this.origin.add(point[0], point[1], point[2]));
            }
            double total = 0.0;
            for (int i = 1; i < this.path.size(); i++) {
                total += this.path.get(i).distanceTo(this.path.get(i - 1));
            }
            this.length = total;
        }

        private void park(PilotSession session, boolean armed, int in) {
            double[] park = this.role().park();
            Vec3 at = this.origin.add(park[0], park[1], park[2]);
            if (armed) {
                this.hold(session, at, true);
                return;
            }
            DroneSim sim = session.sim();
            boolean toggle = sim != null && sim.telemetry().armBlockers == 0 && in % 10 >= 5;
            session.setTestInput(StickPositions.IDLE, toggle, StabMode.ANGLE);
            TrainingSelfTests.teleport(at.x, at.y - this.footOffset, at.z, 0.0, 0.0, 0.0, 0.0);
        }

        private void hold(PilotSession session, Vec3 at, boolean armed) {
            session.setTestInput(new StickPositions(0.0F, 0.0F, 0.0F, hover(session.sim())), armed, StabMode.ANGLE);
            TrainingSelfTests.teleport(at.x, at.y - this.footOffset, at.z, 0.0, 0.0, 0.0, 0.0);
        }

        private void fly(PilotSession session) {
            session.setTestInput(new StickPositions(0.0F, 0.0F, 0.0F, hover(session.sim())), true, StabMode.ANGLE);
            this.travelled = Math.min(this.length, this.travelled + SPEED / 20.0);
            double left = this.travelled;
            Vec3 at = this.path.getLast();
            Vec3 direction = Vec3.ZERO;
            for (int i = 1; i < this.path.size(); i++) {
                Vec3 a = this.path.get(i - 1);
                Vec3 b = this.path.get(i);
                double segment = b.distanceTo(a);
                if (left <= segment || i == this.path.size() - 1) {
                    direction = segment > 1.0E-6 ? b.subtract(a).scale(1.0 / segment) : Vec3.ZERO;
                    at = a.add(direction.scale(Math.min(left, segment)));
                    break;
                }
                left -= segment;
            }
            double yaw = Math.toDegrees(Math.atan2(-direction.x, direction.z));
            Vec3 velocity = direction.scale(SPEED);
            TrainingSelfTests.teleport(at.x, at.y - this.footOffset, at.z, yaw, velocity.x, velocity.y, velocity.z);
        }

        private static float hover(@Nullable DroneSim sim) {
            double hover = sim == null ? 0.35 : sim.telemetry().hoverThrottleStick;
            return (float) (Double.isFinite(hover) && hover > 0.05 ? hover : 0.35);
        }
    }

    private record RaceConfigState(int laps, boolean countdown, boolean reportResults) {
        static RaceConfigState race() {
            RaceConfig config = RaceConfig.CONFIG.get();
            RaceConfigState saved = new RaceConfigState(config.laps, config.countdown, config.reportResults);
            config.laps = 1;
            config.countdown = false;
            config.reportResults = true;
            return saved;
        }

        void restore() {
            RaceConfig config = RaceConfig.CONFIG.get();
            config.laps = this.laps;
            config.countdown = this.countdown;
            config.reportResults = this.reportResults;
        }
    }
}
