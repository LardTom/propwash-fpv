package de.lardtom.propwash.drone.client;

import de.lardtom.propwash.build.DroneBuild;
import de.lardtom.propwash.build.PartWear;
import de.lardtom.propwash.content.ContentIds;
import de.lardtom.propwash.core.CoreComponents;
import de.lardtom.propwash.dev.SelfTest;
import de.lardtom.propwash.dev.SelfTest.StepResult;
import de.lardtom.propwash.drone.CovFlightDatapack;
import de.lardtom.propwash.drone.DroneInteractions;
import de.lardtom.propwash.drone.DroneModule;
import de.lardtom.propwash.drone.DronePiloting;
import de.lardtom.propwash.drone.PilotMode;
import de.lardtom.propwash.drone.QuadcopterEntity;
import de.lardtom.propwash.drone.SurfaceMaterials;
import de.lardtom.propwash.input.StickPositions;
import de.lardtom.propwash.sim.DroneSim;
import de.lardtom.propwash.sim.SimSnapshot;
import de.lardtom.propwash.sim.StabMode;
import de.lardtom.propwash.sim.Telemetry;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SnowLayerBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;

public final class CovSurfaceSelfTests {
    public static final String CEILING_STEP = SurfaceSelfTests.SLIME + " – von unten gegen eine Slime-Decke federt die Drohne zurück";
    public static final String OUTSIDE_STEP = SurfaceSelfTests.WOOL
            + " – nicht dabei: Teppich, Bett, Schneeschicht, Woll-Stufe und Woll-Platte in keinem Tag (Server und Client), Aufprall mit 20 m/s wie auf Stein";
    public static final String MOSS_STEP = SurfaceSelfTests.WOOL + " – Datenpaket nimmt Moos in drone_cushion auf: Wand und Fall mit 15 m/s ohne Absturz";
    public static final String CLEANUP_STEP = SurfaceSelfTests.WOOL + " – Abdeckung: Testfeld abbauen";
    public static final String MOSS_PACK = "moss_cushion";
    public static final String MOSS_FILE = "data/propwash/tags/block/drone_cushion.json";
    private static final int OFFSET_X = -14;
    private static final int OFFSET_Z = 10;
    private static final int CLEAR = 3;
    private static final int CLEAR_HEIGHT = 9;
    private static final int CEILING_DY = 4;
    private static final int WALL_DX = 3;
    private static final double CEILING_SPEED = 8.0;
    private static final double HARD_SPEED = 20.0;
    private static final double MOSS_SPEED = 15.0;
    private static final double PLACED = 0.3;
    private static final int SYNC_TICKS = 100;
    private static final int CONTACT_TICKS = 60;
    private static final int AFTER_TICKS = 20;
    private static final int PACK_TICKS = 600;
    private static final int PLATE_SETTLE_TICKS = 5;
    private static final int PLATE_TICKS = 40;
    private static final String RUN = "cov_surface";
    private static final Quaternionf FACING_EAST = new Quaternionf().rotationY((float) (-Math.PI / 2.0));
    private static final AtomicReference<@Nullable Rig> RIG = new AtomicReference<>();

    private CovSurfaceSelfTests() {
    }

    public static void register() {
        SelfTest.clientStep(OUTSIDE_STEP, 3000, CovSurfaceSelfTests::outsideStep);
        SelfTest.clientStep(MOSS_STEP, 3000, CovSurfaceSelfTests::mossStep);
        SelfTest.clientStep(CLEANUP_STEP, 300, CovSurfaceSelfTests::cleanupStep);
    }

    enum Case {
        CEILING("Slime-Decke", Blocks.SLIME_BLOCK, Motion.UP, CEILING_SPEED),
        CARPET("Teppich", Blocks.CARPET.pick(DyeColor.WHITE), Motion.DOWN, HARD_SPEED),
        BED("Bett", Blocks.BED.pick(DyeColor.RED), Motion.DOWN, HARD_SPEED),
        WOOL_SLAB("Woll-Platte", Blocks.WOOL_SLAB.pick(DyeColor.LIME), Motion.DOWN, HARD_SPEED),
        WOOL_STAIRS("Woll-Stufe", Blocks.WOOL_STAIRS.pick(DyeColor.ORANGE), Motion.DOWN, HARD_SPEED),
        SNOW("Schneeschicht", Blocks.SNOW, Motion.DOWN, HARD_SPEED),
        STONE("Stein", Blocks.STONE, Motion.DOWN, HARD_SPEED),
        MOSS_WALL("Moos-Wand", Blocks.MOSS_BLOCK, Motion.EAST, MOSS_SPEED),
        MOSS_FLOOR("Moos-Boden", Blocks.MOSS_BLOCK, Motion.DOWN, MOSS_SPEED),
        PLATE_CRASH("Holzdruckplatte", Blocks.OAK_PRESSURE_PLATE, Motion.DOWN, MOSS_SPEED);

        final String what;
        final Block block;
        final Motion motion;
        final double speed;

        Case(String what, Block block, Motion motion, double speed) {
            this.what = what;
            this.block = block;
            this.motion = motion;
            this.speed = speed;
        }
    }

    enum Motion {
        UP,
        DOWN,
        EAST
    }

    private static final class Rig {
        final int x;
        final int y;
        final int z;
        final Map<BlockPos, BlockState> original = new LinkedHashMap<>();
        int droneId = -1;
        @Nullable ItemStack head;
        @Nullable ItemStack hand;

        Rig(int x, int y, int z) {
            this.x = x;
            this.y = y;
            this.z = z;
        }

        double top(Case c) {
            return switch (c) {
                case CARPET -> this.y + 1.0 / 16.0;
                case BED -> this.y + 9.0 / 16.0;
                case WOOL_SLAB -> this.y + 0.5;
                case WOOL_STAIRS -> this.y + 1.0;
                case SNOW -> this.y + 2.0 / 16.0;
                case STONE, MOSS_FLOOR -> this.y + 1.0;
                default -> this.y;
            };
        }

        Vec3 ground(Case c) {
            return new Vec3(this.x + 0.5, this.top(c), this.z + 0.5);
        }

        Vec3 air(Case c) {
            return switch (c.motion) {
                case UP -> new Vec3(this.x + 0.5, this.y + CEILING_DY - 1.2, this.z + 0.5);
                case EAST -> new Vec3(this.x + 0.5, this.y + 1.5, this.z + 0.5);
                case DOWN -> new Vec3(this.x + 0.5, this.top(c) + (c == Case.PLATE_CRASH ? 2.2 : 1.2), this.z + 0.5);
            };
        }
    }

    private static final class Run {
        final List<Case> cases;
        int index;
        int stage;
        int mark;
        @Nullable CompletableFuture<?> pending;
        int crashes;
        @Nullable PartWear wear;
        boolean touched;
        int touchedAt;
        double after = Double.NaN;
        double extreme;
        int minSignal = Integer.MAX_VALUE;
        boolean platePlaced;
        final List<String> log = new ArrayList<>();
        final Map<Case, Boolean> crashed = new LinkedHashMap<>();

        Run(List<Case> cases) {
            this.cases = cases;
        }

        Case current() {
            return this.cases.get(this.index);
        }

        void enter(int stage, int tick) {
            this.stage = stage;
            this.mark = tick;
        }
    }

    private static StepResult run(SelfTest.StepContext ctx, List<Case> cases, @Nullable String intro) {
        Run run = (Run) ctx.state().computeIfAbsent(RUN, key -> new Run(cases));
        if (run.pending != null) {
            if (run.pending.isCompletedExceptionally()) {
                input(0.0F, false);
                return StepResult.fail(run.current().what + ", Server-Aktion in Stufe " + run.stage + ": " + run.pending.exceptionNow().getMessage());
            }
            if (!run.pending.isDone()) {
                return StepResult.pending();
            }
            run.pending = null;
        }
        if (run.index >= run.cases.size()) {
            input(0.0F, false);
            if (intro != null) {
                run.log.addFirst(intro);
            }
            ctx.log(String.join("; ", run.log));
            return StepResult.pass();
        }
        StepResult result = tick(ctx, run);
        if (result.status() == StepResult.Status.FAIL) {
            input(0.0F, false);
            return result;
        }
        return StepResult.pending();
    }

    private static StepResult tick(SelfTest.StepContext ctx, Run run) {
        int waited = ctx.tick() - run.mark;
        Case c = run.current();
        PilotSession session = PilotSession.INSTANCE;
        if (run.stage == 0) {
            input(0.0F, false);
            run.pending = ctx.onServer(server -> prepare(server, c));
            run.enter(1, ctx.tick());
            return StepResult.pending();
        }
        Rig rig = RIG.get();
        QuadcopterEntity drone = session.drone();
        DroneSim sim = session.sim();
        if (rig == null) {
            return StepResult.fail("kein Testfeld");
        }
        if (drone == null || sim == null || drone.getId() != rig.droneId || !session.isFpv()) {
            return waited > SYNC_TICKS ? StepResult.fail("keine FPV-Sitzung mit der Testdrohne") : StepResult.pending();
        }
        Telemetry t = sim.telemetry();
        switch (run.stage) {
            case 1 -> {
                input(0.0F, false);
                BlockPos probe = probe(rig, c);
                if (c != Case.PLATE_CRASH && !drone.level().getBlockState(probe).is(c.block)) {
                    return waited > SYNC_TICKS ? StepResult.fail(c.what + " kommt beim Client nicht an: " + probe.toShortString()) : StepResult.pending();
                }
                Vec3 start = c.motion == Motion.DOWN ? rig.ground(c) : new Vec3(rig.x + 0.5, rig.y, rig.z + 0.5);
                run.pending = ctx.onServer(server -> serverDrone(server, rig.droneId).resetTo(start, FACING_EAST));
                run.enter(2, ctx.tick());
                return StepResult.pending();
            }
            case 2 -> {
                boolean grounded = t.grounded || t.sleeping;
                input(0.0F, t.armed || grounded && waited >= 3);
                if (!t.armed) {
                    return waited > SYNC_TICKS ? StepResult.fail(c.what + ": nicht scharf geschaltet, Sperren=" + t.armBlockers) : StepResult.pending();
                }
                Vec3 air = rig.air(c);
                run.pending = ctx.onServer(server -> serverDrone(server, rig.droneId).resetTo(air, FACING_EAST));
                run.enter(3, ctx.tick());
                return StepResult.pending();
            }
            case 3 -> {
                input((float) t.hoverThrottleStick, true);
                if (!near(drone, rig.air(c))) {
                    Vec3 air = rig.air(c);
                    return waited > SYNC_TICKS ? StepResult.fail(String.format(Locale.ROOT, "%s: Drohne nicht in Startlage gesetzt: %.2f/%.2f/%.2f statt "
                            + "%.2f/%.2f/%.2f, scharf %s", c.what, drone.getX(), drone.getY(), drone.getZ(), air.x, air.y, air.z, t.armed)) : StepResult.pending();
                }
                if (!t.armed) {
                    return StepResult.fail(c.what + ": beim Versetzen entschärft: " + t.lastDisarmReason);
                }
                if (c == Case.PLATE_CRASH && !run.platePlaced) {
                    run.platePlaced = true;
                    run.pending = ctx.onServer(server -> set(player(server).level(), rig, new BlockPos(rig.x, rig.y, rig.z),
                            Blocks.OAK_PRESSURE_PLATE.defaultBlockState()));
                    return StepResult.pending();
                }
                if (c == Case.PLATE_CRASH && (!drone.level().getBlockState(probe(rig, c)).is(c.block) || plateSignal(drone, rig) != 0)) {
                    return waited > SYNC_TICKS ? StepResult.fail("Holzdruckplatte kommt beim Client nicht an oder ist schon an: Signal " + plateSignal(drone, rig))
                            : StepResult.pending();
                }
                run.crashes = session.crashCount();
                run.minSignal = Integer.MAX_VALUE;
                run.wear = sim.wear();
                run.touched = false;
                run.after = Double.NaN;
                run.extreme = 0.0;
                push(sim, c);
                run.enter(4, ctx.tick());
                return StepResult.pending();
            }
            case 4 -> {
                input((float) t.hoverThrottleStick, t.armed);
                double v = velocity(sim, c.motion);
                if (!run.touched) {
                    if (v < 0.5 * c.speed) {
                        run.touched = true;
                        run.touchedAt = ctx.tick();
                    } else if (waited > CONTACT_TICKS) {
                        return StepResult.fail(c.what + ": kein Kontakt");
                    }
                    return StepResult.pending();
                }
                int since = ctx.tick() - run.touchedAt;
                run.extreme = Math.min(run.extreme, v);
                if (c == Case.PLATE_CRASH) {
                    if (since > PLATE_SETTLE_TICKS && since <= PLATE_SETTLE_TICKS + PLATE_TICKS) {
                        run.minSignal = Math.min(run.minSignal, plateSignal(drone, rig));
                    }
                    if (since <= PLATE_SETTLE_TICKS + PLATE_TICKS) {
                        return StepResult.pending();
                    }
                }
                if (since == 5) {
                    run.after = v;
                }
                if (since < AFTER_TICKS) {
                    return StepResult.pending();
                }
                boolean crashed = session.crashCount() != run.crashes;
                boolean worn = !sim.wear().equals(run.wear);
                run.crashed.put(c, crashed);
                run.log.add(String.format(Locale.ROOT, "%s mit %.0f m/s: Absturz %s, Verschleiß %s, danach %.2f m/s (Gegenrichtung bis %.2f), scharf %s%s", c.what,
                        c.speed, crashed ? "ja" : "nein", worn ? "ja" : "nein", run.after, -run.extreme, t.armed,
                        c == Case.PLATE_CRASH ? String.format(Locale.ROOT, ", Platte %d Ticks lang mindestens Signal %d", PLATE_TICKS, run.minSignal) : ""));
                String problem = judge(run, c, crashed, worn, t.armed);
                if (problem != null) {
                    return StepResult.fail(problem + " – " + String.join("; ", run.log) + (worn ? "; Verschleiß vorher " + run.wear + ", nachher " + sim.wear()
                            + ", Item " + drone.wear() : ""));
                }
                run.index++;
                run.enter(0, ctx.tick());
                return StepResult.pending();
            }
            default -> {
                return StepResult.fail("unbekannte Stufe " + run.stage);
            }
        }
    }

    private static @Nullable String judge(Run run, Case c, boolean crashed, boolean worn, boolean armed) {
        switch (c) {
            case CEILING -> {
                if (crashed || worn || !armed) {
                    return "Slime-Decke: Absturz, Verschleiß oder entschärft";
                }
                if (!(run.after < 0.0)) {
                    return String.format(Locale.ROOT, "Slime-Decke federt nicht nach unten zurück: vy danach %.2f m/s", run.after);
                }
            }
            case MOSS_WALL, MOSS_FLOOR -> {
                if (crashed || worn || !armed) {
                    return c.what + ": Absturz, Verschleiß oder entschärft trotz drone_cushion";
                }
                if (Math.abs(run.after) > 3.0) {
                    return String.format(Locale.ROOT, "%s fängt nicht ab wie Wolle: %.2f m/s danach", c.what, run.after);
                }
            }
            case STONE -> {
                if (!crashed) {
                    return "Kontrolle: Stein mit 20 m/s ohne Absturz";
                }
            }
            case PLATE_CRASH -> {
                if (!crashed || armed) {
                    return "Absturz auf die Holzdruckplatte nicht erkannt oder nicht entschärft";
                }
                if (run.minSignal != 15) {
                    return "abgestürzte Drohne hält die Holzdruckplatte nicht " + PLATE_TICKS + " Ticks gedrückt: kleinstes Signal " + run.minSignal;
                }
            }
            default -> {
                if (!crashed) {
                    return c.what + " fängt die Drohne ab, obwohl der Block nicht dabei ist";
                }
            }
        }
        return null;
    }

    private static int plateSignal(QuadcopterEntity drone, Rig rig) {
        BlockState plate = drone.level().getBlockState(new BlockPos(rig.x, rig.y, rig.z));
        return plate.hasProperty(BlockStateProperties.POWERED) ? plate.getValue(BlockStateProperties.POWERED) ? 15 : 0 : -1;
    }

    public static StepResult plateCrashStep(SelfTest.StepContext ctx) {
        Map<String, Object> state = ctx.state();
        if (!state.containsKey("plate_result")) {
            StepResult result = run(ctx, List.of(Case.PLATE_CRASH), null);
            if (result.status() == StepResult.Status.PENDING) {
                return result;
            }
            state.put("plate_result", result);
            state.put("plate_cleanup", ctx.tick());
            state.remove(RUN);
        }
        int start = (int) state.get("plate_cleanup");
        StepResult cleaned = cleanup(ctx, ctx.tick() - start);
        if (cleaned.status() == StepResult.Status.PENDING) {
            return cleaned;
        }
        StepResult result = (StepResult) state.get("plate_result");
        return result.status() == StepResult.Status.PASS && cleaned.status() == StepResult.Status.FAIL ? cleaned : result;
    }

    private static double velocity(DroneSim sim, Motion motion) {
        SimSnapshot snapshot = new SimSnapshot();
        sim.copyStateTo(snapshot);
        return switch (motion) {
            case UP -> snapshot.vy;
            case DOWN -> -snapshot.vy;
            case EAST -> snapshot.vx;
        };
    }

    private static void push(DroneSim sim, Case c) {
        SimSnapshot snapshot = new SimSnapshot();
        sim.copyStateTo(snapshot);
        snapshot.vx = c.motion == Motion.EAST ? c.speed : 0.0;
        snapshot.vy = c.motion == Motion.UP ? c.speed : c.motion == Motion.DOWN ? -c.speed : 0.0;
        snapshot.vz = 0.0;
        snapshot.sleeping = false;
        sim.copyStateFrom(snapshot);
    }

    private static BlockPos probe(Rig rig, Case c) {
        return switch (c.motion) {
            case UP -> new BlockPos(rig.x, rig.y + CEILING_DY, rig.z);
            case EAST -> new BlockPos(rig.x + WALL_DX, rig.y + 1, rig.z);
            case DOWN -> new BlockPos(rig.x, rig.y, rig.z);
        };
    }

    private static void prepare(MinecraftServer server, Case c) {
        ServerPlayer player = player(server);
        ServerLevel level = player.level();
        Rig rig = RIG.get();
        if (rig == null) {
            rig = new Rig(player.getBlockX() + OFFSET_X, player.getBlockY(), player.getBlockZ() + OFFSET_Z);
            RIG.set(rig);
        }
        if (!(rig.droneId >= 0 && level.getEntity(rig.droneId) instanceof QuadcopterEntity)) {
            spawn(player, rig);
        }
        QuadcopterEntity current = serverDrone(server, rig.droneId);
        if (DronePiloting.pilotedBy(player.getUUID()).filter(piloted -> piloted == current).isEmpty()) {
            fly(player, current, rig);
        }
        build(level, rig, c);
        current.setWear(PartWear.PRISTINE);
    }

    private static void spawn(ServerPlayer player, Rig rig) {
        ServerLevel level = player.level();
        QuadcopterEntity drone = DroneModule.DRONE.get().create(level, EntitySpawnReason.COMMAND);
        if (drone == null) {
            throw new IllegalStateException("Drohne nicht erzeugt");
        }
        drone.snapTo(rig.x + 0.5, rig.y, rig.z + 0.5, 0.0F, 0.0F);
        drone.setBuild(DroneBuild.FREESTYLE);
        if (!level.addFreshEntity(drone)) {
            throw new IllegalStateException("addFreshEntity fehlgeschlagen");
        }
        rig.droneId = drone.getId();
    }

    private static void fly(ServerPlayer player, QuadcopterEntity drone, Rig rig) {
        DronePiloting.pilotedBy(player.getUUID()).ifPresent(DronePiloting::release);
        ItemStack goggles = ContentIds.item(ContentIds.GOGGLES_CREATIVE).map(ItemStack::new).orElse(ItemStack.EMPTY);
        goggles.set(CoreComponents.BOUND_DRONE.get(), DroneInteractions.binding(drone));
        if (rig.head == null) {
            rig.head = player.getItemBySlot(EquipmentSlot.HEAD).copy();
        }
        player.setItemSlot(EquipmentSlot.HEAD, goggles);
        ItemStack remote = new ItemStack(DroneModule.REMOTE.get());
        remote.set(CoreComponents.BOUND_DRONE.get(), DroneInteractions.binding(drone));
        if (rig.hand == null) {
            rig.hand = player.getMainHandItem().copy();
        }
        player.setItemSlot(EquipmentSlot.MAINHAND, remote);
        DronePiloting.StartResult result = DronePiloting.tryStart(player, drone, PilotMode.FPV);
        if (result != DronePiloting.StartResult.STARTED) {
            throw new IllegalStateException("FPV-Start verweigert: " + result);
        }
    }

    private static void set(ServerLevel level, Rig rig, BlockPos pos, BlockState state) {
        rig.original.putIfAbsent(pos.immutable(), level.getBlockState(pos));
        level.setBlock(pos, state, Block.UPDATE_ALL);
    }

    private static void build(ServerLevel level, Rig rig, Case c) {
        for (int dx = -CLEAR; dx <= WALL_DX + 1; dx++) {
            for (int dz = -CLEAR; dz <= CLEAR; dz++) {
                set(level, rig, new BlockPos(rig.x + dx, rig.y - 1, rig.z + dz), Blocks.STONE.defaultBlockState());
                for (int dy = CLEAR_HEIGHT - 1; dy >= 0; dy--) {
                    set(level, rig, new BlockPos(rig.x + dx, rig.y + dy, rig.z + dz), Blocks.AIR.defaultBlockState());
                }
            }
        }
        switch (c.motion) {
            case UP -> {
                for (int dx = -2; dx <= 2; dx++) {
                    for (int dz = -2; dz <= 2; dz++) {
                        set(level, rig, new BlockPos(rig.x + dx, rig.y + CEILING_DY, rig.z + dz), c.block.defaultBlockState());
                    }
                }
            }
            case EAST -> {
                for (int dy = 0; dy <= 3; dy++) {
                    for (int dz = -2; dz <= 2; dz++) {
                        set(level, rig, new BlockPos(rig.x + WALL_DX, rig.y + dy, rig.z + dz), c.block.defaultBlockState());
                    }
                }
            }
            case DOWN -> {
                if (c == Case.PLATE_CRASH) {
                    return;
                }
                if (c == Case.BED) {
                    for (int dx = -1; dx <= 1; dx++) {
                        BlockPos foot = new BlockPos(rig.x + dx, rig.y, rig.z);
                        BlockState bed = c.block.defaultBlockState().setValue(BedBlock.FACING, Direction.NORTH);
                        set(level, rig, foot, bed.setValue(BedBlock.PART, BedPart.FOOT));
                        set(level, rig, foot.north(), bed.setValue(BedBlock.PART, BedPart.HEAD));
                    }
                } else {
                    BlockState state = c == Case.SNOW ? Blocks.SNOW.defaultBlockState().setValue(SnowLayerBlock.LAYERS, 2)
                            : c == Case.WOOL_STAIRS ? c.block.defaultBlockState().setValue(StairBlock.FACING, Direction.EAST) : c.block.defaultBlockState();
                    for (int dx = -2; dx <= 2; dx++) {
                        for (int dz = -2; dz <= 2; dz++) {
                            set(level, rig, new BlockPos(rig.x + dx, rig.y, rig.z + dz), state);
                        }
                    }
                }
            }
        }
    }

    static List<String> outsideTags() {
        List<String> wrong = new ArrayList<>();
        int checked = 0;
        for (Block block : BuiltInRegistries.BLOCK) {
            BlockState state = block.defaultBlockState();
            String id = BuiltInRegistries.BLOCK.getKey(block).toString();
            boolean carpet = state.is(BlockTags.WOOL_CARPETS) || id.endsWith("_carpet");
            boolean bed = state.is(BlockTags.BEDS);
            boolean woolShape = id.contains("wool") && (id.endsWith("_slab") || id.endsWith("_stairs"));
            if (!carpet && !bed && !woolShape && block != Blocks.SNOW) {
                continue;
            }
            checked++;
            if (state.is(SurfaceMaterials.BOUNCY) || state.is(SurfaceMaterials.CUSHION)) {
                wrong.add(id);
            }
        }
        if (checked < 60) {
            wrong.add("nur " + checked + " Teppiche, Betten und Schneeschichten gefunden");
        }
        return wrong;
    }

    static long woolShapes() {
        return BuiltInRegistries.BLOCK.keySet().stream().filter(id -> id.getPath().contains("wool")
                && (id.getPath().endsWith("_slab") || id.getPath().endsWith("_stairs"))).count();
    }

    private static StepResult outsideStep(SelfTest.StepContext ctx) {
        Map<String, Object> state = ctx.state();
        if (!state.containsKey("tags")) {
            AtomicReference<List<String>> server = new AtomicReference<>();
            state.put("tags", server);
            state.put("tags_future", ctx.onServer(s -> server.set(outsideTags())));
            return StepResult.pending();
        }
        @SuppressWarnings("unchecked")
        AtomicReference<List<String>> server = (AtomicReference<List<String>>) state.get("tags");
        CompletableFuture<?> future = (CompletableFuture<?>) state.get("tags_future");
        if (future != null) {
            if (future.isCompletedExceptionally()) {
                return StepResult.fail("Tag-Prüfung am Server: " + future.exceptionNow().getMessage());
            }
            if (!future.isDone()) {
                return StepResult.pending();
            }
            List<String> client = outsideTags();
            if (!server.get().isEmpty() || !client.isEmpty()) {
                return StepResult.fail("Blöcke in drone_bouncy/drone_cushion, die nicht dabei sein sollen: Server " + server.get() + ", Client " + client);
            }
            state.put("tags_future", null);
            state.remove("tags_future");
        }
        return run(ctx, List.of(Case.STONE, Case.CARPET, Case.BED, Case.SNOW, Case.WOOL_SLAB, Case.WOOL_STAIRS),
                "Teppiche, Betten, Schneeschicht und " + woolShapes() + " Woll-Stufen und -Platten in keinem Tag, Server und Client");
    }

    private static StepResult mossStep(SelfTest.StepContext ctx) {
        Map<String, Object> state = ctx.state();
        Minecraft mc = Minecraft.getInstance();
        String phase = (String) state.getOrDefault("moss_phase", "install");
        int since = ctx.tick() - (int) state.getOrDefault("moss_since", 0);
        switch (phase) {
            case "install" -> {
                if (Blocks.MOSS_BLOCK.defaultBlockState().is(SurfaceMaterials.CUSHION)) {
                    return StepResult.fail("Moos ist schon vor dem Datenpaket in drone_cushion");
                }
                state.put("moss_future", ctx.onServer(server -> CovFlightDatapack.install(server, MOSS_PACK, List.of(MOSS_FILE))));
                state.put("moss_phase", "reload");
                state.put("moss_since", ctx.tick());
                return StepResult.pending();
            }
            case "reload" -> {
                CompletableFuture<?> future = (CompletableFuture<?>) state.get("moss_future");
                if (future.isCompletedExceptionally()) {
                    return StepResult.fail("Datenpaket: " + future.exceptionNow().getMessage());
                }
                @SuppressWarnings("unchecked")
                AtomicReference<Boolean> serverTag = (AtomicReference<Boolean>) state.computeIfAbsent("moss_server", key -> new AtomicReference<>(false));
                if (future.isDone() && (since % 5 == 0)) {
                    state.put("moss_check", ctx.onServer(server -> serverTag.set(Blocks.MOSS_BLOCK.defaultBlockState().is(SurfaceMaterials.CUSHION))));
                }
                boolean client = mc.level != null && mc.level.registryAccess().lookupOrThrow(Registries.BLOCK)
                        .get(ResourceKey.create(Registries.BLOCK, BuiltInRegistries.BLOCK.getKey(Blocks.MOSS_BLOCK)))
                        .map(holder -> holder.is(SurfaceMaterials.CUSHION)).orElse(false);
                if (!future.isDone() || !serverTag.get() || !client) {
                    return since > PACK_TICKS ? StepResult.fail("Moos nach /reload nicht im Tag: Server " + serverTag.get() + ", Client " + client)
                            : StepResult.pending();
                }
                state.put("moss_log", String.format(Locale.ROOT, "Datenpaket %s aktiv, Moos nach %d Ticks im Tag drone_cushion (Server und Client)",
                        CovFlightDatapack.packId(MOSS_PACK), since));
                state.put("moss_phase", "fly");
                state.put("moss_since", ctx.tick());
                return StepResult.pending();
            }
            case "fly" -> {
                StepResult result = run(ctx, List.of(Case.MOSS_WALL, Case.MOSS_FLOOR), (String) state.get("moss_log"));
                if (result.status() == StepResult.Status.PENDING) {
                    return result;
                }
                state.put("moss_result", result);
                state.put("moss_future", ctx.onServer(server -> CovFlightDatapack.remove(server, MOSS_PACK)));
                state.put("moss_phase", "remove");
                state.put("moss_since", ctx.tick());
                return StepResult.pending();
            }
            case "remove" -> {
                CompletableFuture<?> future = (CompletableFuture<?>) state.get("moss_future");
                if (future.isCompletedExceptionally()) {
                    return StepResult.fail("Datenpaket entfernen: " + future.exceptionNow().getMessage());
                }
                if (!future.isDone() || Blocks.MOSS_BLOCK.defaultBlockState().is(SurfaceMaterials.CUSHION)) {
                    return since > PACK_TICKS ? StepResult.fail("Moos bleibt nach dem Entfernen des Datenpakets im Tag") : StepResult.pending();
                }
                return (StepResult) state.get("moss_result");
            }
            default -> {
                return StepResult.fail("unbekannte Phase " + phase);
            }
        }
    }

    public static StepResult ceilingStep(SelfTest.StepContext ctx) {
        return run(ctx, List.of(Case.CEILING), null);
    }

    static StepResult cleanupStep(SelfTest.StepContext ctx) {
        return cleanup(ctx, ctx.tick());
    }

    private static StepResult cleanup(SelfTest.StepContext ctx, int tick) {
        Rig rig = RIG.get();
        if (tick == 0) {
            PilotSession.INSTANCE.clearTestInput();
            ctx.state().put(RUN, ctx.onServer(server -> {
                CovFlightDatapack.remove(server, MOSS_PACK);
                if (rig == null) {
                    return;
                }
                for (ServerLevel level : server.getAllLevels()) {
                    Entity entity = rig.droneId >= 0 ? level.getEntity(rig.droneId) : null;
                    if (entity instanceof QuadcopterEntity drone) {
                        DronePiloting.release(drone);
                        drone.discard();
                    }
                }
                ServerPlayer player = player(server);
                if (rig.head != null) {
                    player.setItemSlot(EquipmentSlot.HEAD, rig.head);
                }
                if (rig.hand != null) {
                    player.setItemSlot(EquipmentSlot.MAINHAND, rig.hand);
                }
                ServerLevel level = player.level();
                List<Map.Entry<BlockPos, BlockState>> entries = new ArrayList<>(rig.original.entrySet());
                for (int i = entries.size() - 1; i >= 0; i--) {
                    level.setBlock(entries.get(i).getKey(), entries.get(i).getValue(), Block.UPDATE_ALL);
                }
                RIG.compareAndSet(rig, null);
            }));
            return StepResult.pending();
        }
        CompletableFuture<?> future = (CompletableFuture<?>) ctx.state().get(RUN);
        if (future.isCompletedExceptionally()) {
            return StepResult.fail("Aufräumen: " + future.exceptionNow().getMessage());
        }
        if (!future.isDone()) {
            return StepResult.pending();
        }
        return PilotSession.INSTANCE.isActive() ? (tick > SYNC_TICKS ? StepResult.fail("Sitzung läuft nach dem Aufräumen weiter")
                : StepResult.pending()) : StepResult.pass();
    }

    private static void input(float throttle, boolean arm) {
        PilotSession.INSTANCE.setTestInput(new StickPositions(0.0F, 0.0F, 0.0F, throttle), arm, StabMode.ANGLE);
    }

    private static boolean near(QuadcopterEntity drone, Vec3 target) {
        return Math.abs(drone.getX() - target.x) < PLACED && Math.abs(drone.getZ() - target.z) < PLACED && Math.abs(drone.getY() - target.y) < PLACED;
    }

    private static ServerPlayer player(MinecraftServer server) {
        List<ServerPlayer> players = server.getPlayerList().getPlayers();
        if (players.isEmpty()) {
            throw new IllegalStateException("kein Spieler auf dem Server");
        }
        return players.getFirst();
    }

    private static QuadcopterEntity serverDrone(MinecraftServer server, int id) {
        if (!(player(server).level().getEntity(id) instanceof QuadcopterEntity drone)) {
            throw new IllegalStateException("Testdrohne auf dem Server verschwunden");
        }
        return drone;
    }
}
