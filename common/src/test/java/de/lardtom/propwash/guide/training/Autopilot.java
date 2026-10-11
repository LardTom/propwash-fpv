package de.lardtom.propwash.guide.training;

import de.lardtom.propwash.sim.Axis;
import de.lardtom.propwash.sim.StickMath;
import de.lardtom.propwash.trick.TrickType;

import java.util.ArrayList;
import java.util.List;

final class Autopilot {
    static final double MAX_TILT_DEG = 38.0;
    static final double VELOCITY_GAIN = 2.6;
    static final double CROSS_GAIN = 2.2;
    static final double VERTICAL_GAIN = 2.0;
    static final double YAW_GAIN = 4.0;
    static final double MAX_YAW_DPS = 220.0;
    static final double BRAKE = 3.0;

    private final SimFlight flight;
    private final List<double[]> points = new ArrayList<>();
    private final List<Integer> owners = new ArrayList<>();
    private final double cruise;
    private int index = 1;
    private int trickAttempts;
    private boolean landing;

    Autopilot(SimFlight flight, double cruise) {
        this.flight = flight;
        this.cruise = cruise * flight.scale.geometry();
        Lesson lesson = flight.run.lesson();
        TrainingFrame frame = flight.frame;
        List<double[]> path = lesson.path();
        int cursor = 0;
        this.add(frame, path.get(cursor++), -1);
        for (int g = 0; g < lesson.goals().size(); g++) {
            Goal goal = lesson.goals().get(g);
            int count = switch (goal) {
                case Goal.Arm arm -> 0;
                case Goal.Hover hover -> 1;
                case Goal.Heading heading -> 1;
                case Goal.Rings rings -> 3 * rings.count();
                case Goal.Laps laps -> 3 * laps.count() + 3;
                case Goal.Land land -> 1;
                case Goal.Tricks tricks -> 1;
                case Goal.Combo combo -> 1;
            };
            for (int i = 0; i < count; i++) {
                this.add(frame, path.get(cursor++), g);
            }
        }
        if (cursor != path.size()) {
            throw new IllegalStateException("Pfad passt nicht zu den Zielen: " + cursor + " / " + path.size());
        }
    }

    private void add(TrainingFrame frame, double[] local, int goal) {
        this.points.add(new double[]{frame.worldX(local[0], local[2]), frame.worldY(local[1]), frame.worldZ(local[0], local[2])});
        this.owners.add(goal);
    }

    void fly(double maxSeconds) {
        SimFlight f = this.flight;
        int lap = 0;
        for (int i = 0; i < 12 && f.run.state() == LessonRun.State.READY; i++) {
            f.input.armSwitch = i >= 3;
            f.rates(0.0, 0.0, 0.0, 0.0);
            f.step();
        }
        while (!f.run.finished() && f.time < maxSeconds) {
            Goal goal = f.run.goal();
            if (goal instanceof Goal.Arm arm) {
                f.rates(0.0, 0.0, 0.0, 0.0);
                f.input.armSwitch = f.run.armedTime() < arm.seconds() + 0.15;
                f.step();
                continue;
            }
            if (goal instanceof Goal.Laps loop && this.index >= this.points.size()) {
                lap++;
                if (lap > loop.laps()) {
                    break;
                }
                this.index = this.firstOf(f.run.goalIndex()) + 3;
            }
            int at = Math.min(this.index, this.points.size() - 1);
            int owner = this.owners.get(at);
            boolean atAction = owner == f.run.goalIndex() && this.isStationary(goal)
                    && this.reached(this.points.get(at), 0.9 * f.scale.geometry());
            if (goal instanceof Goal.Land && (this.landing || this.index >= this.points.size() - 1 && this.reached(this.points.getLast(), 0.6))) {
                this.landing = true;
                this.land();
                continue;
            }
            if (atAction) {
                this.act(goal, this.points.get(at));
                continue;
            }
            this.follow();
        }
    }

    private int firstOf(int goal) {
        for (int i = 0; i < this.owners.size(); i++) {
            if (this.owners.get(i) == goal) {
                return i;
            }
        }
        return this.owners.size();
    }

    private boolean isStationary(Goal goal) {
        return goal instanceof Goal.Hover || goal instanceof Goal.Heading || goal instanceof Goal.Tricks || goal instanceof Goal.Combo;
    }

    private boolean reached(double[] point, double radius) {
        SimFlight f = this.flight;
        double dx = point[0] - f.sample.x;
        double dy = point[1] - f.sample.y;
        double dz = point[2] - f.sample.z;
        return Math.sqrt(dx * dx + dy * dy + dz * dz) < radius;
    }

    private void act(Goal goal, double[] point) {
        SimFlight f = this.flight;
        switch (goal) {
            case Goal.Heading heading -> {
                int marker = heading.markers().get(Math.min(f.run.markerStep(), heading.markers().size() - 1));
                double target = TrainingFrame.yawOf(f.run.course().markerX(marker) - f.sample.x, f.run.course().markerZ(marker) - f.sample.z);
                double error = TrainingFrame.delta(f.sample.yaw(), target);
                this.hold(point, Math.clamp(error * 3.0, -120.0, 120.0));
            }
            case Goal.Tricks tricks -> this.trick(tricks.types().isEmpty() ? null : tricks.types().iterator().next().variantOf(), point);
            case Goal.Combo combo -> {
                this.settle(point);
                f.spin(0.0, 650.0, 0.0, 0.3, 0.55);
                this.holdFor(point, 1.0);
                f.spin(650.0, 0.0, 0.0, 0.3, 0.55);
                this.holdFor(point, 1.0);
                f.spin(0.0, 650.0, 0.0, 0.3, 0.55);
                this.holdFor(point, 3.5);
                this.trickAttempts++;
            }
            default -> this.hold(point, 0.0);
        }
    }

    private void settle(double[] point) {
        SimFlight f = this.flight;
        for (int i = 0; i < 720 && !f.run.finished(); i++) {
            this.hold(point, 0.0);
            if (i > 90 && this.reached(point, 0.5) && f.velocity.length() < 0.5) {
                return;
            }
        }
    }

    private void holdFor(double[] point, double seconds) {
        int frames = (int) Math.round(seconds * SimFlight.FPS);
        for (int i = 0; i < frames; i++) {
            this.hold(point, 0.0);
        }
    }

    private void trick(TrickType type, double[] point) {
        SimFlight f = this.flight;
        if (this.trickAttempts > 3) {
            this.hold(point, 0.0);
            return;
        }
        this.trickAttempts++;
        String name = type == null ? "FLIP" : type.name();
        switch (name) {
            case "POWER_LOOP" -> {
                this.settle(new double[]{point[0], point[1] - 3.0 * f.scale.geometry(), point[2]});
                f.tiltFor(35.0, 1.6);
                f.spin(0.0, -220.0, 0.0, 0.85, 1.65);
                f.levelFor(3.0);
            }
            case "SPLIT_S" -> {
                this.settle(new double[]{point[0], point[1] + 3.0 * f.scale.geometry(), point[2]});
                f.tiltFor(35.0, 1.6);
                f.spin(650.0, 0.0, 0.0, 0.2, 0.28);
                f.spin(0.0, -300.0, 0.0, 0.6, 0.6);
                f.levelFor(3.0);
            }
            case "ROLL", "DOUBLE_ROLL", "TRIPLE_ROLL" -> {
                this.settle(point);
                f.spin(650.0, 0.0, 0.0, 0.3, 0.55);
                this.holdFor(point, 2.0);
            }
            default -> {
                this.settle(point);
                f.spin(0.0, 650.0, 0.0, 0.3, 0.55);
                this.holdFor(point, 2.0);
            }
        }
    }

    private void hold(double[] point, double yawRate) {
        SimFlight f = this.flight;
        this.command(point, 0.0, yawRate, false);
        f.step();
    }

    private void land() {
        SimFlight f = this.flight;
        double[] point = this.points.getLast();
        if (f.sample.nearGround() && f.sample.grounded) {
            f.rates(0.0, 0.0, 0.0, 0.0);
            f.input.armSwitch = false;
            f.step();
            return;
        }
        double[] below = {point[0], SimFlight.GROUND - 1.0, point[2]};
        double horizontal = Math.hypot(point[0] - f.sample.x, point[2] - f.sample.z);
        double sink = horizontal < 0.25 ? 0.7 : 0.0;
        this.command(horizontal < 0.25 ? below : new double[]{point[0], f.sample.y, point[2]}, sink, 0.0, true);
        f.step();
    }

    private void follow() {
        SimFlight f = this.flight;
        if (this.index >= this.points.size()) {
            this.hold(this.points.getLast(), 0.0);
            return;
        }
        double[] a = this.points.get(this.index - 1);
        double[] b = this.points.get(this.index);
        double ux = b[0] - a[0];
        double uy = b[1] - a[1];
        double uz = b[2] - a[2];
        double length = Math.sqrt(ux * ux + uy * uy + uz * uz);
        if (length < 1.0E-6) {
            this.index++;
            return;
        }
        ux /= length;
        uy /= length;
        uz /= length;
        double px = f.sample.x - a[0];
        double py = f.sample.y - a[1];
        double pz = f.sample.z - a[2];
        double along = px * ux + py * uy + pz * uz;
        if (along >= length || this.reached(b, 0.35 * f.scale.geometry())) {
            this.index++;
            this.follow();
            return;
        }
        double t = Math.clamp(along, 0.0, length);
        double cx = a[0] + ux * t - f.sample.x;
        double cy = a[1] + uy * t - f.sample.y;
        double cz = a[2] + uz * t - f.sample.z;
        double remaining = this.remainingBeforeStop(length - along);
        double corner = this.cornerSpeed(this.index);
        double speed = Math.min(this.cruise, Math.sqrt(2.0 * BRAKE * Math.max(0.0, remaining)) + 0.6);
        speed = Math.min(speed, Math.sqrt(corner * corner + 2.0 * BRAKE * Math.max(0.0, length - along)));
        double vx = ux * speed + cx * CROSS_GAIN;
        double vy = uy * speed + cy * CROSS_GAIN;
        double vz = uz * speed + cz * CROSS_GAIN;
        this.velocityCommand(vx, vy, vz, true);
        f.step();
    }

    private double cornerSpeed(int i) {
        if (i + 1 >= this.points.size() || i < 1) {
            return this.cruise;
        }
        double[] a = this.points.get(i - 1);
        double[] b = this.points.get(i);
        double[] c = this.points.get(i + 1);
        double ux = b[0] - a[0];
        double uy = b[1] - a[1];
        double uz = b[2] - a[2];
        double vx = c[0] - b[0];
        double vy = c[1] - b[1];
        double vz = c[2] - b[2];
        double lu = Math.sqrt(ux * ux + uy * uy + uz * uz);
        double lv = Math.sqrt(vx * vx + vy * vy + vz * vz);
        if (lu < 1.0E-6 || lv < 1.0E-6) {
            return this.cruise;
        }
        double cos = Math.clamp((ux * vx + uy * vy + uz * vz) / (lu * lv), -1.0, 1.0);
        double angle = Math.toDegrees(Math.acos(cos));
        if (angle < 15.0) {
            return this.cruise;
        }
        return Math.max(1.2, this.cruise * Math.max(0.0, 1.0 - angle / 110.0));
    }

    private double remainingBeforeStop(double toCurrent) {
        double total = toCurrent;
        int goal = this.flight.run.goalIndex();
        for (int i = this.index; i + 1 < this.points.size(); i++) {
            int owner = this.owners.get(i + 1);
            Goal next = owner >= 0 ? this.flight.run.lesson().goals().get(owner) : null;
            if (next != null && this.isStationary(next) && owner >= goal) {
                double[] p = this.points.get(i);
                double[] q = this.points.get(i + 1);
                return total + Math.sqrt(Math.pow(q[0] - p[0], 2) + Math.pow(q[1] - p[1], 2) + Math.pow(q[2] - p[2], 2));
            }
            double[] p = this.points.get(i);
            double[] q = this.points.get(i + 1);
            total += Math.sqrt(Math.pow(q[0] - p[0], 2) + Math.pow(q[1] - p[1], 2) + Math.pow(q[2] - p[2], 2));
            if (total > 60.0) {
                return total;
            }
        }
        Goal last = this.flight.run.lesson().goals().getLast();
        return last instanceof Goal.Laps ? 1000.0 : total;
    }

    private void command(double[] point, double sink, double yawRate, boolean faceTravel) {
        SimFlight f = this.flight;
        double dx = point[0] - f.sample.x;
        double dy = point[1] - f.sample.y;
        double dz = point[2] - f.sample.z;
        double vx = Math.clamp(dx * 1.2, -3.0, 3.0);
        double vy = sink > 0.0 ? -sink : Math.clamp(dy * 1.5, -2.5, 2.5);
        double vz = Math.clamp(dz * 1.2, -3.0, 3.0);
        this.velocityCommand(vx, vy, vz, false);
        if (!faceTravel) {
            SimFlight s = this.flight;
            s.input.yaw = StickMath.rateToStick(yawRate, s.sim.config().rates, Axis.YAW);
        }
    }

    private void velocityCommand(double vx, double vy, double vz, boolean faceTravel) {
        SimFlight f = this.flight;
        double ax = (vx - f.velocity.x) * VELOCITY_GAIN;
        double az = (vz - f.velocity.z) * VELOCITY_GAIN;
        double ay = (vy - f.velocity.y) * VERTICAL_GAIN;
        double limit = SimFlight.G * Math.tan(Math.toRadians(MAX_TILT_DEG));
        double horizontal = Math.hypot(ax, az);
        if (horizontal > limit) {
            ax *= limit / horizontal;
            az *= limit / horizontal;
        }
        ay = Math.clamp(ay, -6.0, 8.0);
        double yawRate = 0.0;
        if (faceTravel && Math.hypot(vx, vz) > 1.0) {
            double error = TrainingFrame.delta(f.sample.yaw(), TrainingFrame.yawOf(vx, vz));
            yawRate = Math.clamp(error * YAW_GAIN, -MAX_YAW_DPS, MAX_YAW_DPS);
        }
        f.attitude(ax, SimFlight.G + ay, az, yawRate, Double.NaN);
    }
}
