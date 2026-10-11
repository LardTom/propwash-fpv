package de.lardtom.propwash.guide.training;

import de.lardtom.propwash.build.DroneBuild;
import de.lardtom.propwash.sim.Axis;
import de.lardtom.propwash.sim.DroneSim;
import de.lardtom.propwash.sim.FlightControllerConfig;
import de.lardtom.propwash.sim.MoveReport;
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

final class SimFlight {
    static final double FPS = 120.0;
    static final long FRAME_NANOS = Math.round(1.0E9 / FPS);
    static final double GROUND = 64.0;
    static final double RAYCAST_RANGE = 5.0;
    static final double SUPPORT_PROBE = 0.05;
    static final double LEVEL_GAIN = 6.0;
    static final double MAX_CORRECTION_DPS = 500.0;
    static final double G = 9.81;

    final DroneSim sim;
    final PilotInput input = new PilotInput();
    final SimEnvironment env = new SimEnvironment();
    final StepResult result = new StepResult();
    final MoveReport report = new MoveReport();
    final TrainingScale scale;
    final TrainingFrame frame;
    final LessonRun run;
    final TrainingSample sample = new TrainingSample();
    final List<LessonRun.Event> events = new ArrayList<>();
    final Vector3d velocity = new Vector3d();
    final Quaterniond attitude = new Quaterniond();
    final Quaterniond inverse = new Quaterniond();
    final double foot;
    final double height;
    final double halfWidth;
    double x;
    double y;
    double z;
    double time;

    SimFlight(DroneBuild build, Lesson lesson) {
        this(build, lesson, 180.0F);
    }

    SimFlight(DroneBuild build, Lesson lesson, float yaw) {
        SimTuning tuning = new SimTuning();
        this.sim = new DroneSim(build, new FlightControllerConfig(), tuning, 42L);
        this.scale = TrainingScale.of(build.frame().wheelbaseMm);
        this.frame = new TrainingFrame(0.5, GROUND, 0.5, yaw, this.scale.geometry());
        this.run = new LessonRun(Course.flat(lesson, this.frame, this.scale), null);
        this.foot = this.sim.params().hitboxFootOffset;
        this.height = this.sim.params().hitboxHeight;
        this.halfWidth = this.sim.params().hitboxWidth / 2.0;
        this.x = this.frame.x();
        this.y = GROUND - this.foot;
        this.z = this.frame.z();
        this.sim.reset(this.x, this.y, this.z, -Math.toRadians(this.frame.yaw()));
        this.sim.setBatteryCharge(1.0);
        this.input.mode = StabMode.ACRO;
        this.input.throttle = 0.0;
        this.input.armSwitch = false;
        this.step();
    }

    double bottom() {
        return this.y + this.foot;
    }

    void step() {
        double distance = this.y - GROUND;
        this.env.groundDistance = distance <= RAYCAST_RANGE ? Math.max(0.0, distance) : Double.POSITIVE_INFINITY;
        this.sim.step(FRAME_NANOS, this.input, this.env, this.result);
        double dx = this.result.dx;
        double dy = this.result.dy;
        double dz = this.result.dz;
        double ay = dy;
        if (dy < 0.0) {
            ay = Math.max(dy, Math.min(0.0, GROUND - this.bottom()));
        }
        this.x += dx;
        this.y += ay;
        this.z += dz;
        MoveReport r = this.report;
        r.clear();
        r.requestedX = dx;
        r.requestedY = dy;
        r.requestedZ = dz;
        r.actualX = dx;
        r.actualY = ay;
        r.actualZ = dz;
        r.hitY = Math.abs(ay - dy) > 1.0E-7;
        r.supportGap = Math.min(SUPPORT_PROBE, Math.max(0.0, this.bottom() - GROUND));
        this.sim.afterMove(r);
        this.time += 1.0 / FPS;
        Telemetry t = this.sim.telemetry();
        this.sim.getVelocity(this.velocity);
        this.sim.getAttitude(this.attitude);
        TrainingSample s = this.sample;
        s.time = t.time;
        s.x = this.x;
        s.z = this.z;
        s.footY = this.bottom();
        s.y = s.footY + this.height * 0.5;
        s.vx = this.velocity.x;
        s.vy = this.velocity.y;
        s.vz = this.velocity.z;
        s.qx = this.attitude.x;
        s.qy = this.attitude.y;
        s.qz = this.attitude.z;
        s.qw = this.attitude.w;
        s.armed = t.armed;
        s.grounded = t.grounded;
        s.heightAboveGround = Math.max(0.0, this.bottom() - GROUND);
        s.crashCount = t.crashCount;
        s.impactCount = t.impactCount;
        s.throttle = t.throttleOut;
        s.radius = this.halfWidth;
        this.run.update(s);
        this.events.addAll(this.run.drainEvents());
    }

    double hover() {
        double h = this.sim.telemetry().hoverThrottleStick;
        return Double.isFinite(h) && h > 0.05 ? h : 0.35;
    }

    void rates(double rollDps, double pitchDps, double yawDps, double throttle) {
        RateProfile rates = this.sim.config().rates;
        this.input.roll = StickMath.rateToStick(rollDps, rates, Axis.ROLL);
        this.input.pitch = StickMath.rateToStick(pitchDps, rates, Axis.PITCH);
        this.input.yaw = StickMath.rateToStick(yawDps, rates, Axis.YAW);
        this.input.throttle = Math.clamp(throttle, 0.0, 1.0);
    }

    void attitude(double ax, double ay, double az, double yawRateDps, double throttle) {
        Quaterniond q = this.attitude;
        this.sim.getAttitude(q);
        Vector3d up = q.transform(new Vector3d(0.0, 1.0, 0.0));
        Vector3d left = q.transform(new Vector3d(1.0, 0.0, 0.0));
        Vector3d target = new Vector3d(ax, ay, az).normalize();
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
        Vector3d omega = new Vector3d(axis).mul(Math.toRadians(correction)).add(0.0, -Math.toRadians(yawRateDps), 0.0);
        q.conjugate(this.inverse).transform(omega);
        double pitch = Math.toDegrees(omega.x);
        double roll = Math.toDegrees(omega.z);
        double yaw = -Math.toDegrees(omega.y);
        double thr = throttle;
        if (!Double.isFinite(thr)) {
            double tilt = Math.max(0.5, up.y);
            double lift = Math.max(0.05, ay / G);
            thr = this.hover() * Math.sqrt(lift / tilt);
        }
        this.rates(roll, pitch, yaw, thr);
    }

    void level(double yawRateDps) {
        this.attitude(0.0, G - 1.5 * this.velocity.y, 0.0, yawRateDps, Double.NaN);
    }

    void spin(double rollDps, double pitchDps, double yawDps, double throttle, double seconds) {
        int frames = (int) Math.round(seconds * FPS);
        for (int i = 0; i < frames; i++) {
            this.rates(rollDps, pitchDps, yawDps, throttle);
            this.step();
        }
    }

    void levelFor(double seconds) {
        int frames = (int) Math.round(seconds * FPS);
        for (int i = 0; i < frames; i++) {
            this.level(0.0);
            this.step();
        }
    }

    void tiltFor(double forwardTiltDeg, double seconds) {
        int frames = (int) Math.round(seconds * FPS);
        for (int i = 0; i < frames; i++) {
            double yaw = Math.toRadians(this.sample.yaw());
            double fx = -Math.sin(yaw);
            double fz = Math.cos(yaw);
            double a = G * Math.tan(Math.toRadians(forwardTiltDeg));
            this.attitude(fx * a, G - 1.5 * this.velocity.y, fz * a, 0.0, Double.NaN);
            this.step();
        }
    }
}
