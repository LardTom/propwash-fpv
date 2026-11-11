package de.lardtom.propwash.stats.client;

import de.lardtom.propwash.build.DroneBuild;
import de.lardtom.propwash.content.ContentIds;
import de.lardtom.propwash.core.CoreComponents;
import de.lardtom.propwash.dev.SelfTest;
import de.lardtom.propwash.dev.StatsJoinCheck;
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
import de.lardtom.propwash.stats.PilotStats;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.joml.Vector3d;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;

final class StatsFlight {
    static final int ARM_TICKS = 20;
    static final int TIMEOUT_TICKS = 400;
    private static final String PHASE = "stats_phase";
    private static final String SINCE = "stats_since";
    private static final String FUTURE = "stats_future";
    private static final String RESULT = "stats_result";
    private static volatile int droneId = -1;
    private static volatile double groundY;
    private static volatile @Nullable ItemStack previousHead;
    private static volatile @Nullable ItemStack previousHand;

    record Snapshot(int drone, Map<String, Integer> values) {
    }

    private StatsFlight() {
    }

    static ServerPlayer player(MinecraftServer server) {
        List<ServerPlayer> players = server.getPlayerList().getPlayers();
        if (players.isEmpty()) {
            throw new IllegalStateException("kein Spieler auf dem Server");
        }
        return players.getFirst();
    }

    static Map<String, Integer> values(ServerPlayer player) {
        return StatsJoinCheck.values(player);
    }

    static Map<String, Integer> delta(Map<String, Integer> before, Map<String, Integer> after) {
        return StatsJoinCheck.delta(before, after);
    }

    static void ground(double y) {
        groundY = y;
    }

    static String phase(SelfTest.StepContext ctx, String initial) {
        Map<String, Object> state = ctx.state();
        if (!state.containsKey(PHASE)) {
            state.put(PHASE, initial);
            state.put(SINCE, ctx.tick());
        }
        return (String) state.get(PHASE);
    }

    static StepResult next(SelfTest.StepContext ctx, String phase) {
        ctx.state().put(PHASE, phase);
        ctx.state().put(SINCE, ctx.tick());
        return StepResult.pending();
    }

    static int since(SelfTest.StepContext ctx) {
        return ctx.tick() - (int) ctx.state().get(SINCE);
    }

    static <T> void ask(SelfTest.StepContext ctx, String key, Function<MinecraftServer, T> action) {
        AtomicReference<T> result = new AtomicReference<>();
        CompletableFuture<Void> future = ctx.onServer(server -> result.set(action.apply(server)));
        ctx.state().put(key, new Asked<>(future, result));
    }

    @SuppressWarnings("unchecked")
    static <T> @Nullable Answer<T> answer(SelfTest.StepContext ctx, String key) {
        Asked<T> asked = (Asked<T>) ctx.state().get(key);
        if (asked == null || !asked.future().isDone()) {
            return null;
        }
        if (asked.future().isCompletedExceptionally()) {
            return new Answer<>(null, String.valueOf(asked.future().exceptionNow()));
        }
        return new Answer<>(asked.result().get(), null);
    }

    record Asked<T>(CompletableFuture<Void> future, AtomicReference<T> result) {
    }

    record Answer<T>(@Nullable T value, @Nullable String error) {
    }

    static Snapshot spawn(MinecraftServer server, float charge, double dx, double dz) {
        ServerPlayer player = player(server);
        DronePiloting.pilotedBy(player.getUUID()).ifPresent(DronePiloting::release);
        discard(server);
        ServerLevel level = player.level();
        QuadcopterEntity drone = DroneModule.DRONE.get().create(level, EntitySpawnReason.COMMAND);
        if (drone == null) {
            throw new IllegalStateException("Drohne nicht erzeugt");
        }
        Vec3 feet = player.position();
        groundY = feet.y;
        drone.snapTo(feet.x + dx, feet.y + 0.1, feet.z + dz, 180.0F, 0.0F);
        drone.setAttitude(new Quaternionf());
        drone.setBuild(DroneBuild.FREESTYLE);
        drone.setBatteryCharge(charge);
        drone.setCustomName(Component.literal("Statistik"));
        if (!level.addFreshEntity(drone)) {
            throw new IllegalStateException("addFreshEntity fehlgeschlagen");
        }
        droneId = drone.getId();
        if (previousHead == null) {
            previousHead = player.getItemBySlot(EquipmentSlot.HEAD).copy();
        }
        if (previousHand == null) {
            previousHand = player.getMainHandItem().copy();
        }
        ItemStack goggles = ContentIds.item(ContentIds.GOGGLES_CREATIVE).map(ItemStack::new).orElse(ItemStack.EMPTY);
        if (goggles.isEmpty()) {
            throw new IllegalStateException("Kreativ-Brille nicht registriert");
        }
        goggles.set(CoreComponents.BOUND_DRONE.get(), DroneInteractions.binding(drone));
        player.setItemSlot(EquipmentSlot.HEAD, goggles);
        ItemStack remote = new ItemStack(DroneModule.REMOTE.get());
        remote.set(CoreComponents.BOUND_DRONE.get(), DroneInteractions.binding(drone));
        player.setItemSlot(EquipmentSlot.MAINHAND, remote);
        Map<String, Integer> before = values(player);
        DronePiloting.StartResult result = DronePiloting.tryStart(player, drone, PilotMode.FPV);
        if (result != DronePiloting.StartResult.STARTED) {
            throw new IllegalStateException("FPV-Start verweigert: " + result);
        }
        if (!PilotStats.tracking(drone)) {
            throw new IllegalStateException("Server führt für den neuen Piloten keine Zählung");
        }
        return new Snapshot(drone.getId(), before);
    }

    static @Nullable QuadcopterEntity serverDrone(MinecraftServer server) {
        int id = droneId;
        if (id < 0) {
            return null;
        }
        return player(server).level().getEntity(id) instanceof QuadcopterEntity drone ? drone : null;
    }

    static void discard(MinecraftServer server) {
        int id = droneId;
        if (id < 0) {
            return;
        }
        for (ServerLevel level : server.getAllLevels()) {
            Entity entity = level.getEntity(id);
            if (entity instanceof QuadcopterEntity drone) {
                drone.discard();
            }
        }
        droneId = -1;
    }

    static void release(MinecraftServer server) {
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
        discard(server);
        previousHead = null;
        previousHand = null;
    }

    static boolean flying() {
        PilotSession session = PilotSession.INSTANCE;
        QuadcopterEntity drone = session.drone();
        return session.isActive() && session.sim() != null && drone != null && drone.getId() == droneId;
    }

    static @Nullable Telemetry telemetry() {
        DroneSim sim = PilotSession.INSTANCE.sim();
        return sim == null ? null : sim.telemetry();
    }

    static double height() {
        QuadcopterEntity drone = PilotSession.INSTANCE.drone();
        return drone == null ? 0.0 : drone.getY() - groundY;
    }

    static double speed() {
        DroneSim sim = PilotSession.INSTANCE.sim();
        if (sim == null) {
            return 0.0;
        }
        Vector3d velocity = new Vector3d();
        sim.getVelocity(velocity);
        return velocity.isFinite() ? velocity.length() : 0.0;
    }

    static void arm(int since) {
        PilotSession.INSTANCE.setTestInput(new StickPositions(0.0F, 0.0F, 0.0F, 0.0F), since >= ARM_TICKS / 2, StabMode.ANGLE);
    }

    static void disarm() {
        PilotSession.INSTANCE.setTestInput(new StickPositions(0.0F, 0.0F, 0.0F, 0.0F), false, StabMode.ANGLE);
    }

    static void fly(double targetHeight, float pitch) {
        Telemetry t = telemetry();
        double hover = t == null ? 0.3 : Math.clamp(t.hoverThrottleStick, 0.15, 0.6);
        double vs = t == null ? 0.0 : t.verticalSpeed;
        double wanted = Math.clamp(1.2 * (targetHeight - height()), -3.0, 3.0);
        float throttle = (float) Math.clamp(hover + 0.06 * (wanted - vs) + Math.abs(pitch) * 0.15, 0.05, 0.95);
        PilotSession.INSTANCE.setTestInput(new StickPositions(0.0F, pitch, 0.0F, throttle), true, StabMode.ANGLE);
    }

    static boolean armed() {
        Telemetry t = telemetry();
        return t != null && t.armed;
    }

    static StepResult start(SelfTest.StepContext ctx, float charge, double dx, double dz) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.gui.screen() != null) {
            mc.gui.setScreen(null);
        }
        ask(ctx, FUTURE, server -> spawn(server, charge, dx, dz));
        return next(ctx, "fpv");
    }

    static @Nullable StepResult awaitFpv(SelfTest.StepContext ctx, String then) {
        Answer<Snapshot> answer = answer(ctx, FUTURE);
        if (answer != null && answer.error() != null) {
            return finish(ctx, StepResult.fail("Start: " + answer.error()));
        }
        if (answer == null || !flying()) {
            return since(ctx) > TIMEOUT_TICKS ? finish(ctx, StepResult.fail("keine FPV-Sitzung")) : StepResult.pending();
        }
        ctx.state().put("snapshot", answer.value());
        return next(ctx, then);
    }

    static Snapshot snapshot(SelfTest.StepContext ctx) {
        return (Snapshot) ctx.state().get("snapshot");
    }

    static StepResult finish(SelfTest.StepContext ctx, StepResult result) {
        Map<String, Object> state = ctx.state();
        if (!state.containsKey(RESULT)) {
            state.put(RESULT, result);
            PilotSession.INSTANCE.clearTestInput();
            ask(ctx, "stats_release", server -> {
                release(server);
                return Boolean.TRUE;
            });
            return next(ctx, "cleanup");
        }
        return StepResult.pending();
    }

    static StepResult awaitCleanup(SelfTest.StepContext ctx) {
        Answer<Boolean> answer = answer(ctx, "stats_release");
        if (answer == null || PilotSession.INSTANCE.isActive()) {
            return since(ctx) > 200 ? StepResult.fail("Aufräumen hängt: FPV " + PilotSession.INSTANCE.isActive()) : StepResult.pending();
        }
        return (StepResult) ctx.state().get(RESULT);
    }
}
