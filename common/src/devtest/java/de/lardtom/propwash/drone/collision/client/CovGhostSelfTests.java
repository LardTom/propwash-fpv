package de.lardtom.propwash.drone.collision.client;

import com.mojang.blaze3d.platform.NativeImage;
import de.lardtom.propwash.build.DroneBuild;
import de.lardtom.propwash.content.ContentIds;
import de.lardtom.propwash.core.CoreComponents;
import de.lardtom.propwash.dev.SelfTest;
import de.lardtom.propwash.dev.SelfTest.StepResult;
import de.lardtom.propwash.dev.client.Screenshots;
import de.lardtom.propwash.dev.example.ExampleGhost;
import de.lardtom.propwash.drone.DroneInteractions;
import de.lardtom.propwash.drone.DroneModule;
import de.lardtom.propwash.drone.DronePiloting;
import de.lardtom.propwash.drone.PilotMode;
import de.lardtom.propwash.drone.QuadcopterEntity;
import de.lardtom.propwash.drone.client.CameraClip;
import de.lardtom.propwash.drone.client.PilotSession;
import de.lardtom.propwash.input.StickPositions;
import de.lardtom.propwash.osd.OsdData;
import de.lardtom.propwash.osd.OsdWarnings;
import de.lardtom.propwash.osd.client.OsdHud;
import de.lardtom.propwash.sim.DroneSim;
import de.lardtom.propwash.sim.SimSnapshot;
import de.lardtom.propwash.sim.StabMode;
import de.lardtom.propwash.sim.Telemetry;
import de.lardtom.propwash.video.ImageArea;
import de.lardtom.propwash.video.client.FpvImage;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.ARGB;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;

public final class CovGhostSelfTests {
    public static final String GROUP = GhostSelfTests.GROUP + " – Abdeckung: ";
    private static final String GERMAN = "de_de";
    private static final int RIG_DZ = 22;
    private static final int CLEAR_BACK = 3;
    private static final int CLEAR_FRONT = 14;
    private static final int CLEAR_SIDE = 4;
    private static final int CLEAR_HEIGHT = 12;
    private static final int WALL_DX = 4;
    private static final int WALL_THICK = 3;
    private static final int WALL_HALF = 3;
    private static final int WALL_HEIGHT = 6;
    private static final double START_DX = 1.5;
    private static final double HOVER = 2.0;
    private static final double SLOW = 2.5;
    private static final int GRID_X = 16;
    private static final int GRID_Y = 9;
    private static final double BLACK_LIMIT = 0.05;
    private static final double DISTANCE_LIMIT = 45.0;
    private static final Quaternionf FACING_WALL = new Quaternionf().rotationY((float) (-Math.PI / 2.0));
    private static final AtomicReference<Rig> RIG = new AtomicReference<>();
    private static final String STATE = "cov_ghost";
    public static final String NOCLIP_STEP = "guard: Abdeckung – Noclip 0.1.x: FPV, Taste key.propwash.noclip, Flug gegen eine 3 Blöcke dicke Wand";
    private static final String NOCLIP_MOD = "pwnoclip";
    private static final String NOCLIP_KEY = "key.propwash.noclip";
    private static final double FAST = 6.0;
    private static final int PUSH_TICKS = 80;

    private CovGhostSelfTests() {
    }

    public static void register() {
        SelfTest.clientStep(GROUP + "Bild in der Wand ohne schwarze Flächen, nah am Zuschauermodus, OSD-Warnung IM BLOCK (Foto ghost_inside_wall_en)", 1500,
                ctx -> step(ctx, "ghost_inside_wall_en"));
        String back = GROUP + "Sprache zurückstellen";
        SelfTest.switchStep(GROUP + "Sprache Deutsch", back, 2400, ctx -> Screenshots.language(ctx, GERMAN));
        SelfTest.clientStep(GROUP + "Deutsch: Bild in der Wand, OSD-Warnung IM BLOCK (Foto ghost_inside_wall_de)", 1500,
                ctx -> step(ctx, "ghost_inside_wall_de"));
        SelfTest.clientStep(back, 2400, Screenshots::restoreLanguage);
        SelfTest.clientStep(GROUP + "Testfeld abbauen", 300, CovGhostSelfTests::cleanupStep);
        SelfTest.clientStep(NOCLIP_STEP, 1500, CovGhostSelfTests::noclipStep);
        SelfTest.clientStep(NOCLIP_STEP + " – Testfeld abbauen", 300, CovGhostSelfTests::cleanupStep);
    }

    private static final class Rig {
        final int x;
        final int y;
        final int z;
        final Map<BlockPos, BlockState> original = new LinkedHashMap<>();
        boolean built;
        int droneId = -1;
        @Nullable ItemStack head;
        @Nullable ItemStack hand;
        @Nullable Vec3 home;
        @Nullable GameType mode;

        Rig(int x, int y, int z) {
            this.x = x;
            this.y = y;
            this.z = z;
        }

        Vec3 ground() {
            return new Vec3(this.x + START_DX, this.y, this.z + 0.5);
        }

        Vec3 air() {
            return this.ground().add(0.0, HOVER, 0.0);
        }

        double wallMiddle() {
            return this.x + WALL_DX + WALL_THICK / 2.0;
        }
    }

    private static final class Run {
        int stage;
        int mark;
        @Nullable CompletableFuture<?> pending;
        final AtomicReference<double[]> fpv = new AtomicReference<>();
        final AtomicReference<double[]> spectator = new AtomicReference<>();
        @Nullable CompletableFuture<String> shot;
        @Nullable CompletableFuture<String> referenceShot;
        @Nullable Vec3 eye;
        float yRot;
        float xRot;
        float fov;
        int savedFov = -1;
        boolean savedHide;
        boolean warning;
        boolean ghostOn;
        @Nullable ImageArea area;

        void enter(int stage, int tick) {
            this.stage = stage;
            this.mark = tick;
        }
    }

    private static void input(float throttle, boolean arm) {
        PilotSession.INSTANCE.setTestInput(new StickPositions(0.0F, 0.0F, 0.0F, throttle), arm, StabMode.ANGLE);
    }

    private static void pin(DroneSim sim, @Nullable Double x) {
        SimSnapshot snapshot = new SimSnapshot();
        sim.copyStateTo(snapshot);
        if (x != null) {
            snapshot.px = x;
        }
        snapshot.vx = x == null ? SLOW : 0.0;
        snapshot.vy = 0.0;
        snapshot.vz = 0.0;
        snapshot.sleeping = false;
        sim.copyStateFrom(snapshot);
    }

    private static boolean near(QuadcopterEntity drone, Vec3 target) {
        return Math.abs(drone.getX() - target.x) < 0.3 && Math.abs(drone.getZ() - target.z) < 0.3 && Math.abs(drone.getY() - target.y) < 0.3;
    }

    private static double luminance(int pixel) {
        return 0.299 * ARGB.red(pixel) + 0.587 * ARGB.green(pixel) + 0.114 * ARGB.blue(pixel);
    }

    private static double[] analyse(NativeImage image, ImageArea area) {
        double sx = image.getWidth() / (double) area.screenWidth();
        double sy = image.getHeight() / (double) area.screenHeight();
        int x0 = (int) Math.round(area.x() * sx);
        int y0 = (int) Math.round(area.y() * sy);
        int width = Math.max(1, (int) Math.round(area.width() * sx));
        int height = Math.max(1, (int) Math.round(area.height() * sy));
        double[] out = new double[1 + GRID_X * GRID_Y];
        int black = 0;
        int total = 0;
        for (int gy = 0; gy < GRID_Y; gy++) {
            for (int gx = 0; gx < GRID_X; gx++) {
                double sum = 0.0;
                int count = 0;
                for (int y = y0 + gy * height / GRID_Y; y < y0 + (gy + 1) * height / GRID_Y; y += 2) {
                    for (int x = x0 + gx * width / GRID_X; x < x0 + (gx + 1) * width / GRID_X; x += 2) {
                        if (x < 0 || y < 0 || x >= image.getWidth() || y >= image.getHeight()) {
                            continue;
                        }
                        double l = luminance(image.getPixel(x, y));
                        sum += l;
                        count++;
                        total++;
                        if (l < 8.0) {
                            black++;
                        }
                    }
                }
                out[1 + gy * GRID_X + gx] = count == 0 ? 0.0 : sum / count;
            }
        }
        out[0] = total == 0 ? 1.0 : black / (double) total;
        return out;
    }

    private static double distance(double[] a, double[] b) {
        double sum = 0.0;
        int count = 0;
        for (int gy = GRID_Y / 4; gy < GRID_Y - GRID_Y / 4; gy++) {
            for (int gx = GRID_X / 4; gx < GRID_X - GRID_X / 4; gx++) {
                sum += Math.abs(a[1 + gy * GRID_X + gx] - b[1 + gy * GRID_X + gx]);
                count++;
            }
        }
        return count == 0 ? Double.NaN : sum / count;
    }

    private static void grab(AtomicReference<double[]> out, ImageArea area) {
        Minecraft mc = Minecraft.getInstance();
        Screenshot.takeScreenshot(mc.gameRenderer.mainRenderTarget(), 1, image -> {
            try (NativeImage owned = image) {
                out.set(analyse(owned, area));
            }
        });
    }

    private static boolean insideWarning() {
        OsdData data = OsdHud.sampler().data();
        List<OsdWarnings.Warning> warnings = new ArrayList<>();
        OsdWarnings.collect(data, warnings);
        return data.insideBlock && warnings.contains(OsdWarnings.Warning.INSIDE_BLOCK);
    }

    private static StepResult step(SelfTest.StepContext ctx, String file) {
        Run run = (Run) ctx.state().computeIfAbsent(STATE, key -> new Run());
        if (run.pending != null) {
            if (run.pending.isCompletedExceptionally()) {
                return finish(ctx, run, StepResult.fail("Server-Aktion in Stufe " + run.stage + ": " + run.pending.exceptionNow().getMessage()));
            }
            if (!run.pending.isDone()) {
                return StepResult.pending();
            }
            run.pending = null;
        }
        try {
            return advance(ctx, run, file);
        } catch (RuntimeException e) {
            return finish(ctx, run, StepResult.fail(e.toString()));
        }
    }

    private static StepResult advance(SelfTest.StepContext ctx, Run run, String file) {
        Minecraft mc = Minecraft.getInstance();
        int waited = ctx.tick() - run.mark;
        PilotSession session = PilotSession.INSTANCE;
        if (run.stage == 0) {
            ExampleGhost.force(false);
            input(0.0F, false);
            run.pending = ctx.onServer(CovGhostSelfTests::prepare);
            run.enter(1, ctx.tick());
            return StepResult.pending();
        }
        Rig rig = RIG.get();
        if (rig == null) {
            return finish(ctx, run, StepResult.fail("kein Testfeld"));
        }
        if (run.stage >= 20) {
            return reference(ctx, run, rig, file, waited);
        }
        QuadcopterEntity drone = session.drone();
        DroneSim sim = session.sim();
        if (drone == null || sim == null || drone.getId() != rig.droneId || !session.isFpv()) {
            return waited > 120 ? finish(ctx, run, StepResult.fail("keine FPV-Sitzung mit der Testdrohne")) : StepResult.pending();
        }
        Telemetry t = sim.telemetry();
        switch (run.stage) {
            case 1 -> {
                input(0.0F, false);
                if (!near(drone, rig.ground()) || t.armed) {
                    return waited > 120 ? finish(ctx, run, StepResult.fail("Drohne nicht aufs Testfeld gesetzt")) : StepResult.pending();
                }
                run.enter(2, ctx.tick());
            }
            case 2 -> {
                input(0.0F, RigArming.armSwitch(waited, t.armed, t.grounded || t.sleeping));
                if (!t.armed) {
                    return waited > 200 ? finish(ctx, run, StepResult.fail("nicht scharf: " + t.armBlockers)) : StepResult.pending();
                }
                Vec3 air = rig.air();
                run.pending = ctx.onServer(server -> serverDrone(server, rig.droneId).resetTo(air, FACING_WALL));
                run.enter(3, ctx.tick());
            }
            case 3 -> {
                input((float) t.hoverThrottleStick, true);
                if (!near(drone, rig.air())) {
                    return waited > 120 ? finish(ctx, run, StepResult.fail("Drohne nicht vor die Wand gesetzt")) : StepResult.pending();
                }
                if (waited < 10) {
                    return StepResult.pending();
                }
                ExampleGhost.force(true);
                run.ghostOn = true;
                run.enter(4, ctx.tick());
            }
            case 4 -> {
                input((float) t.hoverThrottleStick, true);
                if (drone.getX() < rig.wallMiddle()) {
                    pin(sim, null);
                    return waited > 160 ? finish(ctx, run, StepResult.fail(String.format(Locale.ROOT, "Drohne kommt nicht in die Wand: x %.2f",
                            drone.getX()))) : StepResult.pending();
                }
                pin(sim, rig.wallMiddle());
                run.enter(5, ctx.tick());
            }
            case 5 -> {
                input((float) t.hoverThrottleStick, true);
                pin(sim, rig.wallMiddle());
                if (waited < 20) {
                    return StepResult.pending();
                }
                if (insideWarning()) {
                    run.warning = true;
                } else if (run.ghostOn && waited > 40) {
                    ExampleGhost.force(false);
                    run.ghostOn = false;
                    ctx.log("mit eingeschaltetem Geist keine Warnung IM BLOCK – Geist in der Wand ausgeschaltet wie im Schritt „Schalter in der Wand aus“");
                }
                if (!run.warning) {
                    return waited > 120 ? finish(ctx, run, StepResult.fail("OSD-Warnung IM BLOCK fehlt in der Wand (insideBlock "
                            + OsdHud.sampler().data().insideBlock + ", Geist " + ClientCollision.INSTANCE.ghost() + ")")) : StepResult.pending();
                }
                if (!CameraClip.active()) {
                    return finish(ctx, run, StepResult.fail("Kamera in der Wand nicht frei (CameraClip aus)"));
                }
                net.minecraft.client.Camera camera = mc.gameRenderer.mainCamera();
                run.eye = camera.position();
                run.yRot = camera.yRot();
                run.xRot = camera.xRot();
                run.fov = camera.getFov();
                run.area = FpvImage.area(mc.getWindow().getWidth(), mc.getWindow().getHeight());
                Screenshots.parkMouse(mc);
                Screenshots.hideMarker(true);
                grab(run.fpv, run.area);
                if (Screenshots.enabled()) {
                    run.shot = Screenshots.capture(file);
                }
                run.enter(6, ctx.tick());
            }
            case 6 -> {
                input((float) t.hoverThrottleStick, true);
                pin(sim, rig.wallMiddle());
                if (run.fpv.get() == null || run.shot != null && !run.shot.isDone()) {
                    return waited > 200 ? finish(ctx, run, StepResult.fail("kein Bild aus der Wand")) : StepResult.pending();
                }
                Screenshots.hideMarker(false);
                double black = run.fpv.get()[0];
                ctx.log(String.format(Locale.ROOT, "Bild in der Wand: %.2f %% schwarz (Grenze %.0f %%), Kamera %.2f/%.2f/%.2f, Blick %.1f°/%.1f°, FOV %.1f°, "
                                + "Warnung IM BLOCK %s, Geist %s%s", black * 100.0, BLACK_LIMIT * 100.0, run.eye.x, run.eye.y, run.eye.z, run.yRot, run.xRot,
                        run.fov, run.warning, run.ghostOn ? "an" : "aus (in der Wand ausgeschaltet)",
                        run.shot == null ? "" : ", Bildschirmfoto " + Screenshots.dir().resolve(file + ".png")));
                if (black > BLACK_LIMIT) {
                    return finish(ctx, run, StepResult.fail(String.format(Locale.ROOT, "Bild in der Wand zu %.1f %% schwarz", black * 100.0)));
                }
                ExampleGhost.force(false);
                PilotSession.INSTANCE.clearTestInput();
                Vec3 eye = run.eye;
                float yaw = run.yRot;
                float pitch = run.xRot;
                run.pending = ctx.onServer(server -> spectate(server, rig, eye, yaw, pitch));
                run.enter(20, ctx.tick());
            }
            default -> {
                return finish(ctx, run, StepResult.fail("Stufe " + run.stage));
            }
        }
        return StepResult.pending();
    }

    private static StepResult reference(SelfTest.StepContext ctx, Run run, Rig rig, String file, int waited) {
        Minecraft mc = Minecraft.getInstance();
        switch (run.stage) {
            case 20 -> {
                if (PilotSession.INSTANCE.isActive() || mc.player == null || !mc.player.isSpectator()) {
                    return waited > 200 ? finish(ctx, run, StepResult.fail("Zuschauermodus für das Vergleichsbild startet nicht")) : StepResult.pending();
                }
                if (run.savedFov < 0) {
                    run.savedFov = mc.options.fov().get();
                    run.savedHide = mc.gui.hud.isHidden();
                }
                mc.options.fov().set(Math.max(30, Math.min(110, Math.round(run.fov))));
                if (!mc.gui.hud.isHidden()) {
                    mc.gui.hud.toggle();
                }
                run.enter(21, ctx.tick());
            }
            case 21 -> {
                if (waited < 40) {
                    return StepResult.pending();
                }
                Screenshots.parkMouse(mc);
                grab(run.spectator, run.area);
                if (Screenshots.enabled()) {
                    run.referenceShot = Screenshots.capture(file.replace("ghost_inside_wall", "ghost_inside_wall_spectator"));
                }
                run.enter(22, ctx.tick());
            }
            case 22 -> {
                if (run.spectator.get() == null || run.referenceShot != null && !run.referenceShot.isDone()) {
                    return waited > 200 ? finish(ctx, run, StepResult.fail("kein Vergleichsbild im Zuschauermodus")) : StepResult.pending();
                }
                double distance = distance(run.fpv.get(), run.spectator.get());
                ctx.log(String.format(Locale.ROOT, "Vergleich mit dem Zuschauermodus (FOV %d°, Kamera an derselben Stelle): mittlerer Helligkeitsabstand "
                                + "%.1f von 255 im mittleren Bildbereich (Grenze %.0f), Zuschauerbild %.2f %% schwarz", mc.options.fov().get(), distance,
                        DISTANCE_LIMIT, run.spectator.get()[0] * 100.0));
                if (!(distance <= DISTANCE_LIMIT)) {
                    return finish(ctx, run, StepResult.fail(String.format(Locale.ROOT, "Bild in der Wand weicht vom Zuschauermodus ab: Abstand %.1f", distance)));
                }
                return finish(ctx, run, StepResult.pass());
            }
            case 30 -> {
                if (waited < 10 && PilotSession.INSTANCE.isActive()) {
                    return StepResult.pending();
                }
                return (StepResult) ctx.state().get("cov_ghost_result");
            }
            default -> {
                return finish(ctx, run, StepResult.fail("Stufe " + run.stage));
            }
        }
        return StepResult.pending();
    }

    private static StepResult finish(SelfTest.StepContext ctx, Run run, StepResult result) {
        Minecraft mc = Minecraft.getInstance();
        ExampleGhost.force(false);
        PilotSession.INSTANCE.clearTestInput();
        Screenshots.hideMarker(false);
        if (run.savedFov >= 0) {
            mc.options.fov().set(run.savedFov);
            if (mc.gui.hud.isHidden() != run.savedHide) {
                mc.gui.hud.toggle();
            }
            run.savedFov = -1;
        }
        if (!ctx.state().containsKey("cov_ghost_result")) {
            ctx.state().put("cov_ghost_result", result);
            Rig rig = RIG.get();
            if (rig != null) {
                run.pending = ctx.onServer(server -> unspectate(server, rig));
            }
            run.enter(30, ctx.tick());
        }
        return StepResult.pending();
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

    private static void prepare(MinecraftServer server) {
        ServerPlayer player = player(server);
        ServerLevel level = player.level();
        Rig rig = RIG.get();
        if (rig == null) {
            rig = new Rig(player.getBlockX(), player.getBlockY(), player.getBlockZ() + RIG_DZ);
            RIG.set(rig);
        }
        if (rig.home == null) {
            rig.home = player.position();
            rig.mode = player.gameMode.getGameModeForPlayer();
        }
        if (!rig.built) {
            for (int dx = -CLEAR_BACK; dx <= CLEAR_FRONT; dx++) {
                for (int dz = -CLEAR_SIDE; dz <= CLEAR_SIDE; dz++) {
                    BlockPos floor = new BlockPos(rig.x + dx, rig.y - 1, rig.z + dz);
                    rig.original.putIfAbsent(floor.immutable(), level.getBlockState(floor));
                    level.setBlockAndUpdate(floor, Blocks.STONE.defaultBlockState());
                    for (int dy = 0; dy < CLEAR_HEIGHT; dy++) {
                        BlockPos pos = new BlockPos(rig.x + dx, rig.y + dy, rig.z + dz);
                        boolean wall = dx >= WALL_DX && dx < WALL_DX + WALL_THICK && Math.abs(dz) <= WALL_HALF && dy < WALL_HEIGHT;
                        rig.original.putIfAbsent(pos.immutable(), level.getBlockState(pos));
                        level.setBlockAndUpdate(pos, wall ? Blocks.STONE.defaultBlockState() : Blocks.AIR.defaultBlockState());
                    }
                }
            }
            rig.built = true;
        }
        if (!(rig.droneId >= 0 && level.getEntity(rig.droneId) instanceof QuadcopterEntity)) {
            QuadcopterEntity drone = DroneModule.DRONE.get().create(level, EntitySpawnReason.COMMAND);
            if (drone == null) {
                throw new IllegalStateException("EntityType.create lieferte null");
            }
            drone.snapTo(rig.ground().x, rig.ground().y, rig.ground().z, 0.0F, 0.0F);
            drone.setBuild(DroneBuild.FREESTYLE);
            if (!level.addFreshEntity(drone)) {
                throw new IllegalStateException("addFreshEntity fehlgeschlagen");
            }
            rig.droneId = drone.getId();
        }
        QuadcopterEntity drone = serverDrone(server, rig.droneId);
        player.setGameMode(GameType.CREATIVE);
        if (DronePiloting.pilotedBy(player.getUUID()).filter(piloted -> piloted == drone).isEmpty()) {
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
        player.addTag(ExampleGhost.PERMISSION_TAG);
        drone.resetTo(rig.ground(), FACING_WALL);
    }

    private static void spectate(MinecraftServer server, Rig rig, Vec3 eye, float yaw, float pitch) {
        ServerPlayer player = player(server);
        Entity drone = player.level().getEntity(rig.droneId);
        if (drone instanceof QuadcopterEntity quad && quad.pilot().isPresent()) {
            DronePiloting.release(quad);
        }
        player.setGameMode(GameType.SPECTATOR);
        player.teleportTo(player.level(), eye.x, eye.y - player.getEyeHeight(), eye.z, Set.of(), yaw, pitch, true);
    }

    private static void unspectate(MinecraftServer server, Rig rig) {
        ServerPlayer player = player(server);
        Entity drone = player.level().getEntity(rig.droneId);
        if (drone instanceof QuadcopterEntity quad && quad.pilot().isPresent()) {
            DronePiloting.release(quad);
        }
        player.setGameMode(rig.mode == null ? GameType.CREATIVE : rig.mode);
        if (rig.home != null) {
            player.teleportTo(player.level(), rig.home.x, rig.home.y, rig.home.z, Set.of(), player.getYRot(), player.getXRot(), true);
        }
    }

    private static StepResult cleanupStep(SelfTest.StepContext ctx) {
        ExampleGhost.force(false);
        Rig rig = RIG.get();
        if (rig == null) {
            return StepResult.pass();
        }
        if (ctx.tick() == 0) {
            PilotSession.INSTANCE.clearTestInput();
            ctx.state().put(STATE, ctx.onServer(server -> {
                for (ServerLevel level : server.getAllLevels()) {
                    Entity entity = rig.droneId >= 0 ? level.getEntity(rig.droneId) : null;
                    if (entity instanceof QuadcopterEntity drone) {
                        if (drone.pilot().isPresent()) {
                            DronePiloting.release(drone);
                        }
                        drone.discard();
                    }
                }
                ServerPlayer player = player(server);
                player.removeTag(ExampleGhost.PERMISSION_TAG);
                player.setGameMode(rig.mode == null ? GameType.CREATIVE : rig.mode);
                if (rig.home != null) {
                    player.teleportTo(player.level(), rig.home.x, rig.home.y, rig.home.z, Set.of(), player.getYRot(), player.getXRot(), true);
                }
                if (rig.head != null) {
                    player.setItemSlot(EquipmentSlot.HEAD, rig.head);
                }
                if (rig.hand != null) {
                    player.setItemSlot(EquipmentSlot.MAINHAND, rig.hand);
                }
                ServerLevel level = player.level();
                for (Map.Entry<BlockPos, BlockState> entry : rig.original.entrySet()) {
                    level.setBlockAndUpdate(entry.getKey(), entry.getValue());
                }
                RIG.compareAndSet(rig, null);
            }));
            return StepResult.pending();
        }
        CompletableFuture<?> future = (CompletableFuture<?>) ctx.state().get(STATE);
        if (future.isCompletedExceptionally()) {
            return StepResult.fail("Aufräumen: " + future.exceptionNow().getMessage());
        }
        if (!future.isDone()) {
            return StepResult.pending();
        }
        return PilotSession.INSTANCE.isActive() ? (ctx.tick() > 100 ? StepResult.fail("Sitzung läuft nach dem Aufräumen weiter") : StepResult.pending())
                : StepResult.pass();
    }
    private static final class Noclip {
        int stage;
        int mark;
        @Nullable CompletableFuture<?> pending;
        int epoch;
        int crashes;
        double furthest = Double.NEGATIVE_INFINITY;
        String key = "-";
        boolean radioOn;
        @Nullable StepResult result;
        InputConstants.@Nullable Key savedKey;

        void enter(int stage, int tick) {
            this.stage = stage;
            this.mark = tick;
        }
    }

    private static void pushX(DroneSim sim, double vx) {
        SimSnapshot snapshot = new SimSnapshot();
        sim.copyStateTo(snapshot);
        snapshot.vx = vx;
        snapshot.vy = 0.0;
        snapshot.vz = 0.0;
        snapshot.sleeping = false;
        sim.copyStateFrom(snapshot);
    }

    private static String switchState() {
        for (de.lardtom.propwash.input.SwitchFunction function : de.lardtom.propwash.input.SwitchFunction.values()) {
            if (function.name().contains("NOCLIP")) {
                return function.name() + "=" + (de.lardtom.propwash.input.RadioState.INSTANCE.isActive(function) ? "an" : "aus");
            }
        }
        return "kein SwitchFunction-Wert NOCLIP";
    }

    private static final String NOCLIP_GUID = "0300c0a1c0a1a1a1c0a1a1a1000000c1";
    private static final int NOCLIP_AXIS = 7;

    private static void noclipRadio(boolean on) {
        de.lardtom.propwash.input.SwitchFunction function = null;
        for (de.lardtom.propwash.input.SwitchFunction each : de.lardtom.propwash.input.SwitchFunction.values()) {
            if (each.name().contains("NOCLIP")) {
                function = each;
            }
        }
        if (function == null) {
            return;
        }
        de.lardtom.propwash.input.client.TransmitterInput input = de.lardtom.propwash.input.client.TransmitterInput.INSTANCE;
        if (!input.simulating()) {
            de.lardtom.propwash.input.DeviceProfile profile = de.lardtom.propwash.input.ProfileDefaults.radio("Abdeckung Noclip", 8);
            profile.setBinding(function, de.lardtom.propwash.input.SwitchAssignment.axisMicros(NOCLIP_AXIS, 1700, 2000));
            profile.setupDone = true;
            de.lardtom.propwash.input.InputConfig next = de.lardtom.propwash.input.InputConfig.get().copy();
            next.profiles.put(NOCLIP_GUID, profile);
            next.sanitize();
            de.lardtom.propwash.input.InputConfig.commit(next);
            de.lardtom.propwash.input.client.SimulatedRadio radio = new de.lardtom.propwash.input.client.SimulatedRadio(NOCLIP_GUID, "Abdeckung Noclip",
                    profile, 8, 12).useSavedProfile();
            for (int i = 0; i < 8; i++) {
                radio.setAxis(i, -1.0F);
            }
            input.simulate(radio);
            NOCLIP_RADIO.set(radio);
        }
        de.lardtom.propwash.input.client.SimulatedRadio radio = NOCLIP_RADIO.get();
        if (radio != null) {
            radio.setMicros(NOCLIP_AXIS, on ? 2000 : 1000);
        }
    }

    private static void removeNoclipRadio() {
        if (NOCLIP_RADIO.getAndSet(null) != null) {
            de.lardtom.propwash.input.client.TransmitterInput.INSTANCE.simulate(null);
        }
        de.lardtom.propwash.input.InputConfig next = de.lardtom.propwash.input.InputConfig.get().copy();
        if (next.profiles.remove(NOCLIP_GUID) != null) {
            de.lardtom.propwash.input.InputConfig.commit(next);
        }
    }

    private static final AtomicReference<de.lardtom.propwash.input.client.SimulatedRadio> NOCLIP_RADIO = new AtomicReference<>();

    private static void pressNoclip(Noclip run) {
        KeyMapping mapping = KeyMapping.get(NOCLIP_KEY);
        if (mapping == null) {
            boolean on = !run.radioOn;
            noclipRadio(on);
            run.radioOn = on;
            run.key = "keine Tastenbelegung " + NOCLIP_KEY + " – Schalter der simulierten Funke (Kanal " + (NOCLIP_AXIS + 1) + ") " + (on ? "an" : "aus");
            return;
        }
        if (run.savedKey == null) {
            run.savedKey = InputConstants.getKey(mapping.saveString());
        }
        InputConstants.Key key = InputConstants.Type.KEYBOARD.getOrCreate(InputConstants.KEY_F9);
        mapping.setKey(key);
        KeyMapping.resetMapping();
        KeyMapping.set(key, true);
        KeyMapping.click(key);
        run.key = NOCLIP_KEY + " auf F9 gedrückt";
    }

    private static void releaseNoclip(Noclip run, boolean restore) {
        KeyMapping mapping = KeyMapping.get(NOCLIP_KEY);
        if (mapping == null) {
            return;
        }
        KeyMapping.set(InputConstants.Type.KEYBOARD.getOrCreate(InputConstants.KEY_F9), false);
        if (restore && run.savedKey != null) {
            mapping.setKey(run.savedKey);
            KeyMapping.resetMapping();
        }
    }

    private static StepResult noclipStep(SelfTest.StepContext ctx) {
        if (!de.lardtom.propwash.platform.Services.PLATFORM.isModLoaded(NOCLIP_MOD)) {
            ctx.log("Noclip nicht geladen – übersprungen (Lauf mit dem Noclip-Jar in mods/)");
            return StepResult.pass();
        }
        Noclip run = (Noclip) ctx.state().computeIfAbsent("cov_noclip", key -> new Noclip());
        if (run.pending != null) {
            if (run.pending.isCompletedExceptionally()) {
                return noclipDone(ctx, run, StepResult.fail("Server-Aktion in Stufe " + run.stage + ": " + run.pending.exceptionNow().getMessage()));
            }
            if (!run.pending.isDone()) {
                return StepResult.pending();
            }
            run.pending = null;
        }
        if (run.result != null) {
            return PilotSession.INSTANCE.isActive() && ctx.tick() - run.mark < 100 ? StepResult.pending() : run.result;
        }
        int waited = ctx.tick() - run.mark;
        if (run.stage == 0) {
            ExampleGhost.force(false);
            input(0.0F, false);
            run.pending = ctx.onServer(CovGhostSelfTests::prepare);
            run.enter(1, ctx.tick());
            return StepResult.pending();
        }
        Rig rig = RIG.get();
        PilotSession session = PilotSession.INSTANCE;
        QuadcopterEntity drone = session.drone();
        DroneSim sim = session.sim();
        if (rig == null || drone == null || sim == null || drone.getId() != rig.droneId || !session.isFpv()) {
            return waited > 160 ? noclipDone(ctx, run, StepResult.fail("keine FPV-Sitzung mit der Testdrohne in Stufe " + run.stage)) : StepResult.pending();
        }
        Telemetry t = sim.telemetry();
        switch (run.stage) {
            case 1 -> {
                input(0.0F, false);
                if (!near(drone, rig.ground())) {
                    return waited > 120 ? noclipDone(ctx, run, StepResult.fail("Drohne nicht aufs Testfeld gesetzt")) : StepResult.pending();
                }
                run.enter(2, ctx.tick());
            }
            case 2 -> {
                input(0.0F, RigArming.armSwitch(waited, t.armed, t.grounded || t.sleeping));
                if (!t.armed) {
                    return waited > 200 ? noclipDone(ctx, run, StepResult.fail("nicht scharf: " + t.armBlockers)) : StepResult.pending();
                }
                Vec3 air = rig.air();
                run.pending = ctx.onServer(server -> serverDrone(server, rig.droneId).resetTo(air, FACING_WALL));
                run.enter(3, ctx.tick());
            }
            case 3 -> {
                input((float) t.hoverThrottleStick, true);
                if (!near(drone, rig.air())) {
                    return waited > 120 ? noclipDone(ctx, run, StepResult.fail("Drohne nicht vor die Wand gesetzt")) : StepResult.pending();
                }
                if (waited < 10) {
                    return StepResult.pending();
                }
                String before = switchState();
                pressNoclip(run);
                ctx.log("Noclip-Taste: " + run.key + ", Schalter vorher " + before);
                run.enter(4, ctx.tick());
            }
            case 4 -> {
                input((float) t.hoverThrottleStick, true);
                if (waited < 10) {
                    return StepResult.pending();
                }
                releaseNoclip(run, false);
                run.epoch = drone.resetEpoch();
                run.crashes = session.crashCount();
                ctx.log("Schalter nach dem Drücken " + switchState());
                run.enter(5, ctx.tick());
            }
            case 5 -> {
                input((float) t.hoverThrottleStick, true);
                run.furthest = Math.max(run.furthest, drone.getX());
                double far = rig.x + WALL_DX + WALL_THICK;
                double half = drone.getBbWidth() / 2.0;
                if (drone.getX() - half <= far + 0.3 && waited < PUSH_TICKS) {
                    pushX(sim, FAST);
                    return StepResult.pending();
                }
                pushX(sim, 0.0);
                boolean through = drone.getX() - half > far + 0.3;
                String outcome = through ? "Noclip wirkt" : "Schalter ohne Wirkung";
                ctx.log(String.format(Locale.ROOT, "%s: weitester Punkt x %.2f, Wand %.0f bis %.0f, Rücksetzungen %d, Abstürze %d, %s (für die Nachricht an bluepanee)",
                        outcome, run.furthest, far - WALL_THICK, far, drone.resetEpoch() - run.epoch, session.crashCount() - run.crashes, switchState()));
                pressNoclip(run);
                run.enter(6, ctx.tick());
            }
            case 6 -> {
                input((float) t.hoverThrottleStick, true);
                if (waited < 4) {
                    return StepResult.pending();
                }
                releaseNoclip(run, true);
                return noclipDone(ctx, run, StepResult.pass());
            }
            default -> {
                return noclipDone(ctx, run, StepResult.fail("Stufe " + run.stage));
            }
        }
        return StepResult.pending();
    }

    private static StepResult noclipDone(SelfTest.StepContext ctx, Noclip run, StepResult result) {
        releaseNoclip(run, true);
        removeNoclipRadio();
        PilotSession.INSTANCE.clearTestInput();
        run.result = result;
        run.mark = ctx.tick();
        Rig rig = RIG.get();
        if (rig != null) {
            run.pending = ctx.onServer(server -> unspectate(server, rig));
        }
        return StepResult.pending();
    }
}
