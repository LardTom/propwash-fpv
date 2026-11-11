package de.lardtom.propwash.dev.client;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.platform.InputConstants;
import de.lardtom.propwash.Constants;
import de.lardtom.propwash.build.VideoSystem;
import de.lardtom.propwash.dev.JoinTestEnv;
import de.lardtom.propwash.dev.MigrationJoinCheck;
import de.lardtom.propwash.dev.MigrationValues;
import de.lardtom.propwash.dev.MultiJoinCheck;
import de.lardtom.propwash.dev.example.client.ExampleOperatorClient;
import de.lardtom.propwash.drone.QuadcopterEntity;
import de.lardtom.propwash.drone.client.PilotSession;
import de.lardtom.propwash.drone.operator.client.OperatorClient;
import de.lardtom.propwash.guide.client.TrainingSelfTests;
import de.lardtom.propwash.input.StickPositions;
import de.lardtom.propwash.osd.OsdConfig;
import de.lardtom.propwash.osd.OsdData;
import de.lardtom.propwash.osd.OsdElement;
import de.lardtom.propwash.osd.client.OsdClient;
import de.lardtom.propwash.osd.client.OsdHud;
import de.lardtom.propwash.platform.Services;
import de.lardtom.propwash.race.CourseData;
import de.lardtom.propwash.race.GateEntity;
import de.lardtom.propwash.race.GateGeometry;
import de.lardtom.propwash.race.GhostChoice;
import de.lardtom.propwash.race.LapTracker;
import de.lardtom.propwash.race.RacePayloads;
import de.lardtom.propwash.race.client.ClientCourses;
import de.lardtom.propwash.race.client.ClientGateOpenings;
import de.lardtom.propwash.race.client.ClientLeaderboards;
import de.lardtom.propwash.race.client.ClientRaceTracker;
import de.lardtom.propwash.race.client.RaceConfig;
import de.lardtom.propwash.race.client.RaceState;
import de.lardtom.propwash.race.client.ScoreAreaRenderer;
import de.lardtom.propwash.rescue.client.AutonomyLabels;
import de.lardtom.propwash.server.client.ClientWorldRules;
import de.lardtom.propwash.sim.DroneSim;
import de.lardtom.propwash.sim.StabMode;
import de.lardtom.propwash.video.PilotFeed;
import de.lardtom.propwash.video.client.SpectateKeys;
import de.lardtom.propwash.video.client.SpectateSession;
import de.lardtom.propwash.video.client.WatchedPilot;
import de.lardtom.propwash.video.net.ChannelListS2C;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.util.Util;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

public final class MultiJoinProbe {
    public static final String PILOT = "pilot";
    public static final String WATCHER = "watcher";
    public static final int TIMEOUT_TICKS = 20 * 420;
    private static final String KEY = "multi.run";
    private static final double SPEED = 12.0;
    private static final double APPROACH = 4.0;
    private static final double LOOP_WIDTH = 8.0;
    private static final int LAPS = 3;
    private static final int SESSION_WAIT = 20 * 120;
    private static final int COURSE_WAIT = 20 * 30;
    private static final int ARM_WAIT = 20 * 20;
    private static final int CLEAR_WAIT = 20 * 10;
    private static final int WATCHER_WAIT = 20 * 90;
    private static final int BOARD_WAIT = 20 * 40;
    private static final int GOGGLES_WAIT = 20 * 150;
    private static final int CHANNEL_WAIT = 20 * 90;
    private static final int WATCH_WAIT = 20 * 150;
    private static final int WATCH_AFTER_BOARD = 20 * 25;
    private static final int MIN_WATCH_TICKS = 100;
    private static final double MIN_WATCH_MOVED = 20.0;
    private static final int CLEAR_DONE_WAIT = 20 * 30;
    private static final int REMOVED_WAIT = 20 * 210;
    private static final int AFTER_CLEAR = 40;
    private static final int MIN_MARKER_TICKS = 5;
    private static final int OPERATE_WAIT = 20 * 60;
    private static final int GIMBAL_WAIT = 20 * 30;
    private static final int VIEW_END_WAIT = 20 * 15;
    private static final int GIMBAL_MIN_TICKS = 60;
    private static final double GIMBAL_MIN_PAN = 20.0;
    private static final float[] GIMBAL_AXES = {1.0F, -0.6F, 0.0F, 0.5F};
    private static final List<OsdElement> RACE_ELEMENTS = List.of(OsdElement.LAP_TIME, OsdElement.LAP, OsdElement.GATE);
    private static final int MIN_FEED_TICKS = 40;
    private static final float MIN_STICK_RANGE = 0.5F;
    private static final int SHOT_WAIT = 20 * 6;
    private static final double GATE_AHEAD_DEG = 30.0;
    private static final String CLEAR_COMMAND = "propwash clear";

    private MultiJoinProbe() {
    }

    public static int extraTicks() {
        return JoinTestEnv.multi() == null ? 0 : TIMEOUT_TICKS;
    }

    public static @Nullable String observe(Map<String, Object> state, int tick, Minecraft mc) {
        String role = JoinTestEnv.multi();
        if (PILOT.equals(role) || MultiJoinCheck.SOLO.equals(role)) {
            Pilot pilot = (Pilot) state.computeIfAbsent(KEY, k -> new Pilot(tick, MultiJoinCheck.SOLO.equals(role)));
            return pilot.tick(tick, mc);
        }
        if (WATCHER.equals(role) || MultiJoinCheck.WATCH_SOLO.equals(role)) {
            Watcher watcher = (Watcher) state.computeIfAbsent(KEY, k -> new Watcher(tick, MultiJoinCheck.WATCH_SOLO.equals(role)));
            return watcher.tick(tick, mc);
        }
        if (MigrationJoinCheck.MODE.equals(role)) {
            Migration migration = (Migration) state.computeIfAbsent(KEY, k -> new Migration(tick));
            return migration.tick(tick, mc);
        }
        return "multi role unknown " + role + "\n";
    }

    static void showRaceOsd(Map<OsdElement, Boolean> shown) {
        OsdConfig config = OsdClient.CONFIG.get();
        List<OsdElement> wanted = new ArrayList<>(RACE_ELEMENTS);
        wanted.add(OsdElement.FLIGHT_PATH_MARKER);
        for (OsdElement element : wanted) {
            shown.putIfAbsent(element, config.slot(element).enabled);
            config.slot(element).enabled = true;
        }
    }

    static void restoreRaceOsd(Map<OsdElement, Boolean> shown) {
        OsdConfig config = OsdClient.CONFIG.get();
        shown.forEach((element, enabled) -> config.slot(element).enabled = enabled);
        shown.clear();
    }

    static CompletableFuture<String> picture(String name) {
        CompletableFuture<String> done = new CompletableFuture<>();
        Path file = Services.PLATFORM.getGameDir().resolve("jointest").resolve(name + ".png");
        try {
            Files.createDirectories(file.getParent());
        } catch (IOException e) {
            done.complete("kein Ordner: " + e);
            return done;
        }
        RenderTarget target = Minecraft.getInstance().gameRenderer.mainRenderTarget();
        Screenshot.takeScreenshot(target, 1, image -> Util.ioPool().execute(() -> {
            try (image) {
                image.writeToFile(file);
                done.complete(file.toString());
            } catch (Exception e) {
                done.complete("Bildschirmfoto fehlgeschlagen: " + e);
            }
        }));
        return done;
    }

    static String board(int course) {
        RacePayloads.LeaderboardS2C board = course < 0 ? null : ClientLeaderboards.get(course);
        if (board == null) {
            return "multi board=none\n";
        }
        List<RacePayloads.BoardRow> rows = new ArrayList<>(board.rows());
        rows.sort(Comparator.comparingInt(RacePayloads.BoardRow::overallRank).thenComparing(RacePayloads.BoardRow::name));
        StringBuilder out = new StringBuilder("multi board=");
        for (RacePayloads.BoardRow row : rows) {
            out.append(row.name()).append('/').append(row.frame()).append('/').append(row.nanos()).append('/').append(row.assist()).append(';');
        }
        return out.append('\n').toString();
    }

    static boolean boardHas(int course, String name) {
        RacePayloads.LeaderboardS2C board = course < 0 ? null : ClientLeaderboards.get(course);
        if (board == null) {
            return false;
        }
        for (RacePayloads.BoardRow row : board.rows()) {
            if (row.name().equals(name) && row.assist() == 0) {
                return true;
            }
        }
        return false;
    }

    private static float hoverThrottle(@Nullable DroneSim sim) {
        double hover = sim == null ? 0.35 : sim.telemetry().hoverThrottleStick;
        return (float) (Double.isFinite(hover) && hover > 0.05 ? hover : 0.35);
    }

    private static String seconds(long nanos) {
        return String.format(Locale.ROOT, "%.3f", nanos / 1.0E9);
    }

    private enum PilotPhase { SESSION, COURSE, ARM, CLEAR, DISARM, WAIT_WATCHER, RACE, BOARD, REMOVED }

    private static final class Pilot {
        PilotPhase phase = PilotPhase.SESSION;
        int since;
        final List<Vec3> path = new ArrayList<>();
        final List<LapTracker.Event> events = new ArrayList<>();
        double travelled;
        double length;
        int course = -1;
        boolean watched;
        boolean ghostShown;
        boolean recordGhost;
        boolean finished;
        long best;
        double footOffset;
        @Nullable String failure;

        final boolean solo;
        final Map<OsdElement, Boolean> shownOsd = new EnumMap<>(OsdElement.class);
        int clearTicks;
        int rearms;
        boolean removed;
        int removedAt = -1;

        Pilot(int tick, boolean solo) {
            this.since = tick;
            this.solo = solo;
        }

        void enter(PilotPhase next, int tick) {
            this.phase = next;
            this.since = tick;
            Constants.LOG.info("[Beitrittstest] Mehrspieler-Pilot: {}", next);
        }

        @Nullable String tick(int tick, Minecraft mc) {
            PilotSession session = PilotSession.INSTANCE;
            ClientRaceTracker tracker = ClientRaceTracker.INSTANCE;
            int in = tick - this.since;
            switch (this.phase) {
                case SESSION -> {
                    if (session.isFpv() && session.drone() != null && session.sim() != null) {
                        RaceConfig config = RaceConfig.CONFIG.get();
                        config.laps = LAPS;
                        config.countdown = false;
                        config.reportResults = true;
                        config.ghostMode = GhostChoice.Mode.OWN;
                        showRaceOsd(this.shownOsd);
                        this.enter(PilotPhase.COURSE, tick);
                    } else if (in > SESSION_WAIT) {
                        return this.finish("no session");
                    }
                }
                case COURSE -> {
                    session.setTestInput(StickPositions.IDLE, false, StabMode.ANGLE);
                    CourseData data = tracker.course();
                    if (data != null && data.gates().size() == MultiJoinCheck.GATES) {
                        this.course = data.id();
                        this.plan(ClientCourses.geometry(data), session);
                        this.enter(PilotPhase.ARM, tick);
                    } else if (in > COURSE_WAIT) {
                        return this.finish("no course");
                    }
                }
                case ARM -> {
                    DroneSim sim = session.sim();
                    boolean armed = sim != null && sim.telemetry().armed;
                    if (!armed) {
                        session.setTestInput(StickPositions.IDLE, sim != null && sim.telemetry().armBlockers == 0 && in % 10 >= 5, StabMode.ANGLE);
                    } else {
                        this.hold(session, this.path.getFirst());
                    }
                    if (armed && in > 10) {
                        this.enter(PilotPhase.CLEAR, tick);
                    } else if (in > ARM_WAIT) {
                        return this.finish("not armed");
                    }
                }
                case CLEAR -> {
                    this.hold(session, this.path.getFirst());
                    boolean clear = tracker.reasonsNow().none();
                    this.clearTicks = clear ? this.clearTicks + 1 : 0;
                    if (this.clearTicks >= 10) {
                        this.enter(PilotPhase.WAIT_WATCHER, tick);
                    } else if (in > CLEAR_WAIT && this.rearms < 2) {
                        this.rearms++;
                        Constants.LOG.info("[Beitrittstest] Mehrspieler-Pilot: Flughilfe vor dem Start {}, neu schärfen", tracker.reasonsNow().list());
                        this.enter(PilotPhase.DISARM, tick);
                    } else if (in > CLEAR_WAIT) {
                        this.enter(PilotPhase.WAIT_WATCHER, tick);
                    }
                }
                case DISARM -> {
                    session.setTestInput(StickPositions.IDLE, false, StabMode.ANGLE);
                    DroneSim sim = session.sim();
                    if (in > 20 && (sim == null || !sim.telemetry().armed)) {
                        this.enter(PilotPhase.ARM, tick);
                    }
                }
                case WAIT_WATCHER -> {
                    this.hold(session, this.path.getFirst());
                    if (signalled(mc.player)) {
                        this.watched = true;
                        this.enter(PilotPhase.RACE, tick);
                    } else if (in > (this.solo ? 20 : WATCHER_WAIT)) {
                        this.enter(PilotPhase.RACE, tick);
                    }
                    if (this.phase == PilotPhase.RACE) {
                        tracker.setEventListener(this.events::add);
                    }
                }
                case RACE -> {
                    this.fly(session, tracker);
                    if (this.travelled >= this.length) {
                        tracker.setEventListener(null);
                        this.enter(PilotPhase.BOARD, tick);
                    }
                }
                case BOARD -> {
                    this.hold(session, this.path.getLast());
                    if (in % 40 == 1) {
                        ClientLeaderboards.request(this.course);
                    }
                    if (boardHas(this.course, mc.getUser().getName()) && in > 40 || in > BOARD_WAIT) {
                        if (this.solo) {
                            return this.finish(null);
                        }
                        this.enter(PilotPhase.REMOVED, tick);
                    }
                }
                case REMOVED -> {
                    if (session.isActive()) {
                        this.hold(session, this.path.getLast());
                    } else if (this.removedAt < 0) {
                        this.removed = true;
                        this.removedAt = tick;
                        Constants.LOG.info("[Beitrittstest] Mehrspieler-Pilot: Drohne entfernt (/propwash clear), Sitzung beendet");
                        ClientLeaderboards.request(this.course);
                    }
                    if (this.removedAt >= 0 && tick - this.removedAt > AFTER_CLEAR || in > REMOVED_WAIT) {
                        return this.finish(null);
                    }
                }
            }
            return null;
        }

        private static boolean signalled(@Nullable LocalPlayer player) {
            if (player == null) {
                return false;
            }
            ItemStack stack = player.getInventory().getItem(MultiJoinCheck.SIGNAL_SLOT);
            Component name = stack.get(DataComponents.CUSTOM_NAME);
            return name != null && MultiJoinCheck.WATCHED_SIGNAL.equals(name.getString());
        }

        private void plan(List<GateGeometry> gates, PilotSession session) {
            GateGeometry first = gates.getFirst();
            GateGeometry last = gates.getLast();
            QuadcopterEntity drone = session.drone();
            this.footOffset = drone == null ? 0.0 : drone.getBbHeight() * 0.5;
            double x = first.x();
            double y = first.centerY();
            double zStart = first.z() - APPROACH;
            double zEnd = last.z() + APPROACH;
            this.path.add(new Vec3(x, y, zStart));
            for (int loop = 0; loop <= LAPS; loop++) {
                this.path.add(new Vec3(x, y, zEnd));
                this.path.add(new Vec3(x + LOOP_WIDTH, y, zEnd));
                this.path.add(new Vec3(x + LOOP_WIDTH, y, zStart));
                this.path.add(new Vec3(x, y, zStart));
            }
            double total = 0.0;
            for (int i = 1; i < this.path.size(); i++) {
                total += this.path.get(i).distanceTo(this.path.get(i - 1));
            }
            this.length = total;
            Constants.LOG.info("[Beitrittstest] Mehrspieler-Pilot: Kurs {} mit {} Gates, Weg {} m in {} Runden", this.course, gates.size(),
                    String.format(Locale.ROOT, "%.1f", total), LAPS);
        }

        private void hold(PilotSession session, Vec3 at) {
            session.setTestInput(new StickPositions(0.0F, 0.0F, 0.0F, hoverThrottle(session.sim())), true, StabMode.ANGLE);
            TrainingSelfTests.teleport(at.x, at.y - this.footOffset, at.z, 0.0, 0.0, 0.0, 0.0);
        }

        private void fly(PilotSession session, ClientRaceTracker tracker) {
            DroneSim sim = session.sim();
            session.setTestInput(new StickPositions(0.0F, 0.0F, 0.0F, hoverThrottle(sim)), true, StabMode.ANGLE);
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
            RaceState race = RaceState.INSTANCE;
            if (race.lap() >= 2 && tracker.ghosts().shownPersonal() != null) {
                this.ghostShown = true;
            }
            if (tracker.ghosts().record() != null) {
                this.recordGhost = true;
            }
            if (race.finished()) {
                this.finished = true;
                this.best = race.bestLapNanos();
            }
        }

        private String finish(@Nullable String failure) {
            this.failure = failure;
            restoreRaceOsd(this.shownOsd);
            ClientRaceTracker.INSTANCE.setEventListener(null);
            PilotSession.INSTANCE.clearTestInput();
            long laps = this.events.stream().filter(e -> e.type() == LapTracker.EventType.LAP).count();
            long gates = this.events.stream().filter(e -> e.type() == LapTracker.EventType.GATE).count();
            boolean finishEvent = this.events.stream().anyMatch(e -> e.type() == LapTracker.EventType.FINISH);
            List<String> problems = new ArrayList<>();
            for (LapTracker.Event event : this.events) {
                switch (event.type()) {
                    case MISSED, WRONG_WAY, WRONG_SIDE, FALSE_START, ABORT -> problems.add(event.type() + "@" + event.lap() + "/" + event.gate());
                    default -> {
                    }
                }
            }
            Minecraft mc = Minecraft.getInstance();
            StringBuilder out = new StringBuilder(this.solo ? "multi role=pilot solo\n" : "multi role=pilot\n");
            if (failure != null) {
                out.append("multi pilot failed ").append(failure).append('\n');
            }
            out.append("multi watcher signal=").append(this.watched ? "seen" : "missing").append('\n');
            out.append("multi race laps=").append(laps).append(" gates=").append(gates).append(" finish=").append(finishEvent)
                    .append(" best=").append(seconds(this.best)).append(" problems=").append(problems).append('\n');
            if (finishEvent && this.finished && laps == LAPS && problems.isEmpty()) {
                out.append("multi race finished\n");
            }
            ClientRaceTracker tracker = ClientRaceTracker.INSTANCE;
            out.append("multi assist lap=").append(tracker.lastLapReasons().list()).append(" now=").append(tracker.reasonsNow().list())
                    .append(" details=").append(tracker.lastLapReasons().details()).append('\n');
            out.append(this.ghostShown ? "multi ghost shown\n" : "multi ghost missing\n");
            out.append("multi record ghost=").append(this.recordGhost ? "yes" : "no").append('\n');
            out.append(boardHas(this.course, mc.getUser().getName()) ? "multi leaderboard ok\n" : "multi leaderboard missing\n");
            if (!this.solo) {
                out.append(this.removed ? "multi cleared\n" : "multi clear missing\n");
            }
            out.append(board(this.course));
            return out.toString();
        }
    }

    private enum WatchPhase { GOGGLES, CHANNEL, WATCH, OPERATE, CLEAR }

    private static final class Watcher {
        WatchPhase phase = WatchPhase.GOGGLES;
        int since;
        int watchTicks;
        int boardSeenAt = -1;
        int course = -1;
        double moved;
        @Nullable Vec3 lastCamera;
        @Nullable String pilotName;
        int markerTicks;
        int raceTicks;
        int clearedAt = -1;
        boolean sent;
        final Map<OsdElement, Boolean> shown = new EnumMap<>(OsdElement.class);
        boolean unwatched;
        int gimbalDrone = -1;
        int gimbalFrom = -1;
        double panStart;
        double pan;
        double pitch = Double.NaN;
        @Nullable String gimbalLabel;
        @Nullable String operator;
        int viewEndedAt = -1;
        int feedTicks;
        int lookTicks;
        int gateTicks;
        float stickMin = Float.POSITIVE_INFINITY;
        float stickMax = Float.NEGATIVE_INFINITY;
        @Nullable String lookPreset;
        @Nullable CompletableFuture<String> shot;
        int overlaysClearedAt = -1;

        final boolean solo;

        Watcher(int tick, boolean solo) {
            this.since = tick;
            this.solo = solo;
        }

        void enter(WatchPhase next, int tick) {
            this.phase = next;
            this.since = tick;
            Constants.LOG.info("[Beitrittstest] Mehrspieler-Zuschauer: {}", next);
        }

        @Nullable String tick(int tick, Minecraft mc) {
            SpectateSession session = SpectateSession.INSTANCE;
            int in = tick - this.since;
            switch (this.phase) {
                case GOGGLES -> {
                    if (SpectateSession.goggles(mc) == VideoSystem.ANALOG) {
                        this.enter(WatchPhase.CHANNEL, tick);
                    } else if (in > GOGGLES_WAIT) {
                        return this.finish("no goggles");
                    }
                }
                case CHANNEL -> {
                    ChannelListS2C.Entry entry = null;
                    for (ChannelListS2C.Entry candidate : session.watchable(mc)) {
                        if (candidate.pilotName().equals(MultiJoinCheck.PILOT)) {
                            entry = candidate;
                        }
                    }
                    if (session.watching() && session.targetEntry() != null && MultiJoinCheck.PILOT.equals(session.targetEntry().pilotName())) {
                        this.pilotName = session.targetEntry().pilotName();
                        this.showOsd();
                        this.enter(WatchPhase.WATCH, tick);
                    } else if (entry != null && in % 40 == 10) {
                        KeyMapping.click(InputConstants.getKey(SpectateKeys.CYCLE.saveString()));
                    } else if (in > CHANNEL_WAIT) {
                        return this.finish("no channel");
                    }
                }
                case WATCH -> {
                    if (this.course < 0) {
                        for (CourseData data : ClientCourses.all()) {
                            this.course = data.id();
                        }
                    }
                    if (session.watching() && session.drone() != null) {
                        this.watchTicks++;
                        if (OsdHud.drewLastFrame(OsdElement.FLIGHT_PATH_MARKER)) {
                            this.markerTicks++;
                        }
                        if (RACE_ELEMENTS.stream().anyMatch(OsdHud::drewLastFrame)) {
                            this.raceTicks++;
                        }
                        this.pilotView(tick, mc);
                        Vec3 camera = mc.gameRenderer.mainCamera().position();
                        if (this.lastCamera != null) {
                            this.moved += camera.distanceTo(this.lastCamera);
                        }
                        this.lastCamera = camera;
                    }
                    if (in % 40 == 1 && this.course >= 0) {
                        ClientLeaderboards.request(this.course);
                    }
                    if (this.boardSeenAt < 0 && boardHas(this.course, MultiJoinCheck.PILOT)) {
                        this.boardSeenAt = tick;
                    }
                    boolean enough = this.watchTicks >= 2 * MIN_WATCH_TICKS && this.moved >= MIN_WATCH_MOVED;
                    if (this.solo && enough || this.boardSeenAt >= 0 && tick - this.boardSeenAt > WATCH_AFTER_BOARD || in > WATCH_WAIT) {
                        this.restoreOsd();
                        this.enter(WatchPhase.OPERATE, tick);
                    }
                }
                case OPERATE -> {
                    if (this.operate(tick, in, mc)) {
                        this.enter(WatchPhase.CLEAR, tick);
                    }
                }
                case CLEAR -> {
                    if (!this.sent && mc.getConnection() != null) {
                        this.sent = true;
                        mc.getConnection().sendCommand(CLEAR_COMMAND);
                        Constants.LOG.info("[Beitrittstest] Mehrspieler-Zuschauer: /{} gesendet", CLEAR_COMMAND);
                    }
                    if (this.clearedAt < 0 && (!session.watching() || session.drone() == null) && !OperatorClient.bound()) {
                        this.clearedAt = tick;
                        OperatorClient.testInput(null, 0);
                        Constants.LOG.info("[Beitrittstest] Mehrspieler-Zuschauer: Zuschauen beendet, Drohnen entfernt{}",
                                this.gimbalDrone >= 0 ? ", Gimbal-Steuerung abgegeben" : "");
                        if (this.course >= 0) {
                            ClientLeaderboards.request(this.course);
                        }
                    }
                    if (this.clearedAt >= 0 && this.viewEndedAt < 0 && !session.operator()) {
                        this.viewEndedAt = tick;
                        if (this.gimbalDrone >= 0) {
                            Constants.LOG.info("[Beitrittstest] Mehrspieler-Zuschauer: Operator-Ansicht {} Ticks nach dem Entfernen beendet", tick - this.clearedAt);
                        }
                    }
                    boolean viewDone = this.gimbalDrone < 0 || this.viewEndedAt >= 0 || tick - this.clearedAt > VIEW_END_WAIT;
                    if (this.clearedAt >= 0 && tick - this.clearedAt > AFTER_CLEAR && viewDone || in > CLEAR_DONE_WAIT) {
                        return this.finish(null);
                    }
                }
            }
            return null;
        }

        private boolean operate(int tick, int in, Minecraft mc) {
            SpectateSession session = SpectateSession.INSTANCE;
            if (!this.unwatched) {
                this.unwatched = true;
                if (session.active()) {
                    session.stop(mc, true);
                }
                Constants.LOG.info("[Beitrittstest] Mehrspieler-Zuschauer: Zuschauen beendet, warte auf den Gimbal einer Drohne im autonomen Halten");
            }
            if (this.gimbalDrone < 0) {
                if (OperatorClient.operating()) {
                    this.gimbalDrone = OperatorClient.operatorDrone();
                    QuadcopterEntity drone = drone(mc, this.gimbalDrone);
                    this.panStart = drone == null ? 0.0 : ExampleOperatorClient.PROVIDER.pan(drone);
                    this.gimbalFrom = tick;
                    OperatorClient.testInput(GIMBAL_AXES, 0);
                    Constants.LOG.info("[Beitrittstest] Mehrspieler-Zuschauer: Operator am Gimbal der Drohne {} (Ansicht {}), schickt Achsen {}", this.gimbalDrone,
                            session.phase(), Arrays.toString(GIMBAL_AXES));
                } else if (in > OPERATE_WAIT) {
                    this.operator = "not operator after " + in / 20 + " s (bound " + OperatorClient.bound() + ", view " + session.phase() + ")";
                    return true;
                }
                return false;
            }
            QuadcopterEntity drone = drone(mc, this.gimbalDrone);
            if (drone == null || !OperatorClient.operating()) {
                this.operator = "operator view lost";
                return true;
            }
            this.pan = Math.max(this.pan, ExampleOperatorClient.PROVIDER.pan(drone) - this.panStart);
            this.pitch = ExampleOperatorClient.PROVIDER.pitch(drone);
            String label = AutonomyLabels.label(drone);
            if (label != null) {
                this.gimbalLabel = label;
            }
            int held = tick - this.gimbalFrom;
            if (held >= GIMBAL_MIN_TICKS && this.pan >= GIMBAL_MIN_PAN) {
                this.operator = "ok";
                Constants.LOG.info("[Beitrittstest] Mehrspieler-Zuschauer: Gimbal-Lage am Operator-Client: Schwenk {}°, Neigung {}°, {} Eingaben gesendet",
                        String.format(Locale.ROOT, "%.1f", this.pan), String.format(Locale.ROOT, "%.1f", this.pitch), OperatorClient.sent());
                return true;
            }
            if (held > GIMBAL_WAIT) {
                this.operator = String.format(Locale.ROOT, "gimbal still, pan %.1f", this.pan);
                return true;
            }
            return false;
        }

        private void pilotView(int tick, Minecraft mc) {
            if (WatchedPilot.feed() != null) {
                OsdData data = OsdHud.sampler().data();
                this.feedTicks++;
                this.stickMin = Math.min(this.stickMin, data.stickRoll);
                this.stickMax = Math.max(this.stickMax, data.stickRoll);
            }
            OsdConfig shownConfig = OsdHud.config();
            if (shownConfig != OsdClient.CONFIG.get()) {
                this.lookTicks++;
                this.lookPreset = shownConfig.preset.key();
            }
            if (ScoreAreaRenderer.lastDrawn().stream().anyMatch(drawn -> drawn.kind() == ScoreAreaRenderer.Kind.OPENING)) {
                this.gateTicks++;
            }
            boolean ready = this.feedTicks >= MIN_FEED_TICKS && this.raceTicks > 0 && this.lookTicks > 0 && this.stickMax - this.stickMin >= MIN_STICK_RANGE;
            if (this.shot != null || !ready) {
                return;
            }
            if (this.overlaysClearedAt < 0) {
                Screenshots.clearOverlays(mc);
                this.overlaysClearedAt = tick;
                return;
            }
            boolean ahead = activeGateAhead(mc);
            if (tick - this.overlaysClearedAt >= 5 && ahead || tick - this.overlaysClearedAt > SHOT_WAIT) {
                if (!ahead) {
                    Screenshots.clearOverlays(mc);
                }
                this.shot = picture(JoinTestEnv.label() + "-pilot-osd");
                Constants.LOG.info("[Beitrittstest] Mehrspieler-Zuschauer: Bild mit Renn-HUD, OSD des Piloten ({}) und bewegten Sticks, aktives Gate {}",
                        this.lookPreset, ahead ? "vor der Kamera" : "nicht im Bild");
            }
        }

        private static boolean activeGateAhead(Minecraft mc) {
            PilotFeed feed = WatchedPilot.feed();
            if (feed == null || SpectateSession.INSTANCE.drone() == null || mc.level == null) {
                return false;
            }
            Vec3 eye = mc.gameRenderer.mainCamera().position();
            Vec3 forward = new Vec3(mc.gameRenderer.mainCamera().forwardVector());
            for (Entity entity : mc.level.entitiesForRendering()) {
                if (!(entity instanceof GateEntity gate) || !gate.inCourse() || gate.courseId() != feed.courseId() || !gate.hasPass(feed.nextGateIndex())) {
                    continue;
                }
                Vec3 toGate = gate.getBoundingBox().getCenter().subtract(eye);
                double distance = toGate.length();
                if (distance > 4.0 && distance < 40.0 && Math.toDegrees(Math.acos(Mth.clamp(toGate.normalize().dot(forward), -1.0, 1.0))) < GATE_AHEAD_DEG) {
                    return true;
                }
            }
            return false;
        }

        private static @Nullable QuadcopterEntity drone(Minecraft mc, int id) {
            return mc.level != null && mc.level.getEntity(id) instanceof QuadcopterEntity drone && !drone.isRemoved() ? drone : null;
        }

        private void showOsd() {
            showRaceOsd(this.shown);
        }

        private void restoreOsd() {
            restoreRaceOsd(this.shown);
        }

        private String finish(@Nullable String failure) {
            this.restoreOsd();
            OperatorClient.testInput(null, 0);
            int ended = this.clearedAt >= 0 && this.viewEndedAt >= 0 ? this.viewEndedAt - this.clearedAt : -1;
            boolean released = this.gimbalDrone >= 0 && ended >= 0 && ended <= AFTER_CLEAR && !OperatorClient.bound();
            StringBuilder out = new StringBuilder(this.solo ? "multi role=watcher solo\n" : "multi role=watcher\n");
            if (failure != null) {
                out.append("multi watcher failed ").append(failure).append('\n');
            }
            out.append("multi watch pilot=").append(this.pilotName).append(" ticks=").append(this.watchTicks).append(" moved=")
                    .append(String.format(Locale.ROOT, "%.1f", this.moved)).append('\n');
            if (MultiJoinCheck.PILOT.equals(this.pilotName) && this.watchTicks >= MIN_WATCH_TICKS && this.moved >= MIN_WATCH_MOVED) {
                out.append("multi watched pilot\n");
            }
            out.append("multi watcher marker=").append(this.markerTicks >= MIN_MARKER_TICKS ? "drawn" : "missing").append(" ticks=")
                    .append(this.markerTicks).append('\n');
            out.append("multi watcher racehud=").append(this.raceTicks > 0 ? "drawn" : "missing").append(" ticks=").append(this.raceTicks).append('\n');
            out.append("multi watcher osd=").append(this.lookTicks > 0 ? "pilot" : "own").append(" preset=").append(this.lookPreset == null ? "-" : this.lookPreset)
                    .append(" ticks=").append(this.lookTicks).append(" looks=").append(WatchedPilot.looks()).append('\n');
            float range = this.feedTicks == 0 ? 0.0F : this.stickMax - this.stickMin;
            out.append("multi watcher sticks=").append(this.feedTicks >= MIN_FEED_TICKS && range >= MIN_STICK_RANGE ? "moving" : "still")
                    .append(String.format(Locale.ROOT, " range=%.2f", range)).append(" feed_ticks=").append(this.feedTicks).append(" feeds=")
                    .append(WatchedPilot.feeds()).append('\n');
            out.append("multi watcher gate=").append(this.gateTicks > 0 ? "lit" : "dark").append(" ticks=").append(this.gateTicks).append('\n');
            CompletableFuture<String> picture = this.shot;
            out.append("multi watcher shot=").append(picture == null ? "none" : picture.isDone() ? picture.join() : "pending").append('\n');
            if ("ok".equals(this.operator)) {
                out.append("multi operator ok\n");
            } else {
                out.append("multi operator failed ").append(this.operator == null ? "not reached" : this.operator).append('\n');
            }
            out.append("multi operator drone=").append(this.gimbalDrone).append(" pan=").append(String.format(Locale.ROOT, "%.1f", this.pan)).append(" pitch=")
                    .append(String.format(Locale.ROOT, "%.1f", this.pitch)).append(" sent=").append(OperatorClient.sent()).append(" label=")
                    .append(this.gimbalLabel == null ? "none" : this.gimbalLabel).append(" view_ended=").append(ended < 0 ? "never" : ended + " ticks")
                    .append(" view_phase=").append(SpectateSession.INSTANCE.phase()).append('\n');
            if (released) {
                out.append("multi operator released\n");
            }
            out.append(this.clearedAt >= 0 ? "multi cleared\n" : "multi clear missing\n");
            out.append(boardHas(this.course, MultiJoinCheck.PILOT) ? "multi leaderboard ok\n" : "multi leaderboard missing\n");
            out.append(board(this.course));
            return out.toString();
        }
    }

    private enum MigrationPhase { SIGNAL, SESSION, FLY, LAP, SAVED }

    private static final class Migration {
        static final double SPEED = 6.0;
        static final int SIGNAL_WAIT = 20 * 150;
        static final int SESSION_WAIT = 20 * 60;
        static final int LAP_WAIT = 20 * 60;
        static final int SAVED_WAIT = 20 * 60;
        MigrationPhase phase = MigrationPhase.SIGNAL;
        int since;
        final Map<String, String> server = new LinkedHashMap<>();
        final List<Vec3> path = new ArrayList<>();
        double distance;
        int course = -1;
        List<Integer> bottom = List.of();
        @Nullable Boolean legs;
        long lap;
        @Nullable String failure;
        boolean setupSeen;
        @Nullable String freecam;

        Migration(int tick) {
            this.since = tick;
        }

        void enter(MigrationPhase next, int tick) {
            this.phase = next;
            this.since = tick;
            Constants.LOG.info("[Beitrittstest] Migration-Pilot: {} {}", next, this.server);
        }

        @Nullable String tick(int tick, Minecraft mc) {
            this.read(mc.player);
            int in = tick - this.since;
            PilotSession session = PilotSession.INSTANCE;
            if (this.freecam == null && mc.player != null && ClientWorldRules.INSTANCE.known() && !session.isActive()) {
                List<String> problems = new ArrayList<>();
                String seen = MigrationSelfTests.freecamClient(mc, problems);
                this.freecam = problems.isEmpty() ? "rule_off" : String.join("; ", problems);
                Constants.LOG.info("[Beitrittstest] Migration-Pilot: {}{}", seen, problems.isEmpty() ? "" : ", Befund: " + this.freecam);
            }
            switch (this.phase) {
                case SIGNAL -> {
                    if ("fail".equals(this.server.get("world")) && !this.server.containsKey("player")) {
                        return this.finish("server world failed");
                    }
                    if (this.server.containsKey("player") && this.server.containsKey("course")) {
                        this.course = Integer.parseInt(this.server.get("course"));
                        this.bottom = parse(this.server.getOrDefault("bottom", "[]"));
                        this.enter(MigrationPhase.SESSION, tick);
                    } else if (this.server.containsKey("player") && this.server.containsKey("lap")) {
                        return this.finish("server lap " + this.server.get("lap"));
                    } else if (in > SIGNAL_WAIT) {
                        return this.finish("no signal");
                    }
                }
                case SESSION -> {
                    QuadcopterEntity drone = session.isFpv() ? session.drone() : null;
                    CourseData data = ClientCourses.get(this.course);
                    if (drone != null && data != null) {
                        List<GateGeometry> gates = ClientCourses.geometry(data);
                        ClientGateOpenings openings = ClientGateOpenings.of(this.course, gates);
                        boolean all = !this.bottom.isEmpty();
                        for (int index : this.bottom) {
                            all &= openings.legs(index);
                        }
                        if (all || in > SESSION_WAIT / 2) {
                            this.legs = all;
                            GateGeometry first = gates.getFirst();
                            this.path.add(new Vec3(drone.getX(), drone.getY() + drone.getBbHeight() * 0.5, drone.getZ()));
                            this.path.add(MigrationValues.point(first, -3.0, 0.0, MigrationValues.height(first, 0, this.bottom)));
                            this.path.addAll(MigrationLegs.path(gates, this.bottom));
                            this.path.add(MigrationValues.point(first, 4.0, 0.0, first.centerY()));
                            this.enter(MigrationPhase.FLY, tick);
                        }
                    } else if (in > SESSION_WAIT) {
                        return this.finish(drone == null ? "no session" : "no course " + this.course);
                    }
                }
                case FLY -> {
                    QuadcopterEntity drone = session.isFpv() ? session.drone() : null;
                    if (drone == null) {
                        return this.finish("session lost");
                    }
                    this.distance += SPEED / 20.0;
                    Vec3[] pose = along(this.path, this.distance);
                    TrainingSelfTests.teleport(pose[0].x, pose[0].y - drone.getBbHeight() * 0.5, pose[0].z, 0.0, pose[1].x * SPEED, pose[1].y * SPEED,
                            pose[1].z * SPEED);
                    if (pose[1].lengthSqr() == 0.0) {
                        this.enter(MigrationPhase.LAP, tick);
                    }
                }
                case LAP -> {
                    LapTracker tracker = ClientRaceTracker.INSTANCE.tracker();
                    if (this.lap <= 0L && tracker.courseId() == this.course && tracker.lastLapNanos() > 0L) {
                        this.lap = tracker.lastLapNanos();
                    }
                    if (this.lap > 0L && this.server.containsKey("lap")) {
                        this.enter(MigrationPhase.SAVED, tick);
                    } else if (in > LAP_WAIT) {
                        return this.finish(this.lap > 0L ? "server lap missing" : "client lap missing");
                    }
                }
                case SAVED -> {
                    if (this.server.containsKey("saved") || in > SAVED_WAIT) {
                        return this.finish(null);
                    }
                }
            }
            return null;
        }

        private void read(@Nullable LocalPlayer player) {
            if (player == null) {
                return;
            }
            Component name = player.getInventory().getItem(MultiJoinCheck.SIGNAL_SLOT).get(DataComponents.CUSTOM_NAME);
            String text = name == null ? "" : name.getString();
            if (!text.startsWith(MigrationJoinCheck.SIGNAL + " ")) {
                return;
            }
            Map<String, String> paper = new LinkedHashMap<>();
            for (String part : text.substring(MigrationJoinCheck.SIGNAL.length()).trim().split(" ")) {
                int eq = part.indexOf('=');
                if (eq > 0) {
                    paper.put(part.substring(0, eq), part.substring(eq + 1));
                }
            }
            if (!this.setupSeen) {
                boolean setup = paper.containsKey("player") || "fail".equals(paper.get("world"));
                if (!setup || paper.containsKey("lap") || paper.containsKey("saved")) {
                    return;
                }
                this.setupSeen = true;
            }
            this.server.clear();
            this.server.putAll(paper);
        }

        private static List<Integer> parse(String list) {
            List<Integer> out = new ArrayList<>();
            for (String part : list.replace("[", "").replace("]", "").split(",")) {
                if (!part.isBlank()) {
                    out.add(Integer.parseInt(part.trim()));
                }
            }
            return out;
        }

        private String finish(@Nullable String failure) {
            this.failure = failure;
            StringBuilder out = new StringBuilder("multi role=migration\n");
            if (failure != null) {
                out.append("migration failed ").append(failure).append('\n');
            }
            this.server.forEach((key, value) -> out.append("migration server ").append(key).append('=').append(value).append('\n'));
            out.append("migration client legs=").append(this.legs).append('\n');
            out.append("migration client freecam=").append(this.freecam == null ? "unknown" : this.freecam).append('\n');
            if (this.lap > 0L) {
                out.append("migration lap counted ").append(seconds(this.lap)).append(" s\n");
            }
            out.append(board(this.course));
            return out.toString();
        }
    }

    private static Vec3[] along(List<Vec3> path, double distance) {
        double left = distance;
        for (int i = 1; i < path.size(); i++) {
            Vec3 from = path.get(i - 1);
            Vec3 to = path.get(i);
            double length = from.distanceTo(to);
            if (left <= length && length > 1.0E-9) {
                Vec3 direction = to.subtract(from).scale(1.0 / length);
                return new Vec3[]{from.add(direction.scale(left)), direction};
            }
            left -= length;
        }
        return new Vec3[]{path.getLast(), Vec3.ZERO};
    }
}
