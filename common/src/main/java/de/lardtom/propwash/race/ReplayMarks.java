package de.lardtom.propwash.race;

import java.util.Arrays;

public record ReplayMarks(int gates, long[] times) {
    public static final ReplayMarks NONE = new ReplayMarks(0, new long[0]);

    public static ReplayMarks of(int gates, long[] splits, long lapNanos) {
        int known = Math.max(0, gates);
        if (splits.length == 0) {
            return new ReplayMarks(known, new long[0]);
        }
        long[] out = new long[splits.length];
        int count = 1;
        for (int i = 1; i < splits.length; i++) {
            if (splits[i] != LapTracker.NONE) {
                out[count++] = Math.min(splits[i], lapNanos);
            }
        }
        long[] marks = Arrays.copyOf(out, count);
        Arrays.sort(marks);
        return new ReplayMarks(known, marks);
    }

    public static boolean startOrFinish(long mark, long lapNanos) {
        return mark <= 0L || mark >= lapNanos;
    }
}
