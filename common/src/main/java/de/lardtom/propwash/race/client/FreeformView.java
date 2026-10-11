package de.lardtom.propwash.race.client;

import de.lardtom.propwash.race.FreeformRegion;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public final class FreeformView {
    public static final int SEGMENT_FLOATS = 7;
    public static final int RUN_FLOATS = 3;

    private final FreeformRegion region;
    private final float[] segments;
    private final int segmentCount;
    private final float[] runs;
    private final int runCount;
    private final int leaks;
    private final int blocked;
    private final int plateB;
    private final int floorB;
    private final float clearance;

    @FunctionalInterface
    public interface Leak {
        boolean open(int outsideA, int outsideB);
    }

    private record Unit(int orientation, int line, int inA, int inB, int from, boolean leak) {
    }

    private FreeformView(FreeformRegion region, float[] segments, int segmentCount, float[] runs, int runCount, int leaks, int blocked) {
        this.region = region;
        this.segments = segments;
        this.segmentCount = segmentCount;
        this.runs = runs;
        this.runCount = runCount;
        this.leaks = leaks;
        this.blocked = blocked;
        int ta = region.targetA();
        int tb = region.targetB();
        int top = tb;
        while (region.contains(ta, top + 1)) {
            top++;
        }
        this.plateB = top + 1;
        int bottom = tb;
        while (region.contains(ta, bottom - 1)) {
            bottom--;
        }
        this.floorB = bottom;
        int left = 0;
        while (region.contains(ta - left - 1, tb)) {
            left++;
        }
        int right = 0;
        while (region.contains(ta + right + 1, tb)) {
            right++;
        }
        int down = 0;
        while (region.contains(ta, tb - down - 1)) {
            down++;
        }
        this.clearance = Math.min(Math.min(left, right), Math.min(down, top - tb)) + 0.5F;
    }

    public static FreeformView of(FreeformRegion region) {
        return of(region, (a, b) -> false, 0);
    }

    public static FreeformView of(FreeformRegion region, Leak leak, int blocked) {
        List<Unit> units = new ArrayList<>();
        int[] leaks = {0};
        region.edges((a0, b0, a1, b1, oa, ob) -> {
            boolean horizontal = b0 == b1;
            int inA = horizontal ? 0 : (oa < a0 ? 1 : -1);
            int inB = horizontal ? (ob < b0 ? 1 : -1) : 0;
            boolean open = leak.open(oa, ob);
            if (open) {
                leaks[0]++;
            }
            units.add(new Unit(horizontal ? 0 : 1, horizontal ? b0 : a0, inA, inB, horizontal ? a0 : b0, open));
        });
        units.sort(Comparator.comparingInt(Unit::orientation).thenComparingInt(Unit::line).thenComparingInt(Unit::inA).thenComparingInt(Unit::inB)
                .thenComparing(Unit::leak).thenComparingInt(Unit::from));
        float[] segments = new float[Math.max(1, units.size()) * SEGMENT_FLOATS];
        int count = 0;
        int i = 0;
        while (i < units.size()) {
            Unit first = units.get(i);
            int end = first.from() + 1;
            int j = i + 1;
            while (j < units.size()) {
                Unit next = units.get(j);
                if (next.orientation() != first.orientation() || next.line() != first.line() || next.inA() != first.inA() || next.inB() != first.inB()
                        || next.leak() != first.leak() || next.from() != end) {
                    break;
                }
                end++;
                j++;
            }
            int o = count * SEGMENT_FLOATS;
            boolean horizontal = first.orientation() == 0;
            segments[o] = horizontal ? first.from() : first.line();
            segments[o + 1] = horizontal ? first.line() : first.from();
            segments[o + 2] = horizontal ? end : first.line();
            segments[o + 3] = horizontal ? first.line() : end;
            segments[o + 4] = first.inA();
            segments[o + 5] = first.inB();
            segments[o + 6] = first.leak() ? 1.0F : 0.0F;
            count++;
            i = j;
        }
        List<float[]> rows = new ArrayList<>();
        for (int b = 0; b < region.height(); b++) {
            int start = -1;
            for (int a = 0; a <= region.width(); a++) {
                boolean in = region.contains(a, b);
                if (in && start < 0) {
                    start = a;
                } else if (!in && start >= 0) {
                    rows.add(new float[]{start, a, b});
                    start = -1;
                }
            }
        }
        float[] runs = new float[Math.max(1, rows.size()) * RUN_FLOATS];
        for (int r = 0; r < rows.size(); r++) {
            System.arraycopy(rows.get(r), 0, runs, r * RUN_FLOATS, RUN_FLOATS);
        }
        return new FreeformView(region, segments, count, runs, rows.size(), leaks[0], blocked);
    }

    public FreeformRegion region() {
        return this.region;
    }

    public float[] segments() {
        return this.segments;
    }

    public int segmentCount() {
        return this.segmentCount;
    }

    public float[] runs() {
        return this.runs;
    }

    public int runCount() {
        return this.runCount;
    }

    public int leaks() {
        return this.leaks;
    }

    public int blocked() {
        return this.blocked;
    }

    public boolean intact() {
        return this.leaks == 0 && this.blocked == 0;
    }

    public int plateB() {
        return this.plateB;
    }

    public int floorB() {
        return this.floorB;
    }

    public int columnHeight() {
        return this.plateB - this.floorB;
    }

    public float clearance() {
        return this.clearance;
    }
}
