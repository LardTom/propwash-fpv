package de.lardtom.propwash.relay;

import java.util.Arrays;

public final class RelayPaths {
    public static final int RECEIVER = 0;
    public static final int TRANSMITTER = 1;
    public static final int FIRST_RELAY = 2;

    private final int capacity;
    private final double[] hops;
    private final double[][] layers;
    private final int[][] previous;
    private final int[] path;
    private int nodes;
    private int pathLength;
    private double value = Double.NEGATIVE_INFINITY;

    public RelayPaths(int capacity) {
        if (capacity < FIRST_RELAY) {
            throw new IllegalArgumentException("Zu wenige Knoten: " + capacity);
        }
        this.capacity = capacity;
        this.hops = new double[capacity * capacity];
        this.layers = new double[RelayRules.MAX_RELAYS + 1][capacity];
        this.previous = new int[RelayRules.MAX_RELAYS + 1][capacity];
        this.path = new int[RelayRules.MAX_RELAYS + 2];
        this.reset(FIRST_RELAY);
    }

    public int capacity() {
        return this.capacity;
    }

    public void reset(int nodes) {
        if (nodes < FIRST_RELAY || nodes > this.capacity) {
            throw new IllegalArgumentException("Knotenzahl " + nodes + " außerhalb 2…" + this.capacity);
        }
        this.nodes = nodes;
        Arrays.fill(this.hops, 0, nodes * this.capacity, Double.NaN);
        this.pathLength = 0;
        this.value = Double.NEGATIVE_INFINITY;
    }

    public int nodes() {
        return this.nodes;
    }

    public void hop(int a, int b, double value) {
        this.check(a);
        this.check(b);
        if (a == b) {
            return;
        }
        this.hops[a * this.capacity + b] = value;
        this.hops[b * this.capacity + a] = value;
    }

    public double hop(int a, int b) {
        return this.hops[a * this.capacity + b];
    }

    public boolean solve(int maxRelays, Combine combine) {
        this.pathLength = 0;
        this.value = Double.NEGATIVE_INFINITY;
        int limit = Math.min(RelayRules.clampRelays(maxRelays), this.nodes - FIRST_RELAY);
        if (limit < 1) {
            return false;
        }
        boolean noise = combine == Combine.NOISE;
        double bestScore = Double.NEGATIVE_INFINITY;
        int bestLayer = -1;
        int bestLast = -1;
        double[] first = this.layers[1];
        for (int v = FIRST_RELAY; v < this.nodes; v++) {
            first[v] = start(this.hop(RECEIVER, v), noise);
            this.previous[1][v] = RECEIVER;
        }
        for (int s = 1; s <= limit; s++) {
            double[] current = this.layers[s];
            if (s > 1) {
                double[] before = this.layers[s - 1];
                for (int v = FIRST_RELAY; v < this.nodes; v++) {
                    double best = Double.NEGATIVE_INFINITY;
                    int from = -1;
                    for (int u = FIRST_RELAY; u < this.nodes; u++) {
                        if (u == v || before[u] == Double.NEGATIVE_INFINITY) {
                            continue;
                        }
                        double score = extend(before[u], this.hop(u, v), noise);
                        if (score > best) {
                            best = score;
                            from = u;
                        }
                    }
                    current[v] = best;
                    this.previous[s][v] = from;
                }
            }
            for (int v = FIRST_RELAY; v < this.nodes; v++) {
                if (current[v] == Double.NEGATIVE_INFINITY) {
                    continue;
                }
                double score = extend(current[v], this.hop(v, TRANSMITTER), noise);
                if (score > bestScore) {
                    bestScore = score;
                    bestLayer = s;
                    bestLast = v;
                }
            }
        }
        if (bestLayer < 0) {
            return false;
        }
        this.pathLength = bestLayer + 2;
        this.path[0] = RECEIVER;
        this.path[this.pathLength - 1] = TRANSMITTER;
        int v = bestLast;
        for (int s = bestLayer; s >= 1; s--) {
            this.path[s] = v;
            v = this.previous[s][v];
        }
        this.value = noise ? marginOfNoise(-bestScore) : bestScore;
        return true;
    }

    public boolean found() {
        return this.pathLength > 0;
    }

    public double value() {
        return this.value;
    }

    public int relays() {
        return this.pathLength == 0 ? 0 : this.pathLength - 2;
    }

    public int hopCount() {
        return this.pathLength == 0 ? 0 : this.pathLength - 1;
    }

    public int node(int index) {
        if (index < 0 || index >= this.pathLength) {
            throw new IndexOutOfBoundsException(index);
        }
        return this.path[index];
    }

    public double hopValue(int index) {
        return this.hop(this.node(index), this.node(index + 1));
    }

    public static double noiseTerm(double marginDb) {
        if (Double.isNaN(marginDb) || marginDb == Double.NEGATIVE_INFINITY) {
            return Double.POSITIVE_INFINITY;
        }
        if (marginDb == Double.POSITIVE_INFINITY) {
            return 0.0;
        }
        return Math.pow(10.0, -marginDb / 10.0);
    }

    public static double marginOfNoise(double noise) {
        if (!(noise > 0.0)) {
            return Double.isNaN(noise) ? Double.NEGATIVE_INFINITY : Double.POSITIVE_INFINITY;
        }
        if (noise == Double.POSITIVE_INFINITY) {
            return Double.NEGATIVE_INFINITY;
        }
        return -10.0 * Math.log10(noise);
    }

    private static double start(double hop, boolean noise) {
        if (Double.isNaN(hop) || hop == Double.NEGATIVE_INFINITY) {
            return Double.NEGATIVE_INFINITY;
        }
        return noise ? -noiseTerm(hop) : hop;
    }

    private static double extend(double score, double hop, boolean noise) {
        if (Double.isNaN(hop) || hop == Double.NEGATIVE_INFINITY || score == Double.NEGATIVE_INFINITY) {
            return Double.NEGATIVE_INFINITY;
        }
        return noise ? score - noiseTerm(hop) : Math.min(score, hop);
    }

    private void check(int node) {
        if (node < 0 || node >= this.nodes) {
            throw new IndexOutOfBoundsException("Knoten " + node + " von " + this.nodes);
        }
    }

    public enum Combine {
        WEAKEST,
        NOISE
    }
}
