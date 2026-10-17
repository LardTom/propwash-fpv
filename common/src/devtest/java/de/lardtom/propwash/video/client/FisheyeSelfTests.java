package de.lardtom.propwash.video.client;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.platform.NativeImage;
import de.lardtom.propwash.build.DroneBuild;
import de.lardtom.propwash.build.VideoSystem;
import de.lardtom.propwash.content.ContentIds;
import de.lardtom.propwash.core.CoreComponents;
import de.lardtom.propwash.core.client.ClientBus;
import de.lardtom.propwash.dev.SelfTest;
import de.lardtom.propwash.dev.SelfTest.StepResult;
import de.lardtom.propwash.dev.client.Screenshots;
import de.lardtom.propwash.drone.DroneInteractions;
import de.lardtom.propwash.drone.DroneModule;
import de.lardtom.propwash.drone.DronePiloting;
import de.lardtom.propwash.drone.PilotMode;
import de.lardtom.propwash.drone.QuadcopterEntity;
import de.lardtom.propwash.drone.client.PilotSession;
import de.lardtom.propwash.drone.client.render.DroneRenderConfig;
import de.lardtom.propwash.input.StickPositions;
import de.lardtom.propwash.sim.DroneSim;
import de.lardtom.propwash.sim.StabMode;
import de.lardtom.propwash.sim.Telemetry;
import de.lardtom.propwash.video.FisheyeLens;
import de.lardtom.propwash.video.ImageArea;
import de.lardtom.propwash.video.ImageFormat;
import net.minecraft.client.Camera;
import net.minecraft.client.CloudStatus;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Util;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.joml.Vector3d;
import org.joml.Vector3f;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicReference;

public final class FisheyeSelfTests {
    public static final String PREFIX = "objektiv: ";
    public static final String GEOMETRY_STEP = PREFIX + "Fisheye je Videosystem und Bildformat – Marken an der berechneten Stelle, Bildmitte größer";
    public static final String PERF_STEP = PREFIX + "Bildrate flach gegen Fisheye bei niedriger und hoher Grafik";
    private static final double HOVER_HEIGHT = 2.5;
    private static final int WALL_DISTANCE = 6;
    private static final int WALL_HALF_WIDTH = 13;
    private static final int WALL_HEIGHT = 16;
    private static final int[] MARK_LATERAL = {-10, -7, -4, -1, 2, 5, 8};
    private static final int[] MARK_HEIGHT = {1, 4, 7, 10, 13};
    private static final int CELLS = 8;
    private static final double EDGE_MARGIN_PX = 8.0;
    private static final int MIN_COMPONENT_PX = 12;
    private static final int TIMEOUT_TICKS = 400;
    private static final float WEAK_SIGNAL = 0.45F;
    private static final String INDEX = "index";
    private static final String PHASE = "phase";
    private static final String SINCE = "since";
    private static final String FUTURE = "future";
    private static final String LENS = "lens";
    private static final String PREDICTION = "prediction";
    private static final String ANALYSIS = "analysis";
    private static final String SHOT = "shot";
    private static final String FAILURES = "failures";
    private static final String CENTRE = "centre";
    private static final String SAVED = "saved";
    private static final String HUD = "hud";
    private static final List<Case> CASES = List.of(
            new Case("analog", DroneBuild.RACER, VideoSystem.ANALOG, ImageFormat.WIDE, List.of(Lens.FLAT, Lens.MILD, Lens.FISHEYE, Lens.STRONG)),
            new Case("digital_lite", DroneBuild.CINEWHOOP, VideoSystem.DIGITAL, ImageFormat.WIDE, List.of(Lens.FLAT, Lens.FISHEYE, Lens.STRONG)),
            new Case("digital_hd", DroneBuild.FREESTYLE, VideoSystem.DIGITAL, ImageFormat.WIDE, List.of(Lens.FLAT, Lens.FISHEYE, Lens.STRONG)),
            new Case("analog_4_3", DroneBuild.RACER, VideoSystem.ANALOG, ImageFormat.FOUR_THREE, List.of(Lens.FLAT, Lens.FISHEYE, Lens.STRONG)));
    private static final List<BlockPos> PLACED = new CopyOnWriteArrayList<>();
    private static final FrameMeter METER = new FrameMeter();
    private static volatile int droneId = -1;
    private static volatile double groundY;
    private static volatile @Nullable ItemStack previousHead;
    private static volatile @Nullable ItemStack previousHand;

    private FisheyeSelfTests() {
    }

    public static void register() {
        SelfTest.clientStep(GEOMETRY_STEP, 9000, FisheyeSelfTests::geometryStep);
        SelfTest.clientStep(PERF_STEP, 4000, FisheyeSelfTests::perfStep);
        ClientBus.FRAME_START.register((mc, delta) -> METER.frame());
    }

    public static void flatForOtherSteps() {
        FpvLens.INSTANCE.testStrength(0);
    }

    public static Screenshots.Scene withFisheye(Screenshots.Scene scene) {
        return new Screenshots.Scene() {
            @Override
            public StepResult prepare(SelfTest.StepContext ctx) {
                FpvLens.INSTANCE.testStrength(FisheyeLens.DEFAULT_PERCENT);
                return scene.prepare(ctx);
            }

            @Override
            public void hold(SelfTest.StepContext ctx) {
                scene.hold(ctx);
            }

            @Override
            public void finish(SelfTest.StepContext ctx) {
                FisheyeLens lens = FpvLens.INSTANCE.rendered();
                ctx.log(lens == null ? "Fisheye nicht aktiv" : String.format(Locale.ROOT, "Fisheye aktiv: %.0f° breit, %.1f° hoch gerendert, Entzerrung %d Bilder",
                        lens.horizontalDeg(), lens.renderVerticalDeg(), VideoPass.INSTANCE.lensFramesDrawn()));
                scene.finish(ctx);
                flatForOtherSteps();
            }
        };
    }

    @SuppressWarnings("unchecked")
    private static StepResult geometryStep(SelfTest.StepContext ctx) {
        Map<String, Object> state = ctx.state();
        List<String> failures = (List<String>) state.computeIfAbsent(FAILURES, key -> new ArrayList<String>());
        if (!state.containsKey(INDEX)) {
            state.put(SAVED, VideoSettings.get().copy());
            state.put(HUD, Minecraft.getInstance().gui.hud.isHidden());
            state.put(INDEX, 0);
            state.put(PHASE, Screenshots.enabled() ? "window" : "spawn");
            state.put(SINCE, ctx.tick());
        }
        int index = (int) state.get(INDEX);
        String phase = (String) state.get(PHASE);
        if (index >= CASES.size() && !phase.startsWith("cleanup")) {
            return beginCleanup(ctx);
        }
        Minecraft mc = Minecraft.getInstance();
        PilotSession session = PilotSession.INSTANCE;
        int since = ctx.tick() - (int) state.get(SINCE);
        Case current = index < CASES.size() ? CASES.get(index) : CASES.getLast();
        switch (phase) {
            case "window" -> {
                StepResult sized = Screenshots.resizeWindow(ctx);
                if (sized.status() == StepResult.Status.PENDING) {
                    return sized;
                }
                state.put("resized", true);
                return next(ctx, "spawn");
            }
            case "spawn" -> {
                FpvLens.INSTANCE.testStrength(0);
                VideoConfig config = VideoSettings.get().copy();
                config.imageFormat = current.format();
                VideoSettings.CONFIG.set(config);
                session.clearTestInput();
                state.put(FUTURE, ctx.onServer(server -> spawnAndFly(server, current)));
                return next(ctx, "fly");
            }
            case "fly" -> {
                CompletableFuture<?> future = (CompletableFuture<?>) state.get(FUTURE);
                if (future.isCompletedExceptionally()) {
                    return skipCase(ctx, failures, current, "Start: " + future.exceptionNow().getMessage());
                }
                QuadcopterEntity drone = session.drone();
                if (!future.isDone() || drone == null || drone.getId() != droneId || !session.isFpv() || session.sim() == null
                        || !VideoState.INSTANCE.active()) {
                    return since > TIMEOUT_TICKS ? skipCase(ctx, failures, current, "keine FPV-Sitzung") : StepResult.pending();
                }
                VideoState.INSTANCE.overrideTestQuality(1.0F);
                return next(ctx, "hover");
            }
            case "hover" -> {
                hold(HOVER_HEIGHT);
                if (since >= 40 && settled(telemetry(), HOVER_HEIGHT) || since > TIMEOUT_TICKS) {
                    QuadcopterEntity drone = session.drone();
                    if (drone == null) {
                        return skipCase(ctx, failures, current, "Drohne fehlt");
                    }
                    Wall wall = Wall.ahead(drone);
                    state.put("wall", wall);
                    state.put(FUTURE, ctx.onServer(server -> wall.build(server)));
                    return next(ctx, "wall");
                }
                return StepResult.pending();
            }
            case "wall" -> {
                hold(HOVER_HEIGHT);
                CompletableFuture<?> future = (CompletableFuture<?>) state.get(FUTURE);
                if (future.isCompletedExceptionally()) {
                    return skipCase(ctx, failures, current, "Prüfwand: " + future.exceptionNow().getMessage());
                }
                if (!future.isDone() || since < 30) {
                    return StepResult.pending();
                }
                state.put(LENS, 0);
                return next(ctx, "lens");
            }
            case "lens" -> {
                hold(HOVER_HEIGHT);
                FpvLens.INSTANCE.testStrength(current.lenses().get((int) state.get(LENS)).percent());
                props(false);
                if (!mc.gui.hud.isHidden()) {
                    mc.gui.hud.toggle();
                }
                Screenshots.clearOverlays(mc);
                return next(ctx, "settle");
            }
            case "settle" -> {
                hold(HOVER_HEIGHT);
                if (since < 8) {
                    return StepResult.pending();
                }
                Lens lens = current.lenses().get((int) state.get(LENS));
                FisheyeLens rendered = FpvLens.INSTANCE.rendered();
                if (lens.fisheye() != (rendered != null) || rendered != null && rendered.percent() != lens.percent()) {
                    failures.add(current.name() + " " + lens + ": Objektiv im Bild " + (rendered == null ? "flach" : "Fisheye " + rendered.percent() + " %")
                            + " (Pass bereit " + VideoPass.INSTANCE.lensReady(Util.getNanos()) + ", Fehler " + VideoPass.INSTANCE.lensFailure() + ")");
                    return nextLens(ctx, failures, current);
                }
                Wall wall = (Wall) state.get("wall");
                Prediction prediction = Prediction.of(wall, rendered);
                state.put(PREDICTION, prediction);
                AtomicReference<List<Blob>> blobs = new AtomicReference<>();
                state.put(ANALYSIS, blobs);
                long lensFrames = VideoPass.INSTANCE.lensFramesDrawn();
                state.put("lensFrames", lensFrames);
                Screenshot.takeScreenshot(mc.gameRenderer.mainRenderTarget(), 1, image -> {
                    try (image) {
                        blobs.set(blobs(image, prediction.area()));
                    }
                });
                return next(ctx, "analyse");
            }
            case "analyse" -> {
                hold(HOVER_HEIGHT);
                List<Blob> blobs = ((AtomicReference<List<Blob>>) state.get(ANALYSIS)).get();
                if (blobs == null) {
                    return since > 100 ? nextLens(ctx, failures, current, "kein Bild") : StepResult.pending();
                }
                Prediction prediction = (Prediction) state.get(PREDICTION);
                Lens lens = current.lenses().get((int) state.get(LENS));
                String problem = compare(ctx, current, lens, prediction, blobs, state);
                if (problem != null) {
                    failures.add(current.name() + " " + lens + ": " + problem);
                }
                props(true);
                if (Screenshots.enabled()) {
                    if (mc.gui.hud.isHidden()) {
                        mc.gui.hud.toggle();
                    }
                    return next(ctx, "shot");
                }
                return nextLens(ctx, failures, current);
            }
            case "shot" -> {
                hold(HOVER_HEIGHT);
                if (!state.containsKey(SHOT)) {
                    if (since < 8) {
                        return StepResult.pending();
                    }
                    Lens lens = current.lenses().get((int) state.get(LENS));
                    state.put(SHOT, Screenshots.capture("fisheye_" + current.name() + "_" + lens.key()));
                    return StepResult.pending();
                }
                CompletableFuture<String> shot = (CompletableFuture<String>) state.get(SHOT);
                if (!shot.isDone()) {
                    return StepResult.pending();
                }
                state.remove(SHOT);
                if (shot.isCompletedExceptionally()) {
                    failures.add(current.name() + ": Bildschirmfoto " + shot.exceptionNow());
                }
                return nextLens(ctx, failures, current);
            }
            case "weak" -> {
                hold(HOVER_HEIGHT);
                FpvLens.INSTANCE.testStrength(FisheyeLens.DEFAULT_PERCENT);
                VideoState.INSTANCE.overrideTestQuality(WEAK_SIGNAL);
                if (!state.containsKey(SHOT)) {
                    if (since < 30) {
                        return StepResult.pending();
                    }
                    state.put(SHOT, Screenshots.capture("fisheye_" + current.name() + "_weak_signal"));
                    return StepResult.pending();
                }
                CompletableFuture<String> shot = (CompletableFuture<String>) state.get(SHOT);
                if (!shot.isDone()) {
                    return StepResult.pending();
                }
                state.remove(SHOT);
                if (shot.isCompletedExceptionally()) {
                    failures.add(current.name() + ": Bildschirmfoto mit schwachem Signal " + shot.exceptionNow());
                }
                ctx.log(String.format(Locale.ROOT, "%s: Fisheye mit schwachem Signal (%.0f %%), Videoeffekt-Pass %d Bilder, Entzerrung %d Bilder",
                        current.name(), WEAK_SIGNAL * 100.0F, VideoPass.INSTANCE.framesDrawn(), VideoPass.INSTANCE.lensFramesDrawn()));
                VideoState.INSTANCE.overrideTestQuality(1.0F);
                return nextLens(ctx, failures, current);
            }
            case "cleanup" -> {
                CompletableFuture<?> future = (CompletableFuture<?>) state.get(FUTURE);
                if (!future.isDone()) {
                    return since > TIMEOUT_TICKS ? StepResult.fail("Aufräumen hängt") : StepResult.pending();
                }
                restore(state);
                if (state.remove("resized") != null) {
                    Screenshots.restoreWindow(ctx);
                }
                if (future.isCompletedExceptionally()) {
                    failures.add("Aufräumen: " + future.exceptionNow().getMessage());
                }
                return failures.isEmpty() ? StepResult.pass() : StepResult.fail(String.join("; ", failures));
            }
            default -> {
                return StepResult.fail("unbekannte Phase " + phase);
            }
        }
    }

    private static StepResult nextLens(SelfTest.StepContext ctx, List<String> failures, Case current, String problem) {
        failures.add(current.name() + ": " + problem);
        return nextLens(ctx, failures, current);
    }

    @SuppressWarnings("unchecked")
    private static StepResult nextLens(SelfTest.StepContext ctx, List<String> failures, Case current) {
        Map<String, Object> state = ctx.state();
        int lens = (int) state.get(LENS) + 1;
        if (lens < current.lenses().size()) {
            state.put(LENS, lens);
            return next(ctx, "lens");
        }
        Map<Lens, double[]> centre = (Map<Lens, double[]>) state.remove(CENTRE);
        state.remove("centreId");
        double previous = 1.0;
        for (Lens fisheye : current.lenses()) {
            if (centre == null || !fisheye.fisheye() || !centre.containsKey(Lens.FLAT) || !centre.containsKey(fisheye)) {
                continue;
            }
            double[] flat = centre.get(Lens.FLAT);
            double[] fish = centre.get(fisheye);
            double measured = Math.sqrt(fish[0] / flat[0]);
            double expected = Math.sqrt(fish[1] / flat[1]);
            double horizontal = VideoState.INSTANCE.horizontalFov();
            double nominal = FisheyeLens.of(horizontal, fisheye.percent() / 100.0, 1920.0, 1080.0, 1920.0, 1080.0).centreMagnification();
            ctx.log(String.format(Locale.ROOT, "%s: Marke in der Bildmitte mit Fisheye %d %% %.2f-mal so groß wie flach (erwartet %.2f, Linse %.2f bei %.0f°)",
                    current.name(), fisheye.percent(), measured, expected, nominal, horizontal));
            if (Math.abs(measured / expected - 1.0) > 0.12) {
                failures.add(String.format(Locale.ROOT, "%s: Bildmitte mit %d %% %.2f-mal statt %.2f-mal so groß", current.name(), fisheye.percent(),
                        measured, expected));
            }
            if (measured < previous) {
                failures.add(String.format(Locale.ROOT, "%s: Bildmitte mit %d %% nicht größer als mit schwächerem Objektiv (%.2f nach %.2f)",
                        current.name(), fisheye.percent(), measured, previous));
            }
            previous = measured;
        }
        if (Screenshots.enabled() && current.weakShot() && !state.containsKey("weak")) {
            state.put("weak", true);
            return next(ctx, "weak");
        }
        state.remove("weak");
        state.put(INDEX, (int) state.get(INDEX) + 1);
        return next(ctx, "spawn");
    }

    private static StepResult skipCase(SelfTest.StepContext ctx, List<String> failures, Case current, String problem) {
        failures.add(current.name() + ": " + problem);
        ctx.state().put(INDEX, (int) ctx.state().get(INDEX) + 1);
        return next(ctx, "spawn");
    }

    private static StepResult beginCleanup(SelfTest.StepContext ctx) {
        PilotSession.INSTANCE.clearTestInput();
        ctx.state().put(FUTURE, ctx.onServer(FisheyeSelfTests::release));
        return next(ctx, "cleanup");
    }

    private static void props(boolean shown) {
        DroneRenderConfig render = DroneRenderConfig.get();
        boolean defaults = new DroneRenderConfig().fpvProps;
        boolean wanted = shown && defaults;
        if (render.fpvProps != wanted || render.fpvDucts != (shown && new DroneRenderConfig().fpvDucts)) {
            DroneRenderConfig copy = render.copy();
            copy.fpvProps = wanted;
            copy.fpvDucts = shown && new DroneRenderConfig().fpvDucts;
            DroneRenderConfig.CONFIG.set(copy);
        }
    }

    private static void restore(Map<String, Object> state) {
        Minecraft mc = Minecraft.getInstance();
        props(true);
        flatForOtherSteps();
        VideoState.INSTANCE.overrideTestQuality(Float.NaN);
        if (state.remove(SAVED) instanceof VideoConfig saved) {
            VideoSettings.CONFIG.set(saved);
        }
        if (state.remove(HUD) instanceof Boolean hidden && mc.gui.hud.isHidden() != hidden) {
            mc.gui.hud.toggle();
        }
    }

    private static @Nullable String compare(SelfTest.StepContext ctx, Case current, Lens lens, Prediction prediction, List<Blob> blobs,
                                            Map<String, Object> state) {
        if (prediction.marks().size() < 4) {
            return "nur " + prediction.marks().size() + " Marken ganz im Bild vorhergesagt";
        }
        List<Double> errors = new ArrayList<>();
        double worstRatio = 0.0;
        Mark centreMark = null;
        Blob centreBlob = null;
        double centreDistance = Double.MAX_VALUE;
        for (Mark mark : prediction.marks()) {
            Blob best = null;
            double bestDistance = Double.MAX_VALUE;
            for (Blob blob : blobs) {
                double d = Math.hypot(blob.x() - mark.x(), blob.y() - mark.y());
                if (d < bestDistance) {
                    bestDistance = d;
                    best = blob;
                }
            }
            double size = Math.sqrt(mark.area());
            double tolerance = Math.max(6.0, 0.15 * size);
            if (best == null || bestDistance > tolerance) {
                return String.format(Locale.ROOT, "Marke %s erwartet bei %.0f/%.0f (%.0f px groß), nächste gefundene %.1f px daneben (%d Flecken)",
                        mark.id(), mark.x(), mark.y(), size, bestDistance, blobs.size());
            }
            double ratio = best.area() / mark.area();
            worstRatio = Math.max(worstRatio, Math.max(ratio, 1.0 / ratio));
            errors.add(bestDistance);
            double fromCentre = Math.hypot(mark.x() - prediction.area().screenWidth() * 0.5, mark.y() - prediction.area().screenHeight() * 0.5);
            if (fromCentre < centreDistance) {
                centreDistance = fromCentre;
                centreMark = mark;
                centreBlob = best;
            }
        }
        if (worstRatio > 1.6) {
            return String.format(Locale.ROOT, "Fläche einer Marke weicht um Faktor %.2f von der Rechnung ab", worstRatio);
        }
        double[] sorted = errors.stream().mapToDouble(Double::doubleValue).sorted().toArray();
        double median = sorted[sorted.length / 2];
        double max = sorted[sorted.length - 1];
        ctx.log(String.format(Locale.ROOT, "%s %s: %d Marken, Lageabweichung Median %.2f px, größte %.2f px, Flächen bis Faktor %.2f, Sichtfeld %.1f° hoch gerendert%s",
                current.name(), lens, sorted.length, median, max, worstRatio, Minecraft.getInstance().gameRenderer.mainCamera().getFov(),
                prediction.lens() == null ? "" : String.format(Locale.ROOT, ", Bildkreis %.1f°, Mitte hochgerechnet %.2f-fach",
                        Math.toDegrees(prediction.lens().circleRad()), prediction.lens().centreUpscale())));
        if (median > 3.0) {
            return String.format(Locale.ROOT, "Marken im Median %.2f px neben der Rechnung", median);
        }
        if (lens.fisheye() && VideoPass.INSTANCE.lensFramesDrawn() <= (long) state.get("lensFrames")) {
            return "Entzerrung läuft nicht";
        }
        @SuppressWarnings("unchecked")
        Map<Lens, double[]> centre = (Map<Lens, double[]>) state.computeIfAbsent(CENTRE, key -> new EnumMap<Lens, double[]>(Lens.class));
        if (lens == Lens.FLAT && centreMark != null && centreBlob != null) {
            state.put("centreId", centreMark.id());
            centre.put(lens, new double[]{centreBlob.area(), centreMark.area()});
        } else if (lens.fisheye() && state.get("centreId") instanceof String id) {
            for (Mark mark : prediction.marks()) {
                Blob match = mark.id().equals(id) ? nearest(blobs, mark) : null;
                if (match != null) {
                    centre.put(lens, new double[]{match.area(), mark.area()});
                }
            }
        }
        return null;
    }

    private static @Nullable Blob nearest(List<Blob> blobs, Mark mark) {
        Blob best = null;
        double distance = Double.MAX_VALUE;
        for (Blob blob : blobs) {
            double d = Math.hypot(blob.x() - mark.x(), blob.y() - mark.y());
            if (d < distance) {
                distance = d;
                best = blob;
            }
        }
        return best;
    }

    private static List<Blob> blobs(NativeImage image, ImageArea area) {
        int width = image.getWidth();
        int height = image.getHeight();
        boolean[] mask = new boolean[width * height];
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                mask[y * width + x] = isMark(image.getPixel(x, y));
            }
        }
        int[] stack = new int[width * height];
        List<Blob> blobs = new ArrayList<>();
        for (int start = 0; start < mask.length; start++) {
            if (!mask[start]) {
                continue;
            }
            int top = 0;
            stack[top++] = start;
            mask[start] = false;
            long count = 0;
            double sumX = 0.0;
            double sumY = 0.0;
            while (top > 0) {
                int p = stack[--top];
                int x = p % width;
                int y = p / width;
                count++;
                sumX += x + 0.5;
                sumY += y + 0.5;
                if (x > 0 && mask[p - 1]) {
                    mask[p - 1] = false;
                    stack[top++] = p - 1;
                }
                if (x + 1 < width && mask[p + 1]) {
                    mask[p + 1] = false;
                    stack[top++] = p + 1;
                }
                if (y > 0 && mask[p - width]) {
                    mask[p - width] = false;
                    stack[top++] = p - width;
                }
                if (y + 1 < height && mask[p + width]) {
                    mask[p + width] = false;
                    stack[top++] = p + width;
                }
            }
            if (count >= MIN_COMPONENT_PX) {
                blobs.add(new Blob(sumX / count, sumY / count, count));
            }
        }
        return blobs;
    }

    static boolean isMark(int argb) {
        int r = (argb >> 16) & 0xFF;
        int g = (argb >> 8) & 0xFF;
        int b = argb & 0xFF;
        int low = Math.min(r, b);
        return low >= 60 && g * 2 <= low && Math.abs(r - b) * 2 <= Math.max(r, b);
    }

    private static StepResult perfStep(SelfTest.StepContext ctx) {
        return PerfRun.step(ctx);
    }

    private static StepResult next(SelfTest.StepContext ctx, String phase) {
        ctx.state().put(PHASE, phase);
        ctx.state().put(SINCE, ctx.tick());
        return StepResult.pending();
    }

    private static @Nullable Telemetry telemetry() {
        DroneSim sim = PilotSession.INSTANCE.sim();
        return sim == null ? null : sim.telemetry();
    }

    private static double height() {
        QuadcopterEntity drone = PilotSession.INSTANCE.drone();
        return drone == null ? 0.0 : drone.getY() - groundY;
    }

    private static boolean settled(@Nullable Telemetry t, double target) {
        return t != null && Math.abs(height() - target) < 0.3 && Math.abs(t.verticalSpeed) < 0.4;
    }

    static void hold(double target) {
        Telemetry t = telemetry();
        double hover = t == null ? 0.3 : Math.max(0.15, Math.min(0.6, t.hoverThrottleStick));
        double error = target - height();
        double vs = t == null ? 0.0 : t.verticalSpeed;
        double wanted = Math.max(-3.0, Math.min(3.0, 1.2 * error));
        float throttle = (float) Math.max(0.05, Math.min(0.9, hover + 0.06 * (wanted - vs)));
        PilotSession.INSTANCE.setTestInput(new StickPositions(0.0F, 0.0F, 0.0F, throttle), true, StabMode.ANGLE);
    }

    static void spawnAndFly(MinecraftServer server, Case current) {
        List<ServerPlayer> players = server.getPlayerList().getPlayers();
        if (players.isEmpty()) {
            throw new IllegalStateException("kein Spieler auf dem Server");
        }
        ServerPlayer player = players.getFirst();
        DronePiloting.pilotedBy(player.getUUID()).ifPresent(DronePiloting::release);
        for (ServerLevel other : server.getAllLevels()) {
            if (droneId >= 0 && other.getEntity(droneId) instanceof QuadcopterEntity old) {
                old.discard();
            }
        }
        clearBlocks(server);
        ServerLevel level = player.level();
        QuadcopterEntity drone = DroneModule.DRONE.get().create(level, EntitySpawnReason.COMMAND);
        if (drone == null) {
            throw new IllegalStateException("EntityType.create lieferte null");
        }
        Vec3 feet = player.position();
        BlockPos spot = BlockPos.containing(feet.x + 2.0, feet.y, feet.z - 5.0);
        groundY = feet.y;
        drone.snapTo(spot.getX() + 0.5, feet.y, spot.getZ() + 0.5, 0.0F, 0.0F);
        drone.setAttitude(new Quaternionf());
        drone.setBuild(current.build());
        drone.setCustomName(Component.literal("Objektiv Test"));
        if (!level.addFreshEntity(drone)) {
            throw new IllegalStateException("addFreshEntity fehlgeschlagen");
        }
        droneId = drone.getId();
        ItemStack goggles = ContentIds.item(current.goggles() == VideoSystem.DIGITAL ? ContentIds.GOGGLES_DIGITAL : ContentIds.GOGGLES_ANALOG)
                .map(ItemStack::new).orElse(ItemStack.EMPTY);
        if (goggles.isEmpty()) {
            throw new IllegalStateException("Brille nicht registriert");
        }
        goggles.set(CoreComponents.BOUND_DRONE.get(), DroneInteractions.binding(drone));
        if (previousHead == null) {
            previousHead = player.getItemBySlot(EquipmentSlot.HEAD).copy();
        }
        player.setItemSlot(EquipmentSlot.HEAD, goggles);
        ItemStack remote = new ItemStack(DroneModule.REMOTE.get());
        remote.set(CoreComponents.BOUND_DRONE.get(), DroneInteractions.binding(drone));
        if (previousHand == null) {
            previousHand = player.getMainHandItem().copy();
        }
        player.setItemSlot(EquipmentSlot.MAINHAND, remote);
        DronePiloting.StartResult result = DronePiloting.tryStart(player, drone, PilotMode.FPV);
        if (result != DronePiloting.StartResult.STARTED) {
            throw new IllegalStateException("FPV-Start verweigert: " + result);
        }
    }

    static void release(MinecraftServer server) {
        List<ServerPlayer> players = server.getPlayerList().getPlayers();
        if (!players.isEmpty()) {
            ServerPlayer player = players.getFirst();
            DronePiloting.pilotedBy(player.getUUID()).ifPresent(DronePiloting::release);
            for (ServerLevel level : server.getAllLevels()) {
                if (level.getEntity(droneId) instanceof QuadcopterEntity drone) {
                    drone.discard();
                }
            }
            ItemStack head = previousHead;
            if (head != null) {
                player.setItemSlot(EquipmentSlot.HEAD, head);
            }
            ItemStack hand = previousHand;
            if (hand != null) {
                player.setItemSlot(EquipmentSlot.MAINHAND, hand);
            }
        }
        clearBlocks(server);
        previousHead = null;
        previousHand = null;
        droneId = -1;
    }

    private static void clearBlocks(MinecraftServer server) {
        for (BlockPos pos : PLACED) {
            server.overworld().setBlockAndUpdate(pos, Blocks.AIR.defaultBlockState());
        }
        PLACED.clear();
    }

    enum Lens {
        FLAT(0, "flat"),
        MILD(40, "fisheye_40"),
        FISHEYE(FisheyeLens.DEFAULT_PERCENT, "fisheye"),
        STRONG(FisheyeLens.MAX_PERCENT, "fisheye_100");

        private final int percent;
        private final String key;

        Lens(int percent, String key) {
            this.percent = percent;
            this.key = key;
        }

        int percent() {
            return this.percent;
        }

        String key() {
            return this.key;
        }

        boolean fisheye() {
            return this.percent > 0;
        }
    }

    record Case(String name, DroneBuild build, VideoSystem goggles, ImageFormat format, List<Lens> lenses) {
        boolean weakShot() {
            return this.format == ImageFormat.WIDE && this.build != DroneBuild.CINEWHOOP;
        }
    }

    record Blob(double x, double y, long count) {
        double area() {
            return this.count;
        }
    }

    record Mark(String id, double x, double y, double area) {
    }

    record Wall(boolean alongX, int sign, int forward, int lateral, int ground) {
        static Wall ahead(QuadcopterEntity drone) {
            Vector3d look = new Vector3d(0.0, 0.0, -1.0);
            drone.orientation().transform(look);
            boolean alongX = Math.abs(look.x) > Math.abs(look.z);
            int sign = (int) Math.signum(alongX ? look.x : look.z);
            if (sign == 0) {
                sign = 1;
            }
            int forward = (int) Math.floor((alongX ? drone.getX() : drone.getZ()) + sign * WALL_DISTANCE);
            int lateral = (int) Math.floor(alongX ? drone.getZ() : drone.getX());
            return new Wall(alongX, sign, forward, lateral, (int) Math.floor(groundY));
        }

        BlockPos at(int lateralOffset, int up) {
            int l = this.lateral + lateralOffset;
            return this.alongX ? new BlockPos(this.forward, this.ground + up, l) : new BlockPos(l, this.ground + up, this.forward);
        }

        double face() {
            return this.sign > 0 ? this.forward : this.forward + 1.0;
        }

        Vector3d point(int lateralOffset, int up, double a, double b) {
            double l = this.lateral + lateralOffset + a;
            double y = this.ground + up + b;
            return this.alongX ? new Vector3d(this.face(), y, l) : new Vector3d(l, y, this.face());
        }

        void build(MinecraftServer server) {
            ServerLevel level = server.overworld();
            for (int w = -WALL_HALF_WIDTH; w <= WALL_HALF_WIDTH; w++) {
                for (int up = 0; up < WALL_HEIGHT; up++) {
                    BlockPos pos = this.at(w, up);
                    BlockState block = mark(w, up) ? Blocks.CONCRETE.pick(DyeColor.MAGENTA).defaultBlockState()
                            : Blocks.CONCRETE.pick(((w + up) & 1) == 0 ? DyeColor.BLACK : DyeColor.WHITE).defaultBlockState();
                    level.setBlockAndUpdate(pos, block);
                    PLACED.add(pos.immutable());
                }
            }
        }

        static boolean mark(int lateralOffset, int up) {
            return Arrays.stream(MARK_LATERAL).anyMatch(v -> v == lateralOffset) && Arrays.stream(MARK_HEIGHT).anyMatch(v -> v == up);
        }
    }

    record Prediction(ImageArea area, @Nullable FisheyeLens lens, List<Mark> marks) {
        static Prediction of(Wall wall, @Nullable FisheyeLens lens) {
            Minecraft mc = Minecraft.getInstance();
            Camera camera = mc.gameRenderer.mainCamera();
            RenderTarget main = mc.gameRenderer.mainRenderTarget();
            int width = main.width;
            int height = main.height;
            ImageArea area = FpvImage.area(width, height);
            Vec3 eye = camera.position();
            Quaternionf inverse = new Quaternionf(camera.rotation()).normalize().conjugate();
            double flatFocal = height * 0.5 / Math.tan(Math.toRadians(camera.getFov()) * 0.5);
            List<Mark> marks = new ArrayList<>();
            double[] p = new double[2];
            double[][] grid = new double[(CELLS + 1) * (CELLS + 1)][];
            for (int lateral : MARK_LATERAL) {
                for (int up : MARK_HEIGHT) {
                    String id = lateral + "/" + up;
                    boolean visible = true;
                    for (int i = 0; i <= CELLS && visible; i++) {
                        for (int j = 0; j <= CELLS && visible; j++) {
                            Vector3d world = wall.point(lateral, up, (double) i / CELLS, (double) j / CELLS);
                            Vector3f c = inverse.transform(new Vector3f((float) (world.x - eye.x), (float) (world.y - eye.y), (float) (world.z - eye.z)));
                            visible = screen(c, lens, flatFocal, width, height, p) && inside(area, lens, p, width, height);
                            grid[i * (CELLS + 1) + j] = new double[]{p[0], p[1]};
                        }
                    }
                    if (!visible) {
                        continue;
                    }
                    double sumA = 0.0;
                    double sumX = 0.0;
                    double sumY = 0.0;
                    for (int i = 0; i < CELLS; i++) {
                        for (int j = 0; j < CELLS; j++) {
                            double[] a = grid[i * (CELLS + 1) + j];
                            double[] b = grid[(i + 1) * (CELLS + 1) + j];
                            double[] c = grid[(i + 1) * (CELLS + 1) + j + 1];
                            double[] d = grid[i * (CELLS + 1) + j + 1];
                            double cell = Math.abs((a[0] * b[1] - b[0] * a[1]) + (b[0] * c[1] - c[0] * b[1]) + (c[0] * d[1] - d[0] * c[1])
                                    + (d[0] * a[1] - a[0] * d[1])) * 0.5;
                            sumA += cell;
                            sumX += cell * (a[0] + b[0] + c[0] + d[0]) * 0.25;
                            sumY += cell * (a[1] + b[1] + c[1] + d[1]) * 0.25;
                        }
                    }
                    if (sumA > MIN_COMPONENT_PX * 2) {
                        marks.add(new Mark(id, sumX / sumA, sumY / sumA, sumA));
                    }
                }
            }
            return new Prediction(area, lens, marks);
        }

        private static boolean screen(Vector3f c, @Nullable FisheyeLens lens, double flatFocal, int width, int height, double[] out) {
            double depth = -c.z;
            if (lens != null) {
                double[] image = new double[2];
                if (!lens.project(c.x, c.y, depth, image)) {
                    return false;
                }
                out[0] = width * 0.5 + image[0];
                out[1] = height * 0.5 - image[1];
                return true;
            }
            if (!(depth > 1.0E-3)) {
                return false;
            }
            out[0] = width * 0.5 + flatFocal * c.x / depth;
            out[1] = height * 0.5 - flatFocal * c.y / depth;
            return true;
        }

        private static boolean inside(ImageArea area, @Nullable FisheyeLens lens, double[] p, int width, int height) {
            if (p[0] < area.x() + EDGE_MARGIN_PX || p[1] < area.y() + EDGE_MARGIN_PX || p[0] > area.right() - EDGE_MARGIN_PX
                    || p[1] > area.bottom() - EDGE_MARGIN_PX) {
                return false;
            }
            return lens == null || Math.hypot(p[0] - width * 0.5, p[1] - height * 0.5) < lens.circleRadius() - EDGE_MARGIN_PX;
        }
    }

    static final class FrameMeter {
        private final long[] frames = new long[20_000];
        private volatile boolean running;
        private int count;
        private long last;

        void start() {
            this.count = 0;
            this.last = 0L;
            this.running = true;
        }

        void frame() {
            if (!this.running) {
                return;
            }
            long now = Util.getNanos();
            if (this.last != 0L && this.count < this.frames.length) {
                this.frames[this.count++] = now - this.last;
            }
            this.last = now;
        }

        double[] stop() {
            this.running = false;
            long[] copy = Arrays.copyOf(this.frames, this.count);
            if (copy.length == 0) {
                return new double[]{0.0, 0.0, 0.0, 0.0, 0.0};
            }
            Arrays.sort(copy);
            long total = 0L;
            for (long value : copy) {
                total += value;
            }
            double mean = total / (double) copy.length * 1.0E-6;
            double median = copy[copy.length / 2] * 1.0E-6;
            double p95 = copy[Math.min(copy.length - 1, (int) Math.floor(copy.length * 0.95))] * 1.0E-6;
            return new double[]{1000.0 / mean, mean, median, p95, copy.length};
        }
    }

    private static final class PerfRun {
        private static final int[] DISTANCES = {6, 16};
        private static final int SEGMENT_TICKS = 60;
        private static final int CHUNK_SETTLE_TICKS = 100;
        private static final int ROUNDS = 5;
        private static final int FIELD = 30;

        private PerfRun() {
        }

        @SuppressWarnings("unchecked")
        static StepResult step(SelfTest.StepContext ctx) {
            Map<String, Object> state = ctx.state();
            Minecraft mc = Minecraft.getInstance();
            PilotSession session = PilotSession.INSTANCE;
            if (!state.containsKey(PHASE)) {
                StepResult sized = Screenshots.resizeWindow(ctx);
                if (sized.status() == StepResult.Status.PENDING) {
                    return sized;
                }
                state.put("options", new Object[]{mc.options.renderDistance().get(), mc.options.framerateLimit().get(), mc.options.enableVsync().get(),
                        mc.options.cloudStatus().get(), mc.options.ambientOcclusion().get(), mc.options.entityShadows().get()});
                state.put(SAVED, VideoSettings.get().copy());
                state.put("results", new ArrayList<String>());
                VideoConfig config = VideoSettings.get().copy();
                config.imageFormat = ImageFormat.WIDE;
                VideoSettings.CONFIG.set(config);
                FpvLens.INSTANCE.testStrength(0);
                state.put(FUTURE, ctx.onServer(server -> {
                    spawnAndFly(server, CASES.get(2));
                    field(server);
                }));
                return next(ctx, "fly");
            }
            String phase = (String) state.get(PHASE);
            int since = ctx.tick() - (int) state.get(SINCE);
            mc.getFramerateLimitTracker().onInputReceived();
            List<String> results = (List<String>) state.get("results");
            switch (phase) {
                case "fly" -> {
                    CompletableFuture<?> future = (CompletableFuture<?>) state.get(FUTURE);
                    if (future.isCompletedExceptionally()) {
                        return finish(ctx, "Start: " + future.exceptionNow().getMessage());
                    }
                    QuadcopterEntity drone = session.drone();
                    if (!future.isDone() || drone == null || drone.getId() != droneId || !session.isFpv()) {
                        return since > TIMEOUT_TICKS ? finish(ctx, "keine FPV-Sitzung") : StepResult.pending();
                    }
                    VideoState.INSTANCE.overrideTestQuality(1.0F);
                    state.put("level", 0);
                    return next(ctx, "level");
                }
                case "level" -> {
                    hold(HOVER_HEIGHT * 2.0);
                    int level = (int) state.get("level");
                    if (level >= DISTANCES.length) {
                        return finish(ctx, null);
                    }
                    boolean high = level == 1;
                    mc.options.renderDistance().set(DISTANCES[level]);
                    mc.options.cloudStatus().set(high ? CloudStatus.FANCY : CloudStatus.FAST);
                    mc.options.ambientOcclusion().set(high);
                    mc.options.entityShadows().set(high);
                    mc.options.framerateLimit().set(260);
                    mc.options.enableVsync().set(false);
                    state.put("segment", 0);
                    state.put("sums", new double[4][3]);
                    state.put("diffs", new ArrayList<Double>());
                    return next(ctx, "chunks");
                }
                case "chunks" -> {
                    hold(HOVER_HEIGHT * 2.0);
                    if (since < CHUNK_SETTLE_TICKS) {
                        return StepResult.pending();
                    }
                    return next(ctx, "segment");
                }
                case "segment" -> {
                    hold(HOVER_HEIGHT * 2.0);
                    int segment = (int) state.get("segment");
                    Lens lens = segment % 2 == 0 ? Lens.FLAT : Lens.FISHEYE;
                    if (!state.containsKey("lensSet")) {
                        FpvLens.INSTANCE.testStrength(lens.percent());
                        state.put("lensSet", ctx.tick());
                        return StepResult.pending();
                    }
                    if (!state.containsKey("metering")) {
                        if (since >= 20) {
                            METER.start();
                            state.put("metering", true);
                        }
                        return StepResult.pending();
                    }
                    if (since < 20 + SEGMENT_TICKS) {
                        return StepResult.pending();
                    }
                    state.remove("lensSet");
                    state.remove("metering");
                    double[] measured = METER.stop();
                    double[][] sums = (double[][]) state.get("sums");
                    int slot = lens == Lens.FLAT ? 0 : 1;
                    sums[slot][0] += measured[0];
                    sums[slot][1] += measured[2];
                    sums[slot][2] += measured[3];
                    List<Double> diffs = (List<Double>) state.get("diffs");
                    if (lens == Lens.FLAT) {
                        state.put("flatMedian", measured[2]);
                    } else if (state.get("flatMedian") instanceof Double flatMedian) {
                        diffs.add(measured[2] - flatMedian);
                    }
                    if (lens == Lens.FISHEYE && FpvLens.INSTANCE.rendered() == null) {
                        return finish(ctx, "Fisheye bei der Messung nicht aktiv");
                    }
                    segment++;
                    if (segment < 2 * ROUNDS) {
                        state.put("segment", segment);
                        return next(ctx, "segment");
                    }
                    int level = (int) state.get("level");
                    double flatFps = sums[0][0] / ROUNDS;
                    double fishFps = sums[1][0] / ROUNDS;
                    double[] pairs = ((List<Double>) state.get("diffs")).stream().mapToDouble(Double::doubleValue).sorted().toArray();
                    double cost = pairs.length == 0 ? Double.NaN : pairs[pairs.length / 2];
                    if (!(fishFps > 0.5 * flatFps) || cost > 3.0) {
                        return finish(ctx, String.format(Locale.ROOT, "Fisheye zu teuer: %.1f gegen %.1f B/s, %.2f ms je Bild", fishFps, flatFps, cost));
                    }
                    String line = String.format(Locale.ROOT,
                            "Sichtweite %d (%s): flach %.1f B/s (Median %.2f ms, 95 %% %.2f ms), Fisheye %.1f B/s (Median %.2f ms, 95 %% %.2f ms), "
                                    + "Mehraufwand je Bild im Median der %d Paare %+.2f ms (%+.2f bis %+.2f ms), Fenster %dx%d",
                            DISTANCES[level], level == 1 ? "hoch" : "niedrig", flatFps, sums[0][1] / ROUNDS, sums[0][2] / ROUNDS, fishFps,
                            sums[1][1] / ROUNDS, sums[1][2] / ROUNDS, pairs.length, cost, pairs.length == 0 ? Double.NaN : pairs[0],
                            pairs.length == 0 ? Double.NaN : pairs[pairs.length - 1], mc.getWindow().getWidth(), mc.getWindow().getHeight());
                    results.add(line);
                    ctx.log(line);
                    state.put("level", level + 1);
                    return next(ctx, "level");
                }
                case "cleanup" -> {
                    CompletableFuture<?> future = (CompletableFuture<?>) state.get(FUTURE);
                    if (!future.isDone()) {
                        return since > TIMEOUT_TICKS ? StepResult.fail("Aufräumen hängt") : StepResult.pending();
                    }
                    String problem = (String) state.get("problem");
                    return problem == null ? StepResult.pass() : StepResult.fail(problem);
                }
                default -> {
                    return StepResult.fail("unbekannte Phase " + phase);
                }
            }
        }

        private static void field(MinecraftServer server) {
            ServerLevel level = server.overworld();
            QuadcopterEntity drone = level.getEntity(droneId) instanceof QuadcopterEntity found ? found : null;
            if (drone == null) {
                throw new IllegalStateException("Drohne für das Testgelände fehlt");
            }
            BlockPos centre = drone.blockPosition();
            BlockState[] blocks = {Blocks.STONE_BRICKS.defaultBlockState(), Blocks.OAK_LOG.defaultBlockState(), Blocks.OAK_LEAVES.defaultBlockState(),
                    Blocks.GLASS.defaultBlockState()};
            for (int dx = -FIELD; dx <= FIELD; dx += 3) {
                for (int dz = -FIELD; dz <= FIELD; dz += 3) {
                    if (Math.abs(dx) <= 4 && Math.abs(dz) <= 9) {
                        continue;
                    }
                    int hash = (dx * 73_856_093) ^ (dz * 19_349_663);
                    int height = 1 + Math.floorMod(hash, 9);
                    BlockState block = blocks[Math.floorMod(hash >> 4, blocks.length)];
                    for (int up = 0; up < height; up++) {
                        BlockPos pos = centre.offset(dx, up, dz);
                        if (level.getBlockState(pos).isAir()) {
                            level.setBlockAndUpdate(pos, block);
                            PLACED.add(pos.immutable());
                        }
                    }
                }
            }
        }

        private static StepResult finish(SelfTest.StepContext ctx, @Nullable String problem) {
            Map<String, Object> state = ctx.state();
            Minecraft mc = Minecraft.getInstance();
            Screenshots.restoreWindow(ctx);
            if (state.remove("options") instanceof Object[] saved) {
                mc.options.renderDistance().set((Integer) saved[0]);
                mc.options.framerateLimit().set((Integer) saved[1]);
                mc.options.enableVsync().set((Boolean) saved[2]);
                mc.options.cloudStatus().set((CloudStatus) saved[3]);
                mc.options.ambientOcclusion().set((Boolean) saved[4]);
                mc.options.entityShadows().set((Boolean) saved[5]);
            }
            flatForOtherSteps();
            VideoState.INSTANCE.overrideTestQuality(Float.NaN);
            if (state.remove(SAVED) instanceof VideoConfig saved) {
                VideoSettings.CONFIG.set(saved);
            }
            PilotSession.INSTANCE.clearTestInput();
            state.put("problem", problem);
            state.put(FUTURE, ctx.onServer(FisheyeSelfTests::release));
            return next(ctx, "cleanup");
        }
    }
}
