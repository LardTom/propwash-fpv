package de.lardtom.propwash.race.client;

import de.lardtom.propwash.Constants;
import de.lardtom.propwash.network.client.ClientNet;
import de.lardtom.propwash.race.EntryGhost;
import de.lardtom.propwash.race.GhostAccess;
import de.lardtom.propwash.race.GhostKey;
import de.lardtom.propwash.race.GhostTrack;
import de.lardtom.propwash.race.RacePayloads;
import net.minecraft.util.Util;
import org.jspecify.annotations.Nullable;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

public final class GhostDownloads {
    public static final GhostDownloads INSTANCE = new GhostDownloads();
    public static final long TIMEOUT_NANOS = 30_000_000_000L;

    public enum Status {
        LOADED,
        MISSING,
        DENIED,
        BUSY,
        BROKEN,
        TIMEOUT,
        OFFLINE
    }

    public record Result(Status status, @Nullable EntryGhost ghost, @Nullable GhostTrack track) {
        public boolean loaded() {
            return this.status == Status.LOADED && this.ghost != null && this.track != null;
        }
    }

    private static final class Pending {
        final int course;
        final GhostKey key;
        final Consumer<Result> callback;
        final long started;
        final ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        int next;

        Pending(int course, GhostKey key, Consumer<Result> callback, long started) {
            this.course = course;
            this.key = key;
            this.callback = callback;
            this.started = started;
        }
    }

    public static final int CACHE = 4;
    private final Map<Integer, Pending> pending = new HashMap<>();
    private final Map<String, Result> cache = new LinkedHashMap<>(8, 0.75F, true);
    private int nextRequest = 1;
    private int completed;
    private int failed;
    private int lastParts;

    private GhostDownloads() {
    }

    public boolean available() {
        return ClientNet.canSend(RacePayloads.GhostFetchC2S.TYPE);
    }

    public int fetch(int courseId, GhostKey key, GhostAccess.Purpose purpose, Consumer<Result> callback) {
        this.expire(Util.getNanos());
        Result cached = this.cache.get(cacheKey(courseId, key));
        if (cached != null) {
            callback.accept(cached);
            return 0;
        }
        if (!this.available()) {
            callback.accept(new Result(Status.OFFLINE, null, null));
            return 0;
        }
        int request = this.nextRequest++;
        if (this.nextRequest <= 0) {
            this.nextRequest = 1;
        }
        this.pending.put(request, new Pending(courseId, key, callback, Util.getNanos()));
        if (!ClientNet.sendToServer(RacePayloads.GhostFetchC2S.of(request, courseId, key, purpose))) {
            this.pending.remove(request);
            callback.accept(new Result(Status.OFFLINE, null, null));
            return 0;
        }
        return request;
    }

    public void cancel(int request) {
        this.pending.remove(request);
    }

    public boolean loading(int request) {
        return this.pending.containsKey(request);
    }

    void accept(RacePayloads.GhostChunkS2C chunk) {
        Pending entry = this.pending.get(chunk.request());
        if (entry == null || entry.course != chunk.courseId()) {
            return;
        }
        if (chunk.status() != RacePayloads.GhostStatus.DATA) {
            this.finish(chunk.request(), entry, new Result(status(chunk.status()), null, null));
            return;
        }
        if (chunk.part() != entry.next || chunk.parts() <= 0 || chunk.total() <= 0 || entry.bytes.size() + chunk.data().length > chunk.total()) {
            this.finish(chunk.request(), entry, new Result(Status.BROKEN, null, null));
            return;
        }
        entry.bytes.writeBytes(chunk.data());
        entry.next++;
        if (entry.next < chunk.parts()) {
            return;
        }
        if (entry.bytes.size() != chunk.total()) {
            this.finish(chunk.request(), entry, new Result(Status.BROKEN, null, null));
            return;
        }
        this.lastParts = chunk.parts();
        this.finish(chunk.request(), entry, decode(entry.key, entry.bytes.toByteArray()));
    }

    static Result decode(GhostKey key, byte[] data) {
        try {
            EntryGhost ghost = EntryGhost.decode(data);
            if (!ghost.key().equals(key)) {
                return new Result(Status.BROKEN, null, null);
            }
            return new Result(Status.LOADED, ghost, ghost.decodeTrack());
        } catch (IOException e) {
            Constants.LOG.warn("Geist der Bestenliste nicht lesbar: {}", e.getMessage());
            return new Result(Status.BROKEN, null, null);
        }
    }

    private void finish(int request, Pending entry, Result result) {
        this.pending.remove(request);
        if (result.loaded()) {
            this.cache.put(cacheKey(entry.course, entry.key), result);
            while (this.cache.size() > CACHE) {
                this.cache.remove(this.cache.keySet().iterator().next());
            }
            this.completed++;
        } else {
            this.failed++;
        }
        entry.callback.accept(result);
    }

    private static Status status(RacePayloads.GhostStatus status) {
        return switch (status) {
            case DATA -> Status.LOADED;
            case DENIED -> Status.DENIED;
            case BUSY -> Status.BUSY;
            case MISSING -> Status.MISSING;
        };
    }

    public void tick() {
        this.expire(Util.getNanos());
    }

    private void expire(long now) {
        List<Pending> expired = new ArrayList<>();
        Iterator<Map.Entry<Integer, Pending>> it = this.pending.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<Integer, Pending> entry = it.next();
            if (now - entry.getValue().started > TIMEOUT_NANOS) {
                it.remove();
                expired.add(entry.getValue());
            }
        }
        for (Pending entry : expired) {
            this.failed++;
            entry.callback.accept(new Result(Status.TIMEOUT, null, null));
        }
    }

    public void clear() {
        this.pending.clear();
        this.cache.clear();
    }

    public void forget(int courseId, GhostKey key) {
        this.cache.remove(cacheKey(courseId, key));
    }

    public void forgetCourse(int courseId) {
        String prefix = courseId + "/";
        this.cache.keySet().removeIf(key -> key.startsWith(prefix));
    }

    public void retain(int courseId, Collection<GhostKey> keys) {
        Set<String> keep = new HashSet<>();
        for (GhostKey key : keys) {
            keep.add(cacheKey(courseId, key));
        }
        String prefix = courseId + "/";
        this.cache.keySet().removeIf(key -> key.startsWith(prefix) && !keep.contains(key));
    }

    public @Nullable Result cached(int courseId, GhostKey key) {
        return this.cache.get(cacheKey(courseId, key));
    }

    private static String cacheKey(int courseId, GhostKey key) {
        return courseId + "/" + key.fileName();
    }

    public int completed() {
        return this.completed;
    }

    public int failed() {
        return this.failed;
    }

    public int open() {
        return this.pending.size();
    }

    public int lastParts() {
        return this.lastParts;
    }
}
