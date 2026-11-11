package de.lardtom.propwash.race;

import java.util.ArrayDeque;
import java.util.Iterator;

public final class RuleTimeline {
    public static final int MAX_SPANS = 64;
    public static final long JOIN_GAP_NANOS = AssistTimeline.JOIN_GAP_NANOS;
    public static final long NO_TIME = Long.MIN_VALUE;

    private record Span(long from, long to, RaceRuleSet rules, AssistReasons personal) {
    }

    private final ArrayDeque<Span> spans = new ArrayDeque<>();
    private long firstObserved = NO_TIME;
    private long lastObserved = NO_TIME;

    public void observe(long now, RaceRuleSet rules) {
        this.observe(now, rules, AssistReasons.NONE);
    }

    public void observe(long now, RaceRuleSet rules, RaceStandard server) {
        this.observe(now, rules, rules.personalReasons(server));
    }

    private void observe(long now, RaceRuleSet rules, AssistReasons personal) {
        if (this.lastObserved == NO_TIME || now < this.lastObserved || now - this.lastObserved > JOIN_GAP_NANOS) {
            this.firstObserved = now;
        }
        this.lastObserved = now;
        Span last = this.spans.peekLast();
        if (last != null && last.rules().equals(rules) && last.personal().equals(personal) && now >= last.to() && now - last.to() <= JOIN_GAP_NANOS) {
            this.spans.pollLast();
            this.spans.addLast(new Span(last.from(), now, rules, personal));
            return;
        }
        this.spans.addLast(new Span(now, now, rules, personal));
        while (this.spans.size() > MAX_SPANS) {
            Span oldest = this.spans.pollFirst();
            Span next = this.spans.pollFirst();
            this.spans.addFirst(new Span(oldest.from(), next.to(), oldest.rules().union(next.rules()), oldest.personal().plus(next.personal())));
        }
    }

    public boolean covers(long from, long to) {
        return this.firstObserved != NO_TIME && this.firstObserved <= to && this.lastObserved >= from && !this.spans.isEmpty();
    }

    public RaceRuleSet between(long from, long to) {
        RaceRuleSet out = null;
        Span before = null;
        for (Span span : this.spans) {
            if (span.to() < from) {
                before = span;
                continue;
            }
            if (span.from() > to) {
                break;
            }
            out = out == null ? span.rules() : out.union(span.rules());
        }
        if (out == null) {
            return before == null ? RaceRuleSet.DEFAULTS : before.rules();
        }
        return out;
    }

    public AssistReasons personalBetween(long from, long to) {
        AssistReasons out = null;
        Span before = null;
        for (Span span : this.spans) {
            if (span.to() < from) {
                before = span;
                continue;
            }
            if (span.from() > to) {
                break;
            }
            out = out == null ? span.personal() : out.plus(span.personal());
        }
        if (out == null) {
            return before == null ? AssistReasons.NONE : before.personal();
        }
        return out;
    }

    public void forgetBefore(long time) {
        Iterator<Span> it = this.spans.iterator();
        while (it.hasNext()) {
            Span span = it.next();
            if (span.to() < time && this.spans.size() > 1) {
                it.remove();
            } else {
                break;
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
