package de.lardtom.propwash.sim;

import de.lardtom.propwash.build.DroneBuild;
import de.lardtom.propwash.drone.Depenetration;
import de.lardtom.propwash.interact.SnagMaterial;
import de.lardtom.propwash.interact.SnagSampler;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import static org.junit.jupiter.api.Assertions.assertTrue;

class CovVineBeamTopTest {
    static final double FPS = 144.0;
    static final double DT = 1.0 / FPS;
    static final double TOP = 3.0;
    static final double EPS = 1.0e-7;
    static final double VINE = 1.0 / 16.0;
    static final double SUPPORT_PROBE = 0.05;
    static final double SOLID_PROBE = 1.0;
    static final double GROUND_PROBE = 5.0;
    static final double HULL_NEAR = 0.03;
    static final double GRACE_S = 0.3;
    static final double WATCH_S = 5.0;
    static final double HIGH_OUTPUT = 0.5;
    static final double MAX_HIGH_RUN_S = 0.3;
    static final int MAX_PHANTOM_FRAMES = 3;
    static final int MAX_FLOAT_FRAMES = 2;
    static final double FLOAT_SPEED = 5.0;
    static final double FLOAT_MARGIN = 0.35;
    static final double MAX_PENETRATION = 1.0e-3;
    static final double LATE_S = 1.5;
    static final double MAX_LATE_OUTPUT = 0.05;
    static final double MAX_LATE_THRUST = 0.15;

    static final class Beam implements SnagSampler.World, Depenetration.Solids {
        final DroneSim sim;
        final PilotInput input = new PilotInput();
        final SimEnvironment env = new SimEnvironment();
        final StepResult result = new StepResult();
        final MoveReport report = new MoveReport();
        final List<double[]> boxes = new ArrayList<>();
        final List<double[]> vines = new ArrayList<>();
        final SnagSampler sampler = new SnagSampler();
        final double halfWidth;
        final double height;
        final double foot;
        boolean hit;
        double x;
        double y;
        double z;

        Beam(DroneSim sim) {
            this.sim = sim;
            this.halfWidth = sim.params().hitboxWidth / 2.0;
            this.height = sim.params().hitboxHeight;
            this.foot = sim.params().hitboxFootOffset;
            this.boxes.add(new double[]{-1000.0, -1.0, -1000.0, 1000.0, 0.0, 1000.0});
            this.boxes.add(new double[]{0.0, TOP - 1.0, -30.0, 1.0, TOP, 30.0});
            this.vines.add(new double[]{-VINE, TOP - 1.0, -30.0, 0.0, TOP, 30.0});
            this.vines.add(new double[]{1.0, TOP - 1.0, -30.0, 1.0 + VINE, TOP, 30.0});
            this.vines.add(new double[]{0.0, TOP - 1.0 - VINE, -30.0, 1.0, TOP - 1.0, 30.0});
            this.vines.add(new double[]{-VINE, TOP - 2.0, -30.0, 0.0, TOP - 1.0, 30.0});
        }

        @Override
        public void boxes(int cx, int cy, int cz, SnagSampler s) {
            for (double[] v : this.vines) {
                double minX = Math.max(v[0], cx);
                double minY = Math.max(v[1], cy);
                double minZ = Math.max(v[2], cz);
                double maxX = Math.min(v[3], cx + 1.0);
                double maxY = Math.min(v[4], cy + 1.0);
                double maxZ = Math.min(v[5], cz + 1.0);
                if (minX < maxX && minY < maxY && minZ < maxZ) {
                    s.box(SnagMaterial.FOLIAGE, minX, minY, minZ, maxX, maxY, maxZ);
                }
            }
        }

        @Override
        public boolean collect(AABB area, List<AABB> out) {
            for (double[] b : this.boxes) {
                AABB box = new AABB(b[0], b[1], b[2], b[3], b[4], b[5]);
                if (box.intersects(area)) {
                    out.add(box);
                }
            }
            return true;
        }

        double bottom() {
            return this.y + this.foot;
        }

        double[] hitbox() {
            double b = this.bottom();
            return new double[]{this.x - this.halfWidth, b, this.z - this.halfWidth, this.x + this.halfWidth, b + this.height, this.z + this.halfWidth};
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

        double penetration() {
            double[] m = this.hitbox();
            double worst = 0.0;
            for (double[] b : this.boxes) {
                double dx = Math.min(m[3] - b[0], b[3] - m[0]);
                double dy = Math.min(m[4] - b[1], b[4] - m[1]);
                double dz = Math.min(m[5] - b[2], b[5] - m[2]);
                worst = Math.max(worst, Math.min(dx, Math.min(dy, dz)));
            }
            return worst;
        }

        double supportGap() {
            return -this.clip(1, -SUPPORT_PROBE);
        }

        boolean hullNear() {
            RigidBody b = this.sim.body;
            AirframeSpec p = this.sim.params();
            for (int k = 0; k < AirframeSpec.HULL_POINTS; k++) {
                double hx = this.x + b.r00 * p.hullX(k) + b.r01 * p.hullY(k) + b.r02 * p.hullZ(k);
                double hy = this.y + b.r10 * p.hullX(k) + b.r11 * p.hullY(k) + b.r12 * p.hullZ(k);
                double hz = this.z + b.r20 * p.hullX(k) + b.r21 * p.hullY(k) + b.r22 * p.hullZ(k);
                for (double[] box : this.boxes) {
                    if (hx >= box[0] - HULL_NEAR && hx <= box[3] + HULL_NEAR && hy >= box[1] - HULL_NEAR && hy <= box[4] + HULL_NEAR
                            && hz >= box[2] - HULL_NEAR && hz <= box[5] + HULL_NEAR) {
                        return true;
                    }
                }
            }
            return false;
        }

        double top(double cx, double cz, double start, double range) {
            double best = Double.NEGATIVE_INFINITY;
            for (double[] b : this.boxes) {
                if (cx < b[0] || cx > b[3] || cz < b[2] || cz > b[5]) {
                    continue;
                }
                if (b[1] < start && b[4] > start) {
                    return Double.NaN;
                }
                if (b[4] <= start && b[4] >= start - range) {
                    best = Math.max(best, b[4]);
                }
            }
            return best;
        }

        double rayDown(double range) {
            double best = Double.POSITIVE_INFINITY;
            for (double[] b : this.boxes) {
                if (this.x < b[0] || this.x > b[3] || this.z < b[2] || this.z > b[5] || b[1] > this.y) {
                    continue;
                }
                if (b[4] >= this.y) {
                    return 0.0;
                }
                best = Math.min(best, this.y - b[4]);
            }
            return best <= range ? best : Double.POSITIVE_INFINITY;
        }

        void fillEnvironment() {
            SimEnvironment e = this.env;
            e.groundDistance = this.rayDown(GROUND_PROBE);
            e.solidDistance = e.groundDistance < SOLID_PROBE ? this.rayDown(SOLID_PROBE) : Double.POSITIVE_INFINITY;
            if (!(e.groundDistance < SOLID_PROBE || this.sim.telemetry().grounded)) {
                e.clearSupport();
                return;
            }
            double spacing = SimEnvironment.supportSpacing(2.0 * this.halfWidth);
            double start = this.bottom() + 0.01;
            e.supportSpacing = spacing;
            e.supportX = this.x;
            e.supportZ = this.z;
            for (int iz = 0; iz < SimEnvironment.SUPPORT_SIDE; iz++) {
                for (int ix = 0; ix < SimEnvironment.SUPPORT_SIDE; ix++) {
                    e.support[SimEnvironment.supportCell(ix, iz)] = this.top(this.x + SimEnvironment.supportOffset(ix, spacing),
                            this.z + SimEnvironment.supportOffset(iz, spacing), start, SimEnvironment.SUPPORT_RANGE);
                }
            }
            e.pointCount = 0;
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
            this.fillEnvironment();
            this.sampler.sample(this.sim, this, this.env.snag);
            this.sim.step(nanos, this.input, this.env, this.result);
            this.escape();
            this.move(this.result.dx, this.result.dy, this.result.dz);
        }

        void escape() {
            if (this.sim.telemetry().sleeping) {
                return;
            }
            double[] m = this.hitbox();
            Vec3 out = Depenetration.resolve(new AABB(m[0], m[1], m[2], m[3], m[4], m[5]), this);
            if (out == null) {
                return;
            }
            this.x += out.x;
            this.y += out.y;
            this.z += out.z;
            this.sim.pushOut(out.x, out.y, out.z);
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
            this.hit = r.hitX || r.hitY || r.hitZ;
            Telemetry t = this.sim.telemetry();
            if (t.grounded || t.sleeping) {
                r.supportGap = this.supportGap();
            }
            this.sim.afterMove(r);
        }
    }

    record Drop(double offset, double descent, double across, double tiltDeg, double azimuthDeg, double embed) {
        String key() {
            if (this.embed > 0.0) {
                return String.format(Locale.ROOT, "%.2f m im Balken bei x %.2f, Neigung %.0f° um Achse %.0f°", this.embed, this.offset, this.tiltDeg,
                        this.azimuthDeg);
            }
            return String.format(Locale.ROOT, "Treffpunkt x %.2f, %.0f m/s abwärts, %.0f m/s quer, Neigung %.0f° um Achse %.0f°", this.offset,
                    this.descent, this.across, this.tiltDeg, this.azimuthDeg);
        }
    }

    static final class Result {
        boolean touched;
        boolean airmode;
        double contactHigh;
        double contactThrust;
        double highRun;
        int phantom;
        int floating;
        int inside;
        double worst;
        double endBottom;
        double lateOutput;
        double lateThrust;

        String row() {
            return String.format(Locale.ROOT, "Airmode %s, Motoren über %.0f %% mit Blockkontakt %.2f s (Schub bis %.2f×G), längster Stoß %.2f s, "
                            + "in den letzten %.1f s Motoren %.3f und Schub %.2f×G, aufliegend ohne Auflage %d Frames, schwebend %d Frames, im Block %d Frames "
                            + "(tiefstes Eindringen %.3f m), Unterkante am Ende %.2f", this.airmode, 100.0 * HIGH_OUTPUT, this.contactHigh, this.contactThrust,
                    this.highRun, LATE_S, this.lateOutput, this.lateThrust, this.phantom, this.floating, this.inside, this.worst, this.endBottom);
        }

        boolean ok(boolean embedded) {
            boolean motors = this.highRun <= MAX_HIGH_RUN_S && this.lateOutput <= MAX_LATE_OUTPUT && this.lateThrust <= MAX_LATE_THRUST;
            boolean gravity = this.phantom <= MAX_PHANTOM_FRAMES && this.floating <= MAX_FLOAT_FRAMES;
            boolean blocks = embedded ? this.inside <= 1 : this.worst <= MAX_PENETRATION;
            return this.touched && this.airmode && motors && gravity && blocks;
        }
    }

    static SimTuning tuning() {
        SimTuning t = new SimTuning();
        t.gyroNoiseDps = 0.0;
        t.gyroVibrationDps = 0.0;
        t.crashDisarm = false;
        return t;
    }

    static void orient(RigidBody b, double tiltDeg, double azimuthDeg) {
        double half = Math.toRadians(tiltDeg) / 2.0;
        double az = Math.toRadians(azimuthDeg);
        b.qw = Math.cos(half);
        b.qx = Math.sin(half) * Math.cos(az);
        b.qy = 0.0;
        b.qz = Math.sin(half) * Math.sin(az);
        b.updateDerived();
    }

    static double meanOutput(Telemetry t) {
        double sum = 0.0;
        for (int i = 0; i < AirframeSpec.MOTORS; i++) {
            sum += t.motorOutput[i];
        }
        return sum / AirframeSpec.MOTORS;
    }

    static Beam hovering() {
        FlightControllerConfig config = new FlightControllerConfig();
        config.pid.airmode = true;
        Beam w = new Beam(new DroneSim(DroneBuild.FREESTYLE, config, tuning(), 42L));
        w.x = -6.0;
        w.z = 0.0;
        w.y = -w.foot;
        w.sim.reset(w.x, w.y, w.z, 0.0);
        w.sim.setBatteryCharge(0.8);
        w.input.mode = StabMode.ANGLE;
        w.arm();
        AltitudeHold hold = new AltitudeHold(w.y + 1.5);
        for (int f = 0; f < (int) Math.round(2.5 * FPS); f++) {
            w.input.throttle = Math.max(hold.throttle(w.sim, DT), 0.3);
            w.frame(DT);
        }
        return w;
    }

    static Result run(Drop d) {
        Beam w = hovering();
        RigidBody b = w.sim.body;
        boolean embedded = d.embed() > 0.0;
        w.x = embedded ? d.offset() : d.offset() - d.across() * 0.12;
        w.y = embedded ? TOP - d.embed() - w.foot : TOP + 1.2 - w.foot;
        w.z = 0.0;
        w.sim.teleport(w.x, w.y, w.z, true);
        orient(b, d.tiltDeg(), d.azimuthDeg());
        b.vx = d.across();
        b.vy = -d.descent();
        b.vz = 0.0;
        b.setBodyRates(0.0, 0.0, 0.0);
        w.input.mode = StabMode.ACRO;
        w.input.roll = 0.0;
        w.input.pitch = 0.0;
        w.input.yaw = 0.0;
        w.input.throttle = 0.0;
        Result r = new Result();
        r.airmode = w.sim.telemetry().airmodeActive;
        double touch = Double.NaN;
        double lastHit = Double.NEGATIVE_INFINITY;
        double run = 0.0;
        double t = 0.0;
        double lastVy = b.vy;
        double lateOut = 0.0;
        double lateThrust = 0.0;
        int lateFrames = 0;
        while (t < WATCH_S + 2.0) {
            w.frame(DT);
            t += DT;
            Telemetry tel = w.sim.telemetry();
            double depth = w.penetration();
            r.worst = Math.max(r.worst, depth);
            if (depth > MAX_PENETRATION) {
                r.inside++;
            }
            if (w.hit) {
                lastHit = t;
            }
            double dvy = (b.vy - lastVy) / DT;
            lastVy = b.vy;
            if (Double.isNaN(touch) && (w.hit || tel.grounded || tel.impactCount > 0)) {
                touch = t;
                r.touched = true;
            }
            if (Double.isNaN(touch)) {
                continue;
            }
            double since = t - touch;
            if (since > WATCH_S) {
                break;
            }
            double out = meanOutput(tel);
            boolean contact = tel.grounded || t - lastHit < 0.1 || w.hullNear();
            if (since > WATCH_S - LATE_S) {
                lateOut += out;
                lateThrust += tel.thrustToWeight;
                lateFrames++;
            }
            if (since > GRACE_S) {
                run = out > HIGH_OUTPUT ? run + DT : 0.0;
                r.highRun = Math.max(r.highRun, run);
                if (contact) {
                    r.contactThrust = Math.max(r.contactThrust, tel.thrustToWeight);
                    if (out > HIGH_OUTPUT) {
                        r.contactHigh += DT;
                    }
                }
            }
            boolean free = !w.hit && w.supportGap() >= SUPPORT_PROBE - EPS && !w.hullNear() && depth <= MAX_PENETRATION;
            if (free && tel.grounded) {
                r.phantom++;
            }
            double speed = Math.sqrt(b.vx * b.vx + b.vy * b.vy + b.vz * b.vz);
            double lift = tel.totalThrustN * b.r11 / w.sim.params().massKg;
            if (free && !w.sampler.touching() && speed < FLOAT_SPEED && dvy > -ModelConstants.G + lift + FLOAT_MARGIN * ModelConstants.G) {
                r.floating++;
            }
        }
        r.endBottom = w.bottom();
        r.lateOutput = lateFrames == 0 ? Double.NaN : lateOut / lateFrames;
        r.lateThrust = lateFrames == 0 ? Double.NaN : lateThrust / lateFrames;
        return r;
    }

    static List<Drop> drops() {
        List<Drop> list = new ArrayList<>();
        for (double offset : new double[]{0.5, 0.15, 0.85, -0.1, 1.1}) {
            for (double[] speed : new double[][]{{3.0, 0.0}, {3.0, 4.0}, {8.0, 0.0}, {8.0, 4.0}, {15.0, 4.0}}) {
                for (double[] pose : new double[][]{{0.0, 0.0}, {35.0, 0.0}, {35.0, 90.0}, {70.0, 90.0}, {120.0, 0.0}, {150.0, 90.0}, {180.0, 0.0}}) {
                    list.add(new Drop(offset, speed[0], speed[1], pose[0], pose[1], 0.0));
                }
            }
        }
        return list;
    }

    static List<Drop> embedded() {
        List<Drop> list = new ArrayList<>();
        for (double offset : new double[]{0.5, 0.15}) {
            for (double depth : new double[]{0.03, 0.3, 0.6, 0.9}) {
                for (double[] pose : new double[][]{{0.0, 0.0}, {70.0, 90.0}, {120.0, 0.0}, {150.0, 0.0}, {180.0, 0.0}}) {
                    list.add(new Drop(offset, 1.0, 0.0, pose[0], pose[1], depth));
                }
            }
        }
        return list;
    }

    static void check(List<Drop> list, boolean embedded, String what) {
        List<Result> results = list.parallelStream().map(CovVineBeamTopTest::run).toList();
        List<String> failures = new ArrayList<>();
        for (int i = 0; i < list.size(); i++) {
            Result r = results.get(i);
            String line = list.get(i).key() + ": " + r.row();
            if (!r.ok(embedded)) {
                failures.add(line);
            }
        }
        assertTrue(failures.isEmpty(), failures.size() + " von " + list.size() + " " + what + ":\n" + String.join("\n", failures));
    }

    @Test
    void dropOntoVineBeamFromAboveKeepsMotorsIdleAndGravityActing() {
        check(drops(), false, "Stürzen von oben auf den Balken mit Ranken auffällig");
    }

    @Test
    void droneEmbeddedInTheBeamAfterTheHitLeavesAtOnceAndFalls() {
        check(embedded(), true, "Fällen im Balken auffällig");
    }
}
