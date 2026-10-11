package de.lardtom.propwash.guide.training;

import org.joml.Quaterniond;

import java.util.ArrayList;
import java.util.List;

final class Flight {
    static final double DT = 1.0 / 60.0;

    final LessonRun run;
    final TrainingSample sample = new TrainingSample();
    final List<LessonRun.Event> events = new ArrayList<>();

    Flight(LessonRun run) {
        this.run = run;
        TrainingFrame frame = run.course().frame();
        this.sample.x = frame.x();
        this.sample.y = frame.y() + 0.1;
        this.sample.z = frame.z();
        this.sample.footY = frame.y();
        this.sample.grounded = true;
        this.sample.heightAboveGround = 0.0;
        this.sample.setHeading(frame.yaw());
        this.sample.radius = 0.1;
        this.frame();
    }

    static Flight of(Lesson lesson) {
        return of(lesson, new TrainingFrame(0.0, 64.0, 0.0, 180.0F, 1.0), TrainingScale.FULL);
    }

    static Flight of(Lesson lesson, TrainingFrame frame, TrainingScale scale) {
        return new Flight(new LessonRun(Course.flat(lesson, frame, scale), null));
    }

    Flight frame() {
        this.sample.time += DT;
        this.run.update(this.sample);
        this.events.addAll(this.run.drainEvents());
        return this;
    }

    Flight hold(double seconds) {
        int frames = (int) Math.round(seconds / DT);
        for (int i = 0; i < frames; i++) {
            this.frame();
        }
        return this;
    }

    Flight arm() {
        this.sample.armed = true;
        return this.frame();
    }

    Flight disarm() {
        this.sample.armed = false;
        return this.frame();
    }

    Flight crash() {
        this.sample.crashCount++;
        return this.frame();
    }

    Flight assisted(boolean on) {
        this.sample.assisted = on;
        return this;
    }

    Flight heading(double yaw) {
        this.sample.setHeading(yaw);
        return this;
    }

    Flight place(double x, double y, double z) {
        this.sample.x = x;
        this.sample.y = y;
        this.sample.z = z;
        this.sample.footY = y - 0.1;
        double ground = this.run.course().frame().y();
        this.sample.heightAboveGround = Math.max(0.0, y - 0.1 - ground);
        this.sample.grounded = this.sample.heightAboveGround < 0.02;
        return this;
    }

    Flight local(double lx, double ly, double lz) {
        TrainingFrame f = this.run.course().frame();
        return this.place(f.worldX(lx, lz), f.worldY(ly) + 0.1, f.worldZ(lx, lz));
    }

    Flight moveTo(double x, double y, double z, double speed) {
        double dx = x - this.sample.x;
        double dy = y - this.sample.y;
        double dz = z - this.sample.z;
        double length = Math.sqrt(dx * dx + dy * dy + dz * dz);
        int frames = Math.max(1, (int) Math.ceil(length / (speed * DT)));
        double sx = this.sample.x;
        double sy = this.sample.y;
        double sz = this.sample.z;
        if (Math.hypot(dx, dz) > 0.05) {
            this.sample.setHeading(TrainingFrame.yawOf(dx, dz));
        }
        this.sample.vx = dx / (frames * DT);
        this.sample.vy = dy / (frames * DT);
        this.sample.vz = dz / (frames * DT);
        for (int i = 1; i <= frames; i++) {
            double t = i / (double) frames;
            this.place(sx + dx * t, sy + dy * t, sz + dz * t);
            this.frame();
        }
        this.sample.vx = 0.0;
        this.sample.vy = 0.0;
        this.sample.vz = 0.0;
        return this;
    }

    Flight moveLocal(double lx, double ly, double lz, double speed) {
        TrainingFrame f = this.run.course().frame();
        return this.moveTo(f.worldX(lx, lz), f.worldY(ly) + 0.1, f.worldZ(lx, lz), speed);
    }

    Flight followPath(double speed) {
        for (double[] point : this.run.lesson().path()) {
            this.moveLocal(point[0], point[1], point[2], speed);
        }
        return this;
    }

    Flight flip() {
        Quaterniond base = new Quaterniond(this.sample.qx, this.sample.qy, this.sample.qz, this.sample.qw);
        double rate = Math.toRadians(650.0);
        double angle = 0.0;
        while (angle < Math.PI * 2.0) {
            angle = Math.min(Math.PI * 2.0, angle + rate * DT);
            Quaterniond q = new Quaterniond(base).rotateX(angle);
            this.sample.qx = q.x;
            this.sample.qy = q.y;
            this.sample.qz = q.z;
            this.sample.qw = q.w;
            this.frame();
        }
        this.sample.qx = base.x;
        this.sample.qy = base.y;
        this.sample.qz = base.z;
        this.sample.qw = base.w;
        return this;
    }

    long count(LessonRun.EventType type) {
        return this.events.stream().filter(e -> e.type() == type).count();
    }
}
