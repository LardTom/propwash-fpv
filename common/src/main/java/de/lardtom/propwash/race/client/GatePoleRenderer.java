package de.lardtom.propwash.race.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import de.lardtom.propwash.race.GateGeometry;
import de.lardtom.propwash.race.GateMount;
import de.lardtom.propwash.race.GatePoles;
import de.lardtom.propwash.race.GateStacking;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.world.level.Level;
import org.jspecify.annotations.Nullable;

public final class GatePoleRenderer {
    private static final float END = (float) GatePoles.END;
    private static final float POST_SHIFT = -0.25F;
    private static final float EPSILON = 1.0E-4F;

    private GatePoleRenderer() {
    }

    public static int color(int variant) {
        return 0xFF000000 | GatePoles.ledColor(variant);
    }

    static void fill(GateRenderer renderer, GateRenderState state, GateGeometry local, @Nullable Level level, boolean fitted) {
        state.variant = local.variant();
        state.elementFrame = (float) local.frame();
        state.tiled = false;
        state.startModelled = false;
        state.modelled = fitted && renderer.piecesPresent();
        int color = color(local.variant());
        state.lightColor = color;
        for (ItemStackRenderState piece : state.elementPieces) {
            piece.clear();
        }
        if (!state.modelled) {
            return;
        }
        boolean supported = state.base == GateStacking.Base.STACKED;
        renderer.updatePiece(state, GateRenderer.Piece.POST_LEFT, 0, level);
        renderer.updatePiece(state, GateRenderer.Piece.POST_LEFT_HALF, 0, level);
        renderer.updatePiece(state, GateRenderer.Piece.LED_POST_LEFT, color, level);
        renderer.updatePiece(state, GateRenderer.Piece.LED_POST_LEFT_HALF, color, level);
        renderer.updatePiece(state, GateRenderer.Piece.POLE_END, 0, level);
        if (supported) {
            renderer.updatePiece(state, GateRenderer.Piece.POLE_TEE, 0, level);
        } else {
            renderer.updatePiece(state, GateRenderer.Piece.POLE_FOOT, 0, level);
        }
    }

    static void submitBody(GateRenderState state, PoseStack poseStack, SubmitNodeCollector collector) {
        if (!state.modelled) {
            CourseElementRenderer.submitBody(state, poseStack, collector);
            return;
        }
        float height = state.height;
        int light = state.lightCoords;
        int glow = LightCoordsUtil.FULL_BRIGHT;
        boolean supported = state.base == GateStacking.Base.STACKED;
        poseStack.pushPose();
        GateRenderer.toFrame(state, poseStack);
        if (supported) {
            piece(state, GateRenderer.Piece.POLE_TEE, 0.0F, 0.0F, false, light, poseStack, collector);
        } else {
            piece(state, GateRenderer.Piece.POLE_END, 0.0F, 0.0F, false, light, poseStack, collector);
        }
        piece(state, GateRenderer.Piece.POLE_END, 0.0F, height - END, false, light, poseStack, collector);
        float top = height - END;
        for (float y = supported ? 0.0F : END; y < top - EPSILON; ) {
            boolean whole = top - y >= 1.0F - EPSILON;
            piece(state, whole ? GateRenderer.Piece.POST_LEFT : GateRenderer.Piece.POST_LEFT_HALF, POST_SHIFT, y, false, light, poseStack, collector);
            piece(state, whole ? GateRenderer.Piece.LED_POST_LEFT : GateRenderer.Piece.LED_POST_LEFT_HALF, POST_SHIFT, y, false, glow, poseStack,
                    collector);
            y += whole ? 1.0F : 0.5F;
        }
        if (!supported) {
            if (state.mount == GateMount.HANGING) {
                piece(state, GateRenderer.Piece.POLE_FOOT, 0.0F, height, true, light, poseStack, collector);
            } else {
                piece(state, GateRenderer.Piece.POLE_FOOT, 0.0F, 0.0F, false, light, poseStack, collector);
            }
        }
        poseStack.popPose();
    }

    private static void piece(GateRenderState state, GateRenderer.Piece piece, float x, float y, boolean flip, int light, PoseStack poseStack,
                              SubmitNodeCollector collector) {
        ItemStackRenderState out = state.pieces[piece.ordinal()];
        if (out.isEmpty()) {
            return;
        }
        poseStack.pushPose();
        poseStack.translate(x, y, 0.0F);
        if (flip) {
            poseStack.rotateDegrees(Axis.XP, 180.0F);
        }
        poseStack.scale(state.fitScale, state.fitScale, state.fitScale);
        poseStack.translate(-state.fitX, -state.fitBottom, -state.fitZ);
        out.submit(poseStack, collector, light, OverlayTexture.NO_OVERLAY, state.outlineColor);
        poseStack.popPose();
    }
}
