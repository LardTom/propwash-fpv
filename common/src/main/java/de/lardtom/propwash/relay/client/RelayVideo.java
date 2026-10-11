package de.lardtom.propwash.relay.client;

import de.lardtom.propwash.build.VideoSystem;
import de.lardtom.propwash.relay.RelayCandidates;
import de.lardtom.propwash.relay.RelayLinks;
import de.lardtom.propwash.relay.RelayPaths;
import de.lardtom.propwash.relay.RelayRules;
import de.lardtom.propwash.relay.RepeaterIndex;
import de.lardtom.propwash.video.LinkBudget;
import de.lardtom.propwash.video.link.ObstructionScan;
import de.lardtom.propwash.video.link.ObstructionTracer;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

public final class RelayVideo {
    public static final int CELL_BUDGET_PER_TICK = 24_000;
    static final long PILOT = Long.MIN_VALUE;
    static final long DRONE = Long.MIN_VALUE + 1L;
    private static final int CAPACITY = RelayRules.MAX_CANDIDATES + RelayPaths.FIRST_RELAY;
    private static final int PILOT_RESCAN_TICKS = 10;
    private static final int STATIC_RESCAN_TICKS = 100;
    private static final int FORGET_TICKS = 200;
    private static final double PILOT_MOVED_SQR = 0.25;

    private final RelayCandidates selector = new RelayCandidates();
    private final RelayPaths paths = new RelayPaths(CAPACITY);
    private final int[] chosen = new int[RelayRules.MAX_CANDIDATES];
    private final long[] nodeKey = new long[CAPACITY];
    private final double[] nodeXyz = new double[3 * CAPACITY];
    private final Edge[] nodeEdges = new Edge[CAPACITY * CAPACITY];
    private final Map<Pair, Edge> edges = new HashMap<>();
    private final List<List<Edge>> queues = List.of(new ArrayList<>(), new ArrayList<>(), new ArrayList<>(), new ArrayList<>(), new ArrayList<>());
    private final double[] hopMargin = new double[RelayRules.MAX_RELAYS + 1];
    private final double[] hopLoss = new double[RelayRules.MAX_RELAYS + 1];
    private final double[] hopDistance = new double[RelayRules.MAX_RELAYS + 1];
    private final long[] pathKeys = new long[RelayRules.MAX_RELAYS];
    private double[] found = new double[3 * 32];
    private long[] foundKeys = new long[32];
    private int foundCount;
    private int nodeCount = RelayPaths.FIRST_RELAY;
    private int maxRelays;
    private int tick;
    private long scanNanos;
    private long scanNanosMax;
    private long scanNanosTotal;
    private long scanTicks;
    private int cellsLastTick;
    private int edgeCount;
    private boolean solved;
    private double marginDb = Double.NEGATIVE_INFINITY;
    private int relays;

    public void clear() {
        this.edges.clear();
        Arrays.fill(this.nodeEdges, null);
        this.nodeCount = RelayPaths.FIRST_RELAY;
        this.foundCount = 0;
        this.solved = false;
        this.relays = 0;
        this.marginDb = Double.NEGATIVE_INFINITY;
        this.edgeCount = 0;
        this.cellsLastTick = 0;
    }

    public void resetStats() {
        this.scanNanos = 0L;
        this.scanNanosMax = 0L;
        this.scanNanosTotal = 0L;
        this.scanTicks = 0L;
    }

    public void tick(@Nullable Level level, Vec3 eye, Vec3 antenna, ObstructionTracer.World world, int relayLimit) {
        long start = System.nanoTime();
        this.tick++;
        this.maxRelays = RelayRules.clampRelays(relayLimit);
        RepeaterIndex index = RepeaterIndex.existing(level);
        if (index == null || index.size() == 0 || this.maxRelays == 0) {
            if (this.nodeCount > RelayPaths.FIRST_RELAY || !this.edges.isEmpty()) {
                this.clear();
            }
            return;
        }
        this.collect(index, eye, antenna);
        this.cellsLastTick = this.schedule(world, CELL_BUDGET_PER_TICK);
        this.forget();
        long spent = System.nanoTime() - start;
        this.scanNanos = spent;
        this.scanNanosMax = Math.max(this.scanNanosMax, spent);
        this.scanNanosTotal += spent;
        this.scanTicks++;
    }

    public boolean solve(Vec3 eye, Vec3 antenna, VideoSystem system, double rangeBlocks, boolean obstruction) {
        this.solved = false;
        this.relays = 0;
        this.marginDb = Double.NEGATIVE_INFINITY;
        if (this.nodeCount <= RelayPaths.FIRST_RELAY || Double.isInfinite(rangeBlocks)) {
            return false;
        }
        this.setNode(RelayPaths.RECEIVER, PILOT, eye.x, eye.y, eye.z);
        this.setNode(RelayPaths.TRANSMITTER, DRONE, antenna.x, antenna.y, antenna.z);
        this.paths.reset(this.nodeCount);
        for (int i = 0; i < this.nodeCount; i++) {
            for (int j = i + 1; j < this.nodeCount; j++) {
                if (i == RelayPaths.RECEIVER && j == RelayPaths.TRANSMITTER) {
                    continue;
                }
                Edge edge = this.nodeEdges[i * CAPACITY + j];
                if (edge == null || Double.isNaN(edge.lossDb)) {
                    continue;
                }
                double margin = RelayLinks.videoHopMarginDb(this.distance(i, j), rangeBlocks, obstruction ? edge.lossDb : 0.0);
                if (!Double.isNaN(margin)) {
                    this.paths.hop(i, j, margin);
                }
            }
        }
        if (!this.paths.solve(this.maxRelays, RelayLinks.videoCombine(system))) {
            return false;
        }
        this.solved = true;
        this.relays = this.paths.relays();
        this.marginDb = this.paths.value();
        for (int h = 0; h < this.paths.hopCount(); h++) {
            int a = this.paths.node(h);
            int b = this.paths.node(h + 1);
            this.hopMargin[h] = this.paths.hopValue(h);
            Edge edge = this.nodeEdges[Math.min(a, b) * CAPACITY + Math.max(a, b)];
            this.hopLoss[h] = edge == null ? Double.NaN : edge.lossDb;
            this.hopDistance[h] = this.distance(a, b);
        }
        for (int r = 0; r < this.relays; r++) {
            this.pathKeys[r] = this.nodeKey[this.paths.node(r + 1)];
        }
        return true;
    }

    public boolean solved() {
        return this.solved;
    }

    public int relays() {
        return this.solved ? this.relays : 0;
    }

    public double marginDb() {
        return this.marginDb;
    }

    public double quality(VideoSystem system) {
        return this.solved ? RelayLinks.videoQuality(system, this.marginDb) : 0.0;
    }

    public double hopQuality(VideoSystem system, int hop) {
        return LinkBudget.quality(system, this.hopMargin[hop]);
    }

    public double hopMarginDb(int hop) {
        return this.hopMargin[hop];
    }

    public double hopLossDb(int hop) {
        return this.hopLoss[hop];
    }

    public double hopDistance(int hop) {
        return this.hopDistance[hop];
    }

    public long relayPos(int relay) {
        return this.pathKeys[relay];
    }

    public int candidates() {
        return this.nodeCount - RelayPaths.FIRST_RELAY;
    }

    public int edgeCount() {
        return this.edgeCount;
    }

    public int cellsLastTick() {
        return this.cellsLastTick;
    }

    public long scanNanos() {
        return this.scanNanos;
    }

    public long scanNanosMax() {
        return this.scanNanosMax;
    }

    public double scanNanosAverage() {
        return this.scanTicks == 0L ? 0.0 : (double) this.scanNanosTotal / this.scanTicks;
    }

    public boolean settled() {
        for (int i = 0; i < this.nodeCount; i++) {
            for (int j = i + 1; j < this.nodeCount; j++) {
                Edge edge = this.nodeEdges[i * CAPACITY + j];
                if (edge != null && (Double.isNaN(edge.lossDb) || edge.running)) {
                    return false;
                }
            }
        }
        return true;
    }

    private void collect(RepeaterIndex index, Vec3 eye, Vec3 antenna) {
        double half = RelayRules.span(this.maxRelays) * 0.5;
        double mx = (eye.x + antenna.x) * 0.5;
        double mz = (eye.z + antenna.z) * 0.5;
        this.foundCount = 0;
        index.collect(mx - half, mz - half, mx + half, mz + half, (pos, x, y, z) -> {
            if (this.foundCount == this.foundKeys.length) {
                this.foundKeys = Arrays.copyOf(this.foundKeys, this.foundKeys.length * 2);
                this.found = Arrays.copyOf(this.found, this.found.length * 2);
            }
            this.foundKeys[this.foundCount] = pos;
            this.found[3 * this.foundCount] = x;
            this.found[3 * this.foundCount + 1] = y;
            this.found[3 * this.foundCount + 2] = z;
            this.foundCount++;
        });
        int n = this.selector.select(eye.x, eye.y, eye.z, antenna.x, antenna.y, antenna.z, this.found, this.foundCount, this.maxRelays,
                RelayRules.MAX_CANDIDATES, this.chosen);
        this.setNode(RelayPaths.RECEIVER, PILOT, eye.x, eye.y, eye.z);
        this.setNode(RelayPaths.TRANSMITTER, DRONE, antenna.x, antenna.y, antenna.z);
        for (int i = 0; i < n; i++) {
            int c = this.chosen[i];
            this.setNode(RelayPaths.FIRST_RELAY + i, this.foundKeys[c], this.found[3 * c], this.found[3 * c + 1], this.found[3 * c + 2]);
        }
        this.nodeCount = RelayPaths.FIRST_RELAY + n;
    }

    private int schedule(ObstructionTracer.World world, int budget) {
        for (List<Edge> queue : this.queues) {
            queue.clear();
        }
        Arrays.fill(this.nodeEdges, null);
        int count = 0;
        for (int i = 0; i < this.nodeCount; i++) {
            for (int j = Math.max(i + 1, RelayPaths.FIRST_RELAY); j < this.nodeCount; j++) {
                if (!RelayRules.inReach(this.distance(i, j))) {
                    continue;
                }
                long a = this.nodeKey[i];
                long b = this.nodeKey[j];
                Pair key = new Pair(Math.min(a, b), Math.max(a, b));
                Edge edge = this.edges.computeIfAbsent(key, k -> new Edge());
                edge.usedTick = this.tick;
                this.nodeEdges[i * CAPACITY + j] = edge;
                count++;
                this.queues.get(this.priority(edge, i)).add(edge);
                edge.from = i;
                edge.to = j;
            }
        }
        this.edgeCount = count;
        int spent = 0;
        for (int p = 0; p < this.queues.size() - 1 && spent < budget; p++) {
            for (Edge edge : this.queues.get(p)) {
                if (spent >= budget) {
                    break;
                }
                spent += this.advance(edge, world, budget - spent);
            }
        }
        return spent;
    }

    private int priority(Edge edge, int i) {
        if (edge.running) {
            return 0;
        }
        if (Double.isNaN(edge.lossDb)) {
            return 1;
        }
        int age = this.tick - edge.startedTick;
        if (i == RelayPaths.TRANSMITTER) {
            return 2;
        }
        if (i == RelayPaths.RECEIVER) {
            double dx = this.nodeXyz[0] - edge.sx;
            double dy = this.nodeXyz[1] - edge.sy;
            double dz = this.nodeXyz[2] - edge.sz;
            boolean moved = dx * dx + dy * dy + dz * dz > PILOT_MOVED_SQR;
            return moved || age >= PILOT_RESCAN_TICKS ? 3 : 4;
        }
        return age >= STATIC_RESCAN_TICKS ? 3 : 4;
    }

    private int advance(Edge edge, ObstructionTracer.World world, int budget) {
        if (!edge.running) {
            int a = edge.from;
            int b = edge.to;
            edge.sx = this.nodeXyz[3 * a];
            edge.sy = this.nodeXyz[3 * a + 1];
            edge.sz = this.nodeXyz[3 * a + 2];
            edge.scan.start(edge.sx, edge.sy, edge.sz, this.nodeXyz[3 * b], this.nodeXyz[3 * b + 1], this.nodeXyz[3 * b + 2]);
            edge.running = true;
            edge.startedTick = this.tick;
        }
        int before = edge.scan.roundCells();
        boolean done = edge.scan.advance(world, budget);
        int spent = Math.max(1, edge.scan.roundCells() - before);
        if (done) {
            edge.lossDb = edge.scan.lossDb();
            edge.running = false;
        }
        return spent;
    }

    private void forget() {
        Iterator<Edge> it = this.edges.values().iterator();
        while (it.hasNext()) {
            if (this.tick - it.next().usedTick > FORGET_TICKS) {
                it.remove();
            }
        }
    }

    private void setNode(int node, long key, double x, double y, double z) {
        this.nodeKey[node] = key;
        this.nodeXyz[3 * node] = x;
        this.nodeXyz[3 * node + 1] = y;
        this.nodeXyz[3 * node + 2] = z;
    }

    private double distance(int a, int b) {
        return RelayCandidates.distance(this.nodeXyz[3 * a], this.nodeXyz[3 * a + 1], this.nodeXyz[3 * a + 2],
                this.nodeXyz[3 * b], this.nodeXyz[3 * b + 1], this.nodeXyz[3 * b + 2]);
    }

    private record Pair(long a, long b) {
    }

    private static final class Edge {
        private final ObstructionScan scan = new ObstructionScan();
        private double lossDb = Double.NaN;
        private boolean running;
        private int startedTick = Integer.MIN_VALUE / 2;
        private int usedTick;
        private int from;
        private int to;
        private double sx;
        private double sy;
        private double sz;
    }
}
