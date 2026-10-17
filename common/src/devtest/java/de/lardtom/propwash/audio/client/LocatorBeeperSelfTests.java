package de.lardtom.propwash.audio.client;

import com.mojang.blaze3d.platform.InputConstants;
import de.lardtom.propwash.build.DroneBuild;
import de.lardtom.propwash.content.ContentIds;
import de.lardtom.propwash.core.CoreComponents;
import de.lardtom.propwash.dev.SelfTest;
import de.lardtom.propwash.dev.SelfTest.StepResult;
import de.lardtom.propwash.drone.DroneInteractions;
import de.lardtom.propwash.drone.DroneModule;
import de.lardtom.propwash.drone.DronePiloting;
import de.lardtom.propwash.drone.PilotMode;
import de.lardtom.propwash.drone.QuadcopterEntity;
import de.lardtom.propwash.drone.client.PilotSession;
import de.lardtom.propwash.input.RadioState;
import de.lardtom.propwash.input.SwitchFunction;
import de.lardtom.propwash.input.client.InputKeys;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public final class LocatorBeeperSelfTests {
    private static final String PHASE = "phase";
    private static final String SINCE = "since";
    private static final String FUTURE = "future";
    private static final String BEEPS = "beeps";
    private static final double DISTANCE = 6.0;
    private static volatile int droneId = -1;
    private static volatile @Nullable ItemStack previousHead;
    private static volatile @Nullable ItemStack previousHand;

    private LocatorBeeperSelfTests() {
    }

    public static void register() {
        SelfTest.clientStep("audio: Piepser piept nach dem Verlassen weiter, bis er aus ist, der Akku leer ist oder die Drohne aufgehoben wird",
                1600, LocatorBeeperSelfTests::step);
    }

    private static StepResult step(SelfTest.StepContext ctx) {
        Map<String, Object> state = ctx.state();
        int phase = (int) state.getOrDefault(PHASE, 0);
        int since = ctx.tick() - (int) state.getOrDefault(SINCE, 0);
        Minecraft minecraft = Minecraft.getInstance();
        QuadcopterEntity drone = clientDrone(minecraft);
        UUID self = minecraft.player == null ? null : minecraft.player.getUUID();
        switch (phase) {
            case 0 -> {
                if (!state.containsKey("spawned")) {
                    state.put("spawned", true);
                    state.put(FUTURE, ctx.onServer(LocatorBeeperSelfTests::spawnAndFly));
                    return StepResult.pending();
                }
                StepResult done = future(ctx, "Testflug nicht gestartet");
                if (done != null) {
                    return done;
                }
                if (drone == null || !PilotSession.INSTANCE.isActive() || PilotSession.INSTANCE.drone() != drone) {
                    return since > 300 ? fail(ctx, "keine Sitzung mit der Testdrohne") : StepResult.pending();
                }
                if (RadioState.INSTANCE.isActive(SwitchFunction.BEEPER)) {
                    return fail(ctx, "Piepser ist vor dem Test schon an");
                }
                click();
                return next(ctx, 1);
            }
            case 1 -> {
                if (drone == null || !RadioState.INSTANCE.isActive(SwitchFunction.BEEPER)) {
                    return since > 60 ? fail(ctx, "Taste schaltet den Piepser nicht ein") : StepResult.pending();
                }
                if (!drone.beeperBy().filter(id -> id.equals(self)).isPresent()) {
                    return since > 100 ? fail(ctx, "Server übernimmt den Piepser nicht aus dem Flugzustand") : StepResult.pending();
                }
                state.put(FUTURE, ctx.onServer(server -> serverDrone(server).ifPresent(DronePiloting::release)));
                return next(ctx, 2);
            }
            case 2 -> {
                StepResult done = future(ctx, "Freigeben fehlgeschlagen");
                if (done != null) {
                    return done;
                }
                if (PilotSession.INSTANCE.isActive()) {
                    return since > 200 ? fail(ctx, "Sitzung endet nach dem Freigeben nicht") : StepResult.pending();
                }
                state.put(BEEPS, LocatorBeeper.beeps());
                return next(ctx, 3);
            }
            case 3 -> {
                if (drone == null) {
                    return fail(ctx, "Testdrohne nach dem Verlassen verschwunden");
                }
                if (!drone.beeping() || drone.pilot().isPresent()) {
                    return fail(ctx, "Piepser geht beim Verlassen aus (Pilot " + drone.pilot().isPresent() + ")");
                }
                int beeps = LocatorBeeper.beeps() - (int) state.get(BEEPS);
                if (since < LocatorBeeper.INTERVAL_TICKS * 3 + 4) {
                    return StepResult.pending();
                }
                if (beeps < 2) {
                    return fail(ctx, "nach dem Verlassen nur " + beeps + " Pieptöne in " + since + " Ticks");
                }
                ctx.log("Piepser: " + beeps + " Pieptöne in " + since + " Ticks nach dem Verlassen, Pilot weg, Drohne weiter an");
                if (!RadioState.INSTANCE.isActive(SwitchFunction.BEEPER)) {
                    return fail(ctx, "Tastatur-Schalter fällt beim Verlassen zurück");
                }
                click();
                return next(ctx, 4);
            }
            case 4 -> {
                if (drone == null || drone.beeping()) {
                    return since > 100 ? fail(ctx, "Taste schaltet den Piepser zu Fuß nicht aus") : StepResult.pending();
                }
                if (RadioState.INSTANCE.isActive(SwitchFunction.BEEPER)) {
                    return fail(ctx, "Tastatur-Schalter bleibt nach dem Ausschalten an");
                }
                state.put(BEEPS, LocatorBeeper.beeps());
                state.put(FUTURE, ctx.onServer(server -> serverDrone(server).ifPresent(d -> {
                    d.setBeeper(server.getPlayerList().getPlayers().getFirst().getUUID());
                    d.setBatteryCharge(1.0E-5F);
                })));
                return next(ctx, 5);
            }
            case 5 -> {
                StepResult done = future(ctx, "Akku setzen fehlgeschlagen");
                if (done != null) {
                    return done;
                }
                if (drone == null || drone.beeping() || drone.batteryCharge() > 0.0F) {
                    return since > 200 ? fail(ctx, "leerer Akku schaltet den Piepser nicht ab") : StepResult.pending();
                }
                state.put(FUTURE, ctx.onServer(server -> serverDrone(server).ifPresent(d -> {
                    d.setBatteryCharge(0.5F);
                    d.setBeeper(server.getPlayerList().getPlayers().getFirst().getUUID());
                })));
                return next(ctx, 6);
            }
            case 6 -> {
                StepResult done = future(ctx, "Piepser setzen fehlgeschlagen");
                if (done != null) {
                    return done;
                }
                if (drone == null || !drone.beeping()) {
                    return since > 100 ? fail(ctx, "Piepser lässt sich nicht wieder setzen") : StepResult.pending();
                }
                state.put(FUTURE, ctx.onServer(LocatorBeeperSelfTests::pickUp));
                return next(ctx, 7);
            }
            case 7 -> {
                StepResult done = future(ctx, "Aufheben fehlgeschlagen");
                if (done != null) {
                    return done;
                }
                if (drone != null) {
                    return since > 100 ? fail(ctx, "aufgehobene Drohne bleibt in der Welt") : StepResult.pending();
                }
                int before = LocatorBeeper.beeps();
                state.put(BEEPS, before);
                return next(ctx, 8);
            }
            case 8 -> {
                if (since < LocatorBeeper.INTERVAL_TICKS * 2) {
                    return StepResult.pending();
                }
                int after = LocatorBeeper.beeps() - (int) state.get(BEEPS);
                CompletableFuture<Void> cleanup = ctx.onServer(LocatorBeeperSelfTests::cleanup);
                state.put(FUTURE, cleanup);
                if (after != 0) {
                    return fail(ctx, "nach dem Aufheben noch " + after + " Pieptöne");
                }
                ctx.log("Piepser: aus per Taste zu Fuß, aus bei leerem Akku, aus nach dem Aufheben (Gegenstand ohne Piepser)");
                return StepResult.pass();
            }
            default -> {
                return fail(ctx, "unbekannte Phase " + phase);
            }
        }
    }

    private static StepResult next(SelfTest.StepContext ctx, int phase) {
        ctx.state().put(PHASE, phase);
        ctx.state().put(SINCE, ctx.tick());
        return StepResult.pending();
    }

    private static @Nullable StepResult future(SelfTest.StepContext ctx, String message) {
        Object value = ctx.state().get(FUTURE);
        if (!(value instanceof CompletableFuture<?> future)) {
            return null;
        }
        if (!future.isDone()) {
            return StepResult.pending();
        }
        ctx.state().remove(FUTURE);
        if (future.isCompletedExceptionally()) {
            return fail(ctx, message + ": " + future.exceptionNow().getMessage());
        }
        return null;
    }

    private static StepResult fail(SelfTest.StepContext ctx, String message) {
        ctx.onServer(LocatorBeeperSelfTests::cleanup);
        return StepResult.fail(message);
    }

    private static void click() {
        KeyMapping.click(InputConstants.getKey(InputKeys.BEEPER.saveString()));
    }

    private static @Nullable QuadcopterEntity clientDrone(Minecraft minecraft) {
        if (minecraft.level == null || droneId < 0) {
            return null;
        }
        return minecraft.level.getEntity(droneId) instanceof QuadcopterEntity drone && !drone.isRemoved() ? drone : null;
    }

    private static Optional<QuadcopterEntity> serverDrone(MinecraftServer server) {
        for (ServerLevel level : server.getAllLevels()) {
            if (level.getEntity(droneId) instanceof QuadcopterEntity drone && !drone.isRemoved()) {
                return Optional.of(drone);
            }
        }
        return Optional.empty();
    }

    private static ServerPlayer player(MinecraftServer server) {
        List<ServerPlayer> players = server.getPlayerList().getPlayers();
        if (players.isEmpty()) {
            throw new IllegalStateException("kein Spieler auf dem Server");
        }
        return players.getFirst();
    }

    private static void spawnAndFly(MinecraftServer server) {
        ServerPlayer player = player(server);
        DronePiloting.pilotedBy(player.getUUID()).ifPresent(DronePiloting::release);
        serverDrone(server).ifPresent(QuadcopterEntity::discard);
        ServerLevel level = player.level();
        QuadcopterEntity drone = DroneModule.DRONE.get().create(level, EntitySpawnReason.COMMAND);
        if (drone == null) {
            throw new IllegalStateException("EntityType.create lieferte null");
        }
        Vec3 feet = player.position();
        drone.snapTo(feet.x, feet.y, feet.z + DISTANCE, 0.0F, 0.0F);
        drone.setAttitude(new Quaternionf());
        drone.setBuild(DroneBuild.RACER);
        if (!level.addFreshEntity(drone)) {
            throw new IllegalStateException("addFreshEntity fehlgeschlagen");
        }
        droneId = drone.getId();
        ItemStack head = ContentIds.item(ContentIds.GOGGLES_ANALOG).map(ItemStack::new).orElse(ItemStack.EMPTY);
        head.set(CoreComponents.BOUND_DRONE.get(), DroneInteractions.binding(drone));
        if (previousHead == null) {
            previousHead = player.getItemBySlot(EquipmentSlot.HEAD).copy();
            previousHand = player.getMainHandItem().copy();
        }
        player.setItemSlot(EquipmentSlot.HEAD, head);
        ItemStack remote = new ItemStack(DroneModule.REMOTE.get());
        remote.set(CoreComponents.BOUND_DRONE.get(), DroneInteractions.binding(drone));
        player.setItemSlot(EquipmentSlot.MAINHAND, remote);
        DronePiloting.StartResult result = DronePiloting.tryStart(player, drone, PilotMode.FPV);
        if (result != DronePiloting.StartResult.STARTED) {
            throw new IllegalStateException("FPV-Start verweigert: " + result);
        }
    }

    private static void pickUp(MinecraftServer server) {
        QuadcopterEntity drone = serverDrone(server).orElseThrow(() -> new IllegalStateException("Testdrohne fehlt"));
        ItemStack stack = drone.toStack();
        drone.discard();
        QuadcopterEntity placed = DroneModule.DRONE.get().create(drone.level(), EntitySpawnReason.COMMAND);
        if (placed == null) {
            throw new IllegalStateException("EntityType.create lieferte null");
        }
        placed.loadFromStack(stack);
        if (placed.beeping()) {
            throw new IllegalStateException("Piepser reist im Gegenstand mit");
        }
    }

    private static void cleanup(MinecraftServer server) {
        List<ServerPlayer> players = server.getPlayerList().getPlayers();
        if (!players.isEmpty()) {
            ServerPlayer player = players.getFirst();
            DronePiloting.pilotedBy(player.getUUID()).ifPresent(DronePiloting::release);
            ItemStack head = previousHead;
            if (head != null) {
                player.setItemSlot(EquipmentSlot.HEAD, head);
            }
            ItemStack hand = previousHand;
            if (hand != null) {
                player.setItemSlot(EquipmentSlot.MAINHAND, hand);
            }
        }
        serverDrone(server).ifPresent(QuadcopterEntity::discard);
        previousHead = null;
        previousHand = null;
    }
}
