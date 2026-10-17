package de.lardtom.propwash.video.client;

import de.lardtom.propwash.build.DroneBuild;
import de.lardtom.propwash.build.VideoSystem;
import de.lardtom.propwash.build.VideoType;
import de.lardtom.propwash.content.ContentIds;
import de.lardtom.propwash.dev.SelfTest;
import de.lardtom.propwash.dev.SelfTest.StepResult;
import de.lardtom.propwash.dev.client.Screenshots;
import de.lardtom.propwash.drone.DroneModule;
import de.lardtom.propwash.drone.QuadcopterEntity;
import de.lardtom.propwash.video.AnalogBreakup;
import de.lardtom.propwash.video.DigitalBreakup;
import de.lardtom.propwash.video.LinkBudget;
import de.lardtom.propwash.video.SpectateServer;
import de.lardtom.propwash.video.link.Medium;
import de.lardtom.propwash.video.link.RfMaterial;
import de.lardtom.propwash.video.net.ChannelListS2C;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

public final class ShadowSelfTests {
    private static final int SITE_OFFSET_Z = 400;
    private static final int TREE_X = 110;
    private static final int TRUNK_HEIGHT = 5;
    private static final int FOREST_PILOT = 50;
    private static final double HOUSE_RING = 7.0;
    private static final int BANDO_ROOM = 4;
    private static final int BANDO_FRONT = -2;
    private static final String FUTURE = "shadow_future";
    private static final String PROBE = "shadow_probe";
    private static final String MOVED = "shadow_moved";
    private static final String ARRIVED = "shadow_arrived";
    private static final String ROUNDS = "shadow_rounds";
    private static final String CELLS = "shadow_cells";
    private static final String SPAWNED = "shadow_spawned";
    private static final String REQUESTED = "shadow_requested";
    private static final String RETURN = "shadow_return";
    private static final String RETURN_TICK = "shadow_return_tick";
    private static final String HOME = "shadow_home";
    private static final String BACK = "shadow_back";
    private static final String PERF = "shadow_perf";
    private static final String AVERAGE = "shadow_average";
    private static final String ATTACHED = "shadow_attached";
    private static final String PILOT = "Kim";
    private static final int SETTLE_TICKS = 10;
    private static final int SETTLE_ROUNDS = 4;
    private static final int PROBE_TICKS = 400;
    private static final int HOME_SETTLE_TICKS = 20;
    private static final double HOME_TOLERANCE = 1.0;
    private static final double PLACE_TOLERANCE = 0.03;
    private static final double MATCH_DB = 1.5;
    private static final double MAX_AVERAGE_TICK_MS = 3.0;
    private static final String[] ANALOG_STAGES = {"sauber", "Störstreifen", "Farbverlust", "Schnee", "Rauschen"};
    private static final String[] DIGITAL_STAGES = {"sauber", "weicher", "Blöcke", "Standbilder", "verloren"};
    private static final AtomicReference<BlockPos> ORIGIN = new AtomicReference<>();
    private static final List<BlockPos> PLACED = new CopyOnWriteArrayList<>();
    private static final AtomicInteger DRONE = new AtomicInteger(-1);
    private static final AtomicReference<Saved> SAVED = new AtomicReference<>();
    private static final Probe[] PROBES = probes();

    private ShadowSelfTests() {
    }

    private static Probe[] probes() {
        List<Probe> list = new ArrayList<>();
        list.add(new Probe("1 Baum, 20 m", Lane.TREE, 20, 0.0, 0.0, 14.0));
        list.add(new Probe("1 Baum, 50 m", Lane.TREE, 50, 0.0, 0.0, 13.9));
        list.add(new Probe("1 Baum, 90 m", Lane.TREE, 90, 0.0, 0.0, 14.4));
        list.add(new Probe("Steinhaus, 30 m", Lane.HOUSE, 30, 0.0, 0.0, 13.6));
        list.add(new Probe("Steinhaus, 60 m", Lane.HOUSE, 60, 0.0, 0.0, 14.7));
        list.add(new Probe("Wald, 50 m", Lane.FOREST, FOREST_PILOT, 0.0, 0.0, 22.7));
        list.add(new Probe("freies Feld, 90 m", Lane.OPEN, 90, 0.0, 0.0, 0.0));
        double[][] bando = {{9.0, 18.1, 27.2}, {9.0, 18.1, 27.1}};
        int[] bandoPilot = {30, 60};
        for (int i = 0; i < bandoPilot.length; i++) {
            for (int walls = 1; walls <= 3; walls++) {
                list.add(new Probe(String.format(Locale.ROOT, "Bando aus Beton, %d %s, Pilot %d m davor", walls, walls == 1 ? "Wand" : "Wände", bandoPilot[i]),
                        Lane.BANDO, bandoPilot[i], BANDO_ROOM * (walls - 1), 0.0, bando[i][walls - 1]));
            }
        }
        double[] offsets = {-3.0, -1.5, -0.75, -0.5, -0.25, 0.0, 0.25, 0.5, 0.75, 1.5, 3.0};
        double[] past = {0.0, 0.0, 1.3, 4.4, 8.4, 13.9, 8.4, 4.4, 1.3, 0.0, 0.0};
        for (int i = 0; i < offsets.length; i++) {
            list.add(new Probe(String.format(Locale.ROOT, "Vorbeiflug am Baum, 50 m, %+.2f m seitlich", offsets[i]), Lane.TREE, 50, 0.0,
                    offsets[i], past[i]));
        }
        for (int degrees = 0; degrees < 360; degrees += 45) {
            double angle = Math.toRadians(degrees);
            list.add(new Probe("ums Steinhaus, 60 m, " + degrees + "°", Lane.HOUSE, 60, HOUSE_RING * (Math.cos(angle) - 1.0),
                    HOUSE_RING * Math.sin(angle), degrees == 0 ? 14.7 : 0.0));
        }
        return list.toArray(new Probe[0]);
    }

    public static void register() {
        SelfTest.clientStep("video: Funk-Material der Blöcke", 20, ShadowSelfTests::materialStep);
        SelfTest.clientStep("video: Abschattung hinter Baum, Haus, Bando und Wald, Vorbeiflug und Runde ums Haus", 7500, ShadowSelfTests::flightStep);
    }

    public static void registerShowcase() {
        if (!Screenshots.enabled()) {
            return;
        }
        Screenshots.step("bildschirmfotos: Abschattung – Aufbau mit der Drohne hinter dem Baum", "vtx_shadow_tree_overview", 30,
                new OverviewScene());
        shot(Lane.TREE, 20, VideoSystem.ANALOG, "Analogbild hinter einem Baum, 20 m");
        shot(Lane.TREE, 50, VideoSystem.ANALOG, "Analogbild hinter einem Baum, 50 m");
        shot(Lane.TREE, 100, VideoSystem.ANALOG, "Analogbild hinter einem Baum, 100 m");
        shot(Lane.TREE, 50, VideoSystem.DIGITAL, "Digitalbild hinter einem Baum, 50 m");
        shot(Lane.TREE, 100, VideoSystem.DIGITAL, "Digitalbild hinter einem Baum, 100 m");
        shot(Lane.HOUSE, 40, VideoSystem.ANALOG, "Analogbild hinter einem Steinhaus, 40 m");
        shot(Lane.HOUSE, 40, VideoSystem.DIGITAL, "Digitalbild hinter einem Steinhaus, 40 m");
        shot(Lane.FOREST, 30, VideoSystem.ANALOG, "Analogbild im Wald, 30 m");
        shot(Lane.FOREST, 30, VideoSystem.DIGITAL, "Digitalbild im Wald, 30 m");
        bandoShot(70, 1, VideoSystem.ANALOG, "Störstreifen");
        bandoShot(30, 2, VideoSystem.ANALOG, "Farbverlust");
        bandoShot(45, 2, VideoSystem.ANALOG, "Schnee");
        bandoShot(30, 3, VideoSystem.ANALOG, "Rauschen");
        bandoShot(60, 1, VideoSystem.DIGITAL, "sauber");
        bandoShot(40, 2, VideoSystem.DIGITAL, "weicher");
        bandoShot(60, 2, VideoSystem.DIGITAL, "Blöcke");
        bandoShot(71, 2, VideoSystem.DIGITAL, "Standbilder");
        bandoShot(60, 3, VideoSystem.DIGITAL, "verloren");
        SelfTest.clientStep("bildschirmfotos: Abschattung aufräumen", 600, ShadowSelfTests::cleanupStep);
    }

    private static void shot(Lane lane, int distance, VideoSystem system, String title) {
        String file = "vtx_shadow_" + lane.name().toLowerCase(Locale.ROOT) + "_" + distance + "_" + system.name().toLowerCase(Locale.ROOT);
        Screenshots.step("bildschirmfotos: Abschattung – " + title, file, 40, new ShadowScene(lane, distance, system, 0.0, null));
    }

    private static void bandoShot(int distance, int walls, VideoSystem system, String stage) {
        String file = "vtx_signal_bando_" + walls + "_" + distance + "_" + system.name().toLowerCase(Locale.ROOT);
        String title = String.format(Locale.ROOT, "%s im Bando hinter %d %s, Pilot %d m davor (%s)", system == VideoSystem.DIGITAL ? "Digitalbild" : "Analogbild",
                walls, walls == 1 ? "Wand" : "Wänden", distance, stage);
        Screenshots.step("bildschirmfotos: Abschattung – " + title, file, 40, new ShadowScene(Lane.BANDO, distance, system, BANDO_ROOM * (walls - 1), stage));
    }

    private static StepResult materialStep(SelfTest.StepContext ctx) {
        Object[][] expected = {
                {Blocks.OAK_LEAVES, RfMaterial.FOLIAGE}, {Blocks.SPRUCE_LEAVES, RfMaterial.FOLIAGE}, {Blocks.OAK_LOG, RfMaterial.TRUNK},
                {Blocks.BIRCH_WOOD, RfMaterial.TRUNK}, {Blocks.OAK_PLANKS, RfMaterial.TIMBER}, {Blocks.SPRUCE_STAIRS, RfMaterial.TIMBER},
                {Blocks.OAK_DOOR, RfMaterial.TIMBER}, {Blocks.COBBLESTONE, RfMaterial.MASONRY}, {Blocks.BRICKS, RfMaterial.MASONRY},
                {Blocks.STONE_BRICKS, RfMaterial.MASONRY}, {Blocks.CONCRETE.pick(DyeColor.WHITE), RfMaterial.CONCRETE}, {Blocks.STONE, RfMaterial.EARTH},
                {Blocks.DEEPSLATE, RfMaterial.EARTH}, {Blocks.DIRT, RfMaterial.EARTH}, {Blocks.GRASS_BLOCK, RfMaterial.EARTH},
                {Blocks.GRAVEL, RfMaterial.EARTH}, {Blocks.IRON_ORE, RfMaterial.EARTH}, {Blocks.SAND, RfMaterial.SAND},
                {Blocks.GLASS, RfMaterial.GLASS}, {Blocks.GLASS_PANE, RfMaterial.GLASS}, {Blocks.STAINED_GLASS.pick(DyeColor.RED), RfMaterial.GLASS},
                {Blocks.WATER, RfMaterial.WATER}, {Blocks.ICE, RfMaterial.ICE}, {Blocks.PACKED_ICE, RfMaterial.ICE},
                {Blocks.SNOW_BLOCK, RfMaterial.SNOW}, {Blocks.POWDER_SNOW, RfMaterial.SNOW}, {Blocks.WOOL.pick(DyeColor.WHITE), RfMaterial.CLOTH},
                {Blocks.IRON_BLOCK, RfMaterial.METAL}, {Blocks.IRON_DOOR, RfMaterial.METAL}, {Blocks.IRON_BARS, RfMaterial.METAL_MESH},
                {Blocks.SHORT_GRASS, RfMaterial.PLANT}, {Blocks.POPPY, RfMaterial.PLANT}, {Blocks.WHEAT, RfMaterial.PLANT},
                {Blocks.CACTUS, RfMaterial.TRUNK}, {Blocks.LAVA, RfMaterial.EARTH}, {Blocks.COBWEB, RfMaterial.PLANT},
                {Blocks.TERRACOTTA, RfMaterial.EARTH}, {Blocks.DYED_TERRACOTTA.pick(DyeColor.ORANGE), RfMaterial.EARTH},
                {Blocks.SANDSTONE, RfMaterial.EARTH}, {Blocks.RED_SANDSTONE, RfMaterial.EARTH}, {Blocks.END_STONE, RfMaterial.EARTH},
                {Blocks.GLAZED_TERRACOTTA.pick(DyeColor.ORANGE), RfMaterial.MASONRY}, {Blocks.CUT_SANDSTONE, RfMaterial.MASONRY},
                {Blocks.END_STONE_BRICKS, RfMaterial.MASONRY}};
        StringBuilder wrong = new StringBuilder();
        for (Object[] row : expected) {
            BlockState state = ((Block) row[0]).defaultBlockState();
            Medium medium = ClientBlockLoss.classify(state);
            if (medium.material() != row[1]) {
                wrong.append(' ').append(state.getBlock().getName().getString()).append('=').append(medium.material());
            }
        }
        Medium slab = ClientBlockLoss.classify(Blocks.OAK_SLAB.defaultBlockState());
        Medium pane = ClientBlockLoss.classify(Blocks.GLASS_PANE.defaultBlockState());
        if (slab.fullBlock() || pane.fullBlock() || !ClientBlockLoss.classify(Blocks.STONE.defaultBlockState()).fullBlock()) {
            wrong.append(" Form: Stufe/Scheibe als ganzer Block oder Stein nicht ganz");
        }
        if (!ClientBlockLoss.classify(Blocks.AIR.defaultBlockState()).empty() || !ClientBlockLoss.classify(Blocks.BARRIER.defaultBlockState()).empty()) {
            wrong.append(" Luft/Barriere nicht leer");
        }
        double[][] legacy = {
                {ClientBlockLoss.legacyReflectivity(Blocks.STONE.defaultBlockState()), 1.0},
                {ClientBlockLoss.legacyReflectivity(Blocks.OAK_LEAVES.defaultBlockState()), 1.2 / 7.0},
                {ClientBlockLoss.legacyReflectivity(Blocks.WATER.defaultBlockState()), 0.5},
                {ClientBlockLoss.legacyReflectivity(Blocks.GLASS.defaultBlockState()), 1.5 / 7.0}};
        for (double[] pair : legacy) {
            if (Math.abs(pair[0] - pair[1]) > 1.0E-9) {
                wrong.append(" Mehrwege-Reflexion ").append(pair[0]).append(" statt ").append(pair[1]);
            }
        }
        ctx.log("Materialien: Laub " + RfMaterial.FOLIAGE.straightBlockDb() + " dB, Holzwand " + RfMaterial.TIMBER.straightBlockDb()
                + " dB, Mauer " + RfMaterial.MASONRY.straightBlockDb() + " dB, Beton " + RfMaterial.CONCRETE.straightBlockDb()
                + " dB, Scheibe " + RfMaterial.GLASS.straightBlockDb() + " dB je Block");
        return StepResult.check(wrong.isEmpty(), "falsch eingeordnet:" + wrong);
    }

    private static StepResult flightStep(SelfTest.StepContext ctx) {
        Minecraft mc = Minecraft.getInstance();
        Map<String, Object> state = ctx.state();
        VideoState video = VideoState.INSTANCE;
        if (!state.containsKey(PROBE)) {
            state.put(PROBE, -1);
            state.put(CELLS, 0);
            state.put(MOVED, ctx.tick());
            state.put(FUTURE, ctx.onServer(server -> {
                ServerPlayer player = player(server);
                save(player);
                ensureSite(player);
                Probe first = PROBES[0];
                placePilot(player, first.lane, first.distance, first.antenna());
            }));
            return StepResult.pending();
        }
        CompletableFuture<?> future = (CompletableFuture<?>) state.get(FUTURE);
        if (future.isCompletedExceptionally()) {
            return fail(ctx, "Aufbau: " + future.exceptionNow());
        }
        if (!future.isDone() || mc.level == null || mc.player == null) {
            return StepResult.pending();
        }
        int index = (int) state.get(PROBE);
        int moved = (int) state.get(MOVED);
        if (index < 0) {
            Probe first = PROBES[0];
            if (!state.containsKey(SPAWNED)) {
                boolean ready = mc.player.position().distanceTo(pilotFeet(first.lane, first.distance)) < PLACE_TOLERANCE
                        && loaded(mc, first.antenna()) && loaded(mc, obstacle(first.lane));
                if (!ready) {
                    return ctx.tick() - moved > PROBE_TICKS ? fail(ctx, "Pilot kommt am Testgelände nicht an") : StepResult.pending();
                }
                state.put(SPAWNED, ctx.tick());
                state.put(MOVED, ctx.tick());
                state.put(FUTURE, ctx.onServer(server -> {
                    QuadcopterEntity drone = spawnDrone(player(server).level(), first.lane, VideoSystem.ANALOG);
                    moveDrone(drone, first.lane, first.antenna());
                }));
                return StepResult.pending();
            }
            if (!(mc.level.getEntity(DRONE.get()) instanceof QuadcopterEntity drone)) {
                return ctx.tick() - moved > PROBE_TICKS ? fail(ctx, "Testdrohne kommt auf dem Client nicht an") : StepResult.pending();
            }
            video.overrideTestReceiver(true, VideoSystem.ANALOG);
            video.attachTestSource(drone);
            state.put(ATTACHED, drone);
            video.resetScanStats();
            return next(ctx, 0);
        }
        if (index >= PROBES.length) {
            if (!state.containsKey(PERF)) {
                String perf = String.format(Locale.ROOT, "Abschattung je Tick: Ø %.3f ms, höchstens %.3f ms; größte Runde %d Zellen, letzte Runde %d Strahlen",
                        video.scanTickNanosAverage() / 1.0E6, video.scanTickNanosMax() / 1.0E6, (int) state.get(CELLS), video.scanRoundRays());
                ctx.log(perf);
                state.put(PERF, perf);
                state.put(AVERAGE, video.scanTickNanosAverage() / 1.0E6);
            }
            StepResult waiting = returnHome(ctx);
            if (waiting != null) {
                return waiting;
            }
            return StepResult.check((double) state.get(AVERAGE) < MAX_AVERAGE_TICK_MS, "Abschattung zu teuer: " + state.get(PERF));
        }
        Probe probe = PROBES[index];
        Vec3 pilot = pilotFeet(probe.lane, probe.distance);
        Vec3 target = probe.antenna();
        QuadcopterEntity drone = mc.level.getEntity(DRONE.get()) instanceof QuadcopterEntity d ? d : null;
        if (drone != null && drone != state.get(ATTACHED)) {
            video.attachTestSource(drone);
            state.put(ATTACHED, drone);
        }
        LocalPlayer player = mc.player;
        boolean placed = player.position().distanceTo(pilot) < PLACE_TOLERANCE && drone != null
                && drone.position().add(0.0, drone.getBbHeight() * 0.5, 0.0).distanceTo(target) < PLACE_TOLERANCE;
        boolean chunks = loaded(mc, pilot) && loaded(mc, target) && loaded(mc, obstacle(probe.lane));
        if (!placed || !chunks || video.status() != VideoState.LinkStatus.CONNECTED) {
            state.remove(ARRIVED);
            return ctx.tick() - moved > PROBE_TICKS ? fail(ctx, probe.name + ": Spieler oder Drohne nicht am Platz (Platz " + placed
                    + ", Chunks " + chunks + ", Verbindung " + video.status() + ")") : StepResult.pending();
        }
        if (!state.containsKey(ARRIVED)) {
            state.put(ARRIVED, ctx.tick());
            state.put(ROUNDS, video.scanRounds());
            return StepResult.pending();
        }
        if (ctx.tick() - (int) state.get(ARRIVED) < SETTLE_TICKS || video.scanRounds() - (int) state.get(ROUNDS) < SETTLE_ROUNDS) {
            return StepResult.pending();
        }
        double db = video.obstructionDb();
        double distance = video.distanceBlocks();
        double analog = LinkBudget.quality(VideoSystem.ANALOG, distance, VideoType.ANALOG_RACE.rangeBlocks, db);
        double digital = LinkBudget.quality(VideoSystem.DIGITAL, distance, VideoType.DIGITAL_HD.rangeBlocks, db);
        state.put(CELLS, Math.max((int) state.get(CELLS), video.scanRoundCells()));
        ctx.log(String.format(Locale.ROOT, "%s: %.1f dB Abschattung (Modell %.1f dB, Mittelstrahl %.1f dB) bei %.1f m, Analog %.2f, Digital %.2f, Signal %.2f, %d Strahlen, %d Zellen",
                probe.name, db, probe.expectedDb, video.scanCenterLossDb(), distance, analog, digital, video.rawQuality(), video.scanRoundRays(),
                video.scanRoundCells()));
        boolean matches = probe.expectedDb <= 0.0 ? db == 0.0 : Math.abs(db - probe.expectedDb) <= MATCH_DB;
        if (!matches) {
            return fail(ctx, String.format(Locale.ROOT, "%s: %.2f dB statt %.1f dB wie im Modell (JUnit ShadowScenariosTest)", probe.name, db,
                    probe.expectedDb));
        }
        if (probe.expectedDb > 0.0 && probe.expectedDb < 3.0 && !(video.scanCenterLossDb() == 0.0 && db > 0.5)) {
            return fail(ctx, String.format(Locale.ROOT, "%s: knapp vorbei sollte bei freiem Mittelstrahl schon kosten (Mittelstrahl %.1f dB, gesamt %.1f dB)",
                    probe.name, video.scanCenterLossDb(), db));
        }
        if (drone != null && ControlLinkScan.INSTANCE.videoBandLossDb(drone) != db) {
            return fail(ctx, String.format(Locale.ROOT, "%s: Steuerstrecke sieht %.2f dB statt der Abschattung der Videostrecke %.2f dB", probe.name,
                    ControlLinkScan.INSTANCE.videoBandLossDb(drone), db));
        }
        float expected = (float) LinkBudget.quality(VideoSystem.ANALOG, distance, VideoType.ANALOG_RACE.rangeBlocks, db);
        if (Math.abs(video.rawQuality() - expected) > 0.02F) {
            return fail(ctx, probe.name + ": Signal " + video.rawQuality() + " passt nicht zur Abschattung (erwartet " + expected + ")");
        }
        return next(ctx, index + 1);
    }

    private static StepResult next(SelfTest.StepContext ctx, int index) {
        Map<String, Object> state = ctx.state();
        state.put(PROBE, index);
        state.put(MOVED, ctx.tick());
        state.remove(ARRIVED);
        state.remove(ROUNDS);
        if (index < PROBES.length) {
            Probe target = PROBES[index];
            state.put(FUTURE, ctx.onServer(server -> {
                ServerPlayer player = player(server);
                QuadcopterEntity drone = serverDrone(server);
                if (drone == null) {
                    throw new IllegalStateException("Testdrohne fehlt");
                }
                moveDrone(drone, target.lane, target.antenna());
                placePilot(player, target.lane, target.distance, target.antenna());
            }));
        }
        return StepResult.pending();
    }

    private static StepResult fail(SelfTest.StepContext ctx, String message) {
        cleanup(ctx);
        return StepResult.fail(message);
    }

    private static StepResult cleanupStep(SelfTest.StepContext ctx) {
        if (!ctx.state().containsKey(RETURN)) {
            SpectateSession.INSTANCE.stop(Minecraft.getInstance(), true);
        }
        StepResult waiting = returnHome(ctx);
        return waiting != null ? waiting : StepResult.pass();
    }

    private static @Nullable StepResult returnHome(SelfTest.StepContext ctx) {
        Minecraft mc = Minecraft.getInstance();
        Map<String, Object> state = ctx.state();
        if (!state.containsKey(RETURN)) {
            Saved saved = SAVED.get();
            if (saved != null) {
                state.put(HOME, saved.pos);
            }
            state.put(RETURN_TICK, ctx.tick());
            state.put(RETURN, cleanup(ctx));
            return StepResult.pending();
        }
        CompletableFuture<?> back = (CompletableFuture<?>) state.get(RETURN);
        if (back.isCompletedExceptionally()) {
            return StepResult.fail("Aufräumen: " + back.exceptionNow());
        }
        Vec3 home = (Vec3) state.get(HOME);
        LocalPlayer player = mc.player;
        boolean arrived = back.isDone() && player != null && mc.level != null && (home == null
                || (Math.hypot(player.getX() - home.x, player.getZ() - home.z) < HOME_TOLERANCE && loaded(mc, home)));
        if (!arrived) {
            state.remove(BACK);
            return ctx.tick() - (int) state.get(RETURN_TICK) > PROBE_TICKS ? StepResult.fail("Spieler kommt nach dem Test nicht an seinen Platz zurück")
                    : StepResult.pending();
        }
        if (!state.containsKey(BACK)) {
            state.put(BACK, ctx.tick());
        }
        return ctx.tick() - (int) state.get(BACK) < HOME_SETTLE_TICKS ? StepResult.pending() : null;
    }

    private static CompletableFuture<Void> cleanup(SelfTest.StepContext ctx) {
        VideoState.INSTANCE.attachTestSource(null);
        VideoState.INSTANCE.overrideTestReceiver(false, null);
        return ctx.onServer(ShadowSelfTests::teardown);
    }

    private static void teardown(MinecraftServer server) {
        QuadcopterEntity drone = serverDrone(server);
        if (drone != null) {
            SpectateServer.simulateTransmitter(drone, null);
            drone.discard();
        }
        DRONE.set(-1);
        ServerPlayer player = player(server);
        ServerLevel level = player.level();
        BlockPos o = ORIGIN.get();
        if (o != null) {
            AABB site = new AABB(o.getX() - 8, o.getY() - 4, o.getZ() - 60, o.getX() + TREE_X + 24, o.getY() + 40, o.getZ() + 130);
            for (QuadcopterEntity stray : level.getEntitiesOfClass(QuadcopterEntity.class, site)) {
                SpectateServer.simulateTransmitter(stray, null);
                stray.discard();
            }
        }
        SpectateServer.refreshSoon();
        for (BlockPos pos : PLACED) {
            level.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
        }
        PLACED.clear();
        ORIGIN.set(null);
        Saved saved = SAVED.getAndSet(null);
        if (saved != null) {
            player.setItemSlot(EquipmentSlot.HEAD, saved.head);
            player.setGameMode(saved.mode);
            player.teleportTo(level, saved.pos.x, saved.pos.y, saved.pos.z, Set.of(), saved.yaw, saved.pitch, true);
        }
    }

    private static void save(ServerPlayer player) {
        SAVED.compareAndSet(null, new Saved(player.position(), player.getYRot(), player.getXRot(),
                player.getItemBySlot(EquipmentSlot.HEAD).copy(), player.gameMode()));
        player.setGameMode(GameType.CREATIVE);
    }

    private static ServerPlayer player(MinecraftServer server) {
        List<ServerPlayer> players = server.getPlayerList().getPlayers();
        if (players.isEmpty()) {
            throw new IllegalStateException("kein Spieler");
        }
        return players.getFirst();
    }

    private static @Nullable QuadcopterEntity serverDrone(MinecraftServer server) {
        int id = DRONE.get();
        if (id < 0) {
            return null;
        }
        for (ServerLevel level : server.getAllLevels()) {
            Entity entity = level.getEntity(id);
            if (entity instanceof QuadcopterEntity drone) {
                return drone;
            }
        }
        return null;
    }

    private static BlockPos ensureSite(ServerPlayer player) {
        BlockPos origin = ORIGIN.get();
        if (origin != null) {
            return origin;
        }
        ServerLevel level = player.level();
        int x0 = player.getBlockX();
        int z0 = player.getBlockZ() + SITE_OFFSET_Z;
        for (int cx = (x0 - 8) >> 4; cx <= (x0 + TREE_X + 24) >> 4; cx++) {
            for (int cz = (z0 - 110) >> 4; cz <= (z0 + 130) >> 4; cz++) {
                level.getChunk(cx, cz);
            }
        }
        int y0 = level.getHeight(Heightmap.Types.MOTION_BLOCKING, x0, z0);
        origin = new BlockPos(x0, y0, z0);
        ORIGIN.set(origin);
        int xt = x0 + TREE_X;
        oak(level, xt, y0, z0 + Lane.TREE.z, TRUNK_HEIGHT, xt - x0, 0);
        house(level, xt - 6, y0, z0 + Lane.HOUSE.z - 3);
        bando(level, xt + BANDO_FRONT, y0, z0 + Lane.BANDO.z);
        int fx = xt - FOREST_PILOT;
        int fz = z0 + Lane.FOREST.z;
        for (int x = FOREST_PILOT - 15; x <= FOREST_PILOT + 12; x += 5) {
            for (int z = -24; z <= 24; z += 5) {
                int jx = Math.floorMod(x * 7 + z * 13, 3) - 1;
                int jz = Math.floorMod(x * 11 + z * 5, 3) - 1;
                oak(level, fx + x + jx, y0, fz + z + jz, 4 + Math.floorMod(x * 3 + z * 5, 3), x + jx, z + jz);
            }
        }
        BlockPos pocket = BlockPos.containing(antenna(Lane.FOREST));
        if (!level.getBlockState(pocket).isAir()) {
            level.setBlock(pocket, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
        }
        return origin;
    }

    private static void oak(ServerLevel level, int x, int y, int z, int height, int px, int pz) {
        BlockState leaves = Blocks.OAK_LEAVES.defaultBlockState().setValue(LeavesBlock.PERSISTENT, true);
        for (int layer = -3; layer <= 0; layer++) {
            int radius = layer >= -1 ? 1 : 2;
            int py = height + layer;
            for (int dx = -radius; dx <= radius; dx++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    boolean corner = Math.abs(dx) == radius && Math.abs(dz) == radius;
                    if (corner && (layer == 0 || ((px + dx * 31 + pz * 17 + dz * 7 + py) & 1) == 0)) {
                        continue;
                    }
                    replace(level, new BlockPos(x + dx, y + py, z + dz), leaves);
                }
            }
        }
        for (int dy = 0; dy < height; dy++) {
            replace(level, new BlockPos(x, y + dy, z), Blocks.OAK_LOG.defaultBlockState());
        }
    }

    private static void replace(ServerLevel level, BlockPos pos, BlockState state) {
        BlockState current = level.getBlockState(pos);
        if (current.isAir()) {
            put(level, pos, state);
        } else if (current.is(Blocks.OAK_LEAVES) || current.is(Blocks.OAK_LOG)) {
            level.setBlock(pos, state, Block.UPDATE_CLIENTS);
        }
    }

    private static void house(ServerLevel level, int x0, int y0, int z0) {
        int size = 7;
        for (int x = x0; x < x0 + size; x++) {
            for (int z = z0; z < z0 + size; z++) {
                boolean edge = x == x0 || x == x0 + size - 1 || z == z0 || z == z0 + size - 1;
                boolean corner = (x == x0 || x == x0 + size - 1) && (z == z0 || z == z0 + size - 1);
                if (edge) {
                    for (int y = 0; y < 4; y++) {
                        boolean window = !corner && y == 1 && ((x - x0) % 3 == 1 || (z - z0) % 3 == 1);
                        put(level, new BlockPos(x, y0 + y, z), window ? Blocks.GLASS.defaultBlockState() : Blocks.COBBLESTONE.defaultBlockState());
                    }
                }
                put(level, new BlockPos(x, y0 + 4, z), Blocks.OAK_PLANKS.defaultBlockState());
                if (!edge) {
                    put(level, new BlockPos(x, y0 + 5, z), Blocks.OAK_SLAB.defaultBlockState());
                }
            }
        }
    }

    private static void bando(ServerLevel level, int x0, int y0, int zc) {
        int length = 3 * BANDO_ROOM;
        for (int x = x0; x <= x0 + length; x++) {
            for (int y = 0; y <= 6; y++) {
                for (int z = zc - 8; z <= zc + 8; z++) {
                    boolean shell = x == x0 || x == x0 + length || y == 6 || z == zc - 8 || z == zc + 8;
                    boolean inner = (x - x0) % BANDO_ROOM == 0;
                    if (shell || inner) {
                        DyeColor color = y == 1 ? DyeColor.ORANGE : y == 3 ? DyeColor.CYAN : Math.floorMod(z, 4) == 0 ? DyeColor.YELLOW : DyeColor.LIGHT_GRAY;
                        put(level, new BlockPos(x, y0 + y, z), Blocks.CONCRETE.pick(color).defaultBlockState());
                    }
                }
            }
        }
        for (int room = 0; room < 3; room++) {
            for (int z = zc - 6; z <= zc + 6; z += 3) {
                put(level, new BlockPos(x0 + room * BANDO_ROOM + 2, y0 + 5, z), Blocks.LIGHT.defaultBlockState());
            }
        }
    }

    private static void put(ServerLevel level, BlockPos pos, BlockState state) {
        if (level.getBlockState(pos).isAir()) {
            level.setBlock(pos, state, Block.UPDATE_CLIENTS);
            PLACED.add(pos.immutable());
        }
    }

    private static Vec3 pilotFeet(Lane lane, int distance) {
        BlockPos o = ORIGIN.get();
        if (o == null) {
            return Vec3.ZERO;
        }
        return new Vec3(o.getX() + TREE_X + lane.droneX - distance, o.getY(), o.getZ() + lane.z + 0.5);
    }

    private static Vec3 antenna(Lane lane) {
        BlockPos o = ORIGIN.get();
        if (o == null) {
            return Vec3.ZERO;
        }
        return new Vec3(o.getX() + TREE_X + lane.droneX, o.getY() + lane.height, o.getZ() + lane.z + 0.5);
    }

    private static Vec3 obstacle(Lane lane) {
        BlockPos o = ORIGIN.get();
        return o == null ? Vec3.ZERO : new Vec3(o.getX() + TREE_X, o.getY(), o.getZ() + lane.z);
    }

    private static boolean loaded(Minecraft mc, Vec3 pos) {
        return mc.level != null && mc.level.getChunkSource().hasChunk(((int) Math.floor(pos.x)) >> 4, ((int) Math.floor(pos.z)) >> 4);
    }

    private static void placePilot(ServerPlayer player, Lane lane, int distance, Vec3 target) {
        Vec3 feet = pilotFeet(lane, distance);
        Vec3 eye = feet.add(0.0, player.getEyeHeight(), 0.0);
        double dx = target.x - eye.x;
        double dy = target.y - eye.y;
        double dz = target.z - eye.z;
        float yaw = (float) Math.toDegrees(Math.atan2(-dx, dz));
        float pitch = (float) -Math.toDegrees(Math.atan2(dy, Math.sqrt(dx * dx + dz * dz)));
        player.getAbilities().flying = false;
        player.onUpdateAbilities();
        player.teleportTo(player.level(), feet.x, feet.y, feet.z, Set.of(), yaw, pitch, true);
    }

    private static QuadcopterEntity spawnDrone(ServerLevel level, Lane lane, VideoSystem system) {
        QuadcopterEntity existing = serverDrone(level.getServer());
        if (existing != null) {
            existing.setBuild(build(system));
            moveDrone(existing, lane, antenna(lane));
            return existing;
        }
        QuadcopterEntity drone = DroneModule.DRONE.get().create(level, EntitySpawnReason.COMMAND);
        if (drone == null) {
            throw new IllegalStateException("Drohne nicht erzeugt");
        }
        drone.setBuild(build(system));
        drone.setNoGravity(true);
        Vec3 antenna = antenna(lane);
        drone.snapTo(antenna.x, antenna.y - drone.getBbHeight() * 0.5, antenna.z, 90.0F, 0.0F);
        if (!level.addFreshEntity(drone)) {
            throw new IllegalStateException("addFreshEntity fehlgeschlagen");
        }
        DRONE.set(drone.getId());
        moveDrone(drone, lane, antenna);
        return drone;
    }

    private static void moveDrone(QuadcopterEntity drone, Lane lane, Vec3 antenna) {
        Vec3 pos = new Vec3(antenna.x, antenna.y - drone.getBbHeight() * 0.5, antenna.z);
        float yaw = (float) Math.toRadians(-90.0) + (lane.faceAway ? 0.0F : (float) Math.PI);
        drone.setDeltaMovement(Vec3.ZERO);
        drone.resetTo(pos, new Quaternionf().rotationY(yaw).rotateX((float) Math.toRadians(-6.0)));
    }

    private static DroneBuild build(VideoSystem system) {
        DroneBuild racer = DroneBuild.RACER;
        VideoType video = system == VideoSystem.DIGITAL ? VideoType.DIGITAL_HD : VideoType.ANALOG_RACE;
        return new DroneBuild(racer.frame(), racer.stack(), racer.motor(), racer.prop(), video, racer.battery());
    }

    private static ItemStack goggles(VideoSystem system) {
        return ContentIds.item(system == VideoSystem.DIGITAL ? ContentIds.GOGGLES_DIGITAL : ContentIds.GOGGLES_ANALOG)
                .map(ItemStack::new).orElse(ItemStack.EMPTY);
    }

    private enum Lane {
        TREE(0, 5.5, 1.5, false),
        HOUSE(40, 4.5, 2.0, false),
        FOREST(90, 0.5, 2.5, true),
        OPEN(-40, 5.5, 10.0, false),
        BANDO(-90, 0.5, 2.0, false);

        private final int z;
        private final double droneX;
        private final double height;
        private final boolean faceAway;

        Lane(int z, double droneX, double height, boolean faceAway) {
            this.z = z;
            this.droneX = droneX;
            this.height = height;
            this.faceAway = faceAway;
        }
    }

    private record Probe(String name, Lane lane, int distance, double dx, double dz, double expectedDb) {
        Vec3 antenna() {
            return ShadowSelfTests.antenna(this.lane).add(this.dx, 0.0, this.dz);
        }
    }

    private record Saved(Vec3 pos, float yaw, float pitch, ItemStack head, GameType mode) {
    }

    private static final class ShadowScene implements Screenshots.Scene {
        private final Lane lane;
        private final int distance;
        private final VideoSystem system;
        private final double dx;
        private final @Nullable String stage;

        private ShadowScene(Lane lane, int distance, VideoSystem system, double dx, @Nullable String stage) {
            this.lane = lane;
            this.distance = distance;
            this.system = system;
            this.dx = dx;
            this.stage = stage;
        }

        private Vec3 target() {
            return antenna(this.lane).add(this.dx, 0.0, 0.0);
        }

        @Override
        public StepResult prepare(SelfTest.StepContext ctx) {
            Minecraft mc = Minecraft.getInstance();
            Map<String, Object> state = ctx.state();
            if (!state.containsKey(FUTURE)) {
                state.put(FUTURE, ctx.onServer(server -> {
                    ServerPlayer player = player(server);
                    save(player);
                    ensureSite(player);
                    placePilot(player, this.lane, this.distance, this.target());
                    player.setItemSlot(EquipmentSlot.HEAD, goggles(this.system));
                    QuadcopterEntity drone = spawnDrone(player.level(), this.lane, this.system);
                    moveDrone(drone, this.lane, this.target());
                    SpectateServer.simulateTransmitter(drone, PILOT);
                    SpectateServer.refreshSoon();
                }));
                return StepResult.pending();
            }
            CompletableFuture<?> future = (CompletableFuture<?>) state.get(FUTURE);
            if (future.isCompletedExceptionally()) {
                return StepResult.fail("Aufbau: " + future.exceptionNow());
            }
            if (!future.isDone() || mc.player == null || mc.level == null) {
                return StepResult.pending();
            }
            SpectateSession session = SpectateSession.INSTANCE;
            if (!state.containsKey(REQUESTED)) {
                Optional<ChannelListS2C.Entry> entry = session.watchable(mc).stream()
                        .filter(e -> e.entityId() == DRONE.get() && e.system() == this.system).findFirst();
                boolean chunks = loaded(mc, pilotFeet(this.lane, this.distance));
                if (entry.isEmpty() || mc.gui.screen() != null || !chunks || mc.player.position().distanceTo(pilotFeet(this.lane, this.distance)) > 0.6) {
                    return ctx.tick() > 600 ? StepResult.fail("Kanal der Testdrohne nicht schaubar: " + session.watchable(mc).size())
                            : StepResult.pending();
                }
                if (!session.request(mc, entry.get())) {
                    return StepResult.fail("Zuschauen verweigert: " + session.lastResult());
                }
                state.put(REQUESTED, ctx.tick());
                return StepResult.pending();
            }
            if (!session.watching()) {
                return ctx.tick() - (int) state.get(REQUESTED) > 300 ? StepResult.fail("Zuschauen startet nicht: " + session.phase())
                        : StepResult.pending();
            }
            if (!loaded(mc, antenna(this.lane)) || !loaded(mc, obstacle(this.lane))) {
                return ctx.tick() - (int) state.get(REQUESTED) > 300 ? StepResult.fail("Chunks an der Testdrohne fehlen beim Zuschauen")
                        : StepResult.pending();
            }
            if (this.stage == null) {
                return StepResult.pass();
            }
            VideoState video = VideoState.INSTANCE;
            if (!state.containsKey(ARRIVED)) {
                state.put(ARRIVED, ctx.tick());
                state.put(ROUNDS, video.scanRounds());
                return StepResult.pending();
            }
            if (ctx.tick() - (int) state.get(ARRIVED) < SETTLE_TICKS * 3 || video.scanRounds() - (int) state.get(ROUNDS) < SETTLE_ROUNDS) {
                return StepResult.pending();
            }
            String shown = this.system == VideoSystem.DIGITAL ? DIGITAL_STAGES[DigitalBreakup.stage(video.rawQuality()).ordinal()]
                    : ANALOG_STAGES[AnalogBreakup.stage(video.rawQuality()).ordinal()];
            if (!shown.equals(this.stage)) {
                return StepResult.fail(String.format(Locale.ROOT, "Bando %d m, %s: Stufe %s statt %s (%.1f dB, Signal %.2f)", this.distance, this.system,
                        shown, this.stage, video.obstructionDb(), video.rawQuality()));
            }
            boolean black = video.noSignal();
            if (this.system == VideoSystem.DIGITAL && black != "verloren".equals(this.stage)) {
                return ctx.tick() - (int) state.get(ARRIVED) > 200 ? StepResult.fail("Bando " + this.distance + " m: Schwarzbild " + black
                        + " passt nicht zur Stufe " + this.stage) : StepResult.pending();
            }
            return StepResult.pass();
        }

        @Override
        public void finish(SelfTest.StepContext ctx) {
            VideoState video = VideoState.INSTANCE;
            ctx.log(String.format(Locale.ROOT, "%s %s %d m%s: %.1f dB Abschattung, Abstand %.1f m, Signal %.2f (roh %.2f), Rauschen %.2f, verloren %s, Latenz %.0f ms",
                    this.lane, this.system, this.distance, this.stage == null ? "" : " (" + this.stage + ")", video.obstructionDb(), video.distanceBlocks(),
                    video.signalQuality(), video.rawQuality(), video.noiseLevel(), video.videoLost(), video.latencyNanos() / 1.0E6));
            SpectateSession.INSTANCE.stop(Minecraft.getInstance(), true);
        }
    }

    private static final class OverviewScene implements Screenshots.Scene {
        @Override
        public StepResult prepare(SelfTest.StepContext ctx) {
            Minecraft mc = Minecraft.getInstance();
            Map<String, Object> state = ctx.state();
            if (!state.containsKey(FUTURE)) {
                state.put(FUTURE, ctx.onServer(server -> {
                    ServerPlayer player = player(server);
                    save(player);
                    ensureSite(player);
                    player.setItemSlot(EquipmentSlot.HEAD, ItemStack.EMPTY);
                    Vec3 feet = overviewFeet();
                    Vec3 look = overviewLook();
                    Vec3 eye = feet.add(0.0, player.getEyeHeight(), 0.0);
                    double dx = look.x - eye.x;
                    double dy = look.y - eye.y;
                    double dz = look.z - eye.z;
                    float yaw = (float) Math.toDegrees(Math.atan2(-dx, dz));
                    float pitch = (float) -Math.toDegrees(Math.atan2(dy, Math.sqrt(dx * dx + dz * dz)));
                    player.getAbilities().flying = false;
                    player.onUpdateAbilities();
                    player.teleportTo(player.level(), feet.x, feet.y, feet.z, Set.of(), yaw, pitch, true);
                }));
                return StepResult.pending();
            }
            CompletableFuture<?> future = (CompletableFuture<?>) state.get(FUTURE);
            if (future.isCompletedExceptionally()) {
                return StepResult.fail("Aufbau: " + future.exceptionNow());
            }
            if (!future.isDone() || mc.level == null || mc.player == null) {
                return StepResult.pending();
            }
            if (!state.containsKey(SPAWNED)) {
                if (mc.player.position().distanceTo(overviewFeet()) > PLACE_TOLERANCE || !loaded(mc, antenna(Lane.TREE))) {
                    return ctx.tick() > 600 ? StepResult.fail("Spieler kommt am Testgelände nicht an") : StepResult.pending();
                }
                state.put(SPAWNED, ctx.tick());
                state.put(FUTURE, ctx.onServer(server -> spawnDrone(player(server).level(), Lane.TREE, VideoSystem.ANALOG)));
                return StepResult.pending();
            }
            if (!(mc.level.getEntity(DRONE.get()) instanceof QuadcopterEntity)) {
                return ctx.tick() > 900 ? StepResult.fail("Testdrohne fehlt auf dem Client") : StepResult.pending();
            }
            return StepResult.pass();
        }

        private static Vec3 overviewFeet() {
            BlockPos o = ORIGIN.get();
            return o == null ? Vec3.ZERO : new Vec3(o.getX() + TREE_X + 8.5, o.getY(), o.getZ() - 2.5);
        }

        private static Vec3 overviewLook() {
            BlockPos o = ORIGIN.get();
            return o == null ? Vec3.ZERO : new Vec3(o.getX() + TREE_X + 2.5, o.getY() + 2.0, o.getZ() + 0.5);
        }
    }
}
