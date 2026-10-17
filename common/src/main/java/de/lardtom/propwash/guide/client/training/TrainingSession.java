package de.lardtom.propwash.guide.client.training;

import de.lardtom.propwash.audio.client.AudioHooks;
import de.lardtom.propwash.audio.client.Beep;
import de.lardtom.propwash.drone.QuadcopterEntity;
import de.lardtom.propwash.drone.client.PilotSession;
import de.lardtom.propwash.guide.training.Chapter;
import de.lardtom.propwash.guide.training.Course;
import de.lardtom.propwash.guide.training.Curriculum;
import de.lardtom.propwash.guide.training.Goal;
import de.lardtom.propwash.guide.training.Lesson;
import de.lardtom.propwash.guide.training.LessonRun;
import de.lardtom.propwash.guide.training.Placement;
import de.lardtom.propwash.guide.training.StarRule;
import de.lardtom.propwash.guide.training.TrainingProgress;
import de.lardtom.propwash.guide.training.TrainingSample;
import de.lardtom.propwash.guide.training.TrainingScale;
import de.lardtom.propwash.race.AssistReasons;
import de.lardtom.propwash.race.RaceFormat;
import de.lardtom.propwash.race.client.AssistText;
import de.lardtom.propwash.sim.DroneSim;
import de.lardtom.propwash.sim.Telemetry;
import de.lardtom.propwash.trick.TrickType;
import de.lardtom.propwash.ui.client.theme.Palette;
import de.lardtom.propwash.ui.client.theme.ThemedToast;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.util.Util;
import org.joml.Quaterniond;
import org.joml.Vector3d;
import org.jspecify.annotations.Nullable;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;

public final class TrainingSession {
    public static final TrainingSession INSTANCE = new TrainingSession();
    public static final Object TOAST_TOKEN = new Object();
    public static final double MOVED = 3.0;
    public static final double RESTING_SPEED = 0.6;
    public static final long RETRY_NANOS = 1_500_000_000L;
    public static final long PLACE_RETRY_NANOS = 2_000_000_000L;
    public static final long MESSAGE_NANOS = 2_800_000_000L;
    public static final long BANNER_NANOS = 7_000_000_000L;
    public static final int MAX_MESSAGES = 3;
    public static final int GREEN = 0xFF55FF77;
    public static final int RED = 0xFFFF5555;
    public static final int YELLOW = 0xFFFFE066;
    public static final int WHITE = 0xFFFFFFFF;

    public enum Phase {
        IDLE,
        WAITING,
        AIRBORNE,
        BLOCKED,
        ACTIVE
    }

    public record Message(Component text, int color, long until) {
    }

    public record Banner(boolean success, int stars, double time, double metric, boolean distance, boolean assisted, boolean bestTime,
                         double previousTime, boolean moreStars, LessonRun.@Nullable Failure failure, long since) {
    }

    private final Deque<Message> messages = new ArrayDeque<>();
    private final TrainingSample sample = new TrainingSample();
    private AssistReasons assistNow = AssistReasons.NONE;
    private AssistReasons runAssist = AssistReasons.NONE;
    private final Vector3d velocity = new Vector3d();
    private final Quaterniond attitude = new Quaterniond();
    private @Nullable Lesson lesson;
    private Phase phase = Phase.IDLE;
    private @Nullable Course course;
    private @Nullable LessonRun run;
    private Placement.@Nullable Result placement;
    private TrainingScale scale = TrainingScale.FULL;
    private @Nullable DroneSim tracked;
    private @Nullable Banner banner;
    private long nextPlacement;
    private double placedX = Double.NaN;
    private double placedZ = Double.NaN;
    private long finishedAt;
    private long frames;
    private int runs;

    private TrainingSession() {
    }

    public @Nullable Lesson lesson() {
        return this.lesson;
    }

    public Phase phase() {
        return this.phase;
    }

    public @Nullable Course course() {
        return this.course;
    }

    public @Nullable LessonRun run() {
        return this.run;
    }

    public Placement.@Nullable Result placement() {
        return this.placement;
    }

    public TrainingScale scale() {
        return this.scale;
    }

    public AssistReasons runAssist() {
        return this.runAssist;
    }

    public @Nullable Banner banner() {
        Banner shown = this.banner;
        if (shown != null && Util.getNanos() - shown.since() > BANNER_NANOS) {
            return null;
        }
        return shown;
    }

    public List<Message> messages() {
        long now = Util.getNanos();
        this.messages.removeIf(message -> message.until() <= now);
        return List.copyOf(this.messages);
    }

    public long frames() {
        return this.frames;
    }

    public int runs() {
        return this.runs;
    }

    public boolean active() {
        return this.lesson != null;
    }

    public TrainingSample sample() {
        return this.sample;
    }

    public void select(Lesson next) {
        if (this.run != null && this.run.state() == LessonRun.State.RUNNING) {
            this.run.end();
        }
        this.lesson = next;
        this.clearCourse();
        this.banner = null;
        this.messages.clear();
        this.phase = Phase.WAITING;
        Minecraft minecraft = Minecraft.getInstance();
        ThemedToast.show(minecraft.gui.toastManager(), TOAST_TOKEN, Component.translatable("propwash.guide.training.toast.selected"),
                Component.translatable("propwash.guide.training.hud.lesson", next.code(), Component.translatable(next.titleKey())),
                Palette.ACCENT, 3000L);
    }

    public void stop() {
        if (this.run != null && this.run.state() == LessonRun.State.RUNNING) {
            this.run.end();
        }
        this.lesson = null;
        this.clearCourse();
        this.banner = null;
        this.messages.clear();
        this.phase = Phase.IDLE;
    }

    public void onDisconnect(Minecraft minecraft) {
        this.stop();
        this.tracked = null;
    }

    private void clearCourse() {
        this.course = null;
        this.run = null;
        this.placement = null;
        this.nextPlacement = 0L;
        this.placedX = Double.NaN;
        this.placedZ = Double.NaN;
    }

    public void onFrame(Minecraft minecraft) {
        this.frames++;
        Lesson current = this.lesson;
        if (current == null) {
            this.phase = Phase.IDLE;
            return;
        }
        if (minecraft.level == null || minecraft.player == null) {
            return;
        }
        PilotSession pilot = PilotSession.INSTANCE;
        DroneSim sim = pilot.sim();
        QuadcopterEntity drone = pilot.drone();
        if (sim == null || drone == null) {
            if (this.run != null && this.run.state() == LessonRun.State.RUNNING) {
                this.run.end();
            }
            this.clearCourse();
            this.tracked = null;
            this.phase = Phase.WAITING;
            return;
        }
        if (sim != this.tracked) {
            this.tracked = sim;
            this.clearCourse();
        }
        if (minecraft.isPaused()) {
            return;
        }
        TrainingScale droneScale = TrainingScale.of(drone.build().frame().wheelbaseMm);
        if (this.course != null && droneScale != this.scale) {
            this.clearCourse();
        }
        this.scale = droneScale;
        this.fill(drone, sim);
        long now = Util.getNanos();
        boolean resting = this.resting();
        if (this.course == null || this.run == null) {
            if (!resting) {
                if (this.phase != Phase.BLOCKED) {
                    this.phase = Phase.AIRBORNE;
                }
                return;
            }
            boolean moved = !Double.isFinite(this.placedX) || Math.hypot(drone.getX() - this.placedX, drone.getZ() - this.placedZ) > 0.5;
            if (now < this.nextPlacement && !moved) {
                return;
            }
            this.place(minecraft, current, drone, now);
            return;
        }
        LessonRun active = this.run;
        LessonRun.State before = active.state();
        active.update(this.sample);
        if (before == LessonRun.State.READY && active.state() != LessonRun.State.READY) {
            this.runAssist = this.assistNow;
        } else if (active.state() == LessonRun.State.RUNNING) {
            this.runAssist = this.runAssist.plus(this.assistNow);
        }
        for (LessonRun.Event event : active.drainEvents()) {
            this.handle(minecraft, current, active, event, now);
        }
        double fromAnchor = Math.hypot(drone.getX() - this.course.frame().x(), drone.getZ() - this.course.frame().z());
        if (active.finished()) {
            if (resting && now - this.finishedAt > RETRY_NANOS) {
                if (fromAnchor > MOVED) {
                    this.clearCourse();
                } else {
                    this.newRun(current);
                }
            }
        } else if (active.state() == LessonRun.State.READY && resting && fromAnchor > MOVED) {
            this.clearCourse();
        }
    }

    private boolean resting() {
        return !this.sample.armed && this.sample.nearGround() && this.sample.speed() < RESTING_SPEED;
    }

    private void place(Minecraft minecraft, Lesson current, QuadcopterEntity drone, long now) {
        Placement.Result result = Placement.place(current, drone.getX(), drone.getY(), drone.getZ(), this.sample.yaw(), this.scale,
                new LevelTerrain(minecraft.level));
        this.placement = result;
        this.placedX = drone.getX();
        this.placedZ = drone.getZ();
        this.nextPlacement = now + PLACE_RETRY_NANOS;
        if (!result.ok()) {
            this.phase = Phase.BLOCKED;
            return;
        }
        this.course = result.course();
        this.phase = Phase.ACTIVE;
        this.newRun(current);
    }

    private void newRun(Lesson current) {
        Course placed = this.course;
        if (placed == null) {
            return;
        }
        TrainingProgress progress = TrainingStore.progress();
        this.run = new LessonRun(placed, progress.referenceSplits(current.id(), this.scale));
        this.runAssist = AssistReasons.NONE;
        this.runs++;
    }

    private void fill(QuadcopterEntity drone, DroneSim sim) {
        Telemetry t = sim.telemetry();
        TrainingSample s = this.sample;
        sim.getVelocity(this.velocity);
        sim.getAttitude(this.attitude);
        s.time = t.time;
        s.x = drone.getX();
        s.footY = drone.getY();
        s.y = drone.getY() + drone.getBbHeight() * 0.5;
        s.z = drone.getZ();
        s.vx = this.velocity.x;
        s.vy = this.velocity.y;
        s.vz = this.velocity.z;
        s.qx = this.attitude.x;
        s.qy = this.attitude.y;
        s.qz = this.attitude.z;
        s.qw = this.attitude.w;
        s.armed = t.armed;
        s.grounded = t.grounded;
        s.heightAboveGround = Double.isFinite(t.heightAboveGround) ? Math.max(0.0, t.heightAboveGround) : Double.POSITIVE_INFINITY;
        s.crashCount = t.crashCount;
        s.impactCount = t.impactCount;
        s.throttle = t.throttleOut;
        s.radius = drone.getBbWidth() * 0.5;
        this.assistNow = AssistText.evaluate(PilotSession.INSTANCE, drone);
        s.assisted = this.assistNow.any();
    }

    private void message(Component text, int color) {
        this.messages.addFirst(new Message(text, color, Util.getNanos() + MESSAGE_NANOS));
        while (this.messages.size() > MAX_MESSAGES) {
            this.messages.removeLast();
        }
    }

    public static String time(double seconds) {
        return RaceFormat.time(Math.round(Math.max(0.0, seconds) * 1.0E9), 2);
    }

    public static String delta(double seconds) {
        return RaceFormat.delta(Math.round(seconds * 1.0E9), 2);
    }

    private static Component timed(String key, int number, double value, double delta) {
        MutableComponent text = Component.translatable(key, number, time(value));
        if (Double.isFinite(delta)) {
            text.append(" (" + delta(delta) + ")");
        }
        return text;
    }

    private void handle(Minecraft minecraft, Lesson current, LessonRun active, LessonRun.Event event, long now) {
        switch (event.type()) {
            case STARTED -> {
                this.banner = null;
                this.messages.clear();
                TrainingStore.progress().attempt(current.id());
                TrainingStore.save();
            }
            case RING -> {
                AudioHooks.beep(Beep.GATE);
                int color = !event.hasDelta() ? WHITE : event.delta() <= 0.0 ? GREEN : RED;
                this.message(timed("propwash.guide.training.event.ring", event.index() + 1, event.value(), event.delta()), color);
            }
            case MISSED -> this.message(Component.translatable("propwash.guide.training.event.missed", event.index() + 1), RED);
            case WRONG_WAY -> this.message(Component.translatable("propwash.guide.training.event.wrong_way", event.index() + 1), RED);
            case TOUCH -> this.message(Component.translatable("propwash.guide.training.event.touch", event.index() + 1), YELLOW);
            case MARKER -> {
                AudioHooks.beep(Beep.GATE);
                this.message(Component.translatable("propwash.guide.training.event.marker", event.index() + 1), GREEN);
            }
            case LAP -> {
                AudioHooks.beep(Beep.LAP);
                int color = !event.hasDelta() ? WHITE : event.delta() <= 0.0 ? GREEN : RED;
                this.message(timed("propwash.guide.training.event.lap", event.index(), event.value(), event.delta()), color);
            }
            case TRICK -> {
                TrickType[] types = TrickType.values();
                if (event.index() >= 0 && event.index() < types.length) {
                    AudioHooks.beep(Beep.GATE);
                    this.message(Component.translatable("propwash.guide.training.event.trick",
                            Component.translatable(types[event.index()].translationKey())), GREEN);
                }
            }
            case TRICK_OUTSIDE -> {
                TrickType[] types = TrickType.values();
                if (event.index() >= 0 && event.index() < types.length) {
                    this.message(Component.translatable("propwash.guide.training.event.trick.outside",
                            Component.translatable(types[event.index()].translationKey())), YELLOW);
                }
            }
            case GOAL -> {
                if (event.index() + 1 < current.goals().size()) {
                    AudioHooks.beep(Beep.LAP);
                }
            }
            case SUCCESS -> this.succeed(minecraft, current, active, now);
            case FAILED -> {
                this.finishedAt = now;
                LessonRun.Failure failure = active.failure();
                if (failure != LessonRun.Failure.CRASH) {
                    AudioHooks.beep(Beep.DISARM);
                }
                this.banner = new Banner(false, 0, active.elapsed(), Double.NaN, false, active.assisted(), false, -1.0, false, failure, now);
                TrainingStore.save();
            }
        }
    }

    private void succeed(Minecraft minecraft, Lesson current, LessonRun active, long now) {
        this.finishedAt = now;
        LessonRun.Result result = active.result();
        if (result == null) {
            return;
        }
        AudioHooks.beep(Beep.FINISH);
        TrainingProgress progress = TrainingStore.progress();
        TrainingProgress.Outcome outcome = progress.complete(current, this.scale, result);
        TrainingStore.save();
        boolean distance = current.stars().metric() == StarRule.Metric.PAD_DISTANCE;
        this.banner = new Banner(true, result.stars(), result.time(), result.metric(), distance, result.assisted(), outcome.bestTime(),
                outcome.previousTime(), outcome.moreStars() && outcome.previousStars() > 0, null, now);
        if (outcome.chapterDone()) {
            Chapter chapter = Curriculum.chapter(current.chapter());
            boolean all = allPassed(progress);
            Component title = Component.translatable(all ? "propwash.guide.training.toast.done" : "propwash.guide.training.toast.chapter");
            Component text = chapter == null ? null : Component.translatable("propwash.guide.training.toast.chapter.text",
                    Component.translatable(chapter.titleKey()));
            ThemedToast.show(minecraft.gui.toastManager(), TOAST_TOKEN, title, all ? null : text, Palette.SUCCESS, 5000L);
        }
    }

    private static boolean allPassed(TrainingProgress progress) {
        for (Lesson lesson : Curriculum.lessons()) {
            if (progress.stars(lesson.id()) <= 0) {
                return false;
            }
        }
        return true;
    }

    public @Nullable Goal goal() {
        LessonRun active = this.run;
        return active == null ? null : active.goal();
    }

    public void clearMessages() {
        this.messages.clear();
    }
}
