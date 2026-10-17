package de.lardtom.propwash.rescue.client;

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
import de.lardtom.propwash.input.StickPositions;
import de.lardtom.propwash.race.CourseRegistry;
import de.lardtom.propwash.race.CourseToolItem;
import de.lardtom.propwash.race.GateEntity;
import de.lardtom.propwash.race.GateGeometry;
import de.lardtom.propwash.race.GateSize;
import de.lardtom.propwash.race.LapTracker;
import de.lardtom.propwash.race.RacePayloads;
import de.lardtom.propwash.race.RaceModule;
import de.lardtom.propwash.race.RaceServer;
import de.lardtom.propwash.race.client.ClientCourses;
import de.lardtom.propwash.race.client.ClientRaceTracker;
import de.lardtom.propwash.rescue.HomeMode;
import de.lardtom.propwash.rescue.HomePoint;
import de.lardtom.propwash.rescue.HomePointCodecs;
import de.lardtom.propwash.rescue.HomeResetRules;
import de.lardtom.propwash.rescue.HomeResets;
import de.lardtom.propwash.rescue.RescueSettings;
import de.lardtom.propwash.server.WorldRules;
import de.lardtom.propwash.server.rules.WorldRuleSpecs;
import de.lardtom.propwash.sim.DroneSim;
import de.lardtom.propwash.sim.StabMode;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.core.SectionPos;
import net.minecraft.network.protocol.game.ClientboundSetHeldSlotPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.jspecify.annotations.Nullable;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

final class HomeResetSelfTests {
    private static final String PHASE = "phase";
    private static final String SINCE = "since";
    private static final double DRONE_DX = 5.0;
    private static final double FAR = 400.0;
    private static final String OLD_OPTION = "key_key.propwash.race_restart";
    private static final String NEW_OPTION = "key_key.propwash.reset";
    private static final AtomicReference<UUID> DRONE = new AtomicReference<>();
    private static final AtomicReference<HomePoint> HOME = new AtomicReference<>();
    private static final AtomicReference<Vec3> MOVED = new AtomicReference<>();
    private static final AtomicReference<HomeMode> MODE = new AtomicReference<>();
    private static final AtomicReference<String> PROBLEM = new AtomicReference<>();
    private static final AtomicReference<String> NOTE = new AtomicReference<>();
    private static final AtomicReference<String> ARRIVAL = new AtomicReference<>();
    private static final AtomicReference<HomeResetRules.Outcome> OUTCOME = new AtomicReference<>();
    private static final AtomicInteger COURSE = new AtomicInteger(GateEntity.NO_COURSE);
    private static final List<Integer> GATES = new CopyOnWriteArrayList<>();
    private static final List<LapTracker.Event> EVENTS = new CopyOnWriteArrayList<>();

    private HomeResetSelfTests() {
    }

    static void register() {
        SelfTest.clientStep("reset: Homepoint beim Scharfschalten (Vorbereitung)", 600, HomeResetSelfTests::setupStep);
        SelfTest.clientStep("reset: Taste setzt die Drohne gerade und entschärft auf den Homepoint", 400, HomeResetSelfTests::keyStep);
        SelfTest.clientStep("reset: Welt-Regel aus verweigert den Reset, an erlaubt ihn wieder", 400, HomeResetSelfTests::ruleStep);
        SelfTest.clientStep("reset: Reset bricht den laufenden Lauf ab, danach stehender Start", 900, HomeResetSelfTests::raceStep);
        SelfTest.clientStep("reset: weit entfernter Homepoint im FPV hin und zurück, Ziel-Chunk geladen", 900, HomeResetSelfTests::farStep);
        SelfTest.clientStep("reset: alte Tastenbelegung „Rennen neu starten“ wird übernommen", 100, HomeResetSelfTests::migrationStep);
        SelfTest.clientStep("reset: Aufräumen", 200, HomeResetSelfTests::cleanupStep);
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

    private static Vec3 home() {
        HomePoint home = HOME.get();
        if (home == null) {
            throw new IllegalStateException("kein Homepoint aus der Vorbereitung");
        }
        return new Vec3(home.x(), home.y(), home.z());
    }

    private static void pressReset() {
        KeyMapping.click(InputConstants.getKey(RescueKeys.RESET.saveString()));
    }

    private static @Nullable String atHome(QuadcopterEntity drone, Vec3 home) {
        double dx = drone.getX() - home.x;
        double dz = drone.getZ() - home.z;
        Vector3f up = drone.orientation().transform(new Vector3f(0.0F, 1.0F, 0.0F));
        if (dx * dx + dz * dz > 1.0E-4 || drone.getY() < home.y - 1.0E-3 || drone.getY() > home.y + HomeResetRules.CLEARANCE) {
            return "Drohne nicht am Homepoint: " + drone.position() + " statt " + home;
        }
        if (up.y < 0.999F) {
            return "Drohne nicht gerade: oben=" + up;
        }
        return drone.isArmed() ? "Drohne nach dem Reset noch scharf" : null;
    }

    private static void moveAway(MinecraftServer server, Vec3 offset) {
        QuadcopterEntity drone = serverDrone(server);
        Vec3 target = home().add(offset);
        drone.resetTo(target, new Quaternionf().rotationY(1.1F).rotateZ((float) Math.PI * 0.8F));
        MOVED.set(target);
    }

    private static StepResult setupStep(SelfTest.StepContext ctx) {
        PilotSession session = PilotSession.INSTANCE;
        switch (phase(ctx)) {
            case 0 -> {
                if (ctx.tick() == 0) {
                    RescueSettings cfg = RescueConfigs.get();
                    MODE.compareAndSet(null, cfg.homeMode);
                    cfg.homeMode = HomeMode.FIRST_ARM;
                    RescueController.INSTANCE.clearHome();
                    HOME.set(null);
                }
                StepResult wait = once(ctx, "Drohne", server -> {
                    ServerPlayer player = serverPlayer(server);
                    ServerLevel level = player.level();
                    WorldRules.set(server, WorldRuleSpecs.ALLOW_RESET, 1);
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
                    ItemStack remote = new ItemStack(DroneModule.REMOTE.get());
                    remote.set(CoreComponents.BOUND_DRONE.get(), DroneInteractions.binding(drone));
                    player.getInventory().setItem(0, remote);
                    player.getInventory().setSelectedSlot(0);
                    player.connection.send(new ClientboundSetHeldSlotPacket(0));
                    DronePiloting.StartResult result = DronePiloting.tryStart(player, drone, PilotMode.LINE_OF_SIGHT);
                    if (result != DronePiloting.StartResult.STARTED) {
                        throw new IllegalStateException("Start verweigert: " + result);
                    }
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
                String key = "Homepoint " + (s / 5);
                StepResult wait = once(ctx, key, server -> HOME.set(serverDrone(server).homePoint()));
                if (wait != null) {
                    return wait;
                }
                if (HOME.get() == null) {
                    return s > 160 ? StepResult.fail("kein Homepoint an der Drohne nach dem Scharfschalten") : StepResult.pending();
                }
                next(ctx, 2);
                return StepResult.pending();
            }
            default -> {
                DroneSim sim = session.sim();
                session.setTestInput(StickPositions.IDLE, false, StabMode.ANGLE);
                if (sim == null || sessionDrone() == null) {
                    return StepResult.fail("Sitzung verloren");
                }
                if (sim.telemetry().armed) {
                    return since(ctx) > 60 ? StepResult.fail("Drohne lässt sich nicht entschärfen") : StepResult.pending();
                }
                ctx.log("Homepoint: " + HOME.get());
                return StepResult.pass();
            }
        }
    }

    private static StepResult keyStep(SelfTest.StepContext ctx) {
        PilotSession session = PilotSession.INSTANCE;
        session.setTestInput(StickPositions.IDLE, false, StabMode.ANGLE);
        if (sessionDrone() == null) {
            return StepResult.fail("keine Sitzung");
        }
        switch (phase(ctx)) {
            case 0 -> {
                StepResult wait = once(ctx, "wegsetzen", server -> moveAway(server, new Vec3(10.0, 5.0, 8.0)));
                if (wait != null) {
                    return wait;
                }
                next(ctx, 1);
                return StepResult.pending();
            }
            case 1 -> {
                QuadcopterEntity drone = sessionDrone();
                Vec3 moved = MOVED.get();
                if (drone == null || moved == null) {
                    return StepResult.fail("Sitzung verloren");
                }
                if (Math.abs(drone.getX() - moved.x) > 0.5 || Math.abs(drone.getZ() - moved.z) > 0.5) {
                    return since(ctx) > 60 ? StepResult.fail("Client übernimmt die Versetzung nicht: " + drone.position()) : StepResult.pending();
                }
                if (since(ctx) < 10) {
                    return StepResult.pending();
                }
                ctx.state().put("gesendet", HomeResetInput.sent());
                pressReset();
                next(ctx, 2);
                return StepResult.pending();
            }
            case 2 -> {
                if (HomeResetInput.sent() <= (int) ctx.state().get("gesendet")) {
                    return since(ctx) > 20 ? StepResult.fail("Reset-Taste sendet keine Anfrage") : StepResult.pending();
                }
                String key = "prüfen " + (since(ctx) / 5);
                StepResult wait = once(ctx, key, server -> PROBLEM.set(atHome(serverDrone(server), home())));
                if (wait != null) {
                    return wait;
                }
                if (PROBLEM.get() != null) {
                    return since(ctx) > 60 ? StepResult.fail("Server: " + PROBLEM.get()) : StepResult.pending();
                }
                next(ctx, 3);
                return StepResult.pending();
            }
            default -> {
                QuadcopterEntity drone = sessionDrone();
                DroneSim sim = session.sim();
                if (drone == null || sim == null) {
                    return StepResult.fail("Sitzung nach dem Reset verloren");
                }
                if (since(ctx) < 30) {
                    return StepResult.pending();
                }
                Vec3 home = home();
                if (sim.telemetry().armed) {
                    return StepResult.fail("Client-Sim nach dem Reset noch scharf");
                }
                double dx = drone.getX() - home.x;
                double dz = drone.getZ() - home.z;
                if (dx * dx + dz * dz > 0.01 || drone.getY() < home.y - 0.3) {
                    return StepResult.fail("Client-Drohne nicht am Homepoint: " + drone.position() + " statt " + home);
                }
                Vector3f up = drone.orientation().transform(new Vector3f(0.0F, 1.0F, 0.0F));
                if (up.y < 0.99F) {
                    return StepResult.fail("Client-Drohne nach dem Reset nicht gerade: oben=" + up);
                }
                ctx.log("Reset per Taste: " + drone.position() + ", oben " + up.y);
                return StepResult.pass();
            }
        }
    }

    private static StepResult ruleStep(SelfTest.StepContext ctx) {
        PilotSession.INSTANCE.setTestInput(StickPositions.IDLE, false, StabMode.ANGLE);
        if (sessionDrone() == null) {
            return StepResult.fail("keine Sitzung");
        }
        switch (phase(ctx)) {
            case 0 -> {
                StepResult wait = once(ctx, "Regel aus", server -> {
                    WorldRules.set(server, WorldRuleSpecs.ALLOW_RESET, 0);
                    moveAway(server, new Vec3(6.0, 0.5, -6.0));
                });
                if (wait != null) {
                    return wait;
                }
                if (since(ctx) < HomeResetRules.COOLDOWN_TICKS + 5) {
                    return StepResult.pending();
                }
                ctx.state().put("gesendet", HomeResetInput.sent());
                pressReset();
                next(ctx, 1);
                return StepResult.pending();
            }
            case 1 -> {
                if (since(ctx) < 20) {
                    return StepResult.pending();
                }
                if (HomeResetInput.sent() <= (int) ctx.state().get("gesendet")) {
                    return StepResult.fail("Reset-Taste sendet keine Anfrage");
                }
                StepResult wait = once(ctx, "verweigert", server -> {
                    QuadcopterEntity drone = serverDrone(server);
                    Vec3 moved = MOVED.get();
                    HomeResetRules.Outcome outcome = HomeResets.reset(serverPlayer(server), drone);
                    OUTCOME.set(outcome);
                    Vec3 home = home();
                    double dx = drone.getX() - home.x;
                    double dz = drone.getZ() - home.z;
                    PROBLEM.set(outcome != HomeResetRules.Outcome.DISABLED ? "Regel aus, Ergebnis " + outcome
                            : dx * dx + dz * dz < 1.0 ? "Drohne trotz Regel aus am Homepoint: " + drone.position() + ", versetzt war " + moved : null);
                });
                if (wait != null) {
                    return wait;
                }
                if (PROBLEM.get() != null) {
                    return StepResult.fail(PROBLEM.get());
                }
                next(ctx, 2);
                return StepResult.pending();
            }
            default -> {
                StepResult wait = once(ctx, "Regel an", server -> {
                    WorldRules.set(server, WorldRuleSpecs.ALLOW_RESET, 1);
                    QuadcopterEntity drone = serverDrone(server);
                    HomeResetRules.Outcome outcome = HomeResets.reset(serverPlayer(server), drone);
                    PROBLEM.set(outcome != HomeResetRules.Outcome.HOME ? "Regel an, Ergebnis " + outcome : atHome(drone, home()));
                });
                if (wait != null) {
                    return wait;
                }
                if (PROBLEM.get() != null) {
                    return StepResult.fail(PROBLEM.get());
                }
                ctx.log("Regel aus: " + OUTCOME.get() + ", Regel an: zurück am Homepoint");
                return StepResult.pass();
            }
        }
    }

    private static void buildCourse(MinecraftServer server) {
        ServerPlayer player = serverPlayer(server);
        ServerLevel level = player.level();
        CourseRegistry registry = CourseRegistry.get();
        if (registry == null) {
            throw new IllegalStateException("CourseRegistry läuft nicht");
        }
        Vec3 home = home();
        List<GateEntity> placed = new ArrayList<>();
        for (double dz : new double[]{14.0, 30.0}) {
            GateEntity gate = RaceModule.GATE.get().create(level, EntitySpawnReason.COMMAND);
            if (gate == null) {
                throw new IllegalStateException("Gate nicht erzeugt");
            }
            int top = level.getHeight(Heightmap.Types.MOTION_BLOCKING, (int) Math.floor(home.x), (int) Math.floor(home.z + dz));
            gate.setSize(GateSize.MEDIUM);
            gate.snapTo(home.x, top, home.z + dz, 0.0F, 0.0F);
            if (!level.addFreshEntity(gate)) {
                throw new IllegalStateException("Gate nicht gespawnt");
            }
            placed.add(gate);
            GATES.add(gate.getId());
        }
        ItemStack tool = new ItemStack(RaceModule.COURSE_TOOL.get());
        for (GateEntity gate : placed) {
            CourseToolItem.useOnGate(tool, player, gate);
        }
        int id = CourseToolItem.courseOf(tool);
        CourseRegistry.Course course = registry.course(id);
        if (course == null || course.gates.size() != 2) {
            throw new IllegalStateException("Kurs nicht angelegt");
        }
        COURSE.set(id);
    }

    private static void passStart(MinecraftServer server, double side) {
        ServerLevel level = serverPlayer(server).level();
        if (!(level.getEntity(GATES.getFirst()) instanceof GateEntity gate)) {
            throw new IllegalStateException("Start-Gate fehlt");
        }
        QuadcopterEntity drone = serverDrone(server);
        GateGeometry g = gate.geometry();
        double half = drone.getBbHeight() * 0.5;
        Vec3 at = new Vec3(g.centerX() + g.normalX() * side, g.centerY() - half, g.centerZ() + g.normalZ() * side);
        drone.resetTo(at, HomeResetRules.level(drone.orientation()));
        MOVED.set(at);
    }

    private static boolean abortedByReset() {
        for (LapTracker.Event event : EVENTS) {
            if (event.type() == LapTracker.EventType.ABORT && event.reason() == LapTracker.AbortReason.RESET) {
                return true;
            }
        }
        return false;
    }

    private static long count(LapTracker.EventType type) {
        return EVENTS.stream().filter(event -> event.type() == type).count();
    }

    private static StepResult raceStep(SelfTest.StepContext ctx) {
        PilotSession session = PilotSession.INSTANCE;
        ClientRaceTracker tracker = ClientRaceTracker.INSTANCE;
        if (sessionDrone() == null) {
            return StepResult.fail("keine Sitzung");
        }
        int phase = phase(ctx);
        session.setTestInput(StickPositions.IDLE, phase == 7 && since(ctx) >= 10, StabMode.ANGLE);
        switch (phase) {
            case 0 -> {
                if (ctx.tick() == 0) {
                    EVENTS.clear();
                    GATES.clear();
                }
                StepResult wait = once(ctx, "Kurs", HomeResetSelfTests::buildCourse);
                if (wait != null) {
                    return wait;
                }
                if (ClientCourses.get(COURSE.get()) == null) {
                    return since(ctx) > 100 ? StepResult.fail("Kurs " + COURSE.get() + " nicht am Client") : StepResult.pending();
                }
                tracker.setEventListener(EVENTS::add);
                next(ctx, 1);
                return StepResult.pending();
            }
            case 1 -> {
                StepResult wait = once(ctx, "vor dem Start", server -> passStart(server, -0.8));
                if (wait != null) {
                    return wait;
                }
                QuadcopterEntity drone = sessionDrone();
                Vec3 before = MOVED.get();
                if (drone == null || before == null || Math.abs(drone.getX() - before.x) > 0.2 || Math.abs(drone.getZ() - before.z) > 0.2) {
                    return since(ctx) > 60 ? StepResult.fail("Client übernimmt die Position vor dem Start nicht") : StepResult.pending();
                }
                next(ctx, 2);
                return StepResult.pending();
            }
            case 2 -> {
                StepResult wait = once(ctx, "durch den Start", server -> passStart(server, 0.8));
                if (wait != null) {
                    return wait;
                }
                if (!tracker.tracker().racing()) {
                    return since(ctx) > 60 ? StepResult.fail("Lauf startet nicht: Kurs " + tracker.courseId() + ", Phase "
                            + tracker.tracker().phase() + ", Ereignisse " + EVENTS.size()) : StepResult.pending();
                }
                next(ctx, 3);
                return StepResult.pending();
            }
            case 3 -> {
                String key = "Server-Lauf " + (since(ctx) / 5);
                StepResult wait = once(ctx, key, server -> ctx.state().put("serverKurs", RaceServer.sessionCourse(serverPlayer(server))));
                if (wait != null) {
                    return wait;
                }
                int course = (int) ctx.state().getOrDefault("serverKurs", GateEntity.NO_COURSE);
                if (course != COURSE.get()) {
                    return since(ctx) > 60 ? StepResult.fail("Server kennt den Lauf nicht: " + course) : StepResult.pending();
                }
                if (!tracker.tracker().racing()) {
                    return StepResult.fail("Lauf endet vor dem Reset: " + tracker.tracker().phase());
                }
                if (since(ctx) < HomeResetRules.COOLDOWN_TICKS + 5) {
                    return StepResult.pending();
                }
                ctx.state().put("gesendet", HomeResetInput.sent());
                pressReset();
                next(ctx, 4);
                return StepResult.pending();
            }
            case 4 -> {
                if (!abortedByReset()) {
                    return since(ctx) > 60 ? StepResult.fail("kein Abbruch mit Grund RESET, Phase " + tracker.tracker().phase()
                            + ", Anfragen " + ((int) ctx.state().get("gesendet")) + " → " + HomeResetInput.sent() + ", Drohne "
                            + sessionDrone().position()) : StepResult.pending();
                }
                String key = "nach dem Reset " + (since(ctx) / 5);
                StepResult wait = once(ctx, key, server -> {
                    ServerPlayer player = serverPlayer(server);
                    QuadcopterEntity drone = serverDrone(server);
                    String problem = atHome(drone, home());
                    if (problem == null && RaceServer.sessionCourse(player) != GateEntity.NO_COURSE) {
                        problem = "Server-Lauf nach dem Reset noch aktiv: " + RaceServer.sessionCourse(player);
                    }
                    if (problem == null) {
                        RacePayloads.RaceReportC2S lap = new RacePayloads.RaceReportC2S(RacePayloads.ReportKind.LAP, COURSE.get(), 1,
                                8_000_000_000L, 8_000_000_000L, 0, 0L);
                        RaceServer.Verdict verdict = RaceServer.handle(lap, player, System.nanoTime());
                        if (verdict == RaceServer.Verdict.ACCEPTED || verdict == RaceServer.Verdict.PENDING) {
                            problem = "Runde nach dem Reset angenommen: " + verdict;
                        }
                    }
                    PROBLEM.set(problem);
                });
                if (wait != null) {
                    return wait;
                }
                if (PROBLEM.get() != null) {
                    return since(ctx) > 60 ? StepResult.fail(PROBLEM.get()) : StepResult.pending();
                }
                if (tracker.tracker().phase() != LapTracker.Phase.IDLE || count(LapTracker.EventType.LAP) != 0
                        || count(LapTracker.EventType.FINISH) != 0) {
                    return StepResult.fail("Client nach dem Reset: Phase " + tracker.tracker().phase() + ", Runden "
                            + count(LapTracker.EventType.LAP));
                }
                if (!tracker.resetStartPending()) {
                    return StepResult.fail("stehender Start nach dem Reset nicht vorgemerkt");
                }
                next(ctx, 5);
                return StepResult.pending();
            }
            case 5, 6 -> {
                next(ctx, 7);
                return StepResult.pending();
            }
            case 7 -> {
                if (tracker.lobbyMember() || tracker.countdownActive()) {
                    next(ctx, 8);
                    return StepResult.pending();
                }
                return since(ctx) > 140 ? StepResult.fail("Scharfschalten am Start nach dem Reset startet keine Aufstellung") : StepResult.pending();
            }
            default -> {
                if (since(ctx) < HomeResetRules.COOLDOWN_TICKS + 5) {
                    return StepResult.pending();
                }
                if (!ctx.state().containsKey("zweiter Reset")) {
                    ctx.state().put("zweiter Reset", ctx.tick());
                    pressReset();
                    return StepResult.pending();
                }
                if (tracker.lobbyMember() || tracker.countdownActive()) {
                    return ctx.tick() - (int) ctx.state().get("zweiter Reset") > 60
                            ? StepResult.fail("Reset verlässt die Startaufstellung nicht") : StepResult.pending();
                }
                tracker.setEventListener(null);
                ctx.log("Lauf mit Grund RESET abgebrochen, Server-Runde abgelehnt, stehender Start nach dem Reset");
                return StepResult.pass();
            }
        }
    }

    private static void startFpv(MinecraftServer server) {
        ServerPlayer player = serverPlayer(server);
        QuadcopterEntity drone = serverDrone(server);
        if (drone.pilot().isPresent()) {
            DronePiloting.release(drone);
        }
        ItemStack goggles = ContentIds.item(ContentIds.GOGGLES_CREATIVE).map(ItemStack::new).orElse(ItemStack.EMPTY);
        if (goggles.isEmpty()) {
            throw new IllegalStateException("Kreativ-Brille nicht registriert");
        }
        goggles.set(CoreComponents.BOUND_DRONE.get(), DroneInteractions.binding(drone));
        player.setItemSlot(EquipmentSlot.HEAD, goggles);
        DronePiloting.StartResult result = DronePiloting.tryStart(player, drone, PilotMode.FPV);
        if (result != DronePiloting.StartResult.STARTED) {
            throw new IllegalStateException("FPV-Start verweigert: " + result);
        }
    }

    private static String overlay() {
        try {
            java.lang.reflect.Field field = net.minecraft.client.gui.Hud.class.getDeclaredField("overlayMessageString");
            field.setAccessible(true);
            Object message = field.get(Minecraft.getInstance().gui.hud);
            return message instanceof net.minecraft.network.chat.Component component ? component.getString() : "-";
        } catch (ReflectiveOperationException | RuntimeException e) {
            return "? " + e;
        }
    }

    private static boolean clientChunk(Vec3 at) {
        Minecraft mc = Minecraft.getInstance();
        return mc.level != null && mc.level.hasChunk(SectionPos.blockToSectionCoord(at.x), SectionPos.blockToSectionCoord(at.z));
    }

    private static @Nullable StepResult arrived(SelfTest.StepContext ctx, QuadcopterEntity client, Vec3 target, String where) {
        double dx = client.getX() - target.x;
        double dz = client.getZ() - target.z;
        if (client.getY() < target.y - 0.3) {
            return StepResult.fail("Drohne fällt " + where + " durch: " + client.position());
        }
        if (!clientChunk(target) || dx * dx + dz * dz > 0.25) {
            return since(ctx) > 300 ? StepResult.fail(where + ": Chunk am Client " + clientChunk(target) + ", Drohne " + client.position())
                    : StepResult.pending();
        }
        String key = "angekommen " + where;
        if (!ctx.state().containsKey(key)) {
            ctx.state().put(key, ctx.tick());
        }
        return ctx.tick() - (int) ctx.state().get(key) < 40 ? StepResult.pending() : null;
    }

    private static StepResult farStep(SelfTest.StepContext ctx) {
        PilotSession.INSTANCE.setTestInput(StickPositions.IDLE, false, StabMode.ANGLE);
        QuadcopterEntity client = sessionDrone();
        int phase = phase(ctx);
        if (client == null && phase > 0) {
            StepResult wait = once(ctx, "Diagnose", server -> {
                QuadcopterEntity drone = serverDrone(server);
                NOTE.set("Server: Drohne " + drone.position() + ", Pilot " + drone.pilot().isPresent() + ", Modus " + drone.pilotMode());
            });
            if (wait != null) {
                return wait;
            }
            return StepResult.fail("keine Sitzung in Phase " + phase + " nach " + since(ctx) + " Ticks, Reset gesendet " + ctx.state().containsKey("fern") + ", " + NOTE.get() + ", Meldung "
                    + overlay());
        }
        Vec3 home = home();
        Vec3 far = home.add(FAR, 0.0, 0.0);
        switch (phase) {
            case 0 -> {
                if (since(ctx) < HomeResetRules.COOLDOWN_TICKS + 5) {
                    return StepResult.pending();
                }
                StepResult wait = once(ctx, "FPV", HomeResetSelfTests::startFpv);
                if (wait != null) {
                    return wait;
                }
                if (client == null || PilotSession.INSTANCE.mode() != PilotMode.FPV) {
                    return since(ctx) > 140 ? StepResult.fail("keine FPV-Sitzung") : StepResult.pending();
                }
                next(ctx, 1);
                return StepResult.pending();
            }
            case 1 -> {
                if (since(ctx) < 10) {
                    return StepResult.pending();
                }
                StepResult wait = once(ctx, "fern", server -> {
                    ServerPlayer player = serverPlayer(server);
                    ServerLevel level = player.level();
                    QuadcopterEntity drone = serverDrone(server);
                    int cx = SectionPos.blockToSectionCoord(far.x);
                    int cz = SectionPos.blockToSectionCoord(far.z);
                    NOTE.set("Server-Chunk vorher " + (level.hasChunk(cx, cz) ? "geladen" : "nicht geladen"));
                    drone.setHomePoint(new HomePoint(far.x, far.y, far.z, HomePointCodecs.dimension(level), level.getGameTime(),
                            HomePoint.Source.MANUAL));
                    HomeResetRules.Outcome outcome = HomeResets.reset(player, drone);
                    OUTCOME.set(outcome);
                    PROBLEM.set(outcome == HomeResetRules.Outcome.HOME || outcome == HomeResetRules.Outcome.LOADING ? null : "Ergebnis " + outcome);
                });
                if (wait != null) {
                    return wait;
                }
                if (PROBLEM.get() != null) {
                    return StepResult.fail(PROBLEM.get());
                }
                String key = "angekommen " + (since(ctx) / 5);
                StepResult arrived = once(ctx, key, server -> {
                    QuadcopterEntity drone = serverDrone(server);
                    ARRIVAL.set(HomeResets.pending(drone) ? "lädt noch" : atHome(drone, far));
                });
                if (arrived != null) {
                    return arrived;
                }
                if (ARRIVAL.get() != null) {
                    return since(ctx) > 200 ? StepResult.fail("Server: " + ARRIVAL.get()) : StepResult.pending();
                }
                ctx.log(NOTE.get() + ", erstes Ergebnis " + OUTCOME.get() + ", Drohne nach " + since(ctx) + " Ticks am fernen Homepoint");
                next(ctx, 2);
                return StepResult.pending();
            }
            case 2 -> {
                StepResult wait = arrived(ctx, client, far, "am fernen Homepoint");
                if (wait != null) {
                    return wait;
                }
                StepResult restored = once(ctx, "Homepoint zurück", server -> serverDrone(server).setHomePoint(HOME.get()));
                if (restored != null) {
                    return restored;
                }
                ctx.state().put("Heim-Chunk", clientChunk(home));
                ctx.state().put("gesendet", HomeResetInput.sent());
                pressReset();
                next(ctx, 3);
                return StepResult.pending();
            }
            default -> {
                if (HomeResetInput.sent() <= (int) ctx.state().get("gesendet")) {
                    return since(ctx) > 20 ? StepResult.fail("Reset-Taste sendet keine Anfrage") : StepResult.pending();
                }
                StepResult wait = arrived(ctx, client, home, "zurück am Homepoint");
                if (wait != null) {
                    return wait;
                }
                ctx.log("ferner Homepoint " + Math.round(FAR) + " Blöcke hin und zurück per Taste im FPV, Heim-Chunk beim Reset am Client "
                        + ((boolean) ctx.state().get("Heim-Chunk") ? "geladen" : "nicht geladen") + ", Drohne " + client.position());
                return StepResult.pass();
            }
        }
    }

    private static StepResult migrationStep(SelfTest.StepContext ctx) {
        Minecraft mc = Minecraft.getInstance();
        Path file = mc.gameDirectory.toPath().resolve("options.txt");
        InputConstants.Key before = InputConstants.getKey(RescueKeys.RESET.saveString());
        try {
            mc.options.save();
            List<String> lines = new ArrayList<>();
            for (String line : Files.readAllLines(file, StandardCharsets.UTF_8)) {
                if (!line.startsWith(NEW_OPTION + ":") && !line.startsWith(OLD_OPTION + ":")) {
                    lines.add(line);
                }
            }
            lines.add(OLD_OPTION + ":key.keyboard.f6");
            Files.write(file, lines, StandardCharsets.UTF_8);
            mc.options.load();
            String loaded = RescueKeys.RESET.saveString();
            mc.options.save();
            String saved = Files.readString(file, StandardCharsets.UTF_8);
            RescueKeys.RESET.setKey(before);
            KeyMapping.resetMapping();
            mc.options.save();
            if (!"key.keyboard.f6".equals(loaded)) {
                return StepResult.fail("alte Belegung nicht übernommen: " + loaded);
            }
            if (saved.contains(OLD_OPTION + ":") || !saved.contains(NEW_OPTION + ":key.keyboard.f6")) {
                return StepResult.fail("options.txt nach dem Speichern falsch");
            }
            ctx.log("„" + OLD_OPTION + "“ → „" + NEW_OPTION + "“ (F6)");
            return StepResult.pass();
        } catch (IOException e) {
            RescueKeys.RESET.setKey(before);
            KeyMapping.resetMapping();
            return StepResult.fail("options.txt: " + e.getMessage());
        }
    }

    private static StepResult cleanupStep(SelfTest.StepContext ctx) {
        PilotSession.INSTANCE.clearTestInput();
        ClientRaceTracker.INSTANCE.setEventListener(null);
        StepResult wait = once(ctx, "Aufräumen", server -> {
            WorldRules.set(server, WorldRuleSpecs.ALLOW_RESET, 1);
            UUID id = DRONE.getAndSet(null);
            ServerPlayer player = serverPlayer(server);
            ServerLevel level = player.level();
            if (id != null && level.getEntity(id) instanceof QuadcopterEntity drone) {
                if (drone.pilot().isPresent()) {
                    DronePiloting.release(drone);
                }
                drone.discard();
            }
            for (int gate : GATES) {
                if (level.getEntity(gate) instanceof GateEntity entity) {
                    entity.discard();
                }
            }
            GATES.clear();
            player.getInventory().setItem(0, ItemStack.EMPTY);
            player.setItemSlot(EquipmentSlot.HEAD, ItemStack.EMPTY);
        });
        if (wait != null) {
            return wait;
        }
        HomeMode mode = MODE.getAndSet(null);
        if (mode != null) {
            RescueConfigs.get().homeMode = mode;
        }
        RescueController.INSTANCE.clearHome();
        EVENTS.clear();
        return PilotSession.INSTANCE.isActive() ? (ctx.tick() > 100 ? StepResult.fail("Sitzung endet nicht") : StepResult.pending())
                : StepResult.pass();
    }
}
