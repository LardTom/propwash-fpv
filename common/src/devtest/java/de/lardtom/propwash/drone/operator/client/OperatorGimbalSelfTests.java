package de.lardtom.propwash.drone.operator.client;

import de.lardtom.propwash.api.client.PropwashClientApi;
import de.lardtom.propwash.api.drone.AutonomyResult;
import de.lardtom.propwash.apiimpl.client.RecordingControlImpl;
import de.lardtom.propwash.build.AccessoryType;
import de.lardtom.propwash.build.DroneBuild;
import de.lardtom.propwash.content.ContentIds;
import de.lardtom.propwash.core.CoreComponents;
import de.lardtom.propwash.dev.JoinTestEnv;
import de.lardtom.propwash.dev.SelfTest;
import de.lardtom.propwash.dev.SelfTest.StepResult;
import de.lardtom.propwash.dev.client.Screenshots;
import de.lardtom.propwash.dev.example.ExampleFollow;
import de.lardtom.propwash.dev.example.ExampleOperator;
import de.lardtom.propwash.dev.example.client.ExampleOperatorClient;
import de.lardtom.propwash.drone.DroneInteractions;
import de.lardtom.propwash.drone.DroneModule;
import de.lardtom.propwash.drone.DronePiloting;
import de.lardtom.propwash.drone.PilotMode;
import de.lardtom.propwash.drone.QuadcopterEntity;
import de.lardtom.propwash.drone.client.FpvView;
import de.lardtom.propwash.drone.client.PilotSession;
import de.lardtom.propwash.drone.client.render.DroneRenderConfig;
import de.lardtom.propwash.drone.client.render.FpvProps;
import de.lardtom.propwash.drone.operator.OperatorServer;
import de.lardtom.propwash.drone.operator.net.OperatorRoleS2C;
import de.lardtom.propwash.input.StickPositions;
import de.lardtom.propwash.rescue.AddonAutonomy;
import de.lardtom.propwash.rescue.client.AutonomyLabels;
import de.lardtom.propwash.sim.DroneSim;
import de.lardtom.propwash.sim.SimSnapshot;
import de.lardtom.propwash.sim.StabMode;
import de.lardtom.propwash.video.client.SpectateSession;
import de.lardtom.propwash.video.client.VideoState;
import de.lardtom.propwash.video.client.record.ActionCamRecorder;
import de.lardtom.propwash.video.client.record.FlashbackCam;
import de.lardtom.propwash.video.record.CameraLook;
import de.lardtom.propwash.video.record.Stabilizer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.network.protocol.game.ClientboundSetHeldSlotPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaterniond;
import org.joml.Quaternionf;
import org.joml.Quaternionfc;
import org.jspecify.annotations.Nullable;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

public final class OperatorGimbalSelfTests {
    public static final String GROUP = "addon-api-17: B9";
    private static final int ARENA_DX = 18;
    private static final int HALF = 6;
    private static final int HEIGHT = 12;
    private static final double AIR = 3.0;
    private static final double ROLL = 30.0;
    private static final double MAX_IMAGE_ROLL = 2.0;
    private static final double FOV = 40.0;
    private static final double TAKEOFF = 4.0;
    private static final int SETTLE = 20;
    private static final int VIEW_END = 40;
    private static final int PROPS_TICKS = 20;
    private static final double MAX_OFFSET = 1.0;
    private static final double MIN_FPV_ROLL = 25.0;
    private static final Vec3 HEAD_VIEW = new Vec3(0.55, 0.02, -0.35);
    private static final String PHASE = "phase";
    private static final String SINCE = "since";
    private static final String FUTURE = "future";
    private static final String CONFIG = "config";
    private static final String MARK = "mark";
    private static final String FPV_COUNT = "fpvCount";
    private static final String OPERATOR_INPUT = "propwash.test.operatorInput";
    private static final String OPERATOR_VALUES = "1,0,0,0.5,1,4";
    private static final String OPERATOR_INVALID = "1,0,0";
    private static final byte[] PNG = {(byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1A, '\n'};
    private static final AtomicReference<int @Nullable []> ARENA = new AtomicReference<>();
    private static final AtomicInteger FPV = new AtomicInteger(-1);
    private static final AtomicInteger AUTO = new AtomicInteger(-1);
    private static final AtomicInteger PARKED = new AtomicInteger(-1);
    private static final AtomicReference<String> RESULT = new AtomicReference<>();
    private static final AtomicReference<String> INFO = new AtomicReference<>("");
    private static final AtomicReference<GameType> MODE = new AtomicReference<>();
    private static final AtomicReference<@Nullable String> PREVIOUS_INPUT = new AtomicReference<>();
    private static final AtomicBoolean INPUT_SET = new AtomicBoolean();

    private OperatorGimbalSelfTests() {
    }

    public static void register() {
        SelfTest.clientStep(GROUP + " – Testfeld, Drohne mit Gimbal und Action-Cam vorn, FPV-Sitzung", 500, OperatorGimbalSelfTests::setupStep);
        SelfTest.clientStep(GROUP + " – Action-Cam vorn am Gimbal nimmt horizontstabil auf (Rollwinkel im Bild < 2° bei 30° Rollen)", 900,
                OperatorGimbalSelfTests::rollStep);
        SelfTest.clientStep(GROUP + " – fovDegrees 40° verengt das FPV-Bild", 300, OperatorGimbalSelfTests::fovStep);
        SelfTest.clientStep(GROUP + " – recording().photo legt ein PNG an", 400, OperatorGimbalSelfTests::photoStep);
        SelfTest.clientStep(GROUP + " – Gimbal-Bild ohne Props: Pilot sieht im FPV-Bild Props, im Gimbal-Bild derselben Drohne keine", 400,
                ctx -> propsStep(ctx, false));
        SelfTest.clientStep(GROUP + " – Flashback-Kamerapfad folgt der Gimbal-Kamera auch im FPV-Bild (Rollwinkel < 2° bei 30° Rollen)", 400,
                OperatorGimbalSelfTests::pathStep);
        SelfTest.clientStep(GROUP + " – Flashback-Kamerapfad nimmt die Brennweite der Gimbal-Kamera auch im FPV-Bild (40°, Schirm mit Sichtfeld des Baus)",
                300, OperatorGimbalSelfTests::pathFovStep);
        SelfTest.clientStep(GROUP + " – spectate zeigt das Bild der eigenen Drohne im autonomen Halten", 1200, OperatorGimbalSelfTests::spectateStep);
        SelfTest.clientStep(GROUP + " – Gimbal-Bild ohne Props: Zuschauer sieht im FPV-Bild Props, im Gimbal-Bild keine", 400,
                ctx -> propsStep(ctx, true));
        SelfTest.clientStep(GROUP + " – Operator am Gimbal ohne Funke: Achsen und Aktionen aus " + OPERATOR_INPUT
                        + " drehen den Gimbal, ein ungültiger Wert wirkt nicht, Esc gibt die Kamera ab", 1200,
                OperatorGimbalSelfTests::operatorStep);
        SelfTest.clientStep(GROUP + " – Operator-Ansicht endet sofort, wenn die Drohne vor der Rollen-Meldung verschwindet", 600,
                ctx -> vanishStep(ctx, false));
        SelfTest.clientStep(GROUP + " – Operator-Ansicht endet sofort, wenn die Rollen-Meldung im selben Tick wie das Entfernen der Drohne ankommt", 900,
                ctx -> vanishStep(ctx, true));
        if (Screenshots.enabled()) {
            SelfTest.clientStep(GROUP + " – Fenster für das Bildschirmfoto", 200, Screenshots::resizeWindow);
        }
        SelfTest.clientStep(GROUP + " – Gimbal-Kopf des Beispiel-Zubehörs dreht sich in Drittperson (Foto gimbal_head)", 900,
                OperatorGimbalSelfTests::headStep);
        if (Screenshots.enabled()) {
            SelfTest.clientStep(GROUP + " – Fenster zurück", 100, Screenshots::restoreWindow);
        }
        SelfTest.clientStep(GROUP + " – Testfeld abbauen", 400, OperatorGimbalSelfTests::cleanupStep);
    }

    static boolean ready() {
        return JoinTestEnv.exampleAddon() && AccessoryType.byId(ExampleOperator.GIMBAL_FRONT).isPresent();
    }

    static DroneBuild gimbalBuild() {
        return DroneBuild.FREESTYLE.withAccessory(AccessoryType.byId(ExampleOperator.GIMBAL_FRONT)
                .orElseThrow(() -> new IllegalStateException("Beispiel-Gimbal fehlt")));
    }

    private static int phase(SelfTest.StepContext ctx) {
        return (int) ctx.state().getOrDefault(PHASE, 0);
    }

    private static void next(SelfTest.StepContext ctx, int phase) {
        ctx.state().put(PHASE, phase);
        ctx.state().put(SINCE, ctx.tick());
        ctx.state().remove(FUTURE);
    }

    private static int since(SelfTest.StepContext ctx) {
        return ctx.tick() - (int) ctx.state().getOrDefault(SINCE, 0);
    }

    private static @Nullable StepResult server(SelfTest.StepContext ctx, String what, Consumer<MinecraftServer> action) {
        CompletableFuture<?> future = (CompletableFuture<?>) ctx.state().get(FUTURE);
        if (future == null) {
            RESULT.set(null);
            ctx.state().put(FUTURE, ctx.onServer(action));
            return StepResult.pending();
        }
        if (future.isCompletedExceptionally()) {
            return StepResult.fail(what + ": " + future.exceptionNow().getMessage());
        }
        if (!future.isDone()) {
            return StepResult.pending();
        }
        String result = RESULT.getAndSet(null);
        return result == null ? null : StepResult.fail(what + ": " + result);
    }

    private static ServerPlayer player(MinecraftServer server) {
        if (server.getPlayerList().getPlayers().isEmpty()) {
            throw new IllegalStateException("kein Spieler auf dem Server");
        }
        return server.getPlayerList().getPlayers().getFirst();
    }

    private static @Nullable QuadcopterEntity clientDrone(AtomicInteger which) {
        Minecraft mc = Minecraft.getInstance();
        return mc.level != null && mc.level.getEntity(which.get()) instanceof QuadcopterEntity drone && !drone.isRemoved() ? drone : null;
    }

    private static QuadcopterEntity serverDrone(MinecraftServer server, AtomicInteger which) {
        if (!(player(server).level().getEntity(which.get()) instanceof QuadcopterEntity drone) || drone.isRemoved()) {
            throw new IllegalStateException("Testdrohne fehlt");
        }
        return drone;
    }

    private static QuadcopterEntity spawn(ServerLevel level, Vec3 pos, boolean hover) {
        QuadcopterEntity drone = DroneModule.DRONE.get().create(level, EntitySpawnReason.COMMAND);
        if (drone == null) {
            throw new IllegalStateException("Drohne nicht erzeugt");
        }
        ItemStack stack = new ItemStack(DroneModule.DRONE_ITEM.get());
        stack.set(CoreComponents.DRONE_BUILD.get(), gimbalBuild());
        stack.set(CoreComponents.BATTERY_CHARGE.get(), 1.0F);
        drone.loadFromStack(stack);
        drone.snapTo(pos.x, pos.y, pos.z, 180.0F, 0.0F);
        level.addFreshEntity(drone);
        drone.resetTo(pos, new Quaternionf());
        if (hover) {
            drone.setNoGravity(true);
        }
        return drone;
    }

    private static Vec3 centre(int[] arena) {
        return new Vec3(arena[0] + 0.5, arena[1], arena[2] + 0.5);
    }

    private static void goggles(ServerPlayer player, QuadcopterEntity drone) {
        ItemStack goggles = ContentIds.item(ContentIds.GOGGLES_CREATIVE).map(ItemStack::new).orElse(ItemStack.EMPTY);
        goggles.set(CoreComponents.BOUND_DRONE.get(), DroneInteractions.binding(drone));
        player.setItemSlot(EquipmentSlot.HEAD, goggles);
    }

    private static void holdPose(DroneSim sim, double rollDegrees) {
        SimSnapshot snapshot = new SimSnapshot();
        sim.copyStateTo(snapshot);
        Quaterniond body = new Quaterniond().rotateZ(Math.toRadians(rollDegrees)).rotateY(Math.PI);
        snapshot.qw = body.w;
        snapshot.qx = body.x;
        snapshot.qy = body.y;
        snapshot.qz = body.z;
        snapshot.vx = 0.0;
        snapshot.vy = 0.0;
        snapshot.vz = 0.0;
        snapshot.lx = 0.0;
        snapshot.ly = 0.0;
        snapshot.lz = 0.0;
        snapshot.sleeping = true;
        sim.copyStateFrom(snapshot);
    }

    private static @Nullable StepResult hold(SelfTest.StepContext ctx, double roll) {
        PilotSession session = PilotSession.INSTANCE;
        DroneSim sim = session.sim();
        QuadcopterEntity drone = session.drone();
        if (!session.isFpv() || sim == null || drone == null || drone.getId() != FPV.get()) {
            return StepResult.fail("FPV-Sitzung mit der Gimbal-Drohne verloren");
        }
        session.setTestInput(StickPositions.IDLE, false, StabMode.ANGLE);
        holdPose(sim, roll);
        return null;
    }

    private static StepResult setupStep(SelfTest.StepContext ctx) {
        if (!ready()) {
            ctx.log("Beispiel-Add-on aus, übersprungen");
            return StepResult.pass();
        }
        Minecraft mc = Minecraft.getInstance();
        if (ctx.tick() == 0 && mc.gui.screen() != null) {
            mc.gui.setScreen(null);
        }
        if (phase(ctx) == 0) {
            if (PilotSession.INSTANCE.isActive()) {
                PilotSession.INSTANCE.requestStop();
                return ctx.tick() > 200 ? StepResult.fail("vorige Sitzung endet nicht") : StepResult.pending();
            }
            PilotSession.INSTANCE.setTestInput(StickPositions.IDLE, false, StabMode.ANGLE);
            StepResult wait = server(ctx, "Testfeld", server -> {
                ServerPlayer player = player(server);
                ServerLevel level = player.level();
                int x = player.getBlockX() + ARENA_DX;
                int z = player.getBlockZ();
                level.getChunk(x >> 4, z >> 4);
                int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z);
                int[] arena = {x, y, z};
                ARENA.set(arena);
                for (int dx = -HALF; dx <= HALF; dx++) {
                    for (int dz = -HALF; dz <= HALF; dz++) {
                        level.setBlockAndUpdate(new BlockPos(x + dx, y - 1, z + dz), Blocks.SMOOTH_STONE.defaultBlockState());
                        for (int dy = 0; dy < HEIGHT; dy++) {
                            BlockPos pos = new BlockPos(x + dx, y + dy, z + dz);
                            if (!level.getBlockState(pos).isAir()) {
                                level.setBlockAndUpdate(pos, Blocks.AIR.defaultBlockState());
                            }
                        }
                    }
                }
                MODE.compareAndSet(null, player.gameMode());
                player.setGameMode(GameType.CREATIVE);
                player.teleportTo(level, x - 3.5, y, z + 0.5, Set.of(), -90.0F, 10.0F, true);
                QuadcopterEntity drone = spawn(level, centre(arena).add(0.0, AIR, 0.0), true);
                FPV.set(drone.getId());
            });
            if (wait != null) {
                return wait;
            }
            next(ctx, 1);
            return StepResult.pending();
        }
        if (phase(ctx) == 1) {
            if (clientDrone(FPV) == null || since(ctx) < 10) {
                return since(ctx) > 300 ? StepResult.fail("Gimbal-Drohne kommt nicht beim Client an") : StepResult.pending();
            }
            StepResult wait = server(ctx, "FPV-Start", server -> {
                ServerPlayer player = player(server);
                QuadcopterEntity drone = serverDrone(server, FPV);
                ItemStack remote = new ItemStack(DroneModule.REMOTE.get());
                remote.set(CoreComponents.BOUND_DRONE.get(), DroneInteractions.binding(drone));
                player.getInventory().setItem(0, remote);
                player.getInventory().setSelectedSlot(0);
                player.connection.send(new ClientboundSetHeldSlotPacket(0));
                goggles(player, drone);
                DronePiloting.StartResult result = DronePiloting.tryStart(player, drone, PilotMode.FPV);
                if (result != DronePiloting.StartResult.STARTED) {
                    RESULT.set("FPV-Start verweigert: " + result);
                }
            });
            if (wait != null) {
                return wait;
            }
            next(ctx, 2);
            return StepResult.pending();
        }
        PilotSession session = PilotSession.INSTANCE;
        QuadcopterEntity drone = session.drone();
        if (!session.isFpv() || drone == null || drone.getId() != FPV.get() || session.sim() == null) {
            return since(ctx) > 300 ? StepResult.fail("keine FPV-Sitzung mit der Gimbal-Drohne: aktiv " + session.isActive() + ", FPV " + session.isFpv()
                    + ", Drohne " + (drone == null ? "-" : drone.getId()) + " statt " + FPV.get()) : StepResult.pending();
        }
        if (!ExampleOperatorClient.PROVIDER.appliesTo(drone)) {
            return StepResult.fail("Beispiel-Gimbal greift nicht: Zubehör vorn " + drone.build().accessories().list());
        }
        ctx.log("FPV-Sitzung mit Drohne " + drone.getId() + ", Action-Cam " + ActionCamRecorder.cameraOf(drone).map(AccessoryType::id).orElse(null));
        return StepResult.pass();
    }

    private static StepResult rollStep(SelfTest.StepContext ctx) {
        if (!ready()) {
            return StepResult.pass();
        }
        int phase = phase(ctx);
        double roll = phase == 0 ? 0.0 : ROLL;
        StepResult lost = hold(ctx, roll);
        if (lost != null) {
            return lost;
        }
        PilotSession session = PilotSession.INSTANCE;
        QuadcopterEntity drone = session.drone();
        ActionCamRecorder recorder = ActionCamRecorder.INSTANCE;
        Minecraft mc = Minecraft.getInstance();
        switch (phase) {
            case 0 -> {
                if (since(ctx) < SETTLE) {
                    return StepResult.pending();
                }
                if (!PropwashClientApi.recording().start(drone)) {
                    return StepResult.fail("recording().start ergibt false: " + recorder.phase() + ", Fehler " + recorder.lastError());
                }
                if (PropwashClientApi.recording().start(drone)) {
                    return StepResult.fail("zweites start() während der Aufnahme ergibt true");
                }
                next(ctx, 1);
                return StepResult.pending();
            }
            case 1 -> {
                if (!PropwashClientApi.recording().recording()) {
                    return StepResult.fail("Aufnahme lief nicht weiter: " + recorder.lastError());
                }
                if (since(ctx) < 2 * SETTLE || recorder.capturedFrames() < 10) {
                    return since(ctx) > 400 ? StepResult.fail("keine Bilder aufgenommen: " + recorder.capturedFrames()) : StepResult.pending();
                }
                Quaternionf body = session.bodyOrientation(new Quaternionf());
                float bodyRoll = CameraLook.imageRollDegrees(body);
                float imageRoll = CameraLook.imageRollDegrees(mc.gameRenderer.mainCamera().rotation());
                Quaternionf path = GimbalRecording.path(drone, new Quaternionf(body), session.cameraUptiltDeg());
                float pathRoll = CameraLook.imageRollDegrees(path);
                float rigidRoll = CameraLook.imageRollDegrees(CameraLook.pathRotation(body, session.cameraUptiltDeg(), null, new Quaternionf()));
                Stabilizer.Mode mode = recorder.stabilization();
                PropwashClientApi.recording().stop();
                String values = String.format(Locale.ROOT, "Drohne %.1f°, Bild %.2f°, Kamerapfad %.2f° (starr %.1f°), Stabilisierung %s, %d Bilder",
                        bodyRoll, imageRoll, pathRoll, rigidRoll, mode.key(), recorder.capturedFrames());
                ctx.log(values);
                if (Math.abs(bodyRoll - ROLL) > 3.0) {
                    return StepResult.fail("Drohne rollt nicht um 30°: " + values);
                }
                if (imageRoll >= MAX_IMAGE_ROLL || pathRoll >= MAX_IMAGE_ROLL) {
                    return StepResult.fail("Bild rollt mit: " + values);
                }
                if (mode != Stabilizer.Mode.OFF || !GimbalRecording.stabilized(drone)) {
                    return StepResult.fail("Gimbal-Aufnahme stabilisiert zusätzlich in Software: " + values);
                }
                if (rigidRoll < 25.0F) {
                    return StepResult.fail("Vergleich ohne Gimbal rollt nicht: " + values);
                }
                next(ctx, 2);
                return StepResult.pending();
            }
            default -> {
                if (recorder.phase() != ActionCamRecorder.Phase.IDLE) {
                    return since(ctx) > 300 ? StepResult.fail("Aufnahme endet nicht: " + recorder.phase()) : StepResult.pending();
                }
                return StepResult.pass();
            }
        }
    }

    private static StepResult fovStep(SelfTest.StepContext ctx) {
        if (!ready()) {
            return StepResult.pass();
        }
        StepResult lost = hold(ctx, 0.0);
        if (lost != null) {
            return lost;
        }
        VideoState video = VideoState.INSTANCE;
        Map<String, Object> state = ctx.state();
        switch (phase(ctx)) {
            case 0 -> {
                ExampleOperatorClient.PROVIDER.fov(Double.NaN);
                if (since(ctx) < 5) {
                    return StepResult.pending();
                }
                state.put("wide", (double) video.fpvFov());
                state.put("wideH", video.horizontalFov());
                ExampleOperatorClient.PROVIDER.fov(FOV);
                next(ctx, 1);
                return StepResult.pending();
            }
            case 1 -> {
                if (since(ctx) < 5) {
                    return StepResult.pending();
                }
                double horizontal = video.horizontalFov();
                double vertical = video.fpvFov();
                ExampleOperatorClient.PROVIDER.fov(10.0);
                double clamped = video.horizontalFov();
                ExampleOperatorClient.PROVIDER.fov(Double.NaN);
                double back = video.horizontalFov();
                String values = String.format(Locale.ROOT, "horizontal %.1f° → %.1f° (gekappt bei 10°: %.1f°, zurück %.1f°), vertikal %.1f° → %.1f°",
                        (double) state.get("wideH"), horizontal, clamped, back, (double) state.get("wide"), vertical);
                ctx.log(values);
                if (Math.abs(horizontal - FOV) > 0.01 || !(vertical < (double) state.get("wide") - 5.0)) {
                    return StepResult.fail("fovDegrees wirkt nicht: " + values);
                }
                if (Math.abs(clamped - 20.0) > 0.01 || Math.abs(back - (double) state.get("wideH")) > 0.01) {
                    return StepResult.fail("Kappung oder NaN falsch: " + values);
                }
                return StepResult.pass();
            }
            default -> {
                return StepResult.pass();
            }
        }
    }

    private static StepResult photoStep(SelfTest.StepContext ctx) {
        if (!ready()) {
            return StepResult.pass();
        }
        StepResult lost = hold(ctx, 0.0);
        if (lost != null) {
            return lost;
        }
        RecordingControlImpl photos = RecordingControlImpl.INSTANCE;
        if (phase(ctx) == 0) {
            ctx.state().put("before", photos.photos());
            if (!PropwashClientApi.recording().photo(PilotSession.INSTANCE.drone())) {
                return StepResult.fail("recording().photo ergibt false");
            }
            next(ctx, 1);
            return StepResult.pending();
        }
        if (photos.photos() <= (int) ctx.state().get("before")) {
            return since(ctx) > 300 ? StepResult.fail("kein Foto gespeichert") : StepResult.pending();
        }
        String problem = png(photos.lastPhoto());
        if (problem != null) {
            return StepResult.fail(problem);
        }
        ctx.log("Foto " + photos.lastPhoto());
        return StepResult.pass();
    }

    private static @Nullable String png(@Nullable Path file) {
        if (file == null || !Files.isRegularFile(file)) {
            return "Foto fehlt: " + file;
        }
        try (InputStream in = Files.newInputStream(file)) {
            byte[] head = in.readNBytes(PNG.length);
            if (!Arrays.equals(head, PNG) || Files.size(file) < 1000L) {
                return "kein PNG: " + file + " (" + Files.size(file) + " Bytes)";
            }
        } catch (IOException e) {
            return "Foto nicht lesbar: " + e;
        }
        return null;
    }

    private static StepResult propsStep(SelfTest.StepContext ctx, boolean spectator) {
        if (!ready()) {
            return StepResult.pass();
        }
        Map<String, Object> state = ctx.state();
        QuadcopterEntity drone;
        Quaternionf frame;
        if (spectator) {
            drone = clientDrone(AUTO);
            if (drone == null || SpectateSession.INSTANCE.drone() != drone || !FpvView.spectating()) {
                return propsDone(state, StepResult.fail("Zuschauer-Sitzung mit der eigenen Drohne verloren: Phase " + SpectateSession.INSTANCE.phase()));
            }
            frame = new Quaternionf(drone.orientation());
        } else {
            StepResult lost = hold(ctx, 0.0);
            if (lost != null) {
                return propsDone(state, lost);
            }
            drone = PilotSession.INSTANCE.drone();
            frame = PilotSession.INSTANCE.bodyOrientation(new Quaternionf());
        }
        ExampleOperatorClient.Gimbal gimbal = ExampleOperatorClient.PROVIDER;
        switch (phase(ctx)) {
            case 0 -> {
                propsOn(state);
                gimbal.fpvImage(true);
                if (since(ctx) < SETTLE) {
                    return StepResult.pending();
                }
                state.put(MARK, new int[]{FpvProps.propsDrawn(), FpvProps.framesDrawn()});
                next(ctx, 1);
                return StepResult.pending();
            }
            case 1 -> {
                if (since(ctx) < PROPS_TICKS) {
                    return StepResult.pending();
                }
                int[] mark = (int[]) state.get(MARK);
                state.put(FPV_COUNT, new int[]{FpvProps.propsDrawn() - mark[0], FpvProps.framesDrawn() - mark[1]});
                gimbal.fpvImage(false);
                next(ctx, 2);
                return StepResult.pending();
            }
            case 2 -> {
                if (since(ctx) < 5) {
                    return StepResult.pending();
                }
                state.put(MARK, new int[]{FpvProps.propsDrawn(), FpvProps.framesDrawn()});
                next(ctx, 3);
                return StepResult.pending();
            }
            default -> {
                if (since(ctx) < PROPS_TICKS) {
                    return StepResult.pending();
                }
                int[] mark = (int[]) state.get(MARK);
                int[] fpv = (int[]) state.get(FPV_COUNT);
                int props = FpvProps.propsDrawn() - mark[0];
                int frames = FpvProps.framesDrawn() - mark[1];
                double offset = degreesBetween(Minecraft.getInstance().gameRenderer.mainCamera().rotation(), gimbal.expected(drone, frame, new Quaternionf()));
                String values = String.format(Locale.ROOT, "%s: FPV-Bild %d Props in %d Bildern, Gimbal-Bild %d Props in %d Bildern, Bild zur Gimbal-Kamera %.2f°",
                        spectator ? "Zuschauer" : "Pilot", fpv[0], fpv[1], props, frames, offset);
                ctx.log(values);
                if (fpv[0] <= 0) {
                    return propsDone(state, StepResult.fail("Vergleich ungültig, schon im FPV-Bild keine Props: " + values));
                }
                if (offset > MAX_OFFSET) {
                    return propsDone(state, StepResult.fail("Gimbal-Bild nicht auf dem Schirm: " + values));
                }
                if (props > 0 || frames > 0) {
                    return propsDone(state, StepResult.fail("Props im Gimbal-Bild: " + values));
                }
                return propsDone(state, StepResult.pass());
            }
        }
    }

    private static void propsOn(Map<String, Object> state) {
        if (state.containsKey(CONFIG)) {
            return;
        }
        DroneRenderConfig saved = DroneRenderConfig.get().copy();
        state.put(CONFIG, saved);
        if (!saved.fpvProps) {
            DroneRenderConfig config = saved.copy();
            config.fpvProps = true;
            DroneRenderConfig.CONFIG.set(config);
        }
    }

    private static StepResult propsDone(Map<String, Object> state, StepResult result) {
        ExampleOperatorClient.PROVIDER.fpvImage(false);
        DroneRenderConfig saved = (DroneRenderConfig) state.remove(CONFIG);
        if (saved != null && !saved.fpvProps) {
            DroneRenderConfig.CONFIG.set(saved);
        }
        return result;
    }

    private static StepResult pathStep(SelfTest.StepContext ctx) {
        if (!ready()) {
            return StepResult.pass();
        }
        int phase = phase(ctx);
        StepResult lost = hold(ctx, phase == 0 ? 0.0 : ROLL);
        if (lost != null) {
            ExampleOperatorClient.PROVIDER.fpvImage(false);
            return lost;
        }
        ExampleOperatorClient.Gimbal gimbal = ExampleOperatorClient.PROVIDER;
        PilotSession session = PilotSession.INSTANCE;
        QuadcopterEntity drone = session.drone();
        if (phase == 0) {
            gimbal.fpvImage(true);
            if (since(ctx) < SETTLE) {
                return StepResult.pending();
            }
            next(ctx, 1);
            return StepResult.pending();
        }
        if (since(ctx) < 2 * SETTLE) {
            return StepResult.pending();
        }
        Quaternionf body = session.bodyOrientation(new Quaternionf());
        float bodyRoll = CameraLook.imageRollDegrees(body);
        float screenRoll = CameraLook.imageRollDegrees(Minecraft.getInstance().gameRenderer.mainCamera().rotation());
        Quaternionf path = GimbalRecording.path(drone, new Quaternionf(body), session.cameraUptiltDeg());
        float pathRoll = CameraLook.imageRollDegrees(path);
        double offset = degreesBetween(path, gimbal.expected(drone, body, new Quaternionf()));
        gimbal.fpvImage(false);
        String values = String.format(Locale.ROOT, "Drohne %.1f°, FPV-Bild auf dem Schirm %.1f°, Kamerapfad %.2f°, Kamerapfad zur Gimbal-Kamera %.2f°",
                bodyRoll, screenRoll, pathRoll, offset);
        ctx.log(values);
        if (Math.abs(bodyRoll - ROLL) > 3.0) {
            return StepResult.fail("Drohne rollt nicht um 30°: " + values);
        }
        if (screenRoll < MIN_FPV_ROLL) {
            return StepResult.fail("Schirm zeigt nicht das FPV-Bild: " + values);
        }
        if (pathRoll >= MAX_IMAGE_ROLL || offset > MAX_OFFSET) {
            return StepResult.fail("Kamerapfad folgt dem Bild auf dem Schirm statt der Gimbal-Kamera: " + values);
        }
        return StepResult.pass();
    }

    private static StepResult pathFovStep(SelfTest.StepContext ctx) {
        if (!ready()) {
            return StepResult.pass();
        }
        ExampleOperatorClient.Gimbal gimbal = ExampleOperatorClient.PROVIDER;
        StepResult lost = hold(ctx, 0.0);
        if (lost != null) {
            gimbal.fpvImage(false);
            gimbal.fov(Double.NaN);
            return lost;
        }
        if (phase(ctx) == 0) {
            gimbal.fpvImage(true);
            gimbal.fov(FOV);
            if (since(ctx) < 5) {
                return StepResult.pending();
            }
            next(ctx, 1);
            return StepResult.pending();
        }
        if (since(ctx) < 5) {
            return StepResult.pending();
        }
        Minecraft minecraft = Minecraft.getInstance();
        PilotSession session = PilotSession.INSTANCE;
        QuadcopterEntity drone = session.drone();
        if (drone == null) {
            gimbal.fpvImage(false);
            gimbal.fov(Double.NaN);
            return StepResult.fail("FPV-Sitzung mit der Gimbal-Drohne verloren");
        }
        double screen = VideoState.INSTANCE.horizontalFov();
        double path = FlashbackCam.horizontalFov(minecraft, session, drone);
        gimbal.fpvImage(false);
        gimbal.fov(Double.NaN);
        double plainScreen = VideoState.INSTANCE.horizontalFov();
        double plainPath = FlashbackCam.horizontalFov(minecraft, session, drone);
        String values = String.format(Locale.ROOT,
                "FPV-Bild auf dem Schirm %.1f°, Kamerapfad %.1f° (Gimbal-Kamera %.1f°); ohne Brennweite Schirm %.1f°, Kamerapfad %.1f°",
                screen, path, FOV, plainScreen, plainPath);
        ctx.log(values);
        if (Math.abs(screen - FOV) < 1.0) {
            return StepResult.fail("Schirm zeigt nicht das FPV-Bild mit dem Sichtfeld des Baus: " + values);
        }
        if (Math.abs(path - FOV) > 0.01) {
            return StepResult.fail("Kamerapfad nimmt das Sichtfeld des Schirms statt der Brennweite der Gimbal-Kamera: " + values);
        }
        if (Math.abs(plainPath - plainScreen) > 0.01) {
            return StepResult.fail("ohne Brennweite der Gimbal-Kamera weicht der Kamerapfad vom Schirm ab: " + values);
        }
        return StepResult.pass();
    }

    private static double degreesBetween(Quaternionfc a, Quaternionfc b) {
        double dot = Math.abs(a.x() * b.x() + a.y() * b.y() + a.z() * b.z() + a.w() * b.w());
        double length = Math.sqrt((double) a.lengthSquared() * b.lengthSquared());
        return length < 1.0E-9 ? 180.0 : Math.toDegrees(2.0 * Math.acos(Math.min(1.0, dot / length)));
    }

    private static StepResult spectateStep(SelfTest.StepContext ctx) {
        if (!ready()) {
            return StepResult.pass();
        }
        PilotSession session = PilotSession.INSTANCE;
        switch (phase(ctx)) {
            case 0 -> {
                session.clearTestInput();
                if (session.isActive()) {
                    session.requestStop();
                    return since(ctx) > 200 ? StepResult.fail("FPV-Sitzung endet nicht") : StepResult.pending();
                }
                next(ctx, 1);
                return StepResult.pending();
            }
            case 1 -> {
                StepResult wait = server(ctx, "Drohne im autonomen Halten", server -> {
                    ServerPlayer player = player(server);
                    ServerLevel level = player.level();
                    int[] arena = ARENA.get();
                    if (arena == null) {
                        RESULT.set("kein Testfeld");
                        return;
                    }
                    serverDrone(server, FPV).discard();
                    FPV.set(-1);
                    QuadcopterEntity drone = spawn(level, centre(arena).add(2.0, 0.0, 0.0), false);
                    AUTO.set(drone.getId());
                    player.teleportTo(level, arena[0] - 3.5, arena[1], arena[2] + 0.5, Set.of(), -90.0F, 0.0F, true);
                    goggles(player, drone);
                    AutonomyResult result = ExampleFollow.takeoff(drone, player, TAKEOFF);
                    if (!result.started()) {
                        RESULT.set("Halten startet nicht: " + result);
                    }
                });
                if (wait != null) {
                    return wait;
                }
                next(ctx, 2);
                return StepResult.pending();
            }
            case 2 -> {
                QuadcopterEntity drone = clientDrone(AUTO);
                SpectateSession spectate = SpectateSession.INSTANCE;
                if (drone == null || !AutonomyLabels.active(drone) || spectate.entry(drone.getUUID()) == null || drone.getY() < ARENA.get()[1] + 2.0) {
                    return since(ctx) > 600 ? StepResult.fail("eigene Drohne sendet nicht: Drohne " + (drone != null) + ", Halten "
                            + (drone != null && AutonomyLabels.active(drone)) + ", Kanal " + (drone != null && spectate.entry(drone.getUUID()) != null))
                            : StepResult.pending();
                }
                if (!PropwashClientApi.spectate(drone)) {
                    return StepResult.fail("spectate ergibt false: " + spectate.blocker(Minecraft.getInstance(), spectate.entry(drone.getUUID())));
                }
                next(ctx, 3);
                return StepResult.pending();
            }
            default -> {
                QuadcopterEntity drone = clientDrone(AUTO);
                SpectateSession spectate = SpectateSession.INSTANCE;
                VideoState video = VideoState.INSTANCE;
                boolean shown = drone != null && spectate.watching() && spectate.drone() == drone && FpvView.spectating()
                        && Minecraft.getInstance().getCameraEntity() == drone && video.status() == VideoState.LinkStatus.CONNECTED;
                if (!shown || since(ctx) < SETTLE) {
                    return since(ctx) > 300 ? StepResult.fail("Zuschauer-Sitzung zeigt die Drohne nicht: Phase " + spectate.phase() + ", Bild "
                            + video.status() + ", Ergebnis " + spectate.lastResult()) : StepResult.pending();
                }
                boolean photo = PropwashClientApi.recording().photo(drone);
                ctx.log("Zuschauer-Sitzung " + spectate.phase() + ", Bild " + video.status() + ", Kanal "
                        + SpectateSession.channelName(spectate.entry(drone.getUUID())).getString() + ", Foto als zuschauender Besitzer " + photo);
                return photo ? StepResult.pass() : StepResult.fail("Foto als zuschauender Besitzer verweigert");
            }
        }
    }

    private static StepResult operatorStep(SelfTest.StepContext ctx) {
        if (!ready()) {
            return StepResult.pass();
        }
        Map<String, Object> state = ctx.state();
        switch (phase(ctx)) {
            case 0 -> {
                StepResult wait = server(ctx, "Operator binden", server -> {
                    QuadcopterEntity drone = serverDrone(server, AUTO);
                    if (!ExampleOperator.bind(drone, player(server))) {
                        RESULT.set("bind verweigert: " + ExampleOperator.LAST.get());
                    }
                });
                if (wait != null) {
                    return wait;
                }
                next(ctx, 1);
                return StepResult.pending();
            }
            case 1 -> {
                QuadcopterEntity drone = clientDrone(AUTO);
                if (drone == null || !OperatorClient.operating() || !PropwashClientApi.input().operating()) {
                    return since(ctx) > 300 ? StepResult.fail("Operator-Ansicht startet nicht: gebunden " + OperatorClient.bound() + ", Phase "
                            + SpectateSession.INSTANCE.phase()) : StepResult.pending();
                }
                state.put("pan", ExampleOperatorClient.PROVIDER.pan(drone));
                state.put("sent", OperatorClient.sent());
                setOperatorInput(OPERATOR_VALUES);
                next(ctx, 2);
                return StepResult.pending();
            }
            case 2 -> {
                QuadcopterEntity drone = clientDrone(AUTO);
                if (drone == null) {
                    restoreOperatorInput();
                    return StepResult.fail("Drohne verschwunden");
                }
                double pan = ExampleOperatorClient.PROVIDER.pan(drone) - (double) state.get("pan");
                OperatorClient.State relayed = OperatorClient.state(drone.getId());
                if (pan < 20.0 || relayed == null || relayed.axis(0) < 0.99 || Math.abs(relayed.axis(3) - 0.5) > 0.01
                        || !OperatorClient.action(drone, 1) || OperatorClient.action(drone, 2) || OperatorClient.action(drone, 3)
                        || !OperatorClient.action(drone, 4)) {
                    if (since(ctx) > 300) {
                        restoreOperatorInput();
                        return StepResult.fail(String.format(Locale.ROOT, "Gimbal dreht nicht mit %s=%s: Schwenk %.1f°, Lage %s", OPERATOR_INPUT,
                                OPERATOR_VALUES, pan, describe(relayed)));
                    }
                    return StepResult.pending();
                }
                StepResult wait = server(ctx, "Operator-Achsen am Server", server -> {
                    QuadcopterEntity serverDrone = serverDrone(server, AUTO);
                    DroneSim sim = AddonAutonomy.sim(serverDrone);
                    double axis = sim == null ? Double.NaN : sim.operatorAxis(0);
                    double throttle = OperatorServer.axis(serverDrone, 3);
                    if (!(axis > 0.99) || Math.abs(throttle - 0.5) > 0.01 || !OperatorServer.action(serverDrone, 1) || !OperatorServer.action(serverDrone, 4)) {
                        RESULT.set(String.format(Locale.ROOT, "Server: FlightState#operatorAxis(0) %.2f, Achse 3 %.2f, Aktion 1 %s, Aktion 4 %s", axis, throttle,
                                OperatorServer.action(serverDrone, 1), OperatorServer.action(serverDrone, 4)));
                    } else {
                        INFO.set(String.format(Locale.ROOT, "Server: %d angenommen, %d abgewiesen, FlightState#operatorAxis(0) %.2f",
                                OperatorServer.accepted(), OperatorServer.refused(), axis));
                    }
                });
                if (wait != null) {
                    if (wait.status() == StepResult.Status.FAIL) {
                        restoreOperatorInput();
                    }
                    return wait;
                }
                ctx.log(String.format(Locale.ROOT, "%s=%s: Schwenk %.1f° in %d Ticks, %d Pakete gesendet, Lage %s; %s", OPERATOR_INPUT, OPERATOR_VALUES, pan,
                        since(ctx), OperatorClient.sent() - (int) state.get("sent"), describe(relayed), INFO.get()));
                setOperatorInput(OPERATOR_INVALID);
                next(ctx, 3);
                return StepResult.pending();
            }
            case 3 -> {
                QuadcopterEntity drone = clientDrone(AUTO);
                OperatorClient.State relayed = drone == null ? null : OperatorClient.state(drone.getId());
                boolean neutral = relayed != null && relayed.nanos() > (long) state.computeIfAbsent("invalid", key -> System.nanoTime())
                        && Math.abs(relayed.axis(0)) < 0.5 && !OperatorClient.action(drone, 1) && !OperatorClient.action(drone, 4);
                if (!neutral) {
                    if (since(ctx) > 100) {
                        restoreOperatorInput();
                        return StepResult.fail(String.format(Locale.ROOT, "ungültiger Wert %s=%s wirkt noch: Lage %s", OPERATOR_INPUT, OPERATOR_INVALID,
                                describe(relayed)));
                    }
                    return StepResult.pending();
                }
                ctx.log(String.format(Locale.ROOT, "%s=%s ignoriert, Lage %s nach %d Ticks", OPERATOR_INPUT, OPERATOR_INVALID, describe(relayed), since(ctx)));
                restoreOperatorInput();
                SpectateSession.INSTANCE.consumeEscape();
                next(ctx, 4);
                return StepResult.pending();
            }
            default -> {
                if (OperatorClient.bound() || SpectateSession.INSTANCE.operator()) {
                    return since(ctx) > 200 ? StepResult.fail("Esc gibt die Kamera nicht ab") : StepResult.pending();
                }
                StepResult wait = server(ctx, "Operator gelöst", server -> {
                    if (OperatorServer.bound() != 0 || OperatorServer.operator(serverDrone(server, AUTO)).isPresent()) {
                        RESULT.set("Server hält die Bindung");
                    }
                });
                if (wait != null && wait.status() == StepResult.Status.FAIL && since(ctx) < 200) {
                    ctx.state().remove(FUTURE);
                    return StepResult.pending();
                }
                if (wait != null) {
                    return wait;
                }
                ctx.log("Esc gibt die Kamera zurück, Server löst die Bindung nach " + since(ctx) + " Ticks");
                return StepResult.pass();
            }
        }
    }

    private static void setOperatorInput(String value) {
        if (INPUT_SET.compareAndSet(false, true)) {
            PREVIOUS_INPUT.set(System.getProperty(OPERATOR_INPUT));
        }
        System.setProperty(OPERATOR_INPUT, value);
    }

    private static void restoreOperatorInput() {
        if (!INPUT_SET.compareAndSet(true, false)) {
            return;
        }
        String previous = PREVIOUS_INPUT.getAndSet(null);
        if (previous == null) {
            System.clearProperty(OPERATOR_INPUT);
        } else {
            System.setProperty(OPERATOR_INPUT, previous);
        }
    }

    private static String describe(OperatorClient.@Nullable State state) {
        if (state == null) {
            return "-";
        }
        return String.format(Locale.ROOT, "Achsen %.2f/%.2f/%.2f/%.2f, Aktionen %s", state.axis(0), state.axis(1), state.axis(2), state.axis(3),
                Integer.toBinaryString(state.actions()));
    }

    private static StepResult vanishStep(SelfTest.StepContext ctx, boolean roleInSameTick) {
        if (!ready()) {
            return StepResult.pass();
        }
        Minecraft mc = Minecraft.getInstance();
        SpectateSession spectate = SpectateSession.INSTANCE;
        switch (phase(ctx)) {
            case 0 -> {
                StepResult wait = server(ctx, "Operator binden", server -> {
                    ServerPlayer player = player(server);
                    QuadcopterEntity drone = player.level().getEntity(AUTO.get()) instanceof QuadcopterEntity found && !found.isRemoved() ? found : null;
                    if (drone == null) {
                        int[] arena = ARENA.get();
                        if (arena == null) {
                            RESULT.set("kein Testfeld");
                            return;
                        }
                        drone = spawn(player.level(), centre(arena).add(2.0, 0.0, 0.0), false);
                        AUTO.set(drone.getId());
                        goggles(player, drone);
                        AutonomyResult result = ExampleFollow.takeoff(drone, player, TAKEOFF);
                        if (!result.started()) {
                            RESULT.set("Halten startet nicht: " + result);
                            return;
                        }
                    }
                    if (!ExampleOperator.bind(drone, player)) {
                        RESULT.set("bind verweigert: " + ExampleOperator.LAST.get());
                    }
                });
                if (wait != null) {
                    return wait;
                }
                next(ctx, 1);
                return StepResult.pending();
            }
            case 1 -> {
                QuadcopterEntity drone = clientDrone(AUTO);
                if (drone == null || mc.level == null || !OperatorClient.operating()) {
                    ctx.state().remove("watching");
                    return since(ctx) > 300 ? StepResult.fail("Operator-Ansicht startet nicht: gebunden " + OperatorClient.bound() + ", Phase "
                            + spectate.phase()) : StepResult.pending();
                }
                int watching = (int) ctx.state().computeIfAbsent("watching", key -> ctx.tick());
                if (ctx.tick() - watching < SETTLE) {
                    return StepResult.pending();
                }
                mc.level.removeEntity(drone.getId(), Entity.RemovalReason.DISCARDED);
                if (roleInSameTick) {
                    OperatorClient.onRole(mc, new OperatorRoleS2C(drone.getId(), drone.getUUID(), OperatorRoleS2C.Role.NONE, drone.getName().getString()));
                }
                next(ctx, 2);
                return StepResult.pending();
            }
            case 2 -> {
                boolean ended = !OperatorClient.bound() && !spectate.operator() && !spectate.active() && mc.getCameraEntity() == mc.player;
                if (!ended) {
                    return since(ctx) > VIEW_END ? StepResult.fail("Operator-Ansicht " + since(ctx) + " Ticks nach dem Verschwinden der Drohne nicht beendet: gebunden "
                            + OperatorClient.bound() + ", Phase " + spectate.phase() + ", Operator " + spectate.operator()) : StepResult.pending();
                }
                ctx.state().put("ended", since(ctx));
                next(ctx, 3);
                return StepResult.pending();
            }
            default -> {
                StepResult wait = server(ctx, "Operator gelöst", server -> {
                    QuadcopterEntity drone = player(server).level().getEntity(AUTO.get()) instanceof QuadcopterEntity found && !found.isRemoved() ? found : null;
                    if (drone != null && (roleInSameTick || OperatorServer.operator(drone).isEmpty())) {
                        AUTO.set(-1);
                        ExampleFollow.stop(drone);
                        drone.discard();
                    }
                    if (OperatorServer.bound() != 0) {
                        RESULT.set("Server hält die Bindung");
                    }
                });
                if (wait != null && wait.status() == StepResult.Status.FAIL && since(ctx) < 200) {
                    ctx.state().remove(FUTURE);
                    return StepResult.pending();
                }
                if (wait != null) {
                    return wait;
                }
                ctx.log("Operator-Ansicht " + ctx.state().get("ended") + " Ticks nach dem Verschwinden der Drohne beendet, Server löst die Bindung nach "
                        + since(ctx) + " weiteren Ticks");
                return StepResult.pass();
            }
        }
    }

    private static StepResult headStep(SelfTest.StepContext ctx) {
        if (!ready()) {
            return StepResult.pass();
        }
        Minecraft mc = Minecraft.getInstance();
        Map<String, Object> state = ctx.state();
        switch (phase(ctx)) {
            case 0 -> {
                PilotSession session = PilotSession.INSTANCE;
                session.clearTestInput();
                if (session.isActive()) {
                    session.requestStop();
                    return since(ctx) > 200 ? StepResult.fail("FPV-Sitzung endet nicht") : StepResult.pending();
                }
                StepResult wait = server(ctx, "Drohne für das Foto", server -> {
                    ServerPlayer player = player(server);
                    ServerLevel level = player.level();
                    int[] arena = ARENA.get();
                    if (arena == null) {
                        RESULT.set("kein Testfeld");
                        return;
                    }
                    for (AtomicInteger which : new AtomicInteger[]{FPV, AUTO}) {
                        if (which.get() >= 0 && level.getEntity(which.getAndSet(-1)) instanceof QuadcopterEntity old) {
                            ExampleFollow.stop(old);
                            old.discard();
                        }
                    }

                    Vec3 spot = centre(arena).add(0.0, 1.2, 0.0);
                    QuadcopterEntity drone = spawn(level, spot, true);
                    PARKED.set(drone.getId());
                    player.setItemSlot(EquipmentSlot.HEAD, ItemStack.EMPTY);
                    player.setGameMode(GameType.SPECTATOR);
                    Vec3 eye = spot.add(HEAD_VIEW);
                    player.teleportTo(level, eye.x, eye.y - player.getEyeHeight(), eye.z, Set.of(), 0.0F, 0.0F, true);
                });
                if (wait != null) {
                    return wait;
                }
                ExampleOperatorClient.PROVIDER.force(-45.0, 35.0);
                state.put("posed", GimbalHead.posedFrames());
                state.put("splits", GimbalHead.splitSubmits());
                next(ctx, 1);
                return StepResult.pending();
            }
            case 1 -> {
                QuadcopterEntity drone = clientDrone(PARKED);
                LocalPlayer player = mc.player;
                if (drone == null || player == null) {
                    return since(ctx) > 300 ? StepResult.fail("Drohne kam nicht beim Client an") : StepResult.pending();
                }
                look(player, drone.position().add(0.0, 0.05, 0.0));
                if (!state.containsKey("fov")) {
                    state.put("fov", mc.options.fov().get());
                    mc.options.fov().set(30);
                    if (!mc.gui.hud.isHidden()) {
                        mc.gui.hud.toggle();
                    }
                    Screenshots.clearOverlays(mc);
                }
                int posed = GimbalHead.posedFrames() - (int) state.get("posed");
                int splits = GimbalHead.splitSubmits() - (int) state.get("splits");
                if (posed < 10 || splits < 10 || GimbalHead.headQuads() == 0 || since(ctx) < 30) {
                    return since(ctx) > 400 ? finishHead(ctx, StepResult.fail("Gimbal-Kopf dreht sich nicht: " + posed + " Lagen, " + splits
                            + " geteilte Modelle, " + GimbalHead.headQuads() + " Kopf-Quads")) : StepResult.pending();
                }
                ctx.log(posed + " Lagen aus modelPose, " + splits + " Modelle mit gedrehtem gimbal_head, " + GimbalHead.headQuads() + " Kopf-Quads");
                if (!Screenshots.enabled()) {
                    return finishHead(ctx, StepResult.pass());
                }
                state.put("shot", Screenshots.capture("gimbal_head"));
                next(ctx, 2);
                return StepResult.pending();
            }
            default -> {
                QuadcopterEntity drone = clientDrone(PARKED);
                if (drone != null && mc.player != null) {
                    look(mc.player, drone.position().add(0.0, 0.05, 0.0));
                }
                CompletableFuture<?> shot = (CompletableFuture<?>) state.get("shot");
                if (!shot.isDone()) {
                    return StepResult.pending();
                }
                if (shot.isCompletedExceptionally()) {
                    return finishHead(ctx, StepResult.fail("Bildschirmfoto gimbal_head: " + shot.exceptionNow()));
                }
                ctx.log("Bildschirmfoto " + Screenshots.dir().resolve("gimbal_head.png"));
                return finishHead(ctx, StepResult.pass());
            }
        }
    }

    private static StepResult finishHead(SelfTest.StepContext ctx, StepResult result) {
        Minecraft mc = Minecraft.getInstance();
        ExampleOperatorClient.PROVIDER.force(null, null);
        Object fov = ctx.state().get("fov");
        if (fov instanceof Integer saved) {
            mc.options.fov().set(saved);
        }
        if (mc.gui.hud.isHidden()) {
            mc.gui.hud.toggle();
        }
        return result;
    }

    private static void look(LocalPlayer player, Vec3 target) {
        Vec3 eye = player.getEyePosition();
        double dx = target.x - eye.x;
        double dy = target.y - eye.y;
        double dz = target.z - eye.z;
        float yaw = (float) Math.toDegrees(Math.atan2(-dx, dz));
        float pitch = (float) -Math.toDegrees(Math.atan2(dy, Math.sqrt(dx * dx + dz * dz)));
        player.setYRot(yaw);
        player.setXRot(pitch);
        player.yRotO = yaw;
        player.xRotO = pitch;
        player.setYHeadRot(yaw);
        player.yHeadRotO = yaw;
    }

    private static StepResult cleanupStep(SelfTest.StepContext ctx) {
        if (!ready()) {
            return StepResult.pass();
        }
        OperatorClient.testInput(null, 0);
        restoreOperatorInput();
        ExampleOperatorClient.PROVIDER.fov(Double.NaN);
        ExampleOperatorClient.PROVIDER.force(null, null);
        ExampleOperatorClient.PROVIDER.fpvImage(false);
        PilotSession session = PilotSession.INSTANCE;
        session.clearTestInput();
        if (session.isActive()) {
            session.requestStop();
            return StepResult.pending();
        }
        if (SpectateSession.INSTANCE.active()) {
            SpectateSession.INSTANCE.stop(Minecraft.getInstance(), true);
        }
        StepResult wait = server(ctx, "Aufräumen", server -> {
            ServerPlayer player = player(server);
            for (AtomicInteger which : new AtomicInteger[]{FPV, AUTO, PARKED}) {
                int id = which.getAndSet(-1);
                if (id >= 0 && player.level().getEntity(id) instanceof QuadcopterEntity drone) {
                    ExampleFollow.stop(drone);
                    OperatorServer.unbind(drone);
                    drone.discard();
                }
            }
            player.setItemSlot(EquipmentSlot.HEAD, ItemStack.EMPTY);
            GameType mode = MODE.getAndSet(null);
            player.setGameMode(mode == null ? GameType.CREATIVE : mode);
        });
        return wait != null ? wait : StepResult.pass();
    }
}
