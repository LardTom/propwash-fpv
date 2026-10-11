package de.lardtom.propwash.server.client;

import de.lardtom.propwash.dev.SelfTest;
import de.lardtom.propwash.dev.SelfTest.StepResult;
import de.lardtom.propwash.drone.QuadcopterEntity;
import de.lardtom.propwash.drone.DroneModule;
import de.lardtom.propwash.server.AnchorRules;
import de.lardtom.propwash.server.PilotLink;
import de.lardtom.propwash.server.RangeManager;
import de.lardtom.propwash.server.ServerRules;
import de.lardtom.propwash.server.ServerRulesConfig;
import de.lardtom.propwash.server.ServerRulesS2C;
import de.lardtom.propwash.server.ViewAnchors;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ChunkTrackingView;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.concurrent.CompletableFuture;

public final class ServerSelfTests {
    private static final String PROBE = "probe";
    private static final double FLIGHT_DISTANCE = 320.0;
    private static final double STEP_PER_TICK = 2.0;
    private static final double ARRIVAL_SLACK = 1.0E-6;
    private static final int TEST_VIEW_DISTANCE = 4;

    private ServerSelfTests() {
    }

    public static void register() {
        SelfTest.clientStep("server: Regeln synchronisiert", 100, ctx -> {
            if (!ClientServerRules.INSTANCE.known()) {
                return StepResult.pending();
            }
            ServerRulesConfig config = ServerRulesConfig.get();
            ServerRulesS2C rules = ClientServerRules.INSTANCE.rules();
            boolean hostGenerates = !config.onlyGeneratedChunks || config.hostGeneratesChunks;
            return StepResult.check(rules != null && rules.maxStreamedChunks() == config.maxStreamedChunks
                            && rules.onlyGeneratedChunks() == config.onlyGeneratedChunks
                            && rules.generateChunksPermissionLevel() == config.generateChunksPermissionLevel
                            && (!hostGenerates || rules.mayGenerateChunks())
                            && rules.unlimitedRangeEnabled() == config.allowUnlimitedRange,
                    "Regeln auf dem Client weichen von der Server-Config ab: " + rules);
        });
        WorldRulesSelfTests.register();
        SelfTest.clientStep("server: Sichtanker folgt der Drohne", 1600, ServerSelfTests::anchorStep);
        SelfTest.clientStep(ChunkGenerationSelfTest.NAME, ChunkGenerationSelfTest.MAX_TICKS, ChunkGenerationSelfTest::step);
        SelfTest.clientStep("server: gesperrte Chunk-Erzeugung trennt über unerforschtem Gebiet", 300, ServerSelfTests::unexploredStep);
        GenerationEdgeSelfTests.register();
        SelfTest.afterLeaveStep("server: Zustand nach dem Verlassen leer", 100, ctx -> {
            boolean idle = RangeManager.isIdle() && ViewAnchors.size() == 0 && !ClientServerRules.INSTANCE.known();
            if (idle) {
                return StepResult.pass();
            }
            return ctx.tick() < 80 ? StepResult.pending() : StepResult.fail("Server-Zustand nicht aufgeräumt: links="
                    + RangeManager.links().size() + " tickets=" + RangeManager.ticketCount() + " anker=" + ViewAnchors.size()
                    + " regeln=" + ClientServerRules.INSTANCE.known());
        });
    }

    private static StepResult unexploredStep(SelfTest.StepContext ctx) {
        Probe probe = (Probe) ctx.state().get(PROBE);
        if (probe == null) {
            probe = new Probe();
            ctx.state().put(PROBE, probe);
            Probe p = probe;
            probe.pending = ctx.onServer(server -> startUnexplored(server, p));
            return StepResult.pending();
        }
        if (probe.pending != null) {
            if (probe.pending.isCompletedExceptionally()) {
                return finish(ctx, probe, StepResult.fail("Server-Schritt fehlgeschlagen: " + probe.pending.exceptionNow()));
            }
            if (!probe.pending.isDone()) {
                return StepResult.pending();
            }
            probe.pending = null;
        }
        if (probe.failure != null) {
            return finish(ctx, probe, StepResult.fail(probe.failure));
        }
        if (probe.released) {
            return finish(ctx, probe, StepResult.pass());
        }
        if (++probe.waitTicks > 250) {
            return finish(ctx, probe, StepResult.fail("Pilot über unerforschtem Gebiet nicht gelöst: " + probe.status));
        }
        Probe p = probe;
        probe.pending = ctx.onServer(server -> inspectUnexplored(server, p));
        return StepResult.pending();
    }

    private static void startUnexplored(MinecraftServer server, Probe probe) {
        List<ServerPlayer> players = server.getPlayerList().getPlayers();
        if (players.isEmpty()) {
            probe.failure = "kein Spieler auf dem Server";
            return;
        }
        ServerPlayer player = players.getFirst();
        ServerLevel level = player.level();
        ServerRulesConfig config = ServerRulesConfig.get();
        probe.savedConfig = config.copy();
        config.allowUnlimitedRange = true;
        config.unlimitedRangePermissionLevel = 0;
        config.onlyGeneratedChunks = true;
        config.hostGeneratesChunks = false;
        config.generateChunksPermissionLevel = ServerRulesConfig.GENERATE_NOBODY;
        config.allowPilotingInSpawnProtection = true;
        probe.origin = player.position();
        QuadcopterEntity drone = DroneModule.DRONE.get().create(level, EntitySpawnReason.COMMAND);
        if (drone == null) {
            probe.failure = "EntityType.create lieferte null";
            return;
        }
        Vec3 pos = player.position().add(0.0, 3.0, 0.0);
        drone.snapTo(pos.x, pos.y, pos.z, 0.0F, 0.0F);
        if (!level.addFreshEntity(drone)) {
            probe.failure = "addFreshEntity fehlgeschlagen";
            return;
        }
        drone.setPilot(player);
        probe.drone = drone;
        probe.droneId = drone.getId();
        probe.playerId = player.getId();
        Vec3 far = probe.origin.add(6000.0, 3.0, 6000.0);
        probe.droneChunk = ChunkPos.containing(BlockPos.containing(far));
        drone.resetTo(far, new Quaternionf());
    }

    private static void inspectUnexplored(MinecraftServer server, Probe probe) {
        ServerPlayer player = player(server, probe);
        QuadcopterEntity drone = probe.drone;
        if (player == null || drone == null) {
            probe.failure = "Spieler oder Drohne verschwunden";
            return;
        }
        boolean pilotReleased = drone.pilot().isEmpty();
        boolean linkGone = RangeManager.link(player.getUUID()).isEmpty() && !ViewAnchors.isAnchored(player);
        boolean notGenerated = player.level().getChunkSource().getChunkNow(probe.droneChunk.x(), probe.droneChunk.z()) == null;
        ChunkTrackingView view = player.getChunkTrackingView();
        boolean viewNearBody = view.contains(player.chunkPosition());
        probe.status = "gelöst=" + pilotReleased + " linkWeg=" + linkGone + " nichtErzeugt=" + notGenerated + " sichtAmKörper=" + viewNearBody
                + " entscheidung=" + RangeManager.link(player.getUUID()).map(PilotLink::decision).orElse(AnchorRules.Decision.DETACHED)
                + " prüfungen=" + RangeManager.pendingChunkChecks();
        if (pilotReleased && !notGenerated) {
            probe.failure = "Drohne hat unerforschte Chunks erzeugt: " + probe.status;
            return;
        }
        probe.released = pilotReleased && linkGone && notGenerated && viewNearBody;
    }

    private static StepResult anchorStep(SelfTest.StepContext ctx) {
        Minecraft mc = Minecraft.getInstance();
        Probe probe = (Probe) ctx.state().get(PROBE);
        if (probe == null) {
            probe = new Probe();
            ctx.state().put(PROBE, probe);
            Probe p = probe;
            probe.pending = ctx.onServer(server -> start(server, p));
            return StepResult.pending();
        }
        if (ctx.tick() > 1500) {
            return finish(ctx, probe, StepResult.fail("Sichtanker hängt in Phase " + probe.phase + ": Drohne bei " + probe.dronePos + ", Start " + probe.origin
                    + ", Server-Aufruf " + (probe.pending == null ? "keiner offen" : probe.pending.isDone() ? "fertig" : "wartet seit Tick " + probe.asked)
                    + ", Bildschirm " + (mc.gui.screen() == null ? "keiner" : mc.gui.screen().getClass().getSimpleName()) + ", pausiert " + mc.isPaused()));
        }
        if (probe.pending != null) {
            if (probe.pending.isCompletedExceptionally()) {
                return finish(ctx, probe, StepResult.fail("Server-Schritt in Phase " + probe.phase + " fehlgeschlagen: "
                        + probe.pending.exceptionNow()));
            }
            if (!probe.pending.isDone()) {
                return StepResult.pending();
            }
            probe.pending = null;
            probe.asked = ctx.tick();
        }
        if (probe.failure != null) {
            return finish(ctx, probe, StepResult.fail(probe.failure));
        }
        Probe p = probe;
        switch (probe.phase) {
            case FLYING -> probe.pending = ctx.onServer(server -> fly(server, p));
            case ARRIVED -> {
                if (mc.level == null) {
                    return StepResult.pending();
                }
                Entity seen = mc.level.getEntity(probe.droneId);
                boolean clientSees = seen instanceof QuadcopterEntity && seen.position().distanceTo(probe.dronePos) < 6.0;
                boolean clientHasChunk = mc.level.getChunkSource().hasChunk(probe.droneChunk.x(), probe.droneChunk.z());
                if (probe.serverAnchored && probe.viewCentered && probe.bodyOutsideView && clientSees && clientHasChunk) {
                    ctx.log("Sichtanker an der Drohne " + Math.round(probe.dronePos.x - probe.origin.x) + " Blöcke entfernt, Drohne weiter sichtbar");
                    probe.phase = Phase.LIMIT;
                    probe.pending = ctx.onServer(server -> limit(server, p));
                    return StepResult.pending();
                }
                if (++probe.waitTicks > 400) {
                    return finish(ctx, probe, StepResult.fail("Sichtanker nicht an der Drohne: anker=" + probe.serverAnchored
                            + " zentriert=" + probe.viewCentered + " körperAußerhalb=" + probe.bodyOutsideView
                            + " clientSiehtDrohne=" + clientSees + " clientChunk=" + clientHasChunk + " status=" + probe.status));
                }
                probe.pending = ctx.onServer(server -> inspect(server, p));
            }
            case LIMIT -> {
                if (probe.released) {
                    return finish(ctx, probe, StepResult.pass());
                }
                if (++probe.waitTicks > 200) {
                    return finish(ctx, probe, StepResult.fail("Pilot bei ausgeschalteter Reichweite nicht gelöst: " + probe.status));
                }
                probe.pending = ctx.onServer(server -> inspectRelease(server, p));
            }
            default -> {
                return StepResult.pending();
            }
        }
        return StepResult.pending();
    }

    private static void start(MinecraftServer server, Probe probe) {
        List<ServerPlayer> players = server.getPlayerList().getPlayers();
        if (players.isEmpty()) {
            probe.failure = "kein Spieler auf dem Server";
            return;
        }
        ServerPlayer player = players.getFirst();
        ServerLevel level = player.level();
        ServerRulesConfig config = ServerRulesConfig.get();
        probe.savedConfig = config.copy();
        config.allowUnlimitedRange = true;
        config.unlimitedRangePermissionLevel = 0;
        config.onlyGeneratedChunks = false;
        config.allowPilotingInSpawnProtection = true;
        config.droneViewDistance = TEST_VIEW_DISTANCE;
        probe.origin = player.position();
        QuadcopterEntity drone = DroneModule.DRONE.get().create(level, EntitySpawnReason.COMMAND);
        if (drone == null) {
            probe.failure = "EntityType.create lieferte null";
            return;
        }
        Vec3 pos = player.position().add(0.0, 3.0, 0.0);
        drone.snapTo(pos.x, pos.y, pos.z, 0.0F, 0.0F);
        if (!level.addFreshEntity(drone)) {
            probe.failure = "addFreshEntity fehlgeschlagen";
            return;
        }
        drone.setPilot(player);
        probe.drone = drone;
        probe.droneId = drone.getId();
        probe.playerId = player.getId();
        probe.dronePos = drone.position();
        probe.phase = Phase.FLYING;
    }

    private static void fly(MinecraftServer server, Probe probe) {
        ServerPlayer player = player(server, probe);
        QuadcopterEntity drone = drone(server, probe);
        if (player == null || drone == null) {
            probe.failure = "Spieler oder Drohne verschwunden (Flug)";
            return;
        }
        if (drone.pilot().isEmpty()) {
            probe.failure = "Pilot während des Flugs verloren bei " + Math.round(drone.getX() - probe.origin.x) + " Blöcken";
            return;
        }
        double flown = drone.getX() - probe.origin.x;
        if (flown >= FLIGHT_DISTANCE - ARRIVAL_SLACK) {
            probe.phase = Phase.ARRIVED;
            inspect(server, probe);
            return;
        }
        Vec3 next = drone.position().add(Math.min(STEP_PER_TICK, FLIGHT_DISTANCE - flown), 0.0, 0.0);
        drone.resetTo(next, new Quaternionf());
        probe.dronePos = drone.position();
    }

    private static void inspect(MinecraftServer server, Probe probe) {
        ServerPlayer player = player(server, probe);
        QuadcopterEntity drone = drone(server, probe);
        if (player == null || drone == null) {
            probe.failure = "Spieler oder Drohne verschwunden (Prüfung)";
            return;
        }
        ChunkPos droneChunk = drone.chunkPosition();
        ChunkTrackingView view = player.getChunkTrackingView();
        probe.dronePos = drone.position();
        probe.droneChunk = droneChunk;
        probe.serverAnchored = ViewAnchors.isAnchored(player) && ViewAnchors.anchorDrone(player) == drone;
        probe.viewCentered = view instanceof ChunkTrackingView.Positioned positioned && positioned.center().equals(droneChunk);
        probe.bodyOutsideView = !view.contains(player.chunkPosition());
        PilotLink link = RangeManager.link(player.getUUID()).orElse(null);
        probe.status = link == null ? "kein Link" : link.decision() + " sicht=" + link.viewDistance() + " entfernung="
                + Math.round(link.distance()) + " tickets=" + RangeManager.ticketCount();
    }

    private static void limit(MinecraftServer server, Probe probe) {
        ServerPlayer player = player(server, probe);
        if (player == null) {
            probe.failure = "Spieler verschwunden (Begrenzung)";
            return;
        }
        ServerRulesConfig.get().allowUnlimitedRange = false;
        if (!(ServerRules.maxPilotDistance(player) < FLIGHT_DISTANCE)) {
            probe.failure = "begrenzte Reichweite nicht kleiner als die Testentfernung: " + ServerRules.maxPilotDistance(player);
        }
        probe.waitTicks = 0;
    }

    private static void inspectRelease(MinecraftServer server, Probe probe) {
        ServerPlayer player = player(server, probe);
        QuadcopterEntity drone = probe.drone;
        if (player == null || drone == null) {
            probe.failure = "Spieler oder Drohne verschwunden (Lösen)";
            return;
        }
        Entity.RemovalReason removal = drone.getRemovalReason();
        if (removal != null && removal != Entity.RemovalReason.UNLOADED_TO_CHUNK) {
            probe.failure = "Drohne beim Lösen entfernt: " + removal;
            return;
        }
        ChunkTrackingView view = player.getChunkTrackingView();
        boolean pilotReleased = drone.pilot().isEmpty();
        boolean anchorGone = !ViewAnchors.isAnchored(player) && RangeManager.link(player.getUUID()).isEmpty();
        boolean backAtBody = view instanceof ChunkTrackingView.Positioned positioned && positioned.center().equals(player.chunkPosition());
        probe.status = "gelöst=" + pilotReleased + " ankerWeg=" + anchorGone + " sichtAmKörper=" + backAtBody
                + " entscheidung=" + RangeManager.link(player.getUUID()).map(PilotLink::decision).orElse(AnchorRules.Decision.DETACHED);
        probe.released = pilotReleased && anchorGone && backAtBody;
    }

    private static StepResult finish(SelfTest.StepContext ctx, Probe probe, StepResult result) {
        if (!probe.cleaned) {
            probe.cleaned = true;
            ctx.onServer(server -> cleanup(server, probe));
        }
        return result;
    }

    private static void cleanup(MinecraftServer server, Probe probe) {
        if (probe.savedConfig != null) {
            ServerRulesConfig.get().assign(probe.savedConfig);
        }
        QuadcopterEntity drone = drone(server, probe);
        if (drone != null) {
            drone.discard();
        }
        ServerPlayer player = player(server, probe);
        if (player != null && probe.origin != null) {
            player.teleportTo(probe.origin.x, probe.origin.y, probe.origin.z);
        }
    }

    private static @Nullable ServerPlayer player(MinecraftServer server, Probe probe) {
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (player.getId() == probe.playerId) {
                return player;
            }
        }
        return null;
    }

    private static @Nullable QuadcopterEntity drone(MinecraftServer server, Probe probe) {
        QuadcopterEntity drone = probe.drone;
        return drone != null && !drone.isRemoved() ? drone : null;
    }

    private enum Phase {
        STARTING,
        FLYING,
        ARRIVED,
        LIMIT
    }

    private static final class Probe {
        private int asked;
        volatile @Nullable CompletableFuture<Void> pending;
        volatile Phase phase = Phase.STARTING;
        volatile @Nullable String failure;
        volatile @Nullable ServerRulesConfig savedConfig;
        volatile @Nullable Vec3 origin;
        volatile Vec3 dronePos = Vec3.ZERO;
        volatile ChunkPos droneChunk = new ChunkPos(0, 0);
        volatile @Nullable QuadcopterEntity drone;
        volatile int droneId = -1;
        volatile int playerId = -1;
        volatile boolean serverAnchored;
        volatile boolean viewCentered;
        volatile boolean bodyOutsideView;
        volatile boolean released;
        volatile String status = "";
        int waitTicks;
        boolean cleaned;
    }
}
