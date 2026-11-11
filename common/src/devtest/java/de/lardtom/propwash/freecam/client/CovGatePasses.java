package de.lardtom.propwash.freecam.client;

import de.lardtom.propwash.race.GateGeometry;

import java.util.ArrayList;
import java.util.List;

final class CovGatePasses {
    record Opening(double x, double y, double z) {
    }

    private final List<Opening> openings;
    private final double tolerance;
    private final boolean[] there;
    private final boolean[] back;
    private boolean started;
    private double lastX;
    private double lastY;
    private double lastZ;
    private double drift;

    CovGatePasses(List<Opening> openings, double tolerance) {
        this.openings = List.copyOf(openings);
        this.tolerance = tolerance;
        this.there = new boolean[this.openings.size()];
        this.back = new boolean[this.openings.size()];
    }

    static CovGatePasses of(List<GateGeometry> gates, double tolerance) {
        List<Opening> openings = new ArrayList<>();
        for (GateGeometry gate : gates) {
            openings.add(new Opening(gate.x(), gate.centerY(), gate.z()));
        }
        return new CovGatePasses(openings, tolerance);
    }

    void tick(double x, double y, double z, double toX, double toY, double toZ, boolean back) {
        if (this.started) {
            this.drift = Math.max(this.drift, Math.abs(z - this.lastZ));
            this.segment(this.lastX, this.lastY, this.lastZ, x, y, z, back);
        }
        this.segment(x, y, z, toX, toY, toZ, back);
        this.started = true;
        this.lastX = toX;
        this.lastY = toY;
        this.lastZ = toZ;
    }

    private void segment(double ax, double ay, double az, double bx, double by, double bz, boolean back) {
        for (int i = 0; i < this.openings.size(); i++) {
            Opening opening = this.openings.get(i);
            double plane = opening.z();
            boolean fromBehind = az < plane;
            boolean toBehind = bz < plane;
            if (fromBehind == toBehind || toBehind != back) {
                continue;
            }
            double share = (plane - az) / (bz - az);
            double x = ax + (bx - ax) * share;
            double y = ay + (by - ay) * share;
            if (Math.abs(x - opening.x()) < this.tolerance && Math.abs(y - opening.y()) < this.tolerance) {
                if (back) {
                    this.back[i] = true;
                } else {
                    this.there[i] = true;
                }
            }
        }
    }

    int count() {
        int count = 0;
        for (int i = 0; i < this.openings.size(); i++) {
            count += (this.there[i] ? 1 : 0) + (this.back[i] ? 1 : 0);
        }
        return count;
    }

    double drift() {
        return this.drift;
    }

    int expected() {
        return 2 * this.openings.size();
    }
}
