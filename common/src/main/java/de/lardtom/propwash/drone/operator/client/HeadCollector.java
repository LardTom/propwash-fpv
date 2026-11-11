package de.lardtom.propwash.drone.operator.client;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.gui.Font;
import net.minecraft.client.model.Model;
import net.minecraft.client.renderer.OrderedSubmitNodeCollector;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.block.MovingBlockRenderState;
import net.minecraft.client.renderer.block.dispatch.BlockStateModelPart;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.gizmos.DrawableGizmoPrimitives;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.state.level.QuadParticleRenderState;
import net.minecraft.client.renderer.texture.UvMapping;
import net.minecraft.client.resources.model.geometry.BakedQuad;
import net.minecraft.client.resources.model.geometry.ItemQuads;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Quaternionfc;
import org.joml.Vector3f;
import org.joml.Vector3fc;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

final class HeadCollector implements SubmitNodeCollector {
    private static final int MAX_SPLITS = 256;
    private static final Map<ItemQuads, Map<GimbalHead.Head, Split>> SPLITS = new IdentityHashMap<>();
    private static int headQuads;

    private final SubmitNodeCollector target;
    private final PoseStack.Pose anchor;
    private final GimbalHead.Head head;
    private final Quaternionf rotation;

    HeadCollector(SubmitNodeCollector target, PoseStack.Pose anchor, GimbalHead.Head head, Quaternionfc pose, float tiltDegrees) {
        this.target = target;
        this.anchor = anchor;
        this.head = head;
        float tilt = (float) Math.toRadians(tiltDegrees);
        this.rotation = new Quaternionf().rotationX(-tilt).mul(pose).rotateX(tilt).normalize();
    }

    record Split(ItemQuads head, ItemQuads rest) {
    }

    static synchronized void clear() {
        SPLITS.clear();
    }

    static synchronized int headQuads() {
        return headQuads;
    }

    static synchronized Split split(ItemQuads quads, GimbalHead.Head head) {
        if (SPLITS.size() > MAX_SPLITS) {
            SPLITS.clear();
        }
        Map<GimbalHead.Head, Split> byHead = SPLITS.computeIfAbsent(quads, key -> new IdentityHashMap<>());
        Split known = byHead.get(head);
        if (known != null) {
            return known;
        }
        List<BakedQuad> inside = new ArrayList<>();
        List<BakedQuad> outside = new ArrayList<>();
        for (BakedQuad quad : quads.all()) {
            float x = 0.0F;
            float y = 0.0F;
            float z = 0.0F;
            for (int i = 0; i < BakedQuad.VERTEX_COUNT; i++) {
                Vector3fc p = quad.position(i);
                x += p.x();
                y += p.y();
                z += p.z();
            }
            float n = BakedQuad.VERTEX_COUNT;
            (head.contains(x / n, y / n, z / n) ? inside : outside).add(quad);
        }
        headQuads += inside.size();
        Split split = inside.isEmpty() ? new Split(ItemQuads.EMPTY, quads) : new Split(ItemQuads.split(inside), ItemQuads.split(outside));
        byHead.put(head, split);
        return split;
    }

    @Override
    public void submitItem(PoseStack poseStack, ItemDisplayContext displayContext, int lightCoords, int overlayCoords, int outlineColor, int[] tintLayers,
                           ItemQuads quads, ItemStackRenderState.FoilType foilType) {
        Split split = split(quads, this.head);
        if (split.head().isEmpty()) {
            this.target.submitItem(poseStack, displayContext, lightCoords, overlayCoords, outlineColor, tintLayers, quads, foilType);
            return;
        }
        if (!split.rest().isEmpty()) {
            this.target.submitItem(poseStack, displayContext, lightCoords, overlayCoords, outlineColor, tintLayers, split.rest(), foilType);
        }
        Matrix4f local = new Matrix4f(this.anchor.pose()).invert().mul(poseStack.last().pose());
        Vector3f pivot = local.transformPosition(new Vector3f(this.head.pivotX(), this.head.pivotY(), this.head.pivotZ()));
        PoseStack turned = new PoseStack();
        turned.last().set(this.anchor);
        turned.rotateAround(this.rotation, pivot.x, pivot.y, pivot.z);
        turned.mulPose(local);
        this.target.submitItem(turned, displayContext, lightCoords, overlayCoords, outlineColor, tintLayers, split.head(), foilType);
    }

    @Override
    public OrderedSubmitNodeCollector order(int order) {
        return this.target.order(order);
    }

    @Override
    public void submitShadow(PoseStack poseStack, float radius, List<EntityRenderState.ShadowPiece> pieces) {
        this.target.submitShadow(poseStack, radius, pieces);
    }

    @Override
    public void submitNameTag(PoseStack poseStack, @Nullable Vec3 nameTagAttachment, int offset, Component name, boolean seeThrough, int lightCoords,
                              CameraRenderState camera) {
        this.target.submitNameTag(poseStack, nameTagAttachment, offset, name, seeThrough, lightCoords, camera);
    }

    @Override
    public void submitText(PoseStack poseStack, float x, float y, FormattedCharSequence string, boolean dropShadow, Font.DisplayMode displayMode,
                           int lightCoords, int color, int backgroundColor, int outlineColor) {
        this.target.submitText(poseStack, x, y, string, dropShadow, displayMode, lightCoords, color, backgroundColor, outlineColor);
    }

    @Override
    public void submitTextBackground(PoseStack poseStack, float x0, float y0, float x1, float y1, int color, Font.DisplayMode displayMode,
                                     int lightCoords) {
        this.target.submitTextBackground(poseStack, x0, y0, x1, y1, color, displayMode, lightCoords);
    }

    @Override
    public void submitFlame(PoseStack poseStack, EntityRenderState renderState, Quaternionf rotation) {
        this.target.submitFlame(poseStack, renderState, rotation);
    }

    @Override
    public void submitLeash(PoseStack poseStack, EntityRenderState.LeashState leashState) {
        this.target.submitLeash(poseStack, leashState);
    }

    @Override
    public <S> void submitModel(Model<? super S> model, S state, PoseStack poseStack, RenderType renderType, int lightCoords, int overlayCoords,
                                int tintedColor, @Nullable UvMapping uvMapping, int outlineColor) {
        this.target.submitModel(model, state, poseStack, renderType, lightCoords, overlayCoords, tintedColor, uvMapping, outlineColor);
    }

    @Override
    public <S> void submitCrumblingOverlay(Model<? super S> model, S state, PoseStack poseStack, RenderType renderType, int lightCoords,
                                           int overlayCoords, int tintedColor, ModelFeatureRenderer.CrumblingOverlay crumblingOverlay) {
        this.target.submitCrumblingOverlay(model, state, poseStack, renderType, lightCoords, overlayCoords, tintedColor, crumblingOverlay);
    }

    @Override
    public void submitMovingBlock(PoseStack poseStack, MovingBlockRenderState movingBlockRenderState, int outlineColor) {
        this.target.submitMovingBlock(poseStack, movingBlockRenderState, outlineColor);
    }

    @Override
    public void submitBlockModel(PoseStack poseStack, RenderType renderType, List<BlockStateModelPart> parts, int[] tintLayers, int lightCoords,
                                 int overlayCoords, int outlineColor) {
        this.target.submitBlockModel(poseStack, renderType, parts, tintLayers, lightCoords, overlayCoords, outlineColor);
    }

    @Override
    public void submitBreakingBlockModel(PoseStack poseStack, List<BlockStateModelPart> parts, int progress, boolean isBlockTranslucent) {
        this.target.submitBreakingBlockModel(poseStack, parts, progress, isBlockTranslucent);
    }

    @Override
    public void submitShapeOutline(PoseStack poseStack, VoxelShape shape, RenderType renderType, int color, float width, boolean afterTerrain) {
        this.target.submitShapeOutline(poseStack, shape, renderType, color, width, afterTerrain);
    }

    @Override
    public void submitCustomGeometry(PoseStack poseStack, RenderType renderType, SubmitNodeCollector.CustomGeometryRenderer customGeometryRenderer) {
        this.target.submitCustomGeometry(poseStack, renderType, customGeometryRenderer);
    }

    @Override
    public void submitQuadParticleGroup(QuadParticleRenderState particles) {
        this.target.submitQuadParticleGroup(particles);
    }

    @Override
    public void submitGizmoPrimitives(DrawableGizmoPrimitives.Group group, CameraRenderState camera, boolean onTop) {
        this.target.submitGizmoPrimitives(group, camera, onTop);
    }
}
