package de.lardtom.propwash.race.client;

import de.lardtom.propwash.audio.client.AudioHooks;
import de.lardtom.propwash.audio.client.Beep;
import de.lardtom.propwash.drone.QuadcopterEntity;
import de.lardtom.propwash.drone.client.PilotSession;
import de.lardtom.propwash.network.client.ClientNet;
import de.lardtom.propwash.race.AssistReasons;
import de.lardtom.propwash.race.CourseData;
import de.lardtom.propwash.race.CourseElement;
import de.lardtom.propwash.race.CourseLaps;
import de.lardtom.propwash.race.GateEntity;
import de.lardtom.propwash.race.GateGeometry;
import de.lardtom.propwash.race.GateMounting;
import de.lardtom.propwash.race.GhostLook;
import de.lardtom.propwash.race.GhostRecorder;
import de.lardtom.propwash.race.GhostTrack;
import de.lardtom.propwash.race.LapTracker;
import de.lardtom.propwash.race.RaceClock;
import de.lardtom.propwash.race.RaceFormat;
import de.lardtom.propwash.race.RacePayloads;
import de.lardtom.propwash.server.client.ClientWorldRules;
import de.lardtom.propwash.server.rules.WorldRuleSpecs;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Util;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.joml.Quaternionfc;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;

public final class ClientRaceTracker {
    public static final ClientRaceTracker INSTANCE = new ClientRaceTracker();
    public static final double START_SELECT_RANGE = 64.0;
    public static final double COUNTDOWN_RANGE = 24.0;
    public static final long GO_LIGHT_NANOS = 1_000_000_000L;
    public static final long LOBBY_STALE_NANOS = 2_500_000_000L;
    private static final Quaternionfc LEVEL = new Quaternionf();
    private static final int HUD_DECIMALS = 2;

    private final LapTracker tracker = new LapTracker();
    private final GhostRecorder recorder = new GhostRecorder();
    private final GhostLibrary ghosts = new GhostLibrary();
    private final RaceClock clock = new RaceClock();
    private @Nullable Consumer<LapTracker.Event> eventListener;
    private boolean wasArmed;
    private @Nullable Vec3 lastPosition;
    private boolean resetStart;
    private AssistReasons lapAssist = AssistReasons.NONE;
    private AssistReasons assistNow = AssistReasons.NONE;
    private AssistReasons lastLapAssist = AssistReasons.NONE;
    private String frame = "";
    private @Nullable GhostLook look;
    private int referenceVersion = -1;
    private long goAt = Long.MIN_VALUE;
    private long countdownTotal;
    private int lobbyCourse = LapTracker.NO_COURSE;
    private boolean lobbyMember;
    private List<String> lobbyPilots = List.of();
    private long lobbyDeadline;
    private long lobbyUpdated;
    private int startPilots;

    private ClientRaceTracker() {
    }

    public LapTracker tracker() {
        return this.tracker;
    }

    public GhostRecorder recorder() {
        return this.recorder;
    }

    public GhostLibrary ghosts() {
        return this.ghosts;
    }

    public long now() {
        return this.clock.now(Util.getNanos());
    }

    public boolean paused() {
        return this.clock.paused();
    }

    public void setEventListener(@Nullable Consumer<LapTracker.Event> listener) {
        this.eventListener = listener;
    }

    public @Nullable Vec3 lastPosition() {
        return this.lastPosition;
    }

    public int courseId() {
        return this.tracker.courseId();
    }

    public @Nullable CourseData course() {
        int id = this.tracker.courseId();
        return id == LapTracker.NO_COURSE ? null : ClientCourses.get(id);
    }

    public int nextGateIndex() {
        CourseData course = this.course();
        int position = this.tracker.nextGate();
        if (course == null || position < 0 || position >= course.gates().size()) {
            return GateEntity.NO_INDEX;
        }
        return course.gates().get(position).index();
    }

    public int startGateIndex() {
        CourseData course = this.course();
        return course == null || course.gates().isEmpty() ? GateEntity.NO_INDEX : course.gates().getFirst().index();
    }

    public boolean countdownActive() {
        return this.tracker.phase() == LapTracker.Phase.COUNTDOWN;
    }

    public long countdownTotalNanos() {
        return this.countdownTotal;
    }

    public long sinceGoNanos() {
        return this.goAt == Long.MIN_VALUE ? Long.MAX_VALUE : this.now() - this.goAt;
    }

    public boolean lapAssisted() {
        return this.lapAssist.any();
    }

    public int lapAssist() {
        return this.lapAssist.mask();
    }

    public int assistNow() {
        return this.assistNow.mask();
    }

    public int lastLapAssist() {
        return this.lastLapAssist.mask();
    }

    public AssistReasons lapReasons() {
        return this.lapAssist;
    }

    public AssistReasons reasonsNow() {
        return this.assistNow;
    }

    public AssistReasons lastLapReasons() {
        return this.lastLapAssist;
    }

    public String frame() {
        return this.frame;
    }

    public int lobbyCourse() {
        return this.lobbyCourse;
    }

    public boolean lobbyMember() {
        return this.lobbyMember;
    }

    public List<String> lobbyPilots() {
        return Util.getNanos() - this.lobbyUpdated > LOBBY_STALE_NANOS ? List.of() : this.lobbyPilots;
    }

    public long lobbyRemainingNanos() {
        return Math.max(0L, this.lobbyDeadline - Util.getNanos());
    }

    public int startPilots() {
        return this.startPilots;
    }

    void reset() {
        long now = this.now();
        this.tracker.abort(now, LapTracker.AbortReason.SESSION_ENDED);
        this.tracker.clearCourse(now);
        this.tracker.resetPosition();
        this.tracker.drainEvents();
        this.recorder.reset();
        this.ghosts.clear();
        this.wasArmed = false;
        this.resetStart = false;
        this.lapAssist = AssistReasons.NONE;
        this.lastLapAssist = AssistReasons.NONE;
        this.referenceVersion = -1;
        this.goAt = Long.MIN_VALUE;
        this.clearLobby();
        this.lastPosition = null;
        RaceHud.clear();
    }

    private void clearLobby() {
        this.lobbyCourse = LapTracker.NO_COURSE;
        this.lobbyMember = false;
        this.lobbyPilots = List.of();
        this.lobbyDeadline = 0L;
    }

    void onFrame(Minecraft minecraft) {
        this.clock.setPaused(minecraft.isPaused(), Util.getNanos());
        if (minecraft.level == null || minecraft.player == null || this.clock.paused()) {
            return;
        }
        long now = this.now();
        PilotSession session = PilotSession.INSTANCE;
        QuadcopterEntity drone = session.isActive() ? session.drone() : null;
        if (drone == null) {
            LapTracker.Phase phase = this.tracker.phase();
            if (phase == LapTracker.Phase.RACING || phase == LapTracker.Phase.COUNTDOWN) {
                this.tracker.abort(now, LapTracker.AbortReason.SESSION_ENDED);
            }
            this.tracker.resetPosition();
            this.wasArmed = false;
            this.assistNow = AssistReasons.NONE;
            if (this.lobbyMember) {
                this.leaveLobby();
            }
            this.lastPosition = null;
            this.dispatch();
            return;
        }
        Vec3 position = new Vec3(drone.getX(), drone.getY() + drone.getBbHeight() * 0.5, drone.getZ());
        boolean armed = drone.isArmed();
        this.frame = drone.build().frame().key();
        if (this.look == null || !this.look.matches(drone.build(), drone.paint())) {
            this.look = GhostLook.of(drone.build(), drone.paint());
        }
        this.assistNow = AssistText.evaluate(session, drone);
        RaceConfig config = RaceConfig.CONFIG.get();
        this.selectCourse(minecraft, position, now);
        this.applyLapTarget();
        this.syncGhosts(minecraft);
        LapTracker.Phase phase = this.tracker.phase();
        boolean active = phase == LapTracker.Phase.RACING || phase == LapTracker.Phase.COUNTDOWN;
        if (armed && !this.wasArmed) {
            if (this.standingStart(config) && !active && !this.lobbyMember && this.nearStart(position)) {
                this.requestStart(now, config);
            }
            this.resetStart = false;
        }
        this.wasArmed = armed;
        if (this.tracker.racing() && this.assistNow.any()) {
            AssistReasons fresh = this.assistNow.without(this.lapAssist.mask());
            this.lapAssist = this.lapAssist.plus(this.assistNow);
            if (fresh.any()) {
                String key = savesAssisted() ? "propwash.race.msg.assist_now_saved" : "propwash.race.msg.assist_now";
                RaceHud.message(Component.translatable(key, AssistText.reasons(fresh)), RaceHud.YELLOW, 4_000_000_000L);
            }
        }
        this.lastPosition = position;
        this.tracker.update(now, position.x, position.y, position.z);
        this.recorder.sample(now, drone.getX(), drone.getY(), drone.getZ(), drone.orientation());
        this.dispatch();
    }

    public void feed(long nanos, Vec3 position) {
        this.lastPosition = position;
        this.tracker.update(nanos, position.x, position.y, position.z);
        this.recorder.sample(nanos, position.x, position.y, position.z, LEVEL);
        this.dispatch();
    }

    private void syncGhosts(Minecraft minecraft) {
        CourseData course = this.course();
        if (course == null) {
            return;
        }
        this.ghosts.ensure(minecraft, course, this.frame);
        if (this.referenceVersion != this.ghosts.version()) {
            this.referenceVersion = this.ghosts.version();
            this.tracker.setReference(this.ghosts.referenceSplits());
        }
    }

    public void syncGhostsFor(Minecraft minecraft, String frame) {
        this.frame = frame;
        this.syncGhosts(minecraft);
    }

    private void requestStart(long now, RaceConfig config) {
        if (ClientNet.canSend(RacePayloads.LobbyC2S.TYPE)) {
            ClientNet.sendToServer(new RacePayloads.LobbyC2S(RacePayloads.LobbyAction.JOIN, this.tracker.courseId(), config.countdownSeconds));
            return;
        }
        this.beginCountdown(now, config.countdownSeconds * LapTracker.SECOND, 1);
    }

    private void leaveLobby() {
        if (ClientNet.canSend(RacePayloads.LobbyC2S.TYPE)) {
            ClientNet.sendToServer(new RacePayloads.LobbyC2S(RacePayloads.LobbyAction.LEAVE, this.lobbyCourse, 0));
        }
        this.clearLobby();
    }

    public boolean beginCountdown(long now, long durationNanos, int pilots) {
        this.countdownTotal = Math.max(0L, durationNanos);
        this.startPilots = pilots;
        return this.tracker.beginCountdown(now, durationNanos);
    }

    void onLobby(RacePayloads.LobbyS2C payload) {
        if (payload.pilots().isEmpty()) {
            if (payload.courseId() == this.lobbyCourse) {
                this.clearLobby();
            }
            return;
        }
        boolean joined = payload.member() && !this.lobbyMember;
        this.lobbyCourse = payload.courseId();
        this.lobbyMember = payload.member();
        this.lobbyPilots = payload.pilots();
        this.lobbyUpdated = Util.getNanos();
        this.lobbyDeadline = this.lobbyUpdated + payload.remainingNanos();
        if (joined) {
            AudioHooks.beep(Beep.GATE);
        }
    }

    void onStart(Minecraft minecraft, RacePayloads.RaceStartS2C payload) {
        this.clearLobby();
        PilotSession session = PilotSession.INSTANCE;
        QuadcopterEntity drone = session.isActive() ? session.drone() : null;
        CourseData course = ClientCourses.get(payload.courseId());
        if (drone == null || course == null || course.gates().isEmpty() || minecraft.level == null) {
            return;
        }
        long now = this.now();
        LapTracker.Phase phase = this.tracker.phase();
        if (phase == LapTracker.Phase.RACING || phase == LapTracker.Phase.COUNTDOWN) {
            this.tracker.abort(now, LapTracker.AbortReason.RESTART);
        }
        this.tracker.setCourse(course.id(), ClientCourses.geometry(course), now);
        this.applyLapTarget();
        this.syncGhosts(minecraft);
        this.beginCountdown(now, payload.countdownNanos(), payload.pilots());
        if (payload.pilots() > 1) {
            RaceHud.message(Component.translatable("propwash.race.msg.heat", payload.pilots()), RaceHud.YELLOW);
        }
        this.dispatch();
    }

    public void selectCourseAt(Minecraft minecraft, Vec3 position) {
        this.selectCourse(minecraft, position, this.now());
        this.dispatch();
    }

    private void applyLapTarget() {
        CourseData course = this.course();
        this.tracker.setLapTarget(CourseLaps.resolve(course == null ? CourseLaps.UNSET : course.laps(), RaceConfig.CONFIG.get().laps));
    }

    private boolean standingStart(RaceConfig config) {
        boolean lobbyOpen = !this.lobbyPilots.isEmpty() && this.lobbyCourse == this.tracker.courseId();
        return config.countdown || this.resetStart || lobbyOpen;
    }

    void onHomeReset(QuadcopterEntity drone) {
        long now = this.now();
        LapTracker.Phase phase = this.tracker.phase();
        if (phase == LapTracker.Phase.RACING || phase == LapTracker.Phase.COUNTDOWN) {
            this.tracker.abort(now, LapTracker.AbortReason.RESET);
        }
        if (this.lobbyMember) {
            this.leaveLobby();
        }
        this.tracker.resetPosition();
        this.lastPosition = null;
        this.wasArmed = false;
        this.resetStart = true;
        this.dispatch();
    }

    public boolean resetStartPending() {
        return this.resetStart;
    }

    public boolean nearStart(Vec3 position) {
        CourseData course = this.course();
        if (course == null || course.gates().isEmpty()) {
            return false;
        }
        GateGeometry start = course.gates().getFirst().geometry();
        return position.distanceToSqr(start.centerX(), start.centerY(), start.centerZ()) <= COUNTDOWN_RANGE * COUNTDOWN_RANGE;
    }

    private void selectCourse(Minecraft minecraft, Vec3 position, long now) {
        if (minecraft.level == null) {
            return;
        }
        String dimension = minecraft.level.dimension().identifier().toString();
        LapTracker.Phase phase = this.tracker.phase();
        int current = this.tracker.courseId();
        if (phase == LapTracker.Phase.RACING || phase == LapTracker.Phase.COUNTDOWN) {
            CourseData data = ClientCourses.get(current);
            if (data == null || !data.dimension().equals(dimension) || data.gates().isEmpty()) {
                this.tracker.clearCourse(now);
            } else {
                this.tracker.setCourse(current, ClientCourses.geometry(data), now);
            }
            this.afterCourseChange(current);
            return;
        }
        CourseData best = null;
        double bestDistance = START_SELECT_RANGE * START_SELECT_RANGE;
        for (CourseData course : ClientCourses.all()) {
            if (!course.dimension().equals(dimension) || course.gates().isEmpty()) {
                continue;
            }
            GateGeometry start = course.gates().getFirst().geometry();
            double distance = position.distanceToSqr(start.centerX(), start.centerY(), start.centerZ());
            if (distance < bestDistance) {
                bestDistance = distance;
                best = course;
            }
        }
        if (best == null) {
            if (current != LapTracker.NO_COURSE && phase != LapTracker.Phase.FINISHED) {
                this.tracker.clearCourse(now);
            }
            this.afterCourseChange(current);
            return;
        }
        this.tracker.setCourse(best.id(), ClientCourses.geometry(best), now);
        this.afterCourseChange(current);
    }

    private void afterCourseChange(int previous) {
        if (this.tracker.courseId() != previous) {
            this.referenceVersion = -1;
        }
    }

    private void dispatch() {
        List<LapTracker.Event> events = this.tracker.drainEvents();
        if (events.isEmpty()) {
            return;
        }
        for (LapTracker.Event event : events) {
            AssistReasons assisted = this.lapAssist;
            boolean newGhost = this.recordGhost(event, assisted);
            this.present(event, assisted, newGhost);
            this.report(event, assisted);
            if (this.eventListener != null) {
                this.eventListener.accept(event);
            }
        }
    }

    private boolean recordGhost(LapTracker.Event event, AssistReasons assisted) {
        boolean newGhost = false;
        switch (event.type()) {
            case START, GO -> {
                this.recorder.onLapStart(this.tracker.courseId(), event.nanos());
                this.lapAssist = this.assistNow;
                this.ghosts.useAssisted(this.lapAssist.any() && savesAssisted());
                if (event.type() == LapTracker.EventType.GO) {
                    this.goAt = event.nanos();
                }
                CourseData course = this.course();
                if (course != null) {
                    this.ghosts.requestRecord(course, false);
                }
            }
            case LAP -> {
                CourseData course = this.course();
                int layout = course == null ? 0 : course.layoutHash();
                GhostTrack track = this.recorder.onLapComplete(event.valueNanos(), event.nanos(), this.frame, layout);
                if (track != null && this.look != null && this.look.build().frame().key().equals(this.frame)) {
                    track = track.withLook(this.look);
                }
                if (track != null && course != null
                        && this.ghosts.offerLap(Minecraft.getInstance(), course, track, assisted.any(), savesAssisted())) {
                    newGhost = true;
                    this.referenceVersion = this.ghosts.version();
                    this.tracker.setReference(this.ghosts.referenceSplits());
                }
                this.lastLapAssist = assisted;
                this.lapAssist = this.assistNow;
                this.ghosts.useAssisted(this.lapAssist.any() && savesAssisted());
            }
            case FINISH, ABORT -> this.recorder.stop();
            default -> {
            }
        }
        return newGhost;
    }

    private void present(LapTracker.Event event, AssistReasons assisted, boolean newGhost) {
        int course = this.tracker.courseId();
        int gates = this.tracker.gates().size();
        switch (event.type()) {
            case COUNTDOWN -> AudioHooks.beep(Beep.COUNTDOWN);
            case GO -> {
                AudioHooks.beep(Beep.GO);
                RaceHud.big(Component.translatable("propwash.race.msg.go"), RaceHud.GREEN, 900_000_000L);
                this.announceStartAssist();
            }
            case START -> {
                AudioHooks.beep(Beep.GATE);
                RaceHud.message(Component.translatable("propwash.race.msg.start", CourseEditScreen.ref(course), this.lapLabel()), RaceHud.GREEN);
                this.announceStartAssist();
            }
            case GATE -> {
                AudioHooks.beep(Beep.GATE);
                CourseElement kind = this.kindOf(event.gate());
                if (!kind.gate()) {
                    Component name = GateMounting.elementName(kind);
                    if (event.hasDelta()) {
                        String key = this.tracker.hasReference() ? "propwash.race.msg.element_ghost" : "propwash.race.msg.element_delta";
                        RaceHud.message(Component.translatable(key, name, event.gate() + 1, gates, RaceFormat.time(event.valueNanos(), HUD_DECIMALS),
                                RaceFormat.delta(event.deltaNanos(), HUD_DECIMALS)), deltaColor(event.deltaNanos()));
                    } else {
                        RaceHud.message(Component.translatable("propwash.race.msg.element", name, event.gate() + 1, gates,
                                RaceFormat.time(event.valueNanos(), HUD_DECIMALS)), RaceHud.WHITE);
                    }
                } else if (event.hasDelta()) {
                    String key = this.tracker.hasReference() ? "propwash.race.msg.gate_ghost" : "propwash.race.msg.gate_delta";
                    RaceHud.message(Component.translatable(key, event.gate() + 1, gates,
                            RaceFormat.time(event.valueNanos(), HUD_DECIMALS), RaceFormat.delta(event.deltaNanos(), HUD_DECIMALS)), deltaColor(event.deltaNanos()));
                } else {
                    RaceHud.message(Component.translatable("propwash.race.msg.gate", event.gate() + 1, gates, RaceFormat.time(event.valueNanos(), HUD_DECIMALS)),
                            RaceHud.WHITE);
                }
            }
            case LAP -> {
                AudioHooks.beep(Beep.LAP);
                Component text = event.hasDelta()
                        ? Component.translatable("propwash.race.msg.lap_delta", event.lap(), RaceFormat.time(event.valueNanos(), HUD_DECIMALS),
                        RaceFormat.delta(event.deltaNanos(), HUD_DECIMALS))
                        : Component.translatable("propwash.race.msg.lap", event.lap(), RaceFormat.time(event.valueNanos(), HUD_DECIMALS));
                RaceHud.message(text, event.hasDelta() ? deltaColor(event.deltaNanos()) : RaceHud.WHITE, 4_000_000_000L);
                if (newGhost) {
                    String key = assisted.any() ? "propwash.race.msg.personal_best_assisted" : "propwash.race.msg.personal_best";
                    RaceHud.message(Component.translatable(key), RaceHud.GOLD, 4_000_000_000L);
                } else if (event.best() && event.hasDelta()) {
                    RaceHud.message(Component.translatable("propwash.race.msg.best"), RaceHud.GOLD, 4_000_000_000L);
                }
                if (assisted.any()) {
                    String key = savesAssisted() ? "propwash.race.msg.assisted_saved" : "propwash.race.msg.assisted_because";
                    RaceHud.message(Component.translatable(key, AssistText.reasons(assisted)), RaceHud.GRAY, 4_000_000_000L);
                }
            }
            case FINISH -> {
                AudioHooks.beep(Beep.FINISH);
                String total = RaceFormat.time(event.valueNanos());
                String best = RaceFormat.time(this.tracker.bestLapNanos());
                RaceHud.big(Component.translatable("propwash.race.msg.finish_big"), RaceHud.GOLD, 2_500_000_000L);
                RaceHud.message(Component.translatable("propwash.race.msg.finish", RaceFormat.time(event.valueNanos(), HUD_DECIMALS),
                        RaceFormat.time(this.tracker.bestLapNanos(), HUD_DECIMALS)), RaceHud.GOLD, 6_000_000_000L);
                Minecraft minecraft = Minecraft.getInstance();
                if (minecraft.player != null) {
                    minecraft.player.sendSystemMessage(Component.translatable("propwash.race.msg.summary", CourseEditScreen.ref(course), event.lap(), total, best)
                            .withStyle(ChatFormatting.GOLD));
                }
            }
            case MISSED -> {
                CourseElement kind = this.kindOf(event.gate());
                RaceHud.message(kind.gate() ? Component.translatable("propwash.race.msg.missed", event.gate() + 1)
                        : Component.translatable("propwash.race.msg.missed_element", GateMounting.elementName(kind), event.gate() + 1), RaceHud.RED);
            }
            case WRONG_SIDE -> {
                CourseElement kind = this.kindOf(event.gate());
                RaceHud.message(Component.translatable("propwash.race.msg.wrong_side." + kind.key(), event.gate() + 1,
                        GateMounting.variantName(kind, this.variantOf(event.gate()))), RaceHud.RED);
            }
            case WRONG_WAY -> RaceHud.message(Component.translatable("propwash.race.msg.wrong_way", event.gate() + 1), RaceHud.RED);
            case FALSE_START -> {
                AudioHooks.beep(Beep.CRASH);
                RaceHud.big(Component.translatable("propwash.race.msg.false_start"), RaceHud.RED, 1_500_000_000L);
                RaceHud.message(Component.translatable("propwash.race.msg.false_start_penalty", RaceFormat.time(event.valueNanos(), 0)), RaceHud.RED,
                        4_000_000_000L);
            }
            case ABORT -> {
                LapTracker.AbortReason reason = event.reason();
                if (reason != null && reason != LapTracker.AbortReason.SESSION_ENDED) {
                    RaceHud.message(Component.translatable("propwash.race.msg.abort." + reason.name().toLowerCase(Locale.ROOT)),
                            RaceHud.GRAY);
                }
            }
        }
    }

    private void announceStartAssist() {
        if (this.lapAssist.any()) {
            String key = savesAssisted() ? "propwash.race.msg.assist_start_saved" : "propwash.race.msg.assist_start";
            RaceHud.message(Component.translatable(key, AssistText.reasons(this.lapAssist)), RaceHud.YELLOW, 5_000_000_000L);
        }
    }

    public static boolean savesAssisted() {
        return ClientWorldRules.INSTANCE.on(WorldRuleSpecs.SAVE_ASSISTED_LAPS);
    }

    private CourseElement kindOf(int gate) {
        List<GateGeometry> gates = this.tracker.gates();
        return gate >= 0 && gate < gates.size() ? gates.get(gate).kind() : CourseElement.GATE;
    }

    private int variantOf(int gate) {
        List<GateGeometry> gates = this.tracker.gates();
        return gate >= 0 && gate < gates.size() ? gates.get(gate).variant() : 0;
    }

    private Component lapLabel() {
        int laps = this.tracker.lapTarget();
        return laps <= 0 ? Component.translatable("propwash.race.msg.endless") : Component.translatable("propwash.race.msg.laps", laps);
    }

    private static int deltaColor(long delta) {
        return delta < 0L ? RaceHud.GREEN : delta > 0L ? RaceHud.RED : RaceHud.WHITE;
    }

    private void report(LapTracker.Event event, AssistReasons assisted) {
        if (!RaceConfig.CONFIG.get().reportResults || !ClientNet.canSend(RacePayloads.RaceReportC2S.TYPE)) {
            return;
        }
        int course = this.tracker.courseId();
        int flags = RacePayloads.RaceReportC2S.assistFlags(assisted.mask());
        long penalty = event.lap() == 1 ? this.tracker.penaltyNanos() : 0L;
        RacePayloads.RaceReportC2S payload = switch (event.type()) {
            case START, GO -> new RacePayloads.RaceReportC2S(RacePayloads.ReportKind.START, course, 0, 0L, 0L);
            case LAP -> new RacePayloads.RaceReportC2S(RacePayloads.ReportKind.LAP, course, event.lap(), event.valueNanos(), 0L, flags, penalty,
                    assisted.details());
            case FINISH -> new RacePayloads.RaceReportC2S(RacePayloads.ReportKind.FINISH, course, event.lap(), 0L, event.valueNanos());
            case ABORT -> new RacePayloads.RaceReportC2S(RacePayloads.ReportKind.ABORT, course, 0, 0L, 0L);
            default -> null;
        };
        if (payload != null && course != LapTracker.NO_COURSE) {
            ClientNet.sendToServer(payload);
        }
    }
}
