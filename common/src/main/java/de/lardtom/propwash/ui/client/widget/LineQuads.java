package de.lardtom.propwash.ui.client.widget;

import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.renderpearl.api.pipeline.RenderPipeline;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.gui.render.TextureSetup;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.state.gui.GuiElementRenderState;
import org.joml.Matrix3x2fc;
import org.jspecify.annotations.Nullable;

record LineQuads(Matrix3x2fc pose, float[] corners, int[] colors, int count, @Nullable ScreenRectangle scissorArea,
                 @Nullable ScreenRectangle bounds) implements GuiElementRenderState {
    @Override
    public void buildVertices(VertexConsumer consumer) {
        for (int i = 0; i < this.count; i++) {
            int b = i * 8;
            int color = this.colors[i];
            consumer.addVertexWith2DPose(this.pose, this.corners[b], this.corners[b + 1]).setColor(color);
            consumer.addVertexWith2DPose(this.pose, this.corners[b + 2], this.corners[b + 3]).setColor(color);
            consumer.addVertexWith2DPose(this.pose, this.corners[b + 4], this.corners[b + 5]).setColor(color);
            consumer.addVertexWith2DPose(this.pose, this.corners[b + 6], this.corners[b + 7]).setColor(color);
        }
    }

    @Override
    public RenderPipeline pipeline() {
        return RenderPipelines.GUI;
    }

    @Override
    public TextureSetup textureSetup() {
        return TextureSetup.noTexture();
    }
}
