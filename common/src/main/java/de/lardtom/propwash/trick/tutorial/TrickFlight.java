package de.lardtom.propwash.trick.tutorial;

import de.lardtom.propwash.build.DroneBuild;
import de.lardtom.propwash.sim.Axis;
import de.lardtom.propwash.sim.DroneSim;
import de.lardtom.propwash.sim.FlightControllerConfig;
import de.lardtom.propwash.sim.PilotInput;
import de.lardtom.propwash.sim.RateProfile;
import de.lardtom.propwash.sim.SimEnvironment;
import de.lardtom.propwash.sim.SimTuning;
import de.lardtom.propwash.sim.StabMode;
import de.lardtom.propwash.sim.StepResult;
import de.lardtom.propwash.sim.StickMath;
import de.lardtom.propwash.sim.Telemetry;
import de.lardtom.propwash.trick.ProbeAxes;
import de.lardtom.propwash.trick.TrickSample;
import org.joml.Quaterniond;
import org.joml.Vector3d;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;

public final class TrickFlight {
    public static final double FPS = 120.0;
    public static final double ALTITUDE = 120.0;
    public static final long SEED = 42L;
    public static final double PROBE_RANGE = 3.0;
    private static final double LEVEL_GAIN = 6.0;
    private static final double MAX_CORRECTION_DPS = 500.0;
    private static final double GRAVITY = 9.81;
    private static final double ORBIT_RAMP_SECONDS = 1.2;

    public record Frame(double time, int stage, double x, double y, double z, double qx, double qy, double qz, double qw, double roll,
                        double pitch, double yaw, double throttle, double motor) {
    }

    private final List<Frame> frames = new ArrayList<>();
    private final List<String> stages = new ArrayList<>();
    private final List<double[]> obstacles = new ArrayList<>();
    private final boolean mode3d;
    private int crashes;

    private TrickFlight(boolean mode3d) {
        this.mode3d = mode3d;
    }

    public static TrickFlight fly(TrickChoreography choreography, @Nullable Consumer<TrickSample> sink) {
        TrickFlight flight = new TrickFlight(choreography.mode3d());
        Pilot pilot = new Pilot(flight, sink, choreography.mode3d());
        for (TrickMove move : choreography.moves()) {
            if (!move.stage().isEmpty() && (flight.stages.isEmpty() || !flight.stages.getLast().equals(move.stage()))) {
                flight.stages.add(move.stage());
            }
            pilot.stage = Math.max(0, flight.stages.size() - 1);
            pilot.perform(move);
        }
        flight.crashes = pilot.sim.telemetry().crashCount;
        return flight;
    }

    public List<Frame> frames() {
        return Collections.unmodifiableList(this.frames);
    }

    public List<String> stages() {
        return Collections.unmodifiableList(this.stages);
    }

    public List<double[]> obstacles() {
        return Collections.unmodifiableList(this.obstacles);
    }

    public boolean mode3d() {
        return this.mode3d;
    }

    public int crashes() {
        return this.crashes;
    }

    public double duration() {
        return this.frames.isEmpty() ? 0.0 : this.frames.getLast().time();
    }

    public double stageStart(int stage) {
        for (Frame frame : this.frames) {
            if (frame.stage() >= stage) {
                return frame.time();
            }
        }
        return this.duration();
    }

    public double stageEnd(int stage) {
        return stage >= this.stages.size() - 1 ? this.duration() : this.stageStart(stage + 1);
    }

    static double cast(List<double[]> boxes, double x, double y, double z, double dx, double dy, double dz, double range) {
        double best = Double.POSITIVE_INFINITY;
        double[] o = {x, y, z};
        double[] d = {dx, dy, dz};
        for (double[] b : boxes) {
            double t0 = 0.0;
            double t1 = range;
            boolean hit = true;
            for (int a = 0; a < 3 && hit; a++) {
                double min = b[a];
                double max = b[a + 3];
                if (Math.abs(d[a]) < 1.0E-12) {
                    if (o[a] < min || o[a] > max) {
                        hit = false;
                    }
                    continue;
                }
                double ta = (min - o[a]) / d[a];
                double tb = (max - o[a]) / d[a];
                t0 = Math.max(t0, Math.min(ta, tb));
                t1 = Math.min(t1, Math.max(ta, tb));
                if (t0 > t1) {
                    hit = false;
                }
            }
            if (hit) {
                best = Math.min(best, t0);
            }
        }
        return best;
    }

    private static final class Pilot {
        final TrickFlight flight;
        final @Nullable Consumer<TrickSample> sink;
        final DroneSim sim;
        final PilotInput input = new PilotInput();
        final SimEnvironment env = new SimEnvironment();
        final StepResult result = new StepResult();
        final TrickSample sample = new TrickSample();
        final Vector3d position = new Vector3d();
        final Vector3d velocity = new Vector3d();
        final Quaterniond attitude = new Quaterniond();
        final Quaterniond inverse = new Quaterniond();
        final Quaterniond before = new Quaterniond();
        final Quaterniond step = new Quaterniond();
        final double[] axes = new double[TrickSample.PROBES * 3];
        final boolean threeD;
        final double frameSeconds = 1.0 / FPS;
        double time;
        int stage;

        Pilot(TrickFlight flight, @Nullable Consumer<TrickSample> sink, boolean threeD) {
            this.flight = flight;
            this.sink = sink;
            this.threeD = threeD;
            SimTuning tuning = new SimTuning();
            tuning.gyroNoiseDps = 0.0;
            tuning.gyroVibrationDps = 0.0;
            tuning.propWash = false;
            tuning.wakeField = false;
            this.sim = new DroneSim(DroneBuild.FREESTYLE, new FlightControllerConfig(), tuning, SEED);
            this.sim.reset(0.0, ALTITUDE, 0.0, 0.0);
            this.sim.setBatteryCharge(1.0);
            this.input.mode = StabMode.ACRO;
            this.input.throttle = 0.0;
            this.input.armSwitch = false;
            this.sim.step(0L, this.input, this.env, this.result);
            this.input.armSwitch = true;
            this.sim.step(0L, this.input, this.env, this.result);
            this.input.mode3d = threeD;
        }

        void perform(TrickMove move) {
            switch (move) {
                case TrickMove.Level level -> this.fly(0.0, 0.0, 0.0, Double.NaN, level.seconds());
                case TrickMove.Fly fly -> this.fly(fly.tilt(), fly.bank(), fly.turn(), fly.throttle(), fly.seconds());
                case TrickMove.Rotate rotate -> this.rotate(rotate.roll(), rotate.pitch(), rotate.yaw(), this.throttleOrHover(rotate.throttle()),
                        rotate.degrees());
                case TrickMove.Hold hold -> this.hold(hold.roll(), hold.pitch(), hold.yaw(), this.throttleOrHover(hold.throttle()), hold.seconds());
                case TrickMove.InvertedHold hold -> this.invertedHold(hold.seconds(), hold.sink());
                case TrickMove.InvertedHover hover -> this.invertedHover(hover.seconds());
                case TrickMove.Maverick maverick -> this.maverickLoop(maverick.radius(), maverick.speed());
                case TrickMove.Orbit orbit -> this.orbit(orbit.radius(), orbit.speed(), orbit.turns(), orbit.side());
                case TrickMove.Obstacle box -> {
                    this.sim.getPosition(this.position);
                    this.flight.obstacles.add(new double[]{this.position.x + Math.min(box.x0(), box.x1()), this.position.y + Math.min(box.y0(), box.y1()),
                            this.position.z + Math.min(box.z0(), box.z1()), this.position.x + Math.max(box.x0(), box.x1()),
                            this.position.y + Math.max(box.y0(), box.y1()), this.position.z + Math.max(box.z0(), box.z1())});
                }
            }
        }

        double hover() {
            double h = this.sim.telemetry().hoverThrottleStick;
            return Double.isFinite(h) && h > 0.05 ? h : 0.35;
        }

        double throttleOrHover(double throttle) {
            return Double.isNaN(throttle) ? this.hover() : throttle;
        }

        void frame() {
            this.sim.step(Math.round(1.0E9 * this.frameSeconds), this.input, this.env, this.result);
            this.time += this.frameSeconds;
            Telemetry t = this.sim.telemetry();
            this.sim.getPosition(this.position);
            this.sim.getVelocity(this.velocity);
            this.sim.getAttitude(this.attitude);
            TrickSample s = this.sample;
            s.time = t.time;
            s.x = this.position.x;
            s.y = this.position.y;
            s.z = this.position.z;
            s.vx = this.velocity.x;
            s.vy = this.velocity.y;
            s.vz = this.velocity.z;
            s.qx = this.attitude.x;
            s.qy = this.attitude.y;
            s.qz = this.attitude.z;
            s.qw = this.attitude.w;
            s.armed = t.armed;
            s.crashCount = t.crashCount;
            s.impactCount = t.impactCount;
            s.grounded = t.grounded;
            s.throttle = t.throttleOut;
            s.mode3d = t.mode3d;
            s.assisted = false;
            this.probe(s);
            if (this.sink != null) {
                this.sink.accept(s);
            }
            this.flight.frames.add(new Frame(this.time, this.stage, s.x, s.y, s.z, s.qx, s.qy, s.qz, s.qw, this.input.roll, this.input.pitch,
                    this.input.yaw, this.input.throttle, t.throttleOut));
        }

        private void probe(TrickSample s) {
            List<double[]> boxes = this.flight.obstacles;
            if (boxes.isEmpty()) {
                s.clearNearby();
                return;
            }
            ProbeAxes.compute(s, this.axes);
            s.clearanceKnown = true;
            for (int i = 0; i < TrickSample.PROBES; i++) {
                double dx = this.axes[i * 3];
                double dy = this.axes[i * 3 + 1];
                double dz = this.axes[i * 3 + 2];
                s.clearanceDirY[i] = dy;
                s.clearance[i] = cast(boxes, s.x, s.y, s.z, dx, dy, dz, PROBE_RANGE);
            }
        }

        void rates(double rollDps, double pitchDps, double yawDps, double throttle) {
            RateProfile rates = this.sim.config().rates;
            this.input.roll = StickMath.rateToStick(rollDps, rates, Axis.ROLL);
            this.input.pitch = StickMath.rateToStick(pitchDps, rates, Axis.PITCH);
            this.input.yaw = StickMath.rateToStick(yawDps, rates, Axis.YAW);
            this.input.throttle = this.threeD ? stick3d(throttle) : Math.clamp(throttle, 0.0, 1.0);
        }

        static double stick3d(double signed) {
            double band = FlightControllerConfig.DEADBAND_3D;
            double magnitude = Math.clamp(Math.abs(signed), 0.0, 1.0);
            if (magnitude < 1.0E-9) {
                return 0.5;
            }
            double offset = band + magnitude * (0.5 - band);
            return signed > 0.0 ? 0.5 + offset : 0.5 - offset;
        }

        void rotate(double rollDps, double pitchDps, double yawDps, double throttle, double degrees) {
            double norm = Math.sqrt(rollDps * rollDps + pitchDps * pitchDps + yawDps * yawDps);
            if (norm < 1.0E-9) {
                return;
            }
            double turned = 0.0;
            int guard = (int) Math.round(10.0 * FPS);
            while (turned < degrees && guard-- > 0) {
                this.sim.getAttitude(this.before);
                this.rates(rollDps, pitchDps, yawDps, throttle);
                this.frame();
                this.sim.getAttitude(this.attitude);
                this.before.conjugate(this.step).mul(this.attitude);
                if (this.step.w < 0.0) {
                    this.step.set(-this.step.x, -this.step.y, -this.step.z, -this.step.w);
                }
                double vl = Math.sqrt(this.step.x * this.step.x + this.step.y * this.step.y + this.step.z * this.step.z);
                if (vl > 1.0E-12) {
                    double k = 2.0 * Math.atan2(vl, this.step.w) / vl;
                    double pitch = Math.toDegrees(this.step.x * k);
                    double roll = Math.toDegrees(this.step.z * k);
                    double yaw = -Math.toDegrees(this.step.y * k);
                    turned += (pitch * pitchDps + roll * rollDps + yaw * yawDps) / norm;
                }
            }
        }

        void hold(double rollDps, double pitchDps, double yawDps, double throttle, double seconds) {
            int frames = (int) Math.round(seconds * FPS);
            for (int i = 0; i < frames; i++) {
                this.rates(rollDps, pitchDps, yawDps, throttle);
                this.frame();
            }
        }

        void fly(double forwardTiltDeg, double bankDeg, double turnDps, double throttle, double seconds) {
            int frames = (int) Math.round(seconds * FPS);
            for (int i = 0; i < frames; i++) {
                this.steer(forwardTiltDeg, bankDeg, turnDps, throttle);
                this.frame();
            }
        }

        void invertedHold(double seconds, double sinkTarget) {
            int frames = (int) Math.round(seconds * FPS);
            double base = -this.hover() * 1.35;
            double integral = 0.0;
            for (int i = 0; i < frames; i++) {
                this.sim.getVelocity(this.velocity);
                double error = sinkTarget - this.velocity.y;
                integral = Math.clamp(integral + error * this.frameSeconds * 0.15, -0.4, 0.4);
                double thr = Math.clamp(base - 0.06 * error - integral, -1.0, 0.0);
                this.rates(0.0, 0.0, 0.0, thr);
                this.frame();
            }
        }

        void invertedHover(double seconds) {
            double[] heading = this.heading();
            int frames = (int) Math.round(seconds * FPS);
            this.sim.getPosition(this.position);
            double hold = this.position.y;
            for (int i = 0; i < frames; i++) {
                this.sim.getVelocity(this.velocity);
                this.sim.getPosition(this.position);
                Vector3d thrust = new Vector3d(-1.5 * this.velocity.x, GRAVITY + 4.0 * (hold - this.position.y) - 3.0 * this.velocity.y,
                        -1.5 * this.velocity.z);
                double g = thrust.length();
                this.steerUp(thrust.normalize().negate(), heading[0], heading[1], 0.0, -this.hover() * 1.35 * g / GRAVITY);
                this.frame();
            }
        }

        void maverickLoop(double radius, double speed) {
            double[] heading = this.heading();
            double fx = heading[0];
            double fz = heading[1];
            this.sim.getPosition(this.position);
            double cx = this.position.x;
            double cy = this.position.y + radius;
            double cz = this.position.z;
            double omega = speed / radius;
            double angle = 0.0;
            while (angle < 2.0 * Math.PI) {
                this.sim.getPosition(this.position);
                this.sim.getVelocity(this.velocity);
                double s = Math.sin(angle);
                double c = Math.cos(angle);
                double px = cx + radius * s * fx;
                double py = cy - radius * c;
                double pz = cz + radius * s * fz;
                double vx = speed * c * fx;
                double vy = speed * s;
                double vz = speed * c * fz;
                double ax = -omega * speed * s * fx + 4.0 * (px - this.position.x) + 3.0 * (vx - this.velocity.x);
                double ay = omega * speed * c + 4.0 * (py - this.position.y) + 3.0 * (vy - this.velocity.y);
                double az = -omega * speed * s * fz + 4.0 * (pz - this.position.z) + 3.0 * (vz - this.velocity.z);
                Vector3d thrust = new Vector3d(ax, ay + GRAVITY, az);
                double g = thrust.length();
                this.steerUp(thrust.normalize(), fx, fz, 0.0, this.hover() * g / GRAVITY);
                this.frame();
                angle += omega * this.frameSeconds;
            }
        }

        void orbit(double radius, double speed, double turns, double side) {
            double[] heading = this.heading();
            double fx = heading[0];
            double fz = heading[1];
            this.sim.getPosition(this.position);
            double cx = this.position.x + radius * fx;
            double cy = this.position.y;
            double cz = this.position.z + radius * fz;
            double start = Math.atan2(-fx, -fz);
            double omega = side * speed / radius;
            double angle = 0.0;
            double elapsed = 0.0;
            while (Math.abs(angle) < turns * 2.0 * Math.PI) {
                double ramp = Math.min(1.0, elapsed / ORBIT_RAMP_SECONDS);
                double w = omega * ramp;
                double a = start + angle;
                double rx = Math.sin(a);
                double rz = Math.cos(a);
                double px = cx + radius * rx;
                double pz = cz + radius * rz;
                double vx = radius * w * rz;
                double vz = -radius * w * rx;
                double accel = radius * w * w;
                this.sim.getPosition(this.position);
                this.sim.getVelocity(this.velocity);
                double ax = -accel * rx + 4.0 * (px - this.position.x) + 3.0 * (vx - this.velocity.x);
                double ay = 4.0 * (cy - this.position.y) - 3.0 * this.velocity.y;
                double az = -accel * rz + 4.0 * (pz - this.position.z) + 3.0 * (vz - this.velocity.z);
                Vector3d thrust = new Vector3d(ax, ay + GRAVITY, az);
                double g = thrust.length();
                this.steerUp(thrust.normalize(), -rx, -rz, Math.toDegrees(w), this.hover() * g / GRAVITY);
                this.frame();
                angle += w * this.frameSeconds;
                elapsed += this.frameSeconds;
            }
        }

        private double[] heading() {
            this.sim.getAttitude(this.attitude);
            Vector3d nose = this.attitude.transform(new Vector3d(0.0, 0.0, 1.0));
            double h = Math.hypot(nose.x, nose.z);
            return new double[]{h > 1.0E-6 ? nose.x / h : 0.0, h > 1.0E-6 ? nose.z / h : 1.0};
        }

        void steerUp(Vector3d target, double headingX, double headingZ, double yawFeedDps, double throttle) {
            this.sim.getAttitude(this.attitude);
            Quaterniond q = this.attitude;
            Vector3d up = q.transform(new Vector3d(0.0, 1.0, 0.0));
            Vector3d nose = q.transform(new Vector3d(0.0, 0.0, 1.0));
            Vector3d axis = up.cross(target, new Vector3d());
            double sin = axis.length();
            double angle = Math.atan2(sin, up.dot(target));
            if (sin < 1.0E-6) {
                axis.set(1.0, 0.0, 0.0);
                angle = 0.0;
            } else {
                axis.div(sin);
            }
            double correction = Math.min(Math.toDegrees(angle) * LEVEL_GAIN, MAX_CORRECTION_DPS);
            double heading = Math.atan2(nose.x, nose.z);
            double want = Math.atan2(headingX, headingZ);
            double yawError = Math.toDegrees(Math.atan2(Math.sin(want - heading), Math.cos(want - heading)));
            Vector3d omega = new Vector3d(axis).mul(Math.toRadians(correction)).add(0.0, Math.toRadians(yawError * LEVEL_GAIN + yawFeedDps), 0.0);
            q.conjugate(this.inverse).transform(omega);
            this.rates(Math.toDegrees(omega.z), Math.toDegrees(omega.x), -Math.toDegrees(omega.y), throttle);
        }

        void steer(double forwardTiltDeg, double bankDeg, double turnDps, double throttle) {
            this.sim.getAttitude(this.attitude);
            Quaterniond q = this.attitude;
            Vector3d up = q.transform(new Vector3d(0.0, 1.0, 0.0));
            Vector3d nose = q.transform(new Vector3d(0.0, 0.0, 1.0));
            Vector3d left = q.transform(new Vector3d(1.0, 0.0, 0.0));
            double fx = nose.x;
            double fz = nose.z;
            double h = Math.hypot(fx, fz);
            if (h < 0.3) {
                fx = -up.x;
                fz = -up.z;
                h = Math.hypot(fx, fz);
            }
            if (h < 1.0E-6) {
                fx = 0.0;
                fz = 1.0;
                h = 1.0;
            }
            fx /= h;
            fz /= h;
            double rx = -fz;
            double rz = fx;
            double tf = Math.tan(Math.toRadians(forwardTiltDeg));
            double tb = Math.tan(Math.toRadians(bankDeg));
            Vector3d target = new Vector3d(tf * fx + tb * rx, 1.0, tf * fz + tb * rz).normalize();
            Vector3d axis = up.cross(target, new Vector3d());
            double sin = axis.length();
            double cos = up.dot(target);
            double angle = Math.atan2(sin, cos);
            if (sin < 1.0E-6) {
                axis.set(left);
                if (cos > 0.0) {
                    angle = 0.0;
                }
            } else {
                axis.div(sin);
            }
            double correction = Math.min(Math.toDegrees(angle) * LEVEL_GAIN, MAX_CORRECTION_DPS);
            Vector3d omega = new Vector3d(axis).mul(Math.toRadians(correction)).add(0.0, -Math.toRadians(turnDps), 0.0);
            q.conjugate(this.inverse).transform(omega);
            double pitch = Math.toDegrees(omega.x);
            double roll = Math.toDegrees(omega.z);
            double yaw = -Math.toDegrees(omega.y);
            double thr = throttle;
            if (!Double.isFinite(thr)) {
                this.sim.getVelocity(this.velocity);
                double tilt = Math.max(0.35, up.y);
                thr = this.hover() * Math.sqrt(1.0 / tilt) - 0.03 * this.velocity.y;
            }
            this.rates(roll, pitch, yaw, thr);
        }
    }
}
