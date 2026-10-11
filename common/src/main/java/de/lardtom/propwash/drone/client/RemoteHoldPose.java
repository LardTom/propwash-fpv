package de.lardtom.propwash.drone.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import de.lardtom.propwash.content.ContentIds;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.item.ItemDisplayContext;
import org.joml.Vector3f;
import org.jspecify.annotations.Nullable;

public final class RemoteHoldPose {
    public static final float PITCH = (float) Math.toRadians(-40.0);
    public static final float YAW = (float) Math.toRadians(23.0);
    public static final float TILT = 55.0F;
    public static final float SCALE = 0.6F;
    public static final float HAND_Y = 9.5F;
    public static final float GRIP_Y = 3.5F;
    public static final float GRIP_Z = 8.5F;
    public static final float CROUCH_PITCH = 0.4F;
    private static final float BREATH = 0.025F;
    private static final float BREATH_RATE = 0.067F;
    private static final float HAND_X = 1.0F;

    private static int frames;

    private RemoteHoldPose() {
    }

    public static @Nullable HumanoidArm holdingArm(HumanoidRenderState state) {
        if (!(state instanceof AvatarRenderState avatar) || avatar.isSpectator || !upright(state)) {
            return null;
        }
        HumanoidArm main = avatar.mainArm;
        if (ContentIds.isRemote(state.getUseItemStackForArm(main))) {
            return main;
        }
        HumanoidArm off = main.getOpposite();
        if (ContentIds.isRemote(state.getUseItemStackForArm(off)) && state.getUseItemStackForArm(main).isEmpty()) {
            return off;
        }
        return null;
    }

    public static boolean hidesItemIn(HumanoidRenderState state, HumanoidArm arm) {
        HumanoidArm holding = holdingArm(state);
        return holding != null && holding != arm && !state.getUseItemStackForArm(arm).isEmpty();
    }

    public static void apply(HumanoidModel<?> model, HumanoidRenderState state) {
        if (holdingArm(state) == null) {
            return;
        }
        float pitch = PITCH + (state.isCrouching ? CROUCH_PITCH : 0.0F) + Mth.sin(state.ageInTicks * BREATH_RATE) * BREATH;
        float twist = model.body.yRot;
        pose(model.rightArm, pitch, -YAW + twist);
        pose(model.leftArm, pitch, YAW + twist);
    }

    public static void extract(LivingEntity entity, AvatarRenderState state) {
        ItemStackRenderState remote = ((RemoteHoldAccess) state).propwash$remoteHold();
        remote.clear();
        HumanoidArm arm = holdingArm(state);
        if (arm != null) {
            Minecraft.getInstance().getItemModelResolver().updateForLiving(remote, entity.getItemHeldByArm(arm), ItemDisplayContext.NONE, entity);
        }
    }

    public static void submit(HumanoidModel<?> model, AvatarRenderState state, HumanoidArm arm, PoseStack poseStack,
                              SubmitNodeCollector submitNodeCollector, int lightCoords) {
        ItemStackRenderState remote = ((RemoteHoldAccess) state).propwash$remoteHold();
        if (remote.isEmpty()) {
            Minecraft minecraft = Minecraft.getInstance();
            minecraft.getItemModelResolver().updateForTopItem(remote, state.getUseItemStackForArm(arm), ItemDisplayContext.NONE, minecraft.level, null, 0);
        }
        poseStack.pushPose();
        model.root().translateAndRotate(poseStack);
        transform(model, poseStack);
        remote.submit(poseStack, submitNodeCollector, lightCoords, OverlayTexture.NO_OVERLAY, state.outlineColor);
        poseStack.popPose();
        frames++;
    }

    public static void transform(HumanoidModel<?> model, PoseStack poseStack) {
        Vector3f right = hand(model.rightArm, -HAND_X);
        Vector3f left = hand(model.leftArm, HAND_X);
        poseStack.translate((right.x + left.x) * 0.5F, (right.y + left.y) * 0.5F, (right.z + left.z) * 0.5F);
        poseStack.rotate(Axis.YP, (model.rightArm.yRot + model.leftArm.yRot) * 0.5F);
        poseStack.rotate(Axis.XP, (model.rightArm.xRot + model.leftArm.xRot) * 0.5F - PITCH);
        poseStack.rotateDegrees(Axis.ZP, 180.0F);
        poseStack.rotateDegrees(Axis.XP, -TILT);
        poseStack.scale(SCALE, SCALE, SCALE);
        poseStack.translate(0.0F, (8.0F - GRIP_Y) / 16.0F, (8.0F - GRIP_Z) / 16.0F);
    }

    public static Vector3f hand(ModelPart arm, float x) {
        PoseStack local = new PoseStack();
        arm.translateAndRotate(local);
        return local.last().pose().transformPosition(x / 16.0F, HAND_Y / 16.0F, 0.0F, new Vector3f());
    }

    public static int frames() {
        return frames;
    }

    private static boolean upright(HumanoidRenderState state) {
        return !state.isFallFlying && !state.isVisuallySwimming && !(state.swimAmount > 0.0F) && !state.isUsingItem && !state.isAutoSpinAttack
                && !state.hasPose(Pose.SLEEPING);
    }

    private static void pose(ModelPart arm, float pitch, float yaw) {
        arm.xRot = pitch;
        arm.yRot = yaw;
        arm.zRot = 0.0F;
    }
}
