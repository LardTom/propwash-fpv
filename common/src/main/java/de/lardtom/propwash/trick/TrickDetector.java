package de.lardtom.propwash.trick;

import org.joml.Quaterniond;
import org.jspecify.annotations.Nullable;

import java.util.List;

public final class TrickDetector {
    public static final double MAX_FRAME_GAP = 0.3;
    public static final double MAX_JUMP = 20.0;
    public static final double QUIET_UP = -0.2;
    public static final double UPRIGHT_UP = 0.5;
    public static final double HOLD_UPRIGHT = 0.05;
    public static final double HOLD_TILTED = 0.3;
    public static final double MAX_WINDOW = 8.0;
    public static final double TURN_FIRST = 250.0;
    public static final double TURN_FULL = 360.0;
    public static final int MAX_TURNS = 3;
    public static final double DIVE_NOSE_DOWN = -0.64;
    public static final double DIVE_PATH_STEEP = -0.7;
    public static final double DIVE_SINK = -7.0;
    public static final double DIVE_MIN_SPEED = 9.0;
    public static final double DIVE_RECOVERED_SINK = -1.5;
    public static final double DIVE_MIN_STEEP = 0.7;
    public static final double DIVE_MIN_DROP = 12.0;
    public static final double DIVE_MAX_SECONDS = 15.0;
    public static final double ORBIT_MIN_SPEED = 2.5;
    public static final double ORBIT_MIN_UP = 0.3;
    public static final double ORBIT_SIDEWAYS_COS = 0.64;
    public static final double ORBIT_MIN_RATE = 20.0;
    public static final double ORBIT_GRACE = 0.4;
    public static final double ORBIT_MIN_RATIO = 0.5;
    public static final double ORBIT_MAX_RATIO = 2.0;
    public static final double ORBIT_TURN = 360.0;
    public static final double KNIFE_MAX_UP = 0.3;
    public static final double KNIFE_MAX_NOSE = 0.5;
    public static final double KNIFE_MIN_SPEED = 5.0;
    public static final double KNIFE_ALIGN = 0.7;
    public static final double KNIFE_EXIT = 0.1;
    public static final double KNIFE_MIN_SECONDS = 0.5;
    public static final double KNIFE_MAX_TURN = 25.0;
    public static final double KNIFE_MAX_TURN_RATE = 25.0;
    public static final double GAP_MIN_SPEED = 4.0;
    public static final double GAP_MAX_SIDE = 1.6;
    public static final double GAP_MAX_WIDTH = 2.2;
    public static final double GAP_EXIT = 0.1;
    public static final double GAP_MAX_SECONDS = 3.0;
    public static final double PROX_MIN_SPEED = 7.0;
    public static final double PROX_MAX_CLEARANCE = 0.9;
    public static final double PROX_DOWN_LIMIT = -0.7;
    public static final double PROX_EXIT = 0.12;
    public static final double PROX_MIN_SECONDS = 0.35;
    public static final double PROX_WITH_GAP_SECONDS = 1.5;
    public static final double CRASH_SETTLE = 0.5;
    public static final double CALM_UP = 0.4;
    public static final double CALM_RATE = 120.0;
    public static final double EJECT_START_TILT = 20.0;
    public static final double EJECT_TILT = 40.0;
    public static final double EJECT_HOLD_TILT = 30.0;
    public static final double EJECT_LEVEL = 15.0;
    public static final double EJECT_ENTRY_SECONDS = 0.35;
    public static final double EJECT_MAX_SECONDS = 1.2;
    public static final double EJECT_SNAP_SECONDS = 0.3;
    public static final double EJECT_THROTTLE = 0.75;
    public static final double EJECT_MIN_PUNCH = 0.1;
    public static final double EJECT_MIN_GAIN = 3.0;
    public static final double EJECT_MAX_CLIMB = 6.0;
    public static final double EJECT_MIN_UP = -0.35;
    public static final double GUILLOTINE_MIN_DROP = 8.0;
    public static final double GUILLOTINE_LOOKBACK = 6.0;
    public static final double GUILLOTINE_LATE = 0.8;
    public static final double GUILLOTINE_SINK = 7.0;
    public static final double GUILLOTINE_STEADY_SECONDS = 0.3;
    public static final double GUILLOTINE_STEADY_MAX_TURN = 40.0;
    public static final double GUILLOTINE_MIN_FLIP = 200.0;
    public static final double GUILLOTINE_MAX_FLIP_SECONDS = 1.5;
    public static final double GUILLOTINE_RECOVERED_SINK = -2.5;
    public static final double GUILLOTINE_RECOVERED_UP = 0.3;
    public static final double GUILLOTINE_RECOVER_SECONDS = 0.8;
    public static final double BACK_DIVE_NOSE_UP = 0.26;
    public static final double BACK_DIVE_MIN_UP = -0.3;
    public static final double WALL_MAX_CLEARANCE = 1.0;
    public static final double WALL_FRACTION = 0.5;
    public static final double HAMMER_NOSE_UP = 0.6;
    public static final double HAMMER_STALL = 4.0;
    public static final double HAMMER_MIN_FALL = 2.0;
    public static final double HAMMER_NOSE_DOWN = -0.5;
    public static final double HAMMER_CLIMB = 2.0;
    public static final double HAMMER_KEEP_UP = 0.64;
    public static final double HAMMER_MIN_YAW = 60.0;
    public static final double HAMMER_YAW_RATIO = 1.5;
    public static final double HAMMER_TURN_SECONDS = 1.0;
    public static final double HAMMER_MIN_NOSE_UP = 0.3;
    public static final double HAMMER_MAX_SECONDS = 6.0;
    public static final double HAMMER_MIN_CLIMB = 3.0;
    public static final double HAMMER_RECOVERED_UP = 0.3;
    public static final double HAMMER_REVERSED_COS = -0.5;
    public static final double MAVERICK_MIN_SPEED = 1.5;
    public static final double MAVERICK_START_SPEED = 2.0;
    public static final double MAVERICK_MIN_UP = -0.2;
    public static final double MAVERICK_MAX_LATERAL = 0.5;
    public static final double MAVERICK_MAX_STEP = 60.0;
    public static final double MAVERICK_MAX_SECONDS = 6.0;
    public static final double MAVERICK_MAX_PITCH = 270.0;
    public static final double MAVERICK_TURN = 330.0;
    public static final double MAVERICK_MIN_HEIGHT = 2.5;
    public static final double MAVERICK_BACKWARD_TURN = -90.0;
    public static final double MAVERICK_RESTART_TURN = 15.0;
    static final int HISTORY = 512;
    static final double HISTORY_SLOT = 1.0 / 60.0;

    private final Quaterniond attitude = new Quaterniond();
    private boolean recovering;
    private double recoverCalm;
    private final Quaterniond previous = new Quaterniond();
    private final Quaterniond delta = new Quaterniond();
    private boolean hasPrevious;
    private double prevTime;
    private double prevX;
    private double prevY;
    private double prevZ;
    private int prevCrash;
    private int prevImpact;

    private double dPitch;
    private double dRoll;
    private double dYaw;
    private double rollRate;
    private double pitchRate;
    private double yawRate;
    private double upX;
    private double upY = 1.0;
    private double upZ;
    private double fwdX;
    private double fwdY;
    private double fwdZ = 1.0;
    private double noseHeading = Double.NaN;
    private double pathHeading = Double.NaN;
    private double noseTurn;
    private double pathTurn;
    private double nearest = Double.POSITIVE_INFINITY;
    private boolean contact;

    private final ManeuverLog log = new ManeuverLog();
    private final ManeuverClassifier classifier = new ManeuverClassifier();
    private final LineRecognizer lines = new LineRecognizer();
    private boolean window;
    private double quiet;

    private boolean diving;
    private double diveStartTime;
    private double diveTopY = Double.NaN;
    private double diveSteep;
    private double diveBack;
    private double diveNearest;
    private boolean diveContact;
    private @Nullable Trick heldDive;

    private final double[] histTime = new double[HISTORY];
    private final double[] histY = new double[HISTORY];
    private final double[] histVy = new double[HISTORY];
    private final double[] histFwdY = new double[HISTORY];
    private final double[] histPitch = new double[HISTORY];
    private double histPitchSum;
    private int histHead;
    private int histSize;
    private @Nullable Trick pendingFlip;
    private @Nullable Trick pendingDive;
    private double pendingTop;
    private double pendingTopTime;
    private double guillotineTopTime;

    private boolean ejecting;
    private double ejectLowTime = Double.NaN;
    private double ejectLowSpeed;
    private double ejectLowY;
    private double ejectStart;
    private double ejectSpeed;
    private double ejectY;
    private double ejectPunch;
    private double ejectHigh;
    private double ejectNearest;

    private double orbitSign;
    private double orbitTurn;
    private double orbitGrace;
    private double orbitStart;
    private double orbitNearest = Double.POSITIVE_INFINITY;

    private int hammerPhase;
    private double hammerStart;
    private double hammerY;
    private double hammerTop;
    private double hammerRefX;
    private double hammerRefZ;
    private double hammerLastFwdY;
    private double hammerTurnTime;
    private double hammerNoseUp;
    private double hammerYaw;
    private double hammerTumble;
    private double hammerUpX;
    private double hammerUpY;
    private double hammerUpZ;
    private double hammerTurned;
    private double hammerNearest;

    private boolean maverick;
    private double maverickRefX;
    private double maverickRefZ;
    private double maverickAngle;
    private double maverickTurn;
    private double maverickStart;
    private double maverickLow;
    private double maverickHigh;
    private double maverickPitch;
    private double maverickNearest;

    private boolean inKnife;
    private boolean knifeReverse;
    private double knifeWall;
    private double knifeStart;
    private double knifeMiss;
    private double knifeHeading;
    private double knifeTurn;
    private boolean knifeBlocked;
    private double knifeNearest;
    private boolean knifeContact;

    private boolean inGap;
    private double gapStart;
    private double gapMiss;
    private double gapWidth;
    private boolean gapContact;

    private boolean inProximity;
    private double proxStart;
    private double proxMiss;
    private double proxNearest;
    private boolean proxContact;
    private boolean proxHadGap;

    public TrickDetector() {
    }

    public void reset() {
        this.hasPrevious = false;
        this.recovering = false;
        this.recoverCalm = 0.0;
        this.resetMotion();
    }

    public void resetMotion() {
        this.window = false;
        this.quiet = 0.0;
        this.log.clear();
        this.diving = false;
        this.diveTopY = Double.NaN;
        this.heldDive = null;
        this.pendingFlip = null;
        this.pendingDive = null;
        this.histSize = 0;
        this.histPitchSum = 0.0;
        this.ejecting = false;
        this.ejectLowTime = Double.NaN;
        this.hammerPhase = 0;
        this.maverick = false;
        this.lines.reset();
        this.orbitSign = 0.0;
        this.orbitTurn = 0.0;
        this.orbitGrace = 0.0;
        this.orbitNearest = Double.POSITIVE_INFINITY;
        this.inKnife = false;
        this.knifeBlocked = false;
        this.inGap = false;
        this.inProximity = false;
        this.noseHeading = Double.NaN;
        this.pathHeading = Double.NaN;
        this.rollRate = 0.0;
        this.pitchRate = 0.0;
        this.yawRate = 0.0;
    }

    public double upY() {
        return this.upY;
    }

    public double rotationRate() {
        return Math.sqrt(this.rollRate * this.rollRate + this.pitchRate * this.pitchRate + this.yawRate * this.yawRate);
    }

    public double tumbleRate() {
        return Math.hypot(this.rollRate, this.pitchRate);
    }

    public double yawRate() {
        return this.yawRate;
    }

    public boolean contact() {
        return this.contact;
    }

    public boolean maneuvering() {
        return this.window;
    }

    List<Stroke> lastStrokes() {
        return this.classifier.strokes();
    }

    public void update(TrickSample s, List<Trick> out) {
        this.attitude.set(s.qx, s.qy, s.qz, s.qw);
        if (!(this.attitude.lengthSquared() > 1.0E-12) || !Double.isFinite(s.time)) {
            return;
        }
        this.attitude.normalize();
        if (this.hasPrevious && s.time <= this.prevTime) {
            return;
        }
        boolean crashed = this.hasPrevious && s.crashCount > this.prevCrash;
        this.contact = s.grounded || (this.hasPrevious && s.impactCount > this.prevImpact);
        double dt = this.hasPrevious ? s.time - this.prevTime : 0.0;
        double jump = this.hasPrevious ? Math.sqrt(sq(s.x - this.prevX) + sq(s.y - this.prevY) + sq(s.z - this.prevZ)) : 0.0;
        boolean discontinuous = !this.hasPrevious || dt > MAX_FRAME_GAP || jump > Math.max(MAX_JUMP, s.speed() * dt * 3.0);
        this.orient(discontinuous ? 0.0 : dt);
        this.nearest = nearest(s, false);
        this.remember(s);
        if (crashed) {
            this.recovering = true;
            this.recoverCalm = 0.0;
        }
        if (discontinuous || crashed || !s.armed || s.assisted) {
            this.resetMotion();
            return;
        }
        if (this.recovering) {
            this.recoverCalm = this.calm() ? this.recoverCalm + dt : 0.0;
            if (this.recoverCalm < CRASH_SETTLE) {
                this.resetMotion();
                return;
            }
            this.recovering = false;
        }
        int from = out.size();
        this.headings(s);
        this.history(s);
        this.maneuver(s, dt, out);
        this.resolveGuillotine(s, out);
        this.hammerhead(s, dt, out);
        this.dive(s, dt, out);
        this.maverick(s, out);
        this.orbit(s, dt, out);
        this.knifeEdge(s, dt, out);
        this.gap(s, dt, out);
        this.proximity(s, dt, out);
        this.eject(s, dt, out);
        this.lines.accept(out, from);
    }

    private void remember(TrickSample s) {
        this.previous.set(this.attitude);
        this.prevTime = s.time;
        this.prevX = s.x;
        this.prevY = s.y;
        this.prevZ = s.z;
        this.prevCrash = s.crashCount;
        this.prevImpact = s.impactCount;
        this.hasPrevious = true;
    }

    private void orient(double dt) {
        Quaterniond q = this.attitude;
        this.upX = 2.0 * (q.x * q.y - q.w * q.z);
        this.upY = 1.0 - 2.0 * (q.x * q.x + q.z * q.z);
        this.upZ = 2.0 * (q.y * q.z + q.w * q.x);
        this.fwdX = 2.0 * (q.x * q.z + q.w * q.y);
        this.fwdY = 2.0 * (q.y * q.z - q.w * q.x);
        this.fwdZ = 1.0 - 2.0 * (q.x * q.x + q.y * q.y);
        this.dPitch = 0.0;
        this.dRoll = 0.0;
        this.dYaw = 0.0;
        if (!(dt > 0.0)) {
            this.rollRate = 0.0;
            this.pitchRate = 0.0;
            this.yawRate = 0.0;
            return;
        }
        this.previous.conjugate(this.delta).mul(q);
        Quaterniond d = this.delta;
        if (d.w < 0.0) {
            d.set(-d.x, -d.y, -d.z, -d.w);
        }
        double vl = Math.sqrt(d.x * d.x + d.y * d.y + d.z * d.z);
        double rx = 0.0;
        double ry = 0.0;
        double rz = 0.0;
        if (vl > 1.0E-12) {
            double angle = 2.0 * Math.atan2(vl, d.w);
            double k = angle / vl;
            rx = d.x * k;
            ry = d.y * k;
            rz = d.z * k;
        }
        this.dPitch = Math.toDegrees(rx);
        this.dRoll = Math.toDegrees(rz);
        this.dYaw = -Math.toDegrees(ry);
        this.pitchRate = this.dPitch / dt;
        this.rollRate = this.dRoll / dt;
        this.yawRate = this.dYaw / dt;
    }

    private void headings(TrickSample s) {
        double h = Math.hypot(this.fwdX, this.fwdZ);
        double nose = h > 0.3 ? Math.atan2(this.fwdX, this.fwdZ) : Double.NaN;
        this.noseTurn = Double.isFinite(nose) && Double.isFinite(this.noseHeading) ? ManeuverClassifier.wrap(nose - this.noseHeading) : 0.0;
        this.noseHeading = nose;
        double path = s.horizontalSpeed() >= ORBIT_MIN_SPEED * 0.8 ? Math.atan2(s.vx, s.vz) : Double.NaN;
        this.pathTurn = Double.isFinite(path) && Double.isFinite(this.pathHeading) ? ManeuverClassifier.wrap(path - this.pathHeading) : 0.0;
        this.pathHeading = path;
    }

    private boolean calm() {
        return this.upY >= CALM_UP && this.tumbleRate() < CALM_RATE;
    }

    private boolean still() {
        return this.tumbleRate() < ManeuverClassifier.STROKE_RATE && Math.abs(this.yawRate) < ManeuverClassifier.YAW_RATE;
    }

    private void record(TrickSample s, double dt) {
        this.log.add(s, dt, this.dPitch, this.dRoll, this.dYaw, this.upX, this.upY, this.upZ, this.fwdX, this.fwdY, this.fwdZ, this.nearest, this.contact);
    }

    private void lead(TrickSample s) {
        this.log.clear();
        this.log.add(s, 0.0, 0.0, 0.0, 0.0, this.upX, this.upY, this.upZ, this.fwdX, this.fwdY, this.fwdZ, this.nearest, this.contact);
    }

    private void maneuver(TrickSample s, double dt, List<Trick> out) {
        boolean quietNow = this.still() && this.upY >= QUIET_UP;
        if (!this.window) {
            if (quietNow) {
                this.lead(s);
                return;
            }
            if (this.log.size == 0) {
                this.lead(s);
            }
            this.window = true;
            this.quiet = 0.0;
        }
        this.record(s, dt);
        if (quietNow) {
            this.quiet += dt;
            double hold = this.upY >= UPRIGHT_UP ? HOLD_UPRIGHT : HOLD_TILTED;
            if (this.quiet >= hold) {
                this.classifyWindow(s, out);
                this.window = false;
                this.lead(s);
            }
            return;
        }
        this.quiet = 0.0;
        if (this.log.full() || this.log.duration() > MAX_WINDOW) {
            this.classifyWindow(s, out);
            this.lead(s);
        }
    }

    private void classifyWindow(TrickSample s, List<Trick> out) {
        int before = out.size();
        this.classifier.classify(this.log, this.log.size - 1, out);
        boolean guillotine = false;
        if (this.pendingFlip == null) {
            for (int i = before; i < out.size(); i++) {
                Trick trick = out.get(i);
                if (!guillotineFlip(trick.type())) {
                    continue;
                }
                double drop = this.guillotineDrop(trick.time());
                if (drop >= GUILLOTINE_MIN_DROP) {
                    out.remove(i);
                    this.pendingFlip = trick;
                    this.pendingTop = drop;
                    this.pendingTopTime = this.guillotineTopTime;
                    this.pendingDive = this.heldDive;
                    this.heldDive = null;
                    guillotine = true;
                    break;
                }
            }
        }
        if (this.heldDive != null) {
            out.add(this.heldDive);
            this.heldDive = null;
        }
        if (guillotine) {
            this.resolveGuillotine(s, out);
        }
    }

    private static boolean guillotineFlip(TrickType type) {
        return type == TrickType.FLIP || type == TrickType.DOUBLE_FLIP || type == TrickType.TRIPLE_FLIP || type == TrickType.POWER_LOOP
                || LineRecognizer.matty(type);
    }

    private void history(TrickSample s) {
        this.histPitchSum += Math.abs(this.dPitch);
        if (this.histSize > 0) {
            int last = (this.histHead - 1 + HISTORY) % HISTORY;
            if (s.time - this.histTime[last] < HISTORY_SLOT) {
                return;
            }
        }
        int i = this.histHead;
        this.histTime[i] = s.time;
        this.histY[i] = s.y;
        this.histVy[i] = s.vy;
        this.histFwdY[i] = this.fwdY;
        this.histPitch[i] = this.histPitchSum;
        this.histPitchSum = 0.0;
        this.histHead = (i + 1) % HISTORY;
        this.histSize = Math.min(HISTORY, this.histSize + 1);
    }

    private int slot(int k) {
        return (this.histHead - 1 - k + 2 * HISTORY) % HISTORY;
    }

    private boolean diving(int i) {
        return this.histFwdY[i] <= DIVE_NOSE_DOWN && this.histVy[i] <= -GUILLOTINE_SINK;
    }

    private boolean steadyDive(int k) {
        double from = this.histTime[this.slot(k)];
        double turn = 0.0;
        for (int j = k; j < this.histSize; j++) {
            int i = this.slot(j);
            if (!this.diving(i)) {
                return false;
            }
            if (from - this.histTime[i] >= GUILLOTINE_STEADY_SECONDS) {
                return turn <= GUILLOTINE_STEADY_MAX_TURN;
            }
            turn += this.histPitch[i];
        }
        return false;
    }

    private double guillotineDrop(double end) {
        boolean fast = false;
        double flip = 0.0;
        for (int k = 0; k < this.histSize; k++) {
            int i = this.slot(k);
            double time = this.histTime[i];
            if (time > end) {
                continue;
            }
            double age = end - time;
            if (age > GUILLOTINE_MAX_FLIP_SECONDS) {
                return Double.NaN;
            }
            if (!fast && this.histVy[i] <= -GUILLOTINE_SINK) {
                fast = true;
            }
            if (!fast && age > GUILLOTINE_LATE) {
                return Double.NaN;
            }
            if (fast && this.steadyDive(k)) {
                return flip >= GUILLOTINE_MIN_FLIP ? this.dropAround(end) : Double.NaN;
            }
            flip += this.histPitch[i];
        }
        return Double.NaN;
    }

    private double dropAround(double end) {
        double low = Double.POSITIVE_INFINITY;
        double top = Double.NEGATIVE_INFINITY;
        double topTime = end;
        for (int k = 0; k < this.histSize; k++) {
            int i = this.slot(k);
            double time = this.histTime[i];
            if (time > end) {
                continue;
            }
            if (end - time > GUILLOTINE_LOOKBACK) {
                break;
            }
            low = Math.min(low, this.histY[i]);
            if (this.histY[i] > top) {
                top = this.histY[i];
                topTime = time;
            }
        }
        this.guillotineTopTime = topTime;
        return top - low;
    }

    private void resolveGuillotine(TrickSample s, List<Trick> out) {
        Trick flip = this.pendingFlip;
        if (flip == null) {
            return;
        }
        if (this.contact) {
            this.pendingFlip = null;
            this.pendingDive = null;
            return;
        }
        if (s.vy >= GUILLOTINE_RECOVERED_SINK && this.upY >= GUILLOTINE_RECOVERED_UP) {
            double low = s.y;
            double top = Double.NEGATIVE_INFINITY;
            for (int k = 0; k < this.histSize; k++) {
                int i = this.slot(k);
                if (this.histTime[i] < this.pendingTopTime) {
                    break;
                }
                low = Math.min(low, this.histY[i]);
                top = Math.max(top, this.histY[i]);
            }
            double drop = Double.isFinite(top) ? top - low : this.pendingTop;
            out.add(new Trick(TrickType.GUILLOTINE, flip.time(), drop, flip.minClearance(), this.pendingTopTime));
            this.pendingFlip = null;
            this.pendingDive = null;
            this.heldDive = null;
            this.diving = false;
            this.diveTopY = s.y;
            return;
        }
        if (s.time - flip.time() > GUILLOTINE_RECOVER_SECONDS) {
            if (this.pendingDive != null) {
                out.add(this.pendingDive);
            }
            out.add(flip);
            this.pendingFlip = null;
            this.pendingDive = null;
        }
    }

    private void eject(TrickSample s, double dt, List<Trick> out) {
        double tilt = Math.toDegrees(Math.acos(Math.clamp(this.upY, -1.0, 1.0)));
        double hs = s.horizontalSpeed();
        if (!this.ejecting) {
            if (tilt <= EJECT_START_TILT) {
                if (this.tumbleRate() < ManeuverClassifier.STROKE_RATE) {
                    this.ejectLowTime = s.time;
                    this.ejectLowSpeed = hs;
                    this.ejectLowY = s.y;
                }
                return;
            }
            if (tilt >= EJECT_TILT && this.upY >= EJECT_MIN_UP && Double.isFinite(this.ejectLowTime)
                    && s.time - this.ejectLowTime <= EJECT_ENTRY_SECONDS && !this.contact) {
                this.ejecting = true;
                this.ejectStart = this.ejectLowTime;
                this.ejectSpeed = this.ejectLowSpeed;
                this.ejectY = this.ejectLowY;
                this.ejectPunch = 0.0;
                this.ejectHigh = s.time;
                this.ejectNearest = this.nearest;
            }
            return;
        }
        if (this.upY < EJECT_MIN_UP || this.contact || s.time - this.ejectStart > EJECT_MAX_SECONDS) {
            this.ejecting = false;
            this.ejectLowTime = Double.NaN;
            return;
        }
        this.ejectNearest = Math.min(this.ejectNearest, this.nearest);
        if (tilt >= EJECT_HOLD_TILT) {
            this.ejectHigh = s.time;
            if (s.throttle >= EJECT_THROTTLE) {
                this.ejectPunch += dt;
            }
        }
        if (tilt > EJECT_LEVEL) {
            return;
        }
        this.ejecting = false;
        this.ejectLowTime = Double.NaN;
        double gain = hs - this.ejectSpeed;
        double climb = s.y - this.ejectY;
        if (this.ejectPunch >= EJECT_MIN_PUNCH && s.time - this.ejectHigh <= EJECT_SNAP_SECONDS && gain >= EJECT_MIN_GAIN
                && Math.abs(climb) <= EJECT_MAX_CLIMB) {
            out.add(new Trick(TrickType.EJECT, s.time, gain, this.ejectNearest, this.ejectStart));
        }
    }

    public static int turns(double degrees) {
        if (degrees < TURN_FIRST) {
            return 0;
        }
        return Math.min(MAX_TURNS, 1 + (int) ((degrees - TURN_FIRST) / TURN_FULL));
    }

    private void dive(TrickSample s, double dt, List<Trick> out) {
        if (s.vy >= DIVE_RECOVERED_SINK) {
            if (this.diving) {
                double drop = this.diveTopY - s.y;
                boolean back = this.diveBack > this.diveSteep;
                if (!this.diveContact && Math.max(this.diveSteep, this.diveBack) >= DIVE_MIN_STEEP && drop >= DIVE_MIN_DROP) {
                    Trick dive = new Trick(back ? TrickType.BACKWARDS_DIVE : TrickType.DIVE, s.time, drop, this.diveNearest, this.diveStartTime);
                    if (this.pendingFlip != null) {
                        this.pendingDive = dive;
                    } else if (this.window) {
                        this.heldDive = dive;
                    } else {
                        out.add(dive);
                    }
                }
                this.diving = false;
            }
            this.diveTopY = s.y;
            return;
        }
        if (!Double.isFinite(this.diveTopY)) {
            this.diveTopY = s.y;
        }
        double speed = s.speed();
        boolean falling = s.vy <= DIVE_SINK && speed >= DIVE_MIN_SPEED && s.vy <= DIVE_PATH_STEEP * speed;
        boolean steep = falling && this.fwdY <= DIVE_NOSE_DOWN;
        boolean back = falling && this.fwdY >= BACK_DIVE_NOSE_UP && this.upY >= BACK_DIVE_MIN_UP;
        if (!this.diving) {
            if (!steep && !back) {
                return;
            }
            this.diving = true;
            this.diveStartTime = s.time;
            this.diveSteep = 0.0;
            this.diveBack = 0.0;
            this.diveNearest = this.nearest;
            this.diveContact = false;
        }
        if (steep) {
            this.diveSteep += dt;
        }
        if (back) {
            this.diveBack += dt;
        }
        this.diveNearest = Math.min(this.diveNearest, this.nearest);
        this.diveContact |= this.contact;
        if (s.time - this.diveStartTime > DIVE_MAX_SECONDS) {
            this.diving = false;
            this.diveTopY = s.y;
        }
    }

    private void hammerhead(TrickSample s, double dt, List<Trick> out) {
        if (this.contact || this.upY < ManeuverClassifier.INVERTED_UP) {
            this.hammerPhase = 0;
            return;
        }
        if (this.hammerPhase == 0) {
            if (this.fwdY >= HAMMER_NOSE_UP && s.vy >= HAMMER_CLIMB) {
                double hs = s.horizontalSpeed();
                double h = Math.hypot(this.fwdX, this.fwdZ);
                if (hs >= ORBIT_MIN_SPEED) {
                    this.hammerRefX = s.vx / hs;
                    this.hammerRefZ = s.vz / hs;
                } else if (h > 1.0E-6) {
                    this.hammerRefX = this.fwdX / h;
                    this.hammerRefZ = this.fwdZ / h;
                } else {
                    return;
                }
                this.hammerPhase = 1;
                this.hammerStart = s.time;
                this.hammerY = s.y;
                this.hammerTop = s.y;
                this.hammerNoseUp = 0.0;
                this.hammerNearest = this.nearest;
                this.hammerTurnStart(s);
            }
            return;
        }
        this.hammerTop = Math.max(this.hammerTop, s.y);
        this.hammerNearest = Math.min(this.hammerNearest, this.nearest);
        if (this.hammerPhase == 1) {
            if (s.time - this.hammerStart > HAMMER_MAX_SECONDS) {
                this.hammerPhase = 0;
                return;
            }
            if (this.fwdY >= HAMMER_NOSE_UP) {
                this.hammerNoseUp += dt;
                this.hammerTurnStart(s);
                return;
            }
            this.hammerYaw += Math.abs(this.dYaw);
            this.hammerTumble += Math.hypot(this.dPitch, this.dRoll);
            boolean crossed = this.hammerLastFwdY >= 0.0 && this.fwdY < 0.0;
            this.hammerLastFwdY = this.fwdY;
            if ((crossed && Math.abs(s.vy) > HAMMER_STALL) || s.time - this.hammerTurnTime > HAMMER_TURN_SECONDS) {
                this.hammerPhase = 0;
                return;
            }
            if (this.fwdY > HAMMER_NOSE_DOWN) {
                return;
            }
            boolean kept = this.upX * this.hammerUpX + this.upY * this.hammerUpY + this.upZ * this.hammerUpZ >= HAMMER_KEEP_UP;
            boolean yawed = this.hammerYaw >= HAMMER_MIN_YAW && this.hammerYaw >= HAMMER_YAW_RATIO * this.hammerTumble;
            if (kept && yawed && this.hammerNoseUp >= HAMMER_MIN_NOSE_UP && this.hammerTop - this.hammerY >= HAMMER_MIN_CLIMB) {
                this.hammerPhase = 2;
                this.hammerTurned = s.time;
            } else {
                this.hammerPhase = 0;
            }
            return;
        }
        if (s.time - this.hammerTurned > HAMMER_MAX_SECONDS) {
            this.hammerPhase = 0;
            return;
        }
        if (s.vy < DIVE_RECOVERED_SINK || this.upY < HAMMER_RECOVERED_UP || this.hammerTop - s.y < HAMMER_MIN_FALL) {
            return;
        }
        double h = Math.hypot(this.fwdX, this.fwdZ);
        if (h < 0.3 || (this.fwdX * this.hammerRefX + this.fwdZ * this.hammerRefZ) / h > HAMMER_REVERSED_COS) {
            return;
        }
        this.hammerPhase = 0;
        out.add(new Trick(TrickType.HAMMERHEAD, s.time, this.hammerTop - this.hammerY, this.hammerNearest, this.hammerStart));
        this.diving = false;
        this.diveTopY = s.y;
    }

    private void hammerTurnStart(TrickSample s) {
        this.hammerUpX = this.upX;
        this.hammerUpY = this.upY;
        this.hammerUpZ = this.upZ;
        this.hammerLastFwdY = this.fwdY;
        this.hammerTurnTime = s.time;
        this.hammerYaw = 0.0;
        this.hammerTumble = 0.0;
    }

    private void maverick(TrickSample s, List<Trick> out) {
        double speed = s.speed();
        if (this.contact || this.upY < MAVERICK_MIN_UP || speed < MAVERICK_MIN_SPEED) {
            this.maverick = false;
            return;
        }
        double hs = s.horizontalSpeed();
        boolean level = hs >= MAVERICK_START_SPEED && Math.abs(s.vy) <= 0.5 * hs;
        if (!this.maverick || (level && Math.abs(this.maverickTurn) < MAVERICK_RESTART_TURN)) {
            this.maverick = false;
            if (level) {
                this.maverick = true;
                this.maverickRefX = s.vx / hs;
                this.maverickRefZ = s.vz / hs;
                this.maverickAngle = Math.atan2(s.vy, hs);
                this.maverickTurn = 0.0;
                this.maverickStart = s.time;
                this.maverickLow = s.y;
                this.maverickHigh = s.y;
                this.maverickPitch = 0.0;
                this.maverickNearest = this.nearest;
            }
            return;
        }
        double along = s.vx * this.maverickRefX + s.vz * this.maverickRefZ;
        double lateral = Math.abs(-s.vx * this.maverickRefZ + s.vz * this.maverickRefX);
        double angle = Math.atan2(s.vy, along);
        double step = Math.toDegrees(ManeuverClassifier.wrap(angle - this.maverickAngle));
        this.maverickAngle = angle;
        this.maverickTurn += step;
        this.maverickPitch += Math.abs(this.dPitch);
        if (lateral > MAVERICK_MAX_LATERAL * speed || Math.abs(step) > MAVERICK_MAX_STEP || s.time - this.maverickStart > MAVERICK_MAX_SECONDS
                || this.maverickPitch > MAVERICK_MAX_PITCH || this.maverickTurn < MAVERICK_BACKWARD_TURN) {
            this.maverick = false;
            return;
        }
        this.maverickLow = Math.min(this.maverickLow, s.y);
        this.maverickHigh = Math.max(this.maverickHigh, s.y);
        this.maverickNearest = Math.min(this.maverickNearest, this.nearest);
        if (this.maverickTurn >= MAVERICK_TURN) {
            this.maverick = false;
            double height = this.maverickHigh - this.maverickLow;
            if (height >= MAVERICK_MIN_HEIGHT) {
                out.add(new Trick(TrickType.MAVERICK_LOOP, s.time, height, this.maverickNearest, this.maverickStart));
            }
        }
    }

    private void orbit(TrickSample s, double dt, List<Trick> out) {
        double hs = s.horizontalSpeed();
        boolean ok = false;
        if (hs >= ORBIT_MIN_SPEED && this.upY >= ORBIT_MIN_UP && dt > 0.0 && !this.contact) {
            double h = Math.hypot(this.fwdX, this.fwdZ);
            if (h > 0.3) {
                double cos = (this.fwdX * s.vx + this.fwdZ * s.vz) / (h * hs);
                double rate = Math.toDegrees(this.noseTurn) / dt;
                double ratio = this.noseTurn != 0.0 ? this.pathTurn / this.noseTurn : 0.0;
                ok = Math.abs(cos) <= ORBIT_SIDEWAYS_COS && Math.abs(rate) >= ORBIT_MIN_RATE
                        && ratio >= ORBIT_MIN_RATIO && ratio <= ORBIT_MAX_RATIO;
            }
        }
        if (!ok) {
            this.orbitGrace += dt;
            if (this.orbitGrace > ORBIT_GRACE || this.contact) {
                this.orbitTurn = 0.0;
                this.orbitSign = 0.0;
                this.orbitNearest = Double.POSITIVE_INFINITY;
            }
            return;
        }
        double sign = Math.signum(this.noseTurn);
        if (sign != this.orbitSign) {
            this.orbitSign = sign;
            this.orbitTurn = 0.0;
            this.orbitStart = s.time;
            this.orbitNearest = Double.POSITIVE_INFINITY;
        }
        this.orbitGrace = 0.0;
        this.orbitTurn += Math.toDegrees(this.noseTurn);
        this.orbitNearest = Math.min(this.orbitNearest, this.nearest);
        if (Math.abs(this.orbitTurn) >= ORBIT_TURN) {
            out.add(new Trick(TrickType.ORBIT, s.time, 1.0, this.orbitNearest, this.orbitStart));
            this.orbitTurn -= ORBIT_TURN * this.orbitSign;
            this.orbitStart = s.time;
            this.orbitNearest = Double.POSITIVE_INFINITY;
        }
    }

    private void knifeEdge(TrickSample s, double dt, List<Trick> out) {
        double hs = s.horizontalSpeed();
        double h = Math.hypot(this.fwdX, this.fwdZ);
        boolean ok = false;
        boolean reverse = false;
        if (Math.abs(this.upY) <= KNIFE_MAX_UP && Math.abs(this.fwdY) <= KNIFE_MAX_NOSE && s.speed() >= KNIFE_MIN_SPEED
                && hs >= ORBIT_MIN_SPEED && h > 0.3 && this.still() && Double.isFinite(this.pathHeading)) {
            double align = (this.fwdX * s.vx + this.fwdZ * s.vz) / (h * hs);
            ok = Math.abs(align) >= KNIFE_ALIGN;
            reverse = align < 0.0;
        }
        if (ok && this.inKnife && reverse != this.knifeReverse) {
            ok = false;
        }
        if (ok && this.knifeBlocked) {
            return;
        }
        this.knifeBlocked = false;
        if (ok) {
            if (!this.inKnife) {
                this.inKnife = true;
                this.knifeReverse = reverse;
                this.knifeWall = 0.0;
                this.knifeStart = s.time;
                this.knifeHeading = this.pathHeading;
                this.knifeTurn = 0.0;
                this.knifeNearest = this.nearest;
                this.knifeContact = false;
            }
            if (nearest(s, true) <= WALL_MAX_CLEARANCE) {
                this.knifeWall += dt;
            }
            this.knifeMiss = 0.0;
            this.knifeContact |= this.contact;
            this.knifeNearest = Math.min(this.knifeNearest, this.nearest);
            this.knifeTurn = Math.abs(Math.toDegrees(ManeuverClassifier.wrap(this.pathHeading - this.knifeHeading)));
            if (this.knifeTurn > KNIFE_MAX_TURN) {
                this.inKnife = false;
                this.knifeBlocked = true;
            }
            return;
        }
        if (!this.inKnife) {
            return;
        }
        this.knifeContact |= this.contact;
        this.knifeMiss += dt;
        if (this.knifeMiss < KNIFE_EXIT) {
            return;
        }
        this.inKnife = false;
        double seconds = s.time - this.knifeMiss - this.knifeStart;
        if (!this.knifeContact && seconds >= KNIFE_MIN_SECONDS && this.knifeTurn <= KNIFE_MAX_TURN_RATE * seconds) {
            TrickType type = this.knifeReverse ? TrickType.REVERSE_KNIFE_EDGE
                    : this.knifeWall >= WALL_FRACTION * seconds ? TrickType.WALL_RIDE : TrickType.KNIFE_EDGE;
            out.add(new Trick(type, s.time - this.knifeMiss, seconds, this.knifeNearest, this.knifeStart));
        }
    }

    private void gap(TrickSample s, double dt, List<Trick> out) {
        double width = Double.POSITIVE_INFINITY;
        if (s.clearanceKnown && s.speed() >= GAP_MIN_SPEED) {
            width = Math.min(pair(s.clearance[0], s.clearance[1]), pair(s.clearance[2], s.clearance[3]));
        }
        if (Double.isFinite(width)) {
            if (!this.inGap) {
                this.inGap = true;
                this.gapStart = s.time;
                this.gapWidth = width;
                this.gapContact = false;
            }
            this.gapWidth = Math.min(this.gapWidth, width);
            this.gapMiss = 0.0;
            this.gapContact |= this.contact;
            if (this.inProximity) {
                this.proxHadGap = true;
            }
            return;
        }
        if (!this.inGap) {
            return;
        }
        this.gapContact |= this.contact;
        this.gapMiss += dt;
        if (this.gapMiss < GAP_EXIT) {
            return;
        }
        this.inGap = false;
        double seconds = s.time - this.gapMiss - this.gapStart;
        if (!this.gapContact && seconds <= GAP_MAX_SECONDS) {
            out.add(new Trick(TrickType.GAP, s.time, this.gapWidth, this.gapWidth * 0.5, this.gapStart));
        }
    }

    private static double pair(double a, double b) {
        if (a <= GAP_MAX_SIDE && b <= GAP_MAX_SIDE && a + b <= GAP_MAX_WIDTH) {
            return a + b;
        }
        return Double.POSITIVE_INFINITY;
    }

    private void proximity(TrickSample s, double dt, List<Trick> out) {
        double near = nearest(s, true);
        boolean now = s.clearanceKnown && s.speed() >= PROX_MIN_SPEED && near <= PROX_MAX_CLEARANCE;
        if (now) {
            if (!this.inProximity) {
                this.inProximity = true;
                this.proxStart = s.time;
                this.proxNearest = near;
                this.proxContact = false;
                this.proxHadGap = this.inGap;
            }
            this.proxNearest = Math.min(this.proxNearest, near);
            this.proxMiss = 0.0;
            this.proxContact |= this.contact;
            if (this.inKnife && !this.knifeReverse) {
                this.proxHadGap = true;
            }
            return;
        }
        if (!this.inProximity) {
            return;
        }
        this.proxContact |= this.contact;
        this.proxMiss += dt;
        if (this.proxMiss < PROX_EXIT) {
            return;
        }
        this.inProximity = false;
        double seconds = s.time - this.proxMiss - this.proxStart;
        boolean gapOnly = this.proxHadGap && seconds < PROX_WITH_GAP_SECONDS;
        if (!this.proxContact && seconds >= PROX_MIN_SECONDS && !gapOnly) {
            out.add(new Trick(TrickType.PROXIMITY, s.time, seconds, this.proxNearest, this.proxStart));
        }
    }

    static double nearest(TrickSample s, boolean ignoreDown) {
        if (!s.clearanceKnown) {
            return Double.POSITIVE_INFINITY;
        }
        double best = Double.POSITIVE_INFINITY;
        for (int i = 0; i < TrickSample.PROBES; i++) {
            if (ignoreDown && s.clearanceDirY[i] < PROX_DOWN_LIMIT) {
                continue;
            }
            best = Math.min(best, s.clearance[i]);
        }
        return best;
    }

    private static double sq(double v) {
        return v * v;
    }
}
