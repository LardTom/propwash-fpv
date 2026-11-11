package de.lardtom.propwash.video.client;

import com.mojang.blaze3d.buffers.Std140Builder;
import com.mojang.blaze3d.buffers.Std140SizeCalculator;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.renderpearl.api.GpuFormat;
import com.mojang.renderpearl.api.buffers.GpuBuffer;
import com.mojang.renderpearl.api.buffers.GpuBufferSlice;
import com.mojang.renderpearl.api.commands.RenderPass;
import com.mojang.renderpearl.api.pipeline.BindGroupLayout;
import com.mojang.renderpearl.api.pipeline.ColorTargetState;
import com.mojang.renderpearl.api.pipeline.CompiledRenderPipeline;
import com.mojang.renderpearl.api.pipeline.RenderPipeline;
import com.mojang.renderpearl.api.pipeline.UniformType;
import com.mojang.renderpearl.api.textures.FilterMode;
import com.mojang.renderpearl.api.textures.GpuSampler;
import com.mojang.renderpearl.api.textures.GpuTextureView;
import de.lardtom.propwash.Constants;
import de.lardtom.propwash.compat.iris.client.IrisCompat;
import de.lardtom.propwash.video.EffectParams;
import de.lardtom.propwash.video.FisheyeLens;
import de.lardtom.propwash.video.ImageArea;
import de.lardtom.propwash.video.VideoCheck;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MappableRingBuffer;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Util;
import org.joml.Vector4f;
import org.joml.Vector4fc;
import org.jspecify.annotations.Nullable;

import java.util.Optional;

public final class VideoPass {
    public static final VideoPass INSTANCE = new VideoPass();

    public static final String UNIFORM_BLOCK = "PropwashVideo";
    public static final BindGroupLayout LAYOUT = BindGroupLayout.builder()
            .withUniform("InSampler", UniformType.COMBINED_IMAGE_SAMPLER)
            .withUniform("HistorySampler", UniformType.COMBINED_IMAGE_SAMPLER)
            .withUniform(UNIFORM_BLOCK, UniformType.UNIFORM_BUFFER)
            .build();
    public static final RenderPipeline PIPELINE = RenderPipeline.builder(RenderPipelines.POST_PROCESSING_SNIPPET)
            .withLocation(Constants.id("pipeline/video_feed"))
            .withVertexShader(Identifier.withDefaultNamespace("core/screenquad"))
            .withFragmentShader(Constants.id("post/video_feed"))
            .withBindGroupLayout(LAYOUT)
            .withColorTargetState(ColorTargetState.DEFAULT)
            .build();

    public static final BindGroupLayout SIMPLE_LAYOUT = BindGroupLayout.builder()
            .withUniform("InSampler", UniformType.COMBINED_IMAGE_SAMPLER)
            .build();
    public static final RenderPipeline SIMPLE_PIPELINE = RenderPipeline.builder(RenderPipelines.POST_PROCESSING_SNIPPET)
            .withLocation(Constants.id("pipeline/video_feed_simple"))
            .withVertexShader(Identifier.withDefaultNamespace("core/screenquad"))
            .withFragmentShader(Constants.id("post/video_feed_simple"))
            .withBindGroupLayout(SIMPLE_LAYOUT)
            .withColorTargetState(ColorTargetState.DEFAULT)
            .build();

    public static final String LENS_BLOCK = "PropwashLens";
    public static final BindGroupLayout LENS_LAYOUT = BindGroupLayout.builder()
            .withUniform("InSampler", UniformType.COMBINED_IMAGE_SAMPLER)
            .withUniform(LENS_BLOCK, UniformType.UNIFORM_BUFFER)
            .build();
    public static final RenderPipeline LENS_PIPELINE = RenderPipeline.builder(RenderPipelines.POST_PROCESSING_SNIPPET)
            .withLocation(Constants.id("pipeline/fpv_lens"))
            .withVertexShader(Identifier.withDefaultNamespace("core/screenquad"))
            .withFragmentShader(Constants.id("post/fpv_lens"))
            .withBindGroupLayout(LENS_LAYOUT)
            .withColorTargetState(ColorTargetState.DEFAULT)
            .build();

    static final int LENS_UBO_SIZE = new Std140SizeCalculator().putVec4().putVec4().putVec4().putVec4().get();
    static final int UBO_SIZE = new Std140SizeCalculator()
            .putVec4().putVec4().putVec4().putVec4().putVec4().putVec4().putVec4().putVec4().putVec4().get();
    private static final long HOOK_TIMEOUT_NANOS = 1_000_000_000L;
    private static final Vector4fc BLACK = new Vector4f(0.0F, 0.0F, 0.0F, 1.0F);

    private @Nullable MappableRingBuffer ubo;
    private @Nullable MappableRingBuffer lensUbo;
    private @Nullable TextureTarget frame;
    private @Nullable TextureTarget history;
    private boolean frameStale = true;
    private boolean historyValid;
    private @Nullable Failure failure;
    private @Nullable String failureDetail;
    private boolean hookSeen;
    private long lastHookNanos;
    private long framesDrawn;
    private long lensFramesDrawn;
    private boolean hookWarningLogged;
    private @Nullable Failure lensFailure;
    private @Nullable String lensFailureDetail;
    private final VideoSelfCheck check = new VideoSelfCheck();
    private Fault fault = Fault.NONE;
    private long copies;
    private long simpleFramesDrawn;
    private VideoCheck.Level shownLevel = VideoCheck.Level.FULL;

    private VideoPass() {
    }

    public void apply(RenderTarget main) {
        long now = Util.getNanos();
        this.hookSeen = true;
        this.lastHookNanos = now;
        VideoState state = VideoState.INSTANCE;
        EffectParams params = state.params();
        boolean shaderPack = IrisCompat.shaderPackActive(now);
        if (state.active() && !shaderPack) {
            this.check.maybeRun(this, now);
        }
        VideoCheck.Level level = this.level();
        if (level != this.shownLevel) {
            this.shownLevel = level;
            this.release();
        }
        FisheyeLens lens = this.lensFailure == null && level == VideoCheck.Level.FULL ? FpvLens.INSTANCE.rendered() : null;
        Failure reason = this.overlayReason(now);
        boolean effects = state.active() && this.failure == null && reason == null;
        boolean hold = !effects && !shaderPack && state.active() && this.failure == null && level == VideoCheck.Level.SIMPLE && !params.continuous
                && (reason == Failure.CONFIG || reason == Failure.CHECK);
        if (!effects && lens == null && !hold) {
            if (this.holdsResources()) {
                this.release();
            }
            return;
        }
        if (hold) {
            this.hold(main, params);
            return;
        }
        boolean pass = effects && params.needsPass();
        if (!pass) {
            if (params.frameAdvanced) {
                this.frameStale = true;
            }
            if (lens == null) {
                return;
            }
        }
        try {
            this.draw(main, params, pass, lens);
        } catch (RuntimeException exception) {
            if (pass) {
                this.fail(Failure.ERROR, exception);
            }
            if (lens != null) {
                this.failLens(Failure.ERROR, exception);
            }
            this.release();
        }
    }

    public boolean lensReady(long now) {
        return this.lensFailure == null && this.hookSeen && now - this.lastHookNanos <= HOOK_TIMEOUT_NANOS && this.level() == VideoCheck.Level.FULL;
    }

    public VideoCheck.Level level() {
        return VideoSettings.get().renderer.level(this.check.outcome());
    }

    public VideoCheck.Outcome checkOutcome() {
        return this.check.outcome();
    }

    public @Nullable String checkDetail() {
        return this.check.detail();
    }

    public int checkRuns() {
        return this.check.runs();
    }

    public boolean checkRunning() {
        return this.check.running();
    }

    public boolean checkSimplified() {
        return this.check.outcome().simplifies() && this.level() != VideoCheck.Level.FULL;
    }

    public long copies() {
        return this.copies;
    }

    public long simpleFramesDrawn() {
        return this.simpleFramesDrawn;
    }

    public @Nullable Failure lensFailure() {
        return this.lensFailure;
    }

    public @Nullable String lensFailureDetail() {
        return this.lensFailureDetail;
    }

    public long lensFramesDrawn() {
        return this.lensFramesDrawn;
    }

    public Status status() {
        if (this.failure != null) {
            return Status.FAILED;
        }
        if (VideoSettings.get().renderer == VideoConfig.EffectRenderer.OVERLAY) {
            return Status.DISABLED;
        }
        if (IrisCompat.shaderPackActive(Util.getNanos())) {
            return Status.SHADER_PACK;
        }
        if (this.framesDrawn > 0L) {
            return Status.ACTIVE;
        }
        return Status.READY;
    }

    public boolean overlayFallback() {
        return this.overlayReason(Util.getNanos()) != null || this.failure != null;
    }

    public @Nullable Failure failure() {
        return this.failure;
    }

    public @Nullable String failureDetail() {
        return this.failureDetail;
    }

    public long framesDrawn() {
        return this.framesDrawn;
    }

    public boolean hookSeen() {
        return this.hookSeen;
    }

    public void retry() {
        this.failure = null;
        this.failureDetail = null;
        this.lensFailure = null;
        this.lensFailureDetail = null;
        this.hookWarningLogged = false;
    }

    public void retryAll() {
        this.retry();
        this.check.restart();
    }

    void recheck() {
        this.check.restart();
    }

    void simulateFault(Fault value) {
        this.fault = value;
    }

    void retryAfterCompileFailure() {
        if (this.failure == Failure.COMPILE || this.lensFailure == Failure.COMPILE) {
            this.retry();
        }
    }

    public void release() {
        if (this.ubo != null) {
            this.ubo.close();
            this.ubo = null;
        }
        if (this.lensUbo != null) {
            this.lensUbo.close();
            this.lensUbo = null;
        }
        if (this.frame != null) {
            this.frame.destroyBuffers();
            this.frame = null;
        }
        if (this.history != null) {
            this.history.destroyBuffers();
            this.history = null;
        }
        this.frameStale = true;
        this.historyValid = false;
    }

    public boolean holdsResources() {
        return this.ubo != null || this.lensUbo != null || this.frame != null || this.history != null;
    }

    @Nullable Failure overlayReason(long now) {
        VideoConfig.EffectRenderer renderer = VideoSettings.get().renderer;
        if (renderer == VideoConfig.EffectRenderer.OVERLAY) {
            return Failure.CONFIG;
        }
        if (IrisCompat.shaderPackActive(now)) {
            return Failure.SHADER_PACK;
        }
        if (renderer.level(this.check.outcome()) != VideoCheck.Level.FULL) {
            return Failure.CHECK;
        }
        VideoState state = VideoState.INSTANCE;
        if (state.active() && now - state.activeSinceNanos() > HOOK_TIMEOUT_NANOS
                && (!this.hookSeen || now - this.lastHookNanos > HOOK_TIMEOUT_NANOS)) {
            if (!this.hookWarningLogged) {
                this.hookWarningLogged = true;
                Constants.LOG.warn("Video-Effekt: Render-Hook läuft nicht (anderer Renderer?), nutze das Ersatz-Overlay");
            }
            return Failure.HOOK;
        }
        return null;
    }

    private void draw(RenderTarget main, EffectParams params, boolean effectPass, @Nullable FisheyeLens wanted) {
        GpuTextureView output = main.getColorTextureView();
        if (output == null || main.width <= 0 || main.height <= 0) {
            return;
        }
        boolean pass = effectPass;
        CompiledRenderPipeline feed = null;
        if (pass) {
            feed = RenderSystem.getCompiledPipelineNullable(PIPELINE);
            if (feed == null) {
                this.fail(Failure.COMPILE, null);
                pass = false;
            }
        }
        CompiledRenderPipeline simple = RenderSystem.getCompiledPipelineNullable(SIMPLE_PIPELINE);
        FisheyeLens lens = wanted;
        CompiledRenderPipeline dewarp = null;
        if (lens != null) {
            dewarp = RenderSystem.getCompiledPipelineNullable(LENS_PIPELINE);
            if (dewarp == null) {
                this.failLens(Failure.COMPILE, null);
                lens = null;
            }
        }
        if (!pass && lens == null) {
            return;
        }
        boolean fresh = this.ensureTargets(main.width, main.height);
        if (this.frame == null || this.history == null) {
            return;
        }
        if (!pass) {
            this.copy(simple, main, this.frame);
            this.frameStale = true;
            this.historyValid = false;
            GpuTextureView raw = this.frame.getColorTextureView();
            if (raw != null) {
                this.dewarp(dewarp, raw, output, lens, main.width, main.height);
            }
            return;
        }
        try {
            if (fresh || this.frameStale) {
                this.capture(main, this.frame, simple, dewarp, lens);
                this.historyValid = false;
                this.frameStale = false;
            } else if (params.frameAdvanced) {
                TextureTarget shown = this.frame;
                this.frame = this.history;
                this.history = shown;
                this.capture(main, this.frame, simple, dewarp, lens);
                this.historyValid = true;
            }
            this.feed(main, output, feed, params);
        } catch (RuntimeException exception) {
            this.fail(Failure.ERROR, exception);
            this.release();
        }
    }

    private void capture(RenderTarget main, TextureTarget into, @Nullable CompiledRenderPipeline simple, @Nullable CompiledRenderPipeline dewarp,
                         @Nullable FisheyeLens lens) {
        GpuTextureView source = main.getColorTextureView();
        GpuTextureView target = into.getColorTextureView();
        if (lens == null || dewarp == null || source == null || target == null) {
            this.copy(simple, main, into);
            return;
        }
        this.dewarp(dewarp, source, target, lens, main.width, main.height);
    }

    private void hold(RenderTarget main, EffectParams params) {
        if (params.frameAdvanced) {
            this.frameStale = true;
            return;
        }
        GpuTextureView output = main.getColorTextureView();
        CompiledRenderPipeline simple = RenderSystem.getCompiledPipelineNullable(SIMPLE_PIPELINE);
        if (output == null || simple == null || main.width <= 0 || main.height <= 0) {
            return;
        }
        boolean fresh = this.ensureTargets(main.width, main.height);
        TextureTarget held = this.frame;
        if (held == null) {
            return;
        }
        try {
            if (fresh || this.frameStale) {
                this.copy(simple, main, held);
                this.frameStale = false;
                this.historyValid = false;
            }
            GpuTextureView view = held.getColorTextureView();
            if (view != null) {
                simple(simple, view, output, "propwash video feed simple");
                this.simpleFramesDrawn++;
            }
        } catch (RuntimeException exception) {
            this.fail(Failure.ERROR, exception);
            this.release();
        }
    }

    private void copy(@Nullable CompiledRenderPipeline simple, RenderTarget source, TextureTarget into) {
        GpuTextureView from = source.getColorTextureView();
        GpuTextureView to = into.getColorTextureView();
        if (simple == null || from == null || to == null) {
            if (this.fault != Fault.COPY_NOOP) {
                into.copyColorFrom(source);
            }
            return;
        }
        this.copy(simple, from, to);
    }

    void copy(CompiledRenderPipeline simple, GpuTextureView from, GpuTextureView to) {
        if (this.fault == Fault.COPY_NOOP) {
            return;
        }
        simple(simple, from, to, "propwash video copy");
        this.copies++;
    }

    public static void simple(CompiledRenderPipeline pipeline, GpuTextureView source, GpuTextureView target, String label) {
        GpuSampler sampler = RenderSystem.getSamplerCache().getClampToEdge(FilterMode.NEAREST);
        try (RenderPass pass = RenderSystem.getDevice().createCommandEncoder().createRenderPass(() -> label, target, Optional.empty())) {
            pass.setPipeline(pipeline);
            RenderSystem.bindDefaultUniforms(pass);
            pass.setUniform("InSampler", source, sampler);
            pass.draw(3, 1, 0, 0);
        }
    }

    private void dewarp(@Nullable CompiledRenderPipeline pipeline, GpuTextureView source, GpuTextureView target, @Nullable FisheyeLens lens,
                        int width, int height) {
        if (pipeline == null || lens == null) {
            return;
        }
        try {
            if (this.lensUbo == null) {
                this.lensUbo = new MappableRingBuffer(() -> "propwash fpv lens uniforms", GpuBuffer.USAGE_UNIFORM | GpuBuffer.USAGE_MAP_WRITE,
                        LENS_UBO_SIZE);
            }
            MappableRingBuffer buffer = this.lensUbo;
            float fov = Minecraft.getInstance().gameRenderer.mainCamera().getFov();
            double tanY = fov > 1.0F && fov < 179.0F ? Math.tan(Math.toRadians(fov) * 0.5) : lens.tanY();
            double tanX = tanY * width / height;
            ImageArea area = FpvImage.area(width, height);
            GpuBuffer uniforms = buffer.currentBuffer();
            try (GpuBufferSlice.MappedView view = uniforms.map(false, true)) {
                lensUniforms(Std140Builder.intoBuffer(view.data()), lens, width, height, area, tanX, tanY);
            }
            drawLens(pipeline, source, target, uniforms);
            buffer.rotate();
            this.lensFramesDrawn++;
        } catch (RuntimeException exception) {
            this.failLens(Failure.ERROR, exception);
        }
    }

    static Std140Builder lensUniforms(Std140Builder builder, FisheyeLens lens, int width, int height, ImageArea area, double tanX, double tanY) {
        return builder.putVec4(width, height, 1.0F / width, 1.0F / height)
                .putVec4((float) area.x() / area.screenWidth(), (float) area.y() / area.screenHeight(), (float) area.widthShare(),
                        (float) area.heightShare())
                .putVec4((float) lens.focal(), (float) lens.circleRadius(), (float) tanX, (float) tanY)
                .putVec4((float) FisheyeLens.EDGE_PX, (float) lens.bend(), 0.0F, 0.0F);
    }

    static void drawLens(CompiledRenderPipeline pipeline, GpuTextureView source, GpuTextureView target, GpuBuffer uniforms) {
        GpuSampler sampler = RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR);
        try (RenderPass pass = RenderSystem.getDevice().createCommandEncoder()
                .createRenderPass(() -> "propwash fpv lens", target, Optional.empty())) {
            pass.setPipeline(pipeline);
            RenderSystem.bindDefaultUniforms(pass);
            pass.setUniform("InSampler", source, sampler);
            pass.setUniform(LENS_BLOCK, uniforms);
            pass.draw(3, 1, 0, 0);
        }
    }

    private void feed(RenderTarget main, GpuTextureView output, @Nullable CompiledRenderPipeline compiled, EffectParams params) {
        if (compiled == null) {
            return;
        }
        if (this.ubo == null) {
            this.ubo = new MappableRingBuffer(() -> "propwash video feed uniforms", GpuBuffer.USAGE_UNIFORM | GpuBuffer.USAGE_MAP_WRITE, UBO_SIZE);
        }
        MappableRingBuffer buffer = this.ubo;
        TextureTarget current = this.frame;
        TextureTarget previous = this.historyValid ? this.history : current;
        if (current == null || previous == null) {
            return;
        }
        GpuTextureView currentView = current.getColorTextureView();
        GpuTextureView previousView = previous.getColorTextureView();
        if (currentView == null || previousView == null) {
            return;
        }
        ImageArea area = FpvImage.area(main.width, main.height);
        GpuBuffer uniforms = buffer.currentBuffer();
        try (GpuBufferSlice.MappedView view = uniforms.map(false, true)) {
            feedUniforms(Std140Builder.intoBuffer(view.data()), params, main.width, main.height, area);
        }
        this.drawFeed(compiled, currentView, previousView, uniforms, output, this.fault != Fault.UNBOUND_HISTORY);
        buffer.rotate();
        this.framesDrawn++;
    }

    static Std140Builder feedUniforms(Std140Builder builder, EffectParams params, int width, int height, ImageArea area) {
        return builder.putVec4(params.timeSeconds, params.seed, width, height)
                .putVec4(params.mode, params.brightness, params.noSignal, params.noSignalBlack)
                .putVec4(params.bleedPx, params.scanlines, params.vignette, params.noiseContrast)
                .putVec4(params.glitch, params.glitchSeed, params.sparkle, params.lineJitter)
                .putVec4(params.desaturation, params.chromaBands, params.bandPhase, params.chromaNoise)
                .putVec4(params.noise, params.syncShift, params.shiftTop, params.shiftBottom)
                .putVec4(params.roll, params.blockSeed, params.resolution, 0.0F)
                .putVec4(params.blockiness, params.blockPx, params.blurPx, params.posterize)
                .putVec4((float) area.x() / area.screenWidth(), (float) area.y() / area.screenHeight(), (float) area.widthShare(),
                        (float) area.heightShare());
    }

    void drawFeed(CompiledRenderPipeline compiled, GpuTextureView current, GpuTextureView previous, GpuBuffer uniforms, GpuTextureView output,
                  boolean bindHistory) {
        if (this.fault == Fault.FEED_BLACK) {
            RenderSystem.getDevice().createCommandEncoder().createRenderPass(() -> "propwash video feed black", output, Optional.of(BLACK)).close();
            return;
        }
        GpuSampler sampler = RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR);
        try (RenderPass pass = RenderSystem.getDevice().createCommandEncoder()
                .createRenderPass(() -> "propwash video feed", output, Optional.empty())) {
            pass.setPipeline(compiled);
            RenderSystem.bindDefaultUniforms(pass);
            pass.setUniform("InSampler", current, sampler);
            if (bindHistory) {
                pass.setUniform("HistorySampler", previous, sampler);
            }
            pass.setUniform(UNIFORM_BLOCK, uniforms);
            pass.draw(3, 1, 0, 0);
        }
    }

    private boolean ensureTargets(int width, int height) {
        if (this.frame == null || this.history == null) {
            if (this.frame != null) {
                this.frame.destroyBuffers();
            }
            if (this.history != null) {
                this.history.destroyBuffers();
            }
            this.frame = new TextureTarget("propwash video frame a", width, height, GpuFormat.RGBA8_UNORM, null);
            this.history = new TextureTarget("propwash video frame b", width, height, GpuFormat.RGBA8_UNORM, null);
            this.clear(this.frame);
            this.clear(this.history);
            return true;
        }
        if (this.frame.width != width || this.frame.height != height) {
            this.frame.resize(width, height);
            this.history.resize(width, height);
            this.clear(this.frame);
            this.clear(this.history);
            return true;
        }
        return false;
    }

    private void clear(TextureTarget target) {
        if (target.getColorTexture() != null) {
            RenderSystem.getDevice().createCommandEncoder().clearColorTexture(target.getColorTexture(), BLACK);
        }
    }

    private void fail(Failure reason, @Nullable Throwable cause) {
        this.failure = reason;
        this.failureDetail = cause == null ? null : cause.getClass().getSimpleName() + ": " + cause.getMessage();
        if (cause == null) {
            Constants.LOG.warn("Video-Effekt: Pipeline {} nicht verfügbar, nutze das Ersatz-Overlay", PIPELINE.getLocation());
        } else {
            Constants.LOG.error("Video-Effekt: Fehler im Render-Pass, nutze das Ersatz-Overlay", cause);
        }
    }

    private void failLens(Failure reason, @Nullable Throwable cause) {
        this.lensFailure = reason;
        this.lensFailureDetail = cause == null ? null : cause.getClass().getSimpleName() + ": " + cause.getMessage();
        if (cause == null) {
            Constants.LOG.warn("Fisheye-Objektiv: Pipeline {} nicht verfügbar, zeichne das FPV-Bild flach", LENS_PIPELINE.getLocation());
        } else {
            Constants.LOG.error("Fisheye-Objektiv: Fehler im Render-Pass, zeichne das FPV-Bild flach", cause);
        }
    }

    enum Fault {
        NONE,
        COPY_NOOP,
        FEED_BLACK,
        UNBOUND_HISTORY
    }

    public enum Status {
        READY,
        ACTIVE,
        DISABLED,
        SHADER_PACK,
        FAILED
    }

    public enum Failure {
        CONFIG("config"),
        SHADER_PACK("shaderpack"),
        HOOK("hook"),
        COMPILE("compile"),
        ERROR("error"),
        CHECK("check");

        private final String key;

        Failure(String key) {
            this.key = key;
        }

        public String translationKey() {
            return "propwash.video.fallback." + this.key;
        }
    }
}
