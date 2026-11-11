package de.lardtom.propwash.race.client;

import de.lardtom.propwash.dev.SelfTest;
import de.lardtom.propwash.dev.SelfTest.StepResult;
import de.lardtom.propwash.drone.DroneModule;
import de.lardtom.propwash.drone.QuadcopterEntity;
import de.lardtom.propwash.race.CourseRegistry;
import de.lardtom.propwash.race.GateEntity;
import de.lardtom.propwash.race.GateGeometry;
import de.lardtom.propwash.race.GateOpenings;
import de.lardtom.propwash.race.GateSize;
import de.lardtom.propwash.race.LapTracker;
import de.lardtom.propwash.race.Leaderboard;
import de.lardtom.propwash.race.RaceLine;
import de.lardtom.propwash.race.RaceModule;
import de.lardtom.propwash.race.RacePayloads;
import de.lardtom.propwash.race.RaceServer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;

final class CovRaceSite {
    static final long MS = 1_000_000L;
    static final int HOME_CHUNKS = 3;
    static final int HOME_TICKS = 500;
    private static final String HOME = "daheim";
    private static final String HOME_PROBE = "daheim prüfen";

    private CovRaceSite() {
    }

    record Site(BlockPos origin, Vec3 home, float yaw, float pitch, Map<BlockPos, BlockState> saved, Map<String, Integer> ids, int minX, int maxX,
                int minZ, int maxZ, int height) {
        Vec3 raw(double dx, double dy, double dz) {
            return new Vec3(this.origin.getX() + dx, this.origin.getY() + dy, this.origin.getZ() + dz);
        }

        BlockPos block(int dx, int dy, int dz) {
            return this.origin.offset(dx, dy, dz);
        }

        int id(String key) {
            return this.ids.getOrDefault(key, GateEntity.NO_COURSE);
        }
    }

    private record Pending(CompletableFuture<Void> future, AtomicReference<String> result) {
    }

    static ServerPlayer player(MinecraftServer server) {
        List<ServerPlayer> players = server.getPlayerList().getPlayers();
        if (players.isEmpty()) {
            throw new IllegalStateException("kein Spieler auf dem Server");
        }
        return players.getFirst();
    }

    static @Nullable StepResult once(SelfTest.StepContext ctx, String key, Function<MinecraftServer, String> action) {
        Object stored = ctx.state().get(key);
        if (stored == null) {
            AtomicReference<String> result = new AtomicReference<>();
            CompletableFuture<Void> future = ctx.onServer(server -> result.set(action.apply(server)));
            ctx.state().put(key, new Pending(future, result));
            return StepResult.pending();
        }
        Pending pending = (Pending) stored;
        if (pending.future().isCompletedExceptionally()) {
            return StepResult.fail(key + ": " + pending.future().exceptionNow());
        }
        if (!pending.future().isDone()) {
            return StepResult.pending();
        }
        String problem = pending.result().get();
        return problem == null ? null : StepResult.fail(key + ": " + problem);
    }

    static void forget(SelfTest.StepContext ctx, String key) {
        ctx.state().remove(key);
    }

    static int phase(SelfTest.StepContext ctx) {
        return (int) ctx.state().getOrDefault("cov.phase", 0);
    }

    static int since(SelfTest.StepContext ctx) {
        return ctx.tick() - (int) ctx.state().getOrDefault("cov.since", 0);
    }

    static void next(SelfTest.StepContext ctx, int phase) {
        ctx.state().put("cov.phase", phase);
        ctx.state().put("cov.since", ctx.tick());
    }

    static StepResult prepare(SelfTest.StepContext ctx, AtomicReference<Site> holder, int offsetX, int offsetZ, int[] bounds, Vec3 stand) {
        StepResult wait = once(ctx, "Platz", server -> {
            ServerPlayer player = player(server);
            ServerLevel level = player.level();
            int x = player.getBlockX() + offsetX;
            int z = player.getBlockZ() + offsetZ;
            Vec3 at = new Vec3(x + stand.x, player.getY(), z + stand.z);
            Site site = new Site(new BlockPos(x, player.getBlockY(), z), player.position(), player.getYRot(), player.getXRot(), new LinkedHashMap<>(),
                    new ConcurrentHashMap<>(), bounds[0], bounds[1], bounds[2], bounds[3], bounds[4]);
            holder.set(site);
            player.getAbilities().flying = false;
            player.teleportTo(level, at.x, at.y, at.z, Set.of(), 0.0F, 0.0F, true);
            return null;
        });
        if (wait != null) {
            return wait;
        }
        if (!ctx.state().containsKey("geladen")) {
            Site site = holder.get();
            CompletableFuture<Void> probe = ctx.onServer(server -> {
                ServerLevel level = player(server).level();
                if (loaded(level, site)) {
                    ctx.state().put("geladen", true);
                }
            });
            ctx.state().put("probe " + (ctx.tick() / 10), probe);
            return ctx.tick() > 600 ? StepResult.fail("Platz lädt nicht") : StepResult.pending();
        }
        wait = once(ctx, "Boden", server -> {
            ServerPlayer player = player(server);
            ServerLevel level = player.level();
            Site old = holder.get();
            int ground = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, old.origin().getX(), old.origin().getZ());
            Site site = new Site(new BlockPos(old.origin().getX(), ground, old.origin().getZ()), old.home(), old.yaw(), old.pitch(), old.saved(),
                    old.ids(), old.minX(), old.maxX(), old.minZ(), old.maxZ(), old.height());
            holder.set(site);
            for (GateEntity gate : level.getEntitiesOfClass(GateEntity.class, area(site), gate -> !gate.isRemoved())) {
                gate.discard();
            }
            clear(level, site);
            Vec3 feet = site.raw(stand.x, 0.0, stand.z);
            player.teleportTo(level, feet.x, feet.y, feet.z, Set.of(), 0.0F, 0.0F, true);
            player.resetFallDistance();
            return null;
        });
        return wait != null ? wait : StepResult.pass();
    }

    static boolean loadedAround(ServerLevel level, Vec3 at, int chunks) {
        int cx = (int) Math.floor(at.x) >> 4;
        int cz = (int) Math.floor(at.z) >> 4;
        for (int dx = -chunks; dx <= chunks; dx++) {
            for (int dz = -chunks; dz <= chunks; dz++) {
                if (!level.areEntitiesActuallyLoadedAndTicking(new ChunkPos(cx + dx, cz + dz))) {
                    return false;
                }
            }
        }
        return true;
    }

    static @Nullable StepResult home(SelfTest.StepContext ctx, Site site) {
        AtomicBoolean loaded = (AtomicBoolean) ctx.state().computeIfAbsent(HOME, key -> new AtomicBoolean());
        if (loaded.get()) {
            return null;
        }
        if (ctx.tick() > HOME_TICKS) {
            return StepResult.fail("Ausgangspunkt nach dem Rückteleport nicht geladen (Chunks im Umkreis von " + HOME_CHUNKS + " um "
                    + site.home() + ")");
        }
        CompletableFuture<?> probe = (CompletableFuture<?>) ctx.state().get(HOME_PROBE);
        if (probe == null || probe.isDone()) {
            ctx.state().put(HOME_PROBE, ctx.onServer(server -> loaded.set(loadedAround(player(server).level(), site.home(), HOME_CHUNKS))));
        }
        return StepResult.pending();
    }

    static boolean loaded(ServerLevel level, Site site) {
        BlockPos o = site.origin();
        for (int cx = (o.getX() + site.minX() - 2) >> 4; cx <= (o.getX() + site.maxX() + 2) >> 4; cx++) {
            for (int cz = (o.getZ() + site.minZ() - 2) >> 4; cz <= (o.getZ() + site.maxZ() + 2) >> 4; cz++) {
                if (!level.areEntitiesActuallyLoadedAndTicking(new ChunkPos(cx, cz))) {
                    return false;
                }
            }
        }
        return true;
    }

    static AABB area(Site site) {
        return new AABB(Vec3.atLowerCornerOf(site.block(site.minX() - 3, -3, site.minZ() - 3)),
                Vec3.atLowerCornerOf(site.block(site.maxX() + 4, site.height() + 6, site.maxZ() + 4)));
    }

    static void set(ServerLevel level, Site site, BlockPos pos, BlockState state) {
        BlockState before = level.getBlockState(pos);
        if (before.equals(state)) {
            return;
        }
        site.saved().putIfAbsent(pos.immutable(), before);
        level.setBlockAndUpdate(pos, state);
    }

    static void clear(ServerLevel level, Site site) {
        for (int x = site.minX(); x <= site.maxX(); x++) {
            for (int z = site.minZ(); z <= site.maxZ(); z++) {
                set(level, site, site.block(x, -1, z), Blocks.SMOOTH_STONE.defaultBlockState());
                for (int y = 0; y < site.height(); y++) {
                    BlockPos pos = site.block(x, y, z);
                    if (!level.getBlockState(pos).isAir()) {
                        set(level, site, pos, Blocks.AIR.defaultBlockState());
                    }
                }
            }
        }
    }

    static String teardown(MinecraftServer server, Site site) {
        ServerPlayer player = player(server);
        ServerLevel level = player.level();
        AABB area = area(site);
        for (GateEntity gate : level.getEntitiesOfClass(GateEntity.class, area, gate -> !gate.isRemoved())) {
            gate.discard();
        }
        for (ItemEntity item : level.getEntitiesOfClass(ItemEntity.class, area, item -> !item.isRemoved())) {
            item.discard();
        }
        CourseRegistry registry = CourseRegistry.get();
        if (registry != null) {
            for (Map.Entry<String, Integer> entry : site.ids().entrySet()) {
                if (entry.getKey().startsWith("course") && registry.course(entry.getValue()) != null) {
                    registry.deleteCourse(entry.getValue());
                }
            }
        }
        List<Map.Entry<BlockPos, BlockState>> entries = new ArrayList<>(site.saved().entrySet());
        for (int i = entries.size() - 1; i >= 0; i--) {
            level.setBlockAndUpdate(entries.get(i).getKey(), entries.get(i).getValue());
        }
        site.saved().clear();
        player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
        player.setShiftKeyDown(false);
        player.teleportTo(level, site.home().x, site.home().y, site.home().z, Set.of(), site.yaw(), site.pitch(), true);
        return null;
    }

    static GateEntity spawn(ServerLevel level, GateGeometry geometry, GateSize size) {
        GateEntity entity = RaceModule.GATE.get().create(level, EntitySpawnReason.COMMAND);
        if (entity == null) {
            throw new IllegalStateException("Gate nicht erzeugt");
        }
        entity.setSize(size);
        entity.applyGeometry(geometry);
        if (!level.addFreshEntity(entity)) {
            throw new IllegalStateException("Gate nicht in die Welt gesetzt");
        }
        return entity;
    }

    static @Nullable GateEntity entity(ServerLevel level, Site site, String key) {
        Integer id = site.ids().get(key);
        return id != null && level.getEntity(id) instanceof GateEntity gate && !gate.isRemoved() ? gate : null;
    }

    static GateEntity require(ServerLevel level, Site site, String key) {
        GateEntity gate = entity(level, site, key);
        if (gate == null) {
            throw new IllegalStateException(key + " fehlt am Server");
        }
        return gate;
    }

    static @Nullable GateEntity clientEntity(@Nullable Site site, String key) {
        Minecraft mc = Minecraft.getInstance();
        Integer id = site == null ? null : site.ids().get(key);
        return id != null && mc.level != null && mc.level.getEntity(id) instanceof GateEntity gate && !gate.isRemoved() ? gate : null;
    }

    static void face(ServerPlayer player, float yaw) {
        player.setYRot(yaw);
        player.setXRot(0.0F);
        player.yRotO = yaw;
        player.xRotO = 0.0F;
        player.setYHeadRot(yaw);
    }

    static void lookAt(LocalPlayer player, Vec3 target) {
        Vec3 eye = player.getEyePosition();
        double dx = target.x - eye.x;
        double dy = target.y - eye.y;
        double dz = target.z - eye.z;
        float yaw = (float) Math.toDegrees(Math.atan2(-dx, dz));
        float pitch = (float) -Math.toDegrees(Math.atan2(dy, Math.sqrt(dx * dx + dz * dz)));
        player.setYRot(yaw);
        player.setXRot(pitch);
        player.yRotO = yaw;
        player.xRotO = pitch;
        player.setYHeadRot(yaw);
        player.setYBodyRot(yaw);
        player.yBodyRotO = yaw;
        player.yHeadRotO = yaw;
    }

    static List<Vec3> waypointPath(List<GateGeometry> gates, double lead) {
        return waypointPath(gates, lead, -1, Vec3.ZERO, Vec3.ZERO);
    }

    static List<Vec3> waypointPath(List<GateGeometry> gates, double lead, int index, Vec3 point, Vec3 direction) {
        List<Vec3> path = new ArrayList<>();
        int count = gates.size();
        GateGeometry start = gates.getFirst();
        for (int i = 0; i <= count; i++) {
            GateGeometry gate = gates.get(i % count);
            if (i > 0 && i < count) {
                GateGeometry previous = gates.get(i - 1);
                if (RaceLine.needsDetour(previous, gate)) {
                    Vec3 exit = path.getLast();
                    double reach = Math.max(previous.width(), gate.width()) * 0.5 + 2.5;
                    GateGeometry.Waypoint first = gate.waypoints().getFirst();
                    path.add(new Vec3(exit.x + previous.sideX() * reach, exit.y, exit.z + previous.sideZ() * reach));
                    path.add(new Vec3(first.x() - first.dx() * lead + previous.sideX() * reach, first.y(),
                            first.z() - first.dz() * lead + previous.sideZ() * reach));
                }
            }
            if (i == count) {
                Vec3 exit = path.getLast();
                GateGeometry.Waypoint first = start.waypoints().getFirst();
                double side = 9.0;
                path.add(new Vec3(exit.x + start.sideX() * side, exit.y, exit.z + start.sideZ() * side));
                path.add(new Vec3(first.x() - first.dx() * (lead + 1.0) + start.sideX() * side, first.y(),
                        first.z() - first.dz() * (lead + 1.0) + start.sideZ() * side));
            }
            if (i == index) {
                path.add(point.subtract(direction.scale(lead)));
                path.add(point.add(direction.scale(lead)));
                continue;
            }
            List<GateGeometry.Waypoint> points = i == count ? List.of(gate.waypoints().getFirst()) : gate.waypoints();
            for (GateGeometry.Waypoint wp : points) {
                path.add(new Vec3(wp.x() - wp.dx() * lead, wp.y() - wp.dy() * lead, wp.z() - wp.dz() * lead));
                path.add(new Vec3(wp.x() + wp.dx() * lead, wp.y() + wp.dy() * lead, wp.z() + wp.dz() * lead));
            }
        }
        return path;
    }

    static List<LapTracker.Event> fly(int course, List<GateGeometry> gates, List<Vec3> path, GateOpenings openings) {
        LapTracker tracker = new LapTracker();
        tracker.setLapTarget(0);
        tracker.setCourse(course, gates, 0L);
        tracker.setOpenings(openings);
        long now = 1_000L * MS;
        Vec3 at = path.getFirst();
        tracker.update(now, at.x, at.y, at.z);
        List<LapTracker.Event> events = new ArrayList<>();
        for (int i = 1; i < path.size(); i++) {
            Vec3 to = path.get(i);
            int steps = Math.max(1, (int) Math.ceil(at.distanceTo(to) / 0.25));
            for (int s = 1; s <= steps; s++) {
                now += 15 * MS;
                Vec3 p = at.lerp(to, s / (double) steps);
                tracker.update(now, p.x, p.y, p.z);
                events.addAll(tracker.drainEvents());
            }
            at = to;
        }
        return events;
    }

    static List<Integer> of(List<LapTracker.Event> events, LapTracker.EventType type) {
        return events.stream().filter(e -> e.type() == type).map(LapTracker.Event::gate).toList();
    }

    static List<Integer> upTo(int count) {
        List<Integer> out = new ArrayList<>();
        for (int i = 1; i < count; i++) {
            out.add(i);
        }
        return out;
    }

    static @Nullable String clientLap(int course, List<GateGeometry> gates, List<Vec3> path, GateOpenings openings) {
        List<LapTracker.Event> events = fly(course, gates, path, openings);
        if (!of(events, LapTracker.EventType.GATE).equals(upTo(gates.size())) || of(events, LapTracker.EventType.LAP).size() != 1
                || !of(events, LapTracker.EventType.MISSED).isEmpty() || !of(events, LapTracker.EventType.WRONG_WAY).isEmpty()) {
            return "Client zählt " + events;
        }
        return null;
    }

    record ServerLap(@Nullable String problem, long nanos) {
    }

    private static final java.util.concurrent.atomic.AtomicLong LAST_START = new java.util.concurrent.atomic.AtomicLong(Long.MIN_VALUE);
    private static final java.util.concurrent.atomic.AtomicInteger LAPS = new java.util.concurrent.atomic.AtomicInteger();

    static ServerLap serverLap(MinecraftServer server, ServerPlayer pilot, int courseId, List<Vec3> path, boolean expectAccepted) {
        ServerLevel level = pilot.level();
        CourseRegistry registry = CourseRegistry.get();
        CourseRegistry.Course course = registry == null ? null : registry.course(courseId);
        if (course == null) {
            return new ServerLap("Kurs " + courseId + " fehlt am Server", 0L);
        }
        registry.setLaps(course, 1);
        QuadcopterEntity drone = DroneModule.DRONE.get().create(level, EntitySpawnReason.COMMAND);
        if (drone == null) {
            return new ServerLap("Drohne nicht erzeugt", 0L);
        }
        Vec3 first = path.getFirst();
        drone.snapTo(first.x, first.y, first.z, 0.0F, 0.0F);
        drone.setPilot(pilot);
        if (!level.addFreshEntity(drone)) {
            return new ServerLap("Drohne nicht gespawnt", 0L);
        }
        try {
            long lap = Math.max(RaceServer.minimumLapNanos(course, pilot) * 3L, 8_000L * MS) + (500L - LAPS.incrementAndGet() % 500) * 7L * MS;
            double total = 0.0;
            for (int i = 1; i < path.size(); i++) {
                total += path.get(i - 1).distanceTo(path.get(i));
            }
            double startAt = path.get(0).distanceTo(path.get(1)) * 0.5;
            double endAt = total - path.get(path.size() - 2).distanceTo(path.getLast()) * 0.5;
            double perBlock = lap / (endAt - startAt);
            long t0 = Math.max(System.nanoTime() + 60_000L * MS, LAST_START.get() + 300_000L * MS);
            LAST_START.set(t0);
            RaceServer.Verdict verdict = RaceServer.handle(new RacePayloads.RaceReportC2S(RacePayloads.ReportKind.START, courseId, 0, 0L, 0L), pilot,
                    t0 - 100L * MS);
            if (verdict != RaceServer.Verdict.ACCEPTED) {
                return new ServerLap("Start abgelehnt: " + verdict, 0L);
            }
            double travelled = 0.0;
            long t = t0;
            for (int i = 1; i < path.size(); i++) {
                Vec3 a = path.get(i - 1);
                Vec3 b = path.get(i);
                double length = a.distanceTo(b);
                int steps = Math.max(1, (int) Math.ceil(length / 0.5));
                for (int s = 1; s <= steps; s++) {
                    t = t0 + Math.round((travelled + length * s / steps - startAt) * perBlock);
                    RaceServer.onDroneMoved(pilot, a.lerp(b, (s - 1) / (double) steps), a.lerp(b, s / (double) steps), t);
                }
                travelled += length;
            }
            verdict = RaceServer.handle(new RacePayloads.RaceReportC2S(RacePayloads.ReportKind.LAP, courseId, 1, lap, 0L), pilot, t + 100L * MS);
            RaceServer.handle(new RacePayloads.RaceReportC2S(RacePayloads.ReportKind.ABORT, courseId, 0, 0L, 0L), pilot, t + 200L * MS);
            UUID id = pilot.getUUID();
            List<Leaderboard.Entry> entries = new ArrayList<>(registry.board(course).entries());
            entries.addAll(registry.board(course).assistedEntries());
            boolean listed = entries.stream().anyMatch(entry -> entry.player().equals(id) && entry.nanos() == lap);
            if (!expectAccepted) {
                return verdict == RaceServer.Verdict.ACCEPTED || listed ? new ServerLap("Server nimmt die Runde an, obwohl sie nicht zählen darf ("
                        + verdict + ")", lap) : new ServerLap(null, 0L);
            }
            if (verdict != RaceServer.Verdict.ACCEPTED) {
                return new ServerLap("Runde am Server abgelehnt: " + verdict, lap);
            }
            if (!listed) {
                return new ServerLap("Runde mit " + lap / MS + " ms fehlt in der Bestenliste", lap);
            }
            return new ServerLap(null, lap);
        } finally {
            drone.setPilot(null);
            drone.discard();
        }
    }
}
