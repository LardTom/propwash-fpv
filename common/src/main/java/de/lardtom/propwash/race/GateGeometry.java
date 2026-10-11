package de.lardtom.propwash.race;

import de.lardtom.propwash.core.OrientedBox;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public final class GateGeometry {
    public static final double NO_CROSSING = -1.0;
    public static final double HANGER_SIZE = 0.125;
    public static final double SEGMENT_LENGTH = 0.25;
    private static final double EPSILON = 1.0E-9;
    private static final double SAME_EPSILON = 1.0E-6;

    private final double x;
    private final double y;
    private final double z;
    private final float yawDegrees;
    private final double width;
    private final double height;
    private final double frame;
    private final double depth;
    private final GateMount mount;
    private final boolean flipped;
    private final GateTilt tilt;
    private final double sideX;
    private final double sideY;
    private final double sideZ;
    private final double upX;
    private final double upY;
    private final double upZ;
    private final double normalX;
    private final double normalY;
    private final double normalZ;
    private final double originX;
    private final double originY;
    private final double originZ;
    private final double centerX;
    private final double centerY;
    private final double centerZ;
    private final boolean upright;
    private final boolean aligned;
    private final CourseElement kind;
    private final int variant;
    private final List<Passage> stages;
    private final List<List<Passage>> fouls;
    private final @Nullable FreeformRegion region;

    public record Waypoint(double x, double y, double z, double dx, double dy, double dz) {
    }

    public GateGeometry(double x, double y, double z, float yawDegrees, double width, double height, double frame, double depth,
                        GateMount mount, boolean flipped, GateTilt tilt) {
        this(x, y, z, yawDegrees, width, height, frame, depth, mount, flipped, tilt, CourseElement.GATE, 0);
    }

    public GateGeometry(double x, double y, double z, float yawDegrees, double width, double height, double frame, double depth,
                        GateMount mount, boolean flipped, GateTilt tilt, CourseElement kind, int variant) {
        this.kind = kind == null ? CourseElement.GATE : kind;
        this.variant = this.kind.variant(variant);
        this.region = null;
        boolean element = !this.kind.gate();
        this.x = x;
        this.y = y;
        this.z = z;
        this.yawDegrees = yawDegrees;
        this.width = width;
        this.height = height;
        this.frame = frame;
        this.depth = depth;
        this.mount = !this.kind.mountable() || mount == null ? GateMount.STANDING : mount;
        this.flipped = !element && GateMount.normalizeFlip(this.mount, flipped);
        GateTilt given = tilt == null ? GateTilt.NONE : tilt;
        this.tilt = element && given.tilted() ? new GateTilt(given.yaw(), 0.0F, 0.0F) : given;
        double r = Math.toRadians(yawDegrees);
        double fx = clean(-Math.sin(r));
        double fz = clean(Math.cos(r));
        double rx = clean(Math.cos(r));
        double rz = clean(Math.sin(r));
        double s = this.flipped ? -1.0 : 1.0;
        boolean horizontal = this.mount.horizontal();
        double bsx = switch (this.mount) {
            case STANDING, HANGING -> rx;
            case FLOOR_FLAT, CEILING_FLAT, WALL_DIVE -> s * rx;
            case WALL_SIDE -> -s * fx;
            case WALL_WINDOW -> -s * rx;
        };
        double bsz = switch (this.mount) {
            case STANDING, HANGING -> rz;
            case FLOOR_FLAT, CEILING_FLAT, WALL_DIVE -> s * rz;
            case WALL_SIDE -> -s * fz;
            case WALL_WINDOW -> -s * rz;
        };
        double bnx = switch (this.mount) {
            case STANDING, HANGING -> fx;
            case FLOOR_FLAT, CEILING_FLAT, WALL_DIVE -> 0.0;
            case WALL_SIDE -> s * rx;
            case WALL_WINDOW -> -s * fx;
        };
        double bny = horizontal ? -s : 0.0;
        double bnz = switch (this.mount) {
            case STANDING, HANGING -> fz;
            case FLOOR_FLAT, CEILING_FLAT, WALL_DIVE -> 0.0;
            case WALL_SIDE -> s * rz;
            case WALL_WINDOW -> -s * fz;
        };
        double bux = horizontal ? fx : 0.0;
        double buy = horizontal ? 0.0 : 1.0;
        double buz = horizontal ? fz : 0.0;
        double baseCenterX = horizontal ? x + bux * (height * 0.5) : x;
        double baseCenterY = horizontal ? y : y + height * 0.5;
        double baseCenterZ = horizontal ? z + buz * (height * 0.5) : z;
        if (this.tilt.none()) {
            this.sideX = bsx;
            this.sideY = 0.0;
            this.sideZ = bsz;
            this.upX = bux;
            this.upY = buy;
            this.upZ = buz;
            this.normalX = bnx;
            this.normalY = bny;
            this.normalZ = bnz;
            this.originX = x;
            this.originY = y;
            this.originZ = z;
            this.centerX = baseCenterX;
            this.centerY = baseCenterY;
            this.centerZ = baseCenterZ;
            this.upright = this.mount.upright();
        } else {
            double roll = Math.toRadians(this.tilt.roll());
            double pitch = Math.toRadians(this.tilt.pitch());
            double cr = Math.cos(roll);
            double sr = Math.sin(roll);
            double cp = Math.cos(pitch);
            double sp = Math.sin(pitch);
            double pux = cp * bux - sp * bnx;
            double puy = cp * buy - sp * bny;
            double puz = cp * buz - sp * bnz;
            double tsx = cr * bsx + sr * pux;
            double tsy = sr * puy;
            double tsz = cr * bsz + sr * puz;
            double tux = -sr * bsx + cr * pux;
            double tuy = cr * puy;
            double tuz = -sr * bsz + cr * puz;
            double tnx = cp * bnx + sp * bux;
            double tny = cp * bny + sp * buy;
            double tnz = cp * bnz + sp * buz;
            double yaw = Math.toRadians(this.tilt.yaw());
            double cy = Math.cos(yaw);
            double sy = Math.sin(yaw);
            this.sideX = clean(cy * tsx - sy * tsz);
            this.sideY = clean(tsy);
            this.sideZ = clean(sy * tsx + cy * tsz);
            this.upX = clean(cy * tux - sy * tuz);
            this.upY = clean(tuy);
            this.upZ = clean(sy * tux + cy * tuz);
            this.normalX = clean(cy * tnx - sy * tnz);
            this.normalY = clean(tny);
            this.normalZ = clean(sy * tnx + cy * tnz);
            double half = height * 0.5;
            double ox = baseCenterX - this.upX * half;
            double oy = baseCenterY - this.upY * half;
            double oz = baseCenterZ - this.upZ * half;
            double mx = this.mount.surface() == GateMount.Surface.WALL ? fx : 0.0;
            double my = switch (this.mount.surface()) {
                case FLOOR -> 1.0;
                case CEILING -> -1.0;
                case WALL -> 0.0;
            };
            double mz = this.mount.surface() == GateMount.Surface.WALL ? fz : 0.0;
            double before = x * mx + y * my + z * mz + lowest(bsx, 0.0, bsz, bux, buy, buz, bnx, bny, bnz, mx, my, mz, width, height, depth);
            double after = ox * mx + oy * my + oz * mz
                    + lowest(this.sideX, this.sideY, this.sideZ, this.upX, this.upY, this.upZ, this.normalX, this.normalY, this.normalZ, mx, my, mz,
                    width, height, depth);
            double shift = before - after;
            this.originX = ox + mx * shift;
            this.originY = oy + my * shift;
            this.originZ = oz + mz * shift;
            this.centerX = this.originX + this.upX * half;
            this.centerY = this.originY + this.upY * half;
            this.centerZ = this.originZ + this.upZ * half;
            this.upright = this.mount.upright() && !this.tilt.tilted() && this.upY == 1.0;
        }
        this.aligned = axis(this.sideX, this.sideY, this.sideZ) && axis(this.upX, this.upY, this.upZ) && axis(this.normalX, this.normalY, this.normalZ);
        if (element) {
            this.stages = List.copyOf(this.elementStages());
            List<List<Passage>> perStage = new ArrayList<>(this.kind.stages());
            for (int stage = 0; stage < this.kind.stages(); stage++) {
                perStage.add(List.copyOf(this.elementFouls(stage)));
            }
            this.fouls = List.copyOf(perStage);
        } else {
            this.stages = List.of();
            this.fouls = List.of();
        }
    }

    public GateGeometry(double x, double y, double z, float yawDegrees, double width, double height, double frame, double depth,
                        GateMount mount, boolean flipped) {
        this(x, y, z, yawDegrees, width, height, frame, depth, mount, flipped, GateTilt.NONE);
    }

    public GateGeometry(double x, double y, double z, float yawDegrees, double width, double height, double frame, double depth) {
        this(x, y, z, yawDegrees, width, height, frame, depth, GateMount.STANDING, false, GateTilt.NONE);
    }

    private static double lowest(double sx, double sy, double sz, double ux, double uy, double uz, double nx, double ny, double nz, double mx,
                                 double my, double mz, double width, double height, double depth) {
        double side = Math.abs(sx * mx + sy * my + sz * mz) * width * 0.5;
        double up = Math.min(0.0, (ux * mx + uy * my + uz * mz) * height);
        double normal = Math.abs(nx * mx + ny * my + nz * mz) * depth * 0.5;
        return up - side - normal;
    }

    private static boolean axis(double ax, double ay, double az) {
        int zero = (ax == 0.0 ? 1 : 0) + (ay == 0.0 ? 1 : 0) + (az == 0.0 ? 1 : 0);
        return zero == 2;
    }

    public static GateGeometry of(double x, double y, double z, float yawDegrees, GateSize size) {
        return new GateGeometry(x, y, z, yawDegrees, size.width(), size.height(), size.frame(), size.depth());
    }

    public static GateGeometry of(double x, double y, double z, float yawDegrees, GateSize size, GateMount mount, boolean flipped) {
        return new GateGeometry(x, y, z, yawDegrees, size.width(), size.height(), size.frame(), size.depth(), mount, flipped);
    }

    public static GateGeometry of(double x, double y, double z, float yawDegrees, GateSize size, GateMount mount, boolean flipped, double width,
                                  double height, GateTilt tilt) {
        return new GateGeometry(x, y, z, yawDegrees, width, height, size.frame(), size.depth(), mount, flipped, tilt);
    }

    public static GateGeometry element(double x, double y, double z, float yawDegrees, CourseElement kind, int variant, double width, double height,
                                       GateTilt tilt) {
        return element(x, y, z, yawDegrees, kind, variant, width, height, tilt, GateMount.STANDING);
    }

    public static GateGeometry element(double x, double y, double z, float yawDegrees, CourseElement kind, int variant, double width, double height,
                                       GateTilt tilt, GateMount mount) {
        return new GateGeometry(x, y, z, yawDegrees, width, height, kind.thickness(), kind.depth(width), mount, false, tilt, kind, variant);
    }

    public static GateGeometry freeform(double x, double y, double z, FreeformRegion region, int variant) {
        return new GateGeometry(x, y, z, region == null ? FreeformRegion.single(FreeformRegion.AXIS_Z) : region, variant);
    }

    private GateGeometry(double x, double y, double z, FreeformRegion region, int variant) {
        this.kind = CourseElement.FREEFORM;
        this.variant = this.kind.variant(variant);
        this.region = region;
        this.x = x;
        this.y = y;
        this.z = z;
        int axis = region.axis();
        int aAxis = FreeformRegion.aAxis(axis);
        int bAxis = FreeformRegion.bAxis(axis);
        double sign = this.variant == 0 ? 1.0 : -1.0;
        this.sideX = aAxis == FreeformRegion.AXIS_X ? 1.0 : 0.0;
        this.sideY = 0.0;
        this.sideZ = aAxis == FreeformRegion.AXIS_Z ? 1.0 : 0.0;
        this.upX = 0.0;
        this.upY = bAxis == FreeformRegion.AXIS_Y ? 1.0 : 0.0;
        this.upZ = bAxis == FreeformRegion.AXIS_Z ? 1.0 : 0.0;
        this.normalX = axis == FreeformRegion.AXIS_X ? sign : 0.0;
        this.normalY = axis == FreeformRegion.AXIS_Y ? sign : 0.0;
        this.normalZ = axis == FreeformRegion.AXIS_Z ? sign : 0.0;
        this.yawDegrees = switch (axis) {
            case FreeformRegion.AXIS_X -> sign > 0.0 ? 270.0F : 90.0F;
            case FreeformRegion.AXIS_Z -> sign > 0.0 ? 0.0F : 180.0F;
            default -> 0.0F;
        };
        this.width = region.width();
        this.height = region.height();
        this.frame = 0.0;
        this.depth = 0.0;
        this.mount = GateMount.STANDING;
        this.flipped = false;
        this.tilt = GateTilt.NONE;
        double halfW = this.width * 0.5;
        double halfH = this.height * 0.5;
        this.originX = x - this.sideX * halfW - this.upX * halfH;
        this.originY = y - this.sideY * halfW - this.upY * halfH;
        this.originZ = z - this.sideZ * halfW - this.upZ * halfH;
        double ta = region.targetA() + 0.5;
        double tb = region.targetB() + 0.5;
        this.centerX = this.originX + this.sideX * ta + this.upX * tb;
        this.centerY = this.originY + this.sideY * ta + this.upY * tb;
        this.centerZ = this.originZ + this.sideZ * ta + this.upZ * tb;
        this.upright = axis != FreeformRegion.AXIS_Y;
        this.aligned = true;
        this.stages = List.of(new Passage(this.originX, this.originY, this.originZ, this.sideX, this.sideY, this.sideZ, this.upX, this.upY, this.upZ,
                this.normalX, this.normalY, this.normalZ, 0.0, this.width, 0.0, this.height, region));
        this.fouls = List.of(List.of());
    }

    public @Nullable FreeformRegion region() {
        return this.region;
    }

    public boolean freeform() {
        return this.region != null;
    }

    public GateGeometry at(double newX, double newY, double newZ) {
        if (this.region != null) {
            return new GateGeometry(newX, newY, newZ, this.region, this.variant);
        }
        return new GateGeometry(newX, newY, newZ, this.yawDegrees, this.width, this.height, this.frame, this.depth, this.mount, this.flipped, this.tilt,
                this.kind, this.variant);
    }

    public GateGeometry withTilt(GateTilt next) {
        if (this.region != null) {
            return this;
        }
        return new GateGeometry(this.x, this.y, this.z, this.yawDegrees, this.width, this.height, this.frame, this.depth, this.mount, this.flipped, next,
                this.kind, this.variant);
    }

    public GateGeometry withVariant(int next) {
        if (this.region != null) {
            return new GateGeometry(this.x, this.y, this.z, this.region, next);
        }
        return new GateGeometry(this.x, this.y, this.z, this.yawDegrees, this.width, this.height, this.frame, this.depth, this.mount, this.flipped,
                this.tilt, this.kind, next);
    }

    public CourseElement kind() {
        return this.kind;
    }

    public int variant() {
        return this.variant;
    }

    public boolean gate() {
        return this.kind.gate();
    }

    public double x() {
        return this.x;
    }

    public double y() {
        return this.y;
    }

    public double z() {
        return this.z;
    }

    public float yawDegrees() {
        return this.yawDegrees;
    }

    public double width() {
        return this.width;
    }

    public double height() {
        return this.height;
    }

    public double frame() {
        return this.frame;
    }

    public double depth() {
        return this.depth;
    }

    public GateMount mount() {
        return this.mount;
    }

    public boolean flipped() {
        return this.flipped;
    }

    public GateTilt tilt() {
        return this.tilt;
    }

    public boolean tilted() {
        return this.tilt.tilted();
    }

    public boolean upright() {
        return this.upright;
    }

    public boolean alignedFrame() {
        return this.aligned;
    }

    public float heading() {
        float value = (this.yawDegrees + this.tilt.yaw()) % 360.0F;
        return value < 0.0F ? value + 360.0F : value;
    }

    public static float snapYaw(float yawDegrees) {
        float wrapped = yawDegrees % 360.0F;
        if (wrapped < 0.0F) {
            wrapped += 360.0F;
        }
        float snapped = Math.round(wrapped / 90.0F) * 90.0F;
        return snapped >= 360.0F ? snapped - 360.0F : snapped;
    }

    private double radians() {
        return Math.toRadians(this.yawDegrees);
    }

    private static double clean(double value) {
        return Math.abs(value) < EPSILON ? 0.0 : value;
    }

    public double facingX() {
        return clean(-Math.sin(this.radians()));
    }

    public double facingZ() {
        return clean(Math.cos(this.radians()));
    }

    public double rightX() {
        return clean(Math.cos(this.radians()));
    }

    public double rightZ() {
        return clean(Math.sin(this.radians()));
    }

    public double normalX() {
        return this.normalX;
    }

    public double normalY() {
        return this.normalY;
    }

    public double normalZ() {
        return this.normalZ;
    }

    public double sideX() {
        return this.sideX;
    }

    public double sideY() {
        return this.sideY;
    }

    public double sideZ() {
        return this.sideZ;
    }

    public double upX() {
        return this.upX;
    }

    public double upY() {
        return this.upY;
    }

    public double upZ() {
        return this.upZ;
    }

    public double originX() {
        return this.originX;
    }

    public double originY() {
        return this.originY;
    }

    public double originZ() {
        return this.originZ;
    }

    public boolean axisAligned() {
        double rest = Math.abs(this.yawDegrees % 90.0);
        return rest < 1.0E-4 || Math.abs(rest - 90.0) < 1.0E-4;
    }

    public double centerX() {
        return this.centerX;
    }

    public double centerY() {
        return this.centerY;
    }

    public double centerZ() {
        return this.centerZ;
    }

    public double worldX(double side, double up, double depth) {
        return this.originX + this.sideX * side + this.upX * up + this.normalX * depth;
    }

    public double worldY(double side, double up, double depth) {
        return this.originY + this.sideY * side + this.upY * up + this.normalY * depth;
    }

    public double worldZ(double side, double up, double depth) {
        return this.originZ + this.sideZ * side + this.upZ * up + this.normalZ * depth;
    }

    public double localSide(double px, double py, double pz) {
        if (this.upright) {
            return (px - this.originX) * this.sideX + (pz - this.originZ) * this.sideZ;
        }
        return (px - this.originX) * this.sideX + (py - this.originY) * this.sideY + (pz - this.originZ) * this.sideZ;
    }

    public double localUp(double px, double py, double pz) {
        if (this.upright) {
            return py - this.originY;
        }
        return (px - this.originX) * this.upX + (py - this.originY) * this.upY + (pz - this.originZ) * this.upZ;
    }

    public double localDepth(double px, double py, double pz) {
        return this.signedDistance(px, py, pz);
    }

    public double innerHalfWidth() {
        return this.width * 0.5 - this.frame;
    }

    public boolean insideOpening(double side, double up) {
        return Math.abs(side) < this.innerHalfWidth() && up > this.frame && up < this.height - this.frame;
    }

    public double signedDistance(double px, double pz) {
        return (px - this.originX) * this.normalX + (pz - this.originZ) * this.normalZ;
    }

    public double signedDistance(double px, double py, double pz) {
        if (this.upright) {
            return this.signedDistance(px, pz);
        }
        return (px - this.originX) * this.normalX + (py - this.originY) * this.normalY + (pz - this.originZ) * this.normalZ;
    }

    public double crossing(double x0, double y0, double z0, double x1, double y1, double z1, boolean forward) {
        if (!this.kind.gate()) {
            return NO_CROSSING;
        }
        double d0 = this.signedDistance(x0, y0, z0);
        double d1 = this.signedDistance(x1, y1, z1);
        boolean crosses = forward ? d0 < 0.0 && d1 >= 0.0 : d0 > 0.0 && d1 <= 0.0;
        if (!crosses) {
            return NO_CROSSING;
        }
        double t = d0 / (d0 - d1);
        double hx = x0 + (x1 - x0) * t;
        double hy = y0 + (y1 - y0) * t;
        double hz = z0 + (z1 - z0) * t;
        return this.insideOpening(this.localSide(hx, hy, hz), this.localUp(hx, hy, hz)) ? t : NO_CROSSING;
    }

    public double halfExtentX() {
        return Math.abs(this.sideX) * this.width * 0.5 + Math.abs(this.normalX) * this.depth * 0.5;
    }

    public double halfExtentZ() {
        return Math.abs(this.sideZ) * this.width * 0.5 + Math.abs(this.normalZ) * this.depth * 0.5;
    }

    public double topExtent() {
        return this.mount == GateMount.HANGING ? this.height + GateMount.HANG_DROP : this.height;
    }

    public double ceilingY() {
        return this.y + this.height + GateMount.HANG_DROP;
    }

    public double postOffset() {
        return this.width * 0.5 - this.frame * 0.5;
    }

    public void bounds(BoxSink sink) {
        if (this.region != null) {
            double ax = Math.abs(this.normalX) * 0.5;
            double ay = Math.abs(this.normalY) * 0.5;
            double az = Math.abs(this.normalZ) * 0.5;
            double ex = this.sideX * this.width + this.upX * this.height;
            double ey = this.sideY * this.width + this.upY * this.height;
            double ez = this.sideZ * this.width + this.upZ * this.height;
            sink.accept(this.originX - ax, this.originY - ay, this.originZ - az, this.originX + ex + ax, this.originY + ey + ay,
                    this.originZ + ez + az);
            return;
        }
        if (!this.kind.gate()) {
            double[] hull = {Double.POSITIVE_INFINITY, Double.POSITIVE_INFINITY, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY,
                    Double.NEGATIVE_INFINITY, Double.NEGATIVE_INFINITY};
            this.elementParts(box -> {
                hull[0] = Math.min(hull[0], box.minX());
                hull[1] = Math.min(hull[1], box.minY());
                hull[2] = Math.min(hull[2], box.minZ());
                hull[3] = Math.max(hull[3], box.maxX());
                hull[4] = Math.max(hull[4], box.maxY());
                hull[5] = Math.max(hull[5], box.maxZ());
            });
            sink.accept(hull[0], hull[1], hull[2], hull[3], hull[4], hull[5]);
            return;
        }
        if (this.upright) {
            double ex = this.halfExtentX();
            double ez = this.halfExtentZ();
            double top = this.tilt.none() ? this.topExtent() : this.ceilingTop() - this.originY;
            sink.accept(this.originX - ex, this.originY, this.originZ - ez, this.originX + ex, this.originY + top, this.originZ + ez);
            return;
        }
        double half = this.width * 0.5;
        double n = this.depth * 0.5;
        if (this.tilt.none()) {
            this.local(-half, half, 0.0, this.height, -n, n, sink);
            return;
        }
        double[] hull = {Double.POSITIVE_INFINITY, Double.POSITIVE_INFINITY, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY,
                Double.NEGATIVE_INFINITY, Double.NEGATIVE_INFINITY};
        BoxSink grow = (minX, minY, minZ, maxX, maxY, maxZ) -> {
            hull[0] = Math.min(hull[0], minX);
            hull[1] = Math.min(hull[1], minY);
            hull[2] = Math.min(hull[2], minZ);
            hull[3] = Math.max(hull[3], maxX);
            hull[4] = Math.max(hull[4], maxY);
            hull[5] = Math.max(hull[5], maxZ);
        };
        this.local(-half, half, 0.0, this.height, -n, n, grow);
        if (this.mount == GateMount.HANGING) {
            this.straps(grow);
        }
        sink.accept(hull[0], hull[1], hull[2], hull[3], hull[4], hull[5]);
    }

    private double ceilingTop() {
        return this.mount == GateMount.HANGING ? this.ceilingY() : this.originY + this.height;
    }

    public void boxes(BoxSink sink) {
        this.boxes(sink, GateStacking.Base.BAR);
    }

    public void boxes(BoxSink sink, GateStacking.Base base) {
        if (!this.kind.gate()) {
            this.elementParts(box -> {
                if (box.aligned() || vertical(box)) {
                    sink.accept(box.minX(), box.minY(), box.minZ(), box.maxX(), box.maxY(), box.maxZ());
                } else {
                    segments(box, sink);
                }
            });
            return;
        }
        if (!this.tilt.none() && !this.upright && !this.aligned) {
            this.parts(box -> segments(box, sink), base);
            return;
        }
        if (!this.tilt.none() && this.aligned) {
            this.parts(box -> sink.accept(box.minX(), box.minY(), box.minZ(), box.maxX(), box.maxY(), box.maxZ()), base);
            return;
        }
        double half = this.width * 0.5;
        double inner = half - this.frame;
        double foot = base == GateStacking.Base.STACKED ? this.frame : 0.0;
        this.part(-half, -inner, foot, this.height, sink);
        this.part(inner, half, foot, this.height, sink);
        if (base.bottomBar()) {
            this.part(-inner, inner, 0.0, this.frame, sink);
        }
        this.part(-inner, inner, this.height - this.frame, this.height, sink);
        if (this.mount == GateMount.HANGING) {
            if (this.tilt.none()) {
                double h = HANGER_SIZE * 0.5;
                double post = half - this.frame * 0.5;
                this.local(-post - h, -post + h, this.height, this.height + GateMount.HANG_DROP, -h, h, sink);
                this.local(post - h, post + h, this.height, this.height + GateMount.HANG_DROP, -h, h, sink);
            } else {
                this.straps(sink);
            }
        }
    }

    public void parts(PartSink sink, GateStacking.Base base) {
        if (!this.kind.gate()) {
            this.elementParts(sink);
            return;
        }
        double half = this.width * 0.5;
        double inner = half - this.frame;
        double foot = base == GateStacking.Base.STACKED ? this.frame : 0.0;
        double n = this.depth * 0.5;
        sink.accept(this.oriented(-half, -inner, foot, this.height, -n, n));
        sink.accept(this.oriented(inner, half, foot, this.height, -n, n));
        if (base.bottomBar()) {
            sink.accept(this.oriented(-inner, inner, 0.0, this.frame, -n, n));
        }
        sink.accept(this.oriented(-inner, inner, this.height - this.frame, this.height, -n, n));
        if (this.mount == GateMount.HANGING) {
            if (this.tilt.none()) {
                double h = HANGER_SIZE * 0.5;
                double post = half - this.frame * 0.5;
                sink.accept(this.oriented(-post - h, -post + h, this.height, this.height + GateMount.HANG_DROP, -h, h));
                sink.accept(this.oriented(post - h, post + h, this.height, this.height + GateMount.HANG_DROP, -h, h));
            } else {
                this.straps((minX, minY, minZ, maxX, maxY, maxZ) -> sink.accept(OrientedBox.ofBounds(minX, minY, minZ, maxX, maxY, maxZ)));
            }
        }
    }

    public OrientedBox oriented(double side0, double side1, double up0, double up1, double depth0, double depth1) {
        double s = (side0 + side1) * 0.5;
        double u = (up0 + up1) * 0.5;
        double d = (depth0 + depth1) * 0.5;
        return new OrientedBox(this.worldX(s, u, d), this.worldY(s, u, d), this.worldZ(s, u, d), this.sideX, this.sideY, this.sideZ, this.upX,
                this.upY, this.upZ, this.normalX, this.normalY, this.normalZ, (side1 - side0) * 0.5, (up1 - up0) * 0.5, (depth1 - depth0) * 0.5);
    }

    public void straps(BoxSink sink) {
        double h = HANGER_SIZE * 0.5;
        double post = this.postOffset();
        double ceiling = this.ceilingY();
        for (int side = -1; side <= 1; side += 2) {
            double ax = this.worldX(side * post, this.height, 0.0);
            double ay = this.worldY(side * post, this.height, 0.0);
            double az = this.worldZ(side * post, this.height, 0.0);
            if (ceiling - ay > 1.0E-3) {
                sink.accept(ax - h, ay, az - h, ax + h, ceiling, az + h);
            }
        }
    }

    public static void segments(OrientedBox box, BoxSink sink) {
        int longest = 0;
        for (int axis = 1; axis < 3; axis++) {
            if (box.half(axis) > box.half(longest)) {
                longest = axis;
            }
        }
        double length = box.half(longest) * 2.0;
        int count = Math.max(1, (int) Math.ceil(length / SEGMENT_LENGTH - 1.0E-9));
        double step = length / count;
        double lx = box.axisX(longest);
        double ly = box.axisY(longest);
        double lz = box.axisZ(longest);
        double restX = 0.0;
        double restY = 0.0;
        double restZ = 0.0;
        for (int axis = 0; axis < 3; axis++) {
            if (axis != longest) {
                restX += Math.abs(box.axisX(axis)) * box.half(axis);
                restY += Math.abs(box.axisY(axis)) * box.half(axis);
                restZ += Math.abs(box.axisZ(axis)) * box.half(axis);
            }
        }
        double halfStep = step * 0.5;
        double ex = restX + Math.abs(lx) * halfStep;
        double ey = restY + Math.abs(ly) * halfStep;
        double ez = restZ + Math.abs(lz) * halfStep;
        for (int i = 0; i < count; i++) {
            double t = -box.half(longest) + step * (i + 0.5);
            double cx = box.cx() + lx * t;
            double cy = box.cy() + ly * t;
            double cz = box.cz() + lz * t;
            sink.accept(cx - ex, cy - ey, cz - ez, cx + ex, cy + ey, cz + ez);
        }
    }

    public int boxCount(GateStacking.Base base) {
        if (!this.kind.gate()) {
            return switch (this.kind) {
                case FLAG -> 2;
                case CUBE -> 12;
                case FREEFORM -> 0;
                default -> 1;
            };
        }
        return (base.bottomBar() ? 4 : 3) + (this.mount == GateMount.HANGING ? 2 : 0);
    }

    private void part(double side0, double side1, double up0, double up1, BoxSink sink) {
        if (!this.upright) {
            double n = this.depth * 0.5;
            this.local(side0, side1, up0, up1, -n, n, sink);
            return;
        }
        boolean straight = this.tilt.none() ? this.axisAligned() : this.aligned;
        int segments = straight ? 1 : Math.max(1, (int) Math.ceil((side1 - side0) / SEGMENT_LENGTH));
        double step = (side1 - side0) / segments;
        double n = this.depth * 0.5;
        double sx = this.sideX;
        double sz = this.sideZ;
        double nx = this.normalX;
        double nz = this.normalZ;
        for (int i = 0; i < segments; i++) {
            double a = side0 + step * i;
            double b = a + step;
            double minX = Double.POSITIVE_INFINITY;
            double maxX = Double.NEGATIVE_INFINITY;
            double minZ = Double.POSITIVE_INFINITY;
            double maxZ = Double.NEGATIVE_INFINITY;
            for (int corner = 0; corner < 4; corner++) {
                double s = (corner & 1) == 0 ? a : b;
                double d = (corner & 2) == 0 ? -n : n;
                double cx = this.originX + sx * s + nx * d;
                double cz = this.originZ + sz * s + nz * d;
                minX = Math.min(minX, cx);
                maxX = Math.max(maxX, cx);
                minZ = Math.min(minZ, cz);
                maxZ = Math.max(maxZ, cz);
            }
            sink.accept(minX, this.originY + up0, minZ, maxX, this.originY + up1, maxZ);
        }
    }

    private void local(double side0, double side1, double up0, double up1, double depth0, double depth1, BoxSink sink) {
        double minX = Double.POSITIVE_INFINITY;
        double minY = Double.POSITIVE_INFINITY;
        double minZ = Double.POSITIVE_INFINITY;
        double maxX = Double.NEGATIVE_INFINITY;
        double maxY = Double.NEGATIVE_INFINITY;
        double maxZ = Double.NEGATIVE_INFINITY;
        for (int corner = 0; corner < 8; corner++) {
            double s = (corner & 1) == 0 ? side0 : side1;
            double u = (corner & 2) == 0 ? up0 : up1;
            double d = (corner & 4) == 0 ? depth0 : depth1;
            double cx = this.worldX(s, u, d);
            double cy = this.worldY(s, u, d);
            double cz = this.worldZ(s, u, d);
            minX = Math.min(minX, cx);
            minY = Math.min(minY, cy);
            minZ = Math.min(minZ, cz);
            maxX = Math.max(maxX, cx);
            maxY = Math.max(maxY, cy);
            maxZ = Math.max(maxZ, cz);
        }
        sink.accept(minX, minY, minZ, maxX, maxY, maxZ);
    }

    public static double pathLength(List<GateGeometry> gates) {
        if (gates.size() < 2) {
            return 0.0;
        }
        double total = 0.0;
        for (int i = 0; i < gates.size(); i++) {
            GateGeometry a = gates.get(i);
            GateGeometry b = gates.get((i + 1) % gates.size());
            double dx = b.centerX() - a.centerX();
            double dy = b.centerY() - a.centerY();
            double dz = b.centerZ() - a.centerZ();
            total += Math.sqrt(dx * dx + dy * dy + dz * dz);
        }
        return total;
    }

    public boolean sameAs(GateGeometry other) {
        return other != null && this.kind == other.kind && this.variant == other.variant && Objects.equals(this.region, other.region)
                && this.mount == other.mount && this.flipped == other.flipped && this.tilt.equals(other.tilt)
                && Math.abs(this.x - other.x) < SAME_EPSILON && Math.abs(this.y - other.y) < SAME_EPSILON && Math.abs(this.z - other.z) < SAME_EPSILON
                && Math.abs(this.width - other.width) < SAME_EPSILON && Math.abs(this.height - other.height) < SAME_EPSILON
                && Math.abs(GateTilt.wrap(this.yawDegrees - other.yawDegrees)) < 1.0E-3F;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof GateGeometry that)) {
            return false;
        }
        return Double.compare(this.x, that.x) == 0 && Double.compare(this.y, that.y) == 0 && Double.compare(this.z, that.z) == 0
                && Float.compare(this.yawDegrees, that.yawDegrees) == 0 && Double.compare(this.width, that.width) == 0
                && Double.compare(this.height, that.height) == 0 && Double.compare(this.frame, that.frame) == 0
                && Double.compare(this.depth, that.depth) == 0 && this.mount == that.mount && this.flipped == that.flipped
                && this.tilt.equals(that.tilt) && this.kind == that.kind && this.variant == that.variant && Objects.equals(this.region, that.region);
    }

    @Override
    public int hashCode() {
        int hash = Objects.hash(this.x, this.y, this.z, this.yawDegrees, this.width, this.height, this.frame, this.depth, this.mount, this.flipped,
                this.tilt);
        if (this.region != null) {
            hash = hash * 31 + this.region.hashCode();
        }
        return this.kind.gate() ? hash : hash * 31 + this.kind.ordinal() * 7 + this.variant;
    }

    @Override
    public String toString() {
        return "GateGeometry[x=" + this.x + ", y=" + this.y + ", z=" + this.z + ", yawDegrees=" + this.yawDegrees + ", width=" + this.width
                + ", height=" + this.height + ", frame=" + this.frame + ", depth=" + this.depth + ", mount=" + this.mount + ", flipped="
                + this.flipped + ", tilt=" + this.tilt + (this.kind.gate() ? "" : ", kind=" + this.kind + ", variant=" + this.variant)
                + (this.region == null ? "" : ", region=" + this.region) + "]";
    }

    private static boolean vertical(OrientedBox box) {
        int longest = 0;
        for (int axis = 1; axis < 3; axis++) {
            if (box.half(axis) > box.half(longest)) {
                longest = axis;
            }
        }
        return Math.abs(box.axisY(longest)) > 1.0 - 1.0E-9;
    }

    public List<Passage> stages() {
        return this.stages;
    }

    public List<Passage> fouls(int stage) {
        if (this.fouls.isEmpty()) {
            return List.of();
        }
        return this.fouls.get(Math.max(0, Math.min(this.fouls.size() - 1, stage)));
    }

    public double stageCrossing(int stage, double x0, double y0, double z0, double x1, double y1, double z1) {
        if (this.kind.gate()) {
            return stage == 0 ? this.crossing(x0, y0, z0, x1, y1, z1, true) : NO_CROSSING;
        }
        return stage >= 0 && stage < this.stages.size() ? this.stages.get(stage).crossing(x0, y0, z0, x1, y1, z1, true) : NO_CROSSING;
    }

    public double bannerHeight() {
        return Math.min(CourseElement.BANNER_MAX_HEIGHT, this.height * CourseElement.BANNER_SHARE);
    }

    public int passSign() {
        return this.variant == CourseElement.LEFT ? 1 : -1;
    }

    public double exitX() {
        return this.exitAxis(0);
    }

    public double exitZ() {
        return this.exitAxis(2);
    }

    private double exitAxis(int component) {
        double side = component == 0 ? this.sideX : this.sideZ;
        double normal = component == 0 ? this.normalX : this.normalZ;
        return switch (this.variant) {
            case CourseElement.EXIT_RIGHT -> -side;
            case CourseElement.EXIT_BACK -> -normal;
            case CourseElement.EXIT_LEFT -> side;
            default -> normal;
        };
    }

    private void elementParts(PartSink sink) {
        double t = this.frame * 0.5;
        double h = this.height;
        switch (this.kind) {
            case POLE, GATE_POLE -> sink.accept(this.oriented(-t, t, 0.0, h, -t, t));
            case FLAG -> {
                sink.accept(this.oriented(-t, t, 0.0, h, -t, t));
                double top = h - CourseElement.BANNER_TOP_GAP;
                double bottom = top - this.bannerHeight();
                double d = CourseElement.BANNER_DEPTH * 0.5;
                if (this.passSign() > 0) {
                    sink.accept(this.oriented(t, t + CourseElement.BANNER_WIDTH, bottom, top, -d, d));
                } else {
                    sink.accept(this.oriented(-t - CourseElement.BANNER_WIDTH, -t, bottom, top, -d, d));
                }
            }
            case CUBE -> {
                double half = this.width * 0.5;
                double bar = this.frame;
                double inner = half - bar;
                for (int a = -1; a <= 1; a += 2) {
                    for (int b = -1; b <= 1; b += 2) {
                        sink.accept(this.oriented(span(a, inner, half, true), span(a, inner, half, false), 0.0, h, span(b, inner, half, true),
                                span(b, inner, half, false)));
                    }
                }
                for (double level : new double[]{0.0, h - bar}) {
                    for (int a = -1; a <= 1; a += 2) {
                        sink.accept(this.oriented(-inner, inner, level, level + bar, span(a, inner, half, true), span(a, inner, half, false)));
                        sink.accept(this.oriented(span(a, inner, half, true), span(a, inner, half, false), level, level + bar, -inner, inner));
                    }
                }
            }
            case GATE, FREEFORM -> {
            }
        }
    }

    private static double span(int sign, double inner, double half, boolean low) {
        if (sign < 0) {
            return low ? -half : -inner;
        }
        return low ? inner : half;
    }

    private Passage passage(double ox, double oy, double oz, double sx, double sy, double sz, double ux, double uy, double uz, double nx, double ny,
                            double nz, double side0, double side1, double up0, double up1) {
        return new Passage(ox, oy, oz, sx, sy, sz, ux, uy, uz, nx, ny, nz, side0, side1, up0, up1);
    }

    private Passage ray(double dx, double dz, double nx, double nz) {
        return this.passage(this.originX, this.originY, this.originZ, dx, 0.0, dz, 0.0, 1.0, 0.0, nx, 0.0, nz, 0.0, this.width,
                -CourseElement.BELOW, this.height);
    }

    private Passage face(int exit) {
        double half = this.width * 0.5;
        double bar = this.frame;
        double inner = half - bar;
        double ex;
        double ez;
        double px;
        double pz;
        switch (exit) {
            case CourseElement.EXIT_RIGHT -> {
                ex = -this.sideX;
                ez = -this.sideZ;
                px = this.normalX;
                pz = this.normalZ;
            }
            case CourseElement.EXIT_BACK -> {
                ex = -this.normalX;
                ez = -this.normalZ;
                px = this.sideX;
                pz = this.sideZ;
            }
            case CourseElement.EXIT_LEFT -> {
                ex = this.sideX;
                ez = this.sideZ;
                px = this.normalX;
                pz = this.normalZ;
            }
            default -> {
                ex = this.normalX;
                ez = this.normalZ;
                px = this.sideX;
                pz = this.sideZ;
            }
        }
        double reach = half - bar * 0.5;
        return this.passage(this.originX + ex * reach, this.originY, this.originZ + ez * reach, px, 0.0, pz, 0.0, 1.0, 0.0, ex, 0.0, ez, -inner, inner,
                bar, this.height - bar);
    }

    private List<Passage> elementStages() {
        double lx = this.sideX;
        double lz = this.sideZ;
        double fx = this.normalX;
        double fz = this.normalZ;
        int sign = this.passSign();
        return switch (this.kind) {
            case POLE -> List.of(this.ray(lx * sign, lz * sign, fx, fz));
            case FLAG -> List.of(
                    this.ray(-lx * sign, -lz * sign, fx, fz),
                    this.ray(fx, fz, lx * sign, lz * sign),
                    this.ray(lx * sign, lz * sign, -fx, -fz));
            case CUBE -> {
                double half = this.width * 0.5;
                double inner = half - this.frame;
                Passage top = this.passage(this.originX, this.originY + this.height - this.frame * 0.5, this.originZ, lx, 0.0, lz, fx, 0.0, fz, 0.0,
                        -1.0, 0.0, -inner, inner, -inner, inner);
                yield List.of(top, this.face(this.variant));
            }
            case GATE, FREEFORM, GATE_POLE -> List.of();
        };
    }

    private List<Passage> elementFouls(int stage) {
        int sign = this.passSign();
        return switch (this.kind) {
            case POLE -> List.of(this.ray(-this.sideX * sign, -this.sideZ * sign, this.normalX, this.normalZ));
            case FLAG -> List.of(this.ray(this.sideX * sign, this.sideZ * sign, this.normalX, this.normalZ));
            case CUBE -> {
                List<Passage> faces = new ArrayList<>(4);
                for (int exit = 0; exit < 4; exit++) {
                    if (stage == 0 || exit != this.variant) {
                        faces.add(this.face(exit));
                    }
                }
                yield faces;
            }
            case GATE, FREEFORM, GATE_POLE -> List.of();
        };
    }

    public List<Waypoint> waypoints() {
        double lx = this.sideX;
        double lz = this.sideZ;
        double fx = this.normalX;
        double fz = this.normalZ;
        double t = this.frame * 0.5;
        int sign = this.passSign();
        return switch (this.kind) {
            case GATE, FREEFORM, GATE_POLE -> List.of(new Waypoint(this.centerX, this.centerY, this.centerZ, this.normalX, this.normalY, this.normalZ));
            case POLE -> {
                double r = t + Math.max(0.8, Math.min(2.0, this.width * 0.35));
                double y = this.originY + lineHeight(this.height, 0.5);
                yield List.of(new Waypoint(this.originX + lx * sign * r, y, this.originZ + lz * sign * r, fx, 0.0, fz));
            }
            case FLAG -> {
                double r = t + Math.max(1.0, Math.min(2.2, this.width * 0.35));
                double y = this.originY + lineHeight(this.height, 0.45);
                yield List.of(
                        new Waypoint(this.originX - lx * sign * r, y, this.originZ - lz * sign * r, fx, 0.0, fz),
                        new Waypoint(this.originX + fx * r, y, this.originZ + fz * r, lx * sign, 0.0, lz * sign),
                        new Waypoint(this.originX + lx * sign * r, y, this.originZ + lz * sign * r, -fx, 0.0, -fz));
            }
            case CUBE -> {
                double ex = this.exitX();
                double ez = this.exitZ();
                double half = this.width * 0.5;
                double middle = this.originY + this.height * 0.45;
                yield List.of(
                        new Waypoint(this.originX, this.originY + this.height + 1.0, this.originZ, 0.0, -1.0, 0.0),
                        new Waypoint(this.originX + ex * half, middle, this.originZ + ez * half, ex, 0.0, ez));
            }
        };
    }

    private static double lineHeight(double height, double share) {
        double top = Math.max(0.6, height - 0.4);
        return Math.max(0.6, Math.min(top, height * share));
    }

    @FunctionalInterface
    public interface BoxSink {
        void accept(double minX, double minY, double minZ, double maxX, double maxY, double maxZ);
    }

    @FunctionalInterface
    public interface PartSink {
        void accept(OrientedBox box);
    }
}
