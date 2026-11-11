package de.lardtom.propwash.race;

import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

public final class GateStacking {
    public static final int MAX_STACK = 4;
    public static final double EPSILON = 1.0E-3;

    private GateStacking() {
    }

    public enum Refusal {
        NONE,
        TOO_WIDE,
        TOO_HIGH
    }

    public enum Base {
        BAR,
        LEGS,
        STACKED,
        MOUNTED;

        public static Base of(boolean supported, boolean covered) {
            return of(supported, covered, false);
        }

        public static Base of(boolean supported, boolean covered, boolean crossed) {
            if (supported) {
                return crossed ? MOUNTED : STACKED;
            }
            return covered ? LEGS : BAR;
        }

        public boolean bottomBar() {
            return this == BAR || this == MOUNTED;
        }

        public boolean feet() {
            return this == BAR || this == LEGS;
        }
    }

    public record Drop(int index, double y) {
    }

    public static final double SNAP = 1.0;
    public static final double JOINT_EPSILON = 1.0E-3;
    public static final double ALIGN_EPSILON = 1.0E-4;
    public static final double NEIGHBOUR_REACH = 1.5;

    public record Joints(int mask, boolean linked) {
        public static final Joints NONE = new Joints(0, false);
    }

    public enum LidRefusal {
        NONE,
        NO_RING,
        BLOCKED
    }

    public record Lid(GateGeometry geometry, LidRefusal refusal) {
    }

    public enum StackRefusal {
        NONE,
        STANDING_ONLY,
        OCCUPIED,
        TOO_WIDE,
        TOO_HIGH,
        BLOCKED
    }

    public record Stack(GateGeometry geometry, Base base, StackRefusal refusal) {
    }

    public static boolean jointable(GateGeometry gate) {
        return gate.gate() && !gate.freeform() && gate.mount() == GateMount.STANDING && !gate.tilted();
    }

    static boolean lidShape(GateGeometry gate) {
        return gate.gate() && !gate.freeform() && gate.mount() == GateMount.FLOOR_FLAT && !gate.tilted();
    }

    static double postX(GateGeometry gate, int side) {
        return gate.x() + gate.sideX() * side * gate.postOffset();
    }

    static double postZ(GateGeometry gate, int side) {
        return gate.z() + gate.sideZ() * side * gate.postOffset();
    }

    static boolean parallel(GateGeometry a, GateGeometry b) {
        return Math.abs(a.sideX() * b.sideZ() - a.sideZ() * b.sideX()) < ALIGN_EPSILON;
    }

    static boolean perpendicular(GateGeometry a, GateGeometry b) {
        return Math.abs(a.sideX() * b.sideX() + a.sideZ() * b.sideZ()) < ALIGN_EPSILON;
    }

    public static boolean compatible(GateGeometry a, GateGeometry b) {
        if (!jointable(a) || !jointable(b)) {
            return false;
        }
        if (Math.abs(a.y() - b.y()) > JOINT_EPSILON || Math.abs(a.height() - b.height()) > JOINT_EPSILON
                || Math.abs(a.frame() - b.frame()) > JOINT_EPSILON || Math.abs(a.depth() - b.depth()) > JOINT_EPSILON) {
            return false;
        }
        return parallel(a, b) || (perpendicular(a, b) && Math.abs(a.frame() - a.depth()) < JOINT_EPSILON);
    }

    public static int sharedSide(GateGeometry own, GateGeometry other) {
        if (!compatible(own, other)) {
            return 0;
        }
        for (int side = -1; side <= 1; side += 2) {
            double px = postX(own, side);
            double pz = postZ(own, side);
            for (int theirs = -1; theirs <= 1; theirs += 2) {
                if (Math.abs(postX(other, theirs) - px) < JOINT_EPSILON && Math.abs(postZ(other, theirs) - pz) < JOINT_EPSILON) {
                    return side;
                }
            }
        }
        return 0;
    }

    static boolean before(GateGeometry a, GateGeometry b) {
        if (Math.abs(a.x() - b.x()) > JOINT_EPSILON) {
            return a.x() < b.x();
        }
        return a.z() < b.z() - JOINT_EPSILON;
    }

    static int bit(int side) {
        return side < 0 ? GateGeometry.JOINT_LEFT : GateGeometry.JOINT_RIGHT;
    }

    public static Joints joints(GateGeometry own, List<GateGeometry> others) {
        if (lidShape(own)) {
            for (GateGeometry other : others) {
                if (covers(own, other)) {
                    return new Joints(0, true);
                }
            }
            return Joints.NONE;
        }
        if (!jointable(own)) {
            return Joints.NONE;
        }
        int mask = 0;
        boolean linked = false;
        for (GateGeometry other : others) {
            int side = sharedSide(own, other);
            if (side != 0) {
                linked = true;
                if (before(other, own)) {
                    mask |= bit(side);
                }
            } else if (covers(other, own)) {
                linked = true;
                mask |= GateGeometry.JOINT_TOP;
            }
        }
        return new Joints(mask, linked);
    }

    public static int placementJoints(GateGeometry placed, List<GateGeometry> others) {
        int mask = 0;
        for (GateGeometry other : others) {
            int side = sharedSide(placed, other);
            if (side != 0) {
                mask |= bit(side);
            }
        }
        return mask;
    }

    public static GateGeometry joined(GateGeometry stacked, List<GateGeometry> neighbours) {
        if (!jointable(stacked)) {
            return stacked;
        }
        int mask = placementJoints(stacked, neighbours);
        return mask == 0 ? stacked : stacked.withJoints(mask);
    }

    public static boolean covers(GateGeometry lid, GateGeometry side) {
        if (!lidShape(lid) || !jointable(side) || Math.abs(lid.frame() - side.frame()) > JOINT_EPSILON
                || Math.abs(lid.depth() - side.frame()) > JOINT_EPSILON) {
            return false;
        }
        double barY = side.y() + side.height() - side.frame() * 0.5;
        if (Math.abs(lid.y() - barY) > JOINT_EPSILON) {
            return false;
        }
        double half = lid.width() * 0.5;
        double strip = lid.frame() * 0.5;
        double ms = lid.localSide(side.x(), barY, side.z());
        double mu = lid.localUp(side.x(), barY, side.z());
        boolean alongSide = Math.abs(Math.abs(ms) - (half - strip)) < JOINT_EPSILON && mu > -JOINT_EPSILON && mu < lid.height() + JOINT_EPSILON;
        boolean alongEnd = (Math.abs(mu - strip) < JOINT_EPSILON || Math.abs(mu - (lid.height() - strip)) < JOINT_EPSILON)
                && Math.abs(ms) < half + JOINT_EPSILON;
        if (!alongSide && !alongEnd) {
            return false;
        }
        for (int end = -1; end <= 1; end += 2) {
            double px = postX(side, end);
            double pz = postZ(side, end);
            double ps = lid.localSide(px, barY, pz);
            double pu = lid.localUp(px, barY, pz);
            if (Math.abs(ps) > half + JOINT_EPSILON || pu < -JOINT_EPSILON || pu > lid.height() + JOINT_EPSILON) {
                return false;
            }
        }
        return true;
    }

    static boolean inside(GateGeometry gate, double px, double pz) {
        double side = (px - gate.x()) * gate.sideX() + (pz - gate.z()) * gate.sideZ();
        double depth = (px - gate.x()) * gate.normalX() + (pz - gate.z()) * gate.normalZ();
        return Math.abs(side) < gate.width() * 0.5 + JOINT_EPSILON && Math.abs(depth) < gate.depth() * 0.5 + JOINT_EPSILON;
    }

    public static GateGeometry snap(GateGeometry placed, List<GateGeometry> others) {
        if (!jointable(placed)) {
            return placed;
        }
        double bestX = 0.0;
        double bestZ = 0.0;
        double best = Double.POSITIVE_INFINITY;
        for (GateGeometry other : others) {
            if (!compatible(placed, other)) {
                continue;
            }
            for (int side = -1; side <= 1; side += 2) {
                for (int theirs = -1; theirs <= 1; theirs += 2) {
                    double dx = postX(other, theirs) - postX(placed, side);
                    double dz = postZ(other, theirs) - postZ(placed, side);
                    if (Math.abs(dx) > SNAP + JOINT_EPSILON || Math.abs(dz) > SNAP + JOINT_EPSILON) {
                        continue;
                    }
                    double far = postX(placed, -side) + dx;
                    double farZ = postZ(placed, -side) + dz;
                    if (inside(other, far, farZ)) {
                        continue;
                    }
                    double distance = dx * dx + dz * dz;
                    if (distance < best - 1.0E-9) {
                        best = distance;
                        bestX = dx;
                        bestZ = dz;
                    }
                }
            }
        }
        if (best == Double.POSITIVE_INFINITY || best < 1.0E-12) {
            return placed;
        }
        return placed.at(placed.x() + bestX, placed.y(), placed.z() + bestZ);
    }

    public static Lid lid(GateGeometry clicked, List<GateGeometry> others, GateSize size) {
        if (!jointable(clicked) || Math.abs(clicked.frame() - clicked.depth()) > JOINT_EPSILON
                || Math.abs(size.frame() - clicked.frame()) > JOINT_EPSILON || Math.abs(size.depth() - clicked.depth()) > JOINT_EPSILON) {
            return new Lid(clicked, LidRefusal.NO_RING);
        }
        GateGeometry wing = null;
        double sign = 0.0;
        for (GateGeometry other : others) {
            if (sharedSide(clicked, other) != 0 && perpendicular(clicked, other)) {
                double dot = (other.x() - clicked.x()) * clicked.normalX() + (other.z() - clicked.z()) * clicked.normalZ();
                if (Math.abs(dot) > JOINT_EPSILON) {
                    wing = other;
                    sign = Math.signum(dot);
                    break;
                }
            }
        }
        if (wing == null) {
            return new Lid(clicked, LidRefusal.NO_RING);
        }
        float yaw = GateGeometry.snapYaw(sign > 0.0 ? clicked.yawDegrees() : clicked.yawDegrees() + 180.0F);
        double inX = clicked.normalX() * sign;
        double inZ = clicked.normalZ() * sign;
        double strip = clicked.frame() * 0.5;
        double ox = clicked.x() - inX * strip;
        double oz = clicked.z() - inZ * strip;
        double oy = clicked.y() + clicked.height() - strip;
        GateTilt turn = new GateTilt(clicked.tilt().yaw(), 0.0F, 0.0F);
        GateGeometry geometry = GateGeometry.of(ox, oy, oz, yaw, size, GateMount.FLOOR_FLAT, false, clicked.width(), wing.width(), turn);
        if (!turn.none()) {
            geometry = geometry.at(ox + (ox - geometry.originX()), oy, oz + (oz - geometry.originZ()));
        }
        return new Lid(geometry, LidRefusal.NONE);
    }

    public static boolean underLid(double otherMinY, double otherMaxY, GateGeometry lid) {
        double top = lid.y() + lid.depth() * 0.5;
        return Math.abs(otherMaxY - top) < JOINT_EPSILON && otherMinY < top;
    }

    public static double seat(GateGeometry lower) {
        return lower.y() + lower.height() - lower.frame();
    }

    public static Base placedBase(GateGeometry gate) {
        return gate.tilted() ? Base.MOUNTED : gate.mount().base();
    }

    public static boolean stackable(GateGeometry gate) {
        return gate.gate() && gate.mount().stackable() && !gate.tilted();
    }

    public static boolean sameColumn(GateGeometry a, GateGeometry b) {
        if (!stackable(a) || !stackable(b)) {
            return false;
        }
        return Math.abs(a.x() - b.x()) <= EPSILON && Math.abs(a.z() - b.z()) <= EPSILON;
    }

    public static boolean aligned(GateGeometry a, GateGeometry b) {
        if (a.tilt().yaw() == 0.0F && b.tilt().yaw() == 0.0F) {
            float diff = Math.abs(GateGeometry.snapYaw(a.yawDegrees()) - GateGeometry.snapYaw(b.yawDegrees()));
            return diff < 1.0F || Math.abs(diff - 180.0F) < 1.0F;
        }
        float diff = Math.abs(a.heading() - b.heading()) % 180.0F;
        return diff < 1.0F || diff > 179.0F;
    }

    public static boolean crossed(GateGeometry upper, GateGeometry lower) {
        return !aligned(upper, lower);
    }

    public static double seat(GateGeometry upper, GateGeometry lower) {
        return aligned(upper, lower) ? seat(lower) : lower.y() + lower.height();
    }

    public static GateGeometry seatOn(GateGeometry upper, GateGeometry lower) {
        double y = seat(upper, lower);
        return Math.abs(upper.y() - y) < 1.0E-9 ? upper : upper.at(upper.x(), y, upper.z());
    }

    public static boolean seatedOn(GateGeometry upper, GateGeometry lower) {
        return sameColumn(upper, lower) && Math.abs(upper.y() - seat(upper, lower)) < EPSILON;
    }

    public static boolean restsOn(GateGeometry upper, GateGeometry lower) {
        return seatedOn(upper, lower) && fits(upper.width(), lower.width());
    }

    public static boolean fits(double upperWidth, double lowerWidth) {
        return upperWidth <= lowerWidth + EPSILON;
    }

    public static Refusal refusal(GateGeometry gate, List<GateGeometry> others) {
        if (!stackable(gate)) {
            return Refusal.NONE;
        }
        for (GateGeometry other : others) {
            if (seatedOn(gate, other) && !fits(gate.width(), other.width()) || seatedOn(other, gate) && !fits(other.width(), gate.width())) {
                return Refusal.TOO_WIDE;
            }
        }
        return 1 + chain(gate, others, true) + chain(gate, others, false) > MAX_STACK ? Refusal.TOO_HIGH : Refusal.NONE;
    }

    private static int chain(GateGeometry start, List<GateGeometry> others, boolean down) {
        boolean[] used = new boolean[others.size()];
        GateGeometry current = start;
        int count = 0;
        while (count <= MAX_STACK) {
            int found = -1;
            for (int i = 0; i < others.size() && found < 0; i++) {
                GateGeometry other = others.get(i);
                if (!used[i] && (down ? restsOn(current, other) : restsOn(other, current))) {
                    found = i;
                }
            }
            if (found < 0) {
                break;
            }
            used[found] = true;
            current = others.get(found);
            count++;
        }
        return count;
    }

    public static float stackYaw(float lowerYaw, float playerYaw) {
        float lower = GateGeometry.snapYaw(lowerYaw);
        float wanted = GateGeometry.snapYaw(playerYaw);
        float flipped = GateGeometry.snapYaw(lower + 180.0F);
        return Math.abs(wanted - flipped) < 1.0F ? flipped : lower;
    }

    public static GateGeometry stackOn(GateGeometry top, GateSize size, float yaw) {
        GateGeometry plain = GateGeometry.of(top.x(), seat(top), top.z(), GateGeometry.snapYaw(yaw), size);
        float turn = top.tilt().yaw();
        return turn == 0.0F ? plain : plain.withTilt(new GateTilt(turn, 0.0F, 0.0F));
    }

    public static GateGeometry stackOn(GateGeometry top, GateSize size, float yaw, float turn) {
        GateGeometry plain = stackOn(top, size, yaw);
        if (GateTilt.wrap(turn) == 0.0F) {
            return plain;
        }
        return seatOn(GatePlacement.turnedTo(plain, plain.heading() + turn), top);
    }

    public static GateGeometry stackFor(GateGeometry top, float topYaw, GateSize size, float playerYaw, GatePlacement.Preference preference) {
        return stackOn(top, size, stackYaw(topYaw, playerYaw), placementTurn(preference));
    }

    public static Base stackBase(GateGeometry upper, GateGeometry lower) {
        return crossed(upper, lower) ? Base.MOUNTED : Base.STACKED;
    }

    public static float placementTurn(GatePlacement.Preference preference) {
        GateMount floor = preference.floor();
        if (floor.turns() != 4 || !floor.stackable()) {
            return 0.0F;
        }
        return GateTilt.wrap(preference.floorTurn() * GatePlacement.QUARTER + preference.floorFine());
    }

    public static boolean halfTurn(float degrees) {
        float wrapped = Math.abs(GateTilt.wrap(degrees));
        return wrapped < 0.05F || Math.abs(wrapped - 180.0F) < 0.05F;
    }

    public static List<GateGeometry> turned(List<GateGeometry> column, int index, float heading) {
        GateGeometry old = column.get(index);
        float delta = heading - old.heading();
        GateGeometry first = turnedGate(old, heading);
        if (index > 0) {
            first = seatOn(first, column.get(index - 1));
        }
        double dy = first.y() - old.y();
        List<GateGeometry> out = new ArrayList<>(column.size() - index);
        out.add(first);
        for (int i = index + 1; i < column.size(); i++) {
            GateGeometry gate = column.get(i);
            GateGeometry next = turnedGate(gate, gate.heading() + delta);
            out.add(next.at(next.x(), gate.y() + dy, next.z()));
        }
        return out;
    }

    private static GateGeometry turnedGate(GateGeometry gate, float heading) {
        return GatePlacement.turnedTo(gate, heading).withScore(gate.score());
    }

    public static Refusal check(GateGeometry top, int stackSize, GateSize size) {
        if (!fits(size.width(), top.width())) {
            return Refusal.TOO_WIDE;
        }
        if (stackSize >= MAX_STACK) {
            return Refusal.TOO_HIGH;
        }
        return Refusal.NONE;
    }

    public static int above(List<GateGeometry> gates, int index) {
        GateGeometry lower = gates.get(index);
        for (int i = 0; i < gates.size(); i++) {
            if (i != index && restsOn(gates.get(i), lower)) {
                return i;
            }
        }
        return -1;
    }

    public static int below(List<GateGeometry> gates, int index) {
        GateGeometry upper = gates.get(index);
        for (int i = 0; i < gates.size(); i++) {
            if (i != index && restsOn(upper, gates.get(i))) {
                return i;
            }
        }
        return -1;
    }

    public static List<Integer> column(List<GateGeometry> gates, int index) {
        int bottom = index;
        for (int guard = 0; guard < gates.size(); guard++) {
            int next = below(gates, bottom);
            if (next < 0 || next == index) {
                break;
            }
            bottom = next;
        }
        List<Integer> column = new ArrayList<>();
        int current = bottom;
        while (current >= 0 && !column.contains(current) && column.size() < gates.size()) {
            column.add(current);
            current = above(gates, current);
        }
        return column;
    }

    public static Base base(List<GateGeometry> gates, int index) {
        GateGeometry gate = gates.get(index);
        GateMount mount = gate.mount();
        if (GatePoles.pole(gate)) {
            boolean supported = false;
            for (int i = 0; i < gates.size() && !supported; i++) {
                supported = i != index && GatePoles.restsOn(gate, gates.get(i));
            }
            return GatePoles.base(mount, supported);
        }
        if (gate.tilted()) {
            return Base.MOUNTED;
        }
        if (!mount.stackable()) {
            return mount.base();
        }
        int below = below(gates, index);
        return Base.of(below >= 0, above(gates, index) >= 0, below >= 0 && crossed(gate, gates.get(below)));
    }

    public static int top(List<GateGeometry> gates, int index) {
        List<Integer> column = column(gates, index);
        return column.getLast();
    }

    public static List<Drop> afterRemoval(List<GateGeometry> gates, int removed) {
        List<Drop> drops = new ArrayList<>();
        int current = above(gates, removed);
        if (current < 0) {
            return drops;
        }
        int below = below(gates, removed);
        double delta = dropDelta(gates.get(removed), gates.get(current), below < 0 ? null : gates.get(below));
        List<Integer> seen = new ArrayList<>();
        while (current >= 0 && !seen.contains(current)) {
            seen.add(current);
            drops.add(new Drop(current, gates.get(current).y() + delta));
            current = above(gates, current);
        }
        return drops;
    }

    public static double dropDelta(GateGeometry gone, GateGeometry first, @Nullable GateGeometry below) {
        double target = below == null ? gone.y() : seat(first, below);
        return target - first.y();
    }
}
