package de.lardtom.propwash.relay;

import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

import java.util.Arrays;

public final class RelayReach {
    private static final int PRUNE_TICKS = 20;

    private final RelayCandidates selector = new RelayCandidates();
    private final int[] chosen = new int[RelayRules.MAX_CANDIDATES];
    private final double[] nodes = new double[3 * RelayRules.MAX_CANDIDATES];
    private final boolean[] reached = new boolean[RelayRules.MAX_CANDIDATES];
    private final boolean[] next = new boolean[RelayRules.MAX_CANDIDATES];
    private double[] found = new double[3 * 32];
    private int foundCount;
    private long prunedAt = Long.MIN_VALUE;

    public boolean linked(@Nullable Level level, Vec3 pilot, Vec3 drone, double hopReach, int relays) {
        if (RelayCandidates.distance(pilot.x, pilot.y, pilot.z, drone.x, drone.y, drone.z) <= hopReach) {
            return true;
        }
        int limit = RelayRules.clampRelays(relays);
        RepeaterIndex index = RepeaterIndex.existing(level);
        if (index == null || level == null || limit == 0 || !(hopReach > 0.0) || Double.isInfinite(hopReach)) {
            return false;
        }
        long now = level.getGameTime();
        if (now - this.prunedAt >= PRUNE_TICKS || now < this.prunedAt) {
            this.prunedAt = now;
            index.prune(level);
        }
        if (index.size() == 0) {
            return false;
        }
        double half = RelayRules.span(limit, hopReach) * 0.5;
        double mx = (pilot.x + drone.x) * 0.5;
        double mz = (pilot.z + drone.z) * 0.5;
        this.foundCount = 0;
        index.collect(mx - half, mz - half, mx + half, mz + half, (pos, x, y, z) -> {
            if (3 * this.foundCount == this.found.length) {
                this.found = Arrays.copyOf(this.found, this.found.length * 2);
            }
            this.found[3 * this.foundCount] = x;
            this.found[3 * this.foundCount + 1] = y;
            this.found[3 * this.foundCount + 2] = z;
            this.foundCount++;
        });
        int n = this.selector.select(pilot.x, pilot.y, pilot.z, drone.x, drone.y, drone.z, this.found, this.foundCount, limit, hopReach,
                RelayRules.MAX_CANDIDATES, this.chosen);
        for (int i = 0; i < n; i++) {
            int c = this.chosen[i];
            this.nodes[3 * i] = this.found[3 * c];
            this.nodes[3 * i + 1] = this.found[3 * c + 1];
            this.nodes[3 * i + 2] = this.found[3 * c + 2];
        }
        return chain(pilot.x, pilot.y, pilot.z, drone.x, drone.y, drone.z, this.nodes, n, limit, hopReach, this.reached, this.next);
    }

    public static boolean chain(double ax, double ay, double az, double bx, double by, double bz, double[] nodes, int count, int relays,
                                double hopReach) {
        return chain(ax, ay, az, bx, by, bz, nodes, count, relays, hopReach, new boolean[count], new boolean[count]);
    }

    private static boolean chain(double ax, double ay, double az, double bx, double by, double bz, double[] nodes, int count, int relays,
                                 double hopReach, boolean[] reached, boolean[] next) {
        if (RelayCandidates.distance(ax, ay, az, bx, by, bz) <= hopReach) {
            return true;
        }
        int limit = RelayRules.clampRelays(relays);
        if (count <= 0 || limit == 0) {
            return false;
        }
        Arrays.fill(reached, 0, count, false);
        boolean any = false;
        for (int i = 0; i < count; i++) {
            if (within(ax, ay, az, nodes, i, hopReach)) {
                reached[i] = true;
                any = true;
            }
        }
        for (int layer = 1; any; layer++) {
            for (int i = 0; i < count; i++) {
                if (reached[i] && within(bx, by, bz, nodes, i, hopReach)) {
                    return true;
                }
            }
            if (layer >= limit) {
                return false;
            }
            any = false;
            Arrays.fill(next, 0, count, false);
            for (int i = 0; i < count; i++) {
                if (reached[i]) {
                    continue;
                }
                for (int j = 0; j < count; j++) {
                    if (reached[j] && within(nodes[3 * i], nodes[3 * i + 1], nodes[3 * i + 2], nodes, j, hopReach)) {
                        next[i] = true;
                        any = true;
                        break;
                    }
                }
            }
            for (int i = 0; i < count; i++) {
                reached[i] |= next[i];
            }
        }
        return false;
    }

    private static boolean within(double x, double y, double z, double[] nodes, int i, double hopReach) {
        return RelayCandidates.distance(x, y, z, nodes[3 * i], nodes[3 * i + 1], nodes[3 * i + 2]) <= hopReach;
    }
}
