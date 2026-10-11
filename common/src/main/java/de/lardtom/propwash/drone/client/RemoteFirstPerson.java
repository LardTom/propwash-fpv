package de.lardtom.propwash.drone.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import de.lardtom.propwash.content.ContentIds;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.state.level.FirstPersonHandsAndItemsRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import org.joml.Matrix4f;
import org.joml.Vector3f;

public final class RemoteFirstPerson {
    public static final float SCALE = 0.58F;
    public static final float DEPTH = 0.57F;
    public static final float AHEAD_Y = -0.36F;
    public static final float DOWN_Y = -0.35F;
    public static final float AHEAD_TILT = -50.0F;
    public static final float DOWN_TILT = -36.0F;
    public static final float LOOK_DOWN_FULL = 45.0F;
    public static final float HAND_X = 0.05F;
    public static final float HAND_Y = 0.48F;
    public static final float HAND_Z = 0.08F;
    public static final float HAND_ROLL = 14.0F;
    public static final float HAND_PITCH = -10.0F;
    public static final float HAND_SCALE = 0.7F;
    public static final float FOREARM_PITCH = 55.0F;
    public static final float HOLD_SCALE = 0.5F;
    private static final float GRIP_Y = 6.25F / 16.0F;
    private static final float GRIP_Z = -0.5F / 16.0F;
    private static final Vector3f RIGHT_FIST = fist(true);
    private static final Vector3f LEFT_FIST = fist(false);

    private static final ItemStackRenderState RENDER = new ItemStackRenderState();
    private static int frames;

    private RemoteFirstPerson() {
    }

    public static ItemStack held(FirstPersonHandsAndItemsRenderState state, AvatarRenderState avatar) {
        if (state.isScoping || avatar.isUsingItem) {
            return ItemStack.EMPTY;
        }
        if (ContentIds.isRemote(state.mainHandItem)) {
            return state.mainHandItem;
        }
        if (state.mainHandItem.isEmpty() && ContentIds.isRemote(state.offHandItem)) {
            return state.offHandItem;
        }
        return ItemStack.EMPTY;
    }

    private static Vector3f fist(boolean right) {
        float invert = right ? 1.0F : -1.0F;
        return new Matrix4f()
                .translate(-invert * HAND_X, HAND_Y, HAND_Z)
                .rotateZ(invert * HAND_ROLL * Mth.DEG_TO_RAD)
                .rotateX(HAND_PITCH * Mth.DEG_TO_RAD)
                .scale(HAND_SCALE)
                .rotateY(90.0F * Mth.DEG_TO_RAD)
                .rotateY(92.0F * Mth.DEG_TO_RAD)
                .rotateX(45.0F * Mth.DEG_TO_RAD)
                .rotateZ(invert * -41.0F * Mth.DEG_TO_RAD)
                .translate(invert * 0.3F, -1.1F, 0.45F)
                .translate(invert * -5.0F / 16.0F, 2.0F / 16.0F, 0.0F)
                .rotateZ(invert * 0.1F)
                .transformPosition(new Vector3f(-invert / 16.0F, 10.0F / 16.0F, 0.0F));
    }

    public static float lookDown(float xRot) {
        float t = Mth.clamp(xRot / LOOK_DOWN_FULL, 0.0F, 1.0F);
        return t * t * (3.0F - 2.0F * t);
    }

    public static void base(PoseStack poseStack, float xRot, float inverseArmHeight, float attack) {
        float sqrtAttack = Mth.sqrt(attack);
        float ySwing = -0.2F * Mth.sin(attack * (float) Math.PI);
        float zSwing = -0.4F * Mth.sin(sqrtAttack * (float) Math.PI);
        poseStack.translate(0.0F, -ySwing / 2.0F, zSwing);
        float t = lookDown(xRot);
        poseStack.translate(0.0F, Mth.lerp(t, AHEAD_Y, DOWN_Y) + inverseArmHeight * -1.2F, -DEPTH);
        poseStack.rotateDegrees(Axis.XP, Mth.lerp(t, AHEAD_TILT, DOWN_TILT));
        poseStack.scale(HOLD_SCALE, HOLD_SCALE, HOLD_SCALE);
    }

    public static void hand(PoseStack poseStack, boolean right) {
        float invert = right ? 1.0F : -1.0F;
        Vector3f fist = right ? RIGHT_FIST : LEFT_FIST;
        poseStack.translate(fist.x, fist.y, fist.z);
        poseStack.rotateDegrees(Axis.XP, FOREARM_PITCH);
        poseStack.translate(-fist.x, -fist.y, -fist.z);
        poseStack.translate(-invert * HAND_X, HAND_Y, HAND_Z);
        poseStack.rotateDegrees(Axis.ZP, invert * HAND_ROLL);
        poseStack.rotateDegrees(Axis.XP, HAND_PITCH);
        poseStack.scale(HAND_SCALE, HAND_SCALE, HAND_SCALE);
    }

    public static void submitRemote(PoseStack poseStack, SubmitNodeCollector collector, ItemStack stack, float attack, int lightCoords) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null) {
            return;
        }
        minecraft.getItemModelResolver().updateForLiving(RENDER, stack, ItemDisplayContext.NONE, minecraft.player);
        poseStack.rotateDegrees(Axis.XP, Mth.sin(Mth.sqrt(attack) * (float) Math.PI) * 20.0F);
        poseStack.scale(SCALE, SCALE, SCALE);
        poseStack.translate(0.0F, GRIP_Y, GRIP_Z);
        RENDER.submit(poseStack, collector, lightCoords, OverlayTexture.NO_OVERLAY, 0);
        frames++;
    }

    public static int frames() {
        return frames;
    }
}
