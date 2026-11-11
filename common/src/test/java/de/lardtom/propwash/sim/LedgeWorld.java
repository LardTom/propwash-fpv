package de.lardtom.propwash.sim;

import de.lardtom.propwash.build.DroneBuild;

import java.util.ArrayList;
import java.util.List;

final class LedgeWorld {
    static final double EPS = 1.0e-7;
    static final double GROUND_PROBE = 5.0;
    static final double SOLID_PROBE = 1.0;
    static final double SUPPORT_PROBE = 0.05;
    static final double START = 0.01;

    final DroneSim sim;
    final PilotInput input = new PilotInput();
    final SimEnvironment env = new SimEnvironment();
    final StepResult result = new StepResult();
    final MoveReport report = new MoveReport();
    final List<double[]> boxes = new ArrayList<>();
    final double halfWidth;
    final double height;
    final double foot;
    boolean gatesVisible = true;
    boolean grid = true;
    double x;
    double y;
    double z;

    LedgeWorld(DroneSim sim) {
        this.sim = sim;
        this.halfWidth = sim.params().hitboxWidth / 2.0;
        this.height = sim.params().hitboxHeight;
        this.foot = sim.params().hitboxFootOffset;
        this.input.mode = StabMode.ACRO;
    }

    static LedgeWorld of(DroneBuild build) {
        return new LedgeWorld(new DroneSim(build, new FlightControllerConfig(), Tests.quietTuning(), 42L));
    }

    void block(double minX, double minY, double minZ, double maxX, double maxY, double maxZ) {
        this.boxes.add(new double[]{minX, minY, minZ, maxX, maxY, maxZ, 0.0});
    }

    void gate(double minX, double minY, double minZ, double maxX, double maxY, double maxZ) {
        this.boxes.add(new double[]{minX, minY, minZ, maxX, maxY, maxZ, 1.0});
    }

    void floor(double top) {
        this.block(-1000.0, top - 1.0, -1000.0, 1000.0, top, 1000.0);
    }

    void placeBottom(double px, double bottomY, double pz, boolean inverted) {
        this.x = px;
        this.y = bottomY - this.foot;
        this.z = pz;
        this.sim.reset(this.x, this.y, this.z, 0.0);
        if (inverted) {
            SimSnapshot s = new SimSnapshot();
            this.sim.copyStateTo(s);
            s.qw = 0.0;
            s.qx = 0.0;
            s.qy = 0.0;
            s.qz = 1.0;
            this.sim.copyStateFrom(s);
        }
    }

    double bottom() {
        return this.y + this.foot;
    }

    void frame(double dt) {
        this.frame(Math.round(dt * 1e9));
    }

    void frame(long nanos) {
        this.probe();
        this.sim.step(nanos, this.input, this.env, this.result);
        this.move(this.result.dx, this.result.dy, this.result.dz);
    }

    void run(double seconds, double fps) {
        long frameNanos = Math.round(1e9 / fps);
        long total = Math.round(seconds * 1e9);
        for (long t = 0; t < total; t += frameNanos) {
            this.frame(frameNanos);
        }
    }

    void arm() {
        this.input.armSwitch = false;
        this.frame(0L);
        this.input.armSwitch = true;
        this.frame(0L);
    }

    private void probe() {
        double ground = this.columnTop(this.x, this.z, this.y, GROUND_PROBE, this.gatesVisible);
        this.env.groundDistance = ground == Double.NEGATIVE_INFINITY || Double.isNaN(ground) ? Double.POSITIVE_INFINITY
                : Math.max(0.0, this.y - ground);
        if (this.env.groundDistance < SOLID_PROBE) {
            double solid = this.columnTop(this.x, this.z, this.y, SOLID_PROBE, this.gatesVisible);
            this.env.solidDistance = solid == Double.NEGATIVE_INFINITY || Double.isNaN(solid) ? Double.POSITIVE_INFINITY
                    : Math.max(0.0, this.y - solid);
        } else {
            this.env.solidDistance = Double.POSITIVE_INFINITY;
        }
        if (!this.grid || !(this.sim.telemetry().grounded || this.env.groundDistance < SOLID_PROBE)) {
            this.env.clearSupport();
            return;
        }
        double spacing = SimEnvironment.supportSpacing(2.0 * this.halfWidth);
        this.env.supportSpacing = spacing;
        this.env.supportX = this.x;
        this.env.supportZ = this.z;
        double start = this.bottom() + START;
        for (int iz = 0; iz < SimEnvironment.SUPPORT_SIDE; iz++) {
            for (int ix = 0; ix < SimEnvironment.SUPPORT_SIDE; ix++) {
                double cx = this.x + SimEnvironment.supportOffset(ix, spacing);
                double cz = this.z + SimEnvironment.supportOffset(iz, spacing);
                this.env.support[SimEnvironment.supportCell(ix, iz)] = this.columnTop(cx, cz, start, SimEnvironment.SUPPORT_RANGE, true);
            }
        }
        AirframeSpec p = this.sim.params();
        RigidBody b = this.sim.body;
        int count = Math.min(p.standPointCount(), SimEnvironment.POINT_SUPPORT);
        for (int j = 0; j < count; j++) {
            int k = AirframeSpec.ACCESSORY_GROUND + j;
            double px = this.x + b.r00 * p.groundX(k) + b.r01 * p.groundY(k) + b.r02 * p.groundZ(k);
            double pz = this.z + b.r20 * p.groundX(k) + b.r21 * p.groundY(k) + b.r22 * p.groundZ(k);
            this.env.pointX[j] = px;
            this.env.pointZ[j] = pz;
            this.env.pointSupport[j] = this.columnTop(px, pz, start, SimEnvironment.SUPPORT_RANGE, true);
        }
        this.env.pointCount = count;
    }

    double columnTop(double cx, double cz, double from, double range, boolean gates) {
        double best = Double.NEGATIVE_INFINITY;
        for (double[] b : this.boxes) {
            if (b[6] > 0.5 && !gates) {
                continue;
            }
            if (cx <= b[0] || cx >= b[3] || cz <= b[2] || cz >= b[5]) {
                continue;
            }
            if (b[1] < from && b[4] > from) {
                return Double.NaN;
            }
            if (b[4] <= from && b[4] >= from - range) {
                best = Math.max(best, b[4]);
            }
        }
        return best;
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
        r.supportGap = -this.clip(1, -SUPPORT_PROBE);
        this.sim.afterMove(r);
    }
}
