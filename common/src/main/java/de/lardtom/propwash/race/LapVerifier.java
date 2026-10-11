package de.lardtom.propwash.race;

import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

public final class LapVerifier {
    public static final int MAX_CROSSINGS = 4096;
    public static final long NO_SEQUENCE = -1L;

    private final List<Crossing> crossings = new ArrayList<>();
    private long nextSequence;

    public record Crossing(long sequence, int course, int gate, long nanos) {
    }

    public record VerifiedLap(long startNanos, long endNanos, long endSequence) {
        public long nanos() {
            return this.endNanos - this.startNanos;
        }
    }

    public void record(int course, int gate, long nanos) {
        this.crossings.add(new Crossing(this.nextSequence++, course, gate, nanos));
        if (this.crossings.size() > MAX_CROSSINGS) {
            this.crossings.subList(0, this.crossings.size() - MAX_CROSSINGS).clear();
        }
    }

    public void prune(long olderThanNanos) {
        this.crossings.removeIf(crossing -> crossing.nanos() < olderThanNanos);
    }

    public int size() {
        return this.crossings.size();
    }

    public long lastSequence() {
        return this.nextSequence - 1L;
    }

    public @Nullable VerifiedLap find(int course, int gateCount, long afterSequence, long notBeforeNanos) {
        if (gateCount <= 0) {
            return null;
        }
        for (int end = 0; end < this.crossings.size(); end++) {
            Crossing last = this.crossings.get(end);
            if (last.course() != course || last.gate() != 0 || last.sequence() <= afterSequence) {
                continue;
            }
            int cursor = end;
            boolean complete = true;
            for (int gate = gateCount - 1; gate >= 1 && complete; gate--) {
                cursor = this.previous(course, gate, cursor, afterSequence, notBeforeNanos);
                complete = cursor >= 0;
            }
            if (!complete) {
                continue;
            }
            int start = this.previous(course, 0, cursor, afterSequence - 1L, notBeforeNanos);
            if (start >= 0) {
                return new VerifiedLap(this.crossings.get(start).nanos(), last.nanos(), last.sequence());
            }
        }
        return null;
    }

    private int previous(int course, int gate, int before, long afterSequence, long notBeforeNanos) {
        for (int i = before - 1; i >= 0; i--) {
            Crossing crossing = this.crossings.get(i);
            if (crossing.sequence() <= afterSequence || crossing.nanos() < notBeforeNanos) {
                return -1;
            }
            if (crossing.course() == course && crossing.gate() == gate) {
                return i;
            }
        }
        return -1;
    }
}
