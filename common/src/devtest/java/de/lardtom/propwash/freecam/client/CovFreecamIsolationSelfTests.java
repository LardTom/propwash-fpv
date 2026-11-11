package de.lardtom.propwash.freecam.client;

import de.lardtom.propwash.audio.client.MotorVoices;
import de.lardtom.propwash.blackbox.BlackboxRecorder;
import de.lardtom.propwash.blackbox.client.BlackboxKeys;
import de.lardtom.propwash.blackbox.client.BlackboxSession;
import de.lardtom.propwash.build.DroneBuild;
import de.lardtom.propwash.content.ContentIds;
import de.lardtom.propwash.core.CoreComponents;
import de.lardtom.propwash.dev.SelfTest;
import de.lardtom.propwash.dev.SelfTest.StepResult;
import de.lardtom.propwash.drone.DroneInteractions;
import de.lardtom.propwash.drone.DroneModule;
import de.lardtom.propwash.drone.DronePiloting;
import de.lardtom.propwash.drone.PilotMode;
import de.lardtom.propwash.drone.QuadcopterEntity;
import de.lardtom.propwash.drone.client.PilotSession;
import de.lardtom.propwash.drone.client.trail.TrailRenderer;
import de.lardtom.propwash.freecam.FreecamRules;
import de.lardtom.propwash.freecam.client.FreecamSelfTests.Run;
import de.lardtom.propwash.input.RadioSource;
import de.lardtom.propwash.input.RadioState;
import de.lardtom.propwash.input.StickPositions;
import de.lardtom.propwash.input.client.InputKeys;
import de.lardtom.propwash.input.client.SimulatedRadio;
import de.lardtom.propwash.input.client.TransmitterInput;
import de.lardtom.propwash.race.CourseData;
import de.lardtom.propwash.race.CourseRegistry;
import de.lardtom.propwash.race.CourseToolItem;
import de.lardtom.propwash.race.GateEntity;
import de.lardtom.propwash.race.GateGeometry;
import de.lardtom.propwash.race.LapTracker;
import de.lardtom.propwash.race.RaceModule;
import de.lardtom.propwash.race.client.ClientCourses;
import de.lardtom.propwash.race.client.ClientRaceTracker;
import de.lardtom.propwash.race.client.RaceState;
import de.lardtom.propwash.rx.RxBlock;
import de.lardtom.propwash.rx.RxBlockEntity;
import de.lardtom.propwash.rx.RxModule;
import de.lardtom.propwash.rx.RxSettings;
import de.lardtom.propwash.rx.RxSource;
import de.lardtom.propwash.rx.client.RxClient;
import de.lardtom.propwash.sim.StabMode;
import de.lardtom.propwash.trick.client.TrickTracker;
import de.lardtom.propwash.video.SpectateServer;
import de.lardtom.propwash.video.client.SpectateKeys;
import de.lardtom.propwash.video.client.SpectateSession;
import de.lardtom.propwash.video.client.record.ActionCamRecorder;
import de.lardtom.propwash.video.client.record.FlashbackCam;
import de.lardtom.propwash.video.client.record.RecordingKeys;
import de.lardtom.propwash.video.net.ChannelListS2C;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.joml.Vector3d;
import org.joml.Vector3f;
import org.jspecify.annotations.Nullable;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.TreeMap;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;

public final class CovFreecamIsolationSelfTests {
    private static final int GATE_COUNT = 3;
    private static final int SPACING = 10;
    private static final int GATE_DX = -24;
    private static final int RX_DX = -2;
    private static final int RX_DZ = 3;
    private static final String PILOT_NAME = "Abdeckungspilot";
    private static final String WATCHED = "cov_watched";
    private static final String GATE_PASSES = "cov_gate_passes";
    private static final AtomicInteger WATCHED_ID = new AtomicInteger(-1);
    private static final AtomicInteger FLOWN_ID = new AtomicInteger(-1);
    private static final AtomicInteger COURSE = new AtomicInteger(-1);
    private static final List<Integer> GATES = new CopyOnWriteArrayList<>();
    private static final AtomicReference<BlockPos> RX = new AtomicReference<>();
    private static final AtomicReference<ItemStack> HEAD = new AtomicReference<>();
    private static final AtomicReference<ItemStack> HAND = new AtomicReference<>();
    private static final List<LapTracker.Event> EVENTS = new CopyOnWriteArrayList<>();
    private static final List<Placed> PLACED = new CopyOnWriteArrayList<>();

    private record Placed(BlockPos pos, BlockState state) {
    }

    private CovFreecamIsolationSelfTests() {
    }

    record Counters(int trails, int motors, int synth, int samples, long trickFrames, int tricks, int history, boolean racing) {
        static Counters now() {
            return new Counters(TrailRenderer.trailsTracked(), MotorVoices.trackedDrones(), MotorVoices.synthVoices(), MotorVoices.sampleVoices(),
                    TrickTracker.INSTANCE.frames(), TrickTracker.INSTANCE.session().score().tricks(), TrickTracker.INSTANCE.session().history().size(),
                    RaceState.INSTANCE.racing());
        }
    }

    record Folder(TreeMap<String, Long> sizes) {
        static Folder of(Path dir) {
            TreeMap<String, Long> sizes = new TreeMap<>();
            if (Files.isDirectory(dir)) {
                try (Stream<Path> list = Files.list(dir)) {
                    for (Path file : list.toList()) {
                        sizes.put(file.getFileName().toString(), Files.size(file));
                    }
                } catch (IOException e) {
                    sizes.put("Fehler: " + e, -1L);
                }
            }
            return new Folder(sizes);
        }
    }

    static void register() {
        String group = CovFreecamSelfTests.GROUP;
        SelfTest.clientStep(group + "F7 beendet die Freecam und startet das Zuschauen, F6 im echten Flug verweigert", 3000, CovFreecamSelfTests.guarded(
                CovFreecamSelfTests.phase("Regel an", 300, (ctx, run) -> FreecamSelfTests.setRule(ctx, run, true)),
                CovFreecamSelfTests.phase("fremde Drohne mit Testkanal, Brille auf", 400, CovFreecamIsolationSelfTests::watchedDrone),
                CovFreecamSelfTests.phase("Start", 300, FreecamSelfTests::start),
                CovFreecamSelfTests.phase("F7", 400, CovFreecamIsolationSelfTests::pressF7),
                CovFreecamSelfTests.phase("Zuschauen beenden", 200, CovFreecamIsolationSelfTests::stopWatching),
                CovFreecamSelfTests.phase("echte Drohne im FPV", 400, CovFreecamIsolationSelfTests::realFlight),
                CovFreecamSelfTests.phase("F6 im echten Flug", 100, CovFreecamIsolationSelfTests::f6WhilePiloting),
                CovFreecamSelfTests.phase("Flug beenden, Drohnen weg", 300, CovFreecamIsolationSelfTests::endFlight)));
        SelfTest.clientStep(group + "nichts wird gewertet – Gates eines Testkurses, Flip, keine Runde, kein Trick, kein Trail, kein Motorton", 3000,
                CovFreecamSelfTests.guarded(
                        CovFreecamSelfTests.phase("Testkurs mit drei Gates", 400, CovFreecamIsolationSelfTests::buildCourse),
                        CovFreecamSelfTests.hitch(),
                        CovFreecamSelfTests.phase("Start", 300, FreecamSelfTests::start),
                        CovFreecamSelfTests.phase("Zähler merken, scharf vor dem ersten Gate", 200, CovFreecamIsolationSelfTests::beforeGates),
                        CovFreecamSelfTests.phase("durch die Gates hin und zurück", 600, CovFreecamIsolationSelfTests::throughGates),
                        CovFreecamSelfTests.phase("Flip per Test-Eingabe", 300, CovFreecamIsolationSelfTests::flip),
                        CovFreecamSelfTests.phase("nichts gewertet", 60, CovFreecamIsolationSelfTests::nothingCounted),
                        CovFreecamSelfTests.phase("Ende", 100, FreecamSelfTests::endQuietly),
                        CovFreecamSelfTests.phase("Kurs abbauen", 300, (ctx, run) -> FreecamSelfTests.server(ctx, run,
                                CovFreecamIsolationSelfTests::removeCourse))));
        SelfTest.clientStep(group + "F9 und Ziffernblock 7 in der Freecam – keine Aufnahme, keine Blackbox", 1600, CovFreecamSelfTests.guarded(
                CovFreecamSelfTests.phase("Start", 300, FreecamSelfTests::start),
                CovFreecamSelfTests.phase("F9 und Ziffernblock 7, dann scharf fliegen", 200, CovFreecamIsolationSelfTests::recordKeys),
                CovFreecamSelfTests.phase("Ende", 100, FreecamSelfTests::endQuietly)));
        SelfTest.clientStep(group + "RX-Block auf Gas – folgt der Test-Funke in der Freecam, nicht der Tastatur W/S", 2400, CovFreecamSelfTests.guarded(
                CovFreecamSelfTests.phase("RX-Block auf Gas neben dem Spieler", 300, CovFreecamIsolationSelfTests::placeRx),
                CovFreecamSelfTests.phase("Test-Funke, Gas unten, Block bei 0", 300, CovFreecamIsolationSelfTests::rxRadioIdle),
                CovFreecamSelfTests.phase("Start", 300, FreecamSelfTests::start),
                CovFreecamSelfTests.phase("Test-Funke Vollgas: Block folgt", 300, (ctx, run) -> rxFollows(ctx, run, 2000, 15)),
                CovFreecamSelfTests.phase("Test-Funke halbes Gas: Block folgt", 300, (ctx, run) -> rxFollows(ctx, run, 1500, -1)),
                CovFreecamSelfTests.phase("Tastatur übernimmt", 100, CovFreecamIsolationSelfTests::rxKeyboard),
                CovFreecamSelfTests.phase("W und S in der Freecam: Block hält", 200, CovFreecamIsolationSelfTests::rxKeysIgnored),
                CovFreecamSelfTests.phase("Ende", 100, FreecamSelfTests::endQuietly),
                CovFreecamSelfTests.phase("RX-Block abbauen", 300, (ctx, run) -> FreecamSelfTests.server(ctx, run, CovFreecamIsolationSelfTests::removeRx))));
    }

    static void tearDown(MinecraftServer server) {
        releaseDrones(server);
        removeCourse(server);
        removeRx(server);
    }

    private static void discard(MinecraftServer server, AtomicInteger id) {
        int value = id.getAndSet(-1);
        if (value < 0) {
            return;
        }
        for (ServerLevel level : server.getAllLevels()) {
            Entity entity = level.getEntity(value);
            if (entity instanceof QuadcopterEntity drone) {
                SpectateServer.simulateTransmitter(drone, null);
                drone.discard();
            }
        }
    }

    static void releaseDrones(MinecraftServer server) {
        if (server.getPlayerList().getPlayers().isEmpty()) {
            return;
        }
        ServerPlayer player = FreecamSelfTests.player(server);
        DronePiloting.pilotedBy(player.getUUID()).ifPresent(DronePiloting::release);
        discard(server, WATCHED_ID);
        discard(server, FLOWN_ID);
        ItemStack head = HEAD.getAndSet(null);
        if (head != null) {
            player.setItemSlot(EquipmentSlot.HEAD, head);
        }
        ItemStack hand = HAND.getAndSet(null);
        if (hand != null) {
            player.setItemSlot(EquipmentSlot.MAINHAND, hand);
        }
        SpectateServer.refreshSoon();
    }

    private static ItemStack goggles(@Nullable QuadcopterEntity drone) {
        ItemStack goggles = ContentIds.item(ContentIds.GOGGLES_CREATIVE).map(ItemStack::new).orElse(ItemStack.EMPTY);
        if (goggles.isEmpty()) {
            throw new IllegalStateException("Kreativ-Brille nicht registriert");
        }
        if (drone != null) {
            goggles.set(CoreComponents.BOUND_DRONE.get(), DroneInteractions.binding(drone));
        }
        return goggles;
    }

    private static void rememberItems(ServerPlayer player) {
        HEAD.compareAndSet(null, player.getItemBySlot(EquipmentSlot.HEAD).copy());
        HAND.compareAndSet(null, player.getMainHandItem().copy());
    }

    private static StepResult watchedDrone(SelfTest.StepContext ctx, Run run) {
        Minecraft mc = Minecraft.getInstance();
        if (run.future == null) {
            run.future = ctx.onServer(server -> {
                ServerPlayer player = FreecamSelfTests.player(server);
                discard(server, WATCHED_ID);
                ServerLevel level = player.level();
                QuadcopterEntity drone = DroneModule.DRONE.get().create(level, EntitySpawnReason.COMMAND);
                if (drone == null) {
                    throw new IllegalStateException("Drohne nicht erzeugt");
                }
                Vec3 eye = player.getEyePosition();
                drone.setBuild(DroneBuild.FREESTYLE);
                drone.setNoGravity(true);
                drone.snapTo(eye.x - 3.0, eye.y + 4.0, eye.z - 3.0, 180.0F, 0.0F);
                if (!level.addFreshEntity(drone)) {
                    throw new IllegalStateException("addFreshEntity fehlgeschlagen");
                }
                drone.setAttitude(new Quaternionf());
                WATCHED_ID.set(drone.getId());
                ctx.state().put(WATCHED, drone.getUUID());
                rememberItems(player);
                player.setItemSlot(EquipmentSlot.HEAD, goggles(null));
                SpectateServer.simulateTransmitter(drone, PILOT_NAME);
                SpectateServer.refreshSoon();
            });
            return StepResult.pending();
        }
        if (run.future.isCompletedExceptionally()) {
            return StepResult.fail("Aufbau: " + run.future.exceptionNow());
        }
        ChannelListS2C.Entry entry = ctx.state().get(WATCHED) instanceof UUID id ? SpectateSession.INSTANCE.entry(id) : null;
        return FreecamSelfTests.until(run.future.isDone() && entry != null && SpectateSession.goggles(mc) != null, "Kanal oder Brille fehlt");
    }

    private static StepResult pressF7(SelfTest.StepContext ctx, Run run) {
        Minecraft mc = Minecraft.getInstance();
        SpectateSession spectate = SpectateSession.INSTANCE;
        if (run.tick < 0) {
            if (!FreecamSession.active()) {
                return StepResult.fail("Freecam läuft nicht");
            }
            FreecamSelfTests.clickKey(SpectateKeys.CYCLE);
            run.tick = ctx.tick();
            return StepResult.pending();
        }
        int since = ctx.tick() - run.tick;
        if (FreecamSession.active()) {
            return since > 5 ? StepResult.fail("F7 beendet die Freecam nicht (Zuschauen " + spectate.phase() + ", " + spectate.lastResult() + ")")
                    : StepResult.pending();
        }
        if (run.count == 0) {
            run.count = since;
            if (FreecamSession.INSTANCE.lastEnd() != FreecamRules.End.SPECTATING) {
                return StepResult.fail("Ende mit " + FreecamSession.INSTANCE.lastEnd() + " statt SPECTATING");
            }
        }
        if (!spectate.active()) {
            return StepResult.fail("Zuschauen läuft nach F7 nicht: " + spectate.lastResult());
        }
        if (!spectate.watching() || spectate.drone() == null) {
            return CovFreecamSelfTests.pending("Zuschauen startet nicht: " + spectate.phase());
        }
        if (!(ctx.state().get(WATCHED) instanceof UUID id) || !id.equals(spectate.target())) {
            return StepResult.fail("schaut der falschen Drohne zu: " + spectate.target());
        }
        run.notes.add("F7: Freecam aus nach " + run.count + " Tick(s) mit Grund SPECTATING, Zuschauen nach " + since + " Ticks");
        return StepResult.pass();
    }

    private static StepResult stopWatching(SelfTest.StepContext ctx, Run run) {
        Minecraft mc = Minecraft.getInstance();
        if (run.waited(ctx) == 0) {
            SpectateSession.INSTANCE.stop(mc, true);
            return StepResult.pending();
        }
        return FreecamSelfTests.until(!SpectateSession.INSTANCE.active() && mc.getCameraEntity() == mc.player, "Zuschauen endet nicht");
    }

    private static StepResult realFlight(SelfTest.StepContext ctx, Run run) {
        if (run.future == null) {
            run.future = ctx.onServer(server -> {
                ServerPlayer player = FreecamSelfTests.player(server);
                discard(server, WATCHED_ID);
                DronePiloting.pilotedBy(player.getUUID()).ifPresent(DronePiloting::release);
                ServerLevel level = player.level();
                QuadcopterEntity drone = DroneModule.DRONE.get().create(level, EntitySpawnReason.COMMAND);
                if (drone == null) {
                    throw new IllegalStateException("Drohne nicht erzeugt");
                }
                Vec3 feet = player.position();
                drone.snapTo(feet.x + 2.0, feet.y + 0.1, feet.z - 3.0, 180.0F, 0.0F);
                drone.setAttitude(new Quaternionf());
                drone.setBuild(DroneBuild.FREESTYLE);
                drone.setBatteryCharge(1.0F);
                if (!level.addFreshEntity(drone)) {
                    throw new IllegalStateException("addFreshEntity fehlgeschlagen");
                }
                FLOWN_ID.set(drone.getId());
                rememberItems(player);
                player.setItemSlot(EquipmentSlot.HEAD, goggles(drone));
                ItemStack remote = new ItemStack(DroneModule.REMOTE.get());
                remote.set(CoreComponents.BOUND_DRONE.get(), DroneInteractions.binding(drone));
                player.setItemSlot(EquipmentSlot.MAINHAND, remote);
                DronePiloting.StartResult result = DronePiloting.tryStart(player, drone, PilotMode.FPV);
                if (result != DronePiloting.StartResult.STARTED) {
                    throw new IllegalStateException("FPV-Start verweigert: " + result);
                }
            });
            return StepResult.pending();
        }
        if (run.future.isCompletedExceptionally()) {
            return StepResult.fail("Flugstart: " + run.future.exceptionNow());
        }
        PilotSession session = PilotSession.INSTANCE;
        return FreecamSelfTests.until(run.future.isDone() && session.isFpv() && session.sim() != null && run.waited(ctx) > 10, "FPV-Sitzung startet nicht");
    }

    private static StepResult f6WhilePiloting(SelfTest.StepContext ctx, Run run) {
        Minecraft mc = Minecraft.getInstance();
        if (run.waited(ctx) == 0) {
            if (mc.gui.screen() != null) {
                mc.gui.setScreen(null);
            }
            run.count = FreecamSession.INSTANCE.starts();
            FreecamSelfTests.clickKey(FreecamClient.key());
            return StepResult.pending();
        }
        if (run.waited(ctx) < 4) {
            return StepResult.pending();
        }
        if (FreecamSession.active() || FreecamSession.INSTANCE.starts() != run.count) {
            return StepResult.fail("F6 startet die Freecam im echten Flug");
        }
        if (FreecamSession.INSTANCE.lastDenial() != FreecamRules.Denial.PILOTING) {
            return StepResult.fail("Grund " + FreecamSession.INSTANCE.lastDenial() + " statt PILOTING");
        }
        if (!PilotSession.INSTANCE.isFpv()) {
            return StepResult.fail("echter Flug endet durch F6");
        }
        String message = Component.translatable(FreecamRules.Denial.PILOTING.translationKey()).getString();
        run.notes.add("F6 im echten Flug verweigert: „" + message + "“, Flug läuft weiter");
        return StepResult.pass();
    }

    private static StepResult endFlight(SelfTest.StepContext ctx, Run run) {
        if (run.future == null) {
            run.future = ctx.onServer(CovFreecamIsolationSelfTests::releaseDrones);
            return StepResult.pending();
        }
        return FreecamSelfTests.until(run.future.isDone() && !PilotSession.INSTANCE.isActive() && !SpectateSession.INSTANCE.active(), "Flug endet nicht");
    }

    private static void placeGates(MinecraftServer server) {
        removeCourse(server);
        ServerPlayer player = FreecamSelfTests.player(server);
        ServerLevel level = player.level();
        CourseRegistry registry = CourseRegistry.get();
        if (registry == null) {
            throw new IllegalStateException("CourseRegistry läuft nicht");
        }
        BlockPos site = FreecamSelfTests.site();
        ItemStack previous = player.getMainHandItem().copy();
        float yaw = player.getYRot();
        player.setYRot(0.0F);
        player.setShiftKeyDown(false);
        ItemStack gates = new ItemStack(RaceModule.GATE_MEDIUM.get(), GATE_COUNT);
        player.setItemInHand(InteractionHand.MAIN_HAND, gates);
        int x = site.getX() + GATE_DX;
        int z0 = site.getZ() - SPACING;
        for (int dx = -3; dx <= 3; dx++) {
            for (int z = z0 - 6; z <= z0 + (GATE_COUNT - 1) * SPACING + 6; z++) {
                level.getChunk((x + dx) >> 4, z >> 4);
                place(level, new BlockPos(x + dx, site.getY() - 1, z), Blocks.STONE.defaultBlockState());
                for (int dy = 0; dy <= 8; dy++) {
                    place(level, new BlockPos(x + dx, site.getY() + dy, z), Blocks.AIR.defaultBlockState());
                }
            }
        }
        List<GateEntity> placed = new ArrayList<>();
        try {
            for (int i = 0; i < GATE_COUNT; i++) {
                int z = z0 + i * SPACING;
                level.getChunk(x >> 4, z >> 4);
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
            player.setYRot(yaw);
        }
        ItemStack tool = new ItemStack(RaceModule.COURSE_TOOL.get());
        for (GateEntity gate : placed) {
            CourseToolItem.useOnGate(tool, player, gate);
        }
        int course = CourseToolItem.courseOf(tool);
        CourseRegistry.Course data = registry.course(course);
        if (data == null || data.gates.size() != GATE_COUNT) {
            throw new IllegalStateException("Kurs " + course + " hat nicht " + GATE_COUNT + " Gates");
        }
        for (GateEntity gate : placed) {
            GATES.add(gate.getId());
        }
        COURSE.set(course);
    }

    static void removeCourse(MinecraftServer server) {
        for (ServerLevel level : server.getAllLevels()) {
            for (int id : GATES) {
                Entity gate = level.getEntity(id);
                if (gate != null) {
                    gate.discard();
                }
            }
        }
        GATES.clear();
        int course = COURSE.getAndSet(-1);
        CourseRegistry registry = CourseRegistry.get();
        if (course >= 0 && registry != null) {
            registry.deleteCourse(course);
        }
        if (!PLACED.isEmpty() && !server.getPlayerList().getPlayers().isEmpty()) {
            ServerLevel level = FreecamSelfTests.player(server).level();
            for (int i = PLACED.size() - 1; i >= 0; i--) {
                Placed placed = PLACED.get(i);
                level.setBlockAndUpdate(placed.pos(), placed.state());
            }
        }
        PLACED.clear();
    }

    private static void place(ServerLevel level, BlockPos pos, BlockState state) {
        BlockState before = level.getBlockState(pos);
        if (before.equals(state)) {
            return;
        }
        PLACED.add(new Placed(pos.immutable(), before));
        level.setBlockAndUpdate(pos, state);
    }

    private static @Nullable CourseData course() {
        int id = COURSE.get();
        return id < 0 ? null : ClientCourses.get(id);
    }

    private static StepResult buildCourse(SelfTest.StepContext ctx, Run run) {
        if (run.future == null) {
            run.future = ctx.onServer(CovFreecamIsolationSelfTests::placeGates);
            return StepResult.pending();
        }
        if (run.future.isCompletedExceptionally()) {
            return StepResult.fail("Kurs: " + run.future.exceptionNow());
        }
        CourseData data = course();
        boolean synced = run.future.isDone() && data != null && data.gates().size() == GATE_COUNT;
        return FreecamSelfTests.until(synced, "Kurs kommt nicht beim Client an");
    }

    private static List<GateGeometry> gates() {
        CourseData data = course();
        return data == null ? List.of() : ClientCourses.geometry(data);
    }

    private static StepResult beforeGates(SelfTest.StepContext ctx, Run run) {
        if (!FreecamSession.active()) {
            return StepResult.fail("Freecam aus");
        }
        List<GateGeometry> gates = gates();
        if (gates.size() != GATE_COUNT) {
            return StepResult.fail("Kurs fehlt auf dem Client");
        }
        GateGeometry first = gates.getFirst();
        if (run.waited(ctx) == 0) {
            EVENTS.clear();
            ClientRaceTracker.INSTANCE.setEventListener(EVENTS::add);
            TrickTracker.INSTANCE.session().keepHistory(16);
        }
        if (!FreecamSelfTests.armAt(run, first.x(), first.centerY(), first.z() - 6.0, 0.0F)) {
            return FreecamSelfTests.notArmed(run);
        }
        FreecamSelfTests.arm(FreecamSelfTests.hover(), StabMode.ANGLE);
        ctx.state().put("cov_counters", Counters.now());
        return StepResult.pass();
    }

    private static StepResult throughGates(SelfTest.StepContext ctx, Run run) {
        if (!FreecamSession.active() || !FreecamSession.INSTANCE.armed()) {
            return StepResult.fail("Freecam aus oder entschärft");
        }
        List<GateGeometry> gates = gates();
        GateGeometry first = gates.getFirst();
        GateGeometry last = gates.getLast();
        double start = first.z() - 6.0;
        double end = last.z() + 6.0;
        Vector3d position = CovFreecamSelfTests.position();
        CovGatePasses passes = (CovGatePasses) ctx.state().computeIfAbsent(GATE_PASSES, key -> CovGatePasses.of(gates, 0.5));
        boolean back = run.flag;
        double z = back ? Math.max(start, position.z - 0.3) : Math.min(end, position.z + 0.3);
        double y = heightAt(gates, z);
        passes.tick(position.x, position.y, position.z, first.x(), y, z, back);
        run.count = passes.count();
        FreecamSelfTests.arm(FreecamSelfTests.hover(), StabMode.ANGLE);
        FreecamSession.INSTANCE.testPlace(first.x(), y, z, back ? 180.0F : 0.0F);
        if (!back && z >= end) {
            run.flag = true;
            return StepResult.pending();
        }
        if (back && z <= start) {
            if (run.count < passes.expected()) {
                return StepResult.fail("nur " + run.count + " von " + passes.expected() + " Gate-Durchflügen, Abdrift zwischen zwei Ticks bis "
                        + String.format(Locale.ROOT, "%.6f", passes.drift()) + " Blöcke");
            }
            run.notes.add(run.count + " Durchflüge durch die Gate-Öffnungen mit 6 m/s, Abdrift zwischen zwei Ticks bis "
                    + String.format(Locale.ROOT, "%.6f", passes.drift()) + " Blöcke");
            return StepResult.pass();
        }
        return StepResult.pending();
    }

    private static double heightAt(List<GateGeometry> gates, double z) {
        GateGeometry previous = gates.getFirst();
        if (z <= previous.z()) {
            return previous.centerY();
        }
        for (GateGeometry gate : gates) {
            if (z <= gate.z()) {
                double share = (z - previous.z()) / Math.max(1.0E-6, gate.z() - previous.z());
                return previous.centerY() + (gate.centerY() - previous.centerY()) * share;
            }
            previous = gate;
        }
        return gates.getLast().centerY();
    }

    private static StepResult flip(SelfTest.StepContext ctx, Run run) {
        if (!FreecamSession.active()) {
            return StepResult.fail("Freecam aus");
        }
        BlockPos site = FreecamSelfTests.site();
        if (run.waited(ctx) == 0) {
            FreecamSession.INSTANCE.testPlace(site.getX() + 2.5, site.getY() + 30.0, site.getZ() + 0.5, -90.0F);
            FreecamSession.INSTANCE.setTestInput(new StickPositions(0.0F, 0.0F, 0.0F, FreecamSelfTests.hover()), true, StabMode.ACRO);
            return StepResult.pending();
        }
        Quaternionf body = FreecamSession.INSTANCE.orientation(new Quaternionf());
        float up = body.transform(new Vector3f(0.0F, 1.0F, 0.0F)).y;
        if (!run.flag) {
            FreecamSession.INSTANCE.setTestInput(new StickPositions(1.0F, 0.0F, 0.0F, 0.6F), true, StabMode.ACRO);
            if (up < -0.5F) {
                run.flag = true;
            }
            return run.waited(ctx) > 200 ? StepResult.fail("Flip dreht nicht über Kopf") : StepResult.pending();
        }
        if (up < 0.9F) {
            FreecamSession.INSTANCE.setTestInput(new StickPositions(1.0F, 0.0F, 0.0F, 0.6F), true, StabMode.ACRO);
            return run.waited(ctx) > 250 ? StepResult.fail("Flip kommt nicht zurück") : StepResult.pending();
        }
        FreecamSession.INSTANCE.setTestInput(new StickPositions(0.0F, 0.0F, 0.0F, FreecamSelfTests.hover()), true, StabMode.ANGLE);
        run.notes.add("Flip über die Rollachse in " + run.waited(ctx) + " Ticks");
        return StepResult.pass();
    }

    private static StepResult nothingCounted(SelfTest.StepContext ctx, Run run) {
        if (run.waited(ctx) < 20) {
            return StepResult.pending();
        }
        Counters before = (Counters) ctx.state().get("cov_counters");
        Counters after = Counters.now();
        ClientRaceTracker.INSTANCE.setEventListener(null);
        TrickTracker.INSTANCE.session().keepHistory(0);
        List<String> problems = new ArrayList<>();
        if (!EVENTS.isEmpty()) {
            problems.add("Renn-Ereignisse " + EVENTS.stream().map(e -> e.type().name()).toList());
        }
        if (after.racing() || RaceState.INSTANCE.racing()) {
            problems.add("Rennen läuft");
        }
        if (TrickTracker.INSTANCE.active() || after.trickFrames() != before.trickFrames() || after.tricks() != before.tricks()
                || after.history() != before.history()) {
            problems.add("Trick-Erkennung lief: Frames " + before.trickFrames() + " → " + after.trickFrames() + ", Tricks " + before.tricks() + " → "
                    + after.tricks());
        }
        if (after.trails() > before.trails()) {
            problems.add("Trails " + before.trails() + " → " + after.trails());
        }
        if (after.motors() > before.motors() || after.synth() > before.synth() || after.samples() > before.samples()) {
            problems.add("Motorton: Drohnen " + before.motors() + " → " + after.motors() + ", Stimmen " + before.synth() + "/" + before.samples() + " → "
                    + after.synth() + "/" + after.samples());
        }
        EVENTS.clear();
        if (!problems.isEmpty()) {
            return StepResult.fail(String.join("; ", problems));
        }
        run.notes.add("keine Renn-Ereignisse, kein Trick, Trails " + after.trails() + ", Motorstimmen " + after.synth() + "/" + after.samples());
        return StepResult.pass();
    }

    private static StepResult recordKeys(SelfTest.StepContext ctx, Run run) {
        Minecraft mc = Minecraft.getInstance();
        if (!FreecamSession.active()) {
            return StepResult.fail("Freecam endet: " + FreecamSession.INSTANCE.lastEnd());
        }
        Path dir = BlackboxSession.directory(mc);
        int t = run.waited(ctx);
        if (t == 0) {
            ctx.state().put("cov_blackbox", Folder.of(dir));
            BlackboxRecorder recorder = BlackboxSession.INSTANCE.recorderIfCreated();
            run.flag = recorder != null && recorder.logging();
            FreecamSelfTests.clickKey(RecordingKeys.RECORD);
            FreecamSelfTests.clickKey(BlackboxKeys.TOGGLE);
            return StepResult.pending();
        }
        if (t == 9 && !CovFreecamSelfTests.stall()) {
            return StepResult.fail("Ruckler unterbrochen");
        }
        BlockPos site = FreecamSelfTests.site();
        if (t >= 10 && FreecamSelfTests.armAt(run, site.getX() + 2.5, site.getY() + 10.0, site.getZ() + 0.5, -90.0F)) {
            FreecamSelfTests.arm(FreecamSelfTests.hover(), StabMode.ANGLE);
        }
        if (t == 40) {
            FreecamSelfTests.clickKey(BlackboxKeys.TOGGLE);
        }
        BlackboxRecorder recorder = BlackboxSession.INSTANCE.recorderIfCreated();
        List<String> problems = new ArrayList<>();
        if (ActionCamRecorder.INSTANCE.recording() || ActionCamRecorder.INSTANCE.phase() != ActionCamRecorder.Phase.IDLE) {
            problems.add("Action-Cam " + ActionCamRecorder.INSTANCE.phase());
        }
        if (FlashbackCam.INSTANCE.recording()) {
            problems.add("Flashback nimmt auf");
        }
        if (!run.flag && recorder != null && recorder.logging()) {
            problems.add("Blackbox zeichnet auf");
        }
        if (!problems.isEmpty()) {
            return StepResult.fail("nach " + t + " Ticks: " + String.join(", ", problems));
        }
        if (t < 80) {
            return StepResult.pending();
        }
        if (!FreecamSession.INSTANCE.armed()) {
            return FreecamSelfTests.notArmed(run);
        }
        Folder before = (Folder) ctx.state().get("cov_blackbox");
        Folder after = Folder.of(dir);
        if (!before.equals(after)) {
            return StepResult.fail("Blackbox-Ordner ändert sich: " + before.sizes() + " → " + after.sizes());
        }
        run.notes.add("F9 und Ziffernblock 7: keine Aufnahme, Blackbox-Ordner unverändert (" + after.sizes().size() + " Dateien), auch scharf im Flug");
        return StepResult.pass();
    }

    private static @Nullable SimulatedRadio rxRadio(SelfTest.StepContext ctx) {
        return (SimulatedRadio) ctx.state().get("cov_rx_radio");
    }

    private static int power(BlockPos pos) {
        Minecraft mc = Minecraft.getInstance();
        BlockState state = mc.level == null ? Blocks.AIR.defaultBlockState() : mc.level.getBlockState(pos);
        return state.getBlock() instanceof RxBlock ? state.getValue(RxBlock.POWER) : -1;
    }

    private static StepResult placeRx(SelfTest.StepContext ctx, Run run) {
        if (run.future == null) {
            run.future = ctx.onServer(server -> {
                ServerPlayer player = FreecamSelfTests.player(server);
                ServerLevel level = player.level();
                BlockPos pos = FreecamSelfTests.site().offset(RX_DX, 0, RX_DZ);
                level.setBlock(pos.below(), Blocks.STONE.defaultBlockState(), Block.UPDATE_ALL);
                level.setBlock(pos, RxModule.RX_BLOCK.block().get().defaultBlockState(), Block.UPDATE_ALL);
                if (!(level.getBlockEntity(pos) instanceof RxBlockEntity entity)) {
                    throw new IllegalStateException("RX-Block ohne Block-Entity");
                }
                entity.apply(new RxSettings(player.getUUID(), RxSource.GAS, -1.0F, 1.0F, 64));
                RX.set(pos);
            });
            return StepResult.pending();
        }
        if (run.future.isCompletedExceptionally()) {
            return StepResult.fail("RX-Block: " + run.future.exceptionNow());
        }
        BlockPos pos = RX.get();
        return FreecamSelfTests.until(run.future.isDone() && pos != null && power(pos) >= 0 && RxClient.blockEntity(pos) != null, "RX-Block fehlt am Client");
    }

    static void removeRx(MinecraftServer server) {
        BlockPos pos = RX.getAndSet(null);
        if (pos != null && !server.getPlayerList().getPlayers().isEmpty()) {
            FreecamSelfTests.player(server).level().setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
        }
    }

    private static StepResult rxRadioIdle(SelfTest.StepContext ctx, Run run) {
        BlockPos pos = RX.get();
        if (pos == null) {
            return StepResult.fail("RX-Block fehlt");
        }
        if (run.waited(ctx) == 0) {
            SimulatedRadio radio = CovFreecamSelfTests.radio();
            ctx.state().put("cov_rx_radio", radio);
            TransmitterInput.INSTANCE.simulate(radio);
            return StepResult.pending();
        }
        boolean demanded = !RxClient.demand().empty();
        return FreecamSelfTests.until(demanded && power(pos) == 0 && run.waited(ctx) > 20, "Block meldet keinen Bedarf oder Stärke " + power(pos));
    }

    private static StepResult rxFollows(SelfTest.StepContext ctx, Run run, int micros, int expected) {
        BlockPos pos = RX.get();
        SimulatedRadio radio = rxRadio(ctx);
        if (pos == null || radio == null) {
            return StepResult.fail("RX-Block oder Test-Funke fehlt");
        }
        if (!FreecamSession.active()) {
            return StepResult.fail("Freecam aus: " + FreecamSession.INSTANCE.lastEnd());
        }
        if (run.waited(ctx) == 0) {
            TransmitterInput.INSTANCE.simulate(radio);
            radio.setMicros(CovFreecamSelfTests.THROTTLE_AXIS, micros);
            run.count = RxClient.sentCount();
            return StepResult.pending();
        }
        int power = power(pos);
        boolean ok = expected >= 0 ? power == expected : power >= 6 && power <= 9;
        if (!ok) {
            return run.waited(ctx) > 200 ? StepResult.fail("Block folgt der Test-Funke nicht: Stärke " + power) : CovFreecamSelfTests.pending("Stärke " + power);
        }
        float throttle = FreecamSession.INSTANCE.sticks().throttle();
        float wanted = (micros - 1000) / 1000.0F;
        if (Math.abs(throttle - wanted) > 0.1F) {
            return CovFreecamSelfTests.pending(String.format(Locale.ROOT, "Freecam-Gas %.2f statt %.2f", throttle, wanted));
        }
        ctx.state().put("cov_rx_power", power);
        run.notes.add(String.format(Locale.ROOT, "Test-Funke %d µs: Block Stärke %d nach %d Ticks, Freecam-Gas %.2f, %d RX-Pakete", micros, power,
                run.waited(ctx), throttle, RxClient.sentCount() - run.count));
        return StepResult.pass();
    }

    private static StepResult rxKeyboard(SelfTest.StepContext ctx, Run run) {
        if (run.waited(ctx) == 0) {
            TransmitterInput.INSTANCE.simulate(null);
            TransmitterInput.INSTANCE.setSuppressDevice(true);
            de.lardtom.propwash.input.InputConfig.get().keyboardFallback = true;
            return StepResult.pending();
        }
        return FreecamSelfTests.until(RadioState.INSTANCE.source() == RadioSource.KEYBOARD, "Quelle " + RadioState.INSTANCE.source());
    }

    private static StepResult rxKeysIgnored(SelfTest.StepContext ctx, Run run) {
        Minecraft mc = Minecraft.getInstance();
        BlockPos pos = RX.get();
        if (pos == null || !FreecamSession.active()) {
            return StepResult.fail("RX-Block fehlt oder Freecam aus");
        }
        int held = (int) ctx.state().getOrDefault("cov_rx_power", -2);
        int t = run.waited(ctx);
        if (t == 0) {
            run.count = RxClient.sentCount();
            mc.options.keyUp.setDown(true);
        }
        if (t == 40) {
            mc.options.keyUp.setDown(false);
            run.value = FreecamSession.INSTANCE.sticks().throttle();
            mc.options.keyDown.setDown(true);
        }
        if (t == 70) {
            mc.options.keyDown.setDown(false);
        }
        int power = power(pos);
        if (power != held) {
            mc.options.keyUp.setDown(false);
            mc.options.keyDown.setDown(false);
            return StepResult.fail("Block folgt der Tastatur in der Freecam: Stärke " + held + " → " + power + " nach " + t + " Ticks");
        }
        if (t < 100) {
            return StepResult.pending();
        }
        if (run.value < 0.2) {
            return StepResult.fail(String.format(Locale.ROOT, "W gibt der Freecam kein Gas (%.2f), Test ohne Aussage", run.value));
        }
        int sent = RxClient.sentCount() - run.count;
        if (sent != 0) {
            return StepResult.fail(sent + " RX-Pakete mit Tastatur in der Freecam");
        }
        if (InputKeys.down(InputKeys.THROTTLE_UP)) {
            return StepResult.fail("W bleibt gedrückt");
        }
        run.notes.add(String.format(Locale.ROOT, "Tastatur in der Freecam: W gab der Freecam Gas %.2f, Block hält Stärke %d, 0 RX-Pakete", run.value, power));
        return StepResult.pass();
    }
}
