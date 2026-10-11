package de.lardtom.propwash.drone.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.QuadInstance;
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
import net.minecraft.resources.Identifier;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.joml.Quaternionf;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

final class BlurItemCollector implements SubmitNodeCollector {
    private final OrderedSubmitNodeCollector target;
    private final @Nullable SubmitNodeCollector root;

    private BlurItemCollector(OrderedSubmitNodeCollector target, @Nullable SubmitNodeCollector root) {
        this.target = target;
        this.root = root;
    }

    static SubmitNodeCollector wrap(SubmitNodeCollector collector) {
        if (collector instanceof GhostCollector || collector instanceof BlurItemCollector) {
            return collector;
        }
        return new BlurItemCollector(collector, collector);
    }

    static void submitQuads(OrderedSubmitNodeCollector target, PoseStack poseStack, List<BakedQuad> quads, int[] tintLayers, int lightCoords,
                            int overlayCoords, int color, Function<Identifier, RenderType> types) {
        if (quads.isEmpty()) {
            return;
        }
        Identifier first = quads.getFirst().materialInfo().sprite().atlasLocation();
        boolean mixed = false;
        for (BakedQuad quad : quads) {
            if (!first.equals(quad.materialInfo().sprite().atlasLocation())) {
                mixed = true;
                break;
            }
        }
        int[] tints = tintLayers.clone();
        if (!mixed) {
            submitAtlas(target, poseStack, types.apply(first), quads, tints, lightCoords, overlayCoords, color);
            return;
        }
        Map<Identifier, List<BakedQuad>> byAtlas = new LinkedHashMap<>();
        for (BakedQuad quad : quads) {
            byAtlas.computeIfAbsent(quad.materialInfo().sprite().atlasLocation(), atlas -> new ArrayList<>()).add(quad);
        }
        for (Map.Entry<Identifier, List<BakedQuad>> entry : byAtlas.entrySet()) {
            submitAtlas(target, poseStack, types.apply(entry.getKey()), entry.getValue(), tints, lightCoords, overlayCoords, color);
        }
    }

    private static void submitAtlas(OrderedSubmitNodeCollector target, PoseStack poseStack, RenderType type, List<BakedQuad> quads, int[] tints,
                                    int lightCoords, int overlayCoords, int color) {
        target.submitCustomGeometry(poseStack, type, (pose, buffer) -> {
            QuadInstance instance = new QuadInstance();
            instance.setLightCoords(lightCoords);
            instance.setOverlayCoords(overlayCoords);
            for (BakedQuad quad : quads) {
                BakedQuad.MaterialInfo material = quad.materialInfo();
                int index = material.tintIndex();
                int base = material.isTinted() && index >= 0 && index < tints.length ? tints[index] : -1;
                instance.setColor(Shape.multiply(base, color));
                buffer.putBakedQuad(pose, quad, instance);
            }
        });
    }

    @Override
    public OrderedSubmitNodeCollector order(int order) {
        return new BlurItemCollector(this.root != null ? this.root.order(order) : this.target, null);
    }

    @Override
    public void submitItem(PoseStack poseStack, ItemDisplayContext displayContext, int lightCoords, int overlayCoords, int outlineColor,
                           int[] tintLayers, ItemQuads quads, ItemStackRenderState.FoilType foilType) {
        List<BakedQuad> translucent = quads.translucent();
        if (translucent.isEmpty()) {
            this.target.submitItem(poseStack, displayContext, lightCoords, overlayCoords, outlineColor, tintLayers, quads, foilType);
            return;
        }
        if (!quads.solid().isEmpty()) {
            this.target.submitItem(poseStack, displayContext, lightCoords, overlayCoords, outlineColor, tintLayers,
                    new ItemQuads(quads.solid(), quads.solid(), List.of()), foilType);
        }
        if (outlineColor != 0) {
            this.target.submitItem(poseStack, displayContext, lightCoords, overlayCoords, outlineColor, tintLayers,
                    new ItemQuads(translucent, List.of(), List.of()), ItemStackRenderState.FoilType.NONE);
        }
        submitQuads(this.target, poseStack, translucent, tintLayers, lightCoords, overlayCoords, -1, AfterWaterRenderTypes::blurItems);
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
