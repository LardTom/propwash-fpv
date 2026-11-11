package de.lardtom.propwash.race;

import java.util.Collections;
import java.util.List;
import java.util.function.IntUnaryOperator;
import java.util.function.LongSupplier;
import java.util.function.ToDoubleFunction;
import java.util.function.ToIntFunction;

public final class CourseCrossings {
    public static final int TIME = 0;
    public static final int COURSE = 1;
    public static final int CHECKPOINT = 2;
    public static final int EVENT = 3;
    public static final int OPEN = 0;

    private CourseCrossings() {
    }

    @FunctionalInterface
    public interface Checkpoints {
        int of(int element, int stage);
    }

    public static void collect(GateGeometry geometry, List<CourseOrder.Pass> passes, int course, Checkpoints checkpoints, double x0, double y0, double z0,
                               double x1, double y1, double z1, LongSupplier nextEvent, List<double[]> hits) {
        int first = hits.size();
        for (CourseOrder.Pass pass : passes) {
            GateGeometry flown = geometry.withReverse(pass.reverse());
            if (flown.gate()) {
                double t = flown.crossing(x0, y0, z0, x1, y1, z1, true);
                if (t >= 0.0) {
                    hits.add(new double[]{t, course, checkpoints.of(pass.index(), 0), eventOf(hits, first, t, nextEvent)});
                }
                continue;
            }
            List<Passage> stages = flown.stages();
            for (int stage = 0; stage < stages.size(); stage++) {
                double t = stages.get(stage).crossing(x0, y0, z0, x1, y1, z1, true);
                if (t >= 0.0) {
                    hits.add(new double[]{t, course, checkpoints.of(pass.index(), stage), eventOf(hits, first, t, nextEvent)});
                }
            }
        }
    }

    private static double eventOf(List<double[]> hits, int first, double t, LongSupplier nextEvent) {
        for (int i = first; i < hits.size(); i++) {
            if (hits.get(i)[TIME] == t) {
                return hits.get(i)[EVENT];
            }
        }
        return nextEvent.getAsLong();
    }

    public static void sort(List<double[]> hits, IntUnaryOperator loopCheckpoints) {
        hits.sort((a, b) -> a[TIME] != b[TIME] ? Double.compare(a[TIME], b[TIME]) : a[COURSE] != b[COURSE] ? Double.compare(a[COURSE], b[COURSE])
                : Double.compare(a[CHECKPOINT], b[CHECKPOINT]));
        loopTies(hits, hit -> hit[TIME], hit -> (int) hit[COURSE], hit -> (int) hit[CHECKPOINT], loopCheckpoints);
    }

    public static <T> void loopTies(List<T> sorted, ToDoubleFunction<T> time, ToIntFunction<T> course, ToIntFunction<T> checkpoint, IntUnaryOperator loopCheckpoints) {
        int from = 0;
        while (from < sorted.size()) {
            T head = sorted.get(from);
            int to = from + 1;
            while (to < sorted.size() && time.applyAsDouble(sorted.get(to)) == time.applyAsDouble(head)
                    && course.applyAsInt(sorted.get(to)) == course.applyAsInt(head)) {
                to++;
            }
            if (to - from > 1) {
                rotate(sorted.subList(from, to), checkpoint, loopCheckpoints.applyAsInt(course.applyAsInt(head)));
            }
            from = to;
        }
    }

    private static <T> void rotate(List<T> tie, ToIntFunction<T> checkpoint, int loop) {
        if (loop < 2 || checkpoint.applyAsInt(tie.getFirst()) != 0 || checkpoint.applyAsInt(tie.getLast()) != loop - 1) {
            return;
        }
        int cut = 1;
        int widest = -1;
        for (int i = 1; i < tie.size(); i++) {
            int gap = checkpoint.applyAsInt(tie.get(i)) - checkpoint.applyAsInt(tie.get(i - 1));
            if (gap > widest) {
                widest = gap;
                cut = i;
            }
        }
        Collections.rotate(tie, -cut);
    }
}
