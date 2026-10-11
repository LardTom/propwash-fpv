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
import com.mojang.renderpearl.api.textures.GpuTexture;
import com.mojang.renderpearl.api.textures.GpuTextureView;
import de.lardtom.propwash.Constants;
import de.lardtom.propwash.video.HeadlightCheck;
import de.lardtom.propwash.video.HeadlightMath;
import net.minecraft.client.renderer.MappableRingBuffer;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Util;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;
import org.joml.Vector4f;
import org.jspecify.annotations.Nullable;

import java.util.Arrays;
import java.util.Optional;

public final class HeadlightPass {
    public static final HeadlightPass INSTANCE = new HeadlightPass();

    public static final String UNIFORM_BLOCK = "PropwashHeadlight";
    public static final BindGroupLayout LAYOUT = BindGroupLayout.builder()
            .withUniform("InSampler", UniformType.COMBINED_IMAGE_SAMPLER)
            .withUniform("DepthSampler", UniformType.COMBINED_IMAGE_SAMPLER)
            .withUniform(UNIFORM_BLOCK, UniformType.UNIFORM_BUFFER)
            .build();
    public static final RenderPipeline PIPELINE = RenderPipeline.builder(RenderPipelines.POST_PROCESSING_SNIPPET)
            .withLocation(Constants.id("pipeline/fpv_headlight"))
            .withVertexShader(Identifier.withDefaultNamespace("core/screenquad"))
            .withFragmentShader(Constants.id("post/fpv_headlight"))
            .withBindGroupLayout(LAYOUT)
            .withColorTargetState(ColorTargetState.DEFAULT)
            .build();

    private static final int UBO_SIZE = new Std140SizeCalculator().putMat4f().putVec4().putVec4().putVec4().get();
    private static final long HOOK_TIMEOUT_NANOS = 1_000_000_000L;
    private static final float COS_INNER = (float) Math.cos(Math.toRadians(HeadlightMath.CONE_INNER_DEG));
    private static final float COS_OUTER = (float) Math.cos(Math.toRadians(HeadlightMath.CONE_OUTER_DEG));
    private static final Vector4f BLACK = new Vector4f(0.0F, 0.0F, 0.0F, 1.0F);
    private static final Matrix4f NAN_MATRIX = nanMatrix();

    private final Matrix4f projection = new Matrix4f();
    private final Matrix4f inverse = new Matrix4f();
    private final HeadlightCheck check = new HeadlightCheck();
    private boolean projectionValid;
    private @Nullable MappableRingBuffer ubo;
    private @Nullable TextureTarget scene;
    private VideoPass.@Nullable Failure failure;
    private @Nullable String failureDetail;
    private boolean hookSeen;
    private long lastHookNanos;
    private long framesDrawn;
    private HeadlightCheck.@Nullable Result lastCheck;
    private int checksPassed;
    private int checksFailed;
    private int failureLogs;
    private boolean checkUnavailable;
    private Fault fault = Fault.NONE;

    private HeadlightPass() {
    }

    private static Matrix4f nanMatrix() {
        float[] values = new float[16];
        Arrays.fill(values, Float.NaN);
        return new Matrix4f().set(values);
    }

    public void captureProjection(Matrix4fc matrix) {
        this.projection.set(matrix);
        this.inverse.set(matrix).invert();
        this.projectionValid = this.projection.isFinite() && this.inverse.isFinite();
    }

    public void apply(RenderTarget main) {
        this.hookSeen = true;
        this.lastHookNanos = Util.getNanos();
        Headlight light = Headlight.INSTANCE;
        if (!light.active() || this.failure != null || !this.projectionValid || VideoPass.INSTANCE.overlayFallback()) {
            if (!light.active() && this.holdsResources()) {
                this.release();
            }
            return;
        }
        try {
            this.draw(main, light);
        } catch (RuntimeException exception) {
            this.fail(VideoPass.Failure.ERROR, "Fehler im Render-Pass: " + describe(exception));
            this.release();
        }
    }

    public boolean fallbackNeeded() {
        if (this.failure != null || VideoPass.INSTANCE.overlayFallback()) {
            return true;
        }
        long now = Util.getNanos();
        VideoState state = VideoState.INSTANCE;
        boolean settled = state.active() && now - state.activeSinceNanos() > HOOK_TIMEOUT_NANOS;
        return settled && (!this.hookSeen || !this.projectionValid || now - this.lastHookNanos > HOOK_TIMEOUT_NANOS);
    }

    public VideoPass.@Nullable Failure failure() {
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

    public HeadlightCheck.@Nullable Result lastCheck() {
        return this.lastCheck;
    }

    public int checksPassed() {
        return this.checksPassed;
    }

    public int checksFailed() {
        return this.checksFailed;
    }

    public int failureLogs() {
        return this.failureLogs;
    }

    public void retry() {
        if (this.failure == VideoPass.Failure.CHECK) {
            return;
        }
        this.failure = null;
        this.failureDetail = null;
    }

    public void retryAll() {
        this.failure = null;
        this.failureDetail = null;
        this.check.restart();
    }

    public boolean holdsResources() {
        return this.ubo != null || this.scene != null;
    }

    public void release() {
        if (this.ubo != null) {
            this.ubo.close();
            this.ubo = null;
        }
        if (this.scene != null) {
            this.scene.destroyBuffers();
            this.scene = null;
        }
        this.check.restart();
    }

    void simulateFault(Fault value) {
        this.fault = value;
    }

    void restartChecks() {
        this.check.restart();
    }

    private void draw(RenderTarget main, Headlight light) {
        CompiledRenderPipeline compiled = RenderSystem.getCompiledPipelineNullable(PIPELINE);
        if (compiled == null) {
            this.fail(VideoPass.Failure.COMPILE, "Pipeline " + PIPELINE.getLocation() + " nicht verfügbar");
            return;
        }
        GpuTextureView output = main.getColorTextureView();
        GpuTextureView depth = main.getDepthTextureView();
        GpuTexture color = main.getColorTexture();
        if (output == null || depth == null || color == null || main.width <= 0 || main.height <= 0) {
            return;
        }
        this.ensureTargets(main.width, main.height);
        MappableRingBuffer buffer = this.ubo;
        TextureTarget copy = this.scene;
        if (buffer == null || copy == null) {
            return;
        }
        GpuTexture copyTexture = copy.getColorTexture();
        if (this.fault == Fault.BLACK_SCENE && copyTexture != null) {
            RenderSystem.getDevice().createCommandEncoder().clearColorTexture(copyTexture, BLACK);
        } else {
            copy.copyColorFrom(main);
        }
        GpuTextureView sceneView = copy.getColorTextureView();
        if (sceneView == null) {
            return;
        }
        boolean zeroToOne = RenderSystem.getDevice().getDeviceInfo().isZZeroToOne();
        GpuBuffer uniforms = buffer.currentBuffer();
        try (GpuBufferSlice.MappedView view = uniforms.map(false, true)) {
            Std140Builder.intoBuffer(view.data())
                    .putMat4f(this.fault == Fault.NAN_PROJECTION ? NAN_MATRIX : this.inverse)
                    .putVec4(light.red(), light.green(), light.blue(), light.gain())
                    .putVec4(COS_INNER, COS_OUTER, (float) HeadlightMath.SPILL, zeroToOne ? 1.0F : 0.0F)
                    .putVec4((float) HeadlightMath.RANGE_BLOCKS, (float) HeadlightMath.FALLOFF_BLOCKS, (float) HeadlightMath.LAMBERT_WEIGHT,
                            (float) HeadlightMath.LIFT_PER_GAIN);
        }
        this.check.frame();
        HeadlightProbe probe = this.beginCheck(color, main.width, main.height);
        boolean drawn = false;
        try (RenderPass pass = RenderSystem.getDevice().createCommandEncoder()
                .createRenderPass(() -> "propwash fpv headlight", output, Optional.empty())) {
            pass.setPipeline(compiled);
            RenderSystem.bindDefaultUniforms(pass);
            pass.setUniform("InSampler", sceneView, RenderSystem.getSamplerCache().getClampToEdge(FilterMode.NEAREST));
            pass.setUniform("DepthSampler", depth, RenderSystem.getSamplerCache().getClampToEdge(FilterMode.NEAREST));
            pass.setUniform(UNIFORM_BLOCK, uniforms);
            pass.draw(3, 1, 0, 0);
            drawn = true;
        } finally {
            if (probe != null) {
                this.endCheck(probe, drawn);
            }
        }
        buffer.rotate();
        this.framesDrawn++;
    }

    private @Nullable HeadlightProbe beginCheck(GpuTexture color, int width, int height) {
        if (this.checkUnavailable || !this.check.due()) {
            return null;
        }
        try {
            HeadlightProbe probe = HeadlightProbe.begin(color, width, height, this.check.issue(), this::onCheck);
            if (probe == null) {
                this.check.abandon();
            }
            return probe;
        } catch (RuntimeException exception) {
            this.checkUnavailable = true;
            this.check.abandon();
            Constants.LOG.warn("FPV-Scheinwerfer: Bildprüfung nicht möglich ({}), Shader-Weg bleibt an (Grafik: {})", exception, GpuInfo.describe());
            return null;
        }
    }

    private void endCheck(HeadlightProbe probe, boolean drawn) {
        if (!drawn) {
            probe.cancel();
            return;
        }
        try {
            probe.finish();
        } catch (RuntimeException exception) {
            probe.cancel();
            this.checkUnavailable = true;
            Constants.LOG.warn("FPV-Scheinwerfer: Bildprüfung nicht möglich ({}), Shader-Weg bleibt an (Grafik: {})", exception, GpuInfo.describe());
        }
    }

    private void onCheck(HeadlightCheck.@Nullable Result result) {
        if (result == null) {
            this.check.abandon();
            return;
        }
        this.lastCheck = result;
        this.check.record(result);
        if (result.verdict() == HeadlightCheck.Verdict.PASS) {
            this.checksPassed++;
        } else if (result.verdict() == HeadlightCheck.Verdict.FAIL) {
            this.checksFailed++;
            if (this.failure == null) {
                this.fail(VideoPass.Failure.CHECK, String.format("Bildprüfung: %d von %d Bildpunkten dunkler als ohne Licht", result.darkened(),
                        result.eligible()));
                this.release();
            }
        }
    }

    private void ensureTargets(int width, int height) {
        if (this.ubo == null) {
            this.ubo = new MappableRingBuffer(() -> "propwash fpv headlight uniforms", GpuBuffer.USAGE_UNIFORM | GpuBuffer.USAGE_MAP_WRITE, UBO_SIZE);
        }
        if (this.scene == null) {
            this.scene = new TextureTarget("propwash fpv headlight scene", width, height, GpuFormat.RGBA8_UNORM, null);
        } else if (this.scene.width != width || this.scene.height != height) {
            this.scene.resize(width, height);
        }
    }

    private void fail(VideoPass.Failure reason, String detail) {
        this.failure = reason;
        this.failureDetail = detail;
        this.failureLogs++;
        Constants.LOG.warn("FPV-Scheinwerfer: {}, Shader-Weg abgeschaltet, nutze die Aufhellung im Overlay (Grafik: {})", detail, GpuInfo.describe());
    }

    private static String describe(Throwable cause) {
        StackTraceElement[] trace = cause.getStackTrace();
        return trace.length == 0 ? cause.toString() : cause + " bei " + trace[0];
    }

    enum Fault {
        NONE,
        BLACK_SCENE,
        NAN_PROJECTION
    }
}
