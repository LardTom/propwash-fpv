package de.lardtom.propwash.osd.client;

import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.renderpearl.api.pipeline.RenderPipeline;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.gui.render.TextureSetup;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.state.gui.GuiElementRenderState;
import org.joml.Matrix3x2fc;
import org.jspecify.annotations.Nullable;

record OsdQuads(Matrix3x2fc pose, float[] quads, int from, int count, int split, int outlineColor, int fillColor,
                @Nullable ScreenRectangle scissorArea, @Nullable ScreenRectangle bounds) implements GuiElementRenderState {
    @Override
    public void buildVertices(VertexConsumer consumer) {
        int end = this.from + this.count;
        for (int i = this.from; i < end; i++) {
            int b = i * 4;
            float x0 = this.quads[b];
            float y0 = this.quads[b + 1];
            float x1 = this.quads[b + 2];
            float y1 = this.quads[b + 3];
            int color = i < this.split ? this.outlineColor : this.fillColor;
            consumer.addVertexWith2DPose(this.pose, x0, y0).setColor(color);
            consumer.addVertexWith2DPose(this.pose, x0, y1).setColor(color);
            consumer.addVertexWith2DPose(this.pose, x1, y1).setColor(color);
            consumer.addVertexWith2DPose(this.pose, x1, y0).setColor(color);
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
