package de.lardtom.propwash.guide.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import de.lardtom.propwash.guide.scene.ActorState;
import de.lardtom.propwash.guide.scene.ScenePos;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.item.ItemModelResolver;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.resources.DefaultPlayerSkin;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;

final class ScenePilotVisual {
    private static final float WIDTH = 0.6F;
    private static final float HEIGHT = 1.8F;
    private static final float EYE_HEIGHT = 1.62F;

    private final AvatarRenderState state = new AvatarRenderState();
    private final CameraRenderState camera = new CameraRenderState();

    void prepare(ActorState actor, float timeTicks) {
        Minecraft minecraft = Minecraft.getInstance();
        ItemModelResolver items = minecraft.getItemModelResolver();
        AvatarRenderState s = this.state;
        s.entityType = EntityTypes.PLAYER;
        s.skin = minecraft.player != null ? minecraft.player.getSkin() : DefaultPlayerSkin.getDefaultSkin();
        s.id = Integer.MIN_VALUE;
        s.ageInTicks = timeTicks;
        s.bodyRot = actor.yaw();
        s.yRot = 0.0F;
        s.xRot = actor.headPitch();
        s.isCrouching = actor.crouching();
        s.pose = actor.crouching() ? Pose.CROUCHING : Pose.STANDING;
        s.scale = 1.0F;
        s.ageScale = 1.0F;
        s.lightCoords = LightCoordsUtil.FULL_BRIGHT;
        s.outlineColor = 0;
        s.nameTag = null;
        s.shadowPieces.clear();
        s.boundingBoxWidth = WIDTH;
        s.boundingBoxHeight = HEIGHT;
        s.eyeHeight = EYE_HEIGHT;
        s.mainArm = HumanoidArm.RIGHT;
        s.showCape = false;
        ItemStack head = SceneAssets.item(actor.headItem());
        items.updateForTopItem(s.headItem, head, ItemDisplayContext.HEAD, minecraft.level, null, 0);
        ItemStack hand = SceneAssets.item(actor.handItem());
        s.rightHandItemStack = hand;
        items.updateForTopItem(s.rightHandItemState, hand, ItemDisplayContext.THIRD_PERSON_RIGHT_HAND, minecraft.level, null, 1);
        s.rightArmPose = hand.isEmpty() ? HumanoidModel.ArmPose.EMPTY : HumanoidModel.ArmPose.ITEM;
        s.leftHandItemStack = ItemStack.EMPTY;
        s.leftHandItemState.clear();
        s.leftArmPose = HumanoidModel.ArmPose.EMPTY;
    }

    void submit(ActorState actor, PoseStack ps, SubmitNodeCollector collector) {
        ScenePos pos = actor.position();
        float presence = actor.presence();
        ps.translate(pos.x(), pos.y(), pos.z());
        ps.scale(presence, presence, presence);
        Minecraft.getInstance().getEntityRenderDispatcher().submit(this.state, this.camera, 0.0, 0.0, 0.0, ps, collector);
    }
}
