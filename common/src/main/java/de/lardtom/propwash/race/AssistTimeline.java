package de.lardtom.propwash.race;

import java.util.ArrayDeque;
import java.util.Iterator;

public final class AssistTimeline {
    public static final int MAX_SPANS = 64;
    public static final long JOIN_GAP_NANOS = 1_000_000_000L;
    public static final long NO_TIME = Long.MIN_VALUE;

    private record Span(long from, long to, AssistReasons reasons) {
    }

    private final ArrayDeque<Span> spans = new ArrayDeque<>();
    private long firstObserved = NO_TIME;
    private long lastObserved = NO_TIME;

    public void observe(long now, AssistReasons reasons) {
        if (this.lastObserved == NO_TIME || now < this.lastObserved || now - this.lastObserved > JOIN_GAP_NANOS) {
            this.firstObserved = now;
        }
        this.lastObserved = now;
        if (reasons.none()) {
            return;
        }
        Span last = this.spans.peekLast();
        if (last != null && last.reasons().equals(reasons) && now >= last.to() && now - last.to() <= JOIN_GAP_NANOS) {
            this.spans.pollLast();
            this.spans.addLast(new Span(last.from(), now, reasons));
            return;
        }
        this.spans.addLast(new Span(now, now, reasons));
        while (this.spans.size() > MAX_SPANS) {
            this.spans.pollFirst();
        }
    }

    public boolean covers(long from, long to) {
        return this.firstObserved != NO_TIME && this.firstObserved <= to && this.lastObserved >= from;
    }

    public AssistReasons between(long from, long to) {
        AssistReasons out = AssistReasons.NONE;
        for (Span span : this.spans) {
            if (span.to() >= from && span.from() <= to) {
                out = out.plus(span.reasons());
            }
        }
        return out;
    }

    public void forgetBefore(long time) {
        Iterator<Span> it = this.spans.iterator();
        while (it.hasNext()) {
            if (it.next().to() < time) {
                it.remove();
            }
        }
    }

    public int spans() {
        return this.spans.size();
    }

    public void clear() {
        this.spans.clear();
        this.firstObserved = NO_TIME;
        this.lastObserved = NO_TIME;
    }
}
