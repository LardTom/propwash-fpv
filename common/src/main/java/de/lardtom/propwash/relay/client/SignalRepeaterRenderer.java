package de.lardtom.propwash.relay.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import de.lardtom.propwash.drone.client.render.DroneRenderResources;
import de.lardtom.propwash.relay.RepeaterMeter;
import de.lardtom.propwash.relay.SignalRepeaterBlock;
import de.lardtom.propwash.relay.SignalRepeaterBlockEntity;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.Direction;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.AttachFace;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

public final class SignalRepeaterRenderer implements BlockEntityRenderer<SignalRepeaterBlockEntity, SignalRepeaterRenderer.State> {
    static final float BAR_FROM_X = 6.4F;
    static final float BAR_STEP = 0.9F;
    static final float BAR_WIDTH = 0.6F;
    static final float BAR_FROM_Z = 5.6F;
    static final float BAR_TO_Z = 7.0F;
    static final float BAR_BASE = 3.5F;
    static final float BAR_LOW = 0.4F;
    static final float BAR_RISE = 0.3F;
    private static final float TEXEL = 0.5F;
    private static @Nullable RenderType type;

    public SignalRepeaterRenderer(BlockEntityRendererProvider.Context context) {
    }

    @Override
    public State createRenderState() {
        return new State();
    }

    @Override
    public void extractRenderState(SignalRepeaterBlockEntity entity, State state, float partialTicks, Vec3 cameraPosition,
                                   ModelFeatureRenderer.@Nullable CrumblingOverlay breakProgress) {
        BlockEntityRenderer.super.extractRenderState(entity, state, partialTicks, cameraPosition, breakProgress);
        BlockState block = entity.getBlockState();
        state.bars = RepeaterSignals.INSTANCE.bars(entity.getBlockPos());
        if (block.getBlock() instanceof SignalRepeaterBlock) {
            AttachFace face = block.getValue(SignalRepeaterBlock.FACE);
            Direction facing = block.getValue(SignalRepeaterBlock.FACING);
            state.rotX = face == AttachFace.FLOOR ? 0 : face == AttachFace.WALL ? 90 : 180;
            state.rotY = (int) facing.toYRot() - 180 + (face == AttachFace.CEILING ? 180 : 0);
        }
    }

    @Override
    public void submit(State state, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera) {
        int bars = state.bars;
        int lit = RepeaterMeter.color(bars);
        int light = state.lightCoords;
        poseStack.pushPose();
        poseStack.translate(0.5F, 0.5F, 0.5F);
        poseStack.rotateDegrees(Axis.YN, state.rotY);
        poseStack.rotateDegrees(Axis.XN, state.rotX);
        poseStack.translate(-0.5F, -0.5F, -0.5F);
        poseStack.scale(1.0F / 16.0F, 1.0F / 16.0F, 1.0F / 16.0F);
        collector.submitCustomGeometry(poseStack, type(), (pose, buffer) -> {
            for (int i = 0; i < RepeaterMeter.BARS; i++) {
                boolean on = i < bars;
                float x0 = BAR_FROM_X + i * BAR_STEP;
                float top = BAR_BASE + BAR_LOW + i * BAR_RISE;
                box(pose, buffer, x0, BAR_BASE, BAR_FROM_Z, x0 + BAR_WIDTH, top, BAR_TO_Z, on ? lit : RepeaterMeter.DARK,
                        on ? LightCoordsUtil.FULL_BRIGHT : light);
            }
        });
        poseStack.popPose();
    }

    private static RenderType type() {
        if (type == null) {
            type = RenderTypes.entityCutout(DroneRenderResources.WHITE_TEXTURE);
        }
        return type;
    }

    private static void box(PoseStack.Pose pose, VertexConsumer buffer, float x0, float y0, float z0, float x1, float y1, float z1, int color,
                            int light) {
        quad(pose, buffer, color, light, 0.0F, 1.0F, 0.0F, x0, y1, z0, x0, y1, z1, x1, y1, z1, x1, y1, z0);
        quad(pose, buffer, color, light, 0.0F, 0.0F, -1.0F, x0, y0, z0, x0, y1, z0, x1, y1, z0, x1, y0, z0);
        quad(pose, buffer, color, light, 0.0F, 0.0F, 1.0F, x0, y0, z1, x1, y0, z1, x1, y1, z1, x0, y1, z1);
        quad(pose, buffer, color, light, -1.0F, 0.0F, 0.0F, x0, y0, z0, x0, y0, z1, x0, y1, z1, x0, y1, z0);
        quad(pose, buffer, color, light, 1.0F, 0.0F, 0.0F, x1, y0, z0, x1, y1, z0, x1, y1, z1, x1, y0, z1);
    }

    private static void quad(PoseStack.Pose pose, VertexConsumer buffer, int color, int light, float nx, float ny, float nz, float ax, float ay,
                             float az, float bx, float by, float bz, float cx, float cy, float cz, float dx, float dy, float dz) {
        vertex(pose, buffer, color, light, nx, ny, nz, ax, ay, az);
        vertex(pose, buffer, color, light, nx, ny, nz, bx, by, bz);
        vertex(pose, buffer, color, light, nx, ny, nz, cx, cy, cz);
        vertex(pose, buffer, color, light, nx, ny, nz, dx, dy, dz);
    }

    private static void vertex(PoseStack.Pose pose, VertexConsumer buffer, int color, int light, float nx, float ny, float nz, float x, float y,
                               float z) {
        buffer.addVertex(pose, x, y, z).setColor(color).setUv(TEXEL, TEXEL).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light)
                .setNormal(pose, nx, ny, nz);
    }

    public static final class State extends BlockEntityRenderState {
        int bars;
        int rotX;
        int rotY;
    }
}
