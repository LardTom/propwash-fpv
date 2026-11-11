package de.lardtom.propwash.rescue;

import de.lardtom.propwash.drone.collision.ContactResponse;
import de.lardtom.propwash.sim.DroneSim;
import de.lardtom.propwash.sim.StabMode;
import de.lardtom.propwash.sim.MoveReport;
import de.lardtom.propwash.sim.PilotInput;
import de.lardtom.propwash.sim.SimEnvironment;
import de.lardtom.propwash.sim.StepResult;
import de.lardtom.propwash.sim.SurfaceResponse;
import org.jspecify.annotations.Nullable;

import java.util.List;

public final class VoxelFlight {
    private static final double EPS = 1e-7;
    private static final double RAYCAST_RANGE = 5.0;
    private static final double SUPPORT_PROBE = 0.05;
    private static final double BELOW_REACH = 1.0;
    private static final double FACE_REACH = 0.25;
    private static final double STICKY_GAP = 0.03;

    public final DroneSim sim;
    public final PilotInput input = new PilotInput();
    private final SimEnvironment env = new SimEnvironment();
    private final StepResult result = new StepResult();
    private final MoveReport report = new MoveReport();
    private final RescueWorld world;
    private final double halfWidth;
    private final double height;
    private final double foot;
    private final double[] min = new double[3];
    private final double[] max = new double[3];
    private final int[] cell = new int[3];
    private final SurfaceResponse.Mix mix = new SurfaceResponse.Mix();
    private @Nullable SurfaceCells surfaces;
    private List<? extends ContactResponse.Step> contacts = List.of();
    private double x;
    private double y;
    private double z;

    public VoxelFlight(DroneSim sim, RescueWorld world) {
        this.sim = sim;
        this.world = world;
        this.halfWidth = 0.5 * sim.params().hitboxWidth;
        this.height = sim.params().hitboxHeight;
        this.foot = sim.params().hitboxFootOffset;
        this.input.mode = StabMode.ANGLE;
    }

    public void place(double bottomX, double bottomY, double bottomZ, double headingRad) {
        this.x = bottomX;
        this.y = bottomY - this.foot;
        this.z = bottomZ;
        this.sim.reset(this.x, this.y, this.z, headingRad);
    }

    public void adopt(double bottomX, double bottomY, double bottomZ) {
        this.x = bottomX;
        this.y = bottomY - this.foot;
        this.z = bottomZ;
    }

    public void surfaces(@Nullable SurfaceCells cells) {
        this.surfaces = cells;
    }

    public void contacts(List<? extends ContactResponse.Step> steps) {
        this.contacts = List.copyOf(steps);
    }

    public double x() {
        return this.x;
    }

    public double bottom() {
        return this.y + this.foot;
    }

    public double z() {
        return this.z;
    }

    public void wind(double windX, double windY, double windZ, double gustIntensity) {
        this.env.windX = windX;
        this.env.windY = windY;
        this.env.windZ = windZ;
        this.env.gustIntensity = gustIntensity;
    }

    public void arm() {
        this.input.throttle = 0.0;
        this.input.armSwitch = false;
        this.frame(0L);
        this.input.armSwitch = true;
        this.frame(0L);
    }

    public void apply(RescueAutopilot autopilot) {
        this.input.mode = StabMode.ANGLE;
        this.input.roll = autopilot.roll();
        this.input.pitch = autopilot.pitch();
        this.input.yaw = autopilot.yaw();
        this.input.throttle = autopilot.throttle();
        if (autopilot.wantsDisarm()) {
            this.input.armSwitch = false;
        }
    }

    public void frame(long nanos) {
        this.env.groundDistance = this.groundDistance();
        this.sim.step(nanos, this.input, this.env, this.result);
        this.move(this.result.dx, this.result.dy, this.result.dz);
    }

    private double groundDistance() {
        int cx = WorldProbe.floor(this.x);
        int cz = WorldProbe.floor(this.z);
        int top = WorldProbe.floor(this.y);
        int end = WorldProbe.floor(this.y - RAYCAST_RANGE);
        for (int cy = top; cy >= end; cy--) {
            if (this.world.solid(cx, cy, cz)) {
                double d = this.y - (cy + 1);
                return d <= RAYCAST_RANGE ? Math.max(0.0, d) : Double.POSITIVE_INFINITY;
            }
        }
        return Double.POSITIVE_INFINITY;
    }

    private void move(double dx, double dy, double dz) {
        this.updateBox();
        double ay = this.clip(1, dy);
        this.y += ay;
        this.updateBox();
        double ax;
        double az;
        if (Math.abs(dx) >= Math.abs(dz)) {
            ax = this.clip(0, dx);
            this.x += ax;
            this.updateBox();
            az = this.clip(2, dz);
            this.z += az;
        } else {
            az = this.clip(2, dz);
            this.z += az;
            this.updateBox();
            ax = this.clip(0, dx);
            this.x += ax;
        }
        MoveReport r = this.report;
        r.clear();
        r.requestedX = dx;
        r.requestedY = dy;
        r.requestedZ = dz;
        r.actualX = ax;
        r.actualY = ay;
        r.actualZ = az;
        r.hitX = Math.abs(ax - dx) > 1e-7;
        r.hitY = Math.abs(ay - dy) > 1e-7;
        r.hitZ = Math.abs(az - dz) > 1e-7;
        this.updateBox();
        r.supportGap = -this.clip(1, -SUPPORT_PROBE);
        if (this.surfaces != null) {
            this.fillSurface(r, this.surfaces);
            r.stickySides = this.stickySides(this.surfaces);
            if (!this.contacts.isEmpty()) {
                ContactResponse.chain(ContactResponse.of(r), this.sim.params().restitution, this.contacts).writeTo(r);
            }
        }
        this.sim.afterMove(r);
    }

    private void fillSurface(MoveReport r, SurfaceCells cells) {
        int sx = r.hitX ? (int) Math.signum(r.requestedX) : 0;
        int sy = r.hitY ? (int) Math.signum(r.requestedY) : 0;
        int sz = r.hitZ ? (int) Math.signum(r.requestedZ) : 0;
        if (sy == 0 && (this.sim.telemetry().grounded || sx == 0 && sz == 0)) {
            sy = -1;
        }
        this.mix.clear();
        this.face(cells, 0, sx, FACE_REACH);
        this.face(cells, 1, sy, sy < 0 ? BELOW_REACH : FACE_REACH);
        this.face(cells, 2, sz, FACE_REACH);
        SurfaceResponse response = this.mix.result();
        if (response != null) {
            response.writeTo(r);
        }
    }

    private void face(SurfaceCells cells, int axis, int side, double reach) {
        if (side == 0) {
            return;
        }
        int a1 = (axis + 1) % 3;
        int a2 = (axis + 2) % 3;
        int first = side > 0 ? (int) Math.ceil(this.max[axis] - EPS) : WorldProbe.floor(this.min[axis] + EPS) - 1;
        int last = side > 0 ? (int) Math.ceil(this.max[axis] + reach) - 1 : WorldProbe.floor(this.min[axis] - reach);
        for (int i = WorldProbe.floor(this.min[a1] + EPS); i <= WorldProbe.floor(this.max[a1] - EPS); i++) {
            double d1 = Math.min(this.max[a1], i + 1.0) - Math.max(this.min[a1], i);
            for (int j = WorldProbe.floor(this.min[a2] + EPS); j <= WorldProbe.floor(this.max[a2] - EPS); j++) {
                double d2 = Math.min(this.max[a2], j + 1.0) - Math.max(this.min[a2], j);
                this.cell[a1] = i;
                this.cell[a2] = j;
                for (int c = first; side > 0 ? c <= last : c >= last; c += side) {
                    this.cell[axis] = c;
                    if (this.world.solid(this.cell[0], this.cell[1], this.cell[2])) {
                        SurfaceResponse response = cells.at(this.cell[0], this.cell[1], this.cell[2]);
                        this.mix.add(response == null ? SurfaceResponse.NORMAL : response, d1 * d2);
                        break;
                    }
                }
            }
        }
    }

    private int stickySides(SurfaceCells cells) {
        int sides = 0;
        for (int axis = 0; axis < 3; axis++) {
            for (int side = -1; side <= 1; side += 2) {
                if (this.stickyFace(cells, axis, side)) {
                    sides |= MoveReport.stickyBit(axis, side > 0);
                }
            }
        }
        return sides;
    }

    private boolean stickyFace(SurfaceCells cells, int axis, int side) {
        double plane = side > 0 ? this.max[axis] : this.min[axis];
        int c = side > 0 ? (int) Math.ceil(plane - EPS) : WorldProbe.floor(plane + EPS) - 1;
        double gap = side > 0 ? c - plane : plane - (c + 1);
        if (gap > STICKY_GAP) {
            return false;
        }
        int a1 = (axis + 1) % 3;
        int a2 = (axis + 2) % 3;
        this.cell[axis] = c;
        for (int i = WorldProbe.floor(this.min[a1] + EPS); i <= WorldProbe.floor(this.max[a1] - EPS); i++) {
            this.cell[a1] = i;
            for (int j = WorldProbe.floor(this.min[a2] + EPS); j <= WorldProbe.floor(this.max[a2] - EPS); j++) {
                this.cell[a2] = j;
                if (this.world.solid(this.cell[0], this.cell[1], this.cell[2])
                        && SurfaceResponse.STICKY.equals(cells.at(this.cell[0], this.cell[1], this.cell[2]))) {
                    return true;
                }
            }
        }
        return false;
    }

    public interface SurfaceCells {
        @Nullable SurfaceResponse at(int x, int y, int z);
    }

    private void updateBox() {
        double bottom = this.y + this.foot;
        this.min[0] = this.x - this.halfWidth;
        this.min[1] = bottom;
        this.min[2] = this.z - this.halfWidth;
        this.max[0] = this.x + this.halfWidth;
        this.max[1] = bottom + this.height;
        this.max[2] = this.z + this.halfWidth;
    }

    private double clip(int axis, double d) {
        if (d == 0.0 || !Double.isFinite(d)) {
            return 0.0;
        }
        int a1 = (axis + 1) % 3;
        int a2 = (axis + 2) % 3;
        int lo1 = WorldProbe.floor(this.min[a1] + EPS);
        int hi1 = WorldProbe.floor(this.max[a1] - EPS);
        int lo2 = WorldProbe.floor(this.min[a2] + EPS);
        int hi2 = WorldProbe.floor(this.max[a2] - EPS);
        if (d > 0.0) {
            int start = (int) Math.ceil(this.max[axis] - EPS);
            int end = (int) Math.ceil(this.max[axis] + d) - 1;
            for (int c = start; c <= end; c++) {
                if (this.slabSolid(axis, c, a1, lo1, hi1, a2, lo2, hi2)) {
                    return Math.max(0.0, Math.min(d, c - this.max[axis]));
                }
            }
            return d;
        }
        int start = WorldProbe.floor(this.min[axis] + EPS) - 1;
        int end = WorldProbe.floor(this.min[axis] + d);
        for (int c = start; c >= end; c--) {
            if (this.slabSolid(axis, c, a1, lo1, hi1, a2, lo2, hi2)) {
                return Math.min(0.0, Math.max(d, c + 1 - this.min[axis]));
            }
        }
        return d;
    }

    private boolean slabSolid(int axis, int c, int a1, int lo1, int hi1, int a2, int lo2, int hi2) {
        this.cell[axis] = c;
        for (int i = lo1; i <= hi1; i++) {
            this.cell[a1] = i;
            for (int j = lo2; j <= hi2; j++) {
                this.cell[a2] = j;
                if (this.world.solid(this.cell[0], this.cell[1], this.cell[2])) {
                    return true;
                }
            }
        }
        return false;
    }
}
