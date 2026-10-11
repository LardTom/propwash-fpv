package de.lardtom.propwash.input;

import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

public final class CommandTrigger {
    public static final int SLOTS = SwitchFunction.COMMAND_COUNT;
    public static final long COOLDOWN_MILLIS = 500L;
    public static final long BUDGET_COST_MILLIS = 1000L;
    public static final long BUDGET_MILLIS = 5000L;

    public enum Gate {
        OFF,
        BLOCKED,
        OPEN
    }

    public enum Outcome {
        RUN,
        BLOCKED,
        RATE_LIMITED,
        EMPTY
    }

    public record Event(int slot, boolean on, Outcome outcome) {
    }

    private final boolean[] fired = new boolean[SLOTS];
    private final boolean[] keyLatch = new boolean[SLOTS];
    private final boolean[] ran = new boolean[SLOTS];
    private final long[] lastRun = new long[SLOTS];
    private boolean primed;
    private int readable;
    private boolean budgetUsed;
    private long debtUntil;

    public void reset() {
        this.primed = false;
        for (int i = 0; i < SLOTS; i++) {
            this.keyLatch[i] = false;
        }
    }

    public boolean primed() {
        return this.primed;
    }

    public boolean keyLatched(int slot) {
        return slot >= 0 && slot < SLOTS && this.keyLatch[slot];
    }

    public List<Event> update(int states, int readable, int withOn, int withOff, Gate gate, long now) {
        if (gate == Gate.OFF) {
            this.reset();
            return List.of();
        }
        if (!this.primed) {
            for (int i = 0; i < SLOTS; i++) {
                this.fired[i] = isSet(states, i);
            }
            this.readable = readable;
            this.primed = true;
            return List.of();
        }
        List<Event> events = new ArrayList<>();
        int wasReadable = this.readable;
        this.readable = readable;
        for (int i = 0; i < SLOTS; i++) {
            if (!isSet(readable, i)) {
                continue;
            }
            boolean state = isSet(states, i);
            if (!isSet(wasReadable, i)) {
                this.fired[i] = state;
                continue;
            }
            if (state == this.fired[i]) {
                continue;
            }
            if (!isSet(state ? withOn : withOff, i)) {
                this.fired[i] = state;
                continue;
            }
            if (gate == Gate.BLOCKED) {
                this.fired[i] = state;
                events.add(new Event(i, state, Outcome.BLOCKED));
                continue;
            }
            if (this.coolingDown(i, now)) {
                continue;
            }
            this.fired[i] = state;
            if (!this.takeBudget(now)) {
                events.add(new Event(i, state, Outcome.RATE_LIMITED));
                continue;
            }
            this.markRun(i, now);
            events.add(new Event(i, state, Outcome.RUN));
        }
        return events;
    }

    public @Nullable Event keyPress(int slot, boolean hasOn, boolean hasOff, Gate gate, long now) {
        if (slot < 0 || slot >= SLOTS || gate == Gate.OFF) {
            return null;
        }
        boolean target = !hasOff || !this.keyLatch[slot];
        if (!hasOn && !hasOff) {
            return new Event(slot, true, Outcome.EMPTY);
        }
        if (!(target ? hasOn : hasOff)) {
            this.keyLatch[slot] = target;
            return null;
        }
        if (gate == Gate.BLOCKED) {
            return new Event(slot, target, Outcome.BLOCKED);
        }
        if (this.coolingDown(slot, now)) {
            return null;
        }
        if (!this.takeBudget(now)) {
            return new Event(slot, target, Outcome.RATE_LIMITED);
        }
        this.keyLatch[slot] = hasOff && target;
        this.markRun(slot, now);
        return new Event(slot, target, Outcome.RUN);
    }

    private boolean coolingDown(int slot, long now) {
        return this.ran[slot] && now - this.lastRun[slot] < COOLDOWN_MILLIS;
    }

    private void markRun(int slot, long now) {
        this.ran[slot] = true;
        this.lastRun[slot] = now;
    }

    private boolean takeBudget(long now) {
        long debt = this.budgetUsed ? Math.max(0L, this.debtUntil - now) : 0L;
        if (debt + BUDGET_COST_MILLIS > BUDGET_MILLIS) {
            return false;
        }
        this.budgetUsed = true;
        this.debtUntil = now + debt + BUDGET_COST_MILLIS;
        return true;
    }

    public static int bit(int slot) {
        return 1 << slot;
    }

    public static boolean isSet(int bits, int slot) {
        return (bits & bit(slot)) != 0;
    }
}
