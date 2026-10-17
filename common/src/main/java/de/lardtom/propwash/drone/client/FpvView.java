package de.lardtom.propwash.drone.client;

import de.lardtom.propwash.accessory.client.AddonCamera;
import de.lardtom.propwash.drone.QuadcopterEntity;
import de.lardtom.propwash.video.client.SpectateSession;
import de.lardtom.propwash.video.client.VideoState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.state.LightmapRenderState;
import net.minecraft.client.renderer.state.level.PlayerRenderState;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.joml.Vector3d;
import org.joml.Vector3dc;
import org.joml.Vector3f;
import org.jspecify.annotations.Nullable;

public final class FpvView {
    private static final Vector3d SAMPLE_POS = new Vector3d();
    private static final Quaternionf SAMPLE_ROT = new Quaternionf();
    private static final Vector3f LENS = new Vector3f();
    private static final Vector3d ANCHOR = new Vector3d();
    private static final Quaternionf BODY = new Quaternionf();
    private static boolean bodyKnown;
    private static volatile @Nullable Object pilotBody;

    private FpvView() {
    }

    public static final double BODY_HIDE_DISTANCE = 2.0;

    public static boolean active() {
        return PilotSession.INSTANCE.isFpv() || spectating();
    }

    public static boolean spectating() {
        return !PilotSession.INSTANCE.isActive() && SpectateSession.INSTANCE.drone() != null;
    }

    public static boolean hidesBody(Entity entity, double camX, double camY, double camZ) {
        if (!active() || !isPilotBody(entity)) {
            return false;
        }
        Vec3 center = entity.getBoundingBox().getCenter();
        return center.distanceToSqr(camX, camY, camZ) < BODY_HIDE_DISTANCE * BODY_HIDE_DISTANCE
                || entity.getBoundingBox().contains(camX, camY, camZ);
    }

    public static boolean isPilotBody(Object entity) {
        Object body = pilotBody;
        return body != null && body == entity;
    }

    public static void clearPilotScreenEffects(PlayerRenderState state) {
        state.blockOverlay = null;
        state.waterOverlay = null;
        state.isEyeInWater = false;
        state.isOnFire = false;
        state.isUnderWater = false;
        state.portalEffectIntensity = 0.0F;
        state.nauseaEffectIntensity = 0.0F;
        state.spinningEffectAngle = 0.0F;
        state.itemActivation = null;
    }

    public static void clearPilotLightEffects(LightmapRenderState state) {
        state.nightVisionEffectIntensity = 0.0F;
        state.darknessEffectScale = 0.0F;
        state.brightness = Minecraft.getInstance().options.gamma().get().floatValue();
    }

    static void setPilotBody(@Nullable Object body) {
        pilotBody = body;
    }

    public static void holdBody(@Nullable Object body) {
        if (!PilotSession.INSTANCE.isActive()) {
            pilotBody = body;
        }
    }

    public static Vector3dc anchor() {
        return ANCHOR;
    }

    public static boolean displayedBody(Vector3d outCom, Quaternionf outRotation) {
        if (!bodyKnown || !active()) {
            return false;
        }
        outCom.set(ANCHOR);
        outRotation.set(BODY);
        return true;
    }

    public static boolean cameraPose(Vector3d outPosition, Quaternionf outRotation) {
        PilotSession session = PilotSession.INSTANCE;
        if (!session.isFpv()) {
            bodyKnown = spectatePose(outPosition, outRotation);
            return bodyKnown;
        }
        if (!VideoState.INSTANCE.sampleDisplayed(session.physicsFrameNanos(), SAMPLE_POS, SAMPLE_ROT)) {
            session.comPosition(SAMPLE_POS);
            session.bodyOrientation(SAMPLE_ROT);
        }
        ANCHOR.set(SAMPLE_POS);
        BODY.set(SAMPLE_ROT);
        bodyKnown = true;
        LENS.set((float) session.lensRightOffset(), (float) session.lensUpOffset(), (float) -session.lensForwardOffset());
        SAMPLE_ROT.transform(LENS);
        outPosition.set(SAMPLE_POS.x + LENS.x, SAMPLE_POS.y + LENS.y, SAMPLE_POS.z + LENS.z);
        outRotation.set(SAMPLE_ROT).rotateX((float) Math.toRadians(session.cameraUptiltDeg()));
        QuadcopterEntity flown = session.drone();
        if (flown == null || !AddonCamera.apply(flown, SAMPLE_ROT, session.cameraUptiltDeg(), outRotation, false)) {
            FpvShake.INSTANCE.apply(VideoState.INSTANCE.lastSampleNanos(), outRotation);
        }
        return true;
    }

    private static boolean spectatePose(Vector3d outPosition, Quaternionf outRotation) {
        if (!spectating()) {
            return false;
        }
        SpectateSession spectate = SpectateSession.INSTANCE;
        QuadcopterEntity drone = spectate.drone();
        if (drone == null) {
            return false;
        }
        if (!VideoState.INSTANCE.sampleDisplayed(spectate.frameNanos(), SAMPLE_POS, SAMPLE_ROT)) {
            SAMPLE_POS.set(drone.getX(), drone.getY() + spectate.comOffset(drone), drone.getZ());
            SAMPLE_ROT.set(drone.orientation());
        }
        ANCHOR.set(SAMPLE_POS);
        BODY.set(SAMPLE_ROT);
        LENS.set((float) spectate.lensRightOffset(drone), (float) spectate.lensUpOffset(drone), (float) -spectate.lensForwardOffset(drone));
        SAMPLE_ROT.transform(LENS);
        outPosition.set(SAMPLE_POS.x + LENS.x, SAMPLE_POS.y + LENS.y, SAMPLE_POS.z + LENS.z);
        outRotation.set(SAMPLE_ROT).rotateX((float) Math.toRadians(spectate.cameraUptiltDeg(drone)));
        AddonCamera.apply(drone, SAMPLE_ROT, (float) spectate.cameraUptiltDeg(drone), outRotation, true);
        return true;
    }
}
