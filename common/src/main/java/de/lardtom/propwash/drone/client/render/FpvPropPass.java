package de.lardtom.propwash.drone.client.render;

import com.mojang.blaze3d.ProjectionType;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.renderpearl.api.buffers.GpuBufferSlice;
import com.mojang.renderpearl.api.commands.RenderPass;
import com.mojang.renderpearl.api.textures.GpuTextureView;
import de.lardtom.propwash.Constants;
import de.lardtom.propwash.compat.iris.client.IrisCompat;
import net.minecraft.client.renderer.ProjectionMatrixBuffer;
import net.minecraft.client.renderer.SubmitNodeStorage;
import net.minecraft.client.renderer.feature.FeatureRenderDispatcher;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.util.Util;
import org.joml.Matrix4f;
import org.joml.Matrix4fStack;
import org.joml.Matrix4fc;
import org.jspecify.annotations.Nullable;

import java.util.Optional;
import java.util.OptionalDouble;

public final class FpvPropPass {
    private static final Matrix4f LEVEL_PROJECTION = new Matrix4f();
    private static boolean projectionCaptured;
    private static @Nullable DroneRenderState pending;
    private static @Nullable ProjectionMatrixBuffer projection;
    private static @Nullable SubmitNodeStorage storage;
    private static boolean disabled;
    private static boolean forceWorld;
    private static boolean warned;
    private static int deferred;
    private static int passes;

    private FpvPropPass() {
    }

    public static boolean take(DroneRenderState state) {
        if (disabled || forceWorld || state.fpvGeometry == null || IrisCompat.renderingShadowPass() || IrisCompat.shaderPackActive(Util.getNanos())) {
            return false;
        }
        pending = state;
        deferred++;
        return true;
    }

    public static void captureProjection(Matrix4fc matrix) {
        LEVEL_PROJECTION.set(matrix);
        projectionCaptured = LEVEL_PROJECTION.isFinite();
    }

    public static void render(CameraRenderState camera, FeatureRenderDispatcher dispatcher, RenderTarget target, GpuTextureView depth) {
        DroneRenderState state = pending;
        pending = null;
        boolean captured = projectionCaptured;
        projectionCaptured = false;
        if (state == null || camera == null || camera.isPanoramicMode) {
            return;
        }
        GpuBufferSlice previous = RenderSystem.getProjectionMatrixBuffer();
        ProjectionType previousType = RenderSystem.getProjectionType();
        Matrix4fStack modelView = RenderSystem.getModelViewStack();
        modelView.pushMatrix().mul(camera.viewRotationMatrix);
        try {
            if (projection == null) {
                projection = new ProjectionMatrixBuffer("propwash fpv props");
            }
            if (storage == null) {
                storage = new SubmitNodeStorage();
            }
            RenderSystem.setProjectionMatrix(projection.getBuffer(new Matrix4f(captured ? LEVEL_PROJECTION : camera.projectionMatrix)),
                    ProjectionType.PERSPECTIVE);
            FpvProps.submitAtCamera(state, new PoseStack(), storage, camera);
            try (FeatureRenderDispatcher.PreparedFrame frame = dispatcher.prepareFrame(storage)) {
                if (!frame.isEmpty()) {
                    try (RenderPass pass = RenderSystem.getDevice().createCommandEncoder().createRenderPass(() -> "Propwash FPV props",
                            target.getColorTextureView(), Optional.empty(), depth, OptionalDouble.empty())) {
                        RenderSystem.bindDefaultUniforms(pass);
                        FeatureRenderDispatcher.renderAllFeatures(pass, frame);
                    }
                    passes++;
                }
            }
        } catch (RuntimeException e) {
            disabled = true;
            if (!warned) {
                warned = true;
                Constants.LOG.warn("FPV-Props: eigener Durchgang fehlgeschlagen, zurück zum Zeichnen mit der Welt", e);
            }
        } finally {
            modelView.popMatrix();
            if (previous != null) {
                RenderSystem.setProjectionMatrix(previous, previousType);
            }
        }
    }

    public static void clear() {
        pending = null;
    }

    public static void forceWorld(boolean force) {
        forceWorld = force;
    }

    public static int deferred() {
        return deferred;
    }

    public static int passes() {
        return passes;
    }

    public static boolean disabled() {
        return disabled;
    }
}
