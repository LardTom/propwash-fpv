package de.lardtom.propwash.race;

import java.util.ArrayList;
import java.util.List;

public final class StackTurnRoute {
    public static final double CLEARANCE = 2.5;
    private static final double ARC_STEP = Math.toRadians(10.0);

    private StackTurnRoute() {
    }

    public static double passHeight(GateGeometry gate, boolean legs) {
        return legs ? gate.y() + gate.frame() * 0.5 : gate.centerY();
    }

    public static double radius(List<GateGeometry> gates) {
        double radius = 0.0;
        for (GateGeometry gate : gates) {
            radius = Math.max(radius, gate.width() * 0.5 + gate.depth());
        }
        return radius + CLEARANCE;
    }

    public static List<double[]> lap(List<GateGeometry> gates, GateOpenings openings) {
        double cx = gates.getFirst().x();
        double cz = gates.getFirst().z();
        double radius = radius(gates);
        List<double[]> path = new ArrayList<>();
        for (int i = 0; i <= gates.size(); i++) {
            int index = i % gates.size();
            GateGeometry gate = gates.get(index);
            double height = passHeight(gate, openings.legs(index));
            double in = Math.atan2(-gate.normalZ(), -gate.normalX());
            if (i == 0) {
                path.add(at(cx, cz, radius, in, height));
            } else {
                double[] last = path.getLast();
                path.add(new double[]{last[0], height, last[2]});
                arc(path, cx, cz, radius, Math.atan2(last[2] - cz, last[0] - cx), in, height);
            }
            path.add(at(cx, cz, radius, Math.atan2(gate.normalZ(), gate.normalX()), height));
        }
        return path;
    }

    private static void arc(List<double[]> path, double cx, double cz, double radius, double from, double to, double height) {
        double delta = to - from;
        while (delta > Math.PI) {
            delta -= 2.0 * Math.PI;
        }
        while (delta <= -Math.PI) {
            delta += 2.0 * Math.PI;
        }
        int steps = Math.max(1, (int) Math.ceil(Math.abs(delta) / ARC_STEP));
        for (int step = 1; step <= steps; step++) {
            path.add(at(cx, cz, radius, from + delta * step / steps, height));
        }
    }

    private static double[] at(double cx, double cz, double radius, double angle, double height) {
        return new double[]{cx + Math.cos(angle) * radius, height, cz + Math.sin(angle) * radius};
    }
}
