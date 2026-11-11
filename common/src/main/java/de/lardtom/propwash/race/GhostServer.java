package de.lardtom.propwash.race;

import de.lardtom.propwash.Constants;
import de.lardtom.propwash.api.build.DroneData;
import de.lardtom.propwash.apiimpl.Conversions;
import de.lardtom.propwash.drone.CameraMount;
import de.lardtom.propwash.drone.CraftNames;
import de.lardtom.propwash.drone.QuadcopterEntity;
import de.lardtom.propwash.network.PropwashNet;
import de.lardtom.propwash.network.ServerPayloadContext;
import de.lardtom.propwash.server.WorldRules;
import de.lardtom.propwash.server.rules.WorldRuleSpecs;
import de.lardtom.propwash.share.ShareLayer;
import de.lardtom.propwash.share.ShareService;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import org.jspecify.annotations.Nullable;

import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Locale;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public final class GhostServer {
    public static final long FINE_INTERVAL_NANOS = 20_000_000L;
    public static final int CHUNKS_PER_TICK = 2;
    public static final int MAX_BYTES_PER_TICK = 96 * 1024;
    public static final int MAX_QUEUED = 2;
    private static final Map<UUID, ArrayDeque<Transfer>> TRANSFERS = new HashMap<>();
    private static final Map<UUID, ArrayDeque<Long>> REQUESTS = new HashMap<>();
    private static final int MAX_REQUESTS = 6;
    private static final long REQUEST_WINDOW_NANOS = 10_000_000_000L;

    private GhostServer() {
    }

    private static final class Transfer {
        final int request;
        final int course;
        final byte[] data;
        final int parts;
        int next;

        Transfer(int request, int course, byte[] data) {
            this.request = request;
            this.course = course;
            this.data = data;
            this.parts = Math.max(1, (data.length + RacePayloads.GhostChunkS2C.CHUNK - 1) / RacePayloads.GhostChunkS2C.CHUNK);
        }

        boolean done() {
            return this.next >= this.parts;
        }

        RacePayloads.GhostChunkS2C chunk() {
            int from = this.next * RacePayloads.GhostChunkS2C.CHUNK;
            int to = Math.min(this.data.length, from + RacePayloads.GhostChunkS2C.CHUNK);
            RacePayloads.GhostChunkS2C chunk = new RacePayloads.GhostChunkS2C(this.request, this.course, RacePayloads.GhostStatus.DATA, this.next,
                    this.parts, this.data.length, Arrays.copyOfRange(this.data, from, to));
            this.next++;
            return chunk;
        }
    }

    public static void clear() {
        TRANSFERS.clear();
        REQUESTS.clear();
    }

    public static void leave(ServerPlayer player) {
        TRANSFERS.remove(player.getUUID());
        REQUESTS.remove(player.getUUID());
    }

    public static int rule(@Nullable MinecraftServer server) {
        return WorldRules.get(server, WorldRuleSpecs.FOREIGN_GHOSTS);
    }

    public static boolean admin(ServerPlayer player) {
        return CourseRegistry.isOperator(player);
    }

    public static int access(ServerPlayer player) {
        return GhostAccess.flags(rule(player.level().getServer()), admin(player));
    }

    public static String shareCode(@Nullable QuadcopterEntity drone) {
        if (drone == null) {
            return "";
        }
        try {
            String name = drone.hasCustomName() ? CraftNames.of(drone.getCustomName()) : "";
            DroneData data = new DroneData(Conversions.spec(drone.build()), Conversions.paint(drone.paint()), Conversions.tune(drone.tune()),
                    name.isEmpty() ? Optional.empty() : Optional.of(name));
            String code = ShareService.encode(data, ShareLayer.DEFAULT_EXPORT, true, Optional.empty());
            return code.length() <= EntryGhost.MAX_CODE ? code : "";
        } catch (RuntimeException e) {
            Constants.LOG.debug("Sharecode der Drohne für den Geist nicht erzeugt: {}", e.toString());
            return "";
        }
    }

    public static float uptilt(@Nullable QuadcopterEntity drone) {
        return drone == null ? Float.NaN : CameraMount.uptilt(drone.build(), drone.tune().values());
    }

    public static boolean store(CourseRegistry registry, CourseRegistry.Course course, @Nullable GhostTrack cut, ServerPlayer player, String frame,
                                long lapNanos, long dateMillis, int assist) {
        EntryGhosts store = registry.entryGhosts();
        if (cut == null || !store.enabled()) {
            return false;
        }
        GhostKey key = new GhostKey(player.getUUID(), frame, lapNanos, dateMillis);
        boolean listed = false;
        for (Leaderboard.Entry entry : registry.board(course).storedTimes()) {
            if (key.matches(entry)) {
                listed = true;
                break;
            }
        }
        if (!listed) {
            return false;
        }
        QuadcopterEntity drone = RaceServer.pilotedDrone(player);
        boolean same = drone != null && drone.build().frame().key().equals(frame);
        GhostTrack track = same ? cut.withLook(GhostLook.of(drone.build(), drone.paint())) : cut;
        byte[] data = GhostCodec.encode(track);
        if (data.length > GhostCodec.MAX_ENCODED_BYTES) {
            return false;
        }
        EntryGhost.Info info = new EntryGhost.Info(player.getUUID(), player.getGameProfile().name(), key.frame(), lapNanos, dateMillis, assist,
                track.layout(), same ? uptilt(drone) : Float.NaN, same ? shareCode(drone) : "", EntryGhost.Source.SERVER);
        return store.put(course.key(), new EntryGhost(info, data));
    }

    public static List<RacePayloads.BoardRow> decorate(CourseRegistry registry, CourseRegistry.Course course, List<RacePayloads.BoardRow> rows,
                                                       List<Leaderboard.Entry> entries) {
        EntryGhosts store = registry.entryGhosts();
        RacePayloads.BoardRow[] out = rows.toArray(new RacePayloads.BoardRow[0]);
        for (int i = 0; i < out.length && i < entries.size(); i++) {
            Leaderboard.Entry entry = entries.get(i);
            GhostKey key = GhostKey.of(entry);
            EntryGhost.Info info = store.enabled() ? store.info(course.key(), key) : null;
            boolean usable = info != null && info.layout() == course.toData().layoutHash();
            out[i] = out[i].withGhost(entry.player(), info == null ? "" : info.code(), usable);
        }
        return List.of(out);
    }

    private static boolean allowRequest(UUID player, long now) {
        ArrayDeque<Long> times = REQUESTS.computeIfAbsent(player, id -> new ArrayDeque<>());
        while (!times.isEmpty() && now - times.peekFirst() > REQUEST_WINDOW_NANOS) {
            times.pollFirst();
        }
        if (times.size() >= MAX_REQUESTS) {
            return false;
        }
        times.addLast(now);
        return true;
    }

    static void onFetch(RacePayloads.GhostFetchC2S request, ServerPayloadContext context) {
        ServerPlayer player = context.player();
        RacePayloads.GhostStatus status = fetch(player, request, System.nanoTime());
        if (status != RacePayloads.GhostStatus.DATA) {
            PropwashNet.sendToPlayer(player, RacePayloads.GhostChunkS2C.refused(request.request(), request.courseId(), status));
        }
    }

    public static RacePayloads.GhostStatus fetch(ServerPlayer player, RacePayloads.GhostFetchC2S request, long now) {
        if (!allowRequest(player.getUUID(), now)) {
            return RacePayloads.GhostStatus.BUSY;
        }
        CourseRegistry registry = CourseRegistry.get();
        CourseRegistry.Course course = registry == null ? null : registry.course(request.courseId());
        if (registry == null || course == null) {
            return RacePayloads.GhostStatus.MISSING;
        }
        GhostKey key = request.key();
        Leaderboard.Entry listed = null;
        for (Leaderboard.Entry entry : registry.board(course).storedTimes()) {
            if (key.matches(entry)) {
                listed = entry;
                break;
            }
        }
        if (listed == null) {
            return RacePayloads.GhostStatus.MISSING;
        }
        boolean own = listed.player().equals(player.getUUID());
        if (!GhostAccess.allowed(rule(player.level().getServer()), own, admin(player), request.purpose())) {
            return RacePayloads.GhostStatus.DENIED;
        }
        byte[] data = registry.entryGhosts().read(course.key(), key);
        if (data == null || data.length > EntryGhost.MAX_BYTES) {
            return RacePayloads.GhostStatus.MISSING;
        }
        EntryGhost.Info info = registry.entryGhosts().info(course.key(), key);
        if (info == null || info.layout() != course.toData().layoutHash()) {
            return RacePayloads.GhostStatus.MISSING;
        }
        ArrayDeque<Transfer> queue = TRANSFERS.computeIfAbsent(player.getUUID(), id -> new ArrayDeque<>());
        if (queue.size() >= MAX_QUEUED) {
            return RacePayloads.GhostStatus.BUSY;
        }
        queue.addLast(new Transfer(request.request(), course.id, data));
        return RacePayloads.GhostStatus.DATA;
    }

    public static void tick(MinecraftServer server) {
        if (TRANSFERS.isEmpty()) {
            return;
        }
        Iterator<Map.Entry<UUID, ArrayDeque<Transfer>>> it = TRANSFERS.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<UUID, ArrayDeque<Transfer>> entry = it.next();
            ServerPlayer player = server.getPlayerList().getPlayer(entry.getKey());
            ArrayDeque<Transfer> queue = entry.getValue();
            if (player == null || queue.isEmpty()) {
                it.remove();
                continue;
            }
            int bytes = 0;
            int chunks = 0;
            while (chunks < CHUNKS_PER_TICK && bytes < MAX_BYTES_PER_TICK && !queue.isEmpty()) {
                Transfer transfer = queue.peekFirst();
                RacePayloads.GhostChunkS2C chunk = transfer.chunk();
                bytes += chunk.data().length;
                chunks++;
                PropwashNet.sendToPlayer(player, chunk);
                if (transfer.done()) {
                    queue.pollFirst();
                }
            }
            if (queue.isEmpty()) {
                it.remove();
            }
        }
    }

    public static boolean pending(ServerPlayer player) {
        ArrayDeque<Transfer> queue = TRANSFERS.get(player.getUUID());
        return queue != null && !queue.isEmpty();
    }

    static void onRemove(RacePayloads.TimeRemoveC2S request, ServerPayloadContext context) {
        ServerPlayer player = context.player();
        CourseRegistry registry = CourseRegistry.get();
        CourseRegistry.Course course = registry == null ? null : registry.course(request.courseId());
        if (registry == null || course == null) {
            return;
        }
        if (!GhostAccess.mayRemove(admin(player))) {
            player.sendSystemMessage(Component.translatable("propwash.race.ghosts.remove.denied").withStyle(ChatFormatting.RED));
            RaceServer.sendLeaderboard(player, course.id, false);
            return;
        }
        Leaderboard.Section section = request.assisted() ? Leaderboard.Section.ASSISTED : Leaderboard.Section.CLEAN;
        List<Leaderboard.Entry> removed = remove(registry, course, request.player(), request.frame(), section, player.getGameProfile().name(),
                player.getUUID(), "window");
        if (removed.isEmpty()) {
            player.sendSystemMessage(Component.translatable("propwash.race.ghosts.remove.none").withStyle(ChatFormatting.GRAY));
        } else {
            player.sendSystemMessage(removedMessage(course, removed));
        }
        RaceServer.sendLeaderboard(player, course.id, false);
    }

    public static Component removedMessage(CourseRegistry.Course course, List<Leaderboard.Entry> removed) {
        Leaderboard.Entry best = removed.getFirst();
        for (Leaderboard.Entry entry : removed) {
            if (entry.nanos() < best.nanos()) {
                best = entry;
            }
        }
        return Component.translatable("propwash.race.ghosts.remove.done", removed.size(), best.name(), RaceFormat.time(best.nanos()),
                RaceServer.className(best.frame()), CourseRegistry.ref(course)).withStyle(ChatFormatting.YELLOW);
    }

    public static List<Leaderboard.Entry> remove(CourseRegistry registry, CourseRegistry.Course course, UUID pilot, @Nullable String frame,
                                                 Leaderboard.Section section, String by, @Nullable UUID byId, String via) {
        List<Leaderboard.Entry> removed = registry.removeTimes(course, pilot, frame, section);
        if (!removed.isEmpty()) {
            Leaderboard.Entry best = removed.getFirst();
            for (Leaderboard.Entry entry : removed) {
                if (entry.nanos() < best.nanos()) {
                    best = entry;
                }
            }
            String line = logLine(Instant.now(), by, byId, course, best, frame, section, removed.size(), via);
            registry.entryGhosts().log(line);
            Constants.LOG.info("Bestenliste: {}", line);
            registry.save();
        }
        return removed;
    }

    public static String logLine(Instant when, String by, @Nullable UUID byId, CourseRegistry.Course course, Leaderboard.Entry best,
                                 @Nullable String frame, Leaderboard.Section section, int count, String via) {
        return when + " removed by=" + clean(by) + (byId == null ? "" : "(" + byId + ")") + " via=" + via + " course=" + course.id + " key="
                + course.key() + " pilot=" + clean(best.name()) + "(" + best.player() + ") class=" + (frame == null ? "*" : clean(frame)) + " section="
                + section.name().toLowerCase(Locale.ROOT) + " times=" + count + " best=" + RaceFormat.time(best.nanos());
    }

    private static String clean(String text) {
        StringBuilder out = new StringBuilder(text.length());
        for (char c : text.toCharArray()) {
            out.append(Character.isWhitespace(c) || Character.isISOControl(c) ? '_' : c);
        }
        return out.isEmpty() ? "-" : out.toString();
    }
}
