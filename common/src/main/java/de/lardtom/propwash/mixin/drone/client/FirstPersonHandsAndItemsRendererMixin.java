package de.lardtom.propwash.mixin.drone.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import de.lardtom.propwash.drone.client.RemoteFirstPerson;
import net.minecraft.client.renderer.FirstPersonHandsAndItemsRenderer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.state.level.FirstPersonHandsAndItemsRenderState;
import net.minecraft.client.renderer.state.level.PlayerRenderState;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(FirstPersonHandsAndItemsRenderer.class)
public abstract class FirstPersonHandsAndItemsRendererMixin {
    @Shadow
    protected abstract void renderMapHand(PoseStack poseStack, SubmitNodeCollector submitNodeCollector, int lightCoords, HumanoidArm arm,
                                          PlayerRenderState playerState);

    @Inject(method = "submitArmWithItem", at = @At("HEAD"), cancellable = true)
    private void propwash$holdRemoteWithBothHands(PlayerRenderState playerState, FirstPersonHandsAndItemsRenderState state, float partialTicks,
                                                  float xRot, InteractionHand hand, float attack, ItemStack itemStack, float inverseArmHeight,
                                                  PoseStack poseStack, SubmitNodeCollector submitNodeCollector, int lightCoords, CallbackInfo ci) {
        AvatarRenderState avatar = playerState.avatarRenderState;
        if (avatar == null) {
            return;
        }
        ItemStack remote = RemoteFirstPerson.held(state, avatar);
        if (remote.isEmpty()) {
            return;
        }
        ci.cancel();
        if (hand != InteractionHand.MAIN_HAND) {
            return;
        }
        poseStack.pushPose();
        RemoteFirstPerson.base(poseStack, xRot, inverseArmHeight, attack);
        if (!avatar.isInvisible) {
            for (HumanoidArm arm : HumanoidArm.values()) {
                poseStack.pushPose();
                RemoteFirstPerson.hand(poseStack, arm == HumanoidArm.RIGHT);
                poseStack.rotateDegrees(Axis.YP, 90.0F);
                this.renderMapHand(poseStack, submitNodeCollector, lightCoords, arm, playerState);
                poseStack.popPose();
            }
        }
        RemoteFirstPerson.submitRemote(poseStack, submitNodeCollector, remote, attack, lightCoords);
        poseStack.popPose();
    }
}
