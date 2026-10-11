package de.lardtom.propwash.blackbox.client;

import de.lardtom.propwash.blackbox.BlackboxConfig;
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
import de.lardtom.propwash.input.StickPositions;
import de.lardtom.propwash.sim.DroneSim;
import de.lardtom.propwash.sim.StabMode;
import de.lardtom.propwash.sim.Telemetry;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
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
import java.util.concurrent.CompletableFuture;

final class BlackboxFlightFixture {
    static final String CRAFT_NAME = "Blackbox Test";
    static final double DRONE_DISTANCE = 5.0;
    private static final String FUTURE = "bb_fixture_future";
    private static final String START = "bb_fixture_start";
    private static final int LANDING_TICKS = 200;

    private static volatile int droneId = -1;
    private static volatile @Nullable ItemStack previousHead;
    private static volatile @Nullable ItemStack previousHand;
    private static @Nullable BlackboxConfig savedConfig;

    private BlackboxFlightFixture() {
    }

    static void enableBlackbox() {
        if (savedConfig == null) {
            savedConfig = BlackboxClient.CONFIG.get().copy();
        }
        BlackboxConfig config = BlackboxClient.CONFIG.get().copy();
        config.enabled = true;
        BlackboxClient.CONFIG.set(config);
    }

    static void disableBlackbox() {
        if (savedConfig == null) {
            savedConfig = BlackboxClient.CONFIG.get().copy();
        }
        BlackboxConfig config = BlackboxClient.CONFIG.get().copy();
        config.enabled = false;
        BlackboxClient.CONFIG.set(config);
    }

    static StepResult freshDrone(SelfTest.StepContext ctx) {
        if (!ctx.state().containsKey(FUTURE)) {
            PilotSession.INSTANCE.clearTestInput();
            ctx.state().put(FUTURE, ctx.onServer(BlackboxFlightFixture::spawnAndFly));
            return StepResult.pending();
        }
        CompletableFuture<?> future = (CompletableFuture<?>) ctx.state().get(FUTURE);
        if (!future.isDone()) {
            return StepResult.pending();
        }
        return ensurePiloting(ctx);
    }

    static void restoreConfig() {
        BlackboxConfig saved = savedConfig;
        if (saved != null) {
            BlackboxClient.CONFIG.set(saved);
            savedConfig = null;
        }
    }

    static StepResult ensurePiloting(SelfTest.StepContext ctx) {
        PilotSession session = PilotSession.INSTANCE;
        QuadcopterEntity drone = session.drone();
        if (drone != null && drone.getId() == droneId && session.isFpv() && session.sim() != null) {
            return StepResult.pass();
        }
        if (!ctx.state().containsKey(FUTURE)) {
            ctx.state().put(FUTURE, ctx.onServer(BlackboxFlightFixture::spawnAndFly));
            return StepResult.pending();
        }
        CompletableFuture<?> future = (CompletableFuture<?>) ctx.state().get(FUTURE);
        if (future.isCompletedExceptionally()) {
            return StepResult.fail("Drohne nicht gestartet: " + future.exceptionNow().getMessage());
        }
        return ctx.tick() > 300 ? StepResult.fail("keine FPV-Sitzung mit der Testdrohne") : StepResult.pending();
    }

    static StepResult fly(SelfTest.StepContext ctx, int ticks) {
        PilotSession session = PilotSession.INSTANCE;
        if (!ctx.state().containsKey(START)) {
            ctx.state().put(START, ctx.tick());
            session.setTestInput(new StickPositions(0.0F, 0.0F, 0.0F, 0.0F), true, StabMode.ANGLE);
            return StepResult.pending();
        }
        int t = ctx.tick() - (int) ctx.state().get(START);
        if (t < 10) {
            return StepResult.pending();
        }
        DroneSim sim = session.sim();
        Telemetry telemetry = sim == null ? null : sim.telemetry();
        float hover = telemetry == null ? 0.3F : (float) Math.max(0.15, Math.min(0.6, telemetry.hoverThrottleStick));
        if (t < 10 + ticks) {
            float throttle = t < 22 ? Math.min(1.0F, hover + 0.25F) : hover + 0.04F * (float) Math.sin(t * 0.4);
            float roll = (t / 10) % 2 == 0 ? 0.2F : -0.2F;
            session.setTestInput(new StickPositions(roll, 0.08F, 0.0F, throttle), true, StabMode.ANGLE);
            return StepResult.pending();
        }
        boolean grounded = telemetry != null && telemetry.grounded;
        if (!grounded && t < 10 + ticks + LANDING_TICKS) {
            session.setTestInput(new StickPositions(0.0F, 0.0F, 0.0F, hover * 0.8F), true, StabMode.ANGLE);
            return StepResult.pending();
        }
        session.setTestInput(new StickPositions(0.0F, 0.0F, 0.0F, 0.0F), false, StabMode.ANGLE);
        ctx.state().remove(START);
        return StepResult.pass();
    }

    static void spawnAndFly(MinecraftServer server) {
        List<ServerPlayer> players = server.getPlayerList().getPlayers();
        if (players.isEmpty()) {
            throw new IllegalStateException("kein Spieler auf dem Server");
        }
        ServerPlayer player = players.getFirst();
        DronePiloting.pilotedBy(player.getUUID()).ifPresent(DronePiloting::release);
        for (ServerLevel other : server.getAllLevels()) {
            if (droneId >= 0 && other.getEntity(droneId) instanceof QuadcopterEntity old) {
                old.discard();
            }
        }
        ServerLevel level = player.level();
        QuadcopterEntity drone = DroneModule.DRONE.get().create(level, EntitySpawnReason.COMMAND);
        if (drone == null) {
            throw new IllegalStateException("EntityType.create lieferte null");
        }
        Vec3 feet = player.position();
        drone.snapTo(feet.x, feet.y, feet.z + DRONE_DISTANCE, 0.0F, 0.0F);
        drone.setAttitude(new Quaternionf());
        drone.setBuild(DroneBuild.FREESTYLE);
        drone.setCustomName(Component.literal(CRAFT_NAME));
        if (!level.addFreshEntity(drone)) {
            throw new IllegalStateException("addFreshEntity fehlgeschlagen");
        }
        droneId = drone.getId();
        ItemStack goggles = ContentIds.item(ContentIds.GOGGLES_CREATIVE).map(ItemStack::new).orElse(ItemStack.EMPTY);
        if (goggles.isEmpty()) {
            throw new IllegalStateException("Kreativ-Brille nicht registriert");
        }
        goggles.set(CoreComponents.BOUND_DRONE.get(), DroneInteractions.binding(drone));
        previousHead = player.getItemBySlot(EquipmentSlot.HEAD).copy();
        player.setItemSlot(EquipmentSlot.HEAD, goggles);
        ItemStack remote = new ItemStack(DroneModule.REMOTE.get());
        remote.set(CoreComponents.BOUND_DRONE.get(), DroneInteractions.binding(drone));
        previousHand = player.getMainHandItem().copy();
        player.setItemSlot(EquipmentSlot.MAINHAND, remote);
        DronePiloting.StartResult result = DronePiloting.tryStart(player, drone, PilotMode.FPV);
        if (result != DronePiloting.StartResult.STARTED) {
            throw new IllegalStateException("FPV-Start verweigert: " + result);
        }
    }

    static void releaseDrone(MinecraftServer server) {
        List<ServerPlayer> players = server.getPlayerList().getPlayers();
        if (!players.isEmpty()) {
            ServerPlayer player = players.getFirst();
            DronePiloting.pilotedBy(player.getUUID()).ifPresent(DronePiloting::release);
            for (ServerLevel level : server.getAllLevels()) {
                if (level.getEntity(droneId) instanceof QuadcopterEntity drone) {
                    drone.discard();
                }
            }
            ItemStack head = previousHead;
            if (head != null) {
                player.setItemSlot(EquipmentSlot.HEAD, head);
            }
            ItemStack hand = previousHand;
            if (hand != null) {
                player.setItemSlot(EquipmentSlot.MAINHAND, hand);
            }
        }
        previousHead = null;
        previousHand = null;
        droneId = -1;
    }

    static StepResult cleanup(SelfTest.StepContext ctx) {
        if (ctx.tick() == 0) {
            Minecraft minecraft = Minecraft.getInstance();
            if (minecraft.gui.screen() != null) {
                minecraft.gui.setScreen(null);
            }
            PilotSession.INSTANCE.clearTestInput();
            restoreConfig();
            ctx.state().put(FUTURE, ctx.onServer(BlackboxFlightFixture::releaseDrone));
            return StepResult.pending();
        }
        CompletableFuture<?> future = (CompletableFuture<?>) ctx.state().get(FUTURE);
        if (!future.isDone()) {
            return StepResult.pending();
        }
        if (future.isCompletedExceptionally()) {
            return StepResult.fail("Aufräumen fehlgeschlagen: " + future.exceptionNow().getMessage());
        }
        return PilotSession.INSTANCE.isActive() ? (ctx.tick() > 150 ? StepResult.fail("Sitzung endet nicht") : StepResult.pending()) : StepResult.pass();
    }
}
