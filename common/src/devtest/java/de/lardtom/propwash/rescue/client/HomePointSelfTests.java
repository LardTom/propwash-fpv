package de.lardtom.propwash.rescue.client;

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
import de.lardtom.propwash.rescue.HomeMode;
import de.lardtom.propwash.rescue.HomePoint;
import de.lardtom.propwash.rescue.HomePointCodecs;
import de.lardtom.propwash.rescue.RescueSettings;
import de.lardtom.propwash.sim.DroneSim;
import de.lardtom.propwash.sim.StabMode;
import net.minecraft.client.Minecraft;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.game.ClientboundSetHeldSlotPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityProcessor;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.level.storage.TagValueOutput;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

public final class HomePointSelfTests {
    private static final String PHASE = "phase";
    private static final String SINCE = "since";
    private static final double DRONE_DX = 5.0;
    private static final AtomicReference<UUID> DRONE = new AtomicReference<>();
    private static final AtomicReference<HomePoint> SERVER_HOME = new AtomicReference<>();
    private static final AtomicReference<Vec3> TARGET = new AtomicReference<>();
    private static final AtomicReference<HomeMode> MODE = new AtomicReference<>();

    private HomePointSelfTests() {
    }

    static void register() {
        SelfTest.clientStep("rescue: Homepoint im NBT – Scharfschalten speichert ihn an der Drohne", 600, HomePointSelfTests::armStep);
        SelfTest.clientStep("rescue: Homepoint im NBT – /data merge verschiebt ihn beim Piloten", 400, HomePointSelfTests::commandStep);
        if (Screenshots.enabled()) {
            Screenshots.step("bildschirmfotos: Homepoint im NBT – per /data merge verschoben", "home_point_command", 30, new CommandScene());
        }
        SelfTest.clientStep("rescue: Homepoint im NBT – nach dem Neuladen vom Server übernommen", 600, HomePointSelfTests::reloadStep);
        SelfTest.clientStep("rescue: Homepoint im NBT – Aufräumen", 200, HomePointSelfTests::cleanupStep);
    }

    private static int phase(SelfTest.StepContext ctx) {
        return (int) ctx.state().getOrDefault(PHASE, 0);
    }

    private static int since(SelfTest.StepContext ctx) {
        return ctx.tick() - (int) ctx.state().getOrDefault(SINCE, 0);
    }

    private static void next(SelfTest.StepContext ctx, int phase) {
        ctx.state().put(PHASE, phase);
        ctx.state().put(SINCE, ctx.tick());
    }

    private static @Nullable StepResult once(SelfTest.StepContext ctx, String key, Consumer<MinecraftServer> action) {
        CompletableFuture<?> future = (CompletableFuture<?>) ctx.state().get(key);
        if (future == null) {
            ctx.state().put(key, ctx.onServer(action));
            return StepResult.pending();
        }
        if (future.isCompletedExceptionally()) {
            return StepResult.fail(key + ": " + future.exceptionNow().getMessage());
        }
        return future.isDone() ? null : StepResult.pending();
    }

    private static ServerPlayer serverPlayer(MinecraftServer server) {
        if (server.getPlayerList().getPlayers().isEmpty()) {
            throw new IllegalStateException("kein Spieler auf dem Server");
        }
        return server.getPlayerList().getPlayers().getFirst();
    }

    private static QuadcopterEntity serverDrone(MinecraftServer server) {
        UUID id = DRONE.get();
        if (id != null && serverPlayer(server).level().getEntity(id) instanceof QuadcopterEntity drone && !drone.isRemoved()) {
            return drone;
        }
        throw new IllegalStateException("Testdrohne fehlt auf dem Server");
    }

    private static @Nullable QuadcopterEntity sessionDrone() {
        PilotSession session = PilotSession.INSTANCE;
        QuadcopterEntity drone = session.drone();
        return session.isActive() && drone != null && drone.getUUID().equals(DRONE.get()) ? drone : null;
    }

    private static boolean command(MinecraftServer server, String command) {
        AtomicBoolean success = new AtomicBoolean();
        CommandSourceStack source = server.createCommandSourceStack().withSuppressedOutput()
                .withCallback((ok, result) -> success.set(ok));
        server.getCommands().performPrefixedCommand(source, command);
        return success.get();
    }

    private static String merge(UUID id, Vec3 home) {
        return String.format(Locale.ROOT, "data merge entity %s {HomePoint:{x:%sd,y:%sd,z:%sd}}", id, home.x, home.y, home.z);
    }

    private static boolean sameClientHome(Vec3 expected) {
        Vec3 home = RescueController.INSTANCE.home();
        return home != null && home.equals(expected);
    }

    private static void start(MinecraftServer server, QuadcopterEntity drone) {
        ServerPlayer player = serverPlayer(server);
        ItemStack remote = new ItemStack(DroneModule.REMOTE.get());
        remote.set(CoreComponents.BOUND_DRONE.get(), DroneInteractions.binding(drone));
        player.getInventory().setItem(0, remote);
        player.getInventory().setSelectedSlot(0);
        player.connection.send(new ClientboundSetHeldSlotPacket(0));
        DronePiloting.StartResult result = DronePiloting.tryStart(player, drone, PilotMode.LINE_OF_SIGHT);
        if (result != DronePiloting.StartResult.STARTED) {
            throw new IllegalStateException("Start verweigert: " + result);
        }
    }

    private static StepResult armStep(SelfTest.StepContext ctx) {
        PilotSession session = PilotSession.INSTANCE;
        switch (phase(ctx)) {
            case 0 -> {
                if (ctx.tick() == 0) {
                    RescueSettings cfg = RescueConfigs.get();
                    MODE.compareAndSet(null, cfg.homeMode);
                    cfg.homeMode = HomeMode.FIRST_ARM;
                    RescueController.INSTANCE.clearHome();
                    SERVER_HOME.set(null);
                    TARGET.set(null);
                }
                StepResult wait = once(ctx, "Drohne", server -> {
                    ServerPlayer player = serverPlayer(server);
                    ServerLevel level = player.level();
                    QuadcopterEntity drone = DroneModule.DRONE.get().create(level, EntitySpawnReason.COMMAND);
                    if (drone == null) {
                        throw new IllegalStateException("Drohne nicht erzeugt");
                    }
                    ItemStack stack = new ItemStack(DroneModule.DRONE_ITEM.get());
                    stack.set(CoreComponents.DRONE_BUILD.get(), DroneBuild.FREESTYLE);
                    stack.set(CoreComponents.BATTERY_CHARGE.get(), 1.0F);
                    drone.loadFromStack(stack);
                    drone.snapTo(player.getX() + DRONE_DX, player.getY(), player.getZ(), -90.0F, 0.0F);
                    if (!level.addFreshEntity(drone)) {
                        throw new IllegalStateException("Drohne nicht gespawnt");
                    }
                    DRONE.set(drone.getUUID());
                    if (drone.homePoint() != null) {
                        throw new IllegalStateException("neue Drohne hat schon einen Homepoint");
                    }
                    start(server, drone);
                });
                if (wait != null) {
                    return wait;
                }
                if (sessionDrone() == null) {
                    return ctx.tick() > 200 ? StepResult.fail("keine Sitzung am Client") : StepResult.pending();
                }
                next(ctx, 1);
                return StepResult.pending();
            }
            case 1 -> {
                DroneSim sim = session.sim();
                if (sessionDrone() == null || sim == null) {
                    return StepResult.fail("Sitzung beim Scharfschalten verloren");
                }
                int s = since(ctx);
                session.setTestInput(StickPositions.IDLE, s >= 10, StabMode.ANGLE);
                if (s < 40) {
                    return StepResult.pending();
                }
                if (!sim.telemetry().armed) {
                    return StepResult.fail("nicht scharf: Sperren " + sim.telemetry().armBlockers);
                }
                if (RescueController.INSTANCE.home() == null) {
                    return s > 120 ? StepResult.fail("kein Homepoint am Client nach dem Scharfschalten") : StepResult.pending();
                }
                next(ctx, 2);
                return StepResult.pending();
            }
            default -> {
                session.setTestInput(StickPositions.IDLE, true, StabMode.ANGLE);
                String key = "Lesen " + (since(ctx) / 5);
                StepResult wait = once(ctx, key, server -> SERVER_HOME.set(serverDrone(server).homePoint()));
                if (wait != null) {
                    return wait;
                }
                HomePoint stored = SERVER_HOME.get();
                Vec3 client = RescueController.INSTANCE.home();
                Minecraft mc = Minecraft.getInstance();
                boolean ok = stored != null && client != null && mc.level != null && stored.source() == HomePoint.Source.ARM
                        && stored.samePlace(client.x, client.y, client.z, HomePointCodecs.dimension(mc.level)) && stored.gameTime() > 0L;
                if (ok) {
                    ctx.log("Homepoint an der Drohne: " + stored);
                    return StepResult.pass();
                }
                return since(ctx) > 100 ? StepResult.fail("Server-Homepoint " + stored + ", Client " + client) : StepResult.pending();
            }
        }
    }

    private static StepResult commandStep(SelfTest.StepContext ctx) {
        PilotSession.INSTANCE.setTestInput(StickPositions.IDLE, false, StabMode.ANGLE);
        if (sessionDrone() == null) {
            return StepResult.fail("keine Sitzung");
        }
        switch (phase(ctx)) {
            case 0 -> {
                StepResult wait = once(ctx, "merge", server -> {
                    QuadcopterEntity drone = serverDrone(server);
                    HomePoint home = drone.homePoint();
                    if (home == null) {
                        throw new IllegalStateException("kein Homepoint an der Drohne");
                    }
                    Vec3 target = new Vec3(home.x() + 4.0, home.y(), home.z() + 3.0);
                    if (!command(server, merge(drone.getUUID(), target))) {
                        throw new IllegalStateException("/data merge abgelehnt");
                    }
                    HomePoint now = drone.homePoint();
                    if (now == null || now.source() != HomePoint.Source.COMMAND || !now.samePlace(target.x, target.y, target.z, home.dimension())) {
                        throw new IllegalStateException("/data merge setzt " + now);
                    }
                    TARGET.set(target);
                });
                if (wait != null) {
                    return wait;
                }
                if (!sameClientHome(TARGET.get())) {
                    return since(ctx) > 60 ? StepResult.fail("Client übernimmt " + TARGET.get() + " nicht, hat " + RescueController.INSTANCE.home())
                            : StepResult.pending();
                }
                next(ctx, 1);
                return StepResult.pending();
            }
            case 1 -> {
                StepResult wait = once(ctx, "falsch", server -> {
                    QuadcopterEntity drone = serverDrone(server);
                    HomePoint before = drone.homePoint();
                    String nether = "data merge entity " + drone.getUUID() + " {HomePoint:{x:0d,y:64d,z:0d,dimension:\"minecraft:the_nether\"}}";
                    if (command(server, nether)) {
                        throw new IllegalStateException("Homepoint in anderer Dimension angenommen");
                    }
                    if (command(server, "data merge entity " + drone.getUUID() + " {HomePoint:{x:31000000d}}")) {
                        throw new IllegalStateException("Homepoint außerhalb der Weltgrenze angenommen");
                    }
                    if (before == null || !before.equals(drone.homePoint())) {
                        throw new IllegalStateException("abgelehnter Befehl ändert " + before + " zu " + drone.homePoint());
                    }
                });
                if (wait != null) {
                    return wait;
                }
                if (since(ctx) < 10) {
                    return StepResult.pending();
                }
                return sameClientHome(TARGET.get()) ? StepResult.pass()
                        : StepResult.fail("Client-Homepoint nach abgelehnten Befehlen " + RescueController.INSTANCE.home());
            }
            default -> {
                return StepResult.fail("unbekannte Phase");
            }
        }
    }

    private static StepResult reloadStep(SelfTest.StepContext ctx) {
        PilotSession session = PilotSession.INSTANCE;
        switch (phase(ctx)) {
            case 0 -> {
                session.clearTestInput();
                StepResult wait = once(ctx, "Aussteigen", server -> {
                    QuadcopterEntity drone = serverDrone(server);
                    SERVER_HOME.set(drone.homePoint());
                    DronePiloting.release(drone);
                });
                if (wait != null) {
                    return wait;
                }
                if (session.isActive()) {
                    return since(ctx) > 100 ? StepResult.fail("Sitzung endet nicht") : StepResult.pending();
                }
                RescueController.INSTANCE.onDisconnect();
                if (RescueController.INSTANCE.home() != null) {
                    return StepResult.fail("Client vergisst den Homepoint nicht");
                }
                next(ctx, 1);
                return StepResult.pending();
            }
            case 1 -> {
                StepResult wait = once(ctx, "Neuladen", server -> {
                    QuadcopterEntity drone = serverDrone(server);
                    ServerLevel level = (ServerLevel) drone.level();
                    TagValueOutput output = TagValueOutput.createWithContext(ProblemReporter.DISCARDING, drone.registryAccess());
                    if (!drone.save(output)) {
                        throw new IllegalStateException("Drohne nicht speicherbar");
                    }
                    CompoundTag saved = output.buildResult();
                    if (!saved.contains(HomePointCodecs.KEY)) {
                        throw new IllegalStateException("Speicherstand ohne HomePoint: " + saved.keySet());
                    }
                    drone.discard();
                    Entity loaded = EntityType.loadEntityRecursive(TagValueInput.create(ProblemReporter.DISCARDING, level.registryAccess(), saved),
                            level, EntitySpawnReason.LOAD, EntityProcessor.NOP);
                    if (!(loaded instanceof QuadcopterEntity reloaded) || !level.addFreshEntity(reloaded)) {
                        throw new IllegalStateException("Drohne nicht neu geladen");
                    }
                    if (!SERVER_HOME.get().equals(reloaded.homePoint())) {
                        throw new IllegalStateException("nach dem Laden " + reloaded.homePoint() + " statt " + SERVER_HOME.get());
                    }
                    start(server, reloaded);
                });
                if (wait != null) {
                    return wait;
                }
                if (sessionDrone() == null) {
                    return since(ctx) > 200 ? StepResult.fail("keine Sitzung mit der neu geladenen Drohne") : StepResult.pending();
                }
                next(ctx, 2);
                return StepResult.pending();
            }
            default -> {
                HomePoint stored = SERVER_HOME.get();
                Vec3 expected = new Vec3(stored.x(), stored.y(), stored.z());
                DroneSim sim = session.sim();
                if (sim != null && sim.telemetry().armed) {
                    return StepResult.fail("Drohne ist scharf, Prüfung wertlos");
                }
                if (sameClientHome(expected)) {
                    ctx.log("Homepoint nach dem Neuladen übernommen: " + stored);
                    return StepResult.pass();
                }
                return since(ctx) > 60 ? StepResult.fail("Client übernimmt " + expected + " nicht, hat " + RescueController.INSTANCE.home())
                        : StepResult.pending();
            }
        }
    }

    private static StepResult cleanupStep(SelfTest.StepContext ctx) {
        PilotSession.INSTANCE.clearTestInput();
        StepResult wait = once(ctx, "Aufräumen", server -> {
            UUID id = DRONE.getAndSet(null);
            ServerPlayer player = serverPlayer(server);
            if (id != null && player.level().getEntity(id) instanceof QuadcopterEntity drone) {
                if (drone.pilot().isPresent()) {
                    DronePiloting.release(drone);
                }
                drone.discard();
            }
            player.getInventory().setItem(0, ItemStack.EMPTY);
        });
        if (wait != null) {
            return wait;
        }
        HomeMode mode = MODE.getAndSet(null);
        if (mode != null) {
            RescueConfigs.get().homeMode = mode;
        }
        RescueController.INSTANCE.clearHome();
        return PilotSession.INSTANCE.isActive() ? (ctx.tick() > 100 ? StepResult.fail("Sitzung endet nicht") : StepResult.pending())
                : StepResult.pass();
    }

    private static final class CommandScene implements Screenshots.Scene {
        private static final String MOVED = "moved";
        private static final String MERGED = "merged";

        @Override
        public StepResult prepare(SelfTest.StepContext ctx) {
            Minecraft mc = Minecraft.getInstance();
            Vec3 target = TARGET.get();
            if (target == null || mc.player == null || sessionDrone() == null) {
                return StepResult.fail("kein Homepoint und keine Sitzung für das Bildschirmfoto");
            }
            StepResult resized = Screenshots.resizeWindow(ctx);
            if (resized.status() != StepResult.Status.PASS) {
                return resized;
            }
            Map<String, Object> state = ctx.state();
            Vec3 view = new Vec3(target.x - 7.0, target.y, target.z + 1.5);
            StepResult wait = once(ctx, MOVED, server -> {
                ServerPlayer player = serverPlayer(server);
                player.teleportTo(player.level(), view.x, view.y, view.z, Set.of(), -100.0F, 12.0F, true);
            });
            if (wait != null) {
                return wait;
            }
            if (mc.player.position().distanceTo(view) > 0.5) {
                return ctx.tick() > 100 ? StepResult.fail("Spieler kommt nicht am Blickpunkt an") : StepResult.pending();
            }
            Screenshots.parkMouse(mc);
            state.remove(MERGED);
            return StepResult.pass();
        }

        @Override
        public void hold(SelfTest.StepContext ctx) {
            Vec3 target = TARGET.get();
            UUID id = DRONE.get();
            if (target == null || id == null || ctx.state().containsKey(MERGED)) {
                return;
            }
            Vec3 moved = new Vec3(target.x - 1.5, target.y, target.z - 1.0);
            ctx.state().put(MERGED, ctx.onServer(server -> {
                if (!command(server, merge(id, moved))) {
                    throw new IllegalStateException("/data merge abgelehnt");
                }
            }));
            TARGET.set(moved);
        }
    }
}
