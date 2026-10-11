package de.lardtom.propwash.race;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public final class LapTracker {
    public static final long NONE = -1L;
    public static final int NO_COURSE = 0;
    public static final long SECOND = 1_000_000_000L;
    public static final long DEFAULT_TIMEOUT_NANOS = 120L * SECOND;
    public static final long RESTART_COOLDOWN_NANOS = 3L * SECOND;
    public static final double MAX_SEGMENT = 24.0;
    public static final long FALSE_START_PENALTY_NANOS = 3L * SECOND;
    public static final double LEAVE_DISTANCE = 96.0;
    private static final double BACKWARD = 0.0;
    private static final double FORWARD = 1.0;
    private static final double STAGE = 2.0;
    private static final double UNDO = 3.0;
    private static final double FOUL = 4.0;

    private final List<Event> events = new ArrayList<>();
    private final List<double[]> crossings = new ArrayList<>();
    private List<GateGeometry> gates = List.of();
    private int[] stages = new int[0];
    private int courseId = NO_COURSE;
    private Phase phase = Phase.IDLE;
    private int lapTarget = 3;
    private long timeoutNanos = DEFAULT_TIMEOUT_NANOS;

    private boolean hasLast;
    private double lastX;
    private double lastY;
    private double lastZ;
    private long lastNanos;

    private int lap;
    private int nextGate;
    private boolean holeshotPending;
    private long raceStart;
    private long lapStart;
    private long lastProgress;
    private long finishedAt;
    private long lastLap = NONE;
    private long bestLap = NONE;
    private long totalNanos = NONE;
    private long splitDelta;
    private long[] splits = new long[0];
    private long sessionBest = NONE;
    private long[] sessionBestSplits = new long[0];
    private long[] referenceSplits = new long[0];
    private long penalty;
    private boolean falseStart;
    private final List<Long> lapTimes = new ArrayList<>();

    private long countdownEnd;
    private int countdownAnnounced;

    public enum Phase {
        IDLE,
        COUNTDOWN,
        RACING,
        FINISHED
    }

    public enum EventType {
        COUNTDOWN,
        GO,
        START,
        GATE,
        LAP,
        FINISH,
        MISSED,
        WRONG_WAY,
        WRONG_SIDE,
        FALSE_START,
        ABORT
    }

    public enum AbortReason {
        RESTART,
        TIMEOUT,
        LEFT_COURSE,
        COURSE_CHANGED,
        SESSION_ENDED
    }

    public record Event(EventType type, int lap, int gate, long nanos, long valueNanos, long deltaNanos, boolean best,
                        AbortReason reason) {
        public boolean hasDelta() {
            return this.deltaNanos != 0L;
        }
    }

    public void setLapTarget(int laps) {
        this.lapTarget = Math.max(0, laps);
    }

    public int lapTarget() {
        return this.lapTarget;
    }

    public void setTimeoutNanos(long timeoutNanos) {
        this.timeoutNanos = Math.max(SECOND, timeoutNanos);
    }

    public void setCourse(int courseId, List<GateGeometry> gates, long nowNanos) {
        boolean changed = courseId != this.courseId || !sameLayout(gates, this.gates);
        if (!changed) {
            return;
        }
        if (this.phase == Phase.RACING || this.phase == Phase.COUNTDOWN) {
            this.abort(nowNanos, AbortReason.COURSE_CHANGED);
        }
        if (courseId != this.courseId) {
            this.sessionBest = NONE;
            this.sessionBestSplits = new long[0];
            this.lastLap = NONE;
            this.bestLap = NONE;
            this.totalNanos = NONE;
            this.lapTimes.clear();
            this.phase = Phase.IDLE;
        } else if (gates.size() != this.gates.size()) {
            this.sessionBest = NONE;
            this.sessionBestSplits = new long[0];
        }
        this.courseId = courseId;
        this.gates = List.copyOf(gates);
        this.stages = new int[this.gates.size()];
        this.referenceSplits = new long[0];
    }

    private static boolean sameLayout(List<GateGeometry> a, List<GateGeometry> b) {
        return a.equals(b);
    }

    public void clearCourse(long nowNanos) {
        this.setCourse(NO_COURSE, List.of(), nowNanos);
    }

    public int courseId() {
        return this.courseId;
    }

    public List<GateGeometry> gates() {
        return this.gates;
    }

    public boolean beginCountdown(long nowNanos, long durationNanos) {
        if (this.gates.isEmpty() || this.phase == Phase.RACING || this.phase == Phase.COUNTDOWN) {
            return false;
        }
        this.phase = Phase.COUNTDOWN;
        this.countdownEnd = nowNanos + Math.max(0L, durationNanos);
        this.countdownAnnounced = (int) ((durationNanos + SECOND - 1) / SECOND) + 1;
        this.falseStart = false;
        this.tickCountdown(nowNanos);
        return true;
    }

    public void abort(long nowNanos, AbortReason reason) {
        if (this.phase == Phase.RACING || this.phase == Phase.COUNTDOWN) {
            this.phase = Phase.IDLE;
            this.emit(EventType.ABORT, this.lap, this.nextGate, nowNanos, 0L, 0L, false, reason);
        }
        this.holeshotPending = false;
        this.falseStart = false;
        Arrays.fill(this.stages, 0);
    }

    public void resetPosition() {
        this.hasLast = false;
    }

    public void update(long nowNanos, double x, double y, double z) {
        if (this.hasLast && nowNanos > this.lastNanos) {
            this.checkSegment(nowNanos, x, y, z);
        }
        if (this.phase == Phase.COUNTDOWN) {
            this.tickCountdown(nowNanos);
        }
        this.hasLast = true;
        this.lastX = x;
        this.lastY = y;
        this.lastZ = z;
        this.lastNanos = nowNanos;
        if (this.phase == Phase.RACING && nowNanos - this.lastProgress > this.timeoutNanos) {
            this.abort(nowNanos, AbortReason.TIMEOUT);
        } else if (this.phase == Phase.RACING && this.farFromCourse(x, y, z)) {
            this.abort(nowNanos, AbortReason.LEFT_COURSE);
        }
    }

    public boolean farFromCourse(double x, double y, double z) {
        if (this.gates.isEmpty()) {
            return false;
        }
        double limit = LEAVE_DISTANCE * LEAVE_DISTANCE;
        for (GateGeometry gate : this.gates) {
            double dx = gate.x() - x;
            double dy = gate.y() - y;
            double dz = gate.z() - z;
            if (dx * dx + dy * dy + dz * dz <= limit) {
                return false;
            }
        }
        return true;
    }

    public void tick(long nowNanos) {
        if (this.phase == Phase.COUNTDOWN) {
            this.tickCountdown(nowNanos);
        } else if (this.phase == Phase.RACING && nowNanos - this.lastProgress > this.timeoutNanos) {
            this.abort(nowNanos, AbortReason.TIMEOUT);
        }
    }

    private void tickCountdown(long nowNanos) {
        long remaining = this.countdownEnd - nowNanos;
        if (remaining <= 0L) {
            this.startRace(this.countdownEnd, true);
            this.emit(EventType.GO, 1, 0, this.countdownEnd, 0L, 0L, false, null);
            return;
        }
        int seconds = (int) ((remaining + SECOND - 1) / SECOND);
        if (seconds < this.countdownAnnounced) {
            this.countdownAnnounced = seconds;
            this.emit(EventType.COUNTDOWN, 0, 0, nowNanos, seconds, 0L, false, null);
        }
    }

    private void checkSegment(long nowNanos, double x, double y, double z) {
        double dx = x - this.lastX;
        double dy = y - this.lastY;
        double dz = z - this.lastZ;
        if (dx * dx + dy * dy + dz * dz > MAX_SEGMENT * MAX_SEGMENT || this.gates.isEmpty()) {
            return;
        }
        this.crossings.clear();
        int active = this.activeElement();
        for (int i = 0; i < this.gates.size(); i++) {
            GateGeometry gate = this.gates.get(i);
            if (!gate.gate()) {
                this.elementCrossings(gate, i, active, x, y, z);
                continue;
            }
            double t = gate.crossing(this.lastX, this.lastY, this.lastZ, x, y, z, true);
            if (t >= 0.0) {
                this.crossings.add(new double[]{t, i, FORWARD, 0.0});
            } else if (this.phase == Phase.RACING && i == this.nextGate) {
                double back = gate.crossing(this.lastX, this.lastY, this.lastZ, x, y, z, false);
                if (back >= 0.0) {
                    this.crossings.add(new double[]{back, i, BACKWARD, 0.0});
                }
            }
        }
        if (this.crossings.isEmpty()) {
            return;
        }
        this.crossings.sort((a, b) -> a[0] != b[0] ? Double.compare(a[0], b[0]) : a[1] != b[1] ? Double.compare(a[1], b[1]) : Double.compare(a[2], b[2]));
        long span = nowNanos - this.lastNanos;
        for (double[] crossing : this.crossings) {
            long time = this.lastNanos + Math.round(span * crossing[0]);
            int gate = (int) crossing[1];
            double type = crossing[2];
            if (type == FORWARD) {
                this.onCrossing(gate, time);
            } else if (type == BACKWARD) {
                this.emit(EventType.WRONG_WAY, this.lap, gate, time, 0L, 0L, false, null);
            } else {
                this.onStage(gate, type, (int) crossing[3], time);
            }
        }
    }

    private int activeElement() {
        return this.phase == Phase.RACING ? this.nextGate : 0;
    }

    private void elementCrossings(GateGeometry gate, int index, int active, double x, double y, double z) {
        boolean racing = this.phase == Phase.RACING;
        if (index != active && !(racing && gate.kind().strictMiss())) {
            return;
        }
        List<Passage> passages = gate.stages();
        int stage = Math.min(this.stages[index], passages.size() - 1);
        for (int s = stage; s < passages.size(); s++) {
            double t = passages.get(s).crossing(this.lastX, this.lastY, this.lastZ, x, y, z, true);
            if (t >= 0.0) {
                this.crossings.add(new double[]{t, index, STAGE, s});
            }
        }
        if (stage > 0) {
            double back = passages.get(stage - 1).crossing(this.lastX, this.lastY, this.lastZ, x, y, z, false);
            if (back >= 0.0) {
                this.crossings.add(new double[]{back, index, UNDO, stage - 1});
            }
        }
        if (racing && index == active && gate.freeform()) {
            double back = passages.getFirst().crossing(this.lastX, this.lastY, this.lastZ, x, y, z, false);
            if (back >= 0.0) {
                this.crossings.add(new double[]{back, index, BACKWARD, 0.0});
            }
        }
        if (racing && index == active) {
            for (Passage foul : gate.fouls(stage)) {
                double t = foul.crossing(this.lastX, this.lastY, this.lastZ, x, y, z, true);
                if (t >= 0.0) {
                    this.crossings.add(new double[]{t, index, FOUL, 0.0});
                }
            }
        }
    }

    private void onStage(int gate, double type, int stage, long time) {
        if (gate >= this.stages.length) {
            return;
        }
        if (type == STAGE) {
            if (this.stages[gate] != stage) {
                return;
            }
            this.stages[gate] = stage + 1;
            if (this.stages[gate] >= this.gates.get(gate).kind().stages()) {
                this.stages[gate] = 0;
                this.onCrossing(gate, time);
            }
        } else if (type == UNDO) {
            if (this.stages[gate] == stage + 1) {
                this.stages[gate] = stage;
            }
        } else if (type == FOUL && this.phase == Phase.RACING && gate == this.nextGate) {
            this.stages[gate] = 0;
            this.emit(EventType.WRONG_SIDE, this.lap, gate, time, 0L, 0L, false, null);
        }
    }

    public int stage(int gate) {
        return gate >= 0 && gate < this.stages.length ? this.stages[gate] : 0;
    }

    private void onCrossing(int gate, long time) {
        if (this.phase == Phase.COUNTDOWN && time >= this.countdownEnd) {
            this.tickCountdown(time);
        }
        switch (this.phase) {
            case IDLE -> {
                if (gate == 0) {
                    this.startRace(time, false);
                    this.emit(EventType.START, 1, 0, time, 0L, 0L, false, null);
                }
            }
            case FINISHED -> {
                if (gate == 0 && time - this.finishedAt >= RESTART_COOLDOWN_NANOS) {
                    this.startRace(time, false);
                    this.emit(EventType.START, 1, 0, time, 0L, 0L, false, null);
                }
            }
            case COUNTDOWN -> {
                if (gate == 0 && !this.falseStart) {
                    this.falseStart = true;
                    this.emit(EventType.FALSE_START, 0, gate, time, FALSE_START_PENALTY_NANOS, 0L, false, null);
                }
            }
            case RACING -> this.onRacingCrossing(gate, time);
        }
    }

    private void onRacingCrossing(int gate, long time) {
        if (gate != this.nextGate) {
            this.emit(EventType.MISSED, this.lap, this.nextGate, time, gate, 0L, false, null);
            return;
        }
        this.lastProgress = time;
        int count = this.gates.size();
        if (this.holeshotPending) {
            this.holeshotPending = false;
            this.nextGate = 1 % count;
            this.stages[this.nextGate] = 0;
            this.emit(EventType.GATE, this.lap, gate, time, time - this.lapStart, 0L, false, null);
            return;
        }
        if (gate == 0) {
            this.completeLap(time);
            return;
        }
        long split = time - this.lapStart + this.lapPenalty();
        this.splits[gate] = split;
        long[] referenceSplits = this.reference();
        long reference = gate < referenceSplits.length ? referenceSplits[gate] : NONE;
        this.splitDelta = reference >= 0L ? split - reference : 0L;
        this.nextGate = (gate + 1) % count;
        this.stages[this.nextGate] = 0;
        this.emit(EventType.GATE, this.lap, gate, time, split, this.splitDelta, false, null);
    }

    private void completeLap(long time) {
        long lapTime = time - this.lapStart + this.lapPenalty();
        this.splits[0] = lapTime;
        long[] referenceSplits = this.reference();
        long referenceLap = referenceSplits.length > 0 ? referenceSplits[0] : NONE;
        this.splitDelta = referenceLap >= 0L ? lapTime - referenceLap : 0L;
        boolean raceBest = this.bestLap < 0L || lapTime < this.bestLap;
        if (raceBest) {
            this.bestLap = lapTime;
        }
        boolean sessionBestLap = this.sessionBest < 0L || lapTime < this.sessionBest;
        if (sessionBestLap) {
            this.sessionBest = lapTime;
            this.sessionBestSplits = this.splits.clone();
        }
        this.lastLap = lapTime;
        this.lapTimes.add(lapTime);
        this.emit(EventType.LAP, this.lap, 0, time, lapTime, this.splitDelta, sessionBestLap, null);
        if (this.lapTarget > 0 && this.lap >= this.lapTarget) {
            this.phase = Phase.FINISHED;
            this.totalNanos = time - this.raceStart + this.penalty;
            this.finishedAt = time;
            this.emit(EventType.FINISH, this.lap, 0, time, this.totalNanos, 0L, false, null);
            return;
        }
        this.lap++;
        this.lapStart = time;
        this.nextGate = 1 % this.gates.size();
        Arrays.fill(this.splits, NONE);
        Arrays.fill(this.stages, 0);
    }

    private void startRace(long time, boolean standing) {
        this.phase = Phase.RACING;
        this.lap = 1;
        this.raceStart = time;
        this.lapStart = time;
        this.lastProgress = time;
        boolean passed = standing && this.falseStart;
        this.holeshotPending = standing && !passed;
        this.nextGate = this.holeshotPending ? 0 : 1 % this.gates.size();
        this.penalty = passed ? FALSE_START_PENALTY_NANOS : 0L;
        this.falseStart = false;
        this.lastLap = NONE;
        this.bestLap = NONE;
        this.totalNanos = NONE;
        this.splitDelta = 0L;
        this.lapTimes.clear();
        this.splits = new long[this.gates.size()];
        Arrays.fill(this.splits, NONE);
        Arrays.fill(this.stages, 0);
        if (this.sessionBestSplits.length != this.gates.size()) {
            this.sessionBest = NONE;
            this.sessionBestSplits = new long[0];
        }
    }

    private long lapPenalty() {
        return this.lap == 1 ? this.penalty : 0L;
    }

    private long[] reference() {
        if (this.referenceSplits.length == this.gates.size() && this.referenceSplits.length > 0) {
            return this.referenceSplits;
        }
        return this.sessionBestSplits;
    }

    public void setReference(long[] splits) {
        this.referenceSplits = splits == null ? new long[0] : splits.clone();
    }

    public boolean hasReference() {
        return this.referenceSplits.length == this.gates.size() && this.referenceSplits.length > 0;
    }

    public long penaltyNanos() {
        return this.penalty;
    }

    public long lapPenaltyNanos() {
        return this.racing() || this.phase == Phase.FINISHED ? this.lapPenalty() : 0L;
    }

    public boolean falseStarted() {
        return this.phase == Phase.COUNTDOWN && this.falseStart;
    }

    public long lapElapsedNanos(long nowNanos) {
        return this.racing() ? Math.max(0L, nowNanos - this.lapStart) : 0L;
    }

    private void emit(EventType type, int lap, int gate, long nanos, long value, long delta, boolean best, AbortReason reason) {
        this.events.add(new Event(type, lap, gate, nanos, value, delta, best, reason));
    }

    public List<Event> drainEvents() {
        if (this.events.isEmpty()) {
            return List.of();
        }
        List<Event> drained = List.copyOf(this.events);
        this.events.clear();
        return drained;
    }

    public Phase phase() {
        return this.phase;
    }

    public boolean racing() {
        return this.phase == Phase.RACING;
    }

    public int lap() {
        return this.racing() ? this.lap : 0;
    }

    public long currentLapNanos(long nowNanos) {
        return this.racing() ? Math.max(0L, nowNanos - this.lapStart) + this.lapPenalty() : 0L;
    }

    public long raceNanos(long nowNanos) {
        if (this.racing()) {
            return Math.max(0L, nowNanos - this.raceStart) + this.penalty;
        }
        return this.phase == Phase.FINISHED ? this.totalNanos : 0L;
    }

    public long lastLapNanos() {
        return this.lastLap;
    }

    public long bestLapNanos() {
        return this.bestLap;
    }

    public long sessionBestNanos() {
        return this.sessionBest;
    }

    public long totalNanos() {
        return this.totalNanos;
    }

    public List<Long> lapTimes() {
        return List.copyOf(this.lapTimes);
    }

    public int nextGate() {
        return this.racing() ? this.nextGate : -1;
    }

    public int gateCount() {
        return this.racing() ? this.gates.size() : 0;
    }

    public boolean holeshot() {
        return this.racing() && this.holeshotPending;
    }

    public long splitDeltaNanos() {
        return this.racing() || this.phase == Phase.FINISHED ? this.splitDelta : 0L;
    }

    public long countdownRemainingNanos(long nowNanos) {
        return this.phase == Phase.COUNTDOWN ? Math.max(0L, this.countdownEnd - nowNanos) : NONE;
    }
}
