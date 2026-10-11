package de.lardtom.propwash.trick;

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
import org.joml.Quaterniond;
import org.joml.Vector3d;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

final class SimPilot {
    static final double FPS = 120.0;
    static final double LEVEL_GAIN = 6.0;
    static final double MAX_CORRECTION_DPS = 500.0;

    final DroneSim sim;
    final PilotInput input = new PilotInput();
    final SimEnvironment env = new SimEnvironment();
    final StepResult result = new StepResult();
    final TrickSession session = new TrickSession();
    final TrickSample sample = new TrickSample();
    final List<Trick> detected = new ArrayList<>();
    final Vector3d position = new Vector3d();
    final Vector3d velocity = new Vector3d();
    final Quaterniond attitude = new Quaterniond();
    final Quaterniond inverse = new Quaterniond();
    final Vector3d work = new Vector3d();
    final double[] axes = new double[12];
    final List<double[]> boxes = new ArrayList<>();
    final List<String> trace = new ArrayList<>();
    final Quaterniond before = new Quaterniond();
    final Quaterniond step = new Quaterniond();
    Random jitter;
    double jitterDps;
    double stickLag;
    double lagRoll;
    double lagPitch;
    double lagYaw;
    Random frameJitter;
    double minFps;
    double maxFps;
    double frameSeconds = 1.0 / FPS;
    boolean wasManeuvering;
    double time;
    double fps = FPS;
    boolean threeD;

    SimPilot(DroneBuild build, double altitude) {
        SimTuning tuning = new SimTuning();
        tuning.gyroNoiseDps = 0.0;
        tuning.gyroVibrationDps = 0.0;
        tuning.propWash = false;
        tuning.wakeField = false;
        this.sim = new DroneSim(build, new FlightControllerConfig(), tuning, 42L);
        this.sim.reset(0.0, altitude, 0.0, 0.0);
        this.sim.setBatteryCharge(1.0);
        this.session.keepHistory(256);
        this.input.mode = StabMode.ACRO;
        this.input.throttle = 0.0;
        this.input.armSwitch = false;
        this.sim.step(0L, this.input, this.env, this.result);
        this.input.armSwitch = true;
        this.sim.step(0L, this.input, this.env, this.result);
    }

    static SimPilot freestyle() {
        return new SimPilot(DroneBuild.FREESTYLE, 120.0);
    }

    Telemetry telemetry() {
        return this.sim.telemetry();
    }

    double hover() {
        double h = this.sim.telemetry().hoverThrottleStick;
        return Double.isFinite(h) && h > 0.05 ? h : 0.35;
    }

    SimPilot box(double x0, double y0, double z0, double x1, double y1, double z1) {
        this.boxes.add(new double[]{Math.min(x0, x1), Math.min(y0, y1), Math.min(z0, z1), Math.max(x0, x1), Math.max(y0, y1), Math.max(z0, z1)});
        return this;
    }

    void frame() {
        double seconds = 1.0 / this.fps;
        if (this.frameJitter != null) {
            seconds = 1.0 / (this.minFps + this.frameJitter.nextDouble() * (this.maxFps - this.minFps));
        }
        this.frameSeconds = seconds;
        this.sim.step(Math.round(1.0E9 * seconds), this.input, this.env, this.result);
        this.time += seconds;
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
        int count = this.session.history().size();
        this.session.update(s);
        List<Trick> history = this.session.history();
        for (int i = count; i < history.size(); i++) {
            this.detected.add(history.get(i));
        }
        boolean maneuvering = this.session.detector().maneuvering();
        if (this.wasManeuvering && !maneuvering || count < history.size()) {
            List<Stroke> strokes = this.session.detector().lastStrokes();
            if (!strokes.isEmpty()) {
                this.trace.add(String.format(java.util.Locale.ROOT, "%.2fs %s -> %s", this.time, strokes,
                        history.subList(count, history.size()).stream().map(Trick::type).toList()));
            }
        }
        this.wasManeuvering = maneuvering;
    }

    String report() {
        return String.join("\n", this.trace);
    }

    SimPilot frameRate(double hz) {
        this.fps = hz;
        return this;
    }

    SimPilot unevenFrames(long seed, double minHz, double maxHz) {
        this.frameJitter = new Random(seed);
        this.minFps = minHz;
        this.maxFps = maxHz;
        return this;
    }

    SimPilot human(double lagSeconds) {
        this.stickLag = lagSeconds;
        return this;
    }

    SimPilot sloppy(long seed, double dps) {
        this.jitter = new Random(seed);
        this.jitterDps = dps;
        return this;
    }

    SimPilot clean() {
        this.jitter = null;
        this.jitterDps = 0.0;
        return this;
    }

    SimPilot maxRate(double dps) {
        RateProfile rates = this.sim.config().rates;
        rates.roll.maxRateDps = dps;
        rates.pitch.maxRateDps = dps;
        rates.yaw.maxRateDps = dps;
        return this;
    }

    SimPilot rotate(double rollDps, double pitchDps, double yawDps, double throttle, double degrees) {
        double norm = Math.sqrt(rollDps * rollDps + pitchDps * pitchDps + yawDps * yawDps);
        if (norm < 1.0E-9) {
            return this;
        }
        double turned = 0.0;
        int guard = (int) Math.round(10.0 * this.fps);
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
        return this;
    }

    private void probe(TrickSample s) {
        if (this.boxes.isEmpty()) {
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
            s.clearance[i] = TrickWorld.cast(this.boxes, s.x, s.y, s.z, dx, dy, dz, 3.0);
        }
    }

    void rates(double rollDps, double pitchDps, double yawDps, double throttle) {
        if (this.jitter != null) {
            rollDps += (this.jitter.nextDouble() * 2.0 - 1.0) * this.jitterDps;
            pitchDps += (this.jitter.nextDouble() * 2.0 - 1.0) * this.jitterDps;
            yawDps += (this.jitter.nextDouble() * 2.0 - 1.0) * this.jitterDps;
        }
        if (this.stickLag > 0.0) {
            double k = 1.0 - Math.exp(-this.frameSeconds / this.stickLag);
            this.lagRoll += (rollDps - this.lagRoll) * k;
            this.lagPitch += (pitchDps - this.lagPitch) * k;
            this.lagYaw += (yawDps - this.lagYaw) * k;
            rollDps = this.lagRoll;
            pitchDps = this.lagPitch;
            yawDps = this.lagYaw;
        }
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

    SimPilot mode3d() {
        this.threeD = true;
        this.input.mode3d = true;
        return this;
    }

    SimPilot invertedHold(double rollDps, double pitchDps, double yawDps, double seconds, double sinkTarget) {
        int frames = (int) Math.round(seconds * this.fps);
        double base = -this.hover() * 1.35;
        double integral = 0.0;
        for (int i = 0; i < frames; i++) {
            this.sim.getVelocity(this.velocity);
            double error = sinkTarget - this.velocity.y;
            integral = Math.clamp(integral + error * this.frameSeconds * 0.15, -0.4, 0.4);
            double thr = Math.clamp(base - 0.06 * error - integral, -1.0, 0.0);
            this.rates(rollDps, pitchDps, yawDps, thr);
            this.frame();
        }
        return this;
    }

    SimPilot spin(double rollDps, double pitchDps, double yawDps, double throttle, double seconds) {
        int frames = (int) Math.round(seconds * this.fps);
        for (int i = 0; i < frames; i++) {
            this.rates(rollDps, pitchDps, yawDps, throttle);
            this.frame();
        }
        return this;
    }

    SimPilot hold(double rollDps, double pitchDps, double yawDps, double throttle, double seconds) {
        double end = this.time + seconds;
        while (this.time < end - 1.0E-9) {
            this.rates(rollDps, pitchDps, yawDps, throttle);
            this.frame();
        }
        return this;
    }

    SimPilot level(double seconds) {
        return this.fly(0.0, 0.0, 0.0, seconds);
    }

    SimPilot fly(double forwardTiltDeg, double bankDeg, double turnDps, double seconds) {
        int frames = (int) Math.round(seconds * this.fps);
        for (int i = 0; i < frames; i++) {
            this.steer(forwardTiltDeg, bankDeg, turnDps, Double.NaN);
            this.frame();
        }
        return this;
    }

    SimPilot turn(double forwardTiltDeg, double bankDeg, double seconds) {
        int frames = (int) Math.round(seconds * this.fps);
        for (int i = 0; i < frames; i++) {
            this.sim.getVelocity(this.velocity);
            double speed = Math.max(4.0, Math.hypot(this.velocity.x, this.velocity.z));
            double turnDps = Math.toDegrees(9.81 * Math.tan(Math.toRadians(bankDeg)) / speed);
            this.steer(forwardTiltDeg, bankDeg, turnDps, Double.NaN);
            this.frame();
        }
        return this;
    }

    SimPilot flyThrottle(double forwardTiltDeg, double bankDeg, double turnDps, double throttle, double seconds) {
        int frames = (int) Math.round(seconds * this.fps);
        for (int i = 0; i < frames; i++) {
            this.steer(forwardTiltDeg, bankDeg, turnDps, throttle);
            this.frame();
        }
        return this;
    }

    SimPilot maverickLoop(double radius, double speed, double turns) {
        this.sim.getPosition(this.position);
        this.sim.getAttitude(this.attitude);
        Vector3d nose = this.attitude.transform(new Vector3d(0.0, 0.0, 1.0));
        double h = Math.hypot(nose.x, nose.z);
        double fx = h > 1.0E-6 ? nose.x / h : 0.0;
        double fz = h > 1.0E-6 ? nose.z / h : 1.0;
        double cx = this.position.x;
        double cy = this.position.y + radius;
        double cz = this.position.z;
        double omega = speed / radius;
        double angle = 0.0;
        while (angle < turns * 2.0 * Math.PI) {
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
            Vector3d thrust = new Vector3d(ax, ay + 9.81, az);
            double g = thrust.length();
            this.steerUp(thrust.normalize(), fx, fz, this.hover() * g / 9.81);
            this.frame();
            angle += omega * this.frameSeconds;
        }
        return this;
    }

    SimPilot invertedHover(double seconds) {
        this.sim.getAttitude(this.attitude);
        Vector3d nose = this.attitude.transform(new Vector3d(0.0, 0.0, 1.0));
        double h = Math.hypot(nose.x, nose.z);
        double fx = h > 1.0E-6 ? nose.x / h : 0.0;
        double fz = h > 1.0E-6 ? nose.z / h : 1.0;
        double end = this.time + seconds;
        this.sim.getPosition(this.position);
        double hold = this.position.y;
        while (this.time < end - 1.0E-9) {
            this.sim.getVelocity(this.velocity);
            this.sim.getPosition(this.position);
            Vector3d thrust = new Vector3d(-1.5 * this.velocity.x, 9.81 + 4.0 * (hold - this.position.y) - 3.0 * this.velocity.y, -1.5 * this.velocity.z);
            double g = thrust.length();
            this.steerUp(thrust.normalize().negate(), fx, fz, -this.hover() * 1.35 * g / 9.81);
            this.frame();
        }
        return this;
    }

    void steerUp(Vector3d target, double headingX, double headingZ, double throttle) {
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
        Vector3d omega = new Vector3d(axis).mul(Math.toRadians(correction)).add(0.0, Math.toRadians(yawError * LEVEL_GAIN), 0.0);
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
