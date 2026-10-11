package de.lardtom.propwash.guide.training;

import de.lardtom.propwash.race.GateGeometry;
import de.lardtom.propwash.race.LapTracker;
import de.lardtom.propwash.trick.Trick;
import de.lardtom.propwash.trick.TrickSample;
import de.lardtom.propwash.trick.TrickSession;
import de.lardtom.propwash.trick.TrickType;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

public final class LessonRun {
    public static final double MAX_SEGMENT = 24.0;
    public static final double MAX_FRAME_SECONDS = 0.25;
    public static final double LAND_HEIGHT = 1.0;
    public static final long NANOS = 1_000_000_000L;
    public static final double ZONE_GRACE = 0.25;
    public static final double ZONE_MEMORY = 30.0;

    public enum State {
        READY,
        RUNNING,
        SUCCESS,
        FAILED
    }

    public enum Failure {
        CRASH,
        TIMEOUT,
        LEFT_AREA,
        DISARMED,
        TOO_EARLY,
        TOUCHED,
        OUTSIDE_PAD,
        ENDED;

        public String key() {
            return this.name().toLowerCase(Locale.ROOT);
        }
    }

    public enum EventType {
        STARTED,
        RING,
        MISSED,
        WRONG_WAY,
        TOUCH,
        MARKER,
        LAP,
        TRICK,
        TRICK_OUTSIDE,
        GOAL,
        SUCCESS,
        FAILED
    }

    public record Event(EventType type, int index, double value, double delta) {
        public boolean hasDelta() {
            return Double.isFinite(this.delta);
        }
    }

    public record Result(int stars, double metric, double time, boolean assisted, double[] splits) {
        public Result {
            splits = splits.clone();
        }

        @Override
        public double[] splits() {
            return this.splits.clone();
        }
    }

    private final Course course;
    private final Lesson lesson;
    private final double[] reference;
    private final List<Event> events = new ArrayList<>();
    private final TrickSession tricks = new TrickSession();
    private final TrickSample trickSample = new TrickSample();
    private final LapTracker laps = new LapTracker();
    private final List<Double> splits = new ArrayList<>();
    private final List<Double> lapTimes = new ArrayList<>();
    private final List<double[]> zoneVisits = new ArrayList<>();
    private State state = State.READY;
    private @Nullable Failure failure;
    private @Nullable Result result;
    private int goal;
    private double start = Double.NaN;
    private double now;
    private double end = Double.NaN;
    private boolean assisted;
    private boolean hasLast;
    private double lastX;
    private double lastY;
    private double lastZ;
    private boolean wasArmed;
    private int lastCrash;
    private double armedTime;
    private double zoneTime;
    private int markerStep;
    private double holdTime;
    private double headingError = Double.NaN;
    private int nextRing;
    private int missedAt = -1;
    private int touchedRing = -1;
    private double padDistance = Double.NaN;
    private int trickCount;
    private int comboNow;
    private int comboBest;
    private @Nullable TrickType lastTrick;
    private int lapsDone;
    private boolean lapsStarted;
    private boolean inZone;

    public LessonRun(Course course, double @Nullable [] referenceSplits) {
        this.course = course;
        this.lesson = course.lesson();
        this.reference = referenceSplits == null ? new double[0] : referenceSplits.clone();
        this.enterGoal();
    }

    public Course course() {
        return this.course;
    }

    public Lesson lesson() {
        return this.lesson;
    }

    public State state() {
        return this.state;
    }

    public @Nullable Failure failure() {
        return this.failure;
    }

    public @Nullable Result result() {
        return this.result;
    }

    public boolean finished() {
        return this.state == State.SUCCESS || this.state == State.FAILED;
    }

    public int goalIndex() {
        return this.goal;
    }

    public @Nullable Goal goal() {
        return this.goal < this.lesson.goals().size() ? this.lesson.goals().get(this.goal) : null;
    }

    public double elapsed() {
        if (Double.isNaN(this.start)) {
            return 0.0;
        }
        double until = Double.isNaN(this.end) ? this.now : this.end;
        return Math.max(0.0, until - this.start);
    }

    public double timeLimit() {
        return this.lesson.timeLimit() * this.course.scale().time();
    }

    public boolean assisted() {
        return this.assisted;
    }

    public double armedTime() {
        return this.armedTime;
    }

    public double zoneTime() {
        return this.zoneTime;
    }

    public boolean inZone() {
        return this.inZone;
    }

    public int markerStep() {
        return this.markerStep;
    }

    public double holdTime() {
        return this.holdTime;
    }

    public double headingError() {
        return this.headingError;
    }

    public int nextRing() {
        return this.nextRing;
    }

    public double padDistance() {
        return this.padDistance;
    }

    public int trickCount() {
        return this.trickCount;
    }

    public int comboBest() {
        return this.comboBest;
    }

    public int comboNow() {
        return this.comboNow;
    }

    public @Nullable TrickType lastTrick() {
        return this.lastTrick;
    }

    public int lapsDone() {
        return this.lapsDone;
    }

    public boolean lapsStarted() {
        return this.lapsStarted;
    }

    public int lap() {
        return this.laps.lap();
    }

    public double lapTime() {
        return this.laps.currentLapNanos(this.nanos(this.now)) / (double) NANOS;
    }

    public List<Double> splits() {
        return List.copyOf(this.splits);
    }

    public List<Double> lapTimes() {
        return List.copyOf(this.lapTimes);
    }

    public TrickSession trickSession() {
        return this.tricks;
    }

    public List<Event> drainEvents() {
        if (this.events.isEmpty()) {
            return List.of();
        }
        List<Event> drained = List.copyOf(this.events);
        this.events.clear();
        return drained;
    }

    public List<GateGeometry> upcomingRings() {
        Goal current = this.goal();
        if (current instanceof Goal.Rings rings) {
            return this.course.rings(rings.from() + this.nextRing, rings.to());
        }
        if (current instanceof Goal.Laps loop) {
            List<GateGeometry> gates = this.course.rings(loop.from(), loop.to());
            if (gates.isEmpty()) {
                return List.of();
            }
            int next = this.laps.racing() ? this.laps.nextGate() : 0;
            List<GateGeometry> ordered = new ArrayList<>();
            for (int i = 0; i < gates.size(); i++) {
                ordered.add(gates.get((next + i) % gates.size()));
            }
            return ordered;
        }
        return List.of();
    }

    public int activeRing() {
        Goal current = this.goal();
        if (current instanceof Goal.Rings rings) {
            return rings.from() + this.nextRing;
        }
        if (current instanceof Goal.Laps loop) {
            return loop.from() + (this.laps.racing() ? this.laps.nextGate() : 0);
        }
        return -1;
    }

    public boolean ringDone(int index) {
        for (int g = 0; g < this.lesson.goals().size(); g++) {
            Goal goal = this.lesson.goals().get(g);
            if (goal instanceof Goal.Rings rings && index >= rings.from() && index < rings.to()) {
                return g < this.goal || (g == this.goal && index < rings.from() + this.nextRing) || this.state == State.SUCCESS;
            }
            if (goal instanceof Goal.Laps loop && index >= loop.from() && index < loop.to()) {
                return g < this.goal || this.state == State.SUCCESS;
            }
        }
        return false;
    }

    public void end() {
        if (this.state == State.RUNNING) {
            this.fail(Failure.ENDED);
        }
    }

    private long nanos(double seconds) {
        return Math.round(seconds * NANOS);
    }

    public void update(TrainingSample s) {
        double dt = this.hasLast ? Math.clamp(s.time - this.now, 0.0, MAX_FRAME_SECONDS) : 0.0;
        this.now = s.time;
        s.fill(this.trickSample);
        this.tricks.update(this.trickSample);
        switch (this.state) {
            case READY -> {
                if (s.armed && !this.wasArmed && this.hasLast) {
                    this.begin(s);
                }
            }
            case RUNNING -> this.running(s, dt);
            default -> {
            }
        }
        this.wasArmed = s.armed;
        this.hasLast = true;
        this.lastX = s.x;
        this.lastY = s.y;
        this.lastZ = s.z;
    }

    private void begin(TrainingSample s) {
        this.state = State.RUNNING;
        this.start = s.time;
        this.lastCrash = s.crashCount;
        this.assisted = s.assisted;
        this.events.add(new Event(EventType.STARTED, 0, 0.0, Double.NaN));
        this.enterGoal();
    }

    private void running(TrainingSample s, double dt) {
        if (s.assisted) {
            this.assisted = true;
        }
        if (s.crashCount > this.lastCrash) {
            this.fail(Failure.CRASH);
            return;
        }
        if (this.elapsed() > this.timeLimit()) {
            this.fail(Failure.TIMEOUT);
            return;
        }
        if (this.course.outside(s.x, s.y, s.z)) {
            this.fail(Failure.LEFT_AREA);
            return;
        }
        Goal current = this.goal();
        if (current == null) {
            return;
        }
        boolean disarmed = this.wasArmed && !s.armed;
        if (disarmed && !current.acceptsDisarm()) {
            this.fail(Failure.DISARMED);
            return;
        }
        boolean done = switch (current) {
            case Goal.Arm arm -> this.arm(arm, s, dt, disarmed);
            case Goal.Hover hover -> this.hover(hover, s, dt);
            case Goal.Heading heading -> this.heading(heading, s, dt);
            case Goal.Rings rings -> this.rings(rings, s);
            case Goal.Laps loop -> this.laps(loop, s);
            case Goal.Land land -> this.land(land, s, disarmed);
            case Goal.Tricks trick -> this.tricks(trick, s);
            case Goal.Combo combo -> this.combo(combo, s);
        };
        if (this.state != State.RUNNING) {
            return;
        }
        if (done) {
            this.events.add(new Event(EventType.GOAL, this.goal, this.elapsed(), Double.NaN));
            this.goal++;
            if (this.goal >= this.lesson.goals().size()) {
                this.succeed();
            } else {
                this.enterGoal();
            }
        }
    }

    private void enterGoal() {
        this.armedTime = 0.0;
        this.zoneTime = 0.0;
        this.markerStep = 0;
        this.holdTime = 0.0;
        this.headingError = Double.NaN;
        this.nextRing = 0;
        this.missedAt = -1;
        this.touchedRing = -1;
        this.trickCount = 0;
        this.comboNow = 0;
        this.comboBest = 0;
        this.inZone = false;
        this.zoneVisits.clear();
        this.lapsDone = 0;
        this.lapsStarted = false;
        Goal current = this.goal();
        if (current instanceof Goal.Laps loop) {
            this.laps.setLapTarget(loop.laps());
            this.laps.setTimeoutNanos(this.nanos(Math.max(1.0, this.timeLimit())));
            this.laps.setCourse(1 + this.goal, this.course.rings(loop.from(), loop.to()), this.nanos(this.now));
            this.laps.setReference(new long[0]);
            this.laps.resetPosition();
        }
    }

    private boolean arm(Goal.Arm arm, TrainingSample s, double dt, boolean disarmed) {
        if (s.armed) {
            this.armedTime += dt;
            return false;
        }
        if (!disarmed) {
            return false;
        }
        if (this.armedTime + 1.0E-9 < arm.seconds()) {
            this.fail(Failure.TOO_EARLY);
            return false;
        }
        if (!s.nearGround()) {
            this.fail(Failure.DISARMED);
            return false;
        }
        return true;
    }

    private boolean hover(Goal.Hover hover, TrainingSample s, double dt) {
        this.inZone = this.course.inZone(hover.zone(), s.x, s.y, s.z);
        if (this.inZone && !s.nearGround()) {
            this.zoneTime += dt;
        }
        return this.zoneTime + 1.0E-9 >= hover.seconds();
    }

    private boolean heading(Goal.Heading heading, TrainingSample s, double dt) {
        this.inZone = this.course.inZone(heading.zone(), s.x, s.y, s.z);
        if (this.markerStep >= heading.markers().size()) {
            return true;
        }
        int marker = heading.markers().get(this.markerStep);
        double target = TrainingFrame.yawOf(this.course.markerX(marker) - s.x, this.course.markerZ(marker) - s.z);
        this.headingError = TrainingFrame.delta(s.yaw(), target);
        if (this.inZone && Math.abs(this.headingError) <= heading.tolerance() && !s.nearGround()) {
            this.holdTime += dt;
        } else {
            this.holdTime = 0.0;
        }
        if (this.holdTime + 1.0E-9 >= heading.hold()) {
            this.events.add(new Event(EventType.MARKER, this.markerStep, this.elapsed(), Double.NaN));
            this.markerStep++;
            this.holdTime = 0.0;
        }
        return this.markerStep >= heading.markers().size();
    }

    private boolean segmentValid(TrainingSample s) {
        if (!this.hasLast) {
            return false;
        }
        double dx = s.x - this.lastX;
        double dy = s.y - this.lastY;
        double dz = s.z - this.lastZ;
        return dx * dx + dy * dy + dz * dz <= MAX_SEGMENT * MAX_SEGMENT;
    }

    private boolean rings(Goal.Rings rings, TrainingSample s) {
        List<GateGeometry> gates = this.course.rings();
        if (this.segmentValid(s)) {
            double bestT = Double.POSITIVE_INFINITY;
            int bestRing = -1;
            for (int i = rings.from() + this.nextRing; i < rings.to(); i++) {
                double t = gates.get(i).crossing(this.lastX, this.lastY, this.lastZ, s.x, s.y, s.z, true);
                if (t >= 0.0 && t < bestT) {
                    bestT = t;
                    bestRing = i;
                }
            }
            int expected = rings.from() + this.nextRing;
            if (bestRing == expected) {
                double time = this.elapsed();
                this.splits.add(time);
                int index = this.splits.size() - 1;
                double delta = index < this.reference.length && this.reference[index] > 0.0 ? time - this.reference[index] : Double.NaN;
                this.events.add(new Event(EventType.RING, bestRing, time, delta));
                this.nextRing++;
                this.missedAt = -1;
                this.touchedRing = -1;
            } else if (bestRing > expected && this.missedAt != expected) {
                this.missedAt = expected;
                this.events.add(new Event(EventType.MISSED, expected, this.elapsed(), Double.NaN));
            } else if (expected < rings.to()
                    && gates.get(expected).crossing(this.lastX, this.lastY, this.lastZ, s.x, s.y, s.z, false) >= 0.0) {
                this.events.add(new Event(EventType.WRONG_WAY, expected, this.elapsed(), Double.NaN));
            }
        }
        int expected = rings.from() + this.nextRing;
        if (expected < rings.to()) {
            GateGeometry ring = gates.get(expected);
            if (Course.touches(ring, s.x, s.y, s.z, s.radius)) {
                if (this.touchedRing != expected) {
                    this.touchedRing = expected;
                    this.events.add(new Event(EventType.TOUCH, expected, this.elapsed(), Double.NaN));
                    if (rings.strict()) {
                        this.fail(Failure.TOUCHED);
                        return false;
                    }
                }
            }
        }
        return rings.from() + this.nextRing >= rings.to();
    }

    private boolean laps(Goal.Laps loop, TrainingSample s) {
        long nowNanos = this.nanos(s.time);
        if (!this.segmentValid(s)) {
            this.laps.resetPosition();
        }
        this.laps.update(nowNanos, s.x, s.y, s.z);
        boolean finished = false;
        for (LapTracker.Event event : this.laps.drainEvents()) {
            double time = this.elapsed();
            switch (event.type()) {
                case START -> {
                    this.lapsStarted = true;
                    this.events.add(new Event(EventType.RING, loop.from(), time, Double.NaN));
                }
                case GATE -> {
                    double split = event.valueNanos() / (double) NANOS;
                    double delta = event.hasDelta() ? event.deltaNanos() / (double) NANOS : Double.NaN;
                    this.events.add(new Event(EventType.RING, loop.from() + event.gate(), split, delta));
                }
                case LAP -> {
                    this.lapsDone++;
                    double lapTime = event.valueNanos() / (double) NANOS;
                    double delta = event.hasDelta() ? event.deltaNanos() / (double) NANOS : Double.NaN;
                    this.lapTimes.add(lapTime);
                    this.events.add(new Event(EventType.LAP, this.lapsDone, lapTime, delta));
                }
                case FINISH -> finished = true;
                case MISSED -> this.events.add(new Event(EventType.MISSED, loop.from() + event.gate(), time, Double.NaN));
                case WRONG_WAY -> this.events.add(new Event(EventType.WRONG_WAY, loop.from() + event.gate(), time, Double.NaN));
                default -> {
                }
            }
        }
        return finished;
    }

    private boolean land(Goal.Land land, TrainingSample s, boolean disarmed) {
        this.padDistance = this.course.padDistance(land.pad(), s.x, s.z);
        if (!disarmed) {
            return false;
        }
        boolean onPad = this.padDistance <= this.course.padRadius(land.pad())
                && Math.abs(s.footY - this.course.padHeight(land.pad())) <= LAND_HEIGHT;
        if (!s.nearGround()) {
            this.fail(Failure.DISARMED);
            return false;
        }
        if (!onPad) {
            this.fail(Failure.OUTSIDE_PAD);
            return false;
        }
        return true;
    }

    private void trackZone(int zone, TrainingSample s) {
        boolean inside = this.course.inZone(zone, s.x, s.y, s.z);
        if (inside && !this.inZone) {
            this.zoneVisits.add(new double[]{s.time, Double.POSITIVE_INFINITY});
        } else if (!inside && this.inZone && !this.zoneVisits.isEmpty()) {
            this.zoneVisits.getLast()[1] = s.time;
        }
        this.inZone = inside;
        this.zoneVisits.removeIf(visit -> visit[1] < s.time - ZONE_MEMORY);
    }

    public boolean inZoneDuring(double from, double to) {
        for (double[] visit : this.zoneVisits) {
            if (visit[0] - ZONE_GRACE <= to && visit[1] + ZONE_GRACE >= from) {
                return true;
            }
        }
        return false;
    }

    private boolean trickInZone(Trick trick) {
        boolean inside = this.inZoneDuring(Math.min(trick.start(), trick.time()), trick.time());
        this.lastTrick = trick.type();
        this.events.add(new Event(inside ? EventType.TRICK : EventType.TRICK_OUTSIDE, trick.type().ordinal(), this.elapsed(), Double.NaN));
        return inside;
    }

    private boolean tricks(Goal.Tricks goal, TrainingSample s) {
        this.trackZone(goal.zone(), s);
        for (Trick trick : this.tricks.score().awarded()) {
            if (this.trickInZone(trick) && goal.accepts(trick.type())) {
                this.trickCount++;
            }
        }
        return this.trickCount >= goal.count();
    }

    private boolean combo(Goal.Combo goal, TrainingSample s) {
        this.trackZone(goal.zone(), s);
        for (Trick trick : this.tricks.score().awarded()) {
            if (this.trickInZone(trick)) {
                this.trickCount++;
                this.comboNow++;
            } else {
                this.comboNow = 0;
            }
        }
        this.comboNow = Math.min(this.comboNow, this.tricks.score().comboCount());
        this.comboBest = Math.max(this.comboBest, this.comboNow);
        return this.comboBest >= goal.tricks();
    }

    private void succeed() {
        this.end = this.now;
        this.state = State.SUCCESS;
        double time = this.elapsed();
        StarRule rule = this.lesson.stars();
        double metric = rule.metric() == StarRule.Metric.PAD_DISTANCE ? this.padDistance : time;
        int stars = rule.stars(metric, this.course.scale(), this.assisted);
        double[] split = new double[this.splits.size()];
        for (int i = 0; i < split.length; i++) {
            split[i] = this.splits.get(i);
        }
        this.result = new Result(stars, metric, time, this.assisted, split);
        this.events.add(new Event(EventType.SUCCESS, stars, time, Double.NaN));
    }

    private void fail(Failure reason) {
        if (this.finished()) {
            return;
        }
        this.end = this.now;
        this.state = State.FAILED;
        this.failure = reason;
        this.events.add(new Event(EventType.FAILED, reason.ordinal(), this.elapsed(), Double.NaN));
    }

    public double[] referenceSplits() {
        return Arrays.copyOf(this.reference, this.reference.length);
    }
}
