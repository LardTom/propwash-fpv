package de.lardtom.propwash.trick;

import org.joml.Quaterniond;
import org.joml.Vector3d;

import java.util.ArrayList;
import java.util.List;

final class Kinematic {
    static final double HZ = 120.0;

    interface Path {
        void at(double t, Vector3d position, Quaterniond attitude);
    }

    interface Nearby {
        void fill(double t, TrickSample sample);
    }

    final TrickSession session = new TrickSession();
    final List<Trick> detected = new ArrayList<>();
    final TrickSample sample = new TrickSample();
    double time;
    double hz = HZ;
    boolean armed = true;
    boolean assisted;
    int crashCount;
    int impactCount;
    double throttle = 0.4;
    Nearby nearby;

    Kinematic() {
        this.session.keepHistory(256);
    }

    Kinematic run(double seconds, Path path) {
        Vector3d p0 = new Vector3d();
        Vector3d p1 = new Vector3d();
        Quaterniond q = new Quaterniond();
        Quaterniond scratch = new Quaterniond();
        double dt = 1.0 / this.hz;
        int frames = (int) Math.round(seconds * this.hz);
        double base = this.time;
        for (int i = 0; i < frames; i++) {
            double t = i * dt;
            path.at(t, p0, q);
            path.at(t + dt, p1, scratch);
            this.time = base + t;
            this.feed(p0, p1.sub(p0, new Vector3d()).div(dt), q);
        }
        this.time = base + frames * dt;
        return this;
    }

    void feed(Vector3d position, Vector3d velocity, Quaterniond attitude) {
        TrickSample s = this.sample;
        s.time = this.time;
        s.x = position.x;
        s.y = position.y;
        s.z = position.z;
        s.vx = velocity.x;
        s.vy = velocity.y;
        s.vz = velocity.z;
        s.qx = attitude.x;
        s.qy = attitude.y;
        s.qz = attitude.z;
        s.qw = attitude.w;
        s.armed = this.armed;
        s.assisted = this.assisted;
        s.crashCount = this.crashCount;
        s.impactCount = this.impactCount;
        s.grounded = false;
        s.throttle = this.throttle;
        s.clearNearby();
        if (this.nearby != null) {
            this.nearby.fill(this.time, s);
        }
        int before = this.session.history().size();
        this.session.update(s);
        List<Trick> history = this.session.history();
        for (int i = before; i < history.size(); i++) {
            this.detected.add(history.get(i));
        }
    }

    static Quaterniond heading(double headingRad, double pitchDownRad, double rollRightRad, Quaterniond dest) {
        return dest.identity().rotateY(headingRad).rotateX(pitchDownRad).rotateZ(rollRightRad);
    }

    List<TrickType> types() {
        List<TrickType> out = new ArrayList<>();
        for (Trick trick : this.detected) {
            out.add(trick.type());
        }
        return out;
    }
}
