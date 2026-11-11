package de.lardtom.propwash.race;

import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public final class LapVerifier {
    public static final int MAX_CROSSINGS = 4096;
    public static final long NO_SEQUENCE = -1L;
    public static final int NOTHING_MISSING = -1;

    private final List<Crossing> crossings = new ArrayList<>();
    private long nextSequence;

    public record Crossing(long sequence, int course, int gate, long nanos, long event) {
        public Crossing(long sequence, int course, int gate, long nanos) {
            this(sequence, course, gate, nanos, sequence);
        }
    }

    public record VerifiedLap(long startNanos, long endNanos, long endSequence, long[] starts) {
        public VerifiedLap {
            starts = starts.clone();
        }

        public long nanos() {
            return this.endNanos - this.startNanos;
        }

        @Override
        public long[] starts() {
            return this.starts.clone();
        }

        public VerifiedLap startingAt(long nanos) {
            return new VerifiedLap(nanos, this.endNanos, this.endSequence, this.starts);
        }
    }

    public void record(int course, int gate, long nanos) {
        long sequence = this.nextSequence++;
        this.add(new Crossing(sequence, course, gate, nanos, sequence));
    }

    public void record(int course, int gate, long nanos, long event) {
        this.add(new Crossing(this.nextSequence++, course, gate, nanos, event));
    }

    private void add(Crossing crossing) {
        this.crossings.add(crossing);
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
        return this.find(course, gateCount, afterSequence, notBeforeNanos, false);
    }

    public @Nullable VerifiedLap find(int course, int gateCount, long afterSequence, long notBeforeNanos, boolean pointToPoint) {
        if (gateCount <= 0) {
            return null;
        }
        boolean open = pointToPoint && gateCount >= PointToPoint.MIN_PASSES;
        int finish = open ? PointToPoint.finish(gateCount) : 0;
        int before = open ? finish - 1 : gateCount - 1;
        for (int end = 0; end < this.crossings.size(); end++) {
            Crossing last = this.crossings.get(end);
            if (last.course() != course || last.gate() != finish || last.sequence() <= afterSequence) {
                continue;
            }
            int cursor = end;
            boolean complete = true;
            for (int gate = before; gate >= 1 && complete; gate--) {
                cursor = this.previous(course, gate, cursor, afterSequence, notBeforeNanos);
                complete = cursor >= 0;
            }
            if (!complete) {
                continue;
            }
            int start = this.previous(course, 0, cursor, afterSequence - 1L, notBeforeNanos);
            if (start >= 0) {
                return new VerifiedLap(this.crossings.get(start).nanos(), last.nanos(), last.sequence(),
                        this.starts(course, start, cursor, afterSequence - 1L, notBeforeNanos));
            }
        }
        return null;
    }

    public int firstMissing(int course, int gateCount, long afterSequence, long notBeforeNanos, boolean pointToPoint) {
        if (gateCount <= 0) {
            return NOTHING_MISSING;
        }
        boolean open = pointToPoint && gateCount >= PointToPoint.MIN_PASSES;
        int finish = open ? PointToPoint.finish(gateCount) : 0;
        int before = open ? finish - 1 : gateCount - 1;
        int cursor = this.firstStart(course, afterSequence, notBeforeNanos);
        if (cursor < 0) {
            return 0;
        }
        for (int gate = 1; gate <= before; gate++) {
            cursor = this.following(course, gate, cursor);
            if (cursor < 0) {
                return gate;
            }
        }
        return this.following(course, finish, cursor) < 0 ? finish : NOTHING_MISSING;
    }

    public long restart(int course, int finish) {
        for (int i = this.crossings.size() - 1; i >= 0; i--) {
            Crossing crossing = this.crossings.get(i);
            if (crossing.course() == course && crossing.gate() == finish) {
                return crossing.sequence();
            }
        }
        return this.lastSequence();
    }

    private int firstStart(int course, long afterSequence, long notBeforeNanos) {
        for (int i = 0; i < this.crossings.size(); i++) {
            Crossing crossing = this.crossings.get(i);
            if (crossing.course() == course && crossing.gate() == 0 && crossing.sequence() >= afterSequence && crossing.nanos() >= notBeforeNanos) {
                return i;
            }
        }
        return -1;
    }

    private int following(int course, int gate, int after) {
        long blocked = this.crossings.get(after).event();
        for (int i = after + 1; i < this.crossings.size(); i++) {
            Crossing crossing = this.crossings.get(i);
            if (crossing.event() != blocked && crossing.course() == course && crossing.gate() == gate) {
                return i;
            }
        }
        return -1;
    }

    private long[] starts(int course, int latest, int next, long afterSequence, long notBeforeNanos) {
        long blocked = this.crossings.get(next).event();
        long[] found = new long[4];
        int count = 0;
        for (int i = latest; i >= 0; i--) {
            Crossing crossing = this.crossings.get(i);
            if (crossing.sequence() <= afterSequence || crossing.nanos() < notBeforeNanos) {
                break;
            }
            if (crossing.event() == blocked || crossing.course() != course || crossing.gate() != 0) {
                continue;
            }
            if (count == found.length) {
                found = Arrays.copyOf(found, count * 2);
            }
            found[count++] = crossing.nanos();
        }
        long[] starts = new long[count];
        for (int i = 0; i < count; i++) {
            starts[i] = found[count - 1 - i];
        }
        return starts;
    }

    private int previous(int course, int gate, int before, long afterSequence, long notBeforeNanos) {
        long blocked = before < this.crossings.size() ? this.crossings.get(before).event() : Long.MIN_VALUE;
        for (int i = before - 1; i >= 0; i--) {
            Crossing crossing = this.crossings.get(i);
            if (crossing.sequence() <= afterSequence || crossing.nanos() < notBeforeNanos) {
                return -1;
            }
            if (crossing.event() == blocked) {
                continue;
            }
            if (crossing.course() == course && crossing.gate() == gate) {
                return i;
            }
        }
        return -1;
    }
}
