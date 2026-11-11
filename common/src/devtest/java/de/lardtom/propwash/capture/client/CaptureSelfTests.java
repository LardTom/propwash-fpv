package de.lardtom.propwash.capture.client;

import de.lardtom.propwash.api.PropwashApi;
import de.lardtom.propwash.api.client.CameraChunkRequests;
import de.lardtom.propwash.api.client.PropwashClientApi;
import de.lardtom.propwash.api.drone.CameraChunkResult;
import de.lardtom.propwash.api.drone.CameraChunkStatus;
import de.lardtom.propwash.api.drone.CameraChunkSupply;
import de.lardtom.propwash.capture.CameraArea;
import de.lardtom.propwash.capture.CameraTrackingView;
import de.lardtom.propwash.capture.CaptureRules;
import de.lardtom.propwash.capture.CaptureServer;
import de.lardtom.propwash.dev.CaptureJoinCheck;
import de.lardtom.propwash.dev.CaptureJoinCheck.Far;
import de.lardtom.propwash.compat.flashback.client.FlashbackBridge;
import de.lardtom.propwash.compat.flashback.client.FlashbackProbe;
import de.lardtom.propwash.dev.SelfTest;
import de.lardtom.propwash.video.client.record.FlashbackCamSelfTests;
import de.lardtom.propwash.dev.SelfTest.StepResult;
import de.lardtom.propwash.drone.QuadcopterEntity;
import de.lardtom.propwash.server.ServerModule;
import de.lardtom.propwash.server.ServerRules;
import de.lardtom.propwash.server.ServerRulesConfig;
import de.lardtom.propwash.server.WorldRules;
import de.lardtom.propwash.server.rules.WorldRuleSpecs;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ChunkTrackingView;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

public final class CaptureSelfTests {
    public static final String GROUP = "capture: ";
    public static final String START = GROUP + "feste Startlage – Spieler 20 Chunks östlich und 15 nördlich vom Spawn wie im vollen Lauf, nach dem Aufräumen zurück";
    public static final String OPEN = GROUP + "Lage 300 Blöcke weit weg anmelden – Client hat Chunks, Kamera-Drohne und Marker dort, eigene Ansicht und Hauptbild unverändert";
    public static final String FOLLOW = GROUP + "Lage folgt der Drohne – 6 Chunks weiter neue Chunks da, alte wieder frei";
    public static final String RANGE = GROUP + "Funkreichweite – ohne unbegrenzte Reichweite pausiert die Lage weit weg, danach geht sie weiter";
    public static final String CLOSE = GROUP + "Abmelden – Chunks und Entities dort wieder frei, Speicher des Clients wieder normal";
    public static final String LIMITS = GROUP + "Grenzen – fremde Drohne, keine Drohne, dritte Lage, Radius gekappt, Welt-Regel aus beendet, Drohne weg";
    public static final String ENDS = GROUP + "Ende bei Weltwechsel und Tod – Lage endet, Client im neuen Level ohne Zusatzspeicher";
    public static final String CLEANUP = GROUP + "Aufräumen";
    public static final String FB_RECORD = GROUP + "Flashback nimmt die Lage auf – laufende Aufnahme, Lage 300 Blöcke weit weg kommt dazu";
    public static final String FB_REPLAY = GROUP + "Flashback-Wiederholung hat Gelände, Drohne und Marker der Lage, auf der Gegenseite nichts";
    public static final int RADIUS = 3;
    public static final int FOLLOW_CHUNKS = 6;
    private static final BlockPos NETHER_SPOT = new BlockPos(8, 64, 8);
    private static final BlockPos START_SPOT = new BlockPos(325, 0, -239);
    private static final BlockPos RECORD_SITE = new BlockPos(-3000, 0, 3000);
    private static final Map<String, Object> SERVER = new ConcurrentHashMap<>();
    private static final List<UUID> SPAWNED = new ArrayList<>();
    private static @Nullable UUID recorded;
    private static @Nullable Far recordedFar;
    private static int recordedTicks;
    private static int recordedPlayerChunkX;
    private static @Nullable Origin origin;

    private CaptureSelfTests() {
    }

    private record Origin(ResourceKey<Level> dimension, Vec3 position, float yRot, float xRot) {
    }

    public static void register() {
        SelfTest.clientStep(START, 1200, CaptureSelfTests::startStep);
        SelfTest.clientStep(OPEN, 1600, CaptureSelfTests::openStep);
        SelfTest.clientStep(FOLLOW, 1000, CaptureSelfTests::followStep);
        SelfTest.clientStep(RANGE, 1200, CaptureSelfTests::rangeStep);
        SelfTest.clientStep(CLOSE, 800, CaptureSelfTests::closeStep);
        SelfTest.clientStep(LIMITS, 600, CaptureSelfTests::limitsStep);
        SelfTest.clientStep(ENDS, 2400, CaptureSelfTests::endsStep);
        if (FlashbackCamSelfTests.flashbackPresent()) {
            SelfTest.clientStep(FB_RECORD, 2400, CaptureSelfTests::recordStep);
            SelfTest.afterLeaveStep(FB_REPLAY, 2400, CaptureSelfTests::replayStep);
        }
        SelfTest.clientStep(CLEANUP, 600, CaptureSelfTests::cleanupStep);
    }

    private static int phase(SelfTest.StepContext ctx) {
        return (int) ctx.state().getOrDefault("phase", 0);
    }

    private static void next(SelfTest.StepContext ctx) {
        ctx.state().put("phase", phase(ctx) + 1);
        ctx.state().put("since", ctx.tick());
    }

    private static int since(SelfTest.StepContext ctx) {
        return ctx.tick() - (int) ctx.state().getOrDefault("since", 0);
    }

    private static @Nullable StepResult server(SelfTest.StepContext ctx, String key, Consumer<MinecraftServer> action) {
        CompletableFuture<?> future = (CompletableFuture<?>) ctx.state().get(key);
        if (future == null) {
            ctx.state().put(key, ctx.onServer(action));
            return StepResult.pending();
        }
        if (!future.isDone()) {
            return StepResult.pending();
        }
        if (future.isCompletedExceptionally()) {
            return StepResult.fail(key + ": " + future.exceptionNow());
        }
        return null;
    }

    public static ServerPlayer player(MinecraftServer server) {
        return server.getPlayerList().getPlayers().getFirst();
    }

    private static Far far() {
        return (Far) SERVER.get("far");
    }

    public static int missing(ClientLevel level, CameraArea area) {
        int[] missing = new int[1];
        area.forEach(pos -> {
            if (!level.getChunkSource().hasChunk(pos.x(), pos.z())) {
                missing[0]++;
            }
        });
        return missing[0];
    }

    public static int present(ClientLevel level, CameraArea area) {
        return area.chunks() - missing(level, area);
    }

    public static int storageSide(ClientLevel level) {
        String stats = level.getChunkSource().gatherStats();
        int length = Integer.parseInt(stats.substring(0, stats.indexOf(',')).trim());
        return (int) Math.round(Math.sqrt(length));
    }

    public static int vanillaSide(ClientLevel level) {
        int range = level.getChunkSource() instanceof CaptureStorage storage ? storage.propwash$vanillaRange() : 2;
        return (Math.max(2, range) + 3) * 2 + 1;
    }

    private static Vec3 moveTo(ServerPlayer player, ServerLevel level, int x, int z) {
        level.getChunk(x >> 4, z >> 4);
        Vec3 at = new Vec3(x + 0.5, level.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z), z + 0.5);
        player.teleportTo(level, at.x, at.y, at.z, Set.of(), player.getYRot(), player.getXRot(), true);
        return at;
    }

    private static boolean arrived(Minecraft mc, ResourceKey<Level> dimension, Vec3 at) {
        ClientLevel level = mc.level;
        if (level == null || mc.player == null || level.dimension() != dimension
                || Math.hypot(mc.player.getX() - at.x, mc.player.getZ() - at.z) > 1.0) {
            return false;
        }
        return missing(level, CameraArea.of(mc.player.chunkPosition(), 2)) == 0;
    }

    private static StepResult startStep(SelfTest.StepContext ctx) {
        Minecraft mc = Minecraft.getInstance();
        if (phase(ctx) == 0) {
            StepResult wait = server(ctx, "start", server -> {
                ServerPlayer player = player(server);
                origin = new Origin(player.level().dimension(), player.position(), player.getYRot(), player.getXRot());
                SERVER.put("start", moveTo(player, server.overworld(), START_SPOT.getX(), START_SPOT.getZ()));
            });
            if (wait != null) {
                return wait;
            }
            next(ctx);
            return StepResult.pending();
        }
        Vec3 start = (Vec3) SERVER.get("start");
        if (!arrived(mc, Level.OVERWORLD, start)) {
            return since(ctx) > 600 ? StepResult.fail("Spieler kommt an der Startlage " + start + " nicht an") : StepResult.pending();
        }
        Origin from = origin;
        ctx.log(String.format(Locale.ROOT, "Startlage %.1f %.1f %.1f (Chunk %d, %d) nach %d Ticks, vorher %s", start.x, start.y, start.z,
                mc.player.chunkPosition().x(), mc.player.chunkPosition().z(), since(ctx), from == null ? "-" : from.position()));
        return StepResult.pass();
    }

    private static StepResult openStep(SelfTest.StepContext ctx) {
        Minecraft mc = Minecraft.getInstance();
        CameraChunkRequests requests = PropwashClientApi.cameraChunks();
        switch (phase(ctx)) {
            case 0 -> {
                StepResult wait = server(ctx, "spawn", server -> {
                    SERVER.clear();
                    ServerPlayer player = player(server);
                    WorldRules.set(server, WorldRuleSpecs.CAMERA_CHUNKS, WorldRuleSpecs.CAMERA_CHUNKS.defaultValue());
                    SERVER.put("far", CaptureJoinCheck.spawnFar(player, RADIUS, SPAWNED));
                });
                if (wait != null) {
                    return wait;
                }
                if (!requests.supported()) {
                    return StepResult.fail("Server nimmt keine Anfragen an");
                }
                if (!requests.request(far().droneId(), RADIUS)) {
                    return StepResult.fail("Anfrage nicht gesendet");
                }
                next(ctx);
                return StepResult.pending();
            }
            case 1 -> {
                Far far = far();
                Optional<CameraChunkResult.Status> answer = requests.answer(far.droneId());
                if (answer.isEmpty()) {
                    return since(ctx) > 100 ? StepResult.fail("keine Antwort des Servers") : StepResult.pending();
                }
                if (answer.get() != CameraChunkResult.Status.OPENED || requests.radius(far.droneId()) != RADIUS) {
                    return StepResult.fail("Antwort " + answer.get() + ", Radius " + requests.radius(far.droneId()));
                }
                ctx.state().put("asked", ctx.tick());
                next(ctx);
                return StepResult.pending();
            }
            case 2 -> {
                Far far = far();
                ClientLevel level = mc.level;
                if (level == null || mc.player == null) {
                    return StepResult.pending();
                }
                int missing = missing(level, far.area());
                Entity drone = level.getEntity(far.droneId());
                Entity marker = level.getEntity(far.markerId());
                if (missing > 0 || drone == null || marker == null) {
                    if (since(ctx) > 1200) {
                        return StepResult.fail("nach 60 s fehlen " + missing + " von " + far.area().chunks() + " Chunks, Drohne "
                                + (drone != null) + ", Marker " + (marker != null));
                    }
                    return StepResult.pending();
                }
                ctx.state().put("loaded", since(ctx));
                List<String> problems = new ArrayList<>();
                if (mc.player.position().distanceTo(far.playerAt()) > 1.0) {
                    problems.add("Spieler bewegt: " + mc.player.position());
                }
                if (mc.getCameraEntity() != mc.player) {
                    problems.add("Kamera nicht beim Spieler");
                }
                if (!level.getChunkSource().hasChunk(mc.player.chunkPosition().x(), mc.player.chunkPosition().z())) {
                    problems.add("eigener Chunk fehlt");
                }
                int render = mc.options.getEffectiveRenderDistance();
                int gap = far.area().distanceFrom(mc.player.chunkPosition().x(), mc.player.chunkPosition().z()) - RADIUS - 1;
                if (gap <= render) {
                    problems.add("Lage in Sichtweite: Abstand " + gap + " Chunks, Sichtweite " + render);
                }
                BlockPos dronePos = drone.blockPosition();
                if (mc.levelRenderer.isSectionCompiledAndVisible(dronePos, 0L) || mc.levelRenderer.isSectionCompiledAndVisible(marker.blockPosition(), 0L)) {
                    problems.add("Abschnitt der Drohne wird gezeichnet");
                }
                if (marker.getCustomName() == null || !CaptureJoinCheck.MARKER.equals(marker.getCustomName().getString())) {
                    problems.add("Marker ohne Namen");
                }
                int side = storageSide(level);
                int needed = 2 * (far.area().extentFrom(mc.player.chunkPosition().x(), mc.player.chunkPosition().z())) + 1;
                if (side < needed || !(level.getChunkSource() instanceof CaptureStorage storage) || storage.propwash$cameraStorage() <= 0) {
                    problems.add("Speicher " + side + " für " + needed);
                }
                if (!problems.isEmpty()) {
                    return StepResult.fail(String.join(", ", problems));
                }
                ctx.state().put("side", side);
                next(ctx);
                return StepResult.pending();
            }
            default -> {
                StepResult wait = server(ctx, "check", server -> {
                    ServerPlayer player = player(server);
                    Far far = far();
                    ChunkTrackingView view = player.getChunkTrackingView();
                    SERVER.put("view", view instanceof CameraTrackingView camera && camera.base().center().equals(player.chunkPosition())
                            && camera.areas().contains(far.area()));
                    Entity drone = player.level().getEntity(far.drone());
                    Optional<CameraChunkStatus> status = drone == null ? Optional.empty() : PropwashApi.drones().cameraChunks().status(player, drone);
                    SERVER.put("status", status.map(Object::toString).orElse("-"));
                    SERVER.put("complete", status.map(CameraChunkStatus::complete).orElse(false));
                    SERVER.put("tickets", CaptureServer.tickets());
                });
                if (wait != null) {
                    return wait;
                }
                Far far = far();
                if (!(boolean) SERVER.get("view")) {
                    return StepResult.fail("Server-Ansicht nicht Spieler plus Lage");
                }
                if (!(boolean) SERVER.get("complete")) {
                    return StepResult.fail("Status unvollständig: " + SERVER.get("status"));
                }
                if ((int) SERVER.get("tickets") < 1) {
                    return StepResult.fail("keine Tickets");
                }
                ClientLevel level = mc.level;
                ctx.log(String.format(Locale.ROOT, "Drohne %d Blöcke entfernt, %d Chunks Radius %d in %d Ticks beim Client, Drohne und Marker da, "
                                + "Speicher %d statt %d, Sichtweite %d, Status %s", far.blocks(), far.area().chunks(), RADIUS,
                        (int) ctx.state().get("loaded"), (int) ctx.state().get("side"), level == null ? 0 : vanillaSide(level),
                        mc.options.getEffectiveRenderDistance(), SERVER.get("status")));
                return StepResult.pass();
            }
        }
    }

    private static StepResult followStep(SelfTest.StepContext ctx) {
        Minecraft mc = Minecraft.getInstance();
        Far far = far();
        if (far == null) {
            return StepResult.fail("keine Lage aus dem ersten Schritt");
        }
        switch (phase(ctx)) {
            case 0 -> {
                StepResult wait = server(ctx, "move", server -> {
                    ServerLevel level = player(server).level();
                    Entity drone = level.getEntity(far.drone());
                    Entity marker = level.getEntity(far.marker());
                    if (drone == null || marker == null) {
                        throw new IllegalStateException("Drohne oder Marker nicht geladen");
                    }
                    ChunkPos target = new ChunkPos(far.area().centerX() + FOLLOW_CHUNKS, far.area().centerZ());
                    level.getChunk(target.x(), target.z());
                    level.getChunkSource().addTicketWithRadius(ServerModule.DRONE_VIEW_TICKET.get(), target, 2);
                    int x = target.getMiddleBlockX();
                    int z = target.getMiddleBlockZ();
                    drone.teleportTo(x + 0.5, level.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z) + 0.05, z + 0.5);
                    marker.teleportTo(x + 3.5, level.getHeight(Heightmap.Types.MOTION_BLOCKING, x + 3, z + 1), z + 1.5);
                });
                if (wait != null) {
                    return wait;
                }
                next(ctx);
                return StepResult.pending();
            }
            default -> {
                ClientLevel level = mc.level;
                if (level == null) {
                    return StepResult.pending();
                }
                CameraArea moved = new CameraArea(far.area().centerX() + FOLLOW_CHUNKS, far.area().centerZ(), RADIUS);
                int missing = missing(level, moved);
                int[] stale = new int[1];
                far.area().forEach(pos -> {
                    if (!moved.contains(pos.x(), pos.z(), true) && level.getChunkSource().hasChunk(pos.x(), pos.z())) {
                        stale[0]++;
                    }
                });
                Entity drone = level.getEntity(far.droneId());
                boolean arrived = drone != null && drone.chunkPosition().equals(moved.center());
                if (missing > 0 || stale[0] > 0 || !arrived) {
                    if (since(ctx) > 900) {
                        return StepResult.fail("nach 45 s fehlen " + missing + " neue Chunks, " + stale[0] + " alte noch da, Drohne angekommen " + arrived);
                    }
                    return StepResult.pending();
                }
                SERVER.put("far", new Far(far.droneId(), far.drone(), far.markerId(), far.marker(), moved, far.blocks() + FOLLOW_CHUNKS * 16, far.playerAt(),
                        far.entity()));
                ctx.log("Lage folgt nach " + since(ctx) + " Ticks: " + moved.chunks() + " Chunks bei " + moved.center() + ", alte frei");
                return StepResult.pass();
            }
        }
    }

    private static StepResult rangeStep(SelfTest.StepContext ctx) {
        Minecraft mc = Minecraft.getInstance();
        Far far = far();
        if (far == null) {
            return StepResult.fail("keine Lage aus dem ersten Schritt");
        }
        ClientLevel level = mc.level;
        if (level == null) {
            return StepResult.pending();
        }
        switch (phase(ctx)) {
            case 0 -> {
                StepResult wait = server(ctx, "limited", server -> {
                    ServerRulesConfig config = ServerRulesConfig.get();
                    SERVER.put("unlimited", config.allowUnlimitedRange);
                    config.allowUnlimitedRange = false;
                    SERVER.put("range", ServerRules.maxPilotDistance(player(server)));
                });
                if (wait != null) {
                    return wait;
                }
                next(ctx);
                return StepResult.pending();
            }
            case 1 -> {
                int present = present(level, far.area());
                boolean registered = CaptureClient.INSTANCE.registered(far.droneId());
                int radius = PropwashClientApi.cameraChunks().radius(far.droneId());
                if (present > 0 || !registered || radius != 0) {
                    return since(ctx) > 400 ? restore(ctx, StepResult.fail("ohne Reichweite noch " + present + " Chunks, angemeldet " + registered
                            + ", Radius " + radius)) : StepResult.pending();
                }
                ctx.state().put("paused", since(ctx));
                next(ctx);
                return StepResult.pending();
            }
            case 2 -> {
                StepResult wait = server(ctx, "unlimitedAgain", server -> ServerRulesConfig.get().allowUnlimitedRange = (boolean) SERVER.get("unlimited"));
                if (wait != null) {
                    return wait;
                }
                next(ctx);
                return StepResult.pending();
            }
            default -> {
                int missing = missing(level, far.area());
                if (missing > 0 || PropwashClientApi.cameraChunks().radius(far.droneId()) != RADIUS) {
                    return since(ctx) > 600 ? StepResult.fail("Lage geht nicht weiter, " + missing + " Chunks fehlen") : StepResult.pending();
                }
                ctx.log(String.format(Locale.ROOT, "Reichweite %.0f Blöcke: Lage %d Blöcke entfernt nach %d Ticks pausiert und frei, mit "
                        + "unbegrenzter Reichweite nach %d Ticks wieder da", (double) SERVER.get("range"), far.blocks(), (int) ctx.state().get("paused"), since(ctx)));
                return StepResult.pass();
            }
        }
    }

    private static StepResult restore(SelfTest.StepContext ctx, StepResult result) {
        if (SERVER.get("unlimited") instanceof Boolean unlimited) {
            ctx.onServer(server -> ServerRulesConfig.get().allowUnlimitedRange = unlimited);
        }
        return result;
    }

    private static StepResult closeStep(SelfTest.StepContext ctx) {
        Minecraft mc = Minecraft.getInstance();
        Far far = far();
        CameraChunkRequests requests = PropwashClientApi.cameraChunks();
        if (far == null) {
            return StepResult.fail("keine Lage aus dem ersten Schritt");
        }
        switch (phase(ctx)) {
            case 0 -> {
                if (!requests.release(far.droneId())) {
                    return StepResult.fail("Abmeldung nicht gesendet");
                }
                next(ctx);
                return StepResult.pending();
            }
            case 1 -> {
                ClientLevel level = mc.level;
                if (level == null) {
                    return StepResult.pending();
                }
                int present = present(level, far.area());
                boolean drone = level.getEntity(far.droneId()) != null;
                boolean marker = level.getEntity(far.markerId()) != null;
                int side = storageSide(level);
                int camera = level.getChunkSource() instanceof CaptureStorage storage ? storage.propwash$cameraStorage() : -1;
                boolean registered = CaptureClient.INSTANCE.registered(far.droneId());
                if (present > 0 || drone || marker || side != vanillaSide(level) || camera != 0 || registered) {
                    if (since(ctx) > 600) {
                        return StepResult.fail("nach 30 s noch " + present + " Chunks, Drohne " + drone + ", Marker " + marker + ", Speicher " + side
                                + " statt " + vanillaSide(level) + ", Zusatz " + camera + ", angemeldet " + registered);
                    }
                    return StepResult.pending();
                }
                ctx.state().put("freed", since(ctx));
                next(ctx);
                return StepResult.pending();
            }
            default -> {
                StepResult wait = server(ctx, "after", server -> {
                    ServerPlayer player = player(server);
                    SERVER.put("positioned", player.getChunkTrackingView() instanceof ChunkTrackingView.Positioned);
                    SERVER.put("supplies", CaptureServer.supplies());
                    SERVER.put("ticketsAfter", CaptureServer.tickets());
                    SERVER.put("ended", CaptureServer.ends(CaptureRules.End.CLOSED));
                    discard(player.level(), far.drone());
                    discard(player.level(), far.marker());
                });
                if (wait != null) {
                    return wait;
                }
                if (!(boolean) SERVER.get("positioned") || (int) SERVER.get("supplies") != 0 || (int) SERVER.get("ticketsAfter") != 0) {
                    return StepResult.fail("Server nach dem Abmelden: Ansicht vanilla " + SERVER.get("positioned") + ", Lagen " + SERVER.get("supplies")
                            + ", Tickets " + SERVER.get("ticketsAfter"));
                }
                ctx.log("nach " + ctx.state().get("freed") + " Ticks frei: keine Chunks, Drohne und Marker beim Client weg, Speicher "
                        + (mc.level == null ? 0 : storageSide(mc.level)) + " wie vanilla, Server ohne Lage und Tickets");
                return StepResult.pass();
            }
        }
    }

    private static void discard(ServerLevel level, UUID id) {
        Entity entity = level.getEntity(id);
        if (entity != null) {
            entity.discard();
            SPAWNED.remove(id);
        }
    }

    private static StepResult limitsStep(SelfTest.StepContext ctx) {
        switch (phase(ctx)) {
            case 0 -> {
                StepResult wait = server(ctx, "limits", server -> {
                    ServerPlayer player = player(server);
                    ServerLevel level = player.level();
                    CameraChunkSupply api = PropwashApi.drones().cameraChunks();
                    List<String> out = new ArrayList<>();
                    int x = player.getBlockX();
                    int z = player.getBlockZ() + 6;
                    QuadcopterEntity one = CaptureJoinCheck.spawnDrone(level, player, x + 2, z, true);
                    QuadcopterEntity two = CaptureJoinCheck.spawnDrone(level, player, x + 4, z, true);
                    QuadcopterEntity three = CaptureJoinCheck.spawnDrone(level, player, x + 6, z, true);
                    QuadcopterEntity foreign = CaptureJoinCheck.spawnDrone(level, player, x + 8, z, false);
                    ArmorStand stand = EntityTypes.ARMOR_STAND.create(level, EntitySpawnReason.COMMAND);
                    stand.snapTo(x + 10.5, player.getY(), z + 0.5, 0.0F, 0.0F);
                    level.addFreshEntity(stand);
                    for (Entity e : List.of(one, two, three, foreign, stand)) {
                        SPAWNED.add(e.getUUID());
                    }
                    WorldRules.set(server, WorldRuleSpecs.CAMERA_CHUNKS, 4);
                    out.add("foreign=" + api.open(player, foreign, 3).status());
                    out.add("stand=" + api.open(player, stand, 3).status());
                    CameraChunkResult first = api.open(player, one, 20);
                    out.add("one=" + first.status() + "/" + first.radius());
                    out.add("two=" + api.open(player, two, 2).status());
                    out.add("three=" + api.open(player, three, 2).status());
                    CameraChunkResult smaller = api.open(player, one, 2);
                    out.add("again=" + smaller.status() + "/" + smaller.radius());
                    out.add("active=" + api.active(player).size());
                    WorldRules.set(server, WorldRuleSpecs.CAMERA_CHUNKS, 1);
                    CameraChunkResult capped = api.open(player, one, 5);
                    out.add("capped=" + capped.status() + "/" + capped.radius());
                    SERVER.put("limits", String.join(" ", out));
                    SERVER.put("ruleOff", CaptureServer.ends(CaptureRules.End.RULE_OFF));
                    SERVER.put("gone", CaptureServer.ends(CaptureRules.End.DRONE_GONE));
                    SERVER.put("one", one.getUUID());
                    SERVER.put("two", two.getUUID());
                    WorldRules.set(server, WorldRuleSpecs.CAMERA_CHUNKS, 0);
                });
                if (wait != null) {
                    return wait;
                }
                String expected = "foreign=NOT_OWN_DRONE stand=NOT_A_DRONE one=OPENED/4 two=OPENED three=TOO_MANY again=UPDATED/2 active=2 capped=UPDATED/1";
                if (!expected.equals(SERVER.get("limits"))) {
                    return StepResult.fail("erwartet " + expected + ", war " + SERVER.get("limits"));
                }
                next(ctx);
                return StepResult.pending();
            }
            case 1 -> {
                if (since(ctx) < 5) {
                    return StepResult.pending();
                }
                StepResult wait = server(ctx, "ruleoff", server -> {
                    ServerPlayer player = player(server);
                    CameraChunkSupply api = PropwashApi.drones().cameraChunks();
                    SERVER.put("ruleOffAfter", CaptureServer.ends(CaptureRules.End.RULE_OFF));
                    SERVER.put("activeOff", api.active(player).size());
                    Entity one = player.level().getEntity((UUID) SERVER.get("one"));
                    Entity two = player.level().getEntity((UUID) SERVER.get("two"));
                    SERVER.put("offOpen", String.valueOf(api.open(player, one, 2).status()));
                    WorldRules.set(server, WorldRuleSpecs.CAMERA_CHUNKS, WorldRuleSpecs.CAMERA_CHUNKS.defaultValue());
                    SERVER.put("twoOpen", String.valueOf(api.open(player, two, 2).status()));
                    two.discard();
                });
                if (wait != null) {
                    return wait;
                }
                int ended = (int) SERVER.get("ruleOffAfter") - (int) SERVER.get("ruleOff");
                if (ended != 2 || (int) SERVER.get("activeOff") != 0 || !"RULE_OFF".equals(SERVER.get("offOpen")) || !"OPENED".equals(SERVER.get("twoOpen"))) {
                    return StepResult.fail("Welt-Regel aus: " + ended + " beendet, " + SERVER.get("activeOff") + " aktiv, neu " + SERVER.get("offOpen")
                            + ", danach " + SERVER.get("twoOpen"));
                }
                next(ctx);
                return StepResult.pending();
            }
            case 2 -> {
                if (since(ctx) < 5) {
                    return StepResult.pending();
                }
                StepResult wait = server(ctx, "gone", server -> {
                    SERVER.put("goneAfter", CaptureServer.ends(CaptureRules.End.DRONE_GONE));
                    SERVER.put("suppliesGone", CaptureServer.supplies());
                });
                if (wait != null) {
                    return wait;
                }
                int gone = (int) SERVER.get("goneAfter") - (int) SERVER.get("gone");
                if (gone != 1 || (int) SERVER.get("suppliesGone") != 0) {
                    return StepResult.fail("Drohne weg: " + gone + " beendet, " + SERVER.get("suppliesGone") + " Lagen übrig");
                }
                ctx.log(SERVER.get("limits") + "; Welt-Regel 0 beendet 2 Lagen und lehnt neue ab; entfernte Drohne beendet ihre Lage");
                return StepResult.pass();
            }
            default -> {
                return StepResult.fail("Phase " + phase(ctx));
            }
        }
    }

    private static StepResult endsStep(SelfTest.StepContext ctx) {
        Minecraft mc = Minecraft.getInstance();
        switch (phase(ctx)) {
            case 0 -> {
                StepResult wait = server(ctx, "far2", server -> {
                    ServerPlayer player = player(server);
                    SERVER.put("home", player.position());
                    Far far = CaptureJoinCheck.spawnFar(player, 2, SPAWNED, -16);
                    SERVER.put("far2", far);
                    SERVER.put("far2Open", String.valueOf(PropwashApi.drones().cameraChunks().open(player, far.entity(), 2).status()));
                    SERVER.put("dimension", CaptureServer.ends(CaptureRules.End.DIMENSION));
                });
                if (wait != null) {
                    return wait;
                }
                if (!"OPENED".equals(SERVER.get("far2Open"))) {
                    return StepResult.fail("Anmeldung " + SERVER.get("far2Open"));
                }
                next(ctx);
                return StepResult.pending();
            }
            case 1 -> {
                Far far = (Far) SERVER.get("far2");
                ClientLevel level = mc.level;
                if (level == null || missing(level, far.area()) > 0 || !(level.getChunkSource() instanceof CaptureStorage storage)
                        || storage.propwash$cameraStorage() <= 0) {
                    return since(ctx) > 1000 ? StepResult.fail("zweite Lage kommt nicht an") : StepResult.pending();
                }
                next(ctx);
                return StepResult.pending();
            }
            case 2 -> {
                StepResult wait = server(ctx, "nether", server -> {
                    ServerPlayer player = player(server);
                    ServerLevel nether = server.getLevel(Level.NETHER);
                    if (nether == null) {
                        throw new IllegalStateException("kein Nether");
                    }
                    nether.getChunk(NETHER_SPOT.getX() >> 4, NETHER_SPOT.getZ() >> 4);
                    for (int dx = -1; dx <= 1; dx++) {
                        for (int dz = -1; dz <= 1; dz++) {
                            nether.setBlock(NETHER_SPOT.offset(dx, -1, dz), Blocks.NETHERRACK.defaultBlockState(), Block.UPDATE_ALL);
                            for (int h = 0; h < 3; h++) {
                                nether.setBlock(NETHER_SPOT.offset(dx, h, dz), Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
                            }
                        }
                    }
                    command(server, "execute in minecraft:the_nether run tp " + player.getPlainTextName() + " " + NETHER_SPOT.getX() + ".5 "
                            + NETHER_SPOT.getY() + " " + NETHER_SPOT.getZ() + ".5");
                });
                if (wait != null) {
                    return wait;
                }
                next(ctx);
                return StepResult.pending();
            }
            case 3 -> {
                if (mc.level == null || mc.level.dimension() != Level.NETHER || mc.player == null || since(ctx) < 40) {
                    return since(ctx) > 400 ? StepResult.fail("Spieler kam nicht im Nether an") : StepResult.pending();
                }
                StepResult wait = server(ctx, "netherCheck", server -> {
                    SERVER.put("dimensionAfter", CaptureServer.ends(CaptureRules.End.DIMENSION));
                    SERVER.put("suppliesNether", CaptureServer.supplies());
                    SERVER.put("sessionsNether", CaptureServer.sessions());
                });
                if (wait != null) {
                    return wait;
                }
                int ended = (int) SERVER.get("dimensionAfter") - (int) SERVER.get("dimension");
                int camera = mc.level.getChunkSource() instanceof CaptureStorage storage ? storage.propwash$cameraStorage() : -1;
                if (ended != 1 || (int) SERVER.get("suppliesNether") != 0 || camera != 0 || storageSide(mc.level) != vanillaSide(mc.level)) {
                    return StepResult.fail("Weltwechsel: " + ended + " beendet, " + SERVER.get("suppliesNether") + " Lagen, Zusatzspeicher " + camera
                            + ", Speicher " + storageSide(mc.level) + " statt " + vanillaSide(mc.level));
                }
                ctx.state().put("netherOk", "Weltwechsel beendet die Lage, Nether-Level mit Speicher " + storageSide(mc.level));
                next(ctx);
                return StepResult.pending();
            }
            case 4 -> {
                StepResult wait = server(ctx, "back", server -> {
                    ServerPlayer player = player(server);
                    Vec3 home = (Vec3) SERVER.get("home");
                    command(server, String.format(Locale.ROOT, "execute in minecraft:overworld run tp %s %.3f %.3f %.3f", player.getPlainTextName(),
                            home.x, home.y, home.z));
                });
                if (wait != null) {
                    return wait;
                }
                next(ctx);
                return StepResult.pending();
            }
            case 5 -> {
                if (mc.level == null || mc.level.dimension() != Level.OVERWORLD || mc.player == null || since(ctx) < 40) {
                    return since(ctx) > 400 ? StepResult.fail("Spieler kam nicht zurück") : StepResult.pending();
                }
                StepResult wait = server(ctx, "die", server -> {
                    ServerPlayer player = player(server);
                    QuadcopterEntity near = CaptureJoinCheck.spawnDrone(player.level(), player, player.getBlockX() + 3, player.getBlockZ() + 3, true);
                    SPAWNED.add(near.getUUID());
                    SERVER.put("nearOpen", String.valueOf(PropwashApi.drones().cameraChunks().open(player, near, 2).status()));
                    SERVER.put("died", CaptureServer.ends(CaptureRules.End.DIED));
                    player.kill(player.level());
                });
                if (wait != null) {
                    return wait;
                }
                if (!"OPENED".equals(SERVER.get("nearOpen"))) {
                    return StepResult.fail("Anmeldung vor dem Tod: " + SERVER.get("nearOpen"));
                }
                next(ctx);
                return StepResult.pending();
            }
            case 6 -> {
                if (mc.player != null && mc.player.isDeadOrDying()) {
                    if (!ctx.state().containsKey("respawn") && since(ctx) > 10) {
                        ctx.state().put("respawn", true);
                        mc.player.respawn();
                    }
                    return since(ctx) > 400 ? StepResult.fail("kein Wiedereinstieg") : StepResult.pending();
                }
                if (mc.player == null || !mc.player.isAlive() || !ctx.state().containsKey("respawn")) {
                    return since(ctx) > 400 ? StepResult.fail("kein Tod gesehen") : StepResult.pending();
                }
                if (mc.gui.screen() != null) {
                    mc.gui.setScreen(null);
                }
                next(ctx);
                return StepResult.pending();
            }
            default -> {
                if (since(ctx) < 10) {
                    return StepResult.pending();
                }
                StepResult wait = server(ctx, "afterDeath", server -> {
                    ServerPlayer player = player(server);
                    SERVER.put("diedAfter", CaptureServer.ends(CaptureRules.End.DIED));
                    SERVER.put("suppliesDead", CaptureServer.supplies());
                    Vec3 home = (Vec3) SERVER.get("home");
                    player.teleportTo(home.x, home.y, home.z);
                    player.setHealth(player.getMaxHealth());
                });
                if (wait != null) {
                    return wait;
                }
                int died = (int) SERVER.get("diedAfter") - (int) SERVER.get("died");
                if (died != 1 || (int) SERVER.get("suppliesDead") != 0) {
                    return StepResult.fail("Tod: " + died + " beendet, " + SERVER.get("suppliesDead") + " Lagen übrig");
                }
                ctx.log(ctx.state().get("netherOk") + "; Tod beendet die Lage");
                return StepResult.pass();
            }
        }
    }

    private static StepResult recordStep(SelfTest.StepContext ctx) {
        Minecraft mc = Minecraft.getInstance();
        try {
            switch (phase(ctx)) {
                case 0 -> {
                    StepResult wait = server(ctx, "fbSite", server -> {
                        ServerPlayer player = player(server);
                        SERVER.put("fbHome", new Origin(player.level().dimension(), player.position(), player.getYRot(), player.getXRot()));
                        SERVER.put("fbSite", moveTo(player, server.overworld(), RECORD_SITE.getX(), RECORD_SITE.getZ()));
                    });
                    if (wait != null) {
                        return wait;
                    }
                    next(ctx);
                    return StepResult.pending();
                }
                case 1 -> {
                    if (!arrived(mc, Level.OVERWORLD, (Vec3) SERVER.get("fbSite"))) {
                        return since(ctx) > 600 ? StepResult.fail("Spieler kommt am Aufnahmeort " + SERVER.get("fbSite") + " nicht an") : StepResult.pending();
                    }
                    if (!FlashbackBridge.ready()) {
                        return StepResult.fail("Flashback nicht bereit: " + FlashbackBridge.problem());
                    }
                    FlashbackProbe.quicksave(true);
                    if (!FlashbackBridge.startRecording()) {
                        return StepResult.fail("Flashback startet keine Aufnahme");
                    }
                    next(ctx);
                    return StepResult.pending();
                }
                case 2 -> {
                    if (!FlashbackBridge.writing()) {
                        return since(ctx) > 600 ? StepResult.fail("Flashback schreibt nicht") : StepResult.pending();
                    }
                    StepResult wait = server(ctx, "fbOpen", server -> {
                        ServerPlayer player = player(server);
                        Far far = CaptureJoinCheck.spawnFar(player, RADIUS, SPAWNED, 16);
                        SERVER.put("fb", far);
                        SERVER.put("fbOpen", String.valueOf(PropwashApi.drones().cameraChunks().open(player, far.entity(), RADIUS).status()));
                        SERVER.put("fbPlayerChunk", player.chunkPosition().x());
                    });
                    if (wait != null) {
                        return wait;
                    }
                    if (!"OPENED".equals(SERVER.get("fbOpen"))) {
                        return StepResult.fail("Anmeldung " + SERVER.get("fbOpen"));
                    }
                    next(ctx);
                    return StepResult.pending();
                }
                case 3 -> {
                    Far far = (Far) SERVER.get("fb");
                    ClientLevel level = mc.level;
                    if (level == null || missing(level, far.area()) > 0 || level.getEntity(far.droneId()) == null || level.getEntity(far.markerId()) == null) {
                        return since(ctx) > 1200 ? StepResult.fail("Lage kommt während der Aufnahme nicht an") : StepResult.pending();
                    }
                    ctx.state().put("arrived", since(ctx));
                    next(ctx);
                    return StepResult.pending();
                }
                case 4 -> {
                    if (since(ctx) < 60) {
                        return StepResult.pending();
                    }
                    Object recorder = FlashbackBridge.recorder();
                    if (recorder == null) {
                        return StepResult.fail("keine laufende Flashback-Aufnahme");
                    }
                    recorded = FlashbackBridge.replayId(recorder);
                    recordedTicks = FlashbackBridge.writtenTicks(recorder);
                    recordedFar = (Far) SERVER.get("fb");
                    recordedPlayerChunkX = (int) SERVER.get("fbPlayerChunk");
                    if (!FlashbackBridge.finishRecording()) {
                        return StepResult.fail("Flashback beendet die Aufnahme nicht");
                    }
                    next(ctx);
                    return StepResult.pending();
                }
                case 5 -> {
                    UUID replay = recorded;
                    if (replay == null || FlashbackProbe.replayWith(replay) == null) {
                        return since(ctx) > 600 ? StepResult.fail("Flashback speichert die Aufnahme nicht") : StepResult.pending();
                    }
                    StepResult wait = server(ctx, "fbClose", server -> {
                        ServerPlayer player = player(server);
                        Far far = (Far) SERVER.get("fb");
                        PropwashApi.drones().cameraChunks().close(player, far.entity());
                        Origin home = (Origin) SERVER.get("fbHome");
                        ServerLevel level = server.getLevel(home.dimension());
                        player.teleportTo(level == null ? server.overworld() : level, home.position().x, home.position().y, home.position().z, Set.of(),
                                home.yRot(), home.xRot(), true);
                    });
                    if (wait != null) {
                        return wait;
                    }
                    next(ctx);
                    return StepResult.pending();
                }
                default -> {
                    Origin home = (Origin) SERVER.get("fbHome");
                    if (!arrived(mc, home.dimension(), home.position())) {
                        return since(ctx) > 600 ? StepResult.fail("Spieler kommt nach der Aufnahme nicht zurück an " + home.position()) : StepResult.pending();
                    }
                    Vec3 site = (Vec3) SERVER.get("fbSite");
                    ctx.log(String.format(Locale.ROOT, "Aufnahme %s mit %d Ticks am Aufnahmeort %.1f %.1f %.1f (Chunk %d); Lage %d Blöcke weg kam nach %s Ticks "
                                    + "während der Aufnahme beim Client an; danach zurück an %s", recorded, recordedTicks, site.x, site.y, site.z,
                            recordedPlayerChunkX, recordedFar.blocks(), ctx.state().get("arrived"), home.position()));
                    return StepResult.pass();
                }
            }
        } catch (ReflectiveOperationException | IOException e) {
            return StepResult.fail("Flashback nicht steuerbar: " + e);
        }
    }

    private static StepResult replayStep(SelfTest.StepContext ctx) {
        Minecraft mc = Minecraft.getInstance();
        UUID replay = recorded;
        Far far = recordedFar;
        if (replay == null || far == null) {
            return StepResult.fail("keine Flashback-Aufnahme aus dem Aufnahme-Schritt");
        }
        try {
            switch (phase(ctx)) {
                case 0 -> {
                    Path zip = FlashbackProbe.replayWith(replay);
                    if (zip == null) {
                        return StepResult.fail("Flashback-Aufnahme " + replay + " nicht gefunden");
                    }
                    FlashbackProbe.openReplay(zip);
                    next(ctx);
                    return StepResult.pending();
                }
                case 1 -> {
                    if (!FlashbackBridge.inReplay() || mc.level == null || mc.player == null || !FlashbackProbe.replayReady()) {
                        return since(ctx) > 1500 ? StepResult.fail("Flashback öffnet die Wiederholung nicht") : StepResult.pending();
                    }
                    FlashbackProbe.goToTick(Math.max(0, recordedTicks - 10));
                    next(ctx);
                    return StepResult.pending();
                }
                case 2 -> {
                    if (since(ctx) < 60) {
                        return StepResult.pending();
                    }
                    if (!(FlashbackProbe.replayServer() instanceof MinecraftServer server)) {
                        return StepResult.fail("kein Wiederholungs-Server");
                    }
                    int mirror = recordedPlayerChunkX - (far.area().centerX() - recordedPlayerChunkX);
                    ctx.state().put("replayCheck", CompletableFuture.supplyAsync(() -> replayCheck(server, far, mirror), server));
                    next(ctx);
                    return StepResult.pending();
                }
                case 3 -> {
                    CompletableFuture<?> future = (CompletableFuture<?>) ctx.state().get("replayCheck");
                    if (!future.isDone()) {
                        return since(ctx) > 400 ? StepResult.fail("Prüfung in der Wiederholung hängt") : StepResult.pending();
                    }
                    if (future.isCompletedExceptionally()) {
                        return StepResult.fail("Prüfung in der Wiederholung: " + future.exceptionNow());
                    }
                    String result = (String) future.resultNow();
                    ctx.state().put("replayResult", result);
                    mc.schedule(() -> mc.disconnectFromWorld(Component.literal("Kamera-Chunks geprüft")));
                    next(ctx);
                    return StepResult.pending();
                }
                default -> {
                    if (mc.level != null || FlashbackBridge.inReplay()) {
                        return since(ctx) > 600 ? StepResult.fail("Wiederholung lässt sich nicht verlassen") : StepResult.pending();
                    }
                    String result = (String) ctx.state().get("replayResult");
                    if (!result.startsWith("ok")) {
                        return StepResult.fail(result);
                    }
                    ctx.log("Wiederholung Tick " + Math.max(0, recordedTicks - 10) + ": " + result);
                    return StepResult.pass();
                }
            }
        } catch (ReflectiveOperationException | IOException e) {
            return StepResult.fail("Flashback nicht steuerbar: " + e);
        }
    }

    private static String replayCheck(MinecraftServer server, Far far, int mirrorX) {
        ServerLevel level = server.overworld();
        int[] terrain = new int[2];
        far.area().forEach(pos -> {
            if (!far.area().contains(pos.x(), pos.z(), false)) {
                return;
            }
            terrain[0]++;
            LevelChunk chunk = level.getChunk(pos.x(), pos.z());
            if (chunk.getHeight(Heightmap.Types.WORLD_SURFACE, 8, 8) > level.getMinY()) {
                terrain[1]++;
            }
        });
        LevelChunk control = level.getChunk(mirrorX, far.area().centerZ());
        boolean controlEmpty = control.getHeight(Heightmap.Types.WORLD_SURFACE, 8, 8) <= level.getMinY();
        boolean drone = level.getEntity(far.drone()) instanceof QuadcopterEntity;
        boolean marker = level.getEntity(far.marker()) instanceof ArmorStand;
        String text = String.format(Locale.ROOT, "%d von %d Chunks der Lage mit Gelände, Drohne %s, Marker %s, Gegenseite (Chunk %d) %s", terrain[1], terrain[0],
                drone ? "da" : "fehlt", marker ? "da" : "fehlt", mirrorX, controlEmpty ? "leer" : "mit Gelände");
        boolean ok = terrain[1] == terrain[0] && terrain[0] > 0 && drone && marker && controlEmpty;
        return (ok ? "ok " : "") + text;
    }

    private static StepResult cleanupStep(SelfTest.StepContext ctx) {
        if (phase(ctx) == 0) {
            if (FlashbackCamSelfTests.flashbackPresent() && FlashbackBridge.recording()) {
                FlashbackBridge.finishRecording();
            }
            StepResult wait = server(ctx, "cleanup", server -> {
                ServerPlayer player = player(server);
                PropwashApi.drones().cameraChunks().closeAll(player);
                WorldRules.set(server, WorldRuleSpecs.CAMERA_CHUNKS, WorldRuleSpecs.CAMERA_CHUNKS.defaultValue());
                sweep(player.level());
            });
            if (wait != null) {
                return wait;
            }
            next(ctx);
            return StepResult.pending();
        }
        Origin from = origin;
        if (phase(ctx) == 1) {
            int round = since(ctx) / 20;
            if (!SPAWNED.isEmpty() && round < 15) {
                StepResult wait = server(ctx, "sweep" + round, server -> sweep(player(server).level()));
                if (wait != null && wait.status() == SelfTest.StepResult.Status.FAIL) {
                    return wait;
                }
                return StepResult.pending();
            }
            ctx.state().putIfAbsent("left", SPAWNED.size());
            SPAWNED.clear();
            if (from != null) {
                StepResult wait = server(ctx, "back", server -> {
                    ServerPlayer player = player(server);
                    ServerLevel level = server.getLevel(from.dimension());
                    Vec3 at = from.position();
                    player.teleportTo(level == null ? server.overworld() : level, at.x, at.y, at.z, Set.of(), from.yRot(), from.xRot(), true);
                });
                if (wait != null) {
                    return wait;
                }
            }
            next(ctx);
            return StepResult.pending();
        }
        if (from != null && !arrived(Minecraft.getInstance(), from.dimension(), from.position())) {
            return since(ctx) > 600 ? StepResult.fail("Spieler kommt nicht zurück an " + from.position()) : StepResult.pending();
        }
        origin = null;
        int left = (int) ctx.state().get("left");
        ctx.log((left == 0 ? "alle Test-Entities entfernt" : left + " Test-Entities in entladenen Chunks gelassen")
                + (from == null ? "" : ", Spieler zurück an " + from.position()));
        return StepResult.pass();
    }

    private static void sweep(ServerLevel level) {
        for (UUID id : List.copyOf(SPAWNED)) {
            discard(level, id);
        }
        for (String key : List.of("far", "far2", "fb")) {
            if (SERVER.get(key) instanceof Far far && !SPAWNED.isEmpty()) {
                level.getChunkSource().addTicketWithRadius(ServerModule.DRONE_VIEW_TICKET.get(), far.area().center(), 1);
            }
        }
    }

    private static void command(MinecraftServer server, String command) {
        server.getCommands().performPrefixedCommand(player(server).createCommandSourceStack(), command);
    }
}
