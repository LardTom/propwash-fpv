package de.lardtom.propwash.drone.client;

import de.lardtom.propwash.api.PropwashApi;
import de.lardtom.propwash.api.build.DronePreset;
import de.lardtom.propwash.apiimpl.Conversions;
import de.lardtom.propwash.build.AccessoryType;
import de.lardtom.propwash.build.DroneBuild;
import de.lardtom.propwash.core.CoreComponents;
import de.lardtom.propwash.core.client.ClientBus;
import de.lardtom.propwash.dev.SelfTest;
import de.lardtom.propwash.dev.SelfTest.StepResult;
import de.lardtom.propwash.dev.example.ExampleAccessories;
import de.lardtom.propwash.drone.DroneModule;
import de.lardtom.propwash.drone.DroneScale;
import de.lardtom.propwash.drone.QuadcopterEntity;
import de.lardtom.propwash.sim.AirframeSpec;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Util;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicReference;

public final class PlacedDroneSelfTests {
    public static final String GROUP = "drone: Gesetzte Drohne";
    public static final String PLACE = GROUP + " sinkt nicht ein, steigt nicht und springt nicht (FPV, Speer unten, Kamera-Drohne aus dem Add-on): "
            + "gezeichnete Höhe gegen Server über 100 Ticks";
    public static final String LANDING = GROUP + " landet bei einer Lücke im Netz nicht unter dem Boden (FPV und Speer unten)";
    private static final int OFFSET_X = 6;
    private static final int PAD_X = 2;
    private static final int PAD_Z = 5;
    private static final int HEIGHT = 6;
    private static final int SPACING = 3;
    private static final int WATCH_TICKS = 100;
    private static final int APPEAR_TICKS = 40;
    private static final int MIN_FRAMES = 60;
    private static final double TOLERANCE = 0.001;
    private static final long MS = 1_000_000L;
    private static final long SEND_NANOS = 50L * MS;
    private static final long TOUCHDOWN_NANOS = 1_000L * MS;
    private static final long GAP_BEFORE_NANOS = 100L * MS;
    private static final long GAP_NANOS = 300L * MS;
    private static final long LANDING_RUN_NANOS = 2_500L * MS;
    private static final double SINK_SPEED = 3.0;
    private static final double SIDE_SPEED = 1.0;
    private static final int LANDING_ID = -0x51A7;
    private static final String FUTURE = "future";
    private static final String STARTED = "started";
    private static final String CLEANUP = "cleanup";
    private static final Map<BlockPos, BlockState> ORIGINAL = new LinkedHashMap<>();
    private static final List<Watch> WATCHES = new CopyOnWriteArrayList<>();
    private static final AtomicReference<String> PROBLEM = new AtomicReference<>();
    private static final AtomicReference<BlockPos> BASE = new AtomicReference<>();
    private static final AtomicReference<ItemStack> HAND = new AtomicReference<>(ItemStack.EMPTY);

    private record Case(String name, DroneBuild build) {
    }

    private static final class Watch {
        final String name;
        final double ground;
        final double reach;
        final int id;
        final @Nullable UUID uuid;
        final List<Double> server = Collections.synchronizedList(new ArrayList<>());
        final @Nullable QuadcopterEntity synthetic;
        final double rest;
        long origin;
        int next;
        double lowest = Double.MAX_VALUE;
        double highest = -Double.MAX_VALUE;
        double biggest;
        double last = Double.NaN;
        int frames;
        volatile boolean closed;

        Watch(String name, double ground, double reach, int id, @Nullable UUID uuid, @Nullable QuadcopterEntity synthetic, double rest) {
            this.name = name;
            this.ground = ground;
            this.reach = reach;
            this.id = id;
            this.uuid = uuid;
            this.synthetic = synthetic;
            this.rest = rest;
        }

        void frame(double y) {
            if (!Double.isNaN(this.last)) {
                this.biggest = Math.max(this.biggest, Math.abs(y - this.last));
            }
            this.lowest = Math.min(this.lowest, y);
            this.highest = Math.max(this.highest, y);
            this.last = y;
            this.frames++;
        }
    }

    private PlacedDroneSelfTests() {
    }

    public static void register() {
        SelfTest.clientStep(PLACE, 400, PlacedDroneSelfTests::place);
        SelfTest.clientStep(LANDING, 300, PlacedDroneSelfTests::landing);
        ClientBus.FRAME_START.register(PlacedDroneSelfTests::frame);
        QuadcopterEntity.SERVER_TICK.register(PlacedDroneSelfTests::serverTick);
    }

    private static void serverTick(QuadcopterEntity drone) {
        for (Watch watch : WATCHES) {
            if (!watch.closed && watch.synthetic == null && drone.getUUID().equals(watch.uuid)) {
                watch.server.add(drone.getY());
            }
        }
    }

    private static double reach(QuadcopterEntity drone) {
        float scale = drone.scale();
        return AirframeSpec.of(drone.build()).accessoryDepth(drone.orientation()) * (DroneScale.scaled(scale) ? scale : 1.0);
    }

    private static DroneBuild spear() {
        AccessoryType spear = AccessoryType.byId(ExampleAccessories.SPEAR)
                .orElseThrow(() -> new IllegalStateException("Beispiel-Zubehör fehlt: " + ExampleAccessories.SPEAR));
        return DroneBuild.FREESTYLE.withoutAccessories().withAccessory(spear);
    }

    private static Optional<Case> cameraDrone() {
        Case best = null;
        double deepest = 0.0;
        for (DronePreset preset : PropwashApi.builds().presets()) {
            if (!preset.id().getNamespace().equals("camdrones")) {
                continue;
            }
            Optional<DroneBuild> build = Conversions.build(preset.data().build());
            if (build.isEmpty()) {
                continue;
            }
            double depth = AirframeSpec.of(build.get()).accessoryDepth(new Quaternionf());
            if (best == null || depth > deepest) {
                best = new Case("Kamera-Drohne " + preset.id(), build.get());
                deepest = depth;
            }
        }
        return Optional.ofNullable(best);
    }

    private static List<Case> cases() {
        List<Case> cases = new ArrayList<>();
        cases.add(new Case("FPV-Drohne (Freestyle)", DroneBuild.FREESTYLE));
        cases.add(new Case("Freestyle mit Beispiel-Speer unten", spear()));
        cameraDrone().ifPresent(cases::add);
        return cases;
    }

    private static ServerPlayer player(MinecraftServer server) {
        if (server.getPlayerList().getPlayers().isEmpty()) {
            throw new IllegalStateException("kein Spieler auf dem Server");
        }
        return server.getPlayerList().getPlayers().getFirst();
    }

    private static void set(ServerLevel level, BlockPos pos, BlockState state) {
        BlockState old = level.getBlockState(pos);
        if (old.equals(state)) {
            return;
        }
        ORIGINAL.putIfAbsent(pos.immutable(), old);
        level.setBlock(pos, state, Block.UPDATE_CLIENTS);
    }

    private static BlockPos pad(MinecraftServer server) {
        ServerPlayer player = player(server);
        ServerLevel level = player.level();
        restore(level);
        BlockPos base = new BlockPos(player.getBlockX() + OFFSET_X, player.getBlockY(), player.getBlockZ());
        for (int dx = -PAD_X; dx <= PAD_X; dx++) {
            for (int dz = -PAD_Z; dz <= PAD_Z; dz++) {
                level.getChunk((base.getX() + dx) >> 4, (base.getZ() + dz) >> 4);
                set(level, base.offset(dx, -1, dz), Blocks.SMOOTH_STONE.defaultBlockState());
                for (int dy = 0; dy < HEIGHT; dy++) {
                    set(level, base.offset(dx, dy, dz), Blocks.AIR.defaultBlockState());
                }
            }
        }
        BASE.set(base);
        return base;
    }

    private static void restore(ServerLevel level) {
        for (Map.Entry<BlockPos, BlockState> entry : ORIGINAL.entrySet()) {
            level.setBlock(entry.getKey(), entry.getValue(), Block.UPDATE_CLIENTS);
        }
        ORIGINAL.clear();
    }

    private static void placeAll(MinecraftServer server) {
        ServerPlayer player = player(server);
        ServerLevel level = player.level();
        BlockPos base = pad(server);
        List<Case> cases = cases();
        HAND.set(player.getMainHandItem().copy());
        try {
            for (int i = 0; i < cases.size(); i++) {
                Case placed = cases.get(i);
                BlockPos ground = base.offset(0, -1, (i - 1) * SPACING);
                Vec3 click = new Vec3(ground.getX() + 0.5, ground.getY() + 1.0, ground.getZ() + 0.5);
                AABB around = new AABB(ground.above()).inflate(0.5);
                List<UUID> before = level.getEntitiesOfClass(QuadcopterEntity.class, around).stream().map(Entity::getUUID).toList();
                ItemStack stack = new ItemStack(DroneModule.DRONE_ITEM.get());
                stack.set(CoreComponents.DRONE_BUILD.get(), placed.build());
                stack.set(CoreComponents.BATTERY_CHARGE.get(), 1.0F);
                player.setItemInHand(InteractionHand.MAIN_HAND, stack);
                InteractionResult use = stack.useOn(new UseOnContext(player, InteractionHand.MAIN_HAND,
                        new BlockHitResult(click, Direction.UP, ground, false)));
                QuadcopterEntity drone = level.getEntitiesOfClass(QuadcopterEntity.class, around).stream()
                        .filter(found -> !before.contains(found.getUUID())).findFirst().orElse(null);
                if (!use.consumesAction() || drone == null) {
                    PROBLEM.set(placed.name() + ": nicht gesetzt (" + use + ")");
                    return;
                }
                Watch watch = new Watch(placed.name(), click.y, reach(drone), drone.getId(), drone.getUUID(), null, Double.NaN);
                watch.server.add(drone.getY());
                WATCHES.add(watch);
            }
        } finally {
            player.setItemInHand(InteractionHand.MAIN_HAND, HAND.getAndSet(ItemStack.EMPTY));
        }
    }

    private static void cleanup(MinecraftServer server) {
        ServerLevel level = player(server).level();
        for (Watch watch : WATCHES) {
            watch.closed = true;
            if (watch.uuid != null && level.getEntity(watch.uuid) instanceof QuadcopterEntity drone) {
                drone.discard();
            }
        }
        restore(level);
    }

    private static @Nullable StepResult await(SelfTest.StepContext ctx, String key, String what) {
        CompletableFuture<?> future = (CompletableFuture<?>) ctx.state().get(key);
        if (future == null) {
            return StepResult.fail(what + ": nicht gestartet");
        }
        if (future.isCompletedExceptionally()) {
            return finish(ctx, StepResult.fail(what + ": " + future.exceptionNow().getMessage()));
        }
        return future.isDone() ? null : StepResult.pending();
    }

    private static StepResult finish(SelfTest.StepContext ctx, StepResult result) {
        Minecraft mc = Minecraft.getInstance();
        for (Watch watch : WATCHES) {
            watch.closed = true;
            if (watch.synthetic != null && mc.level != null) {
                mc.level.removeEntity(watch.id, Entity.RemovalReason.DISCARDED);
            }
        }
        if (!ctx.state().containsKey(CLEANUP)) {
            ctx.state().put(CLEANUP, ctx.onServer(PlacedDroneSelfTests::cleanup));
        }
        return result;
    }

    private static String mm(double blocks) {
        return String.format(Locale.ROOT, "%.1f mm", blocks * 1000.0);
    }

    private static StepResult place(SelfTest.StepContext ctx) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) {
            return StepResult.fail("Keine Welt");
        }
        if (!ctx.state().containsKey(FUTURE)) {
            WATCHES.clear();
            PROBLEM.set(null);
            ctx.state().put(FUTURE, ctx.onServer(PlacedDroneSelfTests::placeAll));
            return StepResult.pending();
        }
        StepResult waiting = await(ctx, FUTURE, "Setzen");
        if (waiting != null) {
            return waiting;
        }
        String problem = PROBLEM.get();
        if (problem != null) {
            return finish(ctx, StepResult.fail(problem));
        }
        if (!ctx.state().containsKey(STARTED)) {
            boolean all = WATCHES.stream().allMatch(watch -> mc.level.getEntity(watch.id) instanceof QuadcopterEntity);
            if (!all) {
                return ctx.tick() > APPEAR_TICKS ? finish(ctx, StepResult.fail("gesetzte Drohne erscheint nicht beim Client")) : StepResult.pending();
            }
            ctx.state().put(STARTED, ctx.tick());
            return StepResult.pending();
        }
        if (ctx.tick() - (int) ctx.state().get(STARTED) < WATCH_TICKS) {
            return StepResult.pending();
        }
        WATCHES.forEach(watch -> watch.closed = true);
        if (!ctx.state().containsKey(CLEANUP)) {
            ctx.state().put(CLEANUP, ctx.onServer(PlacedDroneSelfTests::cleanup));
            return StepResult.pending();
        }
        StepResult cleaned = await(ctx, CLEANUP, "Aufräumen");
        if (cleaned != null) {
            return cleaned;
        }
        List<String> problems = new ArrayList<>();
        if (cameraDrone().isEmpty()) {
            ctx.log("ohne Kamera-Add-on: Fall Kamera-Drohne übersprungen");
        }
        for (Watch watch : WATCHES) {
            List<Double> server;
            synchronized (watch.server) {
                server = List.copyOf(watch.server);
            }
            double serverLow = server.stream().mapToDouble(Double::doubleValue).min().orElse(Double.NaN);
            double serverHigh = server.stream().mapToDouble(Double::doubleValue).max().orElse(Double.NaN);
            double settled = server.isEmpty() ? Double.NaN : server.getLast();
            ctx.log(String.format(Locale.ROOT, "%s: Server %d Ticks, Höhe über dem Boden %s bis %s, am Ende %s; Zubehör %s unter der Hitbox; "
                            + "gezeichnet %d Bilder, %s bis %s, größter Schritt %s, am Ende %s neben dem Server",
                    watch.name, server.size(), mm(serverLow - watch.ground), mm(serverHigh - watch.ground), mm(settled - watch.ground),
                    mm(watch.reach), watch.frames, mm(watch.lowest - watch.ground), mm(watch.highest - watch.ground), mm(watch.biggest),
                    mm(watch.last - settled)));
            if (server.size() < WATCH_TICKS / 2 || watch.frames < MIN_FRAMES) {
                problems.add(watch.name + ": zu wenig gemessen (" + server.size() + " Ticks, " + watch.frames + " Bilder)");
                continue;
            }
            if (watch.lowest - watch.reach < watch.ground - TOLERANCE) {
                problems.add(watch.name + ": steckt " + mm(watch.ground - (watch.lowest - watch.reach)) + " im Boden");
            }
            if (watch.lowest < serverLow - TOLERANCE) {
                problems.add(watch.name + ": sinkt " + mm(serverLow - watch.lowest) + " unter die Server-Höhe");
            }
            if (watch.highest > serverHigh + TOLERANCE) {
                problems.add(watch.name + ": steigt " + mm(watch.highest - serverHigh) + " über die Server-Höhe");
            }
            if (watch.biggest > serverHigh - serverLow + TOLERANCE) {
                problems.add(watch.name + ": springt " + mm(watch.biggest) + " in einem Bild (Server bewegt sie " + mm(serverHigh - serverLow) + ")");
            }
            if (Math.abs(watch.last - settled) > TOLERANCE) {
                problems.add(watch.name + ": steht am Ende " + mm(watch.last - settled) + " neben der Server-Höhe");
            }
        }
        return problems.isEmpty() ? StepResult.pass() : StepResult.fail(String.join("; ", problems));
    }

    private static StepResult landing(SelfTest.StepContext ctx) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) {
            return StepResult.fail("Keine Welt");
        }
        if (!ctx.state().containsKey(FUTURE)) {
            WATCHES.clear();
            ctx.state().put(FUTURE, ctx.onServer(PlacedDroneSelfTests::pad));
            return StepResult.pending();
        }
        StepResult waiting = await(ctx, FUTURE, "Platz bauen");
        if (waiting != null) {
            return waiting;
        }
        BlockPos base = BASE.get();
        if (!ctx.state().containsKey(STARTED)) {
            if (!mc.level.getBlockState(base.below()).is(Blocks.SMOOTH_STONE)) {
                return ctx.tick() > APPEAR_TICKS ? finish(ctx, StepResult.fail("Boden kommt nicht beim Client an")) : StepResult.pending();
            }
            List<Case> cases = List.of(new Case("FPV-Drohne (Freestyle)", DroneBuild.FREESTYLE), new Case("Freestyle mit Beispiel-Speer unten", spear()));
            long origin = Util.getNanos();
            for (int i = 0; i < cases.size(); i++) {
                QuadcopterEntity drone = new QuadcopterEntity(DroneModule.DRONE.get(), mc.level);
                drone.setId(LANDING_ID - i);
                drone.setBuild(cases.get(i).build());
                drone.setAttitude(new Quaternionf());
                drone.setNoGravity(true);
                double ground = base.getY();
                double rest = ground + reach(drone);
                Vec3 start = landingPosition(base, i, rest, 0L);
                drone.snapTo(start.x, start.y, start.z, 0.0F, 0.0F);
                mc.level.addEntity(drone);
                Watch watch = new Watch(cases.get(i).name(), ground, reach(drone), drone.getId(), null, drone, rest);
                watch.origin = origin;
                WATCHES.add(watch);
            }
            ctx.state().put(STARTED, ctx.tick());
            return StepResult.pending();
        }
        if (Util.getNanos() - WATCHES.getFirst().origin < LANDING_RUN_NANOS) {
            return StepResult.pending();
        }
        StepResult result = finishLanding(ctx);
        return finish(ctx, result);
    }

    private static StepResult finishLanding(SelfTest.StepContext ctx) {
        List<String> problems = new ArrayList<>();
        for (Watch watch : WATCHES) {
            ctx.log(String.format(Locale.ROOT, "%s: Sinkflug %.1f Blöcke/s, Pakete ab %d ms vor dem Aufsetzen %d ms verspätet; Zubehör %s unter der Hitbox; "
                            + "gezeichnet %d Bilder, tiefster Punkt %s über dem Boden, am Ende %s neben der Ruhelage",
                    watch.name, SINK_SPEED, GAP_BEFORE_NANOS / MS, GAP_NANOS / MS, mm(watch.reach), watch.frames,
                    mm(watch.lowest - watch.reach - watch.ground), mm(watch.last - watch.rest)));
            if (watch.frames < MIN_FRAMES) {
                problems.add(watch.name + ": zu wenige Bilder (" + watch.frames + ")");
                continue;
            }
            if (watch.lowest - watch.reach < watch.ground - TOLERANCE) {
                problems.add(watch.name + ": sinkt beim Landen " + mm(watch.ground - (watch.lowest - watch.reach)) + " unter den Boden");
            }
            if (Math.abs(watch.last - watch.rest) > TOLERANCE) {
                problems.add(watch.name + ": steht am Ende " + mm(watch.last - watch.rest) + " neben der Ruhelage");
            }
        }
        return problems.isEmpty() ? StepResult.pass() : StepResult.fail(String.join("; ", problems));
    }

    private static Vec3 landingPosition(BlockPos base, int index, double rest, long sent) {
        long flown = Math.min(sent, TOUCHDOWN_NANOS);
        double seconds = flown * 1.0E-9;
        double touchdownSeconds = TOUCHDOWN_NANOS * 1.0E-9;
        double x = base.getX() + 0.5 - SIDE_SPEED * touchdownSeconds + SIDE_SPEED * seconds;
        double z = base.getZ() + 0.5 + (index == 0 ? -SPACING : SPACING);
        return new Vec3(x, rest + SINK_SPEED * (touchdownSeconds - seconds), z);
    }

    private static long arrival(long sent) {
        return sent >= TOUCHDOWN_NANOS - GAP_BEFORE_NANOS && sent < TOUCHDOWN_NANOS + GAP_NANOS ? TOUCHDOWN_NANOS + GAP_NANOS : sent;
    }

    private static void frame(Minecraft mc, DeltaTracker delta) {
        if (mc.level == null || WATCHES.isEmpty()) {
            return;
        }
        float partial = delta.getGameTimeDeltaPartialTick(true);
        BlockPos base = BASE.get();
        for (int i = 0; i < WATCHES.size(); i++) {
            Watch watch = WATCHES.get(i);
            if (watch.closed) {
                continue;
            }
            QuadcopterEntity drone = watch.synthetic;
            if (drone != null && base != null) {
                long elapsed = Util.getNanos() - watch.origin;
                while (watch.next * SEND_NANOS <= LANDING_RUN_NANOS && arrival(watch.next * SEND_NANOS) <= elapsed) {
                    long sent = watch.next++ * SEND_NANOS;
                    boolean landed = sent >= TOUCHDOWN_NANOS;
                    drone.lerpMotion(landed ? Vec3.ZERO : new Vec3(SIDE_SPEED, -SINK_SPEED, 0.0).scale(SEND_NANOS * 1.0E-9));
                    drone.moveOrInterpolateTo(landingPosition(base, i, watch.rest, sent), 0.0F, 0.0F);
                }
            } else if (!(mc.level.getEntity(watch.id) instanceof QuadcopterEntity found)) {
                continue;
            } else {
                drone = found;
            }
            watch.frame(drone.getPosition(partial).y);
        }
    }
}
