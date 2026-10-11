package de.lardtom.propwash.drone.client.render;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.platform.NativeImage;
import de.lardtom.propwash.Constants;
import de.lardtom.propwash.api.build.DroneData;
import de.lardtom.propwash.api.build.DronePreset;
import de.lardtom.propwash.apiimpl.ApiExtensions;
import de.lardtom.propwash.apiimpl.Conversions;
import de.lardtom.propwash.build.DroneBuild;
import de.lardtom.propwash.build.VideoSystem;
import de.lardtom.propwash.content.ContentIds;
import de.lardtom.propwash.core.CoreComponents;
import de.lardtom.propwash.dev.SelfTest;
import de.lardtom.propwash.dev.SelfTest.StepResult;
import de.lardtom.propwash.dev.client.Screenshots;
import de.lardtom.propwash.dev.example.ExampleAddon;
import de.lardtom.propwash.drone.DroneInteractions;
import de.lardtom.propwash.drone.DroneModule;
import de.lardtom.propwash.drone.DronePiloting;
import de.lardtom.propwash.drone.PilotMode;
import de.lardtom.propwash.drone.QuadcopterEntity;
import de.lardtom.propwash.drone.client.PilotSession;
import de.lardtom.propwash.rescue.client.RescueController;
import de.lardtom.propwash.sim.BuildAnalysis;
import de.lardtom.propwash.sim.CameraOcclusion;
import de.lardtom.propwash.sim.FpvRig;
import de.lardtom.propwash.video.ImageArea;
import de.lardtom.propwash.video.ImageFormat;
import de.lardtom.propwash.video.client.CleanFrameTap;
import de.lardtom.propwash.video.client.FpvImage;
import de.lardtom.propwash.video.client.VideoConfig;
import de.lardtom.propwash.video.client.VideoSettings;
import de.lardtom.propwash.video.client.VideoState;
import net.minecraft.client.Screenshot;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.network.protocol.game.ClientboundSetHeldSlotPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Util;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.joml.Vector3fc;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

public final class FpvCoverageSelfTests {
    public static final String STEP = "drone-render: Props im FPV-Bild – Analyse gegen gerendertes Bild";
    public static final String DRAWN_STEP = "drone-render: Props im FPV-Bild wurden gezeichnet";
    public static final String BOXED_STEP = "drone-render: Props im FPV-Bild 4:3 – Analyse gegen gerendertes Bild im 4:3-Bereich";
    public static final String BOXED_SHOT_DIR = "fpv_coverage_4_3";
    static final int DRAWN_FRAMES = 10;
    public static final int KEY_RGB = 0xFF00FF;
    public static final double ABSOLUTE_TOLERANCE = 1.0;
    public static final double RELATIVE_TOLERANCE = 0.08;
    public static final double VISIBLE_PERCENT = 0.02;
    public static final String SHOT_DIR = "fpv_coverage";
    static final int TICKS_PER_BUILD = 160;
    static final int SESSION_TICKS = 300;
    private static final int SETTLE_TICKS = 8;
    private static final int DROP_HEIGHT = 100;
    private static final String PHASE = "phase";
    private static final String INDEX = "index";
    private static final String SINCE = "since";
    private static final String FUTURE = "future";
    private static final String COUNT = "count";
    private static final String CLEANUP = "cleanup";
    private static final String FAILURES = "failures";
    private static final String CONFIG = "config";
    private static final String VIDEO = "video";
    private static final String RETRY = "retry";
    private static final AtomicInteger DRONE = new AtomicInteger(-1);
    private static final List<String> REPORT = new ArrayList<>();
    private static final List<String> BOXED_REPORT = new ArrayList<>();
    private static final Set<Identifier> SHOT_FRAMES = new HashSet<>();
    private static final List<String> SHOT_FAILURES = new ArrayList<>();

    private FpvCoverageSelfTests() {
    }

    public static void register() {
        SelfTest.clientStep(DRAWN_STEP, 400, FpvCoverageSelfTests::drawnStep);
        SelfTest.clientStep(STEP, TICKS_PER_BUILD * 48 + SESSION_TICKS * 4, ctx -> step(ctx, false));
        SelfTest.clientStep(BOXED_STEP, TICKS_PER_BUILD * 12 + SESSION_TICKS * 4, ctx -> step(ctx, true));
    }

    private static StepResult drawnStep(SelfTest.StepContext ctx) {
        Map<String, Object> state = ctx.state();
        PilotSession session = PilotSession.INSTANCE;
        String phase = (String) state.getOrDefault(PHASE, "spawn");
        switch (phase) {
            case "spawn" -> {
                FpvProps.maskColor(-1);
                if (session.isActive()) {
                    session.requestStop();
                    return StepResult.pending();
                }
                DroneRenderConfig saved = DroneRenderConfig.get().copy();
                state.put(CONFIG, saved);
                DroneRenderConfig config = saved.copy();
                config.fpvProps = true;
                DroneRenderConfig.CONFIG.set(config);
                state.put(FUTURE, ctx.onServer(server -> start(server, DroneBuild.FREESTYLE)));
                state.put(PHASE, "fly");
                return StepResult.pending();
            }
            case "fly" -> {
                CompletableFuture<?> future = (CompletableFuture<?>) state.get(FUTURE);
                if (future.isCompletedExceptionally()) {
                    return finishDrawn(ctx, "FPV-Start " + future.exceptionNow());
                }
                QuadcopterEntity drone = session.drone();
                if (!future.isDone() || !session.isFpv() || drone == null) {
                    return ctx.tick() > 300 ? finishDrawn(ctx, "keine FPV-Sitzung") : StepResult.pending();
                }
                state.put(COUNT, new int[]{FpvProps.propsDrawn(), FpvProps.framesDrawn(), ctx.tick()});
                state.put(PHASE, "count");
                return StepResult.pending();
            }
            case "count" -> {
                int[] before = (int[]) state.get(COUNT);
                if (ctx.tick() - before[2] < DRAWN_FRAMES) {
                    return StepResult.pending();
                }
                int props = FpvProps.propsDrawn() - before[0];
                int frames = FpvProps.framesDrawn() - before[1];
                ctx.log("FPV-Bild: " + props + " Props in " + frames + " Bildern gezeichnet");
                return finishDrawn(ctx, props > 0 ? null : "während des FPV-Flugs keine Props gezeichnet (" + frames + " Bilder)");
            }
            case "cleanup" -> {
                if (session.isActive()) {
                    session.requestStop();
                    return StepResult.pending();
                }
                CompletableFuture<?> cleanup = (CompletableFuture<?>) state.computeIfAbsent(CLEANUP, key -> ctx.onServer(FpvCoverageSelfTests::cleanup));
                if (!cleanup.isDone()) {
                    return StepResult.pending();
                }
                String problem = (String) state.get(FAILURES);
                return problem == null ? StepResult.pass() : StepResult.fail(problem);
            }
            default -> {
                return StepResult.fail("unbekannte Phase " + phase);
            }
        }
    }

    private static StepResult finishDrawn(SelfTest.StepContext ctx, String problem) {
        Map<String, Object> state = ctx.state();
        if (problem != null) {
            state.put(FAILURES, problem);
        }
        DroneRenderConfig saved = (DroneRenderConfig) state.get(CONFIG);
        if (saved != null) {
            DroneRenderConfig.CONFIG.set(saved);
        }
        PilotSession.INSTANCE.clearTestInput();
        RescueController.INSTANCE.clearHome();
        state.put(PHASE, "cleanup");
        return StepResult.pending();
    }

    public static List<String> report() {
        return List.copyOf(REPORT);
    }

    public static List<String> boxedReport() {
        return List.copyOf(BOXED_REPORT);
    }

    static List<DroneBuild> boxedBuilds() {
        Set<DroneBuild> out = new LinkedHashSet<>(DroneBuild.PRESETS);
        for (DroneData data : List.of(ExampleAddon.stretched(), ExampleAddon.xclass())) {
            Conversions.build(data.build()).ifPresent(out::add);
        }
        return new ArrayList<>(out);
    }

    static List<DroneBuild> builds() {
        Set<DroneBuild> out = new LinkedHashSet<>(DroneBuild.PRESETS);
        for (DroneData data : List.of(ExampleAddon.deadcat(), ExampleAddon.stretched(), ExampleAddon.xclass())) {
            Conversions.build(data.build()).ifPresent(out::add);
        }
        for (DronePreset preset : ApiExtensions.registeredPresets()) {
            Conversions.build(preset.data().build()).ifPresent(out::add);
        }
        return new ArrayList<>(out);
    }

    public static String label(DroneBuild build) {
        return build.frame().id() + "/" + build.motor().id().getPath() + "/" + build.prop().id().getPath() + "/" + build.video().id().getPath();
    }

    @SuppressWarnings("unchecked")
    private static StepResult step(SelfTest.StepContext ctx, boolean boxed) {
        Map<String, Object> state = ctx.state();
        List<DroneBuild> builds = boxed ? boxedBuilds() : builds();
        List<String> report = boxed ? BOXED_REPORT : REPORT;
        int index = (int) state.computeIfAbsent(INDEX, key -> 0);
        List<String> failures = (List<String>) state.computeIfAbsent(FAILURES, key -> new ArrayList<String>());
        if (index == 0 && !state.containsKey(PHASE) && !state.containsKey(CONFIG)) {
            report.clear();
            SHOT_FRAMES.clear();
            synchronized (SHOT_FAILURES) {
                SHOT_FAILURES.clear();
            }
            DroneRenderConfig saved = DroneRenderConfig.get().copy();
            state.put(CONFIG, saved);
            DroneRenderConfig config = saved.copy();
            config.fpvProps = true;
            DroneRenderConfig.CONFIG.set(config);
            if (boxed) {
                VideoConfig video = VideoSettings.get().copy();
                state.put(VIDEO, video.copy());
                video.imageFormat = ImageFormat.FOUR_THREE;
                VideoSettings.CONFIG.set(video);
            }
        }
        if (index >= builds.size()) {
            FpvProps.maskColor(-1);
            if (PilotSession.INSTANCE.isActive()) {
                PilotSession.INSTANCE.requestStop();
                return StepResult.pending();
            }
            CompletableFuture<?> cleanup = (CompletableFuture<?>) state.computeIfAbsent(CLEANUP, key -> ctx.onServer(FpvCoverageSelfTests::cleanup));
            if (!cleanup.isDone()) {
                return StepResult.pending();
            }
            for (String line : report) {
                ctx.log(line);
            }
            synchronized (SHOT_FAILURES) {
                failures.addAll(SHOT_FAILURES);
            }
            if (Screenshots.enabled()) {
                ctx.log("Bildschirmfotos: " + SHOT_FRAMES.size() + " Rahmen nach " + Screenshots.dir().resolve(boxed ? BOXED_SHOT_DIR : SHOT_DIR));
            }
            DroneRenderConfig saved = (DroneRenderConfig) state.get(CONFIG);
            if (saved != null) {
                DroneRenderConfig.CONFIG.set(saved);
            }
            if (state.get(VIDEO) instanceof VideoConfig video) {
                VideoSettings.CONFIG.set(video);
            }
            return StepResult.check(failures.isEmpty(), String.join("; ", failures));
        }
        DroneBuild build = builds.get(index);
        String phase = (String) state.getOrDefault(PHASE, "spawn");
        PilotSession session = PilotSession.INSTANCE;
        switch (phase) {
            case "spawn" -> {
                FpvProps.maskColor(-1);
                if (session.isActive()) {
                    session.requestStop();
                    return StepResult.pending();
                }
                if (!FpvGuardSelfTests.serverReleased(ctx, state)) {
                    return StepResult.pending();
                }
                state.put(FUTURE, ctx.onServer(server -> start(server, build)));
                state.put(SINCE, ctx.tick());
                state.put(PHASE, "fly");
                return StepResult.pending();
            }
            case "fly" -> {
                CompletableFuture<?> future = (CompletableFuture<?>) state.get(FUTURE);
                if (future.isCompletedExceptionally()) {
                    return next(state, failures, label(build) + ": FPV-Start " + future.exceptionNow(), builds.size());
                }
                QuadcopterEntity drone = session.drone();
                if (!future.isDone() || !session.isFpv() || drone == null || !build.equals(drone.build())) {
                    if (ctx.tick() - (int) state.get(SINCE) > SESSION_TICKS && !Integer.valueOf(index).equals(state.get(RETRY))) {
                        state.put(RETRY, index);
                        state.put(PHASE, "spawn");
                        ctx.log(label(build) + ": keine FPV-Sitzung nach " + SESSION_TICKS + " Ticks, zweiter Start");
                        return StepResult.pending();
                    }
                    return timeout(ctx, state, failures, build, "keine FPV-Sitzung", builds.size(), SESSION_TICKS);
                }
                FpvProps.maskColor(KEY_RGB);
                state.put(SINCE, ctx.tick());
                state.put(PHASE, "settle");
                return StepResult.pending();
            }
            case "settle" -> {
                if (ctx.tick() - (int) state.get(SINCE) < SETTLE_TICKS) {
                    return StepResult.pending();
                }
                AtomicReference<int[]> counted = new AtomicReference<>();
                state.put(COUNT, counted);
                Path file = Screenshots.enabled() && SHOT_FRAMES.add(build.frame().id()) ? shotFile(build, boxed) : null;
                CleanFrameTap.request(target -> {
                    ImageArea area = FpvImage.area(target.width, target.height);
                    Screenshot.takeScreenshot(target, 1, image -> {
                        try (image) {
                            counted.set(count(image, area));
                        }
                    });
                    if (file != null) {
                        save(target, file, area);
                    }
                });
                state.put(SINCE, ctx.tick());
                state.put(PHASE, "capture");
                return StepResult.pending();
            }
            case "capture" -> {
                int[] counted = ((AtomicReference<int[]>) state.get(COUNT)).get();
                if (counted == null) {
                    return timeout(ctx, state, failures, build, "kein Bild vor dem Videoeffekt", builds.size());
                }
                FpvProps.maskColor(-1);
                String problem = evaluate(build, session, counted, report);
                return next(state, failures, problem, builds.size());
            }
            default -> {
                return StepResult.fail("unbekannte Phase " + phase);
            }
        }
    }

    private static StepResult timeout(SelfTest.StepContext ctx, Map<String, Object> state, List<String> failures, DroneBuild build, String what,
                                      int total) {
        return timeout(ctx, state, failures, build, what, total, TICKS_PER_BUILD);
    }

    private static StepResult timeout(SelfTest.StepContext ctx, Map<String, Object> state, List<String> failures, DroneBuild build, String what,
                                      int total, int limit) {
        if (ctx.tick() - (int) state.get(SINCE) > limit) {
            FpvProps.maskColor(-1);
            return next(state, failures, label(build) + ": " + what, total);
        }
        return StepResult.pending();
    }

    private static StepResult next(Map<String, Object> state, List<String> failures, String problem, int total) {
        if (problem != null) {
            failures.add(problem);
        }
        state.put(INDEX, (int) state.get(INDEX) + 1);
        state.put(PHASE, "spawn");
        if ((int) state.get(INDEX) >= total) {
            PilotSession.INSTANCE.clearTestInput();
            RescueController.INSTANCE.clearHome();
        }
        return StepResult.pending();
    }

    public static Path shotFile(DroneBuild build) {
        return shotFile(build, false);
    }

    public static Path shotFile(DroneBuild build, boolean boxed) {
        Identifier frame = build.frame().id();
        return Screenshots.dir().resolve(boxed ? BOXED_SHOT_DIR : SHOT_DIR).resolve(frame.getNamespace() + "_" + frame.getPath() + ".png");
    }

    private static void save(RenderTarget target, Path file, ImageArea area) {
        int factor = Screenshots.downscale(target.width, target.height);
        Screenshot.takeScreenshot(target, factor, image -> Util.ioPool().execute(() -> {
            try (image) {
                Files.createDirectories(file.getParent());
                if (area.full() || area.screenWidth() != target.width || area.screenHeight() != target.height) {
                    image.writeToFile(file);
                    return;
                }
                int x = Math.min(image.getWidth() - 1, area.x() / factor);
                int y = Math.min(image.getHeight() - 1, area.y() / factor);
                int w = Math.max(1, Math.min(image.getWidth() - x, area.width() / factor));
                int h = Math.max(1, Math.min(image.getHeight() - y, area.height() / factor));
                try (NativeImage cropped = new NativeImage(w, h, false)) {
                    image.copyRect(cropped, x, y, 0, 0, w, h, false, false);
                    cropped.writeToFile(file);
                }
            } catch (IOException | RuntimeException e) {
                synchronized (SHOT_FAILURES) {
                    SHOT_FAILURES.add("Bildschirmfoto " + file + ": " + e);
                }
            }
        }));
    }

    static int[] count(NativeImage image) {
        return count(image, ImageArea.full(image.getWidth(), image.getHeight()));
    }

    static int[] count(NativeImage image, ImageArea area) {
        if (area.screenWidth() != image.getWidth() || area.screenHeight() != image.getHeight()) {
            area = ImageArea.full(image.getWidth(), image.getHeight());
        }
        int hits = 0;
        for (int y = area.y(); y < area.bottom(); y++) {
            for (int x = area.x(); x < area.right(); x++) {
                if (isKey(image.getPixel(x, y))) {
                    hits++;
                }
            }
        }
        return new int[]{hits, area.width(), area.height()};
    }

    static boolean isKey(int argb) {
        int r = (argb >> 16) & 0xFF;
        int g = (argb >> 8) & 0xFF;
        int b = argb & 0xFF;
        int low = Math.min(r, b);
        return low >= 32 && g * 3 <= low && Math.abs(r - b) * 3 <= Math.max(r, b);
    }

    private static String evaluate(DroneBuild build, PilotSession session, int[] counted, List<String> report) {
        int width = counted[1];
        int height = counted[2];
        double rendered = 100.0 * counted[0] / ((double) width * height);
        QuadcopterEntity drone = session.drone();
        float tilt = drone != null ? FpvProps.cameraTilt(drone) : Float.NaN;
        double fov = VideoState.INSTANCE.horizontalFov();
        FpvRig rig = FpvRig.of(build);
        double analysis = CameraOcclusion.percent(rig, tilt, fov, (double) width / height, height);
        double standard = BuildAnalysis.of(build).propsInViewPercent();
        String line = String.format(Locale.ROOT, "%s: Bild %.2f %%, Analyse %.2f %% (Werkbank %.2f %%, %.0f°, Uptilt %.1f°, %d×%d)",
                label(build), rendered, analysis, standard, fov, tilt, width, height);
        report.add(line);
        Constants.LOG.info("FPV-Abdeckung {}", line);
        String geometry = geometry(build, rig);
        if (geometry != null) {
            return label(build) + ": " + geometry;
        }
        if (Math.abs(fov - rig.horizontalFovDeg()) > 1.0E-6 || Math.abs(tilt - rig.tiltDeg()) > 1.0E-4) {
            return line + String.format(Locale.ROOT, " – Sichtfeld/Uptilt im Bild %.1f°/%.1f°, Analyse %.1f°/%.1f°", fov, tilt, rig.horizontalFovDeg(),
                    rig.tiltDeg());
        }
        double tolerance = Math.max(ABSOLUTE_TOLERANCE, RELATIVE_TOLERANCE * Math.max(rendered, analysis));
        if (Math.abs(rendered - analysis) > tolerance) {
            return line;
        }
        if (rendered > VISIBLE_PERCENT && analysis <= 0.0 || analysis > VISIBLE_PERCENT && rendered <= 0.0) {
            return line + " – sichtbar gegen 0 %";
        }
        return null;
    }

    static String geometry(DroneBuild build, FpvRig rig) {
        DroneAssembly assembly = DroneVisuals.resources().assembly(build);
        for (int slot = 0; slot < DroneLayout.SLOTS; slot++) {
            Vector3fc hub = assembly.blur(slot).position();
            double[] expected = rig.hubMm(slot);
            if (Math.abs(hub.x() - expected[0]) > 0.01 || Math.abs(hub.y() - expected[1]) > 0.01 || Math.abs(hub.z() - expected[2]) > 0.01) {
                return String.format(Locale.ROOT, "Prop %d im Modell bei (%.2f, %.2f, %.2f), Analyse (%.2f, %.2f, %.2f)", slot, hub.x(), hub.y(), hub.z(),
                        expected[0], expected[1], expected[2]);
            }
        }
        Vector3fc lens = FpvProps.lens(build);
        double[] expected = rig.lensMm();
        if (Math.abs(lens.x() - expected[0]) > 0.01 || Math.abs(lens.y() - expected[1]) > 0.01 || Math.abs(lens.z() - expected[2]) > 0.01) {
            return "Objektiv weicht ab";
        }
        return null;
    }

    public static void start(MinecraftServer server, DroneBuild build) {
        ServerPlayer player = server.getPlayerList().getPlayers().getFirst();
        ServerLevel level = player.level();
        discard(server);
        int x = player.getBlockX() + 24;
        int z = player.getBlockZ() + 24;
        level.getChunk(x >> 4, z >> 4);
        int y = Math.min(level.getMaxY() - 8, level.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z) + DROP_HEIGHT);
        QuadcopterEntity drone = DroneModule.DRONE.get().create(level, EntitySpawnReason.COMMAND);
        if (drone == null) {
            throw new IllegalStateException("Drohne nicht erzeugt");
        }
        ItemStack stack = new ItemStack(DroneModule.DRONE_ITEM.get());
        stack.set(CoreComponents.DRONE_BUILD.get(), build);
        drone.loadFromStack(stack);
        Vec3 pos = Vec3.atBottomCenterOf(new BlockPos(x, y, z));
        drone.snapTo(pos.x, pos.y, pos.z, 0.0F, 0.0F);
        level.addFreshEntity(drone);
        drone.resetTo(pos, new Quaternionf().rotationY((float) Math.PI));
        DRONE.set(drone.getId());
        ItemStack remote = new ItemStack(DroneModule.REMOTE.get());
        remote.set(CoreComponents.BOUND_DRONE.get(), DroneInteractions.binding(drone));
        player.getInventory().setItem(0, remote);
        player.getInventory().setSelectedSlot(0);
        player.connection.send(new ClientboundSetHeldSlotPacket(0));
        ItemStack goggles = ContentIds.item(build.video().system == VideoSystem.DIGITAL ? ContentIds.GOGGLES_DIGITAL : ContentIds.GOGGLES_ANALOG)
                .map(ItemStack::new).orElse(ItemStack.EMPTY);
        goggles.set(CoreComponents.BOUND_DRONE.get(), DroneInteractions.binding(drone));
        player.setItemSlot(EquipmentSlot.HEAD, goggles);
        DronePiloting.StartResult result = DronePiloting.tryStart(player, drone, PilotMode.FPV);
        if (result != DronePiloting.StartResult.STARTED) {
            throw new IllegalStateException("Start verweigert: " + result);
        }
    }

    public static void cleanup(MinecraftServer server) {
        discard(server);
        ServerPlayer player = server.getPlayerList().getPlayers().getFirst();
        player.setItemSlot(EquipmentSlot.HEAD, ItemStack.EMPTY);
        player.getInventory().setItem(0, ItemStack.EMPTY);
    }

    private static void discard(MinecraftServer server) {
        int id = DRONE.getAndSet(-1);
        if (id < 0) {
            return;
        }
        for (ServerLevel level : server.getAllLevels()) {
            Entity entity = level.getEntity(id);
            if (entity instanceof QuadcopterEntity drone) {
                if (drone.pilot().isPresent()) {
                    DronePiloting.release(drone);
                }
                drone.discard();
            }
        }
    }
}
