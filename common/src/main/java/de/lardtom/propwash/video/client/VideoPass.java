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
import de.lardtom.propwash.video.ImageArea;
import net.minecraft.client.renderer.MappableRingBuffer;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Util;
import org.joml.Vector4f;
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

    private static final int UBO_SIZE = new Std140SizeCalculator()
            .putVec4().putVec4().putVec4().putVec4().putVec4().putVec4().putVec4().putVec4().putVec4().get();
    private static final long HOOK_TIMEOUT_NANOS = 1_000_000_000L;

    private @Nullable MappableRingBuffer ubo;
    private @Nullable TextureTarget frame;
    private @Nullable TextureTarget history;
    private boolean frameStale = true;
    private boolean historyValid;
    private @Nullable Failure failure;
    private @Nullable String failureDetail;
    private boolean hookSeen;
    private long lastHookNanos;
    private long framesDrawn;
    private boolean hookWarningLogged;

    private VideoPass() {
    }

    public void apply(RenderTarget main) {
        long now = Util.getNanos();
        this.hookSeen = true;
        this.lastHookNanos = now;
        VideoState state = VideoState.INSTANCE;
        EffectParams params = state.params();
        boolean usable = state.active() && this.failure == null && this.overlayReason(now) == null;
        if (!usable) {
            if (this.holdsResources()) {
                this.release();
            }
            return;
        }
        if (!params.needsPass()) {
            if (params.frameAdvanced) {
                this.frameStale = true;
            }
            return;
        }
        try {
            this.draw(main, params);
        } catch (RuntimeException exception) {
            this.fail(Failure.ERROR, exception);
            this.release();
        }
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
        this.hookWarningLogged = false;
    }

    void retryAfterCompileFailure() {
        if (this.failure == Failure.COMPILE) {
            this.retry();
        }
    }

    public void release() {
        if (this.ubo != null) {
            this.ubo.close();
            this.ubo = null;
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
        return this.ubo != null || this.frame != null || this.history != null;
    }

    @Nullable Failure overlayReason(long now) {
        if (VideoSettings.get().renderer == VideoConfig.EffectRenderer.OVERLAY) {
            return Failure.CONFIG;
        }
        if (IrisCompat.shaderPackActive(now)) {
            return Failure.SHADER_PACK;
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

    private void draw(RenderTarget main, EffectParams params) {
        CompiledRenderPipeline compiled = RenderSystem.getCompiledPipelineNullable(PIPELINE);
        if (compiled == null) {
            this.fail(Failure.COMPILE, null);
            return;
        }
        GpuTextureView output = main.getColorTextureView();
        if (output == null || main.width <= 0 || main.height <= 0) {
            return;
        }
        boolean fresh = this.ensureTargets(main.width, main.height);
        MappableRingBuffer buffer = this.ubo;
        if (this.frame == null || this.history == null || buffer == null) {
            return;
        }
        if (fresh || this.frameStale) {
            this.frame.copyColorFrom(main);
            this.historyValid = false;
            this.frameStale = false;
        } else if (params.frameAdvanced) {
            TextureTarget shown = this.frame;
            this.frame = this.history;
            this.history = shown;
            this.frame.copyColorFrom(main);
            this.historyValid = true;
        }
        TextureTarget current = this.frame;
        TextureTarget previous = this.historyValid ? this.history : current;
        GpuTextureView currentView = current.getColorTextureView();
        GpuTextureView previousView = previous.getColorTextureView();
        if (currentView == null || previousView == null) {
            return;
        }
        ImageArea area = FpvImage.area(main.width, main.height);
        GpuBuffer uniforms = buffer.currentBuffer();
        try (GpuBufferSlice.MappedView view = uniforms.map(false, true)) {
            Std140Builder.intoBuffer(view.data())
                    .putVec4(params.timeSeconds, params.seed, main.width, main.height)
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
        GpuSampler sampler = RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR);
        try (RenderPass pass = RenderSystem.getDevice().createCommandEncoder()
                .createRenderPass(() -> "propwash video feed", output, Optional.empty())) {
            pass.setPipeline(compiled);
            RenderSystem.bindDefaultUniforms(pass);
            pass.setUniform("InSampler", currentView, sampler);
            pass.setUniform("HistorySampler", previousView, sampler);
            pass.setUniform(UNIFORM_BLOCK, uniforms);
            pass.draw(3, 1, 0, 0);
        }
        buffer.rotate();
        this.framesDrawn++;
    }

    private boolean ensureTargets(int width, int height) {
        if (this.ubo == null) {
            this.ubo = new MappableRingBuffer(() -> "propwash video feed uniforms", GpuBuffer.USAGE_UNIFORM | GpuBuffer.USAGE_MAP_WRITE, UBO_SIZE);
        }
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
            RenderSystem.getDevice().createCommandEncoder().clearColorTexture(target.getColorTexture(), new Vector4f(0.0F, 0.0F, 0.0F, 1.0F));
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
