package de.lardtom.propwash.video.client.record;

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
import com.mojang.renderpearl.api.textures.GpuTexture;
import com.mojang.renderpearl.api.textures.GpuTextureView;
import de.lardtom.propwash.Constants;
import de.lardtom.propwash.video.record.RecordingFormat;
import net.minecraft.client.renderer.MappableRingBuffer;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;
import org.joml.Matrix3f;
import org.jspecify.annotations.Nullable;

import java.util.Optional;

public final class ActionCamCapture {
    public static final String FRAME_BLOCK = "PropwashActionCam";
    public static final String PACK_BLOCK = "PropwashActionCamPack";
    public static final BindGroupLayout FRAME_LAYOUT = BindGroupLayout.builder()
            .withUniform("InSampler", UniformType.COMBINED_IMAGE_SAMPLER)
            .withUniform(FRAME_BLOCK, UniformType.UNIFORM_BUFFER)
            .build();
    public static final BindGroupLayout PACK_LAYOUT = BindGroupLayout.builder()
            .withUniform("FrameSampler", UniformType.COMBINED_IMAGE_SAMPLER)
            .withUniform(PACK_BLOCK, UniformType.UNIFORM_BUFFER)
            .build();
    public static final RenderPipeline FRAME_PIPELINE = RenderPipeline.builder(RenderPipelines.POST_PROCESSING_SNIPPET)
            .withLocation(Constants.id("pipeline/action_cam_frame"))
            .withVertexShader(Identifier.withDefaultNamespace("core/screenquad"))
            .withFragmentShader(Constants.id("post/action_cam_frame"))
            .withBindGroupLayout(FRAME_LAYOUT)
            .withColorTargetState(ColorTargetState.DEFAULT)
            .build();
    public static final RenderPipeline PACK_PIPELINE = RenderPipeline.builder(RenderPipelines.POST_PROCESSING_SNIPPET)
            .withLocation(Constants.id("pipeline/action_cam_yuv"))
            .withVertexShader(Identifier.withDefaultNamespace("core/screenquad"))
            .withFragmentShader(Constants.id("post/action_cam_yuv"))
            .withBindGroupLayout(PACK_LAYOUT)
            .withColorTargetState(ColorTargetState.DEFAULT)
            .build();

    private static final int READBACKS = 6;
    private static final int FRAME_UBO_SIZE = new Std140SizeCalculator()
            .putVec4().putVec4().putVec4().putVec4().putVec4().putVec4().get();
    private static final int PACK_UBO_SIZE = new Std140SizeCalculator().putVec4().get();

    private final Readback[] readbacks = new Readback[READBACKS];
    private @Nullable RecordingFormat format;
    private @Nullable TextureTarget frame;
    private @Nullable TextureTarget packed;
    private @Nullable MappableRingBuffer frameUbo;
    private @Nullable MappableRingBuffer packUbo;
    private @Nullable String failure;
    private int inFlight;
    private long issued;
    private long completed;
    private boolean warmed;

    public @Nullable String failure() {
        return this.failure;
    }

    public int inFlight() {
        return this.inFlight;
    }

    public long issued() {
        return this.issued;
    }

    public long completed() {
        return this.completed;
    }

    public boolean ringFull() {
        return this.readbacks[0] != null && this.freeReadback() == null;
    }

    public boolean holdsResources() {
        return this.frame != null || this.packed != null || this.frameUbo != null || this.packUbo != null || this.readbacks[0] != null;
    }

    public void retry() {
        this.failure = null;
    }

    public boolean capture(RenderTarget main, RecordingFormat target, Matrix3f warp, float[] tangents, float sharpness, float noise,
                           float seed, Sink sink) {
        if (this.failure != null) {
            return false;
        }
        CompiledRenderPipeline framePipeline = RenderSystem.getCompiledPipelineNullable(FRAME_PIPELINE);
        CompiledRenderPipeline packPipeline = RenderSystem.getCompiledPipelineNullable(PACK_PIPELINE);
        if (framePipeline == null || packPipeline == null) {
            this.fail("Pipeline nicht verfügbar", null);
            return false;
        }
        GpuTextureView source = main.getColorTextureView();
        if (source == null || main.width <= 0 || main.height <= 0) {
            return false;
        }
        try {
            this.ensure(target);
            Readback readback = this.freeReadback();
            if (readback == null) {
                return false;
            }
            TextureTarget frameTarget = this.frame;
            TextureTarget packedTarget = this.packed;
            MappableRingBuffer frameUniforms = this.frameUbo;
            MappableRingBuffer packUniforms = this.packUbo;
            if (frameTarget == null || packedTarget == null || frameUniforms == null || packUniforms == null) {
                return false;
            }
            GpuTextureView frameView = frameTarget.getColorTextureView();
            GpuTextureView packedView = packedTarget.getColorTextureView();
            GpuTexture packedTexture = packedTarget.getColorTexture();
            if (frameView == null || packedView == null || packedTexture == null) {
                return false;
            }
            GpuBuffer frameBuffer = frameUniforms.currentBuffer();
            try (GpuBufferSlice.MappedView view = frameBuffer.map(false, true)) {
                Std140Builder.intoBuffer(view.data())
                        .putVec4(warp.m00, warp.m01, warp.m02, 0.0F)
                        .putVec4(warp.m10, warp.m11, warp.m12, 0.0F)
                        .putVec4(warp.m20, warp.m21, warp.m22, 0.0F)
                        .putVec4(tangents[0], tangents[1], tangents[2], tangents[3])
                        .putVec4(target.width(), target.height(), main.width, main.height)
                        .putVec4(sharpness, noise, seed, 0.0F);
            }
            GpuBuffer packBuffer = packUniforms.currentBuffer();
            try (GpuBufferSlice.MappedView view = packBuffer.map(false, true)) {
                Std140Builder.intoBuffer(view.data())
                        .putVec4(target.width(), target.height(), target.width() / 4.0F, target.height() * 3.0F / 2.0F);
            }
            GpuSampler linear = RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR);
            GpuSampler nearest = RenderSystem.getSamplerCache().getClampToEdge(FilterMode.NEAREST);
            try (RenderPass pass = RenderSystem.getDevice().createCommandEncoder()
                    .createRenderPass(() -> "propwash action cam frame", frameView, Optional.empty())) {
                pass.setPipeline(framePipeline);
                RenderSystem.bindDefaultUniforms(pass);
                pass.setUniform("InSampler", source, linear);
                pass.setUniform(FRAME_BLOCK, frameBuffer);
                pass.draw(3, 1, 0, 0);
            }
            try (RenderPass pass = RenderSystem.getDevice().createCommandEncoder()
                    .createRenderPass(() -> "propwash action cam yuv", packedView, Optional.empty())) {
                pass.setPipeline(packPipeline);
                RenderSystem.bindDefaultUniforms(pass);
                pass.setUniform("FrameSampler", frameView, nearest);
                pass.setUniform(PACK_BLOCK, packBuffer);
                pass.draw(3, 1, 0, 0);
            }
            frameUniforms.rotate();
            packUniforms.rotate();
            readback.busy = true;
            this.inFlight++;
            this.issued++;
            int bytes = target.frameBytes();
            GpuBuffer destination = readback.buffer;
            RenderSystem.getDevice().createCommandEncoder().copyTextureToBuffer(packedTexture, destination, 0L, () -> {
                try {
                    if (!destination.isClosed()) {
                        try (GpuBufferSlice.MappedView view = destination.map(0L, bytes, true, false)) {
                            sink.accept(view);
                        }
                    }
                } catch (RuntimeException e) {
                    Constants.LOG.error("Action-Cam: Bild konnte nicht gelesen werden", e);
                } finally {
                    readback.busy = false;
                    this.inFlight--;
                    this.completed++;
                }
            }, 0);
            return true;
        } catch (RuntimeException e) {
            this.fail("Fehler im Render-Pass", e);
            this.release();
            return false;
        }
    }

    public boolean warmed() {
        return this.warmed;
    }

    public void prewarm(RenderTarget main) {
        if (this.warmed || this.failure != null) {
            return;
        }
        this.warmed = true;
        CompiledRenderPipeline framePipeline = RenderSystem.getCompiledPipelineNullable(FRAME_PIPELINE);
        CompiledRenderPipeline packPipeline = RenderSystem.getCompiledPipelineNullable(PACK_PIPELINE);
        GpuTextureView source = main.getColorTextureView();
        if (framePipeline == null || packPipeline == null || source == null) {
            return;
        }
        TextureTarget frameTarget = new TextureTarget("propwash action cam warmup", 16, 8, GpuFormat.RGBA8_UNORM, null);
        TextureTarget packedTarget = new TextureTarget("propwash action cam warmup yuv", 4, 12, GpuFormat.RGBA8_UNORM, null);
        GpuBuffer frameBuffer = RenderSystem.getDevice().createBuffer(() -> "propwash action cam warmup uniforms",
                GpuBuffer.USAGE_UNIFORM | GpuBuffer.USAGE_MAP_WRITE, FRAME_UBO_SIZE);
        GpuBuffer packBuffer = RenderSystem.getDevice().createBuffer(() -> "propwash action cam warmup pack uniforms",
                GpuBuffer.USAGE_UNIFORM | GpuBuffer.USAGE_MAP_WRITE, PACK_UBO_SIZE);
        try {
            GpuTextureView frameView = frameTarget.getColorTextureView();
            GpuTextureView packedView = packedTarget.getColorTextureView();
            if (frameView == null || packedView == null) {
                return;
            }
            try (GpuBufferSlice.MappedView view = frameBuffer.map(false, true)) {
                Std140Builder.intoBuffer(view.data())
                        .putVec4(1.0F, 0.0F, 0.0F, 0.0F)
                        .putVec4(0.0F, 1.0F, 0.0F, 0.0F)
                        .putVec4(0.0F, 0.0F, 1.0F, 0.0F)
                        .putVec4(1.0F, 1.0F, 1.0F, 1.0F)
                        .putVec4(16.0F, 8.0F, 32.0F, 16.0F)
                        .putVec4(0.0F, 0.0F, 1.0F, 0.0F);
            }
            try (GpuBufferSlice.MappedView view = packBuffer.map(false, true)) {
                Std140Builder.intoBuffer(view.data()).putVec4(16.0F, 8.0F, 4.0F, 12.0F);
            }
            GpuSampler linear = RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR);
            GpuSampler nearest = RenderSystem.getSamplerCache().getClampToEdge(FilterMode.NEAREST);
            try (RenderPass pass = RenderSystem.getDevice().createCommandEncoder()
                    .createRenderPass(() -> "propwash action cam warmup", frameView, Optional.empty())) {
                pass.setPipeline(framePipeline);
                RenderSystem.bindDefaultUniforms(pass);
                pass.setUniform("InSampler", source, linear);
                pass.setUniform(FRAME_BLOCK, frameBuffer);
                pass.draw(3, 1, 0, 0);
            }
            try (RenderPass pass = RenderSystem.getDevice().createCommandEncoder()
                    .createRenderPass(() -> "propwash action cam warmup yuv", packedView, Optional.empty())) {
                pass.setPipeline(packPipeline);
                RenderSystem.bindDefaultUniforms(pass);
                pass.setUniform("FrameSampler", frameView, nearest);
                pass.setUniform(PACK_BLOCK, packBuffer);
                pass.draw(3, 1, 0, 0);
            }
        } catch (RuntimeException e) {
            Constants.LOG.warn("Action-Cam: Vorwärmen der Aufnahme-Shader fehlgeschlagen: {}", e.getMessage());
        } finally {
            frameTarget.destroyBuffers();
            packedTarget.destroyBuffers();
            frameBuffer.close();
            packBuffer.close();
        }
    }

    public void release() {
        if (this.inFlight > 0) {
            return;
        }
        if (this.frame != null) {
            this.frame.destroyBuffers();
            this.frame = null;
        }
        if (this.packed != null) {
            this.packed.destroyBuffers();
            this.packed = null;
        }
        if (this.frameUbo != null) {
            this.frameUbo.close();
            this.frameUbo = null;
        }
        if (this.packUbo != null) {
            this.packUbo.close();
            this.packUbo = null;
        }
        for (int i = 0; i < READBACKS; i++) {
            if (this.readbacks[i] != null) {
                this.readbacks[i].buffer.close();
                this.readbacks[i] = null;
            }
        }
        this.format = null;
    }

    private void ensure(RecordingFormat target) {
        if (target.equals(this.format) && this.frame != null) {
            return;
        }
        if (this.inFlight > 0) {
            throw new IllegalStateException("format change with pending readbacks");
        }
        this.release();
        this.format = target;
        this.frame = new TextureTarget("propwash action cam frame", target.width(), target.height(), GpuFormat.RGBA8_UNORM, null);
        this.packed = new TextureTarget("propwash action cam yuv", target.width() / 4, target.height() * 3 / 2, GpuFormat.RGBA8_UNORM, null);
        this.frameUbo = new MappableRingBuffer(() -> "propwash action cam uniforms", GpuBuffer.USAGE_UNIFORM | GpuBuffer.USAGE_MAP_WRITE,
                FRAME_UBO_SIZE);
        this.packUbo = new MappableRingBuffer(() -> "propwash action cam pack uniforms", GpuBuffer.USAGE_UNIFORM | GpuBuffer.USAGE_MAP_WRITE,
                PACK_UBO_SIZE);
        for (int i = 0; i < READBACKS; i++) {
            GpuBuffer buffer = RenderSystem.getDevice().createBuffer(() -> "propwash action cam readback",
                    GpuBuffer.USAGE_MAP_READ | GpuBuffer.USAGE_COPY_DST, target.frameBytes());
            this.readbacks[i] = new Readback(buffer);
        }
    }

    private @Nullable Readback freeReadback() {
        for (Readback readback : this.readbacks) {
            if (readback != null && !readback.busy) {
                return readback;
            }
        }
        return null;
    }

    private void fail(String reason, @Nullable Throwable cause) {
        this.failure = cause == null ? reason : reason + ": " + cause.getClass().getSimpleName() + ": " + cause.getMessage();
        if (cause == null) {
            Constants.LOG.warn("Action-Cam: {}", reason);
        } else {
            Constants.LOG.error("Action-Cam: {}", reason, cause);
        }
    }

    @FunctionalInterface
    public interface Sink {
        void accept(GpuBufferSlice.MappedView view);
    }

    private static final class Readback {
        final GpuBuffer buffer;
        boolean busy;

        Readback(GpuBuffer buffer) {
            this.buffer = buffer;
        }
    }
}
