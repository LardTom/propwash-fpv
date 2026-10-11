package de.lardtom.propwash.sim;

import de.lardtom.propwash.build.DroneBuild;

import java.util.ArrayList;
import java.util.List;

final class BoxWorld {
    static final double EPS = 1.0e-7;
    static final double RAYCAST_RANGE = 5.0;
    static final double SUPPORT_PROBE = 0.05;

    final DroneSim sim;
    final PilotInput input = new PilotInput();
    final SimEnvironment env = new SimEnvironment();
    final StepResult result = new StepResult();
    final MoveReport report = new MoveReport();
    final List<double[]> boxes = new ArrayList<>();
    final double halfWidth;
    final double height;
    final double foot;
    double x;
    double y;
    double z;

    BoxWorld(DroneSim sim) {
        this.sim = sim;
        this.halfWidth = sim.params().hitboxWidth / 2.0;
        this.height = sim.params().hitboxHeight;
        this.foot = sim.params().hitboxFootOffset;
        this.input.mode = StabMode.ANGLE;
    }

    static BoxWorld of(DroneBuild build, SimTuning tuning) {
        return new BoxWorld(new DroneSim(build, new FlightControllerConfig(), tuning, 42L));
    }

    double[] box(double minX, double minY, double minZ, double maxX, double maxY, double maxZ) {
        double[] b = {minX, minY, minZ, maxX, maxY, maxZ};
        this.boxes.add(b);
        return b;
    }

    double[] ground(double top) {
        return this.box(-1000.0, top - 1.0, -1000.0, 1000.0, top, 1000.0);
    }

    void placeBottom(double px, double bottomY, double pz, double heading) {
        this.x = px;
        this.y = bottomY - this.foot;
        this.z = pz;
        this.sim.reset(this.x, this.y, this.z, heading);
    }

    void launch(double vx, double vy, double vz) {
        SimSnapshot s = new SimSnapshot();
        this.sim.copyStateTo(s);
        s.vx = vx;
        s.vy = vy;
        s.vz = vz;
        this.sim.copyStateFrom(s);
    }

    double bottom() {
        return this.y + this.foot;
    }

    double speed() {
        RigidBody b = this.sim.body;
        return Math.sqrt(b.vx * b.vx + b.vy * b.vy + b.vz * b.vz);
    }

    void arm() {
        this.input.armSwitch = false;
        this.frame(0L);
        this.input.armSwitch = true;
        this.frame(0L);
    }

    void frame(double dt) {
        this.frame(Math.round(dt * 1e9));
    }

    void frame(long nanos) {
        this.env.groundDistance = this.groundDistance();
        this.sim.step(nanos, this.input, this.env, this.result);
        this.move(this.result.dx, this.result.dy, this.result.dz);
    }

    private double groundDistance() {
        double best = Double.POSITIVE_INFINITY;
        for (double[] b : this.boxes) {
            if (this.x >= b[0] && this.x <= b[3] && this.z >= b[2] && this.z <= b[5] && b[4] <= this.y + EPS) {
                best = Math.min(best, this.y - b[4]);
            }
        }
        return best <= RAYCAST_RANGE ? Math.max(0.0, best) : Double.POSITIVE_INFINITY;
    }

    double[] hitbox() {
        double bottom = this.bottom();
        return new double[]{this.x - this.halfWidth, bottom, this.z - this.halfWidth, this.x + this.halfWidth, bottom + this.height,
                this.z + this.halfWidth};
    }

    double clip(int axis, double d) {
        if (d == 0.0) {
            return 0.0;
        }
        double[] m = this.hitbox();
        int a1 = (axis + 1) % 3;
        int a2 = (axis + 2) % 3;
        double allowed = d;
        for (double[] b : this.boxes) {
            if (m[a1 + 3] - EPS <= b[a1] || m[a1] + EPS >= b[a1 + 3] || m[a2 + 3] - EPS <= b[a2] || m[a2] + EPS >= b[a2 + 3]) {
                continue;
            }
            if (d > 0.0 && b[axis] >= m[axis + 3] - EPS) {
                allowed = Math.min(allowed, Math.max(0.0, b[axis] - m[axis + 3]));
            } else if (d < 0.0 && b[axis + 3] <= m[axis] + EPS) {
                allowed = Math.max(allowed, Math.min(0.0, b[axis + 3] - m[axis]));
            }
        }
        return allowed;
    }

    double supportGap() {
        return -this.clip(1, -SUPPORT_PROBE);
    }

    private void shift(int axis, double d) {
        if (axis == 0) {
            this.x += d;
        } else if (axis == 1) {
            this.y += d;
        } else {
            this.z += d;
        }
    }

    void move(double dx, double dy, double dz) {
        double[] want = {dx, dy, dz};
        double[] got = new double[3];
        int[] order = Math.abs(dx) < Math.abs(dz) ? new int[]{1, 2, 0} : new int[]{1, 0, 2};
        for (int axis : order) {
            got[axis] = this.clip(axis, want[axis]);
            this.shift(axis, got[axis]);
        }
        MoveReport r = this.report;
        r.clear();
        r.requestedX = dx;
        r.requestedY = dy;
        r.requestedZ = dz;
        r.actualX = got[0];
        r.actualY = got[1];
        r.actualZ = got[2];
        r.hitX = Math.abs(got[0] - dx) > EPS;
        r.hitY = Math.abs(got[1] - dy) > EPS;
        r.hitZ = Math.abs(got[2] - dz) > EPS;
        r.supportGap = this.supportGap();
        this.sim.afterMove(r);
    }
}
