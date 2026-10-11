package de.lardtom.propwash.input.client.screen.remote;

import com.mojang.blaze3d.platform.Lighting;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import de.lardtom.propwash.Constants;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.render.pip.PictureInPictureRenderer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.util.LightCoordsUtil;

public final class RemoteDemoRenderer extends PictureInPictureRenderer<RemoteDemoRenderState> {
    private static long renderedFrames;
    private static boolean warned;

    public static long renderedFrames() {
        return renderedFrames;
    }

    @Override
    public Class<RemoteDemoRenderState> getRenderStateClass() {
        return RemoteDemoRenderState.class;
    }

    @Override
    protected void renderToTexture(RemoteDemoRenderState state, PoseStack poseStack, SubmitNodeCollector collector) {
        Minecraft.getInstance().gameRenderer.lighting().setupFor(Lighting.Entry.ENTITY_IN_UI);
        RemoteProjection projection = state.projection();
        poseStack.scale(1.0F, -1.0F, -1.0F);
        poseStack.translate(0.0F, -projection.centerOffset() / 16.0F, 0.0F);
        poseStack.rotate(Axis.XP.rotationDegrees(projection.tiltDegrees()));
        poseStack.translate(0.5F - projection.pivotX() / 16.0F, 0.5F - RemoteProjection.PIVOT_Y / 16.0F, 0.5F - RemoteProjection.PIVOT_Z / 16.0F);
        try {
            state.item().submit(poseStack, collector, LightCoordsUtil.FULL_BRIGHT, OverlayTexture.NO_OVERLAY, 0);
            renderedFrames++;
        } catch (RuntimeException e) {
            if (!warned) {
                warned = true;
                Constants.LOG.error("Funke im Empfänger-Assistenten konnte nicht gezeichnet werden", e);
            }
        }
    }

    @Override
    protected float getTranslateY(int height, int guiScale) {
        return height / 2.0F;
    }

    @Override
    protected String getTextureLabel() {
        return "propwash_receiver_wizard_remote";
    }
}
