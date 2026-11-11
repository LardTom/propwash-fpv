package de.lardtom.propwash.race;

import de.lardtom.propwash.Constants;
import de.lardtom.propwash.network.PropwashNet;
import de.lardtom.propwash.network.ServerPayloadContext;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import org.jspecify.annotations.Nullable;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

public final class GhostUploads {
    public static final int MAX_PENDING = 2;
    public static final long DEADLINE_NANOS = 120_000_000_000L;
    public static final int MAX_CHUNKS_PER_SECOND = 30;
    private static final long SECOND = 1_000_000_000L;
    private static final Map<UUID, ArrayDeque<Pending>> PENDING = new HashMap<>();
    private static final Map<UUID, long[]> RATE = new HashMap<>();
    private static int nextToken = 1;
    private static int accepted;
    private static int rejected;
    private static @Nullable Outcome last;

    public record Outcome(UUID player, int courseId, GhostKey key, GhostUploadCheck.Result result, int bytes, int samples, long intervalNanos) {
    }

    private static final class Pending {
        final int token;
        final int courseId;
        final UUID course;
        final GhostKey key;
        final int layout;
        final GhostUploadCheck.Reference reference;
        final long deadline;
        final ByteArrayOutputStream data = new ByteArrayOutputStream();
        int parts = -1;
        int total;
        int next;

        Pending(int token, int courseId, UUID course, GhostKey key, int layout, GhostUploadCheck.Reference reference, long deadline) {
            this.token = token;
            this.courseId = courseId;
            this.course = course;
            this.key = key;
            this.layout = layout;
            this.reference = reference;
            this.deadline = deadline;
        }
    }

    private GhostUploads() {
    }

    public static void clear() {
        PENDING.clear();
        RATE.clear();
    }

    public static void leave(ServerPlayer player) {
        PENDING.remove(player.getUUID());
        RATE.remove(player.getUUID());
    }

    public static int accepted() {
        return accepted;
    }

    public static int rejected() {
        return rejected;
    }

    public static @Nullable Outcome last() {
        return last;
    }

    public static int pending(UUID player) {
        ArrayDeque<Pending> queue = PENDING.get(player);
        return queue == null ? 0 : queue.size();
    }

    public static boolean expect(ServerPlayer player, CourseRegistry.Course course, GhostKey key, int lap, GhostUploadCheck.Reference reference) {
        if (reference.count() < 2 || !player.connection.isAcceptingMessages() || !PropwashNet.canSend(player, RacePayloads.GhostUploadS2C.TYPE)) {
            return false;
        }
        int token = nextToken++;
        if (nextToken <= 0) {
            nextToken = 1;
        }
        ArrayDeque<Pending> queue = PENDING.computeIfAbsent(player.getUUID(), id -> new ArrayDeque<>());
        while (queue.size() >= MAX_PENDING) {
            queue.pollFirst();
        }
        queue.addLast(new Pending(token, course.id, course.key(), key, course.toData().layoutHash(), reference, System.nanoTime() + DEADLINE_NANOS));
        return PropwashNet.sendToPlayer(player, new RacePayloads.GhostUploadS2C(token, course.id, lap, key.nanos()));
    }

    static void onChunk(RacePayloads.GhostUploadC2S chunk, ServerPayloadContext context) {
        receive(context.player(), chunk, System.nanoTime());
    }

    public static GhostUploadCheck.@Nullable Result receive(ServerPlayer player, RacePayloads.GhostUploadC2S chunk, long now) {
        ArrayDeque<Pending> queue = PENDING.get(player.getUUID());
        Pending pending = null;
        if (queue != null) {
            for (Pending candidate : queue) {
                if (candidate.token == chunk.token()) {
                    pending = candidate;
                    break;
                }
            }
        }
        if (pending == null) {
            return null;
        }
        if (chunk.declined() || !allow(player.getUUID(), now)) {
            queue.remove(pending);
            return null;
        }
        if (pending.parts < 0) {
            pending.parts = chunk.parts();
            pending.total = chunk.total();
        }
        boolean fits = chunk.parts() == pending.parts && chunk.total() == pending.total && chunk.part() == pending.next
                && pending.data.size() + chunk.data().length <= pending.total;
        if (!fits) {
            queue.remove(pending);
            return null;
        }
        pending.data.writeBytes(chunk.data());
        pending.next++;
        if (pending.next < pending.parts) {
            return null;
        }
        queue.remove(pending);
        if (pending.data.size() != pending.total) {
            return null;
        }
        return finish(player, pending);
    }

    private static boolean allow(UUID player, long now) {
        long[] window = RATE.computeIfAbsent(player, id -> new long[]{now, 0L});
        if (now - window[0] > SECOND || now < window[0]) {
            window[0] = now;
            window[1] = 0L;
        }
        return ++window[1] <= MAX_CHUNKS_PER_SECOND;
    }

    private static GhostUploadCheck.Result finish(ServerPlayer player, Pending pending) {
        byte[] bytes = pending.data.toByteArray();
        GhostUploadCheck.Result result = GhostUploadCheck.Result.MISSING;
        GhostTrack upload = null;
        try {
            upload = GhostCodec.decode(bytes);
        } catch (IOException | RuntimeException e) {
            Constants.LOG.debug("Geisterspur von {} nicht lesbar: {}", player.getGameProfile().name(), e.toString());
        }
        CourseRegistry registry = CourseRegistry.get();
        CourseRegistry.Course course = registry == null ? null : registry.course(pending.courseId);
        EntryGhost stored = course == null || !course.key().equals(pending.course) ? null : registry.entryGhosts().get(course.key(), pending.key);
        if (upload == null) {
            result = GhostUploadCheck.Result.FORMAT;
        } else if (stored != null && stored.info().source() == EntryGhost.Source.SERVER && course.toData().layoutHash() == pending.layout
                && stored.info().layout() == pending.layout) {
            result = GhostUploadCheck.check(pending.reference, upload, course.toData().geometry(), course.pointToPoint());
            if (result.accepted() && !replace(registry, course, stored, upload)) {
                result = GhostUploadCheck.Result.FORMAT;
            }
        }
        if (result.accepted()) {
            accepted++;
        } else {
            rejected++;
        }
        last = new Outcome(player.getUUID(), pending.courseId, pending.key, result, bytes.length, upload == null ? 0 : upload.samples(),
                upload == null ? 0L : upload.intervalNanos());
        Constants.LOG.debug("Geisterspur von {} für Kurs {} ({} Bytes): {}", player.getGameProfile().name(), pending.courseId, bytes.length, result);
        return result;
    }

    private static boolean replace(CourseRegistry registry, CourseRegistry.Course course, EntryGhost stored, GhostTrack upload) {
        GhostTrack server;
        try {
            server = stored.decodeTrack();
        } catch (IOException e) {
            return false;
        }
        GhostTrack merged = merge(server, upload);
        byte[] data = GhostCodec.encode(merged);
        if (data.length > GhostCodec.MAX_ENCODED_BYTES) {
            return false;
        }
        EntryGhost.Info info = stored.info();
        EntryGhost.Info pilot = new EntryGhost.Info(info.player(), info.name(), info.frame(), info.nanos(), info.dateMillis(), info.assist(), info.layout(),
                info.uptilt(), info.code(), EntryGhost.Source.PILOT);
        return registry.entryGhosts().put(course.key(), new EntryGhost(pilot, data));
    }

    public static GhostTrack merge(GhostTrack server, GhostTrack upload) {
        int drop = (int) Math.max(0L, (upload.leadNanos() - server.leadNanos() + upload.intervalNanos() - 1L) / upload.intervalNanos());
        drop = Math.min(drop, upload.leadSamples());
        double[] positions = Arrays.copyOfRange(upload.positions(), drop * 3, upload.samples() * 3);
        float[] rotations = Arrays.copyOfRange(upload.rotations(), drop * 4, upload.samples() * 4);
        return new GhostTrack(upload.intervalNanos(), positions, rotations, server.lapNanos(), server.frame(), server.layout(), server.look(),
                server.rules(), upload.leadNanos() - drop * upload.intervalNanos());
    }

    public static void tick(MinecraftServer server) {
        if (PENDING.isEmpty()) {
            return;
        }
        long now = System.nanoTime();
        Iterator<Map.Entry<UUID, ArrayDeque<Pending>>> it = PENDING.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<UUID, ArrayDeque<Pending>> entry = it.next();
            entry.getValue().removeIf(pending -> now - pending.deadline > 0L);
            if (entry.getValue().isEmpty() || server.getPlayerList().getPlayer(entry.getKey()) == null) {
                it.remove();
            }
        }
    }
}
