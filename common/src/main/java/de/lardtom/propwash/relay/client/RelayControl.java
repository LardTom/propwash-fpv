package de.lardtom.propwash.relay.client;

import de.lardtom.propwash.relay.RelayCandidates;
import de.lardtom.propwash.relay.RelayLinks;
import de.lardtom.propwash.relay.RelayPaths;
import de.lardtom.propwash.relay.RelayRules;
import de.lardtom.propwash.relay.RepeaterIndex;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

import java.util.Arrays;

public final class RelayControl {
    public static final RelayControl INSTANCE = new RelayControl();
    private static final int CAPACITY = RelayRules.MAX_CANDIDATES + RelayPaths.FIRST_RELAY;

    private final RelayCandidates selector = new RelayCandidates();
    private final RelayPaths paths = new RelayPaths(CAPACITY);
    private final int[] chosen = new int[RelayRules.MAX_CANDIDATES];
    private final double[] nodes = new double[3 * CAPACITY];
    private double[] found = new double[3 * 32];
    private int foundCount;
    private int relays;

    private RelayControl() {
    }

    public void clear() {
        this.relays = 0;
        this.foundCount = 0;
    }

    public int relays() {
        return this.relays;
    }

    public float quality(@Nullable Level level, Vec3 pilot, Vec3 drone, float direct, double serverLimit, boolean freeRange, double creativeRange,
                         int relayLimit) {
        return this.quality(level, pilot, drone, direct, serverLimit, freeRange, creativeRange, relayLimit, null);
    }

    public float quality(@Nullable Level level, Vec3 pilot, Vec3 drone, float direct, double serverLimit, boolean freeRange, double creativeRange,
                         int relayLimit, @Nullable RelayVideo video) {
        this.relays = 0;
        int limit = RelayRules.clampRelays(relayLimit);
        RepeaterIndex index = RepeaterIndex.existing(level);
        double hopReach = RelayLinks.controlHopReach(freeRange, creativeRange);
        if (index == null || index.size() == 0 || limit == 0 || direct >= 1.0F || Double.isInfinite(hopReach)) {
            return direct;
        }
        int count = video != null && video.nodeCount() > RelayPaths.FIRST_RELAY ? this.fromVideo(video, hopReach, freeRange, creativeRange)
                : this.fromIndex(index, pilot, drone, limit, hopReach, freeRange, creativeRange);
        if (count <= RelayPaths.FIRST_RELAY) {
            return direct;
        }
        double relayed = this.paths.solve(limit, RelayPaths.Combine.WEAKEST) ? this.paths.value() : Double.NaN;
        float quality = RelayLinks.controlQuality(direct, relayed, pilot.distanceTo(drone), serverLimit);
        if (!Double.isNaN(relayed) && relayed > direct && quality > direct) {
            this.relays = this.paths.relays();
        }
        return quality;
    }

    private int fromVideo(RelayVideo video, double hopReach, boolean freeRange, double creativeRange) {
        int count = video.nodeCount();
        this.paths.reset(count);
        for (int i = 0; i < count; i++) {
            for (int j = Math.max(i + 1, RelayPaths.FIRST_RELAY); j < count; j++) {
                double distance = RelayCandidates.distance(video.nodeX(i), video.nodeY(i), video.nodeZ(i), video.nodeX(j), video.nodeY(j), video.nodeZ(j));
                if (!RelayRules.inReach(distance, hopReach)) {
                    continue;
                }
                double loss = video.edgeLossDb(i, j);
                double hop = RelayLinks.controlHopQuality(distance, freeRange, creativeRange, Double.isNaN(loss) ? 0.0 : loss);
                if (!Double.isNaN(hop)) {
                    this.paths.hop(i, j, hop);
                }
            }
        }
        return count;
    }

    private int fromIndex(RepeaterIndex index, Vec3 pilot, Vec3 drone, int limit, double hopReach, boolean freeRange, double creativeRange) {
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
        if (n == 0) {
            return 0;
        }
        this.node(RelayPaths.RECEIVER, pilot.x, pilot.y, pilot.z);
        this.node(RelayPaths.TRANSMITTER, drone.x, drone.y, drone.z);
        for (int i = 0; i < n; i++) {
            int c = this.chosen[i];
            this.node(RelayPaths.FIRST_RELAY + i, this.found[3 * c], this.found[3 * c + 1], this.found[3 * c + 2]);
        }
        int count = RelayPaths.FIRST_RELAY + n;
        this.paths.reset(count);
        for (int i = 0; i < count; i++) {
            for (int j = Math.max(i + 1, RelayPaths.FIRST_RELAY); j < count; j++) {
                double distance = this.distance(i, j);
                if (!RelayRules.inReach(distance, hopReach)) {
                    continue;
                }
                double hop = RelayLinks.controlHopQuality(distance, freeRange, creativeRange);
                if (!Double.isNaN(hop)) {
                    this.paths.hop(i, j, hop);
                }
            }
        }
        return count;
    }

    private void node(int index, double x, double y, double z) {
        this.nodes[3 * index] = x;
        this.nodes[3 * index + 1] = y;
        this.nodes[3 * index + 2] = z;
    }

    private double distance(int a, int b) {
        return RelayCandidates.distance(this.nodes[3 * a], this.nodes[3 * a + 1], this.nodes[3 * a + 2],
                this.nodes[3 * b], this.nodes[3 * b + 1], this.nodes[3 * b + 2]);
    }
}
