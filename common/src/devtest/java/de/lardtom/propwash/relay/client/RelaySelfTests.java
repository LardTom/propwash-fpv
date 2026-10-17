package de.lardtom.propwash.relay.client;

import de.lardtom.propwash.build.DroneBuild;
import de.lardtom.propwash.build.VideoSystem;
import de.lardtom.propwash.build.VideoType;
import de.lardtom.propwash.content.ContentIds;
import de.lardtom.propwash.core.CoreComponents;
import de.lardtom.propwash.dev.SelfTest;
import de.lardtom.propwash.dev.SelfTest.StepResult;
import de.lardtom.propwash.drone.DroneInteractions;
import de.lardtom.propwash.drone.DroneModule;
import de.lardtom.propwash.drone.DronePiloting;
import de.lardtom.propwash.drone.PilotMode;
import de.lardtom.propwash.drone.QuadcopterEntity;
import de.lardtom.propwash.drone.RadioLink;
import de.lardtom.propwash.drone.client.PilotSession;
import de.lardtom.propwash.input.StickPositions;
import de.lardtom.propwash.race.CourseData;
import de.lardtom.propwash.race.CourseRegistry;
import de.lardtom.propwash.race.CourseToolItem;
import de.lardtom.propwash.race.GateEntity;
import de.lardtom.propwash.race.GateGeometry;
import de.lardtom.propwash.race.LapAssist;
import de.lardtom.propwash.race.LapTracker;
import de.lardtom.propwash.race.Leaderboard;
import de.lardtom.propwash.race.RacePayloads;
import de.lardtom.propwash.race.RaceModule;
import de.lardtom.propwash.race.RaceServer;
import de.lardtom.propwash.race.client.ClientCourses;
import de.lardtom.propwash.race.client.ClientRaceTracker;
import de.lardtom.propwash.race.client.RaceConfig;
import de.lardtom.propwash.race.client.RaceHud;
import de.lardtom.propwash.relay.RelayModule;
import de.lardtom.propwash.relay.RelayRules;
import de.lardtom.propwash.relay.RepeaterIndex;
import de.lardtom.propwash.relay.SignalRepeaterBlock;
import de.lardtom.propwash.server.WorldRules;
import de.lardtom.propwash.server.rules.WorldRuleSpecs;
import de.lardtom.propwash.video.client.VideoConfig;
import de.lardtom.propwash.video.client.VideoProbe;
import de.lardtom.propwash.video.client.VideoSettings;
import de.lardtom.propwash.video.client.VideoState;
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
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LightBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.AttachFace;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

public final class RelaySelfTests {
    static final int SITE_X = -260;
    static final int SITE_Z = 260;
    static final int SIZE = 16;
    static final int HOLE_FROM = 11;
    static final int HOLE_TO = 15;
    static final int[] SLABS = {0, 5, 10, 15, 20, 25, 30};
    static final BlockPos PILOT = new BlockPos(3, 1, 3);
    static final Vec3 DRONE_FEET = new Vec3(4.5, 27.0, 4.5);
    static final BlockPos LOWER = new BlockPos(13, 1, 13);
    static final BlockPos UPPER = new BlockPos(13, 29, 13);
    static final BlockPos SIDE = new BlockPos(15, 28, 13);
    static final BlockPos CORNER = new BlockPos(30, 0, 0);
    static final BlockPos COURSE = new BlockPos(40, 0, 30);
    private static final String FUTURE = "relay_future";
    private static final String PHASE = "relay_phase";
    private static final String SINCE = "relay_since";
    private static final String ROUNDS = "relay_rounds";
    private static final String PROBE = "relay_probe";
    private static final int SETTLE_TICKS = 12;
    private static final int TIMEOUT = 400;
    private static final int GATE_COUNT = 3;
    private static final int GATE_SPACING = 10;
    private static final long MS = 1_000_000L;
    static final AtomicReference<BlockPos> ORIGIN = new AtomicReference<>();
    static final List<Placed> PLACED = new CopyOnWriteArrayList<>();
    static final AtomicInteger DRONE = new AtomicInteger(-1);
    static final AtomicReference<Saved> SAVED = new AtomicReference<>();
    private static final AtomicReference<List<BlockPos>> MOUNTED = new AtomicReference<>(List.of());
    private static final AtomicReference<String> SERVER_RESULT = new AtomicReference<>();
    private static final AtomicInteger COURSE_ID = new AtomicInteger(-1);
    private static final AtomicLong LAST_FED = new AtomicLong(Long.MIN_VALUE);
    private static final List<Integer> GATE_IDS = new CopyOnWriteArrayList<>();
    private static final Probe[] PROBES = probes();

    private RelaySelfTests() {
    }

    public static void register() {
        SelfTest.clientStep("relay: Repeater an Boden, Wand und Decke setzen, Verzeichnis und Abbau", 900, RelaySelfTests::placeStep);
        SelfTest.clientStep("relay: Betonbau mit sechs Etagen – Bild nur über Repeater, Grenze aus der Welt-Regel", 6000, RelaySelfTests::buildingStep);
        SelfTest.clientStep("relay: Runde mit Kreativ-Brille zählt, Latenz unter dem Sender nennt den Grund", 2400, RelaySelfTests::creativeLapStep);
        SelfTest.clientStep("relay: Aufräumen", 600, RelaySelfTests::cleanupStep);
    }

    private static Probe[] probes() {
        List<Probe> list = new ArrayList<>();
        list.add(new Probe("ohne Repeater", world -> { }, 4, VideoSystem.ANALOG, DRONE_FEET, false, 0));
        list.add(new Probe("nur Repeater unten am Treppenhaus", world -> mount(world, LOWER, AttachFace.FLOOR, Direction.NORTH), 4,
                VideoSystem.ANALOG, DRONE_FEET, false, -1));
        list.add(new Probe("Repeater unten und unter dem Dach", world -> mount(world, UPPER, AttachFace.CEILING, Direction.NORTH), 4,
                VideoSystem.ANALOG, DRONE_FEET, true, 2));
        list.add(new Probe("Welt-Regel: höchstens 1 Repeater", world -> { }, 1, VideoSystem.ANALOG, DRONE_FEET, false, -1));
        list.add(new Probe("Welt-Regel: Repeater aus", world -> { }, 0, VideoSystem.ANALOG, DRONE_FEET, false, 0));
        list.add(new Probe("Welt-Regel wieder 4", world -> { }, 4, VideoSystem.ANALOG, DRONE_FEET, true, 2));
        list.add(new Probe("Drohne in der Nordost-Ecke der obersten Etage", world -> { }, 4, VideoSystem.ANALOG, new Vec3(12.5, 27.0, 3.5), true, 2));
        list.add(new Probe("Drohne in der Südwest-Ecke der obersten Etage", world -> { }, 4, VideoSystem.ANALOG, new Vec3(3.5, 27.0, 12.5), true, 2));
        list.add(new Probe("Wand-Repeater statt Decke", world -> {
            unmount(world, UPPER);
            mount(world, SIDE, AttachFace.WALL, Direction.WEST);
        }, 4, VideoSystem.ANALOG, DRONE_FEET, true, 2));
        list.add(new Probe("Digitalbild über Boden- und Wand-Repeater", world -> { }, 4, VideoSystem.DIGITAL, DRONE_FEET, true, 2));
        return list.toArray(new Probe[0]);
    }

    private static StepResult placeStep(SelfTest.StepContext ctx) {
        Minecraft mc = Minecraft.getInstance();
        Map<String, Object> state = ctx.state();
        int phase = (int) state.getOrDefault(PHASE, 0);
        if (phase == 0) {
            MOUNTED.set(List.of());
            state.put(FUTURE, ctx.onServer(RelaySelfTests::placeByHand));
            state.put(PHASE, 1);
            return StepResult.pending();
        }
        StepResult waiting = waitFuture(ctx);
        if (waiting != null) {
            return waiting;
        }
        List<BlockPos> mounted = MOUNTED.get();
        if (mounted.size() != 3 || mc.level == null) {
            return StepResult.fail("Repeater nicht gesetzt: " + mounted);
        }
        RepeaterIndex index = RepeaterIndex.existing(mc.level);
        if (phase == 1) {
            boolean all = index != null;
            for (BlockPos pos : mounted) {
                all = all && index.contains(pos) && mc.level.getBlockState(pos).getBlock() instanceof SignalRepeaterBlock;
            }
            if (!all) {
                return ctx.tick() > TIMEOUT ? StepResult.fail("Repeater fehlen im Verzeichnis des Clients") : StepResult.pending();
            }
            StringBuilder problems = new StringBuilder();
            double[][] expected = {{0.5, 6.0 / 16.0, 0.5}, {0.5, 0.5, 0.375 + 0.25}, {0.5, 10.0 / 16.0, 0.5}};
            for (int i = 0; i < 3; i++) {
                BlockPos pos = mounted.get(i);
                Vec3 antenna = index.antenna(pos);
                Vec3 want = new Vec3(pos.getX() + expected[i][0], pos.getY() + expected[i][1], pos.getZ() + expected[i][2]);
                if (antenna == null || antenna.distanceTo(want) > 1.0E-6) {
                    problems.append(" Antenne ").append(i).append(' ').append(antenna).append(" statt ").append(want);
                }
                if (!VideoProbe.transparent(mc.level.getBlockState(pos))) {
                    problems.append(" Repeater ").append(i).append(" dämpft das Signal");
                }
            }
            if (!problems.isEmpty()) {
                return StepResult.fail(problems.toString().trim());
            }
            ctx.log("Repeater am Boden, an der Wand und an der Decke im Verzeichnis, Antennen " + index.antenna(mounted.get(0)) + ", "
                    + index.antenna(mounted.get(1)) + ", " + index.antenna(mounted.get(2)));
            state.put(FUTURE, ctx.onServer(server -> breakSupports(server, mounted)));
            state.put(PHASE, 2);
            state.put(SINCE, ctx.tick());
            return StepResult.pending();
        }
        boolean wallGone = index != null && !index.contains(mounted.get(1)) && mc.level.getBlockState(mounted.get(1)).isAir();
        boolean floorGone = index != null && !index.contains(mounted.get(0));
        boolean ceilingKept = index != null && index.contains(mounted.get(2));
        if (!wallGone || !floorGone || !ceilingKept) {
            return ctx.tick() - (int) state.get(SINCE) > TIMEOUT ? StepResult.fail("Verzeichnis nach dem Abbau falsch: Wand weg " + wallGone
                    + ", Boden weg " + floorGone + ", Decke da " + ceilingKept) : StepResult.pending();
        }
        String server = SERVER_RESULT.getAndSet(null);
        return server == null ? StepResult.pass() : StepResult.fail(server);
    }

    private static void placeByHand(MinecraftServer server) {
        ServerPlayer player = player(server);
        ServerLevel level = player.level();
        save(player);
        BlockPos o = origin(player);
        BlockPos c = o.offset(CORNER);
        for (int x = -1; x <= 7; x++) {
            for (int z = -1; z <= 7; z++) {
                put(level, c.offset(x, 0, z), Blocks.STONE_BRICKS.defaultBlockState());
            }
        }
        for (int x = 0; x <= 6; x++) {
            for (int y = 1; y <= 5; y++) {
                put(level, c.offset(x, y, 6), Blocks.STONE_BRICKS.defaultBlockState());
            }
            put(level, c.offset(x, 5, 4), Blocks.STONE_BRICKS.defaultBlockState());
            put(level, c.offset(x, 5, 5), Blocks.STONE_BRICKS.defaultBlockState());
        }
        BlockPos floor = c.offset(1, 0, 3);
        BlockPos wall = c.offset(3, 2, 6);
        BlockPos ceiling = c.offset(5, 5, 4);
        teleport(player, Vec3.atBottomCenterOf(c.offset(3, 1, 1)), 0.0F, 0.0F);
        ItemStack previous = player.getMainHandItem().copy();
        List<BlockPos> mounted = new ArrayList<>();
        StringBuilder problems = new StringBuilder();
        try {
            mounted.add(use(player, floor, Direction.UP, 0.0F, 80.0F));
            mounted.add(use(player, wall, Direction.NORTH, 0.0F, 0.0F));
            mounted.add(use(player, ceiling, Direction.DOWN, 0.0F, -80.0F));
        } finally {
            player.setItemInHand(InteractionHand.MAIN_HAND, previous);
        }
        AttachFace[] faces = {AttachFace.FLOOR, AttachFace.WALL, AttachFace.CEILING};
        for (int i = 0; i < 3; i++) {
            BlockState placed = level.getBlockState(mounted.get(i));
            if (!(placed.getBlock() instanceof SignalRepeaterBlock) || placed.getValue(SignalRepeaterBlock.FACE) != faces[i]) {
                problems.append(" ").append(faces[i]).append(": ").append(placed);
            } else if (faces[i] == AttachFace.WALL && placed.getValue(SignalRepeaterBlock.FACING) != Direction.NORTH) {
                problems.append(" Wand zeigt nach ").append(placed.getValue(SignalRepeaterBlock.FACING));
            }
            PLACED.add(new Placed(mounted.get(i), Blocks.AIR.defaultBlockState()));
            if (!RepeaterIndex.of(level).contains(mounted.get(i))) {
                problems.append(" Server-Verzeichnis ohne ").append(mounted.get(i));
            }
        }
        if (!problems.isEmpty()) {
            throw new IllegalStateException("Platzieren:" + problems);
        }
        MOUNTED.set(List.copyOf(mounted));
    }

    private static BlockPos use(ServerPlayer player, BlockPos support, Direction side, float yaw, float pitch) {
        player.setYRot(yaw);
        player.setXRot(pitch);
        player.setShiftKeyDown(false);
        ItemStack stack = new ItemStack(RelayModule.SIGNAL_REPEATER.item().get());
        player.setItemInHand(InteractionHand.MAIN_HAND, stack);
        Vec3 hit = Vec3.atCenterOf(support).add(side.getStepX() * 0.5, side.getStepY() * 0.5, side.getStepZ() * 0.5);
        InteractionResult result = stack.getItem().useOn(new UseOnContext(player, InteractionHand.MAIN_HAND,
                new BlockHitResult(hit, side, support, false)));
        if (!result.consumesAction()) {
            throw new IllegalStateException("Repeater an " + side + " nicht gesetzt: " + result);
        }
        return support.relative(side);
    }

    private static void breakSupports(MinecraftServer server, List<BlockPos> mounted) {
        ServerLevel level = player(server).level();
        BlockPos wallSupport = mounted.get(1).relative(Direction.SOUTH);
        level.setBlockAndUpdate(wallSupport, Blocks.AIR.defaultBlockState());
        level.setBlockAndUpdate(mounted.get(0), Blocks.AIR.defaultBlockState());
        RepeaterIndex index = RepeaterIndex.of(level);
        if (!level.getBlockState(mounted.get(1)).isAir()) {
            SERVER_RESULT.set("Wand-Repeater fällt ohne Halt nicht ab: " + level.getBlockState(mounted.get(1)));
        } else if (index.contains(mounted.get(0)) || index.contains(mounted.get(1)) || !index.contains(mounted.get(2))) {
            SERVER_RESULT.set("Server-Verzeichnis nach dem Abbau falsch");
        }
    }

    private static StepResult buildingStep(SelfTest.StepContext ctx) {
        Minecraft mc = Minecraft.getInstance();
        Map<String, Object> state = ctx.state();
        int phase = (int) state.getOrDefault(PHASE, 0);
        if (phase == 0) {
            state.put(FUTURE, ctx.onServer(server -> {
                ServerPlayer player = player(server);
                save(player);
                BlockPos o = origin(player);
                build(player.level(), o);
                WorldRules.set(server, WorldRuleSpecs.SIGNAL_REPEATERS, 4);
                teleport(player, Vec3.atBottomCenterOf(o.offset(PILOT)), -45.0F, -40.0F);
                spawnDrone(player.level(), Vec3.atLowerCornerOf(o).add(DRONE_FEET));
            }));
            state.put(PHASE, 1);
            state.put(SINCE, ctx.tick());
            return StepResult.pending();
        }
        StepResult waiting = waitFuture(ctx);
        if (waiting != null) {
            return waiting;
        }
        BlockPos o = ORIGIN.get();
        if (o == null || mc.level == null || mc.player == null) {
            return failVideo("Testbau fehlt");
        }
        QuadcopterEntity drone = mc.level.getEntity(DRONE.get()) instanceof QuadcopterEntity d ? d : null;
        if (phase == 1) {
            boolean ready = drone != null && mc.player.position().distanceTo(Vec3.atBottomCenterOf(o.offset(PILOT))) < 0.05
                    && mc.level.getBlockState(o.offset(0, 15, 0)).is(Blocks.CONCRETE.pick(DyeColor.WHITE));
            if (!ready) {
                return ctx.tick() - (int) state.get(SINCE) > TIMEOUT ? failVideo("Pilot oder Drohne nicht im Betonbau") : StepResult.pending();
            }
            state.put(PHASE, 2);
            state.put(PROBE, 0);
            startProbe(ctx, 0);
            return StepResult.pending();
        }
        int index = (int) state.get(PROBE);
        if (index >= PROBES.length) {
            RelayVideo relay = VideoState.INSTANCE.relay();
            double average = relay.scanNanosAverage() / 1.0E6;
            double max = relay.scanNanosMax() / 1.0E6;
            float control = RelayControl.INSTANCE.quality(mc.level, mc.player.position(), drone == null ? mc.player.position() : drone.position(),
                    RadioLink.quality(10.0, Double.POSITIVE_INFINITY, false), Double.POSITIVE_INFINITY, false, 0.0, RelayRules.MAX_RELAYS);
            ctx.log(String.format(Locale.ROOT, "Repeater-Strecken je Tick: Ø %.3f ms, höchstens %.3f ms; Funk durch die Decken direkt %.2f (unverändert)",
                    average, max, control));
            VideoProbe.detach();
            if (control < 1.0F) {
                return failVideo("Funk im Betonbau verändert: " + control);
            }
            return StepResult.check(average < 2.0, "Repeater-Strecken zu teuer: Ø " + average + " ms");
        }
        Probe probe = PROBES[index];
        if (drone == null) {
            return failVideo(probe.name + ": Drohne fehlt auf dem Client");
        }
        Vec3 antenna = drone.position().add(0.0, drone.getBbHeight() * 0.5, 0.0);
        Vec3 want = Vec3.atLowerCornerOf(o).add(probe.drone).add(0.0, drone.getBbHeight() * 0.5, 0.0);
        VideoState video = VideoState.INSTANCE;
        boolean placed = antenna.distanceTo(want) < 0.05 && video.status() == VideoState.LinkStatus.CONNECTED
                && clientRepeaters(mc, o) == expectedRepeaters(index) && RelayClient.relayLimit() == probe.limit;
        if (!placed) {
            state.remove(ROUNDS);
            return ctx.tick() - (int) state.get(SINCE) > TIMEOUT ? failVideo(probe.name + ": Aufbau kommt nicht an (Drohne "
                    + antenna + " statt " + want + ", Verbindung " + video.status() + ", Repeater " + clientRepeaters(mc, o) + ", Regel "
                    + RelayClient.relayLimit() + ")") : StepResult.pending();
        }
        if (!state.containsKey(ROUNDS)) {
            state.put(ROUNDS, VideoProbe.scanRounds());
            state.put(SINCE, ctx.tick());
            return StepResult.pending();
        }
        RelayVideo relay = video.relay();
        if (ctx.tick() - (int) state.get(SINCE) < SETTLE_TICKS || VideoProbe.scanRounds() - (int) state.get(ROUNDS) < 2 || !relay.settled()) {
            return ctx.tick() - (int) state.get(SINCE) > TIMEOUT ? failVideo(probe.name + ": Abschattung wird nicht fertig")
                    : StepResult.pending();
        }
        float quality = VideoProbe.rawQuality();
        int hops = video.relayHops();
        StringBuilder detail = new StringBuilder();
        for (int hop = 0; hop < hops + 1 && hops > 0; hop++) {
            detail.append(String.format(Locale.ROOT, " | Strecke %d: %.1f m, %.1f dB, Signal %.2f", hop + 1, relay.hopDistance(hop), relay.hopLossDb(hop),
                    video.relayHopQuality(hop)));
        }
        ctx.log(String.format(Locale.ROOT, "%s: Bild %.2f (direkt %.2f, %.1f dB), %d Repeater im Weg, %d Kandidaten, %d Strecken%s", probe.name,
                quality, video.directQuality(), VideoProbe.scanLossDb(), hops, relay.candidates(), relay.edgeCount(), detail));
        if (probe.clear) {
            if (quality < 0.9F || hops != probe.hops) {
                return failVideo(String.format(Locale.ROOT, "%s: Bild %.2f über %d Repeater statt klar über %d", probe.name, quality, hops, probe.hops));
            }
            for (int hop = 0; hop <= hops; hop++) {
                if (!(relay.hopLossDb(hop) < 6.0)) {
                    return failVideo(String.format(Locale.ROOT, "%s: Strecke %d dämpft %.1f dB, obwohl sie frei ist", probe.name, hop + 1,
                            relay.hopLossDb(hop)));
                }
            }
            if (video.directQuality() > 0.05F) {
                return failVideo(probe.name + ": direktes Bild durch fünf Betondecken nicht verloren: " + video.directQuality());
            }
        } else if (quality > 0.05F || probe.hops >= 0 && hops != probe.hops || hops > Math.max(0, probe.limit)) {
            return failVideo(String.format(Locale.ROOT, "%s: Bild %.2f über %d Repeater, erwartet verloren", probe.name, quality, hops));
        }
        state.put(PROBE, index + 1);
        if (index + 1 < PROBES.length) {
            startProbe(ctx, index + 1);
        }
        return StepResult.pending();
    }

    private static StepResult failVideo(String message) {
        VideoProbe.detach();
        return StepResult.fail(message);
    }

    private static void startProbe(SelfTest.StepContext ctx, int index) {
        Probe probe = PROBES[index];
        Map<String, Object> state = ctx.state();
        state.remove(ROUNDS);
        state.put(SINCE, ctx.tick());
        Minecraft mc = Minecraft.getInstance();
        QuadcopterEntity client = mc.level != null && mc.level.getEntity(DRONE.get()) instanceof QuadcopterEntity d ? d : null;
        VideoSystem transmitter = probe.system == VideoSystem.DIGITAL ? VideoSystem.DIGITAL : null;
        if (index == 0) {
            VideoProbe.attach(client, probe.system, transmitter);
        } else {
            VideoProbe.systems(probe.system, transmitter);
        }
        state.put(FUTURE, ctx.onServer(server -> {
            ServerLevel level = player(server).level();
            probe.setup.accept(level);
            WorldRules.set(server, WorldRuleSpecs.SIGNAL_REPEATERS, probe.limit);
            if (level.getEntity(DRONE.get()) instanceof QuadcopterEntity drone) {
                moveDrone(drone, Vec3.atLowerCornerOf(ORIGIN.get()).add(probe.drone));
            }
        }));
    }

    private static int expectedRepeaters(int probe) {
        return probe == 0 ? 0 : probe == 1 ? 1 : 2;
    }

    private static int clientRepeaters(Minecraft mc, BlockPos o) {
        RepeaterIndex index = RepeaterIndex.existing(mc.level);
        if (index == null) {
            return 0;
        }
        int count = 0;
        for (BlockPos pos : List.of(LOWER, UPPER, SIDE)) {
            if (index.contains(o.offset(pos))) {
                count++;
            }
        }
        return count;
    }

    private static StepResult creativeLapStep(SelfTest.StepContext ctx) {
        Minecraft mc = Minecraft.getInstance();
        Map<String, Object> state = ctx.state();
        int phase = (int) state.getOrDefault(PHASE, 0);
        if (phase == 0) {
            SERVER_RESULT.set(null);
            VideoProbe.detach();
            state.put(FUTURE, ctx.onServer(RelaySelfTests::setUpCourse));
            state.put(PHASE, 1);
            state.put(SINCE, ctx.tick());
            return StepResult.pending();
        }
        StepResult waiting = waitFuture(ctx);
        if (waiting != null) {
            return waiting;
        }
        ClientRaceTracker tracker = ClientRaceTracker.INSTANCE;
        VideoConfig config = VideoSettings.get();
        if (phase == 1) {
            PilotSession session = PilotSession.INSTANCE;
            session.setTestInput(StickPositions.IDLE, false, null);
            CourseData course = ClientCourses.get(COURSE_ID.get());
            boolean ready = session.isFpv() && VideoState.INSTANCE.creative() && course != null && course.gates().size() == GATE_COUNT;
            if (!ready) {
                return ctx.tick() - (int) state.get(SINCE) > TIMEOUT ? StepResult.fail("FPV mit Kreativ-Brille am Kurs startet nicht (FPV "
                        + session.isFpv() + ", Kreativ " + VideoState.INSTANCE.creative() + ", Kurs " + course + ")") : StepResult.pending();
            }
            config.creativeLatencyFromModule = true;
            state.put(PHASE, 2);
            state.put(SINCE, ctx.tick());
            return StepResult.pending();
        }
        if (ctx.tick() - (int) state.get(SINCE) < 5) {
            return StepResult.pending();
        }
        CourseData course = ClientCourses.get(COURSE_ID.get());
        if (course == null) {
            return StepResult.fail("Kurs fehlt auf dem Client");
        }
        if (phase == 2) {
            if (!RadioLink.freeRange(mc.player)) {
                return StepResult.fail("Kreativ-Brille ohne freie Reichweite");
            }
            if (tracker.assistNow() != LapAssist.NONE) {
                return StepResult.fail("Kreativ-Brille zählt als Flughilfe: " + LapAssist.list(tracker.assistNow()));
            }
            RaceHud.clear();
            String flown = flyRace(mc, tracker, course);
            if (flown != null) {
                return StepResult.fail(flown);
            }
            if (tracker.lastLapAssist() != LapAssist.NONE || hasMessage("propwash.race.msg.assisted_because")) {
                return StepResult.fail("Runde mit Kreativ-Brille nicht gewertet: " + LapAssist.list(tracker.lastLapAssist()));
            }
            ctx.log("Runde mit Kreativ-Brille (freie Reichweite, Latenz vom Videomodul) gewertet");
            config.creativeLatencyFromModule = false;
            config.creativeLatencyMs = 0;
            state.put(PHASE, 3);
            state.put(SINCE, ctx.tick());
            return StepResult.pending();
        }
        if (phase == 3) {
            int now = tracker.assistNow();
            if (!LapAssist.GOGGLES_LATENCY.in(now)) {
                config.creativeLatencyFromModule = true;
                return StepResult.fail("0 ms Latenz der Kreativ-Brille nicht erkannt: " + LapAssist.list(now));
            }
            RaceHud.clear();
            String flown = flyRace(mc, tracker, course);
            config.creativeLatencyFromModule = true;
            if (flown != null) {
                return StepResult.fail(flown);
            }
            if (!LapAssist.GOGGLES_LATENCY.in(tracker.lastLapAssist())) {
                return StepResult.fail("Runde mit 0 ms Latenz gewertet");
            }
            Component reason = null;
            for (Component message : RaceHud.recentMessages()) {
                if (message.getContents() instanceof TranslatableContents t && t.getKey().equals("propwash.race.msg.assisted_because")) {
                    reason = message;
                }
            }
            if (reason == null || !reason.getString().contains(String.valueOf(VideoType.ANALOG_RACE.latencyMs))) {
                return StepResult.fail("Rennanzeige nennt den Grund nicht: " + RaceHud.recentMessages());
            }
            ctx.log("Nicht gewertet mit Grund: „" + reason.getString() + "“");
            state.put(FUTURE, ctx.onServer(server -> SERVER_RESULT.set(checkLeaderboard(server))));
            state.put(PHASE, 4);
            return StepResult.pending();
        }
        String result = SERVER_RESULT.getAndSet(null);
        return result == null ? StepResult.pass() : StepResult.fail(result);
    }

    private static long clockOf(List<LapTracker.Event> events) {
        long last = Long.MIN_VALUE;
        for (LapTracker.Event event : events) {
            last = Math.max(last, event.nanos());
        }
        return last;
    }

    private static boolean hasMessage(String key) {
        for (Component message : RaceHud.recentMessages()) {
            if (message.getContents() instanceof TranslatableContents t && t.getKey().equals(key)) {
                return true;
            }
        }
        return false;
    }

    private static @Nullable String flyRace(Minecraft mc, ClientRaceTracker tracker, CourseData course) {
        RaceConfig config = RaceConfig.CONFIG.get();
        boolean report = config.reportResults;
        config.reportResults = false;
        List<LapTracker.Event> events = new ArrayList<>();
        tracker.setEventListener(events::add);
        try {
            List<GateGeometry> gates = ClientCourses.geometry(course);
            GateGeometry first = gates.getFirst();
            GateGeometry last = gates.getLast();
            double x = first.x();
            double y = first.centerY();
            double zStart = first.z() - 4.0;
            double zEnd = last.z() + 4.0;
            Vec3 start = new Vec3(x, y, zStart);
            tracker.tracker().setLapTarget(2);
            tracker.ghosts().setPersist(false);
            tracker.selectCourseAt(mc, start);
            if (tracker.courseId() != course.id()) {
                return "Kurs nicht gewählt: " + tracker.courseId();
            }
            long now = Math.max(tracker.now(), LAST_FED.get()) + LapTracker.RESTART_COOLDOWN_NANOS + LapTracker.SECOND;
            Vec3[] position = {start};
            long[] clock = {now};
            tracker.feed(now, start);
            for (int loop = 0; loop < 3; loop++) {
                fly(tracker, position, clock, new Vec3(x, y, zEnd), 12.0);
                fly(tracker, position, clock, new Vec3(x + 8.0, y, zEnd), 12.0);
                fly(tracker, position, clock, new Vec3(x + 8.0, y, zStart), 12.0);
                fly(tracker, position, clock, new Vec3(x, y, zStart), 12.0);
            }
        } finally {
            tracker.setEventListener(null);
            config.reportResults = report;
        }
        LAST_FED.set(clockOf(events));
        long laps = events.stream().filter(e -> e.type() == LapTracker.EventType.LAP).count();
        long finishes = events.stream().filter(e -> e.type() == LapTracker.EventType.FINISH).count();
        return laps == 2 && finishes == 1 ? null : "Rennen nicht vollständig: " + laps + " Runden, " + finishes + " Ziel";
    }

    private static void fly(ClientRaceTracker tracker, Vec3[] position, long[] clock, Vec3 target, double speed) {
        Vec3 from = position[0];
        double length = from.distanceTo(target);
        int steps = Math.max(1, (int) Math.ceil(length / 0.25));
        long stepNanos = Math.round(length / speed / steps * 1.0E9);
        for (int i = 1; i <= steps; i++) {
            clock[0] += stepNanos;
            position[0] = from.lerp(target, i / (double) steps);
            tracker.feed(clock[0], position[0]);
        }
    }

    private static void setUpCourse(MinecraftServer server) {
        ServerPlayer player = player(server);
        ServerLevel level = player.level();
        save(player);
        BlockPos o = origin(player);
        CourseRegistry registry = CourseRegistry.get();
        if (registry == null) {
            throw new IllegalStateException("CourseRegistry läuft nicht");
        }
        BlockPos base = o.offset(COURSE);
        int x = base.getX();
        int z0 = base.getZ() + 6;
        ItemStack previous = player.getMainHandItem().copy();
        player.setYRot(0.0F);
        player.setXRot(0.0F);
        player.setShiftKeyDown(false);
        ItemStack gates = new ItemStack(RaceModule.GATE_MEDIUM.get(), GATE_COUNT);
        player.setItemInHand(InteractionHand.MAIN_HAND, gates);
        List<GateEntity> placed = new ArrayList<>();
        teleport(player, new Vec3(x + 3.5, level.getHeight(Heightmap.Types.MOTION_BLOCKING, x + 3, z0 - 4), z0 - 4.5), 0.0F, 0.0F);
        try {
            for (int i = 0; i < GATE_COUNT; i++) {
                int z = z0 + i * GATE_SPACING;
                int top = level.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z);
                BlockPos ground = new BlockPos(x, top - 1, z);
                BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(ground).add(0.0, 0.5, 0.0), Direction.UP, ground, false);
                InteractionResult result = gates.getItem().useOn(new UseOnContext(player, InteractionHand.MAIN_HAND, hit));
                if (!result.consumesAction()) {
                    throw new IllegalStateException("Gate " + i + " nicht platziert: " + result);
                }
                List<GateEntity> found = level.getEntitiesOfClass(GateEntity.class, new AABB(ground.above()).inflate(0.6));
                if (found.size() != 1) {
                    throw new IllegalStateException("Gate " + i + " nicht gefunden: " + found.size());
                }
                placed.add(found.getFirst());
            }
        } finally {
            player.setItemInHand(InteractionHand.MAIN_HAND, previous);
        }
        ItemStack tool = new ItemStack(RaceModule.COURSE_TOOL.get());
        for (GateEntity gate : placed) {
            CourseToolItem.useOnGate(tool, player, gate);
        }
        CourseToolItem.useOnGate(tool, player, placed.get(1));
        int course = CourseToolItem.courseOf(tool);
        CourseRegistry.Course data = registry.course(course);
        if (data == null || data.gates.size() != GATE_COUNT) {
            throw new IllegalStateException("Kurs " + course + " hat nicht " + GATE_COUNT + " Gates");
        }
        GATE_IDS.clear();
        for (GateEntity gate : placed) {
            GATE_IDS.add(gate.getId());
        }
        COURSE_ID.set(course);
        DronePiloting.pilotedBy(player.getUUID()).ifPresent(DronePiloting::release);
        if (level.getEntity(DRONE.get()) instanceof QuadcopterEntity old) {
            old.discard();
        }
        QuadcopterEntity drone = DroneModule.DRONE.get().create(level, EntitySpawnReason.COMMAND);
        if (drone == null) {
            throw new IllegalStateException("Drohne nicht erzeugt");
        }
        Vec3 at = new Vec3(x + 3.5, level.getHeight(Heightmap.Types.MOTION_BLOCKING, x + 3, z0 - 2) + 0.02, z0 - 2.5);
        drone.snapTo(at.x, at.y, at.z, 0.0F, 0.0F);
        drone.setBuild(DroneBuild.RACER);
        drone.setBatteryCharge(1.0F);
        if (!level.addFreshEntity(drone)) {
            throw new IllegalStateException("Drohne nicht in die Welt gesetzt");
        }
        drone.resetTo(at, new Quaternionf());
        DRONE.set(drone.getId());
        ItemStack goggles = ContentIds.item(ContentIds.GOGGLES_CREATIVE).map(ItemStack::new).orElse(ItemStack.EMPTY);
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

    private static @Nullable String checkLeaderboard(MinecraftServer server) {
        ServerPlayer player = player(server);
        CourseRegistry registry = CourseRegistry.get();
        CourseRegistry.Course course = registry == null ? null : registry.course(COURSE_ID.get());
        if (course == null) {
            return "Kurs fehlt auf dem Server";
        }
        if (RaceServer.pilotedDrone(player) == null) {
            return "keine geflogene Drohne auf dem Server";
        }
        List<GateGeometry> gates = course.toData().geometry();
        long lap = Math.max(RaceServer.minimumLapNanos(course, player) * 3L, 6_000L * MS);
        long t = System.nanoTime() + 120_000L * MS;
        RaceServer.Verdict verdict = RaceServer.handle(new RacePayloads.RaceReportC2S(RacePayloads.ReportKind.START, course.id, 0, 0L, 0L), player, t);
        if (verdict != RaceServer.Verdict.ACCEPTED) {
            return "Start mit Kreativ-Brille abgelehnt: " + verdict;
        }
        long crossed = flyServerLap(player, gates, t + 100L * MS, lap);
        verdict = RaceServer.handle(new RacePayloads.RaceReportC2S(RacePayloads.ReportKind.LAP, course.id, 1, lap, 0L, 0, 0L), player,
                crossed + 100L * MS);
        if (verdict != RaceServer.Verdict.ACCEPTED) {
            return "Runde mit Kreativ-Brille abgelehnt: " + verdict;
        }
        List<Leaderboard.Entry> top = registry.board(course).top(null, 10);
        if (top.isEmpty() || top.getFirst().nanos() != lap || !top.getFirst().player().equals(player.getUUID())) {
            return "Runde mit Kreativ-Brille fehlt in der Bestenliste: " + top;
        }
        return null;
    }

    private static long flyServerLap(ServerPlayer player, List<GateGeometry> gates, long start, long lap) {
        passGate(player, gates.getFirst(), start);
        for (int i = 1; i < gates.size(); i++) {
            passGate(player, gates.get(i), start + lap * i / gates.size());
        }
        passGate(player, gates.getFirst(), start + lap);
        return start + lap;
    }

    private static void passGate(ServerPlayer player, GateGeometry gate, long nanos) {
        Vec3 center = new Vec3(gate.x(), gate.centerY(), gate.z());
        Vec3 normal = new Vec3(gate.normalX(), 0.0, gate.normalZ());
        RaceServer.onDroneMoved(player, center.subtract(normal), center.add(normal), nanos);
    }

    private static StepResult cleanupStep(SelfTest.StepContext ctx) {
        Map<String, Object> state = ctx.state();
        if (!state.containsKey(FUTURE)) {
            PilotSession.INSTANCE.clearTestInput();
            VideoProbe.detach();
            VideoConfig config = VideoSettings.get();
            config.creativeLatencyFromModule = true;
            config.creativeLatencyMs = 0;
            ClientRaceTracker.INSTANCE.ghosts().setPersist(true);
            RaceHud.clear();
            state.put(FUTURE, ctx.onServer(RelaySelfTests::tearDown));
            return StepResult.pending();
        }
        StepResult waiting = waitFuture(ctx);
        if (waiting != null) {
            return waiting;
        }
        if (PilotSession.INSTANCE.isActive()) {
            return ctx.tick() > 300 ? StepResult.fail("FPV-Sitzung endet nach dem Test nicht") : StepResult.pending();
        }
        return StepResult.pass();
    }

    static void tearDown(MinecraftServer server) {
        ServerPlayer player = player(server);
        ServerLevel level = player.level();
        DronePiloting.pilotedBy(player.getUUID()).ifPresent(DronePiloting::release);
        for (ServerLevel any : server.getAllLevels()) {
            Entity drone = any.getEntity(DRONE.get());
            if (drone != null) {
                drone.discard();
            }
            for (int id : GATE_IDS) {
                Entity gate = any.getEntity(id);
                if (gate != null) {
                    gate.discard();
                }
            }
        }
        DRONE.set(-1);
        GATE_IDS.clear();
        BlockPos o = ORIGIN.get();
        if (o != null) {
            AABB site = new AABB(o.getX() - 8, o.getY() - 8, o.getZ() - 8, o.getX() + 72, o.getY() + 24, o.getZ() + 80);
            for (ItemEntity item : level.getEntitiesOfClass(ItemEntity.class, site)) {
                item.discard();
            }
        }
        CourseRegistry registry = CourseRegistry.get();
        if (registry != null && COURSE_ID.get() >= 0) {
            registry.deleteCourse(COURSE_ID.getAndSet(-1));
        }
        for (int i = PLACED.size() - 1; i >= 0; i--) {
            Placed placed = PLACED.get(i);
            level.setBlock(placed.pos(), placed.state(), Block.UPDATE_CLIENTS);
        }
        PLACED.clear();
        WorldRules.set(server, WorldRuleSpecs.SIGNAL_REPEATERS, WorldRuleSpecs.SIGNAL_REPEATERS.defaultValue());
        Saved saved = SAVED.getAndSet(null);
        if (saved != null) {
            player.setItemSlot(EquipmentSlot.HEAD, saved.head());
            player.setItemSlot(EquipmentSlot.MAINHAND, saved.hand());
            player.setGameMode(saved.mode());
            player.teleportTo(level, saved.pos().x, saved.pos().y, saved.pos().z, Set.of(), saved.yaw(), saved.pitch(), true);
        }
    }

    static @Nullable StepResult waitFuture(SelfTest.StepContext ctx) {
        CompletableFuture<?> future = (CompletableFuture<?>) ctx.state().get(FUTURE);
        if (future == null) {
            return null;
        }
        if (future.isCompletedExceptionally()) {
            ctx.state().remove(FUTURE);
            return StepResult.fail("Server: " + future.exceptionNow());
        }
        return future.isDone() ? null : StepResult.pending();
    }

    static ServerPlayer player(MinecraftServer server) {
        List<ServerPlayer> players = server.getPlayerList().getPlayers();
        if (players.isEmpty()) {
            throw new IllegalStateException("kein Spieler");
        }
        return players.getFirst();
    }

    static void save(ServerPlayer player) {
        SAVED.compareAndSet(null, new Saved(player.position(), player.getYRot(), player.getXRot(), player.getItemBySlot(EquipmentSlot.HEAD).copy(),
                player.getMainHandItem().copy(), player.gameMode()));
        player.setGameMode(GameType.CREATIVE);
    }

    static BlockPos origin(ServerPlayer player) {
        BlockPos o = ORIGIN.get();
        if (o != null) {
            return o;
        }
        ServerLevel level = player.level();
        BlockPos spawn = level.getRespawnData().globalPos().pos();
        int x = spawn.getX() + SITE_X;
        int z = spawn.getZ() + SITE_Z;
        for (int cx = (x - 16) >> 4; cx <= (x + 64) >> 4; cx++) {
            for (int cz = (z - 16) >> 4; cz <= (z + 80) >> 4; cz++) {
                level.getChunk(cx, cz);
            }
        }
        o = new BlockPos(x, level.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z), z);
        ORIGIN.set(o);
        return o;
    }

    static void teleport(ServerPlayer player, Vec3 feet, float yaw, float pitch) {
        player.getAbilities().flying = false;
        player.onUpdateAbilities();
        player.teleportTo(player.level(), feet.x, feet.y, feet.z, Set.of(), yaw, pitch, true);
    }

    static void build(ServerLevel level, BlockPos o) {
        BlockState concrete = Blocks.CONCRETE.pick(DyeColor.WHITE).defaultBlockState();
        BlockState light = Blocks.LIGHT.defaultBlockState().setValue(LightBlock.LEVEL, 15);
        for (int x = 0; x <= SIZE; x++) {
            for (int z = 0; z <= SIZE; z++) {
                for (int y = 0; y <= SLABS[SLABS.length - 1]; y++) {
                    boolean wall = x == 0 || x == SIZE || z == 0 || z == SIZE;
                    boolean slab = y % 5 == 0;
                    boolean hole = y > 0 && y < SLABS[SLABS.length - 1] && x >= HOLE_FROM && x <= HOLE_TO && z >= HOLE_FROM && z <= HOLE_TO;
                    if ((wall || slab) && !hole) {
                        put(level, o.offset(x, y, z), concrete);
                    }
                }
            }
        }
        for (int floor = 0; floor < SLABS.length - 1; floor++) {
            for (int[] spot : new int[][]{{4, 4}, {12, 4}, {4, 12}, {8, 8}}) {
                put(level, o.offset(spot[0], floor * 5 + 4, spot[1]), light);
            }
        }
    }

    static void mount(ServerLevel level, BlockPos relative, AttachFace face, Direction facing) {
        BlockState state = RelayModule.SIGNAL_REPEATER.block().get().defaultBlockState().setValue(SignalRepeaterBlock.FACE, face)
                .setValue(SignalRepeaterBlock.FACING, facing);
        BlockPos pos = ORIGIN.get().offset(relative);
        put(level, pos, state);
        if (!level.getBlockState(pos).is(state.getBlock())) {
            throw new IllegalStateException("Repeater nicht gesetzt: " + pos);
        }
    }

    static void unmount(ServerLevel level, BlockPos relative) {
        level.setBlock(ORIGIN.get().offset(relative), Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
    }

    static void put(ServerLevel level, BlockPos pos, BlockState state) {
        BlockState before = level.getBlockState(pos);
        if (before.equals(state)) {
            return;
        }
        PLACED.add(new Placed(pos.immutable(), before));
        level.setBlock(pos, state, Block.UPDATE_ALL);
    }

    static QuadcopterEntity spawnDrone(ServerLevel level, Vec3 feet) {
        if (level.getEntity(DRONE.get()) instanceof QuadcopterEntity existing) {
            moveDrone(existing, feet);
            return existing;
        }
        QuadcopterEntity drone = DroneModule.DRONE.get().create(level, EntitySpawnReason.COMMAND);
        if (drone == null) {
            throw new IllegalStateException("Drohne nicht erzeugt");
        }
        drone.setBuild(DroneBuild.RACER);
        drone.setNoGravity(true);
        drone.snapTo(feet.x, feet.y, feet.z, 0.0F, 0.0F);
        if (!level.addFreshEntity(drone)) {
            throw new IllegalStateException("Drohne nicht in die Welt gesetzt");
        }
        DRONE.set(drone.getId());
        moveDrone(drone, feet);
        return drone;
    }

    static void moveDrone(QuadcopterEntity drone, Vec3 feet) {
        drone.setDeltaMovement(Vec3.ZERO);
        drone.resetTo(feet, new Quaternionf());
    }

    record Placed(BlockPos pos, BlockState state) {
    }

    record Saved(Vec3 pos, float yaw, float pitch, ItemStack head, ItemStack hand, GameType mode) {
    }

    private record Probe(String name, Consumer<ServerLevel> setup, int limit, VideoSystem system, Vec3 drone, boolean clear, int hops) {
    }
}
