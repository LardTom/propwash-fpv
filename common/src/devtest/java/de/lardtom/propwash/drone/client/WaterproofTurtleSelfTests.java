package de.lardtom.propwash.drone.client;

import de.lardtom.propwash.build.DroneBuild;
import de.lardtom.propwash.build.MotorType;
import de.lardtom.propwash.build.PropType;
import de.lardtom.propwash.content.BuildSlots;
import de.lardtom.propwash.content.DroneAssembly;
import de.lardtom.propwash.dev.SelfTest;
import de.lardtom.propwash.dev.SelfTest.StepResult;
import de.lardtom.propwash.dev.client.Screenshots;
import de.lardtom.propwash.drone.DroneModule;
import de.lardtom.propwash.drone.PilotMode;
import de.lardtom.propwash.drone.QuadcopterEntity;
import de.lardtom.propwash.drone.WaterSurface;
import de.lardtom.propwash.drone.coating.CoatingComponents;
import de.lardtom.propwash.drone.coating.CoatingRules;
import de.lardtom.propwash.drone.coating.WaterproofCoatingRecipe;
import de.lardtom.propwash.input.StickPositions;
import de.lardtom.propwash.osd.OsdData;
import de.lardtom.propwash.osd.client.OsdHud;
import de.lardtom.propwash.sim.ArmBlocker;
import de.lardtom.propwash.sim.DisarmReason;
import de.lardtom.propwash.sim.DroneSim;
import de.lardtom.propwash.sim.StabMode;
import de.lardtom.propwash.sim.Telemetry;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3d;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.Function;

public final class WaterproofTurtleSelfTests {
    static final String WATER = "wasserdicht: ";
    static final String TURTLE = "turtle: ";
    private static final String RUN = "run";
    private static final String FUTURE = "future";
    private static final int POOL_DZ = 12;
    private static final int POOL_HALF = 2;
    private static final int POOL_DEPTH = 3;
    private static final AtomicReference<String> PROBLEM = new AtomicReference<>();

    private WaterproofTurtleSelfTests() {
    }

    enum Kind {
        WATER,
        TURTLE
    }

    static void register() {
        SelfTest.clientStep(WATER + "Spray auf die platzierte Drohne, Rezept im Raster, Werkbank behält die Beschichtung nur mit gleicher Elektronik",
                400, WaterproofTurtleSelfTests::coatingStep);
        SelfTest.clientStep(WATER + "Flug im Becken – ohne Beschichtung kein Scharfschalten, beschichtet scharf, Props bremsen, mit Vollgas heraus",
                2400, ctx -> flight(ctx, Kind.WATER));
        SelfTest.clientStep(TURTLE + "kopfüber am Boden – ohne Turtle gesperrt, mit Turtle scharf, Roll dreht sie um, Motoren aus, Ausschalten entschärft",
                2400, ctx -> flight(ctx, Kind.TURTLE));
    }

    private static StepResult coatingStep(SelfTest.StepContext ctx) {
        Map<String, Object> state = ctx.state();
        if (!state.containsKey(FUTURE)) {
            PROBLEM.set(null);
            state.put(FUTURE, ctx.onServer(server -> PROBLEM.set(checkCoating(server))));
            return StepResult.pending();
        }
        CompletableFuture<?> future = (CompletableFuture<?>) state.get(FUTURE);
        if (!future.isDone()) {
            return StepResult.pending();
        }
        if (future.isCompletedExceptionally()) {
            return StepResult.fail("Server: " + future.exceptionNow());
        }
        String problem = PROBLEM.get();
        return problem == null ? StepResult.pass() : StepResult.fail(problem);
    }

    private static @Nullable String checkCoating(MinecraftServer server) {
        ServerPlayer player = Mode3dSelfTests.firstPlayer(server);
        ServerLevel level = player.level();
        ItemStack savedHand = player.getMainHandItem().copy();
        List<QuadcopterEntity> spawned = new ArrayList<>();
        try {
            QuadcopterEntity drone = spawn(level, player.position().add(2.0, 0.0, 2.0), spawned);
            ItemStack spray = new ItemStack(DroneModule.WATERPROOF_SPRAY.get());
            player.setItemSlot(EquipmentSlot.MAINHAND, spray);
            drone.interact(player, InteractionHand.MAIN_HAND, drone.position());
            if (!drone.waterproof()) {
                return "Rechtsklick mit dem Spray beschichtet die Drohne nicht";
            }
            int expected = player.hasInfiniteMaterials() ? 0 : CoatingRules.COST;
            int damage = player.getMainHandItem().getDamageValue();
            if (damage != expected) {
                return "Spray nach einer Drohne bei Schaden " + damage + " statt " + expected;
            }
            drone.interact(player, InteractionHand.MAIN_HAND, drone.position());
            if (player.getMainHandItem().getDamageValue() != expected) {
                return "zweites Sprühen auf eine beschichtete Drohne kostet Haltbarkeit";
            }
            ItemStack item = drone.toStack();
            if (!CoatingComponents.coated(item)) {
                return "aufgehobene Drohne verliert die Beschichtung";
            }
            QuadcopterEntity placed = spawn(level, player.position().add(-2.0, 0.0, 2.0), spawned);
            placed.loadFromStack(item);
            if (!placed.waterproof()) {
                return "platzierte Drohne aus dem Item ist nicht wasserdicht";
            }
            String crafting = checkCrafting(server, level);
            if (crafting != null) {
                return crafting;
            }
            return checkWorkbench(item);
        } finally {
            spawned.forEach(QuadcopterEntity::discard);
            player.setItemSlot(EquipmentSlot.MAINHAND, savedHand);
        }
    }

    private static QuadcopterEntity spawn(ServerLevel level, Vec3 at, List<QuadcopterEntity> spawned) {
        QuadcopterEntity drone = DroneModule.DRONE.get().create(level, EntitySpawnReason.COMMAND);
        if (drone == null) {
            throw new IllegalStateException("EntityType.create lieferte null");
        }
        drone.snapTo(at.x, at.y, at.z, 0.0F, 0.0F);
        drone.setBuild(DroneBuild.FREESTYLE);
        if (!level.addFreshEntity(drone)) {
            throw new IllegalStateException("addFreshEntity fehlgeschlagen");
        }
        spawned.add(drone);
        return drone;
    }

    private static @Nullable String checkCrafting(MinecraftServer server, ServerLevel level) {
        ResourceKey<Recipe<?>> shaped = ResourceKey.create(Registries.RECIPE, Identifier.fromNamespaceAndPath("propwash", "waterproof_spray"));
        if (server.getRecipeManager().byKey(shaped).isEmpty()) {
            return "Rezept propwash:waterproof_spray fehlt";
        }
        ItemStack plain = DroneAssembly.preset(DroneBuild.FREESTYLE);
        ItemStack can = new ItemStack(DroneModule.WATERPROOF_SPRAY.get());
        CraftingInput input = grid(plain, can);
        Optional<RecipeHolder<CraftingRecipe>> found = server.getRecipeManager().getRecipeFor(RecipeType.CRAFTING, input, level);
        if (found.isEmpty() || !found.get().id().equals(WaterproofCoatingRecipe.KEY)) {
            return "Raster mit Drohne und Spray findet " + found.map(h -> h.id().identifier().toString()).orElse("kein Rezept");
        }
        ItemStack result = found.get().value().assemble(input);
        if (!CoatingComponents.coated(result)) {
            return "Ergebnis im Raster ist nicht beschichtet";
        }
        ItemStack rest = found.get().value().getRemainingItems(input).get(sprayIndex(input));
        if (rest.getDamageValue() != CoatingRules.COST) {
            return "Spray bleibt mit Schaden " + rest.getDamageValue() + " statt " + CoatingRules.COST + " im Raster";
        }
        can.setDamageValue(CoatingRules.DURABILITY - CoatingRules.COST);
        CraftingInput last = grid(plain, can);
        if (!found.get().value().getRemainingItems(last).get(sprayIndex(last)).isEmpty()) {
            return "leeres Spray bleibt im Raster liegen";
        }
        if (server.getRecipeManager().getRecipeFor(RecipeType.CRAFTING, grid(result, new ItemStack(DroneModule.WATERPROOF_SPRAY.get())), level)
                .isPresent()) {
            return "beschichtete Drohne lässt sich noch einmal beschichten";
        }
        return null;
    }

    private static int sprayIndex(CraftingInput input) {
        for (int i = 0; i < input.size(); i++) {
            if (input.getItem(i).is(DroneModule.WATERPROOF_SPRAY.get())) {
                return i;
            }
        }
        throw new IllegalStateException("kein Spray im Raster");
    }

    private static CraftingInput grid(ItemStack drone, ItemStack can) {
        List<ItemStack> items = new ArrayList<>();
        for (int i = 0; i < 9; i++) {
            items.add(i == 0 ? drone.copy() : i == 4 ? can.copy() : ItemStack.EMPTY);
        }
        return CraftingInput.of(3, 3, items);
    }

    private static @Nullable String checkWorkbench(ItemStack coated) {
        List<ItemStack> parts = DroneAssembly.disassemble(coated);
        ItemStack stack = parts.get(BuildSlots.STACK);
        ItemStack frame = parts.get(BuildSlots.FRAME);
        ItemStack battery = parts.get(BuildSlots.BATTERY);
        ItemStack same = DroneAssembly.createDrone(DroneBuild.FREESTYLE, stack, frame, battery);
        if (!CoatingComponents.coated(same)) {
            return "zerlegt und gleich wieder gebaut: Beschichtung fehlt";
        }
        ItemStack props = DroneAssembly.createDrone(DroneBuild.FREESTYLE.withProp(PropType.P3_3B), stack, frame, battery);
        if (!CoatingComponents.coated(props)) {
            return "andere Props: Beschichtung fehlt";
        }
        DroneBuild motors = new DroneBuild(DroneBuild.FREESTYLE.frame(), DroneBuild.FREESTYLE.stack(), MotorType.M2306_1860,
                DroneBuild.FREESTYLE.prop(), DroneBuild.FREESTYLE.video(), DroneBuild.FREESTYLE.battery());
        if (CoatingComponents.coated(DroneAssembly.createDrone(motors, stack, frame, battery))) {
            return "andere Motoren: Beschichtung bleibt";
        }
        return null;
    }

    private static StepResult flight(SelfTest.StepContext ctx, Kind kind) {
        Run run = (Run) ctx.state().computeIfAbsent(RUN, key -> new Run(kind));
        try {
            StepResult result = run.tick(ctx);
            if (result.status() != StepResult.Status.PENDING) {
                run.cleanup(ctx);
            }
            return result;
        } catch (RuntimeException exception) {
            run.cleanup(ctx);
            throw exception;
        }
    }

    record Phase(String name, Consumer<Run> enter, Function<Run, @Nullable String> check, int stableTicks, int maxTicks) {
        static Phase of(String name, Consumer<Run> enter, Function<Run, @Nullable String> check) {
            return new Phase(name, enter, check, 8, 160);
        }

        Phase stable(int ticks) {
            return new Phase(this.name, this.enter, this.check, ticks, this.maxTicks);
        }

        Phase limit(int ticks) {
            return new Phase(this.name, this.enter, this.check, this.stableTicks, ticks);
        }
    }

    static final class Run {
        final Kind kind;
        final List<Phase> phases;
        final Mode3dSelfTests.Script script = new Mode3dSelfTests.Script(Mode3dSelfTests.Kind.INVERTED, List.of());
        final Map<BlockPos, BlockState> pool = new LinkedHashMap<>();
        int index = -3;
        int since;
        int stableSince = -1;
        int now;
        double surfaceY;
        double minRpm;
        boolean sawSideways;
        @Nullable CompletableFuture<?> pending;
        @Nullable CompletableFuture<String> shot;
        SelfTest.@Nullable StepContext context;
        boolean cleaned;
        boolean resized;

        Run(Kind kind) {
            this.kind = kind;
            this.phases = kind == Kind.WATER ? waterPhases() : turtlePhases();
        }

        int ticks() {
            return this.now - this.since;
        }

        StepResult tick(SelfTest.StepContext ctx) {
            this.context = ctx;
            this.now = ctx.tick();
            CompletableFuture<?> waiting = this.pending;
            if (waiting != null) {
                if (waiting.isCompletedExceptionally()) {
                    return StepResult.fail(this.label() + ": Server-Aktion fehlgeschlagen: " + waiting.exceptionNow().getMessage());
                }
                if (!waiting.isDone()) {
                    return StepResult.pending();
                }
                this.pending = null;
            }
            CompletableFuture<String> picture = this.shot;
            if (picture != null) {
                if (!picture.isDone()) {
                    return StepResult.pending();
                }
                this.shot = null;
                if (picture.isCompletedExceptionally()) {
                    return StepResult.fail("Bildschirmfoto: " + picture.exceptionNow());
                }
                ctx.log("Bildschirmfoto " + picture.join());
            }
            if (this.index == -3) {
                if (Screenshots.enabled()) {
                    StepResult sized = Screenshots.resizeWindow(ctx);
                    if (sized.status() == StepResult.Status.PENDING) {
                        return sized;
                    }
                    this.resized = true;
                    Screenshots.hideMarker(true);
                }
                this.index = -2;
                this.since = this.now;
                PilotSession.INSTANCE.clearTestInput();
                PilotSession.INSTANCE.resetFlightModes();
                this.pending = ctx.onServer(this::setUp);
                return StepResult.pending();
            }
            PilotSession session = PilotSession.INSTANCE;
            if (this.index == -2) {
                QuadcopterEntity drone = session.drone();
                boolean wanted = this.kind == Kind.WATER ? session.isFpv() : session.isActive();
                if (drone == null || drone.getId() != this.script.droneId || !wanted) {
                    return this.ticks() > 300 ? StepResult.fail("keine Sitzung mit der Testdrohne") : StepResult.pending();
                }
                if (this.ticks() < 30) {
                    return StepResult.pending();
                }
                this.enter(0);
                return StepResult.pending();
            }
            if (this.index >= this.phases.size()) {
                return StepResult.pass();
            }
            if (!session.isActive()) {
                return StepResult.fail(this.label() + ": Sitzung verloren");
            }
            Phase phase = this.phases.get(this.index);
            String problem = phase.check().apply(this);
            if (this.pending != null || this.shot != null) {
                return StepResult.pending();
            }
            if (problem != null) {
                this.stableSince = -1;
                return this.ticks() > phase.maxTicks() ? StepResult.fail(this.label() + ": " + problem) : StepResult.pending();
            }
            if (this.stableSince < 0) {
                this.stableSince = this.now;
            }
            if (this.now - this.stableSince < phase.stableTicks()) {
                return StepResult.pending();
            }
            ctx.log(this.label() + " nach " + (this.stableSince - this.since) + " Ticks");
            this.enter(this.index + 1);
            return this.index >= this.phases.size() ? StepResult.pass() : StepResult.pending();
        }

        private void enter(int next) {
            this.index = next;
            this.since = this.now;
            this.stableSince = -1;
            if (next < this.phases.size()) {
                this.phases.get(next).enter().accept(this);
            }
        }

        private String label() {
            return this.kind + " " + (this.index >= 0 && this.index < this.phases.size() ? this.phases.get(this.index).name() : "Start");
        }

        void photo(String name) {
            if (Screenshots.enabled() && this.shot == null) {
                this.shot = Screenshots.capture(name);
            }
        }

        private void setUp(MinecraftServer server) {
            ServerPlayer player = Mode3dSelfTests.firstPlayer(server);
            if (this.kind == Kind.TURTLE) {
                Mode3dSelfTests.spawnAndFly(server, this.script, true, PilotMode.LINE_OF_SIGHT);
                return;
            }
            ServerLevel level = player.level();
            BlockPos feet = player.blockPosition();
            BlockPos center = feet.offset(0, 0, POOL_DZ);
            for (int dx = -POOL_HALF - 1; dx <= POOL_HALF + 1; dx++) {
                for (int dz = -POOL_HALF - 1; dz <= POOL_HALF + 1; dz++) {
                    for (int dy = -1; dy <= POOL_DEPTH + 2; dy++) {
                        BlockPos pos = center.offset(dx, dy, dz);
                        boolean wall = Math.abs(dx) > POOL_HALF || Math.abs(dz) > POOL_HALF;
                        BlockState next = dy < 0 ? Blocks.STONE.defaultBlockState()
                                : dy >= POOL_DEPTH ? Blocks.AIR.defaultBlockState()
                                : wall ? Blocks.GLASS.defaultBlockState() : Blocks.WATER.defaultBlockState();
                        this.pool.putIfAbsent(pos.immutable(), level.getBlockState(pos));
                        level.setBlock(pos, next, Block.UPDATE_CLIENTS);
                    }
                }
            }
            this.surfaceY = center.getY() + POOL_DEPTH - 1 + 8.0 / 9.0;
            Mode3dSelfTests.spawnAndFly(server, this.script, false, PilotMode.FPV, Vec3.atBottomCenterOf(center).add(0.0, 0.6, 0.0), 0.0F);
        }

        void cleanup(SelfTest.StepContext ctx) {
            if (this.cleaned) {
                return;
            }
            this.cleaned = true;
            PilotSession.INSTANCE.setTestTurtle(null);
            Mode3dSelfTests.cleanup(ctx, this.script);
            Map<BlockPos, BlockState> saved = new LinkedHashMap<>(this.pool);
            if (!saved.isEmpty()) {
                ctx.onServer(server -> {
                    ServerLevel level = Mode3dSelfTests.firstPlayer(server).level();
                    saved.forEach((pos, state) -> level.setBlock(pos, state, Block.UPDATE_CLIENTS));
                });
            }
            if (this.resized) {
                Screenshots.hideMarker(false);
                Screenshots.restoreWindow(ctx);
            }
        }
    }

    private static List<Phase> waterPhases() {
        List<Phase> out = new ArrayList<>();
        out.add(Phase.of("ohne Beschichtung: im Wasser kein Scharfschalten", r -> {
        }, r -> {
            PilotSession.INSTANCE.setTestInput(StickPositions.IDLE, r.ticks() % 20 >= 10, StabMode.ANGLE);
            Telemetry t = telemetry();
            if (t == null || t.armed) {
                return "scharf ohne Beschichtung";
            }
            double surface = surfaceAboveDrone();
            if (!(surface > 0.5)) {
                return "Drohne nicht unter Wasser, Oberfläche " + fmt(surface);
            }
            return r.ticks() > 25 && ArmBlocker.IN_FLUID.isSet(t.armBlockers) ? null : "Blocker " + t.armBlockers;
        }));
        out.add(Phase.of("beschichtet: scharf unter Wasser", r -> {
            PilotSession.INSTANCE.setTestInput(StickPositions.IDLE, false, StabMode.ANGLE);
            int id = r.script.droneId;
            r.pending = r.context.onServer(server -> {
                for (ServerLevel level : server.getAllLevels()) {
                    if (level.getEntity(id) instanceof QuadcopterEntity drone) {
                        drone.setWaterproof(true);
                    }
                }
            });
        }, r -> {
            DroneSim sim = PilotSession.INSTANCE.sim();
            if (sim == null || !sim.waterproof()) {
                return "Beschichtung kommt nicht im Flugmodell an";
            }
            PilotSession.INSTANCE.setTestInput(StickPositions.IDLE, r.ticks() >= 10, StabMode.ANGLE);
            Telemetry t = sim.telemetry();
            if (!t.armed) {
                return "nicht scharf, Blocker " + t.armBlockers;
            }
            return t.submerged > 0.9 ? null : "eingetaucht " + fmt(t.submerged);
        }));
        out.add(Phase.of("halbes Gas: Props drehen langsam, Strom hoch", r -> r.minRpm = 0.0, r -> {
            PilotSession.INSTANCE.setTestInput(new StickPositions(0.0F, 0.0F, 0.0F, 0.45F), true, StabMode.ANGLE);
            Telemetry t = telemetry();
            if (t == null || !t.armed) {
                return "nicht mehr scharf";
            }
            if (r.ticks() == 20 && Screenshots.enabled()) {
                Screenshots.clearOverlays(Minecraft.getInstance());
            }
            if (r.ticks() == 30) {
                r.photo("waterproof_underwater_fpv");
            }
            if (r.ticks() < 30) {
                return "wartet";
            }
            double rpm = Math.abs(t.motorRpm[0]);
            if (rpm < 200.0 || rpm > 12000.0) {
                return "Drehzahl im Wasser " + fmt(rpm);
            }
            return t.submerged > 0.2 ? null : "schon aus dem Wasser";
        }).stable(4));
        out.add(Phase.of("Vollgas: steigt aus dem Wasser und fliegt", r -> {
        }, r -> {
            PilotSession.INSTANCE.setTestInput(new StickPositions(0.0F, 0.0F, 0.0F, 1.0F), true, StabMode.ANGLE);
            Telemetry t = telemetry();
            if (t == null || !t.armed) {
                return "nicht mehr scharf";
            }
            double above = Mode3dSelfTests.comY() - r.surfaceY;
            return above > 2.0 && t.submerged == 0.0 ? null : "über der Oberfläche " + fmt(above);
        }).stable(1).limit(400));
        out.add(Phase.of("Gas weg und entschärfen", r -> {
        }, r -> {
            PilotSession.INSTANCE.setTestInput(StickPositions.IDLE, false, StabMode.ANGLE);
            Telemetry t = telemetry();
            return t != null && !t.armed ? null : "noch scharf";
        }));
        return out;
    }

    private static List<Phase> turtlePhases() {
        List<Phase> out = new ArrayList<>();
        out.add(Phase.of("kopfüber ohne Turtle: gesperrt", r -> PilotSession.INSTANCE.setTestTurtle(false), r -> {
            PilotSession.INSTANCE.setTestInput(StickPositions.IDLE, r.ticks() % 20 >= 10, StabMode.ACRO);
            Telemetry t = telemetry();
            if (t == null || t.tiltDeg < 150.0) {
                return "liegt nicht kopfüber: " + (t == null ? "-" : fmt(t.tiltDeg));
            }
            if (t.armed) {
                return "scharf trotz Kopflage";
            }
            return r.ticks() > 25 && ArmBlocker.TILTED.isSet(t.armBlockers) ? null : "Blocker " + t.armBlockers;
        }));
        out.add(Phase.of("Turtle an: scharf, OSD zeigt TURTLE", r -> {
            PilotSession.INSTANCE.setTestTurtle(true);
            PilotSession.INSTANCE.setTestInput(StickPositions.IDLE, false, StabMode.ACRO);
        }, r -> {
            PilotSession.INSTANCE.setTestInput(StickPositions.IDLE, r.ticks() >= 10, StabMode.ACRO);
            Telemetry t = telemetry();
            if (t == null || !t.armed || !t.turtle) {
                return "nicht im Turtle-Modus scharf, Blocker " + (t == null ? -1 : t.armBlockers);
            }
            OsdData osd = OsdHud.sampler().data();
            if (osd.active && !osd.turtle) {
                return "OSD ohne TURTLE";
            }
            for (double rpm : t.motorRpm) {
                if (Math.abs(rpm) > 300.0) {
                    return "Motoren drehen ohne Knüppel: " + fmt(rpm);
                }
            }
            return null;
        }));
        out.add(Phase.of("Roll rechts: Props einer Seite rückwärts, Drohne kippt herum", r -> {
            r.minRpm = 0.0;
            r.sawSideways = false;
        }, r -> {
            PilotSession.INSTANCE.setTestInput(new StickPositions(1.0F, 0.0F, 0.0F, 0.0F), true, StabMode.ACRO);
            Telemetry t = telemetry();
            if (t == null || !t.armed) {
                return "nicht mehr scharf";
            }
            for (double rpm : t.motorRpm) {
                r.minRpm = Math.min(r.minRpm, rpm);
            }
            if (!r.sawSideways && t.tiltDeg < 130.0 && t.tiltDeg > 60.0) {
                r.sawSideways = true;
                r.photo("turtle_flip_los");
            }
            if (r.minRpm > -2000.0) {
                return "kein Motor rückwärts: " + fmt(r.minRpm);
            }
            return t.tiltDeg < 25.0 ? null : "Neigung " + fmt(t.tiltDeg);
        }).stable(4).limit(300));
        out.add(Phase.of("aufrecht: Motoren stehen trotz Knüppel", r -> {
        }, r -> {
            PilotSession.INSTANCE.setTestInput(new StickPositions(1.0F, 0.0F, 0.0F, 0.0F), true, StabMode.ACRO);
            Telemetry t = telemetry();
            if (t == null || !t.armed || !t.turtle) {
                return "Turtle-Modus nicht mehr scharf";
            }
            if (t.tiltDeg > 25.0) {
                return "nicht mehr aufrecht: " + fmt(t.tiltDeg);
            }
            for (double rpm : t.motorRpm) {
                if (Math.abs(rpm) > 300.0) {
                    return "Motor dreht noch: " + fmt(rpm);
                }
            }
            return null;
        }).stable(20).limit(200));
        out.add(Phase.of("Turtle aus: entschärft, Scharfschalter muss neu", r -> {
            PilotSession.INSTANCE.setTestTurtle(false);
        }, r -> {
            PilotSession.INSTANCE.setTestInput(StickPositions.IDLE, true, StabMode.ACRO);
            Telemetry t = telemetry();
            if (t == null || t.armed) {
                return "noch scharf";
            }
            if (t.lastDisarmReason != DisarmReason.TURTLE_OFF) {
                return "Grund " + t.lastDisarmReason;
            }
            return ArmBlocker.ARM_SWITCH_NOT_RESET.isSet(t.armBlockers) ? null : "Scharfschalter muss nicht neu, Blocker " + t.armBlockers;
        }));
        return out;
    }

    static double surfaceAboveDrone() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            return Double.NaN;
        }
        Vector3d com = PilotSession.INSTANCE.comPosition(new Vector3d());
        return WaterSurface.relative(mc.level, com.x, com.y, com.z);
    }

    static @Nullable Telemetry telemetry() {
        return Mode3dSelfTests.telemetry();
    }

    private static String fmt(double value) {
        return String.format(Locale.ROOT, "%.2f", value);
    }
}
