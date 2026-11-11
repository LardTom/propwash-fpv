package de.lardtom.propwash.race;

import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

public final class PassZoneOverlap {
    public static final double NEAR = 1.5;
    public static final double CANNOT_SHORTEN = 0.0;
    private static final double MIN_ALIGN = 0.7;
    private static final double EPSILON = 1.0E-6;

    public record Finding(int element, int neighbour, double shortened) {
        public boolean shortenable() {
            return this.shortened > CANNOT_SHORTEN;
        }
    }

    private record Opening(double[][] corners, double nx, double ny, double nz) {
    }

    private PassZoneOverlap() {
    }

    public static boolean scoresByZone(GateGeometry geometry) {
        CourseElement kind = geometry.scoringKind();
        return kind == CourseElement.POLE || kind == CourseElement.FLAG;
    }

    public static List<Finding> around(List<GateGeometry> gates, int index, boolean pointToPoint) {
        List<Finding> out = new ArrayList<>();
        int count = gates.size();
        if (index < 0 || index >= count || count < 2) {
            return out;
        }
        for (int element = 0; element < count; element++) {
            int previous = neighbour(element, -1, count, pointToPoint);
            int next = neighbour(element, 1, count, pointToPoint);
            if (element == index || previous == index || next == index) {
                check(gates, element, previous, index, out);
                if (next != previous) {
                    check(gates, element, next, index, out);
                }
            }
        }
        return out;
    }

    public static List<Finding> ofElement(List<GateGeometry> gates, int element, boolean pointToPoint) {
        List<Finding> out = new ArrayList<>();
        for (Finding finding : around(gates, element, pointToPoint)) {
            if (finding.element() == element) {
                out.add(finding);
            }
        }
        return out;
    }

    public static @Nullable Finding shortest(List<Finding> findings) {
        Finding best = null;
        for (Finding finding : findings) {
            if (best == null || finding.shortened() < best.shortened()) {
                best = finding;
            }
        }
        return best;
    }

    private static int neighbour(int element, int step, int count, boolean pointToPoint) {
        int other = element + step;
        if (other >= 0 && other < count) {
            return other;
        }
        return pointToPoint ? -1 : Math.floorMod(other, count);
    }

    private static void check(List<GateGeometry> gates, int element, int neighbour, int index, List<Finding> out) {
        if (neighbour < 0 || neighbour == element || element != index && neighbour != index) {
            return;
        }
        Finding finding = between(element, gates.get(element), neighbour, gates.get(neighbour));
        if (finding != null) {
            out.add(finding);
        }
    }

    public static @Nullable Finding between(int elementIndex, GateGeometry element, int neighbourIndex, GateGeometry neighbour) {
        if (!scoresByZone(element)) {
            return null;
        }
        Opening opening = opening(neighbour);
        if (opening == null) {
            return null;
        }
        double limit = Double.POSITIVE_INFINITY;
        boolean found = false;
        for (Passage zone : element.stages()) {
            double start = covered(zone, opening);
            if (!Double.isNaN(start)) {
                found = true;
                limit = Math.min(limit, start);
            }
        }
        return found ? new Finding(elementIndex, neighbourIndex, shortened(element, limit)) : null;
    }

    private static double covered(Passage zone, Opening opening) {
        double dot = zone.nx() * opening.nx() + zone.ny() * opening.ny() + zone.nz() * opening.nz();
        if (dot < MIN_ALIGN) {
            return Double.NaN;
        }
        double minSide = Double.POSITIVE_INFINITY;
        double maxSide = Double.NEGATIVE_INFINITY;
        double minUp = Double.POSITIVE_INFINITY;
        double maxUp = Double.NEGATIVE_INFINITY;
        double center = 0.0;
        for (double[] corner : opening.corners()) {
            double side = zone.side(corner[0], corner[1], corner[2]);
            double up = zone.up(corner[0], corner[1], corner[2]);
            minSide = Math.min(minSide, side);
            maxSide = Math.max(maxSide, side);
            minUp = Math.min(minUp, up);
            maxUp = Math.max(maxUp, up);
            center += zone.distance(corner[0], corner[1], corner[2]) / opening.corners().length;
        }
        boolean near = Math.abs(center) <= NEAR;
        boolean overlaps = minSide < zone.side1() - EPSILON && maxSide > zone.side0() + EPSILON && minUp < zone.up1() - EPSILON && maxUp > zone.up0() + EPSILON;
        return near && overlaps ? minSide : Double.NaN;
    }

    private static @Nullable Opening opening(GateGeometry gate) {
        if (gate.gate()) {
            double half = gate.innerHalfWidth();
            double low = gate.openingFloor();
            double high = gate.height() - gate.frame();
            return new Opening(new double[][]{corner(gate, -half, low), corner(gate, half, low), corner(gate, -half, high), corner(gate, half, high)},
                    gate.normalX(), gate.normalY(), gate.normalZ());
        }
        if (gate.freeform() && !gate.stages().isEmpty()) {
            Passage passage = gate.stages().getFirst();
            return new Opening(new double[][]{corner(passage, passage.side0(), passage.up0()), corner(passage, passage.side1(), passage.up0()),
                    corner(passage, passage.side0(), passage.up1()), corner(passage, passage.side1(), passage.up1())}, passage.nx(), passage.ny(), passage.nz());
        }
        return null;
    }

    private static double[] corner(GateGeometry gate, double side, double up) {
        return new double[]{gate.worldX(side, up, 0.0), gate.worldY(side, up, 0.0), gate.worldZ(side, up, 0.0)};
    }

    private static double[] corner(Passage passage, double side, double up) {
        return new double[]{passage.ox() + passage.sx() * side + passage.ux() * up, passage.oy() + passage.sy() * side + passage.uy() * up,
                passage.oz() + passage.sz() * side + passage.uz() * up};
    }

    private static double shortened(GateGeometry element, double limit) {
        if (element.kind() == CourseElement.GATE_POLE) {
            double value = Math.floor(limit / ScoreArea.STEP + EPSILON) * ScoreArea.STEP;
            return value >= ScoreArea.MIN_REACH ? value : CANNOT_SHORTEN;
        }
        double value = Math.floor(limit / GateSize.OPENING_STEP + EPSILON) * GateSize.OPENING_STEP;
        return value >= GateSize.MIN_OPENING ? value : CANNOT_SHORTEN;
    }
}
