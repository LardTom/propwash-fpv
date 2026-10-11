package de.lardtom.propwash.content.client;

import de.lardtom.propwash.api.PropwashApi;
import de.lardtom.propwash.api.build.BuildCheck;
import de.lardtom.propwash.api.build.DroneData;
import de.lardtom.propwash.api.build.ImportRequest;
import de.lardtom.propwash.api.build.ImportResult;
import de.lardtom.propwash.api.build.ImportStatus;
import de.lardtom.propwash.apiimpl.Conversions;
import de.lardtom.propwash.build.BatteryType;
import de.lardtom.propwash.build.DroneBuild;
import de.lardtom.propwash.build.DronePaint;
import de.lardtom.propwash.build.MotorType;
import de.lardtom.propwash.build.PartCompatibility;
import de.lardtom.propwash.build.PartWear;
import de.lardtom.propwash.build.PropType;
import de.lardtom.propwash.build.StackType;
import de.lardtom.propwash.content.Batteries;
import de.lardtom.propwash.content.BuildSlots;
import de.lardtom.propwash.content.ContentBlocks;
import de.lardtom.propwash.content.ContentItems;
import de.lardtom.propwash.content.DroneAssembly;
import de.lardtom.propwash.content.DroneWorkbenchMenu;
import de.lardtom.propwash.content.ImportService;
import de.lardtom.propwash.content.WorkbenchLayout;
import de.lardtom.propwash.core.CoreComponents;
import de.lardtom.propwash.core.FlightTune;
import de.lardtom.propwash.dev.SelfTest;
import de.lardtom.propwash.dev.SelfTest.StepResult;
import de.lardtom.propwash.dev.client.Screenshots;
import de.lardtom.propwash.drone.DroneInteraction;
import de.lardtom.propwash.drone.DroneInteractions;
import de.lardtom.propwash.drone.DroneModule;
import de.lardtom.propwash.drone.DronePiloting;
import de.lardtom.propwash.drone.PilotMode;
import de.lardtom.propwash.drone.QuadcopterEntity;
import de.lardtom.propwash.drone.client.PilotSession;
import de.lardtom.propwash.input.StickPositions;
import de.lardtom.propwash.server.ServerRules;
import de.lardtom.propwash.server.WorldRules;
import de.lardtom.propwash.server.client.ClientWorldRules;
import de.lardtom.propwash.server.rules.WorldRuleSpecs;
import de.lardtom.propwash.sim.ArmBlocker;
import de.lardtom.propwash.sim.DroneSim;
import de.lardtom.propwash.sim.StabMode;
import de.lardtom.propwash.sim.Telemetry;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.joml.Vector3d;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Supplier;

public final class PartFreedomSelfTests {
    public static final DroneBuild IMPOSSIBLE = new DroneBuild(DroneBuild.WHOOP.frame(), StackType.STACK_30, MotorType.M2807_1300, PropType.P7_2B,
            DroneBuild.WHOOP.video(), BatteryType.B6S_1300);
    private static final List<DroneBuild.Issue> EXPECTED = List.of(DroneBuild.Issue.MOTOR_MOUNT, DroneBuild.Issue.PROP_TOO_LARGE,
            DroneBuild.Issue.STACK_MOUNT, DroneBuild.Issue.BATTERY_FRAME);
    private static final String INDEX = "freedom_index";
    private static final String SINCE = "freedom_since";
    private static final String FUTURE = "freedom_future";
    private static final int WAIT_TICKS = 100;
    private static final AtomicInteger DRONE_ID = new AtomicInteger(-1);

    private PartFreedomSelfTests() {
    }

    public static void register() {
        SelfTest.clientStep("teile-freiheit: Werkbank, Import und API folgen der Welt-Regel des Servers", 900, PartFreedomSelfTests::workbenchStep);
        SelfTest.clientStep("teile-freiheit: Akku-Schnelltausch folgt der Welt-Regel", 300, PartFreedomSelfTests::batteryStep);
        SelfTest.clientStep("teile-freiheit: unmögliche Drohne startet nur mit Regel aus und fliegt", 1500, PartFreedomSelfTests::flightStep);
    }

    private interface Phase {
        @Nullable StepResult run(SelfTest.StepContext ctx, int waited);
    }

    private static Phase server(String what, Consumer<MinecraftServer> action) {
        return (ctx, waited) -> {
            Map<String, Object> state = ctx.state();
            CompletableFuture<?> future = (CompletableFuture<?>) state.get(FUTURE);
            if (future == null) {
                state.put(FUTURE, ctx.onServer(action));
                return StepResult.pending();
            }
            if (!future.isDone()) {
                return StepResult.pending();
            }
            state.remove(FUTURE);
            if (future.isCompletedExceptionally()) {
                Throwable cause = future.exceptionNow();
                return StepResult.fail(what + ": " + (cause.getMessage() != null ? cause.getMessage() : cause.toString()));
            }
            return null;
        };
    }

    private static Phase await(String what, BooleanSupplier condition) {
        return (ctx, waited) -> {
            if (condition.getAsBoolean()) {
                return null;
            }
            return waited < WAIT_TICKS ? StepResult.pending() : StepResult.fail(what);
        };
    }

    private static StepResult script(SelfTest.StepContext ctx, List<Phase> phases, Consumer<MinecraftServer> cleanup) {
        Map<String, Object> state = ctx.state();
        int index = (int) state.getOrDefault(INDEX, 0);
        if (!state.containsKey(SINCE)) {
            state.put(SINCE, ctx.tick());
        }
        while (index < phases.size()) {
            int waited = ctx.tick() - (int) state.get(SINCE);
            StepResult result;
            try {
                result = phases.get(index).run(ctx, waited);
            } catch (RuntimeException e) {
                result = StepResult.fail(e.getMessage() != null ? e.getMessage() : e.toString());
            }
            if (result == null) {
                index++;
                state.put(INDEX, index);
                state.put(SINCE, ctx.tick());
                continue;
            }
            if (result.status() == StepResult.Status.FAIL) {
                PilotSession.INSTANCE.clearTestInput();
                if (PilotSession.INSTANCE.isActive()) {
                    PilotSession.INSTANCE.requestStop();
                }
                ctx.onServer(cleanup);
            }
            return result;
        }
        return StepResult.pass();
    }

    @SuppressWarnings("unchecked")
    private static <T> T keep(SelfTest.StepContext ctx, String key, Supplier<T> init) {
        return (T) ctx.state().computeIfAbsent(key, k -> init.get());
    }

    private static ServerPlayer player(MinecraftServer server) {
        List<ServerPlayer> players = server.getPlayerList().getPlayers();
        if (players.isEmpty()) {
            throw new IllegalStateException("kein Spieler auf dem Server");
        }
        return players.getFirst();
    }

    private static void rule(MinecraftServer server, boolean enforced) {
        WorldRules.set(server, WorldRuleSpecs.PART_COMPATIBILITY, enforced ? 1 : 0);
        if (WorldRules.partCompatibility(server).enforced() != enforced) {
            throw new IllegalStateException("Welt-Regel part_compatibility liest sich falsch: " + WorldRules.partCompatibility(server));
        }
    }

    private static boolean clientRule(boolean enforced) {
        return ClientWorldRules.INSTANCE.partCompatibility().enforced() == enforced;
    }

    private static DroneData data(DroneBuild build) {
        return new DroneData(Conversions.spec(build), Conversions.paint(DronePaint.DEFAULT), Conversions.tune(FlightTune.EMPTY), Optional.empty());
    }

    private static List<String> names(List<DroneBuild.Issue> issues) {
        List<String> out = new ArrayList<>();
        for (DroneBuild.Issue issue : issues) {
            out.add(issue.name().toLowerCase(Locale.ROOT));
        }
        return out;
    }

    private static int countImpossible(Inventory inventory) {
        int count = 0;
        for (int i = 0; i < inventory.getContainerSize(); i++) {
            ItemStack stack = inventory.getItem(i);
            if (DroneAssembly.isDrone(stack) && IMPOSSIBLE.equals(DroneAssembly.buildOf(stack))) {
                count += stack.getCount();
            }
        }
        return count;
    }

    private static void put(DroneWorkbenchMenu menu, ServerPlayer player, int slot, ItemStack stack) {
        menu.setCarried(stack);
        menu.clicked(slot, 0, ContainerInput.PICKUP, player);
        if (!menu.getCarried().isEmpty()) {
            throw new IllegalStateException("Slot " + slot + " nahm " + stack + " nicht an");
        }
    }

    static DroneWorkbenchMenu openBench(ServerPlayer player, BlockPos pos, DroneBuild build) {
        ServerLevel level = player.level();
        BlockState bench = ContentBlocks.DRONE_WORKBENCH.block().get().defaultBlockState();
        level.setBlockAndUpdate(pos, bench);
        if (player.openMenu(bench.getMenuProvider(level, pos)).isEmpty() || !(player.containerMenu instanceof DroneWorkbenchMenu menu)) {
            throw new IllegalStateException("Werkbank-Menü nicht geöffnet");
        }
        put(menu, player, BuildSlots.FRAME, ContentItems.stack(build.frame()));
        put(menu, player, BuildSlots.STACK, ContentItems.stack(build.stack()));
        put(menu, player, BuildSlots.VIDEO, ContentItems.stack(build.video()));
        put(menu, player, BuildSlots.BATTERY, Batteries.stackOf(build.battery(), 0.9F));
        for (int i = 0; i < DroneBuild.MOTOR_COUNT; i++) {
            put(menu, player, BuildSlots.MOTOR + i, ContentItems.stack(build.motor()));
            put(menu, player, BuildSlots.PROP + i, ContentItems.stack(build.prop()));
        }
        if (!build.equals(menu.check().build().orElse(null))) {
            throw new IllegalStateException("Werkbank zeigt einen anderen Bauplan: " + menu.check());
        }
        return menu;
    }

    static void closeBench(ServerPlayer player, BlockPos pos, BlockState previous) {
        if (player.containerMenu instanceof DroneWorkbenchMenu menu) {
            List<ItemStack> empty = new ArrayList<>();
            for (int i = 0; i < BuildSlots.COUNT; i++) {
                empty.add(ItemStack.EMPTY);
            }
            menu.replaceParts(empty);
        }
        player.closeContainer();
        player.level().setBlockAndUpdate(pos, previous);
    }

    private static boolean resultIsImpossibleDrone(DroneWorkbenchMenu menu) {
        ItemStack result = menu.getSlot(DroneWorkbenchMenu.RESULT_SLOT).getItem();
        return DroneAssembly.isDrone(result) && IMPOSSIBLE.equals(DroneAssembly.buildOf(result));
    }

    private static @Nullable DroneWorkbenchMenu clientMenu() {
        return Minecraft.getInstance().gui.screen() instanceof DroneWorkbenchScreen screen ? screen.getMenu() : null;
    }

    private static StepResult workbenchStep(SelfTest.StepContext ctx) {
        AtomicReference<BlockPos> bench = keep(ctx, "bench", AtomicReference::new);
        AtomicReference<BlockState> previous = keep(ctx, "previous", AtomicReference::new);
        AtomicReference<GameType> mode = keep(ctx, "mode", AtomicReference::new);
        AtomicReference<String> log = keep(ctx, "log", AtomicReference::new);
        Consumer<MinecraftServer> cleanup = server -> {
            ServerPlayer player = player(server);
            BlockPos pos = bench.getAndSet(null);
            if (pos != null) {
                closeBench(player, pos, previous.get());
            }
            Inventory inventory = player.getInventory();
            for (int i = 0; i < inventory.getContainerSize(); i++) {
                ItemStack stack = inventory.getItem(i);
                if (DroneAssembly.isDrone(stack) && IMPOSSIBLE.equals(DroneAssembly.buildOf(stack))) {
                    inventory.setItem(i, ItemStack.EMPTY);
                }
            }
            GameType saved = mode.getAndSet(null);
            if (saved != null) {
                player.setGameMode(saved);
            }
            rule(server, true);
        };
        return script(ctx, List.of(
                server("Regel an", server -> {
                    ServerPlayer player = player(server);
                    rule(server, true);
                    mode.set(player.gameMode());
                    player.setGameMode(GameType.CREATIVE);
                    BlockPos pos = player.blockPosition().offset(-2, 0, 3);
                    previous.set(player.level().getBlockState(pos));
                    bench.set(pos);
                    DroneWorkbenchMenu menu = openBench(player, pos, IMPOSSIBLE);
                    if (!menu.check().build().orElseThrow().problems().equals(EXPECTED)) {
                        throw new IllegalStateException("unerwartete Probleme: " + menu.check().build().orElseThrow().problems());
                    }
                    if (menu.partCompatibility() != PartCompatibility.ENFORCED || !menu.getSlot(DroneWorkbenchMenu.RESULT_SLOT).getItem().isEmpty()) {
                        throw new IllegalStateException("Werkbank baut mit Regel an eine unmögliche Drohne");
                    }
                    BuildCheck check = PropwashApi.builds().check(Conversions.spec(IMPOSSIBLE));
                    if (check.buildable() || !check.problems().equals(names(EXPECTED)) || !PropwashApi.builds().partCompatibility()) {
                        throw new IllegalStateException("API-Prüfung mit Regel an: " + check);
                    }
                    int before = countImpossible(player.getInventory());
                    ImportResult imported = ImportService.apply(player, ImportRequest.createDrone(data(IMPOSSIBLE)));
                    if (imported.status() != ImportStatus.INVALID || countImpossible(player.getInventory()) != before) {
                        throw new IllegalStateException("Import einer neuen Drohne mit Regel an: " + imported);
                    }
                }),
                await("Werkbank am Client zeigt mit Regel an ein Ergebnis oder keine Fehler", () -> {
                    DroneWorkbenchMenu menu = clientMenu();
                    if (menu == null || !clientRule(true) || !IMPOSSIBLE.equals(menu.check().build().orElse(null))) {
                        return false;
                    }
                    WorkbenchReport report = WorkbenchReport.of(menu.check(), PartWear.PRISTINE, menu.partCompatibility());
                    return !report.buildable() && report.liftedProblems().isEmpty()
                            && menu.getSlot(DroneWorkbenchMenu.RESULT_SLOT).getItem().isEmpty();
                }),
                server("Regel aus bei offenem Menü", server -> {
                    ServerPlayer player = player(server);
                    rule(server, false);
                    if (!(player.containerMenu instanceof DroneWorkbenchMenu menu)) {
                        throw new IllegalStateException("Werkbank-Menü geschlossen");
                    }
                    menu.broadcastChanges();
                    if (!resultIsImpossibleDrone(menu)) {
                        throw new IllegalStateException("Werkbank baut mit Regel aus keine Drohne: " + menu.getSlot(DroneWorkbenchMenu.RESULT_SLOT).getItem());
                    }
                    BuildCheck check = PropwashApi.builds().check(Conversions.spec(IMPOSSIBLE));
                    if (!check.buildable() || !check.warnings().containsAll(names(EXPECTED)) || PropwashApi.builds().partCompatibility()) {
                        throw new IllegalStateException("API-Prüfung mit Regel aus: " + check);
                    }
                    ItemStack created = PropwashApi.builds().createDrone(data(IMPOSSIBLE));
                    if (!IMPOSSIBLE.equals(DroneAssembly.buildOf(created))) {
                        throw new IllegalStateException("API createDrone mit Regel aus: " + created);
                    }
                    int before = countImpossible(player.getInventory());
                    ImportResult imported = ImportService.apply(player, ImportRequest.createDrone(data(IMPOSSIBLE)));
                    if (!imported.status().success() || countImpossible(player.getInventory()) != before + 1) {
                        throw new IllegalStateException("Import einer neuen Drohne mit Regel aus: " + imported);
                    }
                    log.set("Server: Ergebnis " + DroneAssembly.buildOf(menu.getSlot(DroneWorkbenchMenu.RESULT_SLOT).getItem()).frame().id()
                            + ", API-Warnungen " + check.warnings() + ", Import " + imported.status());
                }),
                await("Werkbank am Client zeigt mit Regel aus kein Ergebnis oder keine Hinweise", () -> {
                    DroneWorkbenchMenu menu = clientMenu();
                    if (menu == null || !clientRule(false)) {
                        return false;
                    }
                    WorkbenchReport report = WorkbenchReport.of(menu.check(), PartWear.PRISTINE, menu.partCompatibility());
                    return report.buildable() && report.liftedProblems().equals(EXPECTED) && resultIsImpossibleDrone(menu);
                }),
                server("Regel wieder an bei offenem Menü", server -> {
                    ServerPlayer player = player(server);
                    rule(server, true);
                    if (!(player.containerMenu instanceof DroneWorkbenchMenu menu)) {
                        throw new IllegalStateException("Werkbank-Menü geschlossen");
                    }
                    menu.broadcastChanges();
                    if (!menu.getSlot(DroneWorkbenchMenu.RESULT_SLOT).getItem().isEmpty()) {
                        throw new IllegalStateException("Ergebnis bleibt nach dem Einschalten der Regel");
                    }
                }),
                await("Ergebnis am Client bleibt nach dem Einschalten der Regel", () -> {
                    DroneWorkbenchMenu menu = clientMenu();
                    return menu != null && clientRule(true) && menu.getSlot(DroneWorkbenchMenu.RESULT_SLOT).getItem().isEmpty();
                }),
                server("Aufräumen", cleanup),
                (c, waited) -> {
                    c.log(log.get());
                    return null;
                }), cleanup);
    }

    private static QuadcopterEntity spawn(ServerLevel level, Vec3 pos, DroneBuild build) {
        QuadcopterEntity drone = DroneModule.DRONE.get().create(level, EntitySpawnReason.COMMAND);
        if (drone == null) {
            throw new IllegalStateException("Drohne nicht erzeugt");
        }
        drone.setBuild(build);
        drone.setNoGravity(true);
        drone.snapTo(pos.x, pos.y, pos.z, 180.0F, 0.0F);
        if (!level.addFreshEntity(drone)) {
            throw new IllegalStateException("addFreshEntity fehlgeschlagen");
        }
        drone.setAttitude(new Quaternionf());
        return drone;
    }

    private static StepResult batteryStep(SelfTest.StepContext ctx) {
        AtomicReference<QuadcopterEntity> drone = keep(ctx, "drone", AtomicReference::new);
        Consumer<MinecraftServer> cleanup = server -> {
            QuadcopterEntity d = drone.getAndSet(null);
            if (d != null) {
                d.discard();
            }
            ServerPlayer player = player(server);
            player.setShiftKeyDown(false);
            player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
            rule(server, true);
        };
        return script(ctx, List.of(
                server("Akku-Tausch", server -> {
                    ServerPlayer player = player(server);
                    rule(server, true);
                    QuadcopterEntity d = spawn(player.level(), player.getEyePosition().add(0.0, -0.5, 2.0), DroneBuild.WHOOP);
                    drone.set(d);
                    player.setShiftKeyDown(true);
                    ItemStack pack = Batteries.stackOf(BatteryType.B6S_1300, 1.0F);
                    player.setItemInHand(InteractionHand.MAIN_HAND, pack);
                    if (DroneInteractions.resolve(player, d, pack) != DroneInteraction.Action.REJECT_BATTERY) {
                        throw new IllegalStateException("6S am Whoop mit Regel an nicht abgelehnt: " + DroneInteractions.resolve(player, d, pack));
                    }
                    d.interact(player, InteractionHand.MAIN_HAND, d.position());
                    if (d.build().battery() != DroneBuild.WHOOP.battery() || pack.isEmpty()) {
                        throw new IllegalStateException("6S am Whoop mit Regel an eingesetzt");
                    }
                    rule(server, false);
                    if (DroneInteractions.resolve(player, d, pack) != DroneInteraction.Action.QUICK_SWAP) {
                        throw new IllegalStateException("6S am Whoop mit Regel aus nicht erlaubt: " + DroneInteractions.resolve(player, d, pack));
                    }
                    d.interact(player, InteractionHand.MAIN_HAND, d.position());
                    if (d.build().battery() != BatteryType.B6S_1300) {
                        throw new IllegalStateException("6S am Whoop mit Regel aus nicht eingesetzt: " + d.build().battery().id());
                    }
                    if (d.build().buildable() || !d.build().buildable(PartCompatibility.LIFTED)) {
                        throw new IllegalStateException("Whoop mit 6S falsch eingestuft: " + d.build().problems());
                    }
                    cleanup.accept(server);
                })), cleanup);
    }

    private static boolean deniedForParts(Optional<Component> reason) {
        return reason.isPresent() && reason.get().getContents() instanceof TranslatableContents text
                && "propwash.server.deny.parts_incompatible".equals(text.getKey());
    }

    private static @Nullable QuadcopterEntity serverDrone(MinecraftServer server) {
        for (ServerLevel level : server.getAllLevels()) {
            if (level.getEntity(DRONE_ID.get()) instanceof QuadcopterEntity drone) {
                return drone;
            }
        }
        return null;
    }

    private static void place(MinecraftServer server) {
        ServerPlayer player = player(server);
        ServerLevel level = player.level();
        rule(server, true);
        int x = player.getBlockX() + 4;
        int z = player.getBlockZ() - 3;
        int top = level.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z);
        BlockPos ground = new BlockPos(x, top - 1, z);
        ItemStack stack = new ItemStack(DroneModule.DRONE_ITEM.get());
        stack.set(CoreComponents.DRONE_BUILD.get(), IMPOSSIBLE);
        stack.set(CoreComponents.BATTERY_CHARGE.get(), 1.0F);
        player.setShiftKeyDown(false);
        player.setItemInHand(InteractionHand.MAIN_HAND, stack);
        InteractionResult use = stack.useOn(new UseOnContext(player, InteractionHand.MAIN_HAND,
                new BlockHitResult(new Vec3(x + 0.5, top, z + 0.5), Direction.UP, ground, false)));
        if (!use.consumesAction()) {
            throw new IllegalStateException("Platzieren lieferte " + use);
        }
        List<QuadcopterEntity> drones = level.getEntitiesOfClass(QuadcopterEntity.class, new AABB(ground).inflate(3.0),
                d -> d.build().equals(IMPOSSIBLE));
        if (drones.isEmpty()) {
            throw new IllegalStateException("keine Drohne nach dem Platzieren");
        }
        QuadcopterEntity drone = drones.getFirst();
        DRONE_ID.set(drone.getId());
        player.setItemSlot(EquipmentSlot.HEAD, ItemStack.EMPTY);
        if (!deniedForParts(ServerRules.pilotDenialReason(player, drone))) {
            throw new IllegalStateException("Start-Sperre fehlt mit Regel an: " + ServerRules.pilotDenialReason(player, drone));
        }
        if (DronePiloting.tryStart(player, drone, PilotMode.LINE_OF_SIGHT) != DronePiloting.StartResult.DENIED || drone.pilot().isPresent()) {
            throw new IllegalStateException("unmögliche Drohne startet mit Regel an");
        }
        rule(server, false);
        if (ServerRules.pilotDenialReason(player, drone).isPresent()) {
            throw new IllegalStateException("Start mit Regel aus gesperrt: " + ServerRules.pilotDenialReason(player, drone));
        }
        ItemStack remote = new ItemStack(DroneModule.REMOTE.get());
        remote.set(CoreComponents.BOUND_DRONE.get(), DroneInteractions.binding(drone));
        player.setItemInHand(InteractionHand.MAIN_HAND, remote);
        remote.use(level, player, InteractionHand.MAIN_HAND);
        if (!drone.isFlownBy(player.getUUID()) || drone.pilotMode() != PilotMode.LINE_OF_SIGHT) {
            throw new IllegalStateException("Sichtflug mit Regel aus nicht gestartet");
        }
    }

    private static boolean finite(Telemetry t, DroneSim sim, Vector3d position) {
        if (!Double.isFinite(position.x) || !Double.isFinite(position.y) || !Double.isFinite(position.z)) {
            return false;
        }
        if (!Double.isFinite(t.tiltDeg) || !Double.isFinite(sim.gravity())) {
            return false;
        }
        for (int i = 0; i < t.motorDuty.length; i++) {
            if (!Double.isFinite(t.motorDuty[i])) {
                return false;
            }
        }
        return true;
    }

    private static Phase fly(String what, int ticks, boolean arm, float extraThrottle, @Nullable BooleanSupplier until) {
        return (ctx, waited) -> {
            PilotSession session = PilotSession.INSTANCE;
            DroneSim sim = session.sim();
            if (sim == null) {
                return StepResult.fail(what + ": Sitzung verloren");
            }
            Telemetry t = sim.telemetry();
            float throttle = extraThrottle <= 0.0F ? 0.0F : (float) Math.min(1.0, t.hoverThrottleStick + extraThrottle);
            session.setTestInput(new StickPositions(0.0F, 0.0F, 0.0F, throttle), arm, StabMode.ANGLE);
            if (!finite(t, sim, session.comPosition(new Vector3d()))) {
                return StepResult.fail(what + ": Flugzustand nicht endlich");
            }
            if (until != null) {
                if (until.getAsBoolean()) {
                    return null;
                }
                return waited < ticks ? StepResult.pending() : StepResult.fail(what);
            }
            return waited < ticks ? StepResult.pending() : null;
        };
    }

    private static StepResult flightStep(SelfTest.StepContext ctx) {
        AtomicReference<Double> startY = keep(ctx, "start_y", AtomicReference::new);
        AtomicReference<String> climb = keep(ctx, "climb", AtomicReference::new);
        Consumer<MinecraftServer> cleanup = server -> {
            QuadcopterEntity d = serverDrone(server);
            if (d != null) {
                d.setPilot(null);
                d.discard();
            }
            ServerPlayer player = player(server);
            player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
            rule(server, true);
        };
        PilotSession session = PilotSession.INSTANCE;
        return script(ctx, List.of(
                (c, waited) -> c.tick() < DronePiloting.REMOTE_DEBOUNCE_TICKS + 2 ? StepResult.pending() : null,
                server("Start-Sperre und Start", PartFreedomSelfTests::place),
                await("keine Sichtflug-Sitzung mit Regel aus", () -> {
                    QuadcopterEntity drone = session.drone();
                    return session.isLineOfSight() && drone != null && drone.getId() == DRONE_ID.get() && session.sim() != null && clientRule(false);
                }),
                fly("entschärft warten", 10, false, 0.0F, null),
                fly("unmögliche Drohne schaltet mit Regel aus nicht scharf", 60, true, 0.0F, () -> {
                    DroneSim sim = session.sim();
                    if (sim != null && sim.telemetry().armed) {
                        startY.set(session.comPosition(new Vector3d()).y);
                        return true;
                    }
                    return false;
                }),
                fly("unmögliche Drohne steigt nicht", 400, true, 0.08F, () -> {
                    DroneSim sim = session.sim();
                    double climbed = session.comPosition(new Vector3d()).y - startY.get();
                    if (sim != null && sim.telemetry().armed && climbed > 2.0) {
                        climb.set(String.format(Locale.ROOT, "gestiegen um %.1f m, Neigung %.1f°, Schwebegas %.0f %%", climbed,
                                sim.telemetry().tiltDeg, 100.0 * sim.telemetry().hoverThrottleStick));
                        return true;
                    }
                    return false;
                }),
                server("Regel an im Flug", server -> rule(server, true)),
                fly("Regel an kam nicht am Client an", 100, true, 0.02F, () -> clientRule(true)),
                fly("weiterfliegen mit Regel an", 30, true, 0.02F, null),
                (c, waited) -> {
                    DroneSim sim = session.sim();
                    if (sim == null || !sim.telemetry().armed) {
                        return StepResult.fail("Drohne im Flug entschärft, als die Regel anging: "
                                + (sim == null ? "-" : sim.telemetry().lastDisarmReason));
                    }
                    return null;
                },
                fly("entschärft nicht", 60, false, 0.0F, () -> session.sim() != null && !session.sim().telemetry().armed),
                fly("entschärft halten", 20, false, 0.0F, null),
                fly("schaltet mit Regel an wieder scharf oder ohne Sperre", 60, true, 0.0F, () -> {
                    DroneSim sim = session.sim();
                    return sim != null && !sim.telemetry().armed && sim.telemetry().armBlockers != 0
                            && ArmBlocker.BUILD_INVALID.isSet(sim.telemetry().armBlockers);
                }),
                (c, waited) -> {
                    session.clearTestInput();
                    if (session.isActive()) {
                        session.requestStop();
                        return waited < WAIT_TICKS ? StepResult.pending() : StepResult.fail("Sitzung endet nicht");
                    }
                    return null;
                },
                server("Aufräumen", cleanup),
                (c, waited) -> {
                    c.log("unmögliche Drohne " + IMPOSSIBLE.frame().id().getPath() + "/" + IMPOSSIBLE.motor().id().getPath() + "/"
                            + IMPOSSIBLE.prop().id().getPath() + "/" + IMPOSSIBLE.battery().id().getPath() + ": Start mit Regel an gesperrt, "
                            + climb.get() + ", nach dem Entschärfen mit Regel an BUILD_INVALID");
                    return null;
                }), cleanup);
    }

    public static Screenshots.Scene workbenchScene(boolean hoverResult) {
        return new WorkbenchScene(hoverResult);
    }

    private static final class WorkbenchScene implements Screenshots.Scene {
        private final boolean hoverResult;
        private @Nullable BlockPos pos;
        private @Nullable BlockState previous;

        private WorkbenchScene(boolean hoverResult) {
            this.hoverResult = hoverResult;
        }

        @Override
        public StepResult prepare(SelfTest.StepContext ctx) {
            Map<String, Object> state = ctx.state();
            if (!state.containsKey(FUTURE)) {
                state.put(FUTURE, ctx.onServer(server -> {
                    ServerPlayer player = player(server);
                    rule(server, false);
                    BlockPos at = player.blockPosition().offset(-2, 0, 3);
                    this.previous = player.level().getBlockState(at);
                    this.pos = at;
                    DroneWorkbenchMenu menu = openBench(player, at, IMPOSSIBLE);
                    if (!resultIsImpossibleDrone(menu)) {
                        throw new IllegalStateException("kein Ergebnis in der Werkbank mit Regel aus: " + menu.check());
                    }
                }));
                return StepResult.pending();
            }
            CompletableFuture<?> future = (CompletableFuture<?>) state.get(FUTURE);
            if (!future.isDone()) {
                return StepResult.pending();
            }
            if (future.isCompletedExceptionally()) {
                return StepResult.fail("Werkbank: " + future.exceptionNow().getMessage());
            }
            DroneWorkbenchMenu menu = clientMenu();
            if (menu == null || !clientRule(false) || !resultIsImpossibleDrone(menu)) {
                return ctx.tick() > 200 ? StepResult.fail("Werkbank-Screen mit unmöglicher Drohne fehlt") : StepResult.pending();
            }
            return StepResult.pass();
        }

        @Override
        public void hold(SelfTest.StepContext ctx) {
            Minecraft mc = Minecraft.getInstance();
            if (!this.hoverResult || !(mc.gui.screen() instanceof DroneWorkbenchScreen screen)) {
                Screenshots.parkMouse(mc);
                return;
            }
            Slot result = screen.getMenu().getSlot(DroneWorkbenchMenu.RESULT_SLOT);
            double guiX = (screen.width - WorkbenchLayout.WIDTH) / 2 + result.x + 8;
            double guiY = (screen.height - WorkbenchLayout.HEIGHT) / 2 + result.y + 8;
            double x = guiX * mc.getWindow().getScreenWidth() / mc.getWindow().getGuiScaledWidth();
            double y = guiY * mc.getWindow().getScreenHeight() / mc.getWindow().getGuiScaledHeight();
            mc.mouseHandler.onMove(mc.getWindow().handle(), x, y, 0.0, 0.0);
        }

        @Override
        public void finish(SelfTest.StepContext ctx) {
            BlockPos at = this.pos;
            BlockState before = this.previous;
            this.pos = null;
            ctx.onServer(server -> {
                if (at != null && before != null) {
                    closeBench(player(server), at, before);
                }
                rule(server, true);
            });
        }
    }
}
