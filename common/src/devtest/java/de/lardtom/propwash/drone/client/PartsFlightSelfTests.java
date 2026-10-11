package de.lardtom.propwash.drone.client;

import de.lardtom.propwash.api.part.FrameRole;
import de.lardtom.propwash.api.part.FrameShape;
import de.lardtom.propwash.build.BatteryType;
import de.lardtom.propwash.build.DroneBuild;
import de.lardtom.propwash.build.FrameType;
import de.lardtom.propwash.build.MotorType;
import de.lardtom.propwash.build.PartWear;
import de.lardtom.propwash.build.PropType;
import de.lardtom.propwash.build.StackType;
import de.lardtom.propwash.build.VideoType;
import de.lardtom.propwash.core.CoreComponents;
import de.lardtom.propwash.dev.SelfTest;
import de.lardtom.propwash.dev.SelfTest.StepResult;
import de.lardtom.propwash.drone.DroneInteractions;
import de.lardtom.propwash.drone.DroneModule;
import de.lardtom.propwash.drone.DronePiloting;
import de.lardtom.propwash.drone.PilotMode;
import de.lardtom.propwash.drone.QuadcopterEntity;
import de.lardtom.propwash.input.StickPositions;
import de.lardtom.propwash.osd.OsdData;
import de.lardtom.propwash.osd.OsdWarnings;
import de.lardtom.propwash.sim.AirframeSpec;
import de.lardtom.propwash.sim.BuildAnalysis;
import de.lardtom.propwash.sim.BuildWarning;
import de.lardtom.propwash.sim.DroneSim;
import de.lardtom.propwash.sim.SimSnapshot;
import de.lardtom.propwash.sim.StabMode;
import de.lardtom.propwash.sim.Telemetry;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3d;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Predicate;

public final class PartsFlightSelfTests {
    private static final AtomicReference<DroneBuild> DEADCAT = new AtomicReference<>();
    private static final AtomicReference<DroneBuild> X_CLASS = new AtomicReference<>();
    private static final AtomicReference<DroneBuild> OVERVOLTED = new AtomicReference<>();
    private static final AtomicInteger DRONE_ID = new AtomicInteger(-1);
    private static final AtomicReference<String> SERVER_RESULT = new AtomicReference<>("");
    private static final String FUTURE = "future";
    private static final String PHASE = "phase";
    private static final String PHASE_TICK = "phaseTick";
    private static final String START_Y = "startY";
    private static final double HEAT_C = 5.0;
    private static final int SEARCH_LIMIT = 200_000;

    private PartsFlightSelfTests() {
    }

    public static void register() {
        SelfTest.clientStep("parts-flight: Beispiel-Deadcat und X-Class finden", 20, PartsFlightSelfTests::findStep);
        SelfTest.clientStep("parts-flight: Analyse der Beispiel-Teile", 20, PartsFlightSelfTests::analysisStep);
        SelfTest.clientStep("parts-flight: Deadcat fliegt mit Test-Knüppeln", 900, ctx -> flightStep(ctx, DEADCAT.get(), "Deadcat"));
        SelfTest.clientStep("parts-flight: X-Class fliegt mit Test-Knüppeln", 900, ctx -> flightStep(ctx, X_CLASS.get(), "X-Class"));
        SelfTest.clientStep("parts-flight: überhitzte Motoren drosseln, Verschleiß landet am Item", 900, PartsFlightSelfTests::heatStep);
        SelfTest.clientStep("parts-flight: zu viele Zellen lassen den ESC durchbrennen", 600, PartsFlightSelfTests::escStep);
        SelfTest.clientStep("parts-flight: Aufräumen", 200, PartsFlightSelfTests::cleanupStep);
    }

    private static Optional<DroneBuild> find(Predicate<FrameType> frameFilter, Predicate<StackType> stackFilter,
                                             Predicate<BatteryType> batteryFilter, Predicate<DroneBuild> accept) {
        int checked = 0;
        for (FrameType frame : FrameType.all()) {
            if (!frameFilter.test(frame)) {
                continue;
            }
            for (StackType stack : StackType.all()) {
                if (stack.creative() || !stackFilter.test(stack)) {
                    continue;
                }
                for (BatteryType battery : BatteryType.all()) {
                    if (!batteryFilter.test(battery)) {
                        continue;
                    }
                    for (MotorType motor : MotorType.all()) {
                        for (PropType prop : PropType.all()) {
                            for (VideoType video : VideoType.all()) {
                                if (++checked > SEARCH_LIMIT) {
                                    return Optional.empty();
                                }
                                DroneBuild build = new DroneBuild(frame, stack, motor, prop, video, battery);
                                if (build.buildable() && accept.test(build)) {
                                    return Optional.of(build);
                                }
                            }
                        }
                    }
                }
            }
        }
        return Optional.empty();
    }

    private static boolean flies(DroneBuild build) {
        AirframeSpec p = AirframeSpec.of(build);
        if (p.escOvervoltage) {
            return false;
        }
        BuildAnalysis a = BuildAnalysis.of(build);
        return !a.warnings().contains(BuildWarning.CANNOT_HOVER) && a.thrustToWeight() > 2.0 && a.hoverThrottlePercent() < 60.0;
    }

    private static StepResult findStep(SelfTest.StepContext ctx) {
        Optional<DroneBuild> deadcat = find(f -> !f.builtin() && f.definition().shape() == FrameShape.DEADCAT, s -> true, b -> true,
                PartsFlightSelfTests::flies);
        Optional<DroneBuild> xClass = find(f -> !f.builtin() && f.definition().role() == FrameRole.X_CLASS, s -> true, b -> true,
                PartsFlightSelfTests::flies);
        Optional<DroneBuild> overvolted = find(f -> !f.builtin(), s -> s == StackType.STACK_30, b -> b.cells > StackType.STACK_30.maxCells,
                b -> AirframeSpec.of(b).escOvervoltage);
        if (deadcat.isEmpty() || xClass.isEmpty()) {
            return StepResult.fail("Beispiel-Addon fehlt: Deadcat " + deadcat.isPresent() + ", X-Class " + xClass.isPresent());
        }
        DEADCAT.set(deadcat.get());
        X_CLASS.set(xClass.get());
        OVERVOLTED.set(overvolted.orElse(null));
        ctx.log("Deadcat " + AirframeSpec.of(deadcat.get()).buildKey + ", X-Class " + AirframeSpec.of(xClass.get()).buildKey
                + ", Überspannung " + overvolted.map(b -> AirframeSpec.of(b).buildKey).orElse("—"));
        return StepResult.pass();
    }

    private static StepResult analysisStep(SelfTest.StepContext ctx) {
        DroneBuild deadcat = DEADCAT.get();
        DroneBuild xClass = X_CLASS.get();
        if (deadcat == null || xClass == null) {
            return StepResult.fail("keine Beispiel-Drohnen aus dem vorigen Schritt");
        }
        AirframeSpec dc = AirframeSpec.of(deadcat);
        AirframeSpec xc = AirframeSpec.of(xClass);
        BuildAnalysis dca = BuildAnalysis.of(deadcat);
        BuildAnalysis xca = BuildAnalysis.of(xClass);
        if (!dc.explicitArms) {
            return StepResult.fail("Deadcat ohne eigene Arm-Geometrie");
        }
        if (!(dca.propsInViewPercent() < 1.0)) {
            return StepResult.fail("Deadcat hat Props im Bild: " + dca.propsInViewPercent());
        }
        if (xClass.frame().definition().nominalPropInches() < 10.0 || xc.propDiameter < 0.25) {
            return StepResult.fail("X-Class ist keine 10–13-Zoll-Drohne");
        }
        if (!Double.isFinite(xca.sustainedMotorTempC() + dca.sustainedMotorTempC())) {
            return StepResult.fail("Motortemperatur fehlt in der Analyse");
        }
        ctx.log(String.format(Locale.ROOT, "Deadcat %.1f %% Props im Bild, Schwebegas %.0f %%; X-Class Schwebegas %.0f %%, %.1f min, %.0f °C",
                dca.propsInViewPercent(), dca.hoverThrottlePercent(), xca.hoverThrottlePercent(), xca.hoverFlightTimeMin(),
                xca.sustainedMotorTempC()));
        return StepResult.pass();
    }

    private static ServerPlayer serverPlayer(MinecraftServer server) {
        List<ServerPlayer> players = server.getPlayerList().getPlayers();
        if (players.isEmpty()) {
            throw new IllegalStateException("kein Spieler auf dem Server");
        }
        return players.getFirst();
    }

    private static @Nullable QuadcopterEntity serverDrone(MinecraftServer server) {
        for (ServerLevel level : server.getAllLevels()) {
            if (level.getEntity(DRONE_ID.get()) instanceof QuadcopterEntity drone) {
                return drone;
            }
        }
        return null;
    }

    private static void placeAndFly(MinecraftServer server, DroneBuild build) {
        ServerPlayer player = serverPlayer(server);
        ServerLevel level = player.level();
        int x = player.getBlockX() + 4;
        int z = player.getBlockZ() + 2;
        int top = level.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z);
        BlockPos ground = new BlockPos(x, top - 1, z);
        ItemStack stack = new ItemStack(DroneModule.DRONE_ITEM.get());
        stack.set(CoreComponents.DRONE_BUILD.get(), build);
        stack.set(CoreComponents.BATTERY_CHARGE.get(), 1.0F);
        player.setItemInHand(InteractionHand.MAIN_HAND, stack);
        InteractionResult use = stack.useOn(new UseOnContext(player, InteractionHand.MAIN_HAND,
                new BlockHitResult(new Vec3(x + 0.5, top, z + 0.5), Direction.UP, ground, false)));
        if (!use.consumesAction()) {
            throw new IllegalStateException("Platzieren lieferte " + use);
        }
        List<QuadcopterEntity> drones = level.getEntitiesOfClass(QuadcopterEntity.class, new AABB(ground).inflate(3.0),
                d -> d.build().equals(build));
        if (drones.isEmpty()) {
            throw new IllegalStateException("keine Drohne nach dem Platzieren");
        }
        QuadcopterEntity drone = drones.getFirst();
        DRONE_ID.set(drone.getId());
        player.setItemSlot(EquipmentSlot.HEAD, ItemStack.EMPTY);
        ItemStack remote = new ItemStack(DroneModule.REMOTE.get());
        remote.set(CoreComponents.BOUND_DRONE.get(), DroneInteractions.binding(drone));
        player.setItemInHand(InteractionHand.MAIN_HAND, remote);
        remote.use(level, player, InteractionHand.MAIN_HAND);
        if (!drone.isFlownBy(player.getUUID()) || drone.pilotMode() != PilotMode.LINE_OF_SIGHT) {
            throw new IllegalStateException("Sichtflug nicht gestartet");
        }
    }

    private static int phase(SelfTest.StepContext ctx) {
        return (int) ctx.state().getOrDefault(PHASE, 0);
    }

    private static void next(SelfTest.StepContext ctx) {
        ctx.state().put(PHASE, phase(ctx) + 1);
        ctx.state().put(PHASE_TICK, ctx.tick());
    }

    private static int inPhase(SelfTest.StepContext ctx) {
        return ctx.tick() - (int) ctx.state().getOrDefault(PHASE_TICK, 0);
    }

    private static @Nullable StepResult startSession(SelfTest.StepContext ctx, DroneBuild build) {
        PilotSession session = PilotSession.INSTANCE;
        if (!ctx.state().containsKey(FUTURE)) {
            if (ctx.tick() < DronePiloting.REMOTE_DEBOUNCE_TICKS + 2) {
                return StepResult.pending();
            }
            ctx.state().put(FUTURE, ctx.onServer(server -> placeAndFly(server, build)));
            return StepResult.pending();
        }
        CompletableFuture<?> future = (CompletableFuture<?>) ctx.state().get(FUTURE);
        if (future.isCompletedExceptionally()) {
            return StepResult.fail("Start fehlgeschlagen: " + future.exceptionNow().getMessage());
        }
        QuadcopterEntity drone = session.drone();
        if (!future.isDone() || !session.isLineOfSight() || drone == null || drone.getId() != DRONE_ID.get() || session.sim() == null) {
            return ctx.tick() > 200 ? StepResult.fail("keine Sichtflug-Sitzung") : StepResult.pending();
        }
        return null;
    }

    private static StepResult stopAndPass(SelfTest.StepContext ctx, String message) {
        PilotSession session = PilotSession.INSTANCE;
        session.clearTestInput();
        if (session.isActive()) {
            session.requestStop();
            return StepResult.pending();
        }
        ctx.log(message);
        return StepResult.pass();
    }

    private static StepResult flightStep(SelfTest.StepContext ctx, @Nullable DroneBuild build, String name) {
        if (build == null) {
            return StepResult.fail(name + " fehlt");
        }
        if (phase(ctx) == 0) {
            StepResult started = startSession(ctx, build);
            if (started != null) {
                return started;
            }
            next(ctx);
        }
        PilotSession session = PilotSession.INSTANCE;
        DroneSim sim = session.sim();
        if (phase(ctx) < 4 && sim == null) {
            return StepResult.fail(name + ": Sitzung verloren");
        }
        switch (phase(ctx)) {
            case 1 -> {
                session.setTestInput(new StickPositions(0.0F, 0.0F, 0.0F, 0.0F), false, StabMode.ANGLE);
                if (inPhase(ctx) >= 10) {
                    next(ctx);
                }
                return StepResult.pending();
            }
            case 2 -> {
                session.setTestInput(new StickPositions(0.0F, 0.0F, 0.0F, 0.0F), true, StabMode.ANGLE);
                if (sim.telemetry().armed) {
                    ctx.state().put(START_Y, session.comPosition(new Vector3d()).y);
                    next(ctx);
                } else if (inPhase(ctx) > 40) {
                    return StepResult.fail(name + ": nicht scharf geschaltet, Sperren=" + sim.telemetry().armBlockers);
                }
                return StepResult.pending();
            }
            case 3 -> {
                Telemetry t = sim.telemetry();
                float throttle = (float) Math.min(1.0, t.hoverThrottleStick + 0.12);
                session.setTestInput(new StickPositions(0.0F, 0.0F, 0.0F, throttle), true, StabMode.ANGLE);
                if (!t.armed) {
                    return StepResult.fail(name + ": im Flug entschärft (" + t.lastDisarmReason + ")");
                }
                if (inPhase(ctx) > 10 && t.tiltDeg > 20.0) {
                    return StepResult.fail(String.format(Locale.ROOT, "%s kippt: %.1f°", name, t.tiltDeg));
                }
                double climbed = session.comPosition(new Vector3d()).y - (double) ctx.state().get(START_Y);
                if (climbed > 2.0) {
                    ctx.state().put("climbed", climbed);
                    ctx.state().put("tilt", t.tiltDeg);
                    session.setTestInput(new StickPositions(0.0F, 0.0F, 0.0F, 0.0F), false, StabMode.ANGLE);
                    next(ctx);
                    return StepResult.pending();
                }
                return inPhase(ctx) > 400 ? StepResult.fail(name + ": steigt nicht") : StepResult.pending();
            }
            default -> {
                return stopAndPass(ctx, String.format(Locale.ROOT, "%s gestiegen um %.1f m, Neigung %.1f°", name,
                        (double) ctx.state().get("climbed"), (double) ctx.state().get("tilt")));
            }
        }
    }

    private static void heat(DroneSim sim) {
        SimSnapshot snapshot = new SimSnapshot();
        sim.copyStateTo(snapshot);
        double target = sim.params().motorMaxTempC + HEAT_C;
        for (int i = 0; i < AirframeSpec.MOTORS; i++) {
            snapshot.motorTempC[i] = target;
        }
        sim.copyStateFrom(snapshot);
    }

    private static StepResult heatStep(SelfTest.StepContext ctx) {
        if (phase(ctx) == 0) {
            StepResult started = startSession(ctx, DroneBuild.FREESTYLE);
            if (started != null) {
                return started;
            }
            next(ctx);
        }
        PilotSession session = PilotSession.INSTANCE;
        DroneSim sim = session.sim();
        switch (phase(ctx)) {
            case 1 -> {
                if (sim == null) {
                    return StepResult.fail("Sitzung verloren");
                }
                session.setTestInput(new StickPositions(0.0F, 0.0F, 0.0F, 0.0F), false, StabMode.ANGLE);
                if (inPhase(ctx) == 5) {
                    heat(sim);
                }
                if (inPhase(ctx) >= 15) {
                    Telemetry t = sim.telemetry();
                    if (!t.motorDerating) {
                        return StepResult.fail("heiße Motoren drosseln nicht");
                    }
                    for (double limit : t.motorPowerLimit) {
                        if (limit > 0.25) {
                            return StepResult.fail("Leistungsgrenze zu hoch: " + limit);
                        }
                    }
                    OsdData osd = new OsdData();
                    osd.active = true;
                    osd.applyTelemetry(t);
                    List<OsdWarnings.Warning> warnings = new ArrayList<>();
                    OsdWarnings.collect(osd, warnings);
                    if (!warnings.contains(OsdWarnings.Warning.MOTOR_HOT)) {
                        return StepResult.fail("OSD warnt nicht vor heißen Motoren: " + warnings);
                    }
                    next(ctx);
                }
                return StepResult.pending();
            }
            case 2 -> {
                if (sim == null) {
                    return StepResult.fail("Sitzung verloren");
                }
                session.setTestInput(new StickPositions(0.0F, 0.0F, 0.0F, 0.0F), true, StabMode.ANGLE);
                if (sim.telemetry().armed) {
                    next(ctx);
                } else if (inPhase(ctx) > 40) {
                    return StepResult.fail("nicht scharf geschaltet, Sperren=" + sim.telemetry().armBlockers);
                }
                return StepResult.pending();
            }
            case 3 -> {
                if (sim == null) {
                    return StepResult.fail("Sitzung verloren");
                }
                session.setTestInput(new StickPositions(0.0F, 0.0F, 0.0F, 0.9F), true, StabMode.ANGLE);
                Telemetry t = sim.telemetry();
                if (inPhase(ctx) > 10) {
                    for (int i = 0; i < AirframeSpec.MOTORS; i++) {
                        if (t.motorDuty[i] > t.motorPowerLimit[i] + 0.01) {
                            return StepResult.fail(String.format(Locale.ROOT, "Motor %d über der Grenze: %.2f > %.2f", i, t.motorDuty[i],
                                    t.motorPowerLimit[i]));
                        }
                    }
                }
                if (inPhase(ctx) >= 40) {
                    session.setTestInput(new StickPositions(0.0F, 0.0F, 0.0F, 0.0F), false, StabMode.ANGLE);
                    next(ctx);
                }
                return StepResult.pending();
            }
            case 4 -> {
                if (!ctx.state().containsKey("wearFuture")) {
                    if (inPhase(ctx) < 60) {
                        return StepResult.pending();
                    }
                    SERVER_RESULT.set("");
                    ctx.state().put("wearFuture", ctx.onServer(server -> {
                        QuadcopterEntity drone = serverDrone(server);
                        if (drone == null) {
                            throw new IllegalStateException("Drohne auf dem Server verschwunden");
                        }
                        PartWear wear = drone.toStack().getOrDefault(CoreComponents.PART_WEAR.get(), PartWear.PRISTINE);
                        StringBuilder motors = new StringBuilder();
                        for (int position = 0; position < PartWear.POSITIONS; position++) {
                            motors.append(wear.motor(position) < 1.0F ? 'x' : '-');
                        }
                        SERVER_RESULT.set(motors.toString());
                    }));
                    return StepResult.pending();
                }
                CompletableFuture<?> future = (CompletableFuture<?>) ctx.state().get("wearFuture");
                if (future.isCompletedExceptionally()) {
                    return StepResult.fail("Verschleiß nicht lesbar: " + future.exceptionNow().getMessage());
                }
                if (!future.isDone()) {
                    return StepResult.pending();
                }
                if (!"xxxx".equals(SERVER_RESULT.get())) {
                    if (inPhase(ctx) < 200) {
                        ctx.state().remove("wearFuture");
                        return StepResult.pending();
                    }
                    return StepResult.fail("Hitzeverschleiß fehlt am Item: " + SERVER_RESULT.get());
                }
                next(ctx);
                return StepResult.pending();
            }
            default -> {
                return stopAndPass(ctx, "gedrosselt und Motorverschleiß am Item");
            }
        }
    }

    private static StepResult escStep(SelfTest.StepContext ctx) {
        DroneBuild build = OVERVOLTED.get();
        if (build == null) {
            ctx.log("übersprungen: kein angemeldeter Akku mit mehr als 6 Zellen passt zu einem fremden Rahmen");
            return StepResult.pass();
        }
        if (phase(ctx) == 0) {
            StepResult started = startSession(ctx, build);
            if (started != null) {
                return started;
            }
            next(ctx);
        }
        PilotSession session = PilotSession.INSTANCE;
        DroneSim sim = session.sim();
        switch (phase(ctx)) {
            case 1 -> {
                if (sim == null) {
                    return StepResult.fail("Sitzung verloren");
                }
                session.setTestInput(new StickPositions(0.0F, 0.0F, 0.0F, 0.0F), inPhase(ctx) >= 10, StabMode.ANGLE);
                if (sim.telemetry().escBurned) {
                    if (sim.telemetry().armed) {
                        return StepResult.fail("trotz durchgebranntem ESC scharf");
                    }
                    session.setTestInput(new StickPositions(0.0F, 0.0F, 0.0F, 0.0F), false, StabMode.ANGLE);
                    next(ctx);
                }
                return inPhase(ctx) > 60 ? StepResult.fail("ESC brennt nicht durch") : StepResult.pending();
            }
            case 2 -> {
                if (!ctx.state().containsKey("stackFuture")) {
                    SERVER_RESULT.set("");
                    ctx.state().put("stackFuture", ctx.onServer(server -> {
                        QuadcopterEntity drone = serverDrone(server);
                        if (drone == null) {
                            throw new IllegalStateException("Drohne auf dem Server verschwunden");
                        }
                        PartWear wear = drone.toStack().getOrDefault(CoreComponents.PART_WEAR.get(), PartWear.PRISTINE);
                        SERVER_RESULT.set(Float.toString(wear.stack()));
                    }));
                    return StepResult.pending();
                }
                CompletableFuture<?> future = (CompletableFuture<?>) ctx.state().get("stackFuture");
                if (future.isCompletedExceptionally()) {
                    return StepResult.fail("Zustand nicht lesbar: " + future.exceptionNow().getMessage());
                }
                if (!future.isDone()) {
                    return StepResult.pending();
                }
                if (!"0.0".equals(SERVER_RESULT.get())) {
                    if (inPhase(ctx) < 200) {
                        ctx.state().remove("stackFuture");
                        return StepResult.pending();
                    }
                    return StepResult.fail("Stack am Item nicht kaputt: " + SERVER_RESULT.get());
                }
                next(ctx);
                return StepResult.pending();
            }
            default -> {
                return stopAndPass(ctx, "ESC durchgebrannt, Stack am Item kaputt");
            }
        }
    }

    private static StepResult cleanupStep(SelfTest.StepContext ctx) {
        PilotSession.INSTANCE.clearTestInput();
        if (PilotSession.INSTANCE.isActive()) {
            PilotSession.INSTANCE.requestStop();
            return StepResult.pending();
        }
        if (!ctx.state().containsKey(FUTURE)) {
            ctx.state().put(FUTURE, ctx.onServer(server -> {
                for (ServerLevel level : server.getAllLevels()) {
                    for (Entity entity : level.getEntitiesOfClass(QuadcopterEntity.class, serverPlayer(server).getBoundingBox().inflate(64.0))) {
                        entity.discard();
                    }
                }
                ServerPlayer player = serverPlayer(server);
                player.getInventory().clearContent();
                player.setItemSlot(EquipmentSlot.HEAD, ItemStack.EMPTY);
            }));
            return StepResult.pending();
        }
        CompletableFuture<?> future = (CompletableFuture<?>) ctx.state().get(FUTURE);
        if (future.isCompletedExceptionally()) {
            return StepResult.fail("Aufräumen fehlgeschlagen: " + future.exceptionNow().getMessage());
        }
        return future.isDone() && Minecraft.getInstance().player != null ? StepResult.pass() : StepResult.pending();
    }
}
