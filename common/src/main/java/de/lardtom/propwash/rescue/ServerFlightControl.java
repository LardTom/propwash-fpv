package de.lardtom.propwash.rescue;

import de.lardtom.propwash.sim.AirframeSpec;
import de.lardtom.propwash.sim.Axis;
import de.lardtom.propwash.sim.BuildAnalysis;
import de.lardtom.propwash.sim.DroneSim;
import de.lardtom.propwash.sim.FlightControllerConfig;
import de.lardtom.propwash.sim.PidProfile;
import de.lardtom.propwash.sim.RateProfile;
import de.lardtom.propwash.sim.SimTuning;
import de.lardtom.propwash.sim.StickMath;
import de.lardtom.propwash.sim.Telemetry;
import de.lardtom.propwash.sim.ThrottleCurve;
import org.joml.Quaterniond;
import org.joml.Vector3d;

final class ServerFlightControl {
    static final double G = 9.81;
    static final double MAX_TILT_DEG = 35.0;
    static final double BRAKE_ACCEL = 2.5;
    static final double KV = 1.6;
    static final double KI_H = 0.8;
    static final double MAX_I_H = 1.5;
    static final double KP_POS = 0.9;
    static final double KP_ALT = 1.2;
    static final double KV_Z = 2.5;
    static final double KI_Z = 1.2;
    static final double MAX_I_Z = 3.0;
    static final double YAW_GAIN = 2.5;
    static final double MAX_YAW_RATE_DEG = 180.0;
    static final double MAX_DESCENT = 3.0;
    static final double INTEGRATE_BAND = 1.0;

    private final Vector3d velocity = new Vector3d();
    private final Vector3d position = new Vector3d();
    private final Quaterniond orientation = new Quaterniond();
    private AirframeSpec params;
    private double gravity = G;
    private double thrustToWeight = 1.5;
    private double hoverFallback = 0.45;
    private double dragPerMass;
    private double climbRate = 2.0;
    private double maxTilt = Math.toRadians(25.0);
    private PidProfile pid = new PidProfile();
    private RateProfile rates = new RateProfile();
    private double hoverStick = 0.45;
    private double integralX;
    private double integralZ;
    private double integralY;
    double x;
    double bottom;
    double z;
    double comY;
    double vx;
    double vy;
    double vz;
    double heading;
    boolean grounded;
    private double roll;
    private double pitch;
    private double yaw;
    private double throttle;

    void reset() {
        this.integralX = 0.0;
        this.integralZ = 0.0;
        this.integralY = 0.0;
    }

    void read(DroneSim sim, double x, double bottom, double z) {
        AirframeSpec p = sim.params();
        double g = sim.gravity();
        if (p != this.params || g != this.gravity) {
            this.params = p;
            this.gravity = g;
            BuildAnalysis analysis = BuildAnalysis.of(p.build);
            double tw = analysis.thrustToWeight() * (G / SimTuning.referenceGravity(g));
            this.thrustToWeight = Double.isFinite(tw) && tw > 1.05 ? tw : 1.5;
            double hover = analysis.hoverThrottlePercent() / 100.0;
            this.hoverFallback = Double.isFinite(hover) && hover > 0.02 ? hover : 0.45;
            double area = 0.5 * (p.areaZ + p.areaY);
            this.dragPerMass = p.massKg > 0.0 ? 0.5 * 1.225 * p.dragCoefficient * area / p.massKg : 0.0;
            this.climbRate = Math.max(1.5, Math.min(4.0, 1.0 + 0.6 * this.thrustToWeight));
        }
        FlightControllerConfig config = sim.config();
        this.pid = config.pid;
        this.rates = config.rates;
        double tiltByThrust = Math.acos(Math.min(1.0, 1.25 / this.thrustToWeight));
        double tiltByAngleLimit = Math.toRadians(Math.max(5.0, this.pid.angleLimitDeg - 3.0));
        double tiltForAccel = Math.atan(Math.tan(Math.toRadians(MAX_TILT_DEG)) * G / SimTuning.referenceGravity(this.gravity));
        this.maxTilt = Math.max(Math.toRadians(8.0), Math.min(tiltForAccel, Math.min(tiltByThrust, tiltByAngleLimit)));
        sim.getVelocity(this.velocity);
        sim.getAttitude(this.orientation);
        sim.getPosition(this.position);
        Telemetry tm = sim.telemetry();
        this.x = x;
        this.bottom = bottom;
        this.z = z;
        this.comY = finite(this.position.y);
        this.vx = finite(this.velocity.x);
        this.vy = finite(this.velocity.y);
        this.vz = finite(this.velocity.z);
        this.heading = wrap(2.0 * Math.atan2(this.orientation.y, this.orientation.w));
        this.grounded = tm.grounded;
        this.hoverStick = tm.hoverThrottleStick > 0.02 && tm.hoverThrottleStick < 1.0 ? tm.hoverThrottleStick : this.hoverFallback;
    }

    double climbRate() {
        return this.climbRate;
    }

    double hoverStick() {
        return this.hoverStick;
    }

    double footOffset() {
        return this.params == null ? 0.0 : this.params.hitboxFootOffset;
    }

    double positionSpeed(double ex, double ez, double maxSpeed, double[] out) {
        double e = Math.sqrt(ex * ex + ez * ez);
        if (e < 1e-6) {
            out[0] = 0.0;
            out[1] = 0.0;
            return 0.0;
        }
        double v = Math.min(maxSpeed, Math.min(KP_POS * e, Math.sqrt(2.0 * BRAKE_ACCEL * e)));
        out[0] = ex / e * v;
        out[1] = ez / e * v;
        return v;
    }

    double climbToward(double error, double maxClimb) {
        double up = Math.max(0.3, Math.min(maxClimb, this.climbRate * 2.0));
        return Math.max(-Math.min(MAX_DESCENT, up), Math.min(up, KP_ALT * error));
    }

    void control(double dt, double vdx, double vdz, double vyd) {
        double evx = vdx - this.vx;
        double evz = vdz - this.vz;
        double desiredSpeed = Math.sqrt(vdx * vdx + vdz * vdz);
        double ax = KV * evx + this.integralX + this.dragPerMass * desiredSpeed * vdx;
        double az = KV * evz + this.integralZ + this.dragPerMass * desiredSpeed * vdz;
        double g = SimTuning.referenceGravity(this.gravity);
        double maxHorizontal = g * Math.tan(this.maxTilt);
        double ah = Math.sqrt(ax * ax + az * az);
        if (ah > maxHorizontal) {
            ax *= maxHorizontal / ah;
            az *= maxHorizontal / ah;
        } else if (dt > 0.0) {
            this.integralX = clamp(this.integralX + KI_H * evx * dt, -MAX_I_H, MAX_I_H);
            this.integralZ = clamp(this.integralZ + KI_H * evz * dt, -MAX_I_H, MAX_I_H);
        }
        boolean vertical = Double.isFinite(vyd);
        double ty = g;
        if (vertical) {
            double evy = vyd - this.vy;
            ty = g + KV_Z * evy + this.integralY;
            double minTy = 0.3 * g;
            double maxTy = 0.9 * this.thrustToWeight * g * Math.cos(this.maxTilt);
            boolean saturated = false;
            if (ty < minTy) {
                ty = minTy;
                saturated = true;
            } else if (ty > maxTy) {
                ty = maxTy;
                saturated = true;
            }
            if (!saturated && dt > 0.0 && Math.abs(evy) < INTEGRATE_BAND) {
                this.integralY = clamp(this.integralY + KI_Z * evy * dt, -MAX_I_Z, MAX_I_Z);
            }
        }
        double th = Math.sqrt(ax * ax + az * az);
        double maxTh = ty * Math.tan(this.maxTilt);
        if (th > maxTh && th > 1e-9) {
            ax *= maxTh / th;
            az *= maxTh / th;
        }
        double magnitude = Math.sqrt(ax * ax + ty * ty + az * az);
        if (vertical) {
            double ratio = Math.min(magnitude / g, 0.95 * this.thrustToWeight);
            this.throttle = this.stickForThrustRatio(ratio);
        } else {
            this.throttle = Double.NaN;
        }
        double sin = Math.sin(this.heading);
        double cos = Math.cos(this.heading);
        double left = (ax * cos - az * sin) / magnitude;
        double forward = (ax * sin + az * cos) / magnitude;
        double up = ty / magnitude;
        double rollDeg = Math.toDegrees(Math.asin(clamp(-left, -1.0, 1.0)));
        double pitchDeg = Math.toDegrees(Math.atan2(forward, up));
        this.roll = StickMath.angleToStick(rollDeg, this.pid);
        this.pitch = StickMath.angleToStick(pitchDeg, this.pid);
    }

    double yawForRate(double compassDps) {
        double rate = clamp(compassDps, -MAX_YAW_RATE_DEG * 4.0, MAX_YAW_RATE_DEG * 4.0);
        this.yaw = StickMath.rateToStick(rate, this.rates, Axis.YAW);
        return this.yaw;
    }

    double yawForHeading(double compassDeg) {
        double error = wrap180(compassDeg - this.compass());
        return this.yawForRate(clamp(YAW_GAIN * error, -MAX_YAW_RATE_DEG, MAX_YAW_RATE_DEG));
    }

    double compass() {
        return compass(Math.toDegrees(this.heading));
    }

    double roll() {
        return this.roll;
    }

    double pitch() {
        return this.pitch;
    }

    double throttle() {
        return this.throttle;
    }

    private double stickForThrustRatio(double ratio) {
        double idle = this.pid.motorIdlePercent / 100.0;
        double span = (1.0 - idle) * this.pid.motorOutputLimitPercent / 100.0;
        if (span < 1e-6) {
            return this.hoverStick;
        }
        double hoverDuty = idle + span * ThrottleCurve.apply(this.pid, this.hoverStick);
        double duty = hoverDuty * Math.sqrt(Math.max(0.0, ratio));
        return ThrottleCurve.inverse(this.pid, clamp((duty - idle) / span, 0.0, 1.0));
    }

    static double compass(double simHeadingDeg) {
        double d = (180.0 - simHeadingDeg) % 360.0;
        return d < 0.0 ? d + 360.0 : d;
    }

    static double wrap180(double degrees) {
        double d = degrees % 360.0;
        if (d > 180.0) {
            d -= 360.0;
        } else if (d < -180.0) {
            d += 360.0;
        }
        return d;
    }

    static double clamp(double v, double min, double max) {
        return Math.max(min, Math.min(max, v));
    }

    static double finite(double v) {
        return Double.isFinite(v) ? v : 0.0;
    }

    static double wrap(double angle) {
        double a = angle % (2.0 * Math.PI);
        if (a > Math.PI) {
            a -= 2.0 * Math.PI;
        } else if (a <= -Math.PI) {
            a += 2.0 * Math.PI;
        }
        return a;
    }
}
