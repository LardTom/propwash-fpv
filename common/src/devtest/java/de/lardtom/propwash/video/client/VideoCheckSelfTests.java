package de.lardtom.propwash.video.client;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.renderpearl.api.GpuFormat;
import com.mojang.renderpearl.api.buffers.GpuBuffer;
import com.mojang.renderpearl.api.buffers.GpuBufferSlice;
import com.mojang.renderpearl.api.device.GpuDevice;
import com.mojang.renderpearl.api.pipeline.CompiledRenderPipeline;
import com.mojang.renderpearl.api.textures.GpuTexture;
import com.mojang.renderpearl.api.textures.GpuTextureView;
import com.mojang.renderpearl.frontend.FrontendGpuDevice;
import de.lardtom.propwash.build.DroneBuild;
import de.lardtom.propwash.build.VideoSystem;
import de.lardtom.propwash.content.ContentIds;
import de.lardtom.propwash.core.client.ClientBus;
import de.lardtom.propwash.core.CoreComponents;
import de.lardtom.propwash.dev.SelfTest;
import de.lardtom.propwash.dev.SelfTest.StepResult;
import de.lardtom.propwash.dev.client.Screenshots;
import de.lardtom.propwash.drone.DroneInteractions;
import de.lardtom.propwash.drone.DroneModule;
import de.lardtom.propwash.drone.DronePiloting;
import de.lardtom.propwash.drone.PilotMode;
import de.lardtom.propwash.drone.QuadcopterEntity;
import de.lardtom.propwash.drone.client.PilotSession;
import de.lardtom.propwash.ui.client.config.ConfigTabHost;
import de.lardtom.propwash.video.FisheyeLens;
import de.lardtom.propwash.video.ImageFormat;
import de.lardtom.propwash.video.VideoCheck;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Util;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;

public final class VideoCheckSelfTests {
    public static final String STEP = "video: Bildprüfung und Ersatz-Shader – FPV mit Rauschen 0 und > 0 nicht schwarz, Fehlerbild nachgestellt, ungebundener Sampler";
    public static final String TAB_STEP = "video: Einstellung Videoeffekte Automatisch/Voll/Einfach im Video-Tab";
    private static final int TIMEOUT_TICKS = 400;
    private static final int SETTLE_TICKS = 12;
    private static final double BLACK_LUMA = 8.0;
    private static final double VISIBLE_LUMA = 20.0;
    private static final float NOISE = 0.3F;
    private static final float WEAK = 0.35F;
    private static final int GRID_X = 9;
    private static final int GRID_Y = 5;
    private static final String PHASE = "phase";
    private static final String SINCE = "since";
    private static final String FUTURE = "future";
    private static final String SAVED = "saved";
    private static final String HUD = "hud";
    private static final String SHOT = "shot";
    private static final String SHOT_NAME = "shot_name";
    private static final String SHOT_THEN = "shot_then";
    private static final String MEASURE = "measure";
    private static final String RUNS = "runs";
    private static final String MARK = "mark";
    private static final String FAILURES = "failures";
    private static final int TIMING_ROUNDS = 6;
    private static final int TIMING_TICKS = 10;
    private static final int TIMING_COPIES = 20;
    private static volatile int droneId = -1;
    private static volatile @Nullable ItemStack previousHead;
    private static volatile @Nullable ItemStack previousHand;

    private VideoCheckSelfTests() {
    }

    public static void register() {
        SelfTest.clientStep(STEP, 4000, VideoCheckSelfTests::step);
        ClientBus.FRAME_START.register(CopyLoad::frame);
        SelfTest.clientStep(TAB_STEP, 40, VideoCheckSelfTests::tabStep);
    }

    @SuppressWarnings("unchecked")
    private static StepResult step(SelfTest.StepContext ctx) {
        Map<String, Object> state = ctx.state();
        List<String> failures = (List<String>) state.computeIfAbsent(FAILURES, key -> new ArrayList<String>());
        if (!state.containsKey(PHASE)) {
            state.put(SAVED, VideoSettings.get().copy());
            state.put(HUD, Minecraft.getInstance().gui.hud.isHidden());
            state.put(PHASE, Screenshots.enabled() ? "window" : "spawn");
            state.put(SINCE, ctx.tick());
        }
        String phase = (String) state.get(PHASE);
        int since = ctx.tick() - (int) state.get(SINCE);
        Minecraft mc = Minecraft.getInstance();
        VideoPass pass = VideoPass.INSTANCE;
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
                VideoConfig config = VideoSettings.get().copy();
                config.renderer = VideoConfig.EffectRenderer.AUTO;
                config.creativeNoise = 0.0F;
                config.lensStrength = FisheyeLens.DEFAULT_PERCENT;
                config.imageFormat = ImageFormat.WIDE;
                VideoSettings.CONFIG.set(config);
                FpvLens.INSTANCE.testStrength(FisheyeLens.DEFAULT_PERCENT);
                pass.simulateFault(VideoPass.Fault.NONE);
                state.put(FUTURE, ctx.onServer(VideoCheckSelfTests::spawn));
                return next(ctx, "fpv");
            }
            case "fpv" -> {
                CompletableFuture<?> future = (CompletableFuture<?>) state.get(FUTURE);
                if (future.isCompletedExceptionally()) {
                    return cleanup(ctx, failures, "FPV-Start: " + future.exceptionNow().getMessage());
                }
                QuadcopterEntity drone = PilotSession.INSTANCE.drone();
                if (!future.isDone() || drone == null || drone.getId() != droneId || !PilotSession.INSTANCE.isFpv() || !VideoState.INSTANCE.active()
                        || VideoState.INSTANCE.receiver() != VideoSystem.CREATIVE) {
                    return since > TIMEOUT_TICKS ? cleanup(ctx, failures, "keine FPV-Sitzung mit Kreativ-Brille") : StepResult.pending();
                }
                VideoState.INSTANCE.overrideTestQuality(1.0F);
                if (!mc.gui.hud.isHidden()) {
                    mc.gui.hud.toggle();
                }
                Screenshots.clearOverlays(mc);
                return next(ctx, "check");
            }
            case "check" -> {
                VideoCheck.Outcome outcome = recheck(ctx, since);
                if (outcome == null) {
                    return since > TIMEOUT_TICKS ? cleanup(ctx, failures, "Bildprüfung ohne Ergebnis") : StepResult.pending();
                }
                ctx.log("Bildprüfung: " + outcome + " – " + pass.checkDetail());
                ctx.log("Fehlerausgabe: " + VideoSelfCheck.debugState());
                if (outcome != VideoCheck.Outcome.PASS) {
                    failures.add("Bildprüfung ohne Fehler ergab " + outcome + ": " + pass.checkDetail());
                }
                return next(ctx, "identity");
            }
            case "identity" -> {
                if (since < SETTLE_TICKS) {
                    return StepResult.pending();
                }
                if (!state.containsKey(MEASURE)) {
                    state.put(MEASURE, identity());
                    return StepResult.pending();
                }
                @SuppressWarnings("unchecked")
                String compared = ((AtomicReference<String>) state.get(MEASURE)).get();
                if (compared == null) {
                    return since > TIMEOUT_TICKS ? cleanup(ctx, failures, "Vergleich Blit gegen Shader-Kopie ohne Ergebnis") : StepResult.pending();
                }
                state.remove(MEASURE);
                ctx.log("Kopie per Shader gegen Blit: " + compared);
                if (!compared.startsWith("gleich")) {
                    failures.add("Shader-Kopie weicht vom Blit ab: " + compared);
                }
                return next(ctx, "cost");
            }
            case "cost" -> {
                if (since == 0) {
                    CopyLoad.reset();
                }
                if (since < TIMING_ROUNDS * TIMING_TICKS) {
                    if (since % TIMING_TICKS == 0) {
                        CopyLoad.start(since / TIMING_TICKS % 2 == 0);
                    }
                    return StepResult.pending();
                }
                CopyLoad.stop();
                ctx.log(CopyLoad.summary());
                state.put(MARK, new long[]{pass.copies(), pass.lensFramesDrawn(), pass.framesDrawn()});
                return next(ctx, "clean");
            }
            case "clean" -> {
                Luma luma = measure(ctx, since, 0.0F);
                if (luma == null) {
                    return StepResult.pending();
                }
                long[] mark = (long[]) state.get(MARK);
                ctx.log(String.format(Locale.ROOT, "Rauschen 0, Fisheye %d %%: %s, Kopien %d, Entzerrung %d, Effekt-Pass %d", FpvLens.INSTANCE.strengthPercent(),
                        luma, pass.copies() - mark[0], pass.lensFramesDrawn() - mark[1], pass.framesDrawn() - mark[2]));
                if (luma.mean() < VISIBLE_LUMA) {
                    failures.add("Rauschen 0: Bild schwarz (" + luma + ")");
                }
                if (FpvLens.INSTANCE.rendered() == null || pass.lensFramesDrawn() <= mark[1] || pass.copies() <= mark[0]) {
                    failures.add("Rauschen 0: nicht der Weg Kopie + Fisheye (Objektiv " + FpvLens.INSTANCE.rendered() + ", Kopien " + (pass.copies() - mark[0])
                            + ", Entzerrung " + (pass.lensFramesDrawn() - mark[1]) + ")");
                }
                if (pass.framesDrawn() != mark[2]) {
                    failures.add("Rauschen 0: Effekt-Pass lief " + (pass.framesDrawn() - mark[2]) + "-mal");
                }
                state.put(MARK, new long[]{pass.copies(), pass.lensFramesDrawn(), pass.framesDrawn()});
                return next(ctx, "noisy");
            }
            case "noisy" -> {
                Luma luma = measure(ctx, since, NOISE);
                if (luma == null) {
                    return StepResult.pending();
                }
                long[] mark = (long[]) state.get(MARK);
                ctx.log(String.format(Locale.ROOT, "Rauschen %.0f %%: %s, Effekt-Pass %d", NOISE * 100.0F, luma, pass.framesDrawn() - mark[2]));
                if (luma.mean() < VISIBLE_LUMA) {
                    failures.add("Rauschen > 0: Bild schwarz (" + luma + ")");
                }
                if (pass.framesDrawn() <= mark[2]) {
                    failures.add("Rauschen > 0: Effekt-Pass lief nicht");
                }
                fault(VideoPass.Fault.COPY_NOOP);
                return next(ctx, "repro");
            }
            case "repro" -> {
                if (since == 1) {
                    pass.release();
                }
                Luma luma = measure(ctx, since, 0.0F);
                if (luma == null) {
                    return StepResult.pending();
                }
                ctx.log("Fehlerbild nachgestellt (Kopie schreibt nicht, wie der Blit auf Intel): Rauschen 0 → " + luma);
                if (luma.mean() >= BLACK_LUMA) {
                    failures.add("Kopie ohne Wirkung, Rauschen 0: Bild nicht schwarz (" + luma + ")");
                }
                return toShot(ctx, "video_check_fault_black", "repro_noisy");
            }
            case "repro_noisy" -> {
                Luma luma = measure(ctx, since, NOISE);
                if (luma == null) {
                    return StepResult.pending();
                }
                ctx.log("Fehlerbild nachgestellt: Rauschen > 0 → " + luma + " (Umgehung aus dem Discord)");
                if (luma.mean() < VISIBLE_LUMA) {
                    failures.add("Kopie ohne Wirkung, Rauschen > 0: Bild schwarz (" + luma + ")");
                }
                VideoSettings.get().creativeNoise = 0.0F;
                return next(ctx, "repro_check");
            }
            case "repro_check" -> {
                VideoCheck.Outcome outcome = recheck(ctx, since);
                if (outcome == null) {
                    return since > TIMEOUT_TICKS ? cleanup(ctx, failures, "Bildprüfung mit kaputter Kopie ohne Ergebnis") : StepResult.pending();
                }
                ctx.log("Bildprüfung mit kaputter Kopie: " + outcome + ", Stufe " + pass.level() + " – " + pass.checkDetail());
                if (outcome != VideoCheck.Outcome.ALL_BROKEN || pass.level() != VideoCheck.Level.NONE) {
                    failures.add("kaputte Kopie: Bildprüfung " + outcome + ", Stufe " + pass.level() + " statt ALL_BROKEN/NONE");
                }
                if (!VideoSelfCheck.announced()) {
                    failures.add("keine Chat-Meldung „Videoeffekte vereinfacht“");
                }
                return next(ctx, "repro_after");
            }
            case "repro_after" -> {
                Luma luma = measure(ctx, since, 0.0F);
                if (luma == null) {
                    return StepResult.pending();
                }
                ctx.log("nach der Bildprüfung ohne Shader: Rauschen 0 → " + luma + ", Objektiv " + (FpvLens.INSTANCE.rendered() == null ? "flach" : "Fisheye"));
                if (luma.mean() < VISIBLE_LUMA) {
                    failures.add("nach der Bildprüfung ohne Shader weiter schwarz (" + luma + ")");
                }
                if (FpvLens.INSTANCE.rendered() != null) {
                    failures.add("ohne Shader noch Fisheye");
                }
                fault(VideoPass.Fault.FEED_BLACK);
                return next(ctx, "simple_check");
            }
            case "simple_check" -> {
                VideoCheck.Outcome outcome = recheck(ctx, since);
                if (outcome == null) {
                    return since > TIMEOUT_TICKS ? cleanup(ctx, failures, "Bildprüfung mit schwarzem Video-Shader ohne Ergebnis") : StepResult.pending();
                }
                ctx.log("Bildprüfung mit schwarzem Video-Shader: " + outcome + ", Stufe " + pass.level() + " – " + pass.checkDetail());
                if (outcome != VideoCheck.Outcome.FULL_BROKEN || pass.level() != VideoCheck.Level.SIMPLE) {
                    failures.add("schwarzer Video-Shader: Bildprüfung " + outcome + ", Stufe " + pass.level() + " statt FULL_BROKEN/SIMPLE");
                }
                return next(ctx, "simple");
            }
            case "simple" -> {
                Luma luma = measure(ctx, since, NOISE);
                if (luma == null) {
                    return StepResult.pending();
                }
                ctx.log("Ersatz-Shader (Einfach), Rauschen > 0 als Einblendung: " + luma + ", Overlay " + pass.overlayFallback());
                if (luma.mean() < VISIBLE_LUMA) {
                    failures.add("Einfach: Bild schwarz (" + luma + ")");
                }
                if (!pass.overlayFallback() || FpvLens.INSTANCE.rendered() != null) {
                    failures.add("Einfach: Overlay " + pass.overlayFallback() + ", Objektiv " + FpvLens.INSTANCE.rendered());
                }
                return toShot(ctx, "video_check_simple", "hold");
            }
            case "shot" -> {
                return shot(ctx, failures);
            }
            case "hold" -> {
                if (since == 0) {
                    VideoSettings.get().creativeNoise = 0.0F;
                    VideoState.INSTANCE.overrideTestReceiver(true, VideoSystem.DIGITAL);
                    VideoState.INSTANCE.overrideTestQuality(WEAK);
                    state.put(MARK, new long[]{pass.simpleFramesDrawn(), pass.copies()});
                }
                Luma luma = measure(ctx, since, 0.0F);
                if (luma == null) {
                    return StepResult.pending();
                }
                long[] mark = (long[]) state.get(MARK);
                ctx.log(String.format(Locale.ROOT, "Ersatz-Shader hält Bilder (digital, Signal %.0f %%): %d gehaltene Bilder, %d Kopien, %s", WEAK * 100.0F,
                        pass.simpleFramesDrawn() - mark[0], pass.copies() - mark[1], luma));
                if (pass.simpleFramesDrawn() <= mark[0]) {
                    failures.add("Ersatz-Shader zeichnete keine gehaltenen Bilder");
                }
                if (luma.mean() < VISIBLE_LUMA) {
                    failures.add("Ersatz-Shader mit gehaltenen Bildern: schwarz (" + luma + ")");
                }
                fault(VideoPass.Fault.NONE);
                return next(ctx, "healthy");
            }
            case "healthy" -> {
                VideoCheck.Outcome outcome = recheck(ctx, since);
                if (outcome == null) {
                    return since > TIMEOUT_TICKS ? cleanup(ctx, failures, "Bildprüfung nach den Fehlern ohne Ergebnis") : StepResult.pending();
                }
                if (outcome != VideoCheck.Outcome.PASS || pass.level() != VideoCheck.Level.FULL) {
                    failures.add("nach den Fehlern: Bildprüfung " + outcome + ", Stufe " + pass.level());
                }
                state.put(MARK, new long[]{pass.framesDrawn()});
                pass.simulateFault(VideoPass.Fault.UNBOUND_HISTORY);
                return next(ctx, "unbound");
            }
            case "unbound" -> {
                Luma luma = measure(ctx, since, 0.0F);
                if (luma == null) {
                    return StepResult.pending();
                }
                long[] mark = (long[]) state.get(MARK);
                boolean strict = FrontendGpuDevice.STRICT_VALIDATION;
                String detail = String.valueOf(pass.failureDetail());
                ctx.log(String.format(Locale.ROOT, "HistorySampler nicht gebunden (digital, Signal %.0f %%, Blockfehler lesen den Verlauf): strenge Prüfung %s, "
                                + "Fehler %s (%s), Effekt-Pass %d Bilder, %s, Fehlerausgabe %s, OpenGL-Meldungen %s", WEAK * 100.0F, strict, pass.failure(), detail,
                        pass.framesDrawn() - mark[0], luma, VideoSelfCheck.debugState(), VideoSelfCheck.debugMessages()));
                if (strict && (pass.failure() != VideoPass.Failure.ERROR || !detail.contains("HistorySampler") || !pass.overlayFallback())) {
                    failures.add("ungebundener Sampler bei strenger Prüfung: " + pass.failure() + " / " + detail);
                }
                if (!strict && (pass.failure() != null || pass.framesDrawn() <= mark[0])) {
                    failures.add("ungebundener Sampler ohne strenge Prüfung: Fehler " + pass.failure() + " (" + detail + "), Effekt-Pass "
                            + (pass.framesDrawn() - mark[0]));
                }
                if (luma.mean() < VISIBLE_LUMA) {
                    failures.add("ungebundener Sampler: Bild schwarz (" + luma + ")");
                }
                pass.simulateFault(VideoPass.Fault.NONE);
                pass.retry();
                VideoState.INSTANCE.overrideTestReceiver(false, null);
                VideoState.INSTANCE.overrideTestQuality(1.0F);
                VideoSettings.get().creativeNoise = NOISE;
                state.put(MARK, new long[]{pass.framesDrawn()});
                return next(ctx, "resume");
            }
            case "resume" -> {
                long[] mark = (long[]) state.get(MARK);
                if (pass.framesDrawn() <= mark[0] + 3) {
                    return since > 100 ? cleanup(ctx, failures, "Effekt-Pass läuft nach „Shader erneut versuchen“ nicht wieder") : StepResult.pending();
                }
                ctx.log("OpenGL-Meldungen des Geräts: " + VideoSelfCheck.debugMessages());
                return cleanup(ctx, failures, null);
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
                return failures.isEmpty() ? StepResult.pass() : StepResult.fail(String.join("; ", failures));
            }
            default -> {
                return StepResult.fail("unbekannte Phase " + phase);
            }
        }
    }

    private static StepResult tabStep(SelfTest.StepContext ctx) {
        Language language = Language.getInstance();
        List<String> missing = new ArrayList<>();
        for (VideoConfig.EffectRenderer renderer : VideoConfig.EffectRenderer.values()) {
            for (String key : List.of(renderer.translationKey(), renderer.tooltipKey())) {
                if (!language.has(key)) {
                    missing.add(key);
                }
            }
        }
        for (String key : List.of("propwash.video.simplified.chat", "propwash.video.status.simplified", "propwash.video.status.simplified.none")) {
            if (!language.has(key)) {
                missing.add(key);
            }
        }
        if (!missing.isEmpty()) {
            return StepResult.fail("Übersetzungen fehlen: " + missing);
        }
        VideoConfig saved = VideoSettings.get().copy();
        List<AbstractWidget> widgets = new ArrayList<>();
        VideoState.INSTANCE.overrideTestReceiver(true, VideoSystem.ANALOG);
        VideoConfigTab tab = new VideoConfigTab();
        try {
            VideoConfig config = saved.copy();
            config.renderer = VideoConfig.EffectRenderer.AUTO;
            VideoSettings.CONFIG.set(config);
            tab.attach(host(widgets), 0, 0, 480, 280);
            String label = Component.translatable("propwash.video.config.renderer").getString();
            CycleButton<?> button = null;
            for (AbstractWidget widget : widgets) {
                if (widget instanceof CycleButton<?> cycle && widget.getMessage().getString().startsWith(label)) {
                    button = cycle;
                }
            }
            if (button == null) {
                return StepResult.fail("kein Knopf „" + label + "“ im Video-Tab");
            }
            List<String> seen = new ArrayList<>();
            for (int i = 0; i < 3; i++) {
                seen.add(button.getMessage().getString() + " = " + button.getValue());
                button.mouseScrolled(0.0, 0.0, 0.0, -1.0);
            }
            ctx.log("Knopf: " + String.join(" → ", seen) + " → " + button.getMessage().getString());
            List<VideoConfig.EffectRenderer> order = List.of(VideoConfig.EffectRenderer.AUTO, VideoConfig.EffectRenderer.FULL,
                    VideoConfig.EffectRenderer.OVERLAY);
            for (int i = 0; i < order.size(); i++) {
                String expected = label + ": " + Component.translatable(order.get(i).translationKey()).getString();
                if (!seen.get(i).startsWith(expected + " = " + order.get(i))) {
                    return StepResult.fail("Knopf zeigt „" + seen.get(i) + "“ statt „" + expected + "“");
                }
            }
            if (button.getValue() != VideoConfig.EffectRenderer.AUTO) {
                return StepResult.fail("nach drei Klicks nicht wieder Automatisch: " + button.getValue());
            }
            return StepResult.pass();
        } finally {
            tab.discard();
            VideoSettings.CONFIG.set(saved);
            VideoState.INSTANCE.overrideTestReceiver(false, null);
        }
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

    private static AtomicReference<String> identity() {
        AtomicReference<String> out = new AtomicReference<>();
        RenderTarget main = Minecraft.getInstance().gameRenderer.mainRenderTarget();
        CompiledRenderPipeline simple = RenderSystem.getCompiledPipelineNullable(VideoPass.SIMPLE_PIPELINE);
        GpuTextureView source = main.getColorTextureView();
        int width = main.width;
        int height = main.height;
        if (simple == null || source == null || width <= 0 || height <= 0) {
            out.set("nicht möglich (Pipeline " + simple + ", Quelle " + source + ")");
            return out;
        }
        GpuDevice device = RenderSystem.getDevice();
        TextureTarget blit = new TextureTarget("propwash check blit", width, height, GpuFormat.RGBA8_UNORM, null);
        TextureTarget shader = new TextureTarget("propwash check shader", width, height, GpuFormat.RGBA8_UNORM, null);
        GpuTextureView shaderView = shader.getColorTextureView();
        GpuTexture blitTexture = blit.getColorTexture();
        GpuTexture shaderTexture = shader.getColorTexture();
        if (shaderView == null || blitTexture == null || shaderTexture == null) {
            blit.destroyBuffers();
            shader.destroyBuffers();
            out.set("nicht möglich (Ziele ohne Farbtextur)");
            return out;
        }
        blit.copyColorFrom(main);
        VideoPass.INSTANCE.copy(simple, source, shaderView);
        int size = width * height * VideoCheck.BYTES_PER_PIXEL;
        int usage = GpuBuffer.USAGE_MAP_READ | GpuBuffer.USAGE_COPY_DST;
        GpuBuffer first = device.createBuffer(() -> "propwash check blit read", usage, size);
        GpuBuffer second = device.createBuffer(() -> "propwash check shader read", usage, size);
        byte[][] data = new byte[2][];
        int[] pending = {2};
        Runnable done = () -> {
            pending[0]--;
            if (pending[0] > 0) {
                return;
            }
            first.close();
            second.close();
            blit.destroyBuffers();
            shader.destroyBuffers();
            out.set(compare(data[0], data[1], width, height));
        };
        device.createCommandEncoder().copyTextureToBuffer(blitTexture, first, 0L, () -> {
            data[0] = read(first, size);
            done.run();
        }, 0);
        device.createCommandEncoder().copyTextureToBuffer(shaderTexture, second, 0L, () -> {
            data[1] = read(second, size);
            done.run();
        }, 0);
        return out;
    }

    private static byte @Nullable [] read(GpuBuffer buffer, int size) {
        try (GpuBufferSlice.MappedView view = buffer.map(0L, size, true, false)) {
            byte[] bytes = new byte[size];
            view.data().get(0, bytes);
            return bytes;
        } catch (RuntimeException exception) {
            return null;
        }
    }

    private static String compare(byte @Nullable [] blit, byte @Nullable [] shader, int width, int height) {
        if (blit == null || shader == null || blit.length != shader.length) {
            return "Rücklesen fehlgeschlagen";
        }
        int differing = 0;
        int worst = 0;
        int alphaDiffering = 0;
        for (int o = 0; o < blit.length; o += VideoCheck.BYTES_PER_PIXEL) {
            int pixel = 0;
            for (int c = 0; c < 3; c++) {
                pixel = Math.max(pixel, Math.abs((blit[o + c] & 0xFF) - (shader[o + c] & 0xFF)));
            }
            if (pixel > 0) {
                differing++;
                worst = Math.max(worst, pixel);
            }
            if (blit[o + 3] != shader[o + 3]) {
                alphaDiffering++;
            }
        }
        return String.format(Locale.ROOT, "%s: %d von %d Bildpunkten mit anderem RGB (größte Abweichung %d), Alpha anders bei %d (Folge-Shader lesen nur RGB)",
                differing == 0 ? "gleich" : "verschieden", differing, width * height, worst, alphaDiffering);
    }

    private static void fault(VideoPass.Fault fault) {
        VideoPass.INSTANCE.simulateFault(fault);
        VideoPass.INSTANCE.release();
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
    private static @Nullable Luma measure(SelfTest.StepContext ctx, int since, float noise) {
        Map<String, Object> state = ctx.state();
        if (since == 0) {
            VideoSettings.get().creativeNoise = noise;
            state.remove(MEASURE);
            return null;
        }
        if (since < SETTLE_TICKS) {
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

    private static Luma luma(NativeImage image) {
        int width = image.getWidth();
        int height = image.getHeight();
        double sum = 0.0;
        double brightest = 0.0;
        int dark = 0;
        for (int gy = 0; gy < GRID_Y; gy++) {
            for (int gx = 0; gx < GRID_X; gx++) {
                int x = (int) Math.round(width * (0.1 + 0.8 * gx / (GRID_X - 1.0)));
                int y = (int) Math.round(height * (0.1 + 0.8 * gy / (GRID_Y - 1.0)));
                int argb = image.getPixel(Math.min(width - 1, x), Math.min(height - 1, y));
                double l = VideoCheck.luma((argb >> 16) & 0xFF, (argb >> 8) & 0xFF, argb & 0xFF);
                sum += l;
                brightest = Math.max(brightest, l);
                if (l < BLACK_LUMA) {
                    dark++;
                }
            }
        }
        return new Luma(sum / (GRID_X * GRID_Y), brightest, dark, GRID_X * GRID_Y);
    }

    private static StepResult toShot(SelfTest.StepContext ctx, String name, String then) {
        if (!Screenshots.enabled()) {
            return next(ctx, then);
        }
        ctx.state().put(SHOT_NAME, name);
        ctx.state().put(SHOT_THEN, then);
        return next(ctx, "shot");
    }

    @SuppressWarnings("unchecked")
    private static StepResult shot(SelfTest.StepContext ctx, List<String> failures) {
        Map<String, Object> state = ctx.state();
        String name = (String) state.get(SHOT_NAME);
        String then = (String) state.get(SHOT_THEN);
        Minecraft mc = Minecraft.getInstance();
        if (!state.containsKey(SHOT)) {
            if (mc.gui.hud.isHidden()) {
                mc.gui.hud.toggle();
            }
            mc.gui.toastManager().clear();
            if (ctx.tick() - (int) state.get(SINCE) < 4) {
                return StepResult.pending();
            }
            state.put(SHOT, Screenshots.capture(name));
            return StepResult.pending();
        }
        CompletableFuture<String> future = (CompletableFuture<String>) state.get(SHOT);
        if (!future.isDone()) {
            return StepResult.pending();
        }
        state.remove(SHOT);
        if (!mc.gui.hud.isHidden()) {
            mc.gui.hud.toggle();
        }
        if (future.isCompletedExceptionally()) {
            failures.add("Bildschirmfoto " + name + ": " + future.exceptionNow());
        } else {
            ctx.log("Bildschirmfoto " + Screenshots.dir().resolve(Screenshots.fileName(name) + ".png") + " (" + future.join() + ")");
        }
        return next(ctx, then);
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
        CopyLoad.stop();
        VideoPass.INSTANCE.simulateFault(VideoPass.Fault.NONE);
        VideoPass.INSTANCE.retryAll();
        VideoState.INSTANCE.overrideTestReceiver(false, null);
        VideoState.INSTANCE.overrideTestQuality(Float.NaN);
        PilotSession.INSTANCE.clearTestInput();
        ctx.state().put(FUTURE, ctx.onServer(VideoCheckSelfTests::release));
        return next(ctx, "cleanup");
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

    static void spawn(MinecraftServer server) {
        List<ServerPlayer> players = server.getPlayerList().getPlayers();
        if (players.isEmpty()) {
            throw new IllegalStateException("kein Spieler auf dem Server");
        }
        ServerPlayer player = players.getFirst();
        DronePiloting.pilotedBy(player.getUUID()).ifPresent(DronePiloting::release);
        ServerLevel level = player.level();
        QuadcopterEntity drone = DroneModule.DRONE.get().create(level, EntitySpawnReason.COMMAND);
        if (drone == null) {
            throw new IllegalStateException("EntityType.create lieferte null");
        }
        Vec3 feet = player.position();
        drone.snapTo(feet.x + 2.0, feet.y, feet.z - 5.0, 0.0F, 0.0F);
        drone.setAttitude(new Quaternionf());
        drone.setBuild(DroneBuild.FREESTYLE);
        drone.setCustomName(Component.literal("Bildprüfung"));
        if (!level.addFreshEntity(drone)) {
            throw new IllegalStateException("addFreshEntity fehlgeschlagen");
        }
        droneId = drone.getId();
        ItemStack goggles = ContentIds.item(ContentIds.GOGGLES_CREATIVE).map(ItemStack::new).orElse(ItemStack.EMPTY);
        if (goggles.isEmpty()) {
            throw new IllegalStateException("Kreativ-Brille nicht registriert");
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
        previousHead = null;
        previousHand = null;
        droneId = -1;
    }

    private static final class CopyLoad {
        private static final int NONE = 0;
        private static final int BLIT = 1;
        private static final int SHADER = 2;
        private static final long[] NANOS = new long[3];
        private static final long[] FRAMES = new long[3];
        private static int mode = NONE;
        private static long last;
        private static @Nullable TextureTarget scratch;

        private CopyLoad() {
        }

        static void frame(Minecraft minecraft, DeltaTracker deltaTracker) {
            long now = Util.getNanos();
            int current = mode;
            if (last != 0L && current != NONE) {
                NANOS[current] += now - last;
                FRAMES[current]++;
            }
            last = now;
            if (current == NONE) {
                return;
            }
            RenderTarget main = minecraft.gameRenderer.mainRenderTarget();
            CompiledRenderPipeline simple = RenderSystem.getCompiledPipelineNullable(VideoPass.SIMPLE_PIPELINE);
            GpuTextureView source = main.getColorTextureView();
            if (simple == null || source == null || main.width <= 0 || main.height <= 0) {
                return;
            }
            TextureTarget target = scratch;
            if (target == null || target.width != main.width || target.height != main.height) {
                if (target != null) {
                    target.destroyBuffers();
                }
                target = new TextureTarget("propwash check load", main.width, main.height, GpuFormat.RGBA8_UNORM, null);
                scratch = target;
            }
            GpuTextureView view = target.getColorTextureView();
            if (view == null) {
                return;
            }
            for (int i = 0; i < TIMING_COPIES; i++) {
                if (current == BLIT) {
                    target.copyColorFrom(main);
                } else {
                    VideoPass.INSTANCE.copy(simple, source, view);
                }
            }
        }

        static void start(boolean blit) {
            mode = blit ? BLIT : SHADER;
            last = 0L;
        }

        static void stop() {
            mode = NONE;
            last = 0L;
            TextureTarget target = scratch;
            if (target != null) {
                target.destroyBuffers();
                scratch = null;
            }
        }

        static void reset() {
            stop();
            Arrays.fill(NANOS, 0L);
            Arrays.fill(FRAMES, 0L);
        }

        static String summary() {
            double blit = FRAMES[BLIT] == 0L ? Double.NaN : NANOS[BLIT] / (double) FRAMES[BLIT] / 1.0E6;
            double shader = FRAMES[SHADER] == 0L ? Double.NaN : NANOS[SHADER] / (double) FRAMES[SHADER] / 1.0E6;
            return String.format(Locale.ROOT, "Last je Bild mit %d zusätzlichen Kopien des Hauptbilds %dx%d: Blit %.3f ms je Bild (%d Bilder), Shader-Kopie %.3f ms "
                            + "je Bild (%d Bilder), Unterschied je Kopie %+.4f ms", TIMING_COPIES, Minecraft.getInstance().gameRenderer.mainRenderTarget().width,
                    Minecraft.getInstance().gameRenderer.mainRenderTarget().height, blit, FRAMES[BLIT], shader, FRAMES[SHADER], (shader - blit) / TIMING_COPIES);
        }
    }

    record Luma(double mean, double brightest, int dark, int points) {
        @Override
        public String toString() {
            return String.format(Locale.ROOT, "Helligkeit Mittel %.1f, hellster Punkt %.0f, %d von %d Punkten schwarz", this.mean, this.brightest, this.dark,
                    this.points);
        }
    }
}
