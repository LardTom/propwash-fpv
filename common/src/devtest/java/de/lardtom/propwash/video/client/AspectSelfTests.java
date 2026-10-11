package de.lardtom.propwash.video.client;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.platform.Window;
import de.lardtom.propwash.build.DroneBuild;
import de.lardtom.propwash.build.VideoSystem;
import de.lardtom.propwash.dev.SelfTest;
import de.lardtom.propwash.dev.SelfTest.StepResult;
import de.lardtom.propwash.dev.client.Screenshots;
import de.lardtom.propwash.drone.QuadcopterEntity;
import de.lardtom.propwash.drone.client.PilotSession;
import de.lardtom.propwash.drone.client.render.FpvCoverageSelfTests;
import de.lardtom.propwash.osd.OsdGrid;
import de.lardtom.propwash.osd.client.OsdConfigTab;
import de.lardtom.propwash.osd.client.OsdHud;
import de.lardtom.propwash.rescue.client.RescueController;
import de.lardtom.propwash.ui.client.config.ConfigTabHost;
import de.lardtom.propwash.video.FovMath;
import de.lardtom.propwash.video.ImageArea;
import de.lardtom.propwash.video.ImageFormat;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;

public final class AspectSelfTests {
    public static final String STEP = "video: Bildformat 16:9, 4:3 und Auto – Sichtfeld, Bildbereich, Balken und OSD im FPV";
    public static final String TABS_STEP = "video: Bildformat im Video-Tab, 4:3-Vorschau im OSD-Editor";
    private static final String PHASE = "phase";
    private static final String SINCE = "since";
    private static final String FUTURE = "future";
    private static final String SAVED = "saved";
    private static final String INDEX = "index";
    private static final String MARKS = "marks";
    private static final String PIXELS = "pixels";
    private static final String FAILURES = "failures";
    private static final String CLEANUP = "cleanup";
    private static final int SETTLE_TICKS = 5;
    private static final int TIMEOUT_TICKS = 300;
    private static final List<Case> CASES = List.of(
            new Case(DroneBuild.RACER, ImageFormat.FOUR_THREE, ImageFormat.FOUR_THREE),
            new Case(DroneBuild.RACER, ImageFormat.AUTO, ImageFormat.FOUR_THREE),
            new Case(DroneBuild.RACER, ImageFormat.WIDE, ImageFormat.WIDE),
            new Case(DroneBuild.CINEWHOOP, ImageFormat.AUTO, ImageFormat.WIDE),
            new Case(DroneBuild.CINEWHOOP, ImageFormat.FOUR_THREE, ImageFormat.FOUR_THREE));

    private AspectSelfTests() {
    }

    public static void register() {
        SelfTest.clientStep(TABS_STEP, 20, AspectSelfTests::tabsStep);
        SelfTest.clientStep(STEP, 2400, AspectSelfTests::step);
    }

    private static StepResult tabsStep(SelfTest.StepContext ctx) {
        VideoConfig saved = VideoSettings.get().copy();
        try {
            VideoState.INSTANCE.overrideTestReceiver(true, VideoSystem.ANALOG);
            String problem = tabs();
            return problem == null ? StepResult.pass() : StepResult.fail(problem);
        } finally {
            VideoState.INSTANCE.overrideTestReceiver(false, null);
            FpvImage.draft(null);
            VideoSettings.CONFIG.set(saved);
        }
    }

    private static @Nullable String tabs() {
        List<AbstractWidget> widgets = new ArrayList<>();
        new VideoConfigTab().attach(host(widgets), 0, 0, 480, 280);
        String label = Component.translatable("propwash.video.config.format").getString();
        List<Object> formats = new ArrayList<>();
        for (AbstractWidget widget : widgets) {
            if (widget instanceof CycleButton<?> button && widget.getMessage().getString().startsWith(label)) {
                formats.add(button.getValue());
            }
        }
        if (formats.size() != 1 || !(formats.getFirst() instanceof ImageFormat shown) || shown != VideoSettings.get().imageFormat) {
            return "Video-Tab ohne Umschalter für das Bildformat: " + widgets.stream().map(w -> w.getMessage().getString()).toList();
        }
        double wideAspect = 0.0;
        for (ImageFormat format : List.of(ImageFormat.WIDE, ImageFormat.FOUR_THREE)) {
            VideoConfig config = VideoSettings.get().copy();
            config.imageFormat = format;
            VideoSettings.CONFIG.set(config);
            OsdConfigTab osd = new OsdConfigTab();
            osd.attach(host(new ArrayList<>()), 0, 0, 480, 280);
            OsdGrid grid = osd.previewGrid();
            if (grid == null || osd.previewFormat() != format) {
                return "OSD-Editor ohne Vorschau im Format " + format;
            }
            double aspect = grid.width() / grid.height();
            if (format.boxed() && Math.abs(aspect - ImageFormat.FOUR_THREE_ASPECT) > 2.0 / grid.height()) {
                return "OSD-Vorschau in 4:3 hat das Seitenverhältnis " + aspect;
            }
            if (!format.boxed()) {
                wideAspect = aspect;
            } else if (wideAspect + 2.0 / grid.height() < aspect) {
                return "OSD-Vorschau in 16:9 schmaler als in 4:3: " + wideAspect;
            }
        }
        VideoConfig wide = VideoSettings.get().copy();
        wide.imageFormat = ImageFormat.WIDE;
        VideoSettings.CONFIG.set(wide);
        FpvImage.draft(ImageFormat.FOUR_THREE);
        if (FpvImage.editorFormat() != ImageFormat.FOUR_THREE) {
            return "OSD-Vorschau folgt nicht dem ungespeicherten Bildformat im Video-Tab";
        }
        FpvImage.draft(null);
        return FpvImage.editorFormat() == ImageFormat.WIDE ? null : "Entwurf des Bildformats bleibt nach dem Verwerfen hängen";
    }

    private static ConfigTabHost host(List<AbstractWidget> widgets) {
        return new ConfigTabHost() {
            @Override
            public <T extends AbstractWidget> T addWidget(T widget) {
                widgets.add(widget);
                return widget;
            }

            @Override
            public Font font() {
                return Minecraft.getInstance().font;
            }

            @Override
            public Minecraft minecraft() {
                return Minecraft.getInstance();
            }

            @Override
            public Screen screen() {
                return Minecraft.getInstance().gui.screen();
            }

            @Override
            public void rebuild() {
            }

            @Override
            public void changed() {
            }
        };
    }

    @SuppressWarnings("unchecked")
    private static StepResult step(SelfTest.StepContext ctx) {
        Map<String, Object> state = ctx.state();
        PilotSession session = PilotSession.INSTANCE;
        List<String> failures = (List<String>) state.computeIfAbsent(FAILURES, key -> new ArrayList<String>());
        if (!state.containsKey(INDEX)) {
            state.put(SAVED, VideoSettings.get().copy());
            state.put(INDEX, 0);
            state.put(PHASE, "start");
        }
        int index = (int) state.get(INDEX);
        if (index >= CASES.size()) {
            return finish(ctx, failures);
        }
        Case current = CASES.get(index);
        String phase = (String) state.get(PHASE);
        switch (phase) {
            case "start" -> {
                QuadcopterEntity drone = session.drone();
                if (session.isFpv() && drone != null && current.build().equals(drone.build())) {
                    return next(ctx, "format");
                }
                if (session.isActive()) {
                    session.requestStop();
                    return StepResult.pending();
                }
                state.put(FUTURE, ctx.onServer(server -> FpvCoverageSelfTests.start(server, current.build())));
                return next(ctx, "fly");
            }
            case "fly" -> {
                CompletableFuture<?> future = (CompletableFuture<?>) state.get(FUTURE);
                if (future.isCompletedExceptionally()) {
                    failures.add(current + ": FPV-Start " + future.exceptionNow());
                    return advance(ctx);
                }
                QuadcopterEntity drone = session.drone();
                if (!future.isDone() || !session.isFpv() || drone == null || !current.build().equals(drone.build())) {
                    return timedOut(ctx) ? fail(ctx, failures, current, "keine FPV-Sitzung") : StepResult.pending();
                }
                return next(ctx, "format");
            }
            case "format" -> {
                VideoConfig config = VideoSettings.get().copy();
                config.imageFormat = current.format();
                VideoSettings.CONFIG.set(config);
                VideoState.INSTANCE.overrideTestQuality(1.0F);
                Screenshots.clearOverlays(Minecraft.getInstance());
                return next(ctx, "settle");
            }
            case "settle" -> {
                if (ctx.tick() - (int) state.get(SINCE) < SETTLE_TICKS) {
                    return StepResult.pending();
                }
                if (VideoState.INSTANCE.videoLost()) {
                    return timedOut(ctx) ? fail(ctx, failures, current, "Videobild verbindet nicht") : StepResult.pending();
                }
                state.put(MARKS, new long[]{VideoOverlay.barsDrawn(), OsdHud.renderedFrames()});
                return next(ctx, "frames");
            }
            case "frames" -> {
                if (ctx.tick() - (int) state.get(SINCE) < 3) {
                    return StepResult.pending();
                }
                String problem = check(current, (long[]) state.get(MARKS));
                if (problem != null) {
                    return fail(ctx, failures, current, problem);
                }
                ImageArea area = FpvImage.area();
                AtomicReference<String> pixels = new AtomicReference<>();
                state.put(PIXELS, pixels);
                Screenshot.takeScreenshot(Minecraft.getInstance().gameRenderer.mainRenderTarget(), 1, image -> {
                    try (image) {
                        pixels.set(pixels(image, area));
                    }
                });
                return next(ctx, "pixels");
            }
            case "pixels" -> {
                String result = ((AtomicReference<String>) state.get(PIXELS)).get();
                if (result == null) {
                    return timedOut(ctx) ? fail(ctx, failures, current, "kein Bild") : StepResult.pending();
                }
                if (!result.isEmpty()) {
                    VideoState video = VideoState.INSTANCE;
                    failures.add(String.format(Locale.ROOT, "%s: %s (Verbindung %s, Signal %.2f, verloren %s, eingefroren %s)", current, result,
                            video.status(), video.signalQuality(), video.videoLost(), video.frozen()));
                }
                ctx.log(String.format(Locale.ROOT, "%s → %s, Bild %s, Sichtfeld %.1f° hoch bei %.0f° breit", current, FpvImage.format(),
                        FpvImage.area(), VideoState.INSTANCE.fpvFov(), VideoState.INSTANCE.horizontalFov()));
                return advance(ctx);
            }
            default -> {
                return StepResult.fail("unbekannte Phase " + phase);
            }
        }
    }

    private static @Nullable String check(Case current, long[] marks) {
        Minecraft minecraft = Minecraft.getInstance();
        Window window = minecraft.getWindow();
        ImageFormat resolved = FpvImage.format();
        if (resolved != current.expected()) {
            return "Format " + resolved + " statt " + current.expected();
        }
        ImageArea area = FpvImage.area();
        ImageArea expected = ImageArea.of(current.expected(), window.getWidth(), window.getHeight());
        if (!area.equals(expected)) {
            return "Bildbereich " + area + " statt " + expected;
        }
        VideoState video = VideoState.INSTANCE;
        double horizontal = video.horizontalFov();
        float fov = video.fpvFov();
        float camera = minecraft.gameRenderer.mainCamera().getFov();
        if (Math.abs(camera - fov) > 1.0E-3F) {
            return "Kamera nutzt " + camera + "° statt des FPV-Sichtfelds " + fov + "°";
        }
        double screenTanX = Math.tan(Math.toRadians(fov) / 2.0) * window.getWidth() / window.getHeight();
        double imageTanX = area.imageTangentX(screenTanX);
        double wanted = Math.tan(Math.toRadians(horizontal) / 2.0);
        if (Math.abs(imageTanX - wanted) > 1.0E-4 * wanted) {
            return String.format(Locale.ROOT, "Bildbreite zeigt %.3f° statt %.1f°", 2.0 * Math.toDegrees(Math.atan(imageTanX)), horizontal);
        }
        if (area.full() && fov != FovMath.verticalFromHorizontal(horizontal, (double) window.getWidth() / window.getHeight())) {
            return "16:9 weicht vom bisherigen Sichtfeld ab: " + fov;
        }
        if (current.expected().boxed()) {
            double real = 2.0 * Math.toDegrees(Math.atan(wanted * 3.0 / 4.0));
            double imageVertical = 2.0 * Math.toDegrees(Math.atan(area.imageTangentY(Math.tan(Math.toRadians(fov) / 2.0))));
            if (Math.abs(imageVertical - real) > 0.2) {
                return String.format(Locale.ROOT, "4:3-Bild %.2f° hoch, echte 4:3-Kamera %.2f°", imageVertical, real);
            }
        }
        OsdGrid grid = OsdHud.grid(window.getGuiScaledWidth(), window.getGuiScaledHeight());
        float scale = FpvImage.guiScale();
        if (area.full()) {
            if (!grid.equals(OsdGrid.screen(window.getGuiScaledWidth(), window.getGuiScaledHeight()))) {
                return "OSD-Raster im 16:9-Bild nicht bildschirmfüllend: " + grid;
            }
        } else if (Math.abs(grid.left() * scale - area.x()) > 0.01F || Math.abs(grid.width() * scale - area.width()) > 0.01F
                || Math.abs(grid.top() * scale - area.y()) > 0.01F || Math.abs(grid.height() * scale - area.height()) > 0.01F) {
            return "OSD-Raster " + grid + " liegt nicht im Bildbereich " + area;
        }
        if (!area.full() && VideoOverlay.barsDrawn() <= marks[0]) {
            return "keine schwarzen Ränder gezeichnet";
        }
        if (OsdHud.renderedFrames() <= marks[1]) {
            return "OSD nicht gezeichnet";
        }
        return null;
    }

    static String pixels(NativeImage image, ImageArea area) {
        if (image.getWidth() != area.screenWidth() || image.getHeight() != area.screenHeight()) {
            return "Bildgröße " + image.getWidth() + "x" + image.getHeight() + " passt nicht zu " + area;
        }
        int top = image.getHeight() / 10;
        int bottom = image.getHeight() * 9 / 10;
        if (area.pillarboxed()) {
            int[] columns = {area.x() / 2, (area.right() + image.getWidth()) / 2};
            for (int x : columns) {
                for (int y = top; y < bottom; y++) {
                    if (bright(image.getPixel(x, y)) > 6) {
                        return "Rand bei x=" + x + ", y=" + y + " nicht schwarz: " + Integer.toHexString(image.getPixel(x, y));
                    }
                }
            }
        }
        if (area.letterboxed()) {
            int[] rows = {area.y() / 2, (area.bottom() + image.getHeight()) / 2};
            for (int y : rows) {
                for (int x = image.getWidth() / 10; x < image.getWidth() * 9 / 10; x++) {
                    if (bright(image.getPixel(x, y)) > 6) {
                        return "Rand bei y=" + y + " nicht schwarz";
                    }
                }
            }
        }
        int lit = 0;
        int inside = area.x() + Math.max(1, area.width() / 8);
        for (int y = top; y < bottom; y++) {
            if (bright(image.getPixel(inside, y)) > 24) {
                lit++;
            }
        }
        if (lit == 0) {
            return "Bild innen schwarz (x=" + inside + ")";
        }
        return "";
    }

    private static int bright(int argb) {
        return Math.max((argb >> 16) & 0xFF, Math.max((argb >> 8) & 0xFF, argb & 0xFF));
    }

    private static boolean timedOut(SelfTest.StepContext ctx) {
        return ctx.tick() - (int) ctx.state().get(SINCE) > TIMEOUT_TICKS;
    }

    private static StepResult next(SelfTest.StepContext ctx, String phase) {
        ctx.state().put(PHASE, phase);
        ctx.state().put(SINCE, ctx.tick());
        return StepResult.pending();
    }

    private static StepResult fail(SelfTest.StepContext ctx, List<String> failures, Case current, String problem) {
        failures.add(current + ": " + problem);
        return advance(ctx);
    }

    private static StepResult advance(SelfTest.StepContext ctx) {
        ctx.state().put(INDEX, (int) ctx.state().get(INDEX) + 1);
        return next(ctx, "start");
    }

    private static StepResult finish(SelfTest.StepContext ctx, List<String> failures) {
        Map<String, Object> state = ctx.state();
        VideoState.INSTANCE.overrideTestQuality(Float.NaN);
        if (state.remove(SAVED) instanceof VideoConfig saved) {
            VideoSettings.CONFIG.set(saved);
        }
        PilotSession session = PilotSession.INSTANCE;
        if (session.isActive()) {
            session.clearTestInput();
            session.requestStop();
            return StepResult.pending();
        }
        CompletableFuture<?> cleanup = (CompletableFuture<?>) state.computeIfAbsent(CLEANUP, key -> ctx.onServer(FpvCoverageSelfTests::cleanup));
        if (!cleanup.isDone()) {
            return StepResult.pending();
        }
        RescueController.INSTANCE.clearHome();
        return StepResult.check(failures.isEmpty(), String.join("; ", failures));
    }

    private record Case(DroneBuild build, ImageFormat format, ImageFormat expected) {
        @Override
        public String toString() {
            return FpvCoverageSelfTests.label(this.build) + " mit " + this.format;
        }
    }
}
