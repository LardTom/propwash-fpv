package de.lardtom.propwash.race;

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
            if (supported) {
                return STACKED;
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
        if (Math.abs(a.x() - b.x()) > EPSILON || Math.abs(a.z() - b.z()) > EPSILON) {
            return false;
        }
        if (a.tilt().yaw() == 0.0F && b.tilt().yaw() == 0.0F) {
            float diff = Math.abs(GateGeometry.snapYaw(a.yawDegrees()) - GateGeometry.snapYaw(b.yawDegrees()));
            return diff < 1.0F || Math.abs(diff - 180.0F) < 1.0F;
        }
        float diff = Math.abs(a.heading() - b.heading()) % 180.0F;
        return diff < 1.0F || diff > 179.0F;
    }

    public static boolean restsOn(GateGeometry upper, GateGeometry lower) {
        return sameColumn(upper, lower) && Math.abs(upper.y() - seat(lower)) < EPSILON;
    }

    public static boolean fits(double upperWidth, double lowerWidth) {
        return upperWidth <= lowerWidth + EPSILON;
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
        return Base.of(below(gates, index) >= 0, above(gates, index) >= 0);
    }

    public static int top(List<GateGeometry> gates, int index) {
        List<Integer> column = column(gates, index);
        return column.getLast();
    }

    public static List<Drop> afterRemoval(List<GateGeometry> gates, int removed) {
        List<Drop> drops = new ArrayList<>();
        GateGeometry gone = gates.get(removed);
        double support = gone.y();
        int current = above(gates, removed);
        List<Integer> seen = new ArrayList<>();
        while (current >= 0 && !seen.contains(current)) {
            seen.add(current);
            GateGeometry gate = gates.get(current);
            drops.add(new Drop(current, support));
            support += gate.height() - gate.frame();
            current = above(gates, current);
        }
        return drops;
    }
}
