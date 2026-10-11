package de.lardtom.propwash.race.client;

import de.lardtom.propwash.build.DroneBuild;
import de.lardtom.propwash.dev.SelfTest;
import de.lardtom.propwash.dev.SelfTest.StepResult;
import de.lardtom.propwash.dev.client.Screenshots;
import de.lardtom.propwash.drone.DroneModule;
import de.lardtom.propwash.drone.QuadcopterEntity;
import de.lardtom.propwash.race.FreeformGateItem;
import de.lardtom.propwash.race.GateEntity;
import de.lardtom.propwash.race.GateGeometry;
import de.lardtom.propwash.race.GateMount;
import de.lardtom.propwash.race.GateMounting;
import de.lardtom.propwash.race.GateSignal;
import de.lardtom.propwash.race.GateSignalTarget;
import de.lardtom.propwash.race.GateSignals;
import de.lardtom.propwash.race.GateSize;
import de.lardtom.propwash.race.RaceModule;
import de.lardtom.propwash.server.ServerModule;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.RedstoneWireBlock;
import net.minecraft.world.level.block.RedstoneLampBlock;
import net.minecraft.world.level.block.piston.PistonBaseBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.level.storage.TagValueOutput;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import java.util.function.Supplier;

public final class GateRedstoneSelfTests {
    private static final int OFFSET_X = 44;
    private static final int OFFSET_Z = -40;
    private static final int SHOT_X = 60;
    private static final int SHOT_Z = -170;
    private static final int X0 = -3;
    private static final int X1 = 31;
    private static final int Z0 = -7;
    private static final int Z1 = 19;
    private static final int SHOT_PULSE = 100;
    private static final String FUTURE = "future";
    private static final String WAIT = "warten: ";
    private static final AtomicReference<Rig> RIG = new AtomicReference<>();
    private static final AtomicReference<Rig> SHOT_RIG = new AtomicReference<>();
    private static final AtomicReference<Vec3> SHOT_HOME = new AtomicReference<>();
    private static final AtomicReference<String> RESULT = new AtomicReference<>();
    private static final AtomicReference<BlockPos> PENDING = new AtomicReference<>();
    private static final AtomicInteger PHASE = new AtomicInteger();
    private static final AtomicLong T0 = new AtomicLong();
    private static final AtomicReference<ChunkPos> TICKET = new AtomicReference<>();
    private static final AtomicReference<Spot> RETURN = new AtomicReference<>();
    private static final AtomicReference<GateSignal> SAVED_SIGNAL = new AtomicReference<>();
    private static final AtomicReference<String> VERDICT = new AtomicReference<>();
    private static final AtomicReference<List<Flat>> FLATS = new AtomicReference<>(List.of());
    private static final int FROZEN_PULSE = 20;
    private static final int TICKET_RADIUS = 2;
    private static final int FAR = 600;

    private record Spot(Vec3 position, float yaw, float pitch) {
    }

    private record Flat(String name, int id, GateSignalTarget target, BlockPos lamp, BlockState lampWas) {
    }

    public record Rig(BlockPos origin, int standing, int wall, int freeform, int drone, Vec3 home, ItemStack held) {
        BlockPos at(int dx, int dy, int dz) {
            return this.origin.offset(dx, dy, dz);
        }
    }

    private GateRedstoneSelfTests() {
    }

    public static void register() {
        SelfTest.clientStep("race: Redstone – Testbau mit Lampen, Kolben, Wand- und Freiform-Gate", 400, GateRedstoneSelfTests::buildStep);
        SelfTest.clientStep("race: Redstone – Durchflug gibt 10 Ticks Strom in den Block unter dem Gate", 300,
                ctx -> poll(ctx, 280, GateRedstoneSelfTests::pulse));
        SelfTest.clientStep("race: Redstone – Pulslänge, aus, Gegenrichtung, nur im Rennen und Rechte", 300,
                ctx -> poll(ctx, 280, GateRedstoneSelfTests::settings));
        SelfTest.clientStep("race: Redstone – Wand-Tauchtor und Freiform-Gate", 300, ctx -> poll(ctx, 280, GateRedstoneSelfTests::mounts));
        SelfTest.clientStep("race: Redstone – Einstellen im Panel auf der Seite Redstone", 300, GateRedstoneSelfTests::editorStep);
        SelfTest.clientStep("race: Redstone – liegende Gates über Grube und unter Deckenloch schalten den Randblock", 300,
                ctx -> poll(ctx, 280, GateRedstoneSelfTests::lying));
        SelfTest.clientStep("race: Redstone – Puls endet im Chunk ohne Entity-Ticks", 1200, ctx -> poll(ctx, 1180, GateRedstoneSelfTests::frozen));
        SelfTest.clientStep("race: Redstone – Speichern, Laden und Abbauen mitten im Puls", 300,
                ctx -> poll(ctx, 280, GateRedstoneSelfTests::persistence));
        SelfTest.clientStep("race: Redstone – aufräumen", 200, ctx -> poll(ctx, 180, GateRedstoneSelfTests::cleanup));
    }

    private static StepResult poll(SelfTest.StepContext ctx, int limit, Function<MinecraftServer, @Nullable String> action) {
        if (ctx.tick() == 0 && !ctx.state().containsKey("started")) {
            ctx.state().put("started", true);
            PHASE.set(0);
        }
        CompletableFuture<?> future = (CompletableFuture<?>) ctx.state().get(FUTURE);
        if (future == null) {
            RESULT.set(null);
            ctx.state().put(FUTURE, ctx.onServer(server -> RESULT.set(action.apply(server))));
            return StepResult.pending();
        }
        if (future.isCompletedExceptionally()) {
            return StepResult.fail("Server-Aktion fehlgeschlagen: " + future.exceptionNow());
        }
        if (!future.isDone()) {
            return StepResult.pending();
        }
        ctx.state().remove(FUTURE);
        String result = RESULT.get();
        if (result != null && result.startsWith(WAIT)) {
            return ctx.tick() > limit ? StepResult.fail("Zeit abgelaufen, " + result) : StepResult.pending();
        }
        return result == null ? StepResult.pass() : StepResult.fail(result);
    }

    private static ServerPlayer player(MinecraftServer server) {
        return server.getPlayerList().getPlayers().getFirst();
    }

    private static void fill(ServerLevel level, BlockPos from, BlockPos to, BlockState state) {
        for (BlockPos pos : BlockPos.betweenClosed(from, to)) {
            level.setBlockAndUpdate(pos, state);
        }
    }

    private static boolean ticking(ServerLevel level, BlockPos o) {
        for (int cx = (o.getX() + X0) >> 4; cx <= (o.getX() + X1) >> 4; cx++) {
            for (int cz = (o.getZ() + Z0) >> 4; cz <= (o.getZ() + Z1) >> 4; cz++) {
                if (!level.areEntitiesActuallyLoadedAndTicking(new ChunkPos(cx, cz))) {
                    return false;
                }
            }
        }
        return true;
    }

    private static void wire(ServerLevel level, BlockPos... positions) {
        for (BlockPos pos : positions) {
            level.setBlockAndUpdate(pos, Blocks.REDSTONE_WIRE.defaultBlockState());
        }
        for (BlockPos pos : positions) {
            level.setBlockAndUpdate(pos, Block.updateFromNeighbourShapes(Blocks.REDSTONE_WIRE.defaultBlockState(), level, pos));
        }
    }

    private static GateEntity placeGate(ServerPlayer player, BlockPos o, String name, Supplier<InteractionResult> use) {
        ServerLevel level = player.level();
        AABB area = new AABB(Vec3.atLowerCornerOf(o.offset(X0, -2, Z0)), Vec3.atLowerCornerOf(o.offset(X1, 10, Z1)));
        List<GateEntity> before = level.getEntitiesOfClass(GateEntity.class, area);
        InteractionResult result = use.get();
        List<GateEntity> after = level.getEntitiesOfClass(GateEntity.class, area, gate -> !before.contains(gate) && !gate.isRemoved());
        if (after.size() != 1) {
            String why = "";
            if (name.equals("freeform")) {
                FreeformGateItem.Plan plan = FreeformGateItem.plan(level, o.offset(25, 1, 8), Direction.UP, new Vec3(0.0, 0.0, 1.0), false);
                why = ", Plan: " + FreeformGateItem.problem(plan).getString() + " " + plan.result().status();
            }
            throw new IllegalStateException(name + ": " + after.size() + " neue Gates statt 1 (" + result + why + ")");
        }
        return after.getFirst();
    }

    public static Rig build(ServerPlayer player, BlockPos o, Vec3 home, ItemStack held) {
        ServerLevel level = player.level();
        fill(level, o.offset(X0, 0, Z0), o.offset(X1, 9, Z1), Blocks.AIR.defaultBlockState());
        fill(level, o.offset(X0, -2, Z0), o.offset(X1, -1, Z1), Blocks.SMOOTH_STONE.defaultBlockState());
        fill(level, o.offset(2, 0, 0), o.offset(6, 0, 0), Blocks.STONE_BRICKS.defaultBlockState());
        level.setBlockAndUpdate(o.offset(4, 0, 0), Blocks.CHISELED_STONE_BRICKS.defaultBlockState());
        level.setBlockAndUpdate(o.offset(4, 0, 4), Blocks.REDSTONE_LAMP.defaultBlockState());
        level.setBlockAndUpdate(o.offset(4, 0, -3), Blocks.PISTON.defaultBlockState().setValue(PistonBaseBlock.FACING, Direction.UP));
        level.setBlockAndUpdate(o.offset(4, 1, -3), Blocks.GOLD_BLOCK.defaultBlockState());
        wire(level, o.offset(4, 0, 1), o.offset(4, 0, 2), o.offset(4, 0, 3), o.offset(4, 0, -1), o.offset(4, 0, -2));
        fill(level, o.offset(10, 0, 8), o.offset(20, 6, 8), Blocks.STONE_BRICKS.defaultBlockState());
        for (BlockPos lamp : wallLamps(o)) {
            level.setBlockAndUpdate(lamp, Blocks.REDSTONE_LAMP.defaultBlockState());
        }
        fill(level, o.offset(22, 0, 8), o.offset(28, 6, 8), Blocks.STONE_BRICKS.defaultBlockState());
        fill(level, o.offset(24, 2, 8), o.offset(26, 4, 8), Blocks.AIR.defaultBlockState());
        level.setBlockAndUpdate(o.offset(25, 1, 8), Blocks.REDSTONE_LAMP.defaultBlockState());
        GateEntity standing = placeGate(player, o, "standing", () -> GateMountSelfTests.use(player,
                new GateMountSelfTests.Spec("redstone_standing", GateSize.MEDIUM, GateMount.STANDING, 0, 4, 0, 0, Direction.UP, 0.0F), o));
        GateEntity wall = placeGate(player, o, "dive", () -> GateMountSelfTests.use(player,
                new GateMountSelfTests.Spec("redstone_dive", GateSize.MEDIUM, GateMount.WALL_DIVE, 0, 15, 4, 8, Direction.SOUTH, 180.0F), o));
        float head = player.getYHeadRot();
        player.setYHeadRot(0.0F);
        GateEntity freeform;
        try {
            freeform = placeGate(player, o, "freeform", () -> FreeformGateSelfTests.use(player, o.offset(25, 1, 8), Direction.UP, 0.0F, 0.0F));
        } finally {
            player.setYHeadRot(head);
        }
        QuadcopterEntity drone = DroneModule.DRONE.get().create(level, EntitySpawnReason.COMMAND);
        if (drone == null) {
            throw new IllegalStateException("Drohne nicht erzeugt");
        }
        drone.setBuild(DroneBuild.FREESTYLE);
        drone.setNoGravity(true);
        Vec3 start = entry(standing.geometry(), 2.0);
        drone.snapTo(start.x, start.y - drone.getBbHeight() * 0.5, start.z, 0.0F, 0.0F);
        if (!level.addFreshEntity(drone)) {
            throw new IllegalStateException("Drohne nicht gespawnt");
        }
        return new Rig(o, standing.getId(), wall.getId(), freeform.getId(), drone.getId(), home, held);
    }

    static List<BlockPos> wallLamps(BlockPos o) {
        return List.of(o.offset(14, 4, 8), o.offset(16, 4, 8), o.offset(15, 5, 8), o.offset(15, 3, 8));
    }

    private static Vec3 center(GateGeometry g) {
        return new Vec3(g.centerX(), g.centerY(), g.centerZ());
    }

    private static Vec3 normal(GateGeometry g) {
        return new Vec3(g.normalX(), g.normalY(), g.normalZ());
    }

    private static Vec3 entry(GateGeometry g, double distance) {
        return center(g).subtract(normal(g).scale(distance));
    }

    private static void fly(ServerPlayer player, QuadcopterEntity drone, GateEntity gate, boolean forward) {
        GateGeometry g = gate.geometry();
        Vec3 from = entry(g, forward ? 1.5 : -1.5);
        Vec3 to = entry(g, forward ? -1.5 : 1.5);
        QuadcopterEntity.SERVER_MOVED.fire(listener -> listener.onServerMoved(drone, player, from, to));
        drone.snapTo(to.x, to.y - drone.getBbHeight() * 0.5, to.z, drone.getYRot(), 0.0F);
    }

    private static boolean lit(ServerLevel level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        return state.is(Blocks.REDSTONE_LAMP) && state.getValue(RedstoneLampBlock.LIT);
    }

    private static int dust(ServerLevel level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        return state.is(Blocks.REDSTONE_WIRE) ? state.getValue(RedstoneWireBlock.POWER) : -1;
    }

    private static boolean extended(ServerLevel level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        return state.is(Blocks.PISTON) && state.getValue(PistonBaseBlock.EXTENDED);
    }

    private static @Nullable GateEntity gate(ServerLevel level, int id) {
        Entity entity = level.getEntity(id);
        return entity instanceof GateEntity gate && !gate.isRemoved() ? gate : null;
    }

    private static GateSignalTarget expectedStanding(Rig rig) {
        BlockPos s = rig.at(4, 0, 0);
        return new GateSignalTarget(s.getX(), s.getY(), s.getZ(), GateSignalTarget.UP);
    }

    private static GateSignalTarget expectedWall(Rig rig) {
        BlockPos s = rig.at(15, 4, 8);
        return new GateSignalTarget(s.getX(), s.getY(), s.getZ(), GateSignalTarget.SOUTH);
    }

    private static GateSignalTarget expectedFreeform(Rig rig) {
        BlockPos s = rig.at(25, 1, 8);
        return new GateSignalTarget(s.getX(), s.getY(), s.getZ(), GateSignalTarget.UP);
    }

    private static StepResult buildStep(SelfTest.StepContext ctx) {
        if (ctx.tick() == 0 && !ctx.state().containsKey("started")) {
            ctx.state().put("started", true);
            PHASE.set(0);
        }
        return poll(ctx, 380, server -> {
            ServerPlayer player = player(server);
            ServerLevel level = player.level();
            if (PHASE.get() == 0) {
                BlockPos o = new BlockPos(player.getBlockX() + OFFSET_X, player.getBlockY(), player.getBlockZ() + OFFSET_Z);
                PENDING.set(o);
                Vec3 home = player.position();
                ItemStack held = player.getMainHandItem().copy();
                RIG.set(new Rig(o, -1, -1, -1, -1, home, held));
                player.getAbilities().flying = true;
                player.onUpdateAbilities();
                player.teleportTo(level, o.getX() + 8.5, o.getY() + 0.5, o.getZ() - 5.5, Set.of(), 45.0F, 20.0F, true);
                PHASE.set(1);
                return WAIT + "Testgelände lädt";
            }
            BlockPos o = PENDING.get();
            if (!ticking(level, o)) {
                return WAIT + "Testgelände lädt";
            }
            Rig pending = RIG.get();
            Rig rig = build(player, o, pending.home(), pending.held());
            RIG.set(rig);
            player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(RaceModule.COURSE_TOOL.get()));
            GateEntity standing = gate(level, rig.standing());
            GateEntity wall = gate(level, rig.wall());
            GateEntity freeform = gate(level, rig.freeform());
            if (standing == null || wall == null || freeform == null) {
                return "Gates fehlen nach dem Setzen";
            }
            if (!standing.signal().equals(GateSignal.DEFAULT) || !freeform.signal().equals(GateSignal.DEFAULT)) {
                return "neue Gates nicht mit Signal an und 10 Ticks: " + standing.signal() + " / " + freeform.signal();
            }
            if (!standing.signalTarget().equals(expectedStanding(rig))) {
                return "stehendes Gate zielt auf " + standing.signalTarget() + " statt " + expectedStanding(rig);
            }
            if (!wall.signalTarget().equals(expectedWall(rig))) {
                return "Tauchtor zielt auf " + wall.signalTarget() + " statt " + expectedWall(rig);
            }
            if (!freeform.signalTarget().equals(expectedFreeform(rig))) {
                return "Freiform-Gate zielt auf " + freeform.signalTarget() + " statt " + expectedFreeform(rig);
            }
            if (lit(level, rig.at(4, 0, 4)) || dust(level, rig.at(4, 0, 1)) != 0 || extended(level, rig.at(4, 0, -3))) {
                return "Strom schon vor dem ersten Durchflug";
            }
            if (GateSignals.active(level) != 0) {
                return GateSignals.active(level) + " Signale vor dem ersten Durchflug";
            }
            return null;
        });
    }

    private static @Nullable String pulse(MinecraftServer server) {
        Rig rig = RIG.get();
        ServerPlayer player = player(server);
        ServerLevel level = player.level();
        GateEntity gate = rig == null ? null : gate(level, rig.standing());
        QuadcopterEntity drone = rig == null ? null : (QuadcopterEntity) level.getEntity(rig.drone());
        if (gate == null || drone == null) {
            return "kein Testbau";
        }
        BlockPos lamp = rig.at(4, 0, 4);
        BlockPos wire = rig.at(4, 0, 1);
        BlockPos front = rig.at(4, 0, -1);
        BlockPos piston = rig.at(4, 0, -3);
        if (PHASE.get() == 0) {
            fly(player, drone, gate, true);
            T0.set(level.getGameTime());
            if (gate.pulseLeft() != GateSignal.DEFAULT_PULSE || !expectedStanding(rig).equals(gate.emitting())) {
                return "Puls nach dem Durchflug: " + gate.pulseLeft() + " Ticks auf " + gate.emitting();
            }
            if (dust(level, wire) != 15 || dust(level, front) != 15 || !lit(level, lamp) || GateSignals.active(level) != 1) {
                return "kein Strom gleich nach dem Durchflug: Draht " + dust(level, wire) + "/" + dust(level, front) + ", Lampe " + lit(level, lamp);
            }
            PHASE.set(1);
            return WAIT + "Puls läuft";
        }
        long dt = level.getGameTime() - T0.get();
        int expected = (int) Math.max(0L, GateSignal.DEFAULT_PULSE - dt);
        if (gate.pulseLeft() != expected) {
            return "nach " + dt + " Ticks noch " + gate.pulseLeft() + " Ticks Puls statt " + expected;
        }
        boolean on = expected > 0;
        if ((dust(level, wire) == 15) != on || (GateSignals.active(level) == 1) != on) {
            return "nach " + dt + " Ticks Draht " + dust(level, wire) + ", " + GateSignals.active(level) + " Signale, Puls " + expected;
        }
        if (on && !lit(level, lamp)) {
            return "Lampe nach " + dt + " Ticks schon aus";
        }
        if (on && dt >= 2 && !extended(level, piston)) {
            return "Kolben nach " + dt + " Ticks nicht ausgefahren";
        }
        if (dt < 16) {
            return WAIT + "Puls endet (" + dt + " Ticks)";
        }
        if (lit(level, lamp) || extended(level, piston) || dust(level, front) != 0) {
            return "nach " + dt + " Ticks noch Strom: Lampe " + lit(level, lamp) + ", Kolben " + extended(level, piston);
        }
        return null;
    }

    private static @Nullable String settings(MinecraftServer server) {
        Rig rig = RIG.get();
        ServerPlayer player = player(server);
        ServerLevel level = player.level();
        GateEntity gate = rig == null ? null : gate(level, rig.standing());
        QuadcopterEntity drone = rig == null ? null : (QuadcopterEntity) level.getEntity(rig.drone());
        if (gate == null || drone == null) {
            return "kein Testbau";
        }
        BlockPos wire = rig.at(4, 0, 1);
        if (PHASE.get() == 0) {
            GateMounting.Result set = GateMounting.signal(player, gate, GateSignal.DEFAULT.withPulse(40));
            if (set != GateMounting.Result.SIGNAL_SET || gate.signal().pulseTicks() != 40) {
                return "Pulslänge 40 nicht übernommen: " + set + " " + gate.signal();
            }
            fly(player, drone, gate, true);
            if (gate.pulseLeft() != 40 || dust(level, wire) != 15) {
                return "Puls mit 40 Ticks: " + gate.pulseLeft() + ", Draht " + dust(level, wire);
            }
            GateMounting.signal(player, gate, gate.signal().withEnabled(false));
            if (gate.pulseLeft() != 0 || dust(level, wire) != 0 || GateSignals.active(level) != 0) {
                return "Abschalten beendet den laufenden Puls nicht: " + gate.pulseLeft() + ", Draht " + dust(level, wire);
            }
            fly(player, drone, gate, true);
            if (gate.pulseLeft() != 0 || dust(level, wire) != 0) {
                return "ausgeschaltetes Gate gibt Strom";
            }
            GateMounting.signal(player, gate, GateSignal.DEFAULT);
            fly(player, drone, gate, false);
            if (gate.pulseLeft() != 0 || dust(level, wire) != 0) {
                return "Durchflug gegen die Flugrichtung gibt Strom";
            }
            GateMounting.signal(player, gate, GateSignal.DEFAULT.withRaceOnly(true));
            fly(player, drone, gate, true);
            if (gate.pulseLeft() != 0 || dust(level, wire) != 0) {
                return "„Nur im Rennen“ gibt im freien Flug Strom";
            }
            GateMounting.signal(player, gate, GateSignal.DEFAULT);
            fly(player, drone, gate, true);
            if (gate.pulseLeft() != GateSignal.DEFAULT_PULSE || dust(level, wire) != 15) {
                return "kein Puls nach dem Zurückstellen";
            }
            PHASE.set(1);
            return WAIT + "zweiter Durchflug im Puls";
        }
        if (PHASE.get() == 1) {
            if (gate.pulseLeft() == 0) {
                fly(player, drone, gate, true);
                return WAIT + "zweiter Durchflug im Puls (neu)";
            }
            if (gate.pulseLeft() == GateSignal.DEFAULT_PULSE) {
                return WAIT + "zweiter Durchflug im Puls";
            }
            fly(player, drone, gate, true);
            if (gate.pulseLeft() != GateSignal.DEFAULT_PULSE || dust(level, wire) != 15 || GateSignals.active(level) != 1) {
                return "zweiter Durchflug verlängert den Puls nicht: " + gate.pulseLeft() + ", Draht " + dust(level, wire);
            }
            T0.set(level.getGameTime());
            PHASE.set(2);
            return WAIT + "verlängerter Puls endet";
        }
        long dt = level.getGameTime() - T0.get();
        if (dt < GateSignal.DEFAULT_PULSE) {
            if (dust(level, wire) != 15) {
                return "verlängerter Puls endet nach " + dt + " Ticks zu früh";
            }
            return WAIT + "verlängerter Puls endet";
        }
        if (dust(level, wire) != 0 || GateSignals.active(level) != 0) {
            return "verlängerter Puls endet nicht: " + dt + " Ticks";
        }
        ItemStack tool = player.getMainHandItem().copy();
        player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
        GateMounting.Result bare = GateMounting.signal(player, gate, GateSignal.DEFAULT.withEnabled(false));
        player.setItemInHand(InteractionHand.MAIN_HAND, tool);
        if (bare != GateMounting.Result.NEED_TOOL || !gate.signal().enabled()) {
            return "ohne Gate oder Kurs-Werkzeug in der Hand: " + bare;
        }
        Vec3 at = player.position();
        player.teleportTo(level, at.x + 40.0, at.y, at.z, Set.of(), player.getYRot(), player.getXRot(), true);
        GateMounting.Result far = GateMounting.signal(player, gate, GateSignal.DEFAULT.withEnabled(false));
        player.teleportTo(level, at.x, at.y, at.z, Set.of(), player.getYRot(), player.getXRot(), true);
        if (far != GateMounting.Result.TOO_FAR || !gate.signal().enabled()) {
            return "aus 40 Blöcken Entfernung: " + far;
        }
        return null;
    }

    private static @Nullable String mounts(MinecraftServer server) {
        Rig rig = RIG.get();
        ServerPlayer player = player(server);
        ServerLevel level = player.level();
        GateEntity wall = rig == null ? null : gate(level, rig.wall());
        GateEntity freeform = rig == null ? null : gate(level, rig.freeform());
        QuadcopterEntity drone = rig == null ? null : (QuadcopterEntity) level.getEntity(rig.drone());
        if (wall == null || freeform == null || drone == null) {
            return "kein Testbau";
        }
        BlockPos rim = rig.at(25, 1, 8);
        if (PHASE.get() == 0) {
            for (BlockPos lamp : wallLamps(rig.origin())) {
                if (lit(level, lamp)) {
                    return "Wandlampe leuchtet schon vor dem Durchflug";
                }
            }
            if (lit(level, rim)) {
                return "Lampe unter dem Freiform-Gate leuchtet schon vor dem Durchflug";
            }
            fly(player, drone, wall, true);
            if (!expectedWall(rig).equals(wall.emitting())) {
                return "Tauchtor gibt Strom in " + wall.emitting() + " statt " + expectedWall(rig);
            }
            for (BlockPos lamp : wallLamps(rig.origin())) {
                if (!lit(level, lamp)) {
                    return "Wandlampe " + lamp.subtract(rig.origin()) + " neben dem Wandblock des Tauchtors bleibt aus";
                }
            }
            fly(player, drone, freeform, true);
            if (!expectedFreeform(rig).equals(freeform.emitting()) || !lit(level, rim)) {
                return "Freiform-Gate: Strom in " + freeform.emitting() + ", Lampe im Rand " + lit(level, rim);
            }
            if (GateSignals.active(level) != 2) {
                return GateSignals.active(level) + " Signale statt 2";
            }
            T0.set(level.getGameTime());
            PHASE.set(1);
            return WAIT + "Pulse an Wand und Fenster enden";
        }
        long dt = level.getGameTime() - T0.get();
        if (dt < 16) {
            return WAIT + "Pulse an Wand und Fenster enden (" + dt + " Ticks)";
        }
        for (BlockPos lamp : wallLamps(rig.origin())) {
            if (lit(level, lamp)) {
                return "Wandlampe nach " + dt + " Ticks noch an";
            }
        }
        if (lit(level, rim) || GateSignals.active(level) != 0) {
            return "Freiform-Lampe nach " + dt + " Ticks noch an";
        }
        return null;
    }

    private static StepResult editorStep(SelfTest.StepContext ctx) {
        Minecraft mc = Minecraft.getInstance();
        Rig rig = RIG.get();
        if (rig == null || mc.level == null || mc.player == null) {
            return StepResult.fail("kein Testbau");
        }
        if (!(mc.level.getEntity(rig.standing()) instanceof GateEntity gate)) {
            return ctx.tick() > 200 ? StepResult.fail("Gate fehlt beim Client") : StepResult.pending();
        }
        int stage = (int) ctx.state().getOrDefault("stage", 0);
        if (stage == 0) {
            if (!GateMounting.holdsTool(mc.player)) {
                return ctx.tick() > 200 ? StepResult.fail("Kurs-Werkzeug kommt nicht an") : StepResult.pending();
            }
            GateEditor.start(gate);
            GateEditor editor = GateEditor.current();
            if (editor == null) {
                return StepResult.fail("Gate einstellen startet nicht");
            }
            GateEditModel model = editor.model();
            if (model.pages() != 2 || model.page() != 0) {
                GateEditor.stop();
                return StepResult.fail("Panel mit " + model.pages() + " Seiten, Seite " + model.page());
            }
            if (!expectedStanding(rig).equals(editor.signalTarget())) {
                GateEditor.stop();
                return StepResult.fail("roter Rahmen beim Client auf " + editor.signalTarget() + " statt " + expectedStanding(rig));
            }
            for (int page = 0; page < 2; page++) {
                editor.showPage(page);
                for (boolean interactive : new boolean[]{false, true}) {
                    GateEditPanel probe = new GateEditPanel(editor, interactive);
                    probe.layout(480, 270);
                    if (probe.top() < 0 || probe.bottom() > 270) {
                        GateEditor.stop();
                        return StepResult.fail("Panel-Seite " + page + " passt nicht in 480 × 270: " + probe.top() + "–" + probe.bottom());
                    }
                }
            }
            editor.showPage(1);
            if (editor.model().selected() != GateEditModel.Row.SIGNAL) {
                GateEditor.stop();
                return StepResult.fail("Seite Redstone wählt " + editor.model().selected());
            }
            editor.set(GateEditModel.Row.PULSE, 30.0);
            editor.set(GateEditModel.Row.RACE_ONLY, 1.0);
            if (!editor.changed() || editor.status() != GateEditor.Status.SAME) {
                GateEditor.stop();
                return StepResult.fail("Signal-Änderung nicht erkannt: " + editor.changed() + "/" + editor.status());
            }
            if (!editor.apply() || GateEditor.current() != null) {
                GateEditor.stop();
                return StepResult.fail("Übernehmen schließt den Einstell-Modus nicht");
            }
            ctx.state().put("stage", 1);
            return StepResult.pending();
        }
        GateSignal want = new GateSignal(true, 30, true);
        if (stage == 1) {
            if (!gate.signal().equals(want)) {
                return ctx.tick() > 250 ? StepResult.fail("Client-Gate nach dem Übernehmen: " + gate.signal()) : StepResult.pending();
            }
            ctx.state().put("stage", 2);
            ctx.state().put(FUTURE, ctx.onServer(server -> {
                GateEntity serverGate = gate(player(server).level(), rig.standing());
                RESULT.set(serverGate != null && serverGate.signal().equals(want) ? null : "Server-Gate: " + (serverGate == null ? null : serverGate.signal()));
                if (serverGate != null) {
                    serverGate.setSignal(GateSignal.DEFAULT);
                }
            }));
            return StepResult.pending();
        }
        CompletableFuture<?> future = (CompletableFuture<?>) ctx.state().get(FUTURE);
        if (!future.isDone()) {
            return StepResult.pending();
        }
        if (future.isCompletedExceptionally()) {
            return StepResult.fail("Server-Aktion fehlgeschlagen: " + future.exceptionNow());
        }
        return RESULT.get() == null ? StepResult.pass() : StepResult.fail(RESULT.get());
    }

    private static Direction outward(GateGeometry g, GateSignalTarget target) {
        double dx = target.x() + 0.5 - g.centerX();
        double dz = target.z() + 0.5 - g.centerZ();
        return Math.abs(dx) > Math.abs(dz) ? (dx > 0.0 ? Direction.EAST : Direction.WEST) : (dz > 0.0 ? Direction.SOUTH : Direction.NORTH);
    }

    private static @Nullable String lying(MinecraftServer server) {
        Rig rig = RIG.get();
        ServerPlayer player = player(server);
        ServerLevel level = player.level();
        QuadcopterEntity drone = rig == null ? null : (QuadcopterEntity) level.getEntity(rig.drone());
        if (rig == null || drone == null) {
            return "kein Testbau";
        }
        BlockPos o = rig.origin();
        if (PHASE.get() == 0) {
            GateEntity floor = placeGate(player, o, "floor_flat", () -> GateMountSelfTests.use(player,
                    new GateMountSelfTests.Spec("redstone_floor_flat", GateSize.MEDIUM, GateMount.FLOOR_FLAT, 0, 6, -1, 14, Direction.UP, 0.0F), o));
            fill(level, o.offset(5, -2, 13), o.offset(7, -1, 15), Blocks.AIR.defaultBlockState());
            fill(level, o.offset(18, 8, 11), o.offset(24, 8, 17), Blocks.STONE.defaultBlockState());
            GateEntity ceiling = placeGate(player, o, "ceiling_flat", () -> GateMountSelfTests.use(player,
                    new GateMountSelfTests.Spec("redstone_ceiling_flat", GateSize.MEDIUM, GateMount.CEILING_FLAT, 0, 21, 8, 14, Direction.DOWN, 0.0F), o));
            fill(level, o.offset(20, 8, 13), o.offset(22, 8, 15), Blocks.AIR.defaultBlockState());
            List<Flat> flats = new ArrayList<>();
            for (GateEntity gate : List.of(floor, ceiling)) {
                GateSignalTarget target = gate.signalTarget();
                BlockPos support = GateSignals.support(target);
                BlockPos lamp = support.relative(outward(gate.geometry(), target));
                flats.add(new Flat(gate.mount().name().toLowerCase(Locale.ROOT), gate.getId(), target, lamp, level.getBlockState(lamp)));
                level.setBlockAndUpdate(lamp, Blocks.REDSTONE_LAMP.defaultBlockState());
            }
            FLATS.set(List.copyOf(flats));
            for (Flat flat : flats) {
                String problem = flyFlat(player, level, drone, flat, o);
                if (problem != null) {
                    removeFlats(level, o);
                    return problem;
                }
            }
            T0.set(level.getGameTime());
            PHASE.set(1);
            return WAIT + "Pulse der liegenden Gates enden";
        }
        long dt = level.getGameTime() - T0.get();
        if (dt < GateSignal.DEFAULT_PULSE + 6) {
            return WAIT + "Pulse der liegenden Gates enden (" + dt + ")";
        }
        String problem = null;
        for (Flat flat : FLATS.get()) {
            if (problem == null && (lit(level, flat.lamp()) || GateSignals.active(level) != 0)) {
                problem = flat.name() + ": nach " + dt + " Ticks noch Strom, " + GateSignals.active(level) + " Signale";
            }
        }
        removeFlats(level, o);
        return problem;
    }

    private static @Nullable String flyFlat(ServerPlayer player, ServerLevel level, QuadcopterEntity drone, Flat flat, BlockPos o) {
        GateEntity gate = gate(level, flat.id());
        BlockPos support = GateSignals.support(flat.target());
        BlockState state = level.getBlockState(support);
        if (gate == null) {
            return flat.name() + ": Gate fehlt";
        }
        if (!state.isRedstoneConductor(level, support)) {
            return flat.name() + ": Zielblock " + support.subtract(o).toShortString() + " ist " + state + " statt eines Randblocks unter dem Balken";
        }
        fly(player, drone, gate, true);
        if (gate.pulseLeft() != GateSignal.DEFAULT_PULSE || !flat.target().equals(gate.emitting())) {
            return flat.name() + ": Puls nach dem Durchflug " + gate.pulseLeft() + " auf " + gate.emitting() + " statt " + flat.target();
        }
        return lit(level, flat.lamp()) ? null : flat.name() + ": Lampe neben dem Randblock " + support.subtract(o).toShortString() + " bleibt aus";
    }

    private static void removeFlats(ServerLevel level, BlockPos o) {
        for (Flat flat : FLATS.getAndSet(List.of())) {
            Entity entity = level.getEntity(flat.id());
            if (entity != null) {
                entity.discard();
            }
            level.setBlockAndUpdate(flat.lamp(), flat.lampWas());
        }
        fill(level, o.offset(5, -2, 13), o.offset(7, -1, 15), Blocks.SMOOTH_STONE.defaultBlockState());
        fill(level, o.offset(18, 8, 11), o.offset(24, 8, 17), Blocks.AIR.defaultBlockState());
    }

    private static void keepTicket(ServerLevel level) {
        ChunkPos ticket = TICKET.get();
        if (ticket != null) {
            level.getChunkSource().addTicketWithRadius(ServerModule.DRONE_TICKET.get(), ticket, TICKET_RADIUS);
        }
    }

    private static @Nullable String frozen(MinecraftServer server) {
        Rig rig = RIG.get();
        ServerPlayer player = player(server);
        ServerLevel level = player.level();
        if (rig == null) {
            return "kein Testbau";
        }
        GateEntity gate = gate(level, rig.standing());
        BlockPos o = rig.origin();
        BlockPos wire = rig.at(4, 0, 1);
        BlockPos lamp = rig.at(4, 0, 4);
        keepTicket(level);
        int phase = PHASE.get();
        if (phase == 0) {
            QuadcopterEntity drone = (QuadcopterEntity) level.getEntity(rig.drone());
            if (gate == null || drone == null) {
                return "kein Gate oder keine Drohne";
            }
            SAVED_SIGNAL.set(gate.signal());
            VERDICT.set(null);
            gate.setSignal(GateSignal.DEFAULT.withPulse(FROZEN_PULSE));
            Vec3 front = entry(gate.geometry(), 1.5);
            drone.snapTo(front.x, front.y - drone.getBbHeight() * 0.5, front.z, drone.getYRot(), 0.0F);
            ChunkPos chunk = gate.chunkPosition();
            TICKET.set(new ChunkPos(chunk.x() + 1, chunk.z()));
            keepTicket(level);
            RETURN.set(new Spot(player.position(), player.getYRot(), player.getXRot()));
            player.teleportTo(level, o.getX() - FAR + 0.5, o.getY() + 48.0, o.getZ() + 0.5, Set.of(), 0.0F, 0.0F, true);
            T0.set(level.getGameTime());
            PHASE.set(1);
            return WAIT + "Spieler weit weg, Gate-Chunk ohne Entity-Ticks";
        }
        if (phase == 1) {
            if (gate == null) {
                return finishFrozen(player, level, "Gate im Chunk ohne Entity-Ticks nicht geladen");
            }
            BlockPos at = gate.blockPosition();
            if (level.isPositionEntityTicking(at) || !level.isLoaded(GateSignals.support(gate.signalTarget())) || !level.shouldTickBlocksAt(lamp)) {
                return level.getGameTime() - T0.get() > 600 ? finishFrozen(player, level, "Gate-Chunk bleibt in diesem Zustand: Entity-Ticks "
                        + level.isPositionEntityTicking(at) + ", Block-Ticks an der Lampe " + level.shouldTickBlocksAt(lamp))
                        : WAIT + "Gate-Chunk tickt noch";
            }
            QuadcopterEntity drone = (QuadcopterEntity) level.getEntity(rig.drone());
            if (drone == null) {
                return finishFrozen(player, level, "Drohne im Chunk ohne Entity-Ticks nicht geladen");
            }
            fly(player, drone, gate, true);
            if (gate.pulseLeft() != FROZEN_PULSE || dust(level, wire) != 15 || !lit(level, lamp)) {
                return finishFrozen(player, level, "kein Strom beim Durchflug im Chunk ohne Entity-Ticks: Puls " + gate.pulseLeft() + ", Draht "
                        + dust(level, wire) + ", Lampe " + lit(level, lamp));
            }
            T0.set(level.getGameTime());
            PHASE.set(2);
            return WAIT + "Puls läuft im Chunk ohne Entity-Ticks";
        }
        if (phase == 2) {
            long dt = level.getGameTime() - T0.get();
            if (gate != null && dt < FROZEN_PULSE && level.isPositionEntityTicking(gate.blockPosition())) {
                return finishFrozen(player, level, "Gate-Chunk bekommt während des Pulses wieder Entity-Ticks (nach " + dt + " Ticks)");
            }
            if (gate != null && dt <= FROZEN_PULSE && gate.pulseLeft() != Math.max(0, FROZEN_PULSE - (int) dt)) {
                return finishFrozen(player, level, "nach " + dt + " Ticks im Chunk ohne Entity-Ticks noch " + gate.pulseLeft() + " Ticks Puls statt "
                        + Math.max(0, FROZEN_PULSE - (int) dt));
            }
            if (dt < FROZEN_PULSE + 6) {
                return WAIT + "Puls läuft im Chunk ohne Entity-Ticks (" + dt + ")";
            }
            boolean stuck = gate == null || gate.pulseLeft() != 0 || GateSignals.active(level) != 0 || dust(level, wire) != 0 || lit(level, lamp);
            return finishFrozen(player, level, stuck ? "Puls hängt im Chunk ohne Entity-Ticks: nach " + dt + " Ticks Restpuls "
                    + (gate == null ? "-" : gate.pulseLeft()) + ", " + GateSignals.active(level) + " Signale, Draht " + dust(level, wire)
                    + ", Lampe " + lit(level, lamp) : null);
        }
        if (gate == null || !level.isPositionEntityTicking(gate.blockPosition()) || !ticking(level, o)) {
            return level.getGameTime() - T0.get() > 600 ? "Testgelände tickt nach der Rückkehr nicht wieder" : WAIT + "Testgelände tickt wieder";
        }
        ChunkPos ticket = TICKET.getAndSet(null);
        if (ticket != null) {
            level.getChunkSource().removeTicketWithRadius(ServerModule.DRONE_TICKET.get(), ticket, TICKET_RADIUS);
        }
        GateSignal saved = SAVED_SIGNAL.getAndSet(null);
        if (saved != null) {
            gate.setSignal(saved);
        }
        return VERDICT.getAndSet(null);
    }

    private static String finishFrozen(ServerPlayer player, ServerLevel level, @Nullable String verdict) {
        VERDICT.set(verdict);
        Spot spot = RETURN.getAndSet(null);
        if (spot != null) {
            player.teleportTo(level, spot.position().x, spot.position().y, spot.position().z, Set.of(), spot.yaw(), spot.pitch(), true);
        }
        T0.set(level.getGameTime());
        PHASE.set(3);
        return WAIT + "Spieler zurück";
    }

    private static @Nullable String persistence(MinecraftServer server) {
        Rig rig = RIG.get();
        ServerPlayer player = player(server);
        ServerLevel level = player.level();
        if (rig == null) {
            return "kein Testbau";
        }
        BlockPos wire = rig.at(4, 0, 1);
        BlockPos lamp = rig.at(4, 0, 4);
        if (PHASE.get() == 0) {
            GateEntity gate = gate(level, rig.standing());
            GateEntity wall = gate(level, rig.wall());
            QuadcopterEntity drone = (QuadcopterEntity) level.getEntity(rig.drone());
            if (gate == null || wall == null || drone == null) {
                return "Testbau unvollständig: Gate " + gate + ", Tauchtor " + wall + ", Drohne " + drone;
            }
            ProblemReporter.Collector reporter = new ProblemReporter.Collector();
            TagValueOutput plain = TagValueOutput.createWithContext(reporter, server.registryAccess());
            wall.saveWithoutId(plain);
            CompoundTag plainTag = plain.buildResult();
            if (plainTag.contains("RedstoneSignal") || plainTag.contains("PulseTicks") || plainTag.contains("SignalPulse")) {
                return "Gate mit Standard-Signal speichert Signal-Daten: " + plainTag.keySet();
            }
            gate.setSignal(new GateSignal(true, 30, false));
            fly(player, drone, gate, true);
            TagValueOutput output = TagValueOutput.createWithContext(reporter, server.registryAccess());
            gate.saveWithoutId(output);
            CompoundTag saved = output.buildResult();
            GateEntity copy = RaceModule.GATE.get().create(level, EntitySpawnReason.LOAD);
            copy.load(TagValueInput.create(reporter, server.registryAccess(), saved));
            if (!copy.signal().equals(gate.signal()) || copy.pulseLeft() != 30) {
                return "nach dem Laden: " + copy.signal() + ", Puls " + copy.pulseLeft() + " statt " + gate.signal() + ", 30";
            }
            CompoundTag off = saved.copy();
            off.putBoolean("RedstoneSignal", false);
            GateEntity quiet = RaceModule.GATE.get().create(level, EntitySpawnReason.LOAD);
            quiet.load(TagValueInput.create(reporter, server.registryAccess(), off));
            if (quiet.signal().enabled() || quiet.pulseLeft() != 0) {
                return "ausgeschaltetes Gate lädt mit Puls " + quiet.pulseLeft();
            }
            if (!reporter.isEmpty()) {
                return "Meldungen beim Speichern/Laden: " + reporter.getReport();
            }
            if (dust(level, wire) != 15 || GateSignals.active(level) != 1) {
                return "kein Strom vor dem Abbauen";
            }
            gate.discard();
            if (dust(level, wire) != 0 || GateSignals.active(level) != 0) {
                return "Abbauen mitten im Puls lässt Strom an: Draht " + dust(level, wire) + ", " + GateSignals.active(level) + " Signale";
            }
            T0.set(level.getGameTime());
            PHASE.set(1);
            return WAIT + "Lampe geht nach dem Abbauen aus";
        }
        long dt = level.getGameTime() - T0.get();
        if (dt < 6) {
            return WAIT + "Lampe geht nach dem Abbauen aus";
        }
        return lit(level, lamp) ? "Lampe nach dem Abbauen noch an" : null;
    }

    private static @Nullable String cleanup(MinecraftServer server) {
        Rig rig = RIG.getAndSet(null);
        ServerPlayer player = player(server);
        ServerLevel level = player.level();
        if (rig == null) {
            return null;
        }
        for (int id : new int[]{rig.standing(), rig.wall(), rig.freeform(), rig.drone()}) {
            Entity entity = id < 0 ? null : level.getEntity(id);
            if (entity != null) {
                entity.discard();
            }
        }
        BlockPos o = rig.origin();
        fill(level, o.offset(X0, 0, Z0), o.offset(X1, 9, Z1), Blocks.AIR.defaultBlockState());
        player.setItemInHand(InteractionHand.MAIN_HAND, rig.held());
        player.getAbilities().flying = false;
        player.onUpdateAbilities();
        Vec3 home = rig.home();
        player.teleportTo(level, home.x, home.y, home.z, Set.of(), player.getYRot(), player.getXRot(), true);
        return GateSignals.active(level) == 0 ? null : GateSignals.active(level) + " Signale nach dem Aufräumen";
    }

    public static void registerScreenshots(Supplier<BlockPos> base) {
        Supplier<BlockPos> origin = () -> base.get().offset(SHOT_X, 0, SHOT_Z);
        Screenshots.step("bildschirmfotos: Gate-Redstone – Durchflug schaltet Lampe und Kolben", "gate_redstone", 30,
                new ShotScene(origin, new Vec3(9.0, 3.4, -4.5), new Vec3(4.5, 0.9, 0.6), false));
        Screenshots.step("bildschirmfotos: Gate-Redstone – Tauchtor an der Wand und Freiform-Fenster", "gate_redstone_wall", 30,
                new ShotScene(origin, new Vec3(20.5, 2.6, 17.0), new Vec3(20.5, 3.4, 8.5), false));
        Screenshots.step("bildschirmfotos: Gate-Redstone – Gate einstellen, Seite Redstone", "gate_redstone_editor", 30,
                new ShotScene(origin, new Vec3(8.5, 4.2, -4.0), new Vec3(4.8, 1.0, 0.5), true));
        SelfTest.clientStep("bildschirmfotos: Gate-Redstone aufräumen", 200, GateRedstoneSelfTests::cleanupShots);
    }

    private static StepResult cleanupShots(SelfTest.StepContext ctx) {
        if (ctx.tick() == 0) {
            ctx.state().put(FUTURE, ctx.onServer(server -> {
                ServerPlayer player = player(server);
                ServerLevel level = player.level();
                Rig rig = SHOT_RIG.getAndSet(null);
                if (rig != null) {
                    for (int id : new int[]{rig.standing(), rig.wall(), rig.freeform(), rig.drone()}) {
                        Entity entity = level.getEntity(id);
                        if (entity != null) {
                            entity.discard();
                        }
                    }
                }
                player.getAbilities().flying = false;
                player.onUpdateAbilities();
                Vec3 home = SHOT_HOME.getAndSet(null);
                if (home != null) {
                    player.teleportTo(level, home.x, home.y, home.z, Set.of(), player.getYRot(), player.getXRot(), true);
                }
            }));
            return StepResult.pending();
        }
        CompletableFuture<?> future = (CompletableFuture<?>) ctx.state().get(FUTURE);
        if (future.isCompletedExceptionally()) {
            return StepResult.fail("Server-Aktion fehlgeschlagen: " + future.exceptionNow());
        }
        return future.isDone() ? StepResult.pass() : StepResult.pending();
    }

    private static final class ShotScene implements Screenshots.Scene {
        private final Supplier<BlockPos> origin;
        private final Vec3 eye;
        private final Vec3 target;
        private final boolean editor;
        private ItemStack previous = ItemStack.EMPTY;

        private ShotScene(Supplier<BlockPos> origin, Vec3 eye, Vec3 target, boolean editor) {
            this.origin = origin;
            this.eye = eye;
            this.target = target;
            this.editor = editor;
        }

        private Vec3 world(Vec3 local) {
            BlockPos o = this.origin.get();
            return new Vec3(o.getX() + local.x, o.getY() + local.y, o.getZ() + local.z);
        }

        @Override
        public StepResult prepare(SelfTest.StepContext ctx) {
            Minecraft mc = Minecraft.getInstance();
            int stage = (int) ctx.state().getOrDefault("shot", 0);
            CompletableFuture<?> future = (CompletableFuture<?>) ctx.state().get(FUTURE);
            if (future != null) {
                if (future.isCompletedExceptionally()) {
                    return StepResult.fail("Server-Aktion fehlgeschlagen: " + future.exceptionNow());
                }
                if (!future.isDone()) {
                    return StepResult.pending();
                }
                ctx.state().remove(FUTURE);
                String problem = RESULT.get();
                if (problem != null && !problem.startsWith(WAIT)) {
                    return StepResult.fail(problem);
                }
                if (problem == null) {
                    ctx.state().put("shot", ++stage);
                } else if (ctx.tick() > 400) {
                    return StepResult.fail(problem);
                }
            }
            if (stage == 0) {
                RESULT.set(null);
                ctx.state().put(FUTURE, ctx.onServer(server -> {
                    ServerPlayer player = player(server);
                    Vec3 at = this.world(this.eye).subtract(0.0, player.getEyeHeight(), 0.0);
                    SHOT_HOME.compareAndSet(null, player.position());
                    player.getAbilities().flying = true;
                    player.onUpdateAbilities();
                    player.teleportTo(player.level(), at.x, at.y, at.z, Set.of(), 0.0F, 0.0F, true);
                    this.previous = player.getMainHandItem().copy();
                    player.setItemInHand(InteractionHand.MAIN_HAND, this.editor ? new ItemStack(RaceModule.COURSE_TOOL.get()) : ItemStack.EMPTY);
                    RESULT.set(null);
                }));
                return StepResult.pending();
            }
            if (stage == 1) {
                RESULT.set(null);
                ctx.state().put(FUTURE, ctx.onServer(server -> {
                    ServerPlayer player = player(server);
                    ServerLevel level = player.level();
                    BlockPos o = this.origin.get();
                    if (!ticking(level, o)) {
                        RESULT.set(WAIT + "Bildschirmfoto-Gelände lädt");
                        return;
                    }
                    Rig rig = SHOT_RIG.get();
                    if (rig == null) {
                        rig = build(player, o, player.position(), ItemStack.EMPTY);
                        SHOT_RIG.set(rig);
                    }
                    QuadcopterEntity drone = (QuadcopterEntity) level.getEntity(rig.drone());
                    for (int id : new int[]{rig.standing(), rig.wall(), rig.freeform()}) {
                        GateEntity gate = gate(level, id);
                        if (gate == null || drone == null) {
                            RESULT.set("Gate oder Drohne fehlt im Bildschirmfoto-Gelände");
                            return;
                        }
                        gate.setSignal(GateSignal.DEFAULT.withPulse(SHOT_PULSE));
                        if (!this.editor) {
                            fly(player, drone, gate, true);
                        }
                    }
                    GateEntity standing = gate(level, rig.standing());
                    if (standing != null && drone != null) {
                        Vec3 near = entry(standing.geometry(), this.editor ? 2.5 : 1.0);
                        drone.snapTo(near.x, near.y - drone.getBbHeight() * 0.5, near.z, 0.0F, 0.0F);
                        drone.resetTo(new Vec3(near.x, near.y - drone.getBbHeight() * 0.5, near.z), new Quaternionf().rotationY((float) Math.PI));
                    }
                    RESULT.set(null);
                }));
                return StepResult.pending();
            }
            if (mc.player == null || mc.level == null) {
                return StepResult.pending();
            }
            this.hold(ctx);
            Rig rig = SHOT_RIG.get();
            if (rig == null || !(mc.level.getEntity(rig.standing()) instanceof GateEntity gate)) {
                return ctx.tick() > 400 ? StepResult.fail("Gate fehlt beim Client") : StepResult.pending();
            }
            if (mc.player.getEyePosition().distanceTo(this.world(this.eye)) > 0.6) {
                return ctx.tick() > 400 ? StepResult.fail("Spieler nicht am Blickpunkt") : StepResult.pending();
            }
            if (this.editor) {
                if (!GateMounting.holdsTool(mc.player) || !gate.signal().equals(GateSignal.DEFAULT.withPulse(SHOT_PULSE))) {
                    return ctx.tick() > 400 ? StepResult.fail("Kurs-Werkzeug oder Gate-Daten fehlen beim Client") : StepResult.pending();
                }
                GateEditor current = GateEditor.current();
                if (current == null || current.gate() != gate) {
                    GateEditor.start(gate);
                    current = GateEditor.current();
                    if (current == null) {
                        return StepResult.fail("Gate einstellen startet nicht");
                    }
                    current.showPage(1);
                    current.model().select(GateEditModel.Row.PULSE);
                }
                return StepResult.pass();
            }
            BlockState piston = mc.level.getBlockState(rig.at(4, 0, -3));
            boolean on = piston.is(Blocks.PISTON) && piston.getValue(PistonBaseBlock.EXTENDED);
            List<BlockPos> lamps = new ArrayList<>(wallLamps(rig.origin()));
            lamps.add(rig.at(4, 0, 4));
            lamps.add(rig.at(25, 1, 8));
            for (BlockPos pos : lamps) {
                BlockState lamp = mc.level.getBlockState(pos);
                on &= lamp.is(Blocks.REDSTONE_LAMP) && lamp.getValue(RedstoneLampBlock.LIT);
            }
            return on ? StepResult.pass() : ctx.tick() > 400 ? StepResult.fail("Lampen oder Kolben beim Client nicht an") : StepResult.pending();
        }

        @Override
        public void hold(SelfTest.StepContext ctx) {
            LocalPlayer player = Minecraft.getInstance().player;
            if (player == null) {
                return;
            }
            Vec3 eye = player.getEyePosition();
            Vec3 aim = this.world(this.target);
            double dx = aim.x - eye.x;
            double dy = aim.y - eye.y;
            double dz = aim.z - eye.z;
            float yaw = (float) Math.toDegrees(Math.atan2(-dx, dz));
            float pitch = (float) -Math.toDegrees(Math.atan2(dy, Math.sqrt(dx * dx + dz * dz)));
            player.setYRot(yaw);
            player.setXRot(pitch);
            player.yRotO = yaw;
            player.xRotO = pitch;
            player.setYHeadRot(yaw);
            player.yHeadRotO = yaw;
            player.setYBodyRot(yaw);
            player.yBodyRotO = yaw;
        }

        @Override
        public void finish(SelfTest.StepContext ctx) {
            if (this.editor) {
                GateEditor.stop();
                Screenshots.parkMouse(Minecraft.getInstance());
            }
            ItemStack restore = this.previous;
            ctx.onServer(server -> player(server).setItemInHand(InteractionHand.MAIN_HAND, restore));
        }
    }
}
