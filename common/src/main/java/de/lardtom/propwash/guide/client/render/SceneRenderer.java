package de.lardtom.propwash.guide.client.render;

import com.mojang.blaze3d.ProjectionType;
import com.mojang.blaze3d.platform.Lighting;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import de.lardtom.propwash.Constants;
import de.lardtom.propwash.guide.scene.SceneProjection;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.render.pip.PictureInPictureRenderer;
import net.minecraft.client.renderer.Projection;
import net.minecraft.client.renderer.ProjectionMatrixBuffer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import org.joml.Matrix4f;
import org.joml.Quaternionfc;

public final class SceneRenderer extends PictureInPictureRenderer<SceneRenderState> {
    public static final float DEPTH_RANGE = 40000.0F;
    private static long renderedFrames;
    private static long failedDrawables;
    private static boolean warned;

    private final Projection projection = new Projection();
    private final ProjectionMatrixBuffer projectionBuffer = new ProjectionMatrixBuffer("Propwash flight school");

    public static long renderedFrames() {
        return renderedFrames;
    }

    public static long failedDrawables() {
        return failedDrawables;
    }

    @Override
    public Class<SceneRenderState> getRenderStateClass() {
        return SceneRenderState.class;
    }

    @Override
    protected void renderToTexture(SceneRenderState state, PoseStack poseStack, SubmitNodeCollector collector) {
        Minecraft.getInstance().gameRenderer.lighting().setupFor(Lighting.Entry.ENTITY_IN_UI);
        Matrix4f base = poseStack.last().pose();
        float width = base.m30() * 2.0F;
        float height = base.m31() * 2.0F;
        if (width > 0.0F && height > 0.0F) {
            this.projection.setupOrtho(-DEPTH_RANGE, DEPTH_RANGE, width, height, true);
            RenderSystem.setProjectionMatrix(this.projectionBuffer.getBuffer(this.projection), ProjectionType.ORTHOGRAPHIC);
        }
        SceneFrame frame = state.frame();
        float pixelScale = width / Math.max(1, state.x1() - state.x0());
        poseStack.setIdentity();
        frame.projection().applyRaster(state.x0(), state.y0(), pixelScale, new PoseTransform(poseStack));
        PoseStack.Pose scene = poseStack.last();
        frame.markRendered(scene.pose(), state.x0(), state.y0(), pixelScale, width, height);
        for (SceneFrame.Drawable drawable : frame.drawables()) {
            PoseStack local = new PoseStack();
            local.last().set(scene);
            try {
                drawable.submit(local, collector);
            } catch (RuntimeException e) {
                failedDrawables++;
                if (!warned) {
                    warned = true;
                    Constants.LOG.error("Flugschule: Szenen-Element konnte nicht gezeichnet werden", e);
                }
            }
        }
        renderedFrames++;
    }

    @Override
    protected float getTranslateY(int height, int guiScale) {
        return height / 2.0F;
    }

    @Override
    protected String getTextureLabel() {
        return "propwash_flight_school";
    }

    @Override
    public void close() {
        super.close();
        this.projectionBuffer.close();
    }

    private record PoseTransform(PoseStack poseStack) implements SceneProjection.Transform {
        @Override
        public void translate(float x, float y, float z) {
            this.poseStack.translate(x, y, z);
        }

        @Override
        public void scale(float x, float y, float z) {
            this.poseStack.scale(x, y, z);
        }

        @Override
        public void rotate(Quaternionfc rotation) {
            this.poseStack.rotate(rotation);
        }
    }
}
