package de.lardtom.propwash.relay.client;

import de.lardtom.propwash.build.DroneBuild;
import de.lardtom.propwash.build.VideoSystem;
import de.lardtom.propwash.content.ContentIds;
import de.lardtom.propwash.core.CoreComponents;
import de.lardtom.propwash.dev.SelfTest;
import de.lardtom.propwash.dev.SelfTest.StepResult;
import de.lardtom.propwash.dev.client.Screenshots;
import de.lardtom.propwash.drone.DroneInteractions;
import de.lardtom.propwash.drone.DroneModule;
import de.lardtom.propwash.drone.DronePiloting;
import de.lardtom.propwash.drone.PilotMode;
import de.lardtom.propwash.drone.QuadcopterEntity;
import de.lardtom.propwash.drone.RadioLink;
import de.lardtom.propwash.drone.client.PilotSession;
import de.lardtom.propwash.input.StickPositions;
import de.lardtom.propwash.osd.OsdData;
import de.lardtom.propwash.osd.OsdElement;
import de.lardtom.propwash.osd.client.OsdHud;
import de.lardtom.propwash.relay.RelayModule;
import de.lardtom.propwash.relay.RepeaterIndex;
import de.lardtom.propwash.relay.RepeaterMeter;
import de.lardtom.propwash.relay.SignalRepeaterBlock;
import de.lardtom.propwash.server.WorldRules;
import de.lardtom.propwash.server.client.ClientServerRules;
import de.lardtom.propwash.server.rules.WorldRuleSpecs;
import de.lardtom.propwash.sim.DroneSim;
import de.lardtom.propwash.sim.StabMode;
import de.lardtom.propwash.sim.Telemetry;
import de.lardtom.propwash.video.client.VideoProbe;
import de.lardtom.propwash.video.client.VideoState;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.SectionPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.AttachFace;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

public final class RelayFieldSelfTests {
    public static final String ROOMS_STEP = "relay: Raum hinter Betonwand und U-Gang – Repeater unter dem Türsturz, an der Türlaibung, zwei Repeater um die Ecken";
    public static final String METER_STEP = "relay: Signalmesser in der Hand und Balken am Repeater";
    public static final String FIELD_STEP = "relay: Freiland – geradeaus bis zum Abbruch, Repeater bei 60 % und 100 %: Reichweite wächst, RPT zeigt die Strecken";
    public static final String SERVER_STEP = "relay: Freiland – Funk über drei Repeater weiter als 1250 Blöcke, der Server gibt erst ohne Kette frei";
    public static final String CLEANUP_STEP = "relay: Freiland und Räume aufräumen";
    static final int INDOOR_DX = -3200;
    static final int INDOOR_DZ = -3200;
    static final int FIELD_DX = -3200;
    static final int FIELD_DZ = -1800;
    static final BlockPos U = new BlockPos(50, 0, 0);
    static final Vec3 ROOM_PILOT = new Vec3(6.5, 0.0, -50.0);
    static final Vec3 ROOM_DRONE = new Vec3(3.5, 2.4, 8.5);
    static final BlockPos ROOM_CEILING = new BlockPos(6, 2, 2);
    static final BlockPos ROOM_JAMB = new BlockPos(5, 1, 1);
    static final Vec3 U_PILOT = new Vec3(53.5, 0.0, 3.5);
    static final Vec3 U_DRONE = new Vec3(53.5, 1.4, 63.5);
    static final BlockPos U_FIRST = new BlockPos(91, 2, 3);
    static final BlockPos U_SECOND = new BlockPos(92, 1, 63);
    static final BlockPos METER_A = new BlockPos(-40, 0, -20);
    static final BlockPos METER_B = new BlockPos(-40, 0, 20);
    static final double BREAK = 0.1;
    private static final double CLEAN = 0.9;
    private static final double NOISY = 0.5;
    private static final double FLY_HEIGHT = 30.0;
    private static final double TRAVEL_STEP = 16.0;
    private static final int TRAVEL_TICKS = 4;
    private static final double MAX_MEASURE = 1000.0;
    private static final int TIMEOUT = 1200;
    private static final int SETTLE_TICKS = 40;
    private static final String PHASE = "relay_field_phase";
    private static final String SINCE = "relay_field_since";
    private static final String FUTURE = "relay_field_future";
    private static final String READY = "relay_field_ready";
    private static final String PROBE = "relay_field_probe";
    private static final String ROUNDS = "relay_field_rounds";
    static final AtomicReference<@Nullable BlockPos> INDOOR = new AtomicReference<>();
    static final AtomicReference<@Nullable BlockPos> FIELD = new AtomicReference<>();
    private static final AtomicReference<@Nullable String> SERVER = new AtomicReference<>();
    private static final List<BlockPos> FIELD_REPEATERS = new ArrayList<>();
    private static final Probe[] PROBES = probes();
    private static double cut = Double.NaN;

    private RelayFieldSelfTests() {
    }

    public static void register() {
        SelfTest.clientStep(ROOMS_STEP, 6000, RelayFieldSelfTests::roomsStep);
        SelfTest.clientStep(METER_STEP, 2400, RelayFieldSelfTests::meterStep);
        SelfTest.clientStep(FIELD_STEP, 6000, RelayFieldSelfTests::fieldStep);
        SelfTest.clientStep(SERVER_STEP, 6000, RelayFieldSelfTests::serverStep);
        SelfTest.clientStep(CLEANUP_STEP, 900, RelayFieldSelfTests::cleanupStep);
    }

    private static Probe[] probes() {
        List<Probe> list = new ArrayList<>();
        list.add(new Probe("Raum hinter Betonwand ohne Repeater", level -> { }, ROOM_PILOT, ROOM_DRONE, List.of(), false, 0));
        list.add(new Probe("Repeater unter dem Türsturz (Decke)", level -> mount(level, ROOM_CEILING, AttachFace.CEILING, Direction.NORTH), ROOM_PILOT,
                ROOM_DRONE, List.of(ROOM_CEILING), true, 1));
        list.add(new Probe("Repeater an der Türlaibung (Wand) statt unter dem Türsturz", level -> {
            unmount(level, ROOM_CEILING);
            mount(level, ROOM_JAMB, AttachFace.WALL, Direction.EAST);
        }, ROOM_PILOT, ROOM_DRONE, List.of(ROOM_JAMB), true, 1));
        list.add(new Probe("U-Gang ohne Repeater", level -> unmount(level, ROOM_JAMB), U_PILOT, U_DRONE, List.of(), false, 0));
        list.add(new Probe("U-Gang mit einem Repeater an der ersten Ecke", level -> mount(level, U_FIRST, AttachFace.CEILING, Direction.NORTH), U_PILOT,
                U_DRONE, List.of(U_FIRST), false, -1));
        list.add(new Probe("U-Gang mit zwei Repeatern um die Ecken (Decke und Wand)", level -> mount(level, U_SECOND, AttachFace.WALL, Direction.WEST),
                U_PILOT, U_DRONE, List.of(U_FIRST, U_SECOND), true, 2));
        return list.toArray(new Probe[0]);
    }

    static BlockPos indoor(ServerPlayer player) {
        BlockPos o = INDOOR.get();
        if (o != null) {
            return o;
        }
        ServerLevel level = player.level();
        BlockPos spawn = level.getRespawnData().globalPos().pos();
        int x = spawn.getX() + INDOOR_DX;
        int z = spawn.getZ() + INDOOR_DZ;
        for (int cx = (x - 80) >> 4; cx <= (x + 110) >> 4; cx++) {
            for (int cz = (z - 70) >> 4; cz <= (z + 80) >> 4; cz++) {
                level.getChunk(cx, cz);
            }
        }
        o = new BlockPos(x, level.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z), z);
        INDOOR.set(o);
        return o;
    }

    static void buildIndoor(ServerLevel level, BlockPos o) {
        BlockState concrete = Blocks.CONCRETE.pick(net.minecraft.world.item.DyeColor.WHITE).defaultBlockState();
        BlockState air = Blocks.AIR.defaultBlockState();
        fill(level, o, 0, 0, 0, 12, 8, 12, concrete);
        fill(level, o, 3, 0, 3, 9, 4, 9, air);
        fill(level, o, 5, 0, 0, 7, 2, 2, air);
        BlockPos u = o.offset(U);
        fill(level, u, 0, 0, 0, 44, 4, 66, concrete);
        fill(level, u, 2, 0, 2, 42, 2, 4, air);
        fill(level, u, 40, 0, 2, 42, 2, 64, air);
        fill(level, u, 2, 0, 62, 42, 2, 64, air);
    }

    private static void fill(ServerLevel level, BlockPos o, int x0, int y0, int z0, int x1, int y1, int z1, BlockState state) {
        for (int x = x0; x <= x1; x++) {
            for (int y = y0; y <= y1; y++) {
                for (int z = z0; z <= z1; z++) {
                    RelaySelfTests.put(level, o.offset(x, y, z), state);
                }
            }
        }
    }

    static void mount(ServerLevel level, BlockPos relative, AttachFace face, Direction facing) {
        BlockState state = RelayModule.SIGNAL_REPEATER.block().get().defaultBlockState().setValue(SignalRepeaterBlock.FACE, face)
                .setValue(SignalRepeaterBlock.FACING, facing);
        BlockPos pos = INDOOR.get().offset(relative);
        RelaySelfTests.put(level, pos, state);
        if (!level.getBlockState(pos).is(state.getBlock())) {
            throw new IllegalStateException("Repeater nicht gesetzt: " + pos);
        }
    }

    static void unmount(ServerLevel level, BlockPos relative) {
        RelaySelfTests.put(level, INDOOR.get().offset(relative), Blocks.AIR.defaultBlockState());
    }

    private static StepResult roomsStep(SelfTest.StepContext ctx) {
        Minecraft mc = Minecraft.getInstance();
        Map<String, Object> state = ctx.state();
        int phase = (int) state.getOrDefault(PHASE, 0);
        if (phase == 0) {
            state.put(FUTURE, ctx.onServer(server -> {
                ServerPlayer player = RelaySelfTests.player(server);
                RelaySelfTests.save(player);
                DronePiloting.pilotedBy(player.getUUID()).ifPresent(DronePiloting::release);
                BlockPos o = indoor(player);
                buildIndoor(player.level(), o);
                WorldRules.set(server, WorldRuleSpecs.SIGNAL_REPEATERS, 4);
                RelaySelfTests.teleport(player, Vec3.atLowerCornerOf(o).add(ROOM_PILOT), 180.0F, 0.0F);
                RelaySelfTests.spawnDrone(player.level(), Vec3.atLowerCornerOf(o).add(ROOM_DRONE));
            }));
            state.put(PHASE, 1);
            state.put(SINCE, ctx.tick());
            return StepResult.pending();
        }
        StepResult waiting = await(ctx);
        if (waiting != null) {
            return waiting;
        }
        BlockPos o = INDOOR.get();
        if (o == null || mc.level == null || mc.player == null) {
            return fail("Testplatz fehlt");
        }
        QuadcopterEntity drone = indoorDrone(mc, o);
        if (phase == 1) {
            BlockState roof = mc.level.getBlockState(o.offset(6, 8, 6));
            boolean ready = drone != null && roof.is(Blocks.CONCRETE.pick(net.minecraft.world.item.DyeColor.WHITE));
            if (!ready) {
                return since(ctx) > TIMEOUT ? fail("Raum kommt beim Client nicht an (Drohne " + RelaySelfTests.DRONE.get() + " " + (drone != null)
                        + ", Dach " + roof + ", Spieler " + mc.player.position().subtract(Vec3.atLowerCornerOf(o)) + ", Drohnen in der Nähe "
                        + mc.level.getEntitiesOfClass(QuadcopterEntity.class, new net.minecraft.world.phys.AABB(o).inflate(96.0)).size() + ")")
                        : StepResult.pending();
            }
            VideoProbe.attach(drone, VideoSystem.ANALOG, null);
            state.put(PHASE, 2);
            state.put(PROBE, 0);
            startProbe(ctx, 0);
            return StepResult.pending();
        }
        int index = (int) state.get(PROBE);
        if (index >= PROBES.length) {
            VideoProbe.detach();
            return StepResult.pass();
        }
        Probe probe = PROBES[index];
        if (drone == null) {
            return fail(probe.name + ": Drohne fehlt auf dem Client");
        }
        RepeaterIndex repeaters = RepeaterIndex.existing(mc.level);
        for (BlockPos pos : List.of(U_FIRST, U_SECOND)) {
            RepeaterSignals.INSTANCE.bars(o.offset(pos));
        }
        Vec3 antenna = drone.position().add(0.0, drone.getBbHeight() * 0.5, 0.0);
        Vec3 want = Vec3.atLowerCornerOf(o).add(probe.drone).add(0.0, drone.getBbHeight() * 0.5, 0.0);
        boolean mounted = true;
        for (BlockPos pos : List.of(ROOM_CEILING, ROOM_JAMB, U_FIRST, U_SECOND)) {
            mounted = mounted && (repeaters != null && repeaters.contains(o.offset(pos))) == probe.repeaters.contains(pos);
        }
        VideoState video = VideoState.INSTANCE;
        boolean placed = antenna.distanceTo(want) < 0.05 && mc.player.position().distanceTo(Vec3.atLowerCornerOf(o).add(probe.pilot)) < 0.05
                && video.status() == VideoState.LinkStatus.CONNECTED && mounted;
        if (!placed) {
            state.remove(ROUNDS);
            return since(ctx) > TIMEOUT ? fail(probe.name + ": Aufbau kommt nicht an (Drohne " + antenna + " statt " + want + ", Verbindung "
                    + video.status() + ", Repeater " + mounted + ")") : StepResult.pending();
        }
        if (!state.containsKey(ROUNDS)) {
            state.put(ROUNDS, VideoProbe.scanRounds());
            state.put(SINCE, ctx.tick());
            return StepResult.pending();
        }
        RelayVideo relay = video.relay();
        if (since(ctx) < SETTLE_TICKS || VideoProbe.scanRounds() - (int) state.get(ROUNDS) < 2 || !relay.settled()) {
            return since(ctx) > TIMEOUT ? fail(probe.name + ": Abschattung wird nicht fertig") : StepResult.pending();
        }
        float quality = VideoProbe.rawQuality();
        int hops = video.relayHops();
        StringBuilder detail = new StringBuilder();
        for (int hop = 0; hop < hops + 1 && hops > 0; hop++) {
            detail.append(String.format(Locale.ROOT, " | Strecke %d: %.1f m, %.1f dB, Signal %.2f", hop + 1, relay.hopDistance(hop), relay.hopLossDb(hop),
                    video.relayHopQuality(hop)));
        }
        String log = String.format(Locale.ROOT, "%s: Bild %.2f (direkt %.2f, %.1f dB), %d Repeater im Weg%s", probe.name, quality, video.directQuality(),
                VideoProbe.scanLossDb(), hops, detail);
        ctx.log(log);
        if (video.directQuality() > NOISY) {
            return fail(probe.name + ": direktes Bild zu gut für den Versuch: " + log);
        }
        if (probe.clear) {
            if (quality < CLEAN || hops != probe.hops) {
                return fail(probe.name + ": nicht klar über " + probe.hops + " Repeater: " + log);
            }
            for (int hop = 0; hop <= hops; hop++) {
                if (!(relay.hopLossDb(hop) < 3.0)) {
                    return fail(probe.name + ": Strecke " + (hop + 1) + " mit Sicht dämpft: " + log);
                }
            }
            if (index == PROBES.length - 1) {
                int first = RepeaterSignals.INSTANCE.bars(o.offset(U_FIRST));
                int second = RepeaterSignals.INSTANCE.bars(o.offset(U_SECOND));
                ctx.log(String.format(Locale.ROOT, "Balken an den Repeatern der Kette: erste Ecke %d, zweite Ecke %d (in der Kette %s/%s)", first, second,
                        RepeaterSignals.INSTANCE.inChain(o.offset(U_FIRST)), RepeaterSignals.INSTANCE.inChain(o.offset(U_SECOND))));
                if (first < 3 || second < 3 || !RepeaterSignals.INSTANCE.inChain(o.offset(U_FIRST))) {
                    return fail("Balken an den Repeatern zeigen die Kette nicht: " + first + "/" + second);
                }
            }
        } else if (quality > NOISY || probe.hops >= 0 && hops != probe.hops) {
            return fail(probe.name + ": erwartet verrauscht ohne Kette: " + log);
        }
        state.put(PROBE, index + 1);
        if (index + 1 < PROBES.length) {
            startProbe(ctx, index + 1);
        }
        return StepResult.pending();
    }

    private static @Nullable QuadcopterEntity indoorDrone(Minecraft mc, BlockPos o) {
        if (mc.level == null) {
            return null;
        }
        List<QuadcopterEntity> near = mc.level.getEntitiesOfClass(QuadcopterEntity.class, indoorArea(o));
        return near.size() == 1 ? near.getFirst() : null;
    }

    private static net.minecraft.world.phys.AABB indoorArea(BlockPos o) {
        return new net.minecraft.world.phys.AABB(Vec3.atLowerCornerOf(o.offset(-8, -4, -60)), Vec3.atLowerCornerOf(o.offset(100, 12, 72)));
    }

    private static void startProbe(SelfTest.StepContext ctx, int index) {
        Probe probe = PROBES[index];
        Map<String, Object> state = ctx.state();
        state.remove(ROUNDS);
        state.put(SINCE, ctx.tick());
        state.put(FUTURE, ctx.onServer(server -> {
            ServerPlayer player = RelaySelfTests.player(server);
            ServerLevel level = player.level();
            probe.setup.accept(level);
            BlockPos o = INDOOR.get();
            RelaySelfTests.teleport(player, Vec3.atLowerCornerOf(o).add(probe.pilot), probe.pilot.z < 0 ? 0.0F : 180.0F, 0.0F);
            List<QuadcopterEntity> drones = level.getEntitiesOfClass(QuadcopterEntity.class, indoorArea(o));
            if (drones.size() != 1) {
                throw new IllegalStateException("statt einer Testdrohne " + drones.size() + " im Testplatz");
            }
            RelaySelfTests.moveDrone(drones.getFirst(), Vec3.atLowerCornerOf(o).add(probe.drone));
        }));
    }

    private static StepResult meterStep(SelfTest.StepContext ctx) {
        Minecraft mc = Minecraft.getInstance();
        Map<String, Object> state = ctx.state();
        int phase = (int) state.getOrDefault(PHASE, 0);
        if (phase == 0) {
            VideoProbe.detach();
            state.put(FUTURE, ctx.onServer(server -> {
                ServerPlayer player = RelaySelfTests.player(server);
                ServerLevel level = player.level();
                RelaySelfTests.save(player);
                BlockPos o = indoor(player);
                for (BlockPos pos : List.of(ROOM_CEILING, ROOM_JAMB, U_FIRST, U_SECOND)) {
                    unmount(level, pos);
                }
                floor(level, o.offset(METER_A));
                floor(level, o.offset(METER_B));
                player.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(RelayModule.SIGNAL_REPEATER.item().get()));
                RelaySelfTests.teleport(player, Vec3.atBottomCenterOf(o.offset(METER_A).offset(3, 0, -2)), 30.0F, 45.0F);
            }));
            state.put(PHASE, 1);
            state.put(SINCE, ctx.tick());
            return StepResult.pending();
        }
        StepResult waiting = await(ctx);
        if (waiting != null) {
            return waiting;
        }
        BlockPos o = INDOOR.get();
        RepeaterIndex index = RepeaterIndex.existing(mc.level);
        if (o == null || mc.player == null || index == null) {
            return since(ctx) > TIMEOUT ? StepResult.fail("Messplatz fehlt") : StepResult.pending();
        }
        BlockPos a = o.offset(METER_A);
        BlockPos b = o.offset(METER_B);
        RepeaterSignals signals = RepeaterSignals.INSTANCE;
        signals.bars(a);
        signals.bars(b);
        if (phase == 1) {
            boolean ready = index.contains(a) && index.contains(b) && signals.holding() && mc.player.getMainHandItem().is(RelayModule.SIGNAL_REPEATER.item().get());
            if (!ready || since(ctx) < SETTLE_TICKS) {
                return since(ctx) > TIMEOUT ? StepResult.fail("Repeater oder Signalmesser kommen nicht an (A " + index.contains(a) + ", B " + index.contains(b)
                        + ", in der Hand " + signals.holding() + ")") : StepResult.pending();
            }
            Component text = signals.meterText();
            int barsA = signals.bars(a);
            String log = String.format(Locale.ROOT, "Signalmesser 3,6 m vor Repeater A: %.2f bei %.1f m, „%s“, Balken im Item %s (%d/13); Balken am Block A %d, B %d "
                    + "(Strecke A–B 40 m)", signals.meterQuality(), signals.meterDistance(), text == null ? "-" : text.getString(), RepeaterMeter.active(),
                    RelayModule.SIGNAL_REPEATER.item().get().getBarWidth(mc.player.getMainHandItem()), barsA, signals.bars(b));
            ctx.log(log);
            if (!(signals.meterQuality() > CLEAN) || !RepeaterMeter.active() || !key(text, "propwash.relay.meter.signal")
                    || !RelayModule.SIGNAL_REPEATER.item().get().isBarVisible(mc.player.getMainHandItem()) || barsA < 4 || signals.bars(b) < 4) {
                return StepResult.fail("Signalmesser oder Balken falsch: " + log);
            }
            state.put("meter_near", signals.meterQuality());
            state.put(FUTURE, ctx.onServer(server -> {
                ServerPlayer player = RelaySelfTests.player(server);
                RelaySelfTests.teleport(player, Vec3.atLowerCornerOf(o).add(6.5, 0.0, 8.5), 0.0F, 0.0F);
                unmount(player.level(), METER_B);
            }));
            state.put(PHASE, 2);
            state.put(SINCE, ctx.tick());
            return StepResult.pending();
        }
        if (phase == 2) {
            boolean ready = !index.contains(b) && mc.player.position().distanceTo(Vec3.atLowerCornerOf(o).add(6.5, 0.0, 8.5)) < 0.05;
            if (!ready || since(ctx) < SETTLE_TICKS) {
                return since(ctx) > TIMEOUT ? StepResult.fail("Spieler kommt nicht in den Raum") : StepResult.pending();
            }
            Component text = signals.meterText();
            String log = String.format(Locale.ROOT, "im geschlossenen Betonraum: Signal zum Repeater A %.2f bei %.1f m (vorher %.2f), „%s“; Balken an A ohne Partner %d",
                    signals.meterQuality(), signals.meterDistance(), (double) state.get("meter_near"), text == null ? "-" : text.getString(), signals.bars(a));
            ctx.log(log);
            if (!(signals.meterQuality() < (double) state.get("meter_near") - 0.2) || signals.bars(a) != 0) {
                return StepResult.fail("Wand dämpft den Signalmesser nicht oder A zeigt Balken ohne Partner: " + log);
            }
            state.put(FUTURE, ctx.onServer(server -> WorldRules.set(server, WorldRuleSpecs.SIGNAL_REPEATERS, 0)));
            state.put(PHASE, 3);
            state.put(SINCE, ctx.tick());
            return StepResult.pending();
        }
        if (phase == 3) {
            if (RelayClient.relayLimit() != 0 || since(ctx) < 20) {
                return since(ctx) > TIMEOUT ? StepResult.fail("Welt-Regel 0 kommt nicht an") : StepResult.pending();
            }
            Component text = signals.meterText();
            ctx.log("Welt-Regel Repeater aus: „" + (text == null ? "-" : text.getString()) + "“, Balken im Item " + RepeaterMeter.active());
            if (!key(text, "propwash.relay.meter.off") || RepeaterMeter.active() || signals.bars(a) != 0) {
                return StepResult.fail("Signalmesser bei ausgeschalteten Repeatern falsch: " + (text == null ? "-" : text.getString()));
            }
            state.put(FUTURE, ctx.onServer(server -> {
                WorldRules.set(server, WorldRuleSpecs.SIGNAL_REPEATERS, 4);
                RelaySelfTests.player(server).setItemSlot(EquipmentSlot.MAINHAND, ItemStack.EMPTY);
            }));
            state.put(PHASE, 4);
            state.put(SINCE, ctx.tick());
            return StepResult.pending();
        }
        if (signals.holding() || RepeaterMeter.active()) {
            return since(ctx) > TIMEOUT ? StepResult.fail("Signalmesser bleibt nach dem Weglegen an") : StepResult.pending();
        }
        ctx.log("Repeater weggelegt: Signalmesser und Balken im Item aus");
        return StepResult.pass();
    }

    private static void floor(ServerLevel level, BlockPos pos) {
        RelaySelfTests.put(level, pos, RelayModule.SIGNAL_REPEATER.block().get().defaultBlockState().setValue(SignalRepeaterBlock.FACE, AttachFace.FLOOR)
                .setValue(SignalRepeaterBlock.FACING, Direction.NORTH));
    }

    private static boolean key(@Nullable Component text, String key) {
        return text != null && text.getContents() instanceof TranslatableContents t && t.getKey().equals(key);
    }

    private static StepResult fieldStep(SelfTest.StepContext ctx) {
        StepResult waiting = await(ctx);
        if (waiting != null) {
            return waiting;
        }
        Minecraft mc = Minecraft.getInstance();
        Map<String, Object> state = ctx.state();
        String phase = (String) state.getOrDefault(PHASE, "site");
        BlockPos base = FIELD.get();
        switch (phase) {
            case "site" -> {
                VideoProbe.detach();
                FIELD_REPEATERS.clear();
                cut = Double.NaN;
                state.put(FUTURE, ctx.onServer(RelayFieldSelfTests::startField));
                return next(ctx, "fpv");
            }
            case "fpv" -> {
                PilotSession session = PilotSession.INSTANCE;
                QuadcopterEntity drone = session.drone();
                if (!session.isFpv() || session.sim() == null || drone == null || drone.getId() != RelaySelfTests.DRONE.get() || base == null) {
                    return since(ctx) > TIMEOUT ? StepResult.fail("keine FPV-Sitzung im Freiland") : StepResult.pending();
                }
                session.setTestInput(new StickPositions(0.0F, 0.0F, 0.0F, 0.0F), since(ctx) > 10, StabMode.ANGLE);
                return since(ctx) < 20 ? StepResult.pending() : next(ctx, "climb");
            }
            case "climb" -> {
                hold(base.getY() + FLY_HEIGHT);
                QuadcopterEntity drone = PilotSession.INSTANCE.drone();
                if (drone != null && Math.abs(drone.getY() - base.getY() - FLY_HEIGHT) < 2.0 && since(ctx) > 40) {
                    return next(ctx, "out");
                }
                return since(ctx) > TIMEOUT ? StepResult.fail("Steigflug erreicht die Höhe nicht") : StepResult.pending();
            }
            case "out" -> {
                hold(base.getY() + FLY_HEIGHT);
                double distance = horizontal(mc);
                VideoState video = VideoState.INSTANCE;
                if (video.status() == VideoState.LinkStatus.CONNECTED && distance > 50.0 && video.directQuality() < BREAK) {
                    cut = distance;
                    ctx.log(String.format(Locale.ROOT, "Abbruch geradeaus bei %.0f Blöcken (direktes Bild %.2f, Funk %.2f)", distance, video.directQuality(),
                            OsdHud.sampler().data().linkStrength));
                    state.put(FUTURE, ctx.onServer(RelayFieldSelfTests::placeTwo));
                    return next(ctx, "back");
                }
                if (distance > MAX_MEASURE) {
                    return StepResult.fail("kein Abbruch bis " + MAX_MEASURE + " Blöcke (Bild " + video.directQuality() + ")");
                }
                travel(ctx, along(base, MAX_MEASURE + 50.0));
                return since(ctx) > TIMEOUT * 2 ? StepResult.fail("Flug geradeaus kommt nicht voran") : StepResult.pending();
            }
            case "back" -> {
                hold(base.getY() + FLY_HEIGHT);
                RepeaterIndex index = RepeaterIndex.existing(mc.level);
                boolean known = index != null && FIELD_REPEATERS.size() == 2 && index.contains(FIELD_REPEATERS.get(0)) && index.contains(FIELD_REPEATERS.get(1));
                if (travel(ctx, along(base, 0.45 * cut)) && known) {
                    return next(ctx, "far");
                }
                return since(ctx) > TIMEOUT ? StepResult.fail("Repeater bei 60 % und 100 % kommen beim Client nicht an (bekannt " + known + ")")
                        : StepResult.pending();
            }
            case "far" -> {
                hold(base.getY() + FLY_HEIGHT);
                if (!travel(ctx, along(base, 1.5 * cut))) {
                    return since(ctx) > TIMEOUT ? StepResult.fail("Drohne erreicht 150 % der Abbruchstrecke nicht") : StepResult.pending();
                }
                return next(ctx, "settle");
            }
            case "settle" -> {
                hold(base.getY() + FLY_HEIGHT);
                for (BlockPos pos : FIELD_REPEATERS) {
                    RepeaterSignals.INSTANCE.bars(pos);
                }
                List<String> problems = new ArrayList<>();
                String log = sampleFar(mc, problems);
                state.put("relay_field_log", log);
                if (!settled(ctx, problems.isEmpty(), SETTLE_TICKS)) {
                    return since(ctx) > TIMEOUT ? StepResult.fail(String.join(", ", problems) + ": " + log) : StepResult.pending();
                }
                ctx.log(log);
                if (Screenshots.enabled()) {
                    Screenshots.clearOverlays(mc);
                    state.put("relay_field_shot", Screenshots.capture("signal_repeater_field_rpt"));
                }
                return next(ctx, "shot");
            }
            case "shot" -> {
                hold(base.getY() + FLY_HEIGHT);
                @SuppressWarnings("unchecked")
                CompletableFuture<String> shot = (CompletableFuture<String>) state.get("relay_field_shot");
                if (shot != null && !shot.isDone()) {
                    return StepResult.pending();
                }
                if (shot != null && shot.isCompletedExceptionally()) {
                    return StepResult.fail("Bildschirmfoto: " + shot.exceptionNow());
                }
                if (shot != null) {
                    ctx.log("Bildschirmfoto " + Screenshots.dir().resolve(Screenshots.fileName("signal_repeater_field_rpt") + ".png") + " (" + shot.join() + ")");
                }
                return StepResult.pass();
            }
            default -> {
                return StepResult.fail("unbekannte Phase " + phase);
            }
        }
    }

    private static String sampleFar(Minecraft mc, List<String> problems) {
        VideoState video = VideoState.INSTANCE;
        RelayVideo relay = video.relay();
        OsdData data = OsdHud.sampler().data();
        float quality = VideoProbe.rawQuality();
        int hops = video.relayHops();
        BlockPos near = FIELD_REPEATERS.get(0);
        BlockPos far = FIELD_REPEATERS.get(1);
        RepeaterIndex index = RepeaterIndex.existing(mc.level);
        boolean remembered = index != null && index.remembers(near) && mc.level != null
                && mc.level.getChunkSource().getChunkNow(SectionPos.blockToSectionCoord(near.getX()), SectionPos.blockToSectionCoord(near.getZ())) == null;
        StringBuilder detail = new StringBuilder();
        for (int hop = 0; hop < hops + 1 && hops > 0; hop++) {
            detail.append(String.format(Locale.ROOT, " | Strecke %d: %.0f m, %.1f dB, Signal %.2f", hop + 1, relay.hopDistance(hop), relay.hopLossDb(hop),
                    video.relayHopQuality(hop)));
        }
        int bars = RepeaterSignals.INSTANCE.bars(far);
        Control control = control(mc);
        String log = String.format(Locale.ROOT, "Drohne bei 150 %% der Abbruchstrecke (%.0f Blöcke): Bild über Repeater %.2f, direkt %.2f; %d Repeater im Weg, "
                        + "OSD RPT %d (Element %s); Funk direkt %.2f, über Repeater %.2f (%d Repeater, freie Reichweite %s), Failsafe-Stufe %d; "
                        + "Repeater bei 60 %% gemerkt ohne geladenen Chunk %s; Kandidaten %d, Strecken %d; Balken am Repeater bei 100 %% %d%s",
                horizontal(mc), quality, video.directQuality(), hops, data.relayHops, OsdHud.config().enabled(OsdElement.RELAY) ? "an" : "aus", control.direct(),
                control.chain(), control.relays(), control.free(), data.failsafeStage, remembered, relay.candidates(), relay.edgeCount(), bars, detail);
        if (video.directQuality() >= BREAK) {
            problems.add("direktes Bild bei 150 % noch da");
        }
        if (!(quality > BREAK) || !(quality > video.directQuality() + 0.05)) {
            problems.add("Reichweite wächst nicht");
        }
        if (hops != 2 || data.relayHops != 2) {
            problems.add("RPT zeigt nicht zwei Repeater");
        }
        if (!remembered) {
            problems.add("Repeater bei 60 % nicht gemerkt");
        }
        if (control.chain() < CLEAN || data.failsafeStage > 0 || control.free()) {
            problems.add("Funk nicht voll oder freie Reichweite");
        }
        if (bars < 1) {
            problems.add("Balken am Repeater der Kette aus");
        }
        return log;
    }

    private static StepResult serverStep(SelfTest.StepContext ctx) {
        StepResult waiting = await(ctx);
        if (waiting != null) {
            return waiting;
        }
        Minecraft mc = Minecraft.getInstance();
        Map<String, Object> state = ctx.state();
        String phase = (String) state.getOrDefault(PHASE, "third");
        BlockPos base = FIELD.get();
        if (base == null || Double.isNaN(cut) || FIELD_REPEATERS.size() < 2 || !"cut".equals(phase) && !PilotSession.INSTANCE.isFpv()) {
            return StepResult.fail("Freiland-Flug fehlt (Schritt davor)");
        }
        double third = cut + 550.0;
        double target = cut + RadioLink.RELEASE_RANGE + 60.0;
        switch (phase) {
            case "third" -> {
                hold(base.getY() + FLY_HEIGHT);
                if (!travel(ctx, along(base, third - 40.0))) {
                    return since(ctx) > TIMEOUT ? StepResult.fail("Drohne erreicht den dritten Repeater nicht") : StepResult.pending();
                }
                state.put(FUTURE, ctx.onServer(server -> FIELD_REPEATERS.add(placeFloor(server, base, third))));
                return next(ctx, "known");
            }
            case "known" -> {
                hold(base.getY() + FLY_HEIGHT);
                RepeaterIndex index = RepeaterIndex.existing(mc.level);
                boolean known = index != null && FIELD_REPEATERS.size() == 3 && index.contains(FIELD_REPEATERS.get(2));
                if (!known) {
                    return since(ctx) > TIMEOUT ? StepResult.fail("dritter Repeater kommt nicht an") : StepResult.pending();
                }
                return next(ctx, "go");
            }
            case "go" -> {
                hold(base.getY() + FLY_HEIGHT);
                if (!travel(ctx, along(base, target))) {
                    return since(ctx) > TIMEOUT ? StepResult.fail("Drohne erreicht " + Math.round(target) + " Blöcke nicht") : StepResult.pending();
                }
                return next(ctx, "hold");
            }
            case "hold" -> {
                hold(base.getY() + FLY_HEIGHT);
                OsdData data = OsdHud.sampler().data();
                Control control = control(mc);
                state.merge("relay_min_lq", (double) control.chain(), (x, y) -> Math.min((double) x, (double) y));
                state.merge("relay_max_direct", (double) control.direct(), (x, y) -> Math.max((double) x, (double) y));
                state.merge("relay_min_relays", control.relays(), (x, y) -> Math.min((int) x, (int) y));
                state.put("relay_free", control.free());
                if (data.failsafeStage > 0) {
                    state.merge("relay_failsafe", 1, (x, y) -> (int) x + (int) y);
                }
                if (since(ctx) < 60) {
                    return StepResult.pending();
                }
                AtomicReference<String> piloted = new AtomicReference<>();
                state.put("relay_piloted", piloted);
                state.put(FUTURE, ctx.onServer(server -> piloted.set(DronePiloting.pilotedBy(RelaySelfTests.player(server).getUUID())
                        .map(d -> String.format(Locale.ROOT, "%.0f", d.position().distanceTo(RelaySelfTests.player(server).position()))).orElse("frei"))));
                return next(ctx, "verify");
            }
            case "verify" -> {
                hold(base.getY() + FLY_HEIGHT);
                @SuppressWarnings("unchecked")
                AtomicReference<String> piloted = (AtomicReference<String>) state.get("relay_piloted");
                double minLq = (double) state.getOrDefault("relay_min_lq", 0.0);
                double maxDirect = (double) state.getOrDefault("relay_max_direct", 1.0);
                int controlRelays = (int) state.getOrDefault("relay_min_relays", 0);
                boolean free = (boolean) state.getOrDefault("relay_free", true);
                int failsafe = (int) state.getOrDefault("relay_failsafe", 0);
                String log = String.format(Locale.ROOT, "Drohne %.0f Blöcke weg (Freigabe direkt ab %.0f), drei Repeater bei %.0f, %.0f und %.0f: Funk direkt höchstens %.2f, "
                                + "über Repeater mindestens %.2f mit mindestens %d Repeatern (freie Reichweite %s), Failsafe in %d Ticks, Server: Drohne %s; "
                                + "Bild %.2f über %d Repeater", horizontal(mc), RadioLink.RELEASE_RANGE, 0.6 * cut,
                        cut, third, maxDirect, minLq, controlRelays, free, failsafe, "frei".equals(piloted.get()) ? "freigegeben" : "geflogen, " + piloted.get()
                                + " Blöcke weg", VideoState.INSTANCE.signalQuality(), VideoState.INSTANCE.relayHops());
                ctx.log(log);
                if (!PilotSession.INSTANCE.isFpv() || "frei".equals(piloted.get()) || minLq < CLEAN || maxDirect > 0.0 || free || failsafe > 0
                        || controlRelays < 1) {
                    return StepResult.fail("Kette trägt den Funk nicht über die direkte Freigabe-Reichweite: " + log);
                }
                state.put(FUTURE, ctx.onServer(server -> {
                    ServerLevel level = RelaySelfTests.player(server).level();
                    RelaySelfTests.put(level, FIELD_REPEATERS.get(2), Blocks.AIR.defaultBlockState());
                }));
                return next(ctx, "cut");
            }
            case "cut" -> {
                hold(base.getY() + FLY_HEIGHT);
                if (PilotSession.INSTANCE.isFpv()) {
                    return since(ctx) > TIMEOUT ? StepResult.fail("Server gibt die Drohne ohne Kette nicht frei") : StepResult.pending();
                }
                ctx.log("dritter Repeater abgebaut: Server gibt die Drohne frei (Strecke über der Freigabe-Reichweite ohne Kette)");
                return StepResult.pass();
            }
            default -> {
                return StepResult.fail("unbekannte Phase " + phase);
            }
        }
    }

    private static void startField(MinecraftServer server) {
        ServerPlayer player = RelaySelfTests.player(server);
        ServerLevel level = player.level();
        RelaySelfTests.save(player);
        player.setGameMode(GameType.SURVIVAL);
        DronePiloting.pilotedBy(player.getUUID()).ifPresent(DronePiloting::release);
        if (level.getEntity(RelaySelfTests.DRONE.get()) instanceof QuadcopterEntity old) {
            old.discard();
        }
        BlockPos spawn = level.getRespawnData().globalPos().pos();
        int x = spawn.getX() + FIELD_DX;
        int z = spawn.getZ() + FIELD_DZ;
        level.getChunk(SectionPos.blockToSectionCoord(x), SectionPos.blockToSectionCoord(z));
        BlockPos base = new BlockPos(x, level.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z), z);
        FIELD.set(base);
        WorldRules.set(server, WorldRuleSpecs.SIGNAL_REPEATERS, 4);
        WorldRules.set(server, WorldRuleSpecs.BATTERY_SCALE, 1000);
        RelaySelfTests.teleport(player, Vec3.atBottomCenterOf(base), -90.0F, 0.0F);
        QuadcopterEntity drone = DroneModule.DRONE.get().create(level, EntitySpawnReason.COMMAND);
        if (drone == null) {
            throw new IllegalStateException("Drohne nicht erzeugt");
        }
        drone.snapTo(base.getX() + 2.5, base.getY() + 0.1, base.getZ() + 0.5, -90.0F, 0.0F);
        drone.setAttitude(new Quaternionf());
        drone.setBuild(DroneBuild.CINEWHOOP);
        drone.setBatteryCharge(1.0F);
        if (!level.addFreshEntity(drone)) {
            throw new IllegalStateException("Drohne nicht gespawnt");
        }
        RelaySelfTests.DRONE.set(drone.getId());
        ItemStack goggles = ContentIds.item(ContentIds.GOGGLES_DIGITAL).map(ItemStack::new).orElseThrow();
        goggles.set(CoreComponents.BOUND_DRONE.get(), DroneInteractions.binding(drone));
        player.setItemSlot(EquipmentSlot.HEAD, goggles);
        ItemStack remote = new ItemStack(DroneModule.REMOTE.get());
        remote.set(CoreComponents.BOUND_DRONE.get(), DroneInteractions.binding(drone));
        player.setItemSlot(EquipmentSlot.MAINHAND, remote);
        DronePiloting.StartResult result = DronePiloting.tryStart(player, drone, PilotMode.FPV);
        if (result != DronePiloting.StartResult.STARTED) {
            throw new IllegalStateException("FPV-Start verweigert: " + result);
        }
    }

    private static void placeTwo(MinecraftServer server) {
        BlockPos base = FIELD.get();
        FIELD_REPEATERS.add(placeFloor(server, base, 0.6 * cut));
        FIELD_REPEATERS.add(placeFloor(server, base, cut));
    }

    private static BlockPos placeFloor(MinecraftServer server, BlockPos base, double along) {
        ServerLevel level = RelaySelfTests.player(server).level();
        int x = base.getX() + (int) Math.round(along);
        int z = base.getZ();
        level.getChunk(SectionPos.blockToSectionCoord(x), SectionPos.blockToSectionCoord(z));
        BlockPos pos = new BlockPos(x, level.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z), z);
        floor(level, pos);
        if (!RepeaterIndex.of(level).contains(pos)) {
            throw new IllegalStateException("Repeater im Server-Verzeichnis fehlt: " + pos);
        }
        return pos;
    }

    private static Control control(Minecraft mc) {
        QuadcopterEntity drone = PilotSession.INSTANCE.drone();
        if (drone == null || mc.player == null) {
            return new Control(Double.NaN, 0.0F, 0.0F, 0, false);
        }
        double distance = mc.player.position().distanceTo(drone.position());
        boolean free = RadioLink.freeRange(mc.player);
        double limit = ClientServerRules.INSTANCE.maxPilotDistance();
        double shade = VideoState.INSTANCE.pathLossDb(drone);
        float direct = RadioLink.quality(distance, limit, free, 0.0, Double.isNaN(shade) ? 0.0 : shade);
        float chain = RelayControl.INSTANCE.quality(mc.level, mc.player.position(), drone.position(), direct, limit, free, 0.0, RelayClient.relayLimit(),
                VideoState.INSTANCE.relayFor(drone));
        return new Control(distance, direct, chain, RelayControl.INSTANCE.relays(), free);
    }

    private static Vec3 along(BlockPos base, double distance) {
        return new Vec3(base.getX() + 0.5 + distance, base.getY() + FLY_HEIGHT, base.getZ() + 0.5);
    }

    private static double horizontal(Minecraft mc) {
        QuadcopterEntity drone = PilotSession.INSTANCE.drone();
        if (drone == null || mc.player == null) {
            return Double.NaN;
        }
        return Math.hypot(drone.getX() - mc.player.getX(), drone.getZ() - mc.player.getZ());
    }

    private static boolean travel(SelfTest.StepContext ctx, Vec3 target) {
        QuadcopterEntity drone = PilotSession.INSTANCE.drone();
        if (drone == null) {
            return false;
        }
        Vec3 at = drone.position();
        Vec3 flat = new Vec3(target.x, at.y, target.z);
        double distance = at.distanceTo(flat);
        if (distance < 3.0) {
            return true;
        }
        if (since(ctx) % TRAVEL_TICKS == 0 && !ctx.state().containsKey(FUTURE)) {
            Vec3 step = distance <= TRAVEL_STEP ? flat : at.add(flat.subtract(at).normalize().scale(TRAVEL_STEP));
            int id = RelaySelfTests.DRONE.get();
            ctx.state().put(FUTURE, ctx.onServer(server -> {
                if (RelaySelfTests.player(server).level().getEntity(id) instanceof QuadcopterEntity d) {
                    d.resetTo(step, new Quaternionf().rotationY((float) (Math.PI / 2.0)));
                }
            }));
        }
        return false;
    }

    private static void hold(double targetY) {
        PilotSession session = PilotSession.INSTANCE;
        DroneSim sim = session.sim();
        QuadcopterEntity drone = session.drone();
        Telemetry t = sim == null ? null : sim.telemetry();
        double hover = t == null ? 0.3 : Math.clamp(t.hoverThrottleStick, 0.15, 0.6);
        double vs = t == null ? 0.0 : t.verticalSpeed;
        double h = drone == null ? targetY : drone.getY();
        double wanted = Math.clamp(1.2 * (targetY - h), -3.0, 3.0);
        session.setTestInput(new StickPositions(0.0F, 0.0F, 0.0F, (float) Math.clamp(hover + 0.06 * (wanted - vs), 0.05, 0.9)), true, StabMode.ANGLE);
    }

    private static StepResult cleanupStep(SelfTest.StepContext ctx) {
        Map<String, Object> state = ctx.state();
        if (!state.containsKey(PHASE)) {
            PilotSession.INSTANCE.clearTestInput();
            VideoProbe.detach();
            state.put(PHASE, 1);
            state.put(FUTURE, ctx.onServer(server -> {
                WorldRules.set(server, WorldRuleSpecs.BATTERY_SCALE, WorldRuleSpecs.BATTERY_SCALE.defaultValue());
                RelaySelfTests.tearDown(server);
                RelaySelfTests.player(server).setItemSlot(EquipmentSlot.MAINHAND, ItemStack.EMPTY);
            }));
            return StepResult.pending();
        }
        StepResult waiting = await(ctx);
        if (waiting != null) {
            return waiting;
        }
        FIELD_REPEATERS.clear();
        if (PilotSession.INSTANCE.isActive()) {
            return ctx.tick() > 300 ? StepResult.fail("FPV-Sitzung endet nach dem Test nicht") : StepResult.pending();
        }
        return StepResult.pass();
    }

    private static StepResult next(SelfTest.StepContext ctx, String phase) {
        ctx.state().put(PHASE, phase);
        ctx.state().put(SINCE, ctx.tick());
        ctx.state().remove(READY);
        return StepResult.pending();
    }

    private static int since(SelfTest.StepContext ctx) {
        return ctx.tick() - (int) ctx.state().getOrDefault(SINCE, 0);
    }

    private static boolean settled(SelfTest.StepContext ctx, boolean ready, int ticks) {
        if (!ready) {
            ctx.state().remove(READY);
            return false;
        }
        int at = (int) ctx.state().computeIfAbsent(READY, key -> ctx.tick());
        return ctx.tick() - at >= ticks;
    }

    private static @Nullable StepResult await(SelfTest.StepContext ctx) {
        CompletableFuture<?> future = (CompletableFuture<?>) ctx.state().get(FUTURE);
        if (future == null) {
            return null;
        }
        if (future.isCompletedExceptionally()) {
            ctx.state().remove(FUTURE);
            return fail("Server: " + future.exceptionNow());
        }
        if (!future.isDone()) {
            return StepResult.pending();
        }
        ctx.state().remove(FUTURE);
        return null;
    }

    private static StepResult fail(String message) {
        VideoProbe.detach();
        return StepResult.fail(message);
    }

    private record Probe(String name, Consumer<ServerLevel> setup, Vec3 pilot, Vec3 drone, List<BlockPos> repeaters, boolean clear, int hops) {
    }

    private record Control(double distance, float direct, float chain, int relays, boolean free) {
    }
}
