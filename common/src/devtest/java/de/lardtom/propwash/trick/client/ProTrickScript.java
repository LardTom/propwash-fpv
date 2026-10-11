package de.lardtom.propwash.trick.client;

import de.lardtom.propwash.sim.Axis;
import de.lardtom.propwash.sim.RateProfile;
import de.lardtom.propwash.sim.StabMode;
import de.lardtom.propwash.sim.StickMath;
import org.joml.Quaterniond;

final class ProTrickScript {
    static final double RATE = 650.0;
    static final double PULL_RATE = 250.0;
    static final double HALF_RATE = 450.0;
    static final double CLIMB = 22.0;
    static final double DIVE_DROP = 13.0;
    static final double DIVE_SINK = -9.0;
    static final double LEAD = 1.2;
    static final double EJECT_RATE = 450.0;

    enum Stage {
        ARM,
        CLIMB,
        COAST,
        PUSH,
        DIVE,
        FLIP,
        RECOVER,
        SETTLE,
        EJECT_TILT,
        EJECT_HOLD,
        EJECT_SNAP,
        GLIDE,
        SPLIT_ROLL,
        SPLIT_PULL,
        PAUSE,
        JUICY_FLIP,
        JUICY_ROLL,
        LEVEL,
        HOLD
    }

    private final Quaterniond previous = new Quaterniond();
    private final Quaterniond delta = new Quaterniond();
    private final double secondsPerUpdate;
    private boolean hasPrevious;
    private Stage stage;
    private int since;
    private double startY;
    private double topY;
    private double turnPitch;
    private double turnRoll;
    private double upY = 1.0;
    private double fwdY;
    StabMode mode = StabMode.ANGLE;
    double roll;
    double pitch;
    double throttle;

    ProTrickScript(Stage start, double secondsPerUpdate) {
        this.stage = start;
        this.secondsPerUpdate = secondsPerUpdate;
    }

    Stage stage() {
        return this.stage;
    }

    void observe(double y, Quaterniond attitude) {
        if (this.hasPrevious) {
            this.previous.conjugate(this.delta).mul(attitude);
            if (this.delta.w < 0.0) {
                this.delta.set(-this.delta.x, -this.delta.y, -this.delta.z, -this.delta.w);
            }
            double vl = Math.sqrt(this.delta.x * this.delta.x + this.delta.y * this.delta.y + this.delta.z * this.delta.z);
            if (vl > 1.0E-12) {
                double k = Math.toDegrees(2.0 * Math.atan2(vl, this.delta.w) / vl);
                this.turnPitch += this.delta.x * k;
                this.turnRoll += this.delta.z * k;
            }
        }
        this.previous.set(attitude);
        this.hasPrevious = true;
        this.upY = 1.0 - 2.0 * (attitude.x * attitude.x + attitude.z * attitude.z);
        this.fwdY = 2.0 * (attitude.y * attitude.z - attitude.w * attitude.x);
        if (this.stage == Stage.ARM) {
            this.startY = y;
        }
    }

    private void next(Stage next) {
        this.stage = next;
        this.since = 0;
        this.turnPitch = 0.0;
        this.turnRoll = 0.0;
    }

    private int ticks(double seconds) {
        return (int) Math.ceil(seconds / this.secondsPerUpdate);
    }

    private double lead(double rate) {
        return rate * this.secondsPerUpdate * LEAD;
    }

    void command(double y, double vy, boolean armed, RateProfile rates, double hoverStick) {
        this.since++;
        double pitchFast = StickMath.rateToStick(RATE, rates, Axis.PITCH);
        double pitchEject = StickMath.rateToStick(EJECT_RATE, rates, Axis.PITCH);
        double fast = this.lead(RATE);
        double pitchHalf = StickMath.rateToStick(HALF_RATE, rates, Axis.PITCH);
        double rollHalf = StickMath.rateToStick(HALF_RATE, rates, Axis.ROLL);
        double half = this.lead(HALF_RATE);
        double pitchPull = StickMath.rateToStick(PULL_RATE, rates, Axis.PITCH);
        double tilt = Math.toDegrees(Math.acos(Math.clamp(this.upY, -1.0, 1.0)));
        double hold = Math.clamp(hoverStick - 0.05 * vy, 0.05, 0.8);
        this.mode = StabMode.ACRO;
        this.roll = 0.0;
        this.pitch = 0.0;
        switch (this.stage) {
            case ARM -> {
                this.mode = StabMode.ANGLE;
                this.throttle = 0.0;
                if (armed && this.since >= this.ticks(0.15)) {
                    this.startY = y;
                    this.next(Stage.CLIMB);
                }
            }
            case CLIMB -> {
                this.mode = StabMode.ANGLE;
                this.throttle = 0.85;
                if (y >= this.startY + CLIMB) {
                    this.next(Stage.COAST);
                }
            }
            case COAST -> {
                this.mode = StabMode.ANGLE;
                this.throttle = 0.0;
                if (vy <= 0.3) {
                    this.topY = y;
                    this.next(Stage.PUSH);
                }
            }
            case PUSH -> {
                this.throttle = 0.05;
                this.pitch = pitchEject;
                if (Math.toDegrees(Math.asin(Math.clamp(-this.fwdY, -1.0, 1.0))) >= 72.0 - this.lead(EJECT_RATE)) {
                    this.next(Stage.DIVE);
                }
            }
            case DIVE -> {
                this.throttle = 0.05;
                if (this.topY - y >= DIVE_DROP && vy <= DIVE_SINK) {
                    this.next(Stage.FLIP);
                }
            }
            case FLIP -> {
                this.throttle = this.turnPitch >= 165.0 ? 1.0 : 0.05;
                this.pitch = pitchFast;
                if (this.turnPitch >= 280.0 - fast) {
                    this.next(Stage.RECOVER);
                }
            }
            case RECOVER -> {
                this.mode = StabMode.ANGLE;
                this.throttle = 1.0;
                if (vy >= 0.0) {
                    this.next(Stage.SETTLE);
                }
            }
            case SETTLE -> {
                this.mode = StabMode.ANGLE;
                this.throttle = hold;
                if (this.since >= this.ticks(1.5)) {
                    this.next(Stage.EJECT_TILT);
                }
            }
            case EJECT_TILT -> {
                this.throttle = 1.0;
                this.pitch = pitchEject;
                if (tilt >= 62.0 - this.lead(EJECT_RATE)) {
                    this.next(Stage.EJECT_HOLD);
                }
            }
            case EJECT_HOLD -> {
                this.throttle = 1.0;
                if (this.since >= this.ticks(0.2)) {
                    this.next(Stage.EJECT_SNAP);
                }
            }
            case EJECT_SNAP -> {
                this.throttle = 0.3;
                this.pitch = -pitchEject;
                if (tilt <= 8.0 + this.lead(EJECT_RATE) || this.turnPitch <= -60.0) {
                    this.next(Stage.GLIDE);
                }
            }
            case GLIDE -> {
                this.throttle = 0.3;
                if (this.since >= this.ticks(0.1)) {
                    this.next(Stage.SPLIT_ROLL);
                }
            }
            case SPLIT_ROLL -> {
                this.throttle = 0.2;
                this.roll = rollHalf;
                if (this.turnRoll >= 180.0 - half) {
                    this.next(Stage.SPLIT_PULL);
                }
            }
            case SPLIT_PULL -> {
                this.throttle = 0.9;
                this.pitch = -pitchPull;
                if (this.turnPitch <= -175.0 + this.lead(PULL_RATE)) {
                    this.next(Stage.PAUSE);
                }
            }
            case PAUSE -> {
                this.throttle = 0.35;
                if (this.since >= this.ticks(0.3)) {
                    this.next(Stage.JUICY_FLIP);
                }
            }
            case JUICY_FLIP -> {
                this.throttle = 0.6;
                this.pitch = pitchHalf;
                if (this.turnPitch >= 180.0 - half) {
                    this.next(Stage.JUICY_ROLL);
                }
            }
            case JUICY_ROLL -> {
                this.throttle = 0.3;
                this.roll = rollHalf;
                if (this.turnRoll >= 180.0 - half) {
                    this.next(Stage.LEVEL);
                }
            }
            case LEVEL -> {
                this.throttle = 0.4;
                if (this.since >= this.ticks(0.3)) {
                    this.next(Stage.HOLD);
                }
            }
            case HOLD -> {
                this.mode = StabMode.ANGLE;
                this.throttle = hold;
            }
        }
    }
}
