package de.lardtom.propwash.interact.client;

import de.lardtom.propwash.accessory.AccessoryBehaviors;
import de.lardtom.propwash.accessory.AccessoryModule;
import de.lardtom.propwash.accessory.AccessoryPayload;
import de.lardtom.propwash.accessory.MountPoses;
import de.lardtom.propwash.accessory.client.AccessoryActionInput;
import de.lardtom.propwash.accessory.client.AccessoryScreen;
import de.lardtom.propwash.accessory.dispense.DispenseOrigin;
import de.lardtom.propwash.api.PropwashApi;
import de.lardtom.propwash.api.accessory.AccessoryInventory;
import de.lardtom.propwash.api.accessory.DispenseResult;
import de.lardtom.propwash.api.accessory.MountPose;
import de.lardtom.propwash.api.part.AccessorySlot;
import de.lardtom.propwash.build.AccessoryMount;
import de.lardtom.propwash.build.AccessoryType;
import de.lardtom.propwash.build.DroneBuild;
import de.lardtom.propwash.core.CoreComponents;
import de.lardtom.propwash.dev.AccFixSpeedRecorder;
import de.lardtom.propwash.dev.SelfTest;
import de.lardtom.propwash.dev.SelfTest.StepResult;
import de.lardtom.propwash.dev.client.Screenshots;
import de.lardtom.propwash.dev.example.ExampleDispenser;
import de.lardtom.propwash.drone.DroneModule;
import de.lardtom.propwash.drone.QuadcopterEntity;
import de.lardtom.propwash.drone.QuadcopterItem;
import de.lardtom.propwash.drone.client.PilotSession;
import de.lardtom.propwash.input.StickPositions;
import de.lardtom.propwash.interact.DroneCargo;
import de.lardtom.propwash.osd.OsdData;
import de.lardtom.propwash.osd.OsdWarnings;
import de.lardtom.propwash.osd.client.LiveOsdSource;
import de.lardtom.propwash.sim.AirframeSpec;
import de.lardtom.propwash.sim.DroneSim;
import de.lardtom.propwash.sim.StabMode;
import de.lardtom.propwash.sim.Telemetry;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.item.PrimedTnt;
import net.minecraft.world.entity.projectile.arrow.Arrow;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.ItemContainerContents;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicReference;

public final class AccessoryFixSelfTests {
    public static final String GROUP = "addon-api-17: Zubehör-Befunde – ";
    public static final double SPEED = AccFixSpeedRecorder.SPEED;
    public static final int MEASURE_TICKS = AccFixSpeedRecorder.MEASURE_TICKS;
    public static final int STACK = 64;
    private static final String FUTURE = InteractSelfTests.FUTURE;
    private static final String SHOT = "accfix_shot";
    private static final String BASE_MASS = "accfix_base_mass";
    private static final String MAX_HEIGHT = "accfix_max_height";
    private static final String STEADY = "accfix_steady";
    private static final String REPORT_KEY = "accfix_report";
    private static final String RESIZED = "accfix_resized";
    private static final double STONE_GRAMS = 50.0;
    private static final double MASS_TOLERANCE_KG = 0.02;
    private static final double GROUNDED_HEIGHT = 0.15;
    private static final double CLIMB_HEIGHT = 0.6;
    private static final int THROTTLE_TICKS = 60;
    private static final int CORRIDOR = 28;
    private static final int CORRIDOR_SIDE = 3;
    private static final int CORRIDOR_HEIGHT = 5;
    private static final int WARMUP_TICKS = 12;
    private static final int PRESS_EVERY = 7;
    private static final AtomicReference<String> REPORT = new AtomicReference<>("");
    private static final List<Integer> SPAWNED = new CopyOnWriteArrayList<>();
    private static final AtomicReference<@Nullable GameType> MODE = new AtomicReference<>();

    private AccessoryFixSelfTests() {
    }

    public static void register() {
        SelfTest.clientStep(GROUP + "abgestellt feuern: Mündung über dem Boden, Pfeil FIRED, TNT auf der Standfläche, Stein an der Mündung BLOCKED (freestyle_5, cinewhoop_3)",
                400, ctx -> serverStep(ctx, AccessoryFixSelfTests::parked));
        SelfTest.clientStep(GROUP + "Wartung und Drohnen-Item zeigen die echte Beladung und „überladen“", 400, AccessoryFixSelfTests::serviceStep);
        SelfTest.clientStep(GROUP + "schwer beladen: 12,8 kg hebt nicht ab, überladen mit OSD-Warnung, 3,2 kg zählt voll und steigt", 2000,
                AccessoryFixSelfTests::heavyStep);
        SelfTest.clientStep(GROUP + "Tempo im Pilotenflug: 40 Server-Ticks bei 5,6 m/s nie 0, Aktionen zwischen den Ticks mit Tempo", 2000,
                AccessoryFixSelfTests::speedStep);
        SelfTest.clientStep(GROUP + "Aufräumen", 300, AccessoryFixSelfTests::cleanupStep);
        AccessoryFramesSelfTests.register();
        AccessoryTabSelfTests.register();
    }

    @FunctionalInterface
    private interface ServerCheck {
        String run(MinecraftServer server);
    }

    private static StepResult serverStep(SelfTest.StepContext ctx, ServerCheck check) {
        if (!AccessoryGapsSelfTests.ready()) {
            ctx.log("Beispiel-Add-on aus, übersprungen");
            return StepResult.pass();
        }
        if (InteractSelfTests.phase(ctx) == 0) {
            ctx.state().put(FUTURE, ctx.onServer(server -> REPORT.set(check.run(server))));
            InteractSelfTests.next(ctx);
            return StepResult.pending();
        }
        StepResult server = InteractSelfTests.await(ctx, "Server");
        if (server.status() == StepResult.Status.PASS) {
            ctx.log(REPORT.get());
        }
        return server;
    }

    static DroneBuild dispenserOn(DroneBuild base) {
        return base.withAccessory(AccessoryGapsSelfTests.type(ExampleDispenser.DISPENSER));
    }

    static ItemStack stones(int count) {
        return new ItemStack(Items.STONE, count);
    }

    static ItemStack fullShulker() {
        List<ItemStack> inside = new ArrayList<>();
        for (int i = 0; i < 27; i++) {
            inside.add(stones(STACK));
        }
        ItemStack box = new ItemStack(Items.SHULKER_BOX);
        box.set(DataComponents.CONTAINER, ItemContainerContents.fromItems(inside));
        return box;
    }

    private static QuadcopterEntity park(ServerLevel level, DroneBuild build, Vec3 pos) {
        QuadcopterEntity drone = DroneModule.DRONE.get().create(level, EntitySpawnReason.COMMAND);
        if (drone == null) {
            throw new IllegalStateException("Drohne nicht erzeugt");
        }
        ItemStack stack = new ItemStack(DroneModule.DRONE_ITEM.get());
        stack.set(CoreComponents.DRONE_BUILD.get(), build);
        stack.set(CoreComponents.BATTERY_CHARGE.get(), 1.0F);
        drone.loadFromStack(stack);
        drone.snapTo(pos.x, pos.y, pos.z, -90.0F, 0.0F);
        level.addFreshEntity(drone);
        drone.resetTo(pos, InteractSelfTests.upright());
        SPAWNED.add(drone.getId());
        return drone;
    }

    private static AccessoryInventory front(QuadcopterEntity drone) {
        return PropwashApi.accessories().inventory(drone, AccessorySlot.FRONT).orElseThrow(() -> new IllegalStateException("Werfer ohne Inventar"));
    }

    private static Vec3 forward(QuadcopterEntity drone) {
        Vector3f f = new Quaternionf(drone.orientation()).transform(new Vector3f(0.0F, 0.0F, -1.0F));
        return new Vec3(f.x, 0.0, f.z).normalize();
    }

    private static String parked(MinecraftServer server) {
        ServerPlayer player = InteractSelfTests.player(server);
        int[] arena = InteractSelfTests.arena(player);
        ServerLevel level = player.level();
        MODE.compareAndSet(null, player.gameMode());
        player.setGameMode(GameType.CREATIVE);
        ExampleDispenser.consume(true);
        ExampleDispenser.speedScale(1.0);
        Vec3 ground = InteractSelfTests.start(arena);
        List<String> out = new ArrayList<>();
        for (DroneBuild base : List.of(DroneBuild.FREESTYLE, DroneBuild.CINEWHOOP)) {
            QuadcopterEntity drone = park(level, dispenserOn(base), ground);
            String frame = base.frame().key();
            MountPose pose = MountPoses.of(drone, AccessoryMount.FRONT).orElseThrow(() -> new IllegalStateException(frame + ": keine Halterung vorn"));
            double sunk = ground.y - pose.front().y;
            if (!(sunk > 0.0)) {
                throw new IllegalStateException(frame + ": Vorderflächen-Mitte liegt nicht im Boden (" + sunk + "), Testlage stimmt nicht");
            }
            DispenseOrigin origin = DispenseOrigin.of(level, drone, AccessoryMount.FRONT, pose);
            double above = origin.muzzle().y - ground.y;
            if (!(above > 0.0)) {
                throw new IllegalStateException(frame + ": Mündung der abgestellten Drohne " + above + " m unter dem Boden");
            }
            AccessoryInventory inventory = front(drone);
            Vec3 forward = forward(drone);
            inventory.set(0, new ItemStack(Items.ARROW, 2));
            DispenseResult arrowResult = AccessoryGapsSelfTests.fire(drone, player);
            if (arrowResult.status() != DispenseResult.Status.FIRED || !(arrowResult.entity().orElse(null) instanceof Arrow arrow)) {
                throw new IllegalStateException(frame + ": abgestellt " + arrowResult.status() + " statt FIRED");
            }
            double arrowY = arrow.getY() - ground.y;
            double along = arrow.getDeltaMovement().dot(forward);
            arrow.discard();
            if (arrowY < 0.0 || along < 0.8) {
                throw new IllegalStateException(String.format(Locale.ROOT, "%s: Pfeil startet %.3f m über dem Boden mit %.2f B/t nach vorn", frame, arrowY, along));
            }
            inventory.set(0, new ItemStack(Items.TNT));
            DispenseResult tntResult = AccessoryGapsSelfTests.fire(drone, player);
            if (tntResult.status() != DispenseResult.Status.PLACED || !(tntResult.entity().orElse(null) instanceof PrimedTnt tnt)) {
                throw new IllegalStateException(frame + ": TNT abgestellt " + tntResult.status() + " statt PLACED");
            }
            double tntY = tnt.getY() - ground.y;
            tnt.discard();
            if (tntY < -1.0E-6) {
                throw new IllegalStateException(frame + ": TNT startet " + tntY + " m unter der Standfläche");
            }
            BlockPos muzzleBlock = BlockPos.containing(origin.muzzle());
            level.setBlockAndUpdate(muzzleBlock, Blocks.STONE.defaultBlockState());
            inventory.set(0, new ItemStack(Items.ARROW, 1));
            DispenseResult blocked = AccessoryGapsSelfTests.fire(drone, player);
            level.setBlockAndUpdate(muzzleBlock, Blocks.AIR.defaultBlockState());
            blocked.entity().ifPresent(Entity::discard);
            if (blocked.status() != DispenseResult.Status.BLOCKED || inventory.get(0).getCount() != 1) {
                throw new IllegalStateException(frame + ": Stein an der Mündung ergibt " + blocked.status() + ", Rest " + inventory.get(0));
            }
            out.add(String.format(Locale.ROOT, "%s: Vorderflächen-Mitte %.0f mm im Boden, Mündung %.0f mm darüber, Pfeil FIRED (%.2f B/t vorn), TNT PLACED %.2f m über der Standfläche, Stein an der Mündung BLOCKED",
                    frame, sunk * 1000.0, above * 1000.0, along, tntY));
            drone.discard();
        }
        return String.join("; ", out);
    }

    private static StepResult serviceStep(SelfTest.StepContext ctx) {
        if (!AccessoryGapsSelfTests.ready()) {
            return StepResult.pass();
        }
        Minecraft mc = Minecraft.getInstance();
        Map<String, Object> state = ctx.state();
        switch (InteractSelfTests.phase(ctx)) {
            case 0 -> {
                if (Screenshots.enabled() && !state.containsKey(RESIZED)) {
                    StepResult resized = Screenshots.resizeWindow(ctx);
                    if (resized.status() != StepResult.Status.PASS) {
                        return resized;
                    }
                    state.put(RESIZED, true);
                    Screenshots.hideMarker(true);
                }
                state.put(FUTURE, ctx.onServer(server -> {
                    ServerPlayer player = InteractSelfTests.player(server);
                    int[] arena = InteractSelfTests.arena(player);
                    QuadcopterEntity drone = park(player.level(), dispenserOn(DroneBuild.FREESTYLE), InteractSelfTests.start(arena));
                    AccessoryInventory inventory = front(drone);
                    inventory.set(0, fullShulker());
                    inventory.set(1, stones(STACK));
                    if (!AccessoryModule.openMenu(player, drone)) {
                        throw new IllegalStateException("Fenster „Zubehör“ öffnet nicht");
                    }
                }));
                InteractSelfTests.next(ctx);
                return StepResult.pending();
            }
            case 1 -> {
                StepResult server = InteractSelfTests.await(ctx, "Wartung");
                if (server.status() != StepResult.Status.PASS) {
                    return server;
                }
                if (!(mc.gui.screen() instanceof AccessoryScreen screen)) {
                    return InteractSelfTests.since(ctx) > 100 ? StepResult.fail("Fenster „Zubehör“ fehlt: " + mc.gui.screen()) : StepResult.pending();
                }
                QuadcopterEntity drone = screen.getMenu().drone();
                AccessoryType type = screen.getMenu().accessory(AccessoryMount.FRONT);
                if (drone == null || type == null || drone.accessoryContents().at(AccessoryMount.FRONT).isEmpty()) {
                    return InteractSelfTests.since(ctx) > 100 ? StepResult.fail("Inhalt kommt nicht beim Client an") : StepResult.pending();
                }
                double grams = AccessoryBehaviors.INSTANCE.payloadGrams(type, drone.accessoryContents().at(AccessoryMount.FRONT));
                double expected = DroneCargo.grams(fullShulker()) + STONE_GRAMS * STACK;
                if (Math.abs(grams - expected) > 1.0 || !AccessoryPayload.overloaded(type, grams)) {
                    return StepResult.fail(String.format(Locale.ROOT, "Beladung %.0f g statt %.0f g oder nicht überladen", grams, expected));
                }
                String label = Component.translatable(AccessoryScreen.OVERLOADED_KEY, Math.round(grams)).getString();
                String tooltip = tooltip(mc, drone.toStack());
                String line = Component.translatable(QuadcopterItem.OVERLOADED_KEY, Math.round(AccessoryPayload.MAX_LADEN_GRAMS / 1000.0)).getString();
                if (!tooltip.contains(line)) {
                    return StepResult.fail("Drohnen-Item ohne „" + line + "“: " + tooltip);
                }
                state.put(REPORT_KEY, String.format(Locale.ROOT, "Wartung: „%s“ (Flugmodell %.0f g), Drohnen-Item: „%s“", label,
                        AccessoryPayload.flightGrams(type, grams), line));
                InteractSelfTests.next(ctx);
                return StepResult.pending();
            }
            case 2 -> {
                if (Screenshots.enabled() && !state.containsKey(SHOT)) {
                    if (InteractSelfTests.since(ctx) < 20) {
                        Screenshots.parkMouse(mc);
                        Screenshots.clearOverlays(mc);
                        return StepResult.pending();
                    }
                    state.put(SHOT, Screenshots.capture("accessory_overloaded"));
                    return StepResult.pending();
                }
                if (state.containsKey(SHOT)) {
                    CompletableFuture<?> shot = (CompletableFuture<?>) state.get(SHOT);
                    if (!shot.isDone()) {
                        return StepResult.pending();
                    }
                    if (shot.isCompletedExceptionally()) {
                        return StepResult.fail("Bildschirmfoto: " + shot.exceptionNow());
                    }
                    ctx.log("Bildschirmfoto " + Screenshots.dir().resolve("accessory_overloaded.png"));
                }
                mc.gui.setScreen(null);
                if (state.containsKey(RESIZED)) {
                    Screenshots.hideMarker(false);
                    Screenshots.restoreWindow(ctx);
                }
                ctx.log((String) state.get(REPORT_KEY));
                state.put(FUTURE, ctx.onServer(AccessoryFixSelfTests::discardSpawned));
                InteractSelfTests.next(ctx);
                return StepResult.pending();
            }
            default -> {
                return InteractSelfTests.await(ctx, "Wartung aufräumen");
            }
        }
    }

    private static String tooltip(Minecraft mc, ItemStack stack) {
        if (mc.level == null || mc.player == null) {
            return "";
        }
        List<Component> lines = stack.getTooltipLines(Item.TooltipContext.of(mc.level), mc.player, TooltipFlag.NORMAL);
        StringBuilder out = new StringBuilder();
        for (Component line : lines) {
            out.append(line.getString()).append(" | ");
        }
        return out.toString();
    }

    private static StepResult heavyStep(SelfTest.StepContext ctx) {
        if (!AccessoryGapsSelfTests.ready()) {
            return StepResult.pass();
        }
        PilotSession session = PilotSession.INSTANCE;
        Map<String, Object> state = ctx.state();
        int phase = InteractSelfTests.phase(ctx);
        if (phase == 0) {
            state.put(FUTURE, ctx.onServer(server -> {
                startFlight(server, dispenserOn(DroneBuild.FREESTYLE));
                AccessoryInventory inventory = front(InteractSelfTests.serverDrone(server));
                for (int slot = 0; slot < ExampleDispenser.DISPENSER_SLOTS; slot++) {
                    inventory.set(slot, stones(STACK));
                }
            }));
            InteractSelfTests.next(ctx);
            return StepResult.pending();
        }
        if (phase == 1) {
            return InteractSelfTests.started(ctx, "Beladung", null);
        }
        if (phase > 7) {
            return InteractSelfTests.stop(ctx, server -> {
            });
        }
        DroneSim sim = session.sim();
        QuadcopterEntity drone = session.drone();
        int[] arena = InteractSelfTests.ARENA.get();
        if (sim == null || drone == null || arena == null) {
            return StepResult.fail("Sitzung verloren in Phase " + phase);
        }
        Telemetry t = sim.telemetry();
        double height = drone.getY() - arena[1];
        double base = AirframeSpec.of(drone.build()).massKg;
        double mass = sim.params().massKg;
        switch (phase) {
            case 2 -> {
                session.setTestInput(new StickPositions(0.0F, 0.0F, 0.0F, 0.0F), InteractSelfTests.since(ctx) >= 10, StabMode.ANGLE);
                double wanted = base + STACK * ExampleDispenser.DISPENSER_SLOTS * STONE_GRAMS / 1000.0;
                if (Math.abs(mass - wanted) > MASS_TOLERANCE_KG || !t.armed) {
                    return InteractSelfTests.since(ctx) > 200 ? StepResult.fail(String.format(Locale.ROOT,
                            "12,8 kg: Flugmodell %.3f kg statt %.3f kg, scharf %s, Sperren %d", mass, wanted, t.armed, t.armBlockers)) : StepResult.pending();
                }
                state.put(BASE_MASS, base);
                state.put(MAX_HEIGHT, height);
                InteractSelfTests.next(ctx);
                return StepResult.pending();
            }
            case 3 -> {
                session.setTestInput(new StickPositions(0.0F, 0.0F, 0.0F, 1.0F), true, StabMode.ANGLE);
                state.put(MAX_HEIGHT, Math.max((double) state.get(MAX_HEIGHT), height));
                if (InteractSelfTests.since(ctx) < THROTTLE_TICKS) {
                    return StepResult.pending();
                }
                double max = (double) state.get(MAX_HEIGHT);
                if (!t.armed || max > GROUNDED_HEIGHT) {
                    return StepResult.fail(String.format(Locale.ROOT, "12,8 kg: höchstens %.2f m bei Vollgas, scharf %s", max, t.armed));
                }
                REPORT.set(String.format(Locale.ROOT, "12,8 kg: Flugmodell %.3f kg (leer %.3f kg), 3 s Vollgas höchstens %.2f m", mass, base, max));
                session.setTestInput(new StickPositions(0.0F, 0.0F, 0.0F, 0.0F), true, StabMode.ANGLE);
                state.put(FUTURE, ctx.onServer(server -> front(InteractSelfTests.serverDrone(server)).set(0, fullShulker())));
                InteractSelfTests.next(ctx);
                return StepResult.pending();
            }
            case 4 -> {
                session.setTestInput(new StickPositions(0.0F, 0.0F, 0.0F, 0.0F), true, StabMode.ANGLE);
                AccessoryType type = drone.build().accessories().at(AccessoryMount.FRONT).map(AccessoryType::unladen).orElse(null);
                double wanted = type == null ? Double.NaN : base + AccessoryPayload.massStep(type, Double.MAX_VALUE) / 1000.0;
                OsdData data = new OsdData();
                new LiveOsdSource().fill(data);
                List<OsdWarnings.Warning> warnings = new ArrayList<>();
                data.active = true;
                OsdWarnings.collect(data, warnings);
                if (!AccessoryPayload.overloaded(drone) || !data.overloaded || !warnings.contains(OsdWarnings.Warning.OVERLOADED)
                        || Math.abs(mass - wanted) > MASS_TOLERANCE_KG) {
                    return InteractSelfTests.since(ctx) > 200 ? StepResult.fail(String.format(Locale.ROOT,
                            "überladen: erkannt %s, OSD %s %s, Flugmodell %.3f kg statt %.3f kg", AccessoryPayload.overloaded(drone), data.overloaded,
                            warnings, mass, wanted)) : StepResult.pending();
                }
                REPORT.set(REPORT.get() + String.format(Locale.ROOT, "; überladen (Shulkerkiste voll Stein): OSD „%s“, Flugmodell %.3f kg (Grenze 20 kg je Zubehör)",
                        Component.translatable(OsdWarnings.Warning.OVERLOADED.translationKey()).getString(), mass));
                state.put(FUTURE, ctx.onServer(server -> {
                    AccessoryInventory inventory = front(InteractSelfTests.serverDrone(server));
                    inventory.set(0, stones(STACK));
                    for (int slot = 1; slot < ExampleDispenser.DISPENSER_SLOTS; slot++) {
                        inventory.set(slot, ItemStack.EMPTY);
                    }
                }));
                InteractSelfTests.next(ctx);
                return StepResult.pending();
            }
            case 5 -> {
                session.setTestInput(new StickPositions(0.0F, 0.0F, 0.0F, 0.0F), true, StabMode.ANGLE);
                double wanted = base + STACK * STONE_GRAMS / 1000.0;
                if (Math.abs(mass - wanted) > MASS_TOLERANCE_KG || AccessoryPayload.overloaded(drone)) {
                    return InteractSelfTests.since(ctx) > 200 ? StepResult.fail(String.format(Locale.ROOT, "3,2 kg: Flugmodell %.3f kg statt %.3f kg", mass, wanted))
                            : StepResult.pending();
                }
                state.put(MAX_HEIGHT, height);
                state.put(STEADY, mass);
                InteractSelfTests.next(ctx);
                return StepResult.pending();
            }
            case 6 -> {
                session.setTestInput(new StickPositions(0.0F, 0.0F, 0.0F, 1.0F), true, StabMode.ANGLE);
                double start = (double) state.get(MAX_HEIGHT);
                if (!t.armed) {
                    return StepResult.fail("3,2 kg: beim Steigen entschärft (" + t.lastDisarmReason + ")");
                }
                if (height - start < CLIMB_HEIGHT) {
                    return InteractSelfTests.since(ctx) > THROTTLE_TICKS ? StepResult.fail(String.format(Locale.ROOT, "3,2 kg steigt nicht: %.2f m nach 3 s",
                            height - start)) : StepResult.pending();
                }
                REPORT.set(REPORT.get() + String.format(Locale.ROOT, "; 3,2 kg (über der alten Grenze von 2 kg): Flugmodell %.3f kg, %.1f m in %d Ticks Vollgas",
                        (double) state.get(STEADY), height - start, InteractSelfTests.since(ctx)));
                InteractSelfTests.next(ctx);
                return StepResult.pending();
            }
            default -> {
                session.setTestInput(new StickPositions(0.0F, 0.0F, 0.0F, (float) t.hoverThrottleStick), true, StabMode.ANGLE);
                ctx.log(REPORT.get());
                InteractSelfTests.next(ctx);
                return StepResult.pending();
            }
        }
    }

    private static void startFlight(MinecraftServer server, DroneBuild build) {
        InteractSelfTests.discard(server, InteractSelfTests.DRONE_ID.getAndSet(-1));
        ServerPlayer player = InteractSelfTests.player(server);
        int[] arena = InteractSelfTests.arena(player);
        MODE.compareAndSet(null, player.gameMode());
        ExampleDispenser.consume(true);
        ExampleDispenser.speedScale(1.0);
        InteractSelfTests.fly(player, build, InteractSelfTests.start(arena));
    }

    private static void corridor(ServerLevel level, int[] arena) {
        for (int dx = -2; dx <= CORRIDOR; dx++) {
            for (int dz = -CORRIDOR_SIDE; dz <= CORRIDOR_SIDE; dz++) {
                BlockPos floor = new BlockPos(arena[0] + dx, arena[1] - 1, arena[2] + dz);
                if (level.getBlockState(floor).getCollisionShape(level, floor).isEmpty()) {
                    level.setBlockAndUpdate(floor, Blocks.GRASS_BLOCK.defaultBlockState());
                }
                for (int dy = 0; dy < CORRIDOR_HEIGHT; dy++) {
                    BlockPos pos = new BlockPos(arena[0] + dx, arena[1] + dy, arena[2] + dz);
                    if (!level.getBlockState(pos).isAir()) {
                        level.setBlockAndUpdate(pos, Blocks.AIR.defaultBlockState());
                    }
                }
            }
        }
    }

    private static StepResult speedStep(SelfTest.StepContext ctx) {
        if (!AccessoryGapsSelfTests.ready()) {
            return StepResult.pass();
        }
        PilotSession session = PilotSession.INSTANCE;
        Map<String, Object> state = ctx.state();
        int phase = InteractSelfTests.phase(ctx);
        switch (phase) {
            case 0 -> {
                state.put(FUTURE, ctx.onServer(server -> {
                    AccFixSpeedRecorder.hook();
                    AccFixSpeedRecorder.reset();
                    startFlight(server, dispenserOn(DroneBuild.FREESTYLE));
                    corridor(InteractSelfTests.player(server).level(), InteractSelfTests.ARENA.get());
                }));
                InteractSelfTests.next(ctx);
                return StepResult.pending();
            }
            case 1 -> {
                return InteractSelfTests.started(ctx, "Tempo", null);
            }
            case 2, 3 -> {
                return InteractSelfTests.climb(ctx, "Tempo");
            }
            case 4 -> {
                InteractSelfTests.hover(session);
                if (!InteractSelfTests.settled(session, ctx)) {
                    return InteractSelfTests.since(ctx) > 400 ? StepResult.fail("schwebt nicht ruhig") : StepResult.pending();
                }
                InteractSelfTests.next(ctx);
                return StepResult.pending();
            }
            case 5 -> {
                InteractSelfTests.hover(session);
                QuadcopterEntity drone = session.drone();
                if (drone == null) {
                    return StepResult.fail("Sitzung verloren");
                }
                Vec3 f = forward(drone);
                InteractSelfTests.push(session.sim(), f.x * SPEED, 0.0, f.z * SPEED);
                int since = InteractSelfTests.since(ctx);
                if (since == WARMUP_TICKS) {
                    int id = drone.getId();
                    ctx.onServer(server -> AccFixSpeedRecorder.start(id, true));
                }
                if (since > WARMUP_TICKS) {
                    boolean press = (since - WARMUP_TICKS) % PRESS_EVERY == 1;
                    AccessoryActionInput.INSTANCE.setTestBits(press ? 1 : 0);
                }
                String result = AccFixSpeedRecorder.result();
                if (result == null) {
                    return since > WARMUP_TICKS + MEASURE_TICKS * 3 ? StepResult.fail("Server misst kein Tempo: " + AccFixSpeedRecorder.progress()) : StepResult.pending();
                }
                AccessoryActionInput.INSTANCE.setTestBits(null);
                InteractSelfTests.push(session.sim(), 0.0, 0.0, 0.0);
                if (!AccFixSpeedRecorder.passed()) {
                    return StepResult.fail(result);
                }
                ctx.log(result);
                InteractSelfTests.next(ctx);
                return StepResult.pending();
            }
            default -> {
                AccessoryActionInput.INSTANCE.setTestBits(null);
                return InteractSelfTests.stop(ctx, server -> AccFixSpeedRecorder.reset());
            }
        }
    }

    private static void discardSpawned(MinecraftServer server) {
        for (int id : List.copyOf(SPAWNED)) {
            InteractSelfTests.discard(server, id);
        }
        SPAWNED.clear();
    }

    private static StepResult cleanupStep(SelfTest.StepContext ctx) {
        if (!AccessoryGapsSelfTests.ready()) {
            return StepResult.pass();
        }
        if (InteractSelfTests.phase(ctx) == 0) {
            AccessoryActionInput.INSTANCE.setTestBits(null);
            Minecraft mc = Minecraft.getInstance();
            if (mc.gui.screen() != null) {
                mc.gui.setScreen(null);
            }
            InteractSelfTests.next(ctx);
        }
        return InteractSelfTests.stop(ctx, server -> {
            discardSpawned(server);
            AccFixSpeedRecorder.reset();
            ServerPlayer player = InteractSelfTests.player(server);
            GameType mode = MODE.getAndSet(null);
            if (mode != null) {
                player.setGameMode(mode);
            }
        });
    }
}
