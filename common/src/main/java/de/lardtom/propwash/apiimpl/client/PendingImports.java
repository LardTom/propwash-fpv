package de.lardtom.propwash.apiimpl.client;

import de.lardtom.propwash.api.build.ImportResult;
import de.lardtom.propwash.api.build.ImportStatus;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

public final class PendingImports {
    public static final int TIMEOUT_TICKS = 200;

    private final Map<Integer, Pending> pending = new LinkedHashMap<>();
    private final int timeoutTicks;
    private int nextId = 1;
    private long now;

    public PendingImports() {
        this(TIMEOUT_TICKS);
    }

    public PendingImports(int timeoutTicks) {
        if (timeoutTicks < 1) {
            throw new IllegalArgumentException("Zeitlimit muss mindestens einen Tick betragen");
        }
        this.timeoutTicks = timeoutTicks;
    }

    public synchronized Ticket open() {
        int id = this.nextId;
        this.nextId = this.nextId == Integer.MAX_VALUE ? 1 : this.nextId + 1;
        CompletableFuture<ImportResult> future = new CompletableFuture<>();
        Pending previous = this.pending.put(id, new Pending(future, this.now));
        if (previous != null) {
            previous.future().complete(ImportResult.of(ImportStatus.BUSY));
        }
        return new Ticket(id, future);
    }

    public boolean complete(int id, ImportResult result) {
        Pending done;
        synchronized (this) {
            done = this.pending.remove(id);
        }
        return done != null && done.future().complete(result);
    }

    public void tick() {
        List<Pending> expired = new ArrayList<>();
        synchronized (this) {
            this.now++;
            Iterator<Pending> it = this.pending.values().iterator();
            while (it.hasNext()) {
                Pending entry = it.next();
                if (this.now - entry.created() >= this.timeoutTicks) {
                    expired.add(entry);
                    it.remove();
                }
            }
        }
        for (Pending entry : expired) {
            entry.future().complete(ImportResult.of(ImportStatus.NO_TARGET));
        }
    }

    public void failAll(ImportStatus status) {
        List<Pending> open;
        synchronized (this) {
            open = new ArrayList<>(this.pending.values());
            this.pending.clear();
        }
        for (Pending entry : open) {
            entry.future().complete(ImportResult.of(status));
        }
    }

    public synchronized int size() {
        return this.pending.size();
    }

    public record Ticket(int id, CompletableFuture<ImportResult> future) {
    }

    private record Pending(CompletableFuture<ImportResult> future, long created) {
    }
}
