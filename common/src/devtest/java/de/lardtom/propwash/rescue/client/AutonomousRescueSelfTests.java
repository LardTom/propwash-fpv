package de.lardtom.propwash.rescue.client;

import com.mojang.blaze3d.platform.InputConstants;
import de.lardtom.propwash.build.DroneBuild;
import de.lardtom.propwash.core.CoreComponents;
import de.lardtom.propwash.dev.SelfTest;
import de.lardtom.propwash.dev.SelfTest.StepResult;
import de.lardtom.propwash.dev.client.Screenshots;
import de.lardtom.propwash.drone.DroneInteractions;
import de.lardtom.propwash.drone.DroneModule;
import de.lardtom.propwash.drone.DronePiloting;
import de.lardtom.propwash.drone.PilotMode;
import de.lardtom.propwash.drone.QuadcopterEntity;
import de.lardtom.propwash.drone.client.PilotSession;
import de.lardtom.propwash.input.StickPositions;
import de.lardtom.propwash.rescue.AutonomousReturns;
import de.lardtom.propwash.rescue.RescuePhase;
import de.lardtom.propwash.rescue.ReturnPlan;
import de.lardtom.propwash.server.RangeManager;
import de.lardtom.propwash.server.WorldRules;
import de.lardtom.propwash.server.rules.WorldRuleSpecs;
import de.lardtom.propwash.sim.DroneSim;
import de.lardtom.propwash.sim.StabMode;
import de.lardtom.propwash.sim.Telemetry;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.game.ClientboundSetHeldSlotPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.level.storage.TagValueOutput;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.joml.Vector3d;
import org.jspecify.annotations.Nullable;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

final class AutonomousRescueSelfTests {
    private static final int ARENA_BACK = 8;
    private static final int ARENA_FRONT = 44;
    private static final int ARENA_SIDE = 14;
    private static final int ARENA_HEIGHT = 26;
    private static final int START_DX = 30;
    private static final int HALL_FROM = 24;
    private static final int HALL_TO = 36;
    private static final int HALL_SIDE = 6;
    private static final int HALL_ROOF = 6;
    private static final int DOOR_HALF = 1;
    private static final int DOOR_HEIGHT = 3;
    private static final double PLAYER_DX = -4.5;
    private static final double TAKEOFF_HEIGHT = 2.5;
    private static final double LANDING_TOLERANCE = 1.5;
    private static final double GROUND_TOLERANCE = 0.25;
    private static final double FAR_AWAY = 320.0;
    private static final int SNAP_EVERY = 2;
    private static final String PHASE = "phase";
    private static final String SINCE = "since";
    private static final String FUTURE = "future";
    private static final String TAG = "tag";
    private static final int RULE_CHECK = 10;
    private static final int RESCUE_HOLD_TICKS = 12;
    private static final AtomicReference<int[]> ARENA = new AtomicReference<>();
    private static final AtomicReference<UUID> DRONE = new AtomicReference<>();
    private static final AtomicReference<Snap> SNAP = new AtomicReference<>();
    private static final AtomicReference<double[]> PLAYER_HOME = new AtomicReference<>();
    private static final AtomicInteger RELOAD_AT = new AtomicInteger();
    private static final Map<BlockPos, BlockState> ORIGINAL = new HashMap<>();
    private static final Flight FLIGHT = new Flight();

    private record Snap(long time, boolean present, double x, double y, double z, boolean armed, boolean planned, boolean flying,
                        boolean reported, boolean piloted, @Nullable RescuePhase phase, int ticks, String note) {
    }

    private static final class Flight {
        double minX = Double.POSITIVE_INFINITY;
        double maxY = Double.NEGATIVE_INFINITY;
        boolean doorPassed;
        int autonomousTicks;
        long lastSnap = Long.MIN_VALUE;
        double lastX;
        double lastZ;
        double travelled;

        void reset() {
            this.minX = Double.POSITIVE_INFINITY;
            this.maxY = Double.NEGATIVE_INFINITY;
            this.doorPassed = false;
            this.autonomousTicks = 0;
            this.lastSnap = Long.MIN_VALUE;
            this.travelled = 0.0;
        }
    }

    private enum Leave {
        USE_REMOTE,
        DROP_REMOTE
    }

    private AutonomousRescueSelfTests() {
    }

    static void register() {
        SelfTest.clientStep("rescue: ohne Pilot – Testfeld, Drohne und Funke aufbauen", 300, AutonomousRescueSelfTests::setupStep);
        SelfTest.clientStep("rescue: ohne Pilot – Heimflug läuft, Funke benutzt: Server fliegt weiter", 900,
                ctx -> takeOverStep(ctx, false, Leave.USE_REMOTE, true));
        if (Screenshots.enabled()) {
            Screenshots.step("bildschirmfotos: Heimflug ohne Pilot – Drohne landet allein am Homepoint", "rescue_autonomous_landing", 2,
                    new LandingScene());
        }
        SelfTest.clientStep("rescue: ohne Pilot – landet allein am Homepoint", 1600, ctx -> landingStep(ctx, false));
        SelfTest.clientStep("rescue: ohne Pilot – Welt-Regel aus: Motoren gehen aus wie bisher", 900, AutonomousRescueSelfTests::ruleOffStep);
        SelfTest.clientStep("rescue: ohne Pilot – in der Halle, Funke abgelegt: Server fliegt weiter", 900,
                ctx -> takeOverStep(ctx, true, Leave.DROP_REMOTE, true));
        if (Screenshots.enabled()) {
            Screenshots.step("bildschirmfotos: Heimflug ohne Pilot – Drohne fliegt durchs Tor aus der Halle", "rescue_autonomous_door", 1,
                    new DoorScene());
        }
        SelfTest.clientStep("rescue: ohne Pilot – fliegt durchs Tor aus der Halle und landet am Homepoint", 2400, ctx -> landingStep(ctx, true));
        SelfTest.clientStep("rescue: ohne Pilot – Heimflug läuft, Funke benutzt: vor dem Entladen", 900,
                ctx -> takeOverStep(ctx, false, Leave.USE_REMOTE, false));
        SelfTest.clientStep("rescue: ohne Pilot – Chunk entladen und neu geladen, Spieler weit weg: fliegt weiter und landet", 2400,
                AutonomousRescueSelfTests::unloadStep);
        SelfTest.clientStep("rescue: ohne Pilot – Aufräumen", 200, AutonomousRescueSelfTests::cleanupStep);
    }

    private static ServerPlayer serverPlayer(MinecraftServer server) {
        if (server.getPlayerList().getPlayers().isEmpty()) {
            throw new IllegalStateException("kein Spieler auf dem Server");
        }
        return server.getPlayerList().getPlayers().getFirst();
    }

    private static @Nullable QuadcopterEntity serverDrone(MinecraftServer server) {
        UUID id = DRONE.get();
        if (id == null) {
            return null;
        }
        return serverPlayer(server).level().getEntity(id) instanceof QuadcopterEntity drone && !drone.isRemoved() ? drone : null;
    }

    private static int phase(SelfTest.StepContext ctx) {
        return (int) ctx.state().getOrDefault(PHASE, 0);
    }

    private static void next(SelfTest.StepContext ctx, int phase) {
        ctx.state().put(PHASE, phase);
        ctx.state().put(SINCE, ctx.tick());
        ctx.state().remove(FUTURE);
    }

    private static void pressRescue() {
        KeyMapping.click(InputConstants.getKey(RescueKeys.RESCUE.saveString()));
    }

    private static int since(SelfTest.StepContext ctx) {
        return ctx.tick() - (int) ctx.state().getOrDefault(SINCE, 0);
    }

    private static @Nullable StepResult server(SelfTest.StepContext ctx, String what, Consumer<MinecraftServer> action) {
        CompletableFuture<?> future = (CompletableFuture<?>) ctx.state().get(FUTURE);
        if (future == null) {
            ctx.state().put(FUTURE, ctx.onServer(action));
            return StepResult.pending();
        }
        if (future.isCompletedExceptionally()) {
            return StepResult.fail(what + ": " + future.exceptionNow().getMessage());
        }
        return future.isDone() ? null : StepResult.pending();
    }

    private static Vec3 home(int[] arena) {
        return new Vec3(arena[0] + 0.5, arena[1], arena[2] + 0.5);
    }

    private static Vec3 start(int[] arena) {
        return new Vec3(arena[0] + START_DX + 0.5, arena[1], arena[2] + 0.5);
    }

    private static Vec3 viewpoint(int[] arena) {
        return new Vec3(arena[0] + PLAYER_DX, arena[1], arena[2] + 0.5);
    }

    private static void set(ServerLevel level, BlockPos pos, BlockState state) {
        BlockState old = level.getBlockState(pos);
        if (old.equals(state)) {
            return;
        }
        ORIGINAL.putIfAbsent(pos.immutable(), old);
        level.setBlock(pos, state, Block.UPDATE_CLIENTS);
    }

    private static void buildHall(ServerLevel level, int[] arena, boolean present) {
        for (int dx = HALL_FROM; dx <= HALL_TO; dx++) {
            for (int dz = -HALL_SIDE; dz <= HALL_SIDE; dz++) {
                for (int dy = 0; dy <= HALL_ROOF; dy++) {
                    boolean shell = dx == HALL_FROM || dx == HALL_TO || Math.abs(dz) == HALL_SIDE || dy == HALL_ROOF;
                    boolean door = dx == HALL_FROM && Math.abs(dz) <= DOOR_HALF && dy < DOOR_HEIGHT;
                    boolean lamp = present && dy == HALL_ROOF - 1 && (dx - HALL_FROM) % 4 == 2 && Math.abs(dz) % 4 == 2;
                    BlockState state = present && shell && !door ? Blocks.SPRUCE_PLANKS.defaultBlockState()
                            : lamp ? Blocks.LIGHT.defaultBlockState() : Blocks.AIR.defaultBlockState();
                    set(level, new BlockPos(arena[0] + dx, arena[1] + dy, arena[2] + dz), state);
                }
            }
        }
    }

    private static StepResult setupStep(SelfTest.StepContext ctx) {
        Minecraft mc = Minecraft.getInstance();
        if (ctx.tick() == 0) {
            if (mc.gui.screen() != null) {
                mc.gui.setScreen(null);
            }
            FLIGHT.reset();
            SNAP.set(null);
            ctx.state().put(FUTURE, ctx.onServer(server -> {
                ServerPlayer player = serverPlayer(server);
                ServerLevel level = player.level();
                PLAYER_HOME.set(new double[]{player.getX(), player.getY(), player.getZ(), player.getYRot(), player.getXRot()});
                int[] arena = {player.getBlockX() + 12, player.getBlockY(), player.getBlockZ()};
                ARENA.set(arena);
                for (int dx = -ARENA_BACK; dx <= ARENA_FRONT; dx++) {
                    for (int dz = -ARENA_SIDE; dz <= ARENA_SIDE; dz++) {
                        level.getChunk((arena[0] + dx) >> 4, (arena[2] + dz) >> 4);
                        set(level, new BlockPos(arena[0] + dx, arena[1] - 1, arena[2] + dz), Blocks.SMOOTH_STONE.defaultBlockState());
                        for (int dy = 0; dy < ARENA_HEIGHT; dy++) {
                            set(level, new BlockPos(arena[0] + dx, arena[1] + dy, arena[2] + dz), Blocks.AIR.defaultBlockState());
                        }
                    }
                }
                set(level, new BlockPos(arena[0], arena[1] - 1, arena[2]), Blocks.CONCRETE.pick(DyeColor.LIME).defaultBlockState());
                QuadcopterEntity drone = DroneModule.DRONE.get().create(level, EntitySpawnReason.COMMAND);
                if (drone == null) {
                    throw new IllegalStateException("Drohne nicht erzeugt");
                }
                ItemStack stack = new ItemStack(DroneModule.DRONE_ITEM.get());
                stack.set(CoreComponents.DRONE_BUILD.get(), DroneBuild.FREESTYLE);
                drone.loadFromStack(stack);
                Vec3 pos = start(arena);
                drone.snapTo(pos.x, pos.y, pos.z, 90.0F, 0.0F);
                level.addFreshEntity(drone);
                DRONE.set(drone.getUUID());
                player.setItemSlot(EquipmentSlot.HEAD, ItemStack.EMPTY);
                player.getAbilities().flying = false;
                player.onUpdateAbilities();
            }));
            return StepResult.pending();
        }
        StepResult wait = server(ctx, "Testfeld", server -> {
        });
        if (wait != null && wait.status() == StepResult.Status.FAIL) {
            return wait;
        }
        CompletableFuture<?> future = (CompletableFuture<?>) ctx.state().get(FUTURE);
        if (!future.isDone()) {
            return StepResult.pending();
        }
        UUID id = DRONE.get();
        boolean seen = mc.level != null && id != null && mc.level.getEntity(id) instanceof QuadcopterEntity;
        return seen ? StepResult.pass() : ctx.tick() > 200 ? StepResult.fail("Drohne kommt nicht beim Client an") : StepResult.pending();
    }

    private static void poll(SelfTest.StepContext ctx) {
        if (ctx.tick() % SNAP_EVERY != 0) {
            return;
        }
        ctx.onServer(server -> {
            QuadcopterEntity drone = serverDrone(server);
            long time = server.overworld().getGameTime();
            if (drone == null) {
                ServerPlayer player = serverPlayer(server);
                UUID id = DRONE.get();
                Entity any = id == null ? null : player.level().getEntity(id);
                int[] arena = ARENA.get();
                int near = arena == null ? -1 : player.level().getEntitiesOfClass(QuadcopterEntity.class,
                        new AABB(arena[0] - 20, arena[1] - 10, arena[2] - 30, arena[0] + 60, arena[1] + 40, arena[2] + 30)).size();
                String note = "Entity " + (any == null ? "fehlt" : any.getClass().getSimpleName() + " entfernt " + any.getRemovalReason())
                        + ", Drohnen im Feld " + near + ", Spieler " + player.blockPosition().toShortString()
                        + ", Flüge " + AutonomousReturns.flights() + ", Tickets " + RangeManager.ticketCount();
                SNAP.set(new Snap(time, false, 0, 0, 0, false, false, false, false, false, null, 0, note));
                return;
            }
            ReturnPlan plan = drone.returnPlan();
            SNAP.set(new Snap(time, true, drone.getX(), drone.getY(), drone.getZ(), drone.isArmed(), plan != null,
                    AutonomousReturns.flying(drone), AutonomousReturns.reported(drone), drone.pilot().isPresent(),
                    AutonomousReturns.phase(drone), plan == null ? 0 : plan.ticks(), ""));
        });
    }

    private static @Nullable Snap fresh(SelfTest.StepContext ctx) {
        Snap snap = SNAP.get();
        Minecraft mc = Minecraft.getInstance();
        if (snap == null || mc.level == null) {
            return null;
        }
        return snap;
    }

    private static void track(Snap snap, int[] arena) {
        if (snap.time() == FLIGHT.lastSnap || !snap.present()) {
            return;
        }
        if (FLIGHT.lastSnap != Long.MIN_VALUE) {
            FLIGHT.travelled += Math.hypot(snap.x() - FLIGHT.lastX, snap.z() - FLIGHT.lastZ);
        }
        FLIGHT.lastSnap = snap.time();
        FLIGHT.lastX = snap.x();
        FLIGHT.lastZ = snap.z();
        FLIGHT.minX = Math.min(FLIGHT.minX, snap.x());
        FLIGHT.maxY = Math.max(FLIGHT.maxY, snap.y());
        if (snap.flying()) {
            FLIGHT.autonomousTicks++;
        }
        double inDoor = snap.x() - arena[0];
        if (inDoor < HALL_FROM + 0.5 && inDoor > HALL_FROM - 1.5 && snap.y() < arena[1] + DOOR_HEIGHT && Math.abs(snap.z() - arena[2] - 0.5) < 2.0) {
            FLIGHT.doorPassed = true;
        }
    }

    private static String describe(@Nullable Snap snap, int[] arena) {
        if (snap == null) {
            return "kein Serverstand";
        }
        if (!snap.present()) {
            return "Drohne nicht auf dem Server: " + snap.note();
        }
        Vec3 home = home(arena);
        return String.format(Locale.ROOT, "Pos (%.2f, %.2f, %.2f) Abstand %.2f m, Höhe %.2f, scharf %s, Plan %s, fliegt %s, Phase %s, Pilot %s, Ticks %d",
                snap.x(), snap.y(), snap.z(), Math.hypot(snap.x() - home.x, snap.z() - home.z), snap.y() - home.y, snap.armed(),
                snap.planned(), snap.flying(), snap.phase(), snap.piloted(), snap.ticks());
    }

    private static StepResult takeOverStep(SelfTest.StepContext ctx, boolean hall, Leave leave, boolean expectAutonomous) {
        return takeOverStep(ctx, hall, leave, expectAutonomous, true);
    }

    private static StepResult takeOverStep(SelfTest.StepContext ctx, boolean hall, Leave leave, boolean expectAutonomous, boolean ruleOn) {
        Minecraft mc = Minecraft.getInstance();
        int[] arena = ARENA.get();
        PilotSession session = PilotSession.INSTANCE;
        if (arena == null || DRONE.get() == null) {
            return StepResult.fail("kein Testfeld");
        }
        poll(ctx);
        switch (phase(ctx)) {
            case 0 -> {
                if (!ctx.state().containsKey(FUTURE) && session.isActive()) {
                    if (!ctx.state().containsKey("released")) {
                        ctx.state().put("released", ctx.onServer(server -> {
                            QuadcopterEntity drone = serverDrone(server);
                            if (drone != null) {
                                DronePiloting.release(drone);
                            }
                        }));
                    }
                    return since(ctx) > 100 ? StepResult.fail("alte Sitzung endet nicht") : StepResult.pending();
                }
                StepResult wait = server(ctx, "Start", server -> {
                    QuadcopterEntity drone = serverDrone(server);
                    if (drone == null) {
                        throw new IllegalStateException("Drohne fehlt");
                    }
                    if (drone.pilot().isPresent()) {
                        DronePiloting.release(drone);
                    }
                    ServerPlayer player = serverPlayer(server);
                    ServerLevel level = player.level();
                    WorldRules.set(server, WorldRuleSpecs.AUTONOMOUS_RESCUE, ruleOn ? 1 : 0);
                    buildHall(level, arena, hall);
                    drone.setReturnPlan(null);
                    Vec3 pos = start(arena);
                    drone.resetTo(pos, new Quaternionf().rotationY((float) Math.toRadians(-90.0) + (float) Math.PI));
                    drone.setBatteryCharge(1.0F);
                    Vec3 view = viewpoint(arena);
                    player.teleportTo(level, view.x, view.y, view.z, Set.of(), -90.0F, 0.0F, true);
                    ItemStack remote = new ItemStack(DroneModule.REMOTE.get());
                    remote.set(CoreComponents.BOUND_DRONE.get(), DroneInteractions.binding(drone));
                    player.getInventory().setItem(0, remote);
                    player.getInventory().setSelectedSlot(0);
                    player.connection.send(new ClientboundSetHeldSlotPacket(0));
                    player.setOnGround(true);
                    DronePiloting.StartResult result = DronePiloting.tryStart(player, drone, PilotMode.LINE_OF_SIGHT);
                    if (result != DronePiloting.StartResult.STARTED) {
                        throw new IllegalStateException("Start verweigert: " + result);
                    }
                });
                if (wait != null) {
                    return wait;
                }
                QuadcopterEntity drone = session.drone();
                if (!session.isActive() || drone == null || !drone.getUUID().equals(DRONE.get())) {
                    return since(ctx) > 200 ? StepResult.fail("keine Sitzung am Client") : StepResult.pending();
                }
                FLIGHT.reset();
                next(ctx, 1);
                return StepResult.pending();
            }
            case 1 -> {
                DroneSim sim = session.sim();
                if (sim == null) {
                    return StepResult.fail("Sitzung beim Abheben verloren");
                }
                Telemetry t = sim.telemetry();
                int s = since(ctx);
                if (s < 10) {
                    session.setTestInput(new StickPositions(0.0F, 0.0F, 0.0F, 0.0F), false, StabMode.ANGLE);
                    return StepResult.pending();
                }
                if (s < 30) {
                    session.setTestInput(new StickPositions(0.0F, 0.0F, 0.0F, 0.0F), true, StabMode.ANGLE);
                    if (s == 29 && !t.armed) {
                        return StepResult.fail("nicht scharf: Sperren " + t.armBlockers);
                    }
                    return StepResult.pending();
                }
                double height = session.comPosition(new Vector3d()).y - arena[1];
                double hover = Math.clamp(t.hoverThrottleStick, 0.2, 0.6);
                float throttle = (float) Math.clamp(hover + 0.06 * (TAKEOFF_HEIGHT + 0.3 - height) - 0.05 * t.verticalSpeed, 0.05, 0.8);
                session.setTestInput(new StickPositions(0.0F, 0.0F, 0.0F, throttle), true, StabMode.ANGLE);
                if (!t.armed) {
                    return StepResult.fail("beim Abheben entschärft: " + t.lastDisarmReason);
                }
                if (height < TAKEOFF_HEIGHT || Math.abs(t.verticalSpeed) > 0.6) {
                    return s > 400 ? StepResult.fail("hebt nicht ab, Höhe " + height) : StepResult.pending();
                }
                if (mc.level == null) {
                    return StepResult.fail("keine Welt");
                }
                RescueController.INSTANCE.setHome(home(arena), mc.level.dimension(), true);
                RescueKeys.RESCUE.setDown(true);
                pressRescue();
                next(ctx, 5);
                return StepResult.pending();
            }
            case 5 -> {
                if (since(ctx) < RESCUE_HOLD_TICKS) {
                    pressRescue();
                    return StepResult.pending();
                }
                RescueKeys.RESCUE.setDown(false);
                next(ctx, 2);
                return StepResult.pending();
            }
            case 2 -> {
                if (!RescueState.INSTANCE.active()) {
                    DroneSim sim = session.sim();
                    String why = sim == null ? "keine Sitzung" : "scharf " + sim.telemetry().armed + ", Höhe "
                            + String.format(Locale.ROOT, "%.2f", session.comPosition(new Vector3d()).y - arena[1]);
                    return since(ctx) > 60 ? StepResult.fail("GPS Rescue startet nicht: " + why) : StepResult.pending();
                }
                Snap snap = fresh(ctx);
                if (snap == null || !snap.reported() || since(ctx) < 30) {
                    return since(ctx) > 200 ? StepResult.fail("Server kennt den Heimflug nicht: " + describe(snap, arena)) : StepResult.pending();
                }
                ctx.log("Heimflug am Client: " + RescueState.INSTANCE.phase() + ", " + describe(snap, arena));
                next(ctx, 3);
                return StepResult.pending();
            }
            case 3 -> {
                StepResult wait = server(ctx, "Aussteigen", server -> {
                    ServerPlayer player = serverPlayer(server);
                    if (leave == Leave.USE_REMOTE) {
                        DronePiloting.useRemote(player, player.getMainHandItem());
                    } else {
                        player.getInventory().setItem(0, ItemStack.EMPTY);
                    }
                });
                if (wait != null) {
                    return wait;
                }
                next(ctx, 4);
                return StepResult.pending();
            }
            default -> {
                Snap snap = fresh(ctx);
                if (snap != null) {
                    track(snap, arena);
                }
                boolean released = snap != null && !snap.piloted() && !session.isActive();
                if (!released) {
                    return since(ctx) > 80 ? StepResult.fail("Pilot nicht gelöst: " + describe(snap, arena)) : StepResult.pending();
                }
                if (!expectAutonomous) {
                    return StepResult.pass();
                }
                if (!snap.planned() || !snap.flying() || !snap.armed()) {
                    return since(ctx) > 60 ? StepResult.fail("Server fliegt nicht weiter: " + describe(snap, arena)) : StepResult.pending();
                }
                ctx.log("Server fliegt: " + describe(snap, arena));
                return StepResult.pass();
            }
        }
    }

    private static StepResult landingStep(SelfTest.StepContext ctx, boolean hall) {
        int[] arena = ARENA.get();
        if (arena == null) {
            return StepResult.fail("kein Testfeld");
        }
        poll(ctx);
        Snap snap = fresh(ctx);
        if (snap == null) {
            return StepResult.pending();
        }
        track(snap, arena);
        if (!snap.present()) {
            return StepResult.fail("Drohne verschwunden");
        }
        if (snap.piloted()) {
            return StepResult.fail("Drohne hat wieder einen Piloten: " + describe(snap, arena));
        }
        if (snap.planned() || snap.armed()) {
            return ctx.tick() > (hall ? 2300 : 1500) ? StepResult.fail("landet nicht: " + describe(snap, arena)) : StepResult.pending();
        }
        Vec3 home = home(arena);
        double distance = Math.hypot(snap.x() - home.x, snap.z() - home.z);
        String info = describe(snap, arena) + String.format(Locale.ROOT, ", geflogen %.1f m, höchste Höhe %.1f, Ticks ohne Pilot %d",
                FLIGHT.travelled, FLIGHT.maxY - arena[1], FLIGHT.autonomousTicks);
        if (distance > LANDING_TOLERANCE || Math.abs(snap.y() - home.y) > GROUND_TOLERANCE) {
            return StepResult.fail("nicht am Homepoint gelandet: " + info);
        }
        if (FLIGHT.travelled < START_DX - 6.0) {
            return StepResult.fail("zu kurzer Flug ohne Pilot: " + info);
        }
        if (hall && !FLIGHT.doorPassed) {
            return StepResult.fail("nicht durchs Tor geflogen: " + info);
        }
        if (!hall && FLIGHT.maxY - arena[1] < 8.0) {
            return StepResult.fail("nicht auf Rückflughöhe gestiegen: " + info);
        }
        ctx.log("gelandet: " + info);
        return StepResult.pass();
    }

    private static StepResult ruleOffStep(SelfTest.StepContext ctx) {
        int[] arena = ARENA.get();
        if (arena == null) {
            return StepResult.fail("kein Testfeld");
        }
        if (phase(ctx) < RULE_CHECK) {
            StepResult result = takeOverStep(ctx, false, Leave.USE_REMOTE, false, false);
            if (result.status() != StepResult.Status.PASS) {
                return result;
            }
            next(ctx, RULE_CHECK);
            return StepResult.pending();
        }
        poll(ctx);
        Snap snap = fresh(ctx);
        if (snap == null) {
            return StepResult.pending();
        }
        if (phase(ctx) == RULE_CHECK) {
            if (snap.planned() || snap.flying()) {
                return StepResult.fail("Welt-Regel aus, Server fliegt trotzdem: " + describe(snap, arena));
            }
            if (snap.armed() || snap.y() - arena[1] > 0.05) {
                return since(ctx) > 200 ? StepResult.fail("Drohne fällt nicht: " + describe(snap, arena)) : StepResult.pending();
            }
            ctx.log("Regel aus: Motoren aus, Drohne am Boden: " + describe(snap, arena));
            next(ctx, RULE_CHECK + 1);
            return StepResult.pending();
        }
        StepResult restored = server(ctx, "Regel zurück", server -> WorldRules.set(server, WorldRuleSpecs.AUTONOMOUS_RESCUE, 1));
        return restored == null ? StepResult.pass() : restored;
    }

    private static StepResult unloadStep(SelfTest.StepContext ctx) {
        int[] arena = ARENA.get();
        if (arena == null) {
            return StepResult.fail("kein Testfeld");
        }
        poll(ctx);
        Snap snap = fresh(ctx);
        if (snap != null) {
            track(snap, arena);
        }
        switch (phase(ctx)) {
            case 0 -> {
                if (snap == null || !snap.flying() || snap.ticks() < 20) {
                    return ctx.tick() > 200 ? StepResult.fail("Server fliegt vor dem Entladen nicht: " + describe(snap, arena)) : StepResult.pending();
                }
                StepResult wait = server(ctx, "Entladen", server -> {
                    QuadcopterEntity drone = serverDrone(server);
                    if (drone == null) {
                        throw new IllegalStateException("Drohne fehlt");
                    }
                    TagValueOutput output = TagValueOutput.createWithContext(ProblemReporter.DISCARDING, drone.registryAccess());
                    drone.saveWithoutId(output);
                    CompoundTag tag = output.buildResult();
                    if (!tag.contains(QuadcopterEntity.RETURN_PLAN_KEY)) {
                        throw new IllegalStateException("Heimflug-Plan nicht gespeichert: " + tag.keySet());
                    }
                    ctx.state().put(TAG, tag);
                    drone.remove(Entity.RemovalReason.UNLOADED_TO_CHUNK);
                });
                if (wait != null) {
                    return wait;
                }
                next(ctx, 1);
                return StepResult.pending();
            }
            case 1 -> {
                if (since(ctx) < 20) {
                    return StepResult.pending();
                }
                StepResult wait = server(ctx, "Neu laden", server -> {
                    if (AutonomousReturns.flights() != 0) {
                        throw new IllegalStateException("Flug nach dem Entladen nicht beendet: " + AutonomousReturns.flights());
                    }
                    ServerPlayer player = serverPlayer(server);
                    ServerLevel level = player.level();
                    CompoundTag tag = (CompoundTag) ctx.state().get(TAG);
                    Entity entity = EntityType.create(DroneModule.DRONE.get(), TagValueInput.create(ProblemReporter.DISCARDING,
                            level.registryAccess(), tag), level, EntitySpawnReason.LOAD).orElseThrow();
                    if (!(entity instanceof QuadcopterEntity drone) || drone.returnPlan() == null) {
                        throw new IllegalStateException("Plan nach dem Laden weg");
                    }
                    RELOAD_AT.set(drone.returnPlan().ticks());
                    if (!level.addFreshEntity(drone)) {
                        throw new IllegalStateException("Drohne nicht wieder eingefügt");
                    }
                    DRONE.set(drone.getUUID());
                });
                if (wait != null) {
                    return wait;
                }
                next(ctx, 2);
                return StepResult.pending();
            }
            case 2 -> {
                if (snap == null || !snap.present() || !snap.flying() || snap.ticks() <= RELOAD_AT.get() + 5) {
                    return since(ctx) > 100 ? StepResult.fail("neu geladene Drohne fliegt nicht weiter: " + describe(snap, arena))
                            : StepResult.pending();
                }
                StepResult wait = server(ctx, "Spieler weg", server -> {
                    ServerPlayer player = serverPlayer(server);
                    ServerLevel level = player.level();
                    Vec3 far = viewpoint(arena).add(0.0, 0.0, FAR_AWAY);
                    player.getAbilities().flying = true;
                    player.onUpdateAbilities();
                    player.teleportTo(level, far.x, level.getMaxY() - 40.0, far.z, Set.of(), 0.0F, 0.0F, true);
                });
                if (wait != null) {
                    return wait;
                }
                ctx.log("weiter nach dem Neuladen, Spieler weg: " + describe(snap, arena));
                next(ctx, 3);
                return StepResult.pending();
            }
            case 3 -> {
                if (snap == null || !snap.present()) {
                    return since(ctx) > 100 ? StepResult.fail("neu geladene Drohne fehlt: " + describe(snap, arena)) : StepResult.pending();
                }
                if (snap.planned() || snap.armed()) {
                    if (since(ctx) % 40 == 0) {
                        ctx.log("weit weg: " + describe(snap, arena));
                    }
                    return since(ctx) > 2000 ? StepResult.fail("landet weit weg vom Spieler nicht: " + describe(snap, arena)) : StepResult.pending();
                }
                Vec3 home = home(arena);
                double distance = Math.hypot(snap.x() - home.x, snap.z() - home.z);
                if (distance > LANDING_TOLERANCE || Math.abs(snap.y() - home.y) > GROUND_TOLERANCE) {
                    return StepResult.fail("nach dem Neuladen nicht am Homepoint gelandet: " + describe(snap, arena));
                }
                ctx.log("nach Entladen und weit weg gelandet: " + describe(snap, arena));
                next(ctx, 4);
                return StepResult.pending();
            }
            default -> {
                StepResult wait = server(ctx, "Spieler zurück", server -> {
                    ServerPlayer player = serverPlayer(server);
                    Vec3 view = viewpoint(arena);
                    player.getAbilities().flying = false;
                    player.onUpdateAbilities();
                    player.teleportTo(player.level(), view.x, view.y, view.z, Set.of(), -90.0F, 0.0F, true);
                });
                return wait == null ? StepResult.pass() : wait;
            }
        }
    }

    private static StepResult cleanupStep(SelfTest.StepContext ctx) {
        StepResult wait = server(ctx, "Aufräumen", server -> {
            ServerPlayer player = serverPlayer(server);
            ServerLevel level = player.level();
            WorldRules.set(server, WorldRuleSpecs.AUTONOMOUS_RESCUE, 1);
            QuadcopterEntity drone = serverDrone(server);
            if (drone != null) {
                if (drone.pilot().isPresent()) {
                    DronePiloting.release(drone);
                }
                drone.setReturnPlan(null);
                drone.discard();
            }
            for (Map.Entry<BlockPos, BlockState> entry : ORIGINAL.entrySet()) {
                level.setBlock(entry.getKey(), entry.getValue(), Block.UPDATE_CLIENTS);
            }
            ORIGINAL.clear();
            player.getInventory().setItem(0, ItemStack.EMPTY);
            double[] back = PLAYER_HOME.getAndSet(null);
            if (back != null) {
                player.getAbilities().flying = false;
                player.onUpdateAbilities();
                player.teleportTo(level, back[0], back[1], back[2], Set.of(), (float) back[3], (float) back[4], true);
            }
        });
        if (wait != null) {
            return wait;
        }
        PilotSession.INSTANCE.clearTestInput();
        RescueController.INSTANCE.clearHome();
        DRONE.set(null);
        ARENA.set(null);
        return StepResult.pass();
    }

    private static final class LandingScene implements Screenshots.Scene {
        private static final String MOVED = "moved";

        @Override
        public StepResult prepare(SelfTest.StepContext ctx) {
            Minecraft mc = Minecraft.getInstance();
            int[] arena = ARENA.get();
            if (arena == null || mc.player == null) {
                return StepResult.fail("kein Testfeld für das Bildschirmfoto");
            }
            StepResult resized = Screenshots.resizeWindow(ctx);
            if (resized.status() != StepResult.Status.PASS) {
                return resized;
            }
            poll(ctx);
            Snap snap = fresh(ctx);
            if (snap == null) {
                return StepResult.pending();
            }
            track(snap, arena);
            if (!snap.flying()) {
                return StepResult.fail("kein Heimflug ohne Pilot für das Bildschirmfoto: " + describe(snap, arena));
            }
            Vec3 home = home(arena);
            Map<String, Object> state = ctx.state();
            Vec3 view = new Vec3(home.x - 6.5, arena[1], home.z + 4.5);
            if (!state.containsKey(MOVED)) {
                state.put(MOVED, ctx.onServer(server -> {
                    ServerPlayer player = serverPlayer(server);
                    player.teleportTo(player.level(), view.x, view.y, view.z, Set.of(), -125.0F, -5.0F, true);
                    player.getInventory().setSelectedSlot(2);
                    player.connection.send(new ClientboundSetHeldSlotPacket(2));
                }));
                return StepResult.pending();
            }
            double distance = Math.hypot(snap.x() - home.x, snap.z() - home.z);
            double height = snap.y() - arena[1];
            if (snap.phase() != RescuePhase.LAND || distance > 2.0 || height > 3.5 || height < 1.2) {
                return ctx.tick() > 1400 ? StepResult.fail("Landeanflug nicht erreicht: " + describe(snap, arena)) : StepResult.pending();
            }
            CompletableFuture<?> moved = (CompletableFuture<?>) state.get(MOVED);
            if (!moved.isDone() || mc.player.position().distanceTo(view) > 0.2) {
                return StepResult.pending();
            }
            Screenshots.hideMarker(true);
            lookBeside(mc, new Vec3(home.x, arena[1] + 0.3, home.z), 0.45, 1.2);
            return StepResult.pass();
        }

        @Override
        public void hold(SelfTest.StepContext ctx) {
            int[] arena = ARENA.get();
            if (arena != null) {
                Vec3 home = home(arena);
                lookBeside(Minecraft.getInstance(), new Vec3(home.x, arena[1] + 0.3, home.z), 0.45, 1.2);
            }
        }

        @Override
        public void finish(SelfTest.StepContext ctx) {
            Screenshots.hideMarker(false);
            Screenshots.restoreWindow(ctx);
        }
    }

    private static final class DoorScene implements Screenshots.Scene {
        private static final String MOVED = "moved";

        @Override
        public StepResult prepare(SelfTest.StepContext ctx) {
            Minecraft mc = Minecraft.getInstance();
            int[] arena = ARENA.get();
            if (arena == null || mc.player == null) {
                return StepResult.fail("kein Testfeld für das Bildschirmfoto");
            }
            StepResult resized = Screenshots.resizeWindow(ctx);
            if (resized.status() != StepResult.Status.PASS) {
                return resized;
            }
            Map<String, Object> state = ctx.state();
            Vec3 view = new Vec3(arena[0] + HALL_FROM - 3.5, arena[1], arena[2] + 2.2);
            if (!state.containsKey(MOVED)) {
                state.put(MOVED, ctx.onServer(server -> {
                    ServerPlayer player = serverPlayer(server);
                    player.teleportTo(player.level(), view.x, view.y, view.z, Set.of(), -120.0F, 5.0F, true);
                    player.getInventory().setSelectedSlot(2);
                    player.connection.send(new ClientboundSetHeldSlotPacket(2));
                }));
                return StepResult.pending();
            }
            poll(ctx);
            Snap snap = fresh(ctx);
            if (snap == null) {
                return StepResult.pending();
            }
            track(snap, arena);
            if (!snap.flying()) {
                return StepResult.fail("kein Heimflug ohne Pilot für das Bildschirmfoto: " + describe(snap, arena));
            }
            double dx = snap.x() - arena[0];
            boolean atDoor = dx < HALL_FROM - 0.8 && dx > HALL_FROM - 2.6 && snap.y() < arena[1] + DOOR_HEIGHT;
            if (!atDoor) {
                return ctx.tick() > 2000 ? StepResult.fail("Drohne kommt nicht ans Tor: " + describe(snap, arena)) : StepResult.pending();
            }
            CompletableFuture<?> moved = (CompletableFuture<?>) state.get(MOVED);
            if (!moved.isDone() || mc.player.position().distanceTo(view) > 0.2) {
                return StepResult.pending();
            }
            Screenshots.hideMarker(true);
            lookBeside(mc, null, 0.0, 0.9);
            return StepResult.pass();
        }

        @Override
        public void hold(SelfTest.StepContext ctx) {
            lookBeside(Minecraft.getInstance(), null, 0.0, 0.9);
        }

        @Override
        public void finish(SelfTest.StepContext ctx) {
            Screenshots.hideMarker(false);
            Screenshots.restoreWindow(ctx);
        }
    }

    private static void lookBeside(Minecraft mc, @Nullable Vec3 also, double weight, double sideways) {
        UUID id = DRONE.get();
        if (mc.player == null || mc.level == null || id == null || !(mc.level.getEntity(id) instanceof QuadcopterEntity drone)) {
            return;
        }
        Vec3 eye = mc.player.getEyePosition();
        Vec3 target = drone.getBoundingBox().getCenter();
        if (also != null) {
            target = target.scale(1.0 - weight).add(also.scale(weight));
        }
        Vec3 dir = target.subtract(eye);
        Vec3 side = new Vec3(dir.z, 0.0, -dir.x).normalize().scale(sideways);
        target = target.add(side);
        double dx = target.x - eye.x;
        double dy = target.y - eye.y;
        double dz = target.z - eye.z;
        float yaw = (float) Math.toDegrees(Math.atan2(-dx, dz));
        float pitch = (float) -Math.toDegrees(Math.atan2(dy, Math.sqrt(dx * dx + dz * dz)));
        mc.player.setYRot(yaw);
        mc.player.setXRot(pitch);
        mc.player.yRotO = yaw;
        mc.player.xRotO = pitch;
        mc.player.setYHeadRot(yaw);
    }
}
