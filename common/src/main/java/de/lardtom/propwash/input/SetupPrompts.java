package de.lardtom.propwash.input;

import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

public final class SetupPrompts {
    public static final long CONNECT_DELAY_NANOS = 1_000_000_000L;
    public static final long SETTLE_NANOS = 1_500_000_000L;
    public static final long REMIND_DELAY_NANOS = 600_000_000_000L;
    public static final int MAX_REMINDERS = 1;

    private final Map<String, Entry> entries = new LinkedHashMap<>();
    private long safeSince = Long.MIN_VALUE;

    public List<String> update(List<Device> devices, Function<String, SetupStatus> status, long now) {
        Set<String> wasConnected = new HashSet<>();
        for (Map.Entry<String, Entry> e : this.entries.entrySet()) {
            if (e.getValue().connected) {
                wasConnected.add(e.getKey());
            }
            e.getValue().connected = false;
        }
        Set<String> seen = new HashSet<>();
        List<String> queued = new ArrayList<>();
        for (Device device : devices) {
            String guid = device.guid();
            if (!device.eligible() || guid == null || guid.isEmpty() || !seen.add(guid)) {
                continue;
            }
            SetupStatus current = status.apply(guid);
            Entry entry = this.entries.get(guid);
            if (current == null || !current.wantsSetup()) {
                if (entry != null) {
                    entry.connected = true;
                    if (entry.state == State.WAITING) {
                        entry.state = State.RESOLVED;
                    }
                }
                continue;
            }
            if (entry == null) {
                entry = new Entry(now + CONNECT_DELAY_NANOS);
                this.entries.put(guid, entry);
                queued.add(guid);
            } else if (entry.state == State.RESOLVED) {
                entry.state = State.WAITING;
                entry.dueAt = now + CONNECT_DELAY_NANOS;
                entry.notified = false;
                queued.add(guid);
            } else if (entry.state == State.WAITING && !wasConnected.contains(guid)) {
                entry.dueAt = Math.max(entry.dueAt, now + CONNECT_DELAY_NANOS);
                entry.notified = false;
                queued.add(guid);
            }
            entry.connected = true;
        }
        return queued;
    }

    public @Nullable Step poll(long now, boolean inWorld, boolean safe) {
        if (!safe) {
            this.safeSince = Long.MIN_VALUE;
        } else if (this.safeSince == Long.MIN_VALUE) {
            this.safeSince = now;
        }
        if (this.busy()) {
            return null;
        }
        boolean settled = safe && now - this.safeSince >= SETTLE_NANOS;
        for (Map.Entry<String, Entry> e : this.entries.entrySet()) {
            Entry entry = e.getValue();
            if (entry.state != State.WAITING || !entry.connected || now < entry.dueAt) {
                continue;
            }
            if (settled) {
                entry.state = State.SHOWING;
                entry.notified = true;
                return new Step(Action.PROMPT, e.getKey());
            }
            if (inWorld && !safe && !entry.notified) {
                entry.notified = true;
                return new Step(Action.NOTIFY, e.getKey());
            }
        }
        return null;
    }

    public void answer(String guid, Answer answer, long now) {
        Entry entry = this.entries.computeIfAbsent(guid, g -> new Entry(now));
        switch (answer) {
            case SET_UP -> entry.state = State.IN_SETUP;
            case LATER -> this.later(entry, now);
            case NEVER -> entry.state = State.DISMISSED;
        }
    }

    public void setupClosed(String guid, boolean completed, long now) {
        Entry entry = this.entries.computeIfAbsent(guid, g -> new Entry(now));
        if (completed) {
            entry.state = State.RESOLVED;
        } else {
            this.later(entry, now);
        }
    }

    public void withdraw(String guid) {
        Entry entry = this.entries.get(guid);
        if (entry != null && entry.state == State.SHOWING) {
            entry.state = State.WAITING;
        }
    }

    private void later(Entry entry, long now) {
        if (entry.reminders < MAX_REMINDERS) {
            entry.reminders++;
            entry.state = State.WAITING;
            entry.dueAt = now + REMIND_DELAY_NANOS;
            entry.notified = false;
        } else {
            entry.state = State.DISMISSED;
        }
    }

    public boolean busy() {
        for (Entry entry : this.entries.values()) {
            if (entry.state == State.SHOWING || entry.state == State.IN_SETUP) {
                return true;
            }
        }
        return false;
    }

    public @Nullable String firstWaiting() {
        for (Map.Entry<String, Entry> e : this.entries.entrySet()) {
            if (e.getValue().state == State.WAITING && e.getValue().connected) {
                return e.getKey();
            }
        }
        return null;
    }

    public boolean waiting(String guid) {
        Entry entry = this.entries.get(guid);
        return entry != null && entry.state == State.WAITING;
    }

    public boolean dismissed(String guid) {
        Entry entry = this.entries.get(guid);
        return entry != null && entry.state == State.DISMISSED;
    }

    public int reminders(String guid) {
        Entry entry = this.entries.get(guid);
        return entry == null ? 0 : entry.reminders;
    }

    public long dueAt(String guid) {
        Entry entry = this.entries.get(guid);
        return entry == null ? Long.MAX_VALUE : entry.dueAt;
    }

    public void clear() {
        this.entries.clear();
        this.safeSince = Long.MIN_VALUE;
    }

    public enum Answer {
        SET_UP,
        LATER,
        NEVER
    }

    public enum Action {
        PROMPT,
        NOTIFY
    }

    public record Device(String guid, boolean eligible) {
    }

    public record Step(Action action, String guid) {
    }

    private enum State {
        WAITING,
        SHOWING,
        IN_SETUP,
        RESOLVED,
        DISMISSED
    }

    private static final class Entry {
        private State state = State.WAITING;
        private long dueAt;
        private int reminders;
        private boolean notified;
        private boolean connected;

        private Entry(long dueAt) {
            this.dueAt = dueAt;
        }
    }
}
