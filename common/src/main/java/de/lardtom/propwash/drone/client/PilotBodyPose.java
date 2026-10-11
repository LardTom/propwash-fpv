package de.lardtom.propwash.drone.client;

import net.minecraft.client.renderer.entity.state.AvatarRenderState;

public final class PilotBodyPose {
    private static long posedFrames;
    private static boolean lastHeadItemVisible;
    private static boolean lastNameHidden;
    private static float lastHeadYaw;
    private static float lastHeadPitch;
    private static float lastBodyRot;

    private PilotBodyPose() {
    }

    public static void apply(AvatarRenderState state) {
        state.nameTag = null;
        state.scoreText = null;
        state.isCrouching = false;
        posedFrames++;
        lastHeadItemVisible = !state.headItem.isEmpty();
        lastNameHidden = state.nameTag == null;
        lastHeadYaw = state.yRot;
        lastHeadPitch = state.xRot;
        lastBodyRot = state.bodyRot;
    }

    public static long posedFrames() {
        return posedFrames;
    }

    public static boolean lastHeadItemVisible() {
        return lastHeadItemVisible;
    }

    public static boolean lastNameHidden() {
        return lastNameHidden;
    }

    public static float lastHeadYaw() {
        return lastHeadYaw;
    }

    public static float lastHeadPitch() {
        return lastHeadPitch;
    }

    public static float lastBodyRot() {
        return lastBodyRot;
    }
}
