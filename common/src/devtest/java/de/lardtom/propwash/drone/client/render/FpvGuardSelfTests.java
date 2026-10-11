package de.lardtom.propwash.drone.client.render;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.vertex.PoseStack;
import de.lardtom.propwash.Constants;
import de.lardtom.propwash.api.build.DronePreset;
import de.lardtom.propwash.apiimpl.ApiExtensions;
import de.lardtom.propwash.apiimpl.Conversions;
import de.lardtom.propwash.build.DroneBuild;
import de.lardtom.propwash.build.DronePaint;
import de.lardtom.propwash.build.PaintChannel;
import de.lardtom.propwash.build.PaintSlot;
import de.lardtom.propwash.build.PartWear;
import de.lardtom.propwash.build.StackType;
import de.lardtom.propwash.build.VideoSystem;
import de.lardtom.propwash.content.ContentIds;
import de.lardtom.propwash.core.CoreComponents;
import de.lardtom.propwash.core.FlightTune;
import de.lardtom.propwash.dev.SelfTest;
import de.lardtom.propwash.dev.SelfTest.StepResult;
import de.lardtom.propwash.drone.CameraMount;
import de.lardtom.propwash.drone.DroneInteractions;
import de.lardtom.propwash.drone.DroneModule;
import de.lardtom.propwash.drone.DronePiloting;
import de.lardtom.propwash.drone.PilotMode;
import de.lardtom.propwash.drone.QuadcopterEntity;
import de.lardtom.propwash.drone.client.PilotSession;
import de.lardtom.propwash.rescue.client.RescueController;
import de.lardtom.propwash.video.ImageArea;
import de.lardtom.propwash.video.client.CleanFrameTap;
import de.lardtom.propwash.video.client.FpvImage;
import de.lardtom.propwash.video.client.VideoState;
import net.minecraft.client.Screenshot;
import net.minecraft.client.renderer.SubmitNodeStorage;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.core.BlockPos;
import net.minecraft.network.protocol.game.ClientboundSetHeldSlotPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.joml.Vector3fc;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

public final class FpvGuardSelfTests {
    public static final String GEOMETRY_STEP = "drone-render: Guards im FPV-Bild (Geometrie, Lack, Schalter)";
    public static final String STEP = "drone-render: Guards im FPV-Bild – Analyse gegen gerendertes Bild";
    public static final int KEY_RGB = 0x00FF00;
    public static final float[] TILTS = {0.0F, 20.0F, 40.0F};
    public static final double ABSOLUTE_TOLERANCE = 0.5;
    public static final double RELATIVE_TOLERANCE = 0.08;
    public static final double VISIBLE_PERCENT = 0.02;
    public static final double[] MAX_PERCENT = {30.0, 18.0, 5.0};
    public static final double UPPER_PERCENT = 0.05;
    static final int TICKS_PER_SHOT = 200;
    private static final int SETTLE_TICKS = 6;
    private static final int DROP_HEIGHT = 100;
    private static final String PHASE = "phase";
    private static final String INDEX = "index";
    private static final String SINCE = "since";
    private static final String FUTURE = "future";
    private static final String COUNT = "count";
    private static final String CLEANUP = "cleanup";
    private static final String FAILURES = "failures";
    private static final String CONFIG = "config";
    private static final String RELEASE_CHECK = "releaseCheck";
    private static final String RELEASE_FREE = "releaseFree";
    private static final String RELEASE_WAIT = "releaseWait";
    private static final int RELEASE_FALLBACK_TICKS = 100;
    private static final AtomicInteger DRONE = new AtomicInteger(-1);
    private static final List<String> REPORT = new ArrayList<>();

    private FpvGuardSelfTests() {
    }

    public static void register() {
        SelfTest.clientStep(GEOMETRY_STEP, 1, ctx -> geometryStep());
        SelfTest.clientStep(STEP, TICKS_PER_SHOT * 30, FpvGuardSelfTests::step);
    }

    public static List<String> report() {
        return List.copyOf(REPORT);
    }

    public static DroneBuild withCreativeFc(DroneBuild build) {
        return new DroneBuild(build.frame(), StackType.CREATIVE_FC, build.motor(), build.prop(), build.video(), build.battery());
    }

    static List<DroneBuild> builds() {
        Set<DroneBuild> out = new LinkedHashSet<>();
        out.add(withCreativeFc(DroneBuild.WHOOP));
        out.add(withCreativeFc(DroneBuild.CINEWHOOP));
        for (DronePreset preset : ApiExtensions.registeredPresets()) {
            Conversions.build(preset.data().build()).filter(build -> build.frame().ducted).map(FpvGuardSelfTests::withCreativeFc).ifPresent(out::add);
        }
        return new ArrayList<>(out);
    }

    private static StepResult geometryStep() {
        DroneRenderResources res = DroneVisuals.resources();
        List<String> problems = new ArrayList<>();
        for (DroneBuild build : List.of(DroneBuild.WHOOP, DroneBuild.CINEWHOOP)) {
            FpvGuardMesh mesh = FpvGuards.mesh(res.assembly(build).frame(), build.frame());
            if (mesh.quads() < 4 * 24) {
                problems.add(build.frame().key() + ": nur " + mesh.quads() + " Guard-Flächen");
                continue;
            }
            DronePaint paint = DronePaint.DEFAULT.with(PaintSlot.of(PaintChannel.FRAME, 0), 0x3070E0).with(PaintSlot.of(PaintChannel.TPU, 0), 0xE04030);
            DroneRenderState painted = DroneVisuals.previewState(build, PartWear.PRISTINE, paint);
            if (painted == null) {
                problems.add(build.frame().key() + ": keine Vorschau");
                continue;
            }
            FpvGuardMesh lacquered = FpvGuards.mesh(painted.frame, build.frame());
            if (lacquered.quads() != mesh.quads()) {
                problems.add(build.frame().key() + ": lackierte Guards " + lacquered.quads() + " statt " + mesh.quads() + " Flächen");
            } else if (!tinted(lacquered)) {
                problems.add(build.frame().key() + ": Lackierung fehlt an den Guards im FPV-Bild");
            }
            FpvPropGeometry geometry = FpvProps.geometry(build, 20.0F);
            if (geometry == null || geometry.guardPoints() == 0) {
                problems.add(build.frame().key() + ": keine Guard-Punkte in der FPV-Geometrie");
            }
        }
        for (DroneBuild build : List.of(DroneBuild.FREESTYLE, DroneBuild.RACER, DroneBuild.LONG_RANGE)) {
            if (!FpvGuards.mesh(res.assembly(build).frame(), build.frame()).isEmpty()) {
                problems.add(build.frame().key() + ": Guards ohne Ducts");
            }
        }
        DroneRenderState state = new DroneRenderState();
        DroneVisuals.fill(state, DroneBuild.WHOOP, true, false, new float[]{0.1F, 1.2F, 2.3F, 3.4F}, new float[]{0.6F, 0.6F, 0.6F, 0.6F}, 10.0F);
        DroneVisuals.placeInWorld(state);
        state.hidden = true;
        state.fpvGeometry = FpvProps.geometry(DroneBuild.WHOOP, 20.0F);
        state.fpvGuards = FpvGuards.mesh(state.frame, DroneBuild.WHOOP.frame());
        CameraRenderState camera = new CameraRenderState();
        camera.projectionMatrix.setPerspective((float) Math.toRadians(100.0), 16.0F / 9.0F, 0.05F, 256.0F);
        PoseStack poseStack = new PoseStack();
        int before = FpvProps.guardFrames();
        FpvProps.submit(state, poseStack, new SubmitNodeStorage(), camera);
        if (FpvProps.guardFrames() != before + 1) {
            problems.add("Whoop: Guards im FPV-Bild nicht übergeben");
        }
        if (!poseStack.isEmpty()) {
            problems.add("PoseStack nach den Guards nicht leer");
        }
        FpvProps.maskColor(FpvCoverageSelfTests.KEY_RGB);
        try {
            int masked = FpvProps.guardFrames();
            FpvProps.submit(state, new PoseStack(), new SubmitNodeStorage(), camera);
            if (FpvProps.guardFrames() != masked) {
                problems.add("Guards überdecken die Messung der Prop-Scheiben");
            }
        } finally {
            FpvProps.maskColor(-1);
        }
        DroneRenderConfig saved = DroneRenderConfig.get().copy();
        try {
            DroneRenderConfig noDucts = saved.copy();
            noDucts.fpvProps = true;
            noDucts.fpvDucts = false;
            DroneRenderConfig.CONFIG.set(noDucts);
            if (!FpvProps.prepare(state, 20.0F) || !state.fpvGuards.isEmpty() || state.fpvGeometry.guardPoints() != 0) {
                problems.add("Schalter „Ducts im FPV-Bild“ aus, Ducts trotzdem vorbereitet");
            }
            DroneRenderConfig noProps = saved.copy();
            noProps.fpvProps = false;
            noProps.fpvDucts = true;
            DroneRenderConfig.CONFIG.set(noProps);
            if (!FpvProps.prepare(state, 20.0F) || state.fpvGuards.isEmpty()) {
                problems.add("Schalter „Props im FPV-Bild“ aus nimmt die Ducts mit");
            } else {
                int props = FpvProps.propsDrawn();
                int guards = FpvProps.guardFrames();
                FpvProps.submit(state, new PoseStack(), new SubmitNodeStorage(), camera);
                if (FpvProps.propsDrawn() != props || FpvProps.guardFrames() != guards + 1) {
                    problems.add("ohne Props im FPV-Bild: Props " + (FpvProps.propsDrawn() - props) + ", Ducts " + (FpvProps.guardFrames() - guards));
                }
            }
            DroneRenderConfig off = saved.copy();
            off.fpvProps = false;
            off.fpvDucts = false;
            DroneRenderConfig.CONFIG.set(off);
            if (FpvProps.prepare(state, 20.0F) || !state.fpvGuards.isEmpty()) {
                problems.add("beide Schalter aus, FPV-Geometrie trotzdem vorbereitet");
            }
        } finally {
            DroneRenderConfig.CONFIG.set(saved);
        }
        return StepResult.check(problems.isEmpty(), String.join("; ", problems));
    }

    private static boolean tinted(FpvGuardMesh mesh) {
        for (FpvGuardMesh.Batch batch : mesh.batches()) {
            for (int argb : batch.argb()) {
                if (argb != -1 && (argb & 0xFFFFFF) != 0xFFFFFF) {
                    return true;
                }
            }
        }
        return false;
    }

    @SuppressWarnings("unchecked")
    private static StepResult step(SelfTest.StepContext ctx) {
        Map<String, Object> state = ctx.state();
        List<DroneBuild> builds = builds();
        int total = builds.size() * TILTS.length;
        int index = (int) state.computeIfAbsent(INDEX, key -> 0);
        List<String> failures = (List<String>) state.computeIfAbsent(FAILURES, key -> new ArrayList<String>());
        if (index == 0 && !state.containsKey(PHASE) && !state.containsKey(CONFIG)) {
            REPORT.clear();
            DroneRenderConfig saved = DroneRenderConfig.get().copy();
            state.put(CONFIG, saved);
            DroneRenderConfig config = saved.copy();
            config.fpvProps = true;
            config.fpvDucts = true;
            DroneRenderConfig.CONFIG.set(config);
        }
        PilotSession session = PilotSession.INSTANCE;
        if (index >= total) {
            FpvProps.guardMaskColor(-1);
            if (session.isActive()) {
                session.requestStop();
                return StepResult.pending();
            }
            CompletableFuture<?> cleanup = (CompletableFuture<?>) state.computeIfAbsent(CLEANUP, key -> ctx.onServer(FpvGuardSelfTests::cleanup));
            if (!cleanup.isDone()) {
                return StepResult.pending();
            }
            for (String line : REPORT) {
                ctx.log(line);
            }
            if (state.get(CONFIG) instanceof DroneRenderConfig saved) {
                DroneRenderConfig.CONFIG.set(saved);
            }
            session.clearTestInput();
            RescueController.INSTANCE.clearHome();
            return StepResult.check(failures.isEmpty(), String.join("; ", failures));
        }
        DroneBuild build = builds.get(index / TILTS.length);
        float tilt = TILTS[index % TILTS.length];
        String label = String.format(Locale.ROOT, "%s bei %.0f°", FpvCoverageSelfTests.label(build), tilt);
        String phase = (String) state.getOrDefault(PHASE, "spawn");
        switch (phase) {
            case "spawn" -> {
                FpvProps.guardMaskColor(-1);
                if (session.isActive()) {
                    session.requestStop();
                    return StepResult.pending();
                }
                if (!serverReleased(ctx, state)) {
                    return StepResult.pending();
                }
                state.put(FUTURE, ctx.onServer(server -> start(server, build, tilt)));
                state.put(SINCE, ctx.tick());
                state.put(PHASE, "fly");
                return StepResult.pending();
            }
            case "fly" -> {
                CompletableFuture<?> future = (CompletableFuture<?>) state.get(FUTURE);
                if (future.isCompletedExceptionally()) {
                    return next(state, failures, label + ": FPV-Start " + future.exceptionNow());
                }
                QuadcopterEntity drone = session.drone();
                if (!future.isDone() || !session.isFpv() || drone == null || !build.equals(drone.build())
                        || Math.abs(FpvProps.cameraTilt(drone) - tilt) > 0.01F) {
                    return timeout(ctx, state, failures, label + ": keine FPV-Sitzung mit dem Kamerawinkel");
                }
                FpvProps.guardMaskColor(KEY_RGB);
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
                CleanFrameTap.request(target -> {
                    ImageArea area = FpvImage.area(target.width, target.height);
                    Screenshot.takeScreenshot(target, 1, image -> {
                        try (image) {
                            counted.set(count(image, area));
                        }
                    });
                });
                state.put(SINCE, ctx.tick());
                state.put(PHASE, "capture");
                return StepResult.pending();
            }
            case "capture" -> {
                int[] counted = ((AtomicReference<int[]>) state.get(COUNT)).get();
                if (counted == null) {
                    return timeout(ctx, state, failures, label + ": kein Bild vor dem Videoeffekt");
                }
                FpvProps.guardMaskColor(-1);
                return next(state, failures, evaluate(build, label, session.drone(), counted));
            }
            default -> {
                return StepResult.fail("unbekannte Phase " + phase);
            }
        }
    }

    static boolean serverReleased(SelfTest.StepContext ctx, Map<String, Object> state) {
        CompletableFuture<?> check = (CompletableFuture<?>) state.get(RELEASE_CHECK);
        if (check == null) {
            AtomicBoolean free = new AtomicBoolean();
            int waited = (int) state.getOrDefault(RELEASE_WAIT, 0);
            state.put(RELEASE_WAIT, waited + 1);
            state.put(RELEASE_FREE, free);
            state.put(RELEASE_CHECK, ctx.onServer(server -> {
                ServerPlayer player = server.getPlayerList().getPlayers().getFirst();
                Optional<QuadcopterEntity> piloted = DronePiloting.pilotedBy(player.getUUID());
                free.set(piloted.isEmpty());
                if (piloted.isPresent() && waited >= RELEASE_FALLBACK_TICKS) {
                    DronePiloting.release(piloted.get());
                }
            }));
            return false;
        }
        if (!check.isDone()) {
            return false;
        }
        state.remove(RELEASE_CHECK);
        if (!((AtomicBoolean) state.get(RELEASE_FREE)).get()) {
            return false;
        }
        state.remove(RELEASE_WAIT);
        return true;
    }

    private static StepResult timeout(SelfTest.StepContext ctx, Map<String, Object> state, List<String> failures, String what) {
        if (ctx.tick() - (int) state.get(SINCE) > TICKS_PER_SHOT) {
            FpvProps.guardMaskColor(-1);
            return next(state, failures, what);
        }
        return StepResult.pending();
    }

    private static StepResult next(Map<String, Object> state, List<String> failures, String problem) {
        if (problem != null) {
            failures.add(problem);
        }
        state.put(INDEX, (int) state.get(INDEX) + 1);
        state.put(PHASE, "spawn");
        return StepResult.pending();
    }

    public static double[] measure(DroneBuild build, QuadcopterEntity drone, int[] counted) {
        int width = counted[1];
        int height = counted[2];
        double rendered = 100.0 * counted[0] / ((double) width * height);
        float tilt = drone != null ? FpvProps.cameraTilt(drone) : Float.NaN;
        FpvGuardMesh mesh = FpvGuards.mesh(DroneVisuals.resources().assembly(build).frame(), build.frame());
        double analysis = analyticPercent(mesh, FpvProps.lens(build), tilt, VideoState.INSTANCE.horizontalFov(), width, height);
        return new double[]{rendered, analysis, mesh.quads()};
    }

    public static boolean matches(double rendered, double analysis) {
        return Math.abs(rendered - analysis) <= Math.max(ABSOLUTE_TOLERANCE, RELATIVE_TOLERANCE * Math.max(rendered, analysis));
    }

    private static String evaluate(DroneBuild build, String label, QuadcopterEntity drone, int[] counted) {
        int width = counted[1];
        int height = counted[2];
        double rendered = 100.0 * counted[0] / ((double) width * height);
        float tilt = drone != null ? FpvProps.cameraTilt(drone) : Float.NaN;
        double fov = VideoState.INSTANCE.horizontalFov();
        FpvGuardMesh mesh = FpvGuards.mesh(DroneVisuals.resources().assembly(build).frame(), build.frame());
        double analysis = analyticPercent(mesh, FpvProps.lens(build), tilt, fov, width, height);
        String line = String.format(Locale.ROOT, "%s: Guards im Bild %.2f %%, Analyse %.2f %% (%d Flächen, %.0f°, %d×%d)", label, rendered, analysis,
                mesh.quads(), fov, width, height);
        REPORT.add(line);
        Constants.LOG.info("FPV-Guards {}", line);
        if (mesh.isEmpty()) {
            return label + ": keine Guards im Modell";
        }
        double limit = MAX_PERCENT[Math.max(0, Math.min(MAX_PERCENT.length - 1, Math.round(tilt / TILTS[1])))];
        if (rendered > limit || analysis > limit) {
            return String.format(Locale.ROOT, "%s – mehr Ducts als bei einem echten Whoop (höchstens %.0f %%), sitzt die Kamera zu tief?",
                    line, limit);
        }
        if (counted.length > 3 && 100.0 * counted[3] / ((double) width * height) > UPPER_PERCENT) {
            return line + " – Ducts in der oberen Bildhälfte";
        }
        if (analysis <= VISIBLE_PERCENT) {
            if (tilt > TILTS[0] + 0.5F && rendered <= VISIBLE_PERCENT) {
                return null;
            }
            return line + " – Guards laut Analyse nicht im Bild";
        }
        double tolerance = Math.max(ABSOLUTE_TOLERANCE, RELATIVE_TOLERANCE * Math.max(rendered, analysis));
        if (Math.abs(rendered - analysis) > tolerance) {
            return line;
        }
        if (rendered <= 0.0) {
            return line + " – sichtbar gegen 0 %";
        }
        return null;
    }

    public static double analyticPercent(FpvGuardMesh mesh, Vector3fc lens, float tiltDegrees, double horizontalFovDeg, int width, int height) {
        double tanH = Math.tan(Math.toRadians(Math.clamp(horizontalFovDeg, 1.0, 179.0)) / 2.0);
        double tanV = tanH / ((double) width / height);
        double tilt = Math.toRadians(-tiltDegrees);
        double cos = Math.cos(tilt);
        double sin = Math.sin(tilt);
        boolean[] covered = new boolean[width * height];
        float[] points = mesh.pointsMm();
        double[][] quad = new double[4][];
        List<double[]> polygon = new ArrayList<>(8);
        for (int q = 0; q + FpvGuardZone.QUAD_FLOATS <= points.length; q += FpvGuardZone.QUAD_FLOATS) {
            for (int k = 0; k < 4; k++) {
                double dx = points[q + k * 3] - lens.x();
                double dy = points[q + k * 3 + 1] - lens.y();
                double dz = points[q + k * 3 + 2] - lens.z();
                double y = dy * cos - dz * sin;
                double z = dy * sin + dz * cos;
                quad[k] = new double[]{dx, y, -z};
            }
            polygon.clear();
            for (int i = 0; i < 4; i++) {
                double[] current = quad[i];
                double[] previous = quad[(i + 3) % 4];
                boolean currentIn = current[2] >= FpvProps.CLIP_DEPTH_MM;
                boolean previousIn = previous[2] >= FpvProps.CLIP_DEPTH_MM;
                if (currentIn != previousIn) {
                    double t = (FpvProps.CLIP_DEPTH_MM - previous[2]) / (current[2] - previous[2]);
                    polygon.add(new double[]{previous[0] + t * (current[0] - previous[0]), previous[1] + t * (current[1] - previous[1]),
                            FpvProps.CLIP_DEPTH_MM});
                }
                if (currentIn) {
                    polygon.add(current);
                }
            }
            if (polygon.size() < 3) {
                continue;
            }
            int n = polygon.size();
            double[] px = new double[n];
            double[] py = new double[n];
            double minY = Double.POSITIVE_INFINITY;
            double maxY = Double.NEGATIVE_INFINITY;
            for (int i = 0; i < n; i++) {
                double[] v = polygon.get(i);
                px[i] = (v[0] / (v[2] * tanH) + 1.0) * 0.5 * width;
                py[i] = (1.0 - v[1] / (v[2] * tanV)) * 0.5 * height;
                minY = Math.min(minY, py[i]);
                maxY = Math.max(maxY, py[i]);
            }
            int rowFrom = (int) Math.max(0, Math.ceil(minY - 0.5));
            int rowTo = (int) Math.min(height - 1, Math.floor(maxY - 0.5));
            for (int row = rowFrom; row <= rowTo; row++) {
                double cy = row + 0.5;
                double left = Double.POSITIVE_INFINITY;
                double right = Double.NEGATIVE_INFINITY;
                for (int i = 0; i < n; i++) {
                    int j = (i + 1) % n;
                    double y0 = py[i];
                    double y1 = py[j];
                    if ((cy < Math.min(y0, y1)) || (cy > Math.max(y0, y1)) || y0 == y1) {
                        continue;
                    }
                    double x = px[i] + (cy - y0) / (y1 - y0) * (px[j] - px[i]);
                    left = Math.min(left, x);
                    right = Math.max(right, x);
                }
                if (!(left <= right)) {
                    continue;
                }
                int colFrom = (int) Math.max(0, Math.ceil(left - 0.5));
                int colTo = (int) Math.min(width - 1, Math.floor(right - 0.5));
                for (int col = colFrom; col <= colTo; col++) {
                    covered[row * width + col] = true;
                }
            }
        }
        int hits = 0;
        for (boolean c : covered) {
            if (c) {
                hits++;
            }
        }
        return 100.0 * hits / ((double) width * height);
    }

    public static int[] count(NativeImage image, ImageArea area) {
        if (area.screenWidth() != image.getWidth() || area.screenHeight() != image.getHeight()) {
            area = ImageArea.full(image.getWidth(), image.getHeight());
        }
        int hits = 0;
        int upper = 0;
        int middle = area.y() + area.height() / 2;
        for (int y = area.y(); y < area.bottom(); y++) {
            for (int x = area.x(); x < area.right(); x++) {
                if (isKey(image.getPixel(x, y))) {
                    hits++;
                    if (y < middle) {
                        upper++;
                    }
                }
            }
        }
        return new int[]{hits, area.width(), area.height(), upper};
    }

    static boolean isKey(int argb) {
        int r = (argb >> 16) & 0xFF;
        int g = (argb >> 8) & 0xFF;
        int b = argb & 0xFF;
        return g >= 48 && r * 4 <= g && b * 4 <= g;
    }

    public static void start(MinecraftServer server, DroneBuild build, float tilt) {
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
        drone.setTune(new FlightTune(CameraMount.withUptilt(drone.tune().values(), build, tilt)));
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
