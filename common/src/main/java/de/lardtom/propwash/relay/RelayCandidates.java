package de.lardtom.propwash.relay;

import java.util.Arrays;

public final class RelayCandidates {
    private double[] detours = new double[32];
    private Integer[] order = new Integer[32];

    public int select(double ax, double ay, double az, double bx, double by, double bz, double[] positions, int count, int relays, int max,
                      int[] out) {
        int limit = Math.min(max, out.length);
        if (count <= 0 || limit <= 0 || RelayRules.clampRelays(relays) <= 0) {
            return 0;
        }
        if (this.detours.length < count) {
            int size = Math.max(count, this.detours.length * 2);
            this.detours = new double[size];
            this.order = new Integer[size];
        }
        double span = RelayRules.span(relays);
        int kept = 0;
        for (int i = 0; i < count; i++) {
            double x = positions[3 * i];
            double y = positions[3 * i + 1];
            double z = positions[3 * i + 2];
            double toA = distance(ax, ay, az, x, y, z);
            double toB = distance(bx, by, bz, x, y, z);
            double detour = toA + toB;
            if (!(detour <= span)) {
                continue;
            }
            this.detours[i] = detour;
            this.order[kept++] = i;
        }
        double[] keys = this.detours;
        Arrays.sort(this.order, 0, kept, (p, q) -> {
            int c = Double.compare(keys[p], keys[q]);
            return c != 0 ? c : Integer.compare(p, q);
        });
        int n = Math.min(kept, limit);
        for (int i = 0; i < n; i++) {
            out[i] = this.order[i];
        }
        return n;
    }

    public static double distance(double ax, double ay, double az, double bx, double by, double bz) {
        double dx = bx - ax;
        double dy = by - ay;
        double dz = bz - az;
        return Math.sqrt(dx * dx + dy * dy + dz * dz);
    }
}
