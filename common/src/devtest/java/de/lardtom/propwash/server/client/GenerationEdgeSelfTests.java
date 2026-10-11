package de.lardtom.propwash.server.client;

import de.lardtom.propwash.content.ContentIds;
import de.lardtom.propwash.core.CoreComponents;
import de.lardtom.propwash.dev.SelfTest;
import de.lardtom.propwash.dev.SelfTest.StepResult;
import de.lardtom.propwash.dev.client.Screenshots;
import de.lardtom.propwash.build.DroneBuild;
import de.lardtom.propwash.drone.DroneInteractions;
import de.lardtom.propwash.drone.DroneModule;
import de.lardtom.propwash.drone.DronePiloting;
import de.lardtom.propwash.drone.PilotMode;
import de.lardtom.propwash.drone.QuadcopterEntity;
import de.lardtom.propwash.drone.client.EdgeGuard;
import de.lardtom.propwash.drone.client.PilotSession;
import de.lardtom.propwash.input.StickPositions;
import de.lardtom.propwash.osd.OsdData;
import de.lardtom.propwash.osd.OsdWarnings;
import de.lardtom.propwash.osd.client.LiveOsdSource;
import de.lardtom.propwash.server.AnchorRules;
import de.lardtom.propwash.server.EdgeFence;
import de.lardtom.propwash.server.PilotLink;
import de.lardtom.propwash.server.RangeManager;
import de.lardtom.propwash.server.ServerRules;
import de.lardtom.propwash.server.WorldRules;
import de.lardtom.propwash.server.rules.WorldRuleSpecs;
import de.lardtom.propwash.sim.DroneSim;
import de.lardtom.propwash.sim.StabMode;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.joml.Vector3d;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

public final class GenerationEdgeSelfTests {
    static final String NAME = "weltrand: Drohne bremst am Rand der erzeugten Welt, nichts Neues entsteht, keine Löcher";
    private static final String RIG = "edge_rig";
    private static final int SEARCH = 48;
    private static final int MIN_EDGE = 4;
    private static final double START_BEFORE = 44.0;
    private static final double HEIGHT = 7.0;
    private static final double PUSH = 25.0;
    private static final double RESUME = 12.0;
    private static final int PUSH_TICKS = 160;
    private static final int CHECK_RADIUS = 3;
    private static final double ALONG_EDGE_YAW = 160.0;
    private static final double SCENE_HEIGHT = 14.0;

    private GenerationEdgeSelfTests() {
    }

    public static void register() {
        SelfTest.clientStep(NAME, 2400, GenerationEdgeSelfTests::edgeStep);
    }

    public static Screenshots.Scene fpvScene() {
        return new EdgeScene();
    }

    private static StepResult edgeStep(SelfTest.StepContext ctx) {
        Rig rig = (Rig) ctx.state().computeIfAbsent(RIG, key -> new Rig());
        if (rig.failure != null) {
            return cleanup(ctx, rig, StepResult.fail(rig.failure));
        }
        if (rig.phase.ordinal() < Phase.PUSHED.ordinal()) {
            StepResult result = rig.advance(ctx, PUSH_TICKS);
            if (result.status() == StepResult.Status.FAIL) {
                return cleanup(ctx, rig, result);
            }
            return StepResult.pending();
        }
        return switch (rig.phase) {
            case PUSHED -> {
                String problem = checkClient(rig);
                if (problem != null) {
                    yield cleanup(ctx, rig, StepResult.fail(problem));
                }
                rig.pending = ctx.onServer(server -> inspect(server, rig));
                rig.phase = Phase.INSPECTING;
                yield StepResult.pending();
            }
            case INSPECTING -> {
                if (!rig.ready()) {
                    yield StepResult.pending();
                }
                if (rig.scans == null) {
                    yield StepResult.pending();
                }
                for (CompletableFuture<Boolean> scan : rig.scans) {
                    if (!scan.isDone()) {
                        yield StepResult.pending();
                    }
                }
                rig.pending = ctx.onServer(server -> judge(server, rig));
                rig.phase = Phase.JUDGED;
                yield StepResult.pending();
            }
            case JUDGED -> {
                if (!rig.ready()) {
                    yield StepResult.pending();
                }
                if (rig.failure != null) {
                    yield cleanup(ctx, rig, StepResult.fail(rig.failure));
                }
                String holes = checkClientChunks(rig);
                if (holes != null) {
                    yield cleanup(ctx, rig, StepResult.fail(holes));
                }
                rig.pending = ctx.onServer(server -> WorldRules.set(server, WorldRuleSpecs.DRONES_GENERATE_CHUNKS, 1));
                rig.phase = Phase.RESUMING;
                rig.since = ctx.tick();
                yield StepResult.pending();
            }
            case RESUMING -> {
                PilotSession session = PilotSession.INSTANCE;
                DroneSim sim = session.sim();
                if (sim == null || !session.isActive()) {
                    yield cleanup(ctx, rig, StepResult.fail("Sitzung nach dem Einschalten der Regel verloren"));
                }
                session.setTestInput(rig.hover(sim), true, StabMode.ANGLE);
                if (ClientGenerationEdge.INSTANCE.restricted() || !ClientGenerationEdge.INSTANCE.fence().empty()) {
                    yield ctx.tick() - rig.since > 200 ? cleanup(ctx, rig, StepResult.fail("Grenzzaun bleibt nach dem Einschalten der Regel"))
                            : StepResult.pending();
                }
                sim.setHorizontalVelocity(RESUME, 0.0);
                double x = session.comPosition(new Vector3d()).x;
                if (x > rig.edgeX + 20.0) {
                    rig.resumedX = x;
                    rig.pending = ctx.onServer(server -> rig.generatedAfter = server.getPlayerList().getPlayers().getFirst().level()
                            .getChunkSource().getChunkNow(rig.edgeChunkX, rig.chunkZ) != null);
                    rig.phase = Phase.RESUMED;
                    yield StepResult.pending();
                }
                yield ctx.tick() - rig.since > 900 ? cleanup(ctx, rig, StepResult.fail(String.format(Locale.ROOT,
                        "mit Regel an kommt die Drohne nicht über den Rand: x=%.1f Rand=%.0f", x, rig.edgeX))) : StepResult.pending();
            }
            case RESUMED -> {
                if (!rig.ready()) {
                    yield StepResult.pending();
                }
                if (!rig.generatedAfter) {
                    yield cleanup(ctx, rig, StepResult.fail("mit Regel an entsteht der Chunk am Rand nicht"));
                }
                ctx.log(String.format(Locale.ROOT,
                        "Rand bei x=%.0f (Chunk %d), Zaun bei x=%.0f, Drohne höchstens bis x=%.2f (Abstand %.2f, Stopp %.1f), Zaun %d Abschnitte, "
                                + "gebremst in %d Schritten, %d innere Chunks um die Drohne geladen und beim Client, %d Randchunks, %d fehlende nicht erzeugt; "
                                + "Regel an: weiter bis x=%.0f",
                        rig.edgeX, rig.edgeChunkX, rig.fenceX, rig.maxX, rig.minDistance, EdgeFence.STOP_DISTANCE, rig.segments, rig.limited,
                        rig.generatedCount, rig.borderCount, rig.missingCount, rig.resumedX));
                yield cleanup(ctx, rig, StepResult.pass());
            }
            default -> StepResult.pending();
        };
    }

    private static @Nullable String checkClient(Rig rig) {
        PilotSession session = PilotSession.INSTANCE;
        if (!session.isFpv() || session.drone() == null || session.drone().getId() != rig.droneId) {
            return "FPV-Sitzung am Rand verloren (Pilot gelöst?)";
        }
        double stopLine = rig.fenceX - EdgeFence.STOP_DISTANCE;
        if (rig.maxX > stopLine + 1.0) {
            return String.format(Locale.ROOT, "Drohne fliegt über den Grenzzaun: x=%.2f, Stopplinie %.2f", rig.maxX, stopLine);
        }
        if (rig.lastX < stopLine - 4.0) {
            return String.format(Locale.ROOT, "Drohne bleibt zu früh stehen: x=%.2f, Stopplinie %.2f", rig.lastX, stopLine);
        }
        if (!EdgeGuard.INSTANCE.warning() || rig.limited <= 0) {
            return "Grenzzaun nicht aktiv: Warnung=" + EdgeGuard.INSTANCE.warning() + " gebremst=" + rig.limited;
        }
        OsdData data = new OsdData();
        new LiveOsdSource().fill(data);
        data.active = true;
        List<OsdWarnings.Warning> warnings = new ArrayList<>();
        OsdWarnings.collect(data, warnings);
        if (!warnings.contains(OsdWarnings.Warning.WORLD_EDGE)) {
            return "OSD zeigt keinen Weltrand: " + warnings;
        }
        return null;
    }

    private static void inspect(MinecraftServer server, Rig rig) {
        ServerPlayer player = server.getPlayerList().getPlayers().getFirst();
        ServerLevel level = player.level();
        if (!(level.getEntity(rig.droneId) instanceof QuadcopterEntity drone) || !drone.isFlownBy(player.getUUID())) {
            rig.failure = "Server hat den Piloten am Rand gelöst";
            return;
        }
        if (ServerRules.mayGenerateChunks(player)) {
            rig.failure = "Regel aus, aber der Server erlaubt dem Piloten neue Chunks";
            return;
        }
        PilotLink link = RangeManager.link(player.getUUID()).orElse(null);
        if (link == null || link.decision() != AnchorRules.Decision.ANCHOR) {
            rig.failure = "Sichtanker fehlt am Rand: " + (link == null ? "kein Link" : link.decision());
            return;
        }
        rig.coverTickets = link.cover().size();
        ChunkPos center = drone.chunkPosition();
        rig.center = center;
        List<ChunkPos> chunks = new ArrayList<>();
        List<CompletableFuture<Boolean>> scans = new ArrayList<>();
        for (int dx = -CHECK_RADIUS - 1; dx <= CHECK_RADIUS + 1; dx++) {
            for (int dz = -CHECK_RADIUS - 1; dz <= CHECK_RADIUS + 1; dz++) {
                ChunkPos pos = new ChunkPos(center.x() + dx, center.z() + dz);
                chunks.add(pos);
                scans.add(RangeManager.storedAsGenerated(level, pos));
            }
        }
        for (int dx = 0; dx < 4; dx++) {
            ChunkPos pos = new ChunkPos(rig.edgeChunkX + dx, rig.chunkZ);
            chunks.add(pos);
            scans.add(RangeManager.storedAsGenerated(level, pos));
        }
        rig.chunks = chunks;
        rig.scans = scans;
    }

    private static void judge(MinecraftServer server, Rig rig) {
        ServerLevel level = server.getPlayerList().getPlayers().getFirst().level();
        List<ChunkPos> chunks = rig.chunks;
        List<CompletableFuture<Boolean>> scans = rig.scans;
        if (chunks == null || scans == null) {
            rig.failure = "keine Chunk-Prüfung";
            return;
        }
        ChunkPos center = rig.center;
        if (center == null) {
            rig.failure = "keine Mitte für die Chunk-Prüfung";
            return;
        }
        Set<Long> stored = new HashSet<>();
        for (int i = 0; i < chunks.size(); i++) {
            ChunkPos pos = chunks.get(i);
            boolean isLoaded = level.getChunkSource().getChunkNow(pos.x(), pos.z()) != null;
            if (isLoaded || Boolean.TRUE.equals(scans.get(i).getNow(false))) {
                stored.add(pos.pack());
            }
            if (pos.x() >= rig.edgeChunkX && pos.z() == rig.chunkZ && stored.contains(pos.pack())) {
                rig.failure = "Regel aus, aber Chunk " + pos + " hinter dem Rand wurde erzeugt";
                return;
            }
        }
        List<ChunkPos> loaded = new ArrayList<>();
        int interior = 0;
        int border = 0;
        int missing = 0;
        for (int dx = -CHECK_RADIUS; dx <= CHECK_RADIUS; dx++) {
            for (int dz = -CHECK_RADIUS; dz <= CHECK_RADIUS; dz++) {
                ChunkPos pos = new ChunkPos(center.x() + dx, center.z() + dz);
                if (!stored.contains(pos.pack())) {
                    missing++;
                    continue;
                }
                boolean inside = true;
                for (int nx = -1; nx <= 1 && inside; nx++) {
                    for (int nz = -1; nz <= 1 && inside; nz++) {
                        inside = stored.contains(new ChunkPos(pos.x() + nx, pos.z() + nz).pack());
                    }
                }
                if (!inside) {
                    border++;
                    continue;
                }
                if (level.getChunkSource().getChunkNow(pos.x(), pos.z()) == null) {
                    rig.failure = "Loch: erzeugter Chunk " + pos + " neben der Drohne " + center + " ist nicht geladen";
                    return;
                }
                interior++;
                loaded.add(pos);
            }
        }
        rig.generatedCount = interior;
        rig.borderCount = border;
        rig.missingCount = missing;
        rig.loadedChunks = loaded;
    }

    private static @Nullable String checkClientChunks(Rig rig) {
        ClientLevel level = Minecraft.getInstance().level;
        List<ChunkPos> loaded = rig.loadedChunks;
        if (level == null || loaded == null) {
            return "keine Welt beim Client";
        }
        for (ChunkPos pos : loaded) {
            if (!level.getChunkSource().hasChunk(pos.x(), pos.z())) {
                return "Loch im FPV-Bild: erzeugter Chunk " + pos + " fehlt beim Client";
            }
        }
        if (rig.missingCount == 0) {
            return "kein fehlender Chunk in der Nähe – der Test beweist nichts";
        }
        return null;
    }

    private static StepResult cleanup(SelfTest.StepContext ctx, Rig rig, StepResult result) {
        rig.cleanup(ctx);
        return result;
    }

    private enum Phase {
        START,
        SCANNING,
        SPAWNING,
        STARTING,
        MOVING,
        ARRIVING,
        ARMING,
        PUSHING,
        PUSHED,
        INSPECTING,
        JUDGED,
        RESUMING,
        RESUMED
    }

    private static final class Rig {
        volatile Phase phase = Phase.START;
        volatile @Nullable String failure;
        volatile @Nullable CompletableFuture<Void> pending;
        volatile @Nullable List<CompletableFuture<Boolean>> scans;
        volatile @Nullable List<ChunkPos> chunks;
        volatile @Nullable List<ChunkPos> loadedChunks;
        volatile @Nullable ChunkPos center;
        volatile int playerChunkX;
        volatile int chunkZ;
        volatile int edgeChunkX;
        volatile double edgeX;
        volatile double fenceX;
        volatile double targetX;
        volatile double targetY;
        volatile double targetZ;
        volatile int droneId = -1;
        volatile int coverTickets;
        volatile int generatedCount;
        volatile int borderCount;
        volatile int missingCount;
        volatile boolean generatedAfter;
        double yaw = -90.0;
        double height = HEIGHT;
        int since;
        int pushed;
        double maxX = Double.NEGATIVE_INFINITY;
        double lastX;
        double minDistance = Double.POSITIVE_INFINITY;
        double resumedX;
        long limitedAtStart;
        long limited;
        int segments;
        boolean cleaned;

        boolean ready() {
            CompletableFuture<Void> future = this.pending;
            if (future == null) {
                return true;
            }
            if (future.isCompletedExceptionally()) {
                this.failure = "Server-Schritt fehlgeschlagen: " + future.exceptionNow();
                return true;
            }
            return future.isDone();
        }

        StickPositions hover(DroneSim sim) {
            return new StickPositions(0.0F, 0.0F, 0.0F, (float) Math.min(1.0, sim.telemetry().hoverThrottleStick));
        }

        StepResult advance(SelfTest.StepContext ctx, int pushTicks) {
            if (!this.ready()) {
                return StepResult.pending();
            }
            if (this.failure != null) {
                return StepResult.fail(this.failure);
            }
            PilotSession session = PilotSession.INSTANCE;
            switch (this.phase) {
                case START -> {
                    if (ctx.tick() < DronePiloting.REMOTE_DEBOUNCE_TICKS + 2) {
                        return StepResult.pending();
                    }
                    this.pending = ctx.onServer(server -> this.scan(server));
                    this.phase = Phase.SCANNING;
                    this.since = ctx.tick();
                }
                case SCANNING -> {
                    List<CompletableFuture<Boolean>> list = this.scans;
                    if (list == null) {
                        return StepResult.fail("Suche nach dem Rand nicht gestartet");
                    }
                    for (CompletableFuture<Boolean> scan : list) {
                        if (!scan.isDone()) {
                            return ctx.tick() - this.since > 400 ? StepResult.fail("Chunk-Prüfung hängt") : StepResult.pending();
                        }
                    }
                    this.pending = ctx.onServer(server -> this.spawn(server));
                    this.phase = Phase.SPAWNING;
                    this.since = ctx.tick();
                }
                case SPAWNING -> {
                    this.phase = Phase.STARTING;
                    this.since = ctx.tick();
                }
                case STARTING -> {
                    if (!this.ours(session)) {
                        return ctx.tick() - this.since > 200 ? StepResult.fail("FPV-Sitzung startet nicht: " + this.describe(session))
                                : StepResult.pending();
                    }
                    session.setTestInput(new StickPositions(0.0F, 0.0F, 0.0F, 0.0F), false, StabMode.ANGLE);
                    int id = this.droneId;
                    this.pending = ctx.onServer(server -> {
                        if (server.getPlayerList().getPlayers().getFirst().level().getEntity(id) instanceof QuadcopterEntity drone) {
                            drone.resetTo(new Vec3(this.targetX, this.targetY, this.targetZ), new Quaternionf().rotateY((float) Math.toRadians(this.yaw)));
                        } else {
                            this.failure = "Drohne am Server verschwunden";
                        }
                    });
                    this.phase = Phase.MOVING;
                }
                case MOVING -> {
                    this.phase = Phase.ARRIVING;
                    this.since = ctx.tick();
                }
                case ARRIVING -> {
                    boolean ours = this.ours(session);
                    if (ours) {
                        session.setTestInput(new StickPositions(0.0F, 0.0F, 0.0F, 0.0F), false, StabMode.ANGLE);
                    }
                    Vector3d com = session.comPosition(new Vector3d());
                    boolean arrived = ours && Math.abs(com.x - this.targetX) < 3.0 && Math.abs(com.z - this.targetZ) < 3.0;
                    if (!arrived || !ClientGenerationEdge.INSTANCE.restricted() || ClientGenerationEdge.INSTANCE.fence().empty()) {
                        return ctx.tick() - this.since > 400 ? StepResult.fail("Drohne nicht am Rand: " + this.describe(session) + " Ziel="
                                + this.targetX + " gesperrt=" + ClientGenerationEdge.INSTANCE.restricted() + " Zaun="
                                + ClientGenerationEdge.INSTANCE.fence().segments()) : StepResult.pending();
                    }
                    this.segments = ClientGenerationEdge.INSTANCE.fence().segments();
                    this.phase = Phase.ARMING;
                    this.since = ctx.tick();
                }
                case ARMING -> {
                    DroneSim sim = session.sim();
                    if (sim == null) {
                        return StepResult.fail("Sitzung beim Scharfschalten verloren");
                    }
                    session.setTestInput(new StickPositions(0.0F, 0.0F, 0.0F, 0.0F), true, StabMode.ANGLE);
                    if (!sim.telemetry().armed) {
                        return ctx.tick() - this.since > 80 ? StepResult.fail("nicht scharf: Sperren=" + sim.telemetry().armBlockers)
                                : StepResult.pending();
                    }
                    this.limitedAtStart = EdgeGuard.INSTANCE.limitedSlices();
                    this.phase = Phase.PUSHING;
                    this.since = ctx.tick();
                    this.pushed = 0;
                }
                case PUSHING -> {
                    DroneSim sim = session.sim();
                    if (sim == null || !session.isActive()) {
                        return StepResult.fail("Pilot am Rand gelöst nach " + this.pushed + " Ticks");
                    }
                    this.push(session, sim);
                    if (++this.pushed >= pushTicks) {
                        this.phase = Phase.PUSHED;
                        return StepResult.pass();
                    }
                }
                default -> {
                    return StepResult.pass();
                }
            }
            return StepResult.pending();
        }

        boolean ours(PilotSession session) {
            QuadcopterEntity drone = session.drone();
            return session.isFpv() && drone != null && drone.getId() == this.droneId && session.sim() != null;
        }

        String describe(PilotSession session) {
            QuadcopterEntity drone = session.drone();
            Vector3d com = session.comPosition(new Vector3d());
            return "aktiv=" + session.isActive() + " fpv=" + session.isFpv() + " drohne=" + (drone == null ? "-" : drone.getId()) + " erwartet="
                    + this.droneId + String.format(Locale.ROOT, " x=%.1f z=%.1f", com.x, com.z);
        }

        void push(PilotSession session, DroneSim sim) {
            session.setTestInput(this.hover(sim), true, StabMode.ANGLE);
            Vector3d com = session.comPosition(new Vector3d());
            this.lastX = com.x;
            this.maxX = Math.max(this.maxX, com.x);
            this.minDistance = Math.min(this.minDistance, EdgeGuard.INSTANCE.distance());
            this.limited = EdgeGuard.INSTANCE.limitedSlices() - this.limitedAtStart;
            sim.setHorizontalVelocity(PUSH, 0.0);
        }

        private void scan(MinecraftServer server) {
            ServerPlayer player = server.getPlayerList().getPlayers().getFirst();
            ServerLevel level = player.level();
            WorldRules.set(server, WorldRuleSpecs.DRONES_GENERATE_CHUNKS, 0);
            ChunkPos origin = player.chunkPosition();
            this.playerChunkX = origin.x();
            this.chunkZ = origin.z();
            List<CompletableFuture<Boolean>> list = new ArrayList<>();
            for (int dz = -1; dz <= 1; dz++) {
                for (int dx = 1; dx <= SEARCH; dx++) {
                    list.add(RangeManager.storedAsGenerated(level, new ChunkPos(origin.x() + dx, origin.z() + dz)));
                }
            }
            this.scans = list;
        }

        private void spawn(MinecraftServer server) {
            ServerPlayer player = server.getPlayerList().getPlayers().getFirst();
            ServerLevel level = player.level();
            List<CompletableFuture<Boolean>> list = this.scans;
            if (list == null) {
                this.failure = "keine Suche nach dem Rand";
                return;
            }
            int edge = -1;
            int fence = Integer.MAX_VALUE;
            for (int row = 0; row < 3; row++) {
                for (int dx = 1; dx <= SEARCH; dx++) {
                    boolean loaded = level.getChunkSource().getChunkNow(this.playerChunkX + dx, this.chunkZ + row - 1) != null;
                    if (!loaded && !Boolean.TRUE.equals(list.get(row * SEARCH + dx - 1).getNow(false))) {
                        if (row == 1) {
                            edge = dx;
                        }
                        fence = Math.min(fence, dx - 1);
                        break;
                    }
                }
            }
            if (edge < 0 || fence < MIN_EDGE) {
                this.failure = "kein Rand der erzeugten Welt in Richtung +X gefunden (dx=" + edge + ", Zaun " + fence + ")";
                return;
            }
            this.edgeChunkX = this.playerChunkX + edge;
            this.edgeX = this.edgeChunkX * 16.0;
            this.fenceX = (this.playerChunkX + fence) * 16.0;
            this.targetX = this.fenceX - START_BEFORE;
            this.targetZ = this.chunkZ * 16.0 + 8.0;
            int x = player.getBlockX() + 3;
            int z = player.getBlockZ() + 2;
            int top = level.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z);
            BlockPos ground = new BlockPos(x, top - 1, z);
            ItemStack stack = new ItemStack(DroneModule.DRONE_ITEM.get());
            stack.set(CoreComponents.DRONE_BUILD.get(), DroneBuild.FREESTYLE);
            stack.set(CoreComponents.BATTERY_CHARGE.get(), 1.0F);
            player.setItemInHand(InteractionHand.MAIN_HAND, stack);
            InteractionResult use = stack.useOn(new UseOnContext(player, InteractionHand.MAIN_HAND,
                    new BlockHitResult(new Vec3(x + 0.5, top, z + 0.5), Direction.UP, ground, false)));
            if (!use.consumesAction()) {
                this.failure = "Platzieren lieferte " + use;
                return;
            }
            List<QuadcopterEntity> drones = level.getEntitiesOfClass(QuadcopterEntity.class, new AABB(ground).inflate(3.0),
                    d -> d.build().equals(DroneBuild.FREESTYLE) && d.pilot().isEmpty());
            if (drones.isEmpty()) {
                this.failure = "keine Drohne nach dem Platzieren";
                return;
            }
            QuadcopterEntity drone = drones.getFirst();
            this.droneId = drone.getId();
            ItemStack remote = new ItemStack(DroneModule.REMOTE.get());
            remote.set(CoreComponents.BOUND_DRONE.get(), DroneInteractions.binding(drone));
            player.setItemInHand(InteractionHand.MAIN_HAND, remote);
            ItemStack goggles = ContentIds.item(ContentIds.GOGGLES_CREATIVE).map(ItemStack::new).orElse(ItemStack.EMPTY);
            goggles.set(CoreComponents.BOUND_DRONE.get(), DroneInteractions.binding(drone));
            player.setItemSlot(EquipmentSlot.HEAD, goggles);
            DronePiloting.StartResult result = DronePiloting.tryStart(player, drone, PilotMode.FPV);
            if (result != DronePiloting.StartResult.STARTED) {
                this.failure = "FPV-Start verweigert: " + result;
                return;
            }
            int groundY = level.getHeight(Heightmap.Types.MOTION_BLOCKING, (int) Math.floor(this.targetX), (int) Math.floor(this.targetZ));
            this.targetY = Math.max(groundY, level.getMinY() + 1) + this.height;
        }

        void cleanup(SelfTest.StepContext ctx) {
            if (this.cleaned) {
                return;
            }
            this.cleaned = true;
            PilotSession session = PilotSession.INSTANCE;
            session.clearTestInput();
            if (session.isActive()) {
                session.requestStop();
            }
            int id = this.droneId;
            ctx.onServer(server -> {
                WorldRules.set(server, WorldRuleSpecs.DRONES_GENERATE_CHUNKS, 1);
                ServerPlayer player = server.getPlayerList().getPlayers().getFirst();
                if (id >= 0 && player.level().getEntity(id) instanceof QuadcopterEntity drone) {
                    if (drone.pilot().isPresent()) {
                        DronePiloting.release(drone);
                    }
                    drone.discard();
                }
                player.setItemSlot(EquipmentSlot.HEAD, ItemStack.EMPTY);
                player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
            });
        }
    }

    private static final class EdgeScene implements Screenshots.Scene {
        private @Nullable Rig rig;

        @Override
        public StepResult prepare(SelfTest.StepContext ctx) {
            Rig current = this.rig;
            if (current == null) {
                current = new Rig();
                current.yaw = ALONG_EDGE_YAW;
                current.height = SCENE_HEIGHT;
                this.rig = current;
            }
            StepResult result = current.advance(ctx, 90);
            if (result.status() == StepResult.Status.PASS && !EdgeGuard.INSTANCE.warning()) {
                return StepResult.fail("Weltrand-Warnung fehlt vor dem Bild");
            }
            return result;
        }

        @Override
        public void hold(SelfTest.StepContext ctx) {
            Rig current = this.rig;
            PilotSession session = PilotSession.INSTANCE;
            DroneSim sim = session.sim();
            if (current != null && sim != null) {
                current.push(session, sim);
            }
        }

        @Override
        public void finish(SelfTest.StepContext ctx) {
            Rig current = this.rig;
            if (current != null) {
                current.cleanup(ctx);
            }
            this.rig = null;
        }
    }
}
