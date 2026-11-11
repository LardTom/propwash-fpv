package de.lardtom.propwash.video.client;

import com.mojang.blaze3d.platform.NativeImage;
import de.lardtom.propwash.api.client.PropwashClientApi;
import de.lardtom.propwash.apiimpl.client.RecordingControlImpl;
import de.lardtom.propwash.build.VideoSystem;
import de.lardtom.propwash.dev.SelfTest;
import de.lardtom.propwash.dev.SelfTest.StepResult;
import de.lardtom.propwash.dev.client.Screenshots;
import de.lardtom.propwash.drone.QuadcopterEntity;
import de.lardtom.propwash.drone.client.PilotSession;
import de.lardtom.propwash.gpucompat.GpuPaths;
import de.lardtom.propwash.gpucompat.client.GpuFaults;
import de.lardtom.propwash.gpucompat.client.GpuPathCheck;
import de.lardtom.propwash.gpucompat.client.GuardedScreenshot;
import de.lardtom.propwash.ui.client.ConfiguratorScreen;
import de.lardtom.propwash.ui.client.config.ConfigTab;
import de.lardtom.propwash.ui.client.config.ConfigTabs;
import de.lardtom.propwash.ui.client.tab.OverviewTab;
import de.lardtom.propwash.video.FisheyeLens;
import de.lardtom.propwash.video.HeadlightCheck;
import de.lardtom.propwash.video.ImageFormat;
import de.lardtom.propwash.video.VideoCheck;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.client.multiplayer.chat.GuiMessage;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.clock.ClockTimeMarkers;
import net.minecraft.world.clock.WorldClock;
import org.jspecify.annotations.Nullable;

import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;

public final class CompatVideoSelfTests {
    public static final String FPV_STEP = "compat: Brille und Foto mit Fehler-Injektion – Blit ohne Wirkung, GPU-Rücklesen mit Nullen, kein "
            + "Rücklese-Weg: FPV-Bild sichtbar, Bildprüfung und Foto über den Ersatzweg, sonst Meldung statt Schwarz";
    public static final String NIGHT_STEP = "compat: Scheinwerfer nachts mit Blit ohne Wirkung und GPU-Rücklesen mit Nullen – Shader-Kopie, "
            + "Bildprüfung über den Ersatzweg";
    public static final String PIP_STEP = "compat: Bild-im-Bild (Konfigurator-Vorschau wie Werkbank, Share-Vorschau und Flugschule) ohne Kopie und "
            + "Rücklesen";
    private static final int TIMEOUT_TICKS = 400;
    private static final int SETTLE_TICKS = 12;
    private static final double VISIBLE_LUMA = 20.0;
    private static final double BLACK_LUMA = 8.0;
    private static final double SAME_SHARE = 0.7;
    private static final String REFERENCE = "reference";
    private static final int GRID_X = 9;
    private static final int GRID_Y = 5;
    private static final String PHASE = "phase";
    private static final String SINCE = "since";
    private static final String FAILURES = "failures";
    private static final String FUTURE = "future";
    private static final String SAVED = "saved";
    private static final String HUD = "hud";
    private static final String MEASURE = "measure";
    private static final String MARK = "mark";
    private static final String RUNS = "runs";
    private static volatile long clockTicks = -1L;

    private CompatVideoSelfTests() {
    }

    public static void register() {
        SelfTest.clientStep(FPV_STEP, 4000, CompatVideoSelfTests::fpv);
        SelfTest.clientStep(NIGHT_STEP, 2700, CompatVideoSelfTests::night);
        SelfTest.clientStep(PIP_STEP, 400, CompatVideoSelfTests::pip);
    }

    @SuppressWarnings("unchecked")
    private static StepResult fpv(SelfTest.StepContext ctx) {
        Map<String, Object> state = ctx.state();
        List<String> failures = (List<String>) state.computeIfAbsent(FAILURES, key -> new ArrayList<String>());
        if (!state.containsKey(PHASE)) {
            state.put(SAVED, VideoSettings.get().copy());
            state.put(HUD, Minecraft.getInstance().gui.hud.isHidden());
            state.put(PHASE, "spawn");
            state.put(SINCE, ctx.tick());
        }
        String phase = (String) state.get(PHASE);
        int since = ctx.tick() - (int) state.get(SINCE);
        Minecraft mc = Minecraft.getInstance();
        VideoPass pass = VideoPass.INSTANCE;
        switch (phase) {
            case "spawn" -> {
                VideoConfig config = VideoSettings.get().copy();
                config.renderer = VideoConfig.EffectRenderer.AUTO;
                config.creativeNoise = 0.0F;
                config.lensStrength = FisheyeLens.DEFAULT_PERCENT;
                config.imageFormat = ImageFormat.WIDE;
                VideoSettings.CONFIG.set(config);
                FpvLens.INSTANCE.testStrength(FisheyeLens.DEFAULT_PERCENT);
                pass.simulateFault(VideoPass.Fault.NONE);
                GpuFaults.clear();
                state.put(FUTURE, ctx.onServer(server -> {
                    noon(server);
                    VideoCheckSelfTests.spawn(server);
                }));
                return next(ctx, "fpv");
            }
            case "fpv" -> {
                CompletableFuture<?> future = (CompletableFuture<?>) state.get(FUTURE);
                if (future.isCompletedExceptionally()) {
                    return cleanup(ctx, failures, "FPV-Start: " + future.exceptionNow().getMessage());
                }
                if (!future.isDone() || PilotSession.INSTANCE.drone() == null || !PilotSession.INSTANCE.isFpv() || !VideoState.INSTANCE.active()
                        || VideoState.INSTANCE.receiver() != VideoSystem.CREATIVE) {
                    return since > TIMEOUT_TICKS ? cleanup(ctx, failures, "keine FPV-Sitzung mit Kreativ-Brille") : StepResult.pending();
                }
                VideoState.INSTANCE.overrideTestQuality(1.0F);
                if (!mc.gui.hud.isHidden()) {
                    mc.gui.hud.toggle();
                }
                Screenshots.clearOverlays(mc);
                return next(ctx, "reference");
            }
            case "reference" -> {
                Luma luma = measure(ctx, since);
                if (luma == null) {
                    return since > TIMEOUT_TICKS ? cleanup(ctx, failures, "Messung ohne Ergebnis") : StepResult.pending();
                }
                ctx.log("ohne Fehler, Fisheye " + FpvLens.INSTANCE.strengthPercent() + " %, Rauschen 0: " + luma);
                state.put(REFERENCE, luma);
                faults(ctx, true, false, false);
                state.put(MARK, new long[]{pass.copies(), GpuFaults.copies(), pass.lensFramesDrawn()});
                return next(ctx, "blit");
            }
            case "blit" -> {
                Luma luma = measure(ctx, since);
                if (luma == null) {
                    return since > TIMEOUT_TICKS ? cleanup(ctx, failures, "Messung ohne Ergebnis") : StepResult.pending();
                }
                long[] mark = (long[]) state.get(MARK);
                GpuPaths.Health health = GpuPathCheck.health();
                ctx.log(String.format(Locale.ROOT, "Blit ohne Wirkung, Fisheye %d %%, Rauschen 0: %s; Shader-Kopien %d, Blits %d, Entzerrung %d; %s",
                        FpvLens.INSTANCE.strengthPercent(), luma, pass.copies() - mark[0], GpuFaults.copies() - mark[1], pass.lensFramesDrawn() - mark[2],
                        health.describe()));
                if (!visible(state, luma)) {
                    failures.add("Blit ohne Wirkung: FPV-Bild schwarz (" + luma + ", ohne Fehler " + state.get(REFERENCE) + ")");
                }
                if (health.blit() != GpuPaths.Result.ZERO) {
                    failures.add("Prüfung erkennt den Blit ohne Wirkung nicht: " + health.describe());
                }
                if (pass.copies() <= mark[0] || pass.lensFramesDrawn() <= mark[2]) {
                    failures.add("FPV nicht über Shader-Kopie und Fisheye");
                }
                if (GpuFaults.copies() != mark[1]) {
                    failures.add("im FPV-Bild noch " + (GpuFaults.copies() - mark[1]) + " Blits (copyTextureToTexture)");
                }
                faults(ctx, false, true, false);
                return next(ctx, "check_read");
            }
            case "check_read" -> {
                VideoCheck.Outcome outcome = recheck(ctx, since);
                if (outcome == null) {
                    return since > TIMEOUT_TICKS ? cleanup(ctx, failures, "Bildprüfung mit GPU-Rücklesen ohne Wirkung ohne Ergebnis") : StepResult.pending();
                }
                String detail = String.valueOf(pass.checkDetail());
                ctx.log("GPU-Rücklesen liefert Nullen: Bildprüfung " + outcome + ", Stufe " + pass.level() + " – " + detail);
                if (outcome != VideoCheck.Outcome.PASS || !detail.contains("Rücklesen über klassisch")) {
                    failures.add("GPU-Rücklesen mit Nullen: Bildprüfung " + outcome + " statt bestanden über den Ersatzweg (" + detail + ")");
                }
                faults(ctx, false, true, true);
                return next(ctx, "check_none");
            }
            case "check_none" -> {
                VideoCheck.Outcome outcome = recheck(ctx, since);
                if (outcome == null) {
                    return since > TIMEOUT_TICKS ? cleanup(ctx, failures, "Bildprüfung ohne Rücklese-Weg ohne Ergebnis") : StepResult.pending();
                }
                String detail = String.valueOf(pass.checkDetail());
                ctx.log("kein Rücklese-Weg: Bildprüfung " + outcome + ", Stufe " + pass.level() + " – " + detail);
                if (outcome != VideoCheck.Outcome.UNAVAILABLE || !detail.contains("kein Weg")) {
                    failures.add("ohne Rücklese-Weg: Bildprüfung " + outcome + " statt nicht auswertbar mit Grund (" + detail + ")");
                }
                if (pass.level() != VideoCheck.Level.FULL) {
                    failures.add("ohne Rücklese-Weg: Videoeffekte grundlos vereinfacht (" + pass.level() + ")");
                }
                String line = Component.translatable("propwash.gpucompat.read.broken.chat", Component.translatable("propwash.gpucompat.setting"),
                        Component.translatable("propwash.gpucompat.mode.on")).getString();
                if (!GpuPathCheck.announced() || !chat().contains(line)) {
                    failures.add("keine Chat-Meldung „" + line + "“");
                }
                return next(ctx, "check_none_image");
            }
            case "check_none_image" -> {
                Luma luma = measure(ctx, since);
                if (luma == null) {
                    return since > TIMEOUT_TICKS ? cleanup(ctx, failures, "Messung ohne Ergebnis") : StepResult.pending();
                }
                ctx.log("kein Rücklese-Weg, FPV-Bild: " + luma);
                if (!visible(state, luma)) {
                    failures.add("ohne Rücklese-Weg: FPV-Bild schwarz (" + luma + ", ohne Fehler " + state.get(REFERENCE) + ")");
                }
                faults(ctx, false, true, false);
                state.put(MARK, new long[]{RecordingControlImpl.INSTANCE.photos(), RecordingControlImpl.INSTANCE.photoFailures()});
                return next(ctx, "photo_read");
            }
            case "photo_read" -> {
                long[] mark = (long[]) state.get(MARK);
                RecordingControlImpl photos = RecordingControlImpl.INSTANCE;
                if (since == SETTLE_TICKS) {
                    QuadcopterEntity drone = PilotSession.INSTANCE.drone();
                    if (drone == null || !PropwashClientApi.recording().photo(drone)) {
                        return cleanup(ctx, failures, "recording().photo ergibt false");
                    }
                }
                if (since <= SETTLE_TICKS || photos.photos() <= mark[0]) {
                    return since > TIMEOUT_TICKS ? cleanup(ctx, failures, "Foto über den Ersatzweg nicht gespeichert") : StepResult.pending();
                }
                Path file = photos.lastPhoto();
                Luma luma = file == null ? null : png(file);
                ctx.log("Foto mit GPU-Rücklesen ohne Wirkung: " + file + ", Weg " + GuardedScreenshot.lastPath().text() + ", " + luma);
                if (luma == null || !visible(state, luma)) {
                    failures.add("Foto über den Ersatzweg schwarz oder unlesbar: " + luma);
                }
                if (GuardedScreenshot.lastPath() != GpuPaths.ReadPath.CLASSIC) {
                    failures.add("Foto nicht über den Ersatzweg: " + GuardedScreenshot.lastPath());
                }
                delete(file);
                faults(ctx, false, true, true);
                state.put(MARK, new long[]{photos.photos(), photos.photoFailures()});
                return next(ctx, "photo_none");
            }
            case "photo_none" -> {
                long[] mark = (long[]) state.get(MARK);
                RecordingControlImpl photos = RecordingControlImpl.INSTANCE;
                if (since == SETTLE_TICKS) {
                    QuadcopterEntity drone = PilotSession.INSTANCE.drone();
                    if (drone == null || !PropwashClientApi.recording().photo(drone)) {
                        return cleanup(ctx, failures, "recording().photo ergibt false");
                    }
                }
                if (since <= SETTLE_TICKS || photos.photoFailures() <= mark[1]) {
                    return since > TIMEOUT_TICKS ? cleanup(ctx, failures, "Foto ohne Rücklese-Weg: keine Meldung") : StepResult.pending();
                }
                String line = Component.translatable("propwash.gpucompat.photo.failed").getString();
                ctx.log("Foto ohne Rücklese-Weg: kein Bild gespeichert, Meldung „" + line + "“");
                if (photos.photos() != mark[0]) {
                    failures.add("ohne Rücklese-Weg trotzdem ein Foto gespeichert: " + photos.lastPhoto());
                }
                if (!chat().contains(line)) {
                    failures.add("keine Chat-Meldung „" + line + "“");
                }
                return cleanup(ctx, failures, null);
            }
            case "cleanup" -> {
                CompletableFuture<?> future = (CompletableFuture<?>) state.get(FUTURE);
                if (!future.isDone()) {
                    return since > TIMEOUT_TICKS ? StepResult.fail("Aufräumen hängt") : StepResult.pending();
                }
                restore(state);
                return failures.isEmpty() ? StepResult.pass() : StepResult.fail(String.join("; ", failures));
            }
            default -> {
                return StepResult.fail("unbekannte Phase " + phase);
            }
        }
    }

    private static boolean visible(Map<String, Object> state, Luma luma) {
        Luma reference = (Luma) state.get(REFERENCE);
        double needed = reference == null ? VISIBLE_LUMA : Math.max(BLACK_LUMA, Math.min(VISIBLE_LUMA, reference.mean() * SAME_SHARE));
        return luma.mean() >= needed;
    }

    private static StepResult night(SelfTest.StepContext ctx) {
        Map<String, Object> state = ctx.state();
        if (!state.containsKey(MARK)) {
            String aligned = firstBand();
            if (aligned != null) {
                return StepResult.fail(aligned);
            }
            GpuFaults.set(true, true, false);
            GpuPathCheck.recheck();
            state.put(MARK, new long[]{HeadlightPass.INSTANCE.shaderCopies(), HeadlightPass.INSTANCE.blitCopies(), GpuFaults.copies(),
                    GpuFaults.zeroedReads()});
        }
        StepResult result;
        try {
            result = HeadlightNightSelfTests.step(ctx);
        } catch (RuntimeException exception) {
            GpuFaults.clear();
            GpuPathCheck.recheck();
            throw exception;
        }
        if (result.status() == StepResult.Status.PENDING) {
            return result;
        }
        long[] mark = (long[]) state.get(MARK);
        HeadlightPass pass = HeadlightPass.INSTANCE;
        GpuPaths.Health health = GpuPathCheck.health();
        ctx.log(String.format(Locale.ROOT, "Scheinwerfer mit %s: Shader-Kopien %d, Blits über HeadlightPass %d, copyTextureToTexture insgesamt %d, "
                        + "GPU-Rücklesen %d, Prüfungen bestanden %d, gescheitert %d, letzte %s, Rücklesen über %s; %s", GpuFaults.describe(),
                pass.shaderCopies() - mark[0], pass.blitCopies() - mark[1], GpuFaults.copies() - mark[2], GpuFaults.zeroedReads() - mark[3],
                pass.checksPassed(), pass.checksFailed(), pass.lastCheck(), HeadlightProbe.lastPath().text(), health.describe()));
        GpuFaults.clear();
        GpuPathCheck.recheck();
        List<String> failures = new ArrayList<>();
        if (result.status() == StepResult.Status.FAIL) {
            failures.add(result.message());
        }
        if (pass.shaderCopies() <= mark[0] || pass.blitCopies() != mark[1]) {
            failures.add("Scheinwerfer kopiert nicht per Shader (Shader " + (pass.shaderCopies() - mark[0]) + ", Blit " + (pass.blitCopies() - mark[1]) + ")");
        }
        if (HeadlightProbe.lastPath() != GpuPaths.ReadPath.CLASSIC) {
            failures.add("Scheinwerfer-Prüfung liest nicht über den Ersatzweg: " + HeadlightProbe.lastPath());
        }
        return failures.isEmpty() ? StepResult.pass() : StepResult.fail(String.join("; ", failures));
    }

    private static @Nullable String firstBand() {
        try {
            Field check = HeadlightPass.class.getDeclaredField("check");
            check.setAccessible(true);
            Field issued = HeadlightCheck.class.getDeclaredField("issued");
            issued.setAccessible(true);
            issued.setInt(check.get(HeadlightPass.INSTANCE), 0);
            return null;
        } catch (ReflectiveOperationException | RuntimeException exception) {
            return "Prüfstreifen des Scheinwerfers nicht auf den ersten gesetzt: " + exception;
        }
    }

    private static StepResult pip(SelfTest.StepContext ctx) {
        Minecraft mc = Minecraft.getInstance();
        Map<String, Object> state = ctx.state();
        if (!state.containsKey(MARK)) {
            GpuFaults.set(true, true, true);
            ConfigTabs.open(null, OverviewTab.ID);
            state.put(MARK, new long[]{-1L, -1L});
            state.put(SINCE, ctx.tick());
            return StepResult.pending();
        }
        long[] mark = (long[]) state.get(MARK);
        int since = ctx.tick() - (int) state.get(SINCE);
        if (!(mc.gui.screen() instanceof ConfiguratorScreen screen)) {
            return since > 100 ? finishPip(StepResult.fail("Konfigurator öffnet nicht")) : StepResult.pending();
        }
        ConfigTab active = screen.activeTab();
        if (active == null || !OverviewTab.ID.equals(active.id())) {
            screen.selectTab(OverviewTab.ID);
            return StepResult.pending();
        }
        if (mark[0] < 0L) {
            mark[0] = GpuFaults.copies();
            mark[1] = GpuFaults.reads();
            state.put(SINCE, ctx.tick());
            return StepResult.pending();
        }
        if (since < 40) {
            return StepResult.pending();
        }
        long copies = GpuFaults.copies() - mark[0];
        long reads = GpuFaults.reads() - mark[1];
        ctx.log("Konfigurator mit Drohnen-Vorschau (Bild-im-Bild) 40 Ticks lang mit " + GpuFaults.describe() + ": copyTextureToTexture " + copies
                + "-mal, copyTextureToBuffer " + reads + "-mal");
        screen.discardAndClose();
        return finishPip(copies == 0L && reads == 0L ? StepResult.pass()
                : StepResult.fail("Bild-im-Bild ruft Kopie (" + copies + ") oder Rücklesen (" + reads + ") auf"));
    }

    private static StepResult finishPip(StepResult result) {
        GpuFaults.clear();
        GpuPathCheck.recheck();
        return result;
    }

    private static void faults(SelfTest.StepContext ctx, boolean copy, boolean read, boolean classic) {
        GpuFaults.set(copy, read, classic);
        GpuPaths.Health health = GpuPathCheck.recheck();
        ctx.log("Fehler-Injektion: " + GpuFaults.describe() + " → " + health.describe());
    }

    private static VideoCheck.@Nullable Outcome recheck(SelfTest.StepContext ctx, int since) {
        VideoPass pass = VideoPass.INSTANCE;
        if (since == 0) {
            ctx.state().put(RUNS, pass.checkRuns());
            pass.recheck();
            return null;
        }
        int runs = (int) ctx.state().get(RUNS);
        if (pass.checkRuns() <= runs || pass.checkRunning() || pass.checkOutcome() == VideoCheck.Outcome.PENDING) {
            return null;
        }
        return pass.checkOutcome();
    }

    @SuppressWarnings("unchecked")
    private static @Nullable Luma measure(SelfTest.StepContext ctx, int since) {
        Map<String, Object> state = ctx.state();
        if (since < SETTLE_TICKS) {
            state.remove(MEASURE);
            return null;
        }
        if (!state.containsKey(MEASURE)) {
            AtomicReference<Luma> result = new AtomicReference<>();
            state.put(MEASURE, result);
            Screenshot.takeScreenshot(Minecraft.getInstance().gameRenderer.mainRenderTarget(), 1, image -> {
                try (image) {
                    result.set(luma(image));
                }
            });
            return null;
        }
        Luma luma = ((AtomicReference<Luma>) state.get(MEASURE)).get();
        if (luma != null) {
            state.remove(MEASURE);
        }
        return luma;
    }

    private static @Nullable Luma png(Path file) {
        try (InputStream in = Files.newInputStream(file); NativeImage image = NativeImage.read(in)) {
            return luma(image);
        } catch (IOException | RuntimeException exception) {
            return null;
        }
    }

    private static Luma luma(NativeImage image) {
        int width = image.getWidth();
        int height = image.getHeight();
        double sum = 0.0;
        int dark = 0;
        for (int gy = 0; gy < GRID_Y; gy++) {
            for (int gx = 0; gx < GRID_X; gx++) {
                int x = (int) Math.round(width * (0.1 + 0.8 * gx / (GRID_X - 1.0)));
                int y = (int) Math.round(height * (0.1 + 0.8 * gy / (GRID_Y - 1.0)));
                int argb = image.getPixel(Math.min(width - 1, x), Math.min(height - 1, y));
                double l = VideoCheck.luma((argb >> 16) & 0xFF, (argb >> 8) & 0xFF, argb & 0xFF);
                sum += l;
                if (l < 8.0) {
                    dark++;
                }
            }
        }
        return new Luma(sum / (GRID_X * GRID_Y), dark, GRID_X * GRID_Y);
    }

    private static void delete(@Nullable Path file) {
        if (file == null) {
            return;
        }
        try {
            Files.deleteIfExists(file);
        } catch (IOException ignored) {
        }
    }

    private static List<String> chat() {
        try {
            Field field = ChatComponent.class.getDeclaredField("allMessages");
            field.setAccessible(true);
            List<?> messages = (List<?>) field.get(Minecraft.getInstance().gui.hud.getChat());
            List<String> out = new ArrayList<>();
            for (Object message : messages) {
                if (message instanceof GuiMessage gui) {
                    out.add(gui.content().getString());
                }
            }
            return out;
        } catch (ReflectiveOperationException | RuntimeException e) {
            return List.of("? " + e);
        }
    }

    private static StepResult next(SelfTest.StepContext ctx, String phase) {
        ctx.state().put(PHASE, phase);
        ctx.state().put(SINCE, ctx.tick() + 1);
        return StepResult.pending();
    }

    private static StepResult cleanup(SelfTest.StepContext ctx, List<String> failures, @Nullable String problem) {
        if (problem != null) {
            failures.add(problem);
        }
        GpuFaults.clear();
        GpuPathCheck.recheck();
        VideoPass.INSTANCE.simulateFault(VideoPass.Fault.NONE);
        VideoPass.INSTANCE.retryAll();
        VideoState.INSTANCE.overrideTestQuality(Float.NaN);
        PilotSession.INSTANCE.clearTestInput();
        ctx.state().put(FUTURE, ctx.onServer(server -> {
            VideoCheckSelfTests.release(server);
            restoreClock(server);
        }));
        return next(ctx, "cleanup");
    }

    private static void noon(MinecraftServer server) {
        Optional<Holder<WorldClock>> clock = server.overworld().dimensionType().defaultClock();
        if (clock.isEmpty()) {
            return;
        }
        clockTicks = server.clockManager().getInstance(clock.get()).totalTicks();
        server.clockManager().moveToTimeMarker(clock.get(), ClockTimeMarkers.NOON);
    }

    private static void restoreClock(MinecraftServer server) {
        long ticks = clockTicks;
        clockTicks = -1L;
        Optional<Holder<WorldClock>> clock = server.overworld().dimensionType().defaultClock();
        if (ticks >= 0L && clock.isPresent()) {
            server.clockManager().setTotalTicks(clock.get(), ticks);
        }
    }

    private static void restore(Map<String, Object> state) {
        Minecraft mc = Minecraft.getInstance();
        FisheyeSelfTests.flatForOtherSteps();
        if (state.remove(SAVED) instanceof VideoConfig saved) {
            VideoSettings.CONFIG.set(saved);
        }
        if (state.remove(HUD) instanceof Boolean hidden && mc.gui.hud.isHidden() != hidden) {
            mc.gui.hud.toggle();
        }
    }

    record Luma(double mean, int dark, int points) {
        @Override
        public String toString() {
            return String.format(Locale.ROOT, "Helligkeit Mittel %.1f, %d von %d Punkten schwarz", this.mean, this.dark, this.points);
        }
    }
}
